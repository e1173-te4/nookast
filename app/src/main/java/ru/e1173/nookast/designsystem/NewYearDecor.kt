package ru.e1173.nookast.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate

/**
 * New Year mood (DEC-018): a garland thrown over the Now Playing cover, light snowfall over Now
 * Playing and a dusting of snow on a few elements, from 25 December to 7 January inclusive by the
 * device's local date. The snow cap of the Stitch mockup was dropped
 * by the user on 29.10.2026. Settings can force it on to try it, or switch it off.
 */
enum class NewYearMode {
    DATES,
    ALWAYS,
    OFF;

    companion object {
        fun fromStoredValue(value: String?): NewYearMode = entries.firstOrNull { it.name == value } ?: DATES
    }
}

fun isNewYearSeason(date: LocalDate): Boolean =
    (date.monthValue == 12 && date.dayOfMonth >= 25) || (date.monthValue == 1 && date.dayOfMonth <= 7)

/**
 * By dates the mood belongs to the winter theme only (user, 29.10.2026); «Always» shows it in any
 * theme.
 */
fun newYearActive(mode: NewYearMode, date: LocalDate, winterTheme: Boolean = true): Boolean = when (mode) {
    NewYearMode.DATES -> winterTheme && isNewYearSeason(date)
    NewYearMode.ALWAYS -> true
    NewYearMode.OFF -> false
}

val LocalNewYear = compositionLocalOf { false }

/** System "remove animations": the snow stands still and the bulbs keep one colour (see [rememberReducedMotion]). */
@Composable
private fun motionReduced(): Boolean = rememberReducedMotion()

/**
 * A garland thrown over the cover by hand (user, 29.10.2026: the snow cap looked ugly and the
 * straight line along the top edge too neat). The wire hangs in three uneven swags from the top-left
 * corner, catches on the top-right corner and its end dangles down the right side. The cover keeps
 * its place and size; [cover] is its rectangle in this canvas.
 */
@Composable
fun NewYearGarland(cover: Rect, modifier: Modifier = Modifier) {
    val reduced = motionReduced()
    val cycle by rememberInfiniteTransition(label = "garland").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(GARLAND_CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "garlandCycle",
    )
    val dark = LocalNookastColors.current.isDark
    // The classic string of retro C9 bulbs (user, 29.10.2026: make it prettier, from a known
    // pattern): a twisted green wire, teardrop bulbs on dark sockets hanging under it, the five
    // traditional colours in turn, each with a soft halo and a slow twinkle of its own.
    Canvas(modifier) {
        val wire = garlandWire(cover)
        val wireColor = if (dark) Color(0xFF1F4A33) else Color(0xFF2E5E43)
        drawPath(wire, wireColor, style = Stroke(1.9f * density, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        // The second strand winds around the first.
        drawPath(
            wire,
            lerp(wireColor, Color.White, 0.22f),
            style = Stroke(
                1.1f * density,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2.6f * density, 2.6f * density)),
            ),
        )
        val measure = androidx.compose.ui.graphics.PathMeasure().apply { setPath(wire, false) }
        val spacing = 23f * density
        val count = (measure.length / spacing).toInt().coerceAtLeast(6)
        for (index in 1 until count) {
            val distance = measure.length * index / count
            val at = measure.getPosition(distance)
            val tangent = measure.getTangent(distance)
            val color = BULB_PALETTE[index % BULB_PALETTE.size]
            // Each bulb breathes on its own phase; reduced motion keeps them steady and lit.
            val phase = ((if (reduced) 0.25f else cycle) * 3f + index * 0.37f) % 1f
            val glow = if (reduced) 1f else 0.72f + 0.28f * (0.5f + 0.5f * kotlin.math.sin(phase * 2f * Math.PI.toFloat()))
            // A bulb hangs below the wire: half along the wire's slope, half towards the ground.
            val slope = Math.toDegrees(kotlin.math.atan2(tangent.y, tangent.x).toDouble()).toFloat()
            val sway = ((index * 37) % 23 - 11).toFloat()
            withTransform({ rotate(slope * 0.55f + sway, at) }) { drawBulb(at, color, glow) }
        }
    }
}

