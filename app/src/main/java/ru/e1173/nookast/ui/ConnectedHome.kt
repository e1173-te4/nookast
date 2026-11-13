package ru.e1173.nookast.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import ru.e1173.nookast.designsystem.drawCssShadow
import ru.e1173.nookast.designsystem.NookIcons
import ru.e1173.nookast.designsystem.focusRing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.drawBehind
import ru.e1173.nookast.designsystem.LocalNookastColors
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.roundToInt
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import ru.e1173.nookast.R
import ru.e1173.nookast.auth.LoginUiState
import ru.e1173.nookast.data.local.QueueListEntry
import ru.e1173.nookast.data.remote.QueueLoadError
import ru.e1173.nookast.player.PlaybackError
import ru.e1173.nookast.player.PlaybackUiState
import ru.e1173.nookast.player.StreamReachability
import ru.e1173.nookast.player.QueueInsertionPosition
import ru.e1173.nookast.player.QueueSwipeAction
import ru.e1173.nookast.offline.EpisodeDownloadState
import ru.e1173.nookast.preferences.AppThemeMode
import ru.e1173.nookast.preferences.PodcastSortMode
import ru.e1173.nookast.preferences.PodcastViewMode

internal enum class AppDestination(
    val labelResource: Int,
) {
    HOME(R.string.home_title),
    PODCASTS(R.string.podcasts_title),
    FEED(R.string.feed_title),
    QUEUE(R.string.tab_queue),
    SETTINGS(R.string.settings_title),
}

private val PrimaryNavigationDestinations = listOf(
    AppDestination.HOME,
    AppDestination.PODCASTS,
    AppDestination.FEED,
    AppDestination.QUEUE,
)

