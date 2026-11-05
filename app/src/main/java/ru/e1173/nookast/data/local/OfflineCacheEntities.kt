package ru.e1173.nookast.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

@Entity(
    tableName = "podcast_episode_cache",
    indices = [
        Index(value = ["podcast_id"]),
        Index(value = ["pinepods_episode_id"], unique = true),
        Index(value = ["media_url"]),
        Index(value = ["rss_playback_id"], unique = true),
    ],
)
data class PodcastEpisodeCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "cache_key")
    val cacheKey: String,
    @ColumnInfo(name = "podcast_id")
    val podcastId: Int,
    @ColumnInfo(name = "pinepods_episode_id")
    val pinePodsEpisodeId: Int?,
    val guid: String?,
    val source: String,
    val title: String,
    val description: String,
    @ColumnInfo(name = "published_at")
    val publishedAt: String,
    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int,
    @ColumnInfo(name = "listened_seconds")
    val listenedSeconds: Double,
    val completed: Boolean,
    @ColumnInfo(name = "podcast_name")
    val podcastName: String,
    @ColumnInfo(name = "artwork_url")
    val artworkUrl: String,
    @ColumnInfo(name = "media_url")
    val mediaUrl: String,
    val downloaded: Boolean,
    @ColumnInfo(name = "refreshed_at_epoch_ms")
    val refreshedAtEpochMs: Long,
    /** v8, ADR-0012: stable negative playback ID of an RSS-only row; null for PinePods rows. */
    @ColumnInfo(name = "rss_playback_id")
    val rssPlaybackId: Int? = null,
    /** v9: `itunes:season` and `itunes:episode` from the feed, as Pocket Casts shows them. */
    @ColumnInfo(name = "season_number")
    val seasonNumber: Int? = null,
    @ColumnInfo(name = "episode_number")
    val episodeNumber: Int? = null,
) {
    init {
        require(cacheKey.isNotBlank())
        require(podcastId > 0)
        require(pinePodsEpisodeId == null || pinePodsEpisodeId > 0)
        require(source.isNotBlank())
        require(durationSeconds >= 0)
        require(listenedSeconds >= 0)
        require(refreshedAtEpochMs >= 0)
    }
}

@Entity(tableName = "podcast_details_cache")
data class PodcastDetailsCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "podcast_id")
    val podcastId: Int,
    val name: String,
    @ColumnInfo(name = "artwork_url")
    val artworkUrl: String,
    val description: String,
    val author: String,
    val categories: List<String>,
    @ColumnInfo(name = "episode_count")
    val episodeCount: Int?,
    @ColumnInfo(name = "episodes_played")
    val episodesPlayed: Int?,
    @ColumnInfo(name = "refreshed_at_epoch_ms")
    val refreshedAtEpochMs: Long,
) {
    init {
        require(podcastId > 0)
        require(episodeCount == null || episodeCount >= 0)
        require(episodesPlayed == null || episodesPlayed >= 0)
        require(refreshedAtEpochMs >= 0)
    }
}

@Entity(
    tableName = "podcast_refresh_state",
    indices = [Index(value = ["podcast_id"])],
)
data class PodcastRefreshStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "source_key")
    val sourceKey: String,
    @ColumnInfo(name = "podcast_id")
    val podcastId: Int?,
    @ColumnInfo(name = "feed_url")
    val feedUrl: String,
    val etag: String?,
    @ColumnInfo(name = "last_modified")
    val lastModified: String?,
    @ColumnInfo(name = "last_attempt_at_epoch_ms")
    val lastAttemptAtEpochMs: Long,
    @ColumnInfo(name = "last_success_at_epoch_ms")
    val lastSuccessAtEpochMs: Long?,
    @ColumnInfo(name = "last_error_category")
    val lastErrorCategory: String?,
) {
    init {
        require(sourceKey.isNotBlank())
        require(podcastId == null || podcastId > 0)
        require(lastAttemptAtEpochMs >= 0)
        require(lastSuccessAtEpochMs == null || lastSuccessAtEpochMs >= 0)
    }
}

