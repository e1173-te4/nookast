package ru.e1173.nookast.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sin

/*
 * The theme art of DEC-061 (user, 13.11.2026, the eight-theme mock): each theme's pair of night-glow
 * lights, its timeline track and the motion of its thumb. Values are the mock's CSS; a mock length in
 * cqw is 3.6 dp (its phone is 360 px wide), shortened where the 24 dp timeline row has less height
 * than the mock's 43 px seek. Nothing here changes a layout: tracks, lights and effects only draw.
 *
 * Cost (review of 13.11.2026): brushes, paths, strokes and glow paints are made in drawWithCache,
 * which rebuilds when the position, the colours or the size change (about once a second while it
 * plays); a frame of motion only reads the clock and moves, scales or fades what is already made.
 * Glow blurs are fixed; a pulse animates a glow's scale and alpha, never its blur.
 */

/** A theme's night glow: two side lights ([left], [right]) and a haze from the top. */
@Immutable
data class ThemeGlow(val left: Color, val right: Color, val haze: Color)

/** The mock's `glowA`, `glowB` and `haze` of [palette]; the light values are the mock's day ones. */
fun themeGlow(palette: ThemePalette, dark: Boolean): ThemeGlow = when (palette) {
    ThemePalette.SIGNATURE -> if (dark) ThemeGlow(Color(0x50FFB497), Color(0x50C9B2F0), Color(0x663A2752)) else ThemeGlow(Color(0x26F2A07E), Color(0x26B79CEB), Color.White)
    ThemePalette.LAVENDER -> if (dark) ThemeGlow(Color(0x55B35CFF), Color(0x40FF7AD6), Color(0x662E2648)) else ThemeGlow(Color(0x226A53D6), Color(0x1FFF7AD6), Color.White)
    ThemePalette.AUTUMN -> if (dark) ThemeGlow(Color(0x55EB7A44), Color(0x44ED6575), Color(0x663A1E14)) else ThemeGlow(Color(0x26EB7A44), Color(0x1FED6575), Color.White)
    ThemePalette.WINTER -> if (dark) ThemeGlow(Color(0x5538BDF8), Color(0x40A5B4FC), Color(0x661E3A5F)) else ThemeGlow(Color(0x2638BDF8), Color(0x26A5B4FC), Color.White)
    ThemePalette.MINT -> if (dark) ThemeGlow(Color(0x5510B981), Color(0x405EEAD4), Color(0x660D231E)) else ThemeGlow(Color(0x2610B981), Color(0x1F5EEAD4), Color.White)
    ThemePalette.ON_AIR -> if (dark) ThemeGlow(Color(0x66EC4899), Color(0x4D22D3EE), Color(0x66334155)) else ThemeGlow(Color(0x22EC4899), Color(0x2222D3EE), Color.White)
    ThemePalette.FIREFLY -> if (dark) ThemeGlow(Color(0x40D9F26A), Color(0x33D9F26A), Color(0x553E5A3F)) else ThemeGlow(Color(0x33C3DC5A), Color(0x26C3DC5A), Color.White)
    ThemePalette.BEACON -> if (dark) ThemeGlow(Color(0x55E07433), Color(0x402EE6E6), Color(0x660F3D3E)) else ThemeGlow(Color(0x26E07433), Color(0x262E6F73), Color.White)
}

/**
 * The theme's night glow over a screen background (the mock's `.glow` layer): two lights at each
 * side, at a fifth and two thirds of the height, and a haze from the top. It only draws.
 */
@Composable
fun NookThemeGlow(modifier: Modifier = Modifier) {
    val glow = themeGlow(LocalThemePalette.current, LocalNookastColors.current.isDark)
    Spacer(modifier.drawBehind { drawThemeGlow(glow) })
}

internal fun DrawScope.drawThemeGlow(glow: ThemeGlow) {
    glowEllipse(glow.left, -0.08f, 0.20f, 0.60f, 0.22f)
    glowEllipse(glow.right, 1.08f, 0.20f, 0.60f, 0.22f)
    glowEllipse(glow.left, -0.10f, 0.66f, 0.70f, 0.16f)
    glowEllipse(glow.right, 1.10f, 0.66f, 0.70f, 0.16f)
    glowEllipse(glow.haze, 0.50f, 0f, 0.90f, 0.40f)
}

/** CSS `radial-gradient(rx ry at cx cy, color, transparent 70%)`, fractions of the size. */
private fun DrawScope.glowEllipse(color: Color, cx: Float, cy: Float, rx: Float, ry: Float) {
    if (color.alpha <= 0.001f || size.height <= 0f) return
    val center = Offset(size.width * cx, size.height * cy)
    val radiusY = size.height * ry
    val radiusX = size.width * rx
    scale(scaleX = radiusX / radiusY, scaleY = 1f, pivot = center) {
        drawCircle(
            Brush.radialGradient(0f to color, 0.7f to color.copy(alpha = 0f), center = center, radius = radiusY),
            radius = radiusY,
            center = center,
        )
    }
}