private fun DrawScope.drawBulb(at: Offset, color: Color, glow: Float) {
    val u = density
    val center = at + Offset(0f, 9.5f * u)
    drawCircle(
        Brush.radialGradient(
            listOf(color.copy(alpha = 0.42f * glow), color.copy(alpha = 0.12f * glow), Color.Transparent),
            center = center,
            radius = 15f * u,
        ),
        radius = 15f * u,
        center = center,
    )
    // Socket.
    drawRoundRect(
        Color(0xFF1C3A2A),
        topLeft = at + Offset(-2.3f * u, -0.6f * u),
        size = Size(4.6f * u, 4.4f * u),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.1f * u),
    )
    // The C9 teardrop: round shoulders, a soft point at the bottom.
    val top = at + Offset(0f, 3.4f * u)
    val bulb = Path().apply {
        moveTo(top.x - 2.2f * u, top.y)
        cubicTo(top.x - 5.2f * u, top.y + 3.2f * u, top.x - 3.4f * u, top.y + 9.4f * u, top.x, top.y + 12.2f * u)
        cubicTo(top.x + 3.4f * u, top.y + 9.4f * u, top.x + 5.2f * u, top.y + 3.2f * u, top.x + 2.2f * u, top.y)
        close()
    }
    drawPath(
        bulb,
        Brush.verticalGradient(
            listOf(lerp(color, Color.White, 0.18f * glow), color, lerp(color, Color.Black, 0.25f)),
            startY = top.y,
            endY = top.y + 12.2f * u,
        ),
        alpha = 0.78f + 0.22f * glow,
    )
    drawOval(
        Color.White.copy(alpha = 0.5f * glow),
        topLeft = top + Offset(-2.5f * u, 2.0f * u),
        size = Size(1.5f * u, 3.6f * u),
    )
}

private fun garlandWire(cover: Rect): Path {
    val w = cover.width
    val h = cover.height
    fun p(x: Float, y: Float) = Offset(cover.left + w * x, cover.top + h * y)
    val points = listOf(p(-0.015f, 0.03f), p(0.30f, 0.025f), p(0.55f, -0.01f), p(0.80f, 0.02f), p(1.012f, 0.0f))
    val sags = listOf(0.13f, 0.08f, 0.15f, 0.07f)
    return Path().apply {
        moveTo(points[0].x, points[0].y)
        for (index in 1 until points.size) {
            val a = points[index - 1]
            val b = points[index]
            val sag = h * sags[index - 1]
            // A hanging wire: two control points pulled down, the lower one nearer the heavier end.
            cubicTo(a.x + (b.x - a.x) * 0.28f, a.y + sag * 1.25f, a.x + (b.x - a.x) * 0.7f, b.y + sag * 1.1f, b.x, b.y)
        }
        // The loose end falls down the right side and curls a little away from the cover.
        val end = p(1.012f, 0f)
        cubicTo(end.x + w * 0.025f, end.y + h * 0.10f, end.x - w * 0.01f, end.y + h * 0.2f, end.x + w * 0.012f, end.y + h * 0.29f)
    }
}

/**
 * A light dusting of snow on an element's top edge (New Year): a few soft drifts of uneven length,
 * set apart from the rounded corners, as if it had snowed a little. Nothing outside the season.
 */
@Composable
fun Modifier.snowDusted(cornerRadius: androidx.compose.ui.unit.Dp = 0.dp, seed: Int = 0): Modifier {
    if (!LocalNewYear.current) return this
    val dark = LocalNookastColors.current.isDark
    return this.drawWithContent {
        drawContent()
        val corner = cornerRadius.toPx()
        val usable = size.width - corner * 2
        if (usable < 24f * density) return@drawWithContent
        var random = (seed * 7919 + (size.width.toInt() * 31) xor size.height.toInt()).let { if (it == 0) 1 else it }
        fun next(): Float {
            random = random xor (random shl 13); random = random xor (random ushr 17); random = random xor (random shl 5)
            return ((random ushr 1) % 10_000) / 10_000f
        }
        val drifts = 1 + (next() * 2.4f).toInt()
        repeat(drifts) { index ->
            val span = usable * (0.16f + next() * 0.22f)
            val start = corner + (usable - span) * ((index + next() * 0.8f) / drifts).coerceIn(0f, 1f)
            val height = (2.2f + next() * 2.3f) * density
            drawSnowDrift(start, span, height, dark)
        }
    }
}

