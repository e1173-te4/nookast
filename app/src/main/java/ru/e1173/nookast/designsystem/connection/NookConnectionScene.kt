package ru.e1173.nookast.designsystem.connection

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.e1173.nookast.designsystem.Manrope
import ru.e1173.nookast.designsystem.rememberReducedMotion
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The connection scheme of the source card (DEC-069): [story] draws its world, the engine places the
 * couriers, the vessel of buffered sound, the transfer to the server and the corner and bottom words.
 * It moves only while [live] (the card is open and sound plays) and «remove animations» is off; it is
 * a still frame otherwise. A tap on a source or the background calls [onTap].
 *
 * [flash] is read while drawing: 1 → 0 after sound moved to the server's copy.
 */
@Composable
fun NookConnectionScene(
    model: SceneModel,
    labels: SceneLabels,
    story: ConnectionStory,
    colors: SceneColors,
    contentDescription: String,
    modifier: Modifier = Modifier,
    live: Boolean = true,
    flash: () -> Float = { 0f },
    onTap: ((SceneFocus) -> Unit)? = null,
) {
    val reduced = rememberReducedMotion()
    val moving = live && !reduced
    val time = remember { mutableFloatStateOf(STILL_TIME) }
    LaunchedEffect(moving) {
        if (!moving) {
            time.floatValue = STILL_TIME
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time.floatValue = STILL_TIME + (it - start) / 1e9f }
    }
    val measurer = rememberTextMeasurer()
    val hits = remember { HitHolder() }
    Box(
        modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .semantics { this.contentDescription = contentDescription }
            .then(
                if (onTap == null) {
                    Modifier
                } else {
                    Modifier.pointerInput(onTap) {
                        detectTapGestures { tap ->
                            val k = size.width / SCENE_W
                            val p = Offset(tap.x / k, tap.y / k)
                            val near = listOf(SceneFocus.HOST to hits.host, SceneFocus.SERVER to hits.server)
                                .firstOrNull { (_, at) -> hypot(at.x - p.x, at.y - p.y) < TAP_REACH }?.first
                            onTap(near ?: SceneFocus.INTERNET)
                        }
                    }
                },
            )
            .drawWithCache {
                val k = size.width / SCENE_W
                val texts = SceneTexts(measurer, this, k, min(fontScale, MAX_SCENE_FONT_SCALE), labels, colors, model)
                onDrawBehind {
                    val t = time.floatValue
                    val env = SceneEnv(model, t, colors, min(fontScale, MAX_SCENE_FONT_SCALE))
                    var placed: StoryLayout? = null
                    scale(k, k, Offset.Zero) {
                        clipRectScene {
                            placed = drawScene(story, env, flash())
                        }
                    }
                    placed?.let { hits.host = it.host; hits.server = it.server }
                    texts.draw(this, t, placed, story.vesselLift(env))
                }
            },
    )
}

private class HitHolder {
    var host: Offset = Offset(-100f, -100f)
    var server: Offset = Offset(-100f, -100f)
}

private fun DrawScope.clipRectScene(block: DrawScope.() -> Unit) {
    clipRect(0f, 0f, SCENE_W, SCENE_H) { block() }
}

