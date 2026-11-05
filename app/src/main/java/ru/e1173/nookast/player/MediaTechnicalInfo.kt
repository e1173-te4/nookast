package ru.e1173.nookast.player

import ru.e1173.nookast.core.network.HttpClients
import java.io.File
import kotlin.math.roundToInt
import okhttp3.OkHttpClient
import okhttp3.Request

data class MediaTechnicalInfo(
    val sizeBytes: Long,
    val bitrateKbps: Int?,
)

internal fun parseTotalBytes(contentRange: String?, contentLength: Long): Long? {
    val totalFromRange = contentRange
        ?.substringAfterLast('/', missingDelimiterValue = "")
        ?.trim()
        ?.takeIf { it != "*" }
        ?.toLongOrNull()
        ?.takeIf { it > 0 }
    return totalFromRange ?: contentLength.takeIf { it > 0 }
}

internal fun bitrateKbps(sizeBytes: Long, durationMs: Long): Int? {
    if (sizeBytes <= 0 || durationMs <= 0) return null
    // bits per millisecond is numerically equal to kilobits per second.
    return ((sizeBytes.toDouble() * 8.0) / durationMs.toDouble())
        .roundToInt()
        .coerceAtLeast(1)
}

/**
 * Bitrate of the selected audio track as the decoder reads it from the stream header. It covers
 * PinePods server copies, whose size the app must not ask for (DEC-029: the chip always shows
 * the quality).
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun selectedAudioKbps(tracks: androidx.media3.common.Tracks): Int? = tracks.groups.asSequence()
    .filter { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }
    .flatMap { group -> (0 until group.length).asSequence().filter(group::isTrackSelected).map(group::getTrackFormat) }
    .mapNotNull { format -> listOf(format.averageBitrate, format.bitrate, format.peakBitrate).firstOrNull { it > 0 } }
    .firstOrNull()
    ?.let { (it / 1000).coerceAtLeast(1) }

class MediaTechnicalInfoClient {
    private val redirectingClient = baseClient(followRedirects = true)
    private val sameOriginClient = baseClient(followRedirects = false)

    fun fetch(url: String, headers: Map<String, String>, durationMs: Long): MediaTechnicalInfo? {
        val client = if (headers.isEmpty()) redirectingClient else sameOriginClient
        val headSize = executeForSize(client, request(url, headers, head = true))
        val size = headSize ?: executeForSize(client, request(url, headers, head = false))
            ?: return null
        return MediaTechnicalInfo(size, bitrateKbps(size, durationMs))
    }

    private fun request(url: String, headers: Map<String, String>, head: Boolean): Request {
        val builder = Request.Builder().url(url)
        headers.forEach(builder::header)
        if (head) builder.head() else builder.header("Range", "bytes=0-0").get()
        return builder.build()
    }

    private fun executeForSize(client: OkHttpClient, request: Request): Long? = runCatching {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            parseTotalBytes(
                contentRange = response.header("Content-Range"),
                contentLength = response.body.contentLength(),
            )
        }
    }.getOrNull()

    companion object {
        fun fromLocalFile(file: File, durationMs: Long): MediaTechnicalInfo? {
            val size = file.length().takeIf { it > 0 } ?: return null
            return MediaTechnicalInfo(size, bitrateKbps(size, durationMs))
        }

        private fun baseClient(followRedirects: Boolean) = HttpClients.probe(followRedirects)
    }
}
