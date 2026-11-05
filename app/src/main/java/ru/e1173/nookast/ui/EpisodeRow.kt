package ru.e1173.nookast.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.e1173.nookast.R
import ru.e1173.nookast.designsystem.LocalNookastColors
import ru.e1173.nookast.designsystem.NookIcons
import ru.e1173.nookast.designsystem.NookMiniPlayButton

/** One item of an episode row's "⋯" menu. */
internal data class EpisodeMenuItem(val label: String, val onClick: () -> Unit)

/**
 * The one episode row of the app (user, 29.10.2026): every list takes the Dashboard's «Continue
 * listening» row as its template — [EpisodeStackText] with the season, number and date on top, the
 * title in two lines and the time left with the download mark — and only adds or leaves out the
 * cover ([showArtwork]: Dashboard, smart playlists, queue) and the Play and "⋯" controls
 * ([showControls]: podcast pages, Feed, smart playlists). [meta] is the quiet top line. Without
 * controls the menu opens on a long press; in selection mode the controls become a check mark.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EpisodeRow(
    title: String,
    meta: String,
    artworkUrl: String,
    podcastName: String?,
    durationSeconds: Int?,
    listenedSeconds: Double?,
    completed: Boolean,
    downloaded: Boolean,
    downloading: Boolean,
    downloadProgress: Float?,
    playing: Boolean,
    largeControls: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    menu: List<EpisodeMenuItem>,
    modifier: Modifier = Modifier,
    showArtwork: Boolean = false,
    showControls: Boolean = true,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onLongPress: (() -> Unit)? = null,
) {
    val colors = LocalNookastColors.current
    val scale = if (largeControls) 4f / 3f else 1f
    // Touch targets grow by a third for the car; text only a little, so a row stays one glance.
    val textScale = if (largeControls) 1.12f else 1f
    val timing = episodeTiming(durationSeconds, listenedSeconds, completed)
    val shape = RoundedCornerShape(16.dp * scale)
    val background by animateColorAsState(
        if (selected) colors.tintContainer.compositeOver(colors.surface.compositeOver(colors.background))
        else colors.surface.compositeOver(colors.background),
        tween(180),
        label = "episodeRowBackground",
    )
    val trailing = selectionMode || showControls
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(background)
                .border(1.dp, if (selected) colors.accent.copy(alpha = 0.6f) else colors.divider.copy(alpha = 0.7f), shape)
                .focusRing(shape)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = androidx.compose.ui.semantics.Role.Button,
                    onClick = onOpen,
                    onLongClick = onLongPress ?: if (!showControls && menu.isNotEmpty()) ({ menuOpen = true }) else null,
                )
                .padding(
                    start = (if (showArtwork) 6.dp else 14.dp) * scale,
                    end = (if (trailing) 2.dp else 10.dp) * scale,
                    top = 5.dp * scale,
                    bottom = 5.dp * scale,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showArtwork) {
                Artwork(
                    url = artworkUrl,
                    title = title,
                    fallbackUrl = podcastArtworkFallback(podcastName),
                    downloaded = false,
                    badgeSize = 0.dp,
                    cornerRadius = 12.dp * scale,
                    decodeSize = 56.dp * scale,
                    modifier = Modifier.size(56.dp * scale),
                )
                Spacer(Modifier.size(10.dp * scale))
            }
            // The text gives way to the controls: it narrows, it is never cut by them.
            EpisodeStackText(
                meta = meta,
                title = title,
                timeText = timing.text,
                started = timing.started,
                completed = completed,
                downloaded = downloaded,
                downloading = downloading,
                downloadProgress = downloadProgress,
                textScale = textScale,
                progress = timing.progress,
                modifier = Modifier.weight(1f),
            )
            if (trailing) Spacer(Modifier.size(6.dp))
            if (selectionMode) {
                Box(
                    Modifier
                        .size(48.dp * scale)
                        .clip(CircleShape)
                        .combinedClickable(role = androidx.compose.ui.semantics.Role.Checkbox, onClick = onOpen),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(22.dp * scale)
                            .background(if (selected) colors.accent else Color.Transparent, CircleShape)
                            .border(1.5.dp, if (selected) colors.accent else colors.textSecondary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(NookIcons.CheckBold, null, tint = colors.onAccent, modifier = Modifier.size(12.dp * scale))
                    }
                }
            } else if (showControls) {
                NookMiniPlayButton(
                    playing = playing,
                    contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
                    onClick = onPlay,
                    size = 38.dp * scale,
                )
                EpisodeMoreButton(menu, scale)
            }
        }
        if (!showControls) EpisodeMenu(menu, menuOpen, onDismiss = { menuOpen = false })
    }
}

/**
 * The text column of an episode row. Every line is always laid out — an empty subtitle, no time
 * and no progress keep their space — so all rows of a list have one height (user, 27.10.2026).
 */
