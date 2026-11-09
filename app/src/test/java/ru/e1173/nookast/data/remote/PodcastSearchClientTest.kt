package ru.e1173.nookast.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.e1173.nookast.data.local.PodcastSubscriptionEntity
import ru.e1173.nookast.ui.PodcastSearchViewModel
import ru.e1173.nookast.ui.SubscriptionLookup

/** Search answers of both sources (user, 1.11.2026) and the pasted feed link. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PodcastSearchClientTest {
    @Test
    fun `apple results keep only podcasts with a feed`() {
        val body = """
            {"resultCount":2,"results":[
              {"collectionName":"Завтракаст","artistName":"Zavtracast","feedUrl":"https://feeds.example/z","artworkUrl600":"https://img/600.jpg","artworkUrl100":"https://img/100.jpg"},
              {"collectionName":"No feed","artistName":"Nobody"}
            ]}
        """.trimIndent()

        val found = PodcastSearchClient.parse(body)

        assertEquals(1, found.size)
        assertEquals(FoundPodcast("Завтракаст", "Zavtracast", "https://img/600.jpg", "https://feeds.example/z"), found.single())
    }

    @Test
    fun `podcast index feeds carry their id and description`() {
        val body = """{"status":"true","feeds":[{"id":75075,"title":"Batman","url":"https://feeds.example/b","author":"DC","image":"https://img/b.jpg","description":"Gotham"}]}"""

        val found = PodcastSearchClient.parse(body).single()

        assertEquals("https://feeds.example/b", found.feedUrl)
        assertEquals(75075L, found.podcastIndexId)
        assertEquals("https://img/b.jpg", found.artworkUrl)
        assertEquals("Gotham", found.description)
    }

    @Test
    fun `an unknown answer gives nothing`() {
        assertTrue(PodcastSearchClient.parse("""{"error":"x"}""").isEmpty())
    }

    @Test
    fun `only the subscribed feed is in the library and the same show under another feed is told apart`() {
        fun sub(id: Int, name: String, feed: String, author: String = "") =
            PodcastSubscriptionEntity(id, name, "", "", null, 0, feed, author, null, "", 0L)
        val lookup = SubscriptionLookup(
            listOf(
                sub(1, "BeardyCast: гаджеты и медиакультура", "https://cloud.mave.digital/64666"),
                sub(2, "BeardyBuilding", "https://mavecloud.s3mts.ru/storage/feeds/44273.xml", "BeardyCast"),
            ),
        )

        assertEquals(1, lookup.find(FoundPodcast("BeardyCast", "", "", "http://cloud.mave.digital/64666/"))?.podcastId)
        // User, 6.11.2026: followed via the s3mts link, the search finds cloud.mave.digital.
        val otherLink = FoundPodcast("beardybuilding ", "BeardyCast", "", "https://cloud.mave.digital/44273")
        assertNull(lookup.find(otherLink))
        assertEquals(2, lookup.sameShowElsewhere(otherLink)?.podcastId)
        assertNull(lookup.sameShowElsewhere(FoundPodcast("BeardyBuilding", "Someone else", "", "https://other.example/feed")))
        assertNull(lookup.sameShowElsewhere(FoundPodcast("BeardyBuilding", "", "", "https://mavecloud.s3mts.ru/storage/feeds/44273.xml")))
        assertNull(lookup.find(FoundPodcast("Батина Консоль", "", "", "https://mavecloud.s3mts.ru/storage/feeds/43674.xml")))
    }

    @Test
    fun `of two shows with one title only the subscribed feed is marked`() {
        val lookup = SubscriptionLookup(
            listOf(PodcastSubscriptionEntity(1, "Животные в Студии", "", "", null, 0, "https://cloud.mave.digital/35537", "", null, "", 0L)),
        )
        val current = FoundPodcast("Животные в Студии", "", "", "https://cloud.mave.digital/35537")
        val old = FoundPodcast("Животные в студии", "", "", "https://old.example/feed.xml")
        val results = listOf(old, current)

        assertEquals(1, lookup.find(current)?.podcastId)
        assertNull(lookup.find(old))
        assertEquals("old.example", lookup.twinHost(old, results))
        assertNull(lookup.twinHost(current, listOf(current)))
    }

    @Test
    fun `a pasted feed link is offered as it is`() {
        assertEquals("https://example.org/feed.xml", PodcastSearchViewModel.feedLink("  https://example.org/feed.xml "))
        assertNull(PodcastSearchViewModel.feedLink("zavtracast"))
        assertNull(PodcastSearchViewModel.feedLink("https://example.org/a b"))
    }
}
