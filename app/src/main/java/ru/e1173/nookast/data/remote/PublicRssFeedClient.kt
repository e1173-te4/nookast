package ru.e1173.nookast.data.remote

import ru.e1173.nookast.core.network.HttpClients
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import javax.xml.parsers.SAXParserFactory
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

data class RssFeedValidators(
    val etag: String?,
    val lastModified: String?,
)

data class PublicRssEpisode(
    val guid: String,
    val title: String,
    val description: String,
    val publishedAt: String,
    val durationSeconds: Int,
    val artworkUrl: String,
    val mediaUrl: String,
    val season: Int? = null,
    val episodeNumber: Int? = null,
)

data class PublicRssFeed(
    val title: String,
    val description: String,
    val author: String,
    val artworkUrl: String,
    val episodes: List<PublicRssEpisode>,
)

sealed interface PublicRssFetchResult {
    val validators: RssFeedValidators
    val finalUrl: String

    data class Modified(
        val feed: PublicRssFeed,
        override val validators: RssFeedValidators,
        override val finalUrl: String,
    ) : PublicRssFetchResult

    data class NotModified(
        override val validators: RssFeedValidators,
        override val finalUrl: String,
    ) : PublicRssFetchResult
}

/** Fetches only public RSS/Atom data. Authentication headers are never accepted or forwarded. */
class PublicRssFeedClient(
    private val httpClient: OkHttpClient = HttpClients.public,
) {
    fun fetch(feedUrl: String, validators: RssFeedValidators): PublicRssFetchResult {
        val url = feedUrl.trim().toHttpUrlOrNull()
            ?.takeIf { it.scheme == "https" || it.scheme == "http" }
            ?: throw QueueLoadException(QueueLoadError.INVALID_RESPONSE)
        val request = buildPublicRssRequest(url, validators)
        try {
            return httpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 304 -> PublicRssFetchResult.NotModified(
                        validators = response.validatorsOr(validators),
                        finalUrl = response.request.url.toString(),
                    )
                    !response.isSuccessful -> throw QueueLoadException(QueueLoadError.SERVER)
                    else -> PublicRssFetchResult.Modified(
                        feed = parsePublicRssFeed(response.requireLimitedBody()),
                        validators = response.validatorsOr(validators),
                        finalUrl = response.request.url.toString(),
                    )
                }
            }
        } catch (error: QueueLoadException) {
            throw error
        } catch (error: IOException) {
            android.util.Log.w("NookastRss", "Feed not loaded: ${error.javaClass.simpleName}: ${error.message?.take(120)}")
            throw QueueLoadException(QueueLoadError.NETWORK, error)
        } catch (error: Exception) {
            // The kind of failure only, never the feed address (it may be private).
            android.util.Log.w("NookastRss", "Unreadable feed: ${error.javaClass.simpleName}: ${error.message?.take(160)}")
            throw QueueLoadException(QueueLoadError.INVALID_RESPONSE, error)
        }
    }
}

internal fun buildPublicRssRequest(
    url: HttpUrl,
    validators: RssFeedValidators,
): Request = Request.Builder()
    .url(url)
    .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9")
    .header("User-Agent", "Nookast/Android")
    .apply {
        validators.etag?.takeIf(String::isNotBlank)?.let { header("If-None-Match", it) }
        validators.lastModified?.takeIf(String::isNotBlank)?.let {
            header("If-Modified-Since", it)
        }
    }
    .get()
    .build()

internal fun parsePublicRssFeed(input: InputStream): PublicRssFeed {
    val factory = SAXParserFactory.newInstance().apply {
        isNamespaceAware = true
        setSafeFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setSafeFeature("http://xml.org/sax/features/external-general-entities", false)
        setSafeFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setSafeFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    }
    val handler = PublicRssHandler()
    factory.newSAXParser().parse(rejectDoctype(LimitedInputStream(input, MAX_RSS_BYTES)), handler)
    return handler.result()
}

/**
 * Android's SAX parser is Expat: the "disallow-doctype-decl" and entity features above are not
 * honoured (X-08). A feed never needs a DTD, so any `<!DOCTYPE` in the prologue rejects it before
 * parsing, which rules out entity expansion and external entities.
 */
private fun rejectDoctype(input: InputStream): InputStream {
    val buffered = java.io.BufferedInputStream(input, PROLOGUE_BYTES)
    buffered.mark(PROLOGUE_BYTES)
    val head = ByteArray(PROLOGUE_BYTES)
    var read = 0
    while (read < head.size) {
        val n = buffered.read(head, read, head.size - read)
        if (n < 0) break
        read += n
    }
    buffered.reset()
    val prologue = String(head, 0, read, Charsets.ISO_8859_1)
    if (prologue.contains("<!DOCTYPE", ignoreCase = true) || prologue.contains("<!ENTITY", ignoreCase = true)) {
        throw org.xml.sax.SAXException("DOCTYPE is not allowed in a feed")
    }
    return buffered
}

