package ru.e1173.nookast.ui

import ru.e1173.nookast.designsystem.leavingTitle
import ru.e1173.nookast.designsystem.trackLargeTitle
import ru.e1173.nookast.designsystem.trackHeaderBar
import ru.e1173.nookast.designsystem.NookType.scaled
import ru.e1173.nookast.designsystem.nookCard
import ru.e1173.nookast.designsystem.focusRing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ru.e1173.nookast.designsystem.nookElevation
import ru.e1173.nookast.R
import ru.e1173.nookast.data.local.QueueListEntry
import ru.e1173.nookast.data.remote.PodcastDetails
import ru.e1173.nookast.designsystem.LocalNookastColors
import ru.e1173.nookast.designsystem.NookExpandable
import ru.e1173.nookast.designsystem.NookIcons
import ru.e1173.nookast.offline.EpisodeDownloadState

/** What the episode card can do; playback starts only from its Play button. */
internal class EpisodeCardActions(
    val onBack: () -> Unit,
    val onPlay: () -> Unit,
    val onTogglePlayback: () -> Unit,
    val onAddToQueue: () -> Unit,
    val onRemoveFromQueue: () -> Unit,
    val onDownload: () -> Unit,
    val onDeleteDownload: () -> Unit,
    val onToggleSaved: () -> Unit,
    val onMarkPlayed: () -> Unit,
    val onMarkUnplayed: () -> Unit,
    val onOpenPodcast: () -> Unit,
    /** Seeks in the episode that plays, or starts this one there (a chapter or a time). */
    val onPlayFrom: (Long) -> Unit,
    val onOpenEpisode: (QueueListEntry) -> Unit,
)

/**
 * The episode page the Apple Podcasts way (user, 30.10.2026: the first card did not suit — "redo
 * it the Apple way without leaving the app's design"). Tapping an episode in a list opens it while
 * the current episode keeps playing; only the Play capsule replaces what plays.
 *
 * - the cover with its light over the page, a caption line (date and length), the title and the
 *   podcast as a link;
 * - one row of actions (user, 31.10.2026): the Play capsule and Like as a round button that fills
 *   when on; under them how much is left once started;
 * - "⋯" in the glass bar: add to or remove from Up Next, download or delete, played, the podcast;
 * - the description open to its first lines, the chapters (as on Now Playing's Description: a tap
 *   plays from there), «Information» as an iOS inset group and more episodes of the podcast.
 */
