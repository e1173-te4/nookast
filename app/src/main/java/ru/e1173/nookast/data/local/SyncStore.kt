package ru.e1173.nookast.data.local

import androidx.room.withTransaction
import ru.e1173.nookast.core.sync.PlaybackSessions
import ru.e1173.nookast.core.sync.ProgressIntent
import ru.e1173.nookast.core.sync.QueueIntent
import ru.e1173.nookast.core.sync.SyncRepositories
import ru.e1173.nookast.core.sync.SyncRepository
import ru.e1173.nookast.core.sync.anchorForIndex
import ru.e1173.nookast.sync.ProgressCause
import ru.e1173.nookast.sync.ProgressMutation
import ru.e1173.nookast.sync.QueueMutation

/**
 * Entry point of UI and player writes. Since 3.5 every progress and queue intent goes to the
 * synchronization redesign ([SyncRepository], docs/SYNC.md); the signatures stay so callers keep
 * working. Sequences passed by callers are ignored for ordering: the v8 counter orders everything,
 * including deferred RSS and saved-episode intents, so all of them stay comparable.
 */
class SyncStore(private val database: SyncDatabase) {
    val progressDao: ProgressDao = database.progressDao()
    val queueDao: QueueDao = database.queueDao()
    val podcastDao: PodcastDao = database.podcastDao()
    val dashboardDao: DashboardDao = database.dashboardDao()
    val savedEpisodeDao: SavedEpisodeDao = database.savedEpisodeDao()
    val deferredEpisodeIntentDao: DeferredEpisodeIntentDao = database.deferredEpisodeIntentDao()
    val v8: SyncRepository = SyncRepositories.of(database)

    suspend fun recordProgress(mutation: ProgressMutation): Boolean = recordProgressV8(mutation)

    suspend fun recordCompletionAndRemoveFromQueue(
        progress: ProgressMutation,
        removal: QueueMutation.Remove,
        createdAtEpochMs: Long,
        completedContext: CurrentPlaybackContextEntity? = null,
    ) = database.withTransaction {
        require(progress.cause == ProgressCause.COMPLETE)
        require(progress.episodeId == removal.episodeId)
        v8.complete(progress.episodeId, progress.durationMs, PlaybackSessions.current, removeFromQueue = true)
        completedContext?.let { database.offlineCacheDao().upsertCurrentPlaybackContext(it) }
    }

    suspend fun recordPodcastCompletion(
        podcastId: Int,
        progress: List<ProgressMutation>,
        removals: List<QueueMutation.Remove>,
        createdAtEpochMs: Long,
    ) = database.withTransaction {
        require(podcastId > 0)
        require(progress.all { it.cause == ProgressCause.COMPLETE })
        val removed = removals.map { it.episodeId }.toSet()
        progress.forEach { mutation ->
            v8.complete(mutation.episodeId, mutation.durationMs, null, removeFromQueue = mutation.episodeId in removed)
        }
        podcastDao.markAllPlayed(podcastId)
    }

    // Every outbox below refuses writes while the database is quarantined (§10.3, review 3.6):
    // made under another session, they would reach the previous account.

    suspend fun stagePodcastRemoval(item: PodcastOutboxEntity) {
        if (v8.quarantined) return
        podcastDao.stageRemoval(item.copy(sequence = v8.nextCounter()))
    }

    suspend fun stageSavedEpisode(item: SavedEpisodeOutboxEntity): Boolean =
        database.withTransaction {
            if (v8.quarantined) return@withTransaction false
            savedEpisodeDao.upsert(item.copy(sequence = v8.nextCounter()))
            true
        }

    suspend fun stageDeferredProgress(
        episodeCacheKey: String,
        positionMs: Long,
        durationMs: Long?,
        cause: ProgressCause,
        sequence: Long,
        createdAtEpochMs: Long,
    ): Boolean = !v8.quarantined && deferredEpisodeIntentDao.stage(
        DeferredEpisodeIntentEntity(
            episodeCacheKey = episodeCacheKey,
            kind = DeferredEpisodeIntentKind.PROGRESS,
            positionMs = positionMs,
            durationMs = durationMs,
            progressCause = cause,
            sequence = v8.nextCounter(),
            createdAtEpochMs = createdAtEpochMs,
        ),
    )

