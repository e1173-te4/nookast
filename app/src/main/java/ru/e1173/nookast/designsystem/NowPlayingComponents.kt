package ru.e1173.nookast.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * Now Playing components of the approved mockup np-playing-remote-healthy-b (D3). Sizes are the
 * mockup's CSS px = dp at the 346 dp reference width; [scale] enlarges a component for the car
 * profile without changing its proportions. Colours come from the theme tokens only.
 */

private val nook: NookastColors @Composable get() = LocalNookastColors.current

/** Tactile press (DEC-024, §2.2): scale 0.91 on a spring, stiffness 380, damping 0.82. */
@Composable
private fun pressScale(interaction: MutableInteractionSource, @Suppress("UNUSED_PARAMETER") legacy: Float = 0.91f): Float {
    val isPressed by interaction.collectIsPressedAsState()
    val value by animateFloatAsState(
        if (isPressed) PRESSED_SCALE else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.82f, stiffness = 380f),
        label = "press",
    )
    return value
}

private const val PRESSED_SCALE = 0.91f

/** Seek button: 64 dp disc with the shared seek icon of the interval ([seekIconRes]). */
@Composable
fun NookSeekButton(
    seconds: Int,
    forward: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    val colors = nook
    val interaction = remember { MutableInteractionSource() }
    val pressed = pressScale(interaction, 0.9f)
    // DEC-024: 64 dp disc, arc glyph scaled with it (51 dp), number 14 sp.
    val size = 64.dp * scale
    Box(
        modifier
            .size(size)
            .scale(pressed)
            .drawBehind {
                // Night reference (Stitch export, 26.10.2026): dark glass, no accent glow —
                // box-shadow 0 4px 16px rgba(0,0,0,.4) and a 1 px light edge on top.
                if (colors.isDark) drawCssShadow(Color.Black.copy(alpha = 0.40f), 16.dp, this.size.minDimension / 2f, dy = 4.dp)
            }
            .shadow(if (colors.isDark) 0.dp else 6.dp * scale, CircleShape, ambientColor = colors.accent.copy(alpha = 0.10f), spotColor = colors.accent.copy(alpha = 0.10f))
            .background(colors.seekSurface, CircleShape)
            .drawBehind { if (colors.isDark) drawGlassHighlight() }
            .border(1.dp, if (colors.isDark) colors.divider.copy(alpha = 0.9f) else colors.surfaceOutline, CircleShape)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        // The same drawable as the notification and the mini-player (user: identical everywhere).
        Icon(
            androidx.compose.ui.res.painterResource(seekIconRes(forward, seconds)),
            contentDescription = null,
            tint = colors.textPrimary,
            modifier = Modifier.size(51.dp * scale),
        )
    }
}

/**
 * Play/Pause: 90 dp disc with the accent gradient (135°) and its coloured shadow; in dark themes
 * a sharper glow under it instead (DEC-021).
 */
@Composable
fun NookPlayPauseButton(
    playing: Boolean,
    buffering: Boolean,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    val colors = nook
    val interaction = remember { MutableInteractionSource() }
    val pressed = pressScale(interaction, 0.95f)
    // DEC-024: 80 dp (the mockups had 90).
    val size = 80.dp * scale
    val glowIntensity by animateFloatAsState(if (playing) 1f else 0.55f, tween(240), label = "playGlow")
    Box(
        modifier
            .size(size)
            .scale(pressed)
            .drawBehind {
                if (colors.isDark) {
                    // Night reference: box-shadow 0 0 32px accent .65, 0 0 64px accentStrong .40,
                    // 0 8px 24px black .5 — a tight bright aura, not a wide wash. Dimmer when paused.
                    val r = this.size.minDimension / 2f
                    val k = glowIntensity
                    drawCssShadow(Color.Black.copy(alpha = 0.5f), 24.dp, r, dy = 8.dp)
                    drawCssShadow(colors.accentStrong.copy(alpha = 0.40f * k), 64.dp, r)
                    drawCssShadow(colors.accent.copy(alpha = 0.65f * k), 32.dp, r)
                }
            }
            .shadow(
                if (colors.isDark) 0.dp else 14.dp * scale,
                CircleShape,
                ambientColor = colors.accent.copy(alpha = 0.45f),
                spotColor = colors.accent.copy(alpha = 0.45f),
            )
            .background(
                if (colors.isDark) SolidColor(colors.accent) else Brush.linearGradient(listOf(colors.accent, colors.accentStrong)),
                CircleShape,
            )
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        when {
            buffering -> CircularProgressIndicator(
                color = colors.onAccent,
                strokeWidth = 3.dp * scale,
                modifier = Modifier.size(40.dp * scale),
            )
            else -> PlayPauseGlyph(playing, colors.onAccent, size)
        }
    }
}

/**
 * The Now Playing Play/Pause made small for lists (user, 27.10.2026): the same accent disc and
 * glyphs, the gradient by day and the tight aura at night, scaled to [size].
 */
@Composable
fun NookMiniPlayButton(
    playing: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
) {
    val colors = nook
    val interaction = remember { MutableInteractionSource() }
    val pressed = pressScale(interaction)
    val k = size / 80.dp
    Box(
        modifier
            .size(maxOf(size, 44.dp))
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .scale(pressed)
                .drawBehind {
                    if (colors.isDark) {
                        val r = this.size.minDimension / 2f
                        drawCssShadow(colors.accent.copy(alpha = if (playing) 0.55f else 0.35f), 32.dp * k * 1.4f, r)
                    }
                }
                .shadow(if (colors.isDark) 0.dp else 6.dp, CircleShape, ambientColor = colors.accent.copy(alpha = 0.35f), spotColor = colors.accent.copy(alpha = 0.35f))
                .background(
                    if (colors.isDark) SolidColor(colors.accent) else Brush.linearGradient(listOf(colors.accent, colors.accentStrong)),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            PlayPauseGlyph(playing, colors.onAccent, size)
        }
    }
}

