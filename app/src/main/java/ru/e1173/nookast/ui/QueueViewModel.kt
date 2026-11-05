package ru.e1173.nookast.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import ru.e1173.nookast.app.appGraph
import ru.e1173.nookast.core.session.SessionRepository
import ru.e1173.nookast.data.local.QueueListEntry
import ru.e1173.nookast.data.podcastFreshnessOf
import ru.e1173.nookast.data.local.QueueEpisodeEntity
import ru.e1173.nookast.data.local.DashboardCacheEntity
import ru.e1173.nookast.data.local.DashboardContinueLocalEntry
import ru.e1173.nookast.data.local.PodcastMutationKind
import ru.e1173.nookast.data.local.PodcastOutboxEntity
import ru.e1173.nookast.data.local.PodcastSubscriptionEntity
import ru.e1173.nookast.data.local.OfflinePodcastCacheRepository
import ru.e1173.nookast.data.local.toPodcastDetails
import ru.e1173.nookast.data.local.ProgressOutboxEntity
import ru.e1173.nookast.data.local.SyncDatabase
import ru.e1173.nookast.data.local.SyncStore
import ru.e1173.nookast.data.local.SavedEpisodeOutboxEntity
import ru.e1173.nookast.data.remote.QueueClient
import ru.e1173.nookast.data.remote.DashboardClient
import ru.e1173.nookast.data.remote.DashboardSnapshot
import ru.e1173.nookast.data.remote.dashboardSnapshotFromCacheJson
import ru.e1173.nookast.data.remote.toCacheJson
import ru.e1173.nookast.data.remote.PodcastDetails
import ru.e1173.nookast.data.remote.PodcastDetailsClient
import ru.e1173.nookast.data.remote.PublicRssFeedClient
import ru.e1173.nookast.data.remote.PublicRssFetchResult
import ru.e1173.nookast.data.remote.RssFeedValidators
import ru.e1173.nookast.data.remote.PodcastFeedClient
import ru.e1173.nookast.data.remote.FeedChangeRefused
import ru.e1173.nookast.data.remote.ServerFeedError
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import ru.e1173.nookast.data.remote.QueueLoadError
import ru.e1173.nookast.data.remote.QueueLoadException
import ru.e1173.nookast.data.remote.SyncErrorCategory
import ru.e1173.nookast.core.sync.SyncFailure
import ru.e1173.nookast.offline.EpisodeDownloadManager
import ru.e1173.nookast.offline.EpisodeDownloadState
import ru.e1173.nookast.offline.LocalDownloadedEpisode
import ru.e1173.nookast.player.PlaybackPreferences
import ru.e1173.nookast.player.QueueInsertionPosition
import ru.e1173.nookast.player.QueueSwipeAction
import ru.e1173.nookast.player.PlaybackStorage
import ru.e1173.nookast.player.ArtworkBitmapRepository
import ru.e1173.nookast.player.MediaButtonProfileMode
import ru.e1173.nookast.sync.ProgressCause
import ru.e1173.nookast.sync.ProgressMutation
import ru.e1173.nookast.sync.QueueMutation
import ru.e1173.nookast.sync.LocalSequenceGenerator
import ru.e1173.nookast.sync.SyncWorkScheduler

