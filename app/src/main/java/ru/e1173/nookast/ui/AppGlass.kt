package ru.e1173.nookast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import ru.e1173.nookast.designsystem.LocalNookastColors

/**
 * Space the bottom chrome (navigation bar and mini-player) takes over the content. Lazy screens
 * add it to their content padding so the last row can scroll clear of the chrome.
 */
internal val LocalBottomInset = compositionLocalOf<Dp> { 0.dp }

/**
 * Height of the Android status bar. Every screen runs under it (user, 29.10.2026: the bar kept the
 * screen colour while the glass header below it changed), so lists add it to their top padding.
 */
internal val LocalTopInset = compositionLocalOf<Dp> { 0.dp }

/**
 * The colour of the screen under the glass: the theme background, or the screen's own (Podcasts has
 * its own base colour). The root provides it; Unspecified means the theme background.
 */
internal val LocalGlassVeil = compositionLocalOf { Color.Unspecified }

/**
 * Frosted glass (APK-3.6 review: "blur like Telegram"). The glass blurs what really lies under it,
 * the screen's own background and cover light included (every glass screen puts them into its haze
 * source), so the glass takes the colour of the screen next to it and text scrolled under it shows
 * as a soft blur (user, 31.10.2026). The veil only steadies it. Content sources fade out at the screen's
 * edges ([glassEdgeFade]), so the blur has no text at its edge to smear into a flash. Blur needs
 * API 31; earlier devices get the veil only.
 */
@Composable
internal fun Modifier.appGlass(
    state: HazeState,
    veil: Float = 0.55f,
    tint: Color = LocalGlassVeil.current,
    mask: Brush? = null,
    blurRadius: Dp = 28.dp,
    progressive: HazeProgressive? = null,
    alpha: Float = 1f,
): Modifier {
    val colors = LocalNookastColors.current
    val background = tint.takeOrElse { colors.background }
    return hazeEffect(
        state = state,
        style = HazeStyle(
            backgroundColor = background,
            tints = listOf(HazeTint(background.copy(alpha = if (colors.isDark || veil == 0f) veil else veil + 0.1f))),
            blurRadius = blurRadius,
            noiseFactor = 0f,
            fallbackTint = HazeTint(background.copy(alpha = 0.94f)),
        ),
    ) {
        this.mask = mask
        this.progressive = progressive
        this.alpha = alpha
        // The blur reads the content at full resolution (user, 2.11.2026: covers and bold titles
        // shimmered under the glass while scrolling, as a downscaled input steps from pixel to pixel).
        inputScale = HazeInputScale.None
        // Real blur before Android 12 too (RenderScript): the head unit (Android 11) drew the glass as an
        // opaque tint of the base colour, darker than the cover-lit screen (user, 3.11.2026).
        blurEnabled = true
    }
}

/**
 * The top chrome as in iOS (user, 2.11.2026): the same glass reaches up under the Android status bar,
 * so the status bar and a sticky header read as one panel, and it ends in iOS's soft scroll edge,
 * the blur thinning out over [fade] below [solidHeight] instead of stopping at a line. A lighter blur
 * than the bars' keeps scrolled text showing through as a soft shape. It fades in and out with
 * [active]; without a veil it keeps the screen's own colour (user, 31.10.2026).
 */
@Composable
internal fun Modifier.topGlass(
    state: HazeState,
    active: Boolean,
    solidHeight: Float,
    fade: Float,
    tint: Color = LocalGlassVeil.current,
): Modifier {
    val shown by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(TOP_GLASS_FADE_MS),
        label = "topGlass",
    )
    if (shown <= 0f) return this
    // One blur strength everywhere (user, 2.11.2026: "just remove the blurs of different strength";
    // on Android 12 a progressive blur is stacked layers whose steps shimmered under scrolled text).
    // The edge is soft by the glass's opacity alone, eased out over [fade] below [solidHeight].
    val end = (solidHeight + fade).coerceAtLeast(1f)
    val hold = solidHeight / end
    val stops = buildList {
        add(0f to Color.Black)
        add(hold to Color.Black)
        for (step in 1..EDGE_STEPS) {
            val t = step.toFloat() / EDGE_STEPS
            val alpha = 1f - androidx.compose.animation.core.FastOutSlowInEasing.transform(t)
            add(hold + (1f - hold) * t to Color.Black.copy(alpha = alpha))
        }
    }
    return appGlass(
        state,
        veil = TOP_GLASS_VEIL,
        tint = tint,
        blurRadius = TopGlassBlur,
        mask = Brush.verticalGradient(*stops.toTypedArray(), startY = 0f, endY = end),
        alpha = shown,
    )
}

private const val EDGE_STEPS = 8

// No veil and no shade under the status bar (user, 31.10.2026: at night a scrolled header turned a
// little lighter): the header is the blur of what lies under it and nothing else.
private const val TOP_GLASS_VEIL = 0f
private const val TOP_GLASS_FADE_MS = 220
private val TopGlassBlur = 18.dp

/**
 * A sticky header on [topGlass] (user, 2.11.2026: a scrolled header still read as a line with some
 * blur added under it). The glass runs [HeaderEdgeFade] past the header, its blur already thinning
 * over the header's lower part, so no edge is left anywhere; the header keeps its own size in the layout.
 */
