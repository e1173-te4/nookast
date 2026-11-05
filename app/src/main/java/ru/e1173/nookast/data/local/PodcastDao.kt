package ru.e1173.nookast.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PodcastDao {
    @Query("SELECT * FROM podcast_subscriptions")
    abstract fun observeSubscriptions(): Flow<List<PodcastSubscriptionEntity>>

    @Query("SELECT * FROM podcast_subscriptions")
    abstract suspend fun getSubscriptions(): List<PodcastSubscriptionEntity>

    @Query("SELECT * FROM podcast_subscriptions WHERE podcast_id = :podcastId LIMIT 1")
    abstract fun observeSubscription(podcastId: Int): Flow<PodcastSubscriptionEntity?>

    @Query("SELECT podcast_id FROM podcast_outbox WHERE kind = 'REMOVE'")
    abstract suspend fun pendingRemovalIds(): List<Int>

    @Query("DELETE FROM podcast_subscriptions")
    protected abstract suspend fun deleteAllSubscriptions()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertSubscriptions(items: List<PodcastSubscriptionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertSubscription(item: PodcastSubscriptionEntity)

    @Transaction
    open suspend fun replaceSubscriptions(items: List<PodcastSubscriptionEntity>) {
        val pendingRemovals = pendingRemovalIds().toSet()
        deleteAllSubscriptions()
        insertSubscriptions(items.filterNot { it.podcastId in pendingRemovals })
    }

    @Transaction
    open suspend fun upsertSubscriptionUnlessPendingRemoval(item: PodcastSubscriptionEntity) {
        if (item.podcastId !in pendingRemovalIds()) insertSubscription(item)
    }

    @Query("DELETE FROM podcast_subscriptions WHERE podcast_id = :podcastId")
    protected abstract suspend fun deleteSubscription(podcastId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertOutbox(item: PodcastOutboxEntity)

    @Transaction
    open suspend fun stageRemoval(item: PodcastOutboxEntity) {
        deleteSubscription(item.podcastId)
        upsertOutbox(item)
    }

    @Query(
        """
        SELECT * FROM podcast_outbox
        WHERE next_attempt_at_epoch_ms <= :nowEpochMs
        ORDER BY sequence ASC
        LIMIT :limit
        """,
    )
    abstract suspend fun getReadyOutbox(
        nowEpochMs: Long,
        limit: Int,
    ): List<PodcastOutboxEntity>

    @Query("DELETE FROM podcast_outbox WHERE operation_id = :operationId")
    abstract suspend fun confirmOperation(operationId: String): Int

    @Query(
        """
        UPDATE podcast_outbox
        SET attempt_count = attempt_count + 1,
            next_attempt_at_epoch_ms = :nextAttemptAtEpochMs,
            last_error_category = :category
        WHERE operation_id = :operationId
        """,
    )
    abstract suspend fun markAttemptFailed(
        operationId: String,
        nextAttemptAtEpochMs: Long,
        category: String,
    )

    @Query("SELECT COUNT(*) FROM podcast_outbox")
    abstract fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM podcast_outbox")
    abstract suspend fun pendingOutboxCount(): Int

    @Query(
        """
        UPDATE podcast_subscriptions
        SET episodes_played = COALESCE(episode_count, episodes_played)
        WHERE podcast_id = :podcastId
        """,
    )
    abstract suspend fun markAllPlayed(podcastId: Int)
}
