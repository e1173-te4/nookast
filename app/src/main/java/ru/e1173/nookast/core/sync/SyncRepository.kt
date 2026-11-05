package ru.e1173.nookast.core.sync

import androidx.room.withTransaction
import java.util.UUID
import org.json.JSONArray
import ru.e1173.nookast.data.local.AccountEntity
import ru.e1173.nookast.data.local.PlaybackProgressEntity
import ru.e1173.nookast.data.local.QueueIntentEntity
import ru.e1173.nookast.data.local.QueueItemEntity
import ru.e1173.nookast.data.local.ServerQueueEntity
import ru.e1173.nookast.data.local.SyncDatabase
import ru.e1173.nookast.data.local.SyncLogEntity
import ru.e1173.nookast.data.local.SyncV8
import ru.e1173.nookast.sync.ProgressCause

/** Clocks of the engine (I10): order never depends on wall time. */
interface SyncClock {
    fun elapsedRealtimeMs(): Long
    fun wallTimeMs(): Long
    fun bootCount(): Int
}

/**
 * Durable state of the synchronization redesign (docs/SYNC.md §3–§6) on Room v8. Every method is
 * one transaction: an intent and its local effect are written together (I1), confirmations are
 * conditional on the intent counter, and server observations carry a ticket reserved before the
 * request, so a stale answer never overwrites a newer intent or confirmation (I5).
 */
