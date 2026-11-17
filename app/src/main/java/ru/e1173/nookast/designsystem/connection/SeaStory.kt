package ru.e1173.nookast.designsystem.connection

import android.content.res.Resources
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import ru.e1173.nookast.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * «Подводная почта» (DEC-069, user 14.11.2026: «a beautiful and funny seal, fish as the markers»):
 * a reef is the host, schools of glowing fish carry the episode into an air bubble that a seal holds
 * on its nose, the whale is the server. Kelp stands on the way when the host is out of reach from
 * here; still water is no internet. The seal's role was chosen by the design audit.
 */
object SeaStory : ConnectionStory {
    override val id = "sea"
    override val strings = StoryStrings(
        name = R.string.story_sea_name,
        text = R.string.story_sea_text,
        suits = R.string.story_sea_suits,
        intro = R.string.story_sea_intro,
        flowWord = R.string.story_sea_flow,
        offlineWord = R.string.story_sea_offline,
    )

    // The bubble sits high: the seal needs room under it and the bottom line stays clear.
    private const val BX = 136f
    private const val BY = 64f
    private const val BR = 24f

    override fun layout(env: SceneEnv): StoryLayout {
        val bob = sinF(env.t * .9f) * 3f
        val host = Offset(42f, 132f)
        val server = Offset(240f, 50f + bob + if (env.model.server == LinkState.FAIL) 22f else 0f)
        return StoryLayout(
            host = host,
            server = server,
            vessel = circleVessel(BX, BY, BR),
            toVessel = { k ->
                val p = if (k == SourceKind.HOST) host else server
                edgeRoute(p, if (k == SourceKind.HOST) 16f else 20f, Offset(BX, BY), BR + 1, if (k == SourceKind.HOST) 20f else -14f)
            },
            link = { edgeRoute(host, 16f, server, 20f, -64f) },
        )
    }

    private fun bed(dark: Boolean) = if (dark) Color(0xFF122531) else Color(0xFFE6DFCB)

    private fun bedY(x: Float) = SCENE_H - 21f - sinF(x / 30f) * 3f - sinF(x / 11f) * 1.2f

    override fun veil(env: SceneEnv): Pair<Color, Color> =
        mix(if (env.dark) Color(0xFF0C2638) else Color(0xFFDDF2F4), env.colors.sky2, if (env.dark) .25f else .3f) to bed(env.dark)

    /** The seabed (DEC-070): the coral and the kelp grow out of it, and the bottom line rests on it. */
    override fun DrawScope.foreground(env: SceneEnv) {
        val seabed = Path().apply {
            moveTo(0f, SCENE_H)
            var x = 0f
            while (x <= SCENE_W) {
                lineTo(x, bedY(x))
                x += 6f
            }
            lineTo(SCENE_W, bedY(SCENE_W))
            lineTo(SCENE_W, SCENE_H)
            close()
        }
        drawPath(seabed, bed(env.dark))
    }

    override fun DrawScope.background(env: SceneEnv) {
        val dark = env.dark
        val t = env.t
        drawRect(Brush.verticalGradient(listOf(if (dark) Color(0xFF0C2638) else Color(0xFFDDF2F4), if (dark) Color(0xFF050F18) else Color(0xFFA9DCE2)), 0f, SCENE_H), size = Size(SCENE_W, SCENE_H))
        drawRect(env.colors.sky2.copy(alpha = if (dark) .25f else .3f), size = Size(SCENE_W, SCENE_H)) // a tint of the theme
        // light from the surface: slanted rays by day, drifting glow motes by night
        if (!dark) {
            for (i in 0 until 3) {
                val x = 44f + i * 92f + sinF(t * .3f + i) * 10f
                val ray = Path().apply { moveTo(x - 8f, 0f); lineTo(x + 8f, 0f); lineTo(x + 40f, SCENE_H); lineTo(x + 18f, SCENE_H); close() }
                drawPath(ray, Brush.linearGradient(listOf(Color.White.copy(alpha = .42f), Color.White.copy(alpha = 0f)), Offset(x, 0f), Offset(x + 30f, SCENE_H)))
            }
        } else {
            for (i in 0 until 14) {
                val x = rnd(i) * SCENE_W
                var y = (rnd(i + 30) * SCENE_H - t * (3 + i % 4)) % SCENE_H
                if (y < 0f) y += SCENE_H
                drawCircle(Color(150, 240, 255).copy(alpha = (.22f + .22f * sinF(t * 2f + i)).coerceIn(0f, 1f)), .8f, Offset(x, y))
            }
        }
        // the current: soft streaks drifting across; they stop in still water
        if (!env.offline && !env.local) {
            val phase = -t * (14f + env.model.bandwidthMbps.coerceAtMost(20f) * 6f)
            listOf(42f to 4f, 118f to 5f).forEachIndexed { i, (y, a) ->
                val streak = Path()
                var x = -10f
                while (x <= SCENE_W + 10f) {
                    val yy = y + sinF(x / 36f + i + t * .4f) * a
                    if (x < -9f) streak.moveTo(x, yy) else streak.lineTo(x, yy)
                    x += 6f
                }
                drawPath(streak, if (dark) Color(150, 240, 255).copy(alpha = .12f) else Color(30, 110, 130).copy(alpha = .12f), style = line(1.2f, floatArrayOf(22f, 40f), phase))
            }
        }
        // rising bubbles
        for (i in 0 until 4) {
            val x = 20f + rnd(i + 70) * (SCENE_W - 40f)
            val y = SCENE_H - ((t * (10 + i * 3) + rnd(i) * SCENE_H) % SCENE_H)
            drawCircle(if (dark) Color(180, 240, 255).copy(alpha = .35f) else Color.White.copy(alpha = .8f), 1.4f + i % 3 * .6f, Offset(x + sinF(t * 2f + i) * 2f, y), style = Stroke(.8f))
        }
    }

