package ru.e1173.nookast.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import ru.e1173.nookast.auth.AuthSession
import ru.e1173.nookast.data.local.OfflinePodcastCacheRepository
import ru.e1173.nookast.data.local.PodcastSubscriptionEntity
import ru.e1173.nookast.data.local.SyncDatabase
import ru.e1173.nookast.data.local.SyncStore
import ru.e1173.nookast.data.remote.PodcastArtworkFeedResolver
import ru.e1173.nookast.data.remote.PodcastCatalogClient
import ru.e1173.nookast.data.remote.PodcastDetailsClient
import ru.e1173.nookast.data.remote.PublicRssFeedClient
import ru.e1173.nookast.player.ArtworkBitmapRepository
import ru.e1173.nookast.ui.preferredPodcastArtwork

/**
 * The one library refresh for the podcast screen and the background worker (N-04, plan 3.5.8).
 * The two used to be separate copies: the worker wrote raw catalog counters and artwork over the
 * screen's corrected ones, and both could walk every episode of every podcast at the same time.
 *
 * - The fast PinePods catalog is committed first; per-podcast details follow progressively.
 * - The full episode list of a podcast (up to thousands of rows, N-05) is fetched only when its
 *   episode count changed or the last full list is older than a day; otherwise the first page of
 *   details keeps the latest episodes and counters current.
 * - One refresh at a time per process: a second caller returns at once.
 */