data class QueueUiState(
    val entries: List<QueueListEntry> = emptyList(),
    val isRefreshing: Boolean = false,
    val error: QueueLoadError? = null,
    val lastUpdatedEpochMs: Long? = null,
    val pendingProgressCount: Int = 0,
    val pendingQueueCount: Int = 0,
    val syncError: SyncErrorCategory? = null,
    val downloadStates: Map<Int, EpisodeDownloadState> = emptyMap(),
    val downloadProgress: Map<Int, Float?> = emptyMap(),
    val aheadCacheBytes: Long = 0,
    val offlineDownloadBytes: Long = 0,
    val aheadCacheClearPending: Boolean = false,
    val podcastDetails: Map<Int, PodcastDetails> = emptyMap(),
    val podcastDetailsLoading: Set<Int> = emptySet(),
    val podcasts: List<PodcastSubscriptionEntity> = emptyList(),
    // Pills over the covers in Podcasts: only shows that are not [PodcastFreshness.ACTIVE].
    val podcastFreshness: Map<Int, ru.e1173.nookast.data.PodcastFreshness> = emptyMap(),
    // Feeds the server keeps failing to read (PinePods 0.9.1+ «Feed problems»), by podcast id.
    val serverFeedProblems: Map<Int, ru.e1173.nookast.data.remote.ServerFeedProblem> = emptyMap(),
    val podcastsRefreshing: Boolean = false,
    val podcastsError: QueueLoadError? = null,
    val pendingPodcastCount: Int = 0,
    val pendingSavedEpisodeCount: Int = 0,
    val savedEpisodeOverrides: Map<Int, Boolean> = emptyMap(),
    val podcastDetailsById: Map<Int, PodcastDetails> = emptyMap(),
    val podcastDetailsByIdLoading: Set<Int> = emptySet(),
    val podcastActionsInProgress: Set<Int> = emptySet(),
    val podcastActionErrors: Map<Int, QueueLoadError> = emptyMap(),
    val pendingPodcastEpisodeProgress: Map<Int, ProgressOutboxEntity> = emptyMap(),
    val podcastEpisodeProgressUpdatedAt: Map<Int, Long> = emptyMap(),
    /** Changes whenever the Room catalog has newer artwork metadata. */
    val artworkRevisionEpochMs: Long = 0,
    val dashboard: DashboardSnapshot? = null,
    val dashboardRefreshing: Boolean = false,
    val dashboardError: QueueLoadError? = null,
    val localDownloads: List<LocalDownloadedEpisode> = emptyList(),
    val dashboardContinueEntries: List<DashboardContinueLocalEntry> = emptyList(),
    /** The Dashboard has read its disk cache, continue list and downloads: until then it stays
     *  hidden instead of showing empty sections that then jump (user, 29.10.2026). */
    val dashboardLocalReady: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class QueueViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val clock: Clock = Clock.systemUTC()

    // What the Dashboard waits for before it shows (see QueueUiState.dashboardLocalReady).
    private var dashboardCacheRead = false
    private var dashboardContinueRead = false
    private var localDownloadsRead = false
    private val database = SyncDatabase.open(application)
    private val syncStore = SyncStore(database)
    private val offlinePodcastCache = OfflinePodcastCacheRepository(
        podcastDao = syncStore.podcastDao,
        offlineCacheDao = database.offlineCacheDao(),
        promoteDeferredIntents = syncStore::promoteDeferredEpisodeIntents,
    )
    private val sessionStore = application.appGraph.session
    private val podcastDetailsClient = PodcastDetailsClient()
    private val publicRssFeedClient = PublicRssFeedClient()
    private val podcastFeedClient = PodcastFeedClient()
    private val libraryRefresher = application.appGraph.libraryRefresher
    private val dashboardClient = DashboardClient()
    private val downloadManager = EpisodeDownloadManager(application)
    private val playbackPreferences = PlaybackPreferences(application)
    private val playbackStorage = PlaybackStorage(application)
    private val artworkRepository = ArtworkBitmapRepository.get(application)
    private var refreshJob: Job? = null
    private var autoSyncJob: Job? = null
    private val podcastDetailsJobs = mutableMapOf<Int, Job>()
    private val podcastDetailsByIdJobs = mutableMapOf<Int, Job>()
    private val focusedPodcastIds = MutableStateFlow<List<Int>>(emptyList())
    private val focusedEpisodeIds = MutableStateFlow<List<Int>>(emptyList())
    private var podcastCatalogJob: Job? = null
    private var dashboardJob: Job? = null
    private var downloadStatesRefreshJob: Job? = null
    private val syncMutex = Mutex()
    private val sequence = LocalSequenceGenerator(application)
    private val connectivityManager = application.getSystemService(
        Context.CONNECTIVITY_SERVICE,
    ) as ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // A validated network wakes the process scheduler; nothing to do here (I7).
            getApplication<Application>().appGraph.syncScheduler.requestSync()
        }
    }

    private val _state = MutableStateFlow(QueueUiState())
    val state: StateFlow<QueueUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                syncStore.queueDao.observeQueueEntries(),
                syncStore.queueDao.observeQueueState(),
                database.syncV8Dao().observeDirtyProgressCount(),
                database.syncV8Dao().observeQueueIntentCount(),
            ) { entries, queueState, pendingProgress, pendingQueue ->
                QueueSnapshot(entries, queueState?.fetchedAtEpochMs, pendingProgress, pendingQueue)
            }.collect { snapshot ->
                _state.update {
                    it.copy(
                        entries = snapshot.entries,
                        lastUpdatedEpochMs = snapshot.lastUpdatedEpochMs,
                        pendingProgressCount = snapshot.pendingProgressCount,
                        pendingQueueCount = snapshot.pendingQueueCount,
                    )
                }
                refreshDownloadStates(snapshot.entries)
            }
        }
        viewModelScope.launch {
            database.syncV8Dao().observeDirtyProgress().collect { pending ->
                _state.update {
                    it.copy(
                        pendingPodcastEpisodeProgress = pending.associate { row ->
                            row.episodeId to ProgressOutboxEntity(
                                episodeId = row.episodeId,
                                positionMs = row.positionMs,
                                durationMs = row.durationMs,
                                completed = row.completed,
                                cause = row.cause,
                                localSequence = row.intentCounter,
                                recordedAtEpochMs = row.updatedAtEpochMs,
                            )
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            // Every playback tick writes a row; reading two columns, off the main thread and at most
            // once a second keeps that from rebuilding the UI state every 5 s (N-07).
            syncStore.progressDao.observeProgressUpdateTimes()
                .debounce(PROGRESS_TIMES_DEBOUNCE_MS)
                .map { rows -> rows.associate { it.episodeId to it.updatedAtEpochMs } }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
                .collect { times ->
                    _state.update { it.copy(podcastEpisodeProgressUpdatedAt = times) }
                }
        }
        viewModelScope.launch {
            combine(
                syncStore.podcastDao.observeSubscriptions(),
                database.offlineCacheDao().observeLatestPublished(),
                database.offlineCacheDao().observeRssRefreshStates(),
            ) { podcasts, latest, states -> podcastFreshnessOf(podcasts, latest, states) }
                .flowOn(kotlinx.coroutines.Dispatchers.Default).collect { freshness ->
                _state.update { it.copy(podcastFreshness = freshness) }
            }
        }
        viewModelScope.launch {
            combine(
                syncStore.podcastDao.observeSubscriptions(),
                syncStore.podcastDao.observePendingCount(),
            ) { podcasts, pendingCount -> podcasts to pendingCount }
                .collect { (podcasts, pendingCount) ->
                    _state.update {
                        it.copy(
                            podcasts = podcasts,
                            pendingPodcastCount = pendingCount,
                            artworkRevisionEpochMs = podcasts.maxOfOrNull {
                                podcast -> podcast.refreshedAtEpochMs
                            } ?: 0L,
                            dashboard = it.dashboard?.withPodcastArtwork(podcasts),
                        )
                    }
                }
        }
        viewModelScope.launch {
            // Podcast covers are fetched to the disk cache in the background, so scrolling the
            // podcast list only decodes local files instead of waiting for each cover's network
            // request as it comes into view (APK-2.1). Bounds-only: nothing is held in memory.
            syncStore.podcastDao.observeSubscriptions()
                .map { podcasts -> podcasts.map(PodcastSubscriptionEntity::artworkUrl).filter(String::isNotBlank).toSet() }
                .distinctUntilChanged()
                .debounce(ARTWORK_PREFETCH_DEBOUNCE_MS)
                .collect { urls ->
                    withContext(Dispatchers.IO) {
                        val permits = Semaphore(ARTWORK_PREFETCH_CONCURRENCY)
                        coroutineScope {
                            urls.forEach { url ->
                                launch { permits.withPermit { runCatching { artworkRepository.isUsable(url) } } }
                            }
                        }
                    }
                }
        }
        viewModelScope.launch {
            // Only podcasts the UI is showing: the selected one and those of focused episodes
            // (current and pending). Catalog refreshes write podcast after podcast; debounce
            // coalesces those bursts instead of rebuilding state on every write.
            val episodePodcastIds = focusedEpisodeIds.flatMapLatest { ids ->
                if (ids.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(ids.map { offlinePodcastCache.observePodcastIdForEpisode(it) }) {
                        it.filterNotNull()
                    }
                }
            }
            var previousDetails: Map<Int, PodcastDetails> = emptyMap()
            combine(focusedPodcastIds, episodePodcastIds) { selected, ofEpisodes ->
                (selected + ofEpisodes).toSet()
            }
                .distinctUntilChanged()
                .flatMapLatest { ids -> offlinePodcastCache.observePodcastDetails(ids) }
                .debounce(PODCAST_DETAILS_DEBOUNCE_MS)
                .map { cached ->
                    buildPodcastDetailsSnapshot(cached, previousDetails).also { previousDetails = it.byPodcastId }
                }
                // Unchanged podcasts keep their previous instances, so this check and the state
                // comparison on the main thread are identity checks. Comparing fresh copies deep
                // (hundreds of episodes for each of hundreds of episode keys) froze the UI for
                // seconds on every catalog write (ANR, APK-2.1).
                .distinctUntilChanged { old, new -> old.byPodcastId == new.byPodcastId }
                .flowOn(Dispatchers.Default)
                .collect { snapshot ->
                _state.update { current ->
                    current.copy(
                        podcastDetails = snapshot.byEpisodeId,
                        podcastDetailsById = snapshot.byPodcastId,
                    )
                }
            }
        }
        viewModelScope.launch {
            syncStore.dashboardDao.observe().collect { cached ->
                val dashboard = withContext(Dispatchers.Default) {
                    cached?.payloadJson?.let(::dashboardSnapshotFromCacheJson)
                }
                if (cached != null && dashboard == null) {
                    dashboardCacheRead = true
                    markDashboardReady()
                    return@collect
                }
                dashboardCacheRead = true
                _state.update { it.copy(dashboard = dashboard) }
                markDashboardReady()
            }
        }
        viewModelScope.launch {
            syncStore.dashboardDao.observeContinueEntries().collect { entries ->
                dashboardContinueRead = true
                _state.update { it.copy(dashboardContinueEntries = entries) }
                markDashboardReady()
            }
        }
        viewModelScope.launch {
            syncStore.savedEpisodeDao.observeAll().collect { pending ->
                _state.update { current ->
                    current.copy(
                        pendingSavedEpisodeCount = pending.size,
                        savedEpisodeOverrides = current.savedEpisodeOverrides +
                            pending.associate { it.episodeId to it.desiredSaved },
                    )
                }
            }
        }
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        refreshStorageStats()
        viewModelScope.launch {
            while (isActive) {
                delay(750)
                val current = _state.value
                if (current.downloadStates.values.any {
                        it == EpisodeDownloadState.QUEUED ||
                            it == EpisodeDownloadState.DOWNLOADING
                    }
                ) {
                    refreshDownloadStates(current.entries)
                }
            }
        }
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        val session = sessionStore.load() ?: return
        startAutoSync()
        refreshJob = viewModelScope.launch {
            synchronizeAndRefresh(showSpinner = true)
        }
    }

    fun removeFromQueue(episodeId: Int) {
        enqueueQueueMutation(
            QueueMutation.Remove(
                episodeId = episodeId,
                operationId = UUID.randomUUID().toString(),
                sequence = nextSequence(),
            ),
        )
    }

    fun moveInQueue(episodeId: Int, delta: Int) {
        val currentIndex = _state.value.entries.indexOfFirst { it.episodeId == episodeId }
        if (currentIndex < 0) return
        val destination = (currentIndex + delta).coerceIn(0, _state.value.entries.lastIndex)
        moveInQueueTo(episodeId, destination)
    }

    fun moveInQueueTo(episodeId: Int, destinationIndex: Int) {
        val currentIndex = _state.value.entries.indexOfFirst { it.episodeId == episodeId }
        if (currentIndex < 0) return
        val destination = destinationIndex.coerceIn(0, _state.value.entries.lastIndex)
        if (destination == currentIndex) return
        enqueueQueueMutation(
            QueueMutation.Move(
                episodeId = episodeId,
                destinationIndex = destination,
                operationId = UUID.randomUUID().toString(),
                sequence = nextSequence(),
            ),
        )
    }

    fun addStartedEpisodeToQueue(entry: QueueListEntry?) {
        entry ?: return
        if (!playbackPreferences.autoAddStartedEpisodeToQueue) return
        enqueueEpisodeIfMissing(entry, playbackPreferences.playbackStartQueuePosition)
    }

    fun addEpisodeToQueue(entry: QueueListEntry) {
        enqueueEpisodeIfMissing(entry, playbackPreferences.manualQueuePosition)
    }

    fun addEpisodesToQueue(
        entries: List<QueueListEntry>,
        onLocalCommit: (Boolean) -> Unit,
    ) {
        val unique = entries.distinctBy(QueueListEntry::episodeId)
        if (unique.isEmpty()) {
            onLocalCommit(true)
            return
        }
        viewModelScope.launch {
            val position = playbackPreferences.manualQueuePosition
            val ordered = if (position == QueueInsertionPosition.FIRST) {
                unique.asReversed()
            } else {
                unique
            }
            val committed = try {
                withContext(Dispatchers.IO) {
                    ordered.forEach { entry ->
                        val now = clock.millis()
                        val destination = when (position) {
                            QueueInsertionPosition.FIRST -> 0
                            QueueInsertionPosition.LAST -> null
                        }
                        if (entry.episodeId > 0) {
                            syncStore.cacheEpisodeAndEnqueueIfMissing(
                                episode = entry.toEpisodeCache(now),
                                mutation = QueueMutation.Add(
                                    episodeId = entry.episodeId,
                                    destinationIndex = destination,
                                    operationId = UUID.randomUUID().toString(),
                                    sequence = nextSequence(),
                                ),
                                createdAtEpochMs = now,
                            )
                        } else {
                            val cacheKey = requireNotNull(
                                offlinePodcastCache.findRssOnlyEpisode(entry.episodeId)?.cacheKey,
                            )
                            syncStore.stageDeferredQueue(
                                episodeCacheKey = cacheKey,
                                desiredQueued = true,
                                destinationIndex = destination,
                                sequence = nextSequence(),
                                createdAtEpochMs = now,
                            )
                        }
                    }
                }
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (committed) SyncWorkScheduler.enqueueImmediate(getApplication())
            onLocalCommit(committed)
            if (committed) synchronizeAndRefresh(showSpinner = false, refreshRemote = false)
        }
    }

    fun downloadEpisode(entry: QueueListEntry) {
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) { downloadManager.enqueue(entry) }
            _state.update {
                it.copy(
                    downloadStates = it.downloadStates + (entry.episodeId to state),
                    downloadProgress = it.downloadProgress + (entry.episodeId to null),
                )
            }
            refreshStorageStats()
        }
    }

    fun removeEpisodeDownload(episodeId: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { downloadManager.remove(episodeId) }
            _state.update {
                it.copy(
                    downloadStates = it.downloadStates +
                        (episodeId to EpisodeDownloadState.NOT_DOWNLOADED),
                    downloadProgress = it.downloadProgress - episodeId,
                )
            }
            refreshStorageStats()
        }
    }

    fun markEpisodePlayed(entry: QueueListEntry) {
        val durationMs = (entry.durationSeconds ?: 0).toLong().coerceAtLeast(0L) * 1_000L
        val now = clock.millis()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (entry.episodeId > 0) {
                    syncStore.recordCompletionAndRemoveFromQueue(
                        progress = ProgressMutation(
                            episodeId = entry.episodeId,
                            positionMs = durationMs,
                            durationMs = durationMs.takeIf { it > 0L },
                            cause = ProgressCause.COMPLETE,
                            sequence = sequence.next(now),
                            recordedAtEpochMs = now,
                        ),
                        removal = QueueMutation.Remove(
                            episodeId = entry.episodeId,
                            operationId = UUID.randomUUID().toString(),
                            sequence = sequence.next(now),
                        ),
                        createdAtEpochMs = now,
                    )
                } else {
                    val cacheKey = offlinePodcastCache.findRssOnlyEpisode(entry.episodeId)?.cacheKey
                        ?: return@withContext
                    syncStore.stageDeferredCompletionAndQueueRemoval(
                        episodeCacheKey = cacheKey,
                        positionMs = durationMs,
                        durationMs = durationMs.takeIf { it > 0L },
                        progressSequence = sequence.next(now),
                        queueSequence = sequence.next(now),
                        createdAtEpochMs = now,
                    )
                }
            }
            if (entry.episodeId > 0) SyncWorkScheduler.enqueueImmediate(getApplication())
        }
    }

    fun markEpisodesPlayed(
        entries: List<QueueListEntry>,
        onLocalCommit: (Boolean) -> Unit = {},
    ) {
        val unique = entries.distinctBy(QueueListEntry::episodeId)
        if (unique.isEmpty()) {
            onLocalCommit(true)
            return
        }
        val queuedIds = _state.value.entries.mapTo(hashSetOf(), QueueListEntry::episodeId)
        val now = clock.millis()
        viewModelScope.launch {
            val committed = try {
                withContext(Dispatchers.IO) {
                    unique.forEach { entry ->
                        val durationMs = (entry.durationSeconds ?: 0)
                            .toLong()
                            .coerceAtLeast(0L) * 1_000L
                        if (entry.episodeId <= 0) {
                            val cacheKey = requireNotNull(
                                offlinePodcastCache.findRssOnlyEpisode(entry.episodeId)?.cacheKey,
                            )
                            syncStore.stageDeferredCompletionAndQueueRemoval(
                                episodeCacheKey = cacheKey,
                                positionMs = durationMs,
                                durationMs = durationMs.takeIf { it > 0L },
                                progressSequence = sequence.next(now),
                                queueSequence = sequence.next(now),
                                createdAtEpochMs = now,
                            )
                        } else if (entry.episodeId in queuedIds) {
                            syncStore.recordCompletionAndRemoveFromQueue(
                                progress = ProgressMutation(
                                    episodeId = entry.episodeId,
                                    positionMs = durationMs,
                                    durationMs = durationMs.takeIf { it > 0L },
                                    cause = ProgressCause.COMPLETE,
                                    sequence = sequence.next(now),
                                    recordedAtEpochMs = now,
                                ),
                                removal = QueueMutation.Remove(
                                    episodeId = entry.episodeId,
                                    operationId = UUID.randomUUID().toString(),
                                    sequence = sequence.next(now),
                                ),
                                createdAtEpochMs = now,
                            )
                        } else {
                            syncStore.recordProgress(
                                ProgressMutation(
                                    episodeId = entry.episodeId,
                                    positionMs = durationMs,
                                    durationMs = durationMs.takeIf { it > 0L },
                                    cause = ProgressCause.COMPLETE,
                                    sequence = sequence.next(now),
                                    recordedAtEpochMs = now,
                                ),
                            )
                        }
                    }
                }
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (committed && unique.any { it.episodeId > 0 }) {
                SyncWorkScheduler.enqueueImmediate(getApplication())
            }
            onLocalCommit(committed)
        }
    }

    fun markEpisodesUnplayed(
        entries: List<QueueListEntry>,
        onLocalCommit: (Boolean) -> Unit = {},
    ) {
        val unique = entries.distinctBy(QueueListEntry::episodeId)
        if (unique.isEmpty()) {
            onLocalCommit(true)
            return
        }
        val now = clock.millis()
        viewModelScope.launch {
            val sequences = sequence.nextBatch(unique.size, now).iterator()
            val committed = try {
                withContext(Dispatchers.IO) {
                    unique.forEach { entry ->
                        val durationMs = entry.durationSeconds
                            ?.coerceAtLeast(0)
                            ?.toLong()
                            ?.times(1_000L)
                        if (entry.episodeId > 0) {
                            syncStore.recordProgress(
                                ProgressMutation(
                                    episodeId = entry.episodeId,
                                    positionMs = 0,
                                    durationMs = durationMs,
                                    cause = ProgressCause.UNCOMPLETE,
                                    sequence = sequences.nextLong(),
                                    recordedAtEpochMs = now,
                                ),
                            )
                        } else {
                            val cacheKey = requireNotNull(
                                offlinePodcastCache.findRssOnlyEpisode(entry.episodeId)?.cacheKey,
                            )
                            syncStore.stageDeferredProgress(
                                episodeCacheKey = cacheKey,
                                positionMs = 0,
                                durationMs = durationMs,
                                cause = ProgressCause.UNCOMPLETE,
                                sequence = sequences.nextLong(),
                                createdAtEpochMs = now,
                            )
                        }
                    }
                }
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (committed && unique.any { it.episodeId > 0 }) {
                SyncWorkScheduler.enqueueImmediate(getApplication())
            }
            onLocalCommit(committed)
        }
    }

    fun toggleSavedEpisode(episodeId: Int, currentlySaved: Boolean) {
        val desiredSaved = !currentlySaved
        val now = clock.millis()
        _state.update { current ->
            current.copy(
                savedEpisodeOverrides = current.savedEpisodeOverrides +
                    (episodeId to desiredSaved),
            )
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (episodeId > 0) {
                    syncStore.stageSavedEpisode(
                        SavedEpisodeOutboxEntity(
                            episodeId = episodeId,
                            desiredSaved = desiredSaved,
                            sequence = sequence.next(now),
                            createdAtEpochMs = now,
                        ),
                    )
                } else {
                    val cacheKey = offlinePodcastCache.findRssOnlyEpisode(episodeId)?.cacheKey
                        ?: return@withContext
                    syncStore.stageDeferredSaved(
                        episodeCacheKey = cacheKey,
                        desiredSaved = desiredSaved,
                        sequence = sequence.next(now),
                        createdAtEpochMs = now,
                    )
                }
            }
            if (episodeId > 0) {
                SyncWorkScheduler.enqueueImmediate(getApplication())
                synchronizeAndRefresh(showSpinner = false, refreshRemote = false)
            }
        }
    }

    fun loadPodcastDetails(episodeId: Int) {
        focusedEpisodeIds.focus(episodeId)
        if (episodeId <= 0) return
        if (podcastDetailsJobs[episodeId]?.isActive == true) return
        val session = sessionStore.load() ?: return
        podcastDetailsJobs[episodeId] = viewModelScope.launch {
            val shouldRefresh = withContext(Dispatchers.IO) {
                offlinePodcastCache.isEpisodePodcastRefreshDue(episodeId, clock.millis())
            }
            if (!shouldRefresh) {
                podcastDetailsJobs.remove(episodeId)
                return@launch
            }
            _state.update { it.copy(podcastDetailsLoading = it.podcastDetailsLoading + episodeId) }
            runCatching {
                withContext(Dispatchers.IO) {
                    podcastDetailsClient.fetch(session, episodeId).also { fetched ->
                        offlinePodcastCache.upsertPinePodsDetails(fetched, clock.millis())
                    }
                }
            }
            _state.update { current ->
                current.copy(podcastDetailsLoading = current.podcastDetailsLoading - episodeId)
            }
            podcastDetailsJobs.remove(episodeId)
        }
    }

    fun refreshPodcastCatalog() {
        startPodcastCatalogRefresh(forceRss = true)
    }

    /**
     * Points a subscription at another RSS address (user, 6.11.2026). The phone reads the new feed
     * first, so a typo is caught before the server is touched; then the server saves it and reads it
     * at once (PinePods 0.9.1+), and says whether it could.
     */
    fun changeFeedUrl(podcastId: Int, rawUrl: String, onResult: (FeedUrlChangeResult) -> Unit) {
        val url = rawUrl.trim()
        val parsed = url.toHttpUrlOrNull()
        if (parsed == null || (parsed.scheme != "https" && parsed.scheme != "http")) {
            onResult(FeedUrlChangeResult.InvalidUrl)
            return
        }
        val session = sessionStore.load() ?: run {
            onResult(FeedUrlChangeResult.Offline)
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val feed = try {
                    when (val fetched = publicRssFeedClient.fetch(url, RssFeedValidators(null, null))) {
                        is PublicRssFetchResult.Modified -> fetched.feed
                        is PublicRssFetchResult.NotModified -> null
                    }
                } catch (error: QueueLoadException) {
                    return@withContext if (error.reason == QueueLoadError.NETWORK) {
                        FeedUrlChangeResult.DeviceCannotRead(offline = true)
                    } else {
                        FeedUrlChangeResult.DeviceCannotRead(offline = false)
                    }
                }
                try {
                    podcastFeedClient.changeFeedUrl(session, podcastId, url)
                    val problem = podcastFeedClient.refreshNow(session, podcastId)
                    if (problem != null) {
                        FeedUrlChangeResult.ServerCannotRead(problem.kind)
                    } else {
                        FeedUrlChangeResult.Saved(feed?.title.orEmpty(), feed?.episodes?.size ?: 0)
                    }
                } catch (error: FeedChangeRefused) {
                    FeedUrlChangeResult.Refused(error.serverMessage)
                } catch (error: QueueLoadException) {
                    if (error.reason == QueueLoadError.NETWORK) FeedUrlChangeResult.Offline else FeedUrlChangeResult.Refused("")
                }
            }
            if (result is FeedUrlChangeResult.Saved) {
                _state.update { it.copy(serverFeedProblems = it.serverFeedProblems - podcastId) }
            }
            if (result is FeedUrlChangeResult.Saved || result is FeedUrlChangeResult.ServerCannotRead) {
                // The new address reaches the local subscription, its RSS cache and details.
                startPodcastCatalogRefresh(forceRss = true)
            }
            onResult(result)
        }
    }

    fun refreshPodcastCatalogIfStale() {
        if (podcastCatalogJob?.isActive == true) return
        viewModelScope.launch {
            val shouldRefresh = withContext(Dispatchers.IO) {
                offlinePodcastCache.isCatalogRefreshDue(clock.millis())
            }
            if (shouldRefresh) startPodcastCatalogRefresh(forceRss = false)
        }
    }

    private fun startPodcastCatalogRefresh(forceRss: Boolean) {
        if (podcastCatalogJob?.isActive == true) return
        val session = sessionStore.load() ?: return
        podcastCatalogJob = viewModelScope.launch {
            _state.update { it.copy(podcastsRefreshing = true, podcastsError = null) }
            // One refresh shared with the background worker (N-04); the catalog rows land in Room
            // first and the list updates from there while per-podcast details follow.
            val result = runCatching {
                libraryRefresher.refresh(session, forceRss) {
                    _state.update { it.copy(podcastsError = null) }
                }
            }
            runCatching { withContext(Dispatchers.IO) { podcastFeedClient.problems(session) } }
                .onSuccess { problems -> _state.update { it.copy(serverFeedProblems = problems.associateBy { p -> p.podcastId }) } }
            _state.update {
                it.copy(
                    podcastsRefreshing = false,
                    podcastsError = result.exceptionOrNull().toQueueLoadError(),
                )
            }
        }
    }

    fun refreshDashboard() {
        if (dashboardJob?.isActive == true) return
        val session = sessionStore.load() ?: return
        dashboardJob = viewModelScope.launch {
            _state.update { it.copy(dashboardRefreshing = true, dashboardError = null) }
            val previous = _state.value.dashboard
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    dashboardClient.fetch(
                        session = session,
                        cached = previous,
                        refreshedAtEpochMs = clock.millis(),
                    )
                }
            }
            val enrichedDashboard = result.getOrNull()?.withPodcastArtwork(_state.value.podcasts)
            enrichedDashboard?.let { dashboard ->
                withContext(Dispatchers.IO) {
                    syncStore.dashboardDao.upsert(
                        DashboardCacheEntity(
                            payloadJson = dashboard.toCacheJson(),
                            refreshedAtEpochMs = dashboard.refreshedAtEpochMs,
                        ),
                    )
                }
            }
            _state.update { current ->
                val savedIds = enrichedDashboard?.savedEpisodes
                    ?.mapTo(hashSetOf()) { it.episodeId }
                val reconciledOverrides = if (savedIds == null) {
                    current.savedEpisodeOverrides
                } else {
                    current.savedEpisodeOverrides.filterNot { (episodeId, desiredSaved) ->
                        (episodeId in savedIds) == desiredSaved
                    }
                }
                current.copy(
                    dashboard = enrichedDashboard ?: current.dashboard,
                    dashboardRefreshing = false,
                    dashboardError = result.exceptionOrNull().toQueueLoadError(),
                    savedEpisodeOverrides = reconciledOverrides,
                )
            }
            refreshStorageStats()
        }
    }

    fun loadPodcastDetailsById(podcastId: Int) {
        if (podcastId <= 0) return
        focusedPodcastIds.focus(podcastId)
        refreshPodcastDownloadStates(podcastId)
        if (podcastDetailsByIdJobs[podcastId]?.isActive == true) return
        val session = sessionStore.load()
        val subscription = _state.value.podcasts.firstOrNull { it.podcastId == podcastId }
            ?: return
        podcastDetailsByIdJobs[podcastId] = viewModelScope.launch {
            val (shouldRefreshPinePods, shouldRefreshRss) = withContext(Dispatchers.IO) {
                offlinePodcastCache.isPodcastRefreshDue(podcastId, clock.millis()) to
                    offlinePodcastCache.isRssRefreshDue(podcastId, clock.millis())
            }
            if ((!shouldRefreshPinePods || session == null) && !shouldRefreshRss) {
                podcastDetailsByIdJobs.remove(podcastId)
                return@launch
            }
            _state.update {
                it.copy(podcastDetailsByIdLoading = it.podcastDetailsByIdLoading + podcastId)
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    if (shouldRefreshPinePods && session != null) {
                        runCatching {
                            podcastDetailsClient.fetchByPodcastId(session, podcastId)
                        }.getOrNull()?.let { fetched ->
                            offlinePodcastCache.upsertPinePodsDetails(fetched, clock.millis())
                        }
                    }
                    if (shouldRefreshRss) {
                        offlinePodcastCache.refreshPublicRss(
                            subscription = subscription,
                            client = publicRssFeedClient,
                            force = false,
                            refreshedAtEpochMs = clock.millis(),
                        )
                    }
                }
            }
            _state.update { current ->
                current.copy(
                    podcastDetailsByIdLoading = current.podcastDetailsByIdLoading - podcastId,
                )
            }
            refreshPodcastDownloadStates(podcastId)
            podcastDetailsByIdJobs.remove(podcastId)
        }
    }

    fun unsubscribePodcast(podcastId: Int) {
        if (podcastId <= 0 || podcastId in _state.value.podcastActionsInProgress) return
        _state.update {
            it.copy(
                podcastActionsInProgress = it.podcastActionsInProgress + podcastId,
                podcastActionErrors = it.podcastActionErrors - podcastId,
            )
        }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val now = clock.millis()
                    syncStore.stagePodcastRemoval(
                        PodcastOutboxEntity(
                            operationId = UUID.randomUUID().toString(),
                            kind = PodcastMutationKind.REMOVE,
                            podcastId = podcastId,
                            sequence = nextSequence(),
                            createdAtEpochMs = now,
                        ),
                    )
                }
            }
            _state.update { current ->
                current.copy(
                    podcastDetailsById = current.podcastDetailsById - podcastId,
                    podcastActionsInProgress = current.podcastActionsInProgress - podcastId,
                    podcastActionErrors = result.exceptionOrNull().toQueueLoadError()?.let {
                        current.podcastActionErrors + (podcastId to it)
                    } ?: (current.podcastActionErrors - podcastId),
                )
            }
            if (result.isSuccess) {
                SyncWorkScheduler.enqueueImmediate(getApplication())
                synchronizeAndRefresh(showSpinner = false, refreshRemote = false)
            }
        }
    }

    fun markPodcastPlayed(podcastId: Int) {
        if (podcastId <= 0 || podcastId in _state.value.podcastActionsInProgress) return
        val session = sessionStore.load() ?: return
        _state.update {
            it.copy(
                podcastActionsInProgress = it.podcastActionsInProgress + podcastId,
                podcastActionErrors = it.podcastActionErrors - podcastId,
            )
        }
        viewModelScope.launch {
            val result = runCatching {
                val episodes = withContext(Dispatchers.IO) {
                    podcastDetailsClient.fetchAllEpisodes(session, podcastId)
                }
                val now = clock.millis()
                val queuedIds = _state.value.entries.mapTo(mutableSetOf()) { it.episodeId }
                val queuedEpisodes = episodes.filter { it.episodeId in queuedIds }
                val mutationCount = episodes.size + queuedEpisodes.size
                val sequences = if (mutationCount > 0) {
                    sequence.nextBatch(count = mutationCount, nowEpochMs = now).iterator()
                } else {
                    LongRange.EMPTY.iterator()
                }
                val progress = episodes.map { episode ->
                    val durationMs = episode.durationSeconds.toLong() * 1_000L
                    ProgressMutation(
                        episodeId = episode.episodeId,
                        positionMs = durationMs,
                        durationMs = durationMs,
                        cause = ProgressCause.COMPLETE,
                        sequence = sequences.nextLong(),
                        recordedAtEpochMs = now,
                    )
                }
                val removals = queuedEpisodes.asSequence()
                    .map { episode ->
                        QueueMutation.Remove(
                            episodeId = episode.episodeId,
                            operationId = UUID.randomUUID().toString(),
                            sequence = sequences.nextLong(),
                        )
                    }
                    .toList()
                withContext(Dispatchers.IO) {
                    syncStore.recordPodcastCompletion(podcastId, progress, removals, now)
                }
                episodes
            }
            _state.update { current ->
                val completedDetails = result.getOrNull()?.let { episodes ->
                    current.podcastDetailsById[podcastId]?.copy(
                        episodesPlayed = current.podcastDetailsById[podcastId]?.episodeCount,
                        recentEpisodes = episodes.map { episode ->
                            episode.copy(
                                listenedSeconds = episode.durationSeconds.toDouble(),
                                completed = true,
                            )
                        },
                    )
                }
                current.copy(
                    podcastDetailsById = if (completedDetails == null) {
                        current.podcastDetailsById
                    } else {
                        current.podcastDetailsById + (podcastId to completedDetails)
                    },
                    podcastActionsInProgress = current.podcastActionsInProgress - podcastId,
                    podcastActionErrors = result.exceptionOrNull().toQueueLoadError()?.let {
                        current.podcastActionErrors + (podcastId to it)
                    } ?: (current.podcastActionErrors - podcastId),
                )
            }
            if (result.isSuccess) {
                SyncWorkScheduler.enqueueImmediate(getApplication())
                synchronizeAndRefresh(showSpinner = false, refreshRemote = false)
            }
        }
    }

    fun requestAheadCacheClear() {
        playbackStorage.requestAheadCacheClear()
        refreshStorageStats()
    }

    private fun markDashboardReady() {
        if (dashboardCacheRead && dashboardContinueRead && localDownloadsRead && !_state.value.dashboardLocalReady) {
            _state.update { it.copy(dashboardLocalReady = true) }
        }
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            var swept = emptyList<Int>()
            val (snapshot, localDownloads) = withContext(Dispatchers.IO) {
                var downloads = downloadManager.completedEpisodes()
                var cached = offlinePodcastCache.episodesByPinePodsIds(downloads.map { it.episodeId }.filter { it > 0 })
                // Played automatic downloads leave the device a day later (user, 29.10.2026).
                val played = cached.filterValues { it.completed }.keys
                swept = downloadManager.sweepPlayedAutoDownloads(played)
                if (swept.isNotEmpty()) {
                    downloads = downloadManager.completedEpisodes()
                    cached = cached.filterKeys { id -> downloads.any { it.episodeId == id } }
                }
                playbackStorage.snapshot() to downloads.map { episode ->
                    cached[episode.episodeId]?.let { row ->
                        episode.copy(
                            publishedAt = row.publishedAt,
                            durationSeconds = row.durationSeconds.takeIf { it > 0 },
                            listenedSeconds = row.listenedSeconds,
                            completed = row.completed,
                        )
                    } ?: episode
                }
            }
            _state.update {
                it.copy(
                    aheadCacheBytes = snapshot.aheadCacheBytes,
                    offlineDownloadBytes = snapshot.offlineDownloadBytes,
                    aheadCacheClearPending = snapshot.aheadCacheClearPending,
                    localDownloads = localDownloads,
                )
            }
            localDownloadsRead = true
            markDashboardReady()
            if (swept.isNotEmpty()) refreshDownloadStates(_state.value.entries, swept)
        }
    }

    private fun refreshPodcastDownloadStates(podcastId: Int) {
        viewModelScope.launch {
            val episodeIds = withContext(Dispatchers.IO) {
                offlinePodcastCache.episodeIds(podcastId)
            }
            refreshDownloadStates(_state.value.entries, episodeIds)
        }
    }

    private fun refreshDownloadStates(
        entries: List<QueueListEntry>,
        additionalEpisodeIds: Collection<Int> = emptyList(),
    ) {
        downloadStatesRefreshJob?.cancel()
        downloadStatesRefreshJob = viewModelScope.launch {
            val ids = withContext(Dispatchers.Default) {
                buildSet {
                    addAll(entries.map(QueueListEntry::episodeId))
                    addAll(_state.value.downloadStates.keys)
                    addAll(additionalEpisodeIds)
                }
            }
            val progress = withContext(Dispatchers.IO) {
                ids.associateWith(downloadManager::progress)
            }
            _state.update {
                it.copy(
                    downloadStates = progress.mapValues { item -> item.value.state },
                    downloadProgress = progress.mapValues { item -> item.value.fraction },
                )
            }
        }
    }

    private fun enqueueQueueMutation(mutation: QueueMutation) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                syncStore.enqueueQueueMutation(mutation, clock.millis())
            }
            SyncWorkScheduler.enqueueImmediate(getApplication())
            synchronizeAndRefresh(showSpinner = false, refreshRemote = false)
        }
    }

    private fun enqueueEpisodeIfMissing(
        entry: QueueListEntry,
        position: QueueInsertionPosition,
    ) {
        // Public-RSS-only episodes are playable/downloadable immediately, but a queue mutation
        // must never masquerade as a PinePods operation before the real server ID is known.
        viewModelScope.launch {
            val now = clock.millis()
            val destination = when (position) {
                QueueInsertionPosition.FIRST -> 0
                QueueInsertionPosition.LAST -> null
            }
            val inserted = withContext(Dispatchers.IO) {
                if (entry.episodeId > 0) {
                    syncStore.cacheEpisodeAndEnqueueIfMissing(
                        episode = entry.toEpisodeCache(now),
                        mutation = QueueMutation.Add(
                            episodeId = entry.episodeId,
                            destinationIndex = destination,
                            operationId = UUID.randomUUID().toString(),
                            sequence = nextSequence(),
                        ),
                        createdAtEpochMs = now,
                    )
                } else {
                    val cacheKey = offlinePodcastCache.findRssOnlyEpisode(entry.episodeId)?.cacheKey
                        ?: return@withContext false
                    syncStore.stageDeferredQueue(
                        episodeCacheKey = cacheKey,
                        desiredQueued = true,
                        destinationIndex = destination,
                        sequence = nextSequence(),
                        createdAtEpochMs = now,
                    )
                }
            }
            if (!inserted) return@launch
            if (entry.episodeId > 0) {
                SyncWorkScheduler.enqueueImmediate(getApplication())
                synchronizeAndRefresh(showSpinner = false, refreshRemote = false)
            }
        }
    }

    private suspend fun synchronizeAndRefresh(
        showSpinner: Boolean,
        refreshRemote: Boolean = true,
    ) = syncMutex.withLock {
        sessionStore.load() ?: return@withLock
        if (!refreshRemote) {
            // A local change: the process scheduler delivers it (docs/SYNC.md §9, I7).
            getApplication<Application>().appGraph.syncScheduler.requestSync()
            return@withLock
        }
        if (showSpinner) _state.update { it.copy(isRefreshing = true, error = null) }
        val report = try {
            getApplication<Application>().appGraph.syncEngine.syncNow()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        _state.update { current ->
            current.copy(
                isRefreshing = false,
                error = when {
                    report == null -> QueueLoadError.NETWORK
                    report.stoppedBy == SyncFailure.AUTH -> QueueLoadError.AUTHENTICATION
                    else -> null
                },
                syncError = report?.let(::legacySyncCategory),
            )
        }
        refreshStorageStats()
    }

    /** Polling belongs to the process [ru.e1173.nookast.core.sync.SyncScheduler] (I7). */
    private fun startAutoSync() = Unit

    private fun nextSequence(): Long = sequence.next(clock.millis())

    fun clearLocalState(onFinished: () -> Unit) {
        refreshJob?.cancel()
        autoSyncJob?.cancel()
        podcastDetailsJobs.values.forEach { it.cancel() }
        podcastDetailsJobs.clear()
        podcastDetailsByIdJobs.values.forEach { it.cancel() }
        podcastDetailsByIdJobs.clear()
        downloadStatesRefreshJob?.cancel()
        podcastCatalogJob?.cancel()
        dashboardJob?.cancel()
        SyncWorkScheduler.cancelAll(getApplication())
        getApplication<Application>().appGraph.serverDownloads.clear()
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    database.clearAllTables()
                    getApplication<Application>().appGraph.syncRepository.onDatabaseCleared()
                    // The feed cache belongs to the account being cleared.
                    feedCacheFile(getApplication()).delete()
                    downloadManager.clearAll()
                }
            } finally {
                onFinished()
            }
        }
    }

    override fun onCleared() {
        podcastDetailsJobs.values.forEach { it.cancel() }
        podcastDetailsByIdJobs.values.forEach { it.cancel() }
        dashboardJob?.cancel()
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
    }
}