private class PublicRssHandler : DefaultHandler() {
    private data class Frame(val name: String, val text: StringBuilder = StringBuilder())
    private data class EpisodeBuilder(
        var guid: String = "",
        var title: String = "",
        var description: String = "",
        var publishedAt: String = "",
        var durationSeconds: Int = 0,
        var artworkUrl: String = "",
        var mediaUrl: String = "",
        var season: Int? = null,
        var episodeNumber: Int? = null,
    )

    private val stack = ArrayDeque<Frame>()
    private val episodes = mutableListOf<PublicRssEpisode>()
    private var item: EpisodeBuilder? = null
    private var feedTitle = ""
    private var feedDescription = ""
    private var feedAuthor = ""
    private var feedArtwork = ""
    private var channelImageDepth = -1
    private var elementCount = 0

    override fun startElement(
        uri: String?,
        localName: String?,
        qName: String?,
        attributes: Attributes,
    ) {
        if (stack.size >= MAX_DEPTH || ++elementCount > MAX_ELEMENTS) {
            throw org.xml.sax.SAXException("Feed structure exceeds limits")
        }
        val name = elementName(localName, qName)
        stack.addLast(Frame(name))
        when (name) {
            "item", "entry" -> item = EpisodeBuilder()
            "image" -> if (item == null && namespacePrefix(qName).isEmpty()) {
                channelImageDepth = stack.size
            }
            "enclosure" -> item?.mediaUrl = attributes.value("url").safePublicUrl()
            "link" -> if (item != null && attributes.value("rel").equals("enclosure", true)) {
                item?.mediaUrl = attributes.value("href").safePublicUrl()
            }
            "itunes:image", "media:thumbnail" -> {
                val image = (attributes.value("href") ?: attributes.value("url")).safePublicUrl()
                if (item == null) feedArtwork = image.ifBlank { feedArtwork }
                else item?.artworkUrl = image.ifBlank { item?.artworkUrl.orEmpty() }
            }
            "media:content" -> {
                val isImage = attributes.value("medium").equals("image", true) ||
                    attributes.value("type")?.startsWith("image/", true) == true
                if (isImage) {
                    val image = attributes.value("url").safePublicUrl()
                    if (item == null) feedArtwork = image.ifBlank { feedArtwork }
                    else item?.artworkUrl = image.ifBlank { item?.artworkUrl.orEmpty() }
                }
            }
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        // Only the innermost element collects text, up to a limit (X-08, N-06): appending to every
        // open frame copied a large feed once per nesting level.
        val frame = stack.lastOrNull() ?: return
        val room = MAX_FIELD_CHARS - frame.text.length
        if (room > 0) frame.text.append(ch, start, minOf(length, room))
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        val name = elementName(localName, qName)
        val frame = stack.removeLastOrNull() ?: return
        val value = frame.text.toString().trim()
        val active = item
        if (active != null && name !in setOf("item", "entry")) {
            when (name) {
                "guid", "id" -> active.guid = value.ifBlank { active.guid }
                "title" -> active.title = value.ifBlank { active.title }
                "description", "summary", "content", "itunes:summary" ->
                    active.description = value.ifBlank { active.description }
                "pubdate", "published", "updated" ->
                    active.publishedAt = normalizeRssDate(value).ifBlank { active.publishedAt }
                "itunes:duration", "duration" ->
                    active.durationSeconds = parseRssDuration(value).coerceAtLeast(active.durationSeconds)
                // itunes:season / itunes:episode, or podcast:season / podcast:episode (Podcasting 2.0).
                "itunes:season", "season" -> active.season = value.toIntOrNull()?.takeIf { it > 0 } ?: active.season
                "itunes:episode", "episode" -> active.episodeNumber = value.toIntOrNull()?.takeIf { it >= 0 } ?: active.episodeNumber
            }
        } else if (active == null) {
            when (name) {
                "title" -> feedTitle = value.ifBlank { feedTitle }
                "description", "subtitle" -> feedDescription = value.ifBlank { feedDescription }
                "itunes:author", "managingeditor", "name" -> feedAuthor = value.ifBlank { feedAuthor }
                "logo", "icon" -> feedArtwork = value.safePublicUrl().ifBlank { feedArtwork }
                "url" -> if (channelImageDepth > 0) {
                    feedArtwork = value.safePublicUrl().ifBlank { feedArtwork }
                }
            }
        }
        if (name == "item" || name == "entry") {
            item?.let { candidate ->
                val mediaUrl = candidate.mediaUrl.safePublicUrl()
                if (candidate.title.isNotBlank() && mediaUrl.isNotBlank()) {
                    episodes += PublicRssEpisode(
                        guid = candidate.guid.trim(),
                        title = candidate.title.trim(),
                        description = candidate.description.trim(),
                        publishedAt = candidate.publishedAt.trim(),
                        durationSeconds = candidate.durationSeconds.coerceAtLeast(0),
                        artworkUrl = candidate.artworkUrl.safePublicUrl(),
                        mediaUrl = mediaUrl,
                        season = candidate.season,
                        episodeNumber = candidate.episodeNumber,
                    )
                }
            }
            item = null
        }
        if (name == "image" && channelImageDepth == stack.size + 1) channelImageDepth = -1
    }

    fun result(): PublicRssFeed {
        if (episodes.isEmpty() && feedTitle.isBlank()) {
            throw IllegalArgumentException("RSS/Atom document contains no feed data")
        }
        return PublicRssFeed(
            title = feedTitle.trim(),
            description = feedDescription.trim(),
            author = feedAuthor.trim(),
            artworkUrl = feedArtwork.safePublicUrl(),
            episodes = episodes,
        )
    }
}

private fun Response.validatorsOr(previous: RssFeedValidators): RssFeedValidators =
    RssFeedValidators(
        etag = header("ETag")?.takeIf(String::isNotBlank) ?: previous.etag,
        lastModified = header("Last-Modified")?.takeIf(String::isNotBlank)
            ?: previous.lastModified,
    )

private fun Response.requireLimitedBody(): InputStream {
    val responseBody: ResponseBody = body
    if (responseBody.contentLength() > MAX_RSS_BYTES) {
        throw QueueLoadException(QueueLoadError.INVALID_RESPONSE)
    }
    return responseBody.byteStream()
}

private class LimitedInputStream(
    input: InputStream,
    private var remaining: Long,
) : FilterInputStream(input) {
    override fun read(): Int {
        if (remaining <= 0) {
            if (super.read() < 0) return -1
            throw IOException("RSS body exceeds limit")
        }
        val value = super.read()
        if (value >= 0) remaining--
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remaining <= 0) {
            if (super.read() < 0) return -1
            throw IOException("RSS body exceeds limit")
        }
        val read = super.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
        if (read > 0) remaining -= read.toLong()
        return read
    }
}

