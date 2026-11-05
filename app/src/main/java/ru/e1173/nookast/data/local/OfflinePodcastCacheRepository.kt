package ru.e1173.nookast.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import ru.e1173.nookast.data.remote.PodcastDetails
import ru.e1173.nookast.data.remote.PodcastEpisodeProgress
import ru.e1173.nookast.data.remote.PublicRssEpisode
import ru.e1173.nookast.data.remote.PublicRssFeed
import ru.e1173.nookast.data.remote.PublicRssFeedClient
import ru.e1173.nookast.data.remote.PublicRssFetchResult
import ru.e1173.nookast.data.remote.QueueLoadError
import ru.e1173.nookast.data.remote.QueueLoadException
import ru.e1173.nookast.data.remote.RssFeedValidators
import ru.e1173.nookast.data.remote.normalizeRssDate

enum class RssCacheRefreshOutcome {
    UPDATED,
    NOT_MODIFIED,
    SKIPPED,
}

data class CachedPodcastDetails(
    val podcast: PodcastSubscriptionEntity?,
    val episodes: List<PodcastEpisodeCacheEntity>,
    val details: PodcastDetailsCacheEntity?,
)

class OfflinePodcastCacheRepository(
    private val podcastDao: PodcastDao,
    private val offlineCacheDao: OfflineCacheDao,
    /** Returns [PROMOTION_REFUSED] when nothing may be promoted (quarantine); then nothing is deleted. */
    private val promoteDeferredIntents: suspend (Map<String, Int>) -> Int,
) {
    suspend fun isCatalogRefreshDue(nowEpochMs: Long): Boolean = isRefreshDue(
        sourceKey = PINEPODS_CATALOG_REFRESH_KEY,
        nowEpochMs = nowEpochMs,
        ttlMs = FOREGROUND_PINEPODS_TTL_MS,
    )

    suspend fun isPodcastRefreshDue(podcastId: Int, nowEpochMs: Long): Boolean = isRefreshDue(
        sourceKey = pinePodsPodcastRefreshKey(podcastId),
        nowEpochMs = nowEpochMs,
        ttlMs = FOREGROUND_PINEPODS_TTL_MS,
    )

    suspend fun isRssRefreshDue(podcastId: Int, nowEpochMs: Long): Boolean = isRefreshDue(
        sourceKey = rssPodcastRefreshKey(podcastId),
        nowEpochMs = nowEpochMs,
        ttlMs = FOREGROUND_RSS_TTL_MS,
    )

    suspend fun isEpisodePodcastRefreshDue(episodeId: Int, nowEpochMs: Long): Boolean {
        val cached = offlineCacheDao.findEpisodeByPinePodsId(episodeId) ?: return true
        return isPodcastRefreshDue(cached.podcastId, nowEpochMs)
    }

    fun observePodcastDetails(podcastId: Int): Flow<CachedPodcastDetails> = combine(
        podcastDao.observeSubscription(podcastId),
        offlineCacheDao.observeEpisodes(podcastId),
        offlineCacheDao.observeAllPodcastDetails(),
    ) { podcast, episodes, details ->
        CachedPodcastDetails(
            podcast = podcast,
            episodes = episodes,
            details = details.firstOrNull { it.podcastId == podcastId },
        )
    }

    /**
     * Details of the given podcasts only. The UI must never observe every cached episode: each
     * write during a catalog refresh would re-read the whole table (finding N-05, the startup
     * freeze reported on APK-1).
     */
    fun observePodcastDetails(podcastIds: Set<Int>): Flow<List<CachedPodcastDetails>> =
        if (podcastIds.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(podcastIds.sorted().map(::observePodcastDetails)) { it.toList() }
        }

    /** Podcast of a locally cached episode by playback ID (PinePods or RSS-only), or null. */
    fun observePodcastIdForEpisode(playbackId: Int): Flow<Int?> =
        if (playbackId > 0) {
            offlineCacheDao.observePodcastIdForEpisode(playbackId)
        } else {
            flow { emit(findRssOnlyEpisode(playbackId)?.podcastId) }
        }

    suspend fun episodeIds(podcastId: Int): List<Int> = offlineCacheDao.getEpisodes(podcastId)
        .map { episode -> episode.pinePodsEpisodeId ?: rssLocalPlaybackId(episode.cacheKey) }

    /** Cached episodes by their PinePods IDs: dates, lengths and progress for local downloads. */
    suspend fun episodesByPinePodsIds(episodeIds: Collection<Int>): Map<Int, PodcastEpisodeCacheEntity> =
        if (episodeIds.isEmpty()) emptyMap()
        else offlineCacheDao.episodesByPinePodsIds(episodeIds).mapNotNull { row -> row.pinePodsEpisodeId?.let { it to row } }.toMap()

    suspend fun findRssOnlyEpisode(localPlaybackId: Int): PodcastEpisodeCacheEntity? {
        if (localPlaybackId >= 0) return null
        return offlineCacheDao.getRssOnlyEpisodes()
            .firstOrNull { rssLocalPlaybackId(it.cacheKey) == localPlaybackId }
    }

    suspend fun replacePinePodsEpisodes(
        podcastId: Int,
        episodes: List<PodcastEpisodeProgress>,
        refreshedAtEpochMs: Long,
    ) {
        offlineCacheDao.replacePinePodsEpisodes(
            podcastId = podcastId,
            items = episodes.map { episode ->
                episode.toCacheEntity(podcastId, refreshedAtEpochMs)
            },
        )
        promoteResolvedCachedIntents(podcastId)
        recordPinePodsPodcastRefresh(podcastId, refreshedAtEpochMs)
    }

    suspend fun upsertPinePodsDetails(
        details: PodcastDetails,
        refreshedAtEpochMs: Long,
    ) {
        offlineCacheDao.upsertPodcastDetails(details.toCacheEntity(refreshedAtEpochMs))
        offlineCacheDao.upsertEpisodesKeepingNumbers(
            details.podcastId,
            details.recentEpisodes.map { episode ->
                episode.toCacheEntity(details.podcastId, refreshedAtEpochMs)
            },
        )
        promoteResolvedCachedIntents(details.podcastId)
        recordPinePodsPodcastRefresh(details.podcastId, refreshedAtEpochMs)
    }

    suspend fun refreshPublicRss(
        subscription: PodcastSubscriptionEntity,
        client: PublicRssFeedClient,
        force: Boolean,
        refreshedAtEpochMs: Long,
    ): RssCacheRefreshOutcome {
        val feedUrl = subscription.feedUrl.trim()
        if (feedUrl.isBlank()) return RssCacheRefreshOutcome.SKIPPED
        val sourceKey = rssPodcastRefreshKey(subscription.podcastId)
        val previous = offlineCacheDao.getRefreshState(sourceKey)
        if (!force && !refreshDue(
                lastSuccessEpochMs = previous?.lastSuccessAtEpochMs,
                nowEpochMs = refreshedAtEpochMs,
                ttlMs = FOREGROUND_RSS_TTL_MS,
            )
        ) {
            return RssCacheRefreshOutcome.SKIPPED
        }

        return try {
            when (val result = client.fetch(
                feedUrl = feedUrl,
                validators = RssFeedValidators(previous?.etag, previous?.lastModified),
            )) {
                is PublicRssFetchResult.NotModified -> {
                    offlineCacheDao.upsertRefreshState(
                        rssRefreshState(
                            subscription = subscription,
                            previous = previous,
                            feedUrl = result.finalUrl,
                            validators = result.validators,
                            attemptedAtEpochMs = refreshedAtEpochMs,
                            succeeded = true,
                            error = null,
                        ),
                    )
                    RssCacheRefreshOutcome.NOT_MODIFIED
                }
                is PublicRssFetchResult.Modified -> {
                    val existing = offlineCacheDao.getEpisodes(subscription.podcastId)
                    val resolvedMappings = resolvedRssEpisodeMappings(
                        subscription = subscription,
                        feed = result.feed,
                        existing = existing,
                    )
                    // Promote before the merge drops the RSS rows; if promotion is refused (quarantine),
                    // keep the cache as it is so the deferred intents stay reachable (review 3.6).
                    if (resolvedMappings.isNotEmpty() && promoteDeferredIntents(resolvedMappings) == PROMOTION_REFUSED) {
                        return RssCacheRefreshOutcome.SKIPPED
                    }
                    val merged = mergePublicRssEpisodes(
                        subscription = subscription,
                        feed = result.feed,
                        existing = existing,
                        refreshedAtEpochMs = refreshedAtEpochMs,
                    )
                    offlineCacheDao.replaceRssEpisodes(subscription.podcastId, merged)
                    upsertRssDetailsFallback(subscription, result.feed, refreshedAtEpochMs)
                    offlineCacheDao.upsertRefreshState(
                        rssRefreshState(
                            subscription = subscription,
                            previous = previous,
                            feedUrl = result.finalUrl,
                            validators = result.validators,
                            attemptedAtEpochMs = refreshedAtEpochMs,
                            succeeded = true,
                            error = null,
                        ),
                    )
                    RssCacheRefreshOutcome.UPDATED
                }
            }
        } catch (error: Exception) {
            val category = (error as? QueueLoadException)?.reason ?: QueueLoadError.INVALID_RESPONSE
            offlineCacheDao.upsertRefreshState(
                rssRefreshState(
                    subscription = subscription,
                    previous = previous,
                    feedUrl = feedUrl,
                    validators = RssFeedValidators(previous?.etag, previous?.lastModified),
                    attemptedAtEpochMs = refreshedAtEpochMs,
                    succeeded = false,
                    error = category,
                ),
            )
            throw error
        }
    }

    /**
     * N-05: the full episode list of a podcast is read again only when the catalog's episode
     * count changed since the last full read, or after [FULL_EPISODE_LIST_TTL_MS]. The count seen
     * at the last full read is kept in the refresh-state row's validator field.
     */
    suspend fun isFullEpisodeListDue(podcastId: Int, catalogEpisodeCount: Int?, nowEpochMs: Long): Boolean {
        val state = offlineCacheDao.getRefreshState(fullEpisodeListRefreshKey(podcastId)) ?: return true
        if (refreshDue(state.lastSuccessAtEpochMs, nowEpochMs, FULL_EPISODE_LIST_TTL_MS)) return true
        return state.etag != catalogEpisodeCount?.toString()
    }

    suspend fun recordFullEpisodeList(podcastId: Int, catalogEpisodeCount: Int?, refreshedAtEpochMs: Long) {
        offlineCacheDao.upsertRefreshState(
            successfulRefreshState(
                sourceKey = fullEpisodeListRefreshKey(podcastId),
                podcastId = podcastId,
                refreshedAtEpochMs = refreshedAtEpochMs,
            ).copy(etag = catalogEpisodeCount?.toString()),
        )
    }

    suspend fun recordCatalogRefresh(refreshedAtEpochMs: Long) {
        offlineCacheDao.upsertRefreshState(
            successfulRefreshState(
                sourceKey = PINEPODS_CATALOG_REFRESH_KEY,
                podcastId = null,
                refreshedAtEpochMs = refreshedAtEpochMs,
            ),
        )
    }

    private suspend fun recordPinePodsPodcastRefresh(
        podcastId: Int,
        refreshedAtEpochMs: Long,
    ) {
        offlineCacheDao.upsertRefreshState(
            successfulRefreshState(
                sourceKey = pinePodsPodcastRefreshKey(podcastId),
                podcastId = podcastId,
                refreshedAtEpochMs = refreshedAtEpochMs,
            ),
        )
    }

    /**
     * RSS-only rows that PinePods now has under its own ID: their deferred intents move to the
     * PinePods ID first, then the twin row goes. A 304 from the feed never re-merged, so the
     * episode used to stay listed twice until the feed changed (N-03).
     */
    private suspend fun promoteResolvedCachedIntents(podcastId: Int) {
        val mappings = resolvedCachedRssEpisodeMappings(offlineCacheDao.getEpisodes(podcastId))
        if (mappings.isEmpty()) return
        if (promoteDeferredIntents(mappings) == PROMOTION_REFUSED) return
        // The episode playing now keeps its row: the service still writes its progress by the RSS
        // identity until the item changes (review 3.6, finding 3); the next refresh removes it.
        val playing = offlineCacheDao.getCurrentPlaybackContext()?.episodeCacheKey
        offlineCacheDao.deleteRssTwins(mappings.keys - setOfNotNull(playing))
    }

    private suspend fun isRefreshDue(
        sourceKey: String,
        nowEpochMs: Long,
        ttlMs: Long,
    ): Boolean = refreshDue(
        lastSuccessEpochMs = offlineCacheDao.getRefreshState(sourceKey)?.lastSuccessAtEpochMs,
        nowEpochMs = nowEpochMs,
        ttlMs = ttlMs,
    )

    private suspend fun upsertRssDetailsFallback(
        subscription: PodcastSubscriptionEntity,
        feed: PublicRssFeed,
        refreshedAtEpochMs: Long,
    ) {
        val previous = offlineCacheDao.getPodcastDetails(subscription.podcastId)
        offlineCacheDao.upsertPodcastDetails(
            PodcastDetailsCacheEntity(
                podcastId = subscription.podcastId,
                name = previous?.name?.takeIf(String::isNotBlank)
                    ?: subscription.name.takeIf(String::isNotBlank)
                    ?: feed.title,
                artworkUrl = previous?.artworkUrl?.takeIf(String::isNotBlank)
                    ?: subscription.artworkUrl.takeIf(String::isNotBlank)
                    ?: feed.artworkUrl,
                description = previous?.description?.takeIf(String::isNotBlank)
                    ?: subscription.description.takeIf(String::isNotBlank)
                    ?: feed.description,
                author = previous?.author?.takeIf(String::isNotBlank)
                    ?: subscription.author.takeIf(String::isNotBlank)
                    ?: feed.author,
                categories = previous?.categories.orEmpty(),
                episodeCount = previous?.episodeCount
                    ?: subscription.episodeCount
                    ?: feed.episodes.size,
                episodesPlayed = previous?.episodesPlayed ?: subscription.episodesPlayed,
                refreshedAtEpochMs = maxOf(previous?.refreshedAtEpochMs ?: 0L, refreshedAtEpochMs),
            ),
        )
    }
}

