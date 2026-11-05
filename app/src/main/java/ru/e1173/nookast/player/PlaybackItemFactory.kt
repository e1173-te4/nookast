package ru.e1173.nookast.player

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import ru.e1173.nookast.auth.AuthSession
import ru.e1173.nookast.data.local.CurrentPlaybackContextEntity
import ru.e1173.nookast.data.local.PodcastEpisodeCacheEntity
import ru.e1173.nookast.data.local.QueueListEntry
import ru.e1173.nookast.data.local.SyncDatabase
import ru.e1173.nookast.data.local.rssLocalPlaybackId
import ru.e1173.nookast.data.remote.ArtworkUrlResolver
import ru.e1173.nookast.data.remote.MediaSourceCandidates
import ru.e1173.nookast.data.remote.MediaSourceResolver
import ru.e1173.nookast.offline.EpisodeDownloadManager
import ru.e1173.nookast.offline.LocalDownloadedEpisode
import ru.e1173.nookast.player.PlaybackViewModel.Companion.EXTRA_DESCRIPTION
import ru.e1173.nookast.player.PlaybackViewModel.Companion.EXTRA_DURATION_MS
import ru.e1173.nookast.player.PlaybackViewModel.Companion.EXTRA_ARTWORK_URL
import ru.e1173.nookast.player.PlaybackViewModel.Companion.EXTRA_FALLBACK_ARTWORK_URL
import ru.e1173.nookast.player.PlaybackViewModel.Companion.EXTRA_FALLBACK_MEDIA_URL
import ru.e1173.nookast.player.PlaybackViewModel.Companion.EXTRA_PINEPODS_MEDIA_URL
import ru.e1173.nookast.player.PlaybackViewModel.Companion.EXTRA_PRIMARY_MEDIA_URL
import ru.e1173.nookast.player.PlaybackViewModel.Companion.EXTRA_PUBLISHED_AT
import ru.e1173.nookast.player.PlaybackViewModel.Companion.EXTRA_RESUME_POSITION_MS
import ru.e1173.nookast.player.PlaybackViewModel.Companion.EXTRA_USING_FALLBACK_MEDIA_SOURCE

/**
 * The single place that turns a locally known episode into a playable [MediaItem]. Used by the
 * in-app player ([PlaybackViewModel]) and by external controllers through [PlaybackService]
 * (head-unit browser, media buttons, playback resumption), so both build identical items with
 * the same sources, extras and resume position.
 *
 * Episodes are always resolved from Room by playback ID; URIs supplied by a controller are never
 * trusted.
 */
