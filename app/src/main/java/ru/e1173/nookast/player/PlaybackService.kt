package ru.e1173.nookast.player

import androidx.core.net.toUri
import android.app.PendingIntent
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.Metadata
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.CommandButton
import ru.e1173.nookast.designsystem.seekIconRes
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt
import kotlin.math.absoluteValue
import ru.e1173.nookast.MainActivity
import ru.e1173.nookast.R
import ru.e1173.nookast.app.appGraph
import ru.e1173.nookast.core.session.SessionRepository
import ru.e1173.nookast.data.local.SyncDatabase
import ru.e1173.nookast.data.local.SyncStore
import ru.e1173.nookast.data.local.CurrentPlaybackContextEntity
import ru.e1173.nookast.data.local.pinePodsEpisodeCacheKey
import ru.e1173.nookast.data.local.rssLocalPlaybackId
import ru.e1173.nookast.data.remote.MediaSourceResolver
import ru.e1173.nookast.data.remote.MediaSourceCandidates
import ru.e1173.nookast.diagnostics.ControlJournal
import ru.e1173.nookast.diagnostics.ControlJournalFormat
import ru.e1173.nookast.core.sync.PlaybackSessions
import ru.e1173.nookast.offline.EpisodeDownloadManager
import ru.e1173.nookast.sync.ProgressCause
import ru.e1173.nookast.sync.ProgressMutation
import ru.e1173.nookast.sync.QueueMutation
import ru.e1173.nookast.sync.LocalSequenceGenerator
import ru.e1173.nookast.sync.SyncWorkScheduler