internal fun resolvedRssEpisodeMappings(
    subscription: PodcastSubscriptionEntity,
    feed: PublicRssFeed,
    existing: List<PodcastEpisodeCacheEntity>,
): Map<String, Int> {
    val pinePods = existing.filter { it.pinePodsEpisodeId != null }
    return feed.episodes.mapNotNull { rss ->
        val matchedId = matchPinePodsEpisode(rss, pinePods)?.pinePodsEpisodeId
            ?: return@mapNotNull null
        rssEpisodeCacheKey(
            podcastId = subscription.podcastId,
            guid = rss.guid,
            mediaUrl = rss.mediaUrl,
            title = rss.title,
            publishedAt = rss.publishedAt,
        ) to matchedId
    }.toMap()
}

internal fun resolvedCachedRssEpisodeMappings(
    existing: List<PodcastEpisodeCacheEntity>,
): Map<String, Int> {
    val pinePods = existing.filter { it.pinePodsEpisodeId != null }
    val rssOnly = existing.filter { it.pinePodsEpisodeId == null }
    return rssOnly.mapNotNull { rss ->
        val candidate = PublicRssEpisode(
            guid = rss.guid.orEmpty(),
            title = rss.title,
            description = rss.description,
            publishedAt = rss.publishedAt,
            durationSeconds = rss.durationSeconds,
            artworkUrl = rss.artworkUrl,
            mediaUrl = rss.mediaUrl,
        )
        val matchedId = matchPinePodsEpisode(candidate, pinePods)?.pinePodsEpisodeId
            ?: return@mapNotNull null
        rss.cacheKey to matchedId
    }.toMap()
}

