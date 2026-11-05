package ru.e1173.nookast.player

import android.app.ActivityManager
import android.content.Context
import java.io.File

/**
 * Streaming is buffered in RAM only (user decision 27.10.2026, DEC-039): the old Media3 disk cache
 * wrote every streamed byte to internal storage. Downloads are the only media written to disk.
 *
 * The player's allocator counts both the part ahead of the position and the retained back buffer,
 * so the byte target is the user's "ahead" size plus a fixed back-buffer budget, capped by a third
 * of the app's heap so a head unit with a small heap cannot run out of memory.
 */
internal object PlaybackMediaCache {
    @Volatile
    private var legacyDiskCacheRemoved = false

    fun targetBytes(aheadCacheMegabytes: Int): Int = aheadCacheMegabytes
        .coerceIn(
            PlaybackPreferences.MIN_AHEAD_CACHE_MB,
            PlaybackPreferences.MAX_AHEAD_CACHE_MB,
        ) * BYTES_PER_MEGABYTE

    /** RAM the player may hold: ahead + back buffer, at most a third of the heap. */
    fun memoryBudgetBytes(aheadCacheMegabytes: Int, heapMegabytes: Int): Int {
        val wanted = targetBytes(aheadCacheMegabytes) + BACK_BUFFER_BYTES
        val heapCap = (heapMegabytes.coerceAtLeast(MIN_HEAP_MB) / HEAP_SHARE_DIVISOR) * BYTES_PER_MEGABYTE
        return minOf(wanted, heapCap)
    }

    fun heapMegabytes(context: Context): Int {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return manager?.largeMemoryClass ?: MIN_HEAP_MB
    }

    /** Removes the disk cache left by versions before APK-3.9; runs once per process, on IO. */
    fun prepare(context: Context) {
        if (legacyDiskCacheRemoved) return
        synchronized(this) {
            if (legacyDiskCacheRemoved) return
            val appContext = context.applicationContext
            File(appContext.cacheDir, PlaybackStorage.AHEAD_CACHE_DIRECTORY)
                .takeIf(File::exists)
                ?.deleteRecursively()
            PlaybackPreferences(appContext).aheadCacheClearRequested = false
            // APK-3.12 kept device-only listening stats; they are gone (user, 29.10.2026).
            File(appContext.filesDir, "listening-stats.json").takeIf(File::exists)?.delete()
            legacyDiskCacheRemoved = true
        }
    }

    /** Seeking back within this window is served from memory without a new request. */
    const val BACK_BUFFER_MS = 30 * 60 * 1_000

    /** Upper bound on time ahead; the byte budget usually stops loading earlier. */
    const val MAX_AHEAD_MS = 60 * 60 * 1_000
    private const val BYTES_PER_MEGABYTE = 1024 * 1024
    private const val BACK_BUFFER_BYTES = 32 * BYTES_PER_MEGABYTE
    private const val MIN_HEAP_MB = 128
    private const val HEAP_SHARE_DIVISOR = 3
}
