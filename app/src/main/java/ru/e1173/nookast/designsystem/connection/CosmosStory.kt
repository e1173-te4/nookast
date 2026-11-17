package ru.e1173.nookast.designsystem.connection

import android.content.res.Resources
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import ru.e1173.nookast.R
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * «Межзвёздная почта» (DEC-069): the episode is recorded on a distant star, mail comets bring it to
 * the hold, the postal moon is the server. Dust on the way: the host is out of reach from here; dust
 * everywhere: no internet. The orbits' distance is the answer time.
 */
object CosmosStory : ConnectionStory {
    override val id = "cosmos"
    override val strings = StoryStrings(
        name = R.string.story_cosmos_name,
        text = R.string.story_cosmos_text,
        suits = R.string.story_cosmos_suits,
        intro = R.string.story_cosmos_intro,
        flowWord = R.string.story_cosmos_flow,
        offlineWord = R.string.story_cosmos_offline,
    )

    private const val CX = 142f
    private const val CY = 84f
    private const val TR = 24f

    private class Star(val x: Float, val y: Float, val r: Float, val f: Float, val p: Float)

    private val stars = List(38) { i -> Star(rnd(i) * SCENE_W, rnd(i + 99) * SCENE_H, .45f + (i % 3) * .35f, .6f + (i % 5) * .35f, i * 1.7f) }

    override fun layout(env: SceneEnv): StoryLayout {
        val m = env.model
        fun radius(state: LinkState, ms: Long?): Float = when (state) {
            LinkState.FAIL, LinkState.NA -> 104f
            LinkState.RUNNING -> 90f
            LinkState.OK -> (60f + 24f * log10(((ms ?: 60L).coerceAtLeast(1L)) / 8f)).coerceIn(70f, 102f)
        }
        fun orbit(base: Float, r: Float): Offset {
            val a = (base + env.t * 3f) * PI.toFloat() / 180f
            return Offset(CX + r * cosF(a), CY - r * .42f * sinF(a))
        }
        val host = orbit(200f, radius(m.host, m.hostMs))
        val server = orbit(340f, radius(m.server, m.serverMs))
        return StoryLayout(
            host = host,
            server = server,
            vessel = circleVessel(CX, CY, TR),
            toVessel = { k ->
                val p = if (k == SourceKind.HOST) host else server
                edgeRoute(p, 15f, Offset(CX, CY), TR + 1, if (p.x < CX) -22f else 22f)
            },
            link = { edgeRoute(host, 15f, server, 15f, if (host.x < server.x) 46f else -46f) },
        )
    }

    override fun veil(env: SceneEnv): Pair<Color, Color> {
        val c = env.colors
        val top = mix(c.sky1, c.sky2, .25f)
        return (if (env.dark) top else mix(top, Color.White, .6f)) to mix(c.sky1, c.sky2, .75f)
    }

