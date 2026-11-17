package ru.e1173.nookast.designsystem.connection

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.e1173.nookast.designsystem.ThemePalette
import ru.e1173.nookast.designsystem.nookastColors

/**
 * DEC-069: every text of the source pill and card against what is really behind it, on the real
 * tokens of every theme, day and night, with the three covers of the mock-up and without a cover.
 * AA: 4.5:1 for text; the card's title is large (18 sp extra bold) and needs 3:1.
 */
class ConnectionContrastTest {
    private val covers = listOf(null, Color(0xFFA66BFF), Color(0xFF7B45E0), Color(0xFF2DD4BF), Color(0xFF0E8A7E), Color(0xFFFB8A4C), Color(0xFFD0581E), Color(0xFFFFE066), Color(0xFF1B1B1B))

    @Test
    fun `pill, badges and card texts read on their backgrounds`() {
        val failures = mutableListOf<String>()
        ThemePalette.entries.forEach { theme ->
            listOf(true, false).forEach { dark ->
                val colors = nookastColors(theme, dark = dark)
                covers.forEach { cover ->
                    val p = connectionPalette(colors, cover)
                    val card = p.card
                    fun on(fill: Color) = fill.compositeOver(card)
                    val pairs = listOf(
                        "pill ink" to (p.ink.compositeOver(on(p.tile)) to on(p.tile)),
                        "pill bad" to (p.badText to on(p.badSoft)),
                        "pill warn" to (p.warnText to on(p.warnSoft)),
                        "pill accent" to (p.accentText to on(p.accentSoft)),
                        "badge quiet" to (p.badgeQuiet to on(p.tile)),
                        "badge HD" to (p.accentText to on(p.accentSoft)),
                        "badge HQ" to (p.onAccent to p.accentFill),
                        "subtitle" to (p.muted to card),
                        "why" to (p.ink.compositeOver(card) to card),
                        "tile label" to (p.muted to on(p.tile)),
                        "tile note" to (p.coverText to on(p.tile)),
                        "button" to (p.onCover to p.coverFill),
                        "progress" to (p.coverText to on(p.cover.copy(alpha = .16f))),
                        "history fail" to (p.badText to on(p.badSoft)),
                    )
                    pairs.forEach { (name, pair) ->
                        val c = contrastOf(pair.first.compositeOver(pair.second), pair.second)
                        if (c < 4.5f) failures += "$theme ${if (dark) "night" else "day"} cover=$cover $name ${"%.2f".format(c)}"
                    }
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * DEC-070: the bottom line lies on the meadow's near slope and on the seabed. The three covers of the
     * mock-up, day and night, every theme; by day the slope takes at most 8 % of the cover.
     */
    @Test
    fun `the bottom line reads on the near slope and on the seabed`() {
        val covers = mapOf(true to listOf(Color(0xFFA66BFF), Color(0xFF2DD4BF), Color(0xFFFB8A4C)), false to listOf(Color(0xFF7B45E0), Color(0xFF0E8A7E), Color(0xFFD0581E)))
        val model = SceneModel(
            net = LinkState.OK, netMs = 41, server = LinkState.OK, serverMs = 63, host = LinkState.OK, hostMs = 118,
            local = false, fromServer = false, runwaySeconds = 120f, filling = false, level = RunwayLevel.NORMAL,
            bandwidthMbps = 1.8f, flow = .4f, fetchPercent = null, offer = false, shareLeft = .6f, running = false, focus = null,
        )
        val failures = mutableListOf<String>()
        ThemePalette.entries.forEach { theme ->
            listOf(true, false).forEach { dark ->
                covers.getValue(dark).forEach { cover ->
                    val p = connectionPalette(nookastColors(theme, dark = dark), cover)
                    val env = SceneEnv(model, 0f, p.scene)
                    listOf("near slope" to MeadowStory.veil(env).second, "seabed" to SeaStory.veil(env).second).forEach { (name, ground) ->
                        val c = contrastOf(p.coverText, ground)
                        if (c < 4.5f) failures += "$theme ${if (dark) "night" else "day"} cover=$cover $name ${"%.2f".format(c)}"
                    }
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `ink on a fill is chosen by contrast, never a fixed white`() {
        // Firefly's night accent: white gave 1.2:1 in the mock-up.
        val (fill, ink) = fillFor(Color(0xFFDCF27A))
        assertTrue(ink == NookInkOnLight)
        assertTrue(contrastOf(ink, fill) >= 4.6f)
        val (teal, onTeal) = fillFor(Color(0xFF2DD4BF))
        assertTrue(contrastOf(onTeal, teal) >= 4.6f)
    }
}
