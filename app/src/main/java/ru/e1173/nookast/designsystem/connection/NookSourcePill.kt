package ru.e1173.nookast.designsystem.connection

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.withTimeoutOrNull
import ru.e1173.nookast.designsystem.Manrope
import ru.e1173.nookast.designsystem.rememberReducedMotion

/** The pill's colour mood. */
enum class PillTone { NORMAL, RUN, BAD, WARN, ACCENT }

/** The quality badge (DEC-069, extends DEC-029): letters only. */
enum class QualityTier(val label: String) { LO("LO"), SD("SD"), HD("HD"), HQ("HQ"), MAX("MAX") }

/** LO < 64, SD 64–95, HD 96–191, HQ 192–255, MAX from 256 kbps; null when the bitrate is unknown. */
fun qualityTier(kbps: Int?): QualityTier? = when {
    kbps == null || kbps <= 0 -> null
    kbps < 64 -> QualityTier.LO
    kbps < 96 -> QualityTier.SD
    kbps < 192 -> QualityTier.HD
    kbps < 256 -> QualityTier.HQ
    else -> QualityTier.MAX
}

/** What the pill shows; built by the player's UI layer. */
@Immutable
data class PillModel(
    val label: String,
    val short: String,
    val tone: PillTone,
    /** «· 0:38» or «· 46 %»; [tailBare] is the same without the dot, for the glyph-and-number step. */
    val tail: String?,
    val tailBare: String?,
    val tier: QualityTier?,
    val glyph: GlyphKind,
    val fetch: Float,
    val checking: Boolean,
    /** 0…1 of the 60 s line while the runway shows; null otherwise. */
    val runway: Float?,
    val runwayCritical: Boolean,
    /** The server does not answer, but sound plays. */
    val serverDot: Boolean,
    val description: String,
) {
    /** The badge shows only when nothing else speaks at the right. */
    val hasBadge: Boolean get() = tier != null && tail == null && tone != PillTone.RUN && tone != PillTone.BAD
}

/**
 * The densities the pill gives way through when the row is short of room (DEC-069): 0 everything;
 * 1 without the quality badge; 2 the short label; 3 the glyph with its number or badge; 4 the glyph.
 */
fun pillSteps(model: PillModel): List<Int> = listOf(0) + (if (model.hasBadge) listOf(1) else emptyList()) + listOf(2, 3, 4)

/**
 * The source pill under the timeline (DEC-069, replaces the chip of DEC-029). It never runs into the
 * times beside it: it takes the fullest of its five forms that fits [maxWidth] with 1 dp to spare.
 * A tap opens the card, a 500 ms hold checks the connection right in the pill.
 */