private data class PodcastDetailsSnapshot(
    val byPodcastId: Map<Int, PodcastDetails>,
    val byEpisodeId: Map<Int, PodcastDetails>,
)

/** Most recently focused first; old entries drop out so the observed set stays small. */
private fun MutableStateFlow<List<Int>>.focus(id: Int) {
    update { current -> (listOf(id) + (current - id)).take(MAX_FOCUSED_IDS) }
}

private const val MAX_FOCUSED_IDS = 6
private const val PODCAST_DETAILS_DEBOUNCE_MS = 300L

private fun buildPodcastDetailsSnapshot(
    cachedPodcasts: List<ru.e1173.nookast.data.local.CachedPodcastDetails>,
    previous: Map<Int, PodcastDetails>,
): PodcastDetailsSnapshot {
    val byPodcastId = cachedPodcasts.mapNotNull { cached ->
        cached.toPodcastDetails()?.let { details ->
            val kept = previous[details.podcastId]?.takeIf { it == details } ?: details
            details.podcastId to kept
        }
    }.toMap()
    val byEpisodeId = buildMap {
        byPodcastId.values.forEach { details ->
            details.recentEpisodes.forEach { episode -> put(episode.episodeId, details) }
        }
    }
    return PodcastDetailsSnapshot(byPodcastId = byPodcastId, byEpisodeId = byEpisodeId)
}

