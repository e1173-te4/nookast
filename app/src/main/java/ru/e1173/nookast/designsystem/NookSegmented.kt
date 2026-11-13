package ru.e1173.nookast.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One choice of a [NookSegmentedToggle]: its glyph and its spoken name. */
data class NookSegment(val icon: ImageVector, val label: String)

/**
 * Icon segments in one backing (DEC-059), e.g. grid or list: the surface with a hairline outline,
 * radius m, exactly [height] tall so it lines up with the control beside it. Square segments sit
 * 4 dp in, radius s; the selected one is a tint fill that slides between them (Standard), its glyph
 * in the accent, the others secondary. Glyphs are 20 dp × [scale]. No platform shadow.
 */
@Composable
fun NookSegmentedToggle(
    segments: List<NookSegment>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    height: Dp,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    val colors = LocalNookastColors.current
    val haptics = rememberNookHaptics()
    // Square segments up to 36 dp × scale: a large font makes the row taller, not the toggle wider
    // (it took the sort's width, 13.11.2026); the backing still matches the row's height.
    val segment = minOf(height - INSET * 2, MAX_SEGMENT * scale)
    // The fill slides by index, so a change of height alone never moves it; still with «remove animations».
    val reduced = rememberReducedMotion()
    val slide by animateFloatAsState(
        selectedIndex.coerceAtLeast(0).toFloat(),
        if (reduced) snap() else NookMotion.standard(),
        label = "segmentSlide",
    )
    Box(
        modifier
            .height(height)
            .width(segment * segments.size + INSET * 2)
            .background(colors.surface, NookShapes.m)
            .border(HAIRLINE, colors.cardOutline, NookShapes.m)
            .padding(INSET),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (selectedIndex in segments.indices) {
            Box(
                Modifier
                    .offset(x = segment * slide)
                    .size(segment)
                    .background(colors.tintContainer, NookShapes.s),
            )
        }
        Row(Modifier.selectableGroup()) {
            segments.forEachIndexed { index, item ->
                val interaction = remember { MutableInteractionSource() }
                val chosen = index == selectedIndex
                Box(
                    Modifier
                        .size(segment)
                        .focusRing(NookShapes.s)
                        .selectable(chosen, interaction, indication = null, role = Role.RadioButton) {
                            if (!chosen) {
                                haptics.tick()
                                onSelect(index)
                            }
                        }
                        .nookPressable(interaction, NookPress.DISC)
                        .semantics { contentDescription = item.label },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        tint = if (chosen) colors.accentText else colors.textSecondary,
                        modifier = Modifier.size(GLYPH * scale),
                    )
                }
            }
        }
    }
}

private val INSET = 4.dp
private val HAIRLINE = 1.dp
private val GLYPH = 20.dp
private val MAX_SEGMENT = 36.dp