@Composable
internal fun EpisodeCardScreen(
    entry: QueueListEntry,
    podcastDetails: PodcastDetails?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    inQueue: Boolean,
    saved: Boolean,
    downloadState: EpisodeDownloadState?,
    downloadProgress: Float?,
    wide: Boolean,
    largeControls: Boolean,
    actions: EpisodeCardActions,
    chapters: List<DescriptionTimestamp>,
    /** The chapters of the feed or the file, for their pictures. */
    sourceChapters: List<ru.e1173.nookast.player.PlaybackChapter>,
    positionMs: () -> Long,
    moreEpisodes: List<QueueListEntry>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNookastColors.current
    val scroll = rememberScrollState()
    val title = cleanEpisodeTitle(entry.episodeTitle.orEmpty())
    val description = entry.descriptionOrCached(podcastDetails)
    val artwork = entry.artworkUrl.orEmpty()
    val downloaded = downloadState == EpisodeDownloadState.DOWNLOADED
    val downloading = downloadState == EpisodeDownloadState.QUEUED || downloadState == EpisodeDownloadState.DOWNLOADING
    // The bar's title follows the large one going under it (design audit 4.6).
    val titleTracker = ru.e1173.nookast.designsystem.rememberHeaderTitleTracker()
    val density = LocalDensity.current
    val scale = if (largeControls) 4f / 3f else 1f
    Box(modifier.fillMaxSize()) {
        run {
            GlassHeaderScreen(
                headerHeight = 52.dp,
                scrolled = scroll.value > 0,
                backdrop = { ArtworkTintBackdrop(artwork, Modifier.matchParentSize()) },
                modifier = Modifier.fillMaxSize(),
                header = {
                    EpisodeCardBar(
                        title = title,
                        showTitle = false,
                        titleProgress = { titleTracker.progress(density) },
                        modifier = Modifier.trackHeaderBar(titleTracker),
                        menu = buildList {
                            // Up Next lives here, not beside Play (user, 31.10.2026).
                            if (inQueue) {
                                add(EpisodeMenuItem(stringResource(R.string.episode_card_remove_from_queue), actions.onRemoveFromQueue, destructive = true))
                            } else {
                                add(EpisodeMenuItem(stringResource(R.string.add_to_queue), actions.onAddToQueue))
                            }
                            when {
                                downloaded -> add(EpisodeMenuItem(stringResource(R.string.delete_download), actions.onDeleteDownload, destructive = true))
                                downloading -> Unit
                                else -> add(EpisodeMenuItem(stringResource(R.string.download_episode), actions.onDownload))
                            }
                            if (entry.completed == true) {
                                add(EpisodeMenuItem(stringResource(R.string.mark_unplayed), actions.onMarkUnplayed))
                            } else {
                                add(EpisodeMenuItem(stringResource(R.string.mark_played_action), actions.onMarkPlayed))
                            }
                            if (!entry.podcastName.isNullOrBlank()) {
                                add(EpisodeMenuItem(stringResource(R.string.episode_card_go_to_podcast), actions.onOpenPodcast))
                            }
                        },
                        onBack = actions.onBack,
                    )
                },
            ) { topPadding ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll, flingBehavior = deviceFling())
                        .padding(top = topPadding, bottom = 28.dp + LocalBottomInset.current)
                        .padding(horizontal = if (largeControls) 22.dp else 20.dp)
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .widthIn(max = 640.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val coverSize = when {
                        largeControls -> 250.dp
                        wide -> 240.dp
                        else -> 216.dp
                    }
                    val coverShape = RoundedCornerShape(ru.e1173.nookast.designsystem.NookShapes.mRadius * ARTWORK_CORNER_SCALE)
                    // The picture of the chapter at the position, as on Now Playing (user, 31.10.2026).
                    Box(
                        Modifier
                            .padding(top = 12.dp)
                            .then(Modifier.nookElevation(ru.e1173.nookast.designsystem.NookElevation.Level2, ru.e1173.nookast.designsystem.NookShapes.mRadius * ARTWORK_CORNER_SCALE))
                            .size(coverSize),
                    ) {
                        ChapterCover(
                            chapters = sourceChapters,
                            positionMs = if (isCurrent) positionMs() else ((entry.listenDurationSeconds ?: 0.0) * 1_000).toLong(),
                            artworkUrl = artwork,
                            title = entry.episodeTitle.orEmpty(),
                            podcastName = entry.podcastName,
                            cornerRadius = 14.dp,
                            decodeSize = coverSize,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.height(22.dp))
                    // Apple's caption: the numbers, the date and the length in small capitals above the
                    // title, and the app's download mark after them (user, 31.10.2026: a separate
                    // «Downloaded» line under the buttons did not belong to the design).
                    val length = entry.durationSeconds?.takeIf { it > 0 }?.let { minutesLabel((it / 60).coerceAtLeast(1)) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            listOfNotNull(
                                episodeMetaLine(entry.episodeId, entry.episodeTitle.orEmpty(), entry.publishedAt).takeIf(String::isNotBlank),
                                length,
                            ).joinToString(" • ").uppercase(),
                            style = ru.e1173.nookast.designsystem.NookType.label.scaled(scale).copy(letterSpacing = 0.06.em),
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        EpisodeDownloadMark(downloaded, downloading, downloadProgress, 14.dp * scale)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        title,
                        style = ru.e1173.nookast.designsystem.NookType.heroTitle.scaled(scale).copy(letterSpacing = (-0.01).em),
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .trackLargeTitle(titleTracker)
                            .leavingTitle { titleTracker.progress(density) }
                            .semantics { heading() },
                    )
                    entry.podcastName?.takeIf(String::isNotBlank)?.let { podcast ->
                        Row(
                            Modifier
                                .padding(top = 4.dp)
                                .clip(ru.e1173.nookast.designsystem.NookShapes.s)
                                .focusRing(ru.e1173.nookast.designsystem.NookShapes.s)
                                .clickable(role = Role.Button, onClick = actions.onOpenPodcast)
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                podcast,
                                style = ru.e1173.nookast.designsystem.NookType.barTitle.scaled(scale),
                                color = colors.accentText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Icon(NookIcons.ChevronRight, null, tint = colors.accentText, modifier = Modifier.size(14.dp * scale))
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(
                        Modifier.fillMaxWidth().widthIn(max = 420.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PlayCapsule(entry, isCurrent, isPlaying, actions, scale, Modifier.weight(1f))
                        RoundAction(
                            icon = if (saved) NookIcons.HeartFilled else NookIcons.Heart,
                            label = stringResource(if (saved) R.string.episode_card_liked else R.string.episode_card_like),
                            active = saved,
                            scale = scale,
                            onClick = actions.onToggleSaved,
                        )
                    }
                    EpisodeCardStatus(entry, isCurrent, positionMs, scale)
                    Spacer(Modifier.height(22.dp))
                    val bodyStyle = ru.e1173.nookast.designsystem.NookType.body.scaled(scale)
                    // A feed that gives only "..." has no description to show.
                    if (remember(description) { descriptionWithoutTimestampList(normalizeDescriptionText(description)).any(Char::isLetterOrDigit) }) Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
                        NookExpandable(
                            collapsedHeight = with(LocalDensity.current) { bodyStyle.lineHeight.toDp() * DESCRIPTION_LINES },
                            moreLabel = stringResource(R.string.description_more),
                            lessLabel = stringResource(R.string.description_less),
                        ) {
                            RichDescriptionText(
                                html = description,
                                fallback = stringResource(R.string.description_unavailable),
                                style = bodyStyle,
                                color = colors.textPrimary.copy(alpha = 0.86f),
                                // A time seeks in the episode that plays, or starts this one there.
                                onSeekTo = actions.onPlayFrom,
                                // As on Now Playing: a list of times becomes the chapters below.
                                hideTimestampList = true,
                            )
                        }
                    }
                    if (chapters.isNotEmpty()) {
                        CardSection(
                            stringResource(R.string.chapters_title),
                            scale,
                            badge = if (chapters.all(DescriptionTimestamp::fromDescription)) stringResource(R.string.chapters_from_description) else null,
                        ) {
                            Box(Modifier.padding(6.dp)) {
                                ChapterList(
                                    chapters = chapters,
                                    // Only the episode that plays has a current chapter.
                                    positionMs = if (isCurrent) positionMs() else -1L,
                                    largeControls = largeControls,
                                    onSelect = actions.onPlayFrom,
                                )
                            }
                        }
                        Spacer(Modifier.height(22.dp))
                    }
                    EpisodeInformation(entry, downloaded, scale, actions.onOpenPodcast)
                    if (moreEpisodes.isNotEmpty()) {
                        Spacer(Modifier.height(22.dp))
                        MoreEpisodes(entry.podcastName.orEmpty(), moreEpisodes, scale, actions.onOpenEpisode)
                    }
                }
            }
        }
    }
}

private const val DESCRIPTION_LINES = 6

@Composable
internal fun EpisodeCardBar(
    title: String,
    showTitle: Boolean,
    menu: List<EpisodeMenuItem>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** As the large title leaves under the bar (design audit 4.6). */
    titleProgress: (() -> Float)? = null,
) {
    ru.e1173.nookast.designsystem.NookHeaderBar(
        title = title,
        showTitle = showTitle,
        onBack = onBack,
        backLabel = stringResource(R.string.back),
        modifier = modifier,
        titleProgress = titleProgress,
        actions = if (menu.isEmpty()) null else ({
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                ru.e1173.nookast.designsystem.NookHeaderDisc(null, stringResource(R.string.episode_more_actions), { menuOpen = true })
                EpisodeMenu(menu, menuOpen, onDismiss = { menuOpen = false })
            }
        }),
    )
}

/**
 * The one control that changes what plays (Apple's capsule): Play, Resume, Play again, or Pause for
 * the episode that plays. How much is left is told under the row, not squeezed into the capsule.
 */
@Composable
private fun PlayCapsule(
    entry: QueueListEntry,
    isCurrent: Boolean,
    isPlaying: Boolean,
    actions: EpisodeCardActions,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val listened = entry.listenDurationSeconds?.toInt() ?: 0
    val completed = entry.completed == true
    val playingNow = isCurrent && isPlaying
    val label = when {
        playingNow -> stringResource(R.string.pause)
        isCurrent || (listened > 0 && !completed) -> stringResource(R.string.episode_card_resume)
        completed -> stringResource(R.string.episode_card_play_again)
        else -> stringResource(R.string.play)
    }
    ru.e1173.nookast.designsystem.NookButton(
        text = label,
        onClick = if (isCurrent) actions.onTogglePlayback else actions.onPlay,
        modifier = modifier,
        size = ru.e1173.nookast.designsystem.NookButtonSize.L,
        icon = if (playingNow) NookIcons.Pause else NookIcons.Play,
        scale = scale,
    )
}

/**
 * Under the actions, as Apple puts it under the button: a progress line with the time left once the
 * episode is started (live for the one that plays). Nothing for an episode not started.
 */
@Composable
private fun EpisodeCardStatus(
    entry: QueueListEntry,
    isCurrent: Boolean,
    positionMs: () -> Long,
    scale: Float,
) {
    val colors = LocalNookastColors.current
    val durationMs = (entry.durationSeconds ?: 0) * 1_000L
    val listenedMs = if (isCurrent) positionMs() else ((entry.listenDurationSeconds ?: 0.0) * 1_000).toLong()
    val started = durationMs > 0 && listenedMs > 0 && (isCurrent || entry.completed != true)
    if (!started) return
    val style = ru.e1173.nookast.designsystem.NookType.bodySmall.scaled(scale)
    val fraction by animateFloatAsState((listenedMs.toFloat() / durationMs).coerceIn(0f, 1f), label = "cardProgress")
    Row(
        Modifier.padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.width(64.dp * scale).height(4.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.2f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(colors.accent))
        }
        Text(
            minutesLeftLabel((((durationMs - listenedMs) / 60_000).toInt()).coerceAtLeast(1)),
            style = style,
            color = colors.textSecondary,
            maxLines = 1,
        )
    }
}

