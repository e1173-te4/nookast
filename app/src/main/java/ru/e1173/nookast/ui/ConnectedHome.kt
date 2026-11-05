package ru.e1173.nookast.ui

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
import ru.e1173.nookast.player.MediaButtonProfileMode
import ru.e1173.nookast.player.QueueInsertionPosition
import ru.e1173.nookast.player.QueueSwipeAction
import ru.e1173.nookast.offline.EpisodeDownloadState
import ru.e1173.nookast.preferences.AppThemeMode
import ru.e1173.nookast.preferences.InterfaceProfile
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
    onMediaButtonProfileChanged: (MediaButtonProfileMode) -> Unit,
    onPlaybackStartQueuePositionChanged: (QueueInsertionPosition) -> Unit,
    onManualQueuePositionChanged: (QueueInsertionPosition) -> Unit,
    onQueueSwipeStartActionChanged: (QueueSwipeAction) -> Unit,
    onQueueSwipeEndActionChanged: (QueueSwipeAction) -> Unit,
    onClearAheadCache: () -> Unit,
    themeMode: AppThemeMode,
    interfaceProfile: InterfaceProfile,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onInterfaceProfileChanged: (InterfaceProfile) -> Unit,
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
        AppDestination.PODCASTS -> PodcastScreen(
            state = queueState,
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

        AppDestination.FEED -> {
        androidx.compose.runtime.LaunchedEffect(Unit) { onRefreshFeed() }
        EpisodeListScreen(
            title = stringResource(R.string.feed_title),
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
            interfaceProfile = interfaceProfile,
            onClose = onCloseSettings,
            onRefresh = onRefresh,
            onRunDiagnostics = onRunDiagnostics,
            onSignOut = onSignOut,
            onThemeModeChanged = onThemeModeChanged,
            onInterfaceProfileChanged = onInterfaceProfileChanged,
            onDownloadWholeEpisodeChanged = onDownloadWholeEpisodeChanged,
            onAheadCacheChanged = onAheadCacheChanged,
            onSeekBackSecondsChanged = onSeekBackSecondsChanged,
            onSeekForwardSecondsChanged = onSeekForwardSecondsChanged,
            onMediaButtonProfileChanged = onMediaButtonProfileChanged,
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
        Row(Modifier.fillMaxWidth().height(57.dp)) {
            PrimaryNavigationDestinations.forEach { destination ->
                val isSelected = destination == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .focusRing(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                        // A tab for TalkBack, with its selected state (plan 5.5).
                        .selectable(selected = isSelected, role = androidx.compose.ui.semantics.Role.Tab) { onSelected(destination) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    AppNavigationGlyph(
                        destination = destination,
                        selected = isSelected,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(destination.labelResource),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            lineHeight = 10.sp,
                        ),
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
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
    val color = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val stroke = if (selected) 2.4.dp.toPx() else 2.dp.toPx()
        when (destination) {
            AppDestination.HOME -> {
                val roof = Path().apply {
                    moveTo(size.width * 0.16f, size.height * 0.48f)
                    lineTo(size.width * 0.50f, size.height * 0.18f)
                    lineTo(size.width * 0.84f, size.height * 0.48f)
                }
                drawPath(roof, color, style = Stroke(stroke, cap = StrokeCap.Round))
                val body = Path().apply {
                    moveTo(size.width * 0.24f, size.height * 0.43f)
                    lineTo(size.width * 0.24f, size.height * 0.82f)
                    lineTo(size.width * 0.76f, size.height * 0.82f)
                    lineTo(size.width * 0.76f, size.height * 0.43f)
                }
                drawPath(body, color, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            AppDestination.PODCASTS -> {
                val cell = size.minDimension * 0.25f
                val gap = size.minDimension * 0.12f
                val start = (size.width - (cell * 2f + gap)) / 2f
                repeat(2) { row ->
                    repeat(2) { column ->
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(start + column * (cell + gap), start + row * (cell + gap)),
                            size = androidx.compose.ui.geometry.Size(cell, cell),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cell * 0.26f),
                            style = if (selected) androidx.compose.ui.graphics.drawscope.Fill
                            else Stroke(stroke),
                        )
                    }
                }
            }
            AppDestination.FEED -> {
                // Feed: the RSS mark — a dot and two arcs from the lower left corner.
                val origin = Offset(size.width * 0.22f, size.height * 0.78f)
                drawCircle(color, radius = size.minDimension * 0.08f, center = origin)
                listOf(0.32f, 0.56f).forEach { radius ->
                    val r = size.minDimension * radius
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(origin.x - r, origin.y - r),
                        size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            AppDestination.QUEUE -> {
                // Up Next: three lines of a list, the last shorter, and a play triangle beside it.
                val left = size.width * 0.16f
                listOf(0.26f to 0.84f, 0.50f to 0.84f, 0.74f to 0.52f).forEach { (y, end) ->
                    drawLine(color, Offset(left, size.height * y), Offset(size.width * end, size.height * y), strokeWidth = stroke, cap = StrokeCap.Round)
                }
                val play = Path().apply {
                    moveTo(size.width * 0.66f, size.height * 0.62f)
                    lineTo(size.width * 0.88f, size.height * 0.74f)
                    lineTo(size.width * 0.66f, size.height * 0.86f)
                    close()
                }
                drawPath(play, color)
            }
            AppDestination.SETTINGS -> {
                drawCircle(color, radius = size.minDimension * 0.19f, style = Stroke(stroke))
                drawCircle(color, radius = size.minDimension * 0.36f, style = Stroke(stroke))
                repeat(4) { index ->
                    val horizontal = index % 2 == 0
                    val positive = index < 2
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val inner = size.minDimension * 0.36f
                    val outer = size.minDimension * 0.46f
                    val direction = if (positive) 1f else -1f
                    drawLine(
                        color = color,
                        start = if (horizontal) center + Offset(direction * inner, 0f)
                        else center + Offset(0f, direction * inner),
                        end = if (horizontal) center + Offset(direction * outer, 0f)
                        else center + Offset(0f, direction * outer),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackSettingsCard(
    downloadWholeEpisode: Boolean,
    aheadCacheMegabytes: Int,
    seekBackSeconds: Int,
    seekForwardSeconds: Int,
    aheadCacheBytes: Long,
    offlineDownloadBytes: Long,
    aheadCacheClearPending: Boolean,
    mediaButtonProfileMode: MediaButtonProfileMode,
    onDownloadWholeEpisodeChanged: (Boolean) -> Unit,
    onAheadCacheChanged: (Int) -> Unit,
    onSeekBackSecondsChanged: (Int) -> Unit,
    onSeekForwardSecondsChanged: (Int) -> Unit,
    onMediaButtonProfileChanged: (MediaButtonProfileMode) -> Unit,
    onClearAheadCache: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.offline_and_cache),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.download_on_playback))
                    Text(
                        stringResource(R.string.download_on_playback_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = downloadWholeEpisode,
                    onCheckedChange = onDownloadWholeEpisodeChanged,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.ahead_cache_size, aheadCacheMegabytes))
            Slider(
                value = aheadCacheMegabytes.toFloat(),
                onValueChange = { value ->
                    onAheadCacheChanged((value / CACHE_STEP_MB).toInt() * CACHE_STEP_MB)
                },
                valueRange = 8f..256f,
                steps = 30,
            )
            Text(
                stringResource(R.string.cache_restart_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(
                    R.string.storage_usage,
                    formatStorageBytes(aheadCacheBytes),
                    formatStorageBytes(offlineDownloadBytes),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            OutlinedButton(
                onClick = onClearAheadCache,
                enabled = !aheadCacheClearPending,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(
                    stringResource(
                        if (aheadCacheClearPending) {
                            R.string.cache_clear_pending
                        } else {
                            R.string.clear_ahead_cache
                        },
                    ),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.seek_back_setting, seekBackSeconds))
            Slider(
                value = seekBackSeconds.toFloat(),
                onValueChange = { onSeekBackSecondsChanged(it.roundToInt()) },
                valueRange = 5f..55f,
                steps = 49,
            )
            Text(stringResource(R.string.seek_forward_setting, seekForwardSeconds))
            Slider(
                value = seekForwardSeconds.toFloat(),
                onValueChange = { onSeekForwardSecondsChanged(it.roundToInt()) },
                valueRange = 5f..55f,
                steps = 49,
            )
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.media_button_profile))
            MediaButtonProfileDropdown(
                selected = mediaButtonProfileMode,
                onSelected = onMediaButtonProfileChanged,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaButtonProfileDropdown(
    selected: MediaButtonProfileMode,
    onSelected: (MediaButtonProfileMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        androidx.compose.material3.OutlinedTextField(
            value = stringResource(selected.labelResource()),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            MediaButtonProfileMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(stringResource(mode.labelResource())) },
                    onClick = {
                        onSelected(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun MediaButtonProfileMode.labelResource(): Int = when (this) {
    MediaButtonProfileMode.AUTO -> R.string.profile_auto
    MediaButtonProfileMode.STANDARD -> R.string.profile_standard
    MediaButtonProfileMode.JAECOO_J7 -> R.string.profile_jaecoo
}

private fun formatStorageBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format(
        Locale.getDefault(),
        "%.1f GB",
        bytes / (1024.0 * 1024.0 * 1024.0),
    )
    bytes >= 1024L * 1024L -> String.format(
        Locale.getDefault(),
        "%.1f MB",
        bytes / (1024.0 * 1024.0),
    )
    bytes >= 1024L -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
private fun DisplaySettingsCard(
    themeMode: AppThemeMode,
    interfaceProfile: InterfaceProfile,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onInterfaceProfileChanged: (InterfaceProfile) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.appearance_and_layout),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.appearance_temporary_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            ThemeModeDropdown(
                selected = themeMode,
                onSelected = onThemeModeChanged,
                modifier = Modifier.fillMaxWidth(),
            )
            InterfaceProfileDropdown(
                selected = interfaceProfile,
                onSelected = onInterfaceProfileChanged,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeDropdown(
    selected: AppThemeMode,
    onSelected: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        androidx.compose.material3.OutlinedTextField(
            value = stringResource(selected.labelResource()),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.theme_mode)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            AppThemeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(stringResource(mode.labelResource())) },
                    onClick = {
                        onSelected(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InterfaceProfileDropdown(
    selected: InterfaceProfile,
    onSelected: (InterfaceProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        androidx.compose.material3.OutlinedTextField(
            value = stringResource(selected.labelResource()),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.interface_profile)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            InterfaceProfile.entries.forEach { profile ->
                DropdownMenuItem(
                    text = { Text(stringResource(profile.labelResource())) },
                    onClick = {
                        onSelected(profile)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun AppThemeMode.labelResource(): Int = when (this) {
    AppThemeMode.SYSTEM -> R.string.theme_system
    AppThemeMode.LIGHT -> R.string.theme_light
    AppThemeMode.DARK -> R.string.theme_dark
}

private fun InterfaceProfile.labelResource(): Int = when (this) {
    InterfaceProfile.AUTO -> R.string.interface_auto
    InterfaceProfile.STANDARD -> R.string.interface_standard
    InterfaceProfile.LARGE -> R.string.interface_large
    InterfaceProfile.JAECOO_J7 -> R.string.interface_jaecoo
}

@Composable
private fun PlayerCard(
    state: PlaybackUiState,
    touchTarget: Dp,
    onTogglePlayback: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onRunDiagnostics: () -> Unit,
) {
    val state = state.live()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            if (state.streamReachability == StreamReachability.UNAVAILABLE) {
                StreamAvailabilityBanner(state, onRunDiagnostics)
                Spacer(Modifier.height(16.dp))
            }
            if (state.isUsingPinePodsFallback) {
                Text(
                    stringResource(R.string.pinepods_fallback_active),
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                state.title.ifBlank { stringResource(R.string.player_title) },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                state.podcastName,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.durationMs > 0) {
                Slider(
                    value = state.positionMs.coerceAtMost(state.durationMs).toFloat(),
                    onValueChange = { onSeekTo(it.toLong()) },
                    valueRange = 0f..state.durationMs.toFloat(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(formatPlaybackTime(state.positionMs))
                    Text(formatPlaybackTime(state.durationMs))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onSeekBack,
                    modifier = Modifier.weight(1f).heightIn(min = touchTarget),
                ) { Text(stringResource(R.string.seek_back)) }
                Button(
                    onClick = onTogglePlayback,
                    enabled = state.isConnected && !state.isBuffering,
                    modifier = Modifier.weight(1f).heightIn(min = touchTarget),
                ) {
                    Text(
                        stringResource(
                            if (state.isPlaying) R.string.pause else R.string.play,
                        ),
                    )
                }
                OutlinedButton(
                    onClick = onSeekForward,
                    modifier = Modifier.weight(1f).heightIn(min = touchTarget),
                ) { Text(stringResource(R.string.seek_forward)) }
            }
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(it.stringResource()),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun StreamAvailabilityBanner(
    state: PlaybackUiState,
    onRunDiagnostics: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.offline_stream_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(stringResource(R.string.offline_stream_host, state.streamHost))
            Text(
                if (state.bufferedAheadMs > 0) {
                    stringResource(
                        R.string.offline_buffer_remaining,
                        formatPlaybackTime(state.bufferedAheadMs),
                    )
                } else {
                    stringResource(R.string.offline_buffer_empty)
                },
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onRunDiagnostics,
                enabled = !state.diagnosticsRunning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (state.diagnosticsRunning) {
                            R.string.diagnostics_running
                        } else {
                            R.string.extended_diagnostics
                        },
                    ),
                )
            }
            state.diagnosticResults.forEach { result ->
                val host = result.requestedHost.ifBlank { result.resolvedHost }
                Text(
                    if (result.isAvailable) {
                        stringResource(
                            R.string.diagnostic_available,
                            host,
                            result.latencyMs,
                        )
                    } else {
                        stringResource(R.string.diagnostic_unavailable, host)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    serverUrl: String,
    queueState: QueueUiState,
    touchTarget: Dp,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.connected_home_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                serverUrl,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            val pending = queueState.pendingProgressCount + queueState.pendingQueueCount +
                queueState.pendingPodcastCount + queueState.pendingSavedEpisodeCount
            Text(
                if (pending == 0) {
                    stringResource(R.string.sync_no_pending_changes)
                } else {
                    pluralStringResource(R.plurals.sync_pending_changes, pending, pending)
                },
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onRefresh,
                    enabled = !queueState.isRefreshing,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = touchTarget),
                ) {
                    if (queueState.isRefreshing) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.refresh))
                    }
                }
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = touchTarget),
                ) {
                    Text(stringResource(R.string.disconnect))
                }
            }
        }
    }
}

@Composable
private fun QueueCard(
    state: QueueUiState,
    touchTarget: Dp,
    largeControls: Boolean,
    onRefresh: () -> Unit,
    onPlayEpisode: (Int) -> Unit,
    onMoveEpisode: (Int, Int) -> Unit,
    onRemoveEpisode: (Int) -> Unit,
    onDownloadEpisode: (QueueListEntry) -> Unit,
    currentEpisodeId: Int?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.queue_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        pluralStringResource(
                            R.plurals.queue_episode_count,
                            state.entries.size,
                            state.entries.size,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (state.lastUpdatedEpochMs != null) {
                        formatUpdatedAt(state.lastUpdatedEpochMs)
                    } else {
                        stringResource(R.string.not_synced_yet)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.error?.let { error ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(error.stringResource()),
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.heightIn(min = touchTarget),
                ) {
                    Text(stringResource(R.string.try_again))
                }
            }

            if (state.entries.isEmpty() && !state.isRefreshing) {
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.queue_empty),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(R.string.queue_empty_help),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(12.dp))
                state.entries.take(MAX_VISIBLE_QUEUE_ITEMS).forEachIndexed { index, entry ->
                    QueueRow(
                        entry = entry,
                        isCurrent = entry.episodeId == currentEpisodeId,
                        largeControls = largeControls,
                        canMoveUp = index > 0,
                        canMoveDown = index < state.entries.lastIndex,
                        onPlay = { onPlayEpisode(index) },
                        onMoveUp = { onMoveEpisode(entry.episodeId, -1) },
                        onMoveDown = { onMoveEpisode(entry.episodeId, 1) },
                        onRemove = { onRemoveEpisode(entry.episodeId) },
                        downloadState = state.downloadStates[entry.episodeId]
                            ?: EpisodeDownloadState.NOT_DOWNLOADED,
                        onDownload = { onDownloadEpisode(entry) },
                    )
                    if (index < minOf(state.entries.lastIndex, MAX_VISIBLE_QUEUE_ITEMS - 1)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                val hidden = state.entries.size - MAX_VISIBLE_QUEUE_ITEMS
                if (hidden > 0) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        pluralStringResource(R.plurals.queue_more_items, hidden, hidden),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    entry: QueueListEntry,
    isCurrent: Boolean,
    largeControls: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPlay: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    downloadState: EpisodeDownloadState,
    onDownload: () -> Unit,
) {
    Surface(
        color = if (isCurrent) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0f)
        },
        shape = RoundedCornerShape(16.dp),
    ) {
      Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(
                horizontal = 10.dp,
                vertical = if (largeControls) 16.dp else 12.dp,
            ),
      ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.episodeTitle?.takeIf(String::isNotBlank)?.let(::cleanEpisodeTitle)
                        ?: stringResource(R.string.unknown_episode, entry.episodeId),
                    style = if (largeControls) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entry.podcastName.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isCurrent) {
                    Text(
                        stringResource(R.string.current_episode),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            entry.durationSeconds?.let { duration ->
                Text(
                    formatDuration(duration),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.move_up)) }
            OutlinedButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.move_down)) }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(
                onClick = onDownload,
                enabled = downloadState == EpisodeDownloadState.NOT_DOWNLOADED ||
                    downloadState == EpisodeDownloadState.FAILED,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(downloadState.labelResource())) }
            OutlinedButton(
                onClick = onRemove,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.remove_from_queue)) }
        }
      }
    }
}

private fun formatPlaybackTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0) / 1_000).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

internal fun PlaybackError.stringResource(): Int = when (this) {
    PlaybackError.CONTROLLER -> R.string.player_error_controller
    PlaybackError.INVALID_MEDIA -> R.string.player_error_invalid_media
    PlaybackError.PLAYBACK -> R.string.player_error_playback
    PlaybackError.SOURCE_MISSING -> R.string.player_error_missing
    PlaybackError.SOURCE_REFUSED -> R.string.player_error_refused
    PlaybackError.UNSUPPORTED -> R.string.player_error_unsupported
}

private fun EpisodeDownloadState.labelResource(): Int = when (this) {
    EpisodeDownloadState.NOT_DOWNLOADED -> R.string.download
    EpisodeDownloadState.QUEUED -> R.string.download_queued
    EpisodeDownloadState.DOWNLOADING -> R.string.downloading
    EpisodeDownloadState.DOWNLOADED -> R.string.downloaded
    EpisodeDownloadState.FAILED -> R.string.download_retry
}

private const val CACHE_STEP_MB = 8

@Composable
private fun formatUpdatedAt(epochMs: Long): String {
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    return stringResource(R.string.updated_at, formatter.format(Date(epochMs)))
}

@Composable
private fun formatDuration(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val hours = safeSeconds / 3600
    val minutes = (safeSeconds % 3600) / 60
    return if (hours > 0) {
        stringResource(R.string.duration_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.duration_minutes, minutes)
    }
}

private fun QueueLoadError.stringResource(): Int = when (this) {
    QueueLoadError.AUTHENTICATION -> R.string.queue_error_authentication
    QueueLoadError.NETWORK -> R.string.queue_error_network
    QueueLoadError.SERVER -> R.string.queue_error_server
    QueueLoadError.INVALID_RESPONSE -> R.string.queue_error_invalid_response
}

private const val MAX_VISIBLE_QUEUE_ITEMS = 20
