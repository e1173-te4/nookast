package ru.e1173.nookast.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** Design audit 11.11.2026, 1.2 and 1.1: measured contrasts of the tokens, every palette and sky. */
class AccentContrastTest {
    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return maxOf(la, lb).toDouble() / minOf(la, lb).toDouble()
    }

    private val skies = listOf(SkyMood.NONE, SkyMood.MORNING, SkyMood.WHITE_NIGHT, SkyMood.EVENING)

    @Test
    fun `the glyph on the accent fill reads in every night palette and under every sky`() {
        val report = StringBuilder()
        ThemePalette.entries.forEach { palette ->
            skies.forEach { mood ->
                val colors = nookastColors(palette, dark = true).underSky(SkyState(mood, 1f))
                val c = contrast(colors.onAccent, colors.accent)
                report.appendLine("dark $palette $mood ${"%.2f".format(c)}")
                assertTrue("dark $palette $mood: $c", c >= 4.5)
            }
        }
        println(report)
    }

    @Test
    fun `light outlines and labelled fills meet their thresholds`() {
        ThemePalette.entries.forEach { palette ->
            val colors = nookastColors(palette, dark = false)
            // The UI paints the surface over the background (Winter and Mint surfaces are translucent).
            val card = colors.surface.compositeOver(colors.background)
            val outline = colors.cardOutline.compositeOver(card)
            val outlineContrast = contrast(outline, card)
            assertTrue("light $palette outline $outlineContrast", outlineContrast in 1.12..1.35)
            val fillContrast = contrast(colors.onAccent, colors.accentFill)
            assertTrue("light $palette label on accentFill $fillContrast", fillContrast >= 4.5)
            println(
                "light $palette onAccent/accent ${"%.2f".format(contrast(colors.onAccent, colors.accent))}" +
                    " onAccent/accentStrong ${"%.2f".format(contrast(colors.onAccent, colors.accentStrong))}" +
                    " onAccent/accentFill ${"%.2f".format(fillContrast)}" +
                    " outline/surface ${"%.2f".format(contrast(outline, card))}",
            )
        }
    }
}