/**
 * Owns the process-wide player and publishes it to Android system and legacy media controllers.
 * Media items and podcast-specific command mapping are connected in the player vertical slice.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlaybackService : MediaLibraryService(), Player.Listener {
    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var compatibilityRegistry: MediaCompatibilityRegistry
    private lateinit var database: SyncDatabase
    private lateinit var syncStore: SyncStore

    /** Media ID whose playback really started in this process (§5.2); null until then. */
    @Volatile
    private var engagedMediaId: String? = null

    /** The next SEEK discontinuity comes from the app restoring a position, not the user. */
    private var appSeekPending = false
    private lateinit var sessionStore: SessionRepository
    private lateinit var downloadManager: EpisodeDownloadManager
    private lateinit var streamCache: EpisodeStreamCache
    private lateinit var itemFactory: PlaybackItemFactory
    private lateinit var playbackPreferences: PlaybackPreferences
    private lateinit var connectivityManager: ConnectivityManager

    // The app's own sound processing (audio rework З, DEC-060), after the voice tee.
    private val soundProcessor = SoundProcessor()
    private var soundController: SoundController? = null
    private lateinit var mediaDeviceIdentity: MediaDeviceIdentity
    private val carSkipKeyMapper = CarSkipKeyMapper()
    private val replayDetector = ReplayDetector()
    private val controlReadApi = ru.e1173.nookast.core.sync.PinePodsSyncApi()
    private var controlReadJob: Job? = null
    private var controlReadMediaId: String? = null

    // The next control read follows this device's own long pause: it may only offer (see decideStartRead).
    private var resumedHereAfterPause = false
    private var pausedAtElapsedMs = 0L
    private var finalProgressWritten = false

    private val journal: ControlJournal by lazy { appGraph.controlJournal }
    private val metadataBroadcast by lazy { appGraph.legacyMetadataBroadcast }
    private var audioManager: AudioManager? = null
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            addedDevices.forEach { device ->
                journal.record(ControlJournal.Category.AUDIO) {
                    "device added ${ControlJournalFormat.audioDevice(device)}"
                }
            }
            soundController?.routeChanged()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            removedDevices.forEach { device ->
                journal.record(ControlJournal.Category.AUDIO) {
                    "device removed ${ControlJournalFormat.audioDevice(device)}"
                }
            }
            soundController?.routeChanged()
        }
    }
    private val playbackPreferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when {
                PlaybackPreferences.isSeekPreference(key) -> playerScope.launch {
                    val player = mediaLibrarySession?.player as? ExoPlayer ?: return@launch
                    player.setSeekBackIncrementMs(playbackPreferences.seekBackSeconds * 1_000L)
                    player.setSeekForwardIncrementMs(playbackPreferences.seekForwardSeconds * 1_000L)
                    mediaLibrarySession?.setMediaButtonPreferences(seekButtons())
                }
            }
        }

    // Media3 players are thread-confined to the Looper on which they were created.
    // Player state is sampled on Main; database and network work stays on IO.
    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    // Browser and controller requests: concurrent, read-only Room work answered via futures.
    private val libraryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var sequence: LocalSequenceGenerator
    private var periodicProgressJob: Job? = null
    private var contextWriteJob: Job? = null
    private var recoveryRetryJob: Job? = null
    private var trackedMediaItem: MediaItem? = null
    private val completionQueueRemovals = mutableSetOf<Int>()
    private val attemptedMediaUrls = linkedSetOf<String>()
    private var recoveryPending = false
    private var recoveryAttempt = 0
    private var networkCallbackRegistered = false
    private var keepCompletedContextOnEmptyTransition = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            journal.record(ControlJournal.Category.DEVICE, "network available")
            playerScope.launch { retryCurrentSourceForNewNetwork() }
        }

        override fun onLost(network: Network) {
            journal.record(ControlJournal.Category.DEVICE, "network lost")
            playerScope.launch {
                val player = mediaLibrarySession?.player ?: return@launch
                if (player.playWhenReady && player.currentMediaItem?.isRemoteMedia() == true) {
                    recoveryPending = true
                }
            }
        }
    }

    /**
     * Ignition off in the car (user request 25.10.2026): the modem may go before the pause. The
     * screen turning off or a shutdown is the last moment the network may still be up, so the
     * current position is written and delivered at once. All events go to the control journal to
     * learn the real order on the head unit.
     */
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            journal.record(ControlJournal.Category.DEVICE) { "power ${intent.action}" }
            if (intent.action == Intent.ACTION_SCREEN_OFF || intent.action == Intent.ACTION_SHUTDOWN) {
                playerScope.launch {
                    val player = mediaLibrarySession?.player ?: return@launch
                    // The head unit may pause first and darken a moment later.
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (!player.playWhenReady && pausedAtElapsedMs != 0L && now - pausedAtElapsedMs < PAUSE_BEFORE_DARK_MS) {
                        playingAtPowerOffElapsedMs = now
                    }
                    if (player.isPlaying || player.playWhenReady) {
                        playingAtPowerOffElapsedMs = android.os.SystemClock.elapsedRealtime()
                        persistProgress(ProgressCause.PAUSE)
                        persistPlaybackContext(completed = false)
                    }
                }
            }
            if (intent.action == Intent.ACTION_SCREEN_ON) playerScope.launch { resumeAfterIgnition() }
        }
    }

    // When the head unit went dark while playing (ignition off), in memory only.
    private var playingAtPowerOffElapsedMs = 0L

    /**
     * Ignition on (user, 29.10.2026: playback did not come back): the head unit pauses the player
     * when it goes dark and only wakes the screen later. If it was playing then, it plays on, within
     * 12 hours. Only on a recognised head unit, so a phone never starts playing by itself.
     */
    private fun resumeAfterIgnition() {
        val since = playingAtPowerOffElapsedMs
        playingAtPowerOffElapsedMs = 0L
        if (since == 0L || detectBuiltInMediaProfileId(mediaDeviceIdentity) == null) return
        val player = mediaLibrarySession?.player ?: return
        val away = android.os.SystemClock.elapsedRealtime() - since
        journal.record(ControlJournal.Category.DEVICE) { "ignition on after ${away / 1_000}s playing=${player.isPlaying} items=${player.mediaItemCount}" }
        if (player.isPlaying || player.mediaItemCount == 0 || away > RESUME_AFTER_IGNITION_MS) return
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }

    override fun onCreate() {
        super.onCreate()
        // The status bar shows the app's own mark, not Media3's generic note (user, 29.10.2026).
        setMediaNotificationProvider(
            androidx.media3.session.DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(ru.e1173.nookast.R.drawable.ic_notification_nookast)
            },
        )
        compatibilityRegistry = MediaCompatibilityRegistry.load(this)
        mediaDeviceIdentity = currentMediaDeviceIdentity(this)
        database = SyncDatabase.open(this)
        syncStore = SyncStore(database)
        sessionStore = appGraph.session
        sequence = LocalSequenceGenerator(this)

        playbackPreferences = PlaybackPreferences(this)
        playbackPreferences.registerListener(playbackPreferenceListener)
        downloadManager = EpisodeDownloadManager(this)
        itemFactory = PlaybackItemFactory(database, downloadManager, appGraph.serverDownloads::isOnServer)
        ioScope.launch { PlaybackMediaCache.prepare(this@PlaybackService) }
        val memoryBudgetBytes = PlaybackMediaCache.memoryBudgetBytes(
            playbackPreferences.aheadCacheMegabytes,
            PlaybackMediaCache.heapMegabytes(this),
        )
        val upstreamFactory = ResolvingDataSource.Factory(
            playbackNetworkDataSourceFactory(this) { uri ->
                val session = sessionStore.load()
                session != null &&
                    MediaSourceResolver.isPinePodsDownloadRequest(session.serverUrl, uri.toString())
            },
            object : ResolvingDataSource.Resolver {
                override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                    val session = sessionStore.load() ?: return dataSpec
                    if (!MediaSourceResolver.isPinePodsMediaRequest(
                            session.serverUrl,
                            dataSpec.uri.toString(),
                        )
                    ) {
                        return dataSpec
                    }
                    val withHeader = dataSpec.withAdditionalHeaders(
                        mapOf(PINEPODS_API_KEY_HEADER to session.apiKey),
                    )
                    if (!MediaSourceResolver.isPinePodsStreamRequest(session.serverUrl, dataSpec.uri.toString())) {
                        return withHeader
                    }
                    // `/api/data/stream` reads its key only from the query (DEC-023); the RSS key is
                    // added here, at request time, so it never sits in a MediaItem. The cache key
                    // stays the URI without it.
                    val streamKey = appGraph.serverDownloads.streamKey() ?: return withHeader
                    return withHeader.withUri(
                        dataSpec.uri.buildUpon().appendQueryParameter("api_key", streamKey).build(),
                    )
                }

                override fun resolveReportedUri(uri: Uri): Uri = uri.withoutQueryParameter("api_key")
            },
        )
        streamCache = EpisodeStreamCache(this, upstreamFactory, libraryScope)
        EpisodeStreamCache.active = streamCache
        if (!playbackPreferences.downloadWholeEpisodeOnPlayback) {
            // The setting is off: leftovers of an earlier fill are not kept on disk.
            ioScope.launch { java.io.File(cacheDir, EpisodeStreamCache.DIRECTORY).deleteRecursively() }
        }
        // Streams stay in RAM (DEC-039): the byte budget stops loading ahead, and the last 30 minutes
        // are kept behind the position so seeking back does not request the file again.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                PlaybackMediaCache.MAX_AHEAD_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            .setBackBuffer(PlaybackMediaCache.BACK_BUFFER_MS, true)
            .setTargetBufferBytes(memoryBudgetBytes)
            // The retained back buffer counts toward the byte target: with a high-bitrate episode (RGC,
            // 320 kbps: 30 min = 72 MB) the target fills up and loading stopped until the buffer ran dry,
            // so playback stalled for a fetch (user, 9.11.2026: long loading in the car). Below the
            // minimum buffer time loading goes on regardless; that adds at most ~50 s of audio.
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()
        // «Light follows the voice» (design audit 5.8): Media3's pass-through tee is always in the chain,
        // so the switch works at once and never touches the sound; off, the listener only checks a flag.
        // The sink wrapper times each window by media position, so the light follows what is audible.
        ru.e1173.nookast.preferences.VoiceLightSwitch.on = ru.e1173.nookast.preferences.voiceLightEnabled(this)
        // With «Shorten pauses» the voice light rests at 1 (З10: its alignment there is not verified).
        val voiceOn = { ru.e1173.nookast.preferences.VoiceLightSwitch.on && !SoundSession.shortenPauses }
        val voiceTap = VoiceLevelTap(enabled = voiceOn)
        val renderers = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): androidx.media3.exoplayer.audio.AudioSink = VoiceAlignedAudioSink(
                androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(androidx.media3.exoplayer.audio.TeeAudioProcessor(voiceTap), soundProcessor))
                    .build(),
                voiceTap,
                enabled = voiceOn,
            )
        }
        val player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderers)
            .setMediaSourceFactory(
                // Downloaded files are read directly: through the cache they were copied a second
                // time and filled it with what is already on disk (P-11).
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                    RoutingDataSource.Factory(
                        // «Download started episodes» streams through the shared disk cache so the
                        // episode is fetched once; otherwise RAM only (DEC-039, user 28.10.2026).
                        primary = androidx.media3.datasource.DataSource.Factory {
                            if (playbackPreferences.downloadWholeEpisodeOnPlayback) {
                                streamCache.playbackFactory.createDataSource()
                            } else {
                                upstreamFactory.createDataSource()
                            }
                        },
                        alternate = DefaultDataSource.Factory(this),
                        useAlternate = { uri -> uri.scheme != "http" && uri.scheme != "https" },
                    ),
                ),
            )
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            // Keeps Wi-Fi and the CPU awake while streaming with the screen off (P-07).
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(playbackPreferences.seekBackSeconds * 1_000L)
            .setSeekForwardIncrementMs(playbackPreferences.seekForwardSeconds * 1_000L)
            .build()
        player.addListener(this)
        soundController = SoundController(
            context = this,
            processor = soundProcessor,
            player = { mediaLibrarySession?.player as? ExoPlayer },
            mainScope = playerScope,
            account = { sessionStore.load()?.let { ru.e1173.nookast.core.sync.AccountIdentity.of(it).fingerprint } },
            podcastIdOf = { item ->
                val dao = database.offlineCacheDao()
                val byId = item.mediaId.toIntOrNull()?.takeIf { it > 0 }?.let { dao.findEpisodeByPinePodsId(it) }
                (byId ?: item.mediaMetadata.extras?.getString(PlaybackViewModel.EXTRA_PRIMARY_MEDIA_URL)
                    ?.let { dao.findEpisodeByMediaUrl(it) })?.podcastId
            },
            wideVehicleProfile = detectBuiltInMediaProfileId(mediaDeviceIdentity) != null,
        )
        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            player,
            object : MediaLibrarySession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): MediaSession.ConnectionResult {
                    PlaybackActivity.controllerConnected("${controller.packageName}:${controller.uid}", controller.packageName)
                    val access = controllerAccess(
                        packageName = controller.packageName,
                        isOwnApp = controller.isOwnApp(),
                        isTrusted = controller.isTrusted,
                        isMediaNotificationController = session.isMediaNotificationController(controller),
                    )
                    journal.record(ControlJournal.Category.CONTROLLER) {
                        "connect ${describeController(controller)} version=" +
                            "${controller.controllerVersion} interface=" +
                            "${controller.interfaceVersion} access=$access hints=" +
                            ControlJournalFormat.bundleKeys(controller.connectionHints)
                    }
                    // Trusted controllers keep Media3's default result. A rebuilt one (to add seek session
                    // commands for System UI) broke play() on a restored item for the app and stopped the
                    // playback notification from being posted at all (25.10.2026, emulator API 37).
                    // P-19: System UI shows custom actions in the back/forward slots only when previous
                    // and next are unavailable, so only the notification controller gets seek as session
                    // commands with the app's own icons, and loses previous/next. The app keeps the default.
                    if (session.isMediaNotificationController(controller)) {
                        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                            .setAvailableSessionCommands(
                                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                                    .add(SEEK_BACK_COMMAND)
                                    .add(SEEK_FORWARD_COMMAND)
                                    .build(),
                            )
                            .setAvailablePlayerCommands(
                                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                                    .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                                    .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                                    .remove(Player.COMMAND_SEEK_TO_NEXT)
                                    .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                                    .build(),
                            )
                            .setMediaButtonPreferences(seekButtons())
                            .build()
                    }
                    if (access == ControllerAccess.FULL) return super.onConnect(session, controller)
                    // X-02: an arbitrary app may drive playback but not replace the playlist,
                    // browse the library or send custom commands.
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(SessionCommands.EMPTY)
                        .setAvailablePlayerCommands(TRANSPORT_PLAYER_COMMANDS)
                        .build()
                }

                override fun onDisconnected(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ) {
                    PlaybackActivity.controllerDisconnected("${controller.packageName}:${controller.uid}")
                    journal.record(ControlJournal.Category.CONTROLLER) {
                        "disconnect ${describeController(controller)}"
                    }
                }

                // Deprecated in Media3, but still the only hook that sees each command before it
                // runs; the journal needs exactly that.
                @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
                override fun onPlayerCommandRequest(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    playerCommand: Int,
                ): Int {
                    if (!controller.isOwnApp()) journal.record(ControlJournal.Category.COMMAND) {
                        "${ControlJournalFormat.playerCommand(playerCommand)}($playerCommand) " +
                            "from ${describeController(controller)}"
                    }
                    return super.onPlayerCommandRequest(session, controller, playerCommand)
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    if (!controller.isOwnApp()) journal.record(ControlJournal.Category.COMMAND) {
                        "custom action=${customCommand.customAction} args=" +
                            ControlJournalFormat.bundleKeys(args) +
                            " from ${describeController(controller)}"
                    }
                    when (customCommand.customAction) {
                        SEEK_BACK_ACTION -> {
                            session.player.seekBack()
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        SEEK_FORWARD_ACTION -> {
                            session.player.seekForward()
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }

                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> {
                    if (!controller.isOwnApp()) journal.record(ControlJournal.Category.COMMAND) {
                        "addMediaItems ids=" +
                            ControlJournalFormat.mediaIds(mediaItems.map { it.mediaId }) +
                            " from ${describeController(controller)}"
                    }
                    // The in-app player builds complete items with PlaybackItemFactory already.
                    if (controller.isOwnApp()) {
                        return super.onAddMediaItems(mediaSession, controller, mediaItems)
                    }
                    return ioFuture { resolvePlayableItems(mediaItems).toMutableList() }
                }

                override fun onSetMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                    startIndex: Int,
                    startPositionMs: Long,
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    if (!controller.isOwnApp()) journal.record(ControlJournal.Category.COMMAND) {
                        "setMediaItems ids=" +
                            ControlJournalFormat.mediaIds(mediaItems.map { it.mediaId }) +
                            " startIndex=$startIndex startPositionMs=$startPositionMs" +
                            " from ${describeController(controller)}"
                    }
                    if (controller.isOwnApp()) {
                        return super.onSetMediaItems(
                            mediaSession,
                            controller,
                            mediaItems,
                            startIndex,
                            startPositionMs,
                        )
                    }
                    return ioFuture { resolvePlaybackRequest(mediaItems, startIndex, startPositionMs) }
                }

                override fun onPlaybackResumption(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    isForPlayback: Boolean,
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    if (!controller.isOwnApp()) journal.record(ControlJournal.Category.COMMAND) {
                        "playbackResumption forPlayback=$isForPlayback " +
                            "from ${describeController(controller)}"
                    }
                    return ioFuture { resumptionItems() }
                }

                override fun onGetLibraryRoot(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    params: LibraryParams?,
                ): ListenableFuture<LibraryResult<MediaItem>> {
                    journal.record(ControlJournal.Category.LIBRARY) {
                        "getLibraryRoot ${describeParams(params)} " +
                            "from ${describeController(browser)}"
                    }
                    val recent = params?.isRecent == true
                    return Futures.immediateFuture(
                        LibraryResult.ofItem(MediaLibraryTree.root(libraryTitles(), recent), params),
                    )
                }

                override fun onGetChildren(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    parentId: String,
                    page: Int,
                    pageSize: Int,
                    params: LibraryParams?,
                ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                    journal.record(ControlJournal.Category.LIBRARY) {
                        "getChildren parent=${ControlJournalFormat.mediaId(parentId)} " +
                            "page=$page size=$pageSize ${describeParams(params)} " +
                            "from ${describeController(browser)}"
                    }
                    return ioFuture {
                        val children = libraryChildren(parentId)
                            ?: return@ioFuture LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                        LibraryResult.ofItemList(
                            MediaLibraryTree.page(children, page, pageSize),
                            params,
                        )
                    }
                }

                override fun onGetItem(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    mediaId: String,
                ): ListenableFuture<LibraryResult<MediaItem>> {
                    journal.record(ControlJournal.Category.LIBRARY) {
                        "getItem id=${ControlJournalFormat.mediaId(mediaId)} " +
                            "from ${describeController(browser)}"
                    }
                    return ioFuture {
                        val item = MediaLibraryTree.folderItem(mediaId, libraryTitles())
                            ?: mediaId.toIntOrNull()
                                ?.let { itemFactory.findEntry(it) }
                                ?.let { MediaLibraryTree.episodeItem(it, ArtworkContentProvider::uriFor) }
                        if (item != null) {
                            LibraryResult.ofItem(item, null)
                        } else {
                            LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                        }
                    }
                }

                override fun onSubscribe(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    parentId: String,
                    params: LibraryParams?,
                ): ListenableFuture<LibraryResult<Void>> {
                    journal.record(ControlJournal.Category.LIBRARY) {
                        "subscribe parent=${ControlJournalFormat.mediaId(parentId)} " +
                            "from ${describeController(browser)}"
                    }
                    return super.onSubscribe(session, browser, parentId, params)
                }

                override fun onSearch(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    query: String,
                    params: LibraryParams?,
                ): ListenableFuture<LibraryResult<Void>> {
                    // The query text is what the driver said or typed; only its length is kept.
                    journal.record(ControlJournal.Category.LIBRARY) {
                        "search queryLength=${query.length} ${describeParams(params)} " +
                            "from ${describeController(browser)}"
                    }
                    return super.onSearch(session, browser, query, params)
                }

                override fun onMediaButtonEvent(
                    session: MediaSession,
                    controllerInfo: MediaSession.ControllerInfo,
                    intent: Intent,
                ): Boolean {
                    val journalKeyEvent = IntentCompat.getParcelableExtra(
                        intent,
                        Intent.EXTRA_KEY_EVENT,
                        KeyEvent::class.java,
                    )
                    journal.record(ControlJournal.Category.MEDIA_KEY) {
                        (journalKeyEvent?.let { ControlJournalFormat.keyEvent(it) }
                            ?: "intent action=${intent.action} without key event") +
                            " from ${describeController(controllerInfo)}"
                    }
                    val profile = compatibilityRegistry.resolveFor(
                        controllerInfo.packageName,
                        mediaDeviceIdentity,
                        null,
                    )
                    if (!profile.mapPreviousNextToSeek) return false
                    val keyEvent = journalKeyEvent ?: return false
                    if (keyEvent.keyCode != KeyEvent.KEYCODE_MEDIA_PREVIOUS &&
                        keyEvent.keyCode != KeyEvent.KEYCODE_MEDIA_NEXT
                    ) {
                        return false
                    }
                    val carAction = carSkipKeyMapper.onKey(
                        keyCode = keyEvent.keyCode,
                        action = keyEvent.action,
                        repeatCount = keyEvent.repeatCount,
                        eventTimeMs = keyEvent.eventTime,
                        seekBackSeconds = playbackPreferences.seekBackSeconds,
                        seekForwardSeconds = playbackPreferences.seekForwardSeconds,
                    )
                    if (carAction != null) {
                        journal.record(ControlJournal.Category.MEDIA_KEY) {
                            "mapped to $carAction by profile ${profile.id}"
                        }
                        val player = session.player
                        when (carAction) {
                            is CarSkipAction.Seek -> seekPlayerBy(player, carAction.deltaMs)
                            CarSkipAction.NextEpisode -> player.seekToNextMediaItem()
                            CarSkipAction.PreviousEpisode -> player.seekToPreviousMediaItem()
                        }
                    }
                    // Consume both DOWN and UP so Media3 does not also change the playlist item.
                    return true
                }
            },
        )
            .setId(SESSION_ID)
            .setBitmapLoader(CoilArtworkBitmapLoader(this))
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    MainActivity.OPEN_NOW_PLAYING_REQUEST_CODE,
                    Intent(this, MainActivity::class.java).apply {
                        action = MainActivity.ACTION_OPEN_NOW_PLAYING
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

        ContextCompat.registerReceiver(
            this,
            powerReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SHUTDOWN)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Always registered: the callback is cheap and the journal drops records while off.
        audioManager = getSystemService(AudioManager::class.java)?.also { manager ->
            manager.registerAudioDeviceCallback(audioDeviceCallback, null)
        }

        soundController?.start()
        restorePlaybackContext(player)
        // Each time the app comes to the foreground: what was listened elsewhere meanwhile (user, 9.11.2026).
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(foregroundObserver)

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallbackRegistered = runCatching {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            true
        }.getOrDefault(false)

        periodicProgressJob = playerScope.launch {
            while (isActive) {
                delay(PROGRESS_INTERVAL_MS)
                if (player.isPlaying) {
                    persistProgress(ProgressCause.PERIODIC)
                    persistPlaybackContext(completed = false)
                    checkReplay(player)
                }
            }
        }
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo,
    ): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        persistFinalProgressSynchronously()
        soundController?.stop()
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.removeObserver(foregroundObserver)
        followServerJob?.cancel()
        playbackPreferences.unregisterListener(playbackPreferenceListener)
        periodicProgressJob?.cancel()
        contextWriteJob?.cancel()
        recoveryRetryJob?.cancel()
        if (networkCallbackRegistered) {
            runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        }
        audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
        runCatching { unregisterReceiver(powerReceiver) }
        controlReadJob?.cancel()
        mediaLibrarySession?.let { session ->
            session.player.removeListener(this)
            session.player.release()
            session.release()
        }
        mediaLibrarySession = null
        if (EpisodeStreamCache.active === streamCache) EpisodeStreamCache.active = null
        streamCache.release()
        playerScope.cancel()
        ioScope.cancel()
        libraryScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Android may kill the process after the task is swiped without calling onDestroy(). Keep
        // this operation local and bounded; WorkManager performs the network delivery later.
        persistFinalProgressSynchronously()
        super.onTaskRemoved(rootIntent)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        PlaybackActivity.playing = isPlaying
        soundController?.playingChanged(isPlaying)
        journal.record(ControlJournal.Category.PLAYER) { "isPlaying=$isPlaying" }
        appGraph.syncScheduler.setPlaying(isPlaying)
        if (isPlaying && pausedAtElapsedMs != 0L &&
            android.os.SystemClock.elapsedRealtime() - pausedAtElapsedMs > LONG_PAUSE_MS
        ) {
            // After a long pause another device may have moved on: a fresh session and a fresh
            // control read, as for a new start (§5.2, §5.10; review 3.6).
            PlaybackSessions.close()
            controlReadMediaId = null
            resumedHereAfterPause = true
        }
        pausedAtElapsedMs = if (isPlaying) 0L else android.os.SystemClock.elapsedRealtime()
        finalProgressWritten = false
        if (isPlaying) {
            // Real playback engages the session (§5.2); passive progress may be written from now.
            val item = mediaLibrarySession?.player?.currentMediaItem
            engagedMediaId = item?.mediaId
            item?.mediaId?.toIntOrNull()?.takeIf { it > 0 }?.let(PlaybackSessions::engage)
        }
        if (isPlaying) startReplayCheck()
        if (isPlaying) startControlRead()
        if (!isPlaying) {
            persistProgress(ProgressCause.PAUSE)
            persistPlaybackContext(completed = false)
            if (mediaLibrarySession?.player?.playWhenReady == false) {
                switchToDownloadedFileIfReady()
                // A fill skipped on mobile data starts once a pause finds Wi-Fi.
                fillStreamCache(mediaLibrarySession?.player?.currentMediaItem)
            }
        }
        metadataBroadcast.publish(mediaLibrarySession?.player ?: return, "isPlaying")
    }

    private fun fillStreamCache(item: MediaItem?) {
        if (item == null || !playbackPreferences.downloadWholeEpisodeOnPlayback) return
        val uri = item.localConfiguration?.uri ?: return
        if (uri.scheme != "http" && uri.scheme != "https") return
        val episodeId = item.mediaId.toIntOrNull() ?: return
        streamCache.fill(
            episodeId = episodeId,
            uri = uri,
            title = item.mediaMetadata.title?.toString().orEmpty(),
            podcast = item.mediaMetadata.artist?.toString().orEmpty(),
            artworkUrl = item.artworkSourceUrl(),
        )
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        journal.record(ControlJournal.Category.PLAYER) {
            "state=${ControlJournalFormat.playbackState(playbackState)}"
        }
        if (playbackState == Player.STATE_ENDED) {
            persistProgress(ProgressCause.COMPLETE)
            persistPlaybackContext(completed = true)
            val player = mediaLibrarySession?.player
            if (player != null && !player.hasNextMediaItem()) {
                keepCompletedContextOnEmptyTransition = true
                player.clearMediaItems()
            }
        }
        if (playbackState == Player.STATE_READY) clearRecoveryState()
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        journal.record(ControlJournal.Category.PLAYER) {
            "playWhenReady=$playWhenReady reason=${ControlJournalFormat.playWhenReadyReason(reason)}"
        }
        if (!playWhenReady) {
            recoveryRetryJob?.cancel()
            recoveryRetryJob = null
            return
        }
        val player = mediaLibrarySession?.player ?: return
        if (recoveryPending && player.playbackState == Player.STATE_IDLE) {
            retryCurrentSource(clearAttemptedSources = true)
        }
    }

    override fun onTracksChanged(tracks: Tracks) {
        val player = mediaLibrarySession?.player ?: return
        NativeChapters.add(player.currentMediaItem?.mediaId, NativeChapters.fromTracks(tracks))
    }

    override fun onMetadata(metadata: Metadata) {
        val player = mediaLibrarySession?.player ?: return
        NativeChapters.add(player.currentMediaItem?.mediaId, NativeChapters.fromMetadata(metadata))
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        metadataBroadcast.publish(mediaLibrarySession?.player ?: return, "metadata")
    }

    override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
        journal.record(ControlJournal.Category.AUDIO) {
            "suppression=${ControlJournalFormat.suppressionReason(playbackSuppressionReason)}"
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        val player = mediaLibrarySession?.player ?: return
        val item = player.currentMediaItem ?: return
        val extras = item.mediaMetadata.extras
        val currentUrl = item.localConfiguration?.uri?.toString()
        currentUrl?.let(attemptedMediaUrls::add)
        recoveryPending = true
        val nextUrl = nextRecoverySource(
            currentUrl = currentUrl,
            primaryUrl = extras?.getString(PlaybackViewModel.EXTRA_PRIMARY_MEDIA_URL),
            fallbackUrl = extras?.getString(PlaybackViewModel.EXTRA_FALLBACK_MEDIA_URL),
            attemptedUrls = attemptedMediaUrls,
        )
        if (nextUrl != null && switchCurrentMediaSource(player, item, nextUrl)) return
        val httpStatus = (error.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
        val kind = playbackErrorKind(error.errorCode, httpStatus)
        if (kind != PlaybackErrorKind.TRANSIENT) {
            // The same request would fail the same way: stop now instead of minutes of retries (4.6).
            journal.record(ControlJournal.Category.PLAYER) { "error ${error.errorCode} http=${httpStatus ?: "-"} kind=$kind: stopped" }
            clearRecoveryState()
            player.pause()
            return
        }
        scheduleRecoveryRetry()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        soundController?.itemChanged(mediaItem, repeat = reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
        resumedHereAfterPause = false
        NativeChapters.reset(mediaItem?.mediaId)
        replayDetector.reset()
        controlReadJob?.cancel()
        controlReadMediaId = null
        // An automatic transition keeps playing: the next item gets its control read here (§5.11,
        // simplified: read on arrival rather than 20 s before the end).
        if (mediaLibrarySession?.player?.isPlaying == true) startControlRead()
        if (mediaLibrarySession?.player?.isPlaying == true) startReplayCheck()
        journal.record(ControlJournal.Category.PLAYER) {
            "transition id=${ControlJournalFormat.mediaId(mediaItem?.mediaId)} " +
                "reason=${ControlJournalFormat.transitionReason(reason)}"
        }
        val previous = trackedMediaItem
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && previous != null) {
            val durationMs = previous.mediaMetadata.extras
                ?.getLong(PlaybackViewModel.EXTRA_DURATION_MS, 0)
                ?.takeIf { it > 0 }
            persistProgressForItem(
                item = previous,
                positionMs = durationMs ?: 0,
                durationMs = durationMs,
                cause = ProgressCause.COMPLETE,
            )
        }
        if (previous?.mediaId != mediaItem?.mediaId) clearRecoveryState()
        if (previous?.mediaId != mediaItem?.mediaId && mediaItem?.mediaId != engagedMediaId) {
            engagedMediaId = if (mediaLibrarySession?.player?.isPlaying == true) mediaItem?.mediaId else null
            mediaItem?.mediaId?.toIntOrNull()?.takeIf { it > 0 && engagedMediaId != null }?.let(PlaybackSessions::engage)
        }
        trackedMediaItem = mediaItem
        if (mediaItem == null) {
            if (keepCompletedContextOnEmptyTransition) {
                keepCompletedContextOnEmptyTransition = false
            } else {
                contextWriteJob?.cancel()
                contextWriteJob = ioScope.launch {
                    database.offlineCacheDao().clearCurrentPlaybackContext()
                }
            }
        } else {
            persistPlaybackContext(completed = false)
        }
        if (mediaItem != null && playbackPreferences.downloadWholeEpisodeOnPlayback) {
            // Automatic: the stream cache fills the rest on Wi-Fi and becomes the download (P-04).
            fillStreamCache(mediaItem)
        }
        val resumePositionMs = mediaItem?.mediaMetadata?.extras
            ?.getLong(PlaybackViewModel.EXTRA_RESUME_POSITION_MS, 0)
            ?: 0
        if (resumePositionMs > 0 && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            // The app restores the saved position: not a user seek (S-01, §5.2).
            appSeekPending = true
            mediaLibrarySession?.player?.seekTo(resumePositionMs)
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        journal.record(ControlJournal.Category.PLAYER) {
            "discontinuity ${oldPosition.positionMs}->${newPosition.positionMs}ms " +
                "reason=${ControlJournalFormat.discontinuityReason(reason)}"
        }
        if (reason == Player.DISCONTINUITY_REASON_SEEK && appSeekPending) {
            appSeekPending = false
            return
        }
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            // A seek interrupts the sound anyway: the moment to move to a finished download.
            switchToDownloadedFileIfReady()
            // A user seek leaves "from the beginning": a fragment elsewhere is not a replay (§5.13).
            replayDetector.reset()
            persistProgress(ProgressCause.SEEK, newPosition.positionMs)
            persistPlaybackContext(completed = false, explicitPositionMs = newPosition.positionMs)
        }
    }

    /**
     * P-04: once the download of the streamed episode is complete, playback moves to the file on
     * a pause or a seek, so the stream stops spending traffic and a rewind no longer re-downloads.
     */
    private fun switchToDownloadedFileIfReady() {
        val item = mediaLibrarySession?.player?.currentMediaItem ?: return
        val scheme = item.localConfiguration?.uri?.scheme
        if (scheme != "http" && scheme != "https") return
        val episodeId = item.mediaId.toIntOrNull()?.takeIf { it > 0 } ?: return
        ioScope.launch {
            val localUri = downloadManager.localUriIfReady(episodeId) ?: return@launch
            playerScope.launch {
                val player = mediaLibrarySession?.player ?: return@launch
                val current = player.currentMediaItem ?: return@launch
                if (current.mediaId != item.mediaId) return@launch
                val itemIndex = player.currentMediaItemIndex
                if (itemIndex == C.INDEX_UNSET) return@launch
                val positionMs = player.currentPosition.coerceAtLeast(0)
                val shouldPlay = player.playWhenReady
                journal.record(ControlJournal.Category.PLAYER) { "source switched to download" }
                player.replaceMediaItem(itemIndex, current.buildUpon().setUri(localUri).build())
                player.seekTo(itemIndex, positionMs)
                player.prepare()
                if (shouldPlay) player.play()
            }
        }
    }

    private fun switchCurrentMediaSource(
        player: Player,
        item: MediaItem,
        targetUrl: String,
    ): Boolean {
        val targetUri = targetUrl.toUri()
        if (targetUri.scheme != "https" && targetUri.scheme != "http") return false
        val extras = item.mediaMetadata.extras
        val fallbackUrl = extras?.getString(PlaybackViewModel.EXTRA_FALLBACK_MEDIA_URL)
        val updatedExtras = Bundle().apply {
            extras?.let(::putAll)
            putBoolean(
                PlaybackViewModel.EXTRA_USING_FALLBACK_MEDIA_SOURCE,
                targetUrl == fallbackUrl,
            )
        }
        val replacement = item.buildUpon()
            .setUri(targetUri)
            .setMediaMetadata(
                item.mediaMetadata.buildUpon()
                    .setExtras(updatedExtras)
                    .build(),
            )
            .build()
        val itemIndex = player.currentMediaItemIndex
        if (itemIndex == C.INDEX_UNSET) return false
        val resumePositionMs = player.currentPosition.coerceAtLeast(0)
        val shouldPlay = player.playWhenReady
        player.replaceMediaItem(itemIndex, replacement)
        player.seekTo(itemIndex, resumePositionMs)
        player.prepare()
        if (shouldPlay) player.play()
        return true
    }

    private fun scheduleRecoveryRetry() {
        val player = mediaLibrarySession?.player ?: return
        val item = player.currentMediaItem ?: return
        if (!player.playWhenReady || !item.isRemoteMedia()) return
        if (recoveryRetryJob?.isActive == true) return
        if (recoveryAttempt >= MAX_RECOVERY_ATTEMPTS) {
            // About four minutes of retries (P-10): stop and leave the error visible instead of
            // looping forever and draining the battery. Play prepares and tries again.
            journal.record(ControlJournal.Category.PLAYER) { "recovery gave up after $recoveryAttempt attempts" }
            clearRecoveryState()
            player.pause()
            return
        }
        val expectedMediaId = item.mediaId
        val delayMs = recoveryRetryDelayMs(recoveryAttempt)
        recoveryAttempt += 1
        recoveryRetryJob = playerScope.launch {
            delay(delayMs)
            recoveryRetryJob = null
            val activePlayer = mediaLibrarySession?.player ?: return@launch
            if (!recoveryPending || !activePlayer.playWhenReady) return@launch
            if (activePlayer.currentMediaItem?.mediaId != expectedMediaId) return@launch
            retryCurrentSource(clearAttemptedSources = true)
        }
    }

    private fun retryCurrentSourceForNewNetwork() {
        val player = mediaLibrarySession?.player ?: return
        if (!recoveryPending || !player.playWhenReady) return
        if (player.playbackState == Player.STATE_READY && player.playerError == null) {
            clearRecoveryState()
            return
        }
        retryCurrentSource(clearAttemptedSources = true)
    }

    private fun retryCurrentSource(clearAttemptedSources: Boolean) {
        val player = mediaLibrarySession?.player ?: return
        val item = player.currentMediaItem ?: return
        if (!item.isRemoteMedia() || !player.playWhenReady) return
        recoveryRetryJob?.cancel()
        recoveryRetryJob = null
        if (clearAttemptedSources) attemptedMediaUrls.clear()
        val resumePositionMs = player.currentPosition.coerceAtLeast(0)
        player.prepare()
        player.seekTo(resumePositionMs)
        player.play()
    }

    private fun clearRecoveryState() {
        recoveryRetryJob?.cancel()
        recoveryRetryJob = null
        attemptedMediaUrls.clear()
        recoveryPending = false
        recoveryAttempt = 0
    }

    private fun MediaItem.isRemoteMedia(): Boolean = localConfiguration?.uri?.scheme.let {
        it == "https" || it == "http"
    }

    /**
     * Control read at the start of playback (SYNC.md §5.10): another device may have listened
     * further or rewound since this one last saw the episode. Playback starts at once from local
     * data; an answer within [START_WINDOW_MS] moves to the server position with "Go back", a
     * later one only offers it. Once per item and engagement.
     */
    private fun startControlRead() {
        val player = mediaLibrarySession?.player ?: return
        val item = player.currentMediaItem ?: return
        if (item.mediaId == controlReadMediaId) return
        controlReadMediaId = item.mediaId
        val episodeId = item.mediaId.toIntOrNull()?.takeIf { it > 0 } ?: return
        val startPositionMs = player.currentPosition.coerceAtLeast(0)
        val durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val sessionAtStart = PlaybackSessions.currentFor(episodeId)
        // Taken once: this read is the one for the resume after this device's own pause.
        val resumedHere = resumedHereAfterPause
        resumedHereAfterPause = false
        controlReadJob?.cancel()
        // Not ioScope: that one is serial, and a slow read must not hold up progress writes.
        controlReadJob = playerScope.launch(Dispatchers.IO) {
            val session = sessionStore.load() ?: return@launch
            val repository = appGraph.syncRepository
            val local = repository.progress(episodeId)
            // An undelivered intent from before this playback is newer than anything the server has.
            val pendingAtStart = local?.dirty == true && local.intentSession != sessionAtStart
            val ticket = repository.reserveTicket()
            val server = withTimeoutOrNull(CONTROL_READ_TIMEOUT_MS) {
                runCatching { controlReadApi.episode(session, episodeId) }.getOrNull()
            } ?: return@launch
            repository.observeProgress(ticket, server)
            reportMissingDuration(session, episodeId, server.durationSeconds)
            val action = decideStartRead(
                startPositionMs = startPositionMs,
                serverPositionMs = server.listenSeconds * 1_000,
                serverCompleted = server.completed,
                localPendingAtStart = pendingAtStart,
                elapsedSinceStartMs = android.os.SystemClock.elapsedRealtime() - startedAt,
                durationMs = durationMs,
                resumedHere = resumedHere,
            )
            withContext(Dispatchers.Main) { applyControlRead(item.mediaId, action) }
        }
    }

    /** When the server knows no length for the episode, it gets the one the player decoded. */
    private suspend fun reportMissingDuration(session: ru.e1173.nookast.auth.AuthSession, episodeId: Int, serverSeconds: Long?) {
        if ((serverSeconds ?: 0L) > 0L) return
        // The player knows the length once the media is prepared; a few seconds at most.
        var durationMs = 0L
        for (attempt in 0 until 20) {
            durationMs = withContext(Dispatchers.Main) {
                mediaLibrarySession?.player?.takeIf { it.currentMediaItem?.mediaId == episodeId.toString() }
                    ?.duration?.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
            }
            if (durationMs > 0L) break
            kotlinx.coroutines.delay(500)
        }
        if (durationMs <= 0L) return
        runCatching { controlReadApi.reportDuration(session, episodeId, (durationMs / 1_000).toInt()) }
            .onSuccess { journal.record(ControlJournal.Category.PLAYER) { "duration reported " } }
    }

    private fun applyControlRead(mediaId: String, action: StartReadAction) {
        val player = mediaLibrarySession?.player ?: return
        if (player.currentMediaItem?.mediaId != mediaId) return
        journal.record(ControlJournal.Category.PLAYER) { "control-read $action" }
        when (action) {
            StartReadAction.None -> Unit
            is StartReadAction.Jump -> {
                // Recorded as a seek: backwards it becomes a REWIND to the server position, so this
                // session's first ticks cannot undo the other device's rewind.
                val fromMs = player.currentPosition
                player.seekTo(action.toMs)
                ControlReadEvents.publish(mediaId, jumped = true, serverMs = action.toMs, targetMs = fromMs)
            }
            is StartReadAction.Offer ->
                ControlReadEvents.publish(mediaId, jumped = false, serverMs = action.toMs, targetMs = action.toMs)
        }
    }

    /** §5.13: a played episode that starts from the beginning may be a replay. */
    private fun startReplayCheck() {
        val player = mediaLibrarySession?.player ?: return
        val item = player.currentMediaItem ?: return
        val episodeId = item.mediaId.toIntOrNull()?.takeIf { it > 0 } ?: return
        val positionMs = player.currentPosition
        val durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
        ioScope.launch {
            val completed = appGraph.syncRepository.progress(episodeId)?.completed == true
            withContext(Dispatchers.Main) {
                replayDetector.onStart(item.mediaId, completed, positionMs, durationMs)
            }
        }
    }

    private fun checkReplay(player: Player) {
        val item = player.currentMediaItem ?: return
        if (!replayDetector.onPlayed(item.mediaId, PROGRESS_INTERVAL_MS)) return
        val episodeId = item.mediaId.toIntOrNull() ?: return
        val positionMs = player.currentPosition
        val durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
        ioScope.launch {
            if (syncStore.recordReplay(episodeId, positionMs, durationMs)) {
                journal.record(ControlJournal.Category.PLAYER) { "replay episode=$episodeId unplayed" }
                SyncWorkScheduler.enqueueImmediate(this@PlaybackService)
            }
        }
    }

    private fun persistProgress(cause: ProgressCause, explicitPositionMs: Long? = null) {
        val player = mediaLibrarySession?.player ?: return
        val item = player.currentMediaItem ?: return
        val positionMs = (explicitPositionMs ?: player.currentPosition).coerceAtLeast(0)
        val durationMs = player.duration.takeIf { it != C.TIME_UNSET && it >= 0 }
        persistProgressForItem(item, positionMs, durationMs, cause)
    }

    private fun currentProgressMutation(
        cause: ProgressCause,
        explicitPositionMs: Long? = null,
    ): ProgressMutation? {
        val player = mediaLibrarySession?.player ?: return null
        val item = player.currentMediaItem ?: return null
        val positionMs = (explicitPositionMs ?: player.currentPosition).coerceAtLeast(0)
        val durationMs = player.duration.takeIf { it != C.TIME_UNSET && it >= 0 }
        return progressMutationForItem(item, positionMs, durationMs, cause)
    }

    private fun persistProgressForItem(
        item: MediaItem,
        positionMs: Long,
        durationMs: Long?,
        cause: ProgressCause,
    ) {
        // docs/SYNC.md §5.2 (S-01, S40): positions the app set itself are not user intents. Passive
        // writes need a session that really played this item.
        val passive = cause == ProgressCause.PERIODIC || cause == ProgressCause.PAUSE ||
            cause == ProgressCause.STOP
        if (passive && engagedMediaId != item.mediaId) return
        val mutation = progressMutationForItem(item, positionMs, durationMs, cause)
        if (mutation != null) {
            persistMutation(mutation)
        } else {
            persistDeferredProgressForItem(item, positionMs, durationMs, cause)
        }
    }

    private fun progressMutationForItem(
        item: MediaItem,
        positionMs: Long,
        durationMs: Long?,
        cause: ProgressCause,
    ): ProgressMutation? {
        val episodeId = item.mediaId.toIntOrNull()?.takeIf { it > 0 } ?: return null
        return ProgressMutation(
            episodeId = episodeId,
            positionMs = positionMs.coerceAtLeast(0),
            durationMs = durationMs,
            cause = cause,
            sequence = sequence.next(),
            recordedAtEpochMs = System.currentTimeMillis(),
        )
    }

    private fun persistMutation(mutation: ProgressMutation) {
        if (mutation.cause == ProgressCause.SEEK) {
            completionQueueRemovals.remove(mutation.episodeId)
        }
        val removal = if (
            mutation.cause == ProgressCause.COMPLETE &&
            completionQueueRemovals.add(mutation.episodeId)
        ) {
            QueueMutation.Remove(
                episodeId = mutation.episodeId,
                operationId = UUID.randomUUID().toString(),
                sequence = sequence.next(),
            )
        } else {
            null
        }
        ioScope.launch {
            if (removal != null) {
                syncStore.recordCompletionAndRemoveFromQueue(
                    progress = mutation,
                    removal = removal,
                    createdAtEpochMs = mutation.recordedAtEpochMs,
                )
            } else {
                syncStore.recordProgress(mutation)
            }
            // Ticks only write locally (S-09, S28); the scheduler delivers while playing every 30 s.
            // Pause, stop, seeks and completion wake it at once (§9).
            if (mutation.cause != ProgressCause.PERIODIC) {
                SyncWorkScheduler.enqueueImmediate(this@PlaybackService)
            }
        }
    }

    private fun persistDeferredProgressForItem(
        item: MediaItem,
        positionMs: Long,
        durationMs: Long?,
        cause: ProgressCause,
    ) {
        val localEpisodeId = item.mediaId.toIntOrNull()?.takeIf { it < 0 } ?: return
        val now = System.currentTimeMillis()
        ioScope.launch {
            val cached = database.offlineCacheDao().getRssOnlyEpisodes()
                .firstOrNull { rssLocalPlaybackId(it.cacheKey) == localEpisodeId }
                ?: return@launch
            if (cause == ProgressCause.COMPLETE) {
                syncStore.stageDeferredCompletionAndQueueRemoval(
                    episodeCacheKey = cached.cacheKey,
                    positionMs = positionMs.coerceAtLeast(0),
                    durationMs = durationMs,
                    progressSequence = sequence.next(now),
                    queueSequence = sequence.next(now),
                    createdAtEpochMs = now,
                )
            } else {
                syncStore.stageDeferredProgress(
                    episodeCacheKey = cached.cacheKey,
                    positionMs = positionMs.coerceAtLeast(0),
                    durationMs = durationMs,
                    cause = cause,
                    sequence = sequence.next(now),
                    createdAtEpochMs = now,
                )
            }
        }
    }

    private fun persistFinalProgressSynchronously() {
        // Once per stop (P-12): onTaskRemoved and onDestroy both come here; playback that goes on
        // after a swipe re-arms it in onIsPlayingChanged.
        if (finalProgressWritten) return
        finalProgressWritten = true
        // Only a position the user was listening to up to now (review 3.6, finding 1): a paused or
        // merely restored item already wrote its PAUSE, and its player position may be older than
        // an observation adopted since (a rewind on another device). Sending it would undo that.
        val player = mediaLibrarySession?.player
        val listening = player != null && player.playWhenReady &&
            player.currentMediaItem?.mediaId != null && player.currentMediaItem?.mediaId == engagedMediaId
        val mutation = if (listening) currentProgressMutation(ProgressCause.STOP) else null
        val context = capturePlaybackContext(completed = false)
        contextWriteJob?.cancel()
        // Bounded: the process may be killed right after, and the main thread must not hang (P-12).
        runBlocking(Dispatchers.IO) { withTimeoutOrNull(FINAL_WRITE_TIMEOUT_MS) {
            if (mutation != null) syncStore.recordProgress(mutation)
            if (context != null) {
                val entity = context.toEntity()
                database.offlineCacheDao().upsertCurrentPlaybackContext(entity)
                if (listening && mutation == null && entity.pinePodsEpisodeId == null) {
                    syncStore.stageDeferredProgress(
                        episodeCacheKey = entity.episodeCacheKey,
                        positionMs = entity.positionMs,
                        durationMs = entity.durationMs,
                        cause = ProgressCause.STOP,
                        sequence = sequence.next(entity.updatedAtEpochMs),
                        createdAtEpochMs = entity.updatedAtEpochMs,
                    )
                }
            }
        } }
        if (mutation != null) SyncWorkScheduler.enqueueImmediate(this)
    }

    private fun persistPlaybackContext(
        completed: Boolean,
        explicitPositionMs: Long? = null,
    ) {
        val snapshot = capturePlaybackContext(completed, explicitPositionMs) ?: return
        contextWriteJob?.cancel()
        contextWriteJob = ioScope.launch {
            database.offlineCacheDao().upsertCurrentPlaybackContext(snapshot.toEntity())
        }
    }

    private fun capturePlaybackContext(
        completed: Boolean,
        explicitPositionMs: Long? = null,
    ): PlaybackContextSnapshot? {
        val player = mediaLibrarySession?.player ?: return null
        val item = player.currentMediaItem ?: return null
        val episodeId = item.mediaId.toIntOrNull()?.takeIf { it != 0 } ?: return null
        val extras = item.mediaMetadata.extras
        val mediaUrl = extras?.getString(PlaybackViewModel.EXTRA_PRIMARY_MEDIA_URL)
            ?.takeIf(String::isNotBlank)
            ?: item.localConfiguration?.uri?.toString().orEmpty()
        if (mediaUrl.isBlank()) return null
        return PlaybackContextSnapshot(
            episodeId = episodeId,
            title = item.mediaMetadata.title?.toString().orEmpty(),
            podcastName = item.mediaMetadata.artist?.toString().orEmpty(),
            artworkUrl = item.artworkSourceUrl(),
            mediaUrl = mediaUrl,
            description = extras?.getString(PlaybackViewModel.EXTRA_DESCRIPTION).orEmpty(),
            publishedAt = extras?.getString(PlaybackViewModel.EXTRA_PUBLISHED_AT).orEmpty(),
            positionMs = (explicitPositionMs ?: player.currentPosition).coerceAtLeast(0),
            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it >= 0 }
                ?: extras?.getLong(PlaybackViewModel.EXTRA_DURATION_MS, 0)?.takeIf { it > 0 },
            completed = completed,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
    }

    private suspend fun PlaybackContextSnapshot.toEntity(): CurrentPlaybackContextEntity {
        val cached = database.offlineCacheDao().findEpisodeByMediaUrl(mediaUrl)
        val cacheKey = cached?.cacheKey ?: episodeId.takeIf { it > 0 }
            ?.let(::pinePodsEpisodeCacheKey)
            ?: "rss:local:${episodeId.toLong().absoluteValue}"
        return CurrentPlaybackContextEntity(
            episodeCacheKey = cacheKey,
            pinePodsEpisodeId = cached?.pinePodsEpisodeId ?: episodeId.takeIf { it > 0 },
            podcastId = cached?.podcastId,
            episodeTitle = title.ifBlank { cached?.title.orEmpty() },
            podcastName = podcastName.ifBlank { cached?.podcastName.orEmpty() },
            artworkUrl = artworkUrl.ifBlank { cached?.artworkUrl.orEmpty() },
            mediaUrl = cached?.mediaUrl?.takeIf(String::isNotBlank) ?: mediaUrl,
            description = description.ifBlank { cached?.description.orEmpty() },
            publishedAt = publishedAt.ifBlank { cached?.publishedAt.orEmpty() },
            positionMs = positionMs,
            durationMs = durationMs,
            completed = completed,
            updatedAtEpochMs = updatedAtEpochMs,
        )
    }

    /**
     * Shows the interrupted episode after a cold start without preparing it: no buffering over
     * a metered network, and no pause() that would swallow a Play that arrives meanwhile (P-03).
     * Play on an idle player prepares it; Play on an empty player uses [resumptionItems].
     */
    private fun restorePlaybackContext(player: ExoPlayer) {
        ioScope.launch {
            val (entry, positionMs) = itemFactory.currentEntry() ?: return@launch
            val session = sessionStore.load()
            if (!itemFactory.isPlayable(entry, session)) return@launch
            val restoredItem = itemFactory.mediaItem(entry, positionMs, session)
            playerScope.launch {
                if (player.mediaItemCount != 0) return@launch
                player.setMediaItem(restoredItem, positionMs)
            }
        }
    }

    private val foregroundObserver = object : androidx.lifecycle.DefaultLifecycleObserver {
        override fun onStart(owner: androidx.lifecycle.LifecycleOwner) = followServerNowPlaying()
    }
    private var followServerJob: Job? = null

    /**
     * Now Playing follows the listener across devices (user, 9.11.2026: the phone kept showing an
     * episode the car had finished, at the morning mark). While nothing plays here, the server's
     * history is read; when something was listened elsewhere after this device last listened, the
     * newest unfinished episode is loaded at the server position, not played. A position this device
     * has not delivered yet is newer than anything the server knows and keeps the local episode.
     */
    private fun followServerNowPlaying() {
        val player = mediaLibrarySession?.player ?: return
        if (player.isPlaying || followServerJob?.isActive == true) return
        val currentId = player.currentMediaItem?.mediaId?.toIntOrNull()
        followServerJob = playerScope.launch(Dispatchers.IO) {
            // After a cold start the restored item arrives a moment later.
            delay(FOLLOW_SERVER_DELAY_MS)
            val session = sessionStore.load() ?: return@launch
            val context = database.offlineCacheDao().getCurrentPlaybackContext()
            val localId = currentId ?: context?.pinePodsEpisodeId
            val local = localId?.takeIf { it > 0 }?.let { appGraph.syncRepository.progress(it) }
            val rows = runCatching { controlReadApi.listened(session) }.getOrNull() ?: return@launch
            val chosen = ru.e1173.nookast.core.sync.ServerNowPlaying.choose(
                rows,
                localListenedAtEpochMs = context?.updatedAtEpochMs ?: 0L,
                localDirty = local?.dirty == true,
            ) ?: return@launch
            val entry = itemFactory.findEntry(chosen.episodeId) ?: chosen.toEntry()
            if (!itemFactory.isPlayable(entry, session)) return@launch
            val positionMs = chosen.positionSeconds * 1_000
            val item = itemFactory.mediaItem(entry, positionMs, session)
            withContext(Dispatchers.Main) {
                if (player.isPlaying) return@withContext
                val same = player.currentMediaItem?.mediaId == item.mediaId
                if (same && (player.currentPosition - positionMs).absoluteValue < FOLLOW_SERVER_TOLERANCE_MS) return@withContext
                journal.record(ControlJournal.Category.PLAYER) { "follow server episode=${chosen.episodeId} same=$same" }
                if (same) player.seekTo(positionMs) else player.setMediaItem(item, positionMs)
                persistPlaybackContext(completed = false)
            }
        }
    }

    /**
     * Seek back and forward by the intervals from Settings (plan 4.2) in the notification, on the
     * lock screen and in car shells that show media button preferences.
     * The icon is the app's own seek drawable, identical to the in-app buttons (user, 26.10.2026);
     * the platform icon stays as the fallback for shells that only know the standard set.
     */
    private fun seekButtons(): List<CommandButton> = listOf(
        CommandButton.Builder(seekBackIcon(playbackPreferences.seekBackSeconds))
            .setCustomIconResId(seekIconRes(forward = false, playbackPreferences.seekBackSeconds))
            .setSessionCommand(SEEK_BACK_COMMAND)
            .setDisplayName(getString(R.string.seek_back_seconds, playbackPreferences.seekBackSeconds))
            .setSlots(CommandButton.SLOT_BACK)
            .build(),
        CommandButton.Builder(seekForwardIcon(playbackPreferences.seekForwardSeconds))
            .setCustomIconResId(seekIconRes(forward = true, playbackPreferences.seekForwardSeconds))
            .setSessionCommand(SEEK_FORWARD_COMMAND)
            .setDisplayName(getString(R.string.seek_forward_seconds, playbackPreferences.seekForwardSeconds))
            .setSlots(CommandButton.SLOT_FORWARD)
            .build(),
    )

    private fun MediaSession.ControllerInfo.isOwnApp(): Boolean =
        packageName == this@PlaybackService.packageName && uid == android.os.Process.myUid()

    private fun <T> ioFuture(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        libraryScope.launch {
            runCatching { block() }
                .onSuccess { future.set(it) }
                .onFailure { future.setException(it) }
        }
        return future
    }

    private fun libraryTitles() = MediaLibraryTree.FolderTitles(
        root = getString(R.string.library_root),
        continueListening = getString(R.string.library_continue),
        queue = getString(R.string.library_queue),
        downloads = getString(R.string.library_downloads),
    )

    /** Children of a library node, or null for an unknown node. */
    private suspend fun libraryChildren(parentId: String): List<MediaItem>? {
        val entries = when (parentId) {
            MediaLibraryTree.ROOT_ID -> return MediaLibraryTree.rootChildren(libraryTitles())
            MediaLibraryTree.RECENT_ROOT_ID, MediaLibraryTree.CONTINUE_ID ->
                listOfNotNull(itemFactory.currentEntry()?.first)
            MediaLibraryTree.QUEUE_ID -> itemFactory.queueEntries()
            MediaLibraryTree.DOWNLOADS_ID -> itemFactory.downloadedEntries()
            else -> return null
        }
        val session = sessionStore.load()
        return entries
            .filter { itemFactory.isPlayable(it, session) }
            .map { MediaLibraryTree.episodeItem(it, ArtworkContentProvider::uriFor) }
    }

    /** Rebuilds requested items from Room by ID; controller-supplied URIs are ignored. */
    private suspend fun resolvePlayableItems(requested: List<MediaItem>): List<MediaItem> {
        val session = sessionStore.load()
        return requested.mapNotNull { item ->
            val entry = item.mediaId.toIntOrNull()?.let { itemFactory.findEntry(it) }
                ?.takeIf { itemFactory.isPlayable(it, session) }
                ?: return@mapNotNull null
            itemFactory.mediaItem(entry, itemFactory.resumePositionMs(entry), session)
        }
    }

    private suspend fun resolvePlaybackRequest(
        requested: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): MediaSession.MediaItemsWithStartPosition {
        val session = sessionStore.load()
        val requestedEntries = requested.mapNotNull { item ->
            item.mediaId.toIntOrNull()?.let { itemFactory.findEntry(it) }
                ?.takeIf { itemFactory.isPlayable(it, session) }
        }
        if (requestedEntries.isEmpty()) {
            throw UnsupportedOperationException("No locally known playable episode requested")
        }
        val queue = itemFactory.queueEntries().filter { itemFactory.isPlayable(it, session) }
        val (entries, expandedIndex) = MediaLibraryTree.expandToQueue(requestedEntries, queue) { a, b ->
            a.episodeId == b.episodeId
        }
        val index = when {
            entries !== requestedEntries -> expandedIndex
            startIndex == C.INDEX_UNSET -> 0
            else -> startIndex.coerceIn(0, entries.lastIndex)
        }
        val items = entries.map { entry ->
            itemFactory.mediaItem(entry, itemFactory.resumePositionMs(entry), session)
        }
        val position = if (startPositionMs == C.TIME_UNSET || entries !== requestedEntries) {
            items[index].mediaMetadata.extras
                ?.getLong(PlaybackViewModel.EXTRA_RESUME_POSITION_MS, 0) ?: 0
        } else {
            startPositionMs
        }
        return MediaSession.MediaItemsWithStartPosition(items, index, position)
    }

    /** What Play resumes after the process died: the interrupted episode, within its queue. */
    private suspend fun resumptionItems(): MediaSession.MediaItemsWithStartPosition {
        val session = sessionStore.load()
        val (current, positionMs) = itemFactory.currentEntry()
            ?.takeIf { itemFactory.isPlayable(it.first, session) }
            ?: run {
                val first = itemFactory.queueEntries()
                    .firstOrNull { itemFactory.isPlayable(it, session) }
                    ?: throw UnsupportedOperationException("Nothing to resume")
                first to itemFactory.resumePositionMs(first)
            }
        val queue = itemFactory.queueEntries().filter { itemFactory.isPlayable(it, session) }
        val (entries, index) = MediaLibraryTree.expandToQueue(listOf(current), queue) { a, b ->
            a.episodeId == b.episodeId
        }
        val items = entries.mapIndexed { itemIndex, entry ->
            val resume = if (itemIndex == index) positionMs else itemFactory.resumePositionMs(entry)
            itemFactory.mediaItem(entry, resume, session)
        }
        return MediaSession.MediaItemsWithStartPosition(items, index, positionMs)
    }

    private companion object {
        const val RESUME_AFTER_IGNITION_MS = 12 * 60 * 60 * 1_000L
        const val PAUSE_BEFORE_DARK_MS = 10_000L
        const val SESSION_ID = "nookast-playback"
        const val PROGRESS_INTERVAL_MS = 5_000L

        /** A pause longer than this starts a new playback session on resume. */
        const val LONG_PAUSE_MS = 60_000L
        const val FINAL_WRITE_TIMEOUT_MS = 1_500L
        const val MAX_RECOVERY_ATTEMPTS = 8
        const val PINEPODS_API_KEY_HEADER = "Api-Key"
    }
}