/** The engine: everything but the words, in scene units. Returns the layout it drew. */
internal fun DrawScope.drawScene(story: ConnectionStory, env: SceneEnv, flash: Float): StoryLayout {
    val m = env.model
    val c = env.colors
    val layout = story.layout(env)
    val offline = env.offline
    val local = m.local
    val source: SourceKind? = when {
        local -> null
        m.fromServer -> SourceKind.SERVER
        m.host == LinkState.OK -> SourceKind.HOST
        else -> null
    }
    val transfer = (m.offer || m.fetchPercent != null) && m.server == LinkState.OK
    val hostBlocked = !local && !offline && m.host == LinkState.FAIL
    with(story) { background(env) }
    val hostRoute = layout.toVessel(SourceKind.HOST)
    val serverRoute = layout.toVessel(SourceKind.SERVER)
    val link = layout.link()
    if (hostBlocked) with(story) {
        lane(hostRoute, LaneMode.BLOCKED, env)
        obstruction(hostRoute, env)
    }
    if (transfer) with(story) { lane(link, if (m.fetchPercent != null) LaneMode.LIVE else LaneMode.IDLE, env) }
    var splash = 0f
    if (source != null && m.net == LinkState.OK && m.flow > 0f) {
        val route = if (source == SourceKind.SERVER) serverRoute else hostRoute
        with(story) { lane(route, LaneMode.LIVE, env) }
        val n = (1 + m.flow * 2).roundToInt()
        val speed = .2f + m.bandwidthMbps.coerceAtMost(20f) * .06f
        for (i in 0 until n) {
            val u = ((env.t * speed + i.toFloat() / n) % 1f)
            with(story) { courier(route, u, 1f, env) }
            // The courier before this one has just touched the vessel.
            if (u < SPLASH_SPAN) splash = maxOf(splash, 1 - u / SPLASH_SPAN)
        }
    }
    if (transfer && m.fetchPercent != null) {
        for (i in 0 until 2) with(story) { courier(link, (env.t * .42f + i / 2f) % 1f, .8f, env) }
    }
    if (offline) with(story) { offline(env) }
    val fetched = m.fetchPercent?.let { it / 100f }
    val shares = mapOf(
        SourceKind.HOST to when {
            transfer && fetched != null -> 1 - fetched
            source == SourceKind.HOST -> m.shareLeft
            else -> null
        },
        SourceKind.SERVER to when {
            m.fromServer -> m.shareLeft
            fetched != null -> fetched
            else -> null
        },
    )
    for (kind in SourceKind.entries) {
        val state = if (kind == SourceKind.HOST) m.host else m.server
        val p = if (kind == SourceKind.HOST) layout.host else layout.server
        val info = SourceInfo(
            state = state,
            isSource = source == kind,
            share = shares[kind],
            blockedForPhone = kind == SourceKind.HOST && hostBlocked,
            copy = if (m.fromServer) 1f else fetched ?: 0f,
        )
        with(story) { source(kind, p, info, env) }
        if (state == LinkState.RUNNING) checkingRing(p.x, p.y, 15f, env.t, env.ink)
        val focused = (m.focus == SceneFocus.HOST && kind == SourceKind.HOST) || (m.focus == SceneFocus.SERVER && kind == SourceKind.SERVER)
        if (focused) focusRing(p.x, p.y, 19f, c.cover)
    }
    env.splash = splash
    with(story) { vesselDecor(layout.vessel, env) }
    val lift = story.vesselLift(env)
    translate(0f, -lift) { drawVessel(env, layout.vessel, splash) }
    if (flash > 0f) with(story) { flash(1 - flash, layout, env) }
    if (m.running) with(story) { checking(env) }
    with(story) { foreground(env) }
    val (top, bottom) = story.veil(env)
    veils(top, bottom, env.fontScale)
    return layout
}

