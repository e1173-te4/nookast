package ru.e1173.nookast.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PodcastArtworkFeedResolverTest {
    @Test
    fun prefersItunesArtworkAndDecodesAmpersand() {
        val xml = """
            <rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
              <channel>
                <itunes:image href="https://cdn.example/cover.jpg?size=600&amp;crop=1" />
                <image><url>https://fallback.example/cover.jpg</url></image>
              </channel>
            </rss>
        """.trimIndent()

        assertEquals(
            "https://cdn.example/cover.jpg?size=600&crop=1",
            extractPodcastArtworkUrl(xml),
        )
    }

    @Test
    fun supportsClassicRssAndMediaThumbnail() {
        assertEquals(
            "https://example.org/rss.png",
            extractPodcastArtworkUrl("<rss><channel><image><url>https://example.org/rss.png</url></image></channel></rss>"),
        )
        assertEquals(
            "https://example.org/media.png",
            extractPodcastArtworkUrl("<rss xmlns:media='x'><media:thumbnail url='https://example.org/media.png'/></rss>"),
        )
    }

    @Test
    fun rejectsNonNetworkArtworkSchemes() {
        assertNull(extractPodcastArtworkUrl("<itunes:image href='file:///secret.png'/>"))
    }

    @Test
    fun supportsAtomLogoWhenRssImageTagsAreAbsent() {
        assertEquals(
            "https://example.org/atom-cover.png",
            extractPodcastArtworkUrl(
                "<feed xmlns='http://www.w3.org/2005/Atom'><logo>" +
                    "https://example.org/atom-cover.png</logo></feed>",
            ),
        )
    }
}
