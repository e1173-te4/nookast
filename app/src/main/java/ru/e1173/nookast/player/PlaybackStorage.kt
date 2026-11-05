package ru.e1173.nookast.player

import android.content.Context
import android.os.Environment
import java.io.File

data class PlaybackStorageSnapshot(
    val aheadCacheBytes: Long,
    val offlineDownloadBytes: Long,
    val aheadCacheClearPending: Boolean,
)

class PlaybackStorage(private val context: Context) {
    private val preferences = PlaybackPreferences(context)

    fun snapshot(): PlaybackStorageSnapshot = PlaybackStorageSnapshot(
        aheadCacheBytes = directorySize(aheadCacheDirectory()),
        offlineDownloadBytes = directorySize(offlineDownloadDirectory()),
        aheadCacheClearPending = preferences.aheadCacheClearRequested,
    )

    fun requestAheadCacheClear() {
        preferences.aheadCacheClearRequested = true
    }

    /** Called before SimpleCache opens the directory, so no live cache files are removed. */
    fun clearAheadCacheBeforeOpenIfRequested() {
        if (!preferences.aheadCacheClearRequested) return
        val directory = aheadCacheDirectory()
        val cleared = !directory.exists() || directory.deleteRecursively()
        if (cleared) preferences.aheadCacheClearRequested = false
    }

    private fun aheadCacheDirectory(): File = File(context.cacheDir, AHEAD_CACHE_DIRECTORY)

    private fun offlineDownloadDirectory(): File =
        context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS)
            ?: File(context.filesDir, OFFLINE_DIRECTORY)

    companion object {
        const val AHEAD_CACHE_DIRECTORY = "media-ahead-cache"
        private const val OFFLINE_DIRECTORY = "podcasts"

        internal fun directorySize(file: File): Long = when {
            !file.exists() -> 0L
            file.isFile -> file.length().coerceAtLeast(0L)
            else -> file.listFiles()?.sumOf(::directorySize) ?: 0L
        }
    }
}
