package ru.e1173.nookast.designcatalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun CatalogViewport(
    profile: CatalogProfile,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val logicalSize = profile.logicalSize
    if (logicalSize == null) {
        Layout(
            content = content,
            modifier = modifier,
        ) { measurables, constraints ->
            val placeable = measurables.single().measure(constraints)
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.place(0, 0)
            }
        }
        return
    }

    Layout(
        content = content,
        modifier = modifier,
    ) { measurables, constraints ->
        val logicalWidth = logicalSize.width.roundToPx()
        val logicalHeight = logicalSize.height.roundToPx()
        val placeable = measurables.single().measure(
            Constraints.fixed(logicalWidth, logicalHeight),
        )
        val scale = min(
            constraints.maxWidth.toFloat() / logicalWidth,
            constraints.maxHeight.toFloat() / logicalHeight,
        ).coerceAtMost(1f)
        val renderedWidth = (logicalWidth * scale).roundToInt()
        val renderedHeight = (logicalHeight * scale).roundToInt()
        val x = (constraints.maxWidth - renderedWidth) / 2
        val y = (constraints.maxHeight - renderedHeight) / 2
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeWithLayer(x, y) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}
