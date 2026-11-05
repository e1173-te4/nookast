package ru.e1173.nookast.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamReachabilityClientTest {
    private val client = StreamReachabilityClient()

    @Test
    fun `probe requests only one byte without compression`() {
        val request = client.buildProbeRequest("https://media.example/episode.mp3")

        assertEquals("bytes=0-0", request.header("Range"))
        assertEquals("identity", request.header("Accept-Encoding"))
        assertEquals("no-cache", request.header("Cache-Control"))
    }

    @Test
    fun `authenticated probe keeps API key in request header`() {
        val request = client.buildProbeRequest(
            "https://pinepods.example/api/episodes/42/download",
            mapOf("Api-Key" to "test-key"),
        )

        assertEquals("test-key", request.header("Api-Key"))
        assertFalse(request.url.toString().contains("test-key"))
    }

    @Test
    fun `successful responses mean reachable`() {
        assertTrue(StreamReachabilityClient.isReachableResponseCode(200))
        assertTrue(StreamReachabilityClient.isReachableResponseCode(206))
    }

    @Test
    fun `authorization and server errors mean unavailable`() {
        assertFalse(StreamReachabilityClient.isReachableResponseCode(403))
        assertFalse(StreamReachabilityClient.isReachableResponseCode(500))
    }
}