/**
 * The clock of a theme's timeline motion. [seconds] runs while it plays and slows to a stop with
 * [live]; [live] is 1 while it plays and 0 at rest — paused, «remove animations», the power saver or
 * a screen in the background — eased between, so every effect settles into its still picture.
 * Read both in a draw or layer block only: they then redraw without recomposing.
 */
@Stable
class ThemeMotion internal constructor(
    private val clock: State<Float>,
    private val level: State<Float>,
    private val stop: State<Float>,
) {
    val seconds: Float get() = clock.value
    val live: Float get() = level.value

    /** [seconds] when the motion last began to stop: the resting pose is chosen from it, once. */
    val stoppedAt: Float get() = stop.value

    companion object {
        /** No motion: the still picture of every theme. */
        val Still = ThemeMotion(mutableFloatStateOf(0f), mutableFloatStateOf(0f), mutableFloatStateOf(0f))
    }
}

@Composable
fun rememberThemeMotion(playing: Boolean): ThemeMotion {
    val reduced = rememberReducedMotion()
    val powerSave = rememberPowerSaveMode()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val run = playing && !reduced && !powerSave && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val level = animateFloatAsState(if (run) 1f else 0f, tween(THEME_MOTION_EASE_MS, easing = FastOutSlowInEasing), label = "themeMotion")
    val clock = remember { mutableFloatStateOf(0f) }
    val stop = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(run) {
        // The resting pose is fixed when the fade begins, so it cannot change half way.
        if (!run) stop.floatValue = clock.floatValue
        var last = 0L
        // Stopping keeps the clock running until the effects have faded to their still picture.
        while (run || level.value > 0f) {
            withFrameNanos { now ->
                if (last != 0L) clock.floatValue += (now - last) / NANOS_PER_SECOND * level.value
                last = now
            }
        }
    }
    return remember(clock, level, stop) { ThemeMotion(clock, level, stop) }
}

private const val THEME_MOTION_EASE_MS = 400
private const val NANOS_PER_SECOND = 1_000_000_000f

/** Phase 0…1 of a cycle of [period] s that started [delay] s late (a negative CSS delay is a head start). */
private fun phase(seconds: Float, period: Float, delay: Float = 0f): Float {
    val t = (seconds - delay) / period
    return t - floor(t)
}

/** CSS `ease-in-out` close enough: a cosine ramp. */
private fun easeInOut(t: Float): Float = (1f - cos(PI.toFloat() * t)) / 2f

/** An `alternate` CSS cycle: 0 → 1 over [period], back over the next, eased. */
private fun pingPong(seconds: Float, period: Float, delay: Float = 0f): Float {
    val t = (seconds - delay) / period
    val turn = floor(t)
    val local = t - turn
    return easeInOut(if (turn.toInt() % 2 == 0) local else 1f - local)
}

/** A keyframe track: [frames] are phase, value, phase, value… from 0 to 1, eased between neighbours. */
private fun keyframes(phase: Float, frames: FloatArray): Float {
    var i = 2
    while (i < frames.size) {
        if (phase <= frames[i]) {
            val span = frames[i] - frames[i - 2]
            if (span <= 0f) return frames[i + 1]
            return frames[i - 1] + (frames[i + 1] - frames[i - 1]) * easeInOut((phase - frames[i - 2]) / span)
        }
        i += 2
    }
    return frames[frames.size - 1]
}

private val FLICKER = floatArrayOf(0f, 1f, 0.46f, 1f, 0.48f, 0.72f, 0.50f, 1f, 0.52f, 0.85f, 0.54f, 1f, 1f, 1f)
private val TWINKLE = floatArrayOf(0f, 0f, 0.4f, 1f, 1f, 0f)
private val FLUTTER = floatArrayOf(0f, -6f, 0.3f, 6f, 0.6f, -2f, 1f, -6f)

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * A repeating value (an angle, a drift) of the motion's clock, [value] at a given second, that comes
 * to rest as [ThemeMotion.live] falls. The resting pose is the multiple of [period] nearest to the
 * value when the stop began (review 13.11.2026: picking it per frame jumped a period when the fade
 * crossed a half). The result is taken modulo [period]: the drawing looks the same a period on.
 */
private inline fun settle(motion: ThemeMotion, period: Float, value: (Float) -> Float): Float {
    val rest = round(value(motion.stoppedAt) / period) * period
    val shown = rest + (value(motion.seconds) - rest) * motion.live
    return shown - floor(shown / period) * period
}

// ---------------------------------------------------------------- timeline track

/**
 * The theme's timeline track over this element (the timeline row), the track centred vertically:
 * chapter segments with 4 dp gaps (laid out at their real time positions, so the fill ends at the
 * thumb's centre), the passed part and the theme's decoration. Autumn is the track as it was before
 * DEC-061 and never moves.
 */
