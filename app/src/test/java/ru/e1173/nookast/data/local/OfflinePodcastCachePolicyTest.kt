package ru.e1173.nookast.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.e1173.nookast.data.remote.PodcastEpisodeProgress

class OfflinePodcastCachePolicyTest {
    @Test
    fun pinePodsEpisodeUsesStableNamespacedKeyAndKeepsPlayableMetadata() {
        val cached = PodcastEpisodeProgress(
            episodeId = 42,
            title = "Episode",
            publishedAt = "2026-10-16T10:00:00Z",
            durationSeconds = 3_600,
            listenedSeconds = 120.5,
            completed = false,
            podcastName = "Podcast",
            description = "Description",
            artworkUrl = "https://cdn.example/art.jpg",
            mediaUrl = "https://cdn.example/episode.mp3",
            downloaded = true,
        ).toCacheEntity(
            podcastId = 7,
            refreshedAtEpochMs = 123_456L,
        )

        assertEquals("pinepods:42", cached.cacheKey)
        assertEquals(42, cached.pinePodsEpisodeId)
        assertEquals(7, cached.podcastId)
        assertEquals(PodcastCacheSource.PINEPODS, cached.source)
        assertEquals("https://cdn.example/episode.mp3", cached.mediaUrl)
        assertEquals(120.5, cached.listenedSeconds, 0.0)
        assertNull(cached.guid)
    }

    @Test
    fun malformedNegativeRemoteDurationsCannotPoisonLocalCache() {
        val cached = PodcastEpisodeProgress(
            episodeId = 9,
            title = "Episode",
            publishedAt = "",
            durationSeconds = -1,
            listenedSeconds = -3.0,
            completed = false,
        ).toCacheEntity(
            podcastId = 5,
            refreshedAtEpochMs = 1L,
        )

        assertEquals(0, cached.durationSeconds)
        assertEquals(0.0, cached.listenedSeconds, 0.0)
    }

    @Test
    fun refreshKeysDoNotCollideAcrossCatalogAndPodcast() {
        assertEquals("pinepods:catalog", PINEPODS_CATALOG_REFRESH_KEY)
        assertEquals("pinepods:podcast:17", pinePodsPodcastRefreshKey(17))
    }

    @Test
    fun cachedDetailsUseCurrentSubscriptionCountsAndPreserveCategories() {
        val subscription = PodcastSubscriptionEntity(
            podcastId = 7,
            name = "Podcast",
            artworkUrl = "cached-art",
            description = "Catalog description",
            episodeCount = 12,
            episodesPlayed = 5,
            feedUrl = "https://example.com/feed.xml",
            author = "Catalog author",
            latestEpisodeId = 42,
            lastUpdatedAt = "2026-10-16T10:00:00Z",
            refreshedAtEpochMs = 20,
        )
        val details = PodcastDetailsCacheEntity(
            podcastId = 7,
            name = "Podcast",
            artworkUrl = "detail-art",
            description = "Full description",
            author = "Detail author",
            categories = listOf("Technology", "News"),
            episodeCount = 10,
            episodesPlayed = 4,
            refreshedAtEpochMs = 10,
        )
        val cached = CachedPodcastDetails(
            podcast = subscription,
            episodes = emptyList(),
            details = details,
        ).toPodcastDetails()!!

        assertEquals(12, cached.episodeCount)
        assertEquals(5, cached.episodesPlayed)
        assertEquals("Full description", cached.description)
        assertEquals(listOf("Technology", "News"), cached.categories)
    }

    @Test
    fun foregroundRefreshUsesFifteenMinuteStaleWhileRevalidateWindow() {
        val lastSuccess = 1_000L

        assertEquals(
            false,
            refreshDue(lastSuccess, lastSuccess + FOREGROUND_PINEPODS_TTL_MS - 1, FOREGROUND_PINEPODS_TTL_MS),
        )
        assertEquals(
            true,
            refreshDue(lastSuccess, lastSuccess + FOREGROUND_PINEPODS_TTL_MS, FOREGROUND_PINEPODS_TTL_MS),
        )
        assertEquals(true, refreshDue(null, 5_000L, FOREGROUND_PINEPODS_TTL_MS))
    }

    @Test
    fun publicRssRefreshUsesTwoHourConditionalRequestWindow() {
        val lastSuccess = 1_000L

        assertEquals(
            false,
            refreshDue(lastSuccess, lastSuccess + FOREGROUND_RSS_TTL_MS - 1, FOREGROUND_RSS_TTL_MS),
        )
        assertEquals(
            true,
            refreshDue(lastSuccess, lastSuccess + FOREGROUND_RSS_TTL_MS, FOREGROUND_RSS_TTL_MS),
        )
    }
}
