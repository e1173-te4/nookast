package ru.e1173.nookast.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.e1173.nookast.data.local.PodcastSubscriptionEntity
import ru.e1173.nookast.data.remote.DashboardAnalytics
import ru.e1173.nookast.data.remote.DashboardEpisode
import ru.e1173.nookast.data.remote.DashboardSnapshot
import ru.e1173.nookast.data.remote.DashboardTopPodcast

class PodcastArtworkPolicyTest {
    @Test
    fun `latest PinePods episode cover wins without fetching RSS`() {
        var rssCalled = false
        val selected = preferredPodcastArtwork(
            latestEpisodeArtwork = " https://cdn.example/latest.jpg ",
            catalogArtwork = "https://cdn.example/catalog.jpg",
            cachedArtwork = "https://cdn.example/cached.jpg",
        ) {
            rssCalled = true
            "https://cdn.example/rss.jpg"
        }

        assertEquals("https://cdn.example/latest.jpg", selected)
        assertFalse(rssCalled)
    }

    @Test
    fun `RSS metadata is the final fallback for blank PinePods artwork`() {
        var rssCalled = false
        val selected = preferredPodcastArtwork(null, " ", "") {
            rssCalled = true
            " https://cdn.example/rss.jpg "
        }

        assertEquals("https://cdn.example/rss.jpg", selected)
        assertTrue(rssCalled)
    }

    @Test
    fun `RSS metadata replaces PinePods URLs that do not decode`() {
        val selected = preferredPodcastArtwork(
            latestEpisodeArtwork = "https://obsolete.example/latest.jpg",
            catalogArtwork = "https://obsolete.example/catalog.jpg",
            cachedArtwork = null,
            resolveRssArtwork = { "https://working.example/rss.jpg" },
            isUsable = { it.contains("working.example") },
        )

        assertEquals("https://working.example/rss.jpg", selected)
    }

    @Test
    fun `dashboard artwork is reconciled from the refreshed Room catalog`() {
        val dashboard = DashboardSnapshot(
            analytics = DashboardAnalytics(
                topPodcasts = listOf(
                    DashboardTopPodcast(7, "Example Show", "", 60),
                ),
            ),
            savedEpisodes = listOf(
                DashboardEpisode(
                    episodeId = 11,
                    podcastId = 7,
                    title = "Episode",
                    podcastName = "Example Show",
                    artworkUrl = "https://broken.example/old.jpg",
                    mediaUrl = "https://media.example/episode.mp3",
                    durationSeconds = 600,
                    listenedSeconds = 30,
                    completed = false,
                    downloadedOnServer = false,
                ),
            ),
        )
        val catalog = listOf(
            PodcastSubscriptionEntity(
                podcastId = 7,
                name = "Example Show",
                artworkUrl = "https://working.example/cover.jpg",
                description = "",
                episodeCount = 1,
                episodesPlayed = 0,
                feedUrl = "https://example.org/feed.xml",
                author = "",
                latestEpisodeId = 11,
                lastUpdatedAt = "",
                refreshedAtEpochMs = 42,
            ),
        )

        val enriched = dashboard.withPodcastArtwork(catalog)

        assertEquals(
            "https://working.example/cover.jpg",
            enriched.analytics.topPodcasts.single().artworkUrl,
        )
        assertEquals(
            "https://working.example/cover.jpg",
            enriched.savedEpisodes.single().artworkUrl,
        )
    }
}