@Composable
internal fun EpisodeRowText(
    title: String,
    subtitle: String?,
    timeText: String?,
    started: Boolean,
    completed: Boolean,
    progress: Float,
    downloaded: Boolean,
    downloading: Boolean,
    downloadProgress: Float?,
    textScale: Float,
    modifier: Modifier = Modifier,
    titleLines: Int = 1,
) {
    val colors = LocalNookastColors.current
    // Car (user, 28.10.2026): the whole title should fit, so the large profile gives it two lines
    // and shrinks the type down to 12 sp before an ellipsis; the row height stays the same.
    val fitTitle = textScale > 1f
    Column(modifier) {
        Text(
            cleanEpisodeTitle(title).composed(),
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.5.sp * textScale, lineHeight = 18.sp * textScale),
            fontWeight = FontWeight.SemiBold,
            color = if (completed) colors.textSecondary else colors.textPrimary,
            minLines = titleLines,
            maxLines = titleLines,
            overflow = TextOverflow.Ellipsis,
            autoSize = if (fitTitle) {
                androidx.compose.foundation.text.TextAutoSize.StepBased(
                    minFontSize = 12.sp * textScale,
                    maxFontSize = 14.5.sp * textScale,
                    stepSize = 0.5.sp,
                )
            } else {
                null
            },
        )
        Text(
            subtitle.orEmpty(),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp * textScale, lineHeight = 15.sp * textScale),
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            Modifier.padding(top = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                timeText.orEmpty(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp * textScale, lineHeight = 14.sp * textScale),
                color = if (started) colors.accent else colors.textSecondary,
                fontWeight = if (started) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
            EpisodeDownloadMark(downloaded, downloading, downloadProgress, 12.dp * textScale)
        }
        // The progress hairline's slot exists for every row; only started episodes fill it.
        Box(
            Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .height(3.dp)
                .background(if (started && progress > 0f) colors.track else Color.Transparent, CircleShape),
        ) {
            if (started && progress > 0f) {
                Box(Modifier.fillMaxWidth(progress).height(3.dp).background(colors.trackFill, CircleShape))
            }
        }
    }
}

/**
 * The stacked text of the Dashboard and queue rows, one design for both (user, 29.10.2026): a quiet
 * line with season, episode and date; the title in a two-line slot, centred when it takes one
 * line; then the time left with the download mark. [progress] adds the Dashboard's hairline;
 * null leaves it out (the queue).
 */
