package ru.e1173.nookast.ui

import androidx.compose.ui.input.nestedscroll.nestedScroll
import ru.e1173.nookast.designsystem.drawBackdropGrain
import ru.e1173.nookast.designsystem.driftedCenter
import ru.e1173.nookast.designsystem.playerDismissDrag
import ru.e1173.nookast.designsystem.sharedCover
import ru.e1173.nookast.designsystem.nookCard
import ru.e1173.nookast.designsystem.NookButton
import ru.e1173.nookast.designsystem.NookButtonVariant
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.runtime.produceState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import dev.chrisbanes.haze.hazeSource
import ru.e1173.nookast.R
import ru.e1173.nookast.data.local.QueueListEntry
import ru.e1173.nookast.designsystem.snowDusted
import ru.e1173.nookast.designsystem.LocalNookastColors
import ru.e1173.nookast.designsystem.floatingShadow
import ru.e1173.nookast.designsystem.scaleAlpha
import ru.e1173.nookast.designsystem.blendedWithSky
import ru.e1173.nookast.designsystem.DockDownloadState
import ru.e1173.nookast.designsystem.NookActionDock
import ru.e1173.nookast.designsystem.NookIcons
import ru.e1173.nookast.designsystem.NookSourceBadge
import ru.e1173.nookast.designsystem.SourceHealth
import ru.e1173.nookast.designsystem.NookChapterPill
import ru.e1173.nookast.designsystem.NookTimecodes
import ru.e1173.nookast.designsystem.NookExpandable
import ru.e1173.nookast.designsystem.NookMiniPlayer
import ru.e1173.nookast.designsystem.NookSourceChip
import ru.e1173.nookast.designsystem.qualityLabel
import ru.e1173.nookast.designsystem.drawGlowBar
import ru.e1173.nookast.designsystem.drawCssShadow
import androidx.compose.ui.draw.drawBehind
import ru.e1173.nookast.designsystem.MiniPlayerLabels
import ru.e1173.nookast.designsystem.NookTimeline
import ru.e1173.nookast.designsystem.NookTransport
import ru.e1173.nookast.designsystem.TransportLabels
import ru.e1173.nookast.data.local.PodcastSubscriptionEntity
import ru.e1173.nookast.data.remote.PodcastDetails
import ru.e1173.nookast.offline.EpisodeDownloadState
import ru.e1173.nookast.offline.ServerDownloadProgress
import ru.e1173.nookast.player.PlaybackUiState
import ru.e1173.nookast.player.StreamReachability
import ru.e1173.nookast.player.QueueSwipeAction

internal enum class NowPlayingTab {
    PLAYBACK,
    DESCRIPTION,
}

// Theme colours from the design tokens (DEC-017): these used to be fixed Lavender values.
private val DownloadGreen: Color
    @Composable @ReadOnlyComposable get() = LocalNookastColors.current.success
private val WarningCoral = ru.e1173.nookast.designsystem.NookFixedColors.WarningCoral
private val PlayerControlPurple: Color
    @Composable @ReadOnlyComposable get() = LocalNookastColors.current.accent
private const val PREVIEW_ARTWORK_PREFIX = "preview://"

// Cycles keep their own lengths (design audit 2.2).
private const val PLAYING_BARS_CYCLE_MS = 900
private const val SOURCE_PULSE_CYCLE_MS = 1800

@Composable
internal fun MiniPlayerBar(
    state: PlaybackUiState,
    artworkUrl: String,
    showSeekButtons: Boolean,
    largeControls: Boolean,
    onExpand: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onOpenUpNext: () -> Unit,
    modifier: Modifier = Modifier,
    glass: Modifier? = null,
) {
    val state = state.live()
    NookMiniPlayer(
        title = cleanEpisodeTitle(state.title).composed().ifBlank { stringResource(R.string.player_title) },
        podcastName = state.podcastName,
        progress = playbackFraction(state),
        playing = state.isPlaying,
        buffering = state.isBuffering,
        enabled = state.isConnected && !state.isBuffering,
        labels = MiniPlayerLabels(
            open = stringResource(R.string.open_now_playing),
            play = stringResource(R.string.play),
            pause = stringResource(R.string.pause),
            queue = stringResource(R.string.open_up_next),
            seekBack = stringResource(R.string.seek_back_seconds, state.seekBackSeconds),
            seekForward = stringResource(R.string.seek_forward_seconds, state.seekForwardSeconds),
        ),
        onOpen = onExpand,
        onPlayPause = onTogglePlayback,
        onQueue = onOpenUpNext,
        artwork = { artworkModifier ->
            Artwork(
                url = artworkUrl,
                title = state.title,
                fallbackUrl = podcastArtworkFallback(state.podcastName),
                downloaded = false,
                badgeSize = 0.dp,
                cornerRadius = if (largeControls) 13.dp else 10.dp,
                // Flies into the player's cover (design audit 4.1).
                modifier = artworkModifier.sharedCover(
                    radius = (if (largeControls) 13.dp else 10.dp) * ARTWORK_CORNER_SCALE,
                    otherRadius = (if (largeControls) 32.dp else 24.dp) * ARTWORK_CORNER_SCALE,
                ),
            )
        },
        modifier = modifier.snowDusted(16.dp, seed = 3),
        // Seek buttons only on Fold and in the car (mini-player spec: never on a phone).
        seekBackSeconds = state.seekBackSeconds,
        seekForwardSeconds = state.seekForwardSeconds,
        onSeekBack = onSeekBack.takeIf { showSeekButtons },
        onSeekForward = onSeekForward.takeIf { showSeekButtons },
        scale = if (largeControls) 4f / 3f else 1f,
        glass = glass,
    )
}

@Composable
internal fun NowPlayingScreen(
    playbackState: PlaybackUiState,
    queueState: QueueUiState,
    currentEntry: QueueListEntry?,
    podcastDetails: PodcastDetails?,
    podcastDetailsLoading: Boolean,
    wide: Boolean,
    largeControls: Boolean,
    initialTab: NowPlayingTab,
    onCollapse: () -> Unit,
    onOpenUpNext: () -> Unit,
    onOpenPodcast: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onRunDiagnostics: () -> Unit,
    onDownloadToServer: () -> Unit = {},
    onPlayEpisode: (Int) -> Unit,
    onMoveEpisode: (Int, Int) -> Unit,
    onRemoveEpisode: (Int) -> Unit,
    onDownloadEpisode: (QueueListEntry) -> Unit,
    onDeleteDownloadEpisode: (Int) -> Unit,
    onMarkEpisodePlayed: (QueueListEntry) -> Unit,
    onDownloadCurrent: () -> Unit,
    onDeleteCurrentDownload: () -> Unit,
    onMarkPlayed: () -> Unit,
    isSaved: Boolean = false,
    onToggleSaved: () -> Unit = {},
    // The title of what plays while Now Playing shows another episode picked in Up Next, or null.
    playingElsewhere: String? = null,
    // What plays, for the queue beside a preview.
    playingEpisodeId: Int? = null,
    onReturnToPlaying: () -> Unit = {},
    sourceStatusInitiallyExpanded: Boolean = false,
) {
    val playerSettings by androidx.lifecycle.viewmodel.compose.viewModel<PlayerSettingsViewModel>().settings.collectAsStateWithLifecycle()
    var selectedTab by remember(initialTab) { mutableStateOf(initialTab) }
    // Phone: the two tabs are pages that follow the finger (design audit 4.3).
    val tabPager = androidx.compose.foundation.pager.rememberPagerState(initialPage = selectedTab.ordinal) { NowPlayingTab.entries.size }
    LaunchedEffect(selectedTab) { if (tabPager.targetPage != selectedTab.ordinal) tabPager.animateScrollToPage(selectedTab.ordinal) }
    val tabHaptics = ru.e1173.nookast.designsystem.rememberNookHaptics()
    LaunchedEffect(tabPager.settledPage) {
        val settled = NowPlayingTab.entries[tabPager.settledPage]
        if (settled != selectedTab) tabHaptics.tick()
        selectedTab = settled
    }
    var sourceStatusExpanded by remember(sourceStatusInitiallyExpanded) {
        mutableStateOf(sourceStatusInitiallyExpanded)
    }
    var equalizerExpanded by remember { mutableStateOf(false) }
    val resources = LocalResources.current
    val episodeDescription = currentEntry.descriptionOrCached(podcastDetails)
    // Chapters of the feed (Podcasting 2.0) or the stream, else a timestamp list of the
    // description; one list for the pill, the timeline and the Description panel.
    val chapters = remember(episodeDescription, playbackState.nativeChapters, playbackState.feedChapters, resources) {
        episodeChapterList(playbackState.feedChapters.ifEmpty { playbackState.nativeChapters }, episodeDescription, resources)
    }
    // A chapter picked in Description seeks there and returns to Playback; play or pause stays.
    // Wide layouts show the player beside the list, so they stay on the list (user, 29.10.2026).
    val onSelectChapter: (Long) -> Unit = { positionMs ->
        onSeekTo(positionMs)
        if (!wide) selectedTab = NowPlayingTab.PLAYBACK
        sourceStatusExpanded = false
    }
    val currentDownloadState = queueState.downloadStates[playbackState.currentEpisodeId]
    val downloaded = playbackState.isLocalSource ||
        currentDownloadState == EpisodeDownloadState.DOWNLOADED
    val effectiveDownloadState = if (downloaded) {
        EpisodeDownloadState.DOWNLOADED
    } else {
        currentDownloadState ?: EpisodeDownloadState.NOT_DOWNLOADED
    }
    val downloadActionEnabled = effectiveDownloadState == EpisodeDownloadState.NOT_DOWNLOADED ||
        effectiveDownloadState == EpisodeDownloadState.FAILED ||
        effectiveDownloadState == EpisodeDownloadState.DOWNLOADED
    val screenBackground = MaterialTheme.colorScheme.background
    var screenOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { screenOrigin = it.positionInRoot() },
    ) {
    // The screen and its cover light lie in the header glass's source, so the glass keeps their colour.
    val nowPlayingHaze = dev.chrisbanes.haze.rememberHazeState()
    Box(
        Modifier
            .matchParentSize()
            .hazeSource(nowPlayingHaze, zIndex = -1f)
            .background(screenBackground),
    ) {
        ArtworkTintBackdrop(
            url = currentEntry?.artworkUrl.orEmpty().ifBlank { playbackState.artworkUrl },
            modifier = Modifier.matchParentSize(),
            living = playbackState.isPlaying,
        )
        // Snow falls on the background, behind the cover and the controls (user, 2.11.2026).
        if (ru.e1173.nookast.designsystem.LocalNewYear.current) {
            ru.e1173.nookast.designsystem.NewYearSnowfall(Modifier.matchParentSize())
        }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Box(Modifier.fillMaxSize()) {
        // The header stays on top and the content scrolls under it; once it does, the header turns
        // to the mini player's frosted glass (user, 29.10.2026).
        var contentScrolled by remember(selectedTab) { mutableStateOf(false) }
        // The header and its glass start under the status bar (user, 29.10.2026).
        val statusInset = LocalTopInset.current
        val headerHeight = statusInset + when {
            largeControls -> 60.dp
            wide -> 52.dp
            else -> 48.dp
        }
        // Wide: only the scrolling right pane lies under the glass (user, 31.10.2026: the fixed player's
        // cover smeared a grey band into the header over the left half).
        Column(Modifier.fillMaxSize().then(if (wide) Modifier else Modifier.hazeSource(nowPlayingHaze).glassEdgeFade())) {
            // The mockup has no rule under the header.
            if (wide) {
                // The player keeps one geometry on both tabs: switching to Description
                // used to move and resize it (APK-3.4 review).
                val playerWeight = when {
                    largeControls -> 0.5065f
                    else -> 0.50f
                }
                val detailsWeight = 1f - playerWeight
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            // The right panel's list insets its rows by 12 dp (20 in the car):
                            // the end padding is less by that, so both outer edges match.
                            start = if (largeControls) 33.33.dp else 28.dp,
                            end = if (largeControls) 13.33.dp else 16.dp,
                            top = if (largeControls) headerHeight + 13.33.dp else 0.dp,
                            bottom = if (largeControls) 26.67.dp else 12.dp,
                        ),
                    // Player → divider and divider → the panel's rows are the same 12 dp
                    // (user, 27.10.2026); the 12 dp list inset is part of the second one.
                    horizontalArrangement = Arrangement.spacedBy(if (largeControls) 14.dp else 8.dp),
                ) {
                    PlayerPane(
                        state = playbackState,
                        artworkUrl = currentEntry?.artworkUrl.orEmpty()
                            .ifBlank { playbackState.artworkUrl },
                        chapters = chapters,
                        downloaded = downloaded,
                        downloadActionEnabled = downloadActionEnabled,
                        downloadState = effectiveDownloadState,
                        downloadProgress = playbackState.currentEpisodeId?.let(
                            queueState.downloadProgress::get,
                        ),
                        largeControls = largeControls,
                        alignMetadataStart = true,
                        onOpenPodcast = onOpenPodcast,
                        onTogglePlayback = onTogglePlayback,
                        onSeekBack = onSeekBack,
                        onSeekForward = onSeekForward,
                        onSeekTo = onSeekTo,
                        onDownload = onDownloadCurrent,
                        onDeleteDownload = onDeleteCurrentDownload,
                        onMarkPlayed = onMarkPlayed,
                        sourceStatusExpanded = sourceStatusExpanded,
                        onSourceStatusExpandedChange = {
                            sourceStatusExpanded = it
                            if (it) equalizerExpanded = false
                        },
                        onRunDiagnostics = onRunDiagnostics,
                        onDownloadToServer = onDownloadToServer,
                        playingElsewhere = playingElsewhere,
                        onReturnToPlaying = onReturnToPlaying,
                        modifier = Modifier
                            .weight(playerWeight)
                            .fillMaxHeight()
                            .padding(end = if (largeControls) 0.dp else 4.dp)
                            // The cover starts just under the header, level with the right pane (user, 3.11.2026).
                            .padding(top = if (largeControls) 0.dp else headerHeight + WIDE_TOP_GAP),
                    )
                    Box(
                        modifier = Modifier
                            .weight(detailsWeight)
                            .fillMaxHeight()
                            .hazeSource(nowPlayingHaze)
                            // No underlay (user, 11.11.2026: on the large screen at night the faint veil read as a lighter
                            // block; without it the pane lies on the screen's own light like everything else).
                            .glassEdgeFade(),
                    ) {
                        // Wide: the right pane changes with a cross-fade (design audit 4.3).
                        androidx.compose.animation.Crossfade(selectedTab, Modifier.fillMaxSize(), ru.e1173.nookast.designsystem.NookMotion.standard(), label = "playerPane") { shownTab ->
                        if (shownTab == NowPlayingTab.PLAYBACK) {
                            UpNextPanel(
                                entries = queueState.entries,
                                downloadStates = queueState.downloadStates,
                                downloadProgress = queueState.downloadProgress,
                                currentEpisodeId = playingEpisodeId ?: playbackState.currentEpisodeId,
                                compact = true,
                                alignWithDescription = true,
                                // Its list adds 12 dp above the first row: that row then starts level with the cover.
                                topInset = if (largeControls) 0.dp else headerHeight + WIDE_TOP_GAP - 12.dp,
                                onScrolledChange = { contentScrolled = it },
                                largeControls = largeControls,
                                onPlayEpisode = onPlayEpisode,
                                onMoveEpisode = onMoveEpisode,
                                onRemoveEpisode = onRemoveEpisode,
                                onDownloadEpisode = onDownloadEpisode,
                                onDeleteDownloadEpisode = onDeleteDownloadEpisode,
                                onMarkEpisodePlayed = onMarkEpisodePlayed,
                                swipeStartAction = playerSettings.queueSwipeStartAction,
                                swipeEndAction = playerSettings.queueSwipeEndAction,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            DescriptionPane(
                                currentEntry = currentEntry,
                                mediaSizeBytes = playbackState.mediaSizeBytes,
                                bitrateKbps = playbackState.bitrateKbps,
                                podcastDetails = podcastDetails,
                                podcastDetailsLoading = podcastDetailsLoading,
                                chapters = chapters,
                                positionMs = playbackState.live().positionMs,
                                onSelectChapter = onSelectChapter,
                                showEpisodeSummary = false,
                                // Its column adds 3 dp above the first card: that card then starts level with the cover.
                                topInset = if (largeControls) 0.dp else headerHeight + WIDE_TOP_GAP - 3.dp,
                                onScrolledChange = { contentScrolled = it },
                                downloaded = downloaded,
                                largeControls = largeControls,
                                onOpenPodcast = onOpenPodcast,
                                onSeekTo = onSeekTo,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        }
                    }
                }
            } else {
                androidx.compose.foundation.pager.HorizontalPager(tabPager, Modifier.fillMaxSize(), beyondViewportPageCount = 0) { page ->
                    if (page == NowPlayingTab.PLAYBACK.ordinal) {
                        PlayerPane(
                                state = playbackState,
                                artworkUrl = currentEntry?.artworkUrl.orEmpty()
                                    .ifBlank { playbackState.artworkUrl },
                                chapters = chapters,
                                downloaded = downloaded,
                                downloadActionEnabled = downloadActionEnabled,
                                downloadState = effectiveDownloadState,
                                downloadProgress = playbackState.currentEpisodeId?.let(
                                    queueState.downloadProgress::get,
                                ),
                                largeControls = false,
                                alignMetadataStart = false,
                                onOpenPodcast = onOpenPodcast,
                                onTogglePlayback = onTogglePlayback,
                                onSeekBack = onSeekBack,
                                onSeekForward = onSeekForward,
                                onSeekTo = onSeekTo,
                                onDownload = onDownloadCurrent,
                                onDeleteDownload = onDeleteCurrentDownload,
                                onMarkPlayed = onMarkPlayed,
                                sourceStatusExpanded = sourceStatusExpanded,
                                onSourceStatusExpandedChange = {
                                    sourceStatusExpanded = it
                                    if (it) equalizerExpanded = false
                                },
                                onRunDiagnostics = onRunDiagnostics,
                                onDownloadToServer = onDownloadToServer,
                                playingElsewhere = playingElsewhere,
                                onReturnToPlaying = onReturnToPlaying,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 2.dp)
                                    .padding(top = headerHeight),
                            )
                    } else {
                        DescriptionPane(
                                currentEntry = currentEntry,
                                mediaSizeBytes = playbackState.mediaSizeBytes,
                                bitrateKbps = playbackState.bitrateKbps,
                                podcastDetails = podcastDetails,
                                podcastDetailsLoading = podcastDetailsLoading,
                                chapters = chapters,
                                positionMs = playbackState.live().positionMs,
                                onSelectChapter = onSelectChapter,
                                showEpisodeSummary = true,
                                topInset = headerHeight,
                                onScrolledChange = { contentScrolled = it },
                                downloaded = downloaded,
                                largeControls = false,
                                onOpenPodcast = onOpenPodcast,
                                onSeekTo = onSeekTo,
                                modifier = Modifier.fillMaxSize(),
                            )
                    }
                }
            }
            playbackState.error?.let { error ->
                Text(
                    stringResource(error.stringResource()),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
            TopGlassHeader(nowPlayingHaze, contentScrolled, Modifier.align(Alignment.TopCenter), tint = screenBackground) {
            PlayerHeader(
                wide = wide,
                largeControls = largeControls,
                selectedTab = selectedTab,
                pageFraction = if (wide) null else ({ (tabPager.currentPage + tabPager.currentPageOffsetFraction).coerceIn(0f, 1f) }),
                onTabSelected = {
                    selectedTab = it
                    sourceStatusExpanded = false
                },
                onShowEqualizer = {
                    equalizerExpanded = true
                    sourceStatusExpanded = false
                },
                onCollapse = onCollapse,
                onOpenUpNext = onOpenUpNext,
                isSaved = isSaved,
                onToggleSaved = onToggleSaved,
                modifier = Modifier.padding(top = statusInset),
            )
            }
        if (equalizerExpanded) {
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(30f)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { equalizerExpanded = false },
            )
            // Within the window: on the car profile the two columns ran off the right edge (13.11.2026).
            val popoverX = if (wide) { if (largeControls) 162.67.dp else 86.dp } else 0.dp
            val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
            val windowDp = with(LocalDensity.current) { windowInfo.containerSize.width.toDp() to windowInfo.containerSize.height.toDp() }
            SoundPopover(
                largeControls = largeControls,
                wide = wide,
                maxWidth = windowDp.first - popoverX - 16.dp,
                maxHeight = windowDp.second - statusInset - 96.dp,
                modifier = Modifier
                    .align(if (wide) Alignment.TopStart else Alignment.TopCenter)
                    .offset(
                        x = popoverX,
                        y = statusInset + if (largeControls) 66.67.dp else 64.dp,
                    )
                    .zIndex(31f),
            )
        }
        }
    }
    // New Year (DEC-018): the garland hangs over the cover above everything (the player column
    // scrolls and clips, so it is drawn here at the cover's measured place). The snow is in the backdrop.
    if (ru.e1173.nookast.designsystem.LocalNewYear.current) {
        NowPlayingCoverBounds.value?.let { cover ->
            ru.e1173.nookast.designsystem.NewYearGarland(
                cover = cover.translate(-screenOrigin),
                modifier = Modifier.matchParentSize(),
            )
        }
    }
    }
}

/** Three bars rising and falling out of step: something is playing. */
@Composable
private fun PlayingBars(modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "playing-bars")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            tween(PLAYING_BARS_CYCLE_MS, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "phase",
    )
    val color = PlayerControlPurple
    androidx.compose.foundation.Canvas(modifier) {
        val gap = size.width / 7f
        val bar = (size.width - gap * 2) / 3f
        for (index in 0 until 3) {
            val wave = kotlin.math.sin((phase + index * 0.33f) * 2f * Math.PI.toFloat())
            val height = size.height * (0.35f + 0.65f * (0.5f + 0.5f * wave))
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(index * (bar + gap), size.height - height),
                size = androidx.compose.ui.geometry.Size(bar, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(bar / 2f),
            )
        }
    }
}