    /** A little school: a leader and two that keep close behind. */
    override fun DrawScope.courier(route: SceneRoute, u: Float, size: Float, env: SceneEnv) {
        listOf(Triple(0f, 0f, 1f), Triple(.05f, 3.2f, .78f), Triple(.085f, -2.6f, .7f)).forEachIndexed { i, (back, off, k) ->
            val uu = u - back
            if (uu < 0f) return@forEachIndexed
            val p = route.at(uu)
            val fade = min(1f, uu * 6f)
            fish(p.x - sinF(p.angle) * off, p.y + cosF(p.angle) * off + sinF(env.t * 4f + i * 2f) * .6f, p.angle, 1.3f * size * k, fade, env.colors.cover, env.t, u * 13f + i)
        }
    }

    /** A small glowing fish: pointed body, a tail that wags, one bright eye; it never swims belly-up. */
    private fun DrawScope.fish(x: Float, y: Float, angle: Float, size: Float, alpha: Float, col: Color, t: Float, seed: Float) {
        at(x, y, angle) {
            scale(size, if (cosF(angle) < 0f) -size else size, Offset.Zero) {
                val wag = sinF(t * 9f + seed * 5f)
                blob(0f, 0f, 9f, col.copy(alpha = .32f * alpha))
                val body = col.copy(alpha = .96f * alpha)
                drawPath(Path().apply { moveTo(5.4f, 0f); quadraticTo(1.6f, -3.3f, -3f, -.7f); lineTo(-3f, .7f); quadraticTo(1.6f, 3.3f, 5.4f, 0f); close() }, body)
                drawPath(Path().apply { moveTo(-2.6f, 0f); lineTo(-6.6f, -2.8f + wag * 1.3f); quadraticTo(-5f, wag * .8f, -6.6f, 2.8f + wag * 1.3f); close() }, body)
                drawCircle(Color.White.copy(alpha = .95f * alpha), .85f, Offset(2.7f, -.8f))
            }
        }
    }