class LibraryRefresher(
    database: SyncDatabase,
    private val artworkRepository: ArtworkBitmapRepository,
    private val catalogClient: PodcastCatalogClient = PodcastCatalogClient(),
    private val detailsClient: PodcastDetailsClient = PodcastDetailsClient(),
    private val artworkFeedResolver: PodcastArtworkFeedResolver = PodcastArtworkFeedResolver(),
    private val rssClient: PublicRssFeedClient = PublicRssFeedClient(),
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val quarantined: () -> Boolean = { false },
) {
    private val podcastDao = database.podcastDao()
    private val syncStore = SyncStore(database)
    private val cache = OfflinePodcastCacheRepository(
        podcastDao = podcastDao,
        offlineCacheDao = database.offlineCacheDao(),
        promoteDeferredIntents = syncStore::promoteDeferredEpisodeIntents,
    )
    private val running = Mutex()

    suspend fun isCatalogRefreshDue(): Boolean = withContext(Dispatchers.IO) {
        cache.isCatalogRefreshDue(nowMs())
    }

    /**
     * Refreshes the library. [onCatalogCommitted] runs once the catalog rows are in Room (the list
     * is usable from then on). Throws the catalog failure after refreshing public feeds anyway.
     * Returns false when another refresh was already running.
     */
    suspend fun refresh(
        session: AuthSession,
        forceRss: Boolean,
        onCatalogCommitted: suspend () -> Unit = {},
    ): Boolean {
        // Another account's data is in the database: refreshing would mix the catalogs (§10.3).
        if (quarantined()) return false
        if (!running.tryLock()) return false
        try {
            withContext(Dispatchers.IO) { refreshLocked(session, forceRss, onCatalogCommitted) }
        } finally {
            running.unlock()
        }
        return true
    }

    /** Public feeds only, for when PinePods is unreachable; never touches user intents. */
    suspend fun refreshPublicRssOnly(force: Boolean) = withContext(Dispatchers.IO) {
        refreshRss(podcastDao.getSubscriptions(), force)
    }

    private suspend fun refreshLocked(
        session: AuthSession,
        forceRss: Boolean,
        onCatalogCommitted: suspend () -> Unit,
    ) {
        val committed = runCatching {
            val refreshedAt = nowMs()
            val existing = podcastDao.getSubscriptions().associateBy { it.podcastId }
            val catalog = catalogClient.fetch(session)
            val seed = catalog.map { item ->
                val cached = existing[item.podcastId]
                PodcastSubscriptionEntity(
                    podcastId = item.podcastId,
                    name = item.name,
                    // The artwork chosen by an earlier refresh survives until a better one is found.
                    artworkUrl = cached?.artworkUrl?.takeIf(String::isNotBlank) ?: item.artworkUrl.trim(),
                    description = item.description,
                    episodeCount = cached?.episodeCount ?: item.episodeCount,
                    episodesPlayed = cached?.episodesPlayed ?: item.episodesPlayed,
                    feedUrl = item.feedUrl,
                    author = item.author,
                    latestEpisodeId = cached?.latestEpisodeId,
                    lastUpdatedAt = cached?.lastUpdatedAt.orEmpty(),
                    refreshedAtEpochMs = refreshedAt,
                )
            }
            podcastDao.replaceSubscriptions(seed)
            cache.recordCatalogRefresh(refreshedAt)
            catalog to seed.associateBy(PodcastSubscriptionEntity::podcastId)
        }
        onCatalogCommitted()
        val (catalog, seedById) = committed.getOrElse { error ->
            refreshRss(podcastDao.getSubscriptions(), forceRss)
            throw error
        }

        val limiter = Semaphore(PODCAST_CONCURRENCY)
        coroutineScope {
            catalog.map { item ->
                async {
                    limiter.withPermit {
                        runCatching { refreshPodcast(session, item, seedById.getValue(item.podcastId), forceRss) }
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun refreshPodcast(
        session: AuthSession,
        item: ru.e1173.nookast.data.remote.PodcastCatalogItem,
        seed: PodcastSubscriptionEntity,
        forceRss: Boolean,
    ) {
        val details = runCatching { detailsClient.fetchByPodcastId(session, item.podcastId) }.getOrNull()
        val fullListDue = cache.isFullEpisodeListDue(item.podcastId, item.episodeCount, nowMs())
        // PinePods catalog/detail counters vary across server versions: the played count comes
        // from the episode records whenever the full list is read.
        val allEpisodes = if (fullListDue) {
            runCatching { detailsClient.fetchAllEpisodes(session, item.podcastId) }.getOrNull()
        } else {
            null
        }
        val refreshedAt = nowMs()
        when {
            allEpisodes != null -> {
                cache.replacePinePodsEpisodes(item.podcastId, allEpisodes, refreshedAt)
                cache.recordFullEpisodeList(item.podcastId, item.episodeCount, refreshedAt)
            }
            details != null -> cache.upsertPinePodsDetails(details, refreshedAt)
        }
        val latest = details?.recentEpisodes?.firstOrNull()
        val artworkUrl = preferredPodcastArtwork(
            latestEpisodeArtwork = latest?.artworkUrl,
            catalogArtwork = item.artworkUrl,
            cachedArtwork = seed.artworkUrl,
            resolveRssArtwork = { runCatching { artworkFeedResolver.resolve(item.feedUrl) }.getOrNull() },
            isUsable = { candidate -> artworkRepository.isUsable(candidate) },
        )
        podcastDao.upsertSubscriptionUnlessPendingRemoval(
            seed.copy(
                artworkUrl = artworkUrl,
                episodeCount = allEpisodes?.size?.takeIf { it > 0 }
                    ?: details?.episodeCount
                    ?: seed.episodeCount,
                episodesPlayed = allEpisodes?.takeIf { it.isNotEmpty() }?.count { it.completed }
                    ?: seed.episodesPlayed,
                latestEpisodeId = latest?.episodeId ?: seed.latestEpisodeId,
                lastUpdatedAt = latest?.publishedAt ?: seed.lastUpdatedAt,
                refreshedAtEpochMs = nowMs(),
            ),
        )
        cache.refreshPublicRss(seed, rssClient, forceRss, nowMs())
    }

    private suspend fun refreshRss(subscriptions: List<PodcastSubscriptionEntity>, force: Boolean) {
        val limiter = Semaphore(PODCAST_CONCURRENCY)
        coroutineScope {
            subscriptions.map { subscription ->
                async {
                    limiter.withPermit {
                        runCatching { cache.refreshPublicRss(subscription, rssClient, force, nowMs()) }
                    }
                }
            }.awaitAll()
        }
    }

    private companion object {
        const val PODCAST_CONCURRENCY = 3
    }
}
