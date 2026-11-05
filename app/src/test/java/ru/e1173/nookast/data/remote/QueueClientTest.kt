package ru.e1173.nookast.data.remote

import android.app.Application
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class QueueClientTest {
    private val client = QueueClient()

    @Test
    fun parsesAndOrdersQueueByServerPosition() {
        val result = client.parseResponse(
            """
            {
              "data": [
                {
                  "episodeid": 20,
                  "queueposition": 1,
                  "episodetitle": "Second",
                  "podcastname": "Example",
                  "episodeartwork": "https://example.test/second.jpg",
                  "episodeurl": "https://example.test/second.mp3",
                  "episodeduration": 1800,
                  "episodedescription": "Second description",
                  "episodepubdate": "2026-09-30",
                  "listenduration": 15.5,
                  "completed": false,
                  "downloaded": true
                },
                {
                  "episodeid": 10,
                  "queueposition": 0,
                  "episodetitle": "First",
                  "podcastname": "Example",
                  "episodeartwork": "https://example.test/first.jpg",
                  "episodeurl": "https://example.test/first.mp3",
                  "episodeduration": 1200,
                  "listenduration": null,
                  "completed": false
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf(10, 20), result.map { it.episodeId })
        assertEquals(15.5, result[1].listenDurationSeconds ?: 0.0, 0.0)
        assertNull(result[0].listenDurationSeconds)
        assertFalse(result[0].completed)
        assertEquals("Second description", result[1].description)
        assertEquals("2026-09-30", result[1].publishedAt)
        assertEquals(true, result[1].downloaded)
    }

    @Test
    fun preservesServerOrderWhenPositionsAreMissing() {
        val result = client.parseResponse(
            """
            {
              "data": [
                {"episodeid": 99, "episodetitle": "A", "podcastname": "P", "episodeduration": 1},
                {"episodeid": 11, "episodetitle": "B", "podcastname": "P", "episodeduration": 1}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf(99, 11), result.map { it.episodeId })
    }
}
