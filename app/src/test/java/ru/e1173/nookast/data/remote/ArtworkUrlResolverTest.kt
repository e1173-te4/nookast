package ru.e1173.nookast.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkUrlResolverTest {
    @Test
    fun `external artwork is routed through PinePods image proxy`() {
        val resolved = ArtworkUrlResolver.sources(
            serverUrl = "https://pinepods.example/",
            artworkUrl = "https://cdn.example/covers/show one.jpg?size=large",
        )

        assertEquals(
            "https://pinepods.example/api/proxy/image?url=" +
                "https%3A%2F%2Fcdn.example%2Fcovers%2Fshow%2520one.jpg%3Fsize%3Dlarge",
            resolved.primaryUrl,
        )
        assertEquals(
            "https://cdn.example/covers/show%20one.jpg?size=large",
            resolved.fallbackUrl,
        )
    }

    @Test
    fun `same server artwork is not wrapped again`() {
        val resolved = ArtworkUrlResolver.sources(
            serverUrl = "https://pinepods.example/",
            artworkUrl = "https://pinepods.example/local/artwork/42.jpg",
        )

        assertEquals("https://pinepods.example/local/artwork/42.jpg", resolved.primaryUrl)
        assertNull(resolved.fallbackUrl)
    }

    @Test
    fun `relative server artwork becomes absolute without proxy`() {
        val resolved = ArtworkUrlResolver.sources(
            serverUrl = "https://pinepods.example/",
            artworkUrl = "/local/artwork/42.jpg",
        )

        assertEquals("https://pinepods.example/local/artwork/42.jpg", resolved.primaryUrl)
        assertNull(resolved.fallbackUrl)
    }

    @Test
    fun `blank artwork stays absent`() {
        val resolved = ArtworkUrlResolver.sources("https://pinepods.example/", "  ")
        assertNull(resolved.primaryUrl)
        assertNull(resolved.fallbackUrl)
    }

    @Test
    fun `RSS fallback can be recovered from PinePods proxy URL`() {
        val sources = ArtworkUrlResolver.sources(
            "https://pinepods.example/",
            "https://cdn.example/cover art.jpg",
        )

        assertEquals(
            "https://cdn.example/cover%20art.jpg",
            ArtworkUrlResolver.fallbackFromProxiedUrl(requireNotNull(sources.primaryUrl)),
        )
    }

    @Test
    fun `ordinary image URL does not invent a fallback`() {
        assertNull(
            ArtworkUrlResolver.fallbackFromProxiedUrl(
                "https://pinepods.example/local/artwork/42.jpg",
            ),
        )
    }

    @Test
    fun `credentials are allowed only for the configured server origin`() {
        assertTrue(
            ArtworkUrlResolver.isPinePodsOriginRequest(
                "https://pinepods.example.com",
                "https://pinepods.example.com/api/proxy/image?url=https%3A%2F%2Fcdn.example%2Fa.jpg",
            ),
        )
        assertFalse(
            ArtworkUrlResolver.isPinePodsOriginRequest(
                "https://pinepods.example.com",
                "https://cdn.example/a.jpg",
            ),
        )
        assertFalse(
            ArtworkUrlResolver.isPinePodsOriginRequest(
                "https://pinepods.example.com",
                "http://pinepods.example.com/api/proxy/image",
            ),
        )
    }

    @Test
    fun `an existing PinePods proxy keeps its embedded RSS fallback`() {
        val proxied = "https://pinepods.example.com/api/proxy/image" +
            "?url=https%3A%2F%2Fcdn.example%2Fcover.jpg"

        val sources = ArtworkUrlResolver.sourcesForRequest(
            serverUrl = "https://pinepods.example.com",
            artworkUrl = proxied,
        )

        assertEquals(proxied, sources.primaryUrl)
        assertEquals("https://cdn.example/cover.jpg", sources.fallbackUrl)
    }

    @Test
    fun `private feed addresses are never proxied and the key goes only to the proxy (X-09)`() {
        val local = ArtworkUrlResolver.sources("https://pinepods.example/", "http://192.168.1.5/cover.jpg")
        org.junit.Assert.assertEquals("http://192.168.1.5/cover.jpg", local.primaryUrl)
        org.junit.Assert.assertNull(local.fallbackUrl)
        org.junit.Assert.assertTrue(ArtworkUrlResolver.isPrivateHost("localhost"))
        org.junit.Assert.assertTrue(ArtworkUrlResolver.isPrivateHost("10.0.0.2"))
        org.junit.Assert.assertTrue(ArtworkUrlResolver.isPrivateHost("[::1]"))
        org.junit.Assert.assertFalse(ArtworkUrlResolver.isPrivateHost("cdn.example.com"))
        org.junit.Assert.assertTrue(
            ArtworkUrlResolver.isPinePodsImageProxyRequest("https://pinepods.example/", "https://pinepods.example/api/proxy/image?url=x"),
        )
        org.junit.Assert.assertFalse(
            ArtworkUrlResolver.isPinePodsImageProxyRequest("https://pinepods.example/", "https://pinepods.example/api/data/get_key"),
        )
    }
}
