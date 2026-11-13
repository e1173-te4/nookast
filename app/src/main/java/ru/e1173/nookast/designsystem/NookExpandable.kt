package ru.e1173.nookast.designsystem

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Long text that shows its first lines and opens with «Ещё» (DEC-025, Stitch §2.5): above
 * [collapsedHeight] the block is cut, its last 48 dp fade out to transparent (a mask, so any background shows through: user, 31.10.2026 — a
 * scrim of the theme colour drew a dark band over a cover-lit page), the height animates
 * on the 0.82/380 spring and the chevron turns 180° in 240 ms. Short text shows no button.
 */
@Composable
fun NookExpandable(
    collapsedHeight: Dp,
    moreLabel: String,
    lessLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalNookastColors.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "expandChevron",
    )
    Column(modifier) {
        Box(Modifier.animateContentSize(spring(dampingRatio = 0.82f, stiffness = 380f))) {
            val faded = overflows && !expanded
            Layout(
                content = { Box { content() } },
                modifier = Modifier
                    .clipToBounds()
                    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        if (faded) {
                            val fade = 48.dp.toPx()
                            drawRect(
                                Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - fade, endY = size.height),
                                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                            )
                        }
                    },
            ) { measurables, constraints ->
                val placeable = measurables.first().measure(constraints.copy(maxHeight = Constraints.Infinity))
                val cap = collapsedHeight.roundToPx()
                val tooTall = placeable.height > cap
                if (tooTall != overflows) overflows = tooTall
                val height = if (expanded || !tooTall) placeable.height else cap
                layout(placeable.width, height) { placeable.place(0, 0) }
            }
        }
        if (overflows) {
            // No highlight box (user, 29.10.2026): the label only dims while pressed, as on iOS.
            val press = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val pressed by press.collectIsPressedAsState()
            Row(
                Modifier
                    .clickable(interactionSource = press, indication = null, role = Role.Button) { expanded = !expanded }
                    .graphicsLayer { alpha = if (pressed) 0.45f else 1f }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    if (expanded) lessLabel else moreLabel,
                    style = TextStyle(fontFamily = Manrope, fontSize = 14.sp, fontWeight = FontWeight(650)),
                    color = colors.accentText,
                )
                Icon(NookIcons.ChevronDown, null, tint = colors.accentText, modifier = Modifier.size(16.dp).rotate(chevron))
            }
        }
    }
}