internal fun Modifier.themedTimeline(
    palette: ThemePalette,
    colors: NookastColors,
    segments: List<Pair<Float, Float>>,
    fraction: Float,
    motion: ThemeMotion,
    scale: Float,
): Modifier = drawWithCache {
    val unit = 1.dp.toPx() * scale
    val track = TrackGeometry(timelineSpans(segments, size.width, 4f * unit), fraction * size.width, size.height / 2f, unit, colors, 0.75.dp.toPx())
    val draw: DrawScope.() -> Unit = when (palette) {
        ThemePalette.AUTUMN -> autumnTrack(track)
        ThemePalette.LAVENDER -> lavenderTrack(track, motion)
        ThemePalette.SIGNATURE -> signatureTrack(track)
        ThemePalette.WINTER -> winterTrack(track, motion)
        ThemePalette.MINT -> mintTrack(track, fraction, motion)
        ThemePalette.ON_AIR -> onAirTrack(track, motion)
        ThemePalette.FIREFLY -> fireflyTrack(track, fraction, motion)
        ThemePalette.BEACON -> beaconTrack(track)
    }
    onDrawBehind(draw)
}

/** Segment bounds in px, from, to, from, to…; [gap] split between neighbours. */
private fun timelineSpans(segments: List<Pair<Float, Float>>, width: Float, gap: Float): FloatArray {
    val out = FloatArray(segments.size * 2)
    var n = 0
    segments.forEachIndexed { index, (start, end) ->
        val from = start * width + if (index == 0) 0f else gap / 2f
        val to = end * width - if (index == segments.lastIndex) 0f else gap / 2f
        if (to > from) {
            out[n++] = from
            out[n++] = to
        }
    }
    return out.copyOf(n)
}

private class TrackGeometry(val spans: FloatArray, val thumbX: Float, val cy: Float, val unit: Float, val colors: NookastColors, outlinePx: Float) {
    val outline = Stroke(outlinePx)
}

/** Straight segments of [thickness]; at night a faint outline; [fill] paints each passed piece (from, right). */
private inline fun DrawScope.straightTrack(t: TrackGeometry, thickness: Float, outline: Boolean = true, fill: DrawScope.(Float, Float) -> Unit) {
    val radius = CornerRadius(thickness / 2f)
    val top = t.cy - thickness / 2f
    var i = 0
    while (i < t.spans.size) {
        val from = t.spans[i]
        val to = t.spans[i + 1]
        val segment = Size(to - from, thickness)
        drawRoundRect(t.colors.track, Offset(from, top), segment, radius)
        if (outline && t.colors.isDark) drawRoundRect(t.colors.divider.scaleAlpha(0.5f), Offset(from, top), segment, radius, style = t.outline)
        if (t.thumbX > from) fill(from, t.thumbX.coerceAtMost(to))
        i += 2
    }
}

/** The passed parts of the spans, as a clip: [block] runs once per passed piece. */
private inline fun DrawScope.passedClips(t: TrackGeometry, block: DrawScope.() -> Unit) {
    var i = 0
    while (i < t.spans.size) {
        val from = t.spans[i]
        if (t.thumbX > from) clipRect(left = from, top = 0f, right = t.thumbX.coerceAtMost(t.spans[i + 1]), bottom = size.height, block = block)
        i += 2
    }
}

/** Autumn: the track of before DEC-061, unchanged. */
private fun CacheDrawScope.autumnTrack(t: TrackGeometry): DrawScope.() -> Unit {
    val thickness = 4f * t.unit
    val top = t.cy - thickness / 2f
    val glow = GlowPaint(6.dp.toPx())
    val fill = t.colors.trackFill
    val radius = CornerRadius(thickness / 2f)
    return {
        straightTrack(t, thickness) { from, right ->
            if (t.colors.isDark) glow.roundRect(this, fill.copy(alpha = 0.5f), Offset(from, top), Size(right - from, thickness), thickness / 2f)
            drawRoundRect(fill, Offset(from, top), Size(right - from, thickness), radius)
        }
    }
}

/** Lavender: a lilac gradient fill; while it plays, the prism leaves a thin spectrum behind the gem. */
private fun CacheDrawScope.lavenderTrack(t: TrackGeometry, motion: ThemeMotion): DrawScope.() -> Unit {
    val thickness = 4f * t.unit
    val top = t.cy - thickness / 2f
    val radius = CornerRadius(thickness / 2f)
    val glow = GlowPaint(6.dp.toPx())
    val glowColor = t.colors.trackFill.copy(alpha = 0.5f)
    val fill = Brush.horizontalGradient(listOf(t.colors.accentStrong, t.colors.accent), 0f, t.thumbX)
    val length = 58f * t.unit
    val height = 6f * t.unit
    val spectrumTopLeft = Offset(t.thumbX - length, t.cy - height / 2f)
    val spectrumSize = Size(length, height)
    val spectrum = Brush.horizontalGradient(
        0f to Color.Transparent,
        0.30f to Color(0x55FF5F6D),
        0.45f to Color(0x55FFD166),
        0.58f to Color(0x555EF08F),
        0.72f to Color(0x664CC9FF),
        1f to Color(0x99A78BFA),
        startX = t.thumbX - length,
        endX = t.thumbX,
    )
    val blend = if (t.colors.isDark) BlendMode.Screen else BlendMode.SrcOver
    return {
        straightTrack(t, thickness) { from, right ->
            if (t.colors.isDark) glow.roundRect(this, glowColor, Offset(from, top), Size(right - from, thickness), thickness / 2f)
            drawRoundRect(fill, Offset(from, top), Size(right - from, thickness), radius)
        }
        val live = motion.live
        // Clipped to the row: near the start the trail must not run left of the track.
        if (live > 0.001f) clipRect { drawRoundRect(spectrum, spectrumTopLeft, spectrumSize, CornerRadius(height / 2f), alpha = live, blendMode = blend) }
    }
}