class SyncRepository(
    private val database: SyncDatabase,
    private val clock: SyncClock,
) {
    private val dao = database.syncV8Dao()
    private val queueDao = database.queueDao()

    /** Counter of the last confirmed queue delivery; queue reads older than it are discarded. */
    @Volatile
    private var queueConfirmedCounter = 0L

    /**
     * The database was cleared (sign-out with discard, "delete the previous account's data"):
     * in-memory marks of the old data go too, or every queue read of the next session would be
     * discarded as older than a delivery that no longer exists (review 3.6, finding 9).
     */
    fun onDatabaseCleared() {
        queueConfirmedCounter = 0L
        quarantined = false
    }

    // --- Counter ---

    private suspend fun next(): Long {
        dao.ensureCounter()
        dao.incrementCounter()
        return dao.counter()
    }

    /** Reserves an observation ticket before a read is sent (§4). */
    suspend fun reserveTicket(): Long = database.withTransaction { next() }

    /** A counter value for intents kept outside this repository (saved, deferred RSS intents). */
    suspend fun nextCounter(): Long = database.withTransaction { next() }

    /** Highest counter of a pending queue intent for [episodeId], or null. */
    suspend fun latestQueueIntentCounter(episodeId: Int): Long? =
        dao.queueIntents().filter { it.episodeId == episodeId }.maxOfOrNull { it.counter }

    // --- Progress ---

    suspend fun progress(episodeId: Int): ProgressState? = dao.progress(episodeId)?.toState()

    /** Records an intent; false when the reducer found it a no-op (nothing written, no wake-up). */
    suspend fun recordProgress(episodeId: Int, intent: ProgressIntent): Boolean = database.withTransaction {
        if (quarantined) return@withTransaction false
        val entity = dao.progress(episodeId)
        val current = entity?.toState() ?: ProgressState.empty(episodeId)
        val merged = ProgressIntentReducer.apply(current, intent, next()) ?: return@withTransaction false
        dao.upsertProgress(merged.toEntity(entity, clock.wallTimeMs()))
        true
    }

    suspend fun dirtyProgress(limit: Int = 100): List<ProgressState> = dao.dirtyProgress(limit).map { it.toState() }

    suspend fun noteSent(episodeId: Int, positionMs: Long) = dao.noteSent(episodeId, positionMs)

    suspend fun advanceRewindPhase(episodeId: Int, baseCounter: Long): Boolean =
        dao.advanceRewindPhase(episodeId, baseCounter) == 1

    /**
     * Confirms the delivered intent unless a newer one arrived meanwhile (S07). [serverAfter] is
     * what the server holds after the write (max for a record, the exact value after a rewind):
     * it becomes the base of the next intents, so stale passive positions can be detected (§5.5).
     */
    suspend fun confirmProgress(
        episodeId: Int,
        sentCounter: Long,
        serverAfter: ServerKnowledge? = null,
    ): Boolean = database.withTransaction {
        val confirmed = dao.confirmProgress(episodeId, sentCounter, next()) == 1
        if (serverAfter != null) {
            val entity = dao.progress(episodeId)
            if (entity != null) {
                val position = when (serverAfter) {
                    is ServerKnowledge.AtLeast -> maxOf(entity.serverPositionSeconds ?: 0, serverAfter.seconds)
                    is ServerKnowledge.Exactly -> serverAfter.seconds
                }
                dao.upsertProgress(
                    entity.copy(
                        serverPositionSeconds = position,
                        serverCompleted = serverAfter.completed ?: entity.serverCompleted,
                        baseServerPositionSeconds = if (entity.dirty) entity.baseServerPositionSeconds ?: position else position,
                    ),
                )
            }
        }
        confirmed
    }

    /** What a confirmed write implies about the server (max-only records, exact rewinds). */
    sealed interface ServerKnowledge {
        val completed: Boolean?

        data class AtLeast(val seconds: Long, override val completed: Boolean? = null) : ServerKnowledge

        data class Exactly(val seconds: Long, override val completed: Boolean? = null) : ServerKnowledge
    }

    /**
     * Applies a server observation taken with [ticket]. Ignored when the ticket is older than the
     * row's last intent, confirmation or observation. A clean row adopts the server state (§5.9);
     * a dirty passive intent the server already exceeds is a proven no-op (§5.8).
     */
    suspend fun observeProgress(ticket: Long, server: ServerEpisode): Boolean = database.withTransaction {
        val entity = dao.progress(server.episodeId)
        val row = entity?.toState() ?: ProgressState.empty(server.episodeId)
        if (ticket <= row.intentCounter || ticket <= row.confirmedCounter || ticket <= row.serverTicket) {
            return@withTransaction false
        }
        var next = row.copy(
            serverPositionSeconds = server.listenSeconds,
            serverCompleted = server.completed,
            serverTicket = ticket,
            durationMs = row.durationMs ?: server.durationSeconds?.let { it * 1_000 },
        )
        if (!row.dirty) {
            next = next.copy(positionMs = server.listenSeconds * 1_000, completed = server.completed)
        } else if (row.kind == ProgressIntentKind.PASSIVE && !row.migrated &&
            server.listenSeconds >= row.positionMs / 1_000
        ) {
            next = next.copy(
                dirty = false,
                kind = ProgressIntentKind.NONE,
                positionMs = server.listenSeconds * 1_000,
                completed = server.completed,
            )
            log("progress.noop", "episode=${row.episodeId} server=${server.listenSeconds}s local=${row.positionMs / 1_000}s")
        }
        dao.upsertProgress(next.toEntity(entity, clock.wallTimeMs()))
        true
    }

    // --- Queue ---

    suspend fun queueIntents(): List<QueueIntent> = dao.queueIntents().map { it.toIntent() }

    suspend fun localQueue(): List<Int> = queueDao.getQueue().map { it.episodeId }

    /**
     * Adds a queue intent and its local effect in one transaction (§6.1). A CLEAR records the queue
     * the user saw. Returns false for a duplicate operation ID.
     */
    suspend fun addQueueIntent(build: (counter: Long, operationId: String, localQueue: List<Int>) -> QueueIntent): Boolean =
        database.withTransaction {
            if (quarantined) return@withTransaction false
            val local = localQueue()
            val intent = build(next(), UUID.randomUUID().toString(), local)
            if (dao.insertQueueIntent(intent.toEntity()) == -1L) return@withTransaction false
            val state = queueDao.getQueueState()
            val effective = if (state == null || state.serverKnown) {
                QueueRebase.rebase(dao.serverQueue().map { it.episodeId }, queueIntents())
            } else {
                // Until the first observation after the migration, keep v7's local queue (§12).
                QueueRebase.rebase(local, listOf(intent))
            }
            queueDao.replaceQueue(effective.toQueueItems(clock.wallTimeMs()))
            true
        }

    /**
     * Completion (S31): COMPLETE and, if the episode is queued, a REMOVE that waits for it (§6.5),
     * in one transaction. [removeFromQueue] is false while the user is still on the episode
     * (own auto-mark, §5.7): the REMOVE is added when they leave it.
     */
    suspend fun complete(episodeId: Int, durationMs: Long?, session: String?, removeFromQueue: Boolean = true) =
        database.withTransaction {
            if (quarantined) return@withTransaction
            val entity = dao.progress(episodeId)
            val current = entity?.toState() ?: ProgressState.empty(episodeId)
            val counter = next()
            val merged = ProgressIntentReducer.apply(current, ProgressIntent.Complete(durationMs, session), counter)
            if (merged != null) dao.upsertProgress(merged.toEntity(entity, clock.wallTimeMs()))
            if (removeFromQueue && episodeId in localQueue()) {
                val remove = QueueIntent.Remove(episodeId, next(), UUID.randomUUID().toString())
                dao.insertQueueIntent(
                    remove.toEntity().copy(
                        dependsOnEpisodeId = if (merged != null) episodeId else null,
                        dependsOnCounter = if (merged != null) counter else null,
                    ),
                )
                recomputeLocalQueue(remove)
            }
        }

    /** Effective queue after [added]; before the first observation only [added] applies (§12). */
    private suspend fun recomputeLocalQueue(added: QueueIntent) {
        val state = queueDao.getQueueState()
        val effective = if (state == null || state.serverKnown) {
            QueueRebase.rebase(dao.serverQueue().map { it.episodeId }, queueIntents())
        } else {
            QueueRebase.rebase(localQueue(), listOf(added))
        }
        queueDao.replaceQueue(effective.toQueueItems(clock.wallTimeMs()))
    }

    /**
     * Applies a server queue read with [ticket] (§6.6): canonical order, then the effective queue is
     * recomputed from it and ALL intents in the same transaction (review F3). Returns false for a
     * stale read or an unchanged fingerprint.
     */
    suspend fun observeQueue(ticket: Long, rows: List<ServerQueueRow>): Boolean = database.withTransaction {
        if (ticket <= queueConfirmedCounter) return@withTransaction false
        // Display data refreshes even when the order is unchanged (titles, durations, progress).
        val now = clock.wallTimeMs()
        val cache = rows.mapNotNull { row ->
            val meta = row.metadata ?: return@mapNotNull null
            ru.e1173.nookast.data.local.QueueEpisodeEntity(
                episodeId = row.episodeId,
                episodeTitle = meta.title,
                podcastName = meta.podcastName,
                artworkUrl = meta.artworkUrl,
                mediaUrl = meta.mediaUrl,
                durationSeconds = meta.durationSeconds,
                listenDurationSeconds = row.listenSeconds?.toDouble(),
                completed = row.completed,
                description = meta.description,
                publishedAt = meta.publishedAt,
                downloaded = meta.downloaded,
                refreshedAtEpochMs = now,
            )
        }
        if (cache.isNotEmpty()) queueDao.upsertEpisodeCache(cache)
        val canonical = ServerQueueCanon.canonical(rows)
        val fingerprint = ServerQueueCanon.fingerprint(canonical)
        val state = queueDao.getQueueState()
        if (state != null && state.serverKnown && state.serverFingerprint == fingerprint) {
            return@withTransaction false
        }
        replaceServerQueue(canonical, ticket, fingerprint)
        val effective = QueueRebase.rebase(canonical, queueIntents())
        queueDao.replaceQueue(effective.toQueueItems(clock.wallTimeMs()))
        true
    }

    /** Confirms delivered queue intents after the verifying read [verified] matched (§6.4 step 6). */
    suspend fun confirmQueue(operationIds: List<String>, verified: List<Int>) = database.withTransaction {
        val counter = next()
        queueConfirmedCounter = counter
        if (operationIds.isNotEmpty()) dao.deleteQueueIntents(operationIds)
        replaceServerQueue(verified, counter, ServerQueueCanon.fingerprint(verified))
        queueDao.replaceQueue(QueueRebase.rebase(verified, queueIntents()).toQueueItems(clock.wallTimeMs()))
    }

    private suspend fun replaceServerQueue(ids: List<Int>, ticket: Long, fingerprint: String) {
        dao.clearServerQueue()
        dao.insertServerQueue(ids.mapIndexed { index, id -> ServerQueueEntity(id, index, ticket) })
        queueDao.upsertQueueState(
            ru.e1173.nookast.data.local.QueueStateEntity(
                serverFingerprint = fingerprint,
                serverItemCount = ids.size,
                fetchedAtEpochMs = clock.wallTimeMs(),
                serverKnown = true,
            ),
        )
    }

    // --- Failures: backoff, dead letter, dropped intents (§8) ---

    /** True when [aggregate] may be delivered now. A different boot invalidates the backoff (§4). */
    suspend fun isReady(aggregate: String): Boolean {
        val backoff = dao.backoff(aggregate) ?: return true
        if (backoff.pausedReason != null) return false
        if (backoff.bootCount != clock.bootCount()) return true
        return clock.elapsedRealtimeMs() >= backoff.notBeforeElapsedMs
    }

    suspend fun attempts(aggregate: String): Int = dao.backoff(aggregate)?.attempts ?: 0

    suspend fun recordFailure(aggregate: String, category: SyncFailure, delayMs: Long, pausedReason: String? = null) {
        val previous = dao.backoff(aggregate)
        dao.upsertBackoff(
            ru.e1173.nookast.data.local.SyncBackoffEntity(
                aggregate = aggregate,
                attempts = (previous?.attempts ?: 0) + 1,
                notBeforeElapsedMs = clock.elapsedRealtimeMs() + delayMs,
                bootCount = clock.bootCount(),
                lastCategory = category.name,
                pausedReason = pausedReason,
            ),
        )
    }

    suspend fun clearFailure(aggregate: String) = dao.clearBackoff(aggregate)

    /** Network came back: every backoff expires (S52). Pauses stay. */
    suspend fun expireBackoff() = database.withTransaction {
        dao.clearAllBackoff()
    }

    /** Moves a progress intent to the dead letter; the local position stays (§8.1). */
    suspend fun deadLetterProgress(state: ProgressState, category: SyncFailure, reason: String, excerpt: String?) =
        database.withTransaction {
            val entity = dao.progress(state.episodeId) ?: return@withTransaction
            if (entity.intentCounter != state.intentCounter) return@withTransaction
            dao.insertDeadLetter(
                ru.e1173.nookast.data.local.DeadLetterEntity(
                    aggregate = "progress",
                    aggregateKey = state.episodeId.toString(),
                    intentCounter = state.intentCounter,
                    payloadJson = org.json.JSONObject()
                        .put("kind", state.kind.name)
                        .put("position_ms", state.positionMs)
                        .put("completed", state.completed)
                        .toString(),
                    category = category.name,
                    reasonCode = reason,
                    errorExcerpt = excerpt,
                    attempts = attempts("progress:${state.episodeId}"),
                    accountFingerprint = entity.accountFingerprint,
                ),
            )
            dao.upsertProgress(entity.copy(dirty = false, intentKind = SyncV8.IntentKind.NONE, rewindBaseCounter = null, rewindPhase = 0))
            dao.clearBackoff("progress:${state.episodeId}")
            log("progress.dead_letter", "episode=${state.episodeId} reason=$reason")
        }

    /** Drops a progress intent the server proved unnecessary or superseded (I2.4). */
    suspend fun dropProgressIntent(state: ProgressState, reason: String): Boolean = database.withTransaction {
        val dropped = dao.confirmProgress(state.episodeId, state.intentCounter, next()) == 1
        if (dropped) log("progress.dropped", "episode=${state.episodeId} reason=$reason")
        dropped
    }

    /** Moves queue intents to the dead letter and recomputes the local queue (§6.4). */
    suspend fun deadLetterQueueIntents(intents: List<QueueIntent>, category: SyncFailure, reason: String) =
        database.withTransaction {
            if (intents.isEmpty()) return@withTransaction
            intents.forEach { intent ->
                dao.insertDeadLetter(
                    ru.e1173.nookast.data.local.DeadLetterEntity(
                        aggregate = "queue",
                        aggregateKey = intent.operationId,
                        intentCounter = intent.counter,
                        payloadJson = org.json.JSONObject().put("intent", intent.toString()).toString(),
                        category = category.name,
                        reasonCode = reason,
                        errorExcerpt = null,
                        attempts = attempts("queue"),
                        accountFingerprint = null,
                    ),
                )
            }
            dao.deleteQueueIntents(intents.map { it.operationId })
            val effective = QueueRebase.rebase(dao.serverQueue().map { it.episodeId }, queueIntents())
            queueDao.replaceQueue(effective.toQueueItems(clock.wallTimeMs()))
            log("queue.dead_letter", "${intents.size} intent(s) reason=$reason")
        }

    /** Deletes intents compaction proved superseded (I2.2). */
    suspend fun dropQueueIntents(intents: List<QueueIntent>) {
        if (intents.isEmpty()) return
        dao.deleteQueueIntents(intents.map { it.operationId })
        log("queue.compacted", "${intents.size} superseded intent(s)")
    }

    /** Queue intents waiting for, or made obsolete by, a progress intent (§6.5, review F6). */
    data class QueueDependencies(val waiting: Set<String>, val superseded: Set<String>)

    suspend fun queueDependencies(): QueueDependencies {
        val waiting = mutableSetOf<String>()
        val superseded = mutableSetOf<String>()
        val intents = dao.queueIntents()
        intents.forEach { intent ->
            val episodeId = intent.dependsOnEpisodeId ?: return@forEach
            // The user queued the episode again after marking it played: the removal that waited
            // for the "played" delivery is obsolete (review 3.6, finding 5).
            val requeued = intents.any { other ->
                other.episodeId == intent.episodeId && other.counter > intent.counter &&
                    (other.kind == SyncV8.QueueKind.ADD || other.kind == SyncV8.QueueKind.MOVE)
            }
            if (intent.kind == SyncV8.QueueKind.REMOVE && requeued) {
                superseded += intent.operationId
                return@forEach
            }
            val dependsOn = intent.dependsOnCounter ?: return@forEach
            val progress = dao.progress(episodeId)?.toState() ?: return@forEach
            if (!progress.dirty) return@forEach
            when {
                progress.intentCounter > dependsOn &&
                    (progress.kind == ProgressIntentKind.REWIND || progress.kind == ProgressIntentKind.UNCOMPLETE) ->
                    superseded += intent.operationId // the user listens again: keep it queued
                progress.intentCounter <= dependsOn && attempts("progress:$episodeId") < DEPENDENCY_ATTEMPTS ->
                    waiting += intent.operationId
            }
        }
        return QueueDependencies(waiting, superseded)
    }

    // --- Saved episodes and subscriptions (§7) ---

    private val savedDao = database.savedEpisodeDao()
    private val podcastDao = database.podcastDao()

    suspend fun pendingSaved(limit: Int = 100): List<ru.e1173.nookast.data.local.SavedEpisodeOutboxEntity> =
        savedDao.getReady(Long.MAX_VALUE, limit)

    suspend fun confirmSaved(episodeId: Int, sequence: Long): Boolean =
        savedDao.confirmIfCurrent(episodeId, sequence) == 1

    suspend fun pendingPodcastRemovals(limit: Int = 100): List<ru.e1173.nookast.data.local.PodcastOutboxEntity> =
        podcastDao.getReadyOutbox(Long.MAX_VALUE, limit)

    /**
     * A confirmed unsubscribe deleted the podcast's episodes on the server, and a new subscription
     * would get new IDs: intents for those episodes are extinguished with a log entry (§7, I2.4).
     */
    suspend fun confirmPodcastRemoval(operationId: String, podcastId: Int) = database.withTransaction {
        podcastDao.confirmOperation(operationId)
        val episodes = database.offlineCacheDao().getEpisodes(podcastId).mapNotNull { it.pinePodsEpisodeId }.toSet()
        if (episodes.isEmpty()) return@withTransaction
        val counter = next()
        episodes.forEach { id -> dao.progress(id)?.let { if (it.dirty) dao.confirmProgress(id, it.intentCounter, counter) } }
        val queueIntents = queueIntents().filter { intent ->
            when (intent) {
                is QueueIntent.Add -> intent.episodeId in episodes
                is QueueIntent.Remove -> intent.episodeId in episodes
                is QueueIntent.Move -> intent.episodeId in episodes
                is QueueIntent.Clear -> false
            }
        }
        if (queueIntents.isNotEmpty()) dao.deleteQueueIntents(queueIntents.map { it.operationId })
        episodes.forEach { id -> savedDao.get(id)?.let { savedDao.confirmIfCurrent(id, it.sequence) } }
        log("podcast.extinguished", "podcast=$podcastId episodes=${episodes.size}")
    }

    suspend fun dropPodcastRemoval(operationId: String) {
        podcastDao.confirmOperation(operationId)
    }

    /** Moves a saved/podcast intent to the dead letter and drops it from its outbox (§8.1). */
    suspend fun deadLetterOutboxRow(
        aggregate: String,
        key: String,
        counter: Long,
        category: SyncFailure,
        reason: String,
        remove: suspend () -> Unit,
    ) = database.withTransaction {
        dao.insertDeadLetter(
            ru.e1173.nookast.data.local.DeadLetterEntity(
                aggregate = aggregate.substringBefore(':'),
                aggregateKey = key,
                intentCounter = counter,
                payloadJson = "{}",
                category = category.name,
                reasonCode = reason,
                errorExcerpt = null,
                attempts = attempts(aggregate),
                accountFingerprint = null,
            ),
        )
        remove()
        dao.clearBackoff(aggregate)
        log("${aggregate.substringBefore(':')}.dead_letter", "key=$key reason=$reason")
    }

    suspend fun autoCompleteSeconds(): Int? = dao.serverSettings()?.autoCompleteSeconds

    suspend fun saveServerSettings(autoCompleteSeconds: Int?, version: String?) = database.withTransaction {
        dao.upsertServerSettings(
            ru.e1173.nookast.data.local.ServerSettingsEntity(
                autoCompleteSeconds = autoCompleteSeconds,
                serverVersion = version,
                observedCounter = next(),
            ),
        )
    }

    suspend fun deadLetterCount(): Int = dao.deadLetterRowCount()

    fun observeDeadLetters() = dao.observeDeadLetters()

    /** Intents that did not reach the server yet, in the units the user understands (§10.2). */
    data class PendingSummary(
        val progress: Int,
        val queue: Int,
        val saved: Int,
        val podcasts: Int,
        val deadLetters: Int,
    ) {
        val total: Int get() = progress + queue + saved + podcasts + deadLetters
    }

    suspend fun pendingSummary(): PendingSummary = PendingSummary(
        progress = dao.dirtyProgressCount(),
        queue = dao.queueIntentCount(),
        saved = pendingSaved(Int.MAX_VALUE).size,
        podcasts = pendingPodcastRemovals(Int.MAX_VALUE).size,
        deadLetters = dao.deadLetterRowCount(),
    )

    /**
     * Sends a dead-lettered progress intent again (§8.1, 3.5.9). A newer local intent already
     * supersedes it, so the row is then only removed. Other aggregates already recomputed their
     * local state when they were dead-lettered; for them only [discardDeadLetter] applies.
     */
    suspend fun retryDeadLetter(id: Long): Boolean = database.withTransaction {
        val letter = dao.deadLetter(id) ?: return@withTransaction false
        if (letter.aggregate != "progress") return@withTransaction false
        val episodeId = letter.aggregateKey.toIntOrNull() ?: return@withTransaction false
        val entity = dao.progress(episodeId)
        if (entity != null && !entity.dirty) {
            // "Retry" is the user's explicit choice: resend the intent as it was dead-lettered, with
            // its own position. The row itself adopted server observations meanwhile (review 3.6,
            // finding 6), so its current position is not the user's.
            val payload = runCatching { org.json.JSONObject(letter.payloadJson) }.getOrNull()
            val kind = payload?.optString("kind")?.takeIf { it in RETRYABLE_KINDS } ?: SyncV8.IntentKind.PASSIVE
            val positionMs = payload?.optLong("position_ms", entity.positionMs) ?: entity.positionMs
            val completed = payload?.optBoolean("completed", kind == SyncV8.IntentKind.COMPLETE)
                ?: (kind == SyncV8.IntentKind.COMPLETE)
            val counter = next()
            dao.upsertProgress(
                entity.copy(
                    positionMs = positionMs,
                    completed = completed,
                    dirty = true,
                    intentKind = kind,
                    intentCounter = counter,
                    rewindBaseCounter = if (kind == SyncV8.IntentKind.REWIND) counter else null,
                    rewindPhase = 0,
                    maxSentPositionMs = 0,
                ),
            )
        }
        dao.deleteDeadLetterById(id)
        dao.clearBackoff("progress:$episodeId")
        log("dead_letter.retry", "progress episode=$episodeId")
        true
    }

    /** The user gave up on an intent: the local state stays as it is, the server is not told. */
    suspend fun discardDeadLetter(id: Long) = database.withTransaction {
        val letter = dao.deadLetter(id) ?: return@withTransaction
        dao.deleteDeadLetterById(id)
        log("dead_letter.discard", "${letter.aggregate} key=${letter.aggregateKey}")
    }

    // --- Account (§10, I8) ---

    enum class AccountCheck { MATCH, ADOPTED, QUARANTINE }

    /**
     * Set while the database holds another account's data (§10.3). New progress and queue
     * intents are refused then: made under the new session, they would otherwise be delivered to
     * the previous account if the user goes back to it (review 3.6).
     */
    @Volatile
    var quarantined: Boolean = false
        private set

    /**
     * Whose data the database holds. The first session adopts it (including data migrated from v7,
     * which was always the signed-in account's); another account stops the engine until the user
     * decides (§10.3).
     */
    suspend fun checkAccount(fingerprint: String, origin: String, userId: Int): AccountCheck =
        database.withTransaction {
            val account = dao.account()
            when {
                account == null -> {
                    dao.upsertAccount(AccountEntity(fingerprint = fingerprint, serverOrigin = origin, userId = userId, username = ""))
                    log("account.adopted", "user=$userId")
                    AccountCheck.ADOPTED
                }
                account.fingerprint == fingerprint -> AccountCheck.MATCH
                else -> AccountCheck.QUARANTINE
            }.also { quarantined = it == AccountCheck.QUARANTINE }
        }

    suspend fun account(): AccountEntity? = dao.account()

    /** The local data now belongs to this account (same server at a new address, or wiped). */
    suspend fun adoptAccount(fingerprint: String, origin: String, userId: Int) = database.withTransaction {
        dao.upsertAccount(AccountEntity(fingerprint = fingerprint, serverOrigin = origin, userId = userId, username = ""))
        log("account.adopted", "user=$userId (confirmed)")
        quarantined = false
    }

    // --- Log ---

    suspend fun log(event: String, detail: String) {
        dao.insertLog(SyncLogEntity(counter = next(), elapsedMs = clock.elapsedRealtimeMs(), wallMs = clock.wallTimeMs(), event = event, detail = detail))
        dao.trimLog(LOG_SIZE)
    }

    private companion object {
        const val LOG_SIZE = 1_000
        const val DEPENDENCY_ATTEMPTS = 3
        val RETRYABLE_KINDS = setOf(SyncV8.IntentKind.PASSIVE, SyncV8.IntentKind.REWIND, SyncV8.IntentKind.COMPLETE, SyncV8.IntentKind.UNCOMPLETE)
    }
}

