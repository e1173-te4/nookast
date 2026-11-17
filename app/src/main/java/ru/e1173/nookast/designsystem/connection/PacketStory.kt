package ru.e1173.nookast.designsystem.connection

import android.content.res.Resources
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import ru.e1173.nookast.R
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * «Пакетная почта» (DEC-069): the network as a circuit board. Copper traces at 45°, vias and router
 * nodes; the host is a rack, the server a box of four drives, the buffer a chip charged with light,
 * packets are glowing squares. The host out of reach from here: its first node is red and sparks; no
 * internet: the board goes dark and the buffer's link light blinks red.
 */
object PacketStory : ConnectionStory {
    override val id = "net"
    override val strings = StoryStrings(
        name = R.string.story_net_name,
        text = R.string.story_net_text,
        suits = R.string.story_net_suits,
        intro = R.string.story_net_intro,
        flowWord = R.string.story_net_flow,
        offlineWord = R.string.story_net_offline,
    )

    private val hostRoute = PolyRoute(listOf(Offset(52f, 58f), Offset(80f, 58f), Offset(104f, 82f), Offset(104f, 114f), Offset(116f, 114f)))
    private val serverRoute = PolyRoute(listOf(Offset(232f, 58f), Offset(204f, 58f), Offset(180f, 82f), Offset(180f, 114f), Offset(168f, 114f)))
    private val linkRoute = PolyRoute(listOf(Offset(36f, 45f), Offset(36f, 34f), Offset(248f, 34f), Offset(248f, 45f)))
    private val hops = mapOf(
        SourceKind.HOST to listOf(Offset(80f, 58f), Offset(104f, 82f)),
        SourceKind.SERVER to listOf(Offset(204f, 58f), Offset(180f, 82f)),
    )
    private val buffer = Rect(117f, 94f, 167f, 134f)

    override fun layout(env: SceneEnv) = StoryLayout(
        host = Offset(36f, 58f),
        server = Offset(248f, 58f),
        vessel = roundVessel(buffer, 7f, gloss = false),
        toVessel = { k -> if (k == SourceKind.HOST) hostRoute else serverRoute },
        link = { linkRoute },
    )

    override fun veil(env: SceneEnv): Pair<Color, Color> {
        val k = if (env.dark) .25f else .3f
        return mix(if (env.dark) Color(0xFF0B1512) else Color(0xFFF2F5F1), env.colors.sky2, k) to mix(if (env.dark) Color(0xFF08100E) else Color(0xFFE4EBE5), env.colors.sky2, k)
    }

    private fun copper(dark: Boolean) = if (dark) Color(0xFFC9A35C) else Color(0xFFB0843A)

    override fun DrawScope.background(env: SceneEnv) {
        val dark = env.dark
        drawRect(Brush.linearGradient(listOf(if (dark) Color(0xFF0B1512) else Color(0xFFF2F5F1), if (dark) Color(0xFF08100E) else Color(0xFFE4EBE5)), Offset.Zero, Offset(SCENE_W, SCENE_H)), size = Size(SCENE_W, SCENE_H))
        drawRect(env.colors.sky2.copy(alpha = if (dark) .25f else .3f), size = Size(SCENE_W, SCENE_H))
        // a bare board: an even field of vias and nothing else; none under the racks and the buffer (DEC-070)
        val via = if (dark) Color(201, 163, 92).copy(alpha = .14f) else Color(150, 120, 60).copy(alpha = .15f)
        fun taken(x: Float, y: Float) = (kotlin.math.abs(x - 142f) < 34f && kotlin.math.abs(y - 114f) < 30f) ||
            ((kotlin.math.abs(x - 36f) < 24f || kotlin.math.abs(x - 248f) < 24f) && kotlin.math.abs(y - 58f) < 20f)
        var x = 14f
        while (x < SCENE_W) {
            var y = 46f
            while (y <= 142f) {
                if (!taken(x, y)) drawCircle(via, 1f, Offset(x, y))
                y += 16f
            }
            x += 16f
        }
        // every route exists on the board even when nothing runs on it
        val trace = if (dark) Color(201, 163, 92).copy(alpha = .28f) else Color(150, 120, 60).copy(alpha = .3f)
        listOf(hostRoute, serverRoute, linkRoute).forEach { drawPath(it.path(), trace, style = line(1.4f)) }
        if (env.offline) drawRect((if (dark) Color.Black else Color.White).copy(alpha = .35f), size = Size(SCENE_W, SCENE_H))
    }

