package ru.e1173.nookast.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerAddressTest {
    @Test
    fun acceptsHttpsAndNormalizesToServerRoot() {
        val result = ServerAddress.normalize("  https://example.test/some/path?private=value  ")

        assertTrue(result.isSuccess)
        assertEquals("https://example.test/", result.getOrThrow().toString())
    }

    @Test
    fun preservesExplicitPort() {
        val result = ServerAddress.normalize("https://example.test:8443")

        assertEquals("https://example.test:8443/", result.getOrThrow().toString())
    }

    @Test
    fun acceptsExplicitCleartextHttp() {
        val result = ServerAddress.normalize("http://example.test")

        assertTrue(result.isSuccess)
        assertEquals("http://example.test/", result.getOrThrow().toString())
    }

    @Test
    fun rejectsCredentialsEmbeddedInUrl() {
        val result = ServerAddress.normalize("https://user:password@example.test")

        assertTrue(result.isFailure)
        assertEquals(
            LoginError.CREDENTIALS_IN_ADDRESS,
            (result.exceptionOrNull() as? LoginFailure)?.reason,
        )
    }
}
