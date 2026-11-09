package ru.e1173.nookast.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.remember
import ru.e1173.nookast.designsystem.snowDusted
import ru.e1173.nookast.designsystem.LocalNookastColors
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.material3.HorizontalDivider
import ru.e1173.nookast.offline.EpisodeDownloadState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import ru.e1173.nookast.R
import ru.e1173.nookast.data.local.DashboardContinueLocalEntry
import ru.e1173.nookast.data.local.QueueListEntry
import ru.e1173.nookast.data.remote.DashboardAnalytics
import ru.e1173.nookast.data.remote.DashboardDay
import ru.e1173.nookast.data.remote.DashboardEpisode
import ru.e1173.nookast.data.remote.DashboardPlaylist
import ru.e1173.nookast.offline.LocalDownloadedEpisode

private val DashboardSectionShape = RoundedCornerShape(18.dp)
private val DashboardTileShape = RoundedCornerShape(14.dp)

private enum class DashboardProfile { PHONE, FOLD, JAECOO }

private data class DashboardDimensions(
    val sectionGap: Dp,
    val tileGap: Dp,
    val smartTileHeight: Dp,
    val mainRowHeight: Dp,
    val bottomRowHeight: Dp,
    val artworkSize: Dp,
    val collapsedContinueRows: Int,
)

private fun dashboardDimensions(profile: DashboardProfile) = when (profile) {
    DashboardProfile.PHONE -> DashboardDimensions(
        12.dp, 9.dp, 76.dp, 0.dp, 0.dp, 48.dp, 3,
    )
    DashboardProfile.FOLD -> DashboardDimensions(
        12.dp, 10.dp, 72.dp, 350.dp, 196.dp, 54.dp, 4,
    )
    DashboardProfile.JAECOO -> DashboardDimensions(
        18.dp, 14.dp, 104.dp, 520.dp, 340.dp, 76.dp, 4,
    )
}

internal data class DashboardContinueItem(
    val entry: QueueListEntry,
    val lastListenedAtEpochMs: Long,
    val progressFraction: Float,
)

