package ru.e1173.nookast.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import ru.e1173.nookast.preferences.PodcastViewMode
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import ru.e1173.nookast.R
import ru.e1173.nookast.offline.EpisodeDownloadState
import ru.e1173.nookast.data.local.QueueListEntry
import ru.e1173.nookast.data.remote.PlaylistRules
import ru.e1173.nookast.data.remote.QueueLoadError
import ru.e1173.nookast.designsystem.LocalNookastColors
import ru.e1173.nookast.designsystem.NookExpandable
import ru.e1173.nookast.designsystem.NookIcons

/** What the rows of an [EpisodeListScreen] can do; the same handlers as a podcast page. */
internal class EpisodeListActions(
    val onOpen: (List<QueueListEntry>, Int) -> Unit,
    val onPlay: (List<QueueListEntry>, Int) -> Unit,
    val onTogglePlayback: () -> Unit,
    val onAddToQueue: (QueueListEntry) -> Unit,
    val onMarkPlayed: (QueueListEntry) -> Unit,
    val onDownload: (QueueListEntry) -> Unit,
    val onDeleteDownload: (Int) -> Unit,
)

/**
 * A list of episodes in the layout of a podcast page (user, 27.10.2026): the Feed and smart
 * playlists. The header carries the list's name instead of a podcast's, no settings gear; the
 * description card states the list's rules when it has them. Rows are the shared [EpisodeRow].
 */
