package ru.e1173.nookast.ui

import androidx.compose.ui.graphics.graphicsLayer
import ru.e1173.nookast.designsystem.nookPressable
import ru.e1173.nookast.designsystem.NookType.scaled
import androidx.compose.ui.graphics.compositeOver
import ru.e1173.nookast.designsystem.nookCard
import androidx.core.graphics.get
import ru.e1173.nookast.designsystem.drawCssShadow
import ru.e1173.nookast.designsystem.floatingShadow
import ru.e1173.nookast.designsystem.nookElevation
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import dev.chrisbanes.haze.hazeSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import ru.e1173.nookast.R
import ru.e1173.nookast.data.local.PodcastSubscriptionEntity
import ru.e1173.nookast.data.local.ProgressOutboxEntity
import ru.e1173.nookast.data.local.QueueListEntry
import ru.e1173.nookast.data.remote.PodcastDetails
import ru.e1173.nookast.data.remote.PodcastEpisodeProgress
import ru.e1173.nookast.offline.EpisodeDownloadState
import ru.e1173.nookast.preferences.PodcastSortMode
import ru.e1173.nookast.preferences.PodcastViewMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PodcastPanelShape = ru.e1173.nookast.designsystem.NookShapes.l

/** One height for the sort button and the view toggle, so they line up (APK-3.5 review). */
private val PodcastControlHeight = 44.dp

/** The height of «Continue» (a medium NookButton) and so of the gear beside it. */
private val PodcastActionHeight = 40.dp

/** Below this pane width the wide search disc would squeeze the sort control, so it is left out. */
private val PodcastSearchInRowMinWidth = 320.dp

// Both resolve to the theme background now (DEC-017); kept as names for the light/dark branches.
internal val PodcastLightBackground: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
internal val PodcastDarkBackground: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
private val PodcastDownloadGreen: Color
    @Composable @ReadOnlyComposable get() = ru.e1173.nookast.designsystem.LocalNookastColors.current.success

private fun podcastTitleGroup(title: String): Int {
    val first = title.trimStart().firstOrNull() ?: return 2
    return when (first) {
        in '\u0400'..'\u04FF' -> 0
        in 'A'..'Z', in 'a'..'z' -> 1
        else -> 2
    }
}

private val podcastTitleComparator = Comparator<PodcastSubscriptionEntity> { left, right ->
    compareValues(podcastTitleGroup(left.name), podcastTitleGroup(right.name))
        .takeIf { it != 0 }
        ?: compareValues(
            left.name.trim().lowercase(Locale.ROOT),
            right.name.trim().lowercase(Locale.ROOT),
        ).takeIf { it != 0 }
        ?: compareValues(left.name, right.name).takeIf { it != 0 }
        ?: compareValues(left.podcastId, right.podcastId)
}

internal fun sortPodcastSubscriptions(
    podcasts: List<PodcastSubscriptionEntity>,
    sortMode: PodcastSortMode,
): List<PodcastSubscriptionEntity> = when (sortMode) {
    PodcastSortMode.TITLE -> podcasts.sortedWith(podcastTitleComparator)
    PodcastSortMode.UPDATED -> podcasts.sortedWith { left, right ->
        compareValues(right.lastUpdatedAt, left.lastUpdatedAt).takeIf { it != 0 }
            ?: podcastTitleComparator.compare(left, right)
    }
}

internal fun updateStableUpdatedPodcastOrder(
    currentOrder: List<Int>,
    podcasts: List<PodcastSubscriptionEntity>,
    refreshCompleted: Boolean,
): List<Int> {
    val sortedIds = {
        sortPodcastSubscriptions(podcasts, PodcastSortMode.UPDATED)
            .map(PodcastSubscriptionEntity::podcastId)
    }
    if (currentOrder.isEmpty() || refreshCompleted) return sortedIds()

    val currentIds = podcasts.map(PodcastSubscriptionEntity::podcastId)
    val currentIdSet = currentIds.toSet()
    val knownIds = currentOrder.toSet()
    if (currentIdSet == knownIds) return currentOrder

    val retained = currentOrder.filter(currentIdSet::contains)
    val added = sortPodcastSubscriptions(
        podcasts.filterNot { it.podcastId in knownIds },
        PodcastSortMode.UPDATED,
    ).map(PodcastSubscriptionEntity::podcastId)
    return retained + added
}

internal fun selectionAfterLocalCommit(
    selectedEpisodeIds: Set<Int>,
    succeeded: Boolean,
): Set<Int> = if (succeeded) emptySet() else selectedEpisodeIds

internal fun shouldShowPodcastDownloadStatus(state: EpisodeDownloadState): Boolean =
    state == EpisodeDownloadState.DOWNLOADED