/**
 * The one Play/Pause glyph of the app (user, 27.10.2026): every play disc — Now Playing, the
 * mini-player, episode rows and tiles — draws exactly this, scaled to its [disc] diameter.
 */
@Composable
fun PlayPauseGlyph(playing: Boolean, color: Color, disc: Dp) {
    val k = disc / 80.dp
    if (playing) {
        Icon(NookIcons.Pause, null, tint = color, modifier = Modifier.size(36.dp * k))
    } else {
        Icon(NookIcons.Play, null, tint = color, modifier = Modifier.padding(start = 4.dp * k).size(40.dp * k))
    }
}

/** Transport row: seek back, Play, seek forward with 24 dp gaps. */
@Composable
fun NookTransport(
    playing: Boolean,
    buffering: Boolean,
    enabled: Boolean,
    seekBackSeconds: Int,
    seekForwardSeconds: Int,
    labels: TransportLabels,
    onSeekBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(24.dp * scale),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NookSeekButton(seekBackSeconds, forward = false, labels.seekBack, onSeekBack, scale = scale)
        NookPlayPauseButton(playing, buffering, enabled, if (playing) labels.pause else labels.play, onPlayPause, scale = scale)
        NookSeekButton(seekForwardSeconds, forward = true, labels.seekForward, onSeekForward, scale = scale)
    }
}

data class TransportLabels(val seekBack: String, val seekForward: String, val play: String, val pause: String)

/**
 * Timeline (thumb spec, Stitch 26.10.2026): 4 dp track split by 4 dp chapter gaps, segments laid
 * out at their real time positions so the fill ends exactly at the thumb's centre and never
 * inside a gap. Tap seeks at once; a drag moves the thumb with the finger and seeks once on
 * release. At night the fill glows (0 0 6px accent .5) and the track has a faint outline.
 */
@Composable
fun NookTimeline(
    positionMs: Long,
    durationMs: Long,
    chapterStartsMs: List<Long>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    // False shows the position but takes no touch: a preview of an episode that is not playing.
    enabled: Boolean = true,
) {
    val colors = nook
    val palette = LocalThemePalette.current
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    val fraction = when {
        dragFraction >= 0f -> dragFraction
        durationMs > 0 -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        else -> 0f
    }
    val segments = remember(chapterStartsMs, durationMs) { timelineSegments(chapterStartsMs, durationMs) }
    val boundaries = remember(segments) { segments.drop(1).map { it.first } }
    // Scrubbing (user, 27.10.2026): the thumb sticks to chapter boundaries, each boundary crossed
    // ticks the vibration motor, and a time bubble above the finger shows where it is going.
    val view = androidx.compose.ui.platform.LocalView.current
    var dragSegment by remember { mutableIntStateOf(-1) }
    fun dragTo(x: Float, width: Int) {
        val snapFraction = TIMELINE_SNAP_DP * view.resources.displayMetrics.density * scale / width
        val next = snapToChapterBoundary((x / width).coerceIn(0f, 1f), boundaries, snapFraction)
        val segment = boundaries.count { it <= next }
        if (dragSegment >= 0 && segment != dragSegment) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        }
        dragSegment = segment
        dragFraction = next
    }
    // TalkBack reads the position and moves it in steps (U-04, plan 5.5).
    val timelineLabel = androidx.compose.ui.res.stringResource(ru.e1173.nookast.R.string.timeline_position)
    val timelineValue = "${formatScrubTime(positionMs)} / ${formatScrubTime(durationMs)}"
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(24.dp * scale)
            .then(if (enabled) Modifier else Modifier.alpha(0.45f))
            .semantics {
                contentDescription = timelineLabel
                stateDescription = timelineValue
                progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(fraction, 0f..1f, steps = 0)
                if (enabled && durationMs > 0) {
                    setProgress { target ->
                        onSeek((target.coerceIn(0f, 1f) * durationMs).toLong())
                        true
                    }
                }
            }
            .pointerInput(durationMs, enabled) {
                if (!enabled || durationMs <= 0) return@pointerInput
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f).times(durationMs).toLong())
                }
            }
            .pointerInput(durationMs, boundaries, enabled) {
                if (!enabled || durationMs <= 0) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragSegment = -1
                        dragTo(it.x, size.width)
                    },
                    onDragEnd = {
                        onSeek((dragFraction * durationMs).toLong())
                        dragFraction = -1f
                    },
                    onDragCancel = { dragFraction = -1f },
                ) { change, _ -> dragTo(change.position.x, size.width) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (dragFraction >= 0f && durationMs > 0) {
            ScrubTimeBubble(
                text = formatScrubTime((dragFraction * durationMs).toLong()),
                anchorX = maxWidth * dragFraction,
                scale = scale,
            )
        }
        val track = 4.dp * scale
        Canvas(Modifier.fillMaxWidth().height(track)) {
            val gapPx = 4.dp.toPx() * scale
            val w = size.width
            val h = size.height
            val thumbX = fraction * w
            val radius = androidx.compose.ui.geometry.CornerRadius(h / 2f)
            segments.forEachIndexed { index, (start, end) ->
                val startPx = start * w + if (index == 0) 0f else gapPx / 2f
                val endPx = end * w - if (index == segments.lastIndex) 0f else gapPx / 2f
                if (endPx <= startPx) return@forEachIndexed
                val segment = androidx.compose.ui.geometry.Size(endPx - startPx, h)
                drawRoundRect(colors.track, Offset(startPx, 0f), segment, radius)
                if (colors.isDark) {
                    drawRoundRect(
                        colors.divider.copy(alpha = 0.5f),
                        Offset(startPx, 0f),
                        segment,
                        radius,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(0.75.dp.toPx()),
                    )
                }
                if (thumbX > startPx) {
                    val filled = androidx.compose.ui.geometry.Size(thumbX.coerceAtMost(endPx) - startPx, h)
                    if (colors.isDark) drawGlowBar(colors.trackFill.copy(alpha = 0.5f), Offset(startPx, 0f), filled, 6.dp)
                    drawRoundRect(colors.trackFill, Offset(startPx, 0f), filled, radius)
                }
            }
        }
        val box = thumbBox(palette) * scale
        NookTimelineThumb(
            palette = palette,
            scale = scale,
            active = dragFraction >= 0f,
            modifier = Modifier.offset(x = maxWidth * fraction - box / 2),
        )
    }
}