private fun QueueListEntry.toEpisodeCache(refreshedAtEpochMs: Long): QueueEpisodeEntity =
    QueueEpisodeEntity(
        episodeId = episodeId,
        episodeTitle = episodeTitle.orEmpty(),
        podcastName = podcastName.orEmpty(),
        artworkUrl = artworkUrl.orEmpty(),
        mediaUrl = mediaUrl.orEmpty(),
        durationSeconds = durationSeconds?.coerceAtLeast(0) ?: 0,
        listenDurationSeconds = listenDurationSeconds?.coerceAtLeast(0.0),
        completed = completed == true,
        description = description.orEmpty(),
        publishedAt = publishedAt.orEmpty(),
        downloaded = downloaded == true,
        refreshedAtEpochMs = refreshedAtEpochMs,
    )

internal const val ACTIVE_QUEUE_SYNC_INTERVAL_MS = 13_000L
private const val PODCAST_METADATA_CONCURRENCY = 4
private const val ARTWORK_PREFETCH_CONCURRENCY = 3
private const val ARTWORK_PREFETCH_DEBOUNCE_MS = 2_000L
private const val PROGRESS_TIMES_DEBOUNCE_MS = 1_000L

internal fun preferredPodcastArtwork(
    latestEpisodeArtwork: String?,
    catalogArtwork: String?,
    cachedArtwork: String?,
    isUsable: (String) -> Boolean = { true },
    resolveRssArtwork: () -> String?,
): String {
    val pinePodsCandidates = sequenceOf(
        latestEpisodeArtwork,
        catalogArtwork,
        cachedArtwork,
    ).mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.distinct().toList()
    pinePodsCandidates.firstOrNull(isUsable)?.let { return it }

    val rssArtwork = resolveRssArtwork()?.trim().orEmpty()
    if (rssArtwork.isNotEmpty() && isUsable(rssArtwork)) return rssArtwork

    // Keep the most promising URL even when the current network cannot validate it;
    // the disk-backed loader can retry it on a later launch instead of erasing metadata.
    return rssArtwork.ifEmpty { pinePodsCandidates.firstOrNull().orEmpty() }
}