fun CachedPodcastDetails.toPodcastDetails(): PodcastDetails? {
    val subscription = podcast ?: return null
    val cachedDetails = details
    val cachedEpisodes = episodes.mapNotNull(PodcastEpisodeCacheEntity::toEpisodeProgress)
    return PodcastDetails(
        podcastId = subscription.podcastId,
        name = cachedDetails?.name?.takeIf(String::isNotBlank) ?: subscription.name,
        artworkUrl = cachedDetails?.artworkUrl?.takeIf(String::isNotBlank)
            ?: subscription.artworkUrl,
        description = cachedDetails?.description?.takeIf(String::isNotBlank)
            ?: subscription.description,
        author = cachedDetails?.author?.takeIf(String::isNotBlank) ?: subscription.author,
        categories = cachedDetails?.categories.orEmpty(),
        episodeCount = subscription.episodeCount
            ?: cachedDetails?.episodeCount
            ?: cachedEpisodes.size,
        episodesPlayed = subscription.episodesPlayed,
        recentEpisodes = cachedEpisodes,
    )
}

private fun PodcastDetails.toCacheEntity(
    refreshedAtEpochMs: Long,
): PodcastDetailsCacheEntity = PodcastDetailsCacheEntity(
    podcastId = podcastId,
    name = name,
    artworkUrl = artworkUrl,
    description = description,
    author = author,
    categories = categories,
    episodeCount = episodeCount,
    episodesPlayed = episodesPlayed,
    refreshedAtEpochMs = refreshedAtEpochMs,
)

