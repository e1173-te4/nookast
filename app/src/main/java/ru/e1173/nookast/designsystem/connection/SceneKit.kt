package ru.e1173.nookast.designsystem.connection

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * The drawing kit every story shares (DEC-069, design/explorations/source-pill/final.html). The scene is
 * drawn in its own units: 284 × 176, one unit a dp of the phone card. Strokes, radii and sizes below
 * are in those units.
 */

/** The scene's width and height in its own units. */
const val SCENE_W = 284f
const val SCENE_H = 176f

/** The scene's colours: the theme's, the cover's and the status colours of DEC-069. */
@Immutable
data class SceneColors(
    val ink: Color,
    val muted: Color,
    val ok: Color,
    val bad: Color,
    val badText: Color,
    val warn: Color,
    val warnText: Color,
    val cover: Color,
    val coverText: Color,
    val sky1: Color,
    val sky2: Color,
    val surface: Color,
    val accent: Color,
    val dark: Boolean,
)

/** A stable pseudo-random number in 0…1 for the [i]-th thing of a scene (stars, grass, polyps). */
fun rnd(i: Int): Float = rnd(i.toFloat())

fun rnd(i: Float): Float = abs(((sin(i * 127.1 + 311.7) * 43758.5453) % 1.0).toFloat())

/** A path along which couriers travel: a point and its heading at [u] in 0…1. */
interface SceneRoute {
    fun at(u: Float): RoutePoint
    fun path(): Path
}

data class RoutePoint(val x: Float, val y: Float, val angle: Float) {
    val offset: Offset get() = Offset(x, y)
}

/** A quadratic curve from [p0] through [p1] to [p2]. */
class QuadRoute(val p0: Offset, val p1: Offset, val p2: Offset) : SceneRoute {
    override fun at(u: Float): RoutePoint {
        val v = 1 - u
        val x = v * v * p0.x + 2 * v * u * p1.x + u * u * p2.x
        val y = v * v * p0.y + 2 * v * u * p1.y + u * u * p2.y
        val dx = 2 * v * (p1.x - p0.x) + 2 * u * (p2.x - p1.x)
        val dy = 2 * v * (p1.y - p0.y) + 2 * u * (p2.y - p1.y)
        return RoutePoint(x, y, atan2(dy, dx))
    }

    override fun path(): Path = Path().apply {
        moveTo(p0.x, p0.y)
        quadraticTo(p1.x, p1.y, p2.x, p2.y)
    }
}

/** A polyline (the circuit board's traces). */
class PolyRoute(private val points: List<Offset>) : SceneRoute {
    private val lengths = points.zipWithNext { a, b -> hypot(b.x - a.x, b.y - a.y) }
    private val total = lengths.sum()

    override fun at(u: Float): RoutePoint {
        var d = u.coerceIn(0f, 1f) * total
        for (i in lengths.indices) {
            val a = points[i]
            val b = points[i + 1]
            val l = lengths[i]
            if (d <= l) {
                val k = if (l > 0f) d / l else 0f
                return RoutePoint(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k, atan2(b.y - a.y, b.x - a.x))
            }
            d -= l
        }
        val a = points[points.size - 2]
        val b = points.last()
        return RoutePoint(b.x, b.y, atan2(b.y - a.y, b.x - a.x))
    }

    override fun path(): Path = Path().apply {
        points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
    }
}

/**
 * A route that starts and ends on the edges of the things it joins, not in their centres: from [a]
 * (radius [ra]) to [b] (radius [rb]), bowed sideways by [bow].
 */
fun edgeRoute(a: Offset, ra: Float, b: Offset, rb: Float, bow: Float): QuadRoute {
    val mx = (a.x + b.x) / 2
    val my = (a.y + b.y) / 2
    val dx = b.x - a.x
    val dy = b.y - a.y
    val l = hypot(dx, dy).takeIf { it > 0f } ?: 1f
    val cp = Offset(mx - dy / l * bow, my + dx / l * bow)
    fun edge(p: Offset, r: Float): Offset {
        val vx = cp.x - p.x
        val vy = cp.y - p.y
        val vl = hypot(vx, vy).takeIf { it > 0f } ?: 1f
        return Offset(p.x + vx / vl * r, p.y + vy / vl * r)
    }
    return QuadRoute(edge(a, ra), cp, edge(b, rb))
}

/** The runway vessel: its bounding box and its outline grown by [grow]. */

/** [gloss]: the glass highlight, only on round vessels (DEC-070). */
class VesselShape(val box: Rect, val gloss: Boolean = true, private val outline: (grow: Float) -> Path) {
    fun shape(grow: Float = 0f): Path = outline(grow)
}

fun circleVessel(cx: Float, cy: Float, r: Float) = VesselShape(Rect(cx - r, cy - r, cx + r, cy + r)) { g ->
    Path().apply { addOval(Rect(Offset(cx, cy), r + g)) }
}

