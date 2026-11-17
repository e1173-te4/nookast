package ru.e1173.nookast.designsystem

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.e1173.nookast.R

/** The theme's name in the settings list. */
@StringRes
fun ThemePalette.nameRes(): Int = when (this) {
    ThemePalette.SIGNATURE -> R.string.settings_palette_signature
    ThemePalette.LAVENDER -> R.string.settings_palette_lavender
    ThemePalette.AUTUMN -> R.string.settings_palette_autumn
    ThemePalette.WINTER -> R.string.settings_palette_winter
    ThemePalette.MINT -> R.string.settings_palette_mint
    ThemePalette.ON_AIR -> R.string.settings_palette_on_air
    ThemePalette.FIREFLY -> R.string.settings_palette_firefly
    ThemePalette.BEACON -> R.string.settings_palette_beacon
}

/** The theme's history (DEC-061), shown under its name once it is chosen. */
@StringRes
fun ThemePalette.storyRes(): Int = when (this) {
    ThemePalette.SIGNATURE -> R.string.settings_palette_story_signature
    ThemePalette.LAVENDER -> R.string.settings_palette_story_lavender
    ThemePalette.AUTUMN -> R.string.settings_palette_story_autumn
    ThemePalette.WINTER -> R.string.settings_palette_story_winter
    ThemePalette.MINT -> R.string.settings_palette_story_mint
    ThemePalette.ON_AIR -> R.string.settings_palette_story_on_air
    ThemePalette.FIREFLY -> R.string.settings_palette_story_firefly
    ThemePalette.BEACON -> R.string.settings_palette_story_beacon
}

/**
 * A theme in the settings list (DEC-061, the eight-theme mock): a swatch (the night accent, the night
 * and the day background), the name and, for the three new themes, a «new» tag. The chosen row is
 * tinted, its own timeline thumb (thumb only, moving) stands on the right where the check mark was,
 * and its history unfolds under the name. A radio button for accessibility; the touch can start the
 * theme's circular reveal.
 */
@Composable
fun NookThemeRow(
    palette: ThemePalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 48.dp,
) {
    val colors = LocalNookastColors.current
    val reduced = rememberReducedMotion()
    Column(
        modifier
            .fillMaxWidth()
            .clip(NookShapes.l)
            .background(if (selected) colors.tintContainer else Color.Transparent)
            .themeRevealSource()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = minHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ThemeSwatch(palette)
            Text(
                stringResource(palette.nameRes()),
                style = NookType.rowTitle,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (palette.showsNewBadge(ru.e1173.nookast.BuildConfig.VERSION_CODE)) {
                Text(
                    stringResource(R.string.settings_palette_new),
                    style = NookType.navLabel,
                    color = colors.accentText,
                    modifier = Modifier
                        .border(1.dp, colors.accentText, NookShapes.full)
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                )
            }
            // The thumb's place is kept in every row, so choosing a theme moves nothing.
            Box(Modifier.size(THUMB_SLOT_WIDTH, THUMB_SLOT_HEIGHT).clipToBounds(), contentAlignment = Alignment.Center) {
                if (selected) {
                    NookTimelineThumb(palette, motion = rememberThemeMotion(playing = palette != ThemePalette.AUTUMN), compact = true)
                }
            }
        }
        AnimatedVisibility(
            visible = selected,
            enter = if (reduced) EnterTransition.None else expandVertically(NookMotion.emphasized()) + fadeIn(NookMotion.emphasized()),
            exit = if (reduced) ExitTransition.None else shrinkVertically(NookMotion.standard()) + fadeOut(NookMotion.fast()),
        ) {
            Text(
                stringResource(palette.storyRes()),
                style = NookType.bodySmall,
                color = colors.textPrimary.scaleAlpha(0.86f),
                modifier = Modifier.padding(start = SWATCH_SIZE + 12.dp, end = 4.dp, bottom = 10.dp),
            )
        }
    }
}

/** The mock's swatch: the night accent on 38 %, the night background to 70 %, the day background after. */
@Composable
private fun ThemeSwatch(palette: ThemePalette) {
    val night = nookastColors(palette, dark = true)
    val day = nookastColors(palette, dark = false)
    Canvas(Modifier.size(SWATCH_SIZE)) {
        rotate(-90f) {
            drawCircle(
                Brush.sweepGradient(
                    0f to night.accent,
                    0.38f to night.accent,
                    0.38f to night.background,
                    0.70f to night.background,
                    0.70f to day.background,
                    1f to day.background,
                ),
            )
        }
        drawCircle(Color.White.copy(alpha = 0.15f), style = Stroke(1.dp.toPx()), radius = size.minDimension / 2f - 0.5.dp.toPx())
    }
}

private val SWATCH_SIZE = 28.dp
private val THUMB_SLOT_WIDTH = 36.dp
private val THUMB_SLOT_HEIGHT = 40.dp
