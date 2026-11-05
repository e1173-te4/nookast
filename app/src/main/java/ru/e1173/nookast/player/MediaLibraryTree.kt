package ru.e1173.nookast.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import ru.e1173.nookast.data.local.QueueListEntry

/**
 * The browsable tree shown to external media browsers (head-unit shell, Android Auto-like
 * launchers, system resumption UI). Minimal by design (EXECUTION_PLAN 2b.2):
 *
 * - root → «Continue» (the interrupted episode), «Up Next» (local queue), «Downloads»;
 * - a recent-root request ([isRecent]) gets a root whose only child is the interrupted episode.
 *
 * Episode items carry only display metadata and a `content://` artwork URI that needs no
 * authorization; playback items are rebuilt from Room when a controller asks to play an ID.
 */
internal object MediaLibraryTree {
    const val ROOT_ID = "nookast:root"
    const val RECENT_ROOT_ID = "nookast:recent"
    const val CONTINUE_ID = "nookast:continue"
    const val QUEUE_ID = "nookast:queue"
    const val DOWNLOADS_ID = "nookast:downloads"

    data class FolderTitles(
        val root: String,
        val continueListening: String,
        val queue: String,
        val downloads: String,
    )

    fun isFolder(mediaId: String): Boolean = mediaId in FOLDER_IDS

    fun root(titles: FolderTitles, recent: Boolean): MediaItem =
        folder(if (recent) RECENT_ROOT_ID else ROOT_ID, titles.root, MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)

    fun folderItem(mediaId: String, titles: FolderTitles): MediaItem? = when (mediaId) {
        ROOT_ID, RECENT_ROOT_ID -> root(titles, recent = mediaId == RECENT_ROOT_ID)
        CONTINUE_ID -> folder(CONTINUE_ID, titles.continueListening, MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
        QUEUE_ID -> folder(QUEUE_ID, titles.queue, MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
        DOWNLOADS_ID -> folder(DOWNLOADS_ID, titles.downloads, MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
        else -> null
    }

    fun rootChildren(titles: FolderTitles): List<MediaItem> = listOf(
        folderItem(CONTINUE_ID, titles)!!,
        folderItem(QUEUE_ID, titles)!!,
        folderItem(DOWNLOADS_ID, titles)!!,
    )

    /** Display-only episode item for a browser: no media URI, artwork through [artworkUri]. */
    fun episodeItem(entry: QueueListEntry, artworkUri: (Int) -> Uri): MediaItem =
        MediaItem.Builder()
            .setMediaId(entry.episodeId.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(entry.episodeTitle.orEmpty())
                    .setArtist(entry.podcastName.orEmpty())
                    .setDurationMs(entry.durationSeconds?.takeIf { it > 0 }?.let { it * 1_000L })
                    .setArtworkUri(artworkUri(entry.episodeId))
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                    .build(),
            )
            .build()

    /** One page of [items]; an out-of-range page is empty (Media3 paging contract). */
    fun <T> page(items: List<T>, page: Int, pageSize: Int): List<T> {
        if (page < 0 || pageSize <= 0) return emptyList()
        val from = page.toLong() * pageSize
        if (from >= items.size) return emptyList()
        val to = minOf(items.size.toLong(), from + pageSize).toInt()
        return items.subList(from.toInt(), to)
    }

    /**
     * What a controller's play request expands to. Playing one episode that is in the queue plays
     * the queue from that episode (the same rule as tapping it in the app); anything else plays
     * exactly the requested episodes.
     */
    fun <T> expandToQueue(
        requested: List<T>,
        queue: List<T>,
        sameEpisode: (T, T) -> Boolean,
    ): Pair<List<T>, Int> {
        val single = requested.singleOrNull() ?: return requested to 0
        val index = queue.indexOfFirst { sameEpisode(it, single) }
        return if (index >= 0) queue to index else requested to 0
    }

    private fun folder(id: String, title: String, type: Int): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(type)
                    .build(),
            )
            .build()

    private val FOLDER_IDS = setOf(ROOT_ID, RECENT_ROOT_ID, CONTINUE_ID, QUEUE_ID, DOWNLOADS_ID)
}
