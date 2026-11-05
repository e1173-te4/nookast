package ru.e1173.nookast.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.StringReader

/** The search card reads a found podcast's own feed (user, 2.11.2026). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class FeedPreviewClientTest {
    @Test
    fun `channel fields and the latest episodes are read`() {
        val rss = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
              <channel>
                <title>Завтракаст</title>
                <link>https://zavtracast.example/</link>
                <language>ru</language>
                <itunes:author>Zavtracast</itunes:author>
                <description><![CDATA[<p>О играх и кино</p>]]></description>
                <image><url>https://img.example/small.jpg</url><title>Ignored</title></image>
                <itunes:image href="https://img.example/big.jpg"/>
                <item><title>Выпуск 3</title><pubDate>Tue, 28 Oct 2026 10:00:00 +0300</pubDate><itunes:duration>1:02:03</itunes:duration></item>
                <item><title>Выпуск 2</title><pubDate>Tue, 21 Oct 2026 10:00:00 GMT</pubDate><itunes:duration>3540</itunes:duration></item>
                <item><title>Выпуск 1</title><pubDate>broken</pubDate><itunes:duration>59:00</itunes:duration></item>
              </channel>
            </rss>
        """.trimIndent()

        val preview = FeedPreviewClient.parse(StringReader(rss))

        assertEquals("<p>О играх и кино</p>", preview.description)
        assertEquals("Zavtracast", preview.author)
        assertEquals("ru", preview.language)
        assertEquals("https://zavtracast.example/", preview.website)
        assertEquals("https://img.example/big.jpg", preview.artworkUrl)
        assertEquals(3, preview.episodeCount)
        assertEquals(listOf("Выпуск 3", "Выпуск 2", "Выпуск 1"), preview.latest.map(FeedEpisode::title))
        assertEquals("2026-10-28T07:00:00", preview.latest[0].publishedAt)
        assertEquals(3723, preview.latest[0].durationSeconds)
        assertEquals(3540, preview.latest[1].durationSeconds)
        assertNull(preview.latest[2].publishedAt)
    }

    @Test
    fun `only the newest five episodes are kept but all are counted`() {
        val items = (1..8).joinToString("") { "<item><title>E$it</title></item>" }
        val preview = FeedPreviewClient.parse(StringReader("<rss><channel><title>T</title>$items</channel></rss>"))

        assertEquals(8, preview.episodeCount)
        assertEquals(FeedPreviewClient.LATEST, preview.latest.size)
    }

    @Test
    fun `durations and dates in odd forms`() {
        assertNull(FeedPreviewClient.durationSeconds("abc"))
        assertEquals(90, FeedPreviewClient.durationSeconds("1:30"))
        assertEquals("2026-10-21T10:00:00", FeedPreviewClient.isoDate("2026-10-21T10:00:00Z"))
    }
}
