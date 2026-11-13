package ru.e1173.nookast.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The single light of Play in the open player (design audit 4.5, DEC-058). Night themes only; the
 * colour is the accent under the sky. Values: [light] 1 playing / 0 paused (300 ms), a [pulse] on
 * each Play/Pause tap that reaches the reflections later the farther they are (4.10), [breath] while
 * buffering longer than 400 ms (4.11) and [voice] (5.8, 1 until then).
 *
 * Play's own glow is (0.55 + 0.45 · light) · (1 + pulse) · breath · voice; a reflection is
 * (0.5 + 0.5 · light) · (1 + its delayed pulse) · breath · voice. Callers keep alphas within 1.
 */
@Stable
class PlayLight internal constructor(
    private val lightState: State<Float>,
    private val breathState: State<Float>,
    private val voiceSource: State<() -> Float>,
    private val pulses: Map<Int, Animatable<Float, *>>,
    private val scope: CoroutineScope,
    private val reducedMotion: Boolean,
) {
    val light: Float get() = lightState.value
    val breath: Float get() = breathState.value
    val voice: Float get() = voiceSource.value()
    fun pulse(delayMs: Int): Float = pulses[delayMs]?.value ?: 0f

    /** Play's own glow factor (1 while playing at rest). */
    fun playGlow(): Float = (0.55f + 0.45f * light) * (1f + pulse(PlayLightDelay.PLAY)) * breath * voice

    /** A reflection's factor (1 while playing at rest, 0.5 paused). */
    fun reflection(delayMs: Int): Float = (0.5f + 0.5f * light) * (1f + pulse(delayMs)) * breath * voice

    /** The wave of a Play/Pause tap (4.10): 0.5 when playback starts, 0.25 when it pauses (raised from 0.35 / 0.15: the pause wave was hidden by the dimming on frames, 13.11.2026). */
    fun flash(starting: Boolean) {
        if (reducedMotion) return
        val amplitude = if (starting) PULSE_START else PULSE_PAUSE
        pulses.forEach { (delayMs, pulse) ->
            scope.launch {
                delay(delayMs.toLong())
                pulse.animateTo(amplitude, tween(PULSE_RISE_MS, easing = FastOutSlowInEasing))
                pulse.animateTo(0f, tween(PULSE_FALL_MS, easing = FastOutSlowInEasing))
            }
        }
    }

    internal companion object {
        const val PULSE_START = 0.5f
        const val PULSE_PAUSE = 0.25f
        private const val PULSE_RISE_MS = 120
        private const val PULSE_FALL_MS = 380
    }
}

/** How late the wave reaches each element (4.10): by its distance from Play. */
object PlayLightDelay {
    const val PLAY = 0
    const val SEEK = 30
    const val CHAPTER = 60
    const val DOCK = 110
}

/** The open player's light; null elsewhere, where components keep their own playing state. */
val LocalPlayLight = compositionLocalOf<PlayLight?> { null }

/** Keep a light's alpha within 1 after the pulse lifted it. */
fun lit(alpha: Float): Float = alpha.coerceIn(0f, 1f)