/** A sine path across the width: y = cy + amp·sin(2πx/λ + shift), sampled every 2 dp. */
private fun CacheDrawScope.sinePath(cy: Float, amplitude: Float, wavelength: Float, shift: Float, unit: Float): Path = Path().apply {
    val step = 2f * unit
    var x = 0f
    moveTo(0f, cy + amplitude * sin(shift))
    while (x < size.width) {
        x = (x + step).coerceAtMost(size.width)
        lineTo(x, cy + amplitude * sin(2f * PI.toFloat() * x / wavelength + shift))
    }
}

/** Signature: a thin line; the passed path is two wavy strands, lavender and peach, plaited. Still. */
private fun CacheDrawScope.signatureTrack(t: TrackGeometry): DrawScope.() -> Unit {
    val lavender = sinePath(t.cy, 4f * t.unit, 48f * t.unit, 0f, t.unit)
    val peach = sinePath(t.cy, 4f * t.unit, 48f * t.unit, PI.toFloat(), t.unit)
    val strand = Stroke(2.4f * t.unit, cap = StrokeCap.Round)
    val glow = GlowPaint(5.dp.toPx(), strokePx = 2.4f * t.unit)
    val glowColor = if (t.colors.isDark) Color(0x88FFB497) else Color(0x66E8896A)
    return {
        var i = 0
        while (i < t.spans.size) {
            drawLine(t.colors.track, Offset(t.spans[i], t.cy), Offset(t.spans[i + 1], t.cy), strokeWidth = 2f * t.unit, cap = StrokeCap.Round)
            i += 2
        }
        passedClips(t) {
            glow.path(this, glowColor, lavender)
            drawPath(lavender, SIGNATURE_LAVENDER, style = strand)
            drawPath(peach, SIGNATURE_PEACH, style = strand)
        }
    }
}

private val SIGNATURE_LAVENDER = Color(0xFFC9B2F0)
private val SIGNATURE_PEACH = Color(0xFFFFB497)

/** Winter: the passed part is frosted: an ice gradient under two drifting lattices of sparkles. */
private fun CacheDrawScope.winterTrack(t: TrackGeometry, motion: ThemeMotion): DrawScope.() -> Unit {
    val unit = t.unit
    val height = 5.3f * unit
    val top = t.cy - height / 2f
    val radius = CornerRadius(height / 2f)
    val ice = Brush.horizontalGradient(0f to t.colors.accentStrong, 0.7f to t.colors.accent, 1f to WINTER_ICE, startX = 0f, endX = t.thumbX.coerceAtLeast(1f))
    val glow = GlowPaint(7.dp.toPx())
    val glowColor = t.colors.accent.copy(alpha = if (t.colors.isDark) 0.53f else 0.33f)
    val periodA = 11.5f * unit
    val periodB = 8.3f * unit
    return {
        straightTrack(t, 4f * unit) { _, _ -> }
        // The lattices drift while it plays and settle back to their resting place when it stops.
        val shiftA = settle(motion, periodA) { it / 4f * 23f * unit }
        val shiftB = periodB - settle(motion, periodB) { it / 4f * 16.6f * unit }
        var i = 0
        while (i < t.spans.size) {
            val from = t.spans[i]
            if (t.thumbX > from) {
                val right = t.thumbX.coerceAtMost(t.spans[i + 1])
                glow.roundRect(this, glowColor, Offset(from, top), Size(right - from, height), height / 2f)
                drawRoundRect(ice, Offset(from, top), Size(right - from, height), radius)
                clipRect(from, top, right, top + height) {
                    var x = shiftA - periodA + 0.2f * periodA
                    while (x < right) {
                        if (x > from) drawCircle(WINTER_SPARK, 0.9f * unit, Offset(x, top + 0.4f * height))
                        x += periodA
                    }
                    x = shiftB - periodB + 0.7f * periodB
                    while (x < right) {
                        if (x > from) drawCircle(WINTER_SPARK_FAINT, 0.72f * unit, Offset(x, top + 0.7f * height))
                        x += periodB
                    }
                }
            }
            i += 2
        }
    }
}

private val WINTER_ICE = Color(0xFFE0F2FE)
private val WINTER_SPARK = Color(0xCCFFFFFF)
private val WINTER_SPARK_FAINT = Color(0x99FFFFFF)