private fun SAXParserFactory.setSafeFeature(name: String, value: Boolean) {
    runCatching { setFeature(name, value) }
}

private fun Attributes.value(name: String): String? = (0 until length)
    .firstOrNull { index -> getLocalName(index).equals(name, true) || getQName(index).equals(name, true) }
    ?.let(::getValue)

private fun elementName(localName: String?, qName: String?): String {
    val local = localName.orEmpty().lowercase(Locale.ROOT)
    val qualified = qName.orEmpty().lowercase(Locale.ROOT)
    val prefix = namespacePrefix(qName)
    return if (prefix in setOf("itunes", "media")) "$prefix:$local" else local.ifBlank { qualified }
}

private fun namespacePrefix(qName: String?): String = qName.orEmpty()
    .substringBefore(':', "")
    .lowercase(Locale.ROOT)

private fun String?.safePublicUrl(): String = this?.trim()?.toHttpUrlOrNull()
    ?.takeIf { it.scheme == "https" || it.scheme == "http" }
    ?.toString()
    .orEmpty()

internal fun parseRssDuration(value: String): Int {
    val parts = value.trim().split(':')
    return when (parts.size) {
        1 -> parts[0].toDoubleOrNull()?.toInt()?.coerceAtLeast(0) ?: 0
        2 -> parts[0].toIntOrNull()?.let { minutes ->
            parts[1].toIntOrNull()?.let { seconds -> minutes * 60 + seconds }
        } ?: 0
        3 -> parts[0].toIntOrNull()?.let { hours ->
            parts[1].toIntOrNull()?.let { minutes ->
                parts[2].toIntOrNull()?.let { seconds -> hours * 3_600 + minutes * 60 + seconds }
            }
        } ?: 0
        else -> 0
    }
}

internal fun normalizeRssDate(value: String): String {
    val raw = value.trim()
    if (raw.isBlank()) return ""
    val parsers = sequenceOf<() -> Instant>(
        { Instant.parse(raw) },
        { OffsetDateTime.parse(raw).toInstant() },
        { ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() },
    )
    parsers.forEach { parser ->
        try {
            return parser().toString()
        } catch (_: DateTimeParseException) {
            // Try the next common RSS representation.
        }
    }
    return raw
}

// 20 MB (user, 29.10.2026): BeardyCast with 654 episodes is 6 MB and was refused at 5, so its
// season and episode numbers never arrived. The parser streams, so the size costs no memory.
private const val MAX_RSS_BYTES = 20L * 1024L * 1024L
private const val PROLOGUE_BYTES = 4 * 1024
private const val MAX_FIELD_CHARS = 64 * 1024
private const val MAX_DEPTH = 64
private const val MAX_ELEMENTS = 200_000
