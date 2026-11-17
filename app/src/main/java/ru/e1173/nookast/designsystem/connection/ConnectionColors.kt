package ru.e1173.nookast.designsystem.connection

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import ru.e1173.nookast.designsystem.NookFixedColors
import ru.e1173.nookast.designsystem.NookastColors

/** The dark ink that goes on light fills when white would not read (DEC-069). */
val NookInkOnLight = Color(0xFF17131F)

/** WCAG contrast of two opaque colours. */
fun contrastOf(a: Color, b: Color): Float {
    val x = a.luminance()
    val y = b.luminance()
    return (maxOf(x, y) + .05f) / (minOf(x, y) + .05f)
}

/** White or the dark ink, whichever reads better on [fill]: never a fixed white (1.2:1 on Firefly at night). */
fun inkOn(fill: Color): Color = if (contrastOf(Color.White, fill) >= contrastOf(NookInkOnLight, fill)) Color.White else NookInkOnLight

/**
 * A fill that carries text: moved darker or lighter in 3 % steps until its ink reaches [target]
 * (4.6:1, a little above AA so rounding never drops it below). The cover's colour comes from the
 * picture, so this runs while the app runs.
 */
fun fillFor(color: Color, target: Float = FILL_TARGET): Pair<Color, Color> {
    val base = color.copy(alpha = 1f)
    val ink = inkOn(base)
    val toward = if (ink == Color.White) Color.Black else Color.White
    var fill = base
    var k = 0
    while (contrastOf(ink, fill) < target && k < 30) {
        k++
        fill = lerp(base, toward, k * .03f)
    }
    return fill to ink
}

/** [color] moved until it reaches [target] on every one of [backgrounds]. */
fun readableOnAll(color: Color, backgrounds: List<Color>, target: Float): Color {
    val base = color.copy(alpha = 1f)
    val dark = backgrounds.first().luminance() < .5f
    val toward = if (dark) Color.White else Color.Black
    var c = base
    var k = 0
    while (backgrounds.any { contrastOf(c, it) < target } && k < 34) {
        k++
        c = lerp(base, toward, k * .03f)
    }
    return c
}

/** [color] moved away from [background] until it reaches [target]: text in the cover's colour. */
fun readableOn(color: Color, background: Color, target: Float): Color {
    val base = color.copy(alpha = 1f)
    val toward = if (background.luminance() < .5f) Color.White else Color.Black
    var c = base
    var k = 0
    while (contrastOf(c, background) < target && k < 34) {
        k++
        c = lerp(base, toward, k * .03f)
    }
    return c
}

const val FILL_TARGET = 4.6f

/**
 * The colours of the pill, the card and the scheme: the theme's, the status colours of DEC-069
 * (light themes: «bad» text #B52E44, «at the line» #855409) and the cover's, made readable on the card.
 */
@Immutable
data class ConnectionPalette(
    val scene: SceneColors,
    val ink: Color,
    val muted: Color,
    val card: Color,
    val tile: Color,
    val badText: Color,
    val badSoft: Color,
    val warn: Color,
    val warnText: Color,
    val warnSoft: Color,
    val accentText: Color,
    val accentSoft: Color,
    val accentFill: Color,
    val onAccent: Color,
    val cover: Color,
    val coverText: Color,
    val coverFill: Color,
    val onCover: Color,
    /** LO and SD: the secondary colour mixed with the primary 72 : 28, without transparency. */
    val badgeQuiet: Color,
    val dark: Boolean,
)

fun connectionPalette(colors: NookastColors, coverColor: Color?): ConnectionPalette {
    val dark = colors.isDark
    val card = colors.surface.compositeOver(colors.background)
    val bad = NookFixedColors.alertCoral(dark)
    val badText = if (dark) Color(0xFFFF8A80) else Color(0xFFB52E44)
    val warn = if (dark) Color(0xFFF2B04A) else Color(0xFFD08A1E)
    val warnText = if (dark) Color(0xFFF6C46F) else Color(0xFF855409)
    val rawCover = coverColor?.copy(alpha = 1f) ?: colors.accent.copy(alpha = 1f)
    // The cover's light on the scheme and the vessel: bright enough to show at night, deep enough by day.
    val cover = readableOn(rawCover, card, 3f)
    val tileOnCard = colors.textSecondary.copy(alpha = if (dark) .10f else .08f).compositeOver(card)
    // Text in the cover's colour sits on the card, on tiles and on the progress fill: it reads on all of them.
    val coverText = readableOnAll(rawCover, listOf(card, tileOnCard, cover.copy(alpha = .16f).compositeOver(card), cover.copy(alpha = .34f).compositeOver(card)), 4.6f)
    val (coverFill, onCover) = fillFor(rawCover)
    val (accentFill, onAccent) = fillFor(colors.accent)
    val accentText = readableOnAll(colors.accentText, listOf(card, colors.accent.copy(alpha = if (dark) .16f else .12f).compositeOver(card)), 4.6f)
    val sky1 = colors.background.compositeOver(Color.Black)
    val sky2 = lerp(sky1, colors.accent.copy(alpha = 1f), if (dark) .14f else .08f)
    return ConnectionPalette(
        scene = SceneColors(
            ink = colors.textPrimary,
            muted = colors.textSecondary,
            ok = colors.success,
            bad = bad,
            badText = badText,
            warn = warn,
            warnText = warnText,
            cover = cover,
            coverText = coverText,
            sky1 = sky1,
            sky2 = sky2,
            surface = card,
            accent = colors.accent,
            dark = dark,
        ),
        ink = colors.textPrimary,
        muted = readableOnAll(colors.textSecondary, listOf(card, tileOnCard), 4.6f),
        card = card,
        tile = colors.textSecondary.copy(alpha = if (dark) .10f else .08f),
        badText = badText,
        badSoft = bad.copy(alpha = if (dark) .12f else .10f),
        warn = warn,
        warnText = warnText,
        warnSoft = warn.copy(alpha = if (dark) .13f else .12f),
        accentText = accentText,
        accentSoft = colors.accent.copy(alpha = if (dark) .16f else .12f),
        accentFill = accentFill,
        onAccent = onAccent,
        cover = cover,
        coverText = coverText,
        coverFill = coverFill,
        onCover = onCover,
        badgeQuiet = lerp(colors.textSecondary.copy(alpha = 1f), colors.textPrimary.copy(alpha = 1f), .28f),
        dark = dark,
    )
}
