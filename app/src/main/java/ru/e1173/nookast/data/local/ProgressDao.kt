package ru.e1173.nookast.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Query("SELECT * FROM playback_progress WHERE episode_id = :episodeId")
    suspend fun getProgress(episodeId: Int): PlaybackProgressEntity?

    /** Two columns instead of whole rows (N-07): the podcast screen sorts by last progress. */
    @Query("SELECT episode_id AS episodeId, updated_at_epoch_ms AS updatedAtEpochMs FROM playback_progress")
    fun observeProgressUpdateTimes(): Flow<List<ProgressUpdateTime>>

    @Upsert
    suspend fun upsertProgress(entity: PlaybackProgressEntity)

    @Upsert
    suspend fun upsertOutbox(entity: ProgressOutboxEntity)

    @Query(
        """
        SELECT * FROM progress_outbox
        WHERE next_attempt_at_epoch_ms <= :nowEpochMs
        ORDER BY recorded_at_epoch_ms, episode_id
        LIMIT :limit
        """,
    )
    suspend fun getReadyOutbox(nowEpochMs: Long, limit: Int): List<ProgressOutboxEntity>

    @Query("SELECT * FROM progress_outbox WHERE episode_id = :episodeId")
    suspend fun getOutbox(episodeId: Int): ProgressOutboxEntity?

    @Query("SELECT COUNT(*) FROM progress_outbox")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM progress_outbox")
    fun observeAllOutbox(): Flow<List<ProgressOutboxEntity>>

    @Query("SELECT COUNT(*) FROM progress_outbox")
    suspend fun pendingCount(): Int

    @Query(
        """
        UPDATE progress_outbox
        SET attempt_count = attempt_count + 1,
            next_attempt_at_epoch_ms = :nextAttemptAtEpochMs,
            last_error_category = :errorCategory
        WHERE episode_id = :episodeId AND local_sequence = :localSequence
        """,
    )
    suspend fun markAttemptFailed(
        episodeId: Int,
        localSequence: Long,
        nextAttemptAtEpochMs: Long,
        errorCategory: String,
    ): Int

    @Query(
        """
        DELETE FROM progress_outbox
        WHERE episode_id = :episodeId AND local_sequence = :localSequence
        """,
    )
    suspend fun deleteOutboxIfSequence(episodeId: Int, localSequence: Long): Int

    @Query(
        """
        UPDATE playback_progress
        SET last_confirmed_sequence = :localSequence,
            last_confirmed_at_epoch_ms = :confirmedAtEpochMs
        WHERE episode_id = :episodeId AND local_sequence = :localSequence
        """,
    )
    suspend fun markProgressConfirmed(
        episodeId: Int,
        localSequence: Long,
        confirmedAtEpochMs: Long,
    ): Int

    @Transaction
    suspend fun confirmIfCurrent(
        episodeId: Int,
        localSequence: Long,
        confirmedAtEpochMs: Long,
    ): Boolean {
        val deleted = deleteOutboxIfSequence(episodeId, localSequence) == 1
        if (deleted) {
            markProgressConfirmed(episodeId, localSequence, confirmedAtEpochMs)
        }
        return deleted
    }
}

data class ProgressUpdateTime(val episodeId: Int, val updatedAtEpochMs: Long)