/**
 * A chapter boundary within [threshold] of [fraction] pulls the thumb onto itself. The very start
 * pulls too, and wins over a boundary as near (user, 9.11.2026: a short intro chapter made 00:00
 * unreachable, the thumb kept landing on its end).
 */
internal fun snapToChapterBoundary(fraction: Float, boundaries: List<Float>, threshold: Float): Float {
    if (fraction <= threshold) return 0f
    val nearest = boundaries.minByOrNull { kotlin.math.abs(it - fraction) } ?: return fraction
    return if (kotlin.math.abs(nearest - fraction) <= threshold) nearest else fraction
}

internal fun formatScrubTime(positionMs: Long): String {
    val total = (positionMs / 1_000).coerceAtLeast(0)
    val hours = total / 3_600
    val minutes = total % 3_600 / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(java.util.Locale.ROOT, hours, minutes, seconds)
    } else {
        "%d:%02d".format(java.util.Locale.ROOT, minutes, seconds)
    }
}

private const val TIMELINE_SNAP_DP = 10f

/** The scrub position above the finger, which itself covers the thumb (user, 27.10.2026). */
@Composable
private fun ScrubTimeBubble(text: String, anchorX: Dp, scale: Float) {
    val colors = nook
    val density = LocalDensity.current
    val lift = with(density) { (44.dp * scale).roundToPx() }
    val x = with(density) { anchorX.roundToPx() }
    androidx.compose.ui.window.Popup(
        popupPositionProvider = object : androidx.compose.ui.window.PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: androidx.compose.ui.unit.IntRect,
                windowSize: androidx.compose.ui.unit.IntSize,
                layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                popupContentSize: androidx.compose.ui.unit.IntSize,
            ): androidx.compose.ui.unit.IntOffset = androidx.compose.ui.unit.IntOffset(
                (anchorBounds.left + x - popupContentSize.width / 2)
                    .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
                (anchorBounds.top - lift).coerceAtLeast(0),
            )
        },
    ) {
        InterfaceScaled { Text(
            text,
            style = TextStyle(
                fontFamily = Manrope,
                fontSize = 15.sp * scale,
                fontWeight = FontWeight(700),
                fontFeatureSettings = "tnum",
                color = colors.textPrimary,
            ),
            modifier = Modifier
                .background(colors.surface.copy(alpha = 1f), RoundedCornerShape(10.dp * scale))
                .border(1.dp, colors.trackFill.copy(alpha = 0.6f), RoundedCornerShape(10.dp * scale))
                .padding(horizontal = 10.dp * scale, vertical = 4.dp * scale),
        ) }
    }
}

/** Segment bounds as fractions of the duration; one segment without chapters. */
internal fun timelineSegments(chapterStartsMs: List<Long>, durationMs: Long): List<Pair<Float, Float>> {
    if (durationMs <= 0) return listOf(0f to 1f)
    val starts = (listOf(0L) + chapterStartsMs.filter { it in 1 until durationMs }).distinct().sorted()
    // A chapter shorter than 1 % of the episode would be a dot: merge it into the previous one.
    val kept = starts.filterIndexed { index, start -> index == 0 || start - starts[index - 1] >= durationMs / 100 }
    return kept.mapIndexed { index, start ->
        start.toFloat() / durationMs to ((kept.getOrNull(index + 1) ?: durationMs).toFloat() / durationMs)
    }
}

/** Elapsed and remaining time under the timeline: 12/600, tabular figures. */
@Composable
fun NookTimecodes(
    elapsed: String,
    remaining: String,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    center: (@Composable () -> Unit)? = null,
) {
    val style = TextStyle(
        fontFamily = Manrope,
        fontSize = 12.sp * scale,
        fontWeight = FontWeight(600),
        lineHeight = 18.sp * scale,
        fontFeatureSettings = "tnum",
        color = nook.textSecondary,
    )
    // [center]: the source chip between the times, like Lossless in Apple Music (DEC-029). It is
    // centred on the screen, not between texts of different widths, and sits a little lower.
    Box(modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
        Text(elapsed, style = style, modifier = Modifier.align(Alignment.TopStart))
        Text(remaining, style = style, modifier = Modifier.align(Alignment.TopEnd))
        if (center != null) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp * scale)) { center() }
        }
    }
}

/**
 * Chapter pill: surface card, radius 12, prev/next chevrons in accent, title 12.5/500 that slides
 * ±16 dp in the paging direction. [placeholder] ("no chapters") is muted; no dot before the title
 * (user, APK-3.3). In dark themes Play lights it from above, as in the night effects reference
 * (Stitch export, 26.10.2026): a narrow radial beam from 12 dp above the card, an accent top edge,
 * light spilling upwards and a dark drop shadow; full while playing, half when paused.
 */