/** The vessel: liquid light fills it from below; a courier's touch makes it ring and slosh. */
private fun DrawScope.drawVessel(env: SceneEnv, vessel: VesselShape, splash: Float) {
    val m = env.model
    val c = env.colors
    val frac = vesselFraction(m)
    val col = vesselColor(m, c)
    val box = vessel.box
    val cx = box.center.x
    val cy = box.center.y
    scale(1 + splash * .04f, 1 + splash * .04f, Offset(cx, cy)) {
        val outline = vessel.shape()
        clipPath(outline) {
            drawRect(c.surface.copy(alpha = if (c.dark) .5f else .92f), Offset(box.left - 2, box.top - 2), Size(box.width + 4, box.height + 4))
            val top = box.bottom - frac * box.height
            val amp = (if (frac < 1f) 1.4f else 0f) + splash * 3.2f
            fun wave(x: Float) = top + sin(x / 6 + env.t * 2.2f) * amp
            val liquid = androidx.compose.ui.graphics.Path().apply {
                moveTo(box.left - 2, box.bottom + 2)
                for (i in 0..24) {
                    val x = box.left + box.width * i / 24
                    lineTo(x, wave(x))
                }
                lineTo(box.right + 2, box.bottom + 2)
                close()
            }
            drawPath(
                liquid,
                Brush.verticalGradient(
                    listOf(col.copy(alpha = (.9f + splash * .1f).coerceAtMost(1f)), col.copy(alpha = .6f)),
                    startY = top,
                    endY = box.bottom,
                ),
            )
            if (frac < 1f) {
                val surfaceLine = androidx.compose.ui.graphics.Path().apply {
                    for (i in 0..24) {
                        val x = box.left + box.width * i / 24
                        if (i == 0) moveTo(x, wave(x)) else lineTo(x, wave(x))
                    }
                }
                drawPath(surfaceLine, Color.White.copy(alpha = (.5f + splash * .4f).coerceAtMost(1f)), style = Stroke(1f))
            }
            // the glass's highlight, only on round vessels
            if (vessel.gloss) {
            val r = min(box.width, box.height) * .36f
            drawArc(
                Color.White.copy(alpha = .45f),
                189f,
                63f,
                false,
                Offset(cx - box.width * .18f - r, cy - box.height * .18f - r),
                Size(2 * r, 2 * r),
                style = Stroke(2f),
            )
            }
        }
        drawPath(outline, env.ink.copy(alpha = if (c.dark) .3f else .22f), style = Stroke(1.2f))
    }
    // the touch: a thin ring leaves the vessel's edge
    if (splash > 0f) drawPath(vessel.shape((1 - splash) * 9), col.copy(alpha = .65f * splash), style = Stroke(1.5f))
}

internal fun vesselFraction(m: SceneModel): Float = when {
    m.local -> 1f
    m.filling -> (m.runwaySeconds / 50f).coerceIn(0f, 1f)
    else -> (m.runwaySeconds / 120f).coerceIn(0f, 1f)
}

private fun vesselColor(m: SceneModel, c: SceneColors): Color = when {
    m.level == RunwayLevel.CRIT -> c.bad
    m.level == RunwayLevel.WARN -> c.warn
    m.local -> c.ok
    else -> c.cover
}

/**
 * The scheme's words, measured once per size and set of words. Corner and bottom words follow the
 * font scale up to 1.3 and then their zone limits them; they shrink to fit, never below 8 sp, and lie
 * on a soft patch of the card's colour. The vessel's number is part of the picture: it does not grow.
 */
