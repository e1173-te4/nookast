package ru.e1173.nookast.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Queue rows joined with their cached episode metadata, in queue order. */
private const val QUEUE_ENTRIES_SQL = """
        SELECT queue_items.episode_id AS episode_id,
               queue_items.position AS queue_position,
               queue_episode_cache.episode_title AS episode_title,
               queue_episode_cache.podcast_name AS podcast_name,
               queue_episode_cache.artwork_url AS artwork_url,
               queue_episode_cache.media_url AS media_url,
               queue_episode_cache.duration_seconds AS duration_seconds,
               queue_episode_cache.listen_duration_seconds AS listen_duration_seconds,
               queue_episode_cache.completed AS completed,
               queue_episode_cache.description AS description,
               queue_episode_cache.published_at AS published_at,
               queue_episode_cache.downloaded AS downloaded,
               queue_episode_cache.refreshed_at_epoch_ms AS refreshed_at_epoch_ms
        FROM queue_items
        LEFT JOIN queue_episode_cache
            ON queue_episode_cache.episode_id = queue_items.episode_id
        ORDER BY queue_items.position
        """

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue_items ORDER BY position")
    fun observeQueue(): Flow<List<QueueItemEntity>>

    @Query(QUEUE_ENTRIES_SQL)
    fun observeQueueEntries(): Flow<List<QueueListEntry>>

    @Query(QUEUE_ENTRIES_SQL)
    suspend fun getQueueEntries(): List<QueueListEntry>

    @Query("SELECT * FROM queue_items ORDER BY position")
    suspend fun getQueue(): List<QueueItemEntity>

    @Query("DELETE FROM queue_items")
    suspend fun deleteQueueItems()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(items: List<QueueItemEntity>)

    @Upsert
    suspend fun upsertEpisodeCache(items: List<QueueEpisodeEntity>)

    @Transaction
    suspend fun replaceQueue(items: List<QueueItemEntity>) {
        deleteQueueItems()
        if (items.isNotEmpty()) insertQueueItems(items)
    }

    @Upsert
    suspend fun upsertQueueState(state: QueueStateEntity)

    @Query("SELECT * FROM queue_state WHERE singleton_id = 1")
    suspend fun getQueueState(): QueueStateEntity?

    @Query("SELECT * FROM queue_state WHERE singleton_id = 1")
    fun observeQueueState(): Flow<QueueStateEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOutbox(entity: QueueOutboxEntity): Long

    @Query("SELECT * FROM queue_outbox ORDER BY sequence, operation_id")
    suspend fun getAllOutbox(): List<QueueOutboxEntity>

    @Query("SELECT MAX(sequence) FROM queue_outbox WHERE episode_id = :episodeId")
    suspend fun latestOutboxSequence(episodeId: Int): Long?

    @Query(
        """
        SELECT * FROM queue_outbox
        WHERE next_attempt_at_epoch_ms <= :nowEpochMs
        ORDER BY sequence, operation_id
        LIMIT :limit
        """,
    )
    suspend fun getReadyOutbox(nowEpochMs: Long, limit: Int): List<QueueOutboxEntity>

    @Query("SELECT COUNT(*) FROM queue_outbox")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM queue_outbox")
    suspend fun pendingCount(): Int

    @Query("DELETE FROM queue_outbox WHERE operation_id = :operationId")
    suspend fun confirmOperation(operationId: String): Int

    @Query(
        """
        UPDATE queue_outbox
        SET attempt_count = attempt_count + 1,
            next_attempt_at_epoch_ms = :nextAttemptAtEpochMs,
            last_error_category = :errorCategory
        WHERE operation_id = :operationId
        """,
    )
    suspend fun markAttemptFailed(
        operationId: String,
        nextAttemptAtEpochMs: Long,
        errorCategory: String,
    ): Int
}