@Composable
fun NookChapterPill(
    title: String,
    previousLabel: String,
    nextLabel: String,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    modifier: Modifier = Modifier,
    placeholder: Boolean = false,
    playing: Boolean = false,
    chapterIndex: Int = 0,
    scale: Float = 1f,
) {
    val colors = nook
    val shiftPx = with(androidx.compose.ui.platform.LocalDensity.current) { 16.dp.roundToPx() }
    val shape = RoundedCornerShape(12.dp * scale)
    val intensity by animateFloatAsState(
        // Spec says 25 % when paused; at 25 % the ray was invisible on the phone (APK-3.4 review).
        if (playing) 1f else 0.5f,
        tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "focalRay",
    )
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                // Night reference: box-shadow 0 -2px 14px accent .22 (Play's light spilling up),
                // 0 4px 12px black .3.
                if (colors.isDark) {
                    val r = 12.dp.toPx() * scale
                    drawCssShadow(Color.Black.copy(alpha = 0.30f), 12.dp, r, dy = 4.dp)
                    drawCssShadow(colors.accent.copy(alpha = 0.22f * intensity), 14.dp, r, dy = (-2).dp)
                }
            }
            .clip(shape)
            .background(colors.surface)
            .drawWithCache {
                // radial-gradient(circle at 50% -12px, accent .32 0%, accent .08 45%, surface 85%):
                // a CSS circle without a size reaches the farthest corner, so the beam is about
                // as wide as half the card — narrower than the earlier 68 % of the width.
                val focus = Offset(size.width / 2f, -12.dp.toPx())
                val reach = kotlin.math.hypot(size.width / 2f, size.height + 12.dp.toPx())
                val cone = Brush.radialGradient(
                    0f to colors.accent.copy(alpha = 0.32f * intensity),
                    0.45f to colors.accent.copy(alpha = 0.08f * intensity),
                    0.85f to Color.Transparent,
                    center = focus,
                    radius = reach,
                )
                // border-top: 1px accent .7, the other sides the hairline outline.
                val edge = Brush.verticalGradient(
                    0f to colors.accent.copy(alpha = 0.70f * intensity),
                    0.35f to colors.divider,
                    1f to colors.divider,
                )
                onDrawBehind {
                    if (colors.isDark) {
                        drawRect(cone)
                        drawRoundRect(
                            edge,
                            topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx() * scale),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                        )
                    }
                }
            }
            .then(if (colors.isDark) Modifier else Modifier.border(1.dp, colors.surfaceOutline, shape))
            .padding(horizontal = 12.dp * scale, vertical = 8.dp * scale),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChevronButton(NookIcons.ChevronLeft, previousLabel, onPrevious, scale)
        Row(
            Modifier.weight(1f).padding(horizontal = 8.dp * scale),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = chapterIndex to title,
                transitionSpec = {
                    val forward = targetState.first >= initialState.first
                    val shift = if (forward) 16 else -16
                    val motion = androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.82f, stiffness = 380f)
                    (androidx.compose.animation.slideInHorizontally(motion) { if (shift > 0) shiftPx else -shiftPx } + fadeIn(tween(180))) togetherWith
                        (androidx.compose.animation.slideOutHorizontally(motion) { if (shift > 0) -shiftPx else shiftPx } + fadeOut(tween(120)))
                },
                label = "chapter",
            ) { (_, text) ->
                Text(
                    text,
                    style = TextStyle(fontFamily = Manrope, fontSize = 12.5.sp * scale, fontWeight = FontWeight(500), lineHeight = 16.sp * scale),
                    color = if (placeholder) colors.textSecondary else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        ChevronButton(NookIcons.ChevronRight, nextLabel, onNext, scale)
    }
}

@Composable
private fun ChevronButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: (() -> Unit)?, scale: Float) {
    val colors = nook
    val interaction = remember { MutableInteractionSource() }
    val pressed = pressScale(interaction, 0.75f)
    Box(
        Modifier
            .size(24.dp * scale)
            .scale(pressed)
            .clickable(interaction, indication = null, enabled = onClick != null, role = Role.Button) { onClick?.invoke() }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            null,
            tint = if (onClick != null) colors.accent else colors.accent.copy(alpha = 0.35f),
            modifier = Modifier.size(16.dp * scale),
        )
    }
}

enum class DockDownloadState { IDLE, DOWNLOADING, DOWNLOADED }

/**
 * Action dock: "Download" (idle → progress ring → done) and "Played", split by a 1 dp divider,
 * surface card radius 16, 36 dp buttons.
 */
@Composable
fun NookActionDock(
    downloadState: DockDownloadState,
    downloadProgress: Float?,
    downloadLabel: String,
    downloadEnabled: Boolean,
    playedLabel: String,
    played: Boolean,
    onDownload: () -> Unit,
    onPlayed: () -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    val colors = nook
    val shape = RoundedCornerShape(16.dp * scale)
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                // Night reference: box-shadow 0 -2px 16px accent .12, 0 4px 16px black .35.
                if (colors.isDark) {
                    val r = 16.dp.toPx() * scale
                    drawCssShadow(Color.Black.copy(alpha = 0.35f), 16.dp, r, dy = 4.dp)
                    drawCssShadow(colors.accent.copy(alpha = 0.12f), 16.dp, r, dy = (-2).dp)
                }
            }
            .shadow(if (colors.isDark) 0.dp else 4.dp, shape, spotColor = colors.accent.copy(alpha = 0.10f), ambientColor = colors.accent.copy(alpha = 0.05f))
            .clip(shape)
            .background(colors.surface)
            .drawWithCache {
                // radial-gradient(circle at 50% -16px, accent .20 0%, accent .03 60%, surface 100%)
                // and border-top accent .45: the second, weaker reflection of Play.
                val cone = Brush.radialGradient(
                    0f to colors.accent.copy(alpha = 0.20f),
                    0.60f to colors.accent.copy(alpha = 0.03f),
                    1f to Color.Transparent,
                    center = Offset(size.width / 2f, -16.dp.toPx()),
                    radius = kotlin.math.hypot(size.width / 2f, size.height + 16.dp.toPx()),
                )
                val edge = Brush.verticalGradient(
                    0f to colors.accent.copy(alpha = 0.45f),
                    0.35f to colors.divider,
                    1f to colors.divider,
                )
                onDrawBehind {
                    if (colors.isDark) {
                        drawRect(cone)
                        drawRoundRect(
                            edge,
                            topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx() * scale),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                        )
                    }
                }
            }
            .then(if (colors.isDark) Modifier else Modifier.border(1.dp, colors.surfaceOutline, shape))
            .padding(horizontal = 16.dp * scale, vertical = 10.dp * scale),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockButton(downloadLabel, downloadEnabled, onDownload, Modifier.weight(1f), scale) {
            Box(Modifier.size(24.dp * scale).background(colors.tintContainer, CircleShape), contentAlignment = Alignment.Center) {
                when (downloadState) {
                    DockDownloadState.IDLE -> Icon(NookIcons.Download, null, tint = colors.accent, modifier = Modifier.size(14.dp * scale))
                    DockDownloadState.DOWNLOADING -> if (downloadProgress == null) {
                        CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp * scale))
                    } else {
                        CircularProgressIndicator(
                            progress = { downloadProgress.coerceIn(0f, 1f) },
                            color = colors.accent,
                            trackColor = colors.accent.copy(alpha = 0.25f),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp * scale),
                        )
                    }
                    DockDownloadState.DOWNLOADED -> Icon(NookIcons.Check, null, tint = colors.accent, modifier = Modifier.size(14.dp * scale))
                }
            }
        }
        Box(Modifier.width(1.dp).height(24.dp * scale).background(colors.divider))
        DockButton(playedLabel, !played, onPlayed, Modifier.weight(1f), scale) {
            Box(
                Modifier
                    .size(20.dp * scale)
                    // Night reference: the confirmed check glows (0 0 8px accent .5).
                    .drawBehind { if (colors.isDark && played) drawCssShadow(colors.listened.copy(alpha = 0.5f), 8.dp, this.size.minDimension / 2f) }
                    .shadow(if (colors.isDark) 0.dp else 3.dp, CircleShape, spotColor = colors.listened.copy(alpha = 0.35f))
                    .background(if (played) colors.listened else colors.listened.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    NookIcons.CheckBold,
                    null,
                    tint = if (played) colors.onAccent else colors.listened,
                    modifier = Modifier.size(12.dp * scale),
                )
            }
        }
    }
}