private class SceneTexts(
    private val measurer: TextMeasurer,
    private val density: Density,
    private val k: Float,
    private val fontScale: Float,
    labels: SceneLabels,
    private val colors: SceneColors,
    private val model: SceneModel,
) {
    private class Label(val layout: TextLayoutResult, val x: Float, val y: Float, val align: TextAlign, val size: Float, val patch: Boolean)

    private val topY = 6f + 8f * fontScale
    private val soft = if (colors.dark) .62f else .72f
    private val inkTop = colors.ink.copy(alpha = if (colors.dark) .78f else .82f)
    private val left = fit(
        labels.topLeft,
        10f,
        FontWeight.Bold,
        if (labels.topLeftBad) colors.badText else inkTop,
        if (labels.topLeftWide) SCENE_W - 20f else CORNER_ZONE,
        x = 10f,
        y = topY,
        align = TextAlign.Left,
    )
    private val right = labels.topRight?.let {
        fit(it, 9.5f, FontWeight.SemiBold, colors.ink.copy(alpha = soft), CORNER_ZONE, x = SCENE_W - 10f, y = topY, align = TextAlign.Right)
    }
    private val bottom = labels.bottom.map {
        fit(
            it,
            10f,
            FontWeight.Bold,
            if (labels.bottomMuted) colors.ink.copy(alpha = .78f) else colors.coverText,
            SCENE_W - 24f,
            x = SCENE_W / 2,
            y = SCENE_H - 9f,
            align = TextAlign.Center,
        )
    }
    private val onLight = vesselFraction(model) > .5f
    private val vesselInk = if (onLight) Color.White else colors.ink
    private val vesselShadow = Shadow(if (onLight) Color.Black.copy(alpha = .35f) else colors.surface.copy(alpha = .95f), blurRadius = 4f)
    private val vesselValue = labels.vesselValue
    private val vesselCaption = labels.vesselCaption
    private var vesselCache: Pair<Label, Label>? = null
    private var vesselBoxWidth = -1f

    fun draw(scope: DrawScope, t: Float, layout: StoryLayout?, lift: Float) {
        draw(scope, left)
        right?.let { draw(scope, it) }
        if (bottom.isNotEmpty()) draw(scope, bottom[floor(t / BOTTOM_LINE_SECONDS).toInt().mod(bottom.size)])
        val box = layout?.vessel?.box ?: return
        if (vesselBoxWidth != box.width || vesselCache == null) {
            vesselBoxWidth = box.width
            vesselCache = fixed(vesselValue, 13f, FontWeight.ExtraBold, vesselInk, box.width - 5f, box.center.x, box.center.y + 2f) to
                fixed(vesselCaption, 9f, FontWeight.Bold, if (onLight) Color.White.copy(alpha = .92f) else colors.ink.copy(alpha = .78f), box.width - 8f, box.center.x, box.center.y + 13f)
        }
        val (value, caption) = vesselCache ?: return
        scope.translate(0f, -lift * k) {
            draw(this, value)
            draw(this, caption)
        }
    }

    private fun draw(scope: DrawScope, label: Label) {
        if (label.layout.size.width == 0) return
        val w = label.layout.size.width / k
        val x0 = when (label.align) {
            TextAlign.Left -> label.x
            TextAlign.Right -> label.x - w
            else -> label.x - w / 2
        }
        if (label.patch) {
            val sz = label.size
            scope.drawRoundRect(
                colors.surface.copy(alpha = if (colors.dark) .5f else .66f),
                Offset((x0 - 5f) * k, (label.y - sz * .95f) * k),
                Size((w + 10f) * k, sz * 1.42f * k),
                CornerRadius(sz * .71f * k),
            )
        }
        val baseline = label.layout.firstBaseline
        scope.drawText(label.layout, topLeft = Offset(x0 * k, label.y * k - baseline))
    }

    private fun style(size: Float, weight: FontWeight, color: Color, shadow: Shadow? = null) = TextStyle(
        fontFamily = Manrope,
        fontWeight = weight,
        color = color,
        fontSize = (size * k / (density.density * density.fontScale)).sp,
        fontFeatureSettings = "tnum",
        shadow = shadow,
    )

    private fun measure(text: String, size: Float, weight: FontWeight, color: Color, shadow: Shadow? = null) =
        measurer.measure(text, style(size, weight, color, shadow), softWrap = false, maxLines = 1, density = density)

    /** A word in sp: it grows with the font up to 1.3, then shrinks to [maxW], never below 8. */
    private fun fit(text: String, size: Float, weight: FontWeight, color: Color, maxW: Float, x: Float, y: Float, align: TextAlign): Label {
        var sz = size * fontScale
        var layout = measure(text, sz, weight, color)
        while (layout.size.width / k > maxW && sz > MIN_LABEL) {
            sz -= .25f
            layout = measure(text, sz, weight, color)
        }
        return Label(layout, x, y, align, sz, patch = false)
    }

    /** A word that is part of the picture: fixed size, shrinks only to fit [maxW]. */
    private fun fixed(text: String, size: Float, weight: FontWeight, color: Color, maxW: Float, x: Float, y: Float): Label {
        var sz = size
        var layout = measure(text, sz, weight, color, vesselShadow)
        while (layout.size.width / k > maxW && sz > MIN_LABEL) {
            sz -= .25f
            layout = measure(text, sz, weight, color, vesselShadow)
        }
        return Label(layout, x, y, TextAlign.Center, sz, patch = false)
    }
}

/** The scheme's moving time starts at the still frame, so the first live frame matches it. */
private const val STILL_TIME = 37f
private const val TAP_REACH = 24f
private const val SPLASH_SPAN = .16f
private const val CORNER_ZONE = 128f
private const val MIN_LABEL = 8f
private const val MAX_SCENE_FONT_SCALE = 1.3f
private const val BOTTOM_LINE_SECONDS = 3.2f

/** The scheme's height for a card of the phone's width: 176 dp at 284 dp wide. */
fun sceneHeightFor(width: androidx.compose.ui.unit.Dp) = width * (SCENE_H / SCENE_W)