    suspend fun stageDeferredQueue(
        episodeCacheKey: String,
        desiredQueued: Boolean,
        destinationIndex: Int?,
        sequence: Long,
        createdAtEpochMs: Long,
    ): Boolean = !v8.quarantined && deferredEpisodeIntentDao.stage(
        DeferredEpisodeIntentEntity(
            episodeCacheKey = episodeCacheKey,
            kind = DeferredEpisodeIntentKind.QUEUE,
            desiredState = desiredQueued,
            destinationIndex = destinationIndex,
            sequence = v8.nextCounter(),
            createdAtEpochMs = createdAtEpochMs,
        ),
    )

    suspend fun stageDeferredSaved(
        episodeCacheKey: String,
        desiredSaved: Boolean,
        sequence: Long,
        createdAtEpochMs: Long,
    ): Boolean = !v8.quarantined && deferredEpisodeIntentDao.stage(
        DeferredEpisodeIntentEntity(
            episodeCacheKey = episodeCacheKey,
            kind = DeferredEpisodeIntentKind.SAVED,
            desiredState = desiredSaved,
            sequence = v8.nextCounter(),
            createdAtEpochMs = createdAtEpochMs,
        ),
    )

    suspend fun stageDeferredCompletionAndQueueRemoval(
        episodeCacheKey: String,
        positionMs: Long,
        durationMs: Long?,
        progressSequence: Long,
        queueSequence: Long,
        createdAtEpochMs: Long,
    ) = database.withTransaction {
        stageDeferredProgress(episodeCacheKey, positionMs, durationMs, ProgressCause.COMPLETE, progressSequence, createdAtEpochMs)
        stageDeferredQueue(episodeCacheKey, false, null, queueSequence, createdAtEpochMs)
    }

    /**
     * Turns deferred RSS-only intents into ordinary intents once PinePods knows the episode
     * (docs/SYNC.md §11). Idempotent; intents are applied in counter order.
     * Returns [PROMOTION_REFUSED] while the database is quarantined: the intents would be refused
     * and their deferred rows deleted, so nothing is touched (review 3.6, finding 4).
     */
    suspend fun promoteDeferredEpisodeIntents(
        resolvedEpisodeIds: Map<String, Int>,
    ): Int = database.withTransaction {
        if (v8.quarantined) return@withTransaction PROMOTION_REFUSED
        var promoted = 0
        resolvedEpisodeIds.forEach { (cacheKey, episodeId) ->
            require(episodeId > 0)
            deferredEpisodeIntentDao.getForEpisode(cacheKey).sortedBy { it.sequence }.forEach intentLoop@{ intent ->
                when (intent.kind) {
                    DeferredEpisodeIntentKind.PROGRESS -> recordProgressV8(
                        ProgressMutation(
                            episodeId = episodeId,
                            positionMs = requireNotNull(intent.positionMs),
                            durationMs = intent.durationMs,
                            cause = requireNotNull(intent.progressCause),
                            sequence = intent.sequence,
                            recordedAtEpochMs = intent.createdAtEpochMs,
                        ),
                    )
                    DeferredEpisodeIntentKind.QUEUE -> {
                        val superseded = v8.latestQueueIntentCounter(episodeId)?.let { it >= intent.sequence } ?: false
                        if (!superseded) {
                            if (intent.desiredState == true) {
                                val cached = database.offlineCacheDao().findEpisodeByPinePodsId(episodeId)
                                    ?: return@intentLoop
                                queueDao.upsertEpisodeCache(listOf(cached.toQueueEpisodeEntity()))
                                if (episodeId !in v8.localQueue()) {
                                    enqueueV8(QueueMutation.Add(episodeId, intent.destinationIndex, "", intent.sequence))
                                }
                            } else {
                                enqueueV8(QueueMutation.Remove(episodeId, "", intent.sequence))
                            }
                        }
                    }
                    DeferredEpisodeIntentKind.SAVED -> {
                        // Keep the deferred intent's own counter: a newer addressable choice wins.
                        val current = savedEpisodeDao.get(episodeId)
                        if (current == null || current.sequence < intent.sequence) {
                            savedEpisodeDao.upsert(
                                SavedEpisodeOutboxEntity(
                                    episodeId = episodeId,
                                    desiredSaved = requireNotNull(intent.desiredState),
                                    sequence = intent.sequence,
                                    createdAtEpochMs = intent.createdAtEpochMs,
                                ),
                            )
                        }
                    }
                }
                if (deferredEpisodeIntentDao.deleteIfCurrent(intent.episodeCacheKey, intent.kind, intent.sequence) == 1) {
                    promoted += 1
                }
            }
        }
        promoted
    }