/** Keeps only locally timestamped, started, incomplete episodes; never sorts by progress. */
internal fun selectDashboardContinueEpisodes(
    source: List<DashboardContinueLocalEntry>,
): List<DashboardContinueItem> = source
    .asSequence()
    .filter { it.episodeId > 0 && it.positionMs > 0L && !it.progressCompleted }
    .filter { item ->
        val durationMs = item.durationMs
            ?: item.durationSeconds.takeIf { it > 0 }?.times(1_000L)
        durationMs == null || item.positionMs < durationMs
    }
    .sortedByDescending(DashboardContinueLocalEntry::lastListenedAtEpochMs)
    .distinctBy(DashboardContinueLocalEntry::episodeId)
    .take(13)
    .mapIndexed { index, item ->
        val durationMs = item.durationMs
            ?: item.durationSeconds.takeIf { it > 0 }?.times(1_000L)
        DashboardContinueItem(
            entry = QueueListEntry(
                episodeId = item.episodeId,
                queuePosition = index,
                episodeTitle = item.episodeTitle,
                podcastName = item.podcastName,
                artworkUrl = item.artworkUrl,
                mediaUrl = item.mediaUrl,
                durationSeconds = durationMs?.div(1_000L)?.toInt() ?: item.durationSeconds,
                listenDurationSeconds = item.positionMs / 1_000.0,
                completed = false,
                description = item.description,
                publishedAt = item.publishedAt,
                downloaded = item.downloaded,
                refreshedAtEpochMs = item.refreshedAtEpochMs,
            ),
            lastListenedAtEpochMs = item.lastListenedAtEpochMs,
            progressFraction = if (durationMs != null && durationMs > 0L) {
                (item.positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
            } else {
                0f
            },
        )
    }
    .toList()

@Composable
internal fun DashboardScreen(
    state: QueueUiState,
    wide: Boolean,
    largeControls: Boolean,
    touchTarget: Dp,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit = {},
    onPlayEpisodes: (List<QueueListEntry>, Int) -> Unit,
    onMarkEpisodePlayed: (QueueListEntry) -> Unit,
    onDownloadEpisode: (QueueListEntry) -> Unit,
    listActions: EpisodeListActions,
    currentEpisodeId: Int?,
    isPlaying: Boolean,
    hiddenPlaylistIds: Set<Int>,
    onOpenPlaylist: (Int) -> Unit,
    onOpenPodcast: (Int) -> Unit = {},
) {
    val dashboard = state.dashboard
    val continueItems = rememberStableOrder(
        selectDashboardContinueEpisodes(state.dashboardContinueEntries),
        key = { it.entry.episodeId },
    )
    val continueQueue = continueItems.map(DashboardContinueItem::entry)
    val savedQueue = dashboard?.savedEpisodes.orEmpty().mapIndexed { index, episode -> episode.toQueueEntry(index) }
    val downloadQueue = state.localDownloads.mapIndexed { index, episode -> episode.toQueueEntry(index) }
    val playlists = dashboard?.playlists.orEmpty().filterNot { it.playlistId in hiddenPlaylistIds }
    var continueExpanded by rememberSaveable { mutableStateOf(false) }
    val loading = state.dashboardRefreshing && dashboard == null

    val header: @Composable () -> Unit = {
        DashboardHeader(
            refreshing = state.dashboardRefreshing,
            pending = state.pendingProgressCount + state.pendingQueueCount +
                state.pendingPodcastCount + state.pendingSavedEpisodeCount > 0,
            failed = state.dashboardError != null,
            touchTarget = touchTarget,
            large = largeControls,
            onRefresh = onRefresh,
            onOpenSettings = onOpenSettings,
            onOpenSearch = onOpenSearch,
        )
    }
    val playlistsSection: @Composable () -> Unit = {
        // Tiles keep at least ~190 dp so names stay readable; a wide column gets more of them.
        BoxWithConstraints {
            val columns = (maxWidth / (if (largeControls) 230.dp else 190.dp)).toInt().coerceIn(2, 4)
            DashboardSmartPlaylists(playlists, loading, columns, largeControls, onOpenPlaylist)
        }
    }
    val analyticsSection: @Composable () -> Unit = {
        DashboardWeekCard(
            analytics = dashboard?.analytics ?: DashboardAnalytics(),
            loading = loading,
            large = largeControls,
            onOpenPodcast = onOpenPodcast,
            onOpenPodcastNamed = { name ->
                state.podcasts.firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }
                    ?.podcastId?.let(onOpenPodcast)
            },
        )
    }
    val listsSection: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            DashboardEpisodeSection(
                title = stringResource(R.string.dashboard_continue_listening),
                emptyText = R.string.dashboard_continue_empty,
                entries = if (continueExpanded) continueQueue else continueQueue.take(3),
                allEntries = continueQueue,
                action = if (continueQueue.size > 3) {
                    stringResource(if (continueExpanded) R.string.dashboard_collapse else R.string.dashboard_all)
                } else {
                    null
                },
                onAction = { continueExpanded = !continueExpanded },
                actionExpanded = continueExpanded,
                state = state,
                currentEpisodeId = currentEpisodeId,
                isPlaying = isPlaying,
                largeControls = largeControls,
                actions = listActions,
            )
            DashboardEpisodeSection(
                title = stringResource(R.string.dashboard_liked),
                emptyText = R.string.dashboard_liked_empty,
                entries = savedQueue.take(3),
                allEntries = savedQueue,
                state = state,
                currentEpisodeId = currentEpisodeId,
                isPlaying = isPlaying,
                largeControls = largeControls,
                actions = listActions,
            )
            DashboardEpisodeSection(
                title = stringResource(R.string.dashboard_downloaded),
                emptyText = R.string.dashboard_downloaded_empty,
                entries = downloadQueue.take(3),
                allEntries = downloadQueue,
                state = state,
                currentEpisodeId = currentEpisodeId,
                isPlaying = isPlaying,
                largeControls = largeControls,
                actions = listActions,
                downloadedAll = true,
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        header()
        // The sections wait, invisible, until the cache, the continue list and the downloads are read,
        // then fade in at their final size (user, 29.10.2026: the screen jumped as they arrived).
        val shown by androidx.compose.animation.core.animateFloatAsState(
            if (state.dashboardLocalReady) 1f else 0f,
            androidx.compose.animation.core.tween(180),
            label = "dashboardShown",
        )
        Column(
            Modifier.graphicsLayer { alpha = shown },
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
        state.dashboardError?.takeIf { dashboard == null }?.let {
            DashboardMessage(stringResource(R.string.dashboard_error_no_cache))
        }
        if (wide) {
            // A wide screen adapts the same screen: two columns (user, 26.10.2026).
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    playlistsSection()
                    analyticsSection()
                }
                Column(Modifier.weight(1f)) { listsSection() }
            }
        } else {
            playlistsSection()
            analyticsSection()
            listsSection()
        }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Keeps the order of [items] while the screen is shown (APK-3.6 review: «Continue listening» jumped
 * whenever a position was saved and re-sorted the list). New items join on top, gone ones leave.
 */
@Composable
private fun <T> rememberStableOrder(items: List<T>, key: (T) -> Any): List<T> {
    var order by remember { mutableStateOf(items.map(key)) }
    val byKey = items.associateBy(key)
    val kept = order.filter { it in byKey }
    val added = items.map(key).filterNot { it in kept }
    val next = added + kept
    if (next != order) order = next
    return next.mapNotNull { byKey[it] }
}

@Composable
private fun DashboardHeader(
    refreshing: Boolean,
    pending: Boolean,
    failed: Boolean,
    touchTarget: Dp,
    large: Boolean,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit = {},
) {
    val colors = LocalNookastColors.current
    val target = if (touchTarget < 48.dp) 48.dp else touchTarget
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = if (large) 12.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The sync status takes the tagline's own line, cross-fading with it (user, 31.10.2026: a
        // status capsule beside the title squeezed it and made the header jump). The antenna
        // button did nothing visible (APK-3.6 review); a tap on the status retries the sync.
        val status = when {
            refreshing -> R.string.dashboard_status_refreshing
            failed -> R.string.dashboard_status_offline
            pending -> R.string.dashboard_status_pending
            else -> null
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = if (large) 40.sp else 32.sp, lineHeight = if (large) 46.sp else 38.sp),
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            androidx.compose.animation.Crossfade(status, label = "dashboardStatus") { shown ->
                if (shown == null) {
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = !refreshing, role = Role.Button, onClick = onRefresh),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (shown == R.string.dashboard_status_refreshing) {
                            CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp, color = colors.textSecondary)
                        } else {
                            Box(Modifier.size(7.dp).background(if (failed) Color(0xFFED6575) else Color(0xFFE0A33A), CircleShape))
                        }
                        Text(
                            stringResource(shown),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        // Search and add (user, 1.11.2026): a round glass button beside Settings, as iOS puts the
        // toolbar actions of a large title together on its trailing side.
        val searchLabel = stringResource(R.string.dashboard_search)
        Box(
            Modifier
                .size(target)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onOpenSearch)
                .semantics { contentDescription = searchLabel },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(if (large) 52.dp else 42.dp).floatingDisc().background(colors.surface.compositeOver(colors.background), CircleShape)
                    .border(1.dp, colors.divider, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ru.e1173.nookast.designsystem.NookIcons.Search, null, tint = colors.textPrimary, modifier = Modifier.size(if (large) 26.dp else 20.dp))
            }
        }
        val settingsLabel = stringResource(R.string.settings_title)
        Box(
            Modifier
                .size(target)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onOpenSettings)
                .semantics { contentDescription = settingsLabel },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(if (large) 52.dp else 42.dp).floatingDisc().background(colors.surface.compositeOver(colors.background), CircleShape)
                    .border(1.dp, colors.divider, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                DashboardSettingsGlyph(Modifier.size(if (large) 28.dp else 22.dp))
            }
        }
    }
}