/** Where the Now Playing cover is, in root coordinates, for the New Year cap (one screen at a time). */
private val NowPlayingCoverBounds = mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)

@Composable
internal fun UpNextScreen(
    playbackState: PlaybackUiState,
    queueState: QueueUiState,
    largeControls: Boolean,
    /** Null as a tab (user, 1.11.2026): no Back, the title in its place. */
    onBack: (() -> Unit)?,
    onPlayEpisode: (Int) -> Unit,
    onMoveEpisode: (Int, Int) -> Unit,
    onRemoveEpisode: (Int) -> Unit,
    onDownloadEpisode: (QueueListEntry) -> Unit,
    onDeleteDownloadEpisode: (Int) -> Unit,
    onMarkEpisodePlayed: (QueueListEntry) -> Unit,
) {
    val playerSettings by androidx.lifecycle.viewmodel.compose.viewModel<PlayerSettingsViewModel>().settings.collectAsStateWithLifecycle()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // The bar stays and turns to glass over the scrolled list (user, 29.10.2026).
        var scrolled by remember { mutableStateOf(false) }
        GlassHeaderScreen(
            headerHeight = 56.dp,
            scrolled = scrolled,
            modifier = Modifier.fillMaxSize(),
            header = {
            // D3, nextup-normal-b: back chevron, title 19/700, counter capsule; no divider.
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (onBack != null) {
                    ru.e1173.nookast.designsystem.NookHeaderDisc(NookIcons.ChevronLeft, stringResource(R.string.back), onBack)
                } else {
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    stringResource(R.string.queue_title),
                    style = MaterialTheme.typography.titleMedium.merge(ru.e1173.nookast.designsystem.NookPlayerType.queueTitle),
                )
                val count = queueState.entries.size
                Text(
                    pluralStringResource(R.plurals.queue_episode_count, count, count),
                    style = MaterialTheme.typography.labelSmall.merge(ru.e1173.nookast.designsystem.NookPlayerType.queueCount),
                    color = ru.e1173.nookast.designsystem.LocalNookastColors.current.accentText,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .background(LocalNookastColors.current.artworkPlaceholder, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            },
        ) { topPadding ->
            UpNextPanel(
                entries = queueState.entries,
                downloadStates = queueState.downloadStates,
                downloadProgress = queueState.downloadProgress,
                currentEpisodeId = playbackState.currentEpisodeId,
                compact = largeControls,
                largeControls = largeControls,
                showTitle = false,
                topInset = topPadding,
                onScrolledChange = { scrolled = it },
                onPlayEpisode = onPlayEpisode,
                onMoveEpisode = onMoveEpisode,
                onRemoveEpisode = onRemoveEpisode,
                onDownloadEpisode = onDownloadEpisode,
                onDeleteDownloadEpisode = onDeleteDownloadEpisode,
                onMarkEpisodePlayed = onMarkEpisodePlayed,
                swipeStartAction = playerSettings.queueSwipeStartAction,
                swipeEndAction = playerSettings.queueSwipeEndAction,
                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
            )
        }
    }
}

@Composable
private fun PlayerHeader(
    wide: Boolean,
    largeControls: Boolean,
    selectedTab: NowPlayingTab,
    /** Phone: the pager position, 0 on Playback and 1 on Description; the underlines follow it (4.3). */
    pageFraction: (() -> Float)? = null,
    onTabSelected: (NowPlayingTab) -> Unit,
    onShowEqualizer: () -> Unit,
    onCollapse: () -> Unit,
    onOpenUpNext: () -> Unit,
    isSaved: Boolean,
    onToggleSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val collapseDescription = stringResource(R.string.collapse_player)
    val upNextDescription = stringResource(R.string.open_up_next)
    val savedDescription = stringResource(
        if (isSaved) R.string.remove_episode_from_favorites else R.string.add_episode_to_favorites,
    )
    // Header row of the mockup: 44 dp tall, icons and tab labels on one centre line (APK-3.2 review).
    val contentOffsetY = 0.dp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(
                when {
                    // Car: 60 (was 72) so the Description panel can start level with the cover
                    // (user, 28.10.2026).
                    largeControls -> 60.dp
                    wide -> 52.dp
                    else -> 48.dp
                },
            )
            .zIndex(10f)
            // Pulling down here closes the player (design audit 4.2).
            .playerDismissDrag()
            .padding(horizontal = 4.dp),
    ) {
        // The shared icon button as a bare glyph (DEC-059): no disc over the cover light.
        ru.e1173.nookast.designsystem.NookHeaderDisc(
            icon = NookIcons.ChevronDown,
            contentDescription = collapseDescription,
            onClick = onCollapse,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 3.dp, y = contentOffsetY),
            discSize = 40.dp * if (largeControls) 4f / 3f else 1f,
            // Laid out at the disc, where the old 40 / 48 dp button stood; the header keeps its height.
            zone = 40.dp * if (largeControls) 4f / 3f else 1f,
            fill = Color.Transparent,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier
                .then(
                    if (largeControls) {
                        Modifier.align(Alignment.CenterStart).offset(x = 152.dp)
                    } else if (wide) {
                        Modifier.align(Alignment.Center).offset(x = (-83).dp, y = contentOffsetY + 3.75.dp)
                    } else {
                        // Phone: centred in the room between the collapse arrow and the action
                        // capsule, so neither covers a tab in either language (user, 29.10.2026).
                        Modifier.align(Alignment.Center)
                            .padding(start = 46.dp, end = 92.dp)
                            .offset(y = contentOffsetY + 3.75.dp)
                    },
                )
                .offset(
                    y = if (largeControls) 0.dp else 0.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(if (largeControls) 24.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerTab(
                text = stringResource(R.string.playback_tab),
                selected = selectedTab == NowPlayingTab.PLAYBACK,
                selection = pageFraction?.let { f -> { 1f - f() } } ?: { if (selectedTab == NowPlayingTab.PLAYBACK) 1f else 0f },
                playbackTab = true,
                wide = wide,
                largeControls = largeControls,
                onClick = { onTabSelected(NowPlayingTab.PLAYBACK) },
                onLongClick = onShowEqualizer,
            )
            if (!largeControls) Spacer(Modifier.width(if (wide) 11.dp else 5.5.dp))
            PlayerTab(
                text = stringResource(R.string.description_tab),
                selected = selectedTab == NowPlayingTab.DESCRIPTION,
                selection = pageFraction ?: { if (selectedTab == NowPlayingTab.DESCRIPTION) 1f else 0f },
                playbackTab = false,
                wide = wide,
                largeControls = largeControls,
                onClick = { onTabSelected(NowPlayingTab.DESCRIPTION) },
            )
        }
        // Saved and the queue share one capsule at the end of the header, as Apple groups its
        // toolbar actions (user, 29.10.2026: the heart lived apart from the queue button). Wide
        // layouts always show the queue beside the player, so they keep only the heart. The
        // capsule never takes part in measuring the two tabs.
        val showQueueButton = !wide && !largeControls
        val actionHeight = if (largeControls) 52.dp else 36.dp
        val actionWidth = if (largeControls) 60.dp else 40.dp
        val iconSize = if (largeControls) 28.dp else 19.dp
        Row(
            Modifier
                .align(Alignment.CenterEnd)
                .offset(x = if (largeControls) (-24).dp else (-8).dp, y = contentOffsetY)
                .height(actionHeight)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(actionWidth)
                    .clickable(role = Role.Button, onClick = onToggleSaved)
                    .semantics { contentDescription = savedDescription },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isSaved) NookIcons.HeartFilled else NookIcons.Heart,
                    contentDescription = null,
                    tint = if (isSaved) PlayerControlPurple else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(iconSize),
                )
            }
            if (showQueueButton) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(actionHeight * 0.44f)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)),
                )
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(actionWidth)
                        .clickable(role = Role.Button, onClick = onOpenUpNext)
                        .semantics { contentDescription = upNextDescription },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        NookIcons.UpNext,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(iconSize),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerTab(
    text: String,
    selected: Boolean,
    /** How much of the underline shows: 0..1, following the pager (design audit 4.3). */
    selection: () -> Float = { if (selected) 1f else 0f },
    playbackTab: Boolean,
    wide: Boolean,
    largeControls: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val underlineGlow = LocalNookastColors.current.let { if (it.isDark) it.accent.copy(alpha = 0.7f) else Color.Transparent }
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .then(
                if (largeControls) {
                    Modifier.heightIn(min = 56.dp).padding(end = 28.dp)
                } else {
                    Modifier.height(48.dp)
                },
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .then(if (largeControls) Modifier else Modifier.padding(horizontal = 5.dp)),
        contentAlignment = if (largeControls) Alignment.CenterStart else Alignment.Center,
    ) {
        Column(
            modifier = Modifier.offset(
                x = when {
                    largeControls -> 0.dp
                    wide && playbackTab -> 3.4.dp
                    !wide && playbackTab -> (-0.25).dp
                    else -> 0.dp
                },
            ).width(IntrinsicSize.Max),
            horizontalAlignment = if (largeControls) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            // Wide and car: the underline is as wide as the word and sits close under it (user,
            // 27.10.2026: «Воспроизведение» was cut and the bar hung far below).
            Text(
                text,
                // Mockup: the selected tab is text-primary and bold, the accent is only the underline.
                color = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = ru.e1173.nookast.designsystem.NookPlayerType.tabWeight(selected),
                style = when {
                    largeControls -> MaterialTheme.typography.titleMedium.merge(ru.e1173.nookast.designsystem.NookPlayerType.tabLarge)
                    wide -> MaterialTheme.typography.bodyMedium.merge(ru.e1173.nookast.designsystem.NookPlayerType.tabWide)
                    else -> MaterialTheme.typography.bodyMedium.merge(ru.e1173.nookast.designsystem.NookPlayerType.tab)
                },
                maxLines = 1,
                // Phone: a long label (RU «Воспроизведение», large interface scale) shrinks to fit
                // between the arrow and the capsule instead of being cut (user, 29.10.2026).
                autoSize = if (!wide && !largeControls) {
                    androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 13.5.sp, stepSize = 0.25.sp)
                } else {
                    null
                },
            )
            Spacer(
                Modifier.height(
                    when {
                        largeControls -> 6.dp
                        wide -> 6.dp
                        else -> 5.dp
                    },
                ),
            )
            Box(
                Modifier
                    .then(
                        when {
                            largeControls || wide -> Modifier.fillMaxWidth()
                            playbackTab -> Modifier.width(106.5.dp)
                            else -> Modifier.width(57.dp)
                        },
                    )
                    .height(3.dp)
                    // Night reference: the underline glows (0 0 8px accent .7).
                    .graphicsLayer {
                        val s = selection()
                        alpha = s
                        scaleX = 0.4f + 0.6f * s
                    }
                    .drawBehind {
                        if (selection() > 0f && underlineGlow.alpha > 0f) drawGlowBar(underlineGlow, Offset.Zero, size, 8.dp)
                    }
                    .background(
                        PlayerControlPurple,
                        ru.e1173.nookast.designsystem.NookShapes.full,
                    ),
            )
        }
    }
}