    suspend fun enqueueQueueMutation(
        mutation: QueueMutation,
        createdAtEpochMs: Long,
    ): Boolean = enqueueV8(mutation)

    /**
     * Makes an episode visible in the local queue together with its metadata in one transaction.
     * The duplicate check is inside the transaction so rapid double taps cannot create multiple
     * intents or move an episode already in Up Next.
     */
    suspend fun cacheEpisodeAndEnqueueIfMissing(
        episode: QueueEpisodeEntity,
        mutation: QueueMutation.Add,
        createdAtEpochMs: Long,
    ): Boolean = database.withTransaction {
        require(episode.episodeId == mutation.episodeId)
        queueDao.upsertEpisodeCache(listOf(episode))
        if (episode.episodeId in v8.localQueue()) return@withTransaction false
        enqueueV8(mutation)
    }

    /** A played episode replayed from the beginning becomes unplayed at [positionMs] (§5.13). */
    suspend fun recordReplay(episodeId: Int, positionMs: Long, durationMs: Long?): Boolean =
        v8.recordProgress(episodeId, ProgressIntent.Rewind(positionMs, durationMs, PlaybackSessions.current ?: DETACHED))

    private suspend fun recordProgressV8(mutation: ProgressMutation): Boolean {
        val session = PlaybackSessions.current
        val intent = when (mutation.cause) {
            ProgressCause.COMPLETE -> {
                v8.complete(mutation.episodeId, mutation.durationMs, session, removeFromQueue = false)
                return true
            }
            ProgressCause.UNCOMPLETE -> ProgressIntent.Uncomplete
            ProgressCause.SEEK -> {
                val current = v8.progress(mutation.episodeId)?.positionMs ?: 0
                if (mutation.positionMs < current) {
                    ProgressIntent.Rewind(mutation.positionMs, mutation.durationMs, session ?: DETACHED)
                } else {
                    ProgressIntent.Passive(mutation.positionMs, mutation.durationMs, session ?: DETACHED)
                }
            }
            ProgressCause.PERIODIC, ProgressCause.PAUSE, ProgressCause.STOP ->
                ProgressIntent.Passive(mutation.positionMs, mutation.durationMs, session ?: DETACHED)
        }
        return v8.recordProgress(mutation.episodeId, intent)
    }

    private suspend fun enqueueV8(mutation: QueueMutation): Boolean = v8.addQueueIntent { counter, operationId, local ->
        when (mutation) {
            is QueueMutation.Add ->
                QueueIntent.Add(mutation.episodeId, anchorForIndex(local - mutation.episodeId, mutation.destinationIndex), counter, operationId)
            is QueueMutation.Remove -> QueueIntent.Remove(mutation.episodeId, counter, operationId)
            is QueueMutation.Move ->
                QueueIntent.Move(mutation.episodeId, anchorForIndex(local - mutation.episodeId, mutation.destinationIndex), counter, operationId)
            is QueueMutation.Clear -> QueueIntent.Clear(local.toSet(), counter, operationId)
        }
    }

    private companion object {
        /** Session of a write that is not tied to engaged playback; the engine checks it first. */
        const val DETACHED = "detached"
    }
}

private fun PodcastEpisodeCacheEntity.toQueueEpisodeEntity(): QueueEpisodeEntity =
    QueueEpisodeEntity(
        episodeId = requireNotNull(pinePodsEpisodeId),
        episodeTitle = title,
        podcastName = podcastName,
        artworkUrl = artworkUrl,
        mediaUrl = mediaUrl,
        durationSeconds = durationSeconds,
        listenDurationSeconds = listenedSeconds,
        completed = completed,
        description = description,
        publishedAt = publishedAt,
        downloaded = downloaded,
        refreshedAtEpochMs = refreshedAtEpochMs,
    )

/** [SyncStore.promoteDeferredEpisodeIntents] did nothing because the database is quarantined. */
internal const val PROMOTION_REFUSED = -1
