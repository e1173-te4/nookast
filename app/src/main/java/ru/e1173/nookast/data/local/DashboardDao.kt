package ru.e1173.nookast.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class DashboardContinueLocalEntry(
    val episodeId: Int,
    val episodeTitle: String,
    val podcastName: String,
    val artworkUrl: String,
    val mediaUrl: String,
    val durationSeconds: Int,
    val listenDurationSeconds: Double?,
    val completed: Boolean,
    val progressCompleted: Boolean,
    val description: String,
    val publishedAt: String,
    val downloaded: Boolean,
    val refreshedAtEpochMs: Long,
    val positionMs: Long,
    val durationMs: Long?,
    val lastListenedAtEpochMs: Long,
)

@Dao
interface DashboardDao {
    @Query("SELECT * FROM dashboard_cache WHERE singleton_id = 1")
    fun observe(): Flow<DashboardCacheEntity?>

    /**
     * Locally authoritative continuation metadata. Every episode started by the app is cached
     * before playback and progress is merged by the sync reducer, so this join remains
     * available after an item leaves Up Next without inventing an undocumented server timestamp.
     */
    @Query(
        """
        SELECT
            cache.episode_id AS episodeId,
            cache.episode_title AS episodeTitle,
            cache.podcast_name AS podcastName,
            cache.artwork_url AS artworkUrl,
            cache.media_url AS mediaUrl,
            cache.duration_seconds AS durationSeconds,
            cache.listen_duration_seconds AS listenDurationSeconds,
            cache.completed AS completed,
            progress.completed AS progressCompleted,
            cache.description AS description,
            cache.published_at AS publishedAt,
            cache.downloaded AS downloaded,
            cache.refreshed_at_epoch_ms AS refreshedAtEpochMs,
            progress.position_ms AS positionMs,
            progress.duration_ms AS durationMs,
            progress.updated_at_epoch_ms AS lastListenedAtEpochMs
        FROM playback_progress AS progress
        INNER JOIN queue_episode_cache AS cache
            ON cache.episode_id = progress.episode_id
        WHERE progress.position_ms > 0 AND progress.completed = 0 AND progress.episode_id > 0
        ORDER BY progress.updated_at_epoch_ms DESC
        LIMIT 40
        """,
    )
    fun observeContinueEntries(): Flow<List<DashboardContinueLocalEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DashboardCacheEntity)
}