private fun PodcastEpisodeCacheEntity.toEpisodeProgress(): PodcastEpisodeProgress {
    val episodeId = pinePodsEpisodeId ?: rssLocalPlaybackId(cacheKey)
    return PodcastEpisodeProgress(
        episodeId = episodeId,
        title = title,
        publishedAt = publishedAt,
        durationSeconds = durationSeconds,
        listenedSeconds = listenedSeconds,
        completed = completed,
        podcastName = podcastName,
        description = description,
        artworkUrl = artworkUrl,
        mediaUrl = mediaUrl,
        downloaded = downloaded,
        cacheKey = cacheKey,
        pinePodsEpisodeId = pinePodsEpisodeId,
    )
}

internal fun PodcastEpisodeProgress.toCacheEntity(
    podcastId: Int,
    refreshedAtEpochMs: Long,
): PodcastEpisodeCacheEntity = PodcastEpisodeCacheEntity(
    cacheKey = pinePodsEpisodeCacheKey(episodeId),
    podcastId = podcastId,
    pinePodsEpisodeId = episodeId,
    guid = null,
    source = PodcastCacheSource.PINEPODS,
    title = title,
    description = description,
    publishedAt = publishedAt,
    durationSeconds = durationSeconds.coerceAtLeast(0),
    listenedSeconds = listenedSeconds.coerceAtLeast(0.0),
    completed = completed,
    podcastName = podcastName,
    artworkUrl = artworkUrl,
    mediaUrl = mediaUrl,
    downloaded = downloaded,
    refreshedAtEpochMs = refreshedAtEpochMs,
)