internal class PlaybackItemFactory(
    private val database: SyncDatabase,
    private val downloadManager: EpisodeDownloadManager,
    /** Whether the episode is stored on the PinePods server (DEC-023). */
    private val isOnServer: (Int) -> Boolean = { false },
) {
    /** Resume point (docs/SYNC.md §5.9): newest local intent, otherwise the accepted server state. */
    suspend fun resumePositionMs(entry: QueueListEntry): Long {
        if (entry.episodeId < 0) {
            database.offlineCacheDao().getCurrentPlaybackContext()
                ?.takeIf { context ->
                    !context.completed && context.mediaUrl == entry.mediaUrl
                }
                ?.let { return it.positionMs }
        }
        // docs/SYNC.md §5.9: a dirty row is the user's newest intent; a clean row has already
        // adopted every accepted server observation. No wall-clock comparison (S-11).
        val local = database.syncV8Dao().progress(entry.episodeId)
        if (local != null) return if (local.completed) 0 else local.positionMs
        if (entry.completed == true) return 0
        return ((entry.listenDurationSeconds ?: 0.0) * 1_000).toLong()
    }

    fun isPlayable(entry: QueueListEntry, session: AuthSession?): Boolean =
        downloadManager.localUriIfReady(entry.episodeId) != null ||
            mediaSources(entry, session).let { it.primaryUrl != null || it.fallbackUrl != null }

    fun mediaItem(
        entry: QueueListEntry,
        resumePositionMs: Long,
        session: AuthSession?,
    ): MediaItem {
        val mediaSources = mediaSources(entry, session)
        val localUri = downloadManager.localUriIfReady(entry.episodeId)
        val selectedUri = localUri?.toString()
            ?: mediaSources.primaryUrl
            ?: mediaSources.fallbackUrl
            ?: error("No playable source for episode ${entry.episodeId}")
        val artworkSources = session?.let {
            ArtworkUrlResolver.sources(it.serverUrl, entry.artworkUrl.orEmpty())
        }
        val extras = Bundle().apply {
            putLong(EXTRA_RESUME_POSITION_MS, resumePositionMs)
            putLong(EXTRA_DURATION_MS, (entry.durationSeconds ?: 0).toLong() * 1_000)
            putString(EXTRA_DESCRIPTION, entry.description.orEmpty())
            putString(EXTRA_PUBLISHED_AT, entry.publishedAt.orEmpty())
            mediaSources.primaryUrl?.let { putString(EXTRA_PRIMARY_MEDIA_URL, it) }
            mediaSources.fallbackUrl?.let { putString(EXTRA_FALLBACK_MEDIA_URL, it) }
            mediaSources.pinePodsUrl?.let { putString(EXTRA_PINEPODS_MEDIA_URL, it) }
            artworkSources?.fallbackUrl?.let { putString(EXTRA_FALLBACK_ARTWORK_URL, it) }
            (artworkSources?.primaryUrl ?: entry.artworkUrl)?.takeIf(String::isNotBlank)
                ?.let { putString(EXTRA_ARTWORK_URL, it) }
            putBoolean(EXTRA_USING_FALLBACK_MEDIA_SOURCE, false)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(entry.episodeTitle.orEmpty())
            .setArtist(entry.podcastName.orEmpty())
            // Controllers outside the app get artwork through the provider: the real URL names the
            // PinePods server (P-15). The app reads it from [EXTRA_ARTWORK_URL] instead.
            .setArtworkUri(
                ArtworkContentProvider.uriFor(entry.episodeId)
                    .takeIf { !entry.artworkUrl.isNullOrBlank() },
            )
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(entry.episodeId.toString())
            .setUri(selectedUri.toUri())
            .setMediaMetadata(metadata)
            .build()
    }

    fun mediaSources(entry: QueueListEntry, session: AuthSession?): MediaSourceCandidates =
        if (session != null) {
            MediaSourceResolver.sources(
                serverUrl = session.serverUrl,
                episodeId = entry.episodeId,
                rssMediaUrl = entry.mediaUrl.orEmpty(),
                userId = session.userId,
                onServer = isOnServer(entry.episodeId),
            )
        } else {
            MediaSourceCandidates(
                primaryUrl = null,
                fallbackUrl = entry.mediaUrl?.takeIf(String::isNotBlank),
            )
        }

    /** Local Up Next queue in display order. */
    suspend fun queueEntries(): List<QueueListEntry> = database.queueDao().getQueueEntries()

    /** The interrupted episode with its saved position, unless it was completed. */
    suspend fun currentEntry(): Pair<QueueListEntry, Long>? {
        val context = database.offlineCacheDao().getCurrentPlaybackContext() ?: return null
        val playbackId = restorablePlaybackEpisodeId(context) ?: return null
        return context.toEntry(playbackId) to context.positionMs
    }

    /** Completed downloads, newest first, enriched from the episode cache when it is known. */
    suspend fun downloadedEntries(): List<QueueListEntry> =
        downloadManager.completedEpisodes().map { download ->
            findCachedEntry(download.episodeId) ?: download.toEntry()
        }

    /** Any locally known episode by playback ID: queue, current context, caches, downloads. */
    suspend fun findEntry(playbackId: Int): QueueListEntry? {
        queueEntries().firstOrNull { it.episodeId == playbackId }?.let { return it }
        currentEntry()?.first?.takeIf { it.episodeId == playbackId }?.let { return it }
        findCachedEntry(playbackId)?.let { return it }
        return downloadManager.completedEpisodes()
            .firstOrNull { it.episodeId == playbackId }
            ?.toEntry()
    }

    private suspend fun findCachedEntry(playbackId: Int): QueueListEntry? {
        val dao = database.offlineCacheDao()
        val episode = if (playbackId > 0) {
            dao.findEpisodeByPinePodsId(playbackId)
        } else {
            dao.getRssOnlyEpisodes().firstOrNull { rssLocalPlaybackId(it.cacheKey) == playbackId }
        }
        return episode?.toEntry(playbackId)
    }
}

private fun CurrentPlaybackContextEntity.toEntry(playbackId: Int) = QueueListEntry(
    episodeId = playbackId,
    queuePosition = NOT_IN_QUEUE,
    episodeTitle = episodeTitle,
    podcastName = podcastName,
    artworkUrl = artworkUrl,
    mediaUrl = mediaUrl,
    durationSeconds = durationMs?.let { (it / 1_000).toInt() },
    listenDurationSeconds = null,
    completed = completed,
    description = description,
    publishedAt = publishedAt,
    downloaded = null,
    refreshedAtEpochMs = null,
)

private fun PodcastEpisodeCacheEntity.toEntry(playbackId: Int) = QueueListEntry(
    episodeId = playbackId,
    queuePosition = NOT_IN_QUEUE,
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

private fun LocalDownloadedEpisode.toEntry() = QueueListEntry(
    episodeId = episodeId,
    queuePosition = NOT_IN_QUEUE,
    episodeTitle = title,
    podcastName = podcastName,
    artworkUrl = artworkUrl,
    mediaUrl = null,
    durationSeconds = null,
    listenDurationSeconds = null,
    completed = null,
    description = null,
    publishedAt = null,
    downloaded = true,
    refreshedAtEpochMs = null,
)

private const val NOT_IN_QUEUE = -1

/** The artwork URL the app itself loads; the session's artwork URI is the provider's (P-15). */
internal fun MediaItem.artworkSourceUrl(): String =
    mediaMetadata.extras?.getString(EXTRA_ARTWORK_URL)
        ?: mediaMetadata.artworkUri?.toString().orEmpty()