@Composable
internal fun EpisodeStackText(
    meta: String,
    title: String,
    timeText: String?,
    started: Boolean,
    completed: Boolean,
    downloaded: Boolean,
    downloading: Boolean,
    downloadProgress: Float?,
    textScale: Float,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    timeColor: Color? = null,
) {
    val colors = LocalNookastColors.current
    val titleStyle = MaterialTheme.typography.titleSmall.copy(fontSize = 14.5.sp * textScale, lineHeight = 18.sp * textScale)
    val titleSlot = with(LocalDensity.current) { (titleStyle.lineHeight * 2).toDp() }
    Column(modifier) {
        Text(
            meta,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp * textScale, lineHeight = 14.sp * textScale),
            color = colors.textSecondary.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(Modifier.fillMaxWidth().heightIn(min = titleSlot), contentAlignment = Alignment.CenterStart) {
            Text(
                cleanEpisodeTitle(title).composed(),
                style = titleStyle,
                fontWeight = FontWeight.SemiBold,
                color = if (completed) colors.textSecondary else colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                timeText.orEmpty(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp * textScale, lineHeight = 14.sp * textScale),
                color = timeColor ?: if (started) colors.accent else colors.textSecondary,
                fontWeight = if (started || timeColor != null) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
            EpisodeDownloadMark(downloaded, downloading, downloadProgress, 12.dp * textScale)
        }
        if (progress != null) {
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(if (started && progress > 0f) colors.track else Color.Transparent, CircleShape),
            ) {
                if (started && progress > 0f) {
                    Box(Modifier.fillMaxWidth(progress).height(3.dp).background(colors.trackFill, CircleShape))
                }
            }
        }
    }
}

/** What an episode row or tile says about time: progress, whether started, the compact label. */
internal data class EpisodeTiming(val progress: Float, val started: Boolean, val text: String?)

@Composable
internal fun episodeTiming(durationSeconds: Int?, listenedSeconds: Double?, completed: Boolean): EpisodeTiming {
    val duration = durationSeconds?.takeIf { it > 0 }
    val listened = listenedSeconds?.takeIf { it > 0 } ?: 0.0
    val progress = if (duration != null) (listened / duration).toFloat().coerceIn(0f, 1f) else 0f
    val started = listened > 0 && !completed
    // Like the queue: time left once started, the length otherwise; nothing for an unknown length.
    val text = when {
        completed -> stringResource(R.string.played)
        duration == null -> null
        started -> minutesLabel(kotlin.math.ceil((duration - listened).coerceAtLeast(0.0) / 60.0).toInt().coerceAtLeast(1))
        else -> minutesLabel((duration / 60).coerceAtLeast(1))
    }
    return EpisodeTiming(progress, started, text)
}

/** The "⋯" button of an episode row or tile with its menu. */
@Composable
internal fun EpisodeMoreButton(menu: List<EpisodeMenuItem>, scale: Float) {
    val colors = LocalNookastColors.current
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        val moreLabel = stringResource(R.string.episode_more_actions)
        Box(
            Modifier
                // 48 dp tall: the smallest comfortable target (plan 5.5).
                .size(width = 34.dp * scale, height = 48.dp * scale)
                .clip(CircleShape)
                .combinedClickable(role = Role.Button, onClick = { menuOpen = true })
                .semantics { contentDescription = moreLabel },
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
                repeat(3) { Box(Modifier.size(3.5.dp * scale).background(colors.textSecondary, CircleShape)) }
            }
        }
        EpisodeMenu(menu, menuOpen, onDismiss = { menuOpen = false })
    }
}

/** The menu of an episode row, from its "⋯" button or a long press. */
@Composable
internal fun EpisodeMenu(menu: List<EpisodeMenuItem>, expanded: Boolean, onDismiss: () -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 230.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        ru.e1173.nookast.designsystem.InterfaceScaled {
            menu.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    onClick = {
                        onDismiss()
                        item.onClick()
                    },
                )
            }
        }
    }
}