/** Canonical server queue (§6.2): positive IDs, stable order, first occurrence wins. */
object ServerQueueCanon {
    fun canonical(rows: List<ServerQueueRow>): List<Int> = rows
        .filter { it.episodeId > 0 }
        .sortedWith(compareBy<ServerQueueRow>({ it.position }, { it.queueDate }, { it.episodeId }))
        .map { it.episodeId }
        .distinct()

    fun hasTies(rows: List<ServerQueueRow>): Boolean =
        rows.groupBy { it.position }.any { it.value.size > 1 }

    fun fingerprint(ids: List<Int>): String = ids.joinToString(",")
}

// --- Mapping ---

internal fun PlaybackProgressEntity.toState() = ProgressState(
    episodeId = episodeId,
    positionMs = positionMs,
    durationMs = durationMs,
    completed = completed,
    dirty = dirty,
    kind = runCatching { ProgressIntentKind.valueOf(intentKind) }.getOrDefault(ProgressIntentKind.NONE),
    intentCounter = intentCounter,
    intentSession = intentSession,
    rewindBaseCounter = rewindBaseCounter,
    rewindPhase = rewindPhase,
    maxSentPositionMs = maxSentPositionMs,
    baseServerPositionSeconds = baseServerPositionSeconds,
    baseTicket = baseTicket,
    confirmedCounter = confirmedCounter,
    serverPositionSeconds = serverPositionSeconds,
    serverCompleted = serverCompleted,
    serverTicket = serverTicket,
    migrated = migrated,
)