/** Mint: a stem; dew drops shine on its passed part and swell in turn while it plays. */
private fun CacheDrawScope.mintTrack(t: TrackGeometry, fraction: Float, motion: ThemeMotion): DrawScope.() -> Unit {
    val unit = t.unit
    val thickness = 2.7f * unit
    val top = t.cy - thickness / 2f
    val radius = CornerRadius(thickness / 2f)
    val fill = Brush.horizontalGradient(listOf(t.colors.accentStrong, t.colors.accent), 0f, t.thumbX)
    val dew = if (t.colors.isDark) Color(0xFFA7F3D0) else Color(0xFF6EE7B7)
    val glow = t.colors.accent.copy(alpha = if (t.colors.isDark) 0.53f else 0.33f)
    val r = 2.9f * unit
    val light = Offset(-0.3f * r, -0.4f * r)
    // One drop at the origin, moved and swollen per frame.
    val halo = Brush.radialGradient(listOf(glow, glow.copy(alpha = 0f)), Offset.Zero, r * 2.2f)
    val drop = Brush.radialGradient(0f to Color.White, 0.2f to Color.White, 0.45f to dew, 0.72f to dew.copy(alpha = 0f), center = light, radius = r * 1.5f)
    return {
        straightTrack(t, thickness) { from, right -> drawRoundRect(fill, Offset(from, top), Size(right - from, thickness), radius) }
        val live = motion.live
        val seconds = motion.seconds
        for (k in 1..11) {
            val at = k * 0.083f
            if (at >= fraction - 0.02f) break
            val swell = live * (1f - cos(2f * PI.toFloat() * phase(seconds, 3f, -k * 0.37f))) / 2f
            translate(at * size.width, t.cy) {
                scale(1f + 0.35f * swell, Offset.Zero) {
                    drawCircle(halo, r * 2.2f, Offset.Zero)
                    drawCircle(drop, r, Offset.Zero)
                    if (swell > 0.01f) drawCircle(Color.White, r * 0.6f, light, alpha = 0.3f * swell)
                }
            }
        }
    }
}

/** On Air: a radio dial over the track; the passed ticks and the fill are neon that flickers like gas in a tube. */
private fun CacheDrawScope.onAirTrack(t: TrackGeometry, motion: ThemeMotion): DrawScope.() -> Unit {
    val unit = t.unit
    val bottom = t.cy - 4f * unit
    val major = 7f * unit
    val tickWidth = 1f * unit
    val dim = t.colors.textSecondary.copy(alpha = 0.55f)
    val neonHalo = t.colors.accent.copy(alpha = 0.3f)
    val thickness = 4f * unit
    val top = t.cy - thickness / 2f
    val radius = CornerRadius(thickness / 2f)
    val fill = Brush.horizontalGradient(listOf(t.colors.accentStrong, t.colors.accent), 0f, t.thumbX)
    val glow = GlowPaint(9.dp.toPx())
    val core = Color.White.copy(alpha = 0.35f)
    return {
        fun ticks(color: Color, width: Float) {
            for (i in 0..40) {
                val x = (i * 0.025f * size.width).coerceIn(tickWidth / 2f, size.width - tickWidth / 2f)
                val height = if (i % 4 == 0) major else major * 0.45f
                drawLine(color, Offset(x, bottom - height), Offset(x, bottom), strokeWidth = width)
            }
        }
        ticks(dim, tickWidth)
        if (t.thumbX > 0f) {
            clipRect(right = t.thumbX) {
                ticks(neonHalo, tickWidth * 3f)
                ticks(t.colors.accent, tickWidth)
            }
        }
        val alpha = lerp(1f, keyframes(phase(motion.seconds, 2.6f), FLICKER), motion.live)
        straightTrack(t, thickness) { from, right ->
            glow.roundRect(this, t.colors.accent.copy(alpha = 0.6f * alpha), Offset(from, top), Size(right - from, thickness), thickness / 2f)
            drawRoundRect(fill, Offset(from, top), Size(right - from, thickness), radius, alpha = alpha)
            val inner = (right - from - thickness).coerceAtLeast(0f)
            if (inner > 0f) drawLine(core, Offset(from + thickness / 2f, t.cy), Offset(from + thickness / 2f + inner, t.cy), strokeWidth = 0.8f * unit, alpha = alpha)
        }
    }
}

/** One firefly of the trail: the mock's seeded layout (x, y, size, opacity, drift timing). */
private class Firefly(val x: Float, val y: Float, val size: Float, val opacity: Float, val period: Float, val delay: Float, val dx: Float, val dy: Float)

/** The mock's 26 fireflies, from its own linear congruential generator (seed 7). */
private val Fireflies: List<Firefly> by lazy {
    var seed = 7L
    fun rnd(): Float {
        seed = (seed * 9301 + 49297) % 233280
        return seed / 233280f
    }
    List(26) {
        val x = rnd()
        val y = (rnd() - 0.5f) * 0.7f
        val size = 0.6f + rnd() * 1.3f
        val opacity = 0.35f + rnd() * 0.65f
        val period = 2f + rnd() * 3f
        val delay = -(rnd() * 3f)
        val dx = (rnd() - 0.5f) * 3f
        val dy = (rnd() - 0.5f) * 3f
        Firefly(x, y, size, opacity, period, delay, dx, dy)
    }
}