@Composable
private fun DockButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    scale: Float,
    icon: @Composable () -> Unit,
) {
    val colors = nook
    Row(
        modifier
            .height(36.dp * scale)
            // The press shows in the button's own rounded shape, not as a box (user, 31.10.2026).
            .clip(RoundedCornerShape(12.dp * scale))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp * scale, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Text(
            label,
            style = TextStyle(fontFamily = Manrope, fontSize = 13.sp * scale, fontWeight = FontWeight(600), lineHeight = 19.5.sp * scale),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

enum class SourceHealth { HEALTHY, CHECKING, UNAVAILABLE, LOCAL }

/**
 * Sound quality word for a bitrate (DEC-029): SD below 96 kbps, HD up to 191, HQ from 192; null
 * when the bitrate is unknown.
 */
fun qualityLabel(bitrateKbps: Int?): String? = when {
    bitrateKbps == null || bitrateKbps <= 0 -> null
    bitrateKbps < 96 -> "SD"
    bitrateKbps < 192 -> "HD"
    else -> "HQ"
}

/**
 * Source chip under the timeline (DEC-029, replaces the badge over the artwork): a quiet capsule
 * like Apple Music's Lossless mark — status glyph (a softly glowing dot; a spinning ring while
 * checking or buffering; a check for a verified file on the device; coral when the source is
 * unreachable), the source word and the quality tag in a hairline frame. Tap opens the details.
 */
@Composable
fun NookSourceChip(
    label: String,
    quality: String?,
    health: SourceHealth,
    busy: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    // Replaces the status glyph, e.g. with the bars of another episode playing.
    glyph: (@Composable () -> Unit)? = null,
) {
    val colors = nook
    val coral = if (colors.isDark) Color(0xFFFF6E61) else Color(0xFFED6575)
    val tone = if (health == SourceHealth.UNAVAILABLE) coral else colors.textSecondary
    val interaction = remember { MutableInteractionSource() }
    val pressed = pressScale(interaction)
    Row(
        modifier
            .scale(pressed)
            .height(28.dp * scale)
            .background(colors.textSecondary.copy(alpha = if (colors.isDark) 0.12f else 0.09f), CircleShape)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = 11.dp * scale),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp * scale),
    ) {
        when {
            glyph != null -> glyph()
            busy || health == SourceHealth.CHECKING -> CircularProgressIndicator(
                color = colors.textSecondary,
                strokeWidth = 1.5.dp * scale,
                modifier = Modifier.size(11.dp * scale),
            )
            health == SourceHealth.LOCAL -> Icon(NookIcons.CheckBold, null, tint = colors.success, modifier = Modifier.size(12.dp * scale))
            else -> {
                val dot = if (health == SourceHealth.UNAVAILABLE) coral else colors.success
                Box(
                    Modifier
                        .size(7.dp * scale)
                        .drawBehind { drawGlowCircle(dot.copy(alpha = 0.7f), center, size.minDimension / 2f, 6.dp) }
                        .background(dot, CircleShape),
                )
            }
        }
        Text(
            label,
            style = TextStyle(fontFamily = Manrope, fontSize = 12.5.sp * scale, lineHeight = 16.sp * scale, fontWeight = FontWeight(600)),
            color = tone,
            maxLines = 1,
        )
        if (quality != null && health != SourceHealth.UNAVAILABLE) {
            Box(
                Modifier
                    .border(1.dp, tone.copy(alpha = 0.55f), RoundedCornerShape(3.dp * scale))
                    .padding(horizontal = 3.dp * scale),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    quality,
                    style = TextStyle(
                        fontFamily = Manrope,
                        fontSize = 9.5.sp * scale,
                        lineHeight = 13.sp * scale,
                        fontWeight = FontWeight(800),
                        letterSpacing = 0.04.em,
                    ),
                    color = tone,
                )
            }
        }
    }
}

/**
 * Source badge over the artwork: capsule, translucent surface, status dot and 11/700 text. Width
 * follows the text so it never overflows (DEC-022).
 */