@Composable
private fun DashboardSmartPlaylists(
    playlists: List<DashboardPlaylist>,
    loading: Boolean,
    columns: Int,
    large: Boolean,
    onOpen: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DashboardSectionHeader(stringResource(R.string.dashboard_smart_playlists))
        when {
            loading -> DashboardLoading()
            playlists.isEmpty() -> DashboardEmpty(R.string.dashboard_playlists_empty)
            // Every playlist is shown (user, 27.10.2026); Settings can hide some.
            else -> playlists.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { playlist ->
                        DashboardPlaylistCard(playlist, large, onClick = { onOpen(playlist.playlistId) }, modifier = Modifier.weight(1f))
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun DashboardPlaylistCard(
    playlist: DashboardPlaylist,
    large: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = LocalNookastColors.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .snowDusted(18.dp, seed = playlist.playlistId)
            .clip(shape)
            .background(colors.surface.compositeOver(colors.background))
            .border(1.dp, colors.divider.copy(alpha = 0.7f), shape)
            .focusRing(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(if (large) 48.dp else 38.dp).background(colors.accent.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            PlaylistGlyph(playlist.iconName, colors.accent, Modifier.size(if (large) 28.dp else 22.dp))
        }
        val titleStyle = MaterialTheme.typography.titleSmall.copy(fontSize = if (large) 18.sp else 14.sp, lineHeight = if (large) 22.sp else 18.sp)
        val countStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp)
        // Every tile is as tall as a two-line title plus the count, so the grid stays even
        // whatever the names are (user, 27.10.2026); shorter content is centred.
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Column(Modifier.alpha(0f).clearAndSetSemantics {}) {
                Text("", style = titleStyle, minLines = 2)
                Text("", style = countStyle, minLines = 1)
            }
            Column {
                Text(
                    playlist.name.ifBlank { stringResource(R.string.dashboard_unnamed_playlist) },
                    style = titleStyle,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    playlist.episodeCount?.let { pluralStringResource(R.plurals.dashboard_episode_count, it, it) } ?: playlist.summary(),
                    style = countStyle,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * «Your week» (APK-3.6 review: the analytics looked like a placeholder and the chart was empty):
 * the week's listening time large, the daily average, a bar per day with today in the accent and
 * the average as a dashed line, three small figures, the podcast of the week and observations.
 */
@Composable
private fun DashboardWeekCard(
    analytics: DashboardAnalytics,
    loading: Boolean,
    large: Boolean,
    onOpenPodcast: (Int) -> Unit,
    onOpenPodcastNamed: (String) -> Unit = {},
) {
    val colors = LocalNookastColors.current
    val locale = LocalConfiguration.current.locales[0]
    val shape = RoundedCornerShape(20.dp)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DashboardSectionHeader(stringResource(R.string.dashboard_week_title))
        Column(
            Modifier
                .fillMaxWidth()
                .snowDusted(20.dp, seed = 7)
                .clip(shape)
                .background(colors.surface.compositeOver(colors.background))
                .border(1.dp, colors.divider.copy(alpha = 0.7f), shape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (loading && analytics.recentDays.isEmpty()) {
                DashboardLoading()
                return@Column
            }
            // Only what the server knows, so phone and car agree (user, 29.10.2026); the bars are
            // the real last 7 days from the history, not the all-time weekday sums of PinePods.
            val days = analytics.recentDays
            val weekSeconds = days.sumOf { it.listenSeconds }.takeIf { days.isNotEmpty() }
                ?: analytics.weeklyListenSeconds
            Column {
                Text(
                    listeningTime(weekSeconds),
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = if (large) 36.sp else 30.sp),
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
                Text(
                    stringResource(R.string.dashboard_week_average, listeningTime(weekSeconds / 7)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            if (days.size == 7) {
                DashboardWeekChart(
                    minutes = days.map { it.listenSeconds / 60f },
                    large = large,
                    dayLabels = days.map { day ->
                        runCatching { java.time.LocalDate.parse(day.label) }.getOrNull()
                            ?.dayOfWeek?.getDisplayName(java.time.format.TextStyle.SHORT_STANDALONE, locale)
                            ?.replaceFirstChar { it.titlecase(locale) }
                            .orEmpty()
                    },
                    highlight = 6,
                    caption = stringResource(R.string.dashboard_history_caption),
                )
            }
            HorizontalDivider(color = colors.divider.copy(alpha = 0.7f))
            Row(Modifier.fillMaxWidth()) {
                WeekFigure(analytics.weeklyCompletedEpisodes.toString(), stringResource(R.string.dashboard_completed_short), Modifier.weight(1f))
                WeekFigure(analytics.currentStreakDays.toString(), stringResource(R.string.dashboard_streak_short), Modifier.weight(1f))
                WeekFigure(
                    days.count { it.listenSeconds > 0 }.toString(),
                    stringResource(R.string.dashboard_active_days_short),
                    Modifier.weight(1f),
                )
            }
            // All time, and apart from it what was listened in PinePods itself: the import of older
            // listening on one morning would otherwise outweigh it (user, 29.10.2026).
            val totalSeconds = analytics.listeningByDay.sumOf { it.listenSeconds }
            if (totalSeconds > 0 || analytics.pinePodsSeconds >= 0) {
                Row(Modifier.fillMaxWidth()) {
                    WeekFigure(
                        stringResource(R.string.duration_hours, (totalSeconds / 3_600L).toInt()),
                        stringResource(R.string.dashboard_total_label),
                        Modifier.weight(1f),
                    )
                    // The same hours twice say nothing: then only the total (user, 6.11.2026).
                    val sameHours = analytics.pinePodsSeconds / 3_600L == totalSeconds / 3_600L
                    if (analytics.pinePodsSeconds >= 0 && !sameHours) {
                        val since = runCatching { java.time.LocalDate.parse(analytics.pinePodsSince) }.getOrNull()
                            ?.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
                        WeekFigure(
                            stringResource(R.string.duration_hours, (analytics.pinePodsSeconds / 3_600L).toInt()),
                            if (since != null) stringResource(R.string.dashboard_pinepods_since_label, since)
                            else stringResource(R.string.dashboard_pinepods_label),
                            Modifier.weight(2f),
                        )
                    }
                }
            }
            if (analytics.weekPodcastName.isNotBlank()) {
                WeekPodcastRow(
                    name = analytics.weekPodcastName,
                    seconds = analytics.weekPodcastSeconds,
                    artworkUrl = analytics.topPodcasts.firstOrNull { it.name == analytics.weekPodcastName }?.artworkUrl,
                    onClick = { onOpenPodcastNamed(analytics.weekPodcastName) },
                )
            }
            DashboardInsights(analytics, onOpenPodcast)
        }
    }
}

@Composable
private fun WeekPodcastRow(name: String, seconds: Long, artworkUrl: String?, onClick: () -> Unit) {
    val colors = LocalNookastColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Artwork(
            url = artworkUrl ?: podcastArtworkFallback(name).orEmpty(),
            title = name,
            fallbackUrl = podcastArtworkFallback(name),
            downloaded = false,
            badgeSize = 0.dp,
            cornerRadius = 8.dp,
            modifier = Modifier.size(32.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.dashboard_week_podcast), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(listeningTime(seconds), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        androidx.compose.material3.Icon(
            ru.e1173.nookast.designsystem.NookIcons.ChevronDown,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = -90f },
        )
    }
}

/**
 * A few plain observations from the server's own figures instead of badges (user, 29.10.2026):
 * when the account usually listens, how much of what it starts it finishes, its favourite topic,
 * all-time favourite podcast and the longest episode it has played.
 */
@Composable
private fun DashboardInsights(analytics: DashboardAnalytics, onOpenPodcast: (Int) -> Unit) {
    val colors = LocalNookastColors.current
    val lines = buildList {
        if (analytics.usualListeningHour >= 0) {
            add(stringResource(R.string.insight_usual_time, "%d:00".format(analytics.usualListeningHour)) to null)
        }
        if (analytics.completionRatePercent > 0) {
            add(stringResource(R.string.insight_completion, analytics.completionRatePercent.toInt().coerceIn(0, 100)) to null)
        }
        analytics.favoriteCategories.firstOrNull()?.let {
            add(stringResource(R.string.insight_category, it.name) to null)
        }
        analytics.topPodcasts.firstOrNull()?.let {
            add(stringResource(R.string.insight_all_time, it.name, listeningTime(it.listenSeconds)) to it.podcastId)
        }
        analytics.longestEpisode?.let {
            add(stringResource(R.string.insight_longest, cleanEpisodeTitle(it.title), listeningTime(it.durationSeconds)) to null)
        }
    }
    if (lines.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(R.string.dashboard_insights),
            style = MaterialTheme.typography.labelLarge,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        lines.forEach { (text, podcastId) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (podcastId != null) Modifier.clickable { onOpenPodcast(podcastId) } else Modifier)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("•", style = MaterialTheme.typography.bodyMedium, color = colors.accent)
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun WeekFigure(value: String, label: String, modifier: Modifier) {
    val colors = LocalNookastColors.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), fontWeight = FontWeight.Bold, color = colors.textPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun DashboardWeekChart(
    minutes: List<Float>,
    large: Boolean,
    dayLabels: List<String>,
    highlight: Int,
    caption: String,
) {
    val colors = LocalNookastColors.current
    val today = highlight
    val peak = (minutes.maxOrNull() ?: 0f).coerceAtLeast(1f)
    val average = minutes.average().toFloat()
    val labels = dayLabels
    // Interactive (user, 28.10.2026): tap or slide over the bars to read a day's minutes; the bars
    // grow in when the card appears.
    var selected by remember { mutableIntStateOf(-1) }
    val grow = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, androidx.compose.animation.core.tween(700)) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    fun select(x: Float, width: Int, toggle: Boolean) {
        val index = (x / width * 7).toInt().coerceIn(0, 6)
        val next = if (toggle && index == selected) -1 else index
        if (next != selected && next >= 0) {
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        }
        selected = next
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth()) {
            Text(
                if (selected >= 0) {
                    stringResource(
                        R.string.dashboard_day_minutes,
                        labels[selected],
                        listeningTime((minutes[selected] * 60).toLong()),
                    )
                } else {
                    // All-time sums reach thousands of minutes (user saw «79578 мин»): hours read better.
                    listeningTime((peak * 60).toLong())
                },
                modifier = Modifier.align(Alignment.TopEnd),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected >= 0) colors.accent else colors.textSecondary,
                fontWeight = if (selected >= 0) FontWeight.Bold else FontWeight.Normal,
            )
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(if (large) 160.dp else 110.dp)
                    .padding(top = 16.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { select(it.x, size.width, toggle = true) }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, _ -> select(change.position.x, size.width, toggle = false) }
                    },
            ) {
                val slot = size.width / 7f
                val barWidth = slot * 0.46f
                val radius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
                minutes.forEachIndexed { index, value ->
                    val left = slot * index + (slot - barWidth) / 2f
                    drawRoundRect(colors.track, Offset(left, 0f), androidx.compose.ui.geometry.Size(barWidth, size.height), radius)
                    if (value > 0f) {
                        val h = (value / peak * size.height).coerceAtLeast(barWidth) * grow.value
                        val strong = if (selected >= 0) index == selected else index == today
                        drawRoundRect(
                            if (strong) colors.accent else colors.accent.copy(alpha = 0.45f),
                            Offset(left, size.height - h),
                            androidx.compose.ui.geometry.Size(barWidth, h),
                            radius,
                        )
                    }
                }
                if (average > 0f) {
                    val y = size.height - average / peak * size.height
                    drawLine(
                        colors.textSecondary.copy(alpha = 0.7f),
                        Offset(0f, y),
                        Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == (if (selected >= 0) selected else today)) colors.accent else colors.textSecondary,
                    fontWeight = if (index == (if (selected >= 0) selected else today)) FontWeight.Bold else FontWeight.Normal,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Text(
            caption,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary.copy(alpha = 0.8f),
        )
    }
}

/** A Dashboard list in the shared episode rows (user, 27.10.2026). */
@Composable
private fun DashboardEpisodeSection(
    title: String,
    emptyText: Int,
    entries: List<QueueListEntry>,
    allEntries: List<QueueListEntry>,
    state: QueueUiState,
    currentEpisodeId: Int?,
    isPlaying: Boolean,
    largeControls: Boolean,
    actions: EpisodeListActions,
    action: String? = null,
    onAction: () -> Unit = {},
    actionExpanded: Boolean = false,
    downloadedAll: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DashboardSectionHeader(title, action, onAction, actionExpanded)
        if (entries.isEmpty()) {
            DashboardEmpty(emptyText)
        }
        entries.forEach { entry ->
            val index = allEntries.indexOfFirst { it.episodeId == entry.episodeId }.coerceAtLeast(0)
            val download = state.downloadStates[entry.episodeId]
            val downloaded = downloadedAll || download == EpisodeDownloadState.DOWNLOADED
            val current = entry.episodeId == currentEpisodeId
            EpisodeRow(
                title = entry.episodeTitle.orEmpty(),
                showArtwork = true,
                showControls = false,
                artworkUrl = entry.artworkUrl.orEmpty(),
                podcastName = entry.podcastName,
                durationSeconds = entry.durationSeconds,
                listenedSeconds = entry.listenDurationSeconds,
                completed = entry.completed == true,
                downloaded = downloaded,
                downloading = download == EpisodeDownloadState.QUEUED || download == EpisodeDownloadState.DOWNLOADING,
                downloadProgress = state.downloadProgress[entry.episodeId],
                playing = current && isPlaying,
                largeControls = largeControls,
                onOpen = { actions.onOpen(allEntries, index) },
                onPlay = { if (current) actions.onTogglePlayback() else actions.onPlay(allEntries, index) },
                meta = episodeMetaLine(entry.episodeId, entry.episodeTitle.orEmpty(), entry.publishedAt)
                    .ifBlank { entry.podcastName.orEmpty() },
                menu = standardEpisodeMenu(
                    downloaded = downloaded,
                    completed = entry.completed == true,
                    onAddToQueue = { actions.onAddToQueue(entry) },
                    onMarkPlayed = { actions.onMarkPlayed(entry) },
                    onMarkUnplayed = null,
                    onDownload = { actions.onDownload(entry) },
                    onDeleteDownload = { actions.onDeleteDownload(entry.episodeId) },
                ),
            )
        }
    }
}

@Composable
private fun DashboardSectionHeader(
    title: String,
    action: String? = null,
    onAction: () -> Unit = {},
    actionExpanded: Boolean = false,
) {
    val colors = LocalNookastColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f, fill = false).sectionHeading(),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp),
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // A small round chevron instead of «Показать все» / «Свернуть», which did not fit next to
        // a long title (user, 28.10.2026). The words stay as its accessibility label.
        action?.let { label ->
            val turn by androidx.compose.animation.core.animateFloatAsState(
                if (actionExpanded) 180f else 0f,
                label = "sectionChevron",
            )
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.14f))
                    .clickable(onClickLabel = label, onClick = onAction)
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    ru.e1173.nookast.designsystem.NookIcons.ChevronDown,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = turn },
                )
            }
        }
    }
}