/** Firefly: a thin stem; fireflies mark the passed path, blinking and floating while it plays. */
private fun CacheDrawScope.fireflyTrack(t: TrackGeometry, fraction: Float, motion: ThemeMotion): DrawScope.() -> Unit {
    val unit = t.unit
    val thickness = 2f * unit
    val top = t.cy - thickness / 2f
    val radius = CornerRadius(thickness / 2f)
    val dim = t.colors.accent.copy(alpha = if (t.colors.isDark) 0.53f else 0.4f)
    val fill = Brush.horizontalGradient(listOf(dim.copy(alpha = 0f), dim), 0f, t.thumbX)
    // One halo of radius 1 at the origin, moved and scaled to each firefly.
    val halo = Brush.radialGradient(listOf(t.colors.accent.copy(alpha = 0.8f), t.colors.accent.copy(alpha = 0f)), Offset.Zero, 1f)
    val flies = Fireflies
    return {
        straightTrack(t, thickness, outline = false) { from, right -> drawRoundRect(fill, Offset(from, top), Size(right - from, thickness), radius) }
        val live = motion.live
        val seconds = motion.seconds
        for (fly in flies) {
            if (fly.x >= fraction - 0.02f) continue
            val drift = pingPong(seconds, fly.period, fly.delay)
            // Opacity dims to 0.15 half way and comes back (the mock's keyframes over one direction).
            val alpha = lerp(fly.opacity, lerp(fly.opacity, 0.15f, 1f - abs(2f * drift - 1f)), live)
            val r = fly.size * 1.3f * unit
            translate(fly.x * size.width + fly.dx * 2.7f * unit * drift * live, t.cy + fly.y * 28f * unit + fly.dy * 2.7f * unit * drift * live) {
                scale(r + 4f * unit, Offset.Zero) { drawCircle(halo, 1f, Offset.Zero, alpha = alpha) }
                drawCircle(t.colors.accent, r, Offset.Zero, alpha = alpha)
            }
        }
    }
}

/** Beacon: the track is a sine wave; its passed part is lit orange. Still. */
private fun CacheDrawScope.beaconTrack(t: TrackGeometry): DrawScope.() -> Unit {
    val wave = sinePath(t.cy, BEACON_AMPLITUDE_DP * t.unit, BEACON_WAVELENGTH_DP * t.unit, 0f, t.unit)
    val base = Stroke(2.2f * t.unit, cap = StrokeCap.Round)
    val lit = Stroke(2.6f * t.unit, cap = StrokeCap.Round)
    val glow = GlowPaint(6.dp.toPx(), strokePx = 2.6f * t.unit)
    val glowColor = t.colors.accent.copy(alpha = 0.7f)
    return {
        var i = 0
        while (i < t.spans.size) {
            clipRect(left = t.spans[i], top = 0f, right = t.spans[i + 1], bottom = size.height) { drawPath(wave, t.colors.track, style = base) }
            i += 2
        }
        passedClips(t) {
            glow.path(this, glowColor, wave)
            drawPath(wave, t.colors.accent, style = lit)
        }
    }
}

private const val BEACON_AMPLITUDE_DP = 3.5f
private const val BEACON_WAVELENGTH_DP = 60f

/** Where the Beacon wave is at [x] px from the start, in px from the row's centre ([unit] = 1 dp × scale). */
internal fun beaconWaveDy(x: Float, unit: Float): Float =
    BEACON_AMPLITUDE_DP * unit * sin(2f * PI.toFloat() * x / (BEACON_WAVELENGTH_DP * unit))

// ---------------------------------------------------------------- thumbs and their motion

/** Lavender: four sparks twinkle in turn around the gem while it plays (an overlay of the gem). */
internal fun Modifier.lavenderSparks(scale: Float, motion: ThemeMotion): Modifier = drawWithCache {
    val unit = 1.dp.toPx() * scale
    onDrawBehind {
        val live = motion.live
        if (live <= 0.001f) return@onDrawBehind
        for (index in 0 until 4) {
            val shine = keyframes(phase(motion.seconds, 2.4f, index * 0.6f), TWINKLE)
            if (shine <= 0.01f) continue
            translate(center.x + LAVENDER_SPARKS[index * 2] * unit, center.y + LAVENDER_SPARKS[index * 2 + 1] * unit) {
                scale(3f * unit * (0.4f + 0.6f * shine), Offset.Zero) {
                    drawPath(SparkArt.star, Color.White, alpha = shine * live)
                    drawCircle(SparkArt.glow, 0.7f, Offset.Zero, alpha = 0.6f * shine * live)
                }
            }
        }
    }
}

private val LAVENDER_SPARKS = floatArrayOf(-18f, -15f, 12.6f, -12f, -12f, 14f, 11f, 12.6f)

/** A four-ray sparkle of reach 1 at the origin, its rays on the diagonals, and its soft light. */
private object SparkArt {
    val star: Path by lazy {
        Path().apply {
            for (i in 0 until 8) {
                val angle = PI.toFloat() / 4f + i * PI.toFloat() / 4f
                val r = if (i % 2 == 0) 1f else 0.18f
                if (i == 0) moveTo(r * cos(angle), r * sin(angle)) else lineTo(r * cos(angle), r * sin(angle))
            }
            close()
        }
    }
    val glow: Brush by lazy { Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), Offset.Zero, 0.7f) }
}

