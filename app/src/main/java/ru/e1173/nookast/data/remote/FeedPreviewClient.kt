package ru.e1173.nookast.data.remote

import android.util.Xml
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import ru.e1173.nookast.core.network.HttpClients
import java.io.Reader
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** An episode of a feed not in the library, as the search card lists it. */
data class FeedEpisode(
    val title: String,
    /** ISO local date-time in UTC, as the server gives dates; null when the feed's date is unreadable. */
    val publishedAt: String?,
    val durationSeconds: Int?,
)

/** What a podcast's own feed tells about it before it is added. */
data class FeedPreview(
    val description: String,
    val author: String,
    val language: String,
    val website: String,
    val artworkUrl: String,
    val episodeCount: Int,
    /** The newest episodes first, at most [FeedPreviewClient.LATEST]. */
    val latest: List<FeedEpisode>,
)

/**
 * Reads a found podcast's RSS feed from the device for its search card (user, 2.11.2026): the
 * description, details and the latest episodes. Nothing is stored; adding stays a server action.
 */
class FeedPreviewClient(private val client: OkHttpClient = HttpClients.public) {
    fun load(feedUrl: String): FeedPreview {
        val request = Request.Builder().url(feedUrl).header("Accept", "application/rss+xml, application/xml, text/xml").get().build()
        return client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "feed: ${response.code}" }
            parse(response.body.charStream())
        }
    }

    companion object {
        const val LATEST = 5

        internal fun parse(reader: Reader): FeedPreview {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(reader)
            val channel = mutableMapOf<String, String>()
            val episodes = mutableListOf<FeedEpisode>()
            var count = 0
            var item: MutableMap<String, String>? = null
            var depthInItem = 0
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name.lowercase()
                        if (name == "item" || name == "entry") {
                            item = mutableMapOf()
                            depthInItem = parser.depth
                            continue
                        }
                        val target = item ?: channel
                        // Only the channel's own fields: an <image> block's <url> or <title> is not the podcast's.
                        if (item == null && parser.depth != CHANNEL_FIELD_DEPTH) {
                            if (name == "itunes:image" && "image" !in channel) parser.getAttributeValue(null, "href")?.let { channel["image"] = it }
                            if (name == "url" && "imageUrl" !in channel) channel["imageUrl"] = readText(parser)
                            continue
                        }
                        when (name) {
                            "itunes:image" -> parser.getAttributeValue(null, "href")?.let { target.putIfAbsent("image", it) }
                            else -> if (item == null || count < LATEST) {
                                val text = readText(parser)
                                if (text.isNotBlank()) target.putIfAbsent(name, text)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name.lowercase()
                        if ((name == "item" || name == "entry") && item != null && parser.depth == depthInItem) {
                            if (count < LATEST) episodes += item.toEpisode()
                            count++
                            item = null
                        }
                    }
                }
            }
            return FeedPreview(
                description = channel["itunes:summary"] ?: channel["description"] ?: channel["content:encoded"].orEmpty(),
                author = channel["itunes:author"] ?: channel["author"] ?: channel["itunes:owner"].orEmpty(),
                language = channel["language"].orEmpty(),
                website = channel["link"].orEmpty(),
                artworkUrl = channel["image"] ?: channel["imageUrl"].orEmpty(),
                episodeCount = count,
                // Feeds list the newest first as a rule; a feed in the other order is sorted by date.
                latest = episodes.sortedByDescending { it.publishedAt.orEmpty() },
            )
        }

        /** The text of the current tag, CDATA included; a tag with children gives nothing. */
        private fun readText(parser: XmlPullParser): String {
            val text = StringBuilder()
            val depth = parser.depth
            while (true) {
                when (parser.next()) {
                    XmlPullParser.TEXT, XmlPullParser.CDSECT -> text.append(parser.text)
                    XmlPullParser.START_TAG -> return skipRest(parser, depth)
                    XmlPullParser.END_TAG -> if (parser.depth == depth) return text.toString().trim()
                    XmlPullParser.END_DOCUMENT -> return text.toString().trim()
                }
            }
        }

        private fun skipRest(parser: XmlPullParser, depth: Int): String {
            while (!(parser.next() == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (parser.eventType == XmlPullParser.END_DOCUMENT) break
            }
            return ""
        }

        private fun Map<String, String>.toEpisode() = FeedEpisode(
            title = this["title"].orEmpty(),
            publishedAt = (this["pubdate"] ?: this["published"] ?: this["updated"])?.let(::isoDate),
            durationSeconds = this["itunes:duration"]?.let(::durationSeconds),
        )

        /** RFC 822 dates of RSS (or ISO dates of Atom) as UTC «yyyy-MM-ddTHH:mm:ss». */
        internal fun isoDate(value: String): String? {
            val text = value.trim()
            // The weekday is left out: feeds often name the wrong one, and the strict parser refuses those.
            val parsed = runCatching { ZonedDateTime.parse(text.substringAfter(',').trim(), DateTimeFormatter.RFC_1123_DATE_TIME) }
                .recoverCatching { ZonedDateTime.parse(text) }
                .getOrNull() ?: return null
            return parsed.withZoneSameInstant(ZoneOffset.UTC).format(ISO_SECONDS)
        }

        /** «3600», «59:12» or «1:02:03». */
        internal fun durationSeconds(value: String): Int? {
            val parts = value.trim().split(':').map { it.trim().toIntOrNull() ?: return null }
            if (parts.isEmpty() || parts.size > 3) return null
            return parts.fold(0) { total, part -> total * 60 + part }.takeIf { it > 0 }
        }

        private val ISO_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

        /** rss → channel → field. */
        private const val CHANNEL_FIELD_DEPTH = 3
    }
}