@Composable
internal fun ConnectedHome(
    destination: AppDestination,
    loginState: LoginUiState,
    queueState: QueueUiState,
    playbackState: PlaybackUiState,
    wide: Boolean,
    touchTarget: Dp,
    largeControls: Boolean,
    onRefresh: () -> Unit,
    onRefreshDashboard: () -> Unit,
    /** Pull to refresh on Home: always refreshes, unlike opening the screen (independent review of audit 4). */
    onPullDashboard: () -> Unit = onRefreshDashboard,
    onRefreshFeed: () -> Unit,
    feedEpisodeCount: Int,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit = {},
    onPlayEpisode: (Int) -> Unit,
    onMoveEpisode: (Int, Int) -> Unit,
    onRemoveEpisode: (Int) -> Unit,
    onDownloadEpisode: (QueueListEntry) -> Unit,
    onRemoveEpisodeDownload: (Int) -> Unit,
    onDownloadWholeEpisodeChanged: (Boolean) -> Unit,
    onAheadCacheChanged: (Int) -> Unit,
    onSeekBackSecondsChanged: (Int) -> Unit,
    onSeekForwardSecondsChanged: (Int) -> Unit,
    onPlaybackStartQueuePositionChanged: (QueueInsertionPosition) -> Unit,
    onManualQueuePositionChanged: (QueueInsertionPosition) -> Unit,
    onQueueSwipeStartActionChanged: (QueueSwipeAction) -> Unit,
    onQueueSwipeEndActionChanged: (QueueSwipeAction) -> Unit,
    onClearAheadCache: () -> Unit,
    themeMode: AppThemeMode,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    selectedPodcastId: Int?,
    podcastSortMode: PodcastSortMode,
    podcastViewMode: PodcastViewMode,
    collapsedPodcastDescriptions: Set<Int>,
    hidePlayedPodcastIds: Set<Int>,
    onPodcastSortModeChanged: (PodcastSortMode) -> Unit,
    onPodcastViewModeChanged: (PodcastViewMode) -> Unit,
    onPodcastDescriptionCollapsedChanged: (Int, Boolean) -> Unit,
    onPodcastHidePlayedChanged: (Int, Boolean) -> Unit,
    onRefreshPodcasts: () -> Unit,
    onOpenPodcast: (Int) -> Unit,
    onBackFromPodcast: () -> Unit,
    onPlayPodcastEpisodes: (List<QueueListEntry>, Int) -> Unit,
    onOpenPodcastEpisode: (List<QueueListEntry>, Int) -> Unit,
    onAddPodcastEpisodeToQueue: (QueueListEntry) -> Unit,
    onMarkPodcastEpisodesPlayed: (List<QueueListEntry>) -> Unit,
    onMarkPodcastEpisodesUnplayed: (List<QueueListEntry>) -> Unit,
    onAddPodcastEpisodesToQueueCommitted: (List<QueueListEntry>, (Boolean) -> Unit) -> Unit,
    onMarkPodcastEpisodesPlayedCommitted: (List<QueueListEntry>, (Boolean) -> Unit) -> Unit,
    onMarkPodcastEpisodesUnplayedCommitted: (List<QueueListEntry>, (Boolean) -> Unit) -> Unit,
    onUnsubscribePodcast: (Int) -> Unit,
    onChangeFeedUrl: (Int, String, (FeedUrlChangeResult) -> Unit) -> Unit = { _, _, _ -> },
    onCloseSettings: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onSignOut: () -> Unit,
    onTogglePlayback: () -> Unit,
    openPlaylistId: Int?,
    onOpenPlaylist: (Int?) -> Unit,
    hiddenPlaylistIds: Set<Int>,
    onLoadPlaylist: (Int) -> Unit,
    onLoadPodcast: (Int) -> Unit = {},
    listsState: EpisodeListsUiState = EpisodeListsUiState(),
    queueContent: @Composable () -> Unit = {},
) {
    val listActions = EpisodeListActions(
        onOpen = onOpenPodcastEpisode,
        onPlay = onPlayPodcastEpisodes,
        onTogglePlayback = onTogglePlayback,
        onAddToQueue = onAddPodcastEpisodeToQueue,
        onMarkPlayed = { entry -> onMarkPodcastEpisodesPlayed(listOf(entry)) },
        onDownload = onDownloadEpisode,
        onDeleteDownload = onRemoveEpisodeDownload,
    )
    when (destination) {
        // A smart playlist opened from the Dashboard (user, 27.10.2026): a list screen with Back.
        AppDestination.HOME -> if (openPlaylistId != null) {
            androidx.compose.runtime.LaunchedEffect(openPlaylistId) { onLoadPlaylist(openPlaylistId) }
            val loaded = listsState.playlistEpisodes[openPlaylistId]
            val summary = queueState.dashboard?.playlists?.firstOrNull { it.playlistId == openPlaylistId }
            val rulesText = loaded?.rules?.let { playlistRulesText(it) }
            EpisodeListScreen(
                title = loaded?.rules?.name?.takeIf(String::isNotBlank) ?: summary?.name.orEmpty(),
                subtitle = androidx.compose.ui.res.pluralStringResource(
                    R.plurals.dashboard_episode_count,
                    loaded?.total ?: summary?.episodeCount ?: 0,
                    loaded?.total ?: summary?.episodeCount ?: 0,
                ),
                description = listOfNotNull(
                    (loaded?.rules?.description ?: summary?.description)?.takeIf(String::isNotBlank),
                    rulesText?.takeIf(String::isNotBlank),
                ).joinToString("\n\n").ifBlank { null },
                glyph = { PlaylistGlyph(summary?.iconName.orEmpty(), LocalNookastColors.current.onAccent, androidx.compose.ui.Modifier.size(44.dp)) },
                entries = loaded?.episodes.orEmpty(),
                loading = openPlaylistId in listsState.playlistLoading,
                error = listsState.playlistErrors[openPlaylistId],
                currentEpisodeId = playbackState.currentEpisodeId,
                isPlaying = playbackState.isPlaying,
                downloadStates = queueState.downloadStates,
                downloadProgress = queueState.downloadProgress,
                wide = wide,
                largeControls = largeControls,
                actions = listActions,
                onBack = { onOpenPlaylist(null) },
                showArtwork = true,
                playlistId = openPlaylistId,
            )
        } else {
            // A screen of its own on every width; a wide screen only adapts its layout (user, 26.10.2026).
            // Pull to refresh with the screen's own refresh (design audit 4.8).
            ru.e1173.nookast.designsystem.NookPullToRefresh(refreshing = queueState.dashboardRefreshing, onRefresh = onPullDashboard, topInset = LocalTopInset.current) {
                DashboardScreen(
                    state = queueState,
                    wide = wide,
                    largeControls = largeControls,
                    touchTarget = touchTarget,
                    onRefresh = onRefreshDashboard,
                    onOpenSettings = onOpenSettings,
                    onOpenSearch = onOpenSearch,
                    onPlayEpisodes = onPlayPodcastEpisodes,
                    onMarkEpisodePlayed = { entry ->
                        onMarkPodcastEpisodesPlayed(listOf(entry))
                    },
                    onDownloadEpisode = onDownloadEpisode,
                    listActions = listActions,
                    currentEpisodeId = playbackState.currentEpisodeId,
                    isPlaying = playbackState.isPlaying,
                    hiddenPlaylistIds = hiddenPlaylistIds,
                    onOpenPlaylist = { onOpenPlaylist(it) },
                    onOpenPodcast = onOpenPodcast,
                )
            }
        }
        AppDestination.PODCASTS -> // Pull to refresh with the screen's own refresh (design audit 4.8).
        ru.e1173.nookast.designsystem.NookPullToRefresh(refreshing = queueState.podcastsRefreshing, onRefresh = onRefreshPodcasts, topInset = LocalTopInset.current) {
            PodcastScreen(
                state = queueState,
                currentEpisodeId = playbackState.currentEpisodeId,
                isPlaying = playbackState.isPlaying,
                onOpenSearch = onOpenSearch,
                selectedPodcastId = selectedPodcastId,
                sortMode = podcastSortMode,
                viewMode = podcastViewMode,
                collapsedDescriptions = collapsedPodcastDescriptions,
                hidePlayedPodcastIds = hidePlayedPodcastIds,
                wide = wide,
                largeControls = largeControls,
                touchTarget = touchTarget,
                onRefresh = onRefreshPodcasts,
                onSortModeChanged = onPodcastSortModeChanged,
                onViewModeChanged = onPodcastViewModeChanged,
                onDescriptionCollapsedChanged = onPodcastDescriptionCollapsedChanged,
                onHidePlayedChanged = onPodcastHidePlayedChanged,
                onOpenPodcast = onOpenPodcast,
                onBack = onBackFromPodcast,
                onPlayEpisodes = onPlayPodcastEpisodes,
                onOpenEpisode = onOpenPodcastEpisode,
                onDownloadEpisode = onDownloadEpisode,
                onRemoveEpisodeDownload = onRemoveEpisodeDownload,
                onAddToQueue = onAddPodcastEpisodeToQueue,
                onMarkEpisodesPlayed = onMarkPodcastEpisodesPlayed,
                onMarkEpisodesUnplayed = onMarkPodcastEpisodesUnplayed,
                onAddToQueueCommitted = onAddPodcastEpisodesToQueueCommitted,
                onMarkEpisodesPlayedCommitted = onMarkPodcastEpisodesPlayedCommitted,
                onMarkEpisodesUnplayedCommitted = onMarkPodcastEpisodesUnplayedCommitted,
                onUnsubscribe = onUnsubscribePodcast,
                onChangeFeedUrl = onChangeFeedUrl,
                onLoadPodcast = onLoadPodcast,
            )
        }

        AppDestination.FEED -> {
        androidx.compose.runtime.LaunchedEffect(Unit) { onRefreshFeed() }
        // Pull to refresh with the screen's own refresh (design audit 4.8).
        ru.e1173.nookast.designsystem.NookPullToRefresh(refreshing = listsState.feedRefreshing, onRefresh = onRefreshFeed, topInset = LocalTopInset.current) {
            EpisodeListScreen(
                title = stringResource(R.string.feed_title),
            onRetry = onRefreshFeed,
                subtitle = stringResource(R.string.feed_subtitle, listsState.feedEntries.take(feedEpisodeCount).size),
                description = null,
                glyph = { FeedGlyph(androidx.compose.ui.Modifier.size(44.dp), LocalNookastColors.current.onAccent) },
                entries = androidx.compose.runtime.remember(listsState.feedEntries, feedEpisodeCount) {
                    listsState.feedEntries.take(feedEpisodeCount)
                },
                loading = listsState.feedRefreshing,
                error = listsState.feedError,
                currentEpisodeId = playbackState.currentEpisodeId,
                isPlaying = playbackState.isPlaying,
                downloadStates = queueState.downloadStates,
                downloadProgress = queueState.downloadProgress,
                wide = wide,
                largeControls = largeControls,
                actions = listActions,
                playlistId = FEED_HIDE_PLAYED_KEY,
            )
        }
        }

        // Up Next as a tab (user, 1.11.2026: Profile had nothing to show).
        AppDestination.QUEUE -> queueContent()

        AppDestination.SETTINGS -> SettingsScreen(
            serverUrl = loginState.serverUrl.orEmpty(),
            queueState = queueState,
            playbackState = playbackState,
            wide = wide,
            largeControls = largeControls,
            touchTarget = touchTarget,
            themeMode = themeMode,
            onClose = onCloseSettings,
            onRefresh = onRefresh,
            onRunDiagnostics = onRunDiagnostics,
            onSignOut = onSignOut,
            onThemeModeChanged = onThemeModeChanged,
            onDownloadWholeEpisodeChanged = onDownloadWholeEpisodeChanged,
            onAheadCacheChanged = onAheadCacheChanged,
            onSeekBackSecondsChanged = onSeekBackSecondsChanged,
            onSeekForwardSecondsChanged = onSeekForwardSecondsChanged,
            onPlaybackStartQueuePositionChanged = onPlaybackStartQueuePositionChanged,
            onManualQueuePositionChanged = onManualQueuePositionChanged,
            onQueueSwipeStartActionChanged = onQueueSwipeStartActionChanged,
            onQueueSwipeEndActionChanged = onQueueSwipeEndActionChanged,
            onClearAheadCache = onClearAheadCache,
        )
    }
}

