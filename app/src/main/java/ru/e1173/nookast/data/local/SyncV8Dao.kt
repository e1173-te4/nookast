package ru.e1173.nookast.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Queries of the synchronization redesign (docs/SYNC.md §3). Callers own the transactions. */
@Dao
interface SyncV8Dao {
    // --- Counter (§4) ---

    @Query("INSERT OR IGNORE INTO sync_counter (singleton_id, value, next_rss_playback_id) VALUES (1, 0, -1)")
    suspend fun ensureCounter()

    @Query("UPDATE sync_counter SET value = value + 1 WHERE singleton_id = 1")
    suspend fun incrementCounter()

    @Query("SELECT value FROM sync_counter WHERE singleton_id = 1")
    suspend fun counter(): Long

    // --- Progress (§3.2, §5) ---

    @Query("SELECT * FROM playback_progress WHERE episode_id = :episodeId")
    suspend fun progress(episodeId: Int): PlaybackProgressEntity?

    @Upsert
    suspend fun upsertProgress(entity: PlaybackProgressEntity)

    @Query("SELECT * FROM playback_progress WHERE dirty = 1 ORDER BY intent_counter LIMIT :limit")
    suspend fun dirtyProgress(limit: Int): List<PlaybackProgressEntity>

    @Query("SELECT COUNT(*) FROM playback_progress WHERE dirty = 1")
    fun observeDirtyProgressCount(): Flow<Int>

    @Query("SELECT * FROM playback_progress WHERE dirty = 1")
    fun observeDirtyProgress(): Flow<List<PlaybackProgressEntity>>

    /** Conditional confirmation (§5.3): only if no newer intent arrived during the request. */
    @Query(
        """
        UPDATE playback_progress
        SET dirty = 0, intent_kind = 'NONE', rewind_phase = 0, rewind_base_counter = NULL,
            confirmed_counter = :confirmedCounter, migrated = 0
        WHERE episode_id = :episodeId AND intent_counter = :sentCounter
        """,
    )
    suspend fun confirmProgress(episodeId: Int, sentCounter: Long, confirmedCounter: Long): Int

    /** Phase 0 → 1 of a rewind, keyed by the rewind's base, so ticks do not stall it (§5.3, F1). */
    @Query(
        """
        UPDATE playback_progress SET rewind_phase = 1
        WHERE episode_id = :episodeId AND rewind_base_counter = :baseCounter AND rewind_phase = 0
        """,
    )
    suspend fun advanceRewindPhase(episodeId: Int, baseCounter: Long): Int

    @Query(
        """
        UPDATE playback_progress SET max_sent_position_ms = MAX(max_sent_position_ms, :positionMs)
        WHERE episode_id = :episodeId
        """,
    )
    suspend fun noteSent(episodeId: Int, positionMs: Long)

    // --- Queue (§3.3, §6) ---

    @Query("SELECT * FROM queue_intents ORDER BY counter, operation_id, id")
    suspend fun queueIntents(): List<QueueIntentEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQueueIntent(entity: QueueIntentEntity): Long

    @Query("DELETE FROM queue_intents WHERE operation_id IN (:operationIds)")
    suspend fun deleteQueueIntents(operationIds: List<String>)

    @Query("SELECT COUNT(*) FROM queue_intents")
    fun observeQueueIntentCount(): Flow<Int>

    @Query("SELECT * FROM server_queue ORDER BY position")
    suspend fun serverQueue(): List<ServerQueueEntity>

    @Query("DELETE FROM server_queue")
    suspend fun clearServerQueue()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServerQueue(rows: List<ServerQueueEntity>)

    // --- Backoff, dead letter, log (§8, §14) ---

    @Query("SELECT * FROM sync_backoff WHERE aggregate = :aggregate")
    suspend fun backoff(aggregate: String): SyncBackoffEntity?

    @Upsert
    suspend fun upsertBackoff(entity: SyncBackoffEntity)

    @Query("DELETE FROM sync_backoff WHERE aggregate = :aggregate")
    suspend fun clearBackoff(aggregate: String)

    @Query("DELETE FROM sync_backoff")
    suspend fun clearAllBackoff()

    @Query("DELETE FROM dead_letter WHERE aggregate = :aggregate AND aggregate_key = :key")
    suspend fun deleteDeadLetter(aggregate: String, key: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeadLetter(entity: DeadLetterEntity)

    @Query("SELECT * FROM dead_letter ORDER BY intent_counter")
    suspend fun deadLetters(): List<DeadLetterEntity>

    @Query("SELECT * FROM dead_letter ORDER BY intent_counter")
    fun observeDeadLetters(): Flow<List<DeadLetterEntity>>

    @Query("SELECT * FROM dead_letter WHERE id = :id")
    suspend fun deadLetter(id: Long): DeadLetterEntity?

    @Query("DELETE FROM dead_letter WHERE id = :id")
    suspend fun deleteDeadLetterById(id: Long)

    // --- Account (§10, I8) ---

    @Query("SELECT * FROM account WHERE singleton_id = 1")
    suspend fun account(): AccountEntity?

    @Upsert
    suspend fun upsertAccount(entity: AccountEntity)

    @Query("SELECT COUNT(*) FROM playback_progress WHERE dirty = 1")
    suspend fun dirtyProgressCount(): Int

    @Query("SELECT COUNT(*) FROM queue_intents")
    suspend fun queueIntentCount(): Int

    @Query("SELECT COUNT(*) FROM dead_letter")
    suspend fun deadLetterRowCount(): Int

    @Insert
    suspend fun insertLog(entity: SyncLogEntity)

    @Query("DELETE FROM sync_log WHERE id NOT IN (SELECT id FROM sync_log ORDER BY id DESC LIMIT :keep)")
    suspend fun trimLog(keep: Int)

    @Query("SELECT * FROM sync_log ORDER BY id DESC LIMIT :limit")
    suspend fun recentLog(limit: Int): List<SyncLogEntity>

    // --- Server settings ---

    @Query("SELECT * FROM server_settings WHERE singleton_id = 1")
    suspend fun serverSettings(): ServerSettingsEntity?

    @Upsert
    suspend fun upsertServerSettings(entity: ServerSettingsEntity)
}
