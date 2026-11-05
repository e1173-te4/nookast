package ru.e1173.nookast.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSourceResolverTest {
    @Test
    fun `RSS enclosure is primary and PinePods full file endpoint is fallback`() {
        val result = MediaSourceResolver.sources(
            serverUrl = "https://pinepods.example/base",
            episodeId = 42,
            rssMediaUrl = "https://cdn.example/episodes/42.mp3",
        )

        assertEquals("https://cdn.example/episodes/42.mp3", result.primaryUrl)
        assertEquals("https://pinepods.example/api/episodes/42/download", result.fallbackUrl)
        assertEquals("https://pinepods.example/api/episodes/42/download", result.pinePodsUrl)
    }

    @Test
    fun `invalid RSS value falls back to valid PinePods source`() {
        val result = MediaSourceResolver.sources(
            serverUrl = "https://pinepods.example/",
            episodeId = 42,
            rssMediaUrl = "not a URL",
        )

        assertNull(result.primaryUrl)
        assertEquals("https://pinepods.example/api/episodes/42/download", result.fallbackUrl)
        assertEquals("https://pinepods.example/api/episodes/42/download", result.pinePodsUrl)
    }

    @Test
    fun `only exact PinePods media endpoint is authorized`() {
        assertTrue(
            MediaSourceResolver.isPinePodsMediaRequest(
                "https://pinepods.example/",
                "https://pinepods.example/api/episodes/42/download",
            ),
        )
        assertFalse(
            MediaSourceResolver.isPinePodsMediaRequest(
                "https://pinepods.example/",
                "https://cdn.example/api/episodes/42/download",
            ),
        )
        assertFalse(
            MediaSourceResolver.isPinePodsMediaRequest(
                "https://pinepods.example/",
                "https://pinepods.example/api/proxy/image?url=https://cdn.example/a.jpg",
            ),
        )
    }

    @Test
    fun `server copy comes before RSS and carries no key (DEC-023)`() {
        val result = MediaSourceResolver.sources(
            serverUrl = "https://pinepods.example/",
            episodeId = 42,
            rssMediaUrl = "https://cdn.example/episodes/42.mp3",
            userId = 7,
            onServer = true,
        )

        assertEquals("https://pinepods.example/api/data/stream/42?user_id=7", result.primaryUrl)
        assertEquals("https://cdn.example/episodes/42.mp3", result.fallbackUrl)
        assertTrue(MediaSourceResolver.isPinePodsStreamRequest("https://pinepods.example/", result.primaryUrl!!))
        assertFalse(MediaSourceResolver.isPinePodsDownloadRequest("https://pinepods.example/", result.primaryUrl!!))
        assertFalse(result.primaryUrl!!.contains("api_key"))
    }
}