private fun Modifier.podcastClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    // The app's press wash and a slight shrink (design audit 4.4).
    val press = remember { MutableInteractionSource() }
    nookPressable(press).clickable(
        interactionSource = press,
        indication = androidx.compose.foundation.LocalIndication.current,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
internal fun PodcastScreen(
    state: QueueUiState,
    /** The playing episode: its row on a podcast page takes the light of its Play (4.12). */
    currentEpisodeId: Int? = null,
    isPlaying: Boolean = false,
    selectedPodcastId: Int?,
    sortMode: PodcastSortMode,
    viewMode: PodcastViewMode,
    collapsedDescriptions: Set<Int>,
    wide: Boolean,
    largeControls: Boolean,
    touchTarget: Dp,
    onRefresh: () -> Unit,
    onSortModeChanged: (PodcastSortMode) -> Unit,
    onViewModeChanged: (PodcastViewMode) -> Unit,
    onDescriptionCollapsedChanged: (Int, Boolean) -> Unit,
    onOpenPodcast: (Int) -> Unit,
    onBack: () -> Unit,
    onPlayEpisodes: (List<QueueListEntry>, Int) -> Unit,
    onOpenEpisode: (List<QueueListEntry>, Int) -> Unit,
    onDownloadEpisode: (QueueListEntry) -> Unit,
    onRemoveEpisodeDownload: (Int) -> Unit,
    onAddToQueue: (QueueListEntry) -> Unit,
    onMarkEpisodesPlayed: (List<QueueListEntry>) -> Unit,
    onMarkEpisodesUnplayed: (List<QueueListEntry>) -> Unit,
    onUnsubscribe: (Int) -> Unit,
    onChangeFeedUrl: (Int, String, (FeedUrlChangeResult) -> Unit) -> Unit = { _, _, _ -> },
    onLoadPodcast: (Int) -> Unit = {},
    /** Search and add, from the header (audit 3.9): opens Route.Search in this tab's stack. */
    onOpenSearch: (() -> Unit)? = null,
    sortMenuInitiallyExpanded: Boolean = false,
    settingsMenuInitiallyExpanded: Boolean = false,
    descriptionInitiallyFullyExpanded: Boolean = false,
    selectedEpisodeIdsInitially: Set<Int> = emptySet(),
    renderMenusInPlace: Boolean = false,
    pressedPodcastIdForPreview: Int? = null,
    hidePlayedPodcastIds: Set<Int> = emptySet(),
    onHidePlayedChanged: (Int, Boolean) -> Unit = { _, _ -> },
    onAddToQueueCommitted: ((List<QueueListEntry>, (Boolean) -> Unit) -> Unit)? = null,
    onMarkEpisodesPlayedCommitted: ((List<QueueListEntry>, (Boolean) -> Unit) -> Unit)? = null,
    onMarkEpisodesUnplayedCommitted: ((List<QueueListEntry>, (Boolean) -> Unit) -> Unit)? = null,
) {
    // A wide screen always shows a podcast on the right: the first one until another is tapped. It is
    // shown, not opened, so Back leaves the tab instead of reopening it (U-03).
    val shownPodcastId = selectedPodcastId ?: state.podcasts.firstOrNull()?.podcastId?.takeIf { wide }
    LaunchedEffect(shownPodcastId, selectedPodcastId) {
        if (shownPodcastId != null && selectedPodcastId == null) onLoadPodcast(shownPodcastId)
    }
    val selected = state.podcasts.firstOrNull { it.podcastId == shownPodcastId }
    val details = shownPodcastId?.let(state.podcastDetailsById::get)
    val detailsLoading = shownPodcastId in state.podcastDetailsByIdLoading
    val addToQueueCommitted = onAddToQueueCommitted ?: { entries, completed ->
        entries.forEach(onAddToQueue)
        completed(true)
    }
    val markPlayedCommitted = onMarkEpisodesPlayedCommitted ?: { entries, completed ->
        onMarkEpisodesPlayed(entries)
        completed(true)
    }
    val markUnplayedCommitted = onMarkEpisodesUnplayedCommitted ?: { entries, completed ->
        onMarkEpisodesUnplayed(entries)
        completed(true)
    }

    val podcastBackground = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        PodcastDarkBackground
    } else {
        PodcastLightBackground
    }
    // Wide: the screen draws its own status-bar glass over its own background and light (user,
    // 10.11.2026: the bar showed the bare base colour as a strip above the panes, light and dark).
    val screenHaze = dev.chrisbanes.haze.rememberHazeState()
    Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().then(if (wide) Modifier.hazeSource(screenHaze) else Modifier).background(podcastBackground)) {
        // Wide: the open podcast's light lies behind both panes and the status bar (user,
        // 29.10.2026: it was cut at every edge of the pane).
        if (wide && selected != null) {
            ArtworkTintBackdrop(
                selected.artworkUrl.orEmpty().ifBlank { details?.artworkUrl.orEmpty() },
                Modifier.matchParentSize(),
            )
        }
        val library: @Composable (Modifier, Int?) -> Unit = { paneModifier, highlighted ->
            PodcastLibraryPanel(
                onOpenSearch = onOpenSearch,
                podcasts = state.podcasts,
                // The server's own verdict on a feed, once past its reporting threshold, wins.
                freshness = state.podcastFreshness + state.serverFeedProblems.filterValues { it.reported }
                    .mapValues { (id, _) ->
                        // The phone still reads it: the feed is alive, only the server cannot update it.
                        if (id in state.feedsReadByPhone) {
                            ru.e1173.nookast.data.PodcastFreshness.SERVER_CANNOT_UPDATE
                        } else {
                            ru.e1173.nookast.data.PodcastFreshness.FEED_UNAVAILABLE
                        }
                    },
                selectedPodcastId = highlighted,
                sortMode = sortMode,
                viewMode = viewMode,
                forceCompactList = false,
                refreshing = state.podcastsRefreshing,
                hasError = state.podcastsError != null,
                largeControls = largeControls,
                touchTarget = touchTarget,
                onRefresh = onRefresh,
                onSortModeChanged = onSortModeChanged,
                onViewModeChanged = onViewModeChanged,
                onOpenPodcast = onOpenPodcast,
                modifier = paneModifier,
                showViewToggle = true,
                framed = false,
                showTitle = true,
                wideMaster = false,
                searchInControls = wide,
                sortMenuInitiallyExpanded = sortMenuInitiallyExpanded,
                renderMenusInPlace = renderMenusInPlace,
                pressedPodcastIdForPreview = pressedPodcastIdForPreview,
            )
        }
        val podcast: @Composable (Modifier, Int, Boolean) -> Unit = { paneModifier, podcastId, showBack ->
            PodcastDetailsPanel(
                podcastId = podcastId,
                currentEpisodeId = currentEpisodeId,
                isPlaying = isPlaying,
                catalog = selected,
                details = details,
                detailsLoading = detailsLoading,
                collapsed = podcastId in collapsedDescriptions,
                hidePlayed = podcastId in hidePlayedPodcastIds,
                actionInProgress = podcastId in state.podcastActionsInProgress,
                actionError = podcastId in state.podcastActionErrors,
                downloadStates = state.downloadStates,
                downloadProgress = state.downloadProgress,
                pendingProgress = state.pendingPodcastEpisodeProgress,
                progressUpdatedAt = state.podcastEpisodeProgressUpdatedAt,
                largeControls = largeControls,
                showBack = showBack,
                onBack = onBack,
                onDescriptionCollapsedChanged = onDescriptionCollapsedChanged,
                onHidePlayedChanged = onHidePlayedChanged,
                onPlayEpisodes = onPlayEpisodes,
                onOpenEpisode = onOpenEpisode,
                onDownloadEpisode = onDownloadEpisode,
                onRemoveEpisodeDownload = onRemoveEpisodeDownload,
                onAddToQueue = onAddToQueue,
                onMarkEpisodesPlayed = onMarkEpisodesPlayed,
                onMarkEpisodesUnplayed = onMarkEpisodesUnplayed,
                onAddToQueueCommitted = addToQueueCommitted,
                onMarkEpisodesPlayedCommitted = markPlayedCommitted,
                onMarkEpisodesUnplayedCommitted = markUnplayedCommitted,
                onUnsubscribe = onUnsubscribe,
                onChangeFeedUrl = onChangeFeedUrl,
                feedProblem = state.serverFeedProblems[podcastId],
                feedReadByPhone = podcastId in state.feedsReadByPhone,
                modifier = paneModifier,
                settingsMenuInitiallyExpanded = settingsMenuInitiallyExpanded,
                descriptionInitiallyFullyExpanded = descriptionInitiallyFullyExpanded,
                selectedEpisodeIdsInitially = selectedEpisodeIdsInitially,
                renderMenusInPlace = renderMenusInPlace,
            )
        }
        when {
            // Wide (user, 27.10.2026): the phone library on the left as is, the tapped podcast
            // on the right over the rest of the width.
            wide -> WideSplit(
                largeControls = largeControls,
                // The podcast list pads itself by 12 dp (18 in the car), the library grid by 3 + 12.
                detailPadding = if (largeControls) 0.dp else 3.dp,
                phone = { paneModifier -> library(paneModifier, shownPodcastId) },
                detail = { paneModifier ->
                    // Picking another podcast replaces only this pane, as iPad apps do (user, 31.10.2026):
                    // the new page fades in rising a little while the old one fades out.
                    androidx.compose.animation.AnimatedContent(
                        targetState = shownPodcastId,
                        modifier = paneModifier,
                        transitionSpec = {
                            (
                                androidx.compose.animation.fadeIn(ru.e1173.nookast.designsystem.NookMotion.emphasized(delayMs = 60)) +
                                    androidx.compose.animation.slideInVertically(ru.e1173.nookast.designsystem.NookMotion.emphasized()) { it / 28 } +
                                    androidx.compose.animation.scaleIn(ru.e1173.nookast.designsystem.NookMotion.emphasized(), initialScale = 0.985f)
                                ) togetherWith androidx.compose.animation.fadeOut(ru.e1173.nookast.designsystem.NookMotion.fast())
                        },
                        label = "widePodcast",
                    ) { podcastId ->
                        when (podcastId) {
                            null -> PodcastSelectionPrompt(Modifier.fillMaxSize())
                            // The page shows its own podcast only: the leaving one holds no other's data.
                            shownPodcastId -> podcast(Modifier.fillMaxSize().padding(top = 8.dp), podcastId, false)
                            else -> Box(Modifier.fillMaxSize())
                        }
                    }
                },
            )
            shownPodcastId != null -> podcast(Modifier.fillMaxSize(), shownPodcastId, true)
            else -> library(Modifier.fillMaxSize(), null)
        }
        if (renderMenusInPlace && !wide && sortMenuInitiallyExpanded) {
            PodcastSortMenuSurface(
                sortMode = sortMode,
                onSelect = onSortModeChanged,
                modifier = Modifier
                    .offset(x = 0.dp, y = 96.dp)
                    .width(238.dp)
                    .zIndex(20f),
            )
        }
        if (renderMenusInPlace && !wide && settingsMenuInitiallyExpanded) {
            PodcastSettingsMenuSurface(
                onUnsubscribe = { shownPodcastId?.let(onUnsubscribe) },
                largeControls = largeControls,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-10).dp, y = 194.dp)
                    .width(238.dp)
                    .zIndex(20f),
            )
        }
    }
    if (wide) {
        androidx.compose.runtime.CompositionLocalProvider(LocalGlassVeil provides podcastBackground) {
            StatusBarGlass(screenHaze)
        }
    }
    }
}