/** Controller identity for the control journal: package, uid and trust, never user data. */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
private fun describeController(controller: MediaSession.ControllerInfo): String =
    "${controller.packageName} uid=${controller.uid} trusted=${controller.isTrusted}"

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
private fun describeParams(params: LibraryParams?): String =
    if (params == null) {
        "params=null"
    } else {
        "recent=${params.isRecent} offline=${params.isOffline} suggested=${params.isSuggested} " +
            "extras=${ControlJournalFormat.bundleKeys(params.extras)}"
    }

private data class PlaybackContextSnapshot(
    val episodeId: Int,
    val title: String,
    val podcastName: String,
    val artworkUrl: String,
    val mediaUrl: String,
    val description: String,
    val publishedAt: String,
    val positionMs: Long,
    val durationMs: Long?,
    val completed: Boolean,
    val updatedAtEpochMs: Long,
)

internal fun restorablePlaybackEpisodeId(context: CurrentPlaybackContextEntity): Int? {
    if (context.completed) return null
    return context.pinePodsEpisodeId
        ?: runCatching { rssLocalPlaybackId(context.episodeCacheKey) }.getOrNull()
}

private fun seekPlayerBy(player: Player, deltaMs: Long) {
    val target = (player.currentPosition + deltaMs).coerceAtLeast(0)
    val durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
    player.seekTo(if (durationMs != null) target.coerceAtMost(durationMs) else target)
}