/** A round 50 dp action beside the capsule; [active] fills it with the accent. */
@Composable
private fun RoundAction(
    icon: ImageVector,
    label: String,
    active: Boolean,
    scale: Float,
    onClick: () -> Unit,
) {
    val colors = LocalNookastColors.current
    val fill by animateColorAsState(if (active) colors.accent else colors.tintContainer, label = "roundFill")
    val tint by animateColorAsState(if (active) colors.onAccent else colors.accent, label = "roundTint")
    Box(
        Modifier
            .size(50.dp * scale)
            .clip(CircleShape)
            .background(fill)
            .focusRing(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp * scale))
    }
}

/** A titled iOS inset group of the card: heading, then [content] on a rounded plate. */
@Composable
internal fun CardSection(title: String, scale: Float, badge: String? = null, content: @Composable () -> Unit) {
    val colors = LocalNookastColors.current
    Column(Modifier.fillMaxWidth()) {
        ru.e1173.nookast.designsystem.NookSectionTitle(
            title,
            Modifier.padding(bottom = 10.dp),
            scale = scale,
            maxLines = 2,
            trailing = badge?.let { { Text(it, style = ru.e1173.nookast.designsystem.NookType.meta, color = colors.textSecondary, maxLines = 1) } },
        )
        Column(Modifier.fillMaxWidth().nookCard(radius = ru.e1173.nookast.designsystem.NookShapes.lRadius * scale)) { content() }
    }
}