    override fun DrawScope.background(env: SceneEnv) {
        val c = env.colors
        drawRect(Brush.linearGradient(listOf(c.sky1, c.sky2), Offset.Zero, Offset(SCENE_W, SCENE_H)), size = Size(SCENE_W, SCENE_H))
        if (env.dark) {
            blob(SCENE_W * .82f, SCENE_H * .16f, 120f, c.cover.copy(alpha = if (env.offline) .04f else .14f))
            stars.forEach { st ->
                var a = .6f * (.35f + .65f * (.5f + .5f * sinF(env.t * st.f + st.p)))
                if (env.offline) a *= .12f
                if (env.local) a *= .6f
                drawCircle(Color.White.copy(alpha = a), st.r, Offset(st.x, st.y))
            }
        } else {
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .7f), Color.White.copy(alpha = 0f)), 0f, SCENE_H * .7f), size = Size(SCENE_W, SCENE_H))
            listOf(76f, 112f).forEach { r ->
                drawOval(c.ink.copy(alpha = .07f), Offset(CX - r, CY - r * .42f), Size(2 * r, 2 * r * .42f), style = Stroke(1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 5f))))
            }
            stars.take(22).forEachIndexed { i, st ->
                val col = c.ink.copy(alpha = (if (env.offline) .06f else .2f) * (.55f + .45f * sinF(env.t * st.f + st.p)).coerceIn(0f, 1f))
                if (i % 4 == 0) sparkle(st.x, st.y, 2.4f, col) else drawCircle(col, .8f, Offset(st.x, st.y))
            }
        }
    }

    override fun DrawScope.lane(route: SceneRoute, mode: LaneMode, env: SceneEnv) {
        val col = env.colors.cover.copy(alpha = when (mode) { LaneMode.LIVE -> .34f; LaneMode.BLOCKED -> .22f; LaneMode.IDLE -> .3f })
        var u = 0f
        while (u <= 1.001f) {
            drawCircle(col, .9f, route.at(u).offset)
            u += .045f
        }
    }

    override fun DrawScope.courier(route: SceneRoute, u: Float, size: Float, env: SceneEnv) = comet(route, u, size * 1.25f, env.colors.cover)

    /** A smooth tapered tail along the route, a bright core and a soft glow. */
    private fun DrawScope.comet(route: SceneRoute, u: Float, size: Float, col: Color) {
        val tail = .24f
        val n = 20
        val left = mutableListOf<Offset>()
        val right = mutableListOf<Offset>()
        for (k in 0..n) {
            val uu = u - tail * k / n
            if (uu < 0f) break
            val p = route.at(uu)
            val w = 2.6f * size * (1f - k.toFloat() / n).toDouble().pow(1.3).toFloat()
            left += Offset(p.x - sinF(p.angle) * w, p.y + cosF(p.angle) * w)
            right += Offset(p.x + sinF(p.angle) * w, p.y - cosF(p.angle) * w)
        }
        if (left.size < 2) return
        val head = route.at(u)
        val end = route.at(max(0f, u - tail))
        val shape = Path().apply {
            left.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            for (i in right.indices.reversed()) lineTo(right[i].x, right[i].y)
            close()
        }
        drawPath(
            shape,
            Brush.linearGradient(
                0f to col.copy(alpha = .95f),
                .35f to col.copy(alpha = .45f),
                1f to col.copy(alpha = 0f),
                start = head.offset,
                end = end.offset,
            ),
        )
        blob(head.x, head.y, 7f * size, col.copy(alpha = .55f))
        drawCircle(Color.White, 1.9f * size, head.offset)
    }

    /** Dust on the way: the star itself is fine, this device just cannot see it through the dust. */
    override fun DrawScope.obstruction(route: SceneRoute, env: SceneEnv) {
        val p = route.at(.5f)
        val puff = if (env.dark) Color(92, 82, 120) else Color(196, 188, 214)
        for (i in 0 until 5) blob(p.x + cosF(i * 1.3f + env.t * .3f) * 9f, p.y + sinF(i * 1.7f + env.t * .25f) * 6f, 13f + i % 3 * 4f, puff.copy(alpha = .6f))
        val mote = if (env.dark) Color.White.copy(alpha = .35f) else env.colors.ink.copy(alpha = .2f)
        for (i in 0 until 10) drawCircle(mote, .7f, Offset(p.x - 16f + rnd(i) * 32f + sinF(env.t + i) * 2f, p.y - 10f + rnd(i + 5) * 20f))
    }

    override fun DrawScope.offline(env: SceneEnv) {
        val puff = if (env.dark) Color(70, 60, 96) else Color(210, 202, 226)
        for (i in 0 until 11) {
            var x = (rnd(i + 7) * SCENE_W + env.t * (4 + i % 3) * (if (i % 2 == 1) 1 else -1)) % (SCENE_W + 60) - 30
            if (x < -30) x += SCENE_W + 60
            blob(x, 20f + rnd(i + 31) * (SCENE_H - 40f), 34f + rnd(i + 3) * 30f, puff.copy(alpha = .55f))
        }
    }

    override fun DrawScope.source(kind: SourceKind, at: Offset, info: SourceInfo, env: SceneEnv) {
        val (x, y) = at
        val c = env.colors
        if (kind == SourceKind.HOST) {
            info.share?.let { ringArc(x, y, 13f, it, c.cover, c.ink) }
            blob(x, y, 16f, (if (env.dark) Color(0xFFFFE3A3) else Color(0xFFF2B24E)).copy(alpha = if (env.local) .1f else if (env.dark) .45f else .32f))
            sparkle(x, y, 8f + sinF(env.t * 2f) * .5f, (if (env.dark) Color(0xFFFFF4D6) else Color(0xFFB7791F)).copy(alpha = if (env.local) .35f else 1f))
        } else {
            val fail = info.state == LinkState.FAIL
            if (info.share != null && info.isSource) ringArc(x, y, 13f, info.share, c.cover, c.ink)
            val lit = if (fail) (if (env.dark) Color(0xFFC0614E) else Color(0xFFB5523F)) else if (env.dark) Color(0xFFF6F3FF) else Color(0xFF4A4560)
            if (!fail && !env.local) blob(x, y, 15f, c.cover.copy(alpha = if (env.dark) .3f else .18f))
            // The moon's phase is how much of the episode is on the server; copper when it does not answer.
            val phase = if (fail) 1f else max(.14f, info.copy)
            alphaLayer(if (env.local) .35f else 1f) {
                val disc = Path().apply { addOval(Rect(Offset(x, y), 7.5f)) }
                clipPath(disc) {
                    drawRect(c.ink.copy(alpha = if (env.dark) .16f else .1f), Offset(x - 9f, y - 9f), Size(18f, 18f))
                    drawCircle(lit, 7.5f, Offset(x + 15f * (1 - phase), y))
                    if (fail) drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .35f)), Offset(x - 1f, y - 1f), 8f), Offset(x - 9f, y - 9f), Size(18f, 18f))
                }
            }
            drawCircle((if (fail) lit else c.ink).copy(alpha = .4f), 7.5f, Offset(x, y), style = Stroke(.8f))
        }
    }

    override fun DrawScope.checking(env: SceneEnv) {
        for (i in 0 until 2) {
            val p = (env.t * .55f + i / 2f) % 1f
            val r = 24f + p * 130f
            drawOval(env.colors.cover.copy(alpha = .4f * (1 - p)), Offset(CX - r, CY - r * .45f), Size(2 * r, 2 * r * .45f), style = Stroke(1.2f))
        }
    }

    /** One falling star when sound moves to the server's copy. */
    override fun DrawScope.flash(p: Float, layout: StoryLayout, env: SceneEnv) =
        comet(QuadRoute(Offset(-20f, 10f), Offset(140f, 20f), Offset(300f, 70f)), p, 1.1f, if (env.dark) Color.White else env.colors.cover)

    override fun DrawScope.glyph(kind: GlyphKind, fetch: Float, color: Color) {
        when (kind) {
            GlyphKind.DIRECT -> {
                drawPath(svgPath("M10 3.2q.6 5.4 6.8 6.8-6.2 1.4-6.8 6.8-.6-5.4-6.8-6.8 6.2-1.4 6.8-6.8z"), color)
                drawCircle(color.copy(alpha = color.alpha * .7f), 1.3f, Offset(16.6f, 16.2f))
            }
            GlyphKind.SERVER -> drawPath(svgPath("M15.4 12.8A6 6 0 1 1 7.2 4.6a5 5 0 0 0 8.2 8.2z"), color)
            GlyphKind.FETCH -> {
                drawCircle(color.copy(alpha = color.alpha * .22f), 5.6f, Offset(10f, 10f))
                clipPath(Path().apply { addOval(Rect(Offset(10f, 10f), 5.6f)) }) {
                    drawCircle(color, 5.6f, Offset(10f + 11.2f * (1 - fetch), 10f))
                }
            }
            GlyphKind.HOST_DOWN -> {
                drawPath(svgPath("M12 2.6q.5 4 5 5-4.5 1-5 5-.5-4-5-5 4.5-1 5-5z"), color.copy(alpha = color.alpha * .55f))
                drawCircle(color, 3f, Offset(6.2f, 13.6f))
                drawCircle(color, 3.3f, Offset(9.6f, 15f))
                drawCircle(color, 2.2f, Offset(4f, 15.8f))
            }
            GlyphKind.OFFLINE -> {
                drawCircle(color.copy(alpha = color.alpha * .55f), 3.2f, Offset(6.5f, 11f))
                drawCircle(color.copy(alpha = color.alpha * .75f), 4f, Offset(11f, 9f))
                drawCircle(color.copy(alpha = color.alpha * .55f), 2.8f, Offset(14.5f, 11.5f))
                drawCircle(color, .8f, Offset(4f, 6f))
                drawCircle(color, .8f, Offset(16f, 5.5f))
                drawCircle(color, .7f, Offset(9f, 15.5f))
            }
            GlyphKind.LOCAL -> {
                drawRoundRect(color, Offset(3.5f, 5.5f), Size(13f, 9.5f), androidx.compose.ui.geometry.CornerRadius(1.8f), style = Stroke(1.5f))
                drawPath(svgPath("M3.8 6.2 10 11l6.2-4.8"), color, style = line(1.5f))
            }
        }
    }

    override fun sizeNote(resources: Resources, mb: Float): String = resources.getString(
        when {
            mb < 10f -> R.string.story_cosmos_size_postcard
            mb < 30f -> R.string.story_cosmos_size_letter
            mb < 80f -> R.string.story_cosmos_size_small_parcel
            mb < 200f -> R.string.story_cosmos_size_parcel
            else -> R.string.story_cosmos_size_container
        },
    )

    /** Voyager's channel is about 160 bit/s. */
    override fun speedNote(resources: Resources, mbps: Float): String {
        val n = (mbps * 1e6f / 160f).roundToLong()
        return resources.getQuantityString(R.plurals.story_cosmos_speed, quantityOf(n), bigNumber(resources, n))
    }
}