@Composable
internal fun EpisodeListScreen(
    title: String,
    subtitle: String,
    description: String?,
    glyph: @Composable () -> Unit,
    entries: List<QueueListEntry>,
    loading: Boolean,
    error: QueueLoadError?,
    currentEpisodeId: Int?,
    isPlaying: Boolean,
    downloadStates: Map<Int, EpisodeDownloadState>,
    downloadProgress: Map<Int, Float?>,
    wide: Boolean,
    largeControls: Boolean,
    actions: EpisodeListActions,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    /** Covers in the rows: smart playlists show them, the Feed does not (user, 29.10.2026). */
    showArtwork: Boolean = false,
    /** A smart playlist or the Feed ([FEED_HIDE_PLAYED_KEY]) can hide its played episodes like a
     *  podcast page (user, 29.10.2026). */
    playlistId: Int? = null,
) {
    val colors = LocalNookastColors.current
    val preferences: ru.e1173.nookast.preferences.AppPreferencesViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val display by preferences.state.collectAsStateWithLifecycle()
    val tiles = display.episodeListViewMode == PodcastViewMode.GRID
    val hidePlayed = playlistId != null && playlistId in display.hidePlayedPlaylistIds
    val shown = remember(entries, hidePlayed) { if (hidePlayed) entries.filter { it.completed != true } else entries }
    val scale = if (largeControls) 4f / 3f else 1f
    val gridState = rememberLazyGridState()
    ScrollToTopEffect { gridState.animateScrollToItem(0) }
    val scrolled by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0 }
    }
    // A playlist opened from the Dashboard owns the whole width for its glass bar; the Feed is
    // inset by the app.
    val sides = if (onBack != null) 7.dp else 0.dp
    val grid: @Composable (Dp) -> Unit = { topPadding ->
    LazyVerticalGrid(
        flingBehavior = deviceFling(),
        columns = if (tiles) GridCells.Adaptive(168.dp * scale) else GridCells.Fixed(1),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = sides, end = sides, top = topPadding, bottom = 16.dp + LocalBottomInset.current),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ListHero(
                    title = title,
                    subtitle = subtitle,
                    glyph = glyph,
                    loading = loading,
                    canPlay = entries.isNotEmpty(),
                    largeControls = largeControls,
                    onPlayAll = { actions.onPlay(entries, 0) },
                )
                if (!description.isNullOrBlank()) {
                    val shape = RoundedCornerShape(16.dp)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(colors.surface.compositeOver(colors.background))
                            .border(1.dp, colors.divider.copy(alpha = 0.7f), shape)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        NookExpandable(
                            collapsedHeight = 66.dp,
                            moreLabel = stringResource(R.string.description_more),
                            lessLabel = stringResource(R.string.description_less),
                        ) {
                            Text(
                                description,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
                if (error != null && entries.isEmpty()) {
                    Text(stringResource(R.string.feed_error), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                } else if (entries.isEmpty() && !loading) {
                    Text(stringResource(R.string.feed_empty), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                }
                if (entries.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.episodes_section),
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            // One line, never split mid-word (user, 31.10.2026: «Выпуск/и»): the toggle beside it
                            // takes the room left and wraps its own label instead.
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.sectionHeading(),
                        )
                        Spacer(Modifier.width(8.dp))
                        if (playlistId != null) {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                                HidePlayedToggle(hidePlayed) { preferences.setPlaylistHidePlayed(playlistId, !hidePlayed) }
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.width(8.dp))
                        PodcastViewToggle(display.episodeListViewMode, preferences::setEpisodeListViewMode)
                    }
                }
            }
        }
        itemsIndexed(shown, key = { _, entry -> entry.episodeId }) { index, entry ->
            val state = downloadStates[entry.episodeId]
            val downloaded = state == EpisodeDownloadState.DOWNLOADED
            val current = entry.episodeId == currentEpisodeId
            val subtitle = episodeMetaLine(entry.episodeId, entry.episodeTitle.orEmpty(), entry.publishedAt, if (showArtwork) null else entry.podcastName)
            val menu = standardEpisodeMenu(
                downloaded = downloaded,
                completed = entry.completed == true,
                onAddToQueue = { actions.onAddToQueue(entry) },
                onMarkPlayed = { actions.onMarkPlayed(entry) },
                onMarkUnplayed = null,
                onDownload = { actions.onDownload(entry) },
                onDeleteDownload = { actions.onDeleteDownload(entry.episodeId) },
            )
            if (tiles) {
                EpisodeTile(
                    title = entry.episodeTitle.orEmpty(),
                    subtitle = subtitle,
                    artworkUrl = entry.artworkUrl.orEmpty(),
                    podcastName = entry.podcastName,
                    durationSeconds = entry.durationSeconds,
                    listenedSeconds = entry.listenDurationSeconds,
                    completed = entry.completed == true,
                    downloaded = downloaded,
                    downloading = state == EpisodeDownloadState.QUEUED || state == EpisodeDownloadState.DOWNLOADING,
                    downloadProgress = downloadProgress[entry.episodeId],
                    playing = current && isPlaying,
                    largeControls = largeControls,
                    onOpen = { actions.onOpen(shown, index) },
                    onPlay = { if (current) actions.onTogglePlayback() else actions.onPlay(shown, index) },
                    menu = menu,
                )
            } else EpisodeRow(
                title = entry.episodeTitle.orEmpty(),
                meta = subtitle,
                showArtwork = showArtwork,
                artworkUrl = entry.artworkUrl.orEmpty(),
                podcastName = entry.podcastName,
                durationSeconds = entry.durationSeconds,
                listenedSeconds = entry.listenDurationSeconds,
                completed = entry.completed == true,
                downloaded = downloaded,
                downloading = state == EpisodeDownloadState.QUEUED || state == EpisodeDownloadState.DOWNLOADING,
                downloadProgress = downloadProgress[entry.episodeId],
                playing = current && isPlaying,
                largeControls = largeControls,
                onOpen = { actions.onOpen(shown, index) },
                onPlay = { if (current) actions.onTogglePlayback() else actions.onPlay(shown, index) },
                menu = menu,
            )
        }
    }
    }
    if (onBack != null) {
        // Back stays on top as glass over the scrolled list and the status bar (user, 29.10.2026).
        GlassHeaderScreen(
            headerHeight = 52.dp,
            scrolled = scrolled,
            modifier = modifier.fillMaxSize(),
            header = { ListBackBar(title = title, showTitle = scrolled, onBack = onBack) },
        ) { topPadding -> grid(topPadding) }
    } else {
        Box(modifier.fillMaxSize()) { grid(LocalTopInset.current + 4.dp) }
    }
}