@Composable
internal fun AppNavigationBar(
    selected: AppDestination,
    onSelected: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Frosted bar (the caller adds the glass): transparent surface and a hairline on top.
    val hairline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    Surface(
        modifier = modifier.drawBehind { drawLine(hairline, androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Offset(size.width, 0f), 1.dp.toPx()) },
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        // The selected tab's capsule flows between tabs (design audit 4.7); a soft glow at night.
        val colors = ru.e1173.nookast.designsystem.LocalNookastColors.current
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(57.dp)) {
            val count = PrimaryNavigationDestinations.size
            val tabWidth = maxWidth / count
            val index = PrimaryNavigationDestinations.indexOf(selected).coerceAtLeast(0)
            val capsuleX by androidx.compose.animation.core.animateDpAsState(
                tabWidth * index + (tabWidth - NAV_CAPSULE_WIDTH) / 2,
                ru.e1173.nookast.designsystem.NookMotion.spatial(),
                label = "navCapsule",
            )
            if (PrimaryNavigationDestinations.contains(selected)) {
                Box(
                    Modifier
                        .offset { androidx.compose.ui.unit.IntOffset(capsuleX.roundToPx(), NAV_CAPSULE_TOP.roundToPx()) }
                        .size(NAV_CAPSULE_WIDTH, NAV_CAPSULE_HEIGHT)
                        .drawBehind {
                            if (colors.isDark) {
                                drawCssShadow(colors.accent.copy(alpha = NAV_GLOW_ALPHA), NAV_GLOW_BLUR, size.height / 2f)
                            }
                        }
                        .background(colors.tintContainer, ru.e1173.nookast.designsystem.NookShapes.full),
                )
            }
        }
        Row(Modifier.fillMaxWidth().height(57.dp)) {
            PrimaryNavigationDestinations.forEach { destination ->
                val isSelected = destination == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .focusRing(ru.e1173.nookast.designsystem.NookShapes.m)
                        .clip(ru.e1173.nookast.designsystem.NookShapes.m)
                        // A tab for TalkBack, with its selected state (plan 5.5).
                        .selectable(selected = isSelected, role = androidx.compose.ui.semantics.Role.Tab) { onSelected(destination) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Outline to filled over Fast (design audit 4.7).
                    androidx.compose.animation.Crossfade(isSelected, animationSpec = ru.e1173.nookast.designsystem.NookMotion.fast(), label = "navIcon") { shown ->
                        AppNavigationGlyph(
                            destination = destination,
                            selected = shown,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(destination.labelResource),
                        // 11 sp / 600 (design audit 1.4: 9 sp was too small); the bar keeps its 57 dp.
                        style = ru.e1173.nookast.designsystem.NookType.navLabel,
                        color = if (isSelected) ru.e1173.nookast.designsystem.LocalNookastColors.current.accentText
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppNavigationGlyph(
    destination: AppDestination,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) ru.e1173.nookast.designsystem.LocalNookastColors.current.accentText
    else MaterialTheme.colorScheme.onSurfaceVariant
    // Outline when idle, filled when selected (audit 3.7; the change animates in 4.7).
    val icon = when (destination) {
        AppDestination.HOME -> if (selected) NookIcons.HomeFilled else NookIcons.Home
        AppDestination.PODCASTS -> if (selected) NookIcons.LibraryFilled else NookIcons.Library
        AppDestination.FEED -> if (selected) NookIcons.FeedFilled else NookIcons.Feed
        AppDestination.QUEUE -> if (selected) NookIcons.UpNextFilled else NookIcons.UpNextOutline
        AppDestination.SETTINGS -> if (selected) NookIcons.SettingsFilled else NookIcons.Settings
    }
    androidx.compose.material3.Icon(icon, contentDescription = null, tint = color, modifier = modifier)
}

internal fun PlaybackError.stringResource(): Int = when (this) {
    PlaybackError.CONTROLLER -> R.string.player_error_controller
    PlaybackError.INVALID_MEDIA -> R.string.player_error_invalid_media
    PlaybackError.PLAYBACK -> R.string.player_error_playback
    PlaybackError.SOURCE_MISSING -> R.string.player_error_missing
    PlaybackError.SOURCE_REFUSED -> R.string.player_error_refused
    PlaybackError.UNSUPPORTED -> R.string.player_error_unsupported
}

private val NAV_CAPSULE_WIDTH = 56.dp
private val NAV_CAPSULE_HEIGHT = 28.dp
private val NAV_CAPSULE_TOP = 6.5.dp
private const val NAV_GLOW_ALPHA = 0.25f
private val NAV_GLOW_BLUR = 12.dp