/** The queue's download mark: a green disc with an arrow when on the device, a ring while loading. */
@Composable
internal fun EpisodeDownloadMark(
    downloaded: Boolean,
    downloading: Boolean,
    progress: Float?,
    size: androidx.compose.ui.unit.Dp,
) {
    val colors = LocalNookastColors.current
    when {
        downloaded -> Box(
            Modifier.size(size).background(colors.success, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            DownloadGlyph(Modifier.size(size * 0.56f), color = Color.White)
        }
        downloading -> CircularProgressIndicator(
            progress = { progress?.coerceIn(0f, 1f) ?: 0f },
            modifier = Modifier.size(size),
            strokeWidth = 1.5.dp,
            color = colors.success,
            trackColor = colors.divider,
        )
    }
}

/** The standard "⋯" menu of an episode: add to the queue, mark played, download or delete. */
@Composable
internal fun standardEpisodeMenu(
    downloaded: Boolean,
    completed: Boolean,
    onAddToQueue: () -> Unit,
    onMarkPlayed: () -> Unit,
    onMarkUnplayed: (() -> Unit)?,
    onDownload: () -> Unit,
    onDeleteDownload: (() -> Unit)?,
): List<EpisodeMenuItem> = buildList {
    add(EpisodeMenuItem(stringResource(R.string.add_to_queue), onAddToQueue))
    if (completed && onMarkUnplayed != null) {
        add(EpisodeMenuItem(stringResource(R.string.mark_unplayed), onMarkUnplayed))
    } else {
        add(EpisodeMenuItem(stringResource(R.string.mark_played_action), onMarkPlayed))
    }
    if (downloaded && onDeleteDownload != null) {
        add(EpisodeMenuItem(stringResource(R.string.delete_download), onDeleteDownload))
    } else if (!downloaded) {
        add(EpisodeMenuItem(stringResource(R.string.download_episode), onDownload))
    }
}

/**
 * The tile form of [EpisodeRow] for the Feed and smart playlists (user, 27.10.2026), in the spirit
 * of the podcast grid: the cover on top with the shared Play disc on its corner, then the same
 * text column with a two-line title. Every slot is reserved, so all tiles of a grid match.
 */
@Composable
internal fun EpisodeTile(
    title: String,
    subtitle: String?,
    artworkUrl: String,
    podcastName: String?,
    durationSeconds: Int?,
    listenedSeconds: Double?,
    completed: Boolean,
    downloaded: Boolean,
    downloading: Boolean,
    downloadProgress: Float?,
    playing: Boolean,
    largeControls: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    menu: List<EpisodeMenuItem>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNookastColors.current
    val scale = if (largeControls) 4f / 3f else 1f
    val textScale = if (largeControls) 1.12f else 1f
    val timing = episodeTiming(durationSeconds, listenedSeconds, completed)
    val shape = RoundedCornerShape(18.dp * scale)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface.compositeOver(colors.background))
            .border(1.dp, colors.divider.copy(alpha = 0.7f), shape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpen,
            )
            .padding(8.dp * scale),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Artwork(
                url = artworkUrl,
                title = title,
                fallbackUrl = podcastArtworkFallback(podcastName),
                downloaded = false,
                badgeSize = 0.dp,
                cornerRadius = 12.dp * scale,
                modifier = Modifier.fillMaxSize(),
            )
            NookMiniPlayButton(
                playing = playing,
                contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
                onClick = onPlay,
                size = 40.dp * scale,
                modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp),
            )
        }
        Row(Modifier.padding(top = 8.dp * scale), verticalAlignment = Alignment.Top) {
            EpisodeRowText(
                title = title,
                subtitle = subtitle,
                timeText = timing.text,
                started = timing.started,
                completed = completed,
                progress = timing.progress,
                downloaded = downloaded,
                downloading = downloading,
                downloadProgress = downloadProgress,
                textScale = textScale,
                titleLines = 2,
                modifier = Modifier.weight(1f).padding(start = 2.dp),
            )
            EpisodeMoreButton(menu, scale)
        }
    }
}

/**
 * Feeds sometimes send «й» as «и» + a combining breve; Manrope has no mark positioning, so the
 * breve drifted beside the letter (user, 28.10.2026: «про̆йти»). NFC composes it into one glyph.
 */
internal fun String.composed(): String =
    if (java.text.Normalizer.isNormalized(this, java.text.Normalizer.Form.NFC)) this
    else java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFC)