@Composable
private fun SourceStatusPopover(
    state: PlaybackUiState,
    wide: Boolean,
    largeControls: Boolean,
    onRunDiagnostics: () -> Unit,
    onDownloadToServer: () -> Unit = {},
    modifier: Modifier = Modifier,
    pointerAtBottomStart: Boolean = false,
) {
    val state = state.live()
    // Source card (DEC-033, user 27.10.2026: "make it something you want to open"): a widget-like
    // card in the iOS manner — a large status glyph with a soft pulse while playing, the source
    // word and host, the quality tag; two tiles (quality, buffer ring); status pills; the server
    // copy offer and a quiet "Check connection" row.
    val colors = LocalNookastColors.current
    val coral = ru.e1173.nookast.designsystem.NookFixedColors.alertCoral(colors.isDark)
    val tone = when {
        state.isLocalSource -> colors.success
        state.streamReachability == StreamReachability.UNAVAILABLE -> coral
        state.streamReachability == StreamReachability.AVAILABLE -> colors.success
        else -> colors.textSecondary
    }
    val quality = qualityLabel(state.bitrateKbps)
    val card = colors.surface.compositeOver(colors.background)
    val border = colors.divider
    val tileColor = colors.textSecondary.copy(alpha = if (colors.isDark) 0.10f else 0.07f)
    val captionStyle = MaterialTheme.typography.labelSmall.merge(ru.e1173.nookast.designsystem.NookPlayerType.sourceCaption)
    Box(
        modifier = modifier
            .wrapContentHeight(align = Alignment.Top, unbounded = true)
            .width(if (largeControls) 340.dp else 308.dp),
    ) {
        // One outline for the card and its pointer (user, 29.10.2026: the seam between the two
        // looked unfinished): a single shape with a soft, rounded tip, filled and stroked once.
        val pointerHeight = 10.dp
        val popover = remember(pointerAtBottomStart, largeControls) {
            PopoverShape(
                radius = 22.dp,
                pointerWidth = 24.dp,
                pointerHeight = pointerHeight,
                pointerAtBottom = pointerAtBottomStart,
                pointerCenterFromEnd = if (pointerAtBottomStart) null else (if (largeControls) 42.dp else 34.dp) + 11.dp,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val r = 22.dp.toPx()
                    val p = pointerHeight.toPx()
                    inset(0f, if (pointerAtBottomStart) 0f else p, 0f, if (pointerAtBottomStart) p else 0f) {
                        // The same two layers as floatingShadow (one shadow for all floating surfaces).
                        val ink = if (colors.isDark) Color.Black else colors.textPrimary
                        drawCssShadow(ink.copy(alpha = if (colors.isDark) 0.42f else 0.12f), 40.dp, r, dy = 14.dp)
                        drawCssShadow(ink.copy(alpha = if (colors.isDark) 0.30f else 0.06f), 6.dp, r, dy = 1.dp)
                        if (colors.isDark) drawCssShadow(tone.copy(alpha = 0.10f), 24.dp, r)
                    }
                }
                .clip(popover)
                .background(card)
                .border(1.dp, border, popover)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp + if (pointerAtBottomStart) 0.dp else pointerHeight,
                    bottom = 16.dp + if (pointerAtBottomStart) pointerHeight else 0.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header: status glyph, source word and host, quality tag.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SourcePulseGlyph(tone = tone, local = state.isLocalSource, pulsing = state.isPlaying && !state.isLocalSource)
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(
                            when {
                                state.isLocalSource -> R.string.badge_local
                                state.isServerCopySource -> R.string.badge_server
                                state.streamReachability == StreamReachability.UNAVAILABLE -> R.string.badge_unavailable
                                state.streamReachability == StreamReachability.AVAILABLE -> R.string.badge_stream
                                else -> R.string.badge_checking
                            },
                        ),
                        style = MaterialTheme.typography.titleLarge.merge(ru.e1173.nookast.designsystem.NookPlayerType.sourceTitle),
                        color = colors.textPrimary,
                    )
                    Text(
                        sourceLabel(state),
                        style = MaterialTheme.typography.bodySmall.merge(ru.e1173.nookast.designsystem.NookPlayerType.sourceBody),
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (quality != null) {
                    Box(
                        Modifier
                            .border(1.5.dp, colors.textPrimary.copy(alpha = 0.7f), ru.e1173.nookast.designsystem.NookShapes.xs)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(quality, style = MaterialTheme.typography.labelLarge.merge(ru.e1173.nookast.designsystem.NookPlayerType.sourceQuality), color = colors.textPrimary)
                    }
                }
            }
            // Tiles: quality and buffer.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(
                    Modifier.weight(1f).clip(ru.e1173.nookast.designsystem.NookShapes.m).background(tileColor).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(stringResource(R.string.quality_field).uppercase(), style = captionStyle, color = colors.textSecondary)
                    Text(
                        state.bitrateKbps?.let { stringResource(R.string.audio_bitrate_kbps, it) } ?: "—",
                        style = MaterialTheme.typography.titleMedium.merge(ru.e1173.nookast.designsystem.NookPlayerType.sourceMetric),
                        color = colors.textPrimary,
                    )
                    Text(
                        stringResource(
                            when (quality) {
                                "HQ" -> R.string.quality_hq
                                "HD" -> R.string.quality_hd
                                "SD" -> R.string.quality_sd
                                else -> R.string.quality_unknown
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                Row(
                    Modifier.weight(1f).clip(ru.e1173.nookast.designsystem.NookShapes.m).background(tileColor).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val fraction = cachedPercent(state) / 100f
                    val ring = colors.accent
                    val track = colors.track
                    Canvas(Modifier.size(38.dp)) {
                        val stroke = 4.dp.toPx()
                        val inset = stroke / 2f
                        val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                        drawArc(ring, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.cache_field_short).uppercase(), style = captionStyle, color = colors.textSecondary)
                        Text("${cachedPercent(state)}%", style = MaterialTheme.typography.titleMedium.merge(ru.e1173.nookast.designsystem.NookPlayerType.sourceMetric), color = colors.textPrimary)
                        Text(
                            if (state.isLocalSource) {
                                stringResource(R.string.cache_entire_episode)
                            } else {
                                stringResource(
                                    R.string.cache_ahead,
                                    stringResource(R.string.duration_minutes, ceil(state.bufferedAheadMs / 60_000.0).toInt().coerceAtLeast(1)),
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }
            // Status pills: server and time left.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!state.isLocalSource) {
                    SourcePill(
                        text = stringResource(R.string.server_pill, serverLabel(state)),
                        dot = when (state.streamReachability) {
                            StreamReachability.AVAILABLE -> colors.success
                            StreamReachability.UNAVAILABLE -> coral
                            else -> colors.textSecondary
                        },
                        background = tileColor,
                    )
                }
                SourcePill(
                    text = stringResource(
                        R.string.time_left_pill,
                        stringResource(
                            R.string.duration_minutes,
                            ceil((state.durationMs - state.positionMs).coerceAtLeast(0) / 60_000.0).toInt().coerceAtLeast(1),
                        ),
                    ),
                    dot = null,
                    background = tileColor,
                )
            }
            ServerCopySection(state, onDownloadToServer)
            // Quiet action row; results of the last check below it.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(ru.e1173.nookast.designsystem.NookShapes.m)
                    .clickable(enabled = !state.diagnosticsRunning) { onRunDiagnostics() }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.check_connection),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium.merge(ru.e1173.nookast.designsystem.NookPlayerType.sourceAction),
                    color = colors.accentText,
                )
                if (state.diagnosticsRunning) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.accentText)
                } else {
                    Icon(NookIcons.ChevronRight, null, tint = colors.accentText, modifier = Modifier.size(14.dp))
                }
            }
            state.diagnosticResults.forEach { result ->
                val host = result.requestedHost.ifBlank { result.resolvedHost }
                Text(
                    if (result.isAvailable) {
                        stringResource(R.string.diagnostic_available, host, result.latencyMs)
                    } else {
                        stringResource(R.string.diagnostic_unavailable, host)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

/** 44 dp status glyph of the source card; a ring breathes out of it while the stream plays. */
@Composable
private fun SourcePulseGlyph(tone: Color, local: Boolean, pulsing: Boolean) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "sourcePulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            tween(SOURCE_PULSE_CYCLE_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        ),
        label = "sourcePulsePhase",
    )
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(tone.copy(alpha = 0.16f), radius = r)
            if (pulsing) {
                drawCircle(
                    tone.copy(alpha = 0.35f * (1f - phase)),
                    radius = r * (0.45f + 0.55f * phase),
                    style = Stroke(2.dp.toPx()),
                )
            }
        }
        if (local) {
            Icon(NookIcons.CheckBold, null, tint = tone, modifier = Modifier.size(20.dp))
        } else {
            Box(Modifier.size(12.dp).background(tone, CircleShape))
        }
    }
}

@Composable
private fun SourcePill(text: String, dot: Color?, background: Color) {
    Row(
        Modifier.clip(CircleShape).background(background).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) Box(Modifier.size(7.dp).background(dot, CircleShape))
        Text(text, style = MaterialTheme.typography.labelMedium, color = LocalNookastColors.current.textPrimary, maxLines = 1)
    }
}

@Composable
private fun sourceLabel(state: PlaybackUiState): String = when {
    state.isLocalSource -> stringResource(R.string.source_local_file)
    state.isServerCopySource -> stringResource(R.string.source_pinepods_copy)
    state.isUsingPinePodsFallback -> stringResource(R.string.source_pinepods)
    // The origin host tells "SoundCloud is blocked" apart from "my server is down".
    state.streamHost.isNotBlank() -> stringResource(R.string.source_rss_host, state.streamHost)
    else -> stringResource(R.string.source_rss)
}

@Composable
private fun serverLabel(state: PlaybackUiState): String = when (state.streamReachability) {
    StreamReachability.AVAILABLE -> stringResource(R.string.server_available)
    StreamReachability.UNAVAILABLE -> stringResource(R.string.server_unavailable)
    StreamReachability.CHECKING -> stringResource(R.string.server_checking)
    StreamReachability.LOCAL -> stringResource(R.string.server_not_required)
    StreamReachability.IDLE -> stringResource(R.string.server_checking)
}

