package ru.e1173.nookast.data.remote

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.e1173.nookast.core.network.HttpClients

/** Resolves missing podcast artwork from public RSS metadata without forwarding credentials. */
class PodcastArtworkFeedResolver(
    private val httpClient: OkHttpClient = HttpClients.public.newBuilder()
        .connectTimeout(3_500, TimeUnit.MILLISECONDS)
        .readTimeout(5_000, TimeUnit.MILLISECONDS)
        .build(),
) {
    fun resolve(feedUrl: String): String? {
        val url = feedUrl.safeHttpUrl() ?: return null
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/rss+xml, application/xml, text/xml;q=0.9, */*;q=0.2")
            .header("User-Agent", "Nookast/Android")
            .get()
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val output = ByteArrayOutputStream()
                response.body.byteStream().use { input ->
                    val buffer = ByteArray(8_192)
                    var remaining = MAX_FEED_BYTES
                    while (remaining > 0) {
                        val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        remaining -= read
                    }
                }
                extractPodcastArtworkUrl(output.toString(Charsets.UTF_8.name()))
            }
        } catch (_: IOException) {
            null
        }
    }

    private companion object {
        const val MAX_FEED_BYTES = 2 * 1024 * 1024
    }
}

internal fun extractPodcastArtworkUrl(xml: String): String? {
    val candidates = sequenceOf(
        ITUNES_IMAGE.find(xml)?.groupValues?.getOrNull(2),
        RSS_IMAGE.find(xml)?.groupValues?.getOrNull(1),
        MEDIA_IMAGE.find(xml)?.groupValues?.getOrNull(2),
        ATOM_LOGO.find(xml)?.groupValues?.getOrNull(1),
        ATOM_ICON.find(xml)?.groupValues?.getOrNull(1),
    )
    return candidates
        .filterNotNull()
        .map { it.decodeXmlEntities().trim() }
        .mapNotNull(String::safeHttpUrl)
        .firstOrNull()
}

private fun String.safeHttpUrl(): String? = runCatching {
    val uri = URI(trim())
    if ((uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrBlank()) {
        uri.toASCIIString()
    } else {
        null
    }
}.getOrNull()

private fun String.decodeXmlEntities(): String = replace("&amp;", "&", ignoreCase = true)
    .replace("&quot;", "\"", ignoreCase = true)
    .replace("&apos;", "'", ignoreCase = true)
    .replace("&#39;", "'", ignoreCase = true)

private val ITUNES_IMAGE = Regex(
    """<itunes:image\b[^>]*\bhref\s*=\s*([\"'])(.*?)\1""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val RSS_IMAGE = Regex(
    """<image\b[^>]*>.*?<url\b[^>]*>\s*(.*?)\s*</url>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val MEDIA_IMAGE = Regex(
    """<media:(?:thumbnail|content)\b[^>]*\burl\s*=\s*([\"'])(.*?)\1""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val ATOM_LOGO = Regex(
    """<logo\b[^>]*>\s*(.*?)\s*</logo>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val ATOM_ICON = Regex(
    """<icon\b[^>]*>\s*(.*?)\s*</icon>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