internal fun mergePublicRssEpisodes(
    subscription: PodcastSubscriptionEntity,
    feed: PublicRssFeed,
    existing: List<PodcastEpisodeCacheEntity>,
    refreshedAtEpochMs: Long,
): List<PodcastEpisodeCacheEntity> {
    val pinePods = existing.filter { it.pinePodsEpisodeId != null }
    return feed.episodes.map { rss ->
        val matched = matchPinePodsEpisode(rss, pinePods)
        if (matched != null) {
            matched.copy(
                guid = rss.guid.takeIf(String::isNotBlank) ?: matched.guid,
                title = matched.title.ifBlank { rss.title },
                description = matched.description.ifBlank { rss.description },
                publishedAt = matched.publishedAt.ifBlank { rss.publishedAt },
                durationSeconds = matched.durationSeconds.takeIf { it > 0 }
                    ?: rss.durationSeconds,
                podcastName = matched.podcastName.ifBlank { subscription.name },
                artworkUrl = matched.artworkUrl.ifBlank {
                    rss.artworkUrl.ifBlank { feed.artworkUrl.ifBlank { subscription.artworkUrl } }
                },
                mediaUrl = matched.mediaUrl.ifBlank { rss.mediaUrl },
                refreshedAtEpochMs = refreshedAtEpochMs,
                seasonNumber = rss.season,
                episodeNumber = rss.episodeNumber,
            )
        } else {
            val cacheKey = rssEpisodeCacheKey(
                podcastId = subscription.podcastId,
                guid = rss.guid,
                mediaUrl = rss.mediaUrl,
                title = rss.title,
                publishedAt = rss.publishedAt,
            )
            PodcastEpisodeCacheEntity(
                cacheKey = cacheKey,
                podcastId = subscription.podcastId,
                pinePodsEpisodeId = null,
                guid = rss.guid.takeIf(String::isNotBlank),
                source = PodcastCacheSource.RSS,
                title = rss.title,
                description = rss.description,
                publishedAt = rss.publishedAt,
                durationSeconds = rss.durationSeconds,
                listenedSeconds = 0.0,
                completed = false,
                podcastName = subscription.name.ifBlank { feed.title },
                artworkUrl = rss.artworkUrl.ifBlank {
                    feed.artworkUrl.ifBlank { subscription.artworkUrl }
                },
                mediaUrl = rss.mediaUrl,
                downloaded = false,
                refreshedAtEpochMs = refreshedAtEpochMs,
                seasonNumber = rss.season,
                episodeNumber = rss.episodeNumber,
            )
        }
    }.distinctBy(PodcastEpisodeCacheEntity::cacheKey)
}

