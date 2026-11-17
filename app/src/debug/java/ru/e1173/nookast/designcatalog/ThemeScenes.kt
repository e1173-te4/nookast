package ru.e1173.nookast.designcatalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.e1173.nookast.designsystem.LocalNookastColors
import ru.e1173.nookast.designsystem.LocalThemePalette
import ru.e1173.nookast.designsystem.NookButton
import ru.e1173.nookast.designsystem.NookButtonVariant
import ru.e1173.nookast.designsystem.NookSourceChip
import ru.e1173.nookast.designsystem.NookThemeGlow
import ru.e1173.nookast.designsystem.NookTimecodes
import ru.e1173.nookast.designsystem.SourceHealth
import ru.e1173.nookast.designsystem.NookThemeRow
import ru.e1173.nookast.designsystem.NookTimeline
import ru.e1173.nookast.designsystem.NookType
import ru.e1173.nookast.designsystem.ThemePalette
import ru.e1173.nookast.designsystem.nookastColors

/**
 * The eight themes of DEC-061 for manual review: the settings list with the chosen theme's thumb and
 * history, and that theme's night glow and timeline (track, thumb and motion), played or paused.
 */
internal val ThemesScene = CatalogScene(
    id = "themes-r1",
    title = "Themes · Night glow · Timeline",
    state = "eight themes, playing / paused",
    revision = "themes/r1",
    behaviorContract = "docs/design/catalog/components/themes/r1/BEHAVIOR.md",
    content = { context, modifier -> ThemesSample(context, modifier) },
)

@Composable
private fun ThemesSample(context: CatalogSceneContext, modifier: Modifier) {
    val russian = context.locale == CatalogLocale.RU
    val dark = context.theme == CatalogTheme.DARK
    var palette by remember { mutableStateOf(ThemePalette.BEACON) }
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(31L * 60_000L) }
    val duration = 56L * 60_000L + 11_000L
    // The scene takes the chosen theme as the app does: the whole screen in its colours.
    CompositionLocalProvider(LocalNookastColors provides nookastColors(palette, dark), LocalThemePalette provides palette) {
        val colors = LocalNookastColors.current
        Box(modifier.background(colors.background)) {
            NookThemeGlow(Modifier.fillMaxSize())
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(if (russian) "Бегунок темы" else "Theme timeline", style = NookType.rowTitle, color = colors.textPrimary)
                // As on «Сейчас играет»: the podcast name 6 dp above, the times and the source chip under it.
                Column {
                    Text(
                        if (russian) "Северный берег" else "Northern Shore",
                        style = NookType.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    NookTimeline(
                        positionMs = position,
                        durationMs = duration,
                        chapterStartsMs = listOf(9L * 60_000L, 24L * 60_000L, 41L * 60_000L),
                        onSeek = { position = it },
                        playing = playing,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    NookTimecodes(
                        elapsed = "${position / 60_000}:%02d".format(position / 1_000 % 60),
                        remaining = "−${(duration - position) / 60_000}:%02d".format((duration - position) / 1_000 % 60),
                        modifier = Modifier.padding(horizontal = 4.dp),
                        center = {
                            NookSourceChip(
                                label = if (russian) "Поток" else "Stream",
                                quality = "HQ",
                                health = SourceHealth.HEALTHY,
                                busy = false,
                                onClick = {},
                                contentDescription = "",
                            )
                        },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    NookButton(
                        if (playing) (if (russian) "Пауза" else "Pause") else (if (russian) "Играть" else "Play"),
                        { playing = !playing },
                        variant = NookButtonVariant.Tonal,
                    )
                    NookButton("+30 s", { position = (position + 30_000L).coerceAtMost(duration) }, variant = NookButtonVariant.Tonal)
                }
                Box(Modifier.height(8.dp))
                Text(if (russian) "Оформление → тема" else "Appearance → theme", style = NookType.rowTitle, color = colors.textPrimary)
                Column(Modifier.fillMaxWidth().selectableGroup()) {
                    ThemePalette.entries.forEach { entry ->
                        NookThemeRow(entry, selected = entry == palette, onClick = { palette = entry })
                    }
                }
            }
        }
    }
}
