package ru.e1173.nookast.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PodcastFreshnessTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L

    private fun ageDays(days: Long) = FeedFacts(latestEpisodeEpochMs = now - days * day)

    @Test
    fun `the age of the newest episode sets the pill`() {
        assertEquals(PodcastFreshness.ACTIVE, PodcastFreshnessRules.classify(ageDays(30), now, emptyList()))
        assertEquals(PodcastFreshness.QUIET_HALF_YEAR, PodcastFreshnessRules.classify(ageDays(200), now, emptyList()))
        assertEquals(PodcastFreshness.QUIET_YEAR, PodcastFreshnessRules.classify(ageDays(400), now, emptyList()))
        assertEquals(PodcastFreshness.ABANDONED, PodcastFreshnessRules.classify(ageDays(800), now, emptyList()))
        assertEquals(PodcastFreshness.ACTIVE, PodcastFreshnessRules.classify(FeedFacts(null), now, emptyList()))
    }

    @Test
    fun `a feed failing for two weeks while others load is unavailable`() {
        val attempt = now - day
        val failing = FeedFacts(now - 10 * day, lastAttemptEpochMs = attempt, lastSuccessEpochMs = now - 20 * day, lastAttemptFailed = true)

        assertEquals(PodcastFreshness.FEED_UNAVAILABLE, PodcastFreshnessRules.classify(failing, now, listOf(attempt + 60_000)))
    }

    @Test
    fun `an offline phone or a fresh failure does not mark a feed as gone`() {
        val attempt = now - day
        val failing = FeedFacts(now - 10 * day, lastAttemptEpochMs = attempt, lastSuccessEpochMs = now - 20 * day, lastAttemptFailed = true)
        val recent = failing.copy(lastSuccessEpochMs = now - 3 * day)

        assertEquals(PodcastFreshness.ACTIVE, PodcastFreshnessRules.classify(failing, now, listOf(attempt - 5 * day)))
        assertEquals(PodcastFreshness.ACTIVE, PodcastFreshnessRules.classify(recent, now, listOf(attempt)))
    }

    @Test
    fun `publication dates are read in the forms the cache holds`() {
        assertEquals(1_793_982_720_000L, PodcastFreshnessRules.parsePublished("2026-11-06T16:32:00"))
        assertEquals(1_793_982_720_000L, PodcastFreshnessRules.parsePublished("2026-11-06T19:32:00+03:00"))
        assertEquals(1_793_923_200_000L, PodcastFreshnessRules.parsePublished("2026-11-06"))
        assertEquals(null, PodcastFreshnessRules.parsePublished(""))
    }
}
