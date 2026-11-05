package ru.e1173.nookast.data.remote

import android.app.Application
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PodcastCatalogClientTest {
    @Test
    fun baseCatalogIsFallbackForExtendedEndpointFailuresExceptAuthentication() {
        val client = PodcastCatalogClient()

        assertTrue(client.shouldFallbackToBaseCatalog(QueueLoadError.NETWORK))
        assertTrue(client.shouldFallbackToBaseCatalog(QueueLoadError.SERVER))
        assertTrue(client.shouldFallbackToBaseCatalog(QueueLoadError.INVALID_RESPONSE))
        assertFalse(client.shouldFallbackToBaseCatalog(QueueLoadError.AUTHENTICATION))
    }

    @Test
    fun parsesSubscriptionCountsWithoutInventingUpdateDates() {
        val result = PodcastCatalogClient().parse(
            """
            {
              "pods": [
                {
                  "podcastid": 42,
                  "podcastname": "План Б",
                  "artworkurl": "https://example.test/cover.jpg",
                  "description": "Описание",
                  "episodecount": 48,
                  "episodes_played": 12,
                  "feedurl": "https://example.test/feed.xml",
                  "author": "Студия"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, result.size)
        assertEquals(42, result.single().podcastId)
        assertEquals(48, result.single().episodeCount)
        assertEquals(12, result.single().episodesPlayed)
        assertEquals("План Б", result.single().name)
    }

    @Test
    fun parsesBasicCatalogAndNormalizesNullableLegacyFields() {
        val result = PodcastCatalogClient().parse(
            """
            [
              {
                "podcastid": 7,
                "podcastname": "Кабинет лора",
                "artworkurl": null,
                "description": null,
                "episodecount": -1,
                "feedurl": "https://example.test/feed.xml",
                "author": null
              }
            ]
            """.trimIndent(),
        )

        assertEquals(1, result.size)
        assertEquals("", result.single().artworkUrl)
        assertEquals("", result.single().description)
        assertEquals(null, result.single().episodeCount)
        assertEquals(0, result.single().episodesPlayed)
    }
}
