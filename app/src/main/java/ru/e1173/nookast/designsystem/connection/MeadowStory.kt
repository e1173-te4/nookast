package ru.e1173.nookast.designsystem.connection

import android.content.res.Resources
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import ru.e1173.nookast.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * «Одуванчиковая почта» (DEC-069): a far dandelion is the host, the wind is the internet, the
 * parachutes carry the episode into a glass jar. A wattle fence with bindweed stands on the way when
 * the host is out of reach from here (user, 14.11.2026: the bush before it «looked ugly»); in a calm
 * everything stops. The user's own dandelion is the server.
 */
object MeadowStory : ConnectionStory {
    override val id = "meadow"
    override val strings = StoryStrings(
        name = R.string.story_meadow_name,
        text = R.string.story_meadow_text,
        suits = R.string.story_meadow_suits,
        intro = R.string.story_meadow_intro,
        flowWord = R.string.story_meadow_flow,
        offlineWord = R.string.story_meadow_offline,
    )

    private val host = Offset(46f, 64f)
    private val server = Offset(238f, 64f)

    // The jar stands on the far slope (DEC-070); the seeds fly into its neck.
    private val jar = Rect(115f, 90f, 169f, 140f)
    private val mouth = Offset(142f, jar.top - 6f)

    /** The far and the near slope: the sky's second colour with a little of the cover's. By day the
     * near slope takes at most 8 %: the bottom line is read on it (12 % gave 4.41:1 with an orange cover). */
    private fun soil(env: SceneEnv): Pair<Color, Color> {
        val c = env.colors
        return mix(c.sky2, c.cover, if (env.dark) .1f else .045f) to mix(c.sky2, c.cover, if (env.dark) .17f else .08f)
    }

    private fun farY(x: Float) = 151f - 9f * kotlin.math.exp(-((x - 120f) / 130f).let { it * it })

    private fun nearY(x: Float) = 159f - 10f * kotlin.math.exp(-((x - 205f) / 120f).let { it * it })

    private fun slope(y: (Float) -> Float) = Path().apply {
        moveTo(0f, SCENE_H)
        var x = 0f
        while (x <= SCENE_W) {
            lineTo(x, y(x))
            x += 6f
        }
        lineTo(SCENE_W, y(SCENE_W))
        lineTo(SCENE_W, SCENE_H)
        close()
    }

    override fun veil(env: SceneEnv): Pair<Color, Color> = env.colors.sky1 to soil(env).second

    override fun layout(env: SceneEnv) = StoryLayout(
        host = host,
        server = server,
        vessel = roundVessel(jar, 13f),
        toVessel = { k ->
            val p = if (k == SourceKind.HOST) host else server
            QuadRoute(Offset(p.x, p.y - 8f), Offset((p.x + 142f) / 2, min(p.y, mouth.y) - 34f), mouth)
        },
        link = { QuadRoute(Offset(host.x, host.y - 10f), Offset(142f, 6f), Offset(server.x, server.y - 10f)) },
    )

    override fun DrawScope.background(env: SceneEnv) {
        val c = env.colors
        drawRect(Brush.verticalGradient(listOf(c.sky1, c.sky2), 0f, SCENE_H), size = Size(SCENE_W, SCENE_H))
        // The wind: two strokes that end in a curl drift across the sky; in a calm they are gone.
        if (!env.offline && !env.local) {
            val wind = c.cover.copy(alpha = if (env.dark) .34f else .26f)
            val speed = 9f + env.model.bandwidthMbps.coerceAtMost(20f) * 4f
            listOf(Triple(31f, 52f, 0f), Triple(46f, 34f, 170f)).forEach { (y, len, shift) ->
                val x = ((env.t * speed + shift) % (SCENE_W + 150f)) - 60f
                val a = (minOf(x + 50f, SCENE_W + 10f - x) / 46f).coerceIn(0f, 1f)
                if (a <= 0f) return@forEach
                val stroke = Path().apply {
                    moveTo(x - len, y)
                    lineTo(x, y)
                    // a curl: from the bottom of a 3.6 dp circle up and back
                    arcTo(androidx.compose.ui.geometry.Rect(Offset(x, y - 3.6f), 3.6f), 90f, -243f, false)
                }
                drawPath(stroke, wind.copy(alpha = wind.alpha * a), style = line(1.1f))
            }
        }
        // the far slope: the dandelions and the jar stand on it
        drawPath(slope(::farY), soil(env).first)
        // the jar's shadow on the grass
        drawOval(Color.Black.copy(alpha = if (env.dark) .3f else .1f), Offset(142f - 24f, 142.6f - 2.4f), Size(48f, 4.8f))
    }