/** Signature: the disc's peach halo, breathing while it plays (1.25 and 0.7 at the top of a 3 s breath). */
internal fun Modifier.signatureHalo(r: Float, glow: Float, motion: ThemeMotion): Modifier = drawWithCache {
    val center = size.center
    val radius = r * density
    val brush = signatureHaloBrush(center, radius, glow)
    val haloCenter = signatureHaloCenter(center, radius)
    onDrawBehind {
        val b = motion.live * (1f - cos(2f * PI.toFloat() * phase(motion.seconds, 3f))) / 2f
        scale(1f + 0.25f * b, haloCenter) { drawCircle(brush, radius * 1.9f, haloCenter, alpha = 1f - 0.3f * b) }
    }
}

/** Winter: the snowflake turns once in 14 s, and comes to rest at a whole ray (60°) when it stops. */
internal fun winterTurn(motion: ThemeMotion): Float = settle(motion, 60f) { it / 14f * 360f }

/** Winter: three tiny snowflakes fall from the thumb disc of [disc] dp while it plays. */
internal fun Modifier.winterSnow(disc: Float, scale: Float, motion: ThemeMotion, dark: Boolean): Modifier = drawWithCache {
    val unit = 1.dp.toPx() * scale
    val box = disc * unit
    val origin = Offset((size.width - box) / 2f, (size.height - box) / 2f)
    val flake = if (dark) SnowArt.night else SnowArt.day
    val core = if (dark) Color.White else WINTER_DAY_FLAKE
    val r = 1.3f * unit
    onDrawBehind {
        val live = motion.live
        if (live <= 0.001f) return@onDrawBehind
        for (index in 0 until 3) {
            val p = phase(motion.seconds, 3f, index.toFloat())
            val alpha = (if (p < 0.2f) p / 0.2f * 0.9f else 0.9f * (1f - (p - 0.2f) / 0.8f)) * live
            translate(origin.x + SNOW_STARTS[index] * box - 7.5f * unit * p, origin.y + 0.6f * box + 20f * unit * p) {
                scale(r * 3f, Offset.Zero) { drawCircle(flake, 1f, Offset.Zero, alpha = alpha) }
                drawCircle(core, r, Offset.Zero, alpha = alpha)
            }
        }
    }
}

private val SNOW_STARTS = floatArrayOf(0.4f, 0.1f, 0.7f)
private val WINTER_DAY_FLAKE = Color(0xFF7DD3FC)

private object SnowArt {
    val night: Brush by lazy { Brush.radialGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0f)), Offset.Zero, 1f) }
    val day: Brush by lazy { Brush.radialGradient(listOf(WINTER_DAY_FLAKE.copy(alpha = 0.7f), WINTER_DAY_FLAKE.copy(alpha = 0f)), Offset.Zero, 1f) }
}

/** Mint: the leaf flutters about its stalk (−6°, 6°, −2° over 2.2 s); at rest it is straight. */
internal fun mintFlutter(motion: ThemeMotion): Float = motion.live * keyframes(phase(motion.seconds, 2.2f), FLUTTER)

/**
 * On Air: a tuning needle with a capsule lamp at its tip, centred on this element; the lamp pulses
 * while it plays (its glow grows and fades over 1.4 s, alternate).
 */
internal fun Modifier.onAirNeedle(scale: Float, colors: NookastColors, motion: ThemeMotion): Modifier = drawWithCache {
    val center = size.center
    val unit = 1.dp.toPx() * scale
    val width = 3.2f * unit
    // As tall as the row: the lamp then stays inside the 6 dp above it (review 13.11.2026: 30 dp touched the title).
    val height = 24f * unit
    val top = center.y - height / 2f
    val needle = Offset(center.x - width / 2f, top)
    val needleSize = Size(width, height)
    val core = if (colors.isDark) Color(0xFFFFE4F1) else Color(0xFFFDF2F8)
    val needleBrush = Brush.verticalGradient(listOf(core, colors.accent), top, top + height)
    val needleGlow = GlowPaint(7.dp.toPx())
    val needleGlowColor = colors.accent.copy(alpha = 0.8f)
    val lampSize = Size(13f * unit, 8f * unit)
    val lamp = Offset(center.x - lampSize.width / 2f, top - lampSize.height / 2f)
    val lampCenter = Offset(center.x, top)
    val lampGlow = GlowPaint(10.8f * unit)
    val ring = 1.8f * unit
    val ringTopLeft = lamp - Offset(ring, ring)
    val ringSize = Size(lampSize.width + ring * 2f, lampSize.height + ring * 2f)
    onDrawBehind {
        needleGlow.roundRect(this, needleGlowColor, needle, needleSize, width / 2f)
        drawRoundRect(needleBrush, needle, needleSize, CornerRadius(width / 2f))
        val pulse = pingPong(motion.seconds, 1.4f)
        val grow = lerp(1f, lerp(0.75f, 1.35f, pulse), motion.live)
        val shine = lerp(1f, lerp(0.7f, 1f, pulse), motion.live)
        scale(grow, lampCenter) { lampGlow.roundRect(this, colors.accent.copy(alpha = shine), lamp, lampSize, lampSize.height / 2f) }
        drawRoundRect(colors.background, ringTopLeft, ringSize, CornerRadius(lampSize.height / 2f + ring))
        drawRoundRect(colors.accent, lamp, lampSize, CornerRadius(lampSize.height / 2f))
    }
}

