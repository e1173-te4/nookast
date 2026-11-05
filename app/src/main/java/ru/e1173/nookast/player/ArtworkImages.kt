package ru.e1173.nookast.player

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Size
import coil3.toBitmap
import com.google.common.util.concurrent.ListenableFuture
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/** A cover by its feed or PinePods URL; [ArtworkFetcher] knows where the bytes really come from. */
data class ArtworkData(val url: String)

/**
 * Covers through Coil 3 (plan 5.3, U-02, P-06): Coil keeps decoded covers in memory per size and
 * decodes each at the size its screen asks for; the bytes come from [ArtworkBitmapRepository]'s disk
 * cache, which tries the PinePods proxy first and the feed's original URL second, sends the key only
 * to the PinePods origin and checks that the image decodes.
 */
class ArtworkFetcher(
    private val data: ArtworkData,
    private val repository: ArtworkBitmapRepository,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val file = runInterruptible(Dispatchers.IO) { repository.cachedFile(data.url) }
            ?: throw IOException("Artwork is unavailable")
        return SourceFetchResult(
            source = ImageSource(file.toOkioPath(), FileSystem.SYSTEM),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val repository: ArtworkBitmapRepository) : Fetcher.Factory<ArtworkData> {
        override fun create(data: ArtworkData, options: Options, imageLoader: ImageLoader): Fetcher =
            ArtworkFetcher(data, repository)
    }
}

private class ArtworkKeyer : Keyer<ArtworkData> {
    override fun key(data: ArtworkData, options: Options): String = data.url
}

object ArtworkImages {
    @Volatile private var loader: ImageLoader? = null

    /** The app's one image loader: covers only, our disk cache, no second cache of Coil's own. */
    fun loader(context: Context): ImageLoader = loader ?: synchronized(this) {
        loader ?: ImageLoader.Builder(context.applicationContext)
            .components {
                add(ArtworkKeyer())
                add(ArtworkFetcher.Factory(ArtworkBitmapRepository.get(context)))
            }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context.applicationContext, MEMORY_SHARE).build() }
            .diskCache(null)
            .build()
            .also { loader = it }
    }

    /** The key a cover of [url] decoded at [maxPx] has in memory, to show it without waiting. */
    fun memoryKey(url: String, maxPx: Int) = MemoryCache.Key("$url@$maxPx")

    fun request(context: Context, url: String, maxPx: Int): ImageRequest = ImageRequest.Builder(context)
        .data(ArtworkData(url))
        .memoryCacheKey(memoryKey(url, maxPx))
        .size(if (maxPx == Int.MAX_VALUE) Size.ORIGINAL else Size(maxPx, maxPx))
        .precision(Precision.INEXACT)
        // Covers are sampled for their colours (the glow), which hardware bitmaps cannot give.
        .allowHardware(false)
        .build()

    /** The decoded cover, or null when neither source gives one. */
    suspend fun load(context: Context, url: String, maxPx: Int): Bitmap? =
        (loader(context).execute(request(context, url, maxPx)) as? SuccessResult)?.image?.toBitmap()

    /** Whatever is in memory already for [url] at [maxPx]. */
    fun peek(context: Context, url: String, maxPx: Int): Bitmap? =
        loader(context).memoryCache?.get(memoryKey(url, maxPx))?.image?.toBitmap()

    private const val MEMORY_SHARE = 0.2
}

/** Media3 session artwork through the same loader (plan 5.3) instead of a second pipeline. */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class CoilArtworkBitmapLoader(private val context: Context) : BitmapLoader {
    private val delegate = DataSourceBitmapLoader.Builder(context).build()

    override fun supportsMimeType(mimeType: String): Boolean = delegate.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = delegate.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        // Session artwork is the app's own provider URI (P-15); the provider loads the real one.
        if (uri.scheme == "content") return delegate.loadBitmap(uri)
        // A worker thread of its own: blocking there waits for Coil without holding the session.
        return executor.submit<Bitmap> {
            runBlocking { ArtworkImages.load(context, uri.toString(), SESSION_ARTWORK_PX) }
                ?: throw IOException("Artwork is unavailable")
        }
    }

    private companion object {
        const val SESSION_ARTWORK_PX = 1_024
        val executor = MoreExecutors.listeningDecorator(
            Executors.newFixedThreadPool(2) { runnable ->
                Thread(runnable, "nookast-artwork").apply { isDaemon = true }
            },
        )
    }
}
