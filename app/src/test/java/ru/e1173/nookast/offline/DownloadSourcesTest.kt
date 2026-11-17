package ru.e1173.nookast.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.e1173.nookast.auth.AuthSession

class DownloadSourcesTest {
    private val session = AuthSession("https://pods.example.test", userId = 7, apiKey = "secret-key")
    private val rss = "https://media.example.test/42.mp3"

    @Test
    fun `an episode on the server downloads from its copy first, without the key`() {
        val sources = downloadSources(session, 42, rss, onServer = true)

        assertEquals("https://pods.example.test/api/data/stream/42?user_id=7", sources.primaryUrl)
        assertEquals(rss, sources.fallbackUrl)
        listOfNotNull(sources.primaryUrl, sources.fallbackUrl, sources.pinePodsUrl).forEach { url ->
            assertFalse(url.contains("secret-key"))
            assertFalse(url.contains("api_key"))
        }
    }

    @Test
    fun `an episode not on the server downloads from the origin, the server as rescue`() {
        val sources = downloadSources(session, 42, rss, onServer = false)

        assertEquals(rss, sources.primaryUrl)
        assertTrue(sources.fallbackUrl!!.endsWith("/api/episodes/42/download"))
    }

    @Test
    fun `html and xhtml are pages, audio and unknown types are not`() {
        assertTrue(isHtmlMediaType("text/html"))
        assertTrue(isHtmlMediaType("Text/HTML; charset=utf-8"))
        assertTrue(isHtmlMediaType("application/xhtml+xml"))
        assertFalse(isHtmlMediaType("audio/mpeg"))
        assertFalse(isHtmlMediaType("application/octet-stream"))
        assertFalse(isHtmlMediaType(null))
    }
}