@Composable
fun NookSourcePill(
    model: PillModel,
    story: ConnectionStory,
    palette: ConnectionPalette,
    maxWidth: Dp,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    val density = LocalDensity.current
    val fs = density.fontScale * scale
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = Manrope, fontSize = 12.5.sp * scale, fontWeight = FontWeight.Bold, lineHeight = 16.sp * scale, fontFeatureSettings = "tnum")
    val tailStyle = labelStyle.copy(fontWeight = FontWeight.ExtraBold)
    val badgeStyle = TextStyle(fontFamily = Manrope, fontSize = 9.5.sp * scale, fontWeight = FontWeight.ExtraBold, letterSpacing = .04.em, lineHeight = 15.sp * scale)
    val step = remember(model, maxWidth, fs) {
        with(density) {
            fun text(s: String, style: TextStyle) = measurer.measure(s, style, maxLines = 1, softWrap = false).size.width.toDp()
            val badge = model.tier?.let { text(it.label, badgeStyle) + 8.dp * fs } ?: 0.dp
            fun width(d: Int): Dp {
                val label = when {
                    d >= 3 -> null
                    d >= 2 -> model.short
                    else -> model.label
                }
                val tail = when {
                    d >= 4 -> null
                    d >= 3 -> model.tailBare
                    else -> model.tail
                }
                val pieces = mutableListOf(GLYPH * fs)
                label?.let { pieces += text(it, labelStyle) }
                when {
                    tail != null -> pieces += text(tail, tailStyle)
                    model.hasBadge && (d == 0 || d == 3) -> pieces += badge
                }
                val pad = if (d == 4) 10.dp * fs else 14.dp * fs
                return pad + pieces.fold(0.dp) { a, b -> a + b } + GAP * fs * (pieces.size - 1)
            }
            pillSteps(model).firstOrNull { width(it) <= maxWidth - FIT_SLACK } ?: 4
        }
    }
    val (fg, bg) = when (model.tone) {
        PillTone.BAD -> palette.badText to palette.badSoft
        PillTone.WARN -> palette.warnText to palette.warnSoft
        PillTone.ACCENT -> palette.accentText to palette.accentSoft
        else -> palette.ink to palette.tile
    }
    val haptic = LocalHapticFeedback.current
    var pressing by remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (pressing) .96f else 1f, tween(if (pressing) 500 else 150), label = "pillPress")
    // A pill that turns bad pulses its edge twice.
    val pulse = remember { Animatable(0f) }
    val reduced = rememberReducedMotion()
    LaunchedEffect(model.tone == PillTone.BAD) {
        if (model.tone == PillTone.BAD && !reduced) repeat(2) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
        pulse.snapTo(0f)
    }
    val label = when {
        step >= 3 -> null
        step >= 2 -> model.short
        else -> model.label
    }
    val tail = when {
        step >= 4 -> null
        step >= 3 -> model.tailBare
        else -> model.tail
    }
    val showBadge = tail == null && model.hasBadge && (step == 0 || step == 3)
    Row(
        modifier
            .scale(press)
            .drawBehind {
                val p = pulse.value
                if (p > 0f) {
                    val grow = 10.dp.toPx() * p
                    drawRoundRect(
                        palette.scene.bad.copy(alpha = .55f * (1 - p)),
                        Offset(-grow, -grow),
                        Size(size.width + 2 * grow, size.height + 2 * grow),
                        androidx.compose.ui.geometry.CornerRadius(size.height / 2 + grow),
                        style = Stroke(2.dp.toPx()),
                    )
                }
            }
            .height(28.dp * fs)
            .clip(CircleShape)
            .background(bg)
            .semantics(mergeDescendants = true) {
                contentDescription = model.description
                liveRegion = LiveRegionMode.Polite
                role = Role.Button
                onClick { onClick(); true }
                if (onLongPress != null) onLongClick { onLongPress(); true }
            }
            .pointerInput(onClick, onLongPress) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressing = true
                    val up = withTimeoutOrNull(LONG_PRESS_MS) { waitForUpOrCancellation() }
                    if (up == null && onLongPress != null) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                        waitForUpOrCancellation()
                    } else if (up != null) {
                        onClick()
                    }
                    pressing = false
                }
            }
            .animateContentSize(spring(dampingRatio = .8f, stiffness = 400f))
            .padding(start = 5.dp * fs, end = (if (step == 4) 5.dp else 9.dp) * fs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GAP * fs),
    ) {
        PillGlyph(model, story, palette, fg, Modifier.size(GLYPH * fs))
        if (label != null) {
            AnimatedContent(
                targetState = label,
                transitionSpec = { (slideInVertically(tween(320)) { it } + fadeIn(tween(320))) togetherWith (slideOutVertically(tween(200)) { -it } + fadeOut(tween(200))) },
                label = "pillLabel",
            ) { text -> Text(text, style = labelStyle, color = fg, maxLines = 1, softWrap = false) }
        }
        when {
            tail != null -> RollingText(tail, tailStyle, fg)
            showBadge -> QualityBadge(model.tier!!, palette, badgeStyle, fs)
        }
    }
}

/** Numbers that change roll in from below, one character at a time. */
@Composable
private fun RollingText(text: String, style: TextStyle, color: Color) {
    Row {
        text.forEachIndexed { i, ch ->
            androidx.compose.runtime.key(i) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = { (slideInVertically(tween(280)) { it } + fadeIn(tween(280))) togetherWith (slideOutVertically(tween(200)) { -it } + fadeOut(tween(200))) },
                    label = "pillDigit",
                ) { c -> Text(c.toString(), style = style, color = color, maxLines = 1, softWrap = false) }
            }
        }
    }
}