@Composable
private fun DashboardMessage(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = DashboardTileShape,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun DashboardLoading() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(19.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(9.dp))
        Text(
            stringResource(R.string.dashboard_loading),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DashboardEmpty(resource: Int) {
    Text(
        stringResource(resource),
        modifier = Modifier.padding(start = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DashboardSettingsGlyph(modifier: Modifier) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val stroke = 1.8.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color, size.minDimension * 0.17f, center, style = Stroke(stroke))
        drawCircle(color, size.minDimension * 0.34f, center, style = Stroke(stroke))
        repeat(8) { tooth ->
            val angle = Math.toRadians(tooth * 45.0)
            drawLine(
                color,
                Offset(center.x + cos(angle).toFloat() * size.minDimension * 0.37f, center.y + sin(angle).toFloat() * size.minDimension * 0.37f),
                Offset(center.x + cos(angle).toFloat() * size.minDimension * 0.47f, center.y + sin(angle).toFloat() * size.minDimension * 0.47f),
                stroke,
                StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun DashboardPlaylist.summary(): String = when {
    description.isNotBlank() -> description
    includePartiallyPlayed && !includeUnplayed && !includePlayed -> stringResource(R.string.dashboard_playlist_partial)
    maxDurationSeconds != null -> stringResource(R.string.dashboard_playlist_max_minutes, maxDurationSeconds / 60)
    timeFilterHours != null -> stringResource(R.string.dashboard_playlist_recent_hours, timeFilterHours)
    else -> stringResource(R.string.dashboard_smart_playlist)
}

private fun DashboardEpisode.toQueueEntry(index: Int) = QueueListEntry(
    episodeId = episodeId,
    queuePosition = index,
    episodeTitle = title,
    podcastName = podcastName,
    artworkUrl = artworkUrl,
    mediaUrl = mediaUrl,
    durationSeconds = durationSeconds,
    listenDurationSeconds = listenedSeconds.toDouble(),
    completed = completed,
    description = "",
    publishedAt = publishedAt,
    downloaded = downloadedOnServer,
    refreshedAtEpochMs = null,
)

private fun LocalDownloadedEpisode.toQueueEntry(index: Int) = QueueListEntry(
    episodeId = episodeId,
    queuePosition = index,
    episodeTitle = title,
    podcastName = podcastName,
    artworkUrl = artworkUrl,
    mediaUrl = "",
    durationSeconds = durationSeconds,
    listenDurationSeconds = listenedSeconds,
    completed = completed,
    description = "",
    publishedAt = publishedAt,
    downloaded = true,
    refreshedAtEpochMs = completedAtEpochMs,
)

@Composable
private fun listeningTime(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val hours = safe / 3_600L
    val minutes = safe % 3_600L / 60L
    return if (hours > 0L) {
        stringResource(R.string.dashboard_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.dashboard_minutes, minutes)
    }
}

@Composable
private fun remaining(duration: Int, listened: Int): String {
    val minutes = (duration - listened).coerceAtLeast(0) / 60
    return if (minutes > 0) {
        minutesLeftLabel(minutes)
    } else {
        stringResource(R.string.podcast_played)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.0f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