fun roundVessel(box: Rect, radius: Float, gloss: Boolean = true) = VesselShape(box, gloss) { g ->
    Path().apply {
        addRoundRect(RoundRect(box.left - g, box.top - g, box.right + g, box.bottom + g, CornerRadius(radius + g)))
    }
}

/** A soft round glow: [color] in the middle fading to nothing at [r]. */
fun DrawScope.blob(x: Float, y: Float, r: Float, color: Color) {
    if (r <= 0f || color.alpha <= 0f) return
    drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), Offset(x, y), r), r, Offset(x, y))
}

/** A four-pointed sparkle of half-size [s]. */
fun DrawScope.sparkle(x: Float, y: Float, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(x, y - s)
        quadraticTo(x + s * .18f, y - s * .18f, x + s, y)
        quadraticTo(x + s * .18f, y + s * .18f, x, y + s)
        quadraticTo(x - s * .18f, y + s * .18f, x - s, y)
        quadraticTo(x - s * .18f, y - s * .18f, x, y - s)
        close()
    }
    drawPath(p, color)
}

/** A ring with a [frac] arc from the top: how much of the episode a source holds. */
fun DrawScope.ringArc(x: Float, y: Float, r: Float, frac: Float, color: Color, ink: Color) {
    drawCircle(ink.copy(alpha = .12f), r, Offset(x, y), style = Stroke(2f))
    drawArc(color, -90f, frac.coerceIn(0f, 1f) * 360f, false, Offset(x - r, y - r), Size(2 * r, 2 * r), style = Stroke(2f, cap = StrokeCap.Round))
}

/** The dashed ring of a node being checked; it turns with time. */
fun DrawScope.checkingRing(x: Float, y: Float, r: Float, t: Float, ink: Color) {
    drawCircle(ink.copy(alpha = .5f), r, Offset(x, y), style = Stroke(1.4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), -t * 20f)))
}

/** The dotted ring of the node whose checks the card shows. */
fun DrawScope.focusRing(x: Float, y: Float, r: Float, color: Color) {
    drawCircle(color, r, Offset(x, y), style = Stroke(1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))))
}

/** Draws [block] at [alpha]; a whole layer, so overlapping parts do not add up. */
inline fun DrawScope.alphaLayer(alpha: Float, block: DrawScope.() -> Unit) {
    if (alpha >= 1f) {
        block()
        return
    }
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(-40f, -40f, SCENE_W + 40f, SCENE_H + 40f), Paint().apply { this.alpha = alpha })
        block()
        canvas.restore()
    }
}

/** An ellipse of radii [rx], [ry] at [x], [y], turned by [angle] radians. */
fun DrawScope.ellipse(x: Float, y: Float, rx: Float, ry: Float, angle: Float, color: Color) {
    rotate((angle * 180f / PI).toFloat(), Offset(x, y)) {
        drawOval(color, Offset(x - rx, y - ry), Size(2 * rx, 2 * ry))
    }
}

/** A stroke in scene units with round ends. */
fun line(width: Float, dash: FloatArray? = null, phase: Float = 0f) = Stroke(
    width,
    cap = StrokeCap.Round,
    join = StrokeJoin.Round,
    pathEffect = dash?.let { PathEffect.dashPathEffect(it, phase) },
)

/** Moves the origin to [x], [y] and turns by [angle] radians for [block]. */
inline fun DrawScope.at(x: Float, y: Float, angle: Float = 0f, block: DrawScope.() -> Unit) {
    translate(x, y) {
        if (angle == 0f) block() else rotate((angle * 180f / PI).toFloat(), Offset.Zero) { block() }
    }
}

/** [a] moved toward [b] by [k], opaque. */
fun mix(a: Color, b: Color, k: Float): Color = androidx.compose.ui.graphics.lerp(a.copy(alpha = 1f), b.copy(alpha = 1f), k)

/**
 * The quiet zones under the words (DEC-070): the picture dissolves into [top] over 14 + 14 k and into
 * [bottom] over 18 + 16 k, k the font scale up to 1.3. No patch under a word.
 */
fun DrawScope.veils(top: Color, bottom: Color, fontScale: Float) {
    val hT = 14f + 14f * fontScale
    val hB = 18f + 16f * fontScale
    drawRect(Brush.verticalGradient(0f to top.copy(alpha = .96f), .55f to top.copy(alpha = .84f), 1f to top.copy(alpha = 0f), startY = 0f, endY = hT), size = Size(SCENE_W, hT))
    drawRect(
        Brush.verticalGradient(0f to bottom.copy(alpha = 0f), .45f to bottom.copy(alpha = .84f), 1f to bottom.copy(alpha = .96f), startY = SCENE_H - hB, endY = SCENE_H),
        Offset(0f, SCENE_H - hB),
        Size(SCENE_W, hB),
    )
}

fun lerpF(a: Float, b: Float, k: Float) = a + (b - a) * k

fun clamp01(v: Float) = max(0f, min(1f, v))

fun cosF(v: Float) = cos(v)

fun sinF(v: Float) = sin(v)

const val PI_F = PI.toFloat()
