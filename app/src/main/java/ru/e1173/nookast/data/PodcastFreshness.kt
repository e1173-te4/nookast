package ru.e1173.nookast.data

import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * How alive a subscribed podcast is (user, 5.11.2026: pills over the covers in Podcasts for shows
 * that went quiet, were abandoned or whose feed is gone). The background read refresh already
 * re-reads every feed twice a day (ETag), so the age of the newest episode and the outcome of the
 * last feed read are known without any extra traffic.
 */
enum class PodcastFreshness {
    ACTIVE,

    /** No new episode for half a year. */
    QUIET_HALF_YEAR,

    /** No new episode for a year. */
    QUIET_YEAR,

    /** No new episode for two years: the show is over. */
    ABANDONED,

    /** The feed itself fails to load (gone, moved without a redirect, no longer RSS). */
    FEED_UNAVAILABLE,

    /**
     * The server cannot update the feed while the phone reads it (user, 6.11.2026: PinePods 0.9.1+
     * refuses feeds on a home-network address, such as an audiobookshelf behind split DNS).
     */
    SERVER_CANNOT_UPDATE,
}

/** What the app knows about one subscription's feed. */
data class FeedFacts(
    /** The newest episode's publication, from the server or the feed, or null when unknown. */
    val latestEpisodeEpochMs: Long?,
    val lastAttemptEpochMs: Long? = null,
    val lastSuccessEpochMs: Long? = null,
    val lastAttemptFailed: Boolean = false,
)

object PodcastFreshnessRules {
    private const val DAY_MS = 24L * 60 * 60 * 1000
    const val HALF_YEAR_DAYS = 183L
    const val YEAR_DAYS = 365L
    const val ABANDONED_DAYS = 730L

    /** A feed that has not loaded for this long counts as gone. */
    const val UNAVAILABLE_DAYS = 14L

    /** Another feed loaded this close to a failure: the network was up, the feed is to blame. */
    private const val NETWORK_UP_WINDOW_MS = 60L * 60 * 1000

    /**
     * [anySuccessEpochMs] are the last successful reads of all feeds: a failure counts against a
     * feed only when another feed loaded around the same time, so a phone that was simply offline
     * does not mark every show as gone.
     */
    fun classify(facts: FeedFacts, nowEpochMs: Long, anySuccessEpochMs: Collection<Long>): PodcastFreshness {
        val attempt = facts.lastAttemptEpochMs
        if (facts.lastAttemptFailed && attempt != null) {
            // Never read at all: how long it has failed is unknown, so it is not called gone.
            val success = facts.lastSuccessEpochMs
            val longFailing = success != null && nowEpochMs - success >= UNAVAILABLE_DAYS * DAY_MS
            val networkWasUp = anySuccessEpochMs.any { kotlin.math.abs(it - attempt) <= NETWORK_UP_WINDOW_MS }
            if (longFailing && networkWasUp) return PodcastFreshness.FEED_UNAVAILABLE
        }
        val latest = facts.latestEpisodeEpochMs ?: return PodcastFreshness.ACTIVE
        val days = (nowEpochMs - latest) / DAY_MS
        return when {
            days >= ABANDONED_DAYS -> PodcastFreshness.ABANDONED
            days >= YEAR_DAYS -> PodcastFreshness.QUIET_YEAR
            days >= HALF_YEAR_DAYS -> PodcastFreshness.QUIET_HALF_YEAR
            else -> PodcastFreshness.ACTIVE
        }
    }

    /** `2026-10-21T16:32:00`, with or without a zone or fraction, or a bare date; UTC when no zone. */
    fun parsePublished(value: String): Long? {
        val text = value.trim().takeIf(String::isNotEmpty) ?: return null
        return runCatching { java.time.OffsetDateTime.parse(text).toInstant().toEpochMilli() }
            .recoverCatching { LocalDateTime.parse(text.take(19)).toInstant(ZoneOffset.UTC).toEpochMilli() }
            .recoverCatching { java.time.LocalDate.parse(text.take(10)).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }
            .getOrNull()
    }
}

/**
 * The shows that are not [PodcastFreshness.ACTIVE], by podcast id: the newest episode the server or
 * the feed knows of, and the last read of each feed.
 */
internal fun podcastFreshnessOf(
    podcasts: List<ru.e1173.nookast.data.local.PodcastSubscriptionEntity>,
    latest: List<ru.e1173.nookast.data.local.LatestPublishedRow>,
    states: List<ru.e1173.nookast.data.local.PodcastRefreshStateEntity>,
    nowEpochMs: Long = System.currentTimeMillis(),
): Map<Int, PodcastFreshness> {
    val latestById = latest.associate { it.podcastId to it.latestPublishedAt.orEmpty() }
    val stateById = states.filter { it.podcastId != null }.associateBy { it.podcastId!! }
    val successes = states.mapNotNull { it.lastSuccessAtEpochMs }
    return podcasts.mapNotNull { podcast ->
        val newest = listOfNotNull(
            latestById[podcast.podcastId]?.let(PodcastFreshnessRules::parsePublished),
            PodcastFreshnessRules.parsePublished(podcast.lastUpdatedAt),
        ).maxOrNull()
        val state = stateById[podcast.podcastId]
        val facts = FeedFacts(
            latestEpisodeEpochMs = newest,
            lastAttemptEpochMs = state?.lastAttemptAtEpochMs,
            lastSuccessEpochMs = state?.lastSuccessAtEpochMs,
            lastAttemptFailed = state?.lastErrorCategory != null,
        )
        PodcastFreshnessRules.classify(facts, nowEpochMs, successes)
            .takeIf { it != PodcastFreshness.ACTIVE }
            ?.let { podcast.podcastId to it }
    }.toMap()
}

/** Podcasts whose feed the phone read without error in the last [days] days. */
internal fun feedsReadLately(
    states: List<ru.e1173.nookast.data.local.PodcastRefreshStateEntity>,
    nowEpochMs: Long = System.currentTimeMillis(),
    days: Long = 3,
): Set<Int> = states.filter { state ->
    state.podcastId != null && state.lastErrorCategory == null &&
        (state.lastSuccessAtEpochMs ?: 0L) >= nowEpochMs - days * 24 * 60 * 60 * 1000
}.mapNotNull { it.podcastId }.toSet()
