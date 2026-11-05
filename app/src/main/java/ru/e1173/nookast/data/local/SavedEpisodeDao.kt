package ru.e1173.nookast.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedEpisodeDao {
    @Query("SELECT * FROM saved_episode_outbox ORDER BY sequence ASC")
    fun observeAll(): Flow<List<SavedEpisodeOutboxEntity>>

    @Upsert
    suspend fun upsert(item: SavedEpisodeOutboxEntity)

    @Query("SELECT * FROM saved_episode_outbox WHERE episode_id = :episodeId LIMIT 1")
    suspend fun get(episodeId: Int): SavedEpisodeOutboxEntity?

    @Query(
        """
        SELECT * FROM saved_episode_outbox
        WHERE next_attempt_at_epoch_ms <= :nowEpochMs
        ORDER BY sequence ASC
        LIMIT :limit
        """,
    )
    suspend fun getReady(nowEpochMs: Long, limit: Int): List<SavedEpisodeOutboxEntity>

    @Query(
        """
        DELETE FROM saved_episode_outbox
        WHERE episode_id = :episodeId AND sequence = :sequence
        """,
    )
    suspend fun confirmIfCurrent(episodeId: Int, sequence: Long): Int

    @Query(
        """
        UPDATE saved_episode_outbox
        SET attempt_count = attempt_count + 1,
            next_attempt_at_epoch_ms = :nextAttemptAtEpochMs,
            last_error_category = :category
        WHERE episode_id = :episodeId AND sequence = :sequence
        """,
    )
    suspend fun markAttemptFailed(
        episodeId: Int,
        sequence: Long,
        nextAttemptAtEpochMs: Long,
        category: String,
    )

    @Query("SELECT COUNT(*) FROM saved_episode_outbox")
    suspend fun pendingCount(): Int
}
