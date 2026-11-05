package ru.e1173.nookast.offline

import androidx.core.net.toUri
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.edit
import androidx.media3.common.MediaItem
import java.io.File
import java.util.concurrent.Executors
import ru.e1173.nookast.app.appGraph
import ru.e1173.nookast.core.session.SessionRepository
import ru.e1173.nookast.data.local.QueueListEntry
import ru.e1173.nookast.data.remote.MediaSourceCandidates
import ru.e1173.nookast.data.remote.MediaSourceResolver
import ru.e1173.nookast.player.PlaybackViewModel
import ru.e1173.nookast.player.artworkSourceUrl

enum class EpisodeDownloadState {
    NOT_DOWNLOADED,
    QUEUED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED,
}

data class EpisodeDownloadProgress(
    val state: EpisodeDownloadState,
    val fraction: Float?,
)

data class LocalDownloadedEpisode(
    val episodeId: Int,
    val title: String,
    val podcastName: String,
    val artworkUrl: String,
    val fileBytes: Long,
    val completedAtEpochMs: Long,
    // From the episode cache, so the Dashboard row reads like the others (user, 29.10.2026).
    val publishedAt: String = "",
    val durationSeconds: Int? = null,
    val listenedSeconds: Double? = null,
    val completed: Boolean = false,
)