    /**
     * The seal balances the bubble on its nose. A fish arrives: it tosses the bubble and squints,
     * pleased. No internet: the nose drops and the brows go up in the middle. The buffer runs low: the
     * whiskers twitch; nearly empty, they tremble.
     */
    private fun DrawScope.seal(cx: Float, noseY: Float, env: SceneEnv) {
        val t = env.t
        val sad = env.offline
        val happy = env.splash > .25f
        val bob = sinF(t * 1.6f) * .8f
        val ny = noseY + (if (sad) 4f else -env.splash * 1.6f) + bob * .4f
        val dark = env.dark
        val body = if (dark) Color(0xFF93A5B8) else Color(0xFF7E91A5)
        val shade = if (dark) Color(0xFF7688A0) else Color(0xFF667A90)
        val belly = if (dark) Color(0xFFD3DEE7) else Color(0xFFF1F5F8)
        val ink = if (dark) Color(0xFF16202B) else Color(0xFF243140)
        val hx = cx + 1.5f
        val hy = ny + 8f
        alphaLayer(if (env.local) .4f else 1f) {
            // tail flippers
            val sway = sinF(t * 2.2f) * 2f
            listOf(-1f to -3.5f, 1f to 3.5f).forEach { (k, dy) -> ellipse(hx + 33f, hy + 41f + dy + sway * k * .4f, 6.5f, 2.6f, k * .5f + .25f, shade) }
            // body: neck stretched up to the bubble, a round belly, the tail trailing to the right
            val torso = Path().apply {
                moveTo(hx - 6f, hy + 2f)
                cubicTo(hx - 9f, hy + 14f, hx - 13f, hy + 26f, hx - 7f, hy + 36f)
                cubicTo(hx - 1f, hy + 45f, hx + 16f, hy + 46f, hx + 30f, hy + 42f)
                cubicTo(hx + 22f, hy + 36f, hx + 14f, hy + 30f, hx + 9f, hy + 18f)
                cubicTo(hx + 7f, hy + 11f, hx + 7f, hy + 6f, hx + 6f, hy + 2f)
                close()
            }
            drawPath(torso, body)
            clipPath(torso) { ellipse(hx - 9f, hy + 26f, 7.5f, 16f, .12f, belly) }
            // front flipper, paddling
            ellipse(hx + 4f, hy + 27f, 7.5f, 2.8f, .9f + sinF(t * 2.6f) * .25f, shade)
            // spots on the back
            val spot = (if (dark) Color.White else Color.Black).copy(alpha = .12f)
            listOf(Triple(12f, 28f, 1.6f), Triple(17f, 35f, 1.3f), Triple(8f, 22f, 1.1f)).forEach { (dx, dy, r) -> drawCircle(spot, r, Offset(hx + dx, hy + dy)) }
            // head and muzzle
            ellipse(hx, hy, 7.6f, 6.8f, 0f, body)
            ellipse(cx + .4f, ny + 4.2f, 4.4f, 3.5f, 0f, belly)
            // whiskers
            val tw = when (env.level) {
                RunwayLevel.CRIT -> sinF(t * 22f) * .28f
                RunwayLevel.WARN -> sinF(t * 9f) * .12f
                else -> 0f
            }
            val whisker = (if (dark) Color.White else ink).copy(alpha = if (dark) .7f else .55f)
            listOf(-1f, 1f).forEach { k ->
                listOf(-.35f, 0f, .35f).forEach { d ->
                    val a = (if (k < 0) PI_F else 0f) + d * k + tw * k
                    val from = Offset(cx + .4f + k * 3.2f, ny + 4.6f)
                    drawLine(whisker, from, Offset(from.x + cosF(a) * 6.5f, from.y + sinF(a) * 6.5f + 1f), .7f)
                }
            }
            // nose
            ellipse(cx + .4f, ny + 1.9f, 1.7f, 1.2f, 0f, ink)
            // eyes
            listOf(-3.6f to 0f, 4.2f to .3f).forEachIndexed { i, (dx, dy) ->
                val ex = hx + dx
                val ey = hy + 1.2f + dy + if (sad) 1f else 0f
                if (happy) {
                    drawArc(ink, 207f, 126f, false, Offset(ex - 1.7f, ey + .9f - 1.7f), Size(3.4f, 3.4f), style = line(1.1f))
                } else {
                    drawCircle(ink, 1.35f, Offset(ex, ey))
                    drawCircle(Color.White.copy(alpha = .9f), .45f, Offset(ex + .45f, ey - .5f))
                }
                if (sad) {
                    val k = if (i == 1) 1f else -1f
                    drawLine(ink, Offset(ex - 1.8f * k, ey - 3.4f), Offset(ex + 1.6f * k, ey - 2.4f), 1.1f)
                }
            }
            // cheeks
            listOf(-6f to 4.2f, 6.6f to 4.4f).forEach { (dx, dy) -> ellipse(hx + dx, hy + dy, 1.7f, 1.1f, 0f, Color(255, 140, 150).copy(alpha = .38f)) }
        }
    }

    /** A kelp forest between the reef and the bubble. */
    override fun DrawScope.obstruction(route: SceneRoute, env: SceneEnv) {
        for (i in 0 until 6) {
            val x = 76f + i * 6f
            val h = 60f + rnd(i + 3) * 40f
            val col = if (env.dark) Color(70, 140, 110).copy(alpha = .55f + i % 2 * .15f) else Color(60, 120, 80).copy(alpha = .5f + i % 2 * .15f)
            val kelp = Path().apply {
                moveTo(x, SCENE_H - 10f)
                for (k in 1..8) lineTo(x + sinF(env.t * 1.3f + k * .7f + i) * (2f + k * .6f), SCENE_H - 10f - h * k / 8f)
            }
            drawPath(kelp, col, style = line(3f - i % 2))
        }
    }