private fun DrawScope.drawSnowDrift(start: Float, span: Float, height: Float, dark: Boolean) {
    val path = Path().apply {
        moveTo(start, 0.6f * density)
        // A low mound with a softer, flatter tail.
        cubicTo(start + span * 0.12f, -height * 0.9f, start + span * 0.35f, -height * 1.15f, start + span * 0.55f, -height * 0.85f)
        cubicTo(start + span * 0.72f, -height * 0.6f, start + span * 0.88f, -height * 0.35f, start + span, 0.6f * density)
        close()
    }
    drawPath(path, Color.Black.copy(alpha = if (dark) 0.25f else 0.08f), style = Stroke(1.2f * density))
    drawPath(
        path,
        Brush.verticalGradient(0f to Color.White, 1f to Color(0xFFDCEAF7), startY = -height * 1.15f, endY = 0.6f * density),
    )
}

/** Light snowfall over the whole screen: the mockup's 24 flakes with their sizes, speeds and drift. */
@Composable
fun NewYearSnowfall(modifier: Modifier = Modifier) {
    val reduced = motionReduced()
    val time by rememberInfiniteTransition(label = "snow").animateFloat(
        initialValue = 0f,
        targetValue = SNOW_LOOP_MS.toFloat(),
        animationSpec = infiniteRepeatable(tween(SNOW_LOOP_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "snowTime",
    )
    Canvas(modifier) {
        val unit = density
        FLAKES.forEachIndexed { index, flake ->
            val phase = if (reduced) {
                (index * 0.37f) % 1f
            } else {
                (((time / 1000f) + flake.delay) % flake.duration) / flake.duration
            }
            val fade = when {
                phase < 0.1f -> phase / 0.1f
                phase > 0.9f -> (1f - phase) / 0.1f
                else -> 1f
            }
            val x = size.width * flake.left + flake.drift * unit * phase
            val y = -20f * unit + (size.height + 40f * unit) * phase
            val radius = flake.size * unit / 2f
            drawCircle(
                Brush.radialGradient(
                    0f to Color.White, 0.3f to Color.White, 0.8f to Color(0xCCDCF0FF), 1f to Color.Transparent,
                    center = Offset(x, y), radius = radius,
                ),
                radius = radius,
                center = Offset(x, y),
                alpha = flake.opacity * fade,
            )
        }
    }
}

private data class Flake(val left: Float, val size: Float, val duration: Float, val delay: Float, val drift: Float, val opacity: Float)

private val FLAKES = listOf(
    Flake(.03f, 4.5f, 5.6f, .2f, 18f, .85f), Flake(.07f, 3f, 7.2f, 2.1f, -15f, .65f),
    Flake(.11f, 5.5f, 4.8f, 4.4f, 22f, .9f), Flake(.15f, 3.5f, 6.5f, 1.1f, -18f, .7f),
    Flake(.19f, 6f, 5.1f, 3.3f, 14f, .85f), Flake(.23f, 3f, 7.8f, 5.2f, -22f, .6f),
    Flake(.27f, 4.5f, 5.9f, .8f, 16f, .8f), Flake(.32f, 5f, 4.5f, 2.7f, -12f, .85f),
    Flake(.36f, 3.5f, 6.9f, 4.9f, 20f, .65f), Flake(.40f, 6.5f, 4.2f, 1.5f, -14f, .9f),
    Flake(.44f, 3f, 7.4f, 3.8f, 18f, .6f), Flake(.48f, 5f, 5.3f, .4f, -20f, .8f),
    Flake(.53f, 4f, 6.1f, 2.9f, 15f, .75f), Flake(.57f, 6f, 4.7f, 5.6f, -16f, .85f),
    Flake(.61f, 3.5f, 7.1f, 1.8f, 24f, .65f), Flake(.65f, 5.5f, 5.0f, 4.1f, -14f, .85f),
    Flake(.69f, 3f, 7.6f, .9f, 19f, .6f), Flake(.73f, 5f, 4.9f, 3.1f, -22f, .8f),
    Flake(.77f, 4f, 6.4f, 5.4f, 16f, .7f), Flake(.81f, 6.5f, 4.4f, 1.3f, -18f, .9f),
    Flake(.85f, 3.5f, 6.8f, 3.6f, 21f, .65f), Flake(.89f, 5f, 5.2f, .6f, -15f, .8f),
    Flake(.93f, 3.5f, 7.3f, 2.4f, 17f, .7f), Flake(.97f, 5.5f, 4.6f, 4.7f, -19f, .85f),
)

/** The traditional C9 string: red, gold, green, blue and pink in turn. */
private val BULB_PALETTE = listOf(
    Color(0xFFE53935),
    Color(0xFFFFC233),
    Color(0xFF2EAD5B),
    Color(0xFF2F80ED),
    Color(0xFFE0458B),
)
private const val GARLAND_CYCLE_MS = 12_000
private const val SNOW_LOOP_MS = 600_000