    override fun DrawScope.lane(route: SceneRoute, mode: LaneMode, env: SceneEnv) {
        val cu = copper(env.dark)
        val cover = env.colors.cover
        // live: the cover colour with a soft glow; idle: plain copper; blocked: dotted copper (DEC-070)
        when (mode) {
            LaneMode.LIVE -> {
                drawPath(route.path(), cover.copy(alpha = .26f), style = line(5f))
                drawPath(route.path(), cover, style = line(1.8f))
            }
            LaneMode.IDLE -> drawPath(route.path(), cu.copy(alpha = .85f), style = line(1.5f))
            LaneMode.BLOCKED -> drawPath(route.path(), cu.copy(alpha = .6f), style = line(1.5f, floatArrayOf(.5f, 5f)))
        }
    }

    /** A packet: a small glowing square that leaves a short trace of light. */
    override fun DrawScope.courier(route: SceneRoute, u: Float, size: Float, env: SceneEnv) {
        val cover = env.colors.cover
        for (k in 4 downTo 1) {
            val p = route.at(u - k * .025f)
            drawRect(cover.copy(alpha = .12f * (5 - k)), Offset(p.x - 1.5f, p.y - 1.5f), Size(3f, 3f))
        }
        val p = route.at(u)
        blob(p.x, p.y, 7f * size, cover.copy(alpha = .5f))
        at(p.x, p.y, p.angle) {
            drawRect(Color.White, Offset(-2.6f * size, -2.6f * size), Size(5.2f * size, 5.2f * size))
            drawRect(cover, Offset(-2.6f * size, -2.6f * size), Size(1.6f * size, 5.2f * size))
        }
    }