/** Firefly: the brightest firefly, a glowing ball whose halo breathes while it plays (2.4 s). */
internal fun Modifier.fireflyBall(scale: Float, colors: NookastColors, motion: ThemeMotion): Modifier = drawWithCache {
    val center = size.center
    val unit = 1.dp.toPx() * scale
    val r = 7f * unit
    val glow = GlowPaint(16f * unit)
    val glowColor = colors.accent.copy(alpha = 0.75f)
    val halo = if (colors.isDark) Color(0x26DCF27A) else Color(0x225E7F2C)
    val ball = Brush.radialGradient(
        0f to Color(0xFFFFFBE0),
        0.45f to colors.accent,
        0.8f to colors.accentStrong,
        1f to colors.accentStrong,
        center = center + Offset(-0.2f * r, -0.24f * r),
        radius = r * 1.25f,
    )
    onDrawBehind {
        val breath = motion.live * (1f - cos(2f * PI.toFloat() * phase(motion.seconds, 2.4f))) / 2f
        glow.circle(this, glowColor, center, r * (1f + 0.4f * breath))
        drawCircle(halo, r + lerp(5f, 9.4f, breath) * unit, center, alpha = 1f - breath)
        drawCircle(ball, r, center)
    }
}

/**
 * Beacon: a small lighthouse standing on the wave at this element's centre (its foot a fifth of its
 * height below); its beam turns once in 7 s while it plays and comes to rest when it stops.
 * [compact] (the settings row, the empty state) centres the house and shortens the beam.
 */
internal fun Modifier.beaconLighthouse(scale: Float, colors: NookastColors, motion: ThemeMotion, compact: Boolean): Modifier = drawWithCache {
    val center = size.center
    val unit = 1.dp.toPx() * scale
    // One unit of the 20 × 32 house drawing: 11 × 17.6 dp, its top inside the 6 dp above the row even on a crest.
    val k = 0.55f * unit
    val topLeft = Offset(center.x - 10f * k, center.y - (if (compact) 0.5f else 0.8f) * 32f * k)
    val lamp = topLeft + Offset(10f * k, 4.8f * k)
    val reach = (if (compact) 26f else BEACON_BEAM_DP) * unit
    val beam = if (colors.isDark) Color(0x66F4B183) else Color(0x40C6601F)
    // The mock's conic beam (peaks at 80° and 260° from the top, 18° either side), faded out radially.
    val fade = Brush.radialGradient(0f to beam, 0.08f to beam, 0.68f to beam.copy(alpha = 0f), center = lamp, radius = reach)
    val arcTopLeft = lamp - Offset(reach, reach)
    val arcSize = Size(reach * 2f, reach * 2f)
    val lampGlow = GlowPaint(4.dp.toPx())
    val lampGlowColor = colors.accent.copy(alpha = 0.9f)
    // The mock's drop shadow (0 .8cqw .8cqw): about 2 dp on screen, drawn in house units.
    val shadow = GlowPaint(2f * unit / k)
    val shadowColor = Color.Black.copy(alpha = 0.45f)
    onDrawBehind {
        rotate(settle(motion, 180f) { it / 7f * 360f }, lamp) {
            var p = 0
            while (p < BEAM_PEAKS.size) {
                for (slice in -5..5) {
                    drawArc(
                        fade,
                        startAngle = BEAM_PEAKS[p] - 90f + slice * 3f - 1.5f,
                        sweepAngle = 3f,
                        useCenter = true,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        alpha = 0.9f * (1f - abs(slice) / 6f),
                    )
                }
                p++
            }
        }
        lampGlow.circle(this, lampGlowColor, lamp, 3.5f * unit)
        translate(topLeft.x, topLeft.y) {
            scale(k, Offset.Zero) {
                translate(0f, 1.1f) { shadow.path(this, shadowColor, BeaconArt.body) }
                drawPath(BeaconArt.roof, BEACON_RUST)
                drawRoundRect(BEACON_LANTERN, Offset(5.5f, 5f), Size(9f, 6f), CornerRadius(1f))
                drawPath(BeaconArt.body, BEACON_WALL)
                drawPath(BeaconArt.stripes, BEACON_RUST)
                drawRoundRect(BEACON_ROCK, Offset(2f, 30f), Size(16f, 2f), CornerRadius(1f))
            }
        }
    }
}

/** The beam's reach on the timeline: kept short of the title above and the times below (review 13.11.2026). */
private const val BEACON_BEAM_DP = 40f
private val BEAM_PEAKS = floatArrayOf(80f, 260f)

private object BeaconArt {
    private fun path(data: String) = PathParser().parsePathString(data).toPath()
    val roof by lazy { path("M10 1l5 4H5z") }
    val body by lazy { path("M5 11h10l2.2 20H2.8z") }
    val stripes by lazy { path("M4.6 15h10.8l.5 4.5H4.1zM3.7 23.5h12.6l.5 4.5H3.2z") }
}

private val BEACON_RUST = Color(0xFFC6601F)
private val BEACON_LANTERN = Color(0xFFF4B183)
private val BEACON_WALL = Color(0xFFEADCC8)
private val BEACON_ROCK = Color(0xFF0F3D3E)
