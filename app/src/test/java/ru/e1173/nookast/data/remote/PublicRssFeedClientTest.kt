package ru.e1173.nookast.data.remote

import java.io.ByteArrayInputStream
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PublicRssFeedClientTest {
    @Test
    fun conditionalRequestContainsValidatorsButNeverCredentials() {
        val request = buildPublicRssRequest(
            "https://feeds.example.test/show.xml".toHttpUrl(),
            RssFeedValidators(etag = "\"v3\"", lastModified = "Tue, 13 Oct 2026 10:00:00 GMT"),
        )

        assertEquals("\"v3\"", request.header("If-None-Match"))
        assertEquals("Tue, 13 Oct 2026 10:00:00 GMT", request.header("If-Modified-Since"))
        assertNull(request.header("Authorization"))
        assertNull(request.header("Api-Key"))
    }

    @Test
    fun parsesRssMetadataAndEpisodes() {
        val xml = """
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
              <channel>
                <title>Example show</title>
                <description>Feed description</description>
                <itunes:author>Feed author</itunes:author>
                <itunes:image href="https://cdn.example.test/feed.jpg"/>
                <item>
                  <guid>episode-42</guid>
                  <title>Episode 42</title>
                  <description>Episode description</description>
                  <pubDate>Tue, 13 Oct 2026 10:00:00 GMT</pubDate>
                  <itunes:duration>1:02:03</itunes:duration>
                  <enclosure url="https://media.example.test/e42.mp3" type="audio/mpeg"/>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val feed = parsePublicRssFeed(ByteArrayInputStream(xml.toByteArray()))

        assertEquals("Example show", feed.title)
        assertEquals("Feed author", feed.author)
        assertEquals("https://cdn.example.test/feed.jpg", feed.artworkUrl)
        assertEquals(1, feed.episodes.size)
        assertEquals("episode-42", feed.episodes.single().guid)
        assertEquals(3_723, feed.episodes.single().durationSeconds)
        assertEquals("2026-10-13T10:00:00Z", feed.episodes.single().publishedAt)
    }

    @Test
    fun parsesAtomEnclosureWithoutTreatingWebLinkAsMedia() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Atom show</title>
              <author><name>Atom author</name></author>
              <entry>
                <id>tag:example.test,2026:1</id>
                <title>Atom episode</title>
                <updated>2026-10-13T10:00:00Z</updated>
                <link rel="alternate" href="https://example.test/episode"/>
                <link rel="enclosure" href="https://media.example.test/atom.mp3"/>
              </entry>
            </feed>
        """.trimIndent()

        val feed = parsePublicRssFeed(ByteArrayInputStream(xml.toByteArray()))

        assertEquals("Atom author", feed.author)
        assertEquals("https://media.example.test/atom.mp3", feed.episodes.single().mediaUrl)
        assertFalse(feed.episodes.single().mediaUrl.contains("/episode"))
    }

    @Test
    fun feedsWithADoctypeOrEntitiesAreRejectedBeforeParsing() {
        val laughs = """
            <?xml version="1.0"?>
            <!DOCTYPE rss [<!ENTITY a "aaaaaaaaaa"><!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;">]>
            <rss><channel><title>&b;</title></channel></rss>
        """.trimIndent()

        val result = runCatching { parsePublicRssFeed(ByteArrayInputStream(laughs.toByteArray())) }

        assertEquals(org.xml.sax.SAXException::class.java, result.exceptionOrNull()?.javaClass)
    }

    @Test
    fun deeplyNestedFeedsAreRejected() {
        val xml = "<rss>" + "<x>".repeat(100) + "</x>".repeat(100) + "</rss>"

        val result = runCatching { parsePublicRssFeed(ByteArrayInputStream(xml.toByteArray())) }

        assertEquals(org.xml.sax.SAXException::class.java, result.exceptionOrNull()?.javaClass)
    }
}