private fun matchPinePodsEpisode(
    rss: PublicRssEpisode,
    pinePods: List<PodcastEpisodeCacheEntity>,
): PodcastEpisodeCacheEntity? {
    val mediaIdentity = normalizeMediaIdentity(rss.mediaUrl)
    pinePods.filter { normalizeMediaIdentity(it.mediaUrl) == mediaIdentity }
        .singleOrNull()
        ?.let { return it }

    val titleIdentity = normalizeTitleIdentity(rss.title)
    val dateIdentity = normalizeDateIdentity(rss.publishedAt)
    if (titleIdentity.isBlank() || dateIdentity.isBlank()) return null
    return pinePods.filter {
        normalizeTitleIdentity(it.title) == titleIdentity &&
            normalizeDateIdentity(it.publishedAt) == dateIdentity
    }.singleOrNull()
}

private fun normalizeMediaIdentity(value: String): String = value.trim()
    .toHttpUrlOrNull()
    ?.newBuilder()
    ?.fragment(null)
    ?.build()
    ?.toString()
    ?: value.trim().substringBefore('#')

private fun normalizeTitleIdentity(value: String): String = value.trim()
    .lowercase()
    .replace(Regex("\\s+"), " ")

private fun normalizeDateIdentity(value: String): String = normalizeRssDate(value).take(10)

private fun rssRefreshState(
    subscription: PodcastSubscriptionEntity,
    previous: PodcastRefreshStateEntity?,
    feedUrl: String,
    validators: RssFeedValidators,
    attemptedAtEpochMs: Long,
    succeeded: Boolean,
    error: QueueLoadError?,
): PodcastRefreshStateEntity = PodcastRefreshStateEntity(
    sourceKey = rssPodcastRefreshKey(subscription.podcastId),
    podcastId = subscription.podcastId,
    feedUrl = feedUrl,
    etag = validators.etag,
    lastModified = validators.lastModified,
    lastAttemptAtEpochMs = attemptedAtEpochMs,
    lastSuccessAtEpochMs = if (succeeded) attemptedAtEpochMs else previous?.lastSuccessAtEpochMs,
    lastErrorCategory = error?.name,
)

private fun successfulRefreshState(
    sourceKey: String,
    podcastId: Int?,
    refreshedAtEpochMs: Long,
): PodcastRefreshStateEntity = PodcastRefreshStateEntity(
    sourceKey = sourceKey,
    podcastId = podcastId,
    feedUrl = "",
    etag = null,
    lastModified = null,
    lastAttemptAtEpochMs = refreshedAtEpochMs,
    lastSuccessAtEpochMs = refreshedAtEpochMs,
    lastErrorCategory = null,
)

internal const val FOREGROUND_PINEPODS_TTL_MS = 15 * 60_000L
internal const val FOREGROUND_RSS_TTL_MS = 2 * 60 * 60_000L
internal const val FULL_EPISODE_LIST_TTL_MS = 24 * 60 * 60_000L

private fun fullEpisodeListRefreshKey(podcastId: Int) = "pinepods-full-episodes:$podcastId"

internal fun refreshDue(
    lastSuccessEpochMs: Long?,
    nowEpochMs: Long,
    ttlMs: Long,
): Boolean = lastSuccessEpochMs == null || nowEpochMs - lastSuccessEpochMs >= ttlMs
