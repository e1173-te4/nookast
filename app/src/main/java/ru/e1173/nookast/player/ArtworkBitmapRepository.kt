package ru.e1173.nookast.player

import ru.e1173.nookast.core.network.HttpClients
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.e1173.nookast.app.appGraph
import ru.e1173.nookast.core.session.SessionRepository
import ru.e1173.nookast.data.remote.ArtworkUrlResolver

/** PinePods-first artwork loading with RSS fallback and a bounded process-safe disk cache. */
class ArtworkBitmapRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val sessionStore = appContext.appGraph.session
    private val cacheDirectory = File(appContext.cacheDir, CACHE_DIRECTORY)
    private val keyLocks = ConcurrentHashMap<String, Any>()
    private val pruneLock = Any()

    /**
     * The cover's file in the disk cache, fetched when missing or older than a week (Coil reads it
     * and decodes it at the size each screen needs, plan 5.3). A URL that recently failed is not
     * fetched again for [FAILED_RETRY_MS]; a stale file is better than a blank cover offline.
     */
    fun cachedFile(rawUrl: String): File? {
        if (rawUrl.isBlank()) return null
        val file = File(cacheDirectory, "${cacheKey(rawUrl)}.image")
        return if (isUsable(rawUrl)) file else file.takeIf { it.isFile && it.length() in 1..MAX_ARTWORK_BYTES }
    }

    /** A decoded cover for the media session's content provider (head units), at most [maxPx]. */
    fun load(rawUrl: String, maxPx: Int = MAX_ARTWORK_DIMENSION): Bitmap? = cachedFile(rawUrl)?.let { decodeFile(it, maxPx) }

    /**
     * Whether [rawUrl] yields a decodable image, without decoding pixels: only the bounds are
     * read. Catalog refreshes use it to choose podcast artwork; decoding full bitmaps there held
     * up to four 16 MB images at once and starved the UI of memory (startup freeze on APK-1).
     * The bytes are still cached on disk for the later real load.
     */
    fun isUsable(rawUrl: String): Boolean {
        if (rawUrl.isBlank()) return false
        val session = sessionStore.load()
        val cacheKey = cacheKey(rawUrl, session?.serverUrl)
        val lock = keyLocks.getOrPut(cacheKey) { Any() }
        return try {
            synchronized(lock) {
                val cacheFile = File(cacheDirectory, "$cacheKey.image")
                val fresh = System.currentTimeMillis() - cacheFile.lastModified() <= FRESH_CACHE_MS
                if (fresh && cacheFile.isFile && cacheFile.length() in 1..MAX_ARTWORK_BYTES &&
                    runCatching { imageBounds(cacheFile.readBytes()) }.getOrNull() != null
                ) {
                    return@synchronized true
                }
                val failedAt = failedFetches[cacheKey]
                if (failedAt != null && SystemClock.elapsedRealtime() - failedAt < FAILED_RETRY_MS) {
                    return@synchronized false
                }
                val probed = fetchFirstDecodable(rawUrl, session?.serverUrl, session?.apiKey, ::imageBounds)
                if (probed == null) {
                    failedFetches[cacheKey] = SystemClock.elapsedRealtime()
                    return@synchronized false
                }
                failedFetches.remove(cacheKey)
                writeCacheAtomically(cacheFile, probed.bytes)
                pruneCache()
                true
            }
        } finally {
            keyLocks.remove(cacheKey, lock)
        }
    }

    private fun <T> fetchFirstDecodable(
        rawUrl: String,
        serverUrl: String?,
        apiKey: String?,
        decode: (ByteArray) -> T?,
    ): DecodedArtwork<T>? {
        val sources = if (serverUrl != null) {
            ArtworkUrlResolver.sourcesForRequest(serverUrl, rawUrl)
        } else {
            ArtworkUrlResolver.Sources(rawUrl, null)
        }
        return firstDecodedArtwork(
            sources = listOfNotNull(sources.primaryUrl, sources.fallbackUrl).distinct(),
            fetch = { url ->
                fetch(
                    url = url,
                    apiKey = apiKey?.takeIf {
                        serverUrl != null && ArtworkUrlResolver.isPinePodsImageProxyRequest(
                            serverUrl,
                            url,
                        )
                    },
                )
            },
            decode = decode,
        )
    }

    private fun fetch(url: String, apiKey: String?): ByteArray? {
        val request = runCatching {
            Request.Builder()
                .url(url)
                .get()
                .apply {
                    if (!apiKey.isNullOrBlank()) header(PINEPODS_API_KEY_HEADER, apiKey)
                }
                .build()
        }.getOrNull() ?: return null
        val client = if (apiKey == null) externalClient else pinePodsClient
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val declaredLength = response.body.contentLength()
                if (declaredLength > MAX_ARTWORK_BYTES) return@use null
                response.body.byteStream().readLimited(MAX_ARTWORK_BYTES)
                    ?.takeIf { declaredLength <= 0 || it.size.toLong() >= declaredLength }
            }
        }.getOrNull()?.takeIf(::isCompleteImage)
    }

    private fun writeCacheAtomically(destination: File, bytes: ByteArray) {
        runCatching {
            if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) return
            val temporary = File(cacheDirectory, "${destination.name}.part")
            temporary.outputStream().use { stream ->
                stream.write(bytes)
                stream.flush()
            }
            if (!temporary.renameTo(destination)) {
                destination.delete()
                temporary.renameTo(destination)
            }
        }
    }

    /**
     * Keeps the disk cache under [MAX_DISK_CACHE_BYTES], oldest first. Ages and sizes are read
     * once before sorting: other threads write the cache concurrently, and sorting on live
     * `lastModified` values broke TimSort's contract and crashed the app (APK-2, 25.10.2026).
     */
    private fun pruneCache(): Unit = synchronized(pruneLock) {
        val files = cacheDirectory.listFiles { file -> file.isFile && file.name.endsWith(".image") }
            ?.map { file -> CachedFile(file, file.lastModified(), file.length()) }
            ?.sortedBy(CachedFile::modifiedAt)
            ?: return
        var total = files.sumOf(CachedFile::length)
        for (entry in files) {
            if (total <= MAX_DISK_CACHE_BYTES) break
            if (entry.file.delete()) total -= entry.length
        }
    }

    private fun decodeFile(file: File, maxPx: Int): Bitmap? {
        if (!file.isFile || file.length() <= 0 || file.length() > MAX_ARTWORK_BYTES) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        // A cut-off file decodes "successfully" with a transparent lower part, through which the
        // letter placeholder showed as a dark strip (user, 28.10.2026). Drop it and fetch again.
        if (!isCompleteImage(bytes)) {
            file.delete()
            return null
        }
        return decodeBytes(bytes, maxPx).also { if (it == null) file.delete() }
    }

    companion object {
        private const val CACHE_DIRECTORY = "artwork-cache"
        private const val PINEPODS_API_KEY_HEADER = "Api-Key"

        // Full-width Now Playing on the phone (about 1080 px): decodes land between 1024 and 2047 px.
        private const val MAX_ARTWORK_DIMENSION = 1_024
        private const val MAX_ARTWORK_BYTES = 16L * 1024L * 1024L
        private const val MAX_DISK_CACHE_BYTES = 96L * 1024L * 1024L
        private const val MIN_MEMORY_CACHE_KB = 32 * 1_024

        // A sixth of the heap: about 40 to 80 MB on current phones, enough for a full podcast grid.
        private const val FRESH_CACHE_MS = 7L * 24L * 60L * 60L * 1_000L
        private const val FAILED_RETRY_MS = 10L * 60L * 1_000L
        private val failedFetches = ConcurrentHashMap<String, Long>()

        private val pinePodsClient = baseClient(followRedirects = false)
        private val externalClient = baseClient(followRedirects = true)

        @Volatile
        private var instance: ArtworkBitmapRepository? = null

        fun get(context: Context): ArtworkBitmapRepository = instance ?: synchronized(this) {
            instance ?: ArtworkBitmapRepository(context).also { instance = it }
        }

        /**
         * False for a JPEG without its end marker or a PNG without IEND: a truncated transfer.
         * Other formats are trusted to the decoder.
         */
        internal fun isCompleteImage(bytes: ByteArray): Boolean {
            if (bytes.size < 4) return false
            val tail = bytes.copyOfRange(maxOf(0, bytes.size - 64), bytes.size)
            val jpeg = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
            if (jpeg) {
                return (0 until tail.size - 1).any { tail[it] == 0xFF.toByte() && tail[it + 1] == 0xD9.toByte() }
            }
            val png = bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
                bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()
            if (png) return String(tail, Charsets.ISO_8859_1).contains("IEND")
            return true
        }

        /** Width and height when [bytes] is a decodable image, without allocating pixels. */
        internal fun imageBounds(bytes: ByteArray): Pair<Int, Int>? {
            if (bytes.isEmpty() || bytes.size > MAX_ARTWORK_BYTES) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            return bounds.outWidth to bounds.outHeight
        }

        internal fun decodeBytes(bytes: ByteArray, maxPx: Int = MAX_ARTWORK_DIMENSION): Bitmap? {
            if (bytes.isEmpty() || bytes.size > MAX_ARTWORK_BYTES) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val target = artworkSizeBucket(maxPx)
            val sample = artworkSampleSize(bounds.outWidth, bounds.outHeight, target)
            val sampledLongest = maxOf(bounds.outWidth, bounds.outHeight) / sample
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                // Finish the reduction exactly: power-of-two sampling alone can leave twice the size.
                if (sampledLongest > target) {
                    inScaled = true
                    inDensity = sampledLongest
                    inTargetDensity = target
                }
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }

        private fun baseClient(followRedirects: Boolean) = HttpClients.artwork(followRedirects)

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun cacheKey(rawUrl: String, serverUrl: String? = sessionStore.load()?.serverUrl): String =
        sha256("${serverUrl.orEmpty()}\n${rawUrl.trim()}")
}