internal fun DashboardSnapshot.withPodcastArtwork(
    podcasts: List<PodcastSubscriptionEntity>,
): DashboardSnapshot {
    if (podcasts.isEmpty()) return this
    val byId = podcasts.associateBy(PodcastSubscriptionEntity::podcastId)
    val byName = podcasts.associateBy { it.name.trim().lowercase() }
    fun artwork(podcastId: Int?, podcastName: String, current: String): String {
        val catalog = podcastId?.let(byId::get)
            ?: byName[podcastName.trim().lowercase()]
        return catalog?.artworkUrl?.takeIf(String::isNotBlank) ?: current
    }
    return copy(
        analytics = analytics.copy(
            topPodcasts = analytics.topPodcasts.map { podcast ->
                podcast.copy(
                    artworkUrl = artwork(podcast.podcastId, podcast.name, podcast.artworkUrl),
                )
            },
        ),
        savedEpisodes = savedEpisodes.map { episode ->
            episode.copy(
                artworkUrl = artwork(
                    episode.podcastId,
                    episode.podcastName,
                    episode.artworkUrl,
                ),
            )
        },
    )
}

private data class QueueSnapshot(
    val entries: List<QueueListEntry>,
    val lastUpdatedEpochMs: Long?,
    val pendingProgressCount: Int,
    val pendingQueueCount: Int,
)