@Composable
private fun ListHero(
    title: String,
    subtitle: String,
    glyph: @Composable () -> Unit,
    loading: Boolean,
    canPlay: Boolean,
    largeControls: Boolean,
    onPlayAll: () -> Unit,
) {
    val colors = LocalNookastColors.current
    val scale = if (largeControls) 4f / 3f else 1f
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(96.dp * scale)
                .clip(RoundedCornerShape(24.dp * scale))
                .background(Brush.linearGradient(listOf(colors.accent, colors.accentStrong))),
            contentAlignment = Alignment.Center,
        ) { glyph() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp * scale, lineHeight = 30.sp * scale),
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, maxLines = 1)
                if (loading) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = colors.accent)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroButton(stringResource(R.string.list_play_all), filled = true, enabled = canPlay, onClick = onPlayAll)
            }
        }
    }
}

@Composable
private fun HeroButton(label: String, filled: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalNookastColors.current
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .background(if (filled) colors.accent else colors.tintContainer)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            fontWeight = FontWeight.SemiBold,
            color = if (filled) colors.onAccent else colors.accent,
        )
    }
}

/** «25 окт.» this year, «25 окт. 2025» before; the raw text if it is not an ISO date. */

/**
 * The rules of a smart playlist in words (user: show how the list is built), e.g. «15–45 мин ·
 * новые и начатые · сначала новые».
 */
@Composable
internal fun playlistRulesText(rules: PlaylistRules): String {
    val parts = mutableListOf<String>()
    val min = rules.minDurationMinutes
    val max = rules.maxDurationMinutes
    when {
        min != null && max != null -> parts += stringResource(R.string.rule_duration_between, min, max)
        min != null -> parts += stringResource(R.string.rule_duration_over, min)
        max != null -> parts += stringResource(R.string.rule_duration_under, max)
    }
    val states = buildList {
        if (rules.includeUnplayed) add(stringResource(R.string.rule_state_new))
        if (rules.includePartiallyPlayed) add(stringResource(R.string.rule_state_started))
        if (rules.includePlayed) add(stringResource(R.string.rule_state_played))
    }
    if (states.size in 1..2) parts += states.joinToString(stringResource(R.string.rule_and))
    if (states.size == 3) parts += stringResource(R.string.rule_state_all)
    rules.timeFilterHours?.let { hours ->
        parts += if (hours % 24 == 0) {
            pluralStringResource(R.plurals.rule_last_days, hours / 24, hours / 24)
        } else {
            pluralStringResource(R.plurals.rule_last_hours, hours, hours)
        }
    }
    if (rules.podcastCount > 0) parts += pluralStringResource(R.plurals.rule_podcasts, rules.podcastCount, rules.podcastCount)
    when (rules.sortOrder) {
        "date_desc" -> parts += stringResource(R.string.rule_sort_newest)
        "date_asc" -> parts += stringResource(R.string.rule_sort_oldest)
        "duration_asc" -> parts += stringResource(R.string.rule_sort_shortest)
        "duration_desc" -> parts += stringResource(R.string.rule_sort_longest)
    }
    return parts.joinToString(" · ")
}

/**
 * «Hide played» of the podcast pages, shared with smart playlists (user, 29.10.2026): a small
 * check box and its label, no highlight on tap.
 */
@Composable
internal fun HidePlayedToggle(hidePlayed: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompactCheckbox(checked = hidePlayed)
        Text(
            stringResource(if (hidePlayed) R.string.podcast_show_played else R.string.podcast_hide_played),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

/** Key of the Feed among the lists that hide their played episodes (user, 29.10.2026). */
internal const val FEED_HIDE_PLAYED_KEY = Int.MAX_VALUE

/** The sticky bar of a list opened from the Dashboard: Back, and the list's name once scrolled. */
@Composable
private fun ListBackBar(title: String, showTitle: Boolean, onBack: () -> Unit) {
    val colors = LocalNookastColors.current
    val backLabel = stringResource(R.string.back)
    Box(Modifier.fillMaxSize().padding(horizontal = 7.dp)) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onBack)
                .semantics { contentDescription = backLabel },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(38.dp).floatingDisc().background(colors.tintContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(NookIcons.ChevronLeft, null, tint = colors.accent, modifier = Modifier.size(18.dp))
            }
        }
        if (showTitle) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp),
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
            )
        }
    }
}