@Entity(tableName = "current_playback_context")
data class CurrentPlaybackContextEntity(
    @PrimaryKey
    @ColumnInfo(name = "singleton_id")
    val singletonId: Int = SINGLETON_ID,
    @ColumnInfo(name = "episode_cache_key")
    val episodeCacheKey: String,
    @ColumnInfo(name = "pinepods_episode_id")
    val pinePodsEpisodeId: Int?,
    @ColumnInfo(name = "podcast_id")
    val podcastId: Int?,
    @ColumnInfo(name = "episode_title")
    val episodeTitle: String,
    @ColumnInfo(name = "podcast_name")
    val podcastName: String,
    @ColumnInfo(name = "artwork_url")
    val artworkUrl: String,
    @ColumnInfo(name = "media_url")
    val mediaUrl: String,
    val description: String,
    @ColumnInfo(name = "published_at")
    val publishedAt: String,
    @ColumnInfo(name = "position_ms")
    val positionMs: Long,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long?,
    val completed: Boolean,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
) {
    init {
        require(singletonId == SINGLETON_ID)
        require(episodeCacheKey.isNotBlank())
        require(pinePodsEpisodeId == null || pinePodsEpisodeId > 0)
        require(podcastId == null || podcastId > 0)
        require(positionMs >= 0)
        require(durationMs == null || durationMs >= 0)
        require(updatedAtEpochMs >= 0)
    }

    companion object {
        const val SINGLETON_ID = 1
    }
}

internal object PodcastCacheSource {
    const val PINEPODS = "PINEPODS"
    const val RSS = "RSS"
}

internal fun pinePodsEpisodeCacheKey(episodeId: Int): String {
    require(episodeId > 0)
    return "pinepods:$episodeId"
}

internal fun pinePodsPodcastRefreshKey(podcastId: Int): String {
    require(podcastId > 0)
    return "pinepods:podcast:$podcastId"
}

internal fun rssPodcastRefreshKey(podcastId: Int): String {
    require(podcastId > 0)
    return "rss:podcast:$podcastId"
}

internal fun rssEpisodeCacheKey(
    podcastId: Int,
    guid: String,
    mediaUrl: String,
    title: String,
    publishedAt: String,
): String {
    require(podcastId > 0)
    val identity = guid.trim().ifBlank {
        mediaUrl.trim().ifBlank {
            "${title.trim().lowercase(Locale.ROOT)}\u0000${publishedAt.trim()}"
        }
    }
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(identity.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
    return "rss:$podcastId:$digest"
}

/** Local-only player identity. The authoritative external identity remains [cacheKey]. */
internal fun rssLocalPlaybackId(cacheKey: String): Int {
    require(cacheKey.startsWith("rss:"))
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(cacheKey.toByteArray(StandardCharsets.UTF_8))
    val positive = ((digest[0].toInt() and 0x7f) shl 24) or
        ((digest[1].toInt() and 0xff) shl 16) or
        ((digest[2].toInt() and 0xff) shl 8) or
        (digest[3].toInt() and 0xff)
    return -positive.coerceAtLeast(1)
}

internal const val PINEPODS_CATALOG_REFRESH_KEY = "pinepods:catalog"

/** The feed's season and episode number of one cached episode. */
data class EpisodeNumberRow(
    @ColumnInfo(name = "cache_key")
    val cacheKey: String,
    @ColumnInfo(name = "pinepods_episode_id")
    val pinePodsEpisodeId: Int?,
    @ColumnInfo(name = "rss_playback_id")
    val rssPlaybackId: Int?,
    @ColumnInfo(name = "season_number")
    val seasonNumber: Int?,
    @ColumnInfo(name = "episode_number")
    val episodeNumber: Int?,
)

/** The newest cached episode date of one podcast. */
data class LatestPublishedRow(
    val podcastId: Int,
    val latestPublishedAt: String?,
)
