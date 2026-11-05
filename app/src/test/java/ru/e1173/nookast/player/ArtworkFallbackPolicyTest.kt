package ru.e1173.nookast.player

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkFallbackPolicyTest {
    @Test
    fun `invalid successful proxy body does not suppress direct fallback`() {
        val proxyError = "upstream error".toByteArray()
        val validImage = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

        val result = firstDecodedArtwork(
            sources = listOf("proxy", "rss"),
            fetch = { if (it == "proxy") proxyError else validImage },
            decode = { bytes -> bytes.takeIf { it.contentEquals(validImage) }?.let { "bitmap" } },
        )

        assertEquals("bitmap", result?.value)
        assertArrayEquals(validImage, result?.bytes)
    }

    @Test
    fun `failed background refresh keeps already displayed artwork`() {
        assertEquals("cached", retainCachedArtwork(cached = "cached", refreshed = null))
        assertEquals("new", retainCachedArtwork(cached = "cached", refreshed = "new"))
        assertNull(retainCachedArtwork<String>(cached = null, refreshed = null))
    }
}
