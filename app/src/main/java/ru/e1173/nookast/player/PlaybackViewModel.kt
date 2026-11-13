package ru.e1173.nookast.player

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.net.toUri
import android.os.Bundle
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.io.File
import java.util.UUID
import ru.e1173.nookast.data.local.QueueListEntry
import ru.e1173.nookast.data.local.CurrentPlaybackContextEntity
import ru.e1173.nookast.data.local.SyncDatabase
import ru.e1173.nookast.data.local.SyncStore
import ru.e1173.nookast.data.local.pinePodsEpisodeCacheKey
import ru.e1173.nookast.data.local.rssLocalPlaybackId
import ru.e1173.nookast.data.remote.ArtworkUrlResolver
import ru.e1173.nookast.data.remote.MediaSourceCandidates
import ru.e1173.nookast.data.remote.MediaSourceResolver
import ru.e1173.nookast.offline.EpisodeDownloadManager
import ru.e1173.nookast.offline.ServerDownloadProgress
import ru.e1173.nookast.auth.AuthSession
import ru.e1173.nookast.app.appGraph
import ru.e1173.nookast.core.session.SessionRepository
import ru.e1173.nookast.sync.LocalSequenceGenerator
import ru.e1173.nookast.sync.ProgressCause
import ru.e1173.nookast.sync.ProgressMutation
import ru.e1173.nookast.sync.QueueMutation
import ru.e1173.nookast.sync.SyncWorkScheduler

data class PlaybackUiState(
    val isConnected: Boolean = false,
    val currentEpisodeId: Int? = null,
    val title: String = "",
    val podcastName: String = "",
    val artworkUrl: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedAheadMs: Long = 0,
    val streamReachability: StreamReachability = StreamReachability.IDLE,
    val streamHost: String = "",
    val isUsingPinePodsFallback: Boolean = false,
    /** Playing the copy stored on the PinePods server (DEC-023). */
    val isServerCopySource: Boolean = false,
    /** The current episode is stored on the PinePods server. */
    val isOnServer: Boolean = false,
    /** Progress of a download to the server requested from the player, if any. */
    val serverDownload: ServerDownloadProgress? = null,
    /** The origin has not started playing 15 s after the first attempt (DEC-023 offer). */
    val originStalled: Boolean = false,
    val isLocalSource: Boolean = false,
    val isMarkedPlayed: Boolean = false,
    val mediaSizeBytes: Long? = null,
    val bitrateKbps: Int? = null,
    val nativeChapters: List<PlaybackChapter> = emptyList(),
    /** Podcasting 2.0 chapters of the feed through PinePods; preferred over the stream's own. */
    val feedChapters: List<PlaybackChapter> = emptyList(),
    val seekBackSeconds: Int = PlaybackPreferences.DEFAULT_SEEK_BACK_SECONDS,
    val seekForwardSeconds: Int = PlaybackPreferences.DEFAULT_SEEK_FORWARD_SECONDS,
    val autoMarkPlayedNearEndEnabled: Boolean = false,
    val autoMarkPlayedRemainingMinutes: Int =
        PlaybackPreferences.DEFAULT_AUTO_MARK_PLAYED_REMAINING_MINUTES,
    val diagnosticsRunning: Boolean = false,
    val diagnosticResults: List<EndpointProbeResult> = emptyList(),
    val error: PlaybackError? = null,
)

data class PlaybackChapter(
    val startTimeMs: Long,
    val endTimeMs: Long?,
    val title: String,
    /** The chapter's own picture: a link (Podcasting 2.0 JSON) or embedded bytes (ID3 APIC). */
    val imageUrl: String? = null,
    val image: ByteArray? = null,
)

/** The playing position as the ticker sees it; see [PlaybackViewModel.position]. */
data class PlaybackPosition(
    val episodeId: Int? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedAheadMs: Long = 0,
)

enum class PlaybackError {
    CONTROLLER,
    INVALID_MEDIA,
    PLAYBACK,

    /** The source answered that the file is not there (4.6). */
    SOURCE_MISSING,

    /** The source refused the request. */
    SOURCE_REFUSED,

    /** The file is in a format the device cannot play. */
    UNSUPPORTED,
}

class PlaybackViewModel(application: Application) : AndroidViewModel(application), Player.Listener {
    private val database = SyncDatabase.open(application)
    private val syncStore = SyncStore(database)
    private val sessionStore = application.appGraph.session
    private val downloadManager = EpisodeDownloadManager(application)
    private val serverDownloads = application.appGraph.serverDownloads
    private val itemFactory = PlaybackItemFactory(database, downloadManager, application.appGraph.serverDownloads::isOnServer)
    private val playbackPreferences = PlaybackPreferences(application)
    private val reachabilityClient = StreamReachabilityClient()
    private val mediaTechnicalInfoClient = MediaTechnicalInfoClient()
    private val sequence = LocalSequenceGenerator(application)
    private val clock: Clock = Clock.systemUTC()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var connectJob: Job? = null
    private var positionJob: Job? = null
    private var queueSyncJob: Job? = null
    private var probeJob: Job? = null
    private var diagnosticsJob: Job? = null
    private var mediaTechnicalInfoJob: Job? = null
    private var activeSourceUrl: String? = null
    private var activeTechnicalInfoSource: String? = null
    private var lastProbeStartedElapsedMs: Long = 0

    // When the current remote source started waiting without playing; 0 while it plays.
    private var originWaitStartedElapsedMs: Long = 0
    private var bufferingSinceElapsedMs: Long = 0
    private var bufferingRevealJob: Job? = null

    private val _state = MutableStateFlow(
        PlaybackUiState(
            seekBackSeconds = playbackPreferences.seekBackSeconds,
            seekForwardSeconds = playbackPreferences.seekForwardSeconds,
            autoMarkPlayedNearEndEnabled = playbackPreferences.autoMarkPlayedNearEndEnabled,
            autoMarkPlayedRemainingMinutes =
                playbackPreferences.autoMarkPlayedRemainingMinutes,
        ),
    )