/** The URI without [name]: keeps the stream key out of player errors and logs. */
private fun Uri.withoutQueryParameter(name: String): Uri {
    if (getQueryParameter(name) == null) return this
    val builder = buildUpon().clearQuery()
    queryParameterNames.filter { it != name }.forEach { key ->
        getQueryParameters(key).forEach { value -> builder.appendQueryParameter(key, value) }
    }
    return builder.build()
}

/** Seek by the Settings intervals as session commands: System UI shows only those in its slots. */
private const val FOLLOW_SERVER_DELAY_MS = 1_500L
private const val FOLLOW_SERVER_TOLERANCE_MS = 15_000L

private const val SEEK_BACK_ACTION = "ru.e1173.nookast.SEEK_BACK"
private const val SEEK_FORWARD_ACTION = "ru.e1173.nookast.SEEK_FORWARD"
private val SEEK_BACK_COMMAND by lazy { SessionCommand(SEEK_BACK_ACTION, Bundle.EMPTY) }
private val SEEK_FORWARD_COMMAND by lazy { SessionCommand(SEEK_FORWARD_ACTION, Bundle.EMPTY) }

/** The closest platform icon for a seek interval; other values get the generic one. */
internal fun seekBackIcon(seconds: Int): Int = when (seconds) {
    5 -> CommandButton.ICON_SKIP_BACK_5
    10 -> CommandButton.ICON_SKIP_BACK_10
    15 -> CommandButton.ICON_SKIP_BACK_15
    30 -> CommandButton.ICON_SKIP_BACK_30
    else -> CommandButton.ICON_SKIP_BACK
}

internal fun seekForwardIcon(seconds: Int): Int = when (seconds) {
    5 -> CommandButton.ICON_SKIP_FORWARD_5
    10 -> CommandButton.ICON_SKIP_FORWARD_10
    15 -> CommandButton.ICON_SKIP_FORWARD_15
    30 -> CommandButton.ICON_SKIP_FORWARD_30
    else -> CommandButton.ICON_SKIP_FORWARD
}