internal fun ProgressState.toEntity(previous: PlaybackProgressEntity?, wallTimeMs: Long) = PlaybackProgressEntity(
    episodeId = episodeId,
    positionMs = positionMs,
    durationMs = durationMs,
    completed = completed,
    cause = when (kind) {
        ProgressIntentKind.PASSIVE -> ProgressCause.PERIODIC
        ProgressIntentKind.REWIND -> ProgressCause.SEEK
        ProgressIntentKind.COMPLETE -> ProgressCause.COMPLETE
        ProgressIntentKind.UNCOMPLETE -> ProgressCause.UNCOMPLETE
        ProgressIntentKind.NONE -> previous?.cause ?: ProgressCause.PERIODIC
    },
    localSequence = intentCounter.coerceAtLeast(previous?.localSequence ?: 0),
    updatedAtEpochMs = wallTimeMs,
    lastConfirmedSequence = previous?.lastConfirmedSequence,
    lastConfirmedAtEpochMs = previous?.lastConfirmedAtEpochMs,
    dirty = dirty,
    intentKind = kind.name,
    intentCounter = intentCounter,
    intentSession = intentSession,
    rewindBaseCounter = rewindBaseCounter,
    rewindPhase = rewindPhase,
    maxSentPositionMs = maxSentPositionMs,
    baseServerPositionSeconds = baseServerPositionSeconds,
    baseTicket = baseTicket,
    confirmedCounter = confirmedCounter,
    serverPositionSeconds = serverPositionSeconds,
    serverCompleted = serverCompleted,
    serverTicket = serverTicket,
    migrated = migrated,
    accountFingerprint = previous?.accountFingerprint,
)