@Composable
private fun PlayerPane(
    state: PlaybackUiState,
    artworkUrl: String,
    chapters: List<DescriptionTimestamp>,
    downloaded: Boolean,
    downloadActionEnabled: Boolean,
    downloadState: EpisodeDownloadState,
    downloadProgress: Float?,
    largeControls: Boolean,
    alignMetadataStart: Boolean,
    onOpenPodcast: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    onMarkPlayed: () -> Unit,
    sourceStatusExpanded: Boolean,
    onSourceStatusExpandedChange: (Boolean) -> Unit,
    onRunDiagnostics: () -> Unit,
    onDownloadToServer: () -> Unit = {},
    // The title of what plays while this pane previews another episode (user, 5.11.2026): the source
    // chip says so and leads back, the timeline and the chapter pill do not move.
    playingElsewhere: String? = null,
    onReturnToPlaying: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state = state.live()
    val preview = playingElsewhere != null
    // D3 components of the approved mockup; the car profile scales them by 4/3.
    val componentScale = if (largeControls) 4f / 3f else 1f
    BoxWithConstraints(modifier) {
        val paneHeight = maxHeight
        PlayerPaneLayout(
            minHeight = paneHeight,
            bottomCount = 3, // chapter pill, spacer, dock
            // Pulling the pane down past its top closes the player (design audit 4.2, via nested scroll).
            modifier = Modifier
                .fillMaxWidth()
                .then(ru.e1173.nookast.designsystem.rememberPlayerDismissScroll()?.let { Modifier.nestedScroll(it) } ?: Modifier)
                .verticalScroll(rememberScrollState(), flingBehavior = deviceFling()),
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        // Car: the cover's top edge is the right panel's top edge (user, 28.10.2026).
                        top = when {
                            largeControls || alignMetadataStart -> 0.dp
                            else -> 4.dp
                        },
                    ),
            ) {
                // Phone: the fixed cover of the APK-3.5 review. Wide pane (user, 27.10.2026): the
                // cover spans the pane, so the player keeps one edge towards the divider, and its
                // height yields to a short screen so the dock never falls below the fold.
                val fixedWidth = if (largeControls) 426.67.dp else 314.dp
                val fixedHeight = if (largeControls) 406.67.dp else 290.dp
                // Square, as covers are (user, 3.11.2026: a wider-than-tall frame cropped the cover's top
                // and bottom, e.g. the number at its foot).
                val coverSide = if (alignMetadataStart) {
                    minOf(maxWidth, paneHeight * 0.42f)
                } else {
                    minOf(maxWidth, fixedWidth, fixedHeight)
                }
                val coverWidth = coverSide
                val coverHeight = coverSide
                val coverRadius = if (largeControls) 32.dp else 24.dp
                val coverColors = LocalNookastColors.current
                androidx.compose.runtime.DisposableEffect(Unit) {
                    onDispose { NowPlayingCoverBounds.value = null }
                }
                Box(
                    modifier = Modifier
                        .width(coverWidth)
                        .height(coverHeight)
                        // Centred on every screen (user, 5.11.2026: the square wide cover hugged the left edge).
                        .align(Alignment.Center)
                        .onGloballyPositioned { NowPlayingCoverBounds.value = it.boundsInRoot() }
                        // Night reference: 1 px outline and box-shadow 0 8px 24px black .5.
                        .drawBehind {
                            if (coverColors.isDark) {
                                drawCssShadow(Color.Black.copy(alpha = 0.5f), 24.dp, (coverRadius * ARTWORK_CORNER_SCALE).toPx(), dy = 8.dp)
                            }
                        }
                        .then(
                            if (coverColors.isDark) {
                                Modifier.border(1.dp, coverColors.divider.scaleAlpha(0.85f), RoundedCornerShape(coverRadius * ARTWORK_CORNER_SCALE))
                            } else {
                                Modifier
                            },
                        )
                        // The mini-player's cover grows into this one (design audit 4.1).
                        .sharedCover(
                            radius = coverRadius * ARTWORK_CORNER_SCALE,
                            otherRadius = (if (largeControls) 13.dp else 10.dp) * ARTWORK_CORNER_SCALE,
                        ),
                ) {
                    ChapterCover(
                        chapters = state.feedChapters.ifEmpty { state.nativeChapters },
                        positionMs = state.positionMs,
                        artworkUrl = artworkUrl,
                        title = state.title,
                        podcastName = state.podcastName,
                        cornerRadius = coverRadius,
                        criticalWarningSeconds = criticalWarningSeconds(state),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(
                Modifier.height(
                    when {
                        largeControls -> 24.dp
                        else -> 14.dp
                    },
                ),
            )
            // One line (APK-3.5 review): a long title steps its size down to a readable minimum,
            // and only then is cut with an ellipsis.
            val (titleMax, titleMin) = when {
                largeControls -> 28.sp to 20.sp
                alignMetadataStart -> 21.sp to 15.sp
                else -> 18.sp to 13.sp
            }
            // A title too long even at the smallest size scrolls as a marquee instead of being cut
            // (user, 29.10.2026: long titles could not be read anywhere).
            val npTitle = cleanEpisodeTitle(state.title).composed().ifBlank { stringResource(R.string.player_title) }
            var titleOverflows by remember(npTitle) { mutableStateOf(false) }
            val titleStyle = MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = ru.e1173.nookast.designsystem.NookPlayerType.titleWeight,
                textAlign = if (alignMetadataStart) TextAlign.Start else TextAlign.Center,
            )
            if (titleOverflows) {
                androidx.compose.foundation.text.BasicText(
                    npTitle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2_000, repeatDelayMillis = 2_500)
                        .clip(ru.e1173.nookast.designsystem.NookShapes.s)
                        .clickable(onClick = onOpenPodcast),
                    style = titleStyle.merge(ru.e1173.nookast.designsystem.NookPlayerType.fitted(titleMin)).copy(textAlign = TextAlign.Start),
                    maxLines = 1,
                )
            } else {
                androidx.compose.foundation.text.BasicText(
                    npTitle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ru.e1173.nookast.designsystem.NookShapes.s)
                        .clickable(onClick = onOpenPodcast),
                    style = titleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (it.hasVisualOverflow) titleOverflows = true },
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
                        minFontSize = titleMin,
                        maxFontSize = titleMax,
                        stepSize = 0.5.sp,
                    ),
                )
            }
            Spacer(Modifier.height(if (largeControls) 10.dp else 4.dp))
            Text(
                state.podcastName,
                modifier = Modifier
                    .then(if (alignMetadataStart) Modifier.fillMaxWidth() else Modifier)
                    .clip(ru.e1173.nookast.designsystem.NookShapes.s)
                    .clickable(onClick = onOpenPodcast),
                style = when {
                    largeControls -> MaterialTheme.typography.titleLarge.merge(ru.e1173.nookast.designsystem.NookPlayerType.subtitleLarge)
                    alignMetadataStart -> MaterialTheme.typography.bodyLarge.merge(ru.e1173.nookast.designsystem.NookPlayerType.subtitleStart)
                    else -> MaterialTheme.typography.bodyLarge.merge(ru.e1173.nookast.designsystem.NookPlayerType.subtitle)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (alignMetadataStart) TextAlign.Start else TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(
                Modifier.height(
                    when {
                        largeControls -> 30.dp
                        alignMetadataStart -> 15.dp
                        else -> 6.dp
                    },
                ),
            )
            NookTimeline(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                chapterStartsMs = chapters.map(DescriptionTimestamp::positionMs),
                onSeek = onSeekTo,
                modifier = Modifier.padding(horizontal = 4.dp),
                scale = componentScale,
                enabled = !preview,
            )
            NookTimecodes(
                elapsed = formatPlaybackTime(state.positionMs),
                // Both from whole seconds, so they tick together (user, 5.11.2026: with a duration of
                // 38:31.236 the remaining time ticked a quarter second after the elapsed one).
                remaining = "−${formatPlaybackTime((state.durationMs / 1_000 - state.positionMs / 1_000).coerceAtLeast(0) * 1_000)}",
                modifier = Modifier.padding(horizontal = 4.dp),
                scale = componentScale,
                center = {
                    // DEC-029: the source chip lives under the timeline, centred between the times.
                    val reach = rememberSettledReachability(state.streamReachability, waiting = state.isPlaying)
                    if (playingElsewhere != null) {
                        NookSourceChip(
                            label = stringResource(R.string.preview_other_playing),
                            quality = null,
                            health = SourceHealth.HEALTHY,
                            busy = false,
                            onClick = onReturnToPlaying,
                            contentDescription = stringResource(R.string.return_to_now_playing, playingElsewhere),
                            scale = componentScale,
                            glyph = { PlayingBars(Modifier.size(11.dp * componentScale)) },
                        )
                        return@NookTimecodes
                    }
                    Box {
                        NookSourceChip(
                            label = stringResource(
                                when {
                                    state.isLocalSource -> R.string.badge_local
                                    state.isServerCopySource -> R.string.badge_server
                                    reach == StreamReachability.UNAVAILABLE -> R.string.badge_unavailable
                                    reach == StreamReachability.AVAILABLE -> R.string.badge_stream
                                    else -> R.string.badge_checking
                                },
                            ),
                            quality = qualityLabel(state.bitrateKbps),
                            health = when {
                                state.isLocalSource -> SourceHealth.LOCAL
                                reach == StreamReachability.UNAVAILABLE -> SourceHealth.UNAVAILABLE
                                reach == StreamReachability.AVAILABLE -> SourceHealth.HEALTHY
                                else -> SourceHealth.CHECKING
                            },
                            busy = state.isBuffering,
                            onClick = { onSourceStatusExpandedChange(!sourceStatusExpanded) },
                            contentDescription = stringResource(R.string.open_source_status),
                            scale = componentScale,
                        )
                        if (sourceStatusExpanded) {
                            val density = LocalDensity.current
                            Popup(
                                alignment = Alignment.BottomCenter,
                                offset = with(density) {
                                    // The card keeps its place; the window grows by the shadow's room below it.
                                    androidx.compose.ui.unit.IntOffset(
                                        0,
                                        -(32.dp * componentScale).roundToPx() + ru.e1173.nookast.designsystem.FloatingShadowMargin.Bottom.roundToPx(),
                                    )
                                },
                                onDismissRequest = { onSourceStatusExpandedChange(false) },
                                properties = PopupProperties(focusable = true),
                            ) {
                                // DEC-024 §2.6: grows from the chip (scale 0.88, rises 24 dp) on a 340/0.80 spring.
                                ru.e1173.nookast.designsystem.InterfaceScaled {
                                val appear = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
                                androidx.compose.animation.AnimatedVisibility(
                                    visibleState = appear,
                                    enter = scaleIn(
                                        ru.e1173.nookast.designsystem.NookMotion.spatial(),
                                        initialScale = 0.88f,
                                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f),
                                    ) + slideInVertically(ru.e1173.nookast.designsystem.NookMotion.spatial()) { with(density) { 24.dp.roundToPx() } } + fadeIn(ru.e1173.nookast.designsystem.NookMotion.fast()),
                                ) {
                                    androidx.compose.foundation.layout.Box(
                                        Modifier.padding(
                                            start = ru.e1173.nookast.designsystem.FloatingShadowMargin.Side,
                                            end = ru.e1173.nookast.designsystem.FloatingShadowMargin.Side,
                                            top = ru.e1173.nookast.designsystem.FloatingShadowMargin.Top,
                                            bottom = ru.e1173.nookast.designsystem.FloatingShadowMargin.Bottom,
                                        ),
                                    ) {
                                    SourceStatusPopover(
                                        state = state,
                                        wide = alignMetadataStart,
                                        largeControls = largeControls,
                                        onRunDiagnostics = onRunDiagnostics,
                                        onDownloadToServer = onDownloadToServer,
                                        pointerAtBottomStart = true,
                                    )
                                    }
                                }
                                }
                            }
                        }
                    }
                },
            )
            Spacer(
                Modifier.height(
                    when {
                        largeControls -> 28.dp
                        alignMetadataStart -> 16.dp
                        else -> 10.dp
                    },
                ),
            )
            NookTransport(
                playing = state.isPlaying,
                buffering = state.isBuffering,
                enabled = state.isConnected && !state.isBuffering,
                seekBackSeconds = state.seekBackSeconds,
                seekForwardSeconds = state.seekForwardSeconds,
                labels = TransportLabels(
                    seekBack = stringResource(R.string.seek_back_seconds, state.seekBackSeconds),
                    seekForward = stringResource(R.string.seek_forward_seconds, state.seekForwardSeconds),
                    play = stringResource(R.string.play),
                    pause = stringResource(R.string.pause),
                ),
                onSeekBack = onSeekBack,
                onPlayPause = onTogglePlayback,
                onSeekForward = onSeekForward,
                scale = componentScale,
            )
            // Always shown: the current chapter, or that the episode has none (user, APK-3.1).
            Spacer(Modifier.height(if (largeControls) 16.dp else 12.dp))
            if (chapters.isEmpty()) {
                NookChapterPill(
                    title = stringResource(R.string.no_chapters),
                    previousLabel = stringResource(R.string.previous_chapter),
                    nextLabel = stringResource(R.string.next_chapter),
                    onPrevious = null,
                    onNext = null,
                    placeholder = true,
                    playing = state.isPlaying,
                    scale = componentScale,
                    modifier = if (preview) Modifier.alpha(0.45f) else Modifier,
                )
            } else {
                val currentIndex = chapters.indexOfLast { it.positionMs <= state.positionMs }.coerceAtLeast(0)
                val current = chapters[currentIndex]
                // "Previous" restarts the current chapter after its first 3 s, as before.
                val previousIndex = if (state.positionMs - current.positionMs > 3_000L) currentIndex else currentIndex - 1
                val nextIndex = currentIndex + 1
                NookChapterPill(
                    title = current.label,
                    previousLabel = stringResource(R.string.previous_chapter),
                    nextLabel = stringResource(R.string.next_chapter),
                    onPrevious = chapters.getOrNull(previousIndex)?.takeUnless { preview }?.let { { onSeekTo(it.positionMs) } },
                    onNext = chapters.getOrNull(nextIndex)?.takeUnless { preview }?.let { { onSeekTo(it.positionMs) } },
                    modifier = if (preview) Modifier.alpha(0.45f) else Modifier,
                    playing = state.isPlaying,
                    chapterIndex = currentIndex,
                    scale = componentScale,
                )
            }
            Spacer(Modifier.height(if (largeControls) 16.dp else 12.dp))
            NookActionDock(
                downloadState = when (downloadState) {
                    EpisodeDownloadState.QUEUED, EpisodeDownloadState.DOWNLOADING -> DockDownloadState.DOWNLOADING
                    EpisodeDownloadState.DOWNLOADED -> DockDownloadState.DOWNLOADED
                    else -> DockDownloadState.IDLE
                },
                downloadProgress = downloadProgress,
                downloadLabel = stringResource(
                    if (downloadState == EpisodeDownloadState.DOWNLOADED) R.string.delete_download else R.string.download_episode,
                ),
                downloadEnabled = downloadActionEnabled || downloadState == EpisodeDownloadState.DOWNLOADED,
                playedLabel = stringResource(if (state.isMarkedPlayed) R.string.played else R.string.mark_played),
                played = state.isMarkedPlayed,
                onDownload = if (downloadState == EpisodeDownloadState.DOWNLOADED) onDeleteDownload else onDownload,
                onPlayed = onMarkPlayed,
                // Car: the dock sits on the screen's bottom margin, level with the bottom of the
                // right panel (user, 28.10.2026). Phone and Fold keep their own spacing.
                modifier = Modifier.padding(
                    bottom = when {
                        largeControls -> 0.dp
                        alignMetadataStart -> 16.dp
                        else -> 12.dp
                    },
                ),
                scale = componentScale,
            )
        }
    }
}

/**
 * Player column in two groups, children centred horizontally: the top group (cover, titles,
 * timeline, transport) sits right under the header, the last [bottomCount] children (chapter pill
 * and dock) are anchored to the bottom edge, and all spare height goes between them (APK-3.5
 * review). [minHeight] is the visible height; taller content scrolls.
 */
@Composable
private fun PlayerPaneLayout(
    minHeight: Dp,
    bottomCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val placeables = measurables.map { it.measure(loose) }
        val total = placeables.sumOf { it.height }
        val height = maxOf(total, minHeight.roundToPx())
        val spare = height - total
        val firstBottom = (placeables.size - bottomCount).coerceAtLeast(0)
        // The transport floats: half of the spare height above it, half above the bottom group.
        val transportIndex = (firstBottom - 2).coerceAtLeast(1)
        layout(constraints.maxWidth, height) {
            var y = 0
            placeables.forEachIndexed { index, placeable ->
                if (index == transportIndex) y += spare / 2
                if (index == firstBottom) y += spare - spare / 2
                placeable.placeRelative((constraints.maxWidth - placeable.width) / 2, y)
                y += placeable.height
            }
        }
    }
}

// The user asked for 7 and 3 lines instead of the 13 and 5 of the Stitch description (APK-3.4 review).
private const val EPISODE_DESCRIPTION_LINES = 7
private const val PODCAST_DESCRIPTION_LINES = 3

