package ru.e1173.nookast.core.sync

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.e1173.nookast.auth.AuthSession

/** Outcome of one synchronization pass. */
data class SyncReport(
    val deliveredProgress: Int = 0,
    val deliveredQueue: Boolean = false,
    /** Set when the pass stopped on a failure that concerns the whole account (AUTH). */
    val stoppedBy: SyncFailure? = null,
    val failures: Map<String, SyncFailure> = emptyMap(),
    /** The database holds another account's data: nothing was pulled or sent (§10.3). */
    val quarantined: Boolean = false,
)

/**
 * Delivery core of the synchronization redesign (docs/SYNC.md §5, §6, §8, §9).
 *
 * Locks are per aggregate (`progress:<id>`, `queue`), never one engine-wide mutex (review F9).
 * Reads take no lock; their results carry tickets reserved before the request (§4).
 * Lifecycle, triggers and scheduling live in [SyncScheduler]; this class is one pass.
 */
class SyncEngine(
    private val repository: SyncRepository,
    private val api: PinePodsSyncApi,
    private val session: suspend () -> AuthSession?,
    /** The playback session the user is listening to now (§5.2, §5.5), or null. */
    private val activeSession: (episodeId: Int) -> String? = { null },
    private val random: Random = Random.Default,
) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    private fun lock(aggregate: String) = locks.getOrPut(aggregate) { Mutex() }

    /**
     * Only the account check (§10): run right after sign-in and at process start, so the very
     * first intent of a new session is already refused if the database is another account's
     * (review 3.6, finding 8). Returns true when quarantined.
     */
    suspend fun checkAccountNow(): Boolean = withContext(Dispatchers.IO) {
        val current = session() ?: return@withContext false
        val identity = AccountIdentity.of(current)
        repository.checkAccount(identity.fingerprint, identity.origin, identity.userId) ==
            SyncRepository.AccountCheck.QUARANTINE
    }

    /** Pull, deliver everything that is ready, report. Safe to call concurrently. */
    suspend fun syncNow(): SyncReport = withContext(Dispatchers.IO) {
        val current = session() ?: return@withContext SyncReport()
        val identity = AccountIdentity.of(current)
        if (repository.checkAccount(identity.fingerprint, identity.origin, identity.userId) ==
            SyncRepository.AccountCheck.QUARANTINE
        ) {
            return@withContext SyncReport(quarantined = true)
        }
        val failures = mutableMapOf<String, SyncFailure>()
        var delivered = 0
        try {
            refreshServerSettings(current)
            for (state in repository.dirtyProgress()) {
                val aggregate = "progress:${state.episodeId}"
                if (!repository.isReady(aggregate)) continue
                lock(aggregate).withLock {
                    val fresh = repository.progress(state.episodeId)?.takeIf { it.dirty } ?: return@withLock
                    try {
                        if (deliverProgress(current, fresh)) delivered++
                        repository.clearFailure(aggregate)
                    } catch (error: SyncCallException) {
                        val failure = handle(current, aggregate, error) { category, reason ->
                            repository.deadLetterProgress(fresh, category, reason, error.excerpt)
                        }
                        failures[aggregate] = failure
                    }
                }
            }
            val queueDelivered = if (repository.isReady(QUEUE)) {
                lock(QUEUE).withLock {
                    try {
                        deliverQueue(current).also { repository.clearFailure(QUEUE) }
                    } catch (error: SyncCallException) {
                        failures[QUEUE] = handle(current, QUEUE, error) { _, _ -> }
                        false
                    }
                }
            } else {
                false
            }
            deliverSaved(current, failures)
            deliverPodcastRemovals(current, failures)
            if (QUEUE !in failures) pullQueue(current)
            SyncReport(delivered, queueDelivered, null, failures)
        } catch (auth: AuthStop) {
            SyncReport(delivered, false, SyncFailure.AUTH, failures)
        }
    }

    // --- Progress (§5.4–§5.7) ---

    private suspend fun deliverProgress(session: AuthSession, state: ProgressState): Boolean {
        val acs = repository.autoCompleteSeconds() ?: 0
        return when (state.kind) {
            ProgressIntentKind.PASSIVE -> deliverPassive(session, state, acs)
            ProgressIntentKind.REWIND, ProgressIntentKind.UNCOMPLETE -> deliverRewind(session, state, acs)
            ProgressIntentKind.COMPLETE -> deliverComplete(session, state)
            ProgressIntentKind.NONE -> false
        }
    }

    private suspend fun deliverPassive(session: AuthSession, state: ProgressState, acs: Int): Boolean {
        val stale = state.intentSession == null || state.intentSession != activeSession(state.episodeId)
        if (stale) {
            // §5.5: a passive intent from a past session is checked first; if somebody rewound
            // after our knowledge, the old position must not push the server forward again.
            val ticket = repository.reserveTicket()
            val server = api.episode(session, state.episodeId) ?: throw deleted()
            repository.observeProgress(ticket, server)
            val base = state.baseServerPositionSeconds
            if (base != null && server.listenSeconds < base) {
                return repository.dropProgressIntent(state, "stale passive after a rewind elsewhere")
            }
            if (server.listenSeconds >= state.positionMs / 1_000 && !state.migrated) {
                return repository.dropProgressIntent(state, "server already ahead")
            }
        }
        repository.noteSent(state.episodeId, state.positionMs)
        api.recordListen(session, state.episodeId, state.positionMs / 1_000)
        val confirmed = repository.confirmProgress(
            state.episodeId,
            state.intentCounter,
            SyncRepository.ServerKnowledge.AtLeast(state.positionMs / 1_000),
        )
        if (inAutoCompleteWindow(state, acs)) observeAfterWrite(session, state.episodeId)
        return confirmed
    }

    private suspend fun deliverRewind(session: AuthSession, state: ProgressState, acs: Int): Boolean {
        if (state.kind == ProgressIntentKind.REWIND && inAutoCompleteWindow(state, acs)) {
            // §5.7: "replay the last minute" cannot be represented on the server.
            return repository.dropProgressIntent(state, "rewind inside the auto-complete window stays local")
        }
        var current = state
        if (current.rewindPhase == 0) {
            val ticket = repository.reserveTicket()
            val server = api.episode(session, current.episodeId) ?: throw deleted()
            repository.observeProgress(ticket, server)
            val base = current.baseServerPositionSeconds
            if (base != null && server.listenSeconds > base + AHEAD_TOLERANCE_S && !server.completed &&
                current.kind == ProgressIntentKind.REWIND
            ) {
                // §5.6: another device went further after our knowledge; do not roll it back.
                throw SyncCallException(SyncFailure.PERMANENT, excerpt = CONFLICT_AHEAD)
            }
            if (current.kind == ProgressIntentKind.REWIND && !server.completed &&
                current.positionMs / 1_000 >= server.listenSeconds
            ) {
                // Nothing to roll back on the server: a plain record is enough (§5.1, S49).
                repository.noteSent(current.episodeId, current.positionMs)
                api.recordListen(session, current.episodeId, current.positionMs / 1_000)
                return repository.confirmProgress(
                    current.episodeId,
                    current.intentCounter,
                    SyncRepository.ServerKnowledge.AtLeast(current.positionMs / 1_000),
                )
            }
            api.markUncompleted(session, current.episodeId)
            repository.advanceRewindPhase(current.episodeId, requireNotNull(current.rewindBaseCounter))
            current = repository.progress(current.episodeId) ?: return false
        }
        // Phase 1, in the same pass (§5.4): the window with 0 on the server stays minimal.
        if (current.positionMs > 0) {
            repository.noteSent(current.episodeId, current.positionMs)
            api.recordListen(session, current.episodeId, current.positionMs / 1_000)
        }
        val confirmed = repository.confirmProgress(
            current.episodeId,
            current.intentCounter,
            SyncRepository.ServerKnowledge.Exactly(current.positionMs / 1_000, completed = false),
        )
        observeAfterWrite(session, current.episodeId)
        return confirmed
    }

    private suspend fun deliverComplete(session: AuthSession, state: ProgressState): Boolean {
        api.markCompleted(session, state.episodeId)
        // mark_episode_completed answers 200 even for a deleted episode, and silently does nothing
        // when the episode has no duration (API.md §2): confirm only what the read shows (§5.4).
        val read = api.episode(session, state.episodeId) ?: throw deleted()
        if (!read.completed) throw SyncCallException(SyncFailure.PERMANENT, excerpt = COMPLETE_NOT_APPLIED)
        val confirmed = repository.confirmProgress(
            state.episodeId,
            state.intentCounter,
            SyncRepository.ServerKnowledge.AtLeast((state.durationMs ?: state.positionMs) / 1_000, completed = true),
        )
        observeAfterWrite(session, state.episodeId)
        return confirmed
    }

    private suspend fun observeAfterWrite(session: AuthSession, episodeId: Int) {
        val ticket = repository.reserveTicket()
        api.episode(session, episodeId)?.let { repository.observeProgress(ticket, it) }
    }

    private fun inAutoCompleteWindow(state: ProgressState, acs: Int): Boolean {
        val duration = state.durationMs ?: return false
        return acs > 0 && duration > 0 && (duration - state.positionMs) / 1_000 <= acs
    }

    // --- Queue (§6.4) ---

    private suspend fun deliverQueue(session: AuthSession): Boolean {
        val all = repository.queueIntents()
        if (all.isEmpty()) return false
        val kept = QueueCompaction.compact(all)
        repository.dropQueueIntents(all.filterNot { intent -> kept.any { it.operationId == intent.operationId } })

        val superseded = repository.queueDependencies().superseded
        repository.dropQueueIntents(kept.filter { it.operationId in superseded })

        repeat(MAX_QUEUE_ATTEMPTS) {
            // Re-read every attempt: an attempt may dead-letter intents (orphans, S53).
            val waiting = repository.queueDependencies().waiting
            val rows = api.queue(session)
            val server = ServerQueueCanon.canonical(rows)
            val ready = repository.queueIntents().filterNot { it.operationId in waiting }
            if (ready.isEmpty()) return false
            val desired = QueueRebase.rebase(server, ready).filter { it > 0 }.distinct()

            val added = desired.filterNot { it in server }
            val removed = server.filterNot { it in desired }
            added.forEach { api.queuePod(session, it) }
            removed.forEach { api.removeQueued(session, it) }
            val afterMembership = server.filterNot { it in removed } + added
            // Where the server puts an added episode is its own choice (PinePods 0.9.1+ puts it at the top,
            // "play next"; earlier versions at the end), so after any addition the whole order is sent.
            if (added.isNotEmpty() || afterMembership != desired || ServerQueueCanon.hasTies(rows)) api.reorder(session, desired)

            val verified = ServerQueueCanon.canonical(api.queue(session))
            if (verified == desired) {
                repository.confirmQueue(ready.map { it.operationId }, verified)
                return true
            }
            // Queued but invisible: the episode was deleted on the server (S53, orphan rows).
            val orphans = added.filter { it !in verified && api.episode(session, it) == null }
            if (orphans.isNotEmpty()) {
                orphans.forEach { api.removeQueued(session, it) }
                repository.deadLetterQueueIntents(
                    ready.filter { intent -> intent is QueueIntent.Add && intent.episodeId in orphans },
                    SyncFailure.PERMANENT,
                    EPISODE_DELETED,
                )
                return@repeat
            }
            // Otherwise another client edited the queue in between: retry on a fresh read.
        }
        throw SyncCallException(SyncFailure.TRANSIENT, excerpt = "queue changed concurrently")
    }

    // --- Saved episodes and subscriptions (§7) ---

    private suspend fun deliverSaved(session: AuthSession, failures: MutableMap<String, SyncFailure>) {
        for (item in repository.pendingSaved()) {
            val aggregate = "saved:${item.episodeId}"
            if (!repository.isReady(aggregate)) continue
            lock(aggregate).withLock {
                try {
                    if (item.desiredSaved) {
                        api.saveEpisode(session, item.episodeId)
                    } else {
                        api.removeSavedEpisode(session, item.episodeId)
                    }
                    repository.confirmSaved(item.episodeId, item.sequence)
                    repository.clearFailure(aggregate)
                } catch (error: SyncCallException) {
                    failures[aggregate] = handle(session, aggregate, error) { category, reason ->
                        repository.deadLetterOutboxRow(aggregate, item.episodeId.toString(), item.sequence, category, reason) {
                            repository.confirmSaved(item.episodeId, item.sequence)
                        }
                    }
                }
            }
        }
    }

    private suspend fun deliverPodcastRemovals(session: AuthSession, failures: MutableMap<String, SyncFailure>) {
        for (item in repository.pendingPodcastRemovals()) {
            val aggregate = "podcast:${item.podcastId}"
            if (!repository.isReady(aggregate)) continue
            lock(aggregate).withLock {
                try {
                    api.removePodcast(session, item.podcastId)
                    repository.confirmPodcastRemoval(item.operationId, item.podcastId)
                    repository.clearFailure(aggregate)
                } catch (error: SyncCallException) {
                    failures[aggregate] = handle(session, aggregate, error) { category, reason ->
                        repository.deadLetterOutboxRow(aggregate, item.operationId, item.sequence, category, reason) {
                            repository.dropPodcastRemoval(item.operationId)
                        }
                    }
                }
            }
        }
    }

    private suspend fun pullQueue(session: AuthSession) {
        try {
            val ticket = repository.reserveTicket()
            val rows = api.queue(session)
            repository.observeQueue(ticket, rows)
            rows.forEach { row ->
                repository.observeProgress(
                    ticket,
                    ServerEpisode(row.episodeId, row.listenSeconds ?: 0, row.completed, null, queued = true),
                )
            }
        } catch (error: SyncCallException) {
            if (error.failure == SyncFailure.AUTH) throw AuthStop()
        }
    }

    private suspend fun refreshServerSettings(session: AuthSession) {
        if (repository.autoCompleteSeconds() != null) return
        runCatching { api.autoCompleteSeconds(session) }.onSuccess { seconds ->
            repository.saveServerSettings(seconds, api.version(session))
        }.onFailure { error ->
            if (error is SyncCallException && error.failure == SyncFailure.AUTH) throw AuthStop()
        }
    }

    // --- Failures (§8) ---

    private suspend fun handle(
        session: AuthSession,
        aggregate: String,
        error: SyncCallException,
        deadLetter: suspend (SyncFailure, String) -> Unit,
    ): SyncFailure {
        val failure = when (error.failure) {
            SyncFailure.AUTH_SUSPECT -> if (confirmsAuthFailure(session)) SyncFailure.AUTH else SyncFailure.TRANSIENT
            else -> error.failure
        }
        when (failure) {
            SyncFailure.AUTH -> throw AuthStop()
            SyncFailure.PERMANENT -> {
                val conflict = error.excerpt == CONFLICT_AHEAD
                if (conflict || repository.attempts(aggregate) + 1 >= PERMANENT_ATTEMPTS) {
                    deadLetter(failure, if (conflict) CONFLICT_AHEAD else (error.excerpt ?: "permanent"))
                    repository.clearFailure(aggregate)
                } else {
                    repository.recordFailure(aggregate, failure, backoffMs(aggregate, null))
                }
            }
            SyncFailure.PROTOCOL -> {
                val paused = repository.attempts(aggregate) + 1 >= PERMANENT_ATTEMPTS
                repository.recordFailure(
                    aggregate,
                    failure,
                    backoffMs(aggregate, null),
                    pausedReason = if (paused) "protocol: ${error.excerpt}" else null,
                )
            }
            else -> repository.recordFailure(aggregate, failure, backoffMs(aggregate, error.retryAfterSeconds))
        }
        repository.log("failure", "$aggregate ${failure.name} ${error.httpCode ?: ""} ${error.excerpt.orEmpty()}")
        return failure
    }

    private suspend fun confirmsAuthFailure(session: AuthSession): Boolean = try {
        api.queue(session)
        false
    } catch (error: SyncCallException) {
        error.failure == SyncFailure.AUTH
    }

    private suspend fun backoffMs(aggregate: String, retryAfterSeconds: Long?): Long {
        retryAfterSeconds?.let { return it * 1_000 }
        val exponent = repository.attempts(aggregate).coerceIn(0, 6)
        val base = min(MAX_BACKOFF_MS, INITIAL_BACKOFF_MS shl exponent)
        val jitter = (base * 0.2 * (random.nextDouble() * 2 - 1)).toLong()
        return base + jitter
    }

    private fun deleted() = SyncCallException(SyncFailure.PERMANENT, 404, EPISODE_DELETED)

    private class AuthStop : Exception()

    companion object {
        const val QUEUE = "queue"
        const val CONFLICT_AHEAD = "CONFLICT_AHEAD"
        const val EPISODE_DELETED = "EPISODE_DELETED"
        const val COMPLETE_NOT_APPLIED = "COMPLETE_NOT_APPLIED"
        private const val MAX_QUEUE_ATTEMPTS = 3
        private const val PERMANENT_ATTEMPTS = 3
        private const val INITIAL_BACKOFF_MS = 5_000L
        private const val MAX_BACKOFF_MS = 5 * 60_000L
        private const val AHEAD_TOLERANCE_S = 15L
    }
}
