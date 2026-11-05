package ru.e1173.nookast.core.network

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CredentialOriginGuardTest {
    private val origin = MockWebServer()
    private val elsewhere = MockWebServer()

    @Before
    fun start() {
        origin.start()
        elsewhere.start()
    }

    @After
    fun stop() {
        origin.close()
        elsewhere.close()
    }

    @Test
    fun keyIsStrippedWhenARedirectLeavesTheOrigin() {
        origin.enqueue(
            MockResponse.Builder()
                .code(302)
                .addHeader("Location", elsewhere.url("/media.mp3").toString())
                .build(),
        )
        elsewhere.enqueue(MockResponse.Builder().code(200).body("audio").build())

        HttpClients.public.newCall(
            Request.Builder().url(origin.url("/api/episodes/1/download"))
                .header(HttpClients.API_KEY_HEADER, "test-key")
                .build(),
        ).execute().use { assertEquals(200, it.code) }

        assertEquals("test-key", origin.takeRequest().headers[HttpClients.API_KEY_HEADER])
        assertNull(elsewhere.takeRequest().headers[HttpClients.API_KEY_HEADER])
    }

    @Test
    fun keyIsKeptOnSameOriginRedirects() {
        origin.enqueue(
            MockResponse.Builder().code(302).addHeader("Location", origin.url("/moved").toString()).build(),
        )
        origin.enqueue(MockResponse.Builder().code(200).body("ok").build())

        HttpClients.public.newCall(
            Request.Builder().url(origin.url("/start"))
                .header(HttpClients.API_KEY_HEADER, "test-key")
                .build(),
        ).execute().use { assertEquals(200, it.code) }

        origin.takeRequest()
        assertEquals("test-key", origin.takeRequest().headers[HttpClients.API_KEY_HEADER])
    }

    @Test
    fun pinePodsClientNeverFollowsRedirects() {
        origin.enqueue(
            MockResponse.Builder().code(302).addHeader("Location", elsewhere.url("/x").toString()).build(),
        )

        HttpClients.pinePods.newCall(
            Request.Builder().url(origin.url("/api/data/get_queued_episodes"))
                .header(HttpClients.API_KEY_HEADER, "test-key")
                .build(),
        ).execute().use { assertEquals(302, it.code) }

        assertEquals(0, elsewhere.requestCount)
    }
}
