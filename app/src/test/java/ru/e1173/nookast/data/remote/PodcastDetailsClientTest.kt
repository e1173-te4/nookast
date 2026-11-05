package ru.e1173.nookast.data.remote

import android.app.Application
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PodcastDetailsClientTest {
    private val client = PodcastDetailsClient()

    @Test
    fun acceptsKnownPodcastIdResponseShapes() {
        assertEquals(42, client.parsePodcastId("42"))
        assertEquals(42, client.parsePodcastId("""{"podcast_id":42}"""))
        assertEquals(42, client.parsePodcastId("""{"data":{"podcastid":42}}"""))
        assertNull(client.parsePodcastId("""{"data":null}"""))
    }

    @Test
    fun explicitJsonNullsNeverBecomeTheLiteralNullString() {
        // PinePods declares these fields nullable (OpenAPI PodcastExtraResponse). Android's
        // org.json returns "null" from optString() for JSON null, which used to leak into the UI.
        val details = client.parseDetails(
            podcastId = 7,
            detailsBody = """{"data":{"podcastname":"Example","artworkurl":null,"description":null,"author":null}}""",
            episodesBody = """{"episodes":[]}""",
        )

        assertTrue(details.artworkUrl.isEmpty())
        assertTrue(details.description.isEmpty())
        assertTrue(details.author.isEmpty())
    }

    @Test
    fun parsesDescriptionStatsCategoriesAndRecentProgress() {
        val details = client.parseDetails(
            podcastId = 7,
            detailsBody =
                """
                {
                  "data": {
                    "podcastname": "Example",
                    "artworkurl": "https://example.test/art.jpg",
                    "description": "Podcast description",
                    "author": "Author",
                    "categories": {"0":"Technology", "1":"Society"},
                    "episodecount": 48,
                    "episodes_played": 12
                  }
                }
                """.trimIndent(),
            episodesBody =
                """
                {
                  "episodes": [
                    {
                      "Episodeid": 100,
                      "Episodetitle": "Done",
                      "Episodepubdate": "2026-09-30",
                      "Episodeduration": 100,
                      "Listenduration": 100,
                      "Completed": true
                    },
                    {
                      "Episodeid": 99,
                      "Episodetitle": "Half",
                      "Episodepubdate": "2026-09-23",
                      "Episodeduration": 200,
                      "Listenduration": 100,
                      "Completed": false
                    }
                  ],
                  "total": 48
                }
                """.trimIndent(),
        )

        assertEquals("Example", details.name)
        assertEquals(listOf("Technology", "Society"), details.categories)
        assertEquals(48, details.episodeCount)
        assertEquals(12, details.episodesPlayed)
        assertEquals(1f, details.recentEpisodes[0].fraction)
        assertEquals(0.5f, details.recentEpisodes[1].fraction)
    }
}