@Composable
private fun PodcastLibraryPanel(
    onOpenSearch: (() -> Unit)?,
    podcasts: List<PodcastSubscriptionEntity>,
    freshness: Map<Int, ru.e1173.nookast.data.PodcastFreshness>,
    selectedPodcastId: Int?,
    sortMode: PodcastSortMode,
    viewMode: PodcastViewMode,
    forceCompactList: Boolean,
    refreshing: Boolean,
    hasError: Boolean,
    largeControls: Boolean,
    touchTarget: Dp,
    onRefresh: () -> Unit,
    onSortModeChanged: (PodcastSortMode) -> Unit,
    onViewModeChanged: (PodcastViewMode) -> Unit,
    onOpenPodcast: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showViewToggle: Boolean,
    framed: Boolean,
    showTitle: Boolean = true,
    wideMaster: Boolean = false,
    // Wide: the search disc ends the controls row instead of floating by the title (П3).
    searchInControls: Boolean = false,
    sortMenuInitiallyExpanded: Boolean = false,
    renderMenusInPlace: Boolean = false,
    pressedPodcastIdForPreview: Int? = null,
) {
    // Skeletons only before the library has loaded once (design audit 5.4, review of audit 5).
    var libraryLoadedOnce by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var libraryWasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing, podcasts.isNotEmpty()) {
        if (podcasts.isNotEmpty() || (libraryWasRefreshing && !refreshing)) libraryLoadedOnce = true
        libraryWasRefreshing = refreshing
    }
    val podcastBackground = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        PodcastDarkBackground
    } else {
        PodcastLightBackground
    }
    var stableUpdatedOrder by remember { mutableStateOf(emptyList<Int>()) }
    var wasRefreshing by remember { mutableStateOf(refreshing) }
    val podcastIds = podcasts.map(PodcastSubscriptionEntity::podcastId)
    LaunchedEffect(podcastIds, refreshing) {
        stableUpdatedOrder = updateStableUpdatedPodcastOrder(
            currentOrder = stableUpdatedOrder,
            podcasts = podcasts,
            refreshCompleted = wasRefreshing && !refreshing,
        )
        wasRefreshing = refreshing
    }
    val sorted = remember(podcasts, sortMode, stableUpdatedOrder) {
        when (sortMode) {
            PodcastSortMode.UPDATED -> {
                val effectiveOrder = stableUpdatedOrder.ifEmpty {
                    sortPodcastSubscriptions(podcasts, PodcastSortMode.UPDATED)
                        .map(PodcastSubscriptionEntity::podcastId)
                }
                val index = effectiveOrder.withIndex().associate { it.value to it.index }
                podcasts.sortedWith { left, right ->
                    compareValues(
                        index[left.podcastId] ?: Int.MAX_VALUE,
                        index[right.podcastId] ?: Int.MAX_VALUE,
                    ).takeIf { it != 0 } ?: podcastTitleComparator.compare(left, right)
                }
            }
            PodcastSortMode.TITLE -> sortPodcastSubscriptions(podcasts, PodcastSortMode.TITLE)
        }
    }
    val refreshDescription = stringResource(R.string.podcast_refresh)
    val panelShape = if (framed) PodcastPanelShape else androidx.compose.ui.graphics.RectangleShape
    val panelModifier = if (framed) {
        modifier.border(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
            panelShape,
        )
    } else {
        modifier
    }
    Surface(
        modifier = panelModifier,
        shape = panelShape,
        color = when {
            wideMaster -> MaterialTheme.colorScheme.surface
            framed -> MaterialTheme.colorScheme.surfaceContainer
            // The screen paints the background, so a wide layout's cover light shows through.
            else -> Color.Transparent
        },
        tonalElevation = if (framed) 2.dp else 0.dp,
    ) {
        Column(Modifier.fillMaxSize().padding(top = LocalTopInset.current)) {
            Column(
                Modifier.fillMaxWidth().padding(
                    horizontal = when {
                        largeControls -> 20.dp
                        framed -> 16.dp
                        else -> 0.dp
                    },
                ),
            ) {
                if (showTitle) {
                    // Compact title row (APK-3.5 review: the header was too tall).
                    Row(
                        modifier = Modifier.heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.podcasts_title),
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = if (framed) 0.dp else 2.dp),
                            style = ru.e1173.nookast.designsystem.NookType.display.scaled(if (largeControls) 1.12f else 1f),
                            color = ru.e1173.nookast.designsystem.LocalNookastColors.current.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // A background refresh over a list already shown is quiet (user, 9.11.2026).
                        if (refreshing && podcasts.isEmpty()) {
                            CircularProgressIndicator(
                                Modifier.size(18.dp).semantics {
                                    contentDescription = refreshDescription
                                },
                                strokeWidth = 2.dp,
                            )
                        }
                        if (onOpenSearch != null && !searchInControls) {
                            ru.e1173.nookast.designsystem.NookHeaderDisc(
                                ru.e1173.nookast.designsystem.NookIcons.Search,
                                stringResource(R.string.dashboard_search),
                                onOpenSearch,
                            )
                        }
                    }
                }
                val controlScale = if (largeControls) 4f / 3f else 1f
                val density = androidx.compose.ui.platform.LocalDensity.current
                // The toggle is exactly as tall as the sort control, which a large font may grow.
                var controlHeight by remember { mutableStateOf(PodcastControlHeight) }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                // The search disc joins the row only where the pane has room for it next to a usable sort.
                // The search disc joins the row only if the sort label still fits whole beside it (audit
                // of З, C2): measured in the current font, so a large font drops the disc instead.
                val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
                val sortLabels = listOf(stringResource(R.string.podcast_sort_updated), stringResource(R.string.podcast_sort_title))
                val labelWidth = with(density) {
                    sortLabels.maxOf { textMeasurer.measure(it, ru.e1173.nookast.designsystem.NookType.body, maxLines = 1).size.width }.toDp()
                }
                val segment = minOf(controlHeight - 8.dp, 36.dp * controlScale)
                val searchDiscWidth = minOf(controlHeight, PodcastActionHeight * controlScale)
                // Sort: label + 12 dp padding each side + 12 dp gap + chevron + 2 dp outline + 8 dp to the toggle.
                val sortNeeds = labelWidth + 12.dp * 2 + 12.dp + 16.dp * controlScale + 2.dp + 8.dp
                val rowNeeds = sortNeeds + segment * 2 + 8.dp + 8.dp + searchDiscWidth
                val searchFits = onOpenSearch != null && searchInControls && maxWidth >= PodcastSearchInRowMinWidth &&
                    maxWidth >= rowNeeds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = when {
                                wideMaster -> if (largeControls) 12.dp else 10.dp
                                framed -> 1.dp
                                else -> 4.dp
                            },
                            bottom = if (framed) 9.dp else 10.dp,
                    ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    // The sort button and the view toggle share one height and one centre line.
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PodcastSortMenu(
                        sortMode = sortMode,
                        onSortModeChanged = onSortModeChanged,
                        initiallyExpanded = sortMenuInitiallyExpanded,
                        renderInPlace = renderMenusInPlace,
                        // A wide screen keeps the control at a phone width and moves the toggle right.
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .widthIn(max = 420.dp)
                            .fillMaxWidth()
                            .padding(end = 8.dp)
                            .onSizeChanged { controlHeight = with(density) { it.height.toDp() } },
                        scale = controlScale,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showViewToggle) {
                            PodcastViewToggle(viewMode, onViewModeChanged, height = controlHeight, scale = controlScale)
                        }
                        if (searchFits && onOpenSearch != null) {
                            // Flat, on the controls' centre line, 8 dp visibly after the toggle.
                            val searchDisc = minOf(controlHeight, PodcastActionHeight * controlScale)
                            Spacer(Modifier.width((8.dp - (controlHeight - searchDisc) / 2).coerceAtLeast(0.dp)))
                            ru.e1173.nookast.designsystem.NookHeaderDisc(
                                ru.e1173.nookast.designsystem.NookIcons.Search,
                                stringResource(R.string.dashboard_search),
                                onOpenSearch,
                                // Up to 40 dp on the controls' centre line; laid out at their height.
                                discSize = searchDisc,
                                zone = controlHeight,
                                elevated = false,
                            )
                        }
                    }
                }
                }
            }
            if (framed) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
                )
            }
            if (hasError) {
                Text(
                    stringResource(
                        if (podcasts.isEmpty()) {
                            R.string.podcast_library_error_no_cache
                        } else {
                            R.string.podcast_library_error
                        },
                    ),
                    modifier = Modifier.fillMaxWidth().background(
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                    ).padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (sorted.isEmpty() && (!refreshing || libraryLoadedOnce)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ru.e1173.nookast.designsystem.NookEmptyState(
                        title = stringResource(R.string.empty_library_title),
                        message = stringResource(R.string.empty_library_message),
                        actionLabel = stringResource(R.string.empty_find_podcast),
                        onAction = onOpenSearch,
                    )
                }
            } else if (sorted.isEmpty() && !libraryLoadedOnce) {
                // First load with nothing cached: placeholder tiles (design audit 5.4); an empty library
                // that is refreshing keeps its empty state (review of audit 5).
                Column(Modifier.fillMaxSize().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(2) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            repeat(2) { ru.e1173.nookast.designsystem.NookSkeletonTile(Modifier.weight(1f)) }
                        }
                    }
                }
            } else {
                AnimatedContent(
                    targetState = forceCompactList || viewMode == PodcastViewMode.LIST,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        (fadeIn(ru.e1173.nookast.designsystem.NookMotion.standard()) + slideInHorizontally(ru.e1173.nookast.designsystem.NookMotion.emphasized()) { it / 12 })
                            .togetherWith(
                                fadeOut(ru.e1173.nookast.designsystem.NookMotion.fast()) + slideOutHorizontally(ru.e1173.nookast.designsystem.NookMotion.standard()) { -it / 12 },
                            )
                    },
                    label = "podcast-library-view",
                ) { listMode ->
                    if (listMode) BoxWithConstraints(Modifier.fillMaxSize()) {
                        // The list widens into two columns on a wide screen instead of stretching rows.
                        LazyVerticalGrid(
                            flingBehavior = deviceFling(),
                            columns = GridCells.Fixed(if (maxWidth >= 600.dp) 2 else 1),
                            state = rememberTopScrollingGrid(),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 3.dp, end = 3.dp, top = 5.dp, bottom = 5.dp + LocalBottomInset.current),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            gridItems(sorted, key = PodcastSubscriptionEntity::podcastId) { podcast ->
                                PodcastListItem(
                                    podcast = podcast,
                                    freshness = freshness[podcast.podcastId],
                                    selected = podcast.podcastId == selectedPodcastId,
                                    largeControls = largeControls,
                                    pressed = podcast.podcastId == pressedPodcastIdForPreview,
                                    onClick = { onOpenPodcast(podcast.podcastId) },
                                )
                            }
                        }
                    } else {
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            val columns = when {
                                maxWidth >= 980.dp -> 5
                                maxWidth >= 700.dp -> 4
                                maxWidth >= 460.dp -> 3
                                else -> 2
                            }
                            LazyVerticalGrid(
                                flingBehavior = deviceFling(),
                                columns = GridCells.Fixed(columns),
                                state = rememberTopScrollingGrid(),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = if (framed) 3.dp else 0.dp,
                                    end = if (framed) 3.dp else 0.dp,
                                    top = if (framed) 8.dp else 2.dp,
                                    bottom = (if (framed) 8.dp else 2.dp) + LocalBottomInset.current,
                                ),
                                horizontalArrangement = Arrangement.spacedBy(9.dp),
                                verticalArrangement = Arrangement.spacedBy(11.dp),
                            ) {
                                gridItems(sorted, key = PodcastSubscriptionEntity::podcastId) { podcast ->
                                    PodcastGridItem(
                                        podcast = podcast,
                                        freshness = freshness[podcast.podcastId],
                                        pressed = podcast.podcastId == pressedPodcastIdForPreview,
                                        onClick = { onOpenPodcast(podcast.podcastId) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PodcastSortMenu(
    sortMode: PodcastSortMode,
    onSortModeChanged: (PodcastSortMode) -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    initiallyExpanded: Boolean = false,
    renderInPlace: Boolean = false,
) {
    var expanded by remember(initiallyExpanded) { mutableStateOf(initiallyExpanded) }
    // At least the control height; a larger car font grows it instead of pushing the text out.
    val minHeight = PodcastControlHeight
    val colors = ru.e1173.nookast.designsystem.LocalNookastColors.current
    Box(modifier.heightIn(min = minHeight).zIndex(if (expanded && renderInPlace) 10f else 0f)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .podcastClickable { expanded = true },
            // The same backing as the view toggle beside it (DEC-059).
            shape = ru.e1173.nookast.designsystem.NookShapes.m,
            color = colors.surface,
            border = BorderStroke(1.dp, colors.cardOutline),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = minHeight).padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The «Sort» caption lives inside the control (Stitch §6.1), saving a row.
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.podcast_sort_label).uppercase(),
                        style = ru.e1173.nookast.designsystem.NookType.meta.copy(letterSpacing = 0.6.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(
                        stringResource(
                            if (sortMode == PodcastSortMode.UPDATED) {
                                R.string.podcast_sort_updated
                            } else {
                                R.string.podcast_sort_title
                            },
                        ),
                        style = ru.e1173.nookast.designsystem.NookType.body,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                ChevronGlyph(down = true, modifier = Modifier.size(16.dp * scale))
            }
        }
        if (!renderInPlace) {
            ru.e1173.nookast.designsystem.NookMenu(expanded, { expanded = false }, minWidth = 220.dp) {
                    PodcastSortMenuItems(sortMode) { mode ->
                        onSortModeChanged(mode)
                        expanded = false
                    }
            }
        }
    }
}

@Composable
private fun PodcastSortMenuSurface(
    sortMode: PodcastSortMode,
    onSelect: (PodcastSortMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(min = 220.dp).floatingShadow(ru.e1173.nookast.designsystem.NookShapes.mRadius),
        shape = ru.e1173.nookast.designsystem.NookShapes.m,
        color = ru.e1173.nookast.designsystem.LocalNookastColors.current.surface,
        border = BorderStroke(0.5.dp, ru.e1173.nookast.designsystem.LocalNookastColors.current.surfaceOutline),
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            PodcastSortMenuItems(sortMode, onSelect)
        }
    }
}

@Composable
private fun PodcastSortMenuItems(
    sortMode: PodcastSortMode,
    onSelect: (PodcastSortMode) -> Unit,
) {
    PodcastSortMode.entries.forEach { mode ->
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(
                        if (mode == PodcastSortMode.UPDATED) {
                            R.string.podcast_sort_updated
                        } else {
                            R.string.podcast_sort_title
                        },
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            onClick = { onSelect(mode) },
            // Every item keeps the check mark's slot, so both labels start on one line.
            leadingIcon = if (mode == sortMode) {
                { CheckmarkGlyph(Modifier.size(18.dp)) }
            } else {
                { Spacer(Modifier.size(18.dp)) }
            },
        )
    }
}

@Composable
internal fun PodcastViewToggle(
    viewMode: PodcastViewMode,
    onViewModeChanged: (PodcastViewMode) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = PodcastControlHeight,
    scale: Float = 1f,
) {
    // The shared segmented control (DEC-059): glyphs centred in square segments, as tall as the sort.
    val modes = listOf(PodcastViewMode.GRID, PodcastViewMode.LIST)
    ru.e1173.nookast.designsystem.NookSegmentedToggle(
        segments = listOf(
            ru.e1173.nookast.designsystem.NookSegment(ru.e1173.nookast.designsystem.NookIcons.Grid, stringResource(R.string.podcast_view_grid)),
            ru.e1173.nookast.designsystem.NookSegment(ru.e1173.nookast.designsystem.NookIcons.ListView, stringResource(R.string.podcast_view_list)),
        ),
        selectedIndex = modes.indexOf(viewMode),
        onSelect = { onViewModeChanged(modes[it]) },
        height = height,
        modifier = modifier,
        scale = scale,
    )
}

@Composable
private fun PodcastGridItem(
    podcast: PodcastSubscriptionEntity,
    freshness: ru.e1173.nookast.data.PodcastFreshness?,
    pressed: Boolean,
    onClick: () -> Unit,
) {
    val total = podcast.episodeCount ?: 0
    val unplayed = (total - podcast.episodesPlayed).coerceAtLeast(0)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (pressed) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f)
                else Color.Transparent,
                ru.e1173.nookast.designsystem.NookShapes.m,
            )
            .podcastClickable(onClick = onClick)
            .padding(bottom = 3.dp),
    ) {
            Box {
            Artwork(
                url = podcast.artworkUrl,
                title = podcast.name,
                downloaded = false,
                badgeSize = 14.dp,
                reloadKey = podcast.refreshedAtEpochMs,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .nookElevation(ru.e1173.nookast.designsystem.NookElevation.Level1, ru.e1173.nookast.designsystem.NookShapes.mRadius * ARTWORK_CORNER_SCALE),
            )
            freshness?.let { FreshnessPill(it, Modifier.align(Alignment.BottomStart).padding(6.dp)) }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                podcast.name.ifBlank { stringResource(R.string.unknown_podcast) },
                style = ru.e1173.nookast.designsystem.NookType.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                buildPodcastGridCountLine(total, unplayed),
                style = ru.e1173.nookast.designsystem.NookType.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
    }
}