internal fun QueueIntentEntity.toIntent(): QueueIntent {
    val anchor = when (anchorKind) {
        SyncV8.Anchor.FIRST -> QueueAnchor.FIRST
        SyncV8.Anchor.AFTER -> anchorEpisodeId?.let(QueueAnchor::after) ?: QueueAnchor.LAST
        SyncV8.Anchor.INDEX -> QueueAnchor.index(anchorIndex ?: Int.MAX_VALUE)
        else -> QueueAnchor.LAST
    }
    return when (kind) {
        SyncV8.QueueKind.ADD -> QueueIntent.Add(requireNotNull(episodeId), anchor, counter, operationId)
        SyncV8.QueueKind.REMOVE -> QueueIntent.Remove(requireNotNull(episodeId), counter, operationId)
        SyncV8.QueueKind.MOVE -> QueueIntent.Move(requireNotNull(episodeId), anchor, counter, operationId)
        else -> QueueIntent.Clear(
            clearSnapshotJson?.let { json -> JSONArray(json).let { array -> (0 until array.length()).map(array::getInt).toSet() } },
            counter,
            operationId,
        )
    }
}

internal fun QueueIntent.toEntity(): QueueIntentEntity {
    fun anchorFields(anchor: QueueAnchor) = Triple(anchor.kind.name, anchor.episodeId, anchor.index)
    return when (this) {
        is QueueIntent.Add -> anchorFields(anchor).let { (kind, id, index) ->
            QueueIntentEntity(0, counter, operationId, SyncV8.QueueKind.ADD, episodeId, kind, id, index, null, null, null, null, null)
        }
        is QueueIntent.Move -> anchorFields(anchor).let { (kind, id, index) ->
            QueueIntentEntity(0, counter, operationId, SyncV8.QueueKind.MOVE, episodeId, kind, id, index, null, null, null, null, null)
        }
        is QueueIntent.Remove ->
            QueueIntentEntity(0, counter, operationId, SyncV8.QueueKind.REMOVE, episodeId, null, null, null, null, null, null, null, null)
        is QueueIntent.Clear ->
            QueueIntentEntity(0, counter, operationId, SyncV8.QueueKind.CLEAR, null, null, null, null, snapshot?.let { JSONArray(it.toList()).toString() }, null, null, null, null)
    }
}

private fun List<Int>.toQueueItems(wallTimeMs: Long) =
    filter { it > 0 }.mapIndexed { index, id -> QueueItemEntity(id, index, wallTimeMs) }