/** «Information» as an iOS inset group: podcast, date, length, number, progress and the device. */
@Composable
private fun EpisodeInformation(entry: QueueListEntry, downloaded: Boolean, scale: Float, onOpenPodcast: () -> Unit) {
    val colors = LocalNookastColors.current
    val locale = LocalConfiguration.current.locales[0]
    val number = episodeNumberLabel(
        LocalEpisodeNumbers.current[entry.episodeId]?.takeUnless(EpisodeNumber::isEmpty)
            ?: episodeTitleParts(entry.episodeTitle.orEmpty()).number,
    )
    val duration = entry.durationSeconds?.takeIf { it > 0 }
    val listened = entry.listenDurationSeconds?.takeIf { it > 0 }
    val rows = buildList<Triple<String, String, (() -> Unit)?>> {
        entry.podcastName?.takeIf(String::isNotBlank)?.let { add(Triple(stringResource(R.string.episode_info_podcast), it, onOpenPodcast)) }
        entry.publishedAt?.takeIf(String::isNotBlank)?.let { add(Triple(stringResource(R.string.episode_info_published), listDate(it, locale), null)) }
        duration?.let { add(Triple(stringResource(R.string.episode_info_length), minutesLabel((it / 60).coerceAtLeast(1)), null)) }
        number?.let { add(Triple(stringResource(R.string.episode_info_number), it, null)) }
        when {
            entry.completed == true -> add(Triple(stringResource(R.string.episode_info_progress), stringResource(R.string.played), null))
            listened != null && duration != null -> add(
                Triple(
                    stringResource(R.string.episode_info_progress),
                    stringResource(R.string.episode_info_percent, ((listened / duration) * 100).toInt().coerceIn(1, 99)),
                    null,
                ),
            )
        }
        if (downloaded) add(Triple(stringResource(R.string.episode_info_file), stringResource(R.string.episode_info_on_device), null))
    }
    if (rows.isEmpty()) return
    CardSection(stringResource(R.string.episode_info_title), scale) {
        rows.forEachIndexed { index, (label, value, onClick) ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = colors.divider)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp * scale)
                    .then(if (onClick != null) Modifier.focusRing(RectangleShapeless).clickable(role = Role.Button, onClick = onClick) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(label, style = ru.e1173.nookast.designsystem.NookType.body.scaled(scale), color = colors.textSecondary)
                Text(
                    value,
                    style = ru.e1173.nookast.designsystem.NookType.body.scaled(scale),
                    color = if (onClick != null) colors.accent else colors.textPrimary,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (onClick != null) Icon(NookIcons.ChevronRight, null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** «More from the podcast» (user, 31.10.2026): the next episodes as rows that open their own card. */
@Composable
private fun MoreEpisodes(podcast: String, episodes: List<QueueListEntry>, scale: Float, onOpen: (QueueListEntry) -> Unit) {
    val colors = LocalNookastColors.current
    val locale = LocalConfiguration.current.locales[0]
    CardSection(stringResource(R.string.episode_card_more_from, podcast), scale) {
        episodes.forEachIndexed { index, episode ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 76.dp), thickness = 0.5.dp, color = colors.divider)
            Row(
                Modifier
                    .fillMaxWidth()
                    .focusRing(RectangleShapeless)
                    .clickable(role = Role.Button) { onOpen(episode) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Artwork(
                    url = episode.artworkUrl.orEmpty(),
                    title = episode.episodeTitle.orEmpty(),
                    fallbackUrl = podcastArtworkFallback(episode.podcastName),
                    downloaded = false,
                    badgeSize = 0.dp,
                    cornerRadius = 8.dp,
                    decodeSize = 52.dp,
                    modifier = Modifier.size(52.dp * scale),
                )
                Column(Modifier.weight(1f)) {
                    val meta = listOfNotNull(
                        episode.publishedAt?.takeIf(String::isNotBlank)?.let { listDate(it, locale) },
                        episode.durationSeconds?.takeIf { it > 0 }?.let { minutesLabel((it / 60).coerceAtLeast(1)) },
                    ).joinToString(" • ")
                    if (meta.isNotEmpty()) {
                        Text(meta.uppercase(), style = ru.e1173.nookast.designsystem.NookType.meta.scaled(scale), color = colors.textSecondary, maxLines = 1)
                    }
                    Text(
                        cleanEpisodeTitle(episode.episodeTitle.orEmpty()),
                        style = ru.e1173.nookast.designsystem.NookType.rowTitle.scaled(scale),
                        color = if (episode.completed == true) colors.textSecondary else colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(NookIcons.ChevronRight, null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

private const val MORE_EPISODES = 5

private val RectangleShapeless = androidx.compose.ui.graphics.RectangleShape

/** The card of [episodeId] wired to the app: finds the episode, its podcast and what can be done. */
@Composable
internal fun EpisodeCardRoute(
    episodeId: Int,
    cardEntries: MutableMap<Int, QueueListEntry>,
    queueState: QueueUiState,
    listsState: EpisodeListsUiState,
    playbackState: ru.e1173.nookast.player.PlaybackUiState,
    wide: Boolean,
    largeControls: Boolean,
    navigator: AppNavigator,
    queueViewModel: QueueViewModel,
    playbackViewModel: ru.e1173.nookast.player.PlaybackViewModel,
) {
    // After process death the card looks for its episode in what the app keeps.
    val entry = cardEntries[episodeId]
        ?: queueState.entries.firstOrNull { it.episodeId == episodeId }
        ?: listsState.feedEntries.firstOrNull { it.episodeId == episodeId }
        ?: listsState.playlistEpisodes.values.asSequence().flatMap { it.episodes }.firstOrNull { it.episodeId == episodeId }
    if (entry == null) {
        androidx.compose.runtime.LaunchedEffect(episodeId) { navigator.popWhile { it == Route.Episode(episodeId) } }
        return
    }
    androidx.compose.runtime.LaunchedEffect(episodeId) { queueViewModel.loadPodcastDetails(episodeId) }
    val details = queueState.podcastDetails[episodeId]
    val saved = queueState.savedEpisodeOverrides[episodeId]
        ?: queueState.dashboard?.savedEpisodes?.any { it.episodeId == episodeId } ?: false
    // The freshest progress: the queue's row when the episode is queued.
    val shown = queueState.entries.firstOrNull { it.episodeId == episodeId }?.let { queued ->
        queued.copy(description = queued.description?.takeIf(String::isNotBlank) ?: entry.description)
    } ?: entry
    val isCurrent = playbackState.currentEpisodeId == episodeId
    val description = shown.descriptionOrCached(details)
    val resources = androidx.compose.ui.platform.LocalResources.current
    val feedChapters by androidx.compose.runtime.produceState(emptyList<ru.e1173.nookast.player.PlaybackChapter>(), episodeId) {
        value = playbackViewModel.episodeChapters(episodeId)
    }
    // The one that plays also knows the chapters embedded in its file.
    val sourceChapters = when {
        isCurrent -> playbackState.feedChapters.ifEmpty { playbackState.nativeChapters }.ifEmpty { feedChapters }
        else -> feedChapters
    }
    val chapters = remember(sourceChapters, description, resources) { episodeChapterList(sourceChapters, description, resources) }
    val livePosition = LocalPlaybackPosition.current
    val moreEpisodes = remember(details, episodeId) {
        val episodes = details?.recentEpisodes.orEmpty()
        val self = episodes.indexOfFirst { it.episodeId == episodeId }
        // The episodes after this one in the feed first, then the newer ones.
        (episodes.drop(self + 1) + episodes.take(self.coerceAtLeast(0)))
            .filter { it.episodeId != episodeId }
            .take(MORE_EPISODES)
            .mapIndexed { index, episode -> episode.toQueueEntry(index, details?.name.orEmpty(), details?.artworkUrl.orEmpty()) }
    }
    EpisodeCardScreen(
        entry = shown,
        podcastDetails = details,
        isCurrent = isCurrent,
        chapters = chapters,
        sourceChapters = sourceChapters,
        positionMs = { livePosition?.value?.takeIf { it.episodeId == episodeId }?.positionMs ?: playbackState.positionMs },
        moreEpisodes = moreEpisodes,
        isPlaying = playbackState.isPlaying,
        inQueue = queueState.entries.any { it.episodeId == episodeId },
        saved = saved,
        downloadState = queueState.downloadStates[episodeId],
        downloadProgress = queueState.downloadProgress[episodeId],
        wide = wide,
        largeControls = largeControls,
        actions = EpisodeCardActions(
            onBack = { navigator.pop() },
            onPlay = {
                queueViewModel.addStartedEpisodeToQueue(shown)
                playbackViewModel.playQueue(listOf(shown), 0)
            },
            onTogglePlayback = playbackViewModel::togglePlayPause,
            onAddToQueue = { queueViewModel.addEpisodeToQueue(shown) },
            onRemoveFromQueue = { queueViewModel.removeFromQueue(episodeId) },
            onDownload = { queueViewModel.downloadEpisode(shown) },
            onDeleteDownload = { queueViewModel.removeEpisodeDownload(episodeId) },
            onToggleSaved = { queueViewModel.toggleSavedEpisode(episodeId = episodeId, currentlySaved = saved) },
            onMarkPlayed = { queueViewModel.markEpisodePlayed(shown) },
            onMarkUnplayed = { queueViewModel.markEpisodesUnplayed(listOf(shown)) },
            onOpenPodcast = {
                val podcastId = details?.podcastId ?: queueState.podcasts.firstOrNull {
                    it.name.trim().equals(shown.podcastName.orEmpty().trim(), ignoreCase = true)
                }?.podcastId
                if (podcastId != null) {
                    navigator.showTab(AppDestination.PODCASTS)
                    if (wide) navigator.replaceTop(Route.Podcast(podcastId)) else navigator.push(Route.Podcast(podcastId))
                    queueViewModel.loadPodcastDetailsById(podcastId)
                }
            },
            onPlayFrom = { positionMs ->
                if (isCurrent) {
                    playbackViewModel.seekTo(positionMs)
                    if (!playbackState.isPlaying) playbackViewModel.togglePlayPause()
                } else {
                    queueViewModel.addStartedEpisodeToQueue(shown)
                    playbackViewModel.playQueue(listOf(shown), 0, startPositionMs = positionMs)
                }
            },
            onOpenEpisode = { episode ->
                cardEntries[episode.episodeId] = episode
                queueViewModel.loadPodcastDetails(episode.episodeId)
                navigator.push(Route.Episode(episode.episodeId))
            },
        ),
    )
}