    /** Still water: silt drifts down and the water clouds over. */
    override fun DrawScope.offline(env: SceneEnv) {
        drawRect(if (env.dark) Color(10, 20, 26).copy(alpha = .35f) else Color(200, 190, 170).copy(alpha = .28f), size = Size(SCENE_W, SCENE_H))
        val silt = if (env.dark) Color(200, 220, 220).copy(alpha = .25f) else Color(120, 100, 80).copy(alpha = .25f)
        for (i in 0 until 40) drawCircle(silt, .9f, Offset(rnd(i + 5) * SCENE_W + sinF(env.t * .5f + i) * 3f, (rnd(i + 9) * SCENE_H + env.t * (2 + i % 3)) % SCENE_H))
    }

    override fun DrawScope.source(kind: SourceKind, at: Offset, info: SourceInfo, env: SceneEnv) {
        val (x, y) = at
        val dark = env.dark
        val cover = env.colors.cover
        alphaLayer(if (env.local) .35f else 1f) {
            if (kind == SourceKind.HOST) {
                // a branching coral; its glowing polyps are what is left of the episode
                val share = info.share ?: .9f
                val coral = if (dark) Color(0xFFE08A7A) else Color(0xFFD0705E)
                fun branch(bx: Float, by: Float, angle: Float, len: Float, w: Float, d: Int) {
                    if (d > 3) return
                    val ex = bx + cosF(angle) * len
                    val ey = by + sinF(angle) * len
                    drawLine(coral, Offset(bx, by), Offset(ex, ey), w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    branch(ex, ey, angle - .5f, len * .72f, w * .7f, d + 1)
                    branch(ex, ey, angle + .45f, len * .7f, w * .7f, d + 1)
                }
                branch(x, y + 22f, -PI_F / 2, 14f, 3.4f, 0)
                for (i in 0 until 14) {
                    val on = (i * 5) % 14 < (14 * share).roundToInt()
                    val px = x - 13f + rnd(i + 2) * 26f
                    val py = y - 12f + rnd(i + 8) * 20f
                    if (on) blob(px, py, 4f, cover.copy(alpha = .5f))
                    drawCircle(if (on) cover.copy(alpha = .9f) else (if (dark) Color.White else Color.Black).copy(alpha = .15f), 1.3f, Offset(px, py))
                }
            } else {
                // your whale: its belly glows with the copy it carries; unreachable, it sinks deeper and fades
                val fail = info.state == LinkState.FAIL
                val body = if (dark) (if (fail) Color(0xFF3A4A55) else Color(0xFF5E7C93)) else (if (fail) Color(0xFF9AA8B0) else Color(0xFF4E6E86))
                at(x, y) {
                    scale(1.2f, 1.2f, Offset.Zero) {
                        val fl = sinF(env.t * 3f) * 1.4f
                        val torso = Path().apply {
                            moveTo(-17f, 1f)
                            cubicTo(-17f, -9f, -6f, -11f, 3f, -9f)
                            cubicTo(10f, -7.5f, 14f, -3f, 17f, 0f)
                            cubicTo(12f, 5f, 4f, 8.5f, -5f, 8.5f)
                            cubicTo(-12f, 8.5f, -17f, 6f, -17f, 1f)
                            close()
                        }
                        drawPath(torso, body)
                        val tail = Path().apply {
                            moveTo(15.5f, 0f)
                            quadraticTo(20f, -1f + fl * .3f, 24f, -7f + fl)
                            quadraticTo(22.4f, -1f + fl * .5f, 21f, .5f)
                            quadraticTo(23.4f, 2f + fl * .5f, 25f, 6.5f + fl)
                            quadraticTo(19.5f, 3f, 15.5f, 0f)
                            close()
                        }
                        drawPath(tail, body)
                        val copy = if (fail) 0f else info.copy
                        if (copy > 0f) {
                            clipPath(Path().apply { addOval(Rect(-15.5f, .2f, 9.5f, 8.2f)) }) {
                                clipRect(-15.5f, -1f, -15.5f + 25f * copy, 9f) { drawRect(cover.copy(alpha = .88f), Offset(-15.5f, -1f), Size(25f, 10f)) }
                            }
                        }
                        val stripe = Color.White.copy(alpha = if (dark) .22f else .45f)
                        for (i in 0 until 3) drawPath(Path().apply { moveTo(-14.5f + i * 1.4f, 3.6f + i * .9f); quadraticTo(-9f, 6.6f + i * .5f, -3f + i, 6.8f + i * .4f) }, stripe, style = Stroke(.6f))
                        drawCircle(if (dark) Color(0xFFE8F4FA) else Color.White, 1.15f, Offset(-10.5f, -2.2f))
                        if (!fail && !env.local) {
                            val spout = if (dark) Color(190, 240, 255).copy(alpha = .75f) else Color.White.copy(alpha = .95f)
                            drawCircle(spout, 1.1f, Offset(-6.5f, -13f))
                            drawCircle(spout, .9f, Offset(-4f, -15.5f - sinF(env.t * 2f) * .8f))
                            drawCircle(spout, .8f, Offset(-8.6f, -15f - cosF(env.t * 2f) * .8f))
                        }
                    }
                }
            }
        }
    }

    override fun DrawScope.vesselDecor(vessel: VesselShape, env: SceneEnv) = seal(vessel.box.center.x, vessel.box.bottom + 1f, env)

    override fun vesselLift(env: SceneEnv): Float = env.splash * 3f

    /** Sonar rings from the bubble. */
    override fun DrawScope.checking(env: SceneEnv) {
        for (i in 0 until 2) {
            val p = (env.t * .6f + i / 2f) % 1f
            drawCircle(env.colors.cover.copy(alpha = .45f * (1 - p)), 24f + p * 120f, Offset(BX, BY), style = Stroke(1.3f))
        }
    }

    override fun DrawScope.flash(p: Float, layout: StoryLayout, env: SceneEnv) {
        for (i in 0 until 12) {
            drawCircle(Color.White.copy(alpha = (1 - p).coerceIn(0f, 1f)), 1.5f + i % 3, Offset(layout.server.x - 8f + sinF(i * 2f) * 8f, layout.server.y - 10f - p * 50f - i * 4f), style = Stroke(1f))
        }
    }

    private val whale = "M2 10q1-5 8-5t8 5.5q-3 3.5-8 3.5T2 10z"

    override fun DrawScope.glyph(kind: GlyphKind, fetch: Float, color: Color) {
        when (kind) {
            GlyphKind.DIRECT -> drawPath(
                svgPath("M2.4 10c2.2-3.5 5.3-4.7 8.1-4.3 1.6.2 2.7.9 3.5 1.9l3.6-2.3c-.6 1.6-.7 3.1 0 4.7.7 1.6.6 3.1 0 4.7l-3.6-2.3c-.8 1-1.9 1.7-3.5 1.9-2.8.4-5.9-.8-8.1-4.3zM6.9 8.1a1.1 1.1 0 1 0 0 2.2 1.1 1.1 0 0 0 0-2.2z").also { it.fillType = PathFillType.EvenOdd },
                color,
            )
            GlyphKind.SERVER -> {
                drawPath(svgPath(whale), color)
                drawPath(svgPath("M17 10l2.5-3v6z"), color)
            }
            GlyphKind.FETCH -> {
                drawPath(svgPath(whale), color.copy(alpha = color.alpha * .35f))
                clipPath(svgPath(whale)) { drawRect(color, Offset(2f, 4f), Size(16f * fetch, 12f)) }
            }
            GlyphKind.HOST_DOWN -> drawPath(svgPath("M5 18q-2-5 1-9t0-7M10 18q-2-5 1-9t0-7M15 18q-2-5 1-9t0-7"), color, style = line(1.8f))
            GlyphKind.OFFLINE -> {
                drawPath(svgPath("M3 8q3.5 0 7 0t7 0M3 12q3.5 0 7 0t7 0"), color, style = line(1.6f))
                drawCircle(color, .9f, Offset(6f, 15.5f))
                drawCircle(color, .9f, Offset(12f, 16.5f))
            }
            GlyphKind.LOCAL -> {
                drawCircle(color, 6.5f, Offset(10f, 10f), style = Stroke(1.5f))
                drawPath(svgPath("M5.5 11.5a4.5 4.5 0 0 0 9 0z"), color.copy(alpha = color.alpha * .7f))
                drawPath(svgPath("M7 6.8a3.6 3.6 0 0 1 2.6-1.2"), color, style = line(1.1f))
            }
        }
    }

    override fun sizeNote(resources: Resources, mb: Float): String {
        val n = max(1, (mb * 5f).roundToInt())
        return resources.getQuantityString(R.plurals.story_sea_fish, n, n)
    }

    override fun speedNote(resources: Resources, mbps: Float): String = resources.getString(
        when {
            mbps < .5f -> R.string.story_sea_speed_faint
            mbps < 2f -> R.string.story_sea_speed_steady
            mbps < 8f -> R.string.story_sea_speed_fast
            mbps < 30f -> R.string.story_sea_speed_rough
            else -> R.string.story_sea_speed_storm
        },
    )
}