/** Height of [lines] description lines: RichDescriptionText flows words with a 4 dp line gap. */
@Composable
private fun descriptionLinesHeight(style: androidx.compose.ui.text.TextStyle, lines: Int): Dp =
    with(LocalDensity.current) { (style.lineHeight.toDp() + 4.dp) * lines }

@Composable
private fun DescriptionPane(
    currentEntry: QueueListEntry?,
    mediaSizeBytes: Long?,
    bitrateKbps: Int?,
    podcastDetails: PodcastDetails?,
    podcastDetailsLoading: Boolean,
    chapters: List<DescriptionTimestamp>,
    positionMs: Long,
    onSelectChapter: (Long) -> Unit,
    modifier: Modifier = Modifier,
    showEpisodeSummary: Boolean = false,
    downloaded: Boolean = false,
    largeControls: Boolean = false,
    onOpenPodcast: () -> Unit,
    onSeekTo: (Long) -> Unit,
    topInset: Dp = 0.dp,
    onScrolledChange: (Boolean) -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    val episodeDescription = currentEntry.descriptionOrCached(podcastDetails)
    val podcastDescription = podcastDetails?.description.orEmpty()
    BoxWithConstraints(modifier) {
        val compact = !largeControls && maxWidth < 500.dp
        val contentPadding = when {
            largeControls -> 20.dp
            compact -> 11.dp
            else -> 12.dp
        }
        // The text scrolls under the frosted header, which turns to glass once it does (user, 29.10.2026).
        val scroll = rememberScrollState()
        val scrolled by remember { androidx.compose.runtime.derivedStateOf { scroll.value > 0 } }
        androidx.compose.runtime.LaunchedEffect(scrolled) { onScrolledChange(scrolled) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll, flingBehavior = deviceFling())
                .padding(top = topInset)
                .padding(
                    horizontal = contentPadding,
                    // Car: the same inset from the panel edge on all sides (user, 28.10.2026:
                    // the Description block almost touched the panel's top edge).
                    vertical = when {
                        largeControls -> 20.dp
                        compact -> 3.dp
                        else -> contentPadding
                    },
                ),
                verticalArrangement = Arrangement.spacedBy(
                    when {
                        largeControls -> 12.dp
                        compact -> 7.dp
                        else -> 10.dp
                    },
                ),
        ) {
            if (showEpisodeSummary) {
                Surface(
                    onClick = onOpenPodcast, // Stitch §3.1: the summary leads to the podcast page
                    modifier = Modifier.fillMaxWidth(),
                    shape = ru.e1173.nookast.designsystem.NookShapes.m,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(3.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(15.dp),
                    ) {
                        Artwork(
                            url = currentEntry?.artworkUrl.orEmpty(),
                            title = currentEntry?.episodeTitle.orEmpty(),
                            fallbackUrl = podcastArtworkFallback(currentEntry?.podcastName),
                            downloaded = downloaded,
                            badgeSize = 20.dp,
                            modifier = Modifier.width(124.dp).height(114.dp),
                        )
                        // The podcast only: the episode title is read in full in the Description card
                        // below, so it is not shown twice (user, 29.10.2026).
                        Column(Modifier.weight(1f).padding(top = 9.dp, end = 10.dp)) {
                            Text(
                                currentEntry?.podcastName.orEmpty(),
                                style = MaterialTheme.typography.titleMedium.merge(ru.e1173.nookast.designsystem.NookPlayerType.bold),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (chapters.isNotEmpty()) {
                DetailCard(
                    stringResource(R.string.chapters_title),
                    // Timestamps found in the description are marked as such (user, 29.10.2026).
                    badge = if (chapters.all(DescriptionTimestamp::fromDescription)) stringResource(R.string.chapters_from_description) else null,
                    compact = compact,
                    largeControls = largeControls,
                    compactBottomPadding = if (largeControls) 14.dp else 10.dp,
                ) {
                    ChapterList(
                        chapters = chapters,
                        positionMs = positionMs,
                        largeControls = largeControls,
                        onSelect = onSelectChapter,
                    )
                }
            }
            DetailCard(
                stringResource(R.string.episode_description_title),
                compact = compact,
                largeControls = largeControls,
                compactBottomPadding = if (largeControls) 14.dp else 13.dp,
            ) {
                // The whole title, however long: the one place it can always be read (user, 29.10.2026).
                currentEntry?.episodeTitle?.takeIf(String::isNotBlank)?.let { title ->
                    Text(
                        cleanEpisodeTitle(title),
                        style = (if (largeControls) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium).merge(ru.e1173.nookast.designsystem.NookPlayerType.bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                // One metadata strip without wraps (DEC-025, Stitch §3.2): season and number • date • length • size • bitrate.
                val durationLabel = currentEntry?.durationSeconds?.takeIf { it > 0 }?.let {
                    minutesLabel((it / 60).coerceAtLeast(1))
                }
                val bitrateLabel = bitrateKbps?.let { stringResource(R.string.audio_bitrate_kbps, it) }
                val metadata = listOfNotNull(
                    currentEntry?.let { entry ->
                        val number = LocalEpisodeNumbers.current[entry.episodeId]?.takeUnless(EpisodeNumber::isEmpty)
                            ?: episodeTitleParts(entry.episodeTitle.orEmpty()).number
                        episodeNumberLabel(number)
                    },
                    currentEntry?.publishedAt?.takeIf(String::isNotBlank)?.let { formatPublishedDate(it, locale) },
                    durationLabel,
                    mediaSizeBytes?.let { Formatter.formatShortFileSize(context, it) },
                    bitrateLabel,
                )
                if (metadata.isNotEmpty()) {
                    Text(
                        metadata.joinToString(" • "),
                        style = if (largeControls) MaterialTheme.typography.bodyMedium
                        else MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(if (compact) 10.dp else 7.dp))
                }
                val episodeStyle = if (largeControls) {
                    MaterialTheme.typography.bodyMedium.merge(ru.e1173.nookast.designsystem.NookPlayerType.descriptionLarge)
                } else if (compact) {
                    MaterialTheme.typography.bodyMedium.merge(ru.e1173.nookast.designsystem.NookPlayerType.description)
                } else {
                    MaterialTheme.typography.bodyMedium.merge(ru.e1173.nookast.designsystem.NookPlayerType.descriptionWide)
                }
                NookExpandable(
                    collapsedHeight = descriptionLinesHeight(episodeStyle, EPISODE_DESCRIPTION_LINES),
                    moreLabel = stringResource(R.string.description_more),
                    lessLabel = stringResource(R.string.description_less),
                ) {
                    RichDescriptionText(
                        html = episodeDescription,
                        fallback = stringResource(R.string.description_unavailable),
                        style = episodeStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        onSeekTo = onSeekTo,
                        hideTimestampList = true,
                    )
                }
            }
            DetailCard(
                stringResource(R.string.about_podcast),
                compact = compact,
                largeControls = largeControls,
                compactBottomPadding = if (largeControls) 14.dp else 12.dp,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Artwork(
                        url = podcastDetails?.artworkUrl.orEmpty()
                            .ifBlank { currentEntry?.artworkUrl.orEmpty() },
                        title = podcastDetails?.name.orEmpty()
                            .ifBlank { currentEntry?.podcastName.orEmpty() },
                        downloaded = false,
                        badgeSize = 0.dp,
                        modifier = Modifier.size(
                            when {
                                largeControls -> 64.dp
                                compact -> 50.dp
                                else -> 56.dp
                            },
                        ),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            podcastDetails?.name.orEmpty().ifBlank { currentEntry?.podcastName.orEmpty() },
                            style = (if (largeControls) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium).merge(ru.e1173.nookast.designsystem.NookPlayerType.bold),
                        )
                        podcastDetails?.author?.takeIf(String::isNotBlank)?.let {
                            Text(
                                stringResource(R.string.podcast_author_label, it),
                                style = if (largeControls) MaterialTheme.typography.bodyMedium
                                else MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(if (compact) 14.dp else 8.dp))
                if (podcastDetailsLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    val podcastStyle = if (largeControls) {
                        MaterialTheme.typography.bodyMedium.merge(ru.e1173.nookast.designsystem.NookPlayerType.descriptionLarge)
                    } else if (compact) {
                        MaterialTheme.typography.bodyMedium.merge(ru.e1173.nookast.designsystem.NookPlayerType.description)
                    } else {
                        MaterialTheme.typography.bodyMedium.merge(ru.e1173.nookast.designsystem.NookPlayerType.descriptionWide)
                    }
                    NookExpandable(
                        collapsedHeight = descriptionLinesHeight(podcastStyle, PODCAST_DESCRIPTION_LINES),
                        moreLabel = stringResource(R.string.description_more),
                        lessLabel = stringResource(R.string.description_less),
                    ) {
                        RichDescriptionText(
                            html = podcastDescription,
                            fallback = stringResource(R.string.description_unavailable),
                            style = podcastStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!podcastDetails?.categories.isNullOrEmpty()) {
                    Spacer(Modifier.height(if (compact) 8.dp else 9.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState(), flingBehavior = deviceFling()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        podcastDetails.categories.forEach { category ->
                            Surface(
                                shape = ru.e1173.nookast.designsystem.NookShapes.s,
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    category,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    style = if (largeControls) MaterialTheme.typography.bodyMedium
                                    else MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(if (compact) 16.dp else 10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    podcastDetails?.episodeCount?.let { count ->
                        StatChip(
                            pluralStringResource(R.plurals.queue_episode_count, count, count),
                            Modifier.weight(1f),
                            leadingIcon = { ListGlyph(Modifier.size(14.dp)) },
                            onClick = onOpenPodcast,
                        )
                    }
                    podcastDetails?.episodesPlayed?.let { count ->
                        StatChip(
                            pluralStringResource(R.plurals.played_episode_count, count, count),
                            Modifier.weight(1f),
                            leadingIcon = {
                                Box(
                                    Modifier.size(16.dp).background(
                                        MaterialTheme.colorScheme.primary,
                                        CircleShape,
                                    ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CheckGlyph(
                                        Modifier.size(9.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            },
                            onClick = onOpenPodcast,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Chapters of the Description tab: start time and title; the playing chapter is tinted. A tap
 * seeks there and returns to Playback without changing play or pause (user, APK-3.4 review).
 */
@Composable
internal fun ChapterList(
    chapters: List<DescriptionTimestamp>,
    positionMs: Long,
    largeControls: Boolean,
    onSelect: (Long) -> Unit,
) {
    val colors = LocalNookastColors.current
    val currentLabel = stringResource(R.string.current_chapter)
    val currentIndex = chapters.indexOfLast { it.positionMs <= positionMs }
    val textStyle = if (largeControls) {
        MaterialTheme.typography.bodyLarge.merge(ru.e1173.nookast.designsystem.NookPlayerType.chaptersLarge)
    } else {
        MaterialTheme.typography.bodyMedium.merge(ru.e1173.nookast.designsystem.NookPlayerType.chapters)
    }
    // The times form one right-aligned column (user, 31.10.2026: 0:00 and 14:23 started the titles at
    // different places), as wide as the longest time.
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val timeStyle = textStyle.merge(ru.e1173.nookast.designsystem.NookPlayerType.chapterTime)
    val timeWidth = remember(chapters, timeStyle) {
        val widest = chapters.maxOfOrNull { measurer.measure(formatPlaybackTime(it.positionMs), timeStyle).size.width } ?: 0
        with(density) { widest.toDp() }
    }
    // Tighter rows (user, 29.10.2026): the list reads as one block, still a comfortable target.
    Column {
        chapters.forEachIndexed { index, chapter ->
            val current = index == currentIndex
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(ru.e1173.nookast.designsystem.NookShapes.s)
                    .background(if (current) colors.tintContainer else Color.Transparent)
                    .clickable(role = Role.Button) { onSelect(chapter.positionMs) }
                    .semantics { if (current) stateDescription = currentLabel }
                    .heightIn(min = if (largeControls) 48.dp else 36.dp)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    formatPlaybackTime(chapter.positionMs),
                    style = textStyle.merge(ru.e1173.nookast.designsystem.NookPlayerType.chapterTime),
                    modifier = Modifier.width(timeWidth),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    color = colors.accentText,
                )
                Text(
                    chapter.label,
                    modifier = Modifier.weight(1f),
                    style = textStyle,
                    color = if (current) colors.textPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = ru.e1173.nookast.designsystem.NookPlayerType.chapterWeight(current),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DetailCard(
    title: String,
    badge: String? = null,
    compact: Boolean = false,
    largeControls: Boolean = false,
    compactBottomPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.nookCard()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = if (largeControls) 18.dp else 12.dp,
                    end = if (largeControls) 18.dp else 12.dp,
                    top = when {
                        largeControls -> 12.dp
                        compact -> 6.dp
                        else -> 10.dp
                    },
                    bottom = when {
                        largeControls -> compactBottomPadding
                        compact -> compactBottomPadding
                        else -> 10.dp
                    },
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    title,
                    style = if (largeControls) MaterialTheme.typography.titleMedium.merge(ru.e1173.nookast.designsystem.NookPlayerType.cardTitle) else MaterialTheme.typography.titleMedium.merge(ru.e1173.nookast.designsystem.NookPlayerType.bold),
                )
                if (badge != null) {
                    // A quiet capsule, like the secondary tags of Apple's apps.
                    Text(
                        badge,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f), ru.e1173.nookast.designsystem.NookShapes.full)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.merge(if (largeControls) ru.e1173.nookast.designsystem.NookPlayerType.cardBadgeLarge else ru.e1173.nookast.designsystem.NookPlayerType.cardBadge),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
            content()
        }
    }
}

@Composable
private fun StatChip(
    text: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.then(
            if (onClick == null) Modifier else Modifier.clip(ru.e1173.nookast.designsystem.NookShapes.s).clickable(onClick = onClick),
        ),
        shape = ru.e1173.nookast.designsystem.NookShapes.s,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            leadingIcon?.invoke()
            if (leadingIcon != null) Spacer(Modifier.width(7.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
    }
}

internal data class QueueDragTarget(
    val index: Int,
    val centerY: Float,
)

/**
 * Where the dragged row belongs: it passes a neighbour only once the finger crosses that
 * neighbour's centre in the direction of travel. Choosing the nearest centre made rows of
 * different heights swap back and forth near the boundary (jitter, APK-3.2 review).
 */
internal fun queueDragTargetIndex(
    pointerCenterY: Float,
    visibleTargets: List<QueueDragTarget>,
    currentIndex: Int,
): Int {
    if (currentIndex < 0 || visibleTargets.isEmpty()) return currentIndex
    val others = visibleTargets.filter { it.index != currentIndex }.sortedBy { it.index }
    var target = currentIndex
    for (next in others.filter { it.index > currentIndex }) {
        if (pointerCenterY > next.centerY) target = next.index else break
    }
    if (target != currentIndex) return target
    for (previous in others.filter { it.index < currentIndex }.asReversed()) {
        if (pointerCenterY < previous.centerY) target = previous.index else break
    }
    return target
}

@Composable
private fun UpNextPanel(
    entries: List<QueueListEntry>,
    downloadStates: Map<Int, EpisodeDownloadState>,
    downloadProgress: Map<Int, Float?>,
    currentEpisodeId: Int?,
    compact: Boolean,
    largeControls: Boolean,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    alignWithDescription: Boolean = false,
    topInset: Dp = 0.dp,
    onScrolledChange: (Boolean) -> Unit = {},
    onPlayEpisode: (Int) -> Unit,
    onMoveEpisode: (Int, Int) -> Unit,
    onRemoveEpisode: (Int) -> Unit,
    onDownloadEpisode: (QueueListEntry) -> Unit,
    onDeleteDownloadEpisode: (Int) -> Unit,
    onMarkEpisodePlayed: (QueueListEntry) -> Unit,
    swipeStartAction: QueueSwipeAction,
    swipeEndAction: QueueSwipeAction,
) {
    val visualEntries = remember {
        mutableStateListOf<QueueListEntry>().apply { addAll(entries) }
    }
    val listState = rememberLazyListState()
    val listScrolled by remember { androidx.compose.runtime.derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }
    androidx.compose.runtime.LaunchedEffect(listScrolled) { onScrolledChange(listScrolled) }
    val density = LocalDensity.current
    val autoScrollEdgePx = with(density) { 64.dp.toPx() }
    val maxAutoScrollStepPx = with(density) { 22.dp.toPx() }
    var draggedEpisodeId by remember { mutableStateOf<Int?>(null) }
    var draggedOriginalIndex by remember { mutableIntStateOf(-1) }
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var draggedPointerCenterY by remember { mutableFloatStateOf(0f) }

    fun moveDraggedEntryToPointer() {
        val episodeId = draggedEpisodeId ?: return
        val candidates = listState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
            val candidateEpisodeId = item.key as? Int ?: return@mapNotNull null
            val candidateIndex = visualEntries.indexOfFirst {
                it.episodeId == candidateEpisodeId
            }
            if (candidateIndex < 0) null else QueueDragTarget(candidateIndex, item.offset + item.size / 2f)
        }
        val fromIndex = visualEntries.indexOfFirst { it.episodeId == episodeId }
        val targetIndex = queueDragTargetIndex(
            pointerCenterY = draggedPointerCenterY,
            visibleTargets = candidates,
            currentIndex = fromIndex,
        )
        if (fromIndex >= 0 && targetIndex >= 0 && targetIndex != fromIndex) {
            // The lazy list keeps its first visible item in place by key; when that item moves,
            // hold the scroll position instead, or the whole list shifts under the finger.
            val firstVisible = listState.firstVisibleItemIndex
            val firstOffset = listState.firstVisibleItemScrollOffset
            val moved = visualEntries.removeAt(fromIndex)
            visualEntries.add(targetIndex, moved)
            draggedIndex = targetIndex
            listState.requestScrollToItem(firstVisible, firstOffset)
        }
    }

    LaunchedEffect(entries, draggedEpisodeId) {
        if (draggedEpisodeId == null && visualEntries != entries) {
            visualEntries.clear()
            visualEntries.addAll(entries)
        }
    }
    LaunchedEffect(draggedEpisodeId) {
        while (draggedEpisodeId != null) {
            withFrameNanos { }
            val layoutInfo = listState.layoutInfo
            val viewportStart = layoutInfo.viewportStartOffset.toFloat()
            val viewportEnd = layoutInfo.viewportEndOffset.toFloat()
            val scrollDelta = when {
                draggedPointerCenterY < viewportStart + autoScrollEdgePx -> {
                    val pressure = ((viewportStart + autoScrollEdgePx - draggedPointerCenterY) /
                        autoScrollEdgePx).coerceIn(0f, 1f)
                    -maxAutoScrollStepPx * pressure
                }
                draggedPointerCenterY > viewportEnd - autoScrollEdgePx -> {
                    val pressure = ((draggedPointerCenterY - (viewportEnd - autoScrollEdgePx)) /
                        autoScrollEdgePx).coerceIn(0f, 1f)
                    maxAutoScrollStepPx * pressure
                }
                else -> 0f
            }
            if (abs(scrollDelta) >= 0.5f) {
                listState.scrollBy(scrollDelta)
                moveDraggedEntryToPointer()
            }
        }
    }
    LazyColumn(
        flingBehavior = deviceFling(),
        modifier = modifier.clipToBounds(),
        state = listState,
        userScrollEnabled = draggedEpisodeId == null,
        // Beside the player the queue starts where the Description cards start, level with the
        // cover (user, 29.10.2026).
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            // Rows run nearly edge to edge (user, 29.10.2026: wider queue).
            start = if (largeControls) 12.dp else 2.dp,
            end = if (largeControls) 12.dp else 2.dp,
            top = topInset + if (alignWithDescription) (if (largeControls) 20.dp else 12.dp) else if (largeControls) 3.dp else 10.dp,
            bottom = LocalBottomInset.current + if (largeControls) 3.dp else 10.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(
            when {
                largeControls -> 8.dp
                compact -> 6.dp
                else -> 6.dp
            },
        ),
    ) {
        if (showTitle) {
            item(key = "up-next-heading") {
                Text(
                    stringResource(R.string.queue_title),
                    style = if (largeControls) MaterialTheme.typography.headlineSmall.merge(ru.e1173.nookast.designsystem.NookPlayerType.queueTitleLarge) else MaterialTheme.typography.titleLarge.merge(ru.e1173.nookast.designsystem.NookPlayerType.bold),
                    modifier = Modifier.padding(
                        start = if (largeControls) 1.33.dp else 3.dp,
                        end = 3.dp,
                        top = if (alignWithDescription) 0.dp else 3.dp,
                        bottom = if (largeControls) 7.dp else 3.dp,
                    ),
                )
            }
        }
        if (entries.isEmpty()) {
            item { ru.e1173.nookast.designsystem.NookEmptyState(stringResource(R.string.queue_empty), message = stringResource(R.string.empty_queue_message)) }
        }
        itemsIndexed(visualEntries, key = { _, entry -> entry.episodeId }) { index, entry ->
            val dragging = draggedEpisodeId == entry.episodeId
            Column(
                modifier = (if (dragging) Modifier else Modifier.animateItem())
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer {
                        // Read in the layer phase, after this frame's layout: composition saw the
                        // previous frame's offsets, so after each swap the dragged row jumped for a
                        // frame over its neighbours (APK-3.4 review).
                        translationY = if (dragging) {
                            listState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.key == entry.episodeId }
                                ?.let { draggedPointerCenterY - (it.offset + it.size / 2f) } ?: 0f
                        } else {
                            0f
                        }
                    },
            ) {
                UpNextRow(
                entry = entry,
                isCurrent = entry.episodeId == currentEpisodeId,
                downloadState = downloadStates[entry.episodeId]
                    ?: EpisodeDownloadState.NOT_DOWNLOADED,
                downloadProgress = downloadProgress[entry.episodeId],
                compact = compact,
                largeControls = largeControls,
                canMoveUp = index > 0,
                canMoveDown = index < visualEntries.lastIndex,
                onPlay = { onPlayEpisode(index) },
                onMoveUp = { onMoveEpisode(entry.episodeId, index - 1) },
                onMoveDown = { onMoveEpisode(entry.episodeId, index + 1) },
                onRemove = { onRemoveEpisode(entry.episodeId) },
                onDownload = { onDownloadEpisode(entry) },
                onDeleteDownload = { onDeleteDownloadEpisode(entry.episodeId) },
                onMarkPlayed = { onMarkEpisodePlayed(entry) },
                swipeStartAction = swipeStartAction,
                swipeEndAction = swipeEndAction,
                dragging = dragging,
                onDragStart = { pointerOffsetFromHandleCenter ->
                    draggedEpisodeId = entry.episodeId
                    draggedOriginalIndex = index
                    draggedIndex = index
                    val itemInfo = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.key == entry.episodeId }
                    draggedPointerCenterY = (itemInfo?.let { it.offset + it.size / 2f } ?: 0f) +
                        pointerOffsetFromHandleCenter
                },
                onDrag = drag@{ delta ->
                    val layoutInfo = listState.layoutInfo
                    val viewportStart = layoutInfo.viewportStartOffset.toFloat()
                    val viewportEnd = layoutInfo.viewportEndOffset.toFloat()
                    draggedPointerCenterY = (draggedPointerCenterY + delta)
                        .coerceIn(viewportStart, viewportEnd)
                    moveDraggedEntryToPointer()
                },
                onDragEnd = {
                    val destination = draggedIndex
                    val original = draggedOriginalIndex
                    if (original >= 0 && destination >= 0 && original != destination) {
                        onMoveEpisode(entry.episodeId, destination)
                    }
                    draggedEpisodeId = null
                    draggedOriginalIndex = -1
                    draggedIndex = -1
                    draggedPointerCenterY = 0f
                },
                onDragCancel = {
                    draggedEpisodeId = null
                    draggedOriginalIndex = -1
                    draggedIndex = -1
                    draggedPointerCenterY = 0f
                    visualEntries.clear()
                    visualEntries.addAll(entries)
                },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun UpNextRow(
    entry: QueueListEntry,
    isCurrent: Boolean,
    downloadState: EpisodeDownloadState,
    downloadProgress: Float?,
    compact: Boolean,
    largeControls: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPlay: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    onMarkPlayed: () -> Unit,
    swipeStartAction: QueueSwipeAction,
    swipeEndAction: QueueSwipeAction,
    dragging: Boolean,
    onDragStart: (Float) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var swipeOffset by remember(entry.episodeId) { mutableFloatStateOf(0f) }
    val swipeHaptics = ru.e1173.nookast.designsystem.rememberNookHaptics()
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    val revealPx = with(density) { 88.dp.toPx() }
    val reorderHandleSize = if (compact) 44.dp else 48.dp
    val reorderHandleCenterPx = with(density) { reorderHandleSize.toPx() / 2f }
    val reorderDescription = stringResource(R.string.reorder_episode)
    val durationSeconds = (entry.durationSeconds ?: 0).coerceAtLeast(0)
    val listenedSeconds = (entry.listenDurationSeconds ?: 0.0).coerceAtLeast(0.0)
    val progress = if (durationSeconds > 0) {
        (listenedSeconds / durationSeconds).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    val remainingMinutes = ceil(
        (durationSeconds - listenedSeconds).coerceAtLeast(0.0) / 60.0,
    ).toInt().coerceAtLeast(1)
    // D3, nextup-normal-b: 56 dp cover radius 16, 10 dp padding, 12 dp gap, title 14/700.
    val artworkSize = when {
        largeControls -> 75.dp
        compact -> 52.dp
        else -> 56.dp
    }
    val rowHeight = when {
        largeControls -> 102.67.dp
        else -> artworkSize + when {
            compact -> 12.dp
            else -> 16.dp
        }
    }
    val rowShape = RoundedCornerShape(if (largeControls) ru.e1173.nookast.designsystem.NookShapes.xlRadius else ru.e1173.nookast.designsystem.NookShapes.lRadius)
    fun confirmSwipeAction(action: QueueSwipeAction) {
        when (action) {
            QueueSwipeAction.NONE -> Unit
            QueueSwipeAction.DOWNLOAD_OR_DELETE -> when (downloadState) {
                EpisodeDownloadState.DOWNLOADED -> onDeleteDownload()
                EpisodeDownloadState.QUEUED,
                EpisodeDownloadState.DOWNLOADING,
                -> Unit
                else -> onDownload()
            }
            QueueSwipeAction.MARK_PLAYED -> onMarkPlayed()
            QueueSwipeAction.REMOVE_FROM_QUEUE -> onRemove()
        }
        swipeOffset = 0f
    }
    Box(modifier.fillMaxWidth().heightIn(min = rowHeight).height(IntrinsicSize.Min).clip(rowShape)) {
        Box(Modifier.fillMaxSize()) {
            if (swipeStartAction != QueueSwipeAction.NONE) {
                QueueSwipeActionButton(
                    action = swipeStartAction,
                    downloadState = downloadState,
                    onClick = { confirmSwipeAction(swipeStartAction) },
                    modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().width(88.dp),
                )
            }
            if (swipeEndAction != QueueSwipeAction.NONE) {
                QueueSwipeActionButton(
                    action = swipeEndAction,
                    downloadState = downloadState,
                    onClick = { confirmSwipeAction(swipeEndAction) },
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(88.dp),
                )
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .offset { IntOffset(swipeOffset.roundToInt(), 0) }
                .pointerInput(entry.episodeId, swipeStartAction, swipeEndAction) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            swipeOffset = when {
                                swipeOffset > revealPx * 0.42f &&
                                    swipeStartAction != QueueSwipeAction.NONE -> revealPx
                                swipeOffset < -revealPx * 0.42f &&
                                    swipeEndAction != QueueSwipeAction.NONE -> -revealPx
                                else -> 0f
                            }
                        },
                        onDragCancel = { swipeOffset = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val candidate = (swipeOffset + dragAmount).coerceIn(-revealPx, revealPx)
                            val previous = swipeOffset
                            swipeOffset = when {
                                candidate > 0f && swipeStartAction == QueueSwipeAction.NONE -> 0f
                                candidate < 0f && swipeEndAction == QueueSwipeAction.NONE -> 0f
                                else -> candidate
                            }
                            // Crossing the point where letting go acts (design audit 4.4).
                            if ((kotlin.math.abs(previous) > revealPx * 0.42f) != (kotlin.math.abs(swipeOffset) > revealPx * 0.42f)) swipeHaptics.threshold()
                        },
                    )
                },
            shape = rowShape,
            color = when {
                dragging -> MaterialTheme.colorScheme.surface
                isCurrent -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.background
            },
        ) {
            Row(
                modifier = Modifier
                .fillMaxWidth()
                .clickable { if (abs(swipeOffset) < 1f) onPlay() }
                .padding(
                    start = if (largeControls) 9.dp else 4.dp,
                    end = 0.dp,
                    top = if (largeControls) 9.dp else 7.dp,
                    bottom = if (largeControls) 9.dp else 7.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                when {
                    largeControls -> 16.dp
                    compact -> 10.dp
                    else -> 10.dp
                },
            ),
            ) {
            Artwork(
                url = entry.artworkUrl.orEmpty(),
                title = entry.episodeTitle.orEmpty(),
                fallbackUrl = podcastArtworkFallback(entry.podcastName),
                downloaded = false,
                badgeSize = 0.dp,
                cornerRadius = if (largeControls) 21.dp else 16.dp,
                decodeSize = artworkSize,
                modifier = Modifier.size(artworkSize),
            )
            val queueDownloading = downloadState == EpisodeDownloadState.QUEUED ||
                downloadState == EpisodeDownloadState.DOWNLOADING
            // The Dashboard's stacked text (user, 29.10.2026), without its progress hairline.
            EpisodeStackText(
                meta = episodeMetaLine(entry.episodeId, entry.episodeTitle.orEmpty(), entry.publishedAt)
                    .ifBlank { entry.podcastName.orEmpty() },
                title = entry.episodeTitle.orEmpty(),
                timeText = when {
                    isCurrent -> stringResource(R.string.current_episode)
                    entry.completed == true -> stringResource(R.string.played)
                    durationSeconds > 0 && listenedSeconds > 0 -> minutesLeftLabel(remainingMinutes)
                    durationSeconds > 0 -> minutesLabel(remainingMinutes)
                    else -> null
                },
                started = false,
                completed = entry.completed == true,
                downloaded = downloadState == EpisodeDownloadState.DOWNLOADED,
                downloading = queueDownloading,
                downloadProgress = downloadProgress,
                textScale = if (largeControls) 1.1f else 1f,
                timeColor = if (isCurrent) LocalNookastColors.current.success else null,
                modifier = Modifier.weight(1f),
            )
            Box {
                // A grip, not a disc (DEC-059): drag to reorder, tap for the row menu.
                Box(
                    Modifier
                        .size(width = reorderHandleSize * 0.8f, height = reorderHandleSize)
                        .pointerInput(entry.episodeId) {
                            detectDragGestures(
                                onDragStart = { position ->
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDragStart(position.y - reorderHandleCenterPx)
                                },
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragCancel,
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDrag(dragAmount.y)
                                },
                            )
                        }
                        // No ripple on a grip: the menu that opens is the feedback.
                        .clickable(interactionSource = null, indication = null, role = androidx.compose.ui.semantics.Role.Button) { menuExpanded = true }
                        .semantics { contentDescription = reorderDescription },
                    contentAlignment = Alignment.Center,
                ) {
                    DragHandleIcon(
                        if (largeControls) {
                            Modifier.size(width = 16.dp, height = 30.dp)
                        } else {
                            Modifier.size(if (compact) 12.dp else 16.dp)
                        },
                    )
                }
                ru.e1173.nookast.designsystem.NookMenu(menuExpanded, { menuExpanded = false }) {
                    ru.e1173.nookast.designsystem.NookMenuItem(stringResource(R.string.move_up), { menuExpanded = false; onMoveUp() }, enabled = canMoveUp)
                    ru.e1173.nookast.designsystem.NookMenuItem(stringResource(R.string.move_down), { menuExpanded = false; onMoveDown() }, enabled = canMoveDown)
                    ru.e1173.nookast.designsystem.NookMenuItem(stringResource(R.string.remove_from_queue), { menuExpanded = false; onRemove() }, destructive = true)
                    if (downloadState == EpisodeDownloadState.DOWNLOADED) {
                        ru.e1173.nookast.designsystem.NookMenuItem(stringResource(R.string.delete_download), { menuExpanded = false; onDeleteDownload() }, destructive = true)
                    } else if (downloadState == EpisodeDownloadState.NOT_DOWNLOADED ||
                        downloadState == EpisodeDownloadState.FAILED
                    ) {
                        ru.e1173.nookast.designsystem.NookMenuItem(stringResource(R.string.download_episode), { menuExpanded = false; onDownload() })
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun QueueSwipeActionButton(
    action: QueueSwipeAction,
    downloadState: EpisodeDownloadState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val destructive = action == QueueSwipeAction.REMOVE_FROM_QUEUE ||
        (action == QueueSwipeAction.DOWNLOAD_OR_DELETE &&
            downloadState == EpisodeDownloadState.DOWNLOADED)
    val background = if (destructive) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.primaryContainer
    val content = if (destructive) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onPrimaryContainer
    val label = when (action) {
        QueueSwipeAction.NONE -> ""
        QueueSwipeAction.DOWNLOAD_OR_DELETE -> if (downloadState == EpisodeDownloadState.DOWNLOADED) {
            stringResource(R.string.delete_download)
        } else {
            stringResource(R.string.download_episode)
        }
        QueueSwipeAction.MARK_PLAYED -> stringResource(R.string.mark_played)
        QueueSwipeAction.REMOVE_FROM_QUEUE -> stringResource(R.string.remove_from_queue)
    }
    Box(
        modifier = modifier.background(background).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (action) {
                QueueSwipeAction.DOWNLOAD_OR_DELETE -> if (
                    downloadState == EpisodeDownloadState.DOWNLOADED
                ) {
                    DeleteDownloadGlyph(Modifier.size(20.dp), color = content)
                } else {
                    DownloadGlyph(Modifier.size(20.dp), color = content)
                }
                QueueSwipeAction.MARK_PLAYED -> CheckGlyph(Modifier.size(20.dp), color = content)
                QueueSwipeAction.REMOVE_FROM_QUEUE -> RemoveGlyph(Modifier.size(20.dp), color = content)
                QueueSwipeAction.NONE -> Unit
            }
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = content,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun QueueMetadataLine(
    text: String,
    downloaded: Boolean,
    downloading: Boolean = false,
    downloadProgress: Float? = null,
    compact: Boolean,
    largeControls: Boolean,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text,
            style = (if (largeControls) MaterialTheme.typography.bodyMedium.merge(ru.e1173.nookast.designsystem.NookPlayerType.metaLarge) else MaterialTheme.typography.labelSmall)
            .merge(ru.e1173.nookast.designsystem.NookPlayerType.regular),
            color = color,
            maxLines = 1,
        )
        if (downloaded) {
            val badgeSize = when {
                largeControls -> 15.dp
                compact -> 11.dp
                else -> 12.dp
            }
            Box(
                Modifier.size(badgeSize).background(DownloadGreen, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                DownloadGlyph(Modifier.size(badgeSize * 0.56f), color = Color.White)
            }
        } else if (downloading) {
            CircularProgressIndicator(
                progress = { downloadProgress?.coerceIn(0f, 1f) ?: 0f },
                modifier = Modifier.size(if (largeControls) 15.dp else 12.dp),
                strokeWidth = 1.5.dp,
                color = DownloadGreen,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

/** Podcast covers by normalized podcast name: the fallback for dead episode covers (P-16). */
internal val LocalPodcastArtworkByName = compositionLocalOf<Map<String, String>> { emptyMap() }

internal fun podcastArtworkByName(podcasts: List<PodcastSubscriptionEntity>): Map<String, String> =
    podcasts.asSequence()
        .filter { it.artworkUrl.isNotBlank() }
        .associate { it.name.trim().lowercase() to it.artworkUrl }

@Composable
internal fun podcastArtworkFallback(podcastName: String?): String? =
    podcastName?.trim()?.lowercase()?.let { LocalPodcastArtworkByName.current[it] }

/** How much of each caller's corner radius a cover keeps. */
internal const val ARTWORK_CORNER_SCALE = 0.55f

@Composable
internal fun Artwork(
    url: String,
    title: String,
    downloaded: Boolean,
    badgeSize: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    criticalWarningSeconds: Int? = null,
    reloadKey: Any? = null,
    fallbackUrl: String? = null,
    decodeSize: Dp? = null,
) {
    val placeholder = placeholderBrush(title)
    // Every cover and picture of the app is less rounded (user, 29.10.2026): callers keep their
    // radii as proportions and one factor sets the look.
    val shape = RoundedCornerShape(cornerRadius * ARTWORK_CORNER_SCALE)
    // Rows of a list pass their size: BoxWithConstraints subcomposes every item and made long
    // lists stutter (user, 29.10.2026). Without a size the cover still measures itself.
    if (decodeSize != null) {
        val decodePx = with(LocalDensity.current) { decodeSize.roundToPx() }
        Box(modifier.clip(shape)) {
            ArtworkLayers(decodePx, url, title, downloaded, badgeSize, criticalWarningSeconds, reloadKey, fallbackUrl, placeholder)
        }
        return
    }
    BoxWithConstraints(modifier.clip(shape)) {
        val density = LocalDensity.current
        val decodePx = when {
            constraints.hasBoundedWidth || constraints.hasBoundedHeight -> maxOf(
                if (constraints.hasBoundedWidth) constraints.maxWidth else 0,
                if (constraints.hasBoundedHeight) constraints.maxHeight else 0,
            )
            else -> with(density) { 400.dp.roundToPx() }
        }
        ArtworkLayers(decodePx, url, title, downloaded, badgeSize, criticalWarningSeconds, reloadKey, fallbackUrl, placeholder)
    }
}

@Composable
private fun BoxScope.ArtworkLayers(
    decodePx: Int,
    url: String,
    title: String,
    downloaded: Boolean,
    badgeSize: Dp,
    criticalWarningSeconds: Int?,
    reloadKey: Any?,
    fallbackUrl: String?,
    placeholder: Brush,
) {
    run {
        val bitmap = rememberArtworkBitmap(url, reloadKey, fallbackUrl, decodePx)
        val artworkAlpha by animateFloatAsState(
            targetValue = if (bitmap == null) 0f else 1f,
            animationSpec = ru.e1173.nookast.designsystem.NookMotion.standard(), // design audit 5.4
            label = "artworkFadeIn",
        )
        if (LocalInspectionMode.current && url.startsWith(PREVIEW_ARTWORK_PREFIX)) {
            PreviewArtwork(title = title, modifier = Modifier.fillMaxSize())
        } else {
            // The letter placeholder fades out as the cover fades in. It used to stay underneath
            // and showed through covers with transparent areas as a dark strip with a letter
            // (user, 28.10.2026: «Игровой граммофон»). Transparent covers now sit on plain white,
            // as podcast directories show them.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - artworkAlpha }
                    .background(placeholder),
            ) {
                ArtworkInitialPlaceholder(title = title, modifier = Modifier.fillMaxSize())
            }
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = artworkAlpha }
                        .background(Color.White),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        criticalWarningSeconds?.let { seconds ->
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.97f),
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Text(
                    pluralStringResource(
                        R.plurals.playback_will_stop_warning,
                        seconds,
                        seconds,
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelLarge.merge(ru.e1173.nookast.designsystem.NookPlayerType.bold),
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (downloaded && badgeSize > 0.dp) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .size(badgeSize)
                    .background(DownloadGreen, CircleShape)
                    .border(1.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                DownloadGlyph(Modifier.size(badgeSize * 0.58f), color = Color.White)
            }
        }
    }
}

@Composable
private fun PreviewArtwork(title: String, modifier: Modifier = Modifier) {
    val variant = title.hashCode().ushr(3) % 4
    val background = ru.e1173.nookast.designsystem.NookFixedColors.PreviewBackgrounds[variant]
    Canvas(modifier) {
        drawRect(Brush.linearGradient(background))
        drawCircle(
            color = ru.e1173.nookast.designsystem.NookFixedColors.PreviewMoon,
            radius = size.minDimension * 0.42f,
            center = Offset(size.width * 0.72f, size.height * 0.24f),
        )
        drawCircle(
            color = ru.e1173.nookast.designsystem.NookFixedColors.PreviewSun,
            radius = size.minDimension * 0.48f,
            center = Offset(size.width * 0.67f, size.height * 0.93f),
        )
        val silhouette = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.87f)
            cubicTo(
                size.width * 0.28f,
                size.height * 0.61f,
                size.width * 0.34f,
                size.height * 0.37f,
                size.width * 0.51f,
                size.height * 0.27f,
            )
            cubicTo(
                size.width * 0.68f,
                size.height * 0.17f,
                size.width * 0.83f,
                size.height * 0.30f,
                size.width * 0.76f,
                size.height * 0.45f,
            )
            cubicTo(
                size.width * 0.69f,
                size.height * 0.57f,
                size.width * 0.56f,
                size.height * 0.48f,
                size.width * 0.53f,
                size.height * 0.64f,
            )
            lineTo(size.width * 0.49f, size.height * 0.87f)
            close()
        }
        drawPath(silhouette, ru.e1173.nookast.designsystem.NookFixedColors.PreviewSilhouette)
        drawCircle(
            color = ru.e1173.nookast.designsystem.NookFixedColors.PreviewMicrophone,
            radius = size.minDimension * 0.035f,
            center = Offset(size.width * 0.55f, size.height * 0.55f),
        )
        drawLine(
            color = ru.e1173.nookast.designsystem.NookFixedColors.PreviewMicrophone,
            start = Offset(size.width * 0.55f, size.height * 0.58f),
            end = Offset(size.width * 0.54f, size.height * 0.70f),
            strokeWidth = size.minDimension * 0.018f,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Now Playing washed with the cover's colours (user trial 27.10.2026): two soft pools at the top
 * that fade out by the middle of the screen, cross-fading when the episode changes. Off in
 * Settings → Appearance returns the plain background.
 */
@Composable
internal fun ArtworkTintBackdrop(
    url: String,
    modifier: Modifier = Modifier,
    /** In the player: whether it plays, so the two lights drift (design audit 5.2); null elsewhere. */
    living: Boolean? = null,
) {
    if (LocalInspectionMode.current) return
    val preferences: ru.e1173.nookast.preferences.AppPreferencesViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel()
    val display by preferences.state.collectAsStateWithLifecycle()
    if (!display.artworkTint) return
    // One light, two ways (user, 29.10.2026): under a morning or white-night sky the cover's
    // colours lean towards the sky's, and the sky over the app takes the cover's main colour.
    val sky = ru.e1173.nookast.designsystem.LocalSkyState.current
    val dark = LocalNookastColors.current.isDark
    val raw = rememberArtworkGlow(rememberArtworkBitmap(url, maxPx = ARTWORK_TINT_SAMPLE_PX))
    val glow = raw?.let { colors -> if (dark) colors.map { it.blendedWithSky(sky) } else colors }
    androidx.compose.runtime.DisposableEffect(raw) {
        ru.e1173.nookast.designsystem.skyCoverTint.value = raw?.firstOrNull()
        onDispose { ru.e1173.nookast.designsystem.skyCoverTint.value = null }
    }
    val strength = if (dark) 0.30f else 0.20f
    val first by androidx.compose.animation.animateColorAsState(
        (glow?.get(0) ?: Color.Transparent).copy(alpha = if (glow == null) 0f else strength),
        animationSpec = ru.e1173.nookast.designsystem.NookMotion.slow(),
        label = "tintFirst",
    )
    val second by androidx.compose.animation.animateColorAsState(
        (glow?.get(1) ?: Color.Transparent).copy(alpha = if (glow == null) 0f else strength * 0.8f),
        animationSpec = ru.e1173.nookast.designsystem.NookMotion.slow(),
        label = "tintSecond",
    )
    val drift = ru.e1173.nookast.designsystem.rememberBackdropDrift(active = living == true)
    Box(
        modifier.drawBehind {
            val t = drift.value
            drawRect(
                Brush.radialGradient(
                    listOf(first, Color.Transparent),
                    center = driftedCenter(androidx.compose.ui.geometry.Offset(size.width * 0.15f, 0f), t, 47f, 53f),
                    radius = ru.e1173.nookast.designsystem.driftedRadius(size.maxDimension * 0.55f, t, 41f),
                ),
            )
            drawRect(
                Brush.radialGradient(
                    listOf(second, Color.Transparent),
                    center = driftedCenter(androidx.compose.ui.geometry.Offset(size.width * 0.9f, size.height * 0.08f), t, 59f, 43f),
                    radius = ru.e1173.nookast.designsystem.driftedRadius(size.maxDimension * 0.45f, t, 37f),
                ),
            )
            if (living != null) drawBackdropGrain()
        },
    )
}

private const val ARTWORK_TINT_SAMPLE_PX = 128

@Composable
internal fun rememberArtworkBitmap(
    url: String,
    reloadKey: Any? = null,
    fallbackUrl: String? = null,
    maxPx: Int = Int.MAX_VALUE,
): androidx.compose.ui.graphics.ImageBitmap? {
    if (LocalInspectionMode.current) return null
    val context = LocalContext.current
    val sources = remember(url, fallbackUrl) {
        listOf(url, fallbackUrl.orEmpty()).filter(String::isNotBlank).distinct()
    }
    // Coil (plan 5.3): a cover already decoded at this size shows at once, without a flash.
    val memoryBitmap = remember(sources, maxPx) {
        sources.firstNotNullOfOrNull { ru.e1173.nookast.player.ArtworkImages.peek(context, it, maxPx) }?.asImageBitmap()
    }
    val bitmap by produceState(initialValue = memoryBitmap, sources, reloadKey, maxPx) {
        // produceState keeps its value across key changes: without this reset the previous
        // episode's cover stayed on screen when the new one failed to load (APK-2).
        value = memoryBitmap
        if (memoryBitmap != null) return@produceState
        // Artwork is decoration: a failure here must never take the screen down. A dead episode
        // cover falls back to the podcast cover (P-16).
        for (source in sources) {
            val loaded = runCatching { ru.e1173.nookast.player.ArtworkImages.load(context, source, maxPx) }.getOrNull()
            if (loaded != null) {
                value = loaded.asImageBitmap()
                return@produceState
            }
        }
    }
    return bitmap
}

@Composable
private fun ArtworkInitialPlaceholder(
    title: String,
    modifier: Modifier = Modifier,
) {
    val initial = title.firstOrNull { it.isLetterOrDigit() }
        ?.uppercaseChar()
        ?.toString()
        ?: "•"
    BoxWithConstraints(modifier) {
        val compact = maxWidth < 72.dp
        val inset = if (compact) 5.dp else 8.dp
        val vertical = if (compact) 2.dp else 5.dp
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = inset, vertical = inset),
            shape = RoundedCornerShape(if (compact) ru.e1173.nookast.designsystem.NookShapes.xsRadius else ru.e1173.nookast.designsystem.NookShapes.sRadius),
            color = ru.e1173.nookast.designsystem.NookFixedColors.CoverCaption,
            contentColor = Color.White,
        ) {
            Text(
                text = initial,
                modifier = Modifier.padding(horizontal = if (compact) 4.dp else 7.dp, vertical = vertical),
                style = (if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleMedium).merge(ru.e1173.nookast.designsystem.NookPlayerType.medium),
            )
        }
    }
}

private fun placeholderBrush(title: String): Brush {
    val colors = ru.e1173.nookast.designsystem.NookFixedColors.PlaceholderGradients
    val pair = colors[title.hashCode().ushr(4) % colors.size]
    return Brush.linearGradient(listOf(pair.first, pair.second))
}

private fun criticalWarningSeconds(state: PlaybackUiState): Int? {
    if (state.isLocalSource || state.streamReachability != StreamReachability.UNAVAILABLE) return null
    // Only a stream that plays and has something left in the buffer can «stop in N seconds»: at a start
    // without a connection nothing is playing yet (user, 11.11.2026: the warning showed then).
    if (!state.isPlaying || state.bufferedAheadMs <= 0L) return null
    if (state.bufferedAheadMs >= 60_000L) return null
    return ceil(state.bufferedAheadMs.coerceAtLeast(1L) / 1_000.0).toInt().coerceAtLeast(1)
}

private fun cachedPercent(state: PlaybackUiState): Int {
    if (state.isLocalSource) return 100
    if (state.durationMs <= 0) return 0
    return (((state.positionMs + state.bufferedAheadMs).coerceAtMost(state.durationMs) * 100) /
        state.durationMs).toInt().coerceIn(0, 100)
}

private fun playbackFraction(state: PlaybackUiState): Float =
    if (state.durationMs <= 0) 0f
    else (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)

private fun formatPlaybackTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0) / 1_000).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

private fun formatPublishedDate(value: String, locale: Locale): String = runCatching {
    LocalDate.parse(value.take(10)).format(DateTimeFormatter.ofPattern("d MMMM (EEE) yyyy", locale))
}.getOrDefault(value)

@Composable
private fun DragHandleIcon(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    androidx.compose.material3.Icon(ru.e1173.nookast.designsystem.NookIcons.Grip, contentDescription = null, tint = color, modifier = modifier)
}

@Composable
internal fun DownloadGlyph(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    androidx.compose.material3.Icon(ru.e1173.nookast.designsystem.NookIcons.Download, contentDescription = null, tint = color, modifier = modifier)
}

@Composable
internal fun DeleteDownloadGlyph(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    androidx.compose.material3.Icon(ru.e1173.nookast.designsystem.NookIcons.Trash, contentDescription = null, tint = color, modifier = modifier)
}

@Composable
private fun RemoveGlyph(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    // Drawn from NookIcons (design audit 3.7).
    androidx.compose.material3.Icon(ru.e1173.nookast.designsystem.NookIcons.Close, contentDescription = null, tint = color, modifier = modifier)
}

@Composable
private fun CheckGlyph(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    // Drawn from NookIcons (design audit 3.7).
    androidx.compose.material3.Icon(ru.e1173.nookast.designsystem.NookIcons.Check, contentDescription = null, tint = color, modifier = modifier)
}

@Composable
private fun ListGlyph(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurface
    // Drawn from NookIcons (design audit 3.7).
    androidx.compose.material3.Icon(ru.e1173.nookast.designsystem.NookIcons.ListView, contentDescription = null, tint = color, modifier = modifier)
}

/**
 * DEC-023, step 4: when the origin cannot be reached from this device, offer to download the
 * episode to the PinePods server and play the server copy; shows the server task's progress.
 */
@Composable
private fun ServerCopySection(state: PlaybackUiState, onDownloadToServer: () -> Unit) {
    if (state.isLocalSource || state.currentEpisodeId == null) return
    val progress = state.serverDownload
    // The server must answer for a copy on it to help (user, 11.11.2026: the offer showed while the same
    // card said the server was unreachable).
    val serverReachable = state.streamReachability != StreamReachability.UNAVAILABLE
    val originUnavailable = serverReachable && (state.isUsingPinePodsFallback || state.originStalled)
    val textStyle = MaterialTheme.typography.bodySmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            state.isServerCopySource -> Text(stringResource(R.string.server_copy_playing), style = textStyle, color = muted)
            progress is ServerDownloadProgress.Running -> {
                Text(stringResource(R.string.server_copy_downloading, progress.percent), style = textStyle, color = muted)
                LinearProgressIndicator(
                    progress = { (progress.percent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            progress == ServerDownloadProgress.Ready -> Text(stringResource(R.string.server_copy_ready), style = textStyle, color = muted)
            progress == ServerDownloadProgress.Disabled -> Text(stringResource(R.string.server_copy_disabled), style = textStyle, color = muted)
            progress == ServerDownloadProgress.Failed -> {
                Text(stringResource(R.string.server_copy_failed), style = textStyle, color = muted)
                if (serverReachable) NookButton(variant = NookButtonVariant.Tonal, onClick = onDownloadToServer) { Text(stringResource(R.string.server_copy_retry)) }
            }
            originUnavailable && !state.isOnServer -> {
                Text(stringResource(R.string.server_copy_offer_hint), style = textStyle, color = muted)
                NookButton(variant = NookButtonVariant.Tonal, onClick = onDownloadToServer) { Text(stringResource(R.string.server_copy_offer)) }
            }
        }
    }
}

/**
 * The reachability the source chip shows: «Checking» only once a check has lasted
 * [CHECKING_REVEAL_MS]; a quick re-check after a start or a seek keeps the last known state
 * (user, 27.10.2026: no flashing at every micro-load).
 */
@Composable
private fun rememberSettledReachability(value: StreamReachability, waiting: Boolean): StreamReachability {
    var shown by remember { mutableStateOf(if (value == StreamReachability.CHECKING) StreamReachability.AVAILABLE else value) }
    LaunchedEffect(value, waiting) {
        // Paused, nothing waits for the network: a pending check keeps the last known state.
        if (value == StreamReachability.CHECKING && !waiting) return@LaunchedEffect
        if (value == StreamReachability.CHECKING) kotlinx.coroutines.delay(CHECKING_REVEAL_MS)
        shown = value
    }
    return if (value == StreamReachability.CHECKING) shown else value
}

private const val CHECKING_REVEAL_MS = 1_500L

/** An ID3 chapter picture, decoded no larger than about 1024 px; null when it is not an image. */
private fun decodeChapterPicture(bytes: ByteArray): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1024) sample *= 2
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        ?.asImageBitmap()
}.getOrNull()

/**
 * A popover: a rounded card and its pointer as one outline, so the fill and the 1 px border run
 * round both without a seam. The pointer has soft shoulders and a rounded tip; it sits at the top,
 * [pointerCenterFromEnd] from the right edge, or centred at the bottom.
 */
private class PopoverShape(
    private val radius: Dp,
    private val pointerWidth: Dp,
    private val pointerHeight: Dp,
    private val pointerAtBottom: Boolean,
    private val pointerCenterFromEnd: Dp?,
) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline = with(density) {
        val r = radius.toPx()
        val w = pointerWidth.toPx()
        val h = pointerHeight.toPx()
        val top = if (pointerAtBottom) 0f else h
        val bottom = if (pointerAtBottom) size.height - h else size.height
        val cx = pointerCenterFromEnd?.let { size.width - it.toPx() } ?: (size.width / 2f)
        val body = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, top, size.width, bottom, androidx.compose.ui.geometry.CornerRadius(r)))
        }
        val base = if (pointerAtBottom) bottom else top
        val tip = if (pointerAtBottom) size.height else 0f
        val into = if (pointerAtBottom) -1f else 1f
        fun y(share: Float) = base + (tip - base) * share
        val pointer = Path().apply {
            moveTo(cx - w * 0.75f, base + into)
            quadraticTo(cx - w * 0.45f, base, cx - w * 0.24f, y(0.5f))
            lineTo(cx - w * 0.07f, y(0.9f))
            quadraticTo(cx, tip, cx + w * 0.07f, y(0.9f))
            lineTo(cx + w * 0.24f, y(0.5f))
            quadraticTo(cx + w * 0.45f, base, cx + w * 0.75f, base + into)
            close()
        }
        androidx.compose.ui.graphics.Outline.Generic(
            Path.combine(androidx.compose.ui.graphics.PathOperation.Union, body, pointer),
        )
    }
}

/**
 * The episode's description, or the podcast cache's copy when the entry came without one: the
 * Dashboard's liked and downloaded rows carry none (user, 29.10.2026: a Игровой граммофон special
 * showed «no description» though the feed has it).
 */
internal fun QueueListEntry?.descriptionOrCached(podcastDetails: ru.e1173.nookast.data.remote.PodcastDetails?): String {
    val own = this?.description.orEmpty()
    if (own.isNotBlank() || this == null) return own
    return podcastDetails?.recentEpisodes?.firstOrNull { it.episodeId == episodeId }?.description.orEmpty()
}

/**
 * Chapters of the feed (Podcasting 2.0) or the stream, else a timestamp list of the description:
 * one list for Now Playing's pill, timeline and Description panel and for the episode card.
 */
internal fun episodeChapterList(
    chapters: List<ru.e1173.nookast.player.PlaybackChapter>,
    description: String,
    resources: android.content.res.Resources,
): List<DescriptionTimestamp> {
    val native = chapters.mapIndexed { index, chapter ->
        DescriptionTimestamp(
            positionMs = chapter.startTimeMs,
            label = chapter.title.ifBlank { resources.getString(R.string.chapter_number, index + 1) },
        )
    }
    // Real chapters win; timestamps of the description only stand in when there are none.
    return native.ifEmpty { descriptionChapterList(description).map { it.copy(fromDescription = true) } }
        .sortedBy(DescriptionTimestamp::positionMs)
        .distinctBy(DescriptionTimestamp::positionMs)
}

/**
 * The cover of an episode, or the picture of the chapter at [positionMs] when it has one (user,
 * 29.10.2026): a link from the feed's chapters or an image embedded in the file's ID3 chapter. One
 * rule for Now Playing and the episode card (user, 31.10.2026).
 */
@Composable
internal fun ChapterCover(
    chapters: List<ru.e1173.nookast.player.PlaybackChapter>,
    positionMs: Long,
    artworkUrl: String,
    title: String,
    podcastName: String?,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    criticalWarningSeconds: Int? = null,
    decodeSize: Dp? = null,
) {
    val chapterPicture: Any? = chapters.lastOrNull { it.startTimeMs <= positionMs }?.let { it.imageUrl ?: it.image }
    androidx.compose.animation.Crossfade(chapterPicture, modifier, label = "chapterPicture") { picture ->
        val embedded = (picture as? ByteArray)?.let { bytes -> remember(bytes) { decodeChapterPicture(bytes) } }
        if (embedded != null) {
            androidx.compose.foundation.Image(
                embedded,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(cornerRadius * ARTWORK_CORNER_SCALE)),
            )
        } else {
            Artwork(
                url = (picture as? String) ?: artworkUrl,
                title = title,
                fallbackUrl = if (picture is String) artworkUrl else podcastArtworkFallback(podcastName),
                downloaded = false,
                badgeSize = 0.dp,
                cornerRadius = cornerRadius,
                criticalWarningSeconds = criticalWarningSeconds,
                decodeSize = decodeSize,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The mini player's place when nothing plays but the queue has episodes (user, 31.10.2026: Up Next
 * could only be opened from the mini player). The same card: the next episode's cover, «Up Next»
 * with how many episodes wait, Play starts the queue and a tap opens it.
 */
@Composable
internal fun QueueBar(
    entries: List<QueueListEntry>,
    largeControls: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    glass: Modifier? = null,
) {
    val next = entries.firstOrNull() ?: return
    NookMiniPlayer(
        title = stringResource(R.string.queue_title),
        podcastName = pluralStringResource(R.plurals.queue_episode_count, entries.size, entries.size) +
            " · " + cleanEpisodeTitle(next.episodeTitle.orEmpty()),
        progress = 0f,
        playing = false,
        buffering = false,
        enabled = true,
        labels = MiniPlayerLabels(
            open = stringResource(R.string.open_up_next),
            play = stringResource(R.string.play),
            pause = stringResource(R.string.pause),
            queue = stringResource(R.string.open_up_next),
            seekBack = "",
            seekForward = "",
        ),
        onOpen = onOpen,
        onPlayPause = onPlay,
        onQueue = onOpen,
        artwork = { artworkModifier ->
            Artwork(
                url = next.artworkUrl.orEmpty(),
                title = next.episodeTitle.orEmpty(),
                fallbackUrl = podcastArtworkFallback(next.podcastName),
                downloaded = false,
                badgeSize = 0.dp,
                cornerRadius = if (largeControls) 13.dp else 10.dp,
                modifier = artworkModifier,
            )
        },
        modifier = modifier.snowDusted(16.dp, seed = 3),
        scale = if (largeControls) 4f / 3f else 1f,
        glass = glass,
    )
}

/** The wide player's gap under the header: the cover and the right pane start there. */
private val WIDE_TOP_GAP = 4.dp