/** LO and SD quiet, HD tinted, HQ filled, MAX filled with a warm edge and one sheen when it appears. */
@Composable
fun QualityBadge(tier: QualityTier, palette: ConnectionPalette, style: TextStyle, fs: Float) {
    val shape = RoundedCornerShape(4.dp * fs)
    val sheen = remember(tier) { Animatable(if (tier == QualityTier.MAX) -1.2f else 2f) }
    val reduced = rememberReducedMotion()
    LaunchedEffect(tier) {
        if (tier == QualityTier.MAX && !reduced) {
            kotlinx.coroutines.delay(300)
            sheen.animateTo(1.2f, tween(1100, easing = FastOutSlowInEasing))
        }
    }
    val (fg, base) = when (tier) {
        QualityTier.LO, QualityTier.SD -> palette.badgeQuiet to Modifier.border(1.dp, palette.muted.copy(alpha = .55f), shape)
        QualityTier.HD -> palette.accentText to Modifier.background(palette.accentSoft, shape)
        QualityTier.HQ -> palette.onAccent to Modifier.background(palette.accentFill, shape)
        QualityTier.MAX -> palette.onAccent to Modifier.background(
            Brush.linearGradient(listOf(androidx.compose.ui.graphics.lerp(palette.accentFill, Color(0xFFF6B26B), .45f), palette.accentFill, palette.accentFill)),
            shape,
        )
    }
    Box(
        Modifier
            .height(15.dp * fs)
            .clip(shape)
            .then(base)
            .drawWithContent {
                drawContent()
                val x = sheen.value
                if (x in -1.1f..1.1f) {
                    val w = size.width
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = .6f), Color.Transparent),
                            start = Offset(w * (x + .3f), 0f),
                            end = Offset(w * (x + .7f), size.height),
                        ),
                    )
                }
            }
            .padding(horizontal = 4.dp * fs),
        contentAlignment = Alignment.Center,
    ) {
        Text(tier.label, style = style, color = fg, maxLines = 1, softWrap = false)
    }
}

/**
 * The story's glyph. A ring appears only at the buffer line (amber, coral) and while checking (a
 * running arc); a small coral dot at the top right: the server does not answer, but sound plays.
 */
@Composable
fun PillGlyph(model: PillModel, story: ConnectionStory, palette: ConnectionPalette, color: Color, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val spin = if (model.checking && !reduced) {
        val transition = rememberInfiniteTransition(label = "pillSpin")
        transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart), label = "pillSpinAngle")
    } else {
        null
    }
    val runway by animateFloatAsState(model.runway ?: 0f, tween(900, easing = LinearEasing), label = "pillRunway")
    val tint = if (model.glyph == GlyphKind.LOCAL) palette.scene.ok else color
    Canvas(modifier) {
        val k = size.width / 20f
        scale(k, k, Offset.Zero) {
            val ringed = model.checking || model.runway != null
            if (model.runway != null) {
                drawCircle(color.copy(alpha = .15f), 9f, Offset(10f, 10f), style = Stroke(2f))
                drawArc(if (model.runwayCritical) palette.scene.bad else palette.warn, -90f, 360f * runway.coerceIn(.04f, 1f), false, Offset(1f, 1f), Size(18f, 18f), style = Stroke(2f, cap = StrokeCap.Round))
            }
            if (model.checking) {
                rotate(spin?.value ?: 0f, Offset(10f, 10f)) {
                    drawArc(palette.cover, -90f, 360f * .26f, false, Offset(1f, 1f), Size(18f, 18f), style = Stroke(1.8f, cap = StrokeCap.Round))
                }
            }
            if (ringed) {
                translate(3.5f, 3.5f) {
                    scale(.65f, .65f, Offset.Zero) {
                        with(story) { glyph(model.glyph, model.fetch, if (model.checking) tint.copy(alpha = .6f) else tint) }
                    }
                }
            } else {
                with(story) { glyph(model.glyph, model.fetch, tint) }
            }
            if (model.serverDot) {
                drawCircle(palette.card, 3f, Offset(17f, 3.4f))
                drawCircle(palette.scene.bad, 2.4f, Offset(17f, 3.4f))
            }
        }
    }
}

private val GLYPH = 20.dp
private val GAP = 6.dp
private val FIT_SLACK = 1.dp
private const val LONG_PRESS_MS = 500L
