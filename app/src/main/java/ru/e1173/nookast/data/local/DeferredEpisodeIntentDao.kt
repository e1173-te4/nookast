package ru.e1173.nookast.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
abstract class DeferredEpisodeIntentDao {
    @Query(
        """
        SELECT * FROM deferred_episode_intents
        WHERE episode_cache_key = :episodeCacheKey
        ORDER BY sequence, kind
        """,
    )
    abstract suspend fun getForEpisode(episodeCacheKey: String): List<DeferredEpisodeIntentEntity>

    @Query(
        """
        SELECT * FROM deferred_episode_intents
        WHERE episode_cache_key = :episodeCacheKey AND kind = :kind
        LIMIT 1
        """,
    )
    protected abstract suspend fun get(
        episodeCacheKey: String,
        kind: DeferredEpisodeIntentKind,
    ): DeferredEpisodeIntentEntity?

    @Upsert
    protected abstract suspend fun upsert(item: DeferredEpisodeIntentEntity)

    @Transaction
    open suspend fun stage(item: DeferredEpisodeIntentEntity): Boolean {
        val current = get(item.episodeCacheKey, item.kind)
        if (current != null && current.sequence >= item.sequence) return false
        upsert(item)
        return true
    }

    @Query(
        """
        DELETE FROM deferred_episode_intents
        WHERE episode_cache_key = :episodeCacheKey
          AND kind = :kind
          AND sequence = :sequence
        """,
    )
    abstract suspend fun deleteIfCurrent(
        episodeCacheKey: String,
        kind: DeferredEpisodeIntentKind,
        sequence: Long,
    ): Int

    @Query("SELECT COUNT(*) FROM deferred_episode_intents")
    abstract suspend fun pendingCount(): Int

    /** Every waiting intent in the order it was made, for the transfer to the release package (plan 8.2). */
    @Query("SELECT * FROM deferred_episode_intents ORDER BY sequence, kind")
    abstract suspend fun all(): List<DeferredEpisodeIntentEntity>

    /** Adds a transferred intent; one already here for the episode wins (it is the newer intent). */
    @Transaction
    open suspend fun insertIfAbsent(item: DeferredEpisodeIntentEntity): Boolean {
        if (get(item.episodeCacheKey, item.kind) != null) return false
        upsert(item)
        return true
    }
}