@Composable
internal fun TopGlassHeader(
    state: HazeState,
    active: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalGlassVeil.current,
    content: @Composable () -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var headerHeight by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val fadePx = with(density) { HeaderEdgeFade.roundToPx() }
    val solidPx = (headerHeight - fadePx * HEADER_FADE_INSIDE).coerceAtLeast(0f)
    androidx.compose.ui.layout.Layout(
        content = {
            Box(Modifier.topGlass(state, active, solidPx, headerHeight + fadePx - solidPx, tint))
            Box { content() }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val header = measurables[1].measure(constraints)
        if (header.height != headerHeight) headerHeight = header.height
        val glass = measurables[0].measure(androidx.compose.ui.unit.Constraints.fixed(header.width, header.height + fadePx))
        layout(header.width, header.height) {
            glass.place(0, 0)
            header.place(0, 0)
        }
    }
}

private val HeaderEdgeFade = 28.dp

/** How much of [HeaderEdgeFade] the thinning blur starts above the header's lower edge. */
private const val HEADER_FADE_INSIDE = 0.15f

/**
 * The screen's base colour as the lowest layer of a haze source, so a glass over it blurs the real
 * colour rather than an empty layer.
 */
@Composable
internal fun BoxScope.GlassBase(state: HazeState) {
    val base = LocalGlassVeil.current.takeOrElse { LocalNookastColors.current.background }
    Box(Modifier.matchParentSize().hazeSource(state, zIndex = -2f).background(base))
}

/** The bare status bar glass of a screen without a header of its own. */
@Composable
internal fun BoxScope.StatusBarGlass(state: HazeState) {
    val inset = LocalTopInset.current
    if (inset <= 0.dp) return
    // The glass runs a little past the bar with its blur thinning out, as on iOS: a glass that
    // stopped at the bar's edge cut the scrolled list with a visible seam (user, 30.10.2026).
    val fade = StatusBarFade
    val density = androidx.compose.ui.platform.LocalDensity.current
    val solidPx = with(density) { (inset * STATUS_SOLID).toPx() }
    val fadePx = with(density) { (inset + fade).toPx() } - solidPx
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(inset + fade)
            .topGlass(state, active = true, solidHeight = solidPx, fade = fadePx),
    )
}

/**
 * A screen with a sticky header (user, 29.10.2026): the content scrolls under the header and the
 * status bar, which turn to glass once it has. [content] gets the top padding it must leave: the
 * status bar plus [headerHeight]. The screen's base colour and [backdrop] (a cover's light) lie
 * under the content in the same haze source, so the glass blurs them too.
 */
@Composable
internal fun GlassHeaderScreen(
    headerHeight: Dp,
    scrolled: Boolean,
    header: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    backdrop: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable BoxScope.(topPadding: Dp) -> Unit,
) {
    val haze = rememberHazeState()
    val inset = LocalTopInset.current
    Box(modifier) {
        GlassBase(haze)
        if (backdrop != null) Box(Modifier.matchParentSize().hazeSource(haze, zIndex = -1f), content = backdrop)
        Box(Modifier.fillMaxSize().hazeSource(haze).glassEdgeFade()) { content(inset + headerHeight) }
        TopGlassHeader(haze, scrolled, Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().padding(top = inset).height(headerHeight), content = header)
        }
    }
}

/** The live playback position (plan 5.2); read only by the player, mini player and timeline. */
internal val LocalPlaybackPosition = compositionLocalOf<androidx.compose.runtime.State<ru.e1173.nookast.player.PlaybackPosition>?> { null }

/**
 * [this] with the live position when it is about the same episode. Reading it subscribes only the
 * calling composable to the ticker, so the rest of the screen is not recomposed twice a second.
 */
@Composable
internal fun ru.e1173.nookast.player.PlaybackUiState.live(): ru.e1173.nookast.player.PlaybackUiState {
    val live = LocalPlaybackPosition.current?.value ?: return this
    if (live.episodeId == null || live.episodeId != currentEpisodeId) return this
    return copy(positionMs = live.positionMs, durationMs = live.durationMs.takeIf { it > 0 } ?: durationMs, bufferedAheadMs = live.bufferedAheadMs)
}

// The status bar glass ends just past the bar (user, 2.11.2026: it blurred the top of the large
// «Podcasts» title that sits right under the bar).
private val StatusBarFade = 16.dp
private const val STATUS_SOLID = 0.7f

/**
 * The content of a haze source fades out over its last [top] and [bottom] dp (user, 31.10.2026:
 * text reaching the top or bottom edge of the screen flashed in the glass). The blur repeats the
 * edge row of what it blurs, so a line of text at the very edge became a bright smear; faded, the
 * edge row is the screen's own colour. Those edges always lie under a glass bar.
 */
internal fun Modifier.glassEdgeFade(top: Dp = GlassEdgeFade, bottom: Dp = 0.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val height = size.height
        if (height <= 0f) return@drawWithContent
        val topFraction = (top.toPx() / height).coerceIn(0f, 0.5f)
        val bottomFraction = (bottom.toPx() / height).coerceIn(0f, 0.5f)
        drawRect(
            Brush.verticalGradient(
                0f to (if (topFraction > 0f) Color.Transparent else Color.Black),
                topFraction to Color.Black,
                (1f - bottomFraction) to Color.Black,
                1f to (if (bottomFraction > 0f) Color.Transparent else Color.Black),
            ),
            blendMode = BlendMode.DstIn,
        )
    }

internal val GlassEdgeFade = 6.dp

/**
 * A round button of a header floats over the glass (user, 2.11.2026: "as if it hangs in the air over
 * the panel" — do it for every header with buttons): a soft shadow under its disc. Every header
 * button with a disc wears it; the 48 dp touch target leaves the shadow room.
 */
internal fun Modifier.floatingDisc(shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.CircleShape): Modifier =
    shadow(
        elevation = 5.dp,
        shape = shape,
        clip = false,
        ambientColor = Color.Black.copy(alpha = 0.14f),
        spotColor = Color.Black.copy(alpha = 0.26f),
    )