class EpisodeDownloadManager(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val sessionStore = appContext.appGraph.session

    /** [allowMetered] false: automatic downloads wait for an unmetered network (P-04). */
    fun enqueue(entry: QueueListEntry, allowMetered: Boolean = true): EpisodeDownloadState {
        val session = sessionStore.load()
        val sources = if (session != null) {
            MediaSourceResolver.sources(
                serverUrl = session.serverUrl,
                episodeId = entry.episodeId,
                rssMediaUrl = entry.mediaUrl.orEmpty(),
            )
        } else {
            MediaSourceCandidates(primaryUrl = entry.mediaUrl, fallbackUrl = null)
        }
        return enqueue(
            episodeId = entry.episodeId,
            sources = sources,
            title = entry.episodeTitle.orEmpty(),
            podcastName = entry.podcastName.orEmpty(),
            artworkUrl = entry.artworkUrl.orEmpty(),
            allowMetered = allowMetered,
        )
    }

    fun enqueue(item: MediaItem, allowMetered: Boolean = true): EpisodeDownloadState {
        val episodeId = item.mediaId.toIntOrNull() ?: return EpisodeDownloadState.FAILED
        val extras = item.mediaMetadata.extras
        val primary = extras?.getString(PlaybackViewModel.EXTRA_PRIMARY_MEDIA_URL)
            ?: item.localConfiguration?.uri?.toString()
        val fallback = extras?.getString(PlaybackViewModel.EXTRA_FALLBACK_MEDIA_URL)
        val pinePods = extras?.getString(PlaybackViewModel.EXTRA_PINEPODS_MEDIA_URL)
        val serverUrl = sessionStore.load()?.serverUrl
        // The server copy's stream takes its key only in the query, which the player adds at
        // request time; the system DownloadManager would store and log it, so a download started
        // from Now Playing for such an episode goes to the RSS origin instead (user, 27.10.2026:
        // «Скачать» did nothing — the keyless request failed silently).
        val streamPrimary = serverUrl != null && primary != null &&
            MediaSourceResolver.isPinePodsStreamRequest(serverUrl, primary)
        // The stored copy is now fetched inside the app (X-01), so it stays first: it is the fastest
        // source. The RSS origin is the fallback, and PinePods' on-demand download after it.
        val sources = if (streamPrimary) {
            MediaSourceCandidates(primaryUrl = primary, fallbackUrl = fallback ?: pinePods, pinePodsUrl = pinePods)
        } else {
            MediaSourceCandidates(primaryUrl = primary, fallbackUrl = fallback, pinePodsUrl = pinePods)
        }
        return enqueue(
            episodeId = episodeId,
            sources = sources,
            title = item.mediaMetadata.title?.toString().orEmpty(),
            podcastName = item.mediaMetadata.artist?.toString().orEmpty(),
            artworkUrl = item.artworkSourceUrl(),
            allowMetered = allowMetered,
        )
    }

    @Synchronized
    fun completedEpisodes(): List<LocalDownloadedEpisode> = preferences.all.keys
        .asSequence()
        .filter { it.startsWith(COMPLETED_PREFIX) }
        .mapNotNull { it.removePrefix(COMPLETED_PREFIX).toIntOrNull() }
        .distinct()
        .mapNotNull { episodeId ->
            val uri = localUriIfReady(episodeId) ?: return@mapNotNull null
            val path = uri.path ?: return@mapNotNull null
            val file = File(path).takeIf(File::isFile) ?: return@mapNotNull null
            LocalDownloadedEpisode(
                episodeId = episodeId,
                title = preferences.getString(titleKey(episodeId), null).orEmpty(),
                podcastName = preferences.getString(podcastKey(episodeId), null).orEmpty(),
                artworkUrl = preferences.getString(artworkKey(episodeId), null).orEmpty(),
                fileBytes = file.length().coerceAtLeast(0),
                completedAtEpochMs = preferences.getLong(completedAtKey(episodeId), 0),
            )
        }
        .sortedWith(
            compareByDescending<LocalDownloadedEpisode> { it.completedAtEpochMs }
                .thenByDescending { it.episodeId },
        )
        .toList()

    fun state(episodeId: Int): EpisodeDownloadState {
        return progress(episodeId).state
    }

    fun progress(episodeId: Int): EpisodeDownloadProgress {
        localUriIfReady(episodeId)?.let {
            return EpisodeDownloadProgress(EpisodeDownloadState.DOWNLOADED, 1f)
        }
        val downloadId = preferences.getLong(downloadIdKey(episodeId), NO_DOWNLOAD_ID)
        if (downloadId == NO_DOWNLOAD_ID) {
            return EpisodeDownloadProgress(EpisodeDownloadState.NOT_DOWNLOADED, null)
        }
        if (downloadId == IN_APP_DOWNLOAD_ID) {
            if (preferences.getBoolean(inAppFailedKey(episodeId), false)) {
                return EpisodeDownloadProgress(EpisodeDownloadState.FAILED, null)
            }
            val fraction = EpisodeDownloadWorker.progress[episodeId]
            return if (fraction != null) {
                EpisodeDownloadProgress(EpisodeDownloadState.DOWNLOADING, fraction)
            } else {
                EpisodeDownloadProgress(EpisodeDownloadState.QUEUED, null)
            }
        }
        return runCatching {
            manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@use EpisodeDownloadProgress(EpisodeDownloadState.FAILED, null)
                }
                val total = cursor.longOrUnknown(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val downloaded = cursor.longOrUnknown(
                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR,
                )
                val fraction = if (total > 0L && downloaded >= 0L) {
                    (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                } else {
                    null
                }
                when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_PENDING -> EpisodeDownloadProgress(
                        EpisodeDownloadState.QUEUED,
                        fraction,
                    )
                    DownloadManager.STATUS_PAUSED -> {
                        // An unreachable RSS host makes DownloadManager retry for minutes; the
                        // server can still fetch the file, so switch to it at once (27.10.2026).
                        if (cursor.reason() == DownloadManager.PAUSED_WAITING_TO_RETRY &&
                            switchToFallback(episodeId, downloadId)
                        ) {
                            EpisodeDownloadProgress(EpisodeDownloadState.QUEUED, null)
                        } else {
                            EpisodeDownloadProgress(EpisodeDownloadState.DOWNLOADING, fraction)
                        }
                    }
                    DownloadManager.STATUS_RUNNING -> EpisodeDownloadProgress(
                        EpisodeDownloadState.DOWNLOADING,
                        fraction,
                    )
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        if (markCompletedFromCursor(episodeId, cursor) != null) {
                            EpisodeDownloadProgress(EpisodeDownloadState.DOWNLOADED, 1f)
                        } else {
                            EpisodeDownloadProgress(EpisodeDownloadState.FAILED, null)
                        }
                    }
                    else -> EpisodeDownloadProgress(EpisodeDownloadState.FAILED, null)
                }
            }
        }.getOrDefault(EpisodeDownloadProgress(EpisodeDownloadState.FAILED, null))
    }

    @Synchronized
    fun remove(episodeId: Int): Boolean {
        if (episodeId == 0) return false
        val downloadId = preferences.getLong(downloadIdKey(episodeId), NO_DOWNLOAD_ID)
        if (downloadId == IN_APP_DOWNLOAD_ID) {
            EpisodeDownloadWorker.cancel(appContext, episodeId)
            ru.e1173.nookast.player.EpisodeStreamCache.active?.cancel(episodeId)
        } else if (downloadId != NO_DOWNLOAD_ID) {
            runCatching { manager.remove(downloadId) }
        }
        val path = preferences.getString(pathKey(episodeId), null)
        path?.let { File("$it$PART_SUFFIX").delete() }
        val fileDeleted = path
            ?.let(::File)
            ?.let { file -> !file.exists() || file.delete() }
            ?: true
        preferences.edit {
            remove(inAppFailedKey(episodeId))
            remove(downloadIdKey(episodeId))
            remove(pathKey(episodeId))
            remove(sourceKey(episodeId))
            remove(fallbackKey(episodeId))
            remove(titleKey(episodeId))
            remove(podcastKey(episodeId))
            remove(artworkKey(episodeId))
            remove(completedKey(episodeId))
            remove(expectedBytesKey(episodeId))
            remove(completedAtKey(episodeId))
            remove(playedAtKey(episodeId))
        }
        return fileDeleted
    }

    /**
     * Spares the storage (user, 29.10.2026): an episode the player saved because «download started
     * episodes» is on is deleted a day after it was played. [played] are the downloaded episodes
     * marked played now; manual downloads are never touched. Returns the deleted episode ids.
     */
    @Synchronized
    fun sweepPlayedAutoDownloads(played: Set<Int>, nowMs: Long = System.currentTimeMillis()): List<Int> {
        val removed = mutableListOf<Int>()
        completedEpisodes().forEach { episode ->
            val id = episode.episodeId
            if (preferences.getString(sourceKey(id), null) != SOURCE_CACHE) return@forEach
            val playedAt = preferences.getLong(playedAtKey(id), 0L).takeIf { it > 0L }
            when (playedAutoDownloadAction(id in played, playedAt, nowMs)) {
                PlayedAutoDownloadAction.KEEP -> Unit
                PlayedAutoDownloadAction.MARK -> preferences.edit { putLong(playedAtKey(id), nowMs) }
                PlayedAutoDownloadAction.FORGET -> preferences.edit { remove(playedAtKey(id)) }
                PlayedAutoDownloadAction.DELETE -> { remove(id); removed += id }
            }
        }
        return removed
    }

    fun localUriIfReady(episodeId: Int): Uri? {
        val path = preferences.getString(pathKey(episodeId), null) ?: return null
        val file = File(path)
        val expectedBytes = preferences.getLong(expectedBytesKey(episodeId), UNKNOWN_BYTES)
            .takeIf { it > 0 }
        if (isCompletedFileUsable(
                markedComplete = preferences.getBoolean(completedKey(episodeId), false),
                fileLength = file.takeIf(File::isFile)?.length() ?: 0L,
                expectedBytes = expectedBytes,
            )
        ) {
            return Uri.fromFile(file)
        }

        // Migration/recovery path for downloads created by an older app version. DownloadManager
        // is the authority; a merely non-empty destination can still be an interrupted partial.
        val downloadId = preferences.getLong(downloadIdKey(episodeId), NO_DOWNLOAD_ID)
        if (downloadId < 0) return null
        return runCatching {
            manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                if (cursor.status() != DownloadManager.STATUS_SUCCESSFUL) return@use null
                markCompletedFromCursor(episodeId, cursor)
            }
        }.getOrNull()
    }

    fun clearAll() {
        val ids = preferences.all.keys
            .filter { it.startsWith(DOWNLOAD_ID_PREFIX) }
            .mapNotNull { key -> preferences.getLong(key, NO_DOWNLOAD_ID).takeIf { it >= 0 } }
        if (ids.isNotEmpty()) runCatching { manager.remove(*ids.toLongArray()) }
        EpisodeDownloadWorker.cancelAll(appContext)
        preferences.all.keys
            .filter { it.startsWith(PATH_PREFIX) }
            .mapNotNull { preferences.getString(it, null) }
            .map(::File)
            .flatMap { listOf(it, File(it.path + PART_SUFFIX)) }
            .filter(File::isFile)
            .forEach(File::delete)
        preferences.edit { clear() }
    }

    /** Called by [EpisodeDownloadWorker] after the file was renamed into place. */
    internal fun completeInApp(episodeId: Int, file: File, expectedBytes: Long?) {
        synchronized(this) {
            if (preferences.getString(pathKey(episodeId), null) != file.absolutePath) return
            preferences.edit {
                putBoolean(completedKey(episodeId), true)
                remove(inAppFailedKey(episodeId))
                if (expectedBytes != null) putLong(expectedBytesKey(episodeId), expectedBytes)
                putLong(completedAtKey(episodeId), System.currentTimeMillis())
            }
        }
    }

    /**
     * Marks [episodeId] as being filled from the player's stream cache (EpisodeStreamCache) and
     * returns the destination file, or null when it is already downloaded or downloading.
     */
    internal fun beginCacheFill(
        episodeId: Int,
        uri: Uri,
        title: String,
        podcastName: String,
        artworkUrl: String,
    ): File? = synchronized(ENQUEUE_LOCK) {
        val current = state(episodeId)
        if (current == EpisodeDownloadState.DOWNLOADED || current == EpisodeDownloadState.DOWNLOADING) {
            return@synchronized null
        }
        val directory = appContext.getExternalFilesDir(Environment.DIRECTORY_PODCASTS)
            ?: File(appContext.filesDir, "podcasts")
        if (!directory.exists() && !directory.mkdirs()) return@synchronized null
        val destination = File(directory, "$episodeId.${extension(uri)}")
        preferences.edit {
            putLong(downloadIdKey(episodeId), IN_APP_DOWNLOAD_ID)
            putString(pathKey(episodeId), destination.absolutePath)
            putString(sourceKey(episodeId), SOURCE_CACHE)
            remove(fallbackKey(episodeId))
            putString(titleKey(episodeId), title)
            putString(podcastKey(episodeId), podcastName)
            putString(artworkKey(episodeId), artworkUrl)
            putBoolean(completedKey(episodeId), false)
            remove(inAppFailedKey(episodeId))
            remove(expectedBytesKey(episodeId))
            remove(completedAtKey(episodeId))
        }
        destination
    }

    /** A cache fill stopped before the end: forget the marker; the cached part stays for next time. */
    internal fun cancelCacheFill(episodeId: Int) {
        if (preferences.getString(sourceKey(episodeId), null) != SOURCE_CACHE) return
        if (preferences.getBoolean(completedKey(episodeId), false)) return
        preferences.edit {
            remove(downloadIdKey(episodeId))
            remove(pathKey(episodeId))
            remove(sourceKey(episodeId))
        }
    }

    /** The in-app transfer gave up: try the other source, as for a failed system download. */
    internal fun failInApp(episodeId: Int) {
        if (switchToFallback(episodeId, IN_APP_DOWNLOAD_ID)) return
        preferences.edit { putBoolean(inAppFailedKey(episodeId), true) }
    }

    internal fun handleDownloadCompletion(downloadId: Long) {
        runCatching { handleDownloadCompletionSafely(downloadId) }
    }

    private fun handleDownloadCompletionSafely(downloadId: Long) {
        val episodeId = preferences.all.entries.firstNotNullOfOrNull { (key, value) ->
            if (key.startsWith(DOWNLOAD_ID_PREFIX) && value == downloadId) {
                key.removePrefix(DOWNLOAD_ID_PREFIX).toIntOrNull()
            } else {
                null
            }
        } ?: return
        manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (!cursor.moveToFirst()) return
            when (cursor.status()) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    if (markCompletedFromCursor(episodeId, cursor) != null) return
                    // A successful status without a complete destination is still unusable. Treat
                    // it like a failed PinePods transfer and try the RSS source below.
                }

                DownloadManager.STATUS_FAILED -> Unit
                else -> return
            }
        }
        switchToFallback(episodeId, downloadId)
    }

    /**
     * Restarts a failed or stalled transfer from the other source. For an RSS failure that is
     * PinePods' `/api/episodes/{id}/download`: the server fetches the episode into a temporary file,
     * sends it and deletes it at once, so no server download is left behind to clean up.
     */
    @Synchronized
    private fun switchToFallback(episodeId: Int, downloadId: Long): Boolean {
        if (preferences.getLong(downloadIdKey(episodeId), NO_DOWNLOAD_ID) != downloadId) return false
        val fallbackUrl = preferences.getString(fallbackKey(episodeId), null)
            ?.takeIf(String::isNotBlank)
            ?: return false
        val session = sessionStore.load()
        val fallbackSource = if (session != null && MediaSourceResolver.isPinePodsMediaRequest(
                session.serverUrl,
                fallbackUrl,
            )
        ) {
            SOURCE_PINEPODS
        } else {
            SOURCE_RSS
        }
        if (preferences.getString(sourceKey(episodeId), null) == fallbackSource) return false
        preferences.getString(pathKey(episodeId), null)?.let(::File)?.delete()
        if (downloadId >= 0) runCatching { manager.remove(downloadId) }
        return enqueueRequest(
            episodeId = episodeId,
            mediaUrl = fallbackUrl,
            fallbackUrl = null,
            title = preferences.getString(titleKey(episodeId), null).orEmpty(),
            podcastName = preferences.getString(podcastKey(episodeId), null).orEmpty(),
            artworkUrl = preferences.getString(artworkKey(episodeId), null).orEmpty(),
            source = fallbackSource,
            allowMetered = preferences.getBoolean(meteredKey(episodeId), true),
        ) == EpisodeDownloadState.QUEUED
    }

    private fun enqueue(
        episodeId: Int,
        sources: MediaSourceCandidates,
        title: String,
        podcastName: String,
        artworkUrl: String,
        allowMetered: Boolean,
    ): EpisodeDownloadState = synchronized(ENQUEUE_LOCK) {
        require(episodeId != 0)
        val current = state(episodeId)
        if (current == EpisodeDownloadState.QUEUED ||
            current == EpisodeDownloadState.DOWNLOADING ||
            current == EpisodeDownloadState.DOWNLOADED
        ) return@synchronized current
        preferences.getString(pathKey(episodeId), null)?.let(::File)?.delete()
        clearCompletionMarker(episodeId)

        val previousSource = preferences.getString(sourceKey(episodeId), null)
        val fallbackSource = sourceOf(sources.fallbackUrl)
        val shouldUseFallback = current == EpisodeDownloadState.FAILED &&
            previousSource != null &&
            previousSource != fallbackSource &&
            !sources.fallbackUrl.isNullOrBlank()
        val mediaUrl = if (shouldUseFallback) {
            sources.fallbackUrl
        } else {
            sources.primaryUrl ?: sources.fallbackUrl
        }.orEmpty()
        val source = sourceOf(mediaUrl)
        enqueueRequest(
            episodeId = episodeId,
            mediaUrl = mediaUrl,
            fallbackUrl = sources.fallbackUrl,
            title = title,
            podcastName = podcastName,
            artworkUrl = artworkUrl,
            source = source,
            allowMetered = allowMetered,
        )
    }

    private fun enqueueRequest(
        episodeId: Int,
        mediaUrl: String,
        fallbackUrl: String?,
        title: String,
        podcastName: String,
        artworkUrl: String,
        source: String,
        allowMetered: Boolean,
    ): EpisodeDownloadState {
        val uri = runCatching { mediaUrl.toUri() }.getOrNull()
            ?.takeIf { it.scheme == "https" || it.scheme == "http" }
            ?: return EpisodeDownloadState.FAILED
        val directory = appContext.getExternalFilesDir(Environment.DIRECTORY_PODCASTS)
            ?: File(appContext.filesDir, "podcasts")
        if (!directory.exists() && !directory.mkdirs()) return EpisodeDownloadState.FAILED
        val destination = File(directory, "$episodeId.${extension(uri)}")
        // DownloadManager may leave a partial destination after interruption. It can resume its own
        // live jobs, but a new request must never adopt an unverified file as a completed episode.
        if (destination.exists() && !destination.delete()) return EpisodeDownloadState.FAILED

        // Every new download runs in the app (X-01 for PinePods, plan 4.4 for RSS): one progress,
        // resume by Range, no system notification. DownloadManager remains only for jobs started
        // by older versions, which progress() and the completion receiver still finish.
        preferences.edit {
            putLong(downloadIdKey(episodeId), IN_APP_DOWNLOAD_ID)
            putString(pathKey(episodeId), destination.absolutePath)
            putBoolean(meteredKey(episodeId), allowMetered)
            putString(sourceKey(episodeId), source)
            putString(fallbackKey(episodeId), fallbackUrl)
            putString(titleKey(episodeId), title)
            putString(podcastKey(episodeId), podcastName)
            putString(artworkKey(episodeId), artworkUrl)
            putBoolean(completedKey(episodeId), false)
            remove(inAppFailedKey(episodeId))
            remove(expectedBytesKey(episodeId))
            remove(completedAtKey(episodeId))
        }
        return runCatching {
            EpisodeDownloadWorker.enqueue(appContext, episodeId, uri.toString(), destination, allowMetered)
            EpisodeDownloadState.QUEUED
        }.getOrDefault(EpisodeDownloadState.FAILED)
    }

    private fun extension(uri: Uri): String {
        val extension = uri.lastPathSegment
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf { it in SAFE_EXTENSIONS }
        return extension ?: "audio"
    }

    private fun sourceOf(url: String?): String {
        val serverUrl = sessionStore.load()?.serverUrl
        return if (serverUrl != null && url != null && MediaSourceResolver.isPinePodsMediaRequest(serverUrl, url)) {
            SOURCE_PINEPODS
        } else {
            SOURCE_RSS
        }
    }

    private fun meteredKey(episodeId: Int) = "$METERED_PREFIX$episodeId"
    private fun inAppFailedKey(episodeId: Int) = "$IN_APP_FAILED_PREFIX$episodeId"
    private fun downloadIdKey(episodeId: Int) = "$DOWNLOAD_ID_PREFIX$episodeId"
    private fun pathKey(episodeId: Int) = "$PATH_PREFIX$episodeId"
    private fun sourceKey(episodeId: Int) = "$SOURCE_PREFIX$episodeId"
    private fun fallbackKey(episodeId: Int) = "$FALLBACK_PREFIX$episodeId"
    private fun titleKey(episodeId: Int) = "$TITLE_PREFIX$episodeId"
    private fun podcastKey(episodeId: Int) = "$PODCAST_PREFIX$episodeId"
    private fun artworkKey(episodeId: Int) = "$ARTWORK_PREFIX$episodeId"
    private fun completedKey(episodeId: Int) = "$COMPLETED_PREFIX$episodeId"
    private fun expectedBytesKey(episodeId: Int) = "$EXPECTED_BYTES_PREFIX$episodeId"
    private fun completedAtKey(episodeId: Int) = "$COMPLETED_AT_PREFIX$episodeId"
    private fun playedAtKey(episodeId: Int) = "$PLAYED_AT_PREFIX$episodeId"

    private fun clearCompletionMarker(episodeId: Int) {
        preferences.edit {
            putBoolean(completedKey(episodeId), false)
            remove(expectedBytesKey(episodeId))
            remove(completedAtKey(episodeId))
        }
    }

    private fun markCompletedFromCursor(
        episodeId: Int,
        cursor: android.database.Cursor,
    ): Uri? {
        val path = preferences.getString(pathKey(episodeId), null) ?: return null
        val file = File(path)
        val totalBytes = cursor.longOrUnknown(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            .takeIf { it > 0 }
        val downloadedBytes = cursor.longOrUnknown(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            .takeIf { it > 0 }
        val expectedBytes = totalBytes ?: downloadedBytes
        if (!isCompletedFileUsable(true, file.takeIf(File::isFile)?.length() ?: 0L, expectedBytes)) {
            clearCompletionMarker(episodeId)
            return null
        }
        preferences.edit {
            putBoolean(completedKey(episodeId), true)
            if (expectedBytes != null) putLong(expectedBytesKey(episodeId), expectedBytes)
            if (!preferences.contains(completedAtKey(episodeId))) {
                putLong(completedAtKey(episodeId), System.currentTimeMillis())
            }
        }
        return Uri.fromFile(file)
    }

    private companion object {
        const val PREFERENCES_NAME = "episode_downloads"
        const val DOWNLOAD_ID_PREFIX = "download_id_"
        const val PATH_PREFIX = "path_"
        const val SOURCE_PREFIX = "source_"
        const val FALLBACK_PREFIX = "fallback_"
        const val TITLE_PREFIX = "title_"
        const val PODCAST_PREFIX = "podcast_"
        const val ARTWORK_PREFIX = "artwork_"
        const val COMPLETED_PREFIX = "completed_"
        const val EXPECTED_BYTES_PREFIX = "expected_bytes_"
        const val COMPLETED_AT_PREFIX = "completed_at_"
        const val PLAYED_AT_PREFIX = "played_at_"
        const val NO_DOWNLOAD_ID = -1L

        /** Marks a transfer run by [EpisodeDownloadWorker] instead of DownloadManager. */
        const val IN_APP_DOWNLOAD_ID = -2L
        const val IN_APP_FAILED_PREFIX = "in_app_failed_"
        const val METERED_PREFIX = "allow_metered_"

        /** One lock for every instance: the player service and the UI used to race into two jobs (P-04). */
        val ENQUEUE_LOCK = Any()
        const val PART_SUFFIX = ".part"
        const val UNKNOWN_BYTES = -1L
        const val SOURCE_PINEPODS = "pinepods"
        const val SOURCE_RSS = "rss"
        const val SOURCE_CACHE = "stream-cache"
        val SAFE_EXTENSIONS = setOf("mp3", "m4a", "aac", "ogg", "opus", "mp4", "webm")
    }
}

internal enum class PlayedAutoDownloadAction { KEEP, MARK, FORGET, DELETE }

/** A day after an automatic download was first seen played it goes; marked unplayed it stays. */
internal fun playedAutoDownloadAction(played: Boolean, playedAtMs: Long?, nowMs: Long): PlayedAutoDownloadAction = when {
    !played -> if (playedAtMs != null) PlayedAutoDownloadAction.FORGET else PlayedAutoDownloadAction.KEEP
    playedAtMs == null -> PlayedAutoDownloadAction.MARK
    nowMs - playedAtMs >= PLAYED_AUTO_DOWNLOAD_KEEP_MS -> PlayedAutoDownloadAction.DELETE
    else -> PlayedAutoDownloadAction.KEEP
}

internal const val PLAYED_AUTO_DOWNLOAD_KEEP_MS = 24L * 60 * 60 * 1_000

internal fun isCompletedFileUsable(
    markedComplete: Boolean,
    fileLength: Long,
    expectedBytes: Long?,
): Boolean {
    if (!markedComplete || fileLength <= 0) return false
    return expectedBytes == null || expectedBytes <= 0 || fileLength >= expectedBytes
}

private fun android.database.Cursor.status(): Int =
    getInt(getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

private fun android.database.Cursor.reason(): Int =
    getInt(getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))

private fun android.database.Cursor.longOrUnknown(columnName: String): Long {
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getLong(index) else -1L
}

class EpisodeDownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId < 0) return
        val pendingResult = goAsync()
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            try {
                EpisodeDownloadManager(context).handleDownloadCompletion(downloadId)
            } finally {
                pendingResult.finish()
                executor.shutdown()
            }
        }
    }
}