@Composable
private fun PodcastListItem(
    podcast: PodcastSubscriptionEntity,
    freshness: ru.e1173.nookast.data.PodcastFreshness?,
    selected: Boolean,
    largeControls: Boolean,
    pressed: Boolean,
    onClick: () -> Unit,
) {
    val total = podcast.episodeCount ?: 0
    val unplayed = (total - podcast.episodesPlayed).coerceAtLeast(0)
    val shape = ru.e1173.nookast.designsystem.NookShapes.m
    Surface(
        modifier = Modifier.fillMaxWidth().podcastClickable(onClick = onClick),
        shape = shape,
        color = if (pressed) {
            MaterialTheme.colorScheme.primaryContainer
        } else if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f)
        } else {
            Color.Transparent
        },
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Artwork(
                url = podcast.artworkUrl,
                title = podcast.name,
                downloaded = false,
                badgeSize = 12.dp,
                reloadKey = podcast.refreshedAtEpochMs,
                modifier = Modifier.size(if (largeControls) 62.dp else 50.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    podcast.name.ifBlank { stringResource(R.string.unknown_podcast) },
                    style = ru.e1173.nookast.designsystem.NookType.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                freshness?.let { FreshnessPill(it, Modifier.padding(top = 3.dp)) }
                Text(
                    buildPodcastCountLine(total, unplayed),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun buildPodcastCountLine(total: Int, unplayed: Int): String =
    pluralStringResource(R.plurals.podcast_total_episodes, total, total) + " · " +
        pluralStringResource(R.plurals.podcast_unplayed_episodes, unplayed, unplayed)

@Composable
private fun buildPodcastGridCountLine(total: Int, unplayed: Int): String =
    pluralStringResource(R.plurals.podcast_total_episodes, total, total) + "\n" +
        pluralStringResource(R.plurals.podcast_unplayed_episodes, unplayed, unplayed)

@Composable
private fun PodcastDetailsPanel(
    podcastId: Int,
    currentEpisodeId: Int?,
    isPlaying: Boolean,
    catalog: PodcastSubscriptionEntity?,
    details: PodcastDetails?,
    detailsLoading: Boolean,
    collapsed: Boolean,
    hidePlayed: Boolean,
    actionInProgress: Boolean,
    actionError: Boolean,
    downloadStates: Map<Int, EpisodeDownloadState>,
    downloadProgress: Map<Int, Float?>,
    pendingProgress: Map<Int, ProgressOutboxEntity>,
    progressUpdatedAt: Map<Int, Long>,
    largeControls: Boolean,
    showBack: Boolean,
    onBack: () -> Unit,
    onDescriptionCollapsedChanged: (Int, Boolean) -> Unit,
    onHidePlayedChanged: (Int, Boolean) -> Unit,
    onPlayEpisodes: (List<QueueListEntry>, Int) -> Unit,
    onOpenEpisode: (List<QueueListEntry>, Int) -> Unit,
    onDownloadEpisode: (QueueListEntry) -> Unit,
    onRemoveEpisodeDownload: (Int) -> Unit,
    onAddToQueue: (QueueListEntry) -> Unit,
    onMarkEpisodesPlayed: (List<QueueListEntry>) -> Unit,
    onMarkEpisodesUnplayed: (List<QueueListEntry>) -> Unit,
    onAddToQueueCommitted: (List<QueueListEntry>, (Boolean) -> Unit) -> Unit,
    onMarkEpisodesPlayedCommitted: (List<QueueListEntry>, (Boolean) -> Unit) -> Unit,
    onMarkEpisodesUnplayedCommitted: (List<QueueListEntry>, (Boolean) -> Unit) -> Unit,
    onUnsubscribe: (Int) -> Unit,
    onChangeFeedUrl: (Int, String, (FeedUrlChangeResult) -> Unit) -> Unit = { _, _, _ -> },
    feedProblem: ru.e1173.nookast.data.remote.ServerFeedProblem? = null,
    feedReadByPhone: Boolean = false,
    modifier: Modifier = Modifier,
    settingsMenuInitiallyExpanded: Boolean = false,
    descriptionInitiallyFullyExpanded: Boolean = false,
    selectedEpisodeIdsInitially: Set<Int> = emptySet(),
    renderMenusInPlace: Boolean = false,
) {
    val name = details?.name.orEmpty().ifBlank { catalog?.name.orEmpty() }
    var feedUrlDialog by remember(podcastId) { mutableStateOf(false) }
    if (feedUrlDialog) {
        FeedUrlDialog(
            currentUrl = catalog?.feedUrl.orEmpty(),
            onSubmit = { url, onResult -> onChangeFeedUrl(podcastId, url, onResult) },
            onDismiss = { feedUrlDialog = false },
        )
    }
    // The catalog image is already visible and successfully decoded on the previous screen.
    // Prefer it over a stale/invalid details URL to avoid losing the cover during navigation.
    val artwork = catalog?.artworkUrl.orEmpty().ifBlank { details?.artworkUrl.orEmpty() }
    val description = details?.description.orEmpty().ifBlank { catalog?.description.orEmpty() }
    val author = details?.author.orEmpty().ifBlank { catalog?.author.orEmpty() }
    val allEpisodes = remember(details?.recentEpisodes, pendingProgress) {
        details?.recentEpisodes.orEmpty().map { episode ->
            pendingProgress[episode.episodeId]?.let { local ->
                episode.copy(
                    listenedSeconds = local.positionMs / 1_000.0,
                    completed = local.completed,
                )
            } ?: episode
        }
    }
    val visibleEpisodes = if (hidePlayed) allEpisodes.filterNot { it.completed } else allEpisodes
    val queueEntries = remember(allEpisodes, name, artwork) {
        allEpisodes.mapIndexed { index, episode ->
            episode.toQueueEntry(index, name, artwork)
        }
    }
    val startedEpisodeIndices = allEpisodes.indices.filter { index ->
        val episode = allEpisodes[index]
        !episode.completed && episode.listenedSeconds > 0.0
    }
    val continueIndex = startedEpisodeIndices.maxByOrNull { index ->
        progressUpdatedAt[allEpisodes[index].episodeId] ?: Long.MIN_VALUE
    } ?: allEpisodes.indexOfLast { !it.completed }.takeIf { it >= 0 }
    var selectedEpisodeIds by remember(podcastId) { mutableStateOf(selectedEpisodeIdsInitially) }
    var selectionCommitInProgress by remember(podcastId) { mutableStateOf(false) }
    var selectionCommitFailed by remember(podcastId) { mutableStateOf(false) }
    val selectionMode = selectedEpisodeIds.isNotEmpty()
    val selectedEntries = queueEntries.filter { it.episodeId in selectedEpisodeIds }

    BackHandler(enabled = selectionMode) {
        if (!selectionCommitInProgress) {
            selectedEpisodeIds = emptySet()
            selectionCommitFailed = false
        }
    }

    val panelShape = androidx.compose.ui.graphics.RectangleShape
    val panelModifier = modifier
    val podcastBackground = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        PodcastDarkBackground
    } else {
        PodcastLightBackground
    }
    // The cover's light spreads over the whole screen as on Now Playing (user, 29.10.2026): the
    // page runs under the status bar and to the screen edges, so the light has no edges. A wide
    // layout lights both panes from PodcastScreen instead, so it is not cut at the pane.
    // The page's colour and light lie in the header glass's source, so the glass keeps their colour.
    val podcastHaze = dev.chrisbanes.haze.rememberHazeState()
    Box(panelModifier) {
    if (showBack) {
        Box(Modifier.matchParentSize().hazeSource(podcastHaze, zIndex = -1f).background(podcastBackground)) {
            ArtworkTintBackdrop(artwork, Modifier.matchParentSize())
        }
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = panelShape,
        color = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        val topBar: @Composable () -> Unit = {
        if (selectionMode) {
            PodcastSelectionToolbar(
                selectedCount = selectedEpisodeIds.size,
                allSelected = visibleEpisodes.isNotEmpty() &&
                    visibleEpisodes.all { it.episodeId in selectedEpisodeIds },
                largeControls = largeControls,
                enabled = !selectionCommitInProgress,
                onExit = {
                    selectedEpisodeIds = emptySet()
                    selectionCommitFailed = false
                },
                onSelectAll = {
                    val visibleIds = visibleEpisodes.mapTo(linkedSetOf()) {
                        it.episodeId
                    }
                    selectedEpisodeIds = if (visibleIds.all(selectedEpisodeIds::contains)) {
                        selectedEpisodeIds - visibleIds
                    } else {
                        selectedEpisodeIds + visibleIds
                    }
                },
                onAddToQueue = {
                    selectionCommitInProgress = true
                    selectionCommitFailed = false
                    onAddToQueueCommitted(selectedEntries) { succeeded ->
                        selectionCommitInProgress = false
                        selectionCommitFailed = !succeeded
                        selectedEpisodeIds = selectionAfterLocalCommit(
                            selectedEpisodeIds,
                            succeeded,
                        )
                    }
                },
                onMarkPlayed = {
                    selectionCommitInProgress = true
                    selectionCommitFailed = false
                    onMarkEpisodesPlayedCommitted(selectedEntries) { succeeded ->
                        selectionCommitInProgress = false
                        selectionCommitFailed = !succeeded
                        selectedEpisodeIds = selectionAfterLocalCommit(
                            selectedEpisodeIds,
                            succeeded,
                        )
                    }
                },
                onMarkUnplayed = {
                    selectionCommitInProgress = true
                    selectionCommitFailed = false
                    onMarkEpisodesUnplayedCommitted(selectedEntries) { succeeded ->
                        selectionCommitInProgress = false
                        selectionCommitFailed = !succeeded
                        selectedEpisodeIds = selectionAfterLocalCommit(
                            selectedEpisodeIds,
                            succeeded,
                        )
                    }
                },
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The back button floats over the glass in its disc, as in every header (user, 2.11.2026).
                ru.e1173.nookast.designsystem.NookHeaderDisc(
                    ru.e1173.nookast.designsystem.NookIcons.ChevronLeft,
                    stringResource(R.string.podcast_back_to_library),
                    onBack,
                )
                Text(
                    text = name.ifBlank { stringResource(R.string.podcasts_title) },
                    modifier = Modifier.weight(1f),
                    style = ru.e1173.nookast.designsystem.NookType.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.size(48.dp))
            }
        }
        }
        // Phone: Back and the name stay on top and turn to glass, reaching up under the status bar,
        // once the page scrolls (user, 29.10.2026).
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        val listScrolled by remember {
            derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
        }
        Box(Modifier.fillMaxSize()) {
        LazyColumn(
            flingBehavior = deviceFling(),
            modifier = Modifier.fillMaxSize().then(if (showBack) Modifier.hazeSource(podcastHaze).glassEdgeFade() else Modifier),
            state = listState,
            contentPadding = PaddingValues(
                start = if (largeControls) 18.dp else 12.dp,
                end = if (largeControls) 18.dp else 12.dp,
                top = LocalTopInset.current + if (showBack) 56.dp else if (largeControls) 18.dp else 12.dp,
                bottom = (if (showBack) 4.dp else if (largeControls) 18.dp else 12.dp) + LocalBottomInset.current,
            ),
            verticalArrangement = Arrangement.spacedBy(if (largeControls) 6.dp else 4.dp),
        ) {
            // A phone page keeps this bar on top as glass (below); a wide pane lists it.
            if (selectionMode && !showBack) {
                item { topBar() }
            }
            item {
                PodcastHero(
                    name = name,
                    coverScroll = { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 0f },
                    artworkUrl = artwork,
                    artworkRevision = catalog?.refreshedAtEpochMs,
                    author = author,
                    episodeCount = details?.episodeCount ?: catalog?.episodeCount,
                    episodesPlayed = details?.episodesPlayed ?: catalog?.episodesPlayed,
                    showBack = false,
                    actionInProgress = actionInProgress,
                    largeControls = largeControls,
                    continueEnabled = continueIndex != null && queueEntries.isNotEmpty(),
                    onBack = onBack,
                    onContinue = {
                        continueIndex?.let { onPlayEpisodes(queueEntries, it) }
                    },
                    onUnsubscribe = { onUnsubscribe(podcastId) },
                    onChangeFeedUrl = { feedUrlDialog = true },
                    settingsMenuInitiallyExpanded = settingsMenuInitiallyExpanded,
                    renderMenusInPlace = renderMenusInPlace,
                )
            }
            if (feedProblem?.reported == true) {
                item(key = "feed-problem") {
                    FeedProblemPill(feedProblem, feedReadByPhone, onChangeFeedUrl = { feedUrlDialog = true })
                }
            }
            // Episodes to come, only when none are shown yet (design audit 5.4: a refresh stays quiet).
            if (detailsLoading && allEpisodes.isEmpty()) {
                item { ru.e1173.nookast.designsystem.NookSkeletonRows(5, artwork = false) }
            }
            if (actionError || selectionCommitFailed) {
                item {
                    Surface(
                        shape = ru.e1173.nookast.designsystem.NookShapes.m,
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ) {
                        Text(
                            stringResource(R.string.podcast_action_error),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item {
                PodcastDescriptionCard(
                    podcastId = podcastId,
                    description = description,
                    collapsed = collapsed,
                    onCollapsedChanged = onDescriptionCollapsedChanged,
                    initiallyFullyExpanded = descriptionInitiallyFullyExpanded,
                )
            }
            item {
                ru.e1173.nookast.designsystem.NookSectionTitle(
                    stringResource(R.string.podcast_episodes),
                    Modifier.padding(horizontal = 2.dp),
                    scale = if (largeControls) 1.12f else 1f,
                ) {
                    HidePlayedToggle(hidePlayed) { onHidePlayedChanged(podcastId, !hidePlayed) }
                }
            }
            if (visibleEpisodes.isEmpty() && !detailsLoading) {
                item {
                    Text(
                        stringResource(R.string.podcast_no_visible_episodes),
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(visibleEpisodes, key = PodcastEpisodeProgress::episodeId) { episode ->
                    val originalIndex = allEpisodes.indexOfFirst { it.episodeId == episode.episodeId }
                    val downloadState = downloadStates[episode.episodeId]
                        ?: EpisodeDownloadState.NOT_DOWNLOADED
                    // The shared episode row (user, 27.10.2026): same card, play button and menu as
                    // the Dashboard and the Feed; the date line instead of the podcast name.
                    val rowEntry = episode.toQueueEntry(originalIndex.coerceAtLeast(0), name, artwork)
                    val rowDownloaded = episode.downloaded || downloadState == EpisodeDownloadState.DOWNLOADED
                    EpisodeRow(
                        title = episode.title,
                        meta = episodeMetaLine(episode.episodeId, episode.title, episode.publishedAt),
                        artworkUrl = episode.artworkUrl.ifBlank { artwork },
                        podcastName = name,
                        durationSeconds = episode.durationSeconds,
                        listenedSeconds = episode.listenedSeconds,
                        completed = episode.completed,
                        downloaded = rowDownloaded,
                        downloading = downloadState == EpisodeDownloadState.QUEUED || downloadState == EpisodeDownloadState.DOWNLOADING,
                        downloadProgress = downloadProgress[episode.episodeId],
                        playing = episode.episodeId == currentEpisodeId && isPlaying,
                        current = episode.episodeId == currentEpisodeId,
                        largeControls = largeControls,
                        selected = episode.episodeId in selectedEpisodeIds,
                        selectionMode = selectionMode,
                        onOpen = {
                            if (selectionCommitInProgress) {
                                // A selection is being committed; taps wait.
                            } else if (selectionMode) {
                                selectedEpisodeIds = selectedEpisodeIds.toggle(episode.episodeId)
                            } else if (originalIndex >= 0) {
                                onOpenEpisode(queueEntries, originalIndex)
                            }
                        },
                        onLongPress = {
                            if (!selectionCommitInProgress) {
                                selectedEpisodeIds = selectedEpisodeIds + episode.episodeId
                            }
                        },
                        onPlay = { if (originalIndex >= 0) onPlayEpisodes(queueEntries, originalIndex) },
                        menu = standardEpisodeMenu(
                            downloaded = rowDownloaded,
                            completed = episode.completed,
                            onAddToQueue = { onAddToQueue(rowEntry) },
                            onMarkPlayed = { onMarkEpisodesPlayed(listOf(rowEntry)) },
                            onMarkUnplayed = { onMarkEpisodesUnplayed(listOf(rowEntry)) },
                            onDownload = { onDownloadEpisode(rowEntry) },
                            onDeleteDownload = { onRemoveEpisodeDownload(episode.episodeId) },
                        ),
                    )
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }
        if (showBack) {
            TopGlassHeader(podcastHaze, listScrolled, Modifier.align(Alignment.TopCenter).fillMaxWidth(), tint = podcastBackground) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = LocalTopInset.current)
                        .padding(horizontal = 12.dp)
                        .height(52.dp),
                    contentAlignment = Alignment.Center,
                ) { topBar() }
            }
        }
        }
    }
    }
}

@Composable
private fun PodcastHero(
    name: String,
    artworkUrl: String,
    artworkRevision: Long?,
    author: String,
    episodeCount: Int?,
    episodesPlayed: Int?,
    showBack: Boolean,
    actionInProgress: Boolean,
    largeControls: Boolean,
    continueEnabled: Boolean,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onUnsubscribe: () -> Unit,
    onChangeFeedUrl: () -> Unit = {},
    settingsMenuInitiallyExpanded: Boolean = false,
    renderMenusInPlace: Boolean = false,
    /** How far the page has scrolled past the top, in pixels: the cover lags at 0.3 (design audit 4.6). */
    coverScroll: () -> Float = { 0f },
) {
    val coverSize = if (largeControls) 148.dp else 118.dp
    val shape = ru.e1173.nookast.designsystem.NookShapes.l
    val heroBackground = Color.Transparent
    val artworkBitmap = rememberArtworkBitmap(artworkUrl, artworkRevision)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = heroBackground,
        tonalElevation = 0.dp,
    ) {
        Box {
            // The cover's light now washes the whole page as on Now Playing (user, 29.10.2026: the
            // glow inside this card looked muddy and ended at its edges); the card is clear.
            val glow = rememberArtworkGlow(artworkBitmap)
            val dark = ru.e1173.nookast.designsystem.LocalNookastColors.current.isDark
            Row(
                modifier = Modifier.fillMaxWidth().padding(if (largeControls) 14.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                val coverGlow = (glow?.first() ?: Color.Black).copy(alpha = if (dark) 0.35f else 0.22f)
                Artwork(
                    url = artworkUrl,
                    title = name,
                    downloaded = false,
                    badgeSize = 14.dp,
                    reloadKey = artworkRevision,
                    // A narrow pane shrinks the cover (up to ~38 % of the row) so the buttons keep their labels.
                    modifier = Modifier
                        .weight(0.6f, fill = false)
                        .widthIn(max = coverSize)
                        .aspectRatio(1f)
                        .graphicsLayer { translationY = coverScroll() * COVER_PARALLAX }
                        .drawBehind {
                            with(this) { drawCssShadow(coverGlow, 28.dp, 12.dp.toPx() * ARTWORK_CORNER_SCALE, dy = 8.dp) }
                        },
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    if (author.isNotBlank()) {
                        Text(
                            author.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = ru.e1173.nookast.designsystem.LocalNookastColors.current.accentText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(
                        name.ifBlank { stringResource(R.string.unknown_podcast) },
                        style = ru.e1173.nookast.designsystem.NookType.heroTitle.scaled(if (largeControls) 1.12f else 1f),
                        color = ru.e1173.nookast.designsystem.LocalNookastColors.current.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    episodeCount?.let { count ->
                        val total = pluralStringResource(
                            R.plurals.podcast_total_episodes,
                            count,
                            count,
                        )
                        val played = episodesPlayed?.let {
                            pluralStringResource(R.plurals.played_episode_count, it, it)
                        }
                        Text(
                            listOfNotNull(total, played).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        val continueButtonEnabled = continueEnabled && !actionInProgress
                        val buttonScale = if (largeControls) 4f / 3f else 1f
                        // Shrinks before the gear does, so the gear never leaves a narrow pane.
                        ru.e1173.nookast.designsystem.NookButton(
                            onClick = onContinue,
                            modifier = Modifier.weight(1f, fill = false),
                            enabled = continueButtonEnabled,
                            scale = buttonScale,
                        ) {
                            ru.e1173.nookast.designsystem.PlayPauseGlyph(false, androidx.compose.material3.LocalContentColor.current, 30.dp * buttonScale)
                            Text(
                                stringResource(R.string.podcast_continue),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                        PodcastSettingsMenu(
                            actionInProgress = actionInProgress,
                            largeControls = largeControls,
                            onUnsubscribe = onUnsubscribe,
                            onChangeFeedUrl = onChangeFeedUrl,
                            initiallyExpanded = settingsMenuInitiallyExpanded,
                            renderInPlace = renderMenusInPlace,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PodcastSettingsMenu(
    actionInProgress: Boolean,
    largeControls: Boolean,
    onUnsubscribe: () -> Unit,
    onChangeFeedUrl: () -> Unit = {},
    initiallyExpanded: Boolean = false,
    renderInPlace: Boolean = false,
) {
    var expanded by remember(initiallyExpanded) { mutableStateOf(initiallyExpanded) }
    val settingsDescription = stringResource(R.string.podcast_settings)
    // The shared icon disc (DEC-059): as tall as «Continue» beside it, flat, the glyph half of it.
    val disc = PodcastActionHeight * if (largeControls) 4f / 3f else 1f
    Box(Modifier.zIndex(if (expanded && renderInPlace) 10f else 0f)) {
        ru.e1173.nookast.designsystem.NookHeaderDisc(
            icon = if (actionInProgress) null else ru.e1173.nookast.designsystem.NookIcons.Settings,
            contentDescription = settingsDescription,
            onClick = { expanded = true },
            discSize = disc,
            iconSize = disc / 2,
            // Laid out at the disc, so the row keeps the height of «Continue».
            zone = disc,
            elevated = false,
            enabled = !actionInProgress,
            content = if (actionInProgress) {
                {
                    CircularProgressIndicator(
                        Modifier.size(disc / 2),
                        color = ru.e1173.nookast.designsystem.LocalNookastColors.current.accentText,
                        strokeWidth = 2.dp,
                    )
                }
            } else {
                null
            },
        )
        if (!renderInPlace) {
            ru.e1173.nookast.designsystem.NookMenu(expanded, { expanded = false }, minWidth = if (largeControls) 300.dp else 220.dp) {
                    PodcastSettingsMenuItems(
                        largeControls = largeControls,
                        onChangeFeedUrl = {
                            expanded = false
                            onChangeFeedUrl()
                        },
                        onUnsubscribe = {
                            expanded = false
                            onUnsubscribe()
                        },
                    )
            }
        }
    }
}

@Composable
private fun PodcastSettingsMenuSurface(
    onUnsubscribe: () -> Unit,
    modifier: Modifier = Modifier,
    largeControls: Boolean = false,
) {
    Surface(
        modifier = modifier.floatingShadow(ru.e1173.nookast.designsystem.NookShapes.mRadius),
        shape = ru.e1173.nookast.designsystem.NookShapes.m,
        color = ru.e1173.nookast.designsystem.LocalNookastColors.current.surface,
        border = BorderStroke(0.5.dp, ru.e1173.nookast.designsystem.LocalNookastColors.current.surfaceOutline),
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            PodcastSettingsMenuItems(largeControls, onUnsubscribe)
        }
    }
}

@Composable
private fun PodcastSettingsMenuItems(
    largeControls: Boolean,
    onUnsubscribe: () -> Unit,
    onChangeFeedUrl: () -> Unit = {},
) {
    Text(
        stringResource(R.string.podcast_actions_title),
        modifier = Modifier.padding(
            start = if (largeControls) 22.dp else 16.dp,
            end = if (largeControls) 22.dp else 16.dp,
            top = if (largeControls) 14.dp else 8.dp,
            bottom = 4.dp,
        ),
        style = ru.e1173.nookast.designsystem.NookType.rowTitle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.podcast_change_feed_url), style = ru.e1173.nookast.designsystem.NookType.rowTitle) },
        leadingIcon = {
            Surface(
                modifier = Modifier.size(if (largeControls) 38.dp else 30.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "RSS",
                        style = ru.e1173.nookast.designsystem.NookType.navLabel.scaled(if (largeControls) 1.222f else 1f),
                        color = ru.e1173.nookast.designsystem.LocalNookastColors.current.accentText,
                    )
                }
            }
        },
        onClick = onChangeFeedUrl,
        modifier = Modifier.heightIn(min = if (largeControls) 72.dp else 48.dp),
    )
    DropdownMenuItem(
        text = {
            Text(
                stringResource(R.string.podcast_unsubscribe_full),
                color = MaterialTheme.colorScheme.error,
                style = ru.e1173.nookast.designsystem.NookType.rowTitle,
            )
        },
        leadingIcon = {
            Surface(
                modifier = Modifier.size(if (largeControls) 38.dp else 30.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    UnsubscribeGlyph(
                        Modifier.size(if (largeControls) 21.dp else 17.dp),
                        MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        onClick = onUnsubscribe,
        modifier = Modifier.heightIn(min = if (largeControls) 72.dp else 48.dp),
    )
}

@Composable
private fun UnsubscribeGlyph(modifier: Modifier = Modifier, color: Color) {
    // Drawn from NookIcons (design audit 3.7).
    androidx.compose.material3.Icon(ru.e1173.nookast.designsystem.NookIcons.Close, contentDescription = null, tint = color, modifier = modifier)
}

@Composable
private fun PodcastSelectionToolbar(
    selectedCount: Int,
    allSelected: Boolean,
    largeControls: Boolean,
    enabled: Boolean,
    onExit: () -> Unit,
    onSelectAll: () -> Unit,
    onAddToQueue: () -> Unit,
    onMarkPlayed: () -> Unit,
    onMarkUnplayed: () -> Unit,
) {
    val selectAllDescription = stringResource(
        if (allSelected) R.string.podcast_clear_selection else R.string.podcast_select_all,
    )
    val addToQueueDescription = stringResource(R.string.podcast_add_to_queue)
    val markPlayedDescription = stringResource(R.string.podcast_mark_selected_played)
    val markUnplayedDescription = stringResource(R.string.podcast_mark_selected_unplayed)
    val actionColor = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.66f),
        shape = ru.e1173.nookast.designsystem.NookShapes.m,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = actionColor,
        tonalElevation = 3.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = if (largeControls) 48.dp else 42.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The shared icon button as a bare glyph on the toolbar (DEC-059).
                ru.e1173.nookast.designsystem.NookHeaderDisc(
                    icon = ru.e1173.nookast.designsystem.NookIcons.ChevronLeft,
                    contentDescription = stringResource(R.string.back),
                    onClick = onExit,
                    discSize = 38.dp * if (largeControls) 4f / 3f else 1f,
                    zone = if (largeControls) 48.dp else 42.dp,
                    fill = Color.Transparent,
                    tint = actionColor,
                    enabled = enabled,
                )
                Text(
                    stringResource(R.string.podcast_selected_count, selectedCount),
                    modifier = Modifier.weight(1f),
                    style = ru.e1173.nookast.designsystem.NookType.rowTitle,
                    maxLines = 1,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                PodcastSelectionAction(
                    label = stringResource(
                        if (allSelected) R.string.podcast_clear_selection_short
                        else R.string.podcast_select_all_short,
                    ),
                    description = selectAllDescription,
                    enabled = enabled,
                    onClick = onSelectAll,
                    modifier = Modifier.weight(1f),
                ) { SelectAllGlyph(allSelected, Modifier.size(18.dp), actionColor) }
                PodcastSelectionAction(
                    label = stringResource(R.string.podcast_add_to_queue_short),
                    description = addToQueueDescription,
                    enabled = enabled,
                    onClick = onAddToQueue,
                    modifier = Modifier.weight(1f),
                ) { QueueAddGlyph(Modifier.size(18.dp), actionColor) }
                PodcastSelectionAction(
                    label = stringResource(R.string.podcast_mark_played_short),
                    description = markPlayedDescription,
                    enabled = enabled,
                    onClick = onMarkPlayed,
                    modifier = Modifier.weight(1f),
                ) { CheckmarkGlyph(Modifier.size(18.dp), actionColor) }
                PodcastSelectionAction(
                    label = stringResource(R.string.podcast_mark_unplayed_short),
                    description = markUnplayedDescription,
                    enabled = enabled,
                    onClick = onMarkUnplayed,
                    modifier = Modifier.weight(1f),
                ) { MarkUnplayedGlyph(Modifier.size(18.dp), actionColor) }
            }
        }
    }
}

@Composable
private fun PodcastSelectionAction(
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 46.dp)
            .podcastClickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        shape = ru.e1173.nookast.designsystem.NookShapes.s,
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            glyph()
            Text(
                label,
                style = ru.e1173.nookast.designsystem.NookType.meta,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PodcastDescriptionCard(
    podcastId: Int,
    description: String,
    collapsed: Boolean,
    onCollapsedChanged: (Int, Boolean) -> Unit,
    initiallyFullyExpanded: Boolean = false,
) {
    var fullyExpanded by remember(podcastId) { mutableStateOf(initiallyFullyExpanded) }
    val moreLabel = stringResource(R.string.podcast_more)
    val fallback = stringResource(R.string.description_unavailable)
    val body = normalizeDescriptionText(description).ifBlank { fallback }
    val needsMore = body.length > 150
    // «… more» covers the text end in the card's own colour.
    val descriptionBackground = ru.e1173.nookast.designsystem.LocalNookastColors.current.let { it.surface.compositeOver(it.background) }
    Box(Modifier.fillMaxWidth().nookCard()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().podcastClickable {
                    if (collapsed) {
                        fullyExpanded = false
                        onCollapsedChanged(podcastId, false)
                    } else {
                        fullyExpanded = false
                        onCollapsedChanged(podcastId, true)
                    }
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.podcast_description),
                    modifier = Modifier.weight(1f),
                    style = ru.e1173.nookast.designsystem.NookType.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                ChevronGlyph(down = collapsed, modifier = Modifier.size(16.dp))
            }
            AnimatedVisibility(
                visible = !collapsed,
                enter = fadeIn(ru.e1173.nookast.designsystem.NookMotion.standard()) + expandVertically(ru.e1173.nookast.designsystem.NookMotion.standard()),
                exit = fadeOut(ru.e1173.nookast.designsystem.NookMotion.fast()) + shrinkVertically(ru.e1173.nookast.designsystem.NookMotion.standard()),
            ) {
                Column {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.podcastClickable(
                            enabled = !fullyExpanded && needsMore,
                            onClick = { fullyExpanded = true },
                        ),
                    ) {
                        if (fullyExpanded) {
                            RichDescriptionText(
                                html = description,
                                fallback = fallback,
                                style = ru.e1173.nookast.designsystem.NookType.meta,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                body,
                                style = ru.e1173.nookast.designsystem.NookType.meta,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = if (needsMore) TextOverflow.Clip else TextOverflow.Ellipsis,
                            )
                        }
                        if (!fullyExpanded && needsMore) {
                            Text(
                                "… $moreLabel",
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .background(descriptionBackground)
                                    .padding(start = 4.dp),
                                style = ru.e1173.nookast.designsystem.NookType.navLabel,
                                color = ru.e1173.nookast.designsystem.LocalNookastColors.current.accentText,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Set<Int>.toggle(value: Int): Set<Int> = if (value in this) this - value else this + value

@Composable
internal fun CompactCheckbox(checked: Boolean) {
    val borderColor = if (checked) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outline
    val fillColor = if (checked) MaterialTheme.colorScheme.primary else Color.Transparent
    Canvas(
        Modifier
            .size(15.dp)
            .border(1.dp, borderColor, ru.e1173.nookast.designsystem.NookShapes.checkbox)
            .background(fillColor, ru.e1173.nookast.designsystem.NookShapes.checkbox),
    ) {
        if (checked) {
            val path = Path().apply {
                moveTo(size.width * 0.22f, size.height * 0.53f)
                lineTo(size.width * 0.43f, size.height * 0.73f)
                lineTo(size.width * 0.80f, size.height * 0.28f)
            }
            drawPath(
                path,
                Color.White,
                style = Stroke(size.minDimension * 0.12f, cap = StrokeCap.Round),
            )
        }
    }
}

internal fun PodcastEpisodeProgress.toQueueEntry(
    index: Int,
    fallbackPodcastName: String,
    fallbackArtwork: String,
): QueueListEntry = QueueListEntry(
    episodeId = episodeId,
    queuePosition = index.coerceAtLeast(0),
    episodeTitle = title,
    podcastName = podcastName.ifBlank { fallbackPodcastName },
    artworkUrl = artworkUrl.ifBlank { fallbackArtwork },
    mediaUrl = mediaUrl,
    durationSeconds = durationSeconds,
    listenDurationSeconds = listenedSeconds,
    completed = completed,
    description = description,
    publishedAt = publishedAt,
    downloaded = downloaded,
    refreshedAtEpochMs = null,
)

@Composable
private fun PodcastSelectionPrompt(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.border(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
            PodcastPanelShape,
        ),
        shape = PodcastPanelShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.podcast_select_prompt),
                modifier = Modifier.padding(32.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChevronGlyph(down: Boolean, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    // Drawn from NookIcons (design audit 3.7).
    androidx.compose.material3.Icon(if (down) ru.e1173.nookast.designsystem.NookIcons.ChevronDown else ru.e1173.nookast.designsystem.NookIcons.ChevronUp, contentDescription = null, tint = color, modifier = modifier)
}

@Composable
private fun CheckmarkGlyph(
    modifier: Modifier = Modifier,
    color: Color = ru.e1173.nookast.designsystem.LocalNookastColors.current.accentText,
) {
    // Drawn from NookIcons (design audit 3.7).
    androidx.compose.material3.Icon(ru.e1173.nookast.designsystem.NookIcons.Check, contentDescription = null, tint = color, modifier = modifier)
}

@Composable
private fun SelectAllGlyph(
    allSelected: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    // Drawn from NookIcons (design audit 3.7).
    androidx.compose.material3.Icon(if (allSelected) ru.e1173.nookast.designsystem.NookIcons.SelectAllChecked else ru.e1173.nookast.designsystem.NookIcons.SelectAll, contentDescription = null, tint = color, modifier = modifier)
}

@Composable
private fun QueueAddGlyph(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    // Drawn from NookIcons (design audit 3.7).
    androidx.compose.material3.Icon(ru.e1173.nookast.designsystem.NookIcons.ListPlus, contentDescription = null, tint = color, modifier = modifier)
}

@Composable
private fun MarkUnplayedGlyph(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    // Drawn from NookIcons (design audit 3.7).
    androidx.compose.material3.Icon(ru.e1173.nookast.designsystem.NookIcons.CircleDot, contentDescription = null, tint = color, modifier = modifier)
}

/**
 * Two colours of the cover for its glow (user, 30.10.2026: an all-purple cover lit the screen
 * brown). The cover used to be averaged down to two pixels, which mixed its colours into mud, and a
 * near-grey result was forced to 25 % saturation at hue 0, i.e. red-brown. Now the dominant hue
 * wins, a second distinct hue joins it, and a grey cover stays grey.
 */
@Composable
internal fun rememberArtworkGlow(bitmap: androidx.compose.ui.graphics.ImageBitmap?): List<Color>? =
    remember(bitmap) {
        bitmap ?: return@remember null
        runCatching {
            val small = android.graphics.Bitmap.createScaledBitmap(
                bitmap.asAndroidBitmap().copy(android.graphics.Bitmap.Config.ARGB_8888, false),
                GLOW_SAMPLE_SIDE,
                GLOW_SAMPLE_SIDE,
                true,
            )
            val pixels = IntArray(GLOW_SAMPLE_SIDE * GLOW_SAMPLE_SIDE)
            small.getPixels(pixels, 0, GLOW_SAMPLE_SIDE, 0, 0, GLOW_SAMPLE_SIDE, GLOW_SAMPLE_SIDE)
            artworkGlowColors(pixels).map { Color(it) }
        }.getOrNull()
    }

private const val GLOW_SAMPLE_SIDE = 24

/**
 * A show that went quiet, ended or lost its feed (user, 5.11.2026): a small capsule over the cover.
 * A dark scrim keeps it readable on any artwork; a missing feed is coral, as the source chip's alarm.
 */
@Composable
private fun FreshnessPill(freshness: ru.e1173.nookast.data.PodcastFreshness, modifier: Modifier = Modifier) {
    val label = stringResource(
        when (freshness) {
            ru.e1173.nookast.data.PodcastFreshness.QUIET_HALF_YEAR -> R.string.freshness_half_year
            ru.e1173.nookast.data.PodcastFreshness.QUIET_YEAR -> R.string.freshness_year
            ru.e1173.nookast.data.PodcastFreshness.ABANDONED -> R.string.freshness_abandoned
            ru.e1173.nookast.data.PodcastFreshness.FEED_UNAVAILABLE -> R.string.freshness_feed_unavailable
            ru.e1173.nookast.data.PodcastFreshness.SERVER_CANNOT_UPDATE -> R.string.freshness_server_cannot_update
            ru.e1173.nookast.data.PodcastFreshness.ACTIVE -> return
        },
    )
    val alarm = freshness == ru.e1173.nookast.data.PodcastFreshness.FEED_UNAVAILABLE
    Text(
        label,
        style = ru.e1173.nookast.designsystem.NookType.navLabel,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(
                if (alarm) ru.e1173.nookast.designsystem.NookFixedColors.AlarmBadge else Color.Black.copy(alpha = 0.62f),
                ru.e1173.nookast.designsystem.NookShapes.full,
            )
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/**
 * Another RSS address for a subscription (user, 6.11.2026): for a show that moved to another host
 * without a redirect. The phone reads the new feed first, then the server saves and reads it.
 */
@Composable
private fun FeedUrlDialog(
    currentUrl: String,
    onSubmit: (String, (FeedUrlChangeResult) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(currentUrl) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<FeedUrlChangeResult?>(null) }
    val done = result is FeedUrlChangeResult.Saved
    ru.e1173.nookast.designsystem.ScaledAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.podcast_change_feed_url)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.feed_url_help), style = MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        result = null
                    },
                    enabled = !busy && !done,
                    singleLine = true,
                    label = { Text(stringResource(R.string.feed_url_label)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.feed_url_checking), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                result?.let { outcome ->
                    val error = outcome !is FeedUrlChangeResult.Saved
                    Text(
                        feedUrlResultText(outcome),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (error) MaterialTheme.colorScheme.error else ru.e1173.nookast.designsystem.LocalNookastColors.current.accentText,
                    )
                }
            }
        },
        confirmButton = {
            if (done) {
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.feed_url_done)) }
            } else {
                androidx.compose.material3.TextButton(
                    enabled = !busy && url.isNotBlank() && url.trim() != currentUrl.trim(),
                    onClick = {
                        busy = true
                        onSubmit(url) {
                            busy = false
                            result = it
                        }
                    },
                ) { Text(stringResource(R.string.feed_url_save)) }
            }
        },
        dismissButton = {
            if (!done) {
                androidx.compose.material3.TextButton(enabled = !busy, onClick = onDismiss) {
                    Text(stringResource(R.string.control_journal_cancel))
                }
            }
        },
    )
}

@Composable
private fun feedUrlResultText(result: FeedUrlChangeResult): String = when (result) {
    is FeedUrlChangeResult.Saved -> if (result.title.isNotBlank()) {
        pluralStringResource(R.plurals.feed_url_saved, result.episodeCount, result.title, result.episodeCount)
    } else {
        stringResource(R.string.feed_url_saved_plain)
    }
    is FeedUrlChangeResult.ServerCannotRead -> stringResource(
        R.string.feed_url_server_cannot_read,
        stringResource(feedErrorText(result.kind)),
    )
    FeedUrlChangeResult.InvalidUrl -> stringResource(R.string.feed_url_invalid)
    is FeedUrlChangeResult.DeviceCannotRead ->
        stringResource(if (result.offline) R.string.feed_url_offline else R.string.feed_url_not_feed)
    FeedUrlChangeResult.Offline -> stringResource(R.string.feed_url_offline)
    is FeedUrlChangeResult.Refused -> if (result.message.isNotBlank()) {
        stringResource(R.string.feed_url_refused_reason, result.message)
    } else {
        stringResource(R.string.feed_url_refused)
    }
}

/**
 * The server cannot update this podcast's feed (PinePods 0.9.1+ «Feed problems»), as a capsule in
 * the card like the player's source chip (user, 6.11.2026: a red banner fell out of the design).
 * When the phone still reads the feed, the feed is alive and only the server is stuck; a tap
 * explains why and offers another RSS address.
 */
@Composable
private fun FeedProblemPill(
    problem: ru.e1173.nookast.data.remote.ServerFeedProblem,
    readByPhone: Boolean,
    onChangeFeedUrl: () -> Unit,
) {
    val colors = ru.e1173.nookast.designsystem.LocalNookastColors.current
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    var explain by remember { mutableStateOf(false) }
    val coral = ru.e1173.nookast.designsystem.NookFixedColors.alertCoral(colors.isDark)
    val tone = if (readByPhone) colors.textSecondary else coral
    val since = problem.failingSinceEpochMs?.let {
        java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("d MMMM", locale))
    }
    val label = stringResource(if (readByPhone) R.string.freshness_server_cannot_update else R.string.freshness_feed_unavailable)
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            Modifier
                .height(30.dp)
                .clip(CircleShape)
                .background(colors.textSecondary.copy(alpha = if (colors.isDark) 0.12f else 0.09f))
                .clickable(role = androidx.compose.ui.semantics.Role.Button) { explain = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(Modifier.size(7.dp).background(if (readByPhone) ru.e1173.nookast.designsystem.NookFixedColors.Amber else coral, CircleShape))
            Text(
                if (since != null) "$label · $since" else label,
                style = ru.e1173.nookast.designsystem.NookType.label,
                color = tone,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            androidx.compose.material3.Icon(ru.e1173.nookast.designsystem.NookIcons.ChevronRight, null, tint = tone, modifier = Modifier.size(14.dp))
        }
    }
    if (explain) {
        ru.e1173.nookast.designsystem.ScaledAlertDialog(
            onDismissRequest = { explain = false },
            title = { Text(label) },
            text = {
                Text(
                    when {
                        problem.privateAddress -> stringResource(R.string.feed_problem_private_address)
                        readByPhone -> stringResource(R.string.feed_problem_server_only, stringResource(feedErrorText(problem.kind)))
                        else -> stringResource(R.string.feed_problem, stringResource(feedErrorText(problem.kind)))
                    },
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    explain = false
                    onChangeFeedUrl()
                }) { Text(stringResource(R.string.podcast_change_feed_url)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { explain = false }) { Text(stringResource(R.string.feed_url_done)) }
            },
        )
    }
}

internal fun feedErrorText(kind: ru.e1173.nookast.data.remote.ServerFeedError): Int = when (kind) {
    ru.e1173.nookast.data.remote.ServerFeedError.GONE -> R.string.feed_error_gone
    ru.e1173.nookast.data.remote.ServerFeedError.AUTH -> R.string.feed_error_auth
    ru.e1173.nookast.data.remote.ServerFeedError.NOT_FEED -> R.string.feed_error_not_feed
    ru.e1173.nookast.data.remote.ServerFeedError.UNREACHABLE -> R.string.feed_error_unreachable
    ru.e1173.nookast.data.remote.ServerFeedError.THROTTLED -> R.string.feed_error_throttled
    else -> R.string.feed_error_other
}

private const val COVER_PARALLAX = 0.3f