/** A plural's quantity for a large count: Android's plural rules take an Int. */
internal fun quantityOf(n: Long): Int = if (n > Int.MAX_VALUE) (n % 1_000_000).toInt() + 1_000_000 else n.toInt()

/** «11 тыс.», «2,4 млн» / "11,000", "2.4 million". */
internal fun bigNumber(resources: Resources, n: Long): String = when {
    n >= 1_000_000 -> resources.getString(R.string.big_millions, formatOneDecimal(resources, ((n / 100_000.0).roundToLong() / 10.0).toFloat()))
    // Russian counts whole thousands («23 тыс.»), English writes them out ("23,000").
    n >= 1_000 -> resources.getString(
        R.string.big_thousands,
        if (isRussian(resources)) (n / 1000.0).roundToLong().toString() else String.format(java.util.Locale.US, "%,d", (n / 1000.0).roundToLong() * 1000),
    )
    else -> n.toString()
}

internal fun isRussian(resources: Resources) = resources.configuration.locales[0].language == "ru"

internal fun formatOneDecimal(resources: Resources, v: Float): String {
    val text = String.format(java.util.Locale.US, "%.1f", v)
    return if (isRussian(resources)) text.replace('.', ',') else text
}

private val svgCache = mutableMapOf<String, Path>()

/** An SVG path in the glyph's 20 × 20 box. */
internal fun svgPath(d: String): Path = svgCache.getOrPut(d) { androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toPath() }
