package ru.e1173.nookast.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.chrisbanes.haze.hazeSource
import androidx.compose.runtime.CompositionLocalProvider
import ru.e1173.nookast.designsystem.underSky
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.util.Locale
import ru.e1173.nookast.R
import ru.e1173.nookast.auth.LoginStage
import ru.e1173.nookast.auth.LoginUiState
import ru.e1173.nookast.auth.LoginViewModel
import ru.e1173.nookast.diagnostics.DeviceSnapshot
import ru.e1173.nookast.player.PlaybackViewModel
import ru.e1173.nookast.preferences.AppThemeMode
import ru.e1173.nookast.preferences.DisplayPreferencesState
import ru.e1173.nookast.preferences.InterfaceProfile
import ru.e1173.nookast.preferences.PodcastSortMode
import ru.e1173.nookast.preferences.PodcastViewMode
import ru.e1173.nookast.preferences.resolveInterfacePresentation
import androidx.activity.compose.BackHandler

private enum class ServerProtocol(
    val scheme: String,
    val label: String,
) {
    HTTPS("https", "HTTPS"),
    HTTP("http", "HTTP"),
}

private val ConnectionPanelShape = RoundedCornerShape(24.dp)
private val ConnectionFieldShape = RoundedCornerShape(14.dp)

@Composable
fun NookastRoot(
    snapshot: DeviceSnapshot,
    viewModel: LoginViewModel,
    queueViewModel: QueueViewModel,
    playbackViewModel: PlaybackViewModel,
    displayPreferences: DisplayPreferencesState,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onInterfaceProfileChanged: (InterfaceProfile) -> Unit,
    onPodcastSortModeChanged: (PodcastSortMode) -> Unit,
    onPodcastViewModeChanged: (PodcastViewMode) -> Unit,
    onPodcastDescriptionCollapsedChanged: (Int, Boolean) -> Unit,
    onPodcastHidePlayedChanged: (Int, Boolean) -> Unit,
    openNowPlayingRequest: Long = 0L,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val queueState by queueViewModel.state.collectAsStateWithLifecycle()
    val playerSettingsViewModel: PlayerSettingsViewModel = viewModel()
    val playbackState by playbackViewModel.state.collectAsStateWithLifecycle()
    val syncAccount: SyncAccountViewModel = viewModel()
    val listsViewModel: EpisodeListsViewModel = viewModel()
    val listsState by listsViewModel.state.collectAsStateWithLifecycle()
    val signOutStep by syncAccount.signOut.collectAsStateWithLifecycle()
    val quarantine by syncAccount.quarantine.collectAsStateWithLifecycle()
    val signOutAndClear = {
        playbackViewModel.stopForSignOut()
        listsViewModel.clear()
        queueViewModel.clearLocalState {
            viewModel.signOut()
            syncAccount.signOutFinished()
        }
    }
    LaunchedEffect(signOutStep) {
        // Everything reached the server: sign out and clear, as before the barrier existed.
        if (signOutStep == SignOutStep.Clean) signOutAndClear()
    }
    var playerExpanded by rememberSaveable { mutableStateOf(false) }
    var upNextExpanded by rememberSaveable { mutableStateOf(false) }
    var stagedNowPlayingEntry by remember { mutableStateOf<ru.e1173.nookast.data.local.QueueListEntry?>(null) }
    var handledOpenNowPlayingRequest by rememberSaveable { mutableLongStateOf(0L) }
    // Plan 5.1: one back stack per tab instead of loose flags (U-11).
    val navigator = rememberAppNavigator()
    val selectedPodcastId = (navigator.stack(AppDestination.PODCASTS).last() as? Route.Podcast)?.podcastId
    val openPlaylistId = (navigator.top as? Route.Playlist)?.playlistId
    // Episodes opened as cards, by id; after process death the card finds its episode in the lists.
    val cardEntries = remember { androidx.compose.runtime.mutableStateMapOf<Int, ru.e1173.nookast.data.local.QueueListEntry>() }
    var pendingPodcastEpisodeId by rememberSaveable { mutableStateOf<Int?>(null) }
    val selectedDestination = navigator.destination
    var initialPlayerTab by rememberSaveable { mutableStateOf(NowPlayingTab.PLAYBACK) }
    val currentDensity = LocalDensity.current
    val interfacePresentation = resolveInterfacePresentation(
        profile = displayPreferences.interfaceProfile,
        automaticLargeControls = snapshot.prefersLargeControls,
        automaticFontScale = snapshot.preferredFontScale,
        systemFontScale = snapshot.fontScale,
        automaticDensityScale = snapshot.preferredDensityScale,
    )
    val interfaceDensity = Density(
        density = currentDensity.density * interfacePresentation.densityScale,
        fontScale = interfacePresentation.fontScale,
    )
    // Dialogs live in the interface density too: in the car they were drawn at system size.
    CompositionLocalProvider(
        LocalDensity provides interfaceDensity,
        ru.e1173.nookast.designsystem.LocalInterfaceDensity provides interfaceDensity,
    ) {
        SignOutDialog(
            step = signOutStep,
            onSkipWait = syncAccount::skipFlush,
            onKeep = {
                // The data stays with its account fingerprint; signing in to it again resumes delivery.
                playbackViewModel.stopForSignOut()
                viewModel.signOut()
                syncAccount.signOutFinished()
            },
            onDiscard = signOutAndClear,
            onCancel = syncAccount::cancelSignOut,
        )
        quarantine?.let { current ->
            AccountQuarantineDialog(
                quarantine = current,
                onSameServer = syncAccount::adoptCurrentAccount,
                onBackToPrevious = {
                    playbackViewModel.stopForSignOut()
                    viewModel.signOut()
                    syncAccount.quarantineResolved()
                },
                onDeletePrevious = {
                    playbackViewModel.stopForSignOut()
                    queueViewModel.clearLocalState {
                        syncAccount.quarantineResolved()
                        queueViewModel.refresh()
                    }
                },
            )
        }
    }

    LaunchedEffect(state.stage) {
        if (state.stage == LoginStage.CONNECTED) queueViewModel.refresh()
    }

    LaunchedEffect(openNowPlayingRequest, state.stage, playbackState.currentEpisodeId) {
        if (openNowPlayingRequest > handledOpenNowPlayingRequest &&
            state.stage == LoginStage.CONNECTED &&
            playbackState.currentEpisodeId != null
        ) {
            upNextExpanded = false
            initialPlayerTab = NowPlayingTab.PLAYBACK
            playerExpanded = true
            handledOpenNowPlayingRequest = openNowPlayingRequest
        }
    }

    LaunchedEffect(state.stage, selectedDestination) {
        if (state.stage == LoginStage.CONNECTED && selectedDestination == AppDestination.HOME) {
            queueViewModel.refreshDashboard()
        }
        if (state.stage == LoginStage.CONNECTED && selectedDestination == AppDestination.SETTINGS) {
            queueViewModel.refreshStorageStats()
        }
        if (state.stage == LoginStage.CONNECTED && selectedDestination == AppDestination.PODCASTS) {
            queueViewModel.refreshPodcastCatalogIfStale()
        }
    }

    LaunchedEffect(
        state.stage,
        queueState.entries,
        playbackState.currentEpisodeId,
        playbackState.isConnected,
    ) {
        if (state.stage == LoginStage.CONNECTED &&
            playbackState.isConnected &&
            playbackState.currentEpisodeId != null
        ) {
            playbackViewModel.synchronizeQueue(queueState.entries)
        }
    }

    LaunchedEffect(playbackState.currentEpisodeId, pendingPodcastEpisodeId) {
        playbackState.currentEpisodeId?.let(queueViewModel::loadPodcastDetails)
        pendingPodcastEpisodeId?.let(queueViewModel::loadPodcastDetails)
    }

    LaunchedEffect(pendingPodcastEpisodeId, queueState.podcastDetails) {
        pendingPodcastEpisodeId?.let { episodeId ->
            queueState.podcastDetails[episodeId]?.podcastId?.let { podcastId ->
                navigator.showTab(AppDestination.PODCASTS)
                navigator.push(Route.Podcast(podcastId))
                pendingPodcastEpisodeId = null
                queueViewModel.loadPodcastDetailsById(podcastId)
            }
        }
    }

    LaunchedEffect(selectedPodcastId) {
        selectedPodcastId?.let(queueViewModel::loadPodcastDetailsById)
    }

    LaunchedEffect(state.stage) {
        if (state.stage != LoginStage.CONNECTED) {
            playerExpanded = false
            upNextExpanded = false
            navigator.reset()
            pendingPodcastEpisodeId = null
            stagedNowPlayingEntry = null
        }
    }

    LaunchedEffect(queueState.entries, stagedNowPlayingEntry?.episodeId) {
        val stagedId = stagedNowPlayingEntry?.episodeId
        if (stagedId != null && queueState.entries.any { it.episodeId == stagedId }) {
            stagedNowPlayingEntry = null
        }
    }

    val podcastArtworkByName = remember(queueState.podcasts) { podcastArtworkByName(queueState.podcasts) }
    // The automatic place mode looks again now and then while the app is open (user, 2.11.2026: a
    // trip to another city); only with the permission, which can be taken back at any time.
    val skyContext = androidx.compose.ui.platform.LocalContext.current
    val skyPreferences = androidx.lifecycle.viewmodel.compose.viewModel<ru.e1173.nookast.preferences.AppPreferencesViewModel>()
    LaunchedEffect(displayPreferences.skyPlaceMode) {
        if (displayPreferences.skyPlaceMode != ru.e1173.nookast.preferences.SkyPlaceMode.AUTO) return@LaunchedEffect
        while (true) {
            val known = skyPreferences.state.value.skyPlace
            val stale = known == null || System.currentTimeMillis() - known.fixedAtEpochMs >= ru.e1173.nookast.preferences.SkyLocator.AUTO_REFRESH_MS
            if (stale && ru.e1173.nookast.preferences.SkyLocator.hasPermission(skyContext)) {
                ru.e1173.nookast.preferences.SkyLocator.locate(skyContext)?.let(skyPreferences::setSkyPlace)
            }
            kotlinx.coroutines.delay(60 * 60 * 1_000L)
        }
    }
    // Sky moods (morning, white nights, evening) follow the sun over the saved place, once a minute;
    // without a place there is no sky (user, 2.11.2026: no default city).
    val skyPlace = displayPreferences.skyPlace
    val skyState by androidx.compose.runtime.produceState(
        initialValue = ru.e1173.nookast.designsystem.SkyState.None,
        displayPreferences.skyMorning,
        displayPreferences.skyWhiteNights,
        displayPreferences.skyEvening,
        skyPlace,
    ) {
        while (true) {
            value = if (skyPlace == null) {
                ru.e1173.nookast.designsystem.SkyState.None
            } else {
                ru.e1173.nookast.designsystem.skyState(
                    java.time.ZonedDateTime.now(),
                    skyPlace.latitude,
                    skyPlace.longitude,
                    morning = displayPreferences.skyMorning,
                    whiteNights = displayPreferences.skyWhiteNights,
                    evening = displayPreferences.skyEvening,
                )
            }
            kotlinx.coroutines.delay(60_000)
        }
    }
    val effectiveSky = skyState
    val skyColors = ru.e1173.nookast.designsystem.LocalNookastColors.current.underSky(effectiveSky)
    // Season and episode numbers of the feeds for every list (user, 29.10.2026).
    val numbersContext = androidx.compose.ui.platform.LocalContext.current
    val episodeNumberRows by remember(numbersContext) {
        ru.e1173.nookast.data.local.SyncDatabase.open(numbersContext).offlineCacheDao().observeEpisodeNumbers()
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val episodeNumbers = remember(episodeNumberRows) { EpisodeNumbers.from(episodeNumberRows) }
    // Screens run under the status bar and pad themselves by it (user, 29.10.2026).
    val topInsetPx = WindowInsets.safeDrawing.getTop(LocalDensity.current)
    val topInset = with(interfaceDensity) { topInsetPx.toDp() }
    // The glass of the whole app blurs this base colour too (user, 31.10.2026).
    val rootBackground = if (
        state.stage == LoginStage.CONNECTED &&
        selectedDestination == AppDestination.PODCASTS
    ) {
        if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            PodcastDarkBackground
        } else {
            PodcastLightBackground
        }
    } else {
        MaterialTheme.colorScheme.background
    }
    CompositionLocalProvider(
        LocalTopInset provides topInset,
        LocalGlassVeil provides rootBackground,
        LocalPlaybackPosition provides playbackViewModel.position.collectAsStateWithLifecycle(),
        ru.e1173.nookast.designsystem.LocalNookastColors provides skyColors,
        LocalDensity provides interfaceDensity,
        ru.e1173.nookast.designsystem.LocalInterfaceDensity provides interfaceDensity,
        ru.e1173.nookast.designsystem.LocalNewYear provides ru.e1173.nookast.designsystem.newYearActive(
            displayPreferences.newYearMode,
            java.time.LocalDate.now(),
            winterTheme = displayPreferences.palette == ru.e1173.nookast.designsystem.ThemePalette.WINTER,
        ),
        LocalPodcastArtworkByName provides podcastArtworkByName,
        LocalEpisodeNumbers provides episodeNumbers,
        ru.e1173.nookast.designsystem.LocalSkyState provides effectiveSky,
    ) {
        Surface(
            // Morning and white nights lie over every screen, Now Playing and its cover wash
            // included, instead of behind the screens' own backgrounds (user, 28.10.2026).
            modifier = Modifier.fillMaxSize().then(ru.e1173.nookast.designsystem.skyOverlay()),
            color = rootBackground,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            androidx.compose.foundation.layout.WindowInsetsSides.Horizontal +
                                androidx.compose.foundation.layout.WindowInsetsSides.Bottom,
                        ),
                    ),
            ) {
                val wide = maxWidth >= 700.dp
                // «Updated to X» once after the app replaced itself (user, 2.11.2026).
                UpdatedNotice()
                UpdatePrompt()
                val hazeState = dev.chrisbanes.haze.rememberHazeState()
                val largeControls = interfacePresentation.largeControls
                val touchTarget = if (largeControls) 72.dp else 52.dp
                // Phone mockups intentionally use almost the full width.  The old
                // 20 dp inset was applied before every screen added its own inset,
                // producing the visibly narrow "card inside a card" layout.
                val contentPadding = when {
                    largeControls -> 18.dp
                    wide -> 12.dp
                    else -> 12.dp
                }
                // Wide screens keep side margins on every screen (APK-3.5 review: Podcasts lost them).
                // Podcasts and Settings split a wide screen into panes that carry their own insets.
                val splitScreen = state.stage == LoginStage.CONNECTED && wide &&
                    selectedDestination in setOf(AppDestination.PODCASTS, AppDestination.SETTINGS)
                // Screens with a sticky header draw their own glass up under the status bar.
                val ownTopBar = state.stage == LoginStage.CONNECTED && !wide && (
                    selectedDestination == AppDestination.SETTINGS ||
                        selectedDestination == AppDestination.HOME && openPlaylistId != null ||
                        selectedDestination == AppDestination.PODCASTS && selectedPodcastId != null
                    )
                val screenHorizontalPadding = if (splitScreen || ownTopBar) {
                    0.dp
                } else if (state.stage == LoginStage.CONNECTED && wide) {
                    24.dp
                } else if (
                    state.stage == LoginStage.CONNECTED &&
                    selectedDestination == AppDestination.HOME &&
                    !largeControls
                ) {
                    7.dp
                } else {
                    contentPadding
                }
                val screenVerticalPadding = if (
                    splitScreen ||
                    state.stage == LoginStage.CONNECTED &&
                    selectedDestination in setOf(AppDestination.PODCASTS, AppDestination.FEED)
                ) {
                    0.dp
                } else {
                    contentPadding
                }
                val navigationBarHeight = if (largeControls) 64.dp else 57.dp
                val currentEpisodeId = playbackState.currentEpisodeId
                    ?: stagedNowPlayingEntry?.episodeId
                // With nothing playing the bar shows the queue instead (user, 31.10.2026: Up Next could only be
                // reached from the mini player).
                val miniBarShown = currentEpisodeId != null ||
                    // The Up Next tab is the queue itself.
                    queueState.entries.isNotEmpty() && selectedDestination != AppDestination.QUEUE
                val currentEntry = queueState.entries.firstOrNull {
                    it.episodeId == currentEpisodeId
                } ?: stagedNowPlayingEntry?.takeIf { it.episodeId == currentEpisodeId }
                // An episode picked in Up Next while another plays (user, 3.11.2026: usually one wants to look at
                // it first): Now Playing shows it without stopping what plays, until its Play or a return.
                var previewEntry by remember { mutableStateOf<ru.e1173.nookast.data.local.QueueListEntry?>(null) }
                LaunchedEffect(playbackState.currentEpisodeId) {
                    if (previewEntry?.episodeId == playbackState.currentEpisodeId) previewEntry = null
                }
                // Closing Now Playing ends the preview.
                LaunchedEffect(playerExpanded) { if (!playerExpanded) previewEntry = null }
                val pickFromQueue: (Int) -> Unit = { index ->
                    val entry = queueState.entries.getOrNull(index)
                    if (playbackState.isPlaying && entry != null && entry.episodeId != playbackState.currentEpisodeId) {
                        previewEntry = entry
                        upNextExpanded = false
                        playerExpanded = true
                    } else {
                        previewEntry = null
                        stagedNowPlayingEntry = entry
                        playbackViewModel.prepareQueue(queueState.entries, index)
                    }
                }
                val podcastDetails = currentEpisodeId?.let(queueState.podcastDetails::get)
                val podcastDetailsLoading = currentEpisodeId?.let {
                    it in queueState.podcastDetailsLoading
                } == true
                val currentEpisodeSaved = currentEpisodeId?.let { episodeId ->
                    queueState.savedEpisodeOverrides[episodeId]
                        ?: queueState.dashboard?.savedEpisodes?.any {
                            it.episodeId == episodeId
                        }
                } ?: false
                // The preview's own facts on the Now Playing screen; what plays keeps playing.
                val shownState = previewEntry?.let { preview ->
                    playbackState.copy(
                        currentEpisodeId = preview.episodeId,
                        title = preview.episodeTitle.orEmpty(),
                        podcastName = preview.podcastName.orEmpty(),
                        artworkUrl = preview.artworkUrl.orEmpty(),
                        isPlaying = false,
                        isBuffering = false,
                        positionMs = ((preview.listenDurationSeconds ?: 0.0) * 1_000).toLong(),
                        durationMs = (preview.durationSeconds ?: 0) * 1_000L,
                        bufferedAheadMs = 0L,
                        // The stream facts belong to what plays, not to the preview.
                        streamReachability = if (preview.downloaded == true) ru.e1173.nookast.player.StreamReachability.LOCAL else ru.e1173.nookast.player.StreamReachability.AVAILABLE,
                        streamHost = "",
                        isUsingPinePodsFallback = false,
                        isServerCopySource = false,
                        serverDownload = null,
                        originStalled = false,
                        isLocalSource = preview.downloaded == true,
                        isMarkedPlayed = preview.completed == true,
                        mediaSizeBytes = null,
                        bitrateKbps = null,
                        nativeChapters = emptyList(),
                        feedChapters = emptyList(),
                        error = null,
                    )
                } ?: playbackState
                val shownEpisodeId = previewEntry?.episodeId ?: currentEpisodeId
                val shownDetails = shownEpisodeId?.let(queueState.podcastDetails::get)
                val shownDetailsLoading = shownEpisodeId?.let { it in queueState.podcastDetailsLoading } == true
                val shownSaved = previewEntry?.let { preview ->
                    queueState.savedEpisodeOverrides[preview.episodeId]
                        ?: queueState.dashboard?.savedEpisodes?.any { it.episodeId == preview.episodeId }
                        ?: false
                } ?: currentEpisodeSaved

                // Content runs under the navigation bar and the mini-player, which blur it like
                // Telegram does (APK-3.6 review: an opaque band cut the list off). The space they
                // take is added inside the scroll or handed to lazy lists as LocalBottomInset.
                val bottomInset = if (
                    state.stage == LoginStage.CONNECTED &&
                    selectedDestination != AppDestination.SETTINGS
                ) {
                    navigationBarHeight + if (miniBarShown) {
                        if (largeControls) 96.dp else 78.dp
                    } else {
                        8.dp
                    }
                } else {
                    0.dp
                }
                val lazyScreen = splitScreen ||
                    (selectedDestination == AppDestination.HOME && openPlaylistId != null) ||
                    state.stage == LoginStage.CONNECTED &&
                    selectedDestination in setOf(AppDestination.PODCASTS, AppDestination.FEED, AppDestination.SETTINGS)
                var contentModifier = Modifier
                    .fillMaxWidth()
                    .hazeSource(hazeState)
                    .padding(horizontal = screenHorizontalPadding)
                // The content scrolls under the status bar, so the top gap is inside the scroll.
                contentModifier = if (lazyScreen) {
                    contentModifier.fillMaxSize()
                } else {
                    contentModifier.fillMaxSize().verticalScroll(rememberScrollState(), flingBehavior = deviceFling()).padding(
                        top = topInset + screenVerticalPadding,
                        bottom = bottomInset + screenVerticalPadding,
                    )
                }
                // Each route's frame (plan 5.1): wide screens keep side margins, Podcasts and Settings
                // split into panes with their own insets, screens with a sticky header own the width.
                val routeFrame: (Route) -> RouteFrame = { route ->
                    val destination = route.destination(navigator.tab)
                    val split = wide && route !is Route.Episode &&
                        destination in setOf(AppDestination.PODCASTS, AppDestination.SETTINGS)
                    val ownBar = route is Route.Episode || route is Route.Queue || route is Route.Search || route is Route.Playlist ||
                        !wide && (route is Route.Settings || route is Route.Podcast)
                    RouteFrame(
                        lazy = split || route is Route.Playlist || route is Route.Episode || route is Route.Queue || route is Route.Search ||
                            destination in setOf(AppDestination.PODCASTS, AppDestination.FEED, AppDestination.SETTINGS),
                        horizontalPadding = when {
                            split || ownBar -> 0.dp
                            wide -> 24.dp
                            destination == AppDestination.HOME && !largeControls -> 7.dp
                            else -> contentPadding
                        },
                        verticalPadding = if (split || destination in setOf(AppDestination.PODCASTS, AppDestination.FEED)) 0.dp else contentPadding,
                        bottomInset = if (route is Route.Settings) 0.dp else navigationBarHeight + if (miniBarShown) {
                            if (largeControls) 96.dp else 78.dp
                        } else {
                            8.dp
                        },
                        ownTopBar = ownBar,
                    )
                }
                val routeContent: @Composable (Route, @Composable (Route) -> Unit) -> Unit = { route, home ->
                    if (route is Route.Episode) {
                        EpisodeCardRoute(
                            episodeId = route.episodeId,
                            cardEntries = cardEntries,
                            queueState = queueState,
                            listsState = listsState,
                            playbackState = playbackState,
                            wide = wide,
                            largeControls = largeControls,
                            navigator = navigator,
                            queueViewModel = queueViewModel,
                            playbackViewModel = playbackViewModel,
                        )
                    } else if (route is Route.Search) {
                        // The library marks what is already added; it may not be loaded yet on a fresh start.
                        LaunchedEffect(Unit) { queueViewModel.refreshPodcastCatalogIfStale() }
                        PodcastSearchScreen(
                            subscriptions = queueState.podcasts,
                            largeControls = largeControls,
                            onBack = { navigator.pop() },
                            onAdded = queueViewModel::refreshPodcastCatalog,
                            onOpenPodcast = { podcastId ->
                                navigator.showTab(AppDestination.PODCASTS)
                                if (wide) navigator.replaceTop(Route.Podcast(podcastId)) else navigator.push(Route.Podcast(podcastId))
                                queueViewModel.loadPodcastDetailsById(podcastId)
                            },
                        )
                    } else {
                        home(route)
                    }
                }
                if (state.stage == LoginStage.CONNECTED) {
                    val connectedHome: @Composable (Route) -> Unit = { route ->
                        ConnectedHome(
                            destination = route.destination(navigator.tab),
                            loginState = state,
                            queueState = queueState,
                            playbackState = playbackState,
                            wide = wide,
                            touchTarget = touchTarget,
                            largeControls = largeControls,
                            onRefresh = queueViewModel::refresh,
                            onRefreshDashboard = queueViewModel::refreshDashboard,
                            onRefreshFeed = listsViewModel::refreshFeed,
                            listsState = listsState,
                            onTogglePlayback = playbackViewModel::togglePlayPause,
                            queueContent = {
                                UpNextScreen(
                                    playbackState = playbackState,
                                    queueState = queueState,
                                    largeControls = largeControls,
                                    onBack = null,
                                    onPlayEpisode = { index ->
                                        pickFromQueue(index)
                                        initialPlayerTab = NowPlayingTab.PLAYBACK
                                        playerExpanded = true
                                    },
                                    onMoveEpisode = queueViewModel::moveInQueueTo,
                                    onRemoveEpisode = queueViewModel::removeFromQueue,
                                    onDownloadEpisode = queueViewModel::downloadEpisode,
                                    onDeleteDownloadEpisode = queueViewModel::removeEpisodeDownload,
                                    onMarkEpisodePlayed = queueViewModel::markEpisodePlayed,
                                )
                            },
                            openPlaylistId = (route as? Route.Playlist)?.playlistId,
                            onOpenPlaylist = { id -> if (id == null) navigator.popWhile { it is Route.Playlist } else navigator.push(Route.Playlist(id)) },
                            hiddenPlaylistIds = displayPreferences.hiddenDashboardPlaylists,
                            onLoadPlaylist = listsViewModel::loadPlaylist,
                            feedEpisodeCount = displayPreferences.feedEpisodeCount,
                            onOpenSettings = { navigator.push(Route.Settings) },
                            onOpenSearch = { navigator.push(Route.Search) },
                            onPlayEpisode = { index ->
                                playbackViewModel.playQueue(queueState.entries, index)
                            },
                            onMoveEpisode = queueViewModel::moveInQueue,
                            onRemoveEpisode = queueViewModel::removeFromQueue,
                            onDownloadEpisode = queueViewModel::downloadEpisode,
                            onRemoveEpisodeDownload = queueViewModel::removeEpisodeDownload,
                            onDownloadWholeEpisodeChanged =
                                playerSettingsViewModel::setDownloadWholeEpisodeOnPlayback,
                            onAheadCacheChanged = playerSettingsViewModel::setAheadCacheMegabytes,
                            onSeekBackSecondsChanged = playbackViewModel::setSeekBackSeconds,
                            onSeekForwardSecondsChanged = playbackViewModel::setSeekForwardSeconds,
                            onMediaButtonProfileChanged = playerSettingsViewModel::setMediaButtonProfileMode,
                            onPlaybackStartQueuePositionChanged =
                                playerSettingsViewModel::setPlaybackStartQueuePosition,
                            onManualQueuePositionChanged = playerSettingsViewModel::setManualQueuePosition,
                            onQueueSwipeStartActionChanged =
                                playerSettingsViewModel::setQueueSwipeStartAction,
                            onQueueSwipeEndActionChanged =
                                playerSettingsViewModel::setQueueSwipeEndAction,
                            onClearAheadCache = queueViewModel::requestAheadCacheClear,
                            themeMode = displayPreferences.themeMode,
                            interfaceProfile = displayPreferences.interfaceProfile,
                            onThemeModeChanged = onThemeModeChanged,
                            onInterfaceProfileChanged = onInterfaceProfileChanged,
                            selectedPodcastId = (route as? Route.Podcast)?.podcastId,
                            podcastSortMode = displayPreferences.podcastSortMode,
                            podcastViewMode = displayPreferences.podcastViewMode,
                            collapsedPodcastDescriptions =
                                displayPreferences.collapsedPodcastDescriptions,
                            hidePlayedPodcastIds = displayPreferences.hidePlayedPodcastIds,
                            onPodcastSortModeChanged = onPodcastSortModeChanged,
                            onPodcastViewModeChanged = onPodcastViewModeChanged,
                            onPodcastDescriptionCollapsedChanged =
                                onPodcastDescriptionCollapsedChanged,
                            onPodcastHidePlayedChanged = onPodcastHidePlayedChanged,
                            onRefreshPodcasts = queueViewModel::refreshPodcastCatalog,
                            onLoadPodcast = queueViewModel::loadPodcastDetailsById,
                            onOpenPodcast = { podcastId ->
                                navigator.showTab(AppDestination.PODCASTS)
                                // Wide: the right pane shows one podcast, so a tap replaces it and Back
                                // leaves the tab instead of reopening the first podcast (U-03).
                                if (wide) navigator.replaceTop(Route.Podcast(podcastId)) else navigator.push(Route.Podcast(podcastId))
                                queueViewModel.loadPodcastDetailsById(podcastId)
                            },
                            onBackFromPodcast = { navigator.pop() },
                            onPlayPodcastEpisodes = { entries, index ->
                                queueViewModel.addStartedEpisodeToQueue(entries.getOrNull(index))
                                playbackViewModel.playQueue(entries, index)
                                stagedNowPlayingEntry = null
                                initialPlayerTab = NowPlayingTab.PLAYBACK
                                playerExpanded = true
                            },
                            // A tap opens the episode card; what plays goes on playing (user, 30.10.2026).
                            onOpenPodcastEpisode = { entries, index ->
                                entries.getOrNull(index)?.let { entry ->
                                    cardEntries[entry.episodeId] = entry
                                    queueViewModel.loadPodcastDetails(entry.episodeId)
                                    navigator.push(Route.Episode(entry.episodeId))
                                }
                            },
                            onMarkPodcastEpisodesPlayed = { entries ->
                                queueViewModel.markEpisodesPlayed(entries)
                            },
                            onMarkPodcastEpisodesUnplayed = { entries ->
                                queueViewModel.markEpisodesUnplayed(entries)
                            },
                            onAddPodcastEpisodeToQueue = queueViewModel::addEpisodeToQueue,
                            onAddPodcastEpisodesToQueueCommitted =
                                queueViewModel::addEpisodesToQueue,
                            onMarkPodcastEpisodesPlayedCommitted =
                                queueViewModel::markEpisodesPlayed,
                            onMarkPodcastEpisodesUnplayedCommitted =
                                queueViewModel::markEpisodesUnplayed,
                            onChangeFeedUrl = queueViewModel::changeFeedUrl,
                            onUnsubscribePodcast = { podcastId ->
                                queueViewModel.unsubscribePodcast(podcastId)
                                navigator.popWhile { it is Route.Podcast }
                            },
                            onCloseSettings = { navigator.pop() },
                            onRunDiagnostics = playbackViewModel::runExtendedDiagnostics,
                            onSignOut = {
                                // SYNC.md §10.2: deliver first, then let the user decide about the rest.
                                playbackViewModel.pauseForSignOut()
                                syncAccount.beginSignOut()
                            },
                        )
                    }
                    AppNavHost(
                        navigator = navigator,
                        hazeState = hazeState,
                        frame = { route -> routeFrame(route) },
                        // Wide Podcasts: the library and the open podcast are one split screen, so picking a
                        // podcast changes only its pane (user, 31.10.2026: the whole screen was redrawn).
                        contentKey = { route -> if (wide && route is Route.Podcast) Route.Podcasts.encode() else route.encode() },
                        content = { route -> routeContent(route, connectedHome) },
                    )
                } else {
                CompositionLocalProvider(LocalBottomInset provides if (lazyScreen) bottomInset else 0.dp) {
                Column(
                    modifier = contentModifier,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = if (largeControls) {
                                    MaterialTheme.typography.displaySmall
                                } else {
                                    MaterialTheme.typography.headlineLarge
                                },
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(R.string.app_tagline),
                                style = if (largeControls) {
                                    MaterialTheme.typography.titleLarge
                                } else {
                                    MaterialTheme.typography.titleMedium
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(Modifier.height(24.dp))

                    if (wide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            LoginCard(
                                state = state,
                                touchTarget = touchTarget,
                                onSignIn = viewModel::signIn,
                                onVerifyMfa = viewModel::verifyMfa,
                                onSignOut = viewModel::signOut,
                                modifier = Modifier.weight(1f),
                            )
                            DeviceCard(
                                snapshot = snapshot,
                                touchTarget = touchTarget,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        LoginCard(
                            state = state,
                            touchTarget = touchTarget,
                            onSignIn = viewModel::signIn,
                            onVerifyMfa = viewModel::verifyMfa,
                            onSignOut = viewModel::signOut,
                        )
                        Spacer(Modifier.height(18.dp))
                        DeviceCard(snapshot = snapshot, touchTarget = touchTarget)
                    }
                }
                }
                }

                val topBarOwned = if (state.stage == LoginStage.CONNECTED) routeFrame(navigator.top).ownTopBar else ownTopBar
                if (!topBarOwned) StatusBarGlass(hazeState)
                // Back closes the player and the queue first; the tab's stack is NavDisplay's.
                BackHandler(enabled = playerExpanded || upNextExpanded) {
                    if (upNextExpanded) upNextExpanded = false else playerExpanded = false
                }

                val showAppChrome = state.stage == LoginStage.CONNECTED &&
                    selectedDestination != AppDestination.SETTINGS &&
                    !playerExpanded && !upNextExpanded
                if (showAppChrome) {
                    AppNavigationBar(
                        selected = selectedDestination,
                        // A tab tap switches tabs; the shown tab again returns to its root, as on iOS
                        // (a smart playlist closes with Home, user 29.10.2026).
                        onSelected = navigator::selectTab,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .appGlass(hazeState)
                            .heightIn(min = navigationBarHeight),
                    )
                }

                if (state.stage == LoginStage.CONNECTED &&
                    selectedDestination != AppDestination.SETTINGS &&
                    miniBarShown
                ) {
                    if (upNextExpanded) {
                        UpNextScreen(
                            playbackState = playbackState,
                            queueState = queueState,
                            largeControls = largeControls,
                            onBack = { upNextExpanded = false },
                            onPlayEpisode = { index ->
                                pickFromQueue(index)
                                upNextExpanded = false
                                initialPlayerTab = NowPlayingTab.PLAYBACK
                                playerExpanded = true
                            },
                            onMoveEpisode = queueViewModel::moveInQueueTo,
                            onRemoveEpisode = queueViewModel::removeFromQueue,
                            onDownloadEpisode = queueViewModel::downloadEpisode,
                            onDeleteDownloadEpisode = queueViewModel::removeEpisodeDownload,
                            onMarkEpisodePlayed = queueViewModel::markEpisodePlayed,
                        )
                    }

                    if (currentEpisodeId != null) AnimatedVisibility(
                        visible = playerExpanded && !upNextExpanded,
                        // Mini-player spec §4: grows up from the mini-player on a 0.82/380 spring and
                        // sinks back on 0.86/420 with a quick 120 ms fade.
                        enter = fadeIn(tween(180)) +
                            scaleIn(
                                spring(dampingRatio = 0.82f, stiffness = 380f),
                                initialScale = 0.92f,
                                transformOrigin = TransformOrigin(0.5f, 1f),
                            ) +
                            slideInVertically(spring(dampingRatio = 0.82f, stiffness = 380f)) { it / 6 },
                        exit = fadeOut(tween(120)) +
                            scaleOut(
                                spring(dampingRatio = 0.86f, stiffness = 420f),
                                targetScale = 0.92f,
                                transformOrigin = TransformOrigin(0.5f, 1f),
                            ) +
                            slideOutVertically(spring(dampingRatio = 0.86f, stiffness = 420f)) { it / 6 },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        NowPlayingScreen(
                            playbackState = shownState,
                            queueState = queueState,
                            currentEntry = previewEntry ?: currentEntry,
                            podcastDetails = shownDetails,
                            podcastDetailsLoading = shownDetailsLoading,
                            playingElsewhere = playbackState.title.takeIf { previewEntry != null },
                            onReturnToPlaying = { previewEntry = null },
                            playingEpisodeId = playbackState.currentEpisodeId,
                            wide = wide,
                            largeControls = largeControls,
                            initialTab = initialPlayerTab,
                            onCollapse = { playerExpanded = false },
                            onOpenUpNext = { upNextExpanded = true },
                            onOpenPodcast = {
                                navigator.showTab(AppDestination.PODCASTS)
                                val localPodcastId = currentEntry?.podcastName
                                    ?.let { podcastName ->
                                        queueState.podcasts.firstOrNull { podcast ->
                                            podcast.name.trim().equals(
                                                podcastName.trim(),
                                                ignoreCase = true,
                                            )
                                        }?.podcastId
                                    }
                                if (localPodcastId != null) {
                                    if (wide) navigator.replaceTop(Route.Podcast(localPodcastId)) else navigator.push(Route.Podcast(localPodcastId))
                                    pendingPodcastEpisodeId = null
                                    queueViewModel.loadPodcastDetailsById(localPodcastId)
                                } else {
                                    pendingPodcastEpisodeId = currentEpisodeId
                                }
                                playerExpanded = false
                            },
                            onTogglePlayback = {
                                val preview = previewEntry
                                val previewIndex = preview?.let { p -> queueState.entries.indexOfFirst { it.episodeId == p.episodeId } } ?: -1
                                if (previewIndex >= 0) {
                                    previewEntry = null
                                    stagedNowPlayingEntry = preview
                                    playbackViewModel.playQueue(queueState.entries, previewIndex)
                                    return@NowPlayingScreen
                                }
                                stagedNowPlayingEntry?.let(queueViewModel::addStartedEpisodeToQueue)
                                playbackViewModel.togglePlayPause()
                            },
                            onSeekBack = {
                                if (previewEntry == null) playbackViewModel.seekBy(-playbackState.seekBackSeconds * 1_000L)
                            },
                            onSeekForward = {
                                if (previewEntry == null) playbackViewModel.seekBy(playbackState.seekForwardSeconds * 1_000L)
                            },
                            onSeekTo = { if (previewEntry == null) playbackViewModel.seekTo(it) },
                            onRunDiagnostics = playbackViewModel::runExtendedDiagnostics,
                            onDownloadToServer = playbackViewModel::downloadCurrentToServer,
                            onPlayEpisode = { index ->
                                pickFromQueue(index)
                            },
                            onMoveEpisode = queueViewModel::moveInQueueTo,
                            onRemoveEpisode = queueViewModel::removeFromQueue,
                            onDownloadEpisode = queueViewModel::downloadEpisode,
                            onDeleteDownloadEpisode = queueViewModel::removeEpisodeDownload,
                            onMarkEpisodePlayed = queueViewModel::markEpisodePlayed,
                            onDownloadCurrent = {
                                (previewEntry ?: currentEntry)?.let(queueViewModel::downloadEpisode)
                                    ?: playbackViewModel.downloadCurrentEpisode()
                            },
                            onDeleteCurrentDownload = {
                                queueViewModel.removeEpisodeDownload(previewEntry?.episodeId ?: currentEpisodeId)
                            },
                            onMarkPlayed = {
                                previewEntry?.let { preview ->
                                    queueViewModel.markEpisodePlayed(preview)
                                    previewEntry = null
                                    return@NowPlayingScreen
                                }
                                // The next queued episode takes the screen; an empty queue closes it.
                                playbackViewModel.markCurrentPlayed(
                                    queueState.entries.filter { it.episodeId != currentEpisodeId && it.completed != true },
                                )
                            },
                            onAudioOverallGainChanged = playbackViewModel::setAudioOverallGainDb,
                            onAudioBassChanged = playbackViewModel::setAudioBassDb,
                            onAudioSpeechClarityChanged = playbackViewModel::setAudioSpeechClarity,
                            isSaved = shownSaved,
                            onToggleSaved = {
                                queueViewModel.toggleSavedEpisode(
                                    episodeId = previewEntry?.episodeId ?: currentEpisodeId,
                                    currentlySaved = shownSaved,
                                )
                            },
                        )
                    }

                    AnimatedVisibility(
                        visible = !playerExpanded && !upNextExpanded,
                        enter = fadeIn(tween(220)) +
                            scaleIn(tween(280), initialScale = 0.96f) +
                            slideInVertically(tween(280)) { it / 3 },
                        exit = fadeOut(tween(160)) +
                            scaleOut(tween(220), targetScale = 0.96f) +
                            slideOutVertically(tween(220)) { it / 3 },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            // Floating card across the whole width, 8 dp from the sides on every screen (user,
                            // 29.10.2026: wider), 8 dp above the navigation.
                            .padding(start = 8.dp, end = 8.dp, bottom = navigationBarHeight + 8.dp)
                            .fillMaxWidth(),
                    ) {
                        if (currentEpisodeId != null) MiniPlayerBar(
                            state = playbackState,
                            artworkUrl = currentEntry?.artworkUrl.orEmpty()
                                .ifBlank { playbackState.artworkUrl },
                            showSeekButtons = wide || largeControls,
                            largeControls = largeControls,
                            onExpand = {
                                initialPlayerTab = NowPlayingTab.PLAYBACK
                                playerExpanded = true
                            },
                            onTogglePlayback = playbackViewModel::togglePlayPause,
                            onSeekBack = {
                                playbackViewModel.seekBy(-playbackState.seekBackSeconds * 1_000L)
                            },
                            onSeekForward = {
                                playbackViewModel.seekBy(playbackState.seekForwardSeconds * 1_000L)
                            },
                            onOpenUpNext = { upNextExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            glass = Modifier.appGlass(hazeState, veil = 0.2f),
                        )
                        else QueueBar(
                            entries = queueState.entries,
                            largeControls = largeControls,
                            onOpen = { upNextExpanded = true },
                            onPlay = { playbackViewModel.playQueue(queueState.entries, 0) },
                            modifier = Modifier.fillMaxWidth(),
                            glass = Modifier.appGlass(hazeState, veil = 0.2f),
                        )
                    }
                }
                if (state.stage == LoginStage.CONNECTED) {
                    ControlReadMessage(
                        currentEpisodeId = currentEpisodeId,
                        onSeek = playbackViewModel::seekTo,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginCard(
    state: LoginUiState,
    touchTarget: Dp,
    onSignIn: (String, String, String) -> Unit,
    onVerifyMfa: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The address, login and code survive rotation (U-09); the password lives in the ViewModel only,
    // never in saved state that could reach the disk.
    var serverProtocol by rememberSaveable { mutableStateOf(ServerProtocol.HTTPS) }
    var serverHost by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    val loginViewModel: LoginViewModel = viewModel()
    var password by loginViewModel.passwordDraft
    var mfaCode by rememberSaveable { mutableStateOf("") }
    var httpConfirmHost by rememberSaveable { mutableStateOf<String?>(null) }
    httpConfirmHost?.let { host ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { httpConfirmHost = null },
            title = { Text(stringResource(R.string.http_confirm_title)) },
            text = { Text(stringResource(R.string.http_confirm_text, host.ifBlank { "…" })) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    serverProtocol = ServerProtocol.HTTP
                    httpConfirmHost = null
                }) { Text(stringResource(R.string.http_confirm_use_http)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    serverProtocol = ServerProtocol.HTTPS
                    httpConfirmHost = null
                }) { Text(stringResource(R.string.http_confirm_keep_https)) }
            },
        )
    }

    SectionCard(modifier) {
        Text(
            text = when (state.stage) {
                LoginStage.CONNECTED -> stringResource(R.string.server_connected)
                LoginStage.MFA -> stringResource(R.string.confirm_sign_in)
                else -> stringResource(R.string.connect_server)
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(16.dp))

        when (state.stage) {
            LoginStage.CONNECTED -> {
                InfoLine(stringResource(R.string.address), state.serverUrl.orEmpty())
                InfoLine(stringResource(R.string.user), state.userId?.toString().orEmpty())
                state.message?.let { StatusMessage(stringResource(it.text, it.arg.orEmpty()), isError = false) }
                Spacer(Modifier.height(18.dp))
                OutlinedButton(
                    onClick = onSignOut,
                    shape = ConnectionFieldShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = touchTarget),
                ) {
                    Text(stringResource(R.string.disconnect_server))
                }
            }

            LoginStage.MFA -> {
                Text(
                    stringResource(R.string.mfa_instruction),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = mfaCode,
                    onValueChange = { mfaCode = it.filter(Char::isDigit).take(12) },
                    label = { Text(stringResource(R.string.verification_code)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                    shape = ConnectionFieldShape,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.message?.let { StatusMessage(stringResource(it.text, it.arg.orEmpty()), isError = true) }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val submittedCode = mfaCode
                        mfaCode = ""
                        onVerifyMfa(submittedCode)
                    },
                    enabled = mfaCode.isNotBlank(),
                    shape = ConnectionFieldShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = touchTarget),
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }

            LoginStage.SIGN_IN,
            LoginStage.LOADING -> {
                Text(
                    stringResource(R.string.login_api_key_explanation),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                ProtocolDropdown(
                    selected = serverProtocol,
                    // HTTP only after an explicit yes (plan 7.2): the password and key would travel open.
                    onSelected = { if (it == ServerProtocol.HTTP) httpConfirmHost = serverHost else serverProtocol = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = serverHost,
                    onValueChange = { input ->
                        val trimmed = input.trimStart()
                        when {
                            trimmed.startsWith("https://", ignoreCase = true) -> {
                                serverProtocol = ServerProtocol.HTTPS
                                serverHost = trimmed.substring(8)
                            }

                            trimmed.startsWith("http://", ignoreCase = true) -> {
                                // A pasted http:// address does not switch silently: it asks first.
                                serverHost = trimmed.substring(7)
                                if (serverProtocol != ServerProtocol.HTTP) httpConfirmHost = serverHost
                            }

                            else -> serverHost = input
                        }
                    },
                    label = { Text(stringResource(R.string.server_address)) },
                    placeholder = { Text(stringResource(R.string.server_address_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                    shape = ConnectionFieldShape,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (serverProtocol == ServerProtocol.HTTP) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.http_warning),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.username)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = ConnectionFieldShape,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    shape = ConnectionFieldShape,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.message?.let { StatusMessage(stringResource(it.text, it.arg.orEmpty()), isError = true) }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val submittedPassword = password
                        password = ""
                        onSignIn(
                            "${serverProtocol.scheme}://${serverHost.trim()}",
                            username,
                            submittedPassword,
                        )
                    },
                    enabled = state.stage != LoginStage.LOADING &&
                        serverHost.isNotBlank() && username.isNotBlank() && password.isNotEmpty(),
                    shape = ConnectionFieldShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = touchTarget),
                ) {
                    if (state.stage == LoginStage.LOADING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.connecting))
                    } else {
                        Text(stringResource(R.string.sign_in))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProtocolDropdown(
    selected: ServerProtocol,
    onSelected: (ServerProtocol) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.server_protocol)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = ConnectionFieldShape,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ServerProtocol.entries.forEach { protocol ->
                DropdownMenuItem(
                    text = { Text(protocol.label) },
                    onClick = {
                        onSelected(protocol)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    snapshot: DeviceSnapshot,
    touchTarget: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    SectionCard(modifier) {
        Text(
            stringResource(R.string.device_parameters),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (snapshot.isJaecooJ7HeadUnit) {
                stringResource(R.string.jaecoo_profile_detected)
            } else if (snapshot.prefersLargeControls) {
                stringResource(R.string.large_screen_detected)
            } else {
                stringResource(R.string.device_help)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        DeviceLine(
            stringResource(R.string.device),
            "${snapshot.manufacturer} ${snapshot.model}",
        )
        DeviceLine(
            stringResource(R.string.android),
            stringResource(R.string.android_version_format, snapshot.androidRelease, snapshot.sdkInt),
        )
        DeviceLine(
            stringResource(R.string.window),
            stringResource(R.string.pixel_size_format, snapshot.windowWidthPx, snapshot.windowHeightPx),
        )
        DeviceLine(
            stringResource(R.string.ui_size),
            stringResource(R.string.dp_size_format, snapshot.screenWidthDp, snapshot.screenHeightDp),
        )
        DeviceLine(
            stringResource(R.string.density),
            stringResource(R.string.density_format, snapshot.densityDpi),
        )
        DeviceLine(
            stringResource(R.string.font_scale),
            if (snapshot.preferredFontScale > snapshot.fontScale) {
                String.format(
                    Locale.ROOT,
                    "%.2f → %.2f",
                    snapshot.fontScale,
                    snapshot.preferredFontScale,
                )
            } else {
                String.format(Locale.ROOT, "%.2f", snapshot.fontScale)
            },
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { copyDeviceReport(context, snapshot.asReport()) },
            shape = ConnectionFieldShape,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = touchTarget),
        ) {
            Text(stringResource(R.string.copy_report))
        }
    }
}

@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = ConnectionPanelShape,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.82f),
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            content = content,
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Text(value, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun DeviceLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f),
        )
        Text(
            value,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.55f),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun StatusMessage(message: String, isError: Boolean) {
    Spacer(Modifier.height(12.dp))
    Text(
        text = message,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyMedium,
    )
}

private fun copyDeviceReport(context: Context, report: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.device_report), report))
}