    /** The near slope hides where the stalks and the fence meet the ground; a few blades lean with the wind. */
    override fun DrawScope.foreground(env: SceneEnv) {
        drawPath(slope(::nearY), soil(env).second)
        val wind = if (env.offline || env.local) 0f else .5f + env.model.bandwidthMbps.coerceAtMost(20f) * .25f
        val blade = env.ink.copy(alpha = if (env.dark) .24f else .22f)
        val lean = floatArrayOf(-2.4f, 1.2f, 3f, -1.6f, 2.2f)
        floatArrayOf(16f, 22f, 90f, 96f, 102f, 186f, 192f, 258f, 264f, 270f).forEachIndexed { i, x ->
            val y = nearY(x) + 1.5f
            val h = 6f + rnd(i + 9) * 6f
            val bend = sinF(env.t * 1.4f + i * .9f) * 1.6f * wind + wind * 2f + lean[i % 5]
            drawPath(Path().apply { moveTo(x, y); quadraticTo(x + bend * .4f, y - h * .6f, x + bend, y - h) }, blade, style = line(1f))
        }
    }

    override fun DrawScope.courier(route: SceneRoute, u: Float, size: Float, env: SceneEnv) {
        val p = route.at(u)
        val fade = min(1f, u * 6f) * (if (u > .9f) (1 - u) * 10f else 1f)
        seed(p.x + sinF(env.t * 3f + u * 9f) * 2.5f, p.y, sinF(env.t * 2f + u * 7f) * .35f, size, .9f * fade, env.colors.cover)
    }

    /** A dandelion parachute: stem, seven rays and the seed. */
    private fun DrawScope.seed(x: Float, y: Float, angle: Float, scale: Float, alpha: Float, col: Color) {
        val c = col.copy(alpha = (col.alpha * alpha).coerceIn(0f, 1f))
        at(x, y, angle) {
            val s = scale
            val stroke = line(.9f * s)
            drawLine(c, Offset(0f, 5f * s), Offset(0f, -1f * s), .9f * s)
            for (i in -3..3) {
                val a = -PI_F / 2 + i * .32f
                drawPath(Path().apply { moveTo(0f, -s); lineTo(cosF(a) * 5f * s, (-1f + sinF(a) * 5f) * s) }, c, style = stroke)
            }
            drawOval(c, Offset(-.9f * s, 4.4f * s), Size(1.8f * s, 3.2f * s))
        }
    }

    private fun DrawScope.head(x0: Float, y0: Float, frac: Float, col: Color, t: Float, sway: Float, wilted: Boolean) {
        var x = x0
        var y = y0
        val stem = Path()
        if (wilted) {
            stem.moveTo(x + sway * 4f, SCENE_H + 4f)
            stem.quadraticTo(x + sway * 2f, y + 10f, x + sway * 14f, y + 14f)
            x += sway * 14f
            y += 16f
        } else {
            stem.moveTo(x, y + 4f)
            stem.quadraticTo(x + sway * 8f, y + 50f, x + sway * 4f, SCENE_H + 4f)
        }
        drawPath(stem, col.copy(alpha = .45f), style = Stroke(1.4f))
        val n = 26
        val keep = (n * frac).roundToInt()
        val ray = col.copy(alpha = .5f)
        for (i in 0 until n) {
            if ((i * 11) % n >= keep) continue
            val a = i.toFloat() / n * PI_F * 2 + sinF(t * .8f + i) * .03f
            val tip = Offset(x + cosF(a) * 11f, y + sinF(a) * 11f)
            drawLine(ray, Offset(x + cosF(a) * 3f, y + sinF(a) * 3f), tip, .7f)
            for (k in -1..1) {
                val b = a + k * .35f
                drawLine(ray, tip, Offset(tip.x + cosF(b) * 3.5f, tip.y + sinF(b) * 3.5f), .7f)
            }
        }
        drawCircle(col.copy(alpha = .75f), 3f, Offset(x, y))
    }