@Composable
fun rememberPlayLight(playing: Boolean, buffering: Boolean, voice: () -> Float = { 1f }): PlayLight {
    val reduced = rememberReducedMotion()
    val light = animateFloatAsState(
        if (playing) 1f else 0f,
        tween(LIGHT_MS, easing = FastOutSlowInEasing),
        label = "playLight",
    )
    // Breathing only when buffering lasts (4.11): a short stall does not flicker the light.
    var breathing by remember { mutableStateOf(false) }
    LaunchedEffect(buffering) {
        if (buffering) {
            delay(BREATH_AFTER_MS)
            breathing = true
        } else {
            breathing = false
        }
    }
    val breath = remember { Animatable(1f) }
    LaunchedEffect(breathing, reduced) {
        if (!breathing) {
            breath.animateTo(1f, NookMotion.standard())
        } else if (reduced) {
            breath.animateTo(BREATH_REDUCED, NookMotion.standard())
        } else {
            while (true) {
                breath.animateTo(BREATH_LOW, tween(BREATH_PERIOD_MS / 2, easing = FastOutSlowInEasing))
                breath.animateTo(1f, tween(BREATH_PERIOD_MS / 2, easing = FastOutSlowInEasing))
            }
        }
    }
    // Read while drawing: the voice moves the glow without recomposing the player (5.8).
    val voiceState = rememberUpdatedState(voice)
    val scope = rememberCoroutineScope()
    val pulses = remember {
        listOf(PlayLightDelay.PLAY, PlayLightDelay.SEEK, PlayLightDelay.CHAPTER, PlayLightDelay.DOCK)
            .associateWith { Animatable(0f) }
    }
    val breathState = remember { derivedBreath(breath) }
    return remember(reduced) { PlayLight(light, breathState, voiceState, pulses, scope, reduced) }
}

private fun derivedBreath(breath: Animatable<Float, *>): State<Float> = object : State<Float> {
    override val value: Float get() = breath.value
}

private const val LIGHT_MS = 300
private const val BREATH_AFTER_MS = 400L
private const val BREATH_PERIOD_MS = 1600
private const val BREATH_LOW = 0.6f
private const val BREATH_REDUCED = 0.8f

/**
 * Play's reflection on a seek disc (design audit 4.9): on the side facing Play, a 110° crescent on
 * the rim (1.5 dp, accent .55 in the middle fading to nothing at its ends) and a soft radial fill
 * from that rim point (radius 0.9 r, accent .14). Night themes, in the open player only; the
 * existing highlight, outline and shadow of the disc stay.
 */