@Composable
fun NookSourceBadge(
    label: String,
    health: SourceHealth,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    val colors = nook
    val dot = when (health) {
        SourceHealth.HEALTHY -> colors.success
        SourceHealth.LOCAL -> null // a verified file on the device needs no status dot
        SourceHealth.CHECKING -> colors.textSecondary
        SourceHealth.UNAVAILABLE -> Color(0xFFED6575)
    }
    // Night reference: rgba(14,12,22,.65) glass, 1 px white .16 rim, 0 2px 8px black .4.
    val background = if (colors.isDark) Color(0xA60E0C16) else Color(0xC7FFFFFF)
    Row(
        modifier
            .drawBehind { if (colors.isDark) drawCssShadow(Color.Black.copy(alpha = 0.4f), 8.dp, this.size.height / 2f, dy = 2.dp) }
            .background(background, CircleShape)
            .border(if (colors.isDark) 1.dp else 0.75.dp, if (colors.isDark) Color(0x29FFFFFF) else Color(0xE6FFFFFF), CircleShape)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = 8.dp * scale, vertical = 4.dp * scale),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp * scale),
    ) {
        if (dot != null) {
            Box(
                Modifier
                    .size(8.dp * scale)
                    .drawBehind {
                        if (colors.isDark) drawGlowCircle(dot.copy(alpha = 0.8f), center, this.size.minDimension / 2f - 1.dp.toPx(), 6.dp)
                        else drawCircle(dot.copy(alpha = 0.35f))
                    }
                    .padding(1.dp * scale)
                    .background(dot, CircleShape),
            )
        }
        Text(
            label,
            style = TextStyle(fontFamily = Manrope, fontSize = 11.sp * scale, fontWeight = FontWeight(700), lineHeight = 13.sp * scale, letterSpacing = (-0.01).em),
            color = if (colors.isDark) Color(0xFFCBD5E1) else colors.textPrimary,
            maxLines = 1,
        )
    }
}

/** Header tab: 13.5 sp, bold and underlined (2.5 dp accent) when selected, medium otherwise. */
@Composable
fun NookHeaderTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, scale: Float = 1f) {
    val colors = nook
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp * scale))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(bottom = 4.dp * scale),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            label,
            style = TextStyle(
                fontFamily = Manrope,
                fontSize = 13.5.sp * scale,
                fontWeight = FontWeight(if (selected) 700 else 500),
                lineHeight = 20.25.sp * scale,
                letterSpacing = (-0.025).em,
            ),
            color = if (selected) colors.textPrimary else colors.textSecondary,
            modifier = Modifier.padding(bottom = 4.dp * scale),
        )
        if (selected) {
            Box(Modifier.matchParentWidth().height(2.5.dp * scale).background(colors.accent, CircleShape))
        }
    }
}

private fun Modifier.matchParentWidth(): Modifier = this.then(Modifier.fillMaxWidth())

/** Title block: 20/750 title, 14/550 podcast name, centred. */
@Composable
fun NookNowPlayingTitle(title: String, podcast: String, onPodcastClick: () -> Unit, modifier: Modifier = Modifier, scale: Float = 1f) {
    val colors = nook
    androidx.compose.foundation.layout.Column(modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            title,
            style = TextStyle(fontFamily = Manrope, fontSize = 20.sp * scale, fontWeight = FontWeight(750), lineHeight = 25.sp * scale, letterSpacing = (-0.02).em),
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            podcast,
            style = TextStyle(fontFamily = Manrope, fontSize = 14.sp * scale, fontWeight = FontWeight(550), lineHeight = 21.sp * scale, letterSpacing = (-0.025).em),
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp * scale).clip(RoundedCornerShape(8.dp * scale)).clickable(onClick = onPodcastClick),
        )
    }
}

/** Box of the theme's thumb; the thumb's centre sits on the end of the timeline fill. */
internal fun thumbBox(palette: ThemePalette): Dp = when (palette) {
    ThemePalette.LAVENDER -> 24.dp // the 17 dp gem and its ambient shadow
    ThemePalette.WINTER, ThemePalette.MINT -> 28.dp // the disc of the mockups
    ThemePalette.AUTUMN -> 36.dp // the fixed exception of the thumb spec: 36 dp box, centred
    ThemePalette.SIGNATURE -> 26.dp // the pearl and its halo
}

/**
 * Timeline thumb of the Stitch mockups, for good (user, 31.10.2026: the trial option became the
 * thumb): Lavender «Prism Gem» with its rim, the Winter snowflake and the Mint leaf in their discs,
 * the Autumn maple PNG and the Signature pearl, each with its own soft glow. While scrubbing
 * ([active]) it grows to 1.15 on a 400/0.80 spring and glows 25 % brighter.
 */
