package ru.e1173.nookast.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** PinePods 0.9.1+ «Feed problems» as the server sends them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PodcastFeedClientTest {
    @Test
    fun `failing feeds are read with their kind, start and threshold`() {
        val body = """
            {"feeds":[
              {"podcast_id":19,"name":"A","feed_url":"https://a.example/rss","kind":"gone","failures":3,"failing_since":1793000000,"reported":true},
              {"podcast_id":20,"name":"B","feed_url":"https://b.example/rss","kind":"weird","failures":1,"reported":false},
              {"podcast_id":0,"name":"broken"}
            ],"imports":[]}
        """.trimIndent()

        assertEquals(
            listOf(
                ServerFeedProblem(19, ServerFeedError.GONE, 1_793_000_000_000L, reported = true),
                ServerFeedProblem(20, ServerFeedError.OTHER, null, reported = false),
            ),
            parseFeedProblems(body),
        )
        assertEquals(emptyList<ServerFeedProblem>(), parseFeedProblems("Not Found"))
    }
}
