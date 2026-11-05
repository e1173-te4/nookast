package ru.e1173.nookast.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.e1173.nookast.data.remote.PublicRssEpisode
import ru.e1173.nookast.data.remote.PublicRssFeed

class PublicRssEpisodeMergeTest {
    @Test
    fun stableKeyPrefersGuidAndProducesNegativeLocalPlayerId() {
        val first = rssEpisodeCacheKey(
            podcastId = 12,
            guid = " stable-guid ",
            mediaUrl = "https://one.example.test/a.mp3",
            title = "First title",
            publishedAt = "2026-10-13",
        )
        val second = rssEpisodeCacheKey(
            podcastId = 12,
            guid = "stable-guid",
            mediaUrl = "https://two.example.test/b.mp3",
            title = "Changed title",
            publishedAt = "2026-10-14",
        )

        assertEquals(first, second)
        assertTrue(rssLocalPlaybackId(first) < 0)
        assertEquals(rssLocalPlaybackId(first), rssLocalPlaybackId(second))
    }

    @Test
    fun exactEnclosureMatchKeepsPinePodsIdentityAndProgress() {
        val subscription = subscription()
        val existing = pinePodsEpisode(mediaUrl = "https://media.example.test/42.mp3")
        val merged = mergePublicRssEpisodes(
            subscription = subscription,
            feed = feed(
                episode = rssEpisode(mediaUrl = "https://media.example.test/42.mp3"),
            ),
            existing = listOf(existing),
            refreshedAtEpochMs = 2_000,
        ).single()

        assertEquals(42, merged.pinePodsEpisodeId)
        assertEquals("pinepods:42", merged.cacheKey)
        assertEquals(120.0, merged.listenedSeconds, 0.0)
        assertTrue(merged.completed)
    }

    @Test
    fun unmatchedEpisodeUsesStableRssIdentityWithoutFakePinePodsId() {
        val rss = rssEpisode(mediaUrl = "https://media.example.test/new.mp3")
        val merged = mergePublicRssEpisodes(
            subscription = subscription(),
            feed = feed(rss),
            existing = emptyList(),
            refreshedAtEpochMs = 2_000,
        ).single()

        assertNull(merged.pinePodsEpisodeId)
        assertEquals(PodcastCacheSource.RSS, merged.source)
        assertEquals(
            rssEpisodeCacheKey(12, rss.guid, rss.mediaUrl, rss.title, rss.publishedAt),
            merged.cacheKey,
        )
    }

    @Test
    fun ambiguousTitleAndDateDoesNotGuessPinePodsIdentity() {
        val first = pinePodsEpisode("https://media.example.test/first.mp3")
        val second = first.copy(
            cacheKey = "pinepods:43",
            pinePodsEpisodeId = 43,
            mediaUrl = "https://media.example.test/second.mp3",
        )
        val merged = mergePublicRssEpisodes(
            subscription = subscription(),
            feed = feed(rssEpisode("https://media.example.test/third.mp3")),
            existing = listOf(first, second),
            refreshedAtEpochMs = 2_000,
        ).single()

        assertNull(merged.pinePodsEpisodeId)
        assertEquals(PodcastCacheSource.RSS, merged.source)
    }

    @Test
    fun resolvedMappingsUseStableRssKeyForAnExactEnclosureMatch() {
        val rss = rssEpisode(mediaUrl = "https://media.example.test/42.mp3")

        val mappings = resolvedRssEpisodeMappings(
            subscription = subscription(),
            feed = feed(rss),
            existing = listOf(pinePodsEpisode(rss.mediaUrl)),
        )

        assertEquals(
            mapOf(
                rssEpisodeCacheKey(12, rss.guid, rss.mediaUrl, rss.title, rss.publishedAt) to 42,
            ),
            mappings,
        )
    }

    @Test
    fun resolvedMappingsDoNotPromoteAnAmbiguousTitleAndDateMatch() {
        val first = pinePodsEpisode("https://media.example.test/first.mp3")
        val second = first.copy(
            cacheKey = "pinepods:43",
            pinePodsEpisodeId = 43,
            mediaUrl = "https://media.example.test/second.mp3",
        )

        val mappings = resolvedRssEpisodeMappings(
            subscription = subscription(),
            feed = feed(rssEpisode("https://media.example.test/third.mp3")),
            existing = listOf(first, second),
        )

        assertTrue(mappings.isEmpty())
    }

    @Test
    fun cachedRssMappingCanResolveAfterPinePodsRefreshWhenFeedIsNotModified() {
        val rss = rssEpisode("https://media.example.test/42.mp3")
        val rssCache = mergePublicRssEpisodes(
            subscription = subscription(),
            feed = feed(rss),
            existing = emptyList(),
            refreshedAtEpochMs = 2_000,
        ).single()

        assertEquals(
            mapOf(rssCache.cacheKey to 42),
            resolvedCachedRssEpisodeMappings(
                listOf(rssCache, pinePodsEpisode(rss.mediaUrl)),
            ),
        )
    }

    @Test
    fun cachedRssMappingStillRejectsAmbiguousPinePodsCandidates() {
        val rss = rssEpisode("https://media.example.test/third.mp3")
        val rssCache = mergePublicRssEpisodes(
            subscription = subscription(),
            feed = feed(rss),
            existing = emptyList(),
            refreshedAtEpochMs = 2_000,
        ).single()
        val first = pinePodsEpisode("https://media.example.test/first.mp3")
        val second = first.copy(
            cacheKey = "pinepods:43",
            pinePodsEpisodeId = 43,
            mediaUrl = "https://media.example.test/second.mp3",
        )

        assertTrue(resolvedCachedRssEpisodeMappings(listOf(rssCache, first, second)).isEmpty())
    }

    private fun subscription() = PodcastSubscriptionEntity(
        podcastId = 12,
        name = "Show",
        artworkUrl = "https://cdn.example.test/show.jpg",
        description = "Description",
        episodeCount = 1,
        episodesPlayed = 0,
        feedUrl = "https://feeds.example.test/show.xml",
        author = "Author",
        latestEpisodeId = 42,
        lastUpdatedAt = "2026-10-13T10:00:00Z",
        refreshedAtEpochMs = 1_000,
    )

    private fun feed(episode: PublicRssEpisode) = PublicRssFeed(
        title = "Show",
        description = "Description",
        author = "Author",
        artworkUrl = "https://cdn.example.test/feed.jpg",
        episodes = listOf(episode),
    )

    private fun rssEpisode(mediaUrl: String) = PublicRssEpisode(
        guid = "guid-42",
        title = "Episode 42",
        description = "RSS description",
        publishedAt = "2026-10-13T10:00:00Z",
        durationSeconds = 3_600,
        artworkUrl = "",
        mediaUrl = mediaUrl,
    )

    private fun pinePodsEpisode(mediaUrl: String) = PodcastEpisodeCacheEntity(
        cacheKey = "pinepods:42",
        podcastId = 12,
        pinePodsEpisodeId = 42,
        guid = null,
        source = PodcastCacheSource.PINEPODS,
        title = "Episode 42",
        description = "PinePods description",
        publishedAt = "2026-10-13T10:00:00Z",
        durationSeconds = 3_600,
        listenedSeconds = 120.0,
        completed = true,
        podcastName = "Show",
        artworkUrl = "https://cdn.example.test/episode.jpg",
        mediaUrl = mediaUrl,
        downloaded = false,
        refreshedAtEpochMs = 1_000,
    )
}