@Composable
fun NookTimelineThumb(palette: ThemePalette, modifier: Modifier = Modifier, scale: Float = 1f, active: Boolean = false) {
    val dark = nook.isDark
    val grow by animateFloatAsState(
        if (active) 1.15f else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.80f, stiffness = 400f),
        label = "thumbGrow",
    )
    val boost = if (active) 1.25f else 1f
    Box(modifier.size(thumbBox(palette) * scale).scale(grow), contentAlignment = Alignment.Center) {
        when (palette) {
            ThemePalette.LAVENDER -> Canvas(Modifier.fillMaxSize()) {
                drawPrismGem(center, unit = 1.dp.toPx() * scale, glow = (if (dark) 0.45f else 0.32f) * boost, stitchRim = true)
            }
            // The glyph in a 28 dp disc of frosted glass with the mockups' two outlines.
            ThemePalette.WINTER -> ThumbDisc(
                disc = if (dark) Color(0xF5131B2A) else Color(0xEBFFFFFF),
                ring = if (dark) Color(0x9938BDF8) else Color(0xFF7DD3FC),
                glow = Color(0xFF38BDF8).copy(alpha = (if (dark) 0.5f else 0.3f) * boost),
                dark = dark,
                size = 28.dp * scale,
                active = active,
            ) {
                GlowingThumb(
                    androidx.compose.ui.graphics.vector.rememberVectorPainter(NookThumbs.Snowflake),
                    glow = Color(0xFF0284C7).copy(alpha = 0.35f * boost),
                    sigma = (4f * 0.21f * scale).dp,
                    dy = (2f * 0.21f * scale).dp,
                    modifier = Modifier.size(21.dp * scale),
                )
            }
            // The mockups' own leaf (user, 31.10.2026: the redrawn one was not as good): the 24-unit
            // leaf path, 16 dp in #34D399 with a 4 px accent glow at night, 20 dp in #059669 by day.
            ThemePalette.MINT -> ThumbDisc(
                disc = if (dark) Color(0xF50C241E) else Color(0xEBFFFFFF),
                ring = if (dark) Color(0xB310B981) else Color(0xFF6EE7B7),
                glow = Color(0xFF10B981).copy(alpha = (if (dark) 0.5f else 0.3f) * boost),
                dark = dark,
                size = 28.dp * scale,
                active = active,
            ) {
                GlowingThumb(
                    androidx.compose.ui.graphics.vector.rememberVectorPainter(if (dark) NookThumbs.StitchLeafNight else NookThumbs.StitchLeafDay),
                    glow = if (dark) Color(0xFF10B981).copy(alpha = 0.8f * boost) else Color(0xFF064E3B).copy(alpha = 0.12f),
                    sigma = (if (dark) 2f else 0.5f).dp * scale,
                    dy = (if (dark) 0f else 1f).dp * scale,
                    modifier = Modifier.size((if (dark) 16.dp else 20.dp) * scale),
                )
            }
            // Signature: the icon's two waves of hair, peach and lavender, meeting in one disc
            // (user, 29.10.2026: a marker from the icon and the theme, without an outline).
            ThemePalette.SIGNATURE -> Canvas(Modifier.fillMaxSize()) {
                drawSignatureWave(center, r = 8.5.dp.toPx() * scale, glow = (if (dark) 0.42f else 0.26f) * boost)
            }
            // Warm amber glow in dark themes only, no outline (thumb spec §2.2).
            ThemePalette.AUTUMN -> GlowingThumb(
                androidx.compose.ui.res.painterResource(ru.e1173.nookast.R.drawable.thumb_autumn_maple),
                glow = if (dark) Color(0xFFD97706).copy(alpha = 0.35f * boost) else null,
                sigma = 3.dp * scale,
                dy = 0.dp,
            )
        }
    }
}

/**
 * The 28 dp disc of the Stitch Winter and Mint thumbs (mockup markup), redrawn richer (user,
 * 31.10.2026: the ring came out oval — a timeline row lower than 28 dp squeezed it — and the
 * mockup's second outline was missing). Always a circle of [size]:
 * - a soft halo in the accent (0 0 12px accent at night, a lighter one by day);
 * - a frosted disc, lit from the top;
 * - the mockup's two outlines: a 1 dp ring in the accent that runs light → full → light around it
 *   like ice or a leaf's edge, and a hairline inside it (white by day, a pale accent at night);
 * - while scrubbing ([active]) the halo brightens and the ring's light turns slowly.
 */
