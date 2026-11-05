package ru.e1173.nookast.player

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.scale
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.runBlocking
import ru.e1173.nookast.BuildConfig
import ru.e1173.nookast.app.appGraph
import ru.e1173.nookast.data.remote.ArtworkUrlResolver
import ru.e1173.nookast.offline.EpisodeDownloadManager

/**
 * Serves episode artwork to external media controllers (head-unit shell, system media UI) as
 * `content://<applicationId>.artwork/episode/<playbackId>`.
 *
 * Controllers cannot send the PinePods API key, so they cannot load the proxy URL themselves.
 * This provider loads the image through [ArtworkBitmapRepository] (which adds the key only for
 * the configured server), scales it to at most [MAX_EDGE_PX] and serves a JPEG copy from the
 * cache. Only artwork of locally known episode IDs is served: a caller cannot make the app fetch
 * an arbitrary URL. It is exported without a permission because head units do not hold one.
 */
class ArtworkContentProvider : ContentProvider() {
    private val generationLock = Any()

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = if (playbackId(uri) != null) MIME_TYPE else null

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Artwork is read-only")
        val id = playbackId(uri) ?: throw FileNotFoundException("Unknown artwork $uri")
        val context = requireNotNull(context)
        val file = exportFile(context, id)
        // Serialized: concurrent requests for one episode would share the temporary file.
        synchronized(generationLock) {
            if (!file.isFile || System.currentTimeMillis() - file.lastModified() > MAX_AGE_MS) {
                val bitmap = loadArtwork(context, id)
                    ?: throw FileNotFoundException("No artwork for $id")
                writeJpeg(scaled(bitmap), file)
            }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun loadArtwork(context: Context, id: Int): Bitmap? {
        val graph = context.appGraph
        val entry = runBlocking {
            PlaybackItemFactory(graph.database, EpisodeDownloadManager(context)).findEntry(id)
        } ?: return null
        val rawUrl = entry.artworkUrl?.takeIf(String::isNotBlank) ?: return null
        val repository = ArtworkBitmapRepository.get(context)
        val sources = graph.session.load()?.let { ArtworkUrlResolver.sources(it.serverUrl, rawUrl) }
        return listOfNotNull(sources?.primaryUrl, sources?.fallbackUrl, rawUrl)
            .distinct()
            .firstNotNullOfOrNull { url -> runCatching { repository.load(url) }.getOrNull() }
    }

    private fun writeJpeg(bitmap: Bitmap, destination: File) {
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, destination.name + ".tmp")
        temporary.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        if (!temporary.renameTo(destination)) {
            destination.delete()
            temporary.renameTo(destination)
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        const val AUTHORITY = "${BuildConfig.APPLICATION_ID}.artwork"
        private const val PATH_EPISODE = "episode"
        private const val MIME_TYPE = "image/jpeg"
        private const val MAX_EDGE_PX = 512
        private const val JPEG_QUALITY = 88
        private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1_000
        private const val EXPORT_DIRECTORY = "artwork-export"

        fun uriFor(playbackId: Int): Uri = Uri.Builder()
            .scheme("content")
            .authority(AUTHORITY)
            .appendPath(PATH_EPISODE)
            .appendPath(playbackId.toString())
            .build()

        internal fun playbackId(uri: Uri): Int? {
            if (uri.authority != AUTHORITY) return null
            val segments = uri.pathSegments
            if (segments.size != 2 || segments[0] != PATH_EPISODE) return null
            return segments[1].toIntOrNull()?.takeIf { it != 0 }
        }

        internal fun scaledSize(width: Int, height: Int, maxEdge: Int = MAX_EDGE_PX): Pair<Int, Int> {
            val longest = maxOf(width, height)
            if (longest <= maxEdge) return width to height
            val ratio = maxEdge.toDouble() / longest
            return maxOf(1, (width * ratio).toInt()) to maxOf(1, (height * ratio).toInt())
        }

        private fun scaled(bitmap: Bitmap): Bitmap {
            val (width, height) = scaledSize(bitmap.width, bitmap.height)
            return if (width == bitmap.width && height == bitmap.height) {
                bitmap
            } else {
                bitmap.scale(width, height)
            }
        }

        private fun exportFile(context: Context, id: Int): File =
            File(File(context.cacheDir, EXPORT_DIRECTORY), "episode-$id.jpg")
    }
}
