package ru.e1173.nookast.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class OfflineCacheDao {
    // Freshness pills (user, 5.11.2026): the newest cached episode of each podcast and the outcome of
    // each feed's last read. Dates are ISO-8601, so the greatest string is the newest.
    @Query(
        "SELECT podcast_id AS podcastId, MAX(published_at) AS latestPublishedAt FROM podcast_episode_cache " +
            "WHERE published_at != '' GROUP BY podcast_id",
    )
    abstract fun observeLatestPublished(): Flow<List<LatestPublishedRow>>

    @Query("SELECT * FROM podcast_refresh_state WHERE source_key LIKE 'rss:podcast:%'")
    abstract fun observeRssRefreshStates(): Flow<List<PodcastRefreshStateEntity>>

    @Query(
        """
        SELECT * FROM podcast_episode_cache
        WHERE podcast_id = :podcastId
        ORDER BY published_at DESC, cache_key ASC
        """,
    )
    abstract fun observeEpisodes(podcastId: Int): Flow<List<PodcastEpisodeCacheEntity>>

    @Query(
        """
        SELECT * FROM podcast_episode_cache
        WHERE pinepods_episode_id = :episodeId
        LIMIT 1
        """,
    )
    abstract suspend fun findEpisodeByPinePodsId(episodeId: Int): PodcastEpisodeCacheEntity?

    @Query(
        """
        SELECT podcast_id FROM podcast_episode_cache
        WHERE pinepods_episode_id = :episodeId
        LIMIT 1
        """,
    )
    abstract fun observePodcastIdForEpisode(episodeId: Int): Flow<Int?>

    @Query("SELECT * FROM podcast_episode_cache WHERE cache_key = :cacheKey LIMIT 1")
    abstract suspend fun findEpisodeByCacheKey(cacheKey: String): PodcastEpisodeCacheEntity?

    @Query("SELECT * FROM podcast_episode_cache WHERE pinepods_episode_id IS NULL")
    abstract suspend fun getRssOnlyEpisodes(): List<PodcastEpisodeCacheEntity>

    @Query(
        """
        SELECT * FROM podcast_episode_cache
        WHERE media_url = :mediaUrl
        ORDER BY CASE WHEN pinepods_episode_id IS NULL THEN 1 ELSE 0 END
        LIMIT 1
        """,
    )
    abstract suspend fun findEpisodeByMediaUrl(mediaUrl: String): PodcastEpisodeCacheEntity?

    @Query("SELECT * FROM podcast_episode_cache WHERE podcast_id = :podcastId")
    abstract suspend fun getEpisodes(podcastId: Int): List<PodcastEpisodeCacheEntity>

    @Query("SELECT * FROM podcast_episode_cache WHERE pinepods_episode_id IN (:episodeIds)")
    abstract suspend fun episodesByPinePodsIds(episodeIds: Collection<Int>): List<PodcastEpisodeCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertEpisodes(items: List<PodcastEpisodeCacheEntity>)

    @Query("SELECT * FROM podcast_details_cache")
    abstract fun observeAllPodcastDetails(): Flow<List<PodcastDetailsCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertPodcastDetails(item: PodcastDetailsCacheEntity)

    @Query("SELECT * FROM podcast_details_cache WHERE podcast_id = :podcastId LIMIT 1")
    abstract suspend fun getPodcastDetails(podcastId: Int): PodcastDetailsCacheEntity?

    @Query(
        """
        DELETE FROM podcast_episode_cache
        WHERE podcast_id = :podcastId AND source = :source
        """,
    )
    protected abstract suspend fun deleteEpisodesBySource(podcastId: Int, source: String)

    @Transaction
    open suspend fun replacePinePodsEpisodes(
        podcastId: Int,
        items: List<PodcastEpisodeCacheEntity>,
    ) {
        require(items.all { it.podcastId == podcastId && it.source == PodcastCacheSource.PINEPODS })
        val numbered = keepNumbers(podcastId, items)
        deleteEpisodesBySource(podcastId, PodcastCacheSource.PINEPODS)
        if (numbered.isNotEmpty()) upsertEpisodes(numbered)
    }

    /** PinePods rows from the server, keeping the season and episode numbers the feed gave them. */
    @Transaction
    open suspend fun upsertEpisodesKeepingNumbers(podcastId: Int, items: List<PodcastEpisodeCacheEntity>) {
        if (items.isNotEmpty()) upsertEpisodes(keepNumbers(podcastId, items))
    }

    private suspend fun keepNumbers(podcastId: Int, items: List<PodcastEpisodeCacheEntity>): List<PodcastEpisodeCacheEntity> {
        val known = numberedEpisodes(podcastId).associateBy(EpisodeNumberRow::cacheKey)
        return items.map { item ->
            val row = known[item.cacheKey]
            if (row == null || item.seasonNumber != null || item.episodeNumber != null) item
            else item.copy(seasonNumber = row.seasonNumber, episodeNumber = row.episodeNumber)
        }
    }

    @Query(
        "SELECT cache_key, pinepods_episode_id, rss_playback_id, season_number, episode_number FROM podcast_episode_cache " +
            "WHERE podcast_id = :podcastId AND (season_number IS NOT NULL OR episode_number IS NOT NULL)",
    )
    abstract suspend fun numberedEpisodes(podcastId: Int): List<EpisodeNumberRow>

    /** Every cached episode the feed numbered, for the lists of the whole app. */
    @Query(
        "SELECT cache_key, pinepods_episode_id, rss_playback_id, season_number, episode_number FROM podcast_episode_cache " +
            "WHERE season_number IS NOT NULL OR episode_number IS NOT NULL",
    )
    abstract fun observeEpisodeNumbers(): Flow<List<EpisodeNumberRow>>

    /** RSS-only rows PinePods now knows under its own ID (N-03); never touches PinePods rows. */
    @Query("DELETE FROM podcast_episode_cache WHERE cache_key IN (:cacheKeys) AND pinepods_episode_id IS NULL")
    abstract suspend fun deleteRssTwins(cacheKeys: Collection<String>)

    @Transaction
    open suspend fun replaceRssEpisodes(
        podcastId: Int,
        items: List<PodcastEpisodeCacheEntity>,
    ) {
        require(items.all { it.podcastId == podcastId })
        deleteEpisodesBySource(podcastId, PodcastCacheSource.RSS)
        if (items.isNotEmpty()) upsertEpisodes(items)
    }

    @Query("SELECT * FROM podcast_refresh_state WHERE source_key = :sourceKey LIMIT 1")
    abstract suspend fun getRefreshState(sourceKey: String): PodcastRefreshStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertRefreshState(item: PodcastRefreshStateEntity)

    @Query("SELECT * FROM current_playback_context WHERE singleton_id = 1 LIMIT 1")
    abstract fun observeCurrentPlaybackContext(): Flow<CurrentPlaybackContextEntity?>

    @Query("SELECT * FROM current_playback_context WHERE singleton_id = 1 LIMIT 1")
    abstract suspend fun getCurrentPlaybackContext(): CurrentPlaybackContextEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertCurrentPlaybackContext(item: CurrentPlaybackContextEntity)

    @Query("DELETE FROM current_playback_context WHERE singleton_id = 1")
    abstract suspend fun clearCurrentPlaybackContext()
}
