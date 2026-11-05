package ru.e1173.nookast.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.e1173.nookast.offline.EpisodeDownloadManager
import ru.e1173.nookast.offline.EpisodeDownloadWorker

/**
 * «Download started episodes» without downloading twice (user, 28.10.2026, approved disk use for
 * this setting only). As in Media3 and Pocket Casts, the player and the download share one disk
 * cache: playback reads what is already there and adds what it fetches; a background writer fills
 * the rest of the file. When the whole episode is in the cache it is copied into the downloads
 * folder, registered as an ordinary download, and removed from the cache.
 *
 * With the setting off, nothing here is used and streams stay in RAM only (DEC-039).
 */
@OptIn(UnstableApi::class)
internal class EpisodeStreamCache(
    private val context: Context,
    private val upstream: DataSource.Factory,
    private val scope: CoroutineScope,
) {
    private val cache: SimpleCache by lazy {
        val directory = File(context.cacheDir, DIRECTORY)
        SimpleCache(directory, LeastRecentlyUsedCacheEvictor(MAX_BYTES), StandaloneDatabaseProvider(context))
    }
    private val jobs = ConcurrentHashMap<Int, Job>()
    private val writers = ConcurrentHashMap<Int, CacheWriter>()

    /** Playback through the cache: cached bytes are read from disk, new ones are written to it. */
    val playbackFactory: DataSource.Factory by lazy {
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * Fills the rest of [uri] for [episodeId] on an unmetered network, then exports it. A metered
     * network only caches what is played; the fill starts at the next pause or episode on Wi-Fi.
     */
    fun fill(episodeId: Int, uri: Uri, title: String, podcast: String, artworkUrl: String) {
        if (episodeId <= 0 || jobs[episodeId]?.isActive == true) return
        val downloads = EpisodeDownloadManager(context)
        if (downloads.localUriIfReady(episodeId) != null) return
        if (isMetered()) return
        val destination = downloads.beginCacheFill(episodeId, uri, title, podcast, artworkUrl) ?: return
        jobs[episodeId] = scope.launch {
            try {
                val source = playbackFactory.createDataSource() as CacheDataSource
                val writer = CacheWriter(source, DataSpec(uri), null) { length, cached, _ ->
                    if (length > 0) EpisodeDownloadWorker.progress[episodeId] = (cached.toFloat() / length).coerceIn(0f, 1f)
                }
                writers[episodeId] = writer
                writer.cache()
                val bytes = export(uri, destination)
                downloads.completeInApp(episodeId, destination, bytes)
                cache.removeResource(uri.toString())
            } catch (error: Exception) {
                // Interrupted (network, pause of the job, removal): the cached part stays and the
                // next fill resumes from it; the half-written export is dropped.
                File(destination.path + ".part").delete()
                downloads.cancelCacheFill(episodeId)
            } finally {
                EpisodeDownloadWorker.progress.remove(episodeId)
                writers.remove(episodeId)
                jobs.remove(episodeId)
            }
        }
    }

    fun cancel(episodeId: Int) {
        writers[episodeId]?.cancel()
        jobs[episodeId]?.cancel()
    }

    private fun export(uri: Uri, destination: File): Long {
        val part = File(destination.path + ".part")
        destination.parentFile?.mkdirs()
        val reader = CacheDataSource.Factory().setCache(cache).createDataSource()
        var total = 0L
        reader.open(DataSpec(uri))
        try {
            part.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = reader.read(buffer, 0, buffer.size)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    total += read
                }
            }
        } finally {
            reader.close()
        }
        if (destination.exists()) destination.delete()
        check(part.renameTo(destination)) { "cannot move export" }
        return total
    }

    private fun isMetered(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        return manager?.isActiveNetworkMetered ?: true
    }

    fun release() {
        jobs.values.forEach(Job::cancel)
        writers.values.forEach(CacheWriter::cancel)
        runCatching { cache.release() }
    }

    companion object {
        /** The player service's instance, so removing a download can stop its fill. */
        @Volatile
        var active: EpisodeStreamCache? = null

        const val DIRECTORY = "episode-stream-cache"
        private const val MAX_BYTES = 1024L * 1024L * 1024L
    }
}