@Composable
private fun ThumbDisc(
    disc: Color,
    ring: Color,
    glow: Color,
    dark: Boolean,
    size: Dp,
    active: Boolean,
    content: @Composable () -> Unit,
) {
    val turn = if (active) {
        val spin = androidx.compose.animation.core.rememberInfiniteTransition(label = "thumbRing")
        spin.animateFloat(
            0f,
            360f,
            androidx.compose.animation.core.infiniteRepeatable(tween(2400, easing = androidx.compose.animation.core.LinearEasing)),
            label = "thumbRingTurn",
        ).value
    } else {
        0f
    }
    val hairline = if (dark) ring.copy(alpha = 0.35f).compositeOver(Color.White.copy(alpha = 0.25f)) else Color.White.copy(alpha = 0.9f)
    Box(
        Modifier
            .requiredSize(size)
            .drawBehind {
                val r = this.size.minDimension / 2f
                // Halo.
                if (dark) drawCssShadow(glow, 12.dp, r) else drawCssShadow(glow.copy(alpha = glow.alpha * 0.7f), 6.dp, r, dy = 1.dp)
                // Frosted disc, a little lighter at the top as glass under light.
                drawCircle(disc, r)
                drawCircle(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = if (dark) 0.10f else 0.35f), Color.Transparent),
                        startY = 0f,
                        endY = this.size.height * 0.6f,
                    ),
                    r,
                )
                // Hairline inside the ring.
                val ringWidth = 1.dp.toPx()
                drawCircle(hairline, r - ringWidth * 1.5f, style = androidx.compose.ui.graphics.drawscope.Stroke(0.6.dp.toPx()))
                // The ring: its light runs around it and turns while scrubbing.
                rotate(turn) {
                    drawCircle(
                        Brush.sweepGradient(
                            listOf(ring.copy(alpha = ring.alpha * 0.55f), ring, Color.White.copy(alpha = if (dark) 0.75f else 0.95f).compositeOver(ring), ring, ring.copy(alpha = ring.alpha * 0.55f)),
                        ),
                        r - ringWidth / 2f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(ringWidth * 1.25f),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The thumb picture over a blurred copy of itself in [glow] (API 31+; earlier: no glow). */
@Composable
private fun GlowingThumb(
    painter: androidx.compose.ui.graphics.painter.Painter,
    glow: Color?,
    sigma: Dp,
    dy: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (glow != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // The blurred layer is larger than the picture so the glow is not cut at its edges.
            val margin = sigma * 3
            BoxWithConstraints(Modifier.fillMaxSize()) {
                androidx.compose.foundation.Image(
                    painter,
                    contentDescription = null,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(glow),
                    modifier = Modifier
                        .requiredSize(maxWidth + margin * 2, maxHeight + margin * 2)
                        .graphicsLayer {
                            val radius = ((sigma.toPx() - 0.5f) / 0.57735f).coerceAtLeast(0.5f)
                            renderEffect = androidx.compose.ui.graphics.BlurEffect(radius, radius, androidx.compose.ui.graphics.TileMode.Decal)
                            translationY = dy.toPx()
                        }
                        .padding(margin),
                )
            }
        }
        androidx.compose.foundation.Image(painter, contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

/** Texts of [NookMiniPlayer] for accessibility. */
data class MiniPlayerLabels(
    val open: String,
    val play: String,
    val pause: String,
    val queue: String,
    val seekBack: String = "",
    val seekForward: String = "",
)

/**
 * Mini-player (Stitch mini-player spec, 26.10.2026): a floating card 56 dp tall with radius 16,
 * 12 dp from the screen sides and 8 dp above the navigation bar (the caller places it). Artwork
 * 40/8 and the titles open Now Playing; Play/Pause is a 38 dp accent disc and the queue a 38 dp
 * quiet button, both with 48 dp touch targets. One continuous 2.5 dp progress line runs along
 * the bottom edge, masked by the corners, with a 6 dp micro thumb on the end of the fill. Light:
 * white with a soft shadow; dark: surface with a faint accent glow. Seek buttons only where
 * [onSeekBack]/[onSeekForward] are given (Fold and car; never on a phone).
 */
@Composable
fun NookMiniPlayer(
    title: String,
    podcastName: String,
    progress: Float,
    playing: Boolean,
    buffering: Boolean,
    enabled: Boolean,
    labels: MiniPlayerLabels,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onQueue: () -> Unit,
    artwork: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    seekBackSeconds: Int = 10,
    seekForwardSeconds: Int = 30,
    onSeekBack: (() -> Unit)? = null,
    onSeekForward: (() -> Unit)? = null,
    scale: Float = 1f,
    glass: Modifier? = null,
) {
    val colors = nook
    val shape = RoundedCornerShape(16.dp * scale)
    // One edge for every theme (APK-3.6 review: some themes showed an accent outline): a neutral
    // hairline and a soft shadow; over [glass] the card is translucent so the blur shows through.
    val solid = if (colors.isDark) colors.surface.compositeOver(colors.background) else Color.White
    val surface = if (glass != null) solid.copy(alpha = if (colors.isDark) 0.38f else 0.45f) else solid
    val outline = if (colors.isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.07f)
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val r = 16.dp.toPx() * scale
                drawCssShadow(Color.Black.copy(alpha = if (colors.isDark) 0.45f else 0.12f), 24.dp, r, dy = 8.dp)
            }
            .clip(shape)
            .then(glass ?: Modifier)
            .background(surface)
            .border(1.dp, outline, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp * scale)
                .padding(end = 6.dp * scale),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp * scale))
                    .clickable(role = Role.Button, onClick = onOpen)
                    .semantics { contentDescription = labels.open }
                    .padding(start = 8.dp * scale, end = 6.dp * scale),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                artwork(Modifier.size(40.dp * scale).clip(RoundedCornerShape(8.dp * scale)))
                Spacer(Modifier.width(10.dp * scale))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = TextStyle(fontFamily = Manrope, fontSize = 13.sp * scale, lineHeight = 17.sp * scale, fontWeight = FontWeight(650), letterSpacing = (-0.01).sp),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        podcastName,
                        style = TextStyle(fontFamily = Manrope, fontSize = 11.5.sp * scale, lineHeight = 15.sp * scale, fontWeight = FontWeight(550)),
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onSeekBack != null) MiniSeekButton(seekBackSeconds, forward = false, labels.seekBack, onSeekBack, scale)
            MiniPlayPause(playing, buffering, enabled, if (playing) labels.pause else labels.play, onPlayPause, scale)
            if (onSeekForward != null) MiniSeekButton(seekForwardSeconds, forward = true, labels.seekForward, onSeekForward, scale)
            Spacer(Modifier.width(4.dp * scale))
            MiniRoundButton(labels.queue, onQueue, scale) {
                Icon(NookIcons.UpNext, null, tint = colors.textSecondary, modifier = Modifier.size(18.dp * scale))
            }
        }
        Canvas(Modifier.fillMaxWidth().height(2.5.dp * scale)) {
            val y = size.height / 2f
            val end = size.width * progress.coerceIn(0f, 1f)
            drawLine(colors.track, Offset(0f, y), Offset(size.width, y), size.height)
            if (end > 0f) {
                drawLine(colors.trackFill, Offset(0f, y), Offset(end, y), size.height)
                // Micro thumb: 6 dp lens with a 0.75 dp light rim.
                drawCircle(if (colors.isDark) Color(0xFFF1F5F9) else Color.White, radius = 3.dp.toPx() * scale, center = Offset(end, y))
                drawCircle(colors.trackFill, radius = 2.25.dp.toPx() * scale, center = Offset(end, y))
            }
        }
    }
}

@Composable
private fun MiniPlayPause(playing: Boolean, buffering: Boolean, enabled: Boolean, label: String, onClick: () -> Unit, scale: Float) {
    val colors = nook
    val interaction = remember { MutableInteractionSource() }
    val pressed = pressScale(interaction)
    Box(
        Modifier
            .size(48.dp * scale)
            .clickable(interaction, indication = null, enabled = enabled && !buffering, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(38.dp * scale).scale(pressed).background(colors.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when {
                buffering -> CircularProgressIndicator(color = colors.onAccent, strokeWidth = 2.2.dp, modifier = Modifier.size(18.dp * scale))
                else -> PlayPauseGlyph(playing, colors.onAccent, 38.dp * scale)
            }
        }
    }
}

@Composable
private fun MiniSeekButton(seconds: Int, forward: Boolean, label: String, onClick: () -> Unit, scale: Float) {
    val colors = nook
    MiniRoundButton(label, onClick, scale) {
        Icon(
            androidx.compose.ui.res.painterResource(seekIconRes(forward, seconds)),
            null,
            tint = colors.textPrimary,
            modifier = Modifier.size(32.dp * scale),
        )
    }
}

@Composable
private fun MiniRoundButton(label: String, onClick: () -> Unit, scale: Float, content: @Composable () -> Unit) {
    val colors = nook
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .size(48.dp * scale)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(38.dp * scale)
                .background(if (isPressed) colors.accent.copy(alpha = 0.08f) else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}