    // Settings brought in by a transfer file (plan 8.2) apply at once, not after a restart.
    private val preferencesListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _state.update {
            it.copy(
                seekBackSeconds = playbackPreferences.seekBackSeconds,
                seekForwardSeconds = playbackPreferences.seekForwardSeconds,
                autoMarkPlayedNearEndEnabled = playbackPreferences.autoMarkPlayedNearEndEnabled,
                autoMarkPlayedRemainingMinutes = playbackPreferences.autoMarkPlayedRemainingMinutes,
            )
        }
    }.also(playbackPreferences::registerListener)
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    /**
     * The live position, twice a second while playing (plan 5.2, U-05). Only the player, the mini
     * player and the timeline read it, so the ticker no longer recomposes the whole app; [state]
     * carries the position as of the last event (open, seek, play, pause).
     */
    private val _position = MutableStateFlow(PlaybackPosition())
    val position: StateFlow<PlaybackPosition> = _position.asStateFlow()

    init {
        // Embedded chapters come from the service's own player (see NativeChapters).
        viewModelScope.launch {
            NativeChapters.state.collect { snapshot ->
                _state.update {
                    if (snapshot.mediaId != null && snapshot.mediaId == it.currentEpisodeId?.toString()) {
                        it.copy(nativeChapters = snapshot.chapters)
                    } else {
                        it
                    }
                }
            }
        }
    }

    fun connect() {
        if (controller != null || controllerFuture != null || connectJob?.isActive == true) return
        connectJob = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                PlaybackMediaCache.prepare(getApplication<Application>())
            }
            val future = MediaController.Builder(
                getApplication<Application>(),
                SessionToken(
                    getApplication<Application>(),
                    ComponentName(getApplication<Application>(), PlaybackService::class.java),
                ),
            ).buildAsync()
            controllerFuture = future
            future.addListener(
                {
                    if (controllerFuture !== future) return@addListener
                    runCatching { future.get() }
                        .onSuccess { mediaController ->
                            controller = mediaController
                            mediaController.addListener(this@PlaybackViewModel)
                            _state.update { it.copy(isConnected = true) }
                            publishPlayerState(mediaController)
                            startPositionUpdates()
                        }
                        .onFailure {
                            MediaController.releaseFuture(future)
                            controllerFuture = null
                            _state.update { it.copy(error = PlaybackError.CONTROLLER) }
                        }
                },
                ContextCompat.getMainExecutor(getApplication<Application>()),
            )
        }
    }

    /** Plays [entries] from [selectedIndex]; [startPositionMs] overrides the saved position (a chapter picked on the card). */
    fun playQueue(entries: List<QueueListEntry>, selectedIndex: Int, startPositionMs: Long? = null) {
        openQueue(entries, selectedIndex, autoPlay = true, startPositionMs = startPositionMs)
    }

    /**
     * Opens an episode in Now Playing without starting it. This deliberately prepares the media
     * item so artwork, duration and the saved resume position are available immediately, while
     * keeping playWhenReady false.
     */
    fun prepareQueue(entries: List<QueueListEntry>, selectedIndex: Int) {
        openQueue(entries, selectedIndex, autoPlay = false)
    }

    // The episode just opened, on screen at once while the controller catches up (user,
    // 29.10.2026: Now Playing showed the previous episode and spinners for seconds).
    private var pendingOpen: QueueListEntry? = null
    private var pendingOpenTimeout: Job? = null

    private fun showPendingOpen(entry: QueueListEntry) {
        pendingOpen = entry
        pendingOpenTimeout?.cancel()
        // A failed open must not freeze the screen on the pending episode.
        pendingOpenTimeout = viewModelScope.launch {
            delay(PENDING_OPEN_TIMEOUT_MS)
            if (pendingOpen === entry) clearPendingOpen()
        }
        _state.update {
            it.copy(
                currentEpisodeId = entry.episodeId,
                title = entry.episodeTitle.orEmpty(),
                podcastName = entry.podcastName.orEmpty(),
                artworkUrl = entry.artworkUrl.orEmpty(),
                isPlaying = false,
                isBuffering = false,
                positionMs = if (entry.completed == true) 0 else ((entry.listenDurationSeconds ?: 0.0) * 1_000).toLong(),
                durationMs = (entry.durationSeconds ?: 0) * 1_000L,
                bufferedAheadMs = 0,
                isMarkedPlayed = false,
                mediaSizeBytes = null,
                bitrateKbps = null,
                nativeChapters = emptyList(),
                feedChapters = emptyList(),
                diagnosticResults = emptyList(),
                error = null,
            )
        }
    }

    private fun clearPendingOpen() {
        if (pendingOpen == null) return
        pendingOpen = null
        controller?.let(::publishPlayerState)
    }
    private fun openQueue(
        entries: List<QueueListEntry>,
        selectedIndex: Int,
        autoPlay: Boolean,
        startPositionMs: Long? = null,
    ) {
        val selected = entries.getOrNull(selectedIndex) ?: return
        showPendingOpen(selected)
        viewModelScope.launch {
            val session = withContext(Dispatchers.IO) { sessionStore.load() }
            // "Download started episodes" is handled by the player service on the item transition;
            // starting it here as well raced it into a second download (P-04).
            // DEC-023: whether the server holds a copy decides the source; bounded to 3 s.
            if (session != null && downloadManager.localUriIfReady(selected.episodeId) == null) {
                serverDownloads.refreshForPlayback()
            }
            val playableEntries = withContext(Dispatchers.IO) {
                // docs/SYNC.md §6.8: an episode started outside Up Next plays alone, followed by
                // the queue (synchronizeQueue appends it). Loading a whole podcast (hundreds of
                // items) and trimming it item by item crashed the system Bluetooth service.
                val queued = database.queueDao().getQueue().map { it.episodeId }.toSet()
                val source = if (selected.episodeId in queued) entries else listOf(selected)
                source.filter { entry -> itemFactory.isPlayable(entry, session) }
            }
            val playableIndex = playableEntries.indexOfFirst { it.episodeId == selected.episodeId }
            if (playableIndex < 0) {
                clearPendingOpen()
                _state.update { it.copy(error = PlaybackError.INVALID_MEDIA) }
                return@launch
            }
            val items = withContext(Dispatchers.IO) {
                playableEntries.map { entry ->
                    itemFactory.mediaItem(entry, itemFactory.resumePositionMs(entry), session)
                }
            }
            val mediaController = controller
            if (mediaController == null) {
                pendingOpen = null
                _state.update { it.copy(error = PlaybackError.CONTROLLER) }
                return@launch
            }
            if (autoPlay) autoCompletePreviousEpisodeIfEligible(selected.episodeId)
            val startPosition = startPositionMs ?: items[playableIndex].resumePositionMs()
            mediaController.pause()
            mediaController.setMediaItems(items, playableIndex, startPosition)
            mediaController.prepare()
            if (autoPlay) mediaController.play()
            publishPlayerState(mediaController)
        }
    }

    /**
     * Keeps Media3's actual Up Next list aligned with the local-first Room queue. The currently
     * playing item is never interrupted when another client removes it from PinePods: it remains
     * as the active item while the following items adopt the newest effective queue order.
     */
    fun synchronizeQueue(entries: List<QueueListEntry>) {
        val mediaController = controller ?: return
        val currentItem = mediaController.currentMediaItem ?: return
        val currentEpisodeId = currentItem.mediaId.toIntOrNull()?.takeIf { it > 0 } ?: return

        queueSyncJob?.cancel()
        queueSyncJob = viewModelScope.launch {
            val session = withContext(Dispatchers.IO) { sessionStore.load() }
            val desiredItems = withContext(Dispatchers.IO) {
                val queueItems = entries.mapNotNull { entry ->
                    if (entry.episodeId == currentEpisodeId) {
                        currentItem
                    } else {
                        val playable = itemFactory.isPlayable(entry, session)
                        if (!playable) return@mapNotNull null
                        itemFactory.mediaItem(entry, itemFactory.resumePositionMs(entry), session)
                    }
                }
                val byEpisodeId = queueItems.associateBy { it.mediaId.toIntOrNull() }
                desiredPlaybackOrder(
                    currentEpisodeId = currentEpisodeId,
                    queueEpisodeIds = entries.map(QueueListEntry::episodeId),
                ).mapNotNull { episodeId ->
                    if (episodeId == currentEpisodeId) currentItem else byEpisodeId[episodeId]
                }
            }

            val activeController = controller ?: return@launch
            if (activeController.currentMediaItem?.mediaId != currentItem.mediaId) return@launch
            val currentIds = (0 until activeController.mediaItemCount).mapNotNull { index ->
                activeController.getMediaItemAt(index).mediaId.toIntOrNull()
            }
            val desiredIds = desiredItems.mapNotNull { it.mediaId.toIntOrNull() }
            if (currentIds == desiredIds) return@launch

            val itemsByEpisodeId = desiredItems.associateBy { it.mediaId.toIntOrNull() }
            val edits = planPlaylistEdits(currentIds, desiredIds) ?: return@launch
            if (edits.size <= MAX_INCREMENTAL_PLAYLIST_EDITS) {
                edits.forEach { edit ->
                    when (edit) {
                        is PlaylistEdit.Remove -> activeController.removeMediaItem(edit.index)
                        is PlaylistEdit.Move -> activeController.moveMediaItem(edit.fromIndex, edit.toIndex)
                        is PlaylistEdit.Insert -> {
                            val item = itemsByEpisodeId.getValue(edit.episodeId)
                            activeController.addMediaItem(edit.index, item)
                        }
                    }
                }
            } else {
                // Many edits: two batch replacements around the playing item (§6.8). Every queue
                // change makes Bluetooth and the head-unit shell re-read the whole queue.
                val currentIndex = activeController.currentMediaItemIndex
                val desiredCurrent = desiredIds.indexOf(currentEpisodeId).takeIf { it >= 0 } ?: return@launch
                activeController.replaceMediaItems(
                    currentIndex + 1,
                    activeController.mediaItemCount,
                    desiredItems.subList(desiredCurrent + 1, desiredItems.size),
                )
                activeController.replaceMediaItems(0, currentIndex, desiredItems.subList(0, desiredCurrent))
            }
        }
    }

    fun togglePlayPause() {
        controller?.let { player ->
            if (player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)) player.pause() else player.play()
            publishPlayerState(player)
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0))
        controller?.let(::publishPlayerState)
    }

    fun seekBy(deltaMs: Long) {
        val player = controller ?: return
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
        val target = (player.currentPosition + deltaMs).coerceAtLeast(0)
        player.seekTo(if (duration != null) target.coerceAtMost(duration) else target)
        publishPlayerState(player)
    }

    fun setSeekBackSeconds(seconds: Int) {
        playbackPreferences.seekBackSeconds = seconds
        _state.update { it.copy(seekBackSeconds = playbackPreferences.seekBackSeconds) }
    }

    fun setSeekForwardSeconds(seconds: Int) {
        playbackPreferences.seekForwardSeconds = seconds
        _state.update { it.copy(seekForwardSeconds = playbackPreferences.seekForwardSeconds) }
    }

    fun setAutoMarkPlayedNearEndEnabled(enabled: Boolean) {
        playbackPreferences.autoMarkPlayedNearEndEnabled = enabled
        _state.update { it.copy(autoMarkPlayedNearEndEnabled = enabled) }
    }

    fun setAutoMarkPlayedRemainingMinutes(minutes: Int) {
        playbackPreferences.autoMarkPlayedRemainingMinutes = minutes
        _state.update {
            it.copy(
                autoMarkPlayedRemainingMinutes =
                    playbackPreferences.autoMarkPlayedRemainingMinutes,
            )
        }
    }

    fun downloadCurrentEpisode() {
        val item = controller?.currentMediaItem ?: return
        viewModelScope.launch(Dispatchers.IO) { downloadManager.enqueue(item) }
    }

    /** Sign-out barrier (SYNC.md §10.2): the pause records the final position before the flush. */
    fun pauseForSignOut() {
        controller?.pause()
    }

    fun stopForSignOut() {
        probeJob?.cancel()
        diagnosticsJob?.cancel()
        mediaTechnicalInfoJob?.cancel()
        activeSourceUrl = null
        activeTechnicalInfoSource = null
        controller?.let { player ->
            player.pause()
            player.stop()
            player.clearMediaItems()
        }
        viewModelScope.launch(Dispatchers.IO) {
            database.offlineCacheDao().clearCurrentPlaybackContext()
        }
        _state.value = PlaybackUiState(
            isConnected = controller != null,
            seekBackSeconds = playbackPreferences.seekBackSeconds,
            seekForwardSeconds = playbackPreferences.seekForwardSeconds,
            autoMarkPlayedNearEndEnabled = playbackPreferences.autoMarkPlayedNearEndEnabled,
            autoMarkPlayedRemainingMinutes =
                playbackPreferences.autoMarkPlayedRemainingMinutes,
        )
    }

    /**
     * Marks the current episode played. Now Playing then shows the next queued episode, playing on
     * if it was playing (user, 29.10.2026); with nothing queued the player empties and closes.
     */
    fun markCurrentPlayed(next: List<QueueListEntry> = emptyList()) {
        val current = _state.value
        val episodeId = current.currentEpisodeId ?: return
        if (current.isMarkedPlayed) return
        val wasPlaying = current.isPlaying
        controller?.pause()
        _state.update { it.copy(isPlaying = false, isMarkedPlayed = true) }
        viewModelScope.launch {
            val now = clock.millis()
            val duration = current.durationMs.takeIf { it > 0 }
            withContext(Dispatchers.IO) {
                if (episodeId > 0) {
                    syncStore.recordCompletionAndRemoveFromQueue(
                        progress = ProgressMutation(
                            episodeId = episodeId,
                            positionMs = duration ?: current.positionMs,
                            durationMs = duration,
                            cause = ProgressCause.COMPLETE,
                            sequence = sequence.next(now),
                            recordedAtEpochMs = now,
                        ),
                        removal = QueueMutation.Remove(
                            episodeId = episodeId,
                            operationId = UUID.randomUUID().toString(),
                            sequence = sequence.next(now),
                        ),
                        createdAtEpochMs = now,
                    )
                } else {
                    val context = database.offlineCacheDao().getCurrentPlaybackContext()
                    if (context != null && context.pinePodsEpisodeId == null) {
                        syncStore.stageDeferredCompletionAndQueueRemoval(
                            episodeCacheKey = context.episodeCacheKey,
                            positionMs = duration ?: current.positionMs,
                            durationMs = duration,
                            progressSequence = sequence.next(now),
                            queueSequence = sequence.next(now),
                            createdAtEpochMs = now,
                        )
                    }
                }
            }
            val following = next.filter { it.episodeId != episodeId }
            if (following.isNotEmpty()) {
                openQueue(following, 0, autoPlay = wasPlaying)
            } else {
                controller?.clearMediaItems()
                controller?.let(::publishPlayerState)
            }
            if (episodeId > 0) SyncWorkScheduler.enqueueImmediate(getApplication())
        }
    }

    override fun onEvents(player: Player, events: Player.Events) {
        publishPlayerState(player)
        if (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING) {
            scheduleStreamProbe(force = true)
        }
    }

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        val shown = when (playbackErrorKind(error.errorCode)) {
            PlaybackErrorKind.MISSING -> PlaybackError.SOURCE_MISSING
            PlaybackErrorKind.REFUSED -> PlaybackError.SOURCE_REFUSED
            PlaybackErrorKind.UNSUPPORTED -> PlaybackError.UNSUPPORTED
            PlaybackErrorKind.TRANSIENT -> PlaybackError.PLAYBACK
        }
        _state.update { it.copy(error = shown, isBuffering = false) }
        scheduleStreamProbe(force = true)
    }

    fun runExtendedDiagnostics() {
        if (diagnosticsJob?.isActive == true) return
        // The current source (its server's health check for PinePods media), the PinePods server and
        // a neutral connectivity check (X-10: no personal or third-party hosts from every install).
        val targets = buildList {
            activeSourceUrl?.let { add(pinePodsHealthUrl(it) ?: it) }
            sessionStore.load()?.serverUrl?.let { server ->
                ru.e1173.nookast.auth.ServerAddress.normalize(server).getOrNull()
                    ?.resolve(PINEPODS_HEALTH_PATH)?.toString()?.let(::add)
            }
            add(StreamReachabilityClient.CONNECTIVITY_CHECK_URL)
            // Yandex next to Google (user, 11.11.2026): where one of them is blocked, the other tells
            // whether the internet itself works.
            add(StreamReachabilityClient.YANDEX_CHECK_URL)
        }.distinct()
        _state.update { it.copy(diagnosticsRunning = true, diagnosticResults = emptyList()) }
        diagnosticsJob = viewModelScope.launch {
            val results = withContext(Dispatchers.IO) {
                coroutineScope {
                    targets.map { url ->
                        async { reachabilityClient.probe(url, probeHeaders(url)) }
                    }.awaitAll()
                }
            }
            _state.update { it.copy(diagnosticsRunning = false, diagnosticResults = results) }
        }
    }

    private suspend fun autoCompletePreviousEpisodeIfEligible(nextEpisodeId: Int) {
        if (!playbackPreferences.autoMarkPlayedNearEndEnabled) return
        val mediaController = controller ?: return
        val currentItem = mediaController.currentMediaItem ?: return
        val currentEpisodeId = currentItem.mediaId.toIntOrNull() ?: return
        if (currentEpisodeId <= 0) return
        val extras = currentItem.mediaMetadata.extras
        val controllerDurationMs = mediaController.duration
            .takeIf { it != C.TIME_UNSET && it > 0 }
            ?: extras?.getLong(EXTRA_DURATION_MS, 0)?.takeIf { it > 0 }
        val controllerPositionMs = mediaController.currentPosition.coerceAtLeast(0)
        val currentTitle = currentItem.mediaMetadata.title?.toString().orEmpty()
        val currentPodcastName = currentItem.mediaMetadata.artist?.toString().orEmpty()
        val currentArtworkUrl = currentItem.artworkSourceUrl()
        val currentMediaUrl = extras?.getString(EXTRA_PRIMARY_MEDIA_URL)
            ?: extras?.getString(EXTRA_FALLBACK_MEDIA_URL)
            ?: currentItem.localConfiguration?.uri?.toString().orEmpty()
        val currentDescription = extras?.getString(EXTRA_DESCRIPTION).orEmpty()
        val currentPublishedAt = extras?.getString(EXTRA_PUBLISHED_AT).orEmpty()
        val now = clock.millis()
        val context = withContext(Dispatchers.IO) {
            val persisted = database.offlineCacheDao().getCurrentPlaybackContext()
                ?.takeIf { it.pinePodsEpisodeId == currentEpisodeId }
            val cached = if (persisted == null && currentEpisodeId > 0) {
                database.offlineCacheDao().findEpisodeByPinePodsId(currentEpisodeId)
            } else {
                null
            }
            val base = persisted ?: cached?.let { episode ->
                CurrentPlaybackContextEntity(
                    episodeCacheKey = episode.cacheKey,
                    pinePodsEpisodeId = episode.pinePodsEpisodeId,
                    podcastId = episode.podcastId,
                    episodeTitle = episode.title,
                    podcastName = episode.podcastName,
                    artworkUrl = episode.artworkUrl,
                    mediaUrl = episode.mediaUrl,
                    description = episode.description,
                    publishedAt = episode.publishedAt,
                    positionMs = controllerPositionMs,
                    durationMs = controllerDurationMs,
                    completed = episode.completed,
                    updatedAtEpochMs = now,
                )
            } ?: CurrentPlaybackContextEntity(
                episodeCacheKey = pinePodsEpisodeCacheKey(currentEpisodeId),
                pinePodsEpisodeId = currentEpisodeId.takeIf { it > 0 },
                podcastId = null,
                episodeTitle = currentTitle,
                podcastName = currentPodcastName,
                artworkUrl = currentArtworkUrl,
                mediaUrl = currentMediaUrl,
                description = currentDescription,
                publishedAt = currentPublishedAt,
                positionMs = controllerPositionMs,
                durationMs = controllerDurationMs,
                completed = false,
                updatedAtEpochMs = now,
            )
            base.copy(
                positionMs = controllerPositionMs,
                durationMs = controllerDurationMs ?: persisted?.durationMs,
                updatedAtEpochMs = now,
            )
        }
        val episodeId = context.pinePodsEpisodeId
        if (!shouldAutoCompletePreviousEpisode(
                enabled = true,
                previousEpisodeId = episodeId ?: rssLocalPlaybackId(context.episodeCacheKey),
                nextEpisodeId = nextEpisodeId,
                positionMs = context.positionMs,
                durationMs = context.durationMs,
                alreadyCompleted = context.completed,
                remainingMinutesThreshold = playbackPreferences.autoMarkPlayedRemainingMinutes,
            )
        ) {
            return
        }
        val completedContext = context.copy(
            positionMs = context.durationMs ?: context.positionMs,
            completed = true,
            updatedAtEpochMs = now,
        )
        withContext(Dispatchers.IO) {
            if (episodeId != null) {
                syncStore.recordCompletionAndRemoveFromQueue(
                    progress = ProgressMutation(
                        episodeId = episodeId,
                        positionMs = completedContext.positionMs,
                        durationMs = completedContext.durationMs,
                        cause = ProgressCause.COMPLETE,
                        sequence = sequence.next(now),
                        recordedAtEpochMs = now,
                    ),
                    removal = QueueMutation.Remove(
                        episodeId = episodeId,
                        operationId = UUID.randomUUID().toString(),
                        sequence = sequence.next(now),
                    ),
                    createdAtEpochMs = now,
                    completedContext = completedContext,
                )
            } else {
                syncStore.stageDeferredCompletionAndQueueRemoval(
                    episodeCacheKey = context.episodeCacheKey,
                    positionMs = completedContext.positionMs,
                    durationMs = completedContext.durationMs,
                    progressSequence = sequence.next(now),
                    queueSequence = sequence.next(now),
                    createdAtEpochMs = now,
                )
                database.offlineCacheDao().upsertCurrentPlaybackContext(completedContext)
            }
        }
        if (episodeId != null) SyncWorkScheduler.enqueueImmediate(getApplication())
    }

    private fun MediaItem.resumePositionMs(): Long =
        mediaMetadata.extras?.getLong(EXTRA_RESUME_POSITION_MS, 0) ?: 0

    private fun publishPlayerState(player: Player, fromTicker: Boolean = false) {
        pendingOpen?.let { pending ->
            // Until the controller holds the opened episode, its callbacks describe the old one.
            if (player.currentMediaItem?.mediaId != pending.episodeId.toString()) return
            pendingOpen = null
        }
        val item = player.currentMediaItem
        val sourceUri = item?.localConfiguration?.uri
        val technicalInfoSource = sourceUri?.toString()
        val technicalInfoSourceChanged = technicalInfoSource != activeTechnicalInfoSource
        if (technicalInfoSourceChanged) {
            activeTechnicalInfoSource = technicalInfoSource
            mediaTechnicalInfoJob?.cancel()
        }
        val remoteSourceUrl = sourceUri?.toString()?.takeIf {
            sourceUri.scheme == "https" || sourceUri.scheme == "http"
        }
        val sourceChanged = remoteSourceUrl != activeSourceUrl
        if (sourceChanged) {
            activeSourceUrl = remoteSourceUrl
            originWaitStartedElapsedMs = 0
            probeJob?.cancel()
            lastProbeStartedElapsedMs = 0
        }
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
            ?: item?.mediaMetadata?.extras?.getLong(EXTRA_DURATION_MS, 0)
            ?: 0
        val trackKbps = if (player.isCommandAvailable(Player.COMMAND_GET_TRACKS)) {
            selectedAudioKbps(player.currentTracks)
        } else {
            null
        }
        val position = player.currentPosition.coerceAtLeast(0)
        val localSource = sourceUri?.scheme == "file"
        val originStalled = originStalled(player, remoteSourceUrl)
        val bufferedAhead = if (localSource && duration > 0) {
            duration - position
        } else {
            player.bufferedPosition.coerceAtLeast(position) - position
        }.coerceAtLeast(0)
        _position.value = PlaybackPosition(
            episodeId = item?.mediaId?.toIntOrNull(),
            positionMs = position,
            durationMs = duration.coerceAtLeast(0),
            bufferedAheadMs = bufferedAhead,
        )
        _state.update {
            val episodeId = item?.mediaId?.toIntOrNull()
            val episodeChanged = episodeId != it.currentEpisodeId
            it.copy(
                currentEpisodeId = episodeId,
                title = item?.mediaMetadata?.title?.toString().orEmpty(),
                podcastName = item?.mediaMetadata?.artist?.toString().orEmpty(),
                artworkUrl = item?.artworkSourceUrl().orEmpty(),
                // The intent, not the instant: a seek or micro-load keeps Pause on screen.
                isPlaying = player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING),
                // Only a wait the listener asked for shows (user, 31.10.2026): an episode opened from the
                // queue prepares quietly, and Play just starts once it is ready.
                isBuffering = settledBuffering(player.playbackState == Player.STATE_BUFFERING && player.playWhenReady),
                positionMs = if (fromTicker && !episodeChanged) it.positionMs else position,
                durationMs = duration.coerceAtLeast(0),
                bufferedAheadMs = if (fromTicker && !episodeChanged) it.bufferedAheadMs else bufferedAhead,
                streamReachability = when {
                    item == null -> StreamReachability.IDLE
                    localSource -> StreamReachability.LOCAL
                    sourceChanged -> StreamReachability.CHECKING
                    else -> it.streamReachability
                },
                streamHost = remoteSourceUrl?.let(Uri::parse)?.host.orEmpty(),
                isUsingPinePodsFallback = item?.mediaMetadata?.extras
                    ?.getBoolean(EXTRA_USING_FALLBACK_MEDIA_SOURCE, false)
                    ?: false,
                isServerCopySource = remoteSourceUrl != null && isServerStream(remoteSourceUrl),
                isOnServer = episodeId != null && serverDownloads.isOnServer(episodeId),
                serverDownload = episodeId?.let { id -> serverDownloads.progress.value[id] },
                originStalled = originStalled,
                isLocalSource = localSource,
                isMarkedPlayed = if (episodeId != it.currentEpisodeId) false else it.isMarkedPlayed,
                mediaSizeBytes = if (technicalInfoSourceChanged) null else it.mediaSizeBytes,
                bitrateKbps = when {
                    technicalInfoSourceChanged -> trackKbps
                    it.bitrateKbps != null -> it.bitrateKbps
                    it.mediaSizeBytes != null && duration > 0 ->
                        bitrateKbps(it.mediaSizeBytes, duration)
                    else -> trackKbps
                },
                nativeChapters = NativeChapters.state.value.takeIf { snapshot -> snapshot.mediaId == item?.mediaId }
                    ?.chapters.orEmpty(),
                feedChapters = if (episodeChanged) emptyList() else it.feedChapters,
                seekBackSeconds = playbackPreferences.seekBackSeconds,
                seekForwardSeconds = playbackPreferences.seekForwardSeconds,
                diagnosticResults = if (sourceChanged) emptyList() else it.diagnosticResults,
                error = null,
            )
        }
        item?.mediaId?.toIntOrNull()?.takeIf { it > 0 && it != feedChaptersEpisodeId }?.let(::loadFeedChapters)
        if (remoteSourceUrl != null) scheduleStreamProbe(force = sourceChanged)
        if (technicalInfoSourceChanged && sourceUri != null) {
            scheduleMediaTechnicalInfo(sourceUri, duration.coerceAtLeast(0))
        }
    }

    // Podcasting 2.0 chapters (with their pictures) once per episode; PinePods reads the feed for
    // them, so the answer is kept for the session.
    private var feedChaptersEpisodeId: Int? = null
    private var feedChaptersJob: Job? = null
    private val feedChaptersCache = mutableMapOf<Int, List<PlaybackChapter>>()

    /** Feed chapters of any episode (the episode card), from the same session cache. */
    suspend fun episodeChapters(episodeId: Int): List<PlaybackChapter> = feedChaptersCache[episodeId] ?: withContext(Dispatchers.IO) {
        val session = sessionStore.load() ?: return@withContext null
        runCatching { EpisodeChapters.fetch(session, episodeId) }.getOrNull()
    }?.also { feedChaptersCache[episodeId] = it }.orEmpty()

    private fun loadFeedChapters(episodeId: Int) {
        feedChaptersEpisodeId = episodeId
        feedChaptersJob?.cancel()
        feedChaptersJob = viewModelScope.launch {
            val chapters = feedChaptersCache[episodeId] ?: withContext(Dispatchers.IO) {
                val session = sessionStore.load() ?: return@withContext null
                runCatching { EpisodeChapters.fetch(session, episodeId) }.getOrNull()
            }?.also { feedChaptersCache[episodeId] = it } ?: return@launch
            _state.update { if (it.currentEpisodeId == episodeId) it.copy(feedChapters = chapters) else it }
        }
    }

    private fun scheduleMediaTechnicalInfo(sourceUri: Uri, durationMs: Long) {
        val source = sourceUri.toString()
        mediaTechnicalInfoJob = viewModelScope.launch {
            val info = withContext(Dispatchers.IO) {
                when (sourceUri.scheme) {
                    "file" -> sourceUri.path?.let(::File)?.let {
                        MediaTechnicalInfoClient.fromLocalFile(it, durationMs)
                    }
                    "content" -> localContentSize(sourceUri)?.let {
                        MediaTechnicalInfo(it, bitrateKbps(it, durationMs))
                    }
                    // Asking PinePods' download endpoint for size starts another full origin
                    // download on the server; the player's own request is enough.
                    "http", "https" -> if (pinePodsHealthUrl(source) != null) null else mediaTechnicalInfoClient.fetch(
                        url = source,
                        headers = probeHeaders(source),
                        durationMs = durationMs,
                    )
                    else -> null
                }
            }
            if (activeTechnicalInfoSource != source || info == null) return@launch
            _state.update {
                it.copy(mediaSizeBytes = info.sizeBytes, bitrateKbps = info.bitrateKbps)
            }
        }
    }

    private fun localContentSize(uri: Uri): Long? = getApplication<Application>()
        .contentResolver
        .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
        ?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index < 0 || cursor.isNull(index)) null else cursor.getLong(index).takeIf { it > 0 }
        }

    private fun scheduleStreamProbe(force: Boolean) {
        val sourceUrl = activeSourceUrl ?: return
        val now = SystemClock.elapsedRealtime()
        if (probeJob?.isActive == true) return
        if (!force && now - lastProbeStartedElapsedMs < STREAM_PROBE_INTERVAL_MS) return
        if (force && now - lastProbeStartedElapsedMs < MIN_FORCED_PROBE_INTERVAL_MS) return
        lastProbeStartedElapsedMs = now
        probeJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                // PinePods' download endpoint fetches the whole episode before answering: probing
                // it timed out and reported a healthy server as unavailable. Its health check
                // answers in milliseconds and says what the indicator means.
                val healthUrl = pinePodsHealthUrl(sourceUrl)
                if (healthUrl != null) {
                    reachabilityClient.probe(healthUrl)
                } else {
                    reachabilityClient.probe(sourceUrl, probeHeaders(sourceUrl))
                }
            }
            if (activeSourceUrl != sourceUrl) return@launch
            _state.update {
                it.copy(
                    streamReachability = if (result.isAvailable) {
                        StreamReachability.AVAILABLE
                    } else {
                        StreamReachability.UNAVAILABLE
                    },
                    streamHost = result.resolvedHost.ifBlank { result.requestedHost },
                )
            }
        }
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (isActive) {
                // Only while a screen shows the position (plan 5.2): in the background nothing ticks.
                if (_position.subscriptionCount.value == 0) _position.subscriptionCount.first { it > 0 }
                controller?.let { publishPlayerState(it, fromTicker = true) }
                if (controller?.playWhenReady == true) scheduleStreamProbe(force = false)
                delay(500)
            }
        }
    }

    /**
     * DEC-023, step 4: the origin is unreachable for this device, so the server downloads the
     * episode and the player switches to its copy at the same position. The copy is temporary:
     * it is deleted from the server once the episode is played.
     */
    fun downloadCurrentToServer() {
        val episodeId = _state.value.currentEpisodeId?.takeIf { it > 0 } ?: return
        serverDownloads.requestTemporary(episodeId) {
            viewModelScope.launch { switchToServerCopy(episodeId) }
        }
        controller?.let(::publishPlayerState)
    }

    private suspend fun switchToServerCopy(episodeId: Int) {
        val player = controller ?: return
        if (player.currentMediaItem?.mediaId != episodeId.toString()) return
        val session = withContext(Dispatchers.IO) { sessionStore.load() } ?: return
        val entry = withContext(Dispatchers.IO) { itemFactory.findEntry(episodeId) } ?: return
        val positionMs = player.currentPosition.coerceAtLeast(0)
        val item = withContext(Dispatchers.IO) { itemFactory.mediaItem(entry, positionMs, session) }
        val index = player.currentMediaItemIndex
        if (index == C.INDEX_UNSET || player.currentMediaItem?.mediaId != episodeId.toString()) return
        player.replaceMediaItem(index, item)
        player.seekTo(index, positionMs)
        player.prepare()
        player.play()
        publishPlayerState(player)
    }

    /**
     * Whether the UI should show buffering: only once the player has been waiting for
     * [BUFFERING_REVEAL_MS] in a row, so starts, seeks and micro-loads don't flash the spinner.
     * The end of buffering shows at once.
     */
    private fun settledBuffering(raw: Boolean): Boolean {
        if (!raw) {
            bufferingSinceElapsedMs = 0
            bufferingRevealJob?.cancel()
            return false
        }
        val now = SystemClock.elapsedRealtime()
        if (bufferingSinceElapsedMs == 0L) {
            bufferingSinceElapsedMs = now
            bufferingRevealJob?.cancel()
            bufferingRevealJob = viewModelScope.launch {
                delay(BUFFERING_REVEAL_MS)
                controller?.let(::publishPlayerState)
            }
        }
        return now - bufferingSinceElapsedMs >= BUFFERING_REVEAL_MS
    }

    /**
     * DEC-023: offer the server download once the origin has kept the listener waiting for
     * [ORIGIN_STALL_OFFER_MS] since the first attempt, without waiting for timeouts and the
     * automatic rescue path. PinePods sources do not count: there is nothing to offer for them.
     */
    private fun originStalled(player: Player, remoteSourceUrl: String?): Boolean {
        val waiting = remoteSourceUrl != null && player.playWhenReady &&
            player.playbackState != Player.STATE_READY && player.playbackState != Player.STATE_ENDED &&
            pinePodsHealthUrl(remoteSourceUrl) == null
        if (!waiting) {
            originWaitStartedElapsedMs = 0
            return false
        }
        val now = SystemClock.elapsedRealtime()
        if (originWaitStartedElapsedMs == 0L) originWaitStartedElapsedMs = now
        return now - originWaitStartedElapsedMs >= ORIGIN_STALL_OFFER_MS
    }

    private fun isServerStream(sourceUrl: String): Boolean {
        val session = sessionStore.load() ?: return false
        return MediaSourceResolver.isPinePodsStreamRequest(session.serverUrl, sourceUrl)
    }

    private fun pinePodsHealthUrl(sourceUrl: String): String? {
        val session = sessionStore.load() ?: return null
        if (!MediaSourceResolver.isPinePodsMediaRequest(session.serverUrl, sourceUrl)) return null
        return sourceUrl.toUri().buildUpon()
            .encodedPath(PINEPODS_HEALTH_PATH)
            .encodedQuery(null)
            .build()
            .toString()
    }

    private fun probeHeaders(sourceUrl: String): Map<String, String> {
        val session = sessionStore.load() ?: return emptyMap()
        if (!MediaSourceResolver.isPinePodsMediaRequest(session.serverUrl, sourceUrl)) {
            return emptyMap()
        }
        return mapOf("Api-Key" to session.apiKey)
    }

    override fun onCleared() {
        playbackPreferences.unregisterListener(preferencesListener)
        connectJob?.cancel()
        positionJob?.cancel()
        queueSyncJob?.cancel()
        probeJob?.cancel()
        diagnosticsJob?.cancel()
        mediaTechnicalInfoJob?.cancel()
        controller?.removeListener(this)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    companion object {
        private const val PENDING_OPEN_TIMEOUT_MS = 8_000L

        /** Above this, playlist reconciliation uses batch replacements instead of single edits. */
        private const val MAX_INCREMENTAL_PLAYLIST_EDITS = 3
        private const val PINEPODS_HEALTH_PATH = "/api/pinepods_check"
        private const val ORIGIN_STALL_OFFER_MS = 15_000L

        /** Buffering shorter than this (a start, a seek, a micro-load) shows no spinner (user, 27.10.2026). */
        internal const val BUFFERING_REVEAL_MS = 1_500L
        const val EXTRA_RESUME_POSITION_MS = "ru.e1173.nookast.extra.RESUME_POSITION_MS"
        const val EXTRA_DURATION_MS = "ru.e1173.nookast.extra.DURATION_MS"
        const val EXTRA_PRIMARY_MEDIA_URL = "ru.e1173.nookast.extra.PRIMARY_MEDIA_URL"
        const val EXTRA_FALLBACK_MEDIA_URL = "ru.e1173.nookast.extra.FALLBACK_MEDIA_URL"
        const val EXTRA_PINEPODS_MEDIA_URL = "ru.e1173.nookast.extra.PINEPODS_MEDIA_URL"
        const val EXTRA_FALLBACK_ARTWORK_URL = "ru.e1173.nookast.extra.FALLBACK_ARTWORK_URL"
        const val EXTRA_ARTWORK_URL = "ru.e1173.nookast.extra.ARTWORK_URL"
        const val EXTRA_DESCRIPTION = "ru.e1173.nookast.extra.DESCRIPTION"
        const val EXTRA_PUBLISHED_AT = "ru.e1173.nookast.extra.PUBLISHED_AT"
        const val EXTRA_USING_FALLBACK_MEDIA_SOURCE =
            "ru.e1173.nookast.extra.USING_FALLBACK_MEDIA_SOURCE"
        private const val STREAM_PROBE_INTERVAL_MS = 60_000L
        private const val MIN_FORCED_PROBE_INTERVAL_MS = 5_000L
    }
}

internal fun shouldAutoCompletePreviousEpisode(
    enabled: Boolean,
    previousEpisodeId: Int,
    nextEpisodeId: Int,
    positionMs: Long,
    durationMs: Long?,
    alreadyCompleted: Boolean,
    remainingMinutesThreshold: Int,
): Boolean {
    if (!enabled || alreadyCompleted) return false
    if (previousEpisodeId == 0 || previousEpisodeId == nextEpisodeId) return false
    val duration = durationMs?.takeIf { it > 0 } ?: return false
    val position = positionMs.coerceAtLeast(0)
    if (position <= 0 || position >= duration) return false
    val thresholdMs = remainingMinutesThreshold.coerceIn(
        PlaybackPreferences.MIN_AUTO_MARK_PLAYED_REMAINING_MINUTES,
        PlaybackPreferences.MAX_AUTO_MARK_PLAYED_REMAINING_MINUTES,
    ) * 60_000L
    return duration - position <= thresholdMs
}