    /** The first hop on the host's way has gone down: a red node and three sparks. */
    override fun DrawScope.obstruction(route: SceneRoute, env: SceneEnv) {
        val (x, y) = hops.getValue(SourceKind.HOST).first()
        drawCircle(env.colors.bad, 5.6f, Offset(x, y))
        drawLine(Color.White, Offset(x - 2.2f, y - 2.2f), Offset(x + 2.2f, y + 2.2f), 1.4f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(Color.White, Offset(x + 2.2f, y - 2.2f), Offset(x - 2.2f, y + 2.2f), 1.4f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        for (i in 0 until 3) {
            val p = (env.t * 1.5f + i / 3f) % 1f
            val a = i * 2.1f - 2.4f
            drawCircle(Color(255, 200, 120).copy(alpha = 1 - p), 1f, Offset(x + cosF(a) * (7f + p * 8f), y + sinF(a) * (7f + p * 8f) - p * 3f))
        }
    }

    /** No link: the board is dark and a red link light blinks next to the buffer. */
    override fun DrawScope.offline(env: SceneEnv) {
        val on = sinF(env.t * 5f) > 0f
        val bad = env.colors.bad
        drawCircle(if (on) bad else bad.copy(alpha = .3f), 2.6f, Offset(173f, 100f))
        if (on) blob(173f, 100f, 8f, bad.copy(alpha = .5f))
    }

    override fun DrawScope.source(kind: SourceKind, at: Offset, info: SourceInfo, env: SceneEnv) {
        val (x, y) = at
        val dark = env.dark
        val c = env.colors
        val ink = if (dark) Color(0xFFD9E2DC) else Color(0xFF2A3330)
        val unit = if (dark) Color(0xFF1E2A26) else Color.White
        val fail = info.state == LinkState.FAIL
        val flowing = !env.local && env.model.flow > 0f && env.model.net == LinkState.OK
        alphaLayer(if (env.local) .35f else 1f) {
            // the hop nodes on this source's way blink when traffic passes
            hops.getValue(kind).forEachIndexed { i, (hx, hy) ->
                if (kind == SourceKind.HOST && info.blockedForPhone && i == 0) return@forEachIndexed
                drawCircle(unit, 4.2f, Offset(hx, hy))
                drawCircle(ink.copy(alpha = .45f), 4.2f, Offset(hx, hy), style = Stroke(1f))
                val lit = info.isSource && flowing && sinF(env.t * 9f - i * 1.4f) > .3f
                drawCircle(if (lit) c.ok else ink.copy(alpha = .25f), 1.6f, Offset(hx, hy))
            }
            if (kind == SourceKind.HOST) {
                // a rack: three units; the side bar is what is left of the episode
                for (i in 0 until 3) {
                    drawRoundRect(unit, Offset(x - 14f, y - 12f + i * 8f), Size(28f, 7f), CornerRadius(1.5f))
                    drawRoundRect(ink.copy(alpha = .6f), Offset(x - 14f, y - 12f + i * 8f), Size(28f, 7f), CornerRadius(1.5f), style = Stroke(1f))
                    drawCircle(if (sinF(env.t * 7f + i * 2f) > 0f) c.ok else c.ok.copy(alpha = .3f), 1.1f, Offset(x + 9f, y - 8.5f + i * 8f))
                }
                info.share?.let { share ->
                    drawRect(ink.copy(alpha = .12f), Offset(x - 19f, y - 12f), Size(2.5f, 23f))
                    drawRect(c.cover, Offset(x - 19f, y - 12f + 23f * (1 - share)), Size(2.5f, 23f * share))
                }
            } else {
                // your own server: four drive bays fill as the copy grows
                drawRoundRect(unit, Offset(x - 13f, y - 13f), Size(26f, 26f), CornerRadius(3f))
                drawRoundRect(if (fail) c.bad else ink.copy(alpha = .6f), Offset(x - 13f, y - 13f), Size(26f, 26f), CornerRadius(3f), style = Stroke(1.2f))
                for (i in 0 until 4) {
                    val f = (info.copy * 4 - i).coerceIn(0f, 1f)
                    drawRect(ink.copy(alpha = .12f), Offset(x - 9f, y - 9f + i * 5f), Size(18f, 3.4f))
                    if (f > 0f && !fail) drawRect(c.cover, Offset(x - 9f, y - 9f + i * 5f), Size(18f * f, 3.4f))
                }
                drawCircle(if (fail) c.bad else c.ok, 1.3f, Offset(x + 9f, y + 10f))
                if (info.share != null && info.isSource) {
                    drawRect(ink.copy(alpha = .12f), Offset(x + 16f, y - 12f), Size(2.5f, 23f))
                    drawRect(c.cover, Offset(x + 16f, y - 12f + 23f * (1 - info.share)), Size(2.5f, 23f * info.share))
                }
            }
        }
    }

    /** The buffer chip's pins. */
    override fun DrawScope.vesselDecor(vessel: VesselShape, env: SceneEnv) {
        val b = vessel.box
        val pin = if (env.dark) Color(201, 163, 92).copy(alpha = .7f) else Color(150, 120, 60).copy(alpha = .7f)
        for (i in 0 until 4) {
            val px = b.left + 8f + i * ((b.width - 16f) / 3f)
            drawRect(pin, Offset(px - 1.5f, b.top - 5f), Size(3f, 5f))
            drawRect(pin, Offset(px - 1.5f, b.bottom), Size(3f, 5f))
        }
    }

    /** A ping pulse runs along every trace. */
    override fun DrawScope.checking(env: SceneEnv) {
        listOf(hostRoute, serverRoute, linkRoute).forEachIndexed { i, r ->
            val p = r.at((env.t * .7f + i * .3f) % 1f)
            blob(p.x, p.y, 8f, env.colors.cover.copy(alpha = .7f))
        }
    }

    override fun DrawScope.flash(p: Float, layout: StoryLayout, env: SceneEnv) {
        for (i in 0 until 6) {
            val q = serverRoute.at((p * 1.2f - i * .04f).coerceAtMost(1f))
            drawRect(env.colors.cover.copy(alpha = (1 - i * .15f).coerceIn(0f, 1f)), Offset(q.x - 2f, q.y - 2f), Size(4f, 4f))
        }
    }

    override fun DrawScope.glyph(kind: GlyphKind, fetch: Float, color: Color) {
        when (kind) {
            GlyphKind.DIRECT -> {
                drawRoundRect(color, Offset(3f, 6f), Size(9f, 9f), CornerRadius(1.5f))
                drawPath(svgPath("M14 10.5h4M16 8.5l2 2-2 2"), color, style = line(1.5f))
            }
            GlyphKind.SERVER -> {
                drawRoundRect(color, Offset(3.5f, 3f), Size(13f, 14f), CornerRadius(2f), style = Stroke(1.4f))
                drawPath(svgPath("M6 7h8M6 10h8M6 13h8"), color, style = line(1.6f))
            }
            GlyphKind.FETCH -> {
                drawRoundRect(color.copy(alpha = color.alpha * .6f), Offset(3.5f, 3f), Size(13f, 14f), CornerRadius(2f), style = Stroke(1.4f))
                val lit = max(1, (3 * fetch).roundToInt())
                listOf(7f, 10f, 13f).forEachIndexed { i, y ->
                    drawLine(color.copy(alpha = color.alpha * if (i < lit) 1f else .25f), Offset(6f, y), Offset(14f, y), 1.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }
            GlyphKind.HOST_DOWN -> {
                drawPath(svgPath("M2 10h5M13 10h5"), color, style = line(1.8f))
                drawRoundRect(color, Offset(7.5f, 7f), Size(5f, 6f), CornerRadius(1f))
                drawPath(svgPath("M8.3 7.8l3.4 4.4M11.7 7.8l-3.4 4.4"), Color.Black.copy(alpha = .55f), style = Stroke(1.1f))
            }
            GlyphKind.OFFLINE -> {
                drawRoundRect(color, Offset(4f, 5f), Size(12f, 10f), CornerRadius(1.5f), style = Stroke(1.5f))
                drawPath(svgPath("M7 15v-3h6v3"), color, style = Stroke(1.3f))
                drawCircle(color, 2.2f, Offset(15.5f, 4.5f))
            }
            GlyphKind.LOCAL -> {
                drawRoundRect(color, Offset(5f, 5f), Size(10f, 10f), CornerRadius(2f), style = Stroke(1.5f))
                drawRoundRect(color, Offset(7.5f, 9.5f), Size(5f, 3f), CornerRadius(.8f))
                drawPath(svgPath("M7 3v2M10 3v2M13 3v2M7 15v2M10 15v2M13 15v2"), color, style = line(1.2f))
            }
        }
    }

    /** Packets of 1500 bytes. */
    override fun sizeNote(resources: Resources, mb: Float): String {
        val n = (mb * 1e6f / 1500f).roundToLong()
        return resources.getQuantityString(R.plurals.story_net_packets, quantityOf(n), bigNumber(resources, n))
    }

    override fun speedNote(resources: Resources, mbps: Float): String {
        val n = (mbps * 1e6f / 8f / 1500f).roundToInt()
        return resources.getQuantityString(R.plurals.story_net_speed, n, n)
    }
}