    /** A low picket fence between the far dandelion and the jar; one bindweed has climbed it (DEC-070). */
    override fun DrawScope.obstruction(route: SceneRoute, env: SceneEnv) {
        val xs = floatArrayOf(70f, 78.6f, 87.2f, 95.8f)
        val tops = floatArrayOf(108f, 105f, 107f, 104f)
        val w = 6.2f
        val wood = if (env.dark) mix(env.colors.sky2, Color(0xFFF3F0FF), .34f) else Color.White
        val edge = env.ink.copy(alpha = if (env.dark) .38f else .2f)
        // two rails behind the pickets
        val rail = env.ink.copy(alpha = if (env.dark) .26f else .16f)
        floatArrayOf(121f, 138f).forEach { y ->
            drawRoundRect(rail, Offset(xs[0] - 3.5f, y), Size(xs[3] + w + 7f - xs[0], 3f), CornerRadius(1.5f))
        }
        xs.forEachIndexed { i, x ->
            val picket = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        Rect(x, tops[i], x + w, SCENE_H),
                        topLeft = CornerRadius(w / 2),
                        topRight = CornerRadius(w / 2),
                        bottomRight = CornerRadius.Zero,
                        bottomLeft = CornerRadius.Zero,
                    ),
                )
            }
            drawPath(picket, wood)
            drawPath(picket, edge, style = Stroke(.8f))
        }
        // the bindweed: one vine, three leaves, one flower on top
        val leaf = if (env.dark) Color(0xFF96C496) else Color(0xFF407A4E)
        fun vine(k: Float) = Offset(72f + k * 26f + sinF(k * 7f) * 4.5f, 151f - k * 49f + sinF(env.t * .9f + k * 4f) * .4f)
        drawPath(Path().apply { for (i in 0..36) vine(i / 36f).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }, leaf, style = line(1.1f))
        floatArrayOf(.22f, .5f, .78f).forEachIndexed { i, k ->
            val p = vine(k)
            at(p.x, p.y, if (i % 2 == 1) -.9f else 2.2f) {
                drawPath(Path().apply { moveTo(0f, 0f); quadraticTo(3.8f, -3.2f, 6.8f, 0f); quadraticTo(3.8f, 3.2f, 0f, 0f) }, leaf)
            }
        }
        val (fx, fy) = vine(1f)
        val flower = Path().apply {
            for (j in 0..20) {
                val a = j / 20f * PI_F * 2
                val rr = 4.2f + cosF(a * 5f) * .5f
                if (j == 0) moveTo(fx + cosF(a) * rr, fy + sinF(a) * rr) else lineTo(fx + cosF(a) * rr, fy + sinF(a) * rr)
            }
            close()
        }
        drawPath(flower, Color.White)
        drawPath(flower, env.colors.cover.copy(alpha = if (env.dark) .75f else .55f), style = Stroke(.8f))
        drawCircle(env.colors.cover.copy(alpha = .9f), 1.1f, Offset(fx, fy))
        // two seeds that did not get past it
        for (i in 0 until 2) seed(xs[0] - 9f + i * 3.5f, 113f + i * 12f + sinF(env.t + i) * 1.6f, 1.25f + i * .3f, .8f, .55f, env.ink)
    }

    override fun DrawScope.offline(env: SceneEnv) {
        for (i in 0 until 5) seed(70f + i * 36f, 40f + ((env.t * 6f + i * 23f) % 90f), 0f, .9f, .45f, env.ink)
    }

    override fun DrawScope.source(kind: SourceKind, at: Offset, info: SourceInfo, env: SceneEnv) {
        val wilted = kind == SourceKind.SERVER && info.state == LinkState.FAIL
        val frac = when {
            env.local -> .8f
            kind == SourceKind.HOST -> info.share ?: .9f
            else -> max(.08f, info.copy)
        }
        val col = if (wilted) (if (env.dark) Color(0xFFE7A08F) else Color(0xFFB5523F)) else env.ink
        alphaLayer(if (env.local) .35f else 1f) {
            head(at.x, at.y, frac, col, env.t, if (kind == SourceKind.HOST) -1f else 1f, wilted)
        }
    }

    /** The jar's neck, drawn before the jar so the jar's edge closes it. */
    override fun DrawScope.vesselDecor(vessel: VesselShape, env: SceneEnv) {
        drawRoundRect(env.ink.copy(alpha = if (env.dark) .3f else .22f), Offset(125f, vessel.box.top - 5.5f), Size(34f, 6.5f), CornerRadius(3f))
    }

    override fun DrawScope.checking(env: SceneEnv) {
        val x = ((env.t * 90f) % (SCENE_W + 80f)) - 40f
        val cover = env.colors.cover
        drawRect(
            Brush.horizontalGradient(listOf(cover.copy(alpha = 0f), cover.copy(alpha = .14f), cover.copy(alpha = 0f)), x - 40f, x + 40f),
            Offset(x - 40f, 0f),
            Size(80f, SCENE_H),
        )
    }

    override fun DrawScope.flash(p: Float, layout: StoryLayout, env: SceneEnv) {
        for (i in 0 until 10) seed(layout.server.x - p * 96f + sinF(i.toFloat()) * 10f, layout.server.y - 10f + i * 3f + sinF(p * 9f + i) * 6f, 0f, 1f, 1 - p, env.colors.cover)
    }

    override fun DrawScope.glyph(kind: GlyphKind, fetch: Float, color: Color) {
        val thin = line(1f)
        when (kind) {
            GlyphKind.DIRECT -> at(10f, 9.5f, 18f * PI_F / 180f) {
                val s = 1.1f
                val stroke = line(1.3f)
                drawPath(svgPath("M0 4.5V-1M0-1-4.75-2.5M0-1-3.9-4.2M0-1-2-5.6M0-1 0-6M0-1 2-5.6M0-1 3.9-4.2M0-1 4.75-2.5").scaled(s), color, style = stroke)
                drawOval(color, Offset(-.9f * s, 4.1f * s), Size(1.8f * s, 3f * s))
            }
            GlyphKind.SERVER -> {
                drawPath(svgPath("M10 6.6V2.6M12.4 7.6l2.8-2.8M13.4 10h4M12.4 12.4l2.8 2.8M10 13.4v4M7.6 12.4l-2.8 2.8M6.6 10h-4M7.6 7.6 4.8 4.8"), color, style = thin)
                drawCircle(color, 2f, Offset(10f, 10f))
            }
            GlyphKind.FETCH -> {
                val n = 8
                val keep = max(1, (n * fetch).roundToInt())
                for (i in 0 until keep) {
                    val a = i.toFloat() / n * PI_F * 2 - PI_F / 2
                    drawLine(color, Offset(10f + cosF(a) * 3.4f, 10f + sinF(a) * 3.4f), Offset(10f + cosF(a) * 7.4f, 10f + sinF(a) * 7.4f), 1f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
                drawCircle(color, 2f, Offset(10f, 10f))
            }
            GlyphKind.HOST_DOWN -> {
                drawPath(svgPath("M4.5 17V8.5M9 17V7M13.5 17V8.5"), color, style = line(1.6f))
                drawPath(svgPath("M2.5 11.2q3.3-1.4 6.5 0t6.5 0M2.5 14.6q3.3 1.4 6.5 0t6.5 0"), color, style = line(1.2f))
                at(15.5f, 4.5f) { drawPath(svgPath("M0 4.5V-1M0-1-4-3M0-1 0-5.5M0-1 4-3").scaled(.6f), color, style = line(.9f)) }
            }
            GlyphKind.OFFLINE -> {
                drawPath(svgPath("M3 8h9M3 12h14"), color, style = line(1.6f))
                drawCircle(color, 1.2f, Offset(15f, 7f))
            }
            GlyphKind.LOCAL -> {
                drawRoundRect(color, Offset(5.5f, 5f), Size(9f, 11f), CornerRadius(2.6f), style = Stroke(1.5f))
                drawRoundRect(color, Offset(6.5f, 3f), Size(7f, 2f), CornerRadius(1f))
                drawRoundRect(color.copy(alpha = color.alpha * .7f), Offset(6.5f, 10f), Size(7f, 5f), CornerRadius(1.5f))
            }
        }
    }

    /** 200 KB a seed; a dandelion has about 170 of them. */
    override fun sizeNote(resources: Resources, mb: Float): String {
        val n = max(1, (mb * 5f).roundToInt())
        return resources.getQuantityString(R.plurals.story_meadow_seeds, n, n)
    }

    override fun speedNote(resources: Resources, mbps: Float): String = resources.getString(
        when {
            mbps < .5f -> R.string.story_meadow_speed_breath
            mbps < 2f -> R.string.story_meadow_speed_breeze
            mbps < 8f -> R.string.story_meadow_speed_strong
            mbps < 30f -> R.string.story_meadow_speed_gale
            else -> R.string.story_meadow_speed_hurricane
        },
    )
}

/** This path scaled by [s] about the origin. */
internal fun Path.scaled(s: Float): Path = Path().also { copy ->
    copy.addPath(this)
    copy.transform(androidx.compose.ui.graphics.Matrix().apply { scale(s, s) })
}