/** Decode sizes are rounded up to a few buckets so list and hero covers share cache entries. */
internal fun artworkSizeBucket(maxPx: Int): Int = ARTWORK_SIZE_BUCKETS.firstOrNull { it >= maxPx }
    ?: ARTWORK_SIZE_BUCKETS.last()

/** Largest power-of-two subsampling that keeps the longest side at or above [targetPx]. */
internal fun artworkSampleSize(width: Int, height: Int, targetPx: Int): Int {
    var sample = 1
    while (maxOf(width, height) / (sample * 2) >= targetPx) sample *= 2
    return sample
}

private val ARTWORK_SIZE_BUCKETS = listOf(128, 256, 384, 512, 1_024)

private class CachedFile(val file: File, val modifiedAt: Long, val length: Long)

internal data class DecodedArtwork<T>(val bytes: ByteArray, val value: T)

internal fun <T> retainCachedArtwork(cached: T?, refreshed: T?): T? = refreshed ?: cached

/** Continue past HTTP-success error bodies until a source actually decodes as an image. */
internal fun <T> firstDecodedArtwork(
    sources: List<String>,
    fetch: (String) -> ByteArray?,
    decode: (ByteArray) -> T?,
): DecodedArtwork<T>? {
    sources.forEach { source ->
        val bytes = fetch(source) ?: return@forEach
        val decoded = decode(bytes) ?: return@forEach
        return DecodedArtwork(bytes, decoded)
    }
    return null
}

private fun InputStream.readLimited(maxBytes: Long): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) return null
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