@Composable
fun androidx.compose.ui.Modifier.seekPlayReflection(playOnRight: Boolean): androidx.compose.ui.Modifier {
    val colors = LocalNookastColors.current
    val light = LocalPlayLight.current
    if (light == null || !colors.isDark) return this
    val accent = colors.accent
    return drawBehind {
        val k = light.reflection(PlayLightDelay.SEEK)
        val r = size.minDimension / 2f
        val c = center
        val towardPlay = if (playOnRight) 1f else -1f
        drawCircle(
            androidx.compose.ui.graphics.Brush.radialGradient(
                listOf(accent.copy(alpha = lit(SEEK_FILL_ALPHA * k)), androidx.compose.ui.graphics.Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(c.x + towardPlay * r, c.y),
                radius = SEEK_FILL_REACH * r,
            ),
            radius = r,
            center = c,
        )
        val stroke = SEEK_RIM_WIDTH.toPx()
        val middle = if (playOnRight) 0f else 180f
        val step = SEEK_ARC_DEGREES / SEEK_ARC_STEPS
        for (i in 0 until SEEK_ARC_STEPS) {
            val t = (i + 0.5f) / SEEK_ARC_STEPS
            val falloff = 1f - kotlin.math.abs(t - 0.5f) * 2f
            drawArc(
                accent.copy(alpha = lit(SEEK_RIM_ALPHA * falloff * k)),
                startAngle = middle - SEEK_ARC_DEGREES / 2f + i * step,
                sweepAngle = step,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(c.x - r + stroke / 2f, c.y - r + stroke / 2f),
                size = androidx.compose.ui.geometry.Size(2f * r - stroke, 2f * r - stroke),
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
            )
        }
    }
}

private const val SEEK_FILL_ALPHA = 0.14f
private const val SEEK_FILL_REACH = 0.9f
private const val SEEK_RIM_ALPHA = 0.55f
private const val SEEK_ARC_DEGREES = 110f
private const val SEEK_ARC_STEPS = 22
private val SEEK_RIM_WIDTH = androidx.compose.ui.unit.Dp(1.5f)

/**
 * A Play disc that lights the card it sits on (design audit 4.12): the card records where it is,
 * the disc where its centre is; the card then draws the light from that point.
 */
@Stable
class NookLightSource {
    internal var cardOrigin by mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
    internal var discCenter by mutableStateOf<androidx.compose.ui.geometry.Offset?>(null)
    internal val local: androidx.compose.ui.geometry.Offset? get() = discCenter?.minus(cardOrigin)
}

@Composable
fun rememberLightSource(): NookLightSource = remember { NookLightSource() }

/** On the Play disc that lights the card. */
fun androidx.compose.ui.Modifier.lightEmitter(source: NookLightSource): androidx.compose.ui.Modifier =
    then(
        androidx.compose.ui.Modifier.onGloballyPositioned {
            val bounds = it.boundsInRoot()
            source.discCenter = bounds.center
        },
    )

/**
 * The card's light from its Play disc (4.12), drawn over the card's surface and outline and under
 * its content: a radial fill of [reach] (accent [fillAlpha] → 0) and the top outline in accent .45
 * over the disc, fading into the usual outline [edgeSpread] to each side. Night themes only; the
 * factor is 0.5 + 0.5 · light, the light following [playing] over 300 ms.
 */
@Composable
fun androidx.compose.ui.Modifier.playSourceLight(
    source: NookLightSource,
    playing: Boolean,
    reach: androidx.compose.ui.unit.Dp,
    fillAlpha: Float,
    edgeSpread: androidx.compose.ui.unit.Dp,
    edgeAlpha: Float = CARD_EDGE_ALPHA,
): androidx.compose.ui.Modifier {
    val colors = LocalNookastColors.current
    val placed = then(androidx.compose.ui.Modifier.onGloballyPositioned { source.cardOrigin = it.boundsInRoot().topLeft })
    if (!colors.isDark) return placed
    val light by animateFloatAsState(if (playing) 1f else 0f, tween(LIGHT_MS, easing = FastOutSlowInEasing), label = "cardLight")
    val accent = colors.accent
    return placed.drawBehind {
        val c = source.local ?: return@drawBehind
        val k = 0.5f + 0.5f * light
        drawRect(
            androidx.compose.ui.graphics.Brush.radialGradient(
                listOf(accent.copy(alpha = lit(fillAlpha * k)), androidx.compose.ui.graphics.Color.Transparent),
                center = c,
                radius = reach.toPx(),
            ),
        )
        val spread = edgeSpread.toPx()
        drawRect(
            androidx.compose.ui.graphics.Brush.horizontalGradient(
                0f to androidx.compose.ui.graphics.Color.Transparent,
                0.5f to accent.copy(alpha = lit(edgeAlpha * k)),
                1f to androidx.compose.ui.graphics.Color.Transparent,
                startX = c.x - spread,
                endX = c.x + spread,
            ),
            size = androidx.compose.ui.geometry.Size(size.width, CARD_EDGE_WIDTH.toPx()),
        )
    }
}

private const val CARD_EDGE_ALPHA = 0.45f
private val CARD_EDGE_WIDTH = androidx.compose.ui.unit.Dp(1f)

/** The light of the current episode's row or tile from its own Play (4.12): 64 dp, .20, 96 dp, outline .60 (raised from .12 / .45: too faint in the Feed, design audit 12.11.2026). */
@Composable
fun androidx.compose.ui.Modifier.currentEpisodeLight(source: NookLightSource, playing: Boolean): androidx.compose.ui.Modifier =
    playSourceLight(source, playing, ROW_LIGHT_REACH, ROW_LIGHT_ALPHA, ROW_LIGHT_SPREAD, ROW_EDGE_ALPHA)

private val ROW_LIGHT_REACH = androidx.compose.ui.unit.Dp(64f)
private const val ROW_LIGHT_ALPHA = 0.20f
private const val ROW_EDGE_ALPHA = 0.60f
private val ROW_LIGHT_SPREAD = androidx.compose.ui.unit.Dp(96f)