internal fun Throwable?.toQueueLoadError(): QueueLoadError? = when (this) {
    null -> null
    is QueueLoadException -> reason
    else -> QueueLoadError.INVALID_RESPONSE
}

/** The settings screen still shows the v7 categories; the engine's taxonomy maps onto them. */
private fun legacySyncCategory(report: ru.e1173.nookast.core.sync.SyncReport): SyncErrorCategory? {
    val failure = report.stoppedBy ?: report.failures.values.firstOrNull() ?: return null
    return when (failure) {
        SyncFailure.AUTH, SyncFailure.AUTH_SUSPECT -> SyncErrorCategory.AUTHENTICATION
        SyncFailure.TRANSIENT, SyncFailure.NETWORK_LOGIN -> SyncErrorCategory.NETWORK
        SyncFailure.PERMANENT, SyncFailure.PROTOCOL -> SyncErrorCategory.SERVER
    }
}

/** How a change of a podcast's RSS address ended. */
sealed interface FeedUrlChangeResult {
    data class Saved(val title: String, val episodeCount: Int) : FeedUrlChangeResult

    /** Saved, but the server cannot read the new feed (it may not reach that host). */
    data class ServerCannotRead(val kind: ServerFeedError) : FeedUrlChangeResult

    data object InvalidUrl : FeedUrlChangeResult

    /** The phone could not read the new address as a feed; nothing was changed. */
    data class DeviceCannotRead(val offline: Boolean) : FeedUrlChangeResult

    data object Offline : FeedUrlChangeResult

    /** The server refused, with its message (e.g. the feed is already another subscription). */
    data class Refused(val message: String) : FeedUrlChangeResult
}
