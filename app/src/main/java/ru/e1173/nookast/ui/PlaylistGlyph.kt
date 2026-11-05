package ru.e1173.nookast.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser

/**
 * Glyph of a PinePods smart playlist by its Phosphor icon name (`ph-car-simple`, `ph-play`,
 * `ph-sparkle`, `ph-fast-forward`, `ph-couch`, …), drawn on a 24-unit grid in [tint].
 */
@Composable
internal fun PlaylistGlyph(iconName: String, tint: Color, modifier: Modifier = Modifier) {
    val (strokes, fills) = remember(iconName) { playlistGlyphPaths(iconName) }
    val strokePaths = remember(strokes) { strokes.map { PathParser().parsePathString(it).toPath() } }
    val fillPaths = remember(fills) { fills.map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier) {
        val k = size.minDimension / 24f
        scale(k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            fillPaths.forEach { drawPath(it, tint, style = Fill) }
            strokePaths.forEach {
                drawPath(it, tint, style = Stroke(width = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

private fun playlistGlyphPaths(iconName: String): Pair<List<String>, List<String>> = when {
    iconName.contains("car") -> listOf(
        "M3.5 15.5V12l2.3-4.5h12.4L20.5 12v3.5Z",
        "M3.5 12h17",
        "M7.5 15.5v2M16.5 15.5v2",
    ) to emptyList()
    iconName.contains("play") -> emptyList<String>() to listOf("M8 5.2v13.6a.8.8 0 0 0 1.2.7l10.4-6.8a.8.8 0 0 0 0-1.4L9.2 4.5A.8.8 0 0 0 8 5.2Z")
    iconName.contains("sparkle") || iconName.contains("star") -> emptyList<String>() to listOf(
        "M10.5 3l1.9 5.6 5.6 1.9-5.6 1.9-1.9 5.6-1.9-5.6L3 10.5l5.6-1.9Z",
        "M18 14l.9 2.1 2.1.9-2.1.9L18 20l-.9-2.1-2.1-.9 2.1-.9Z",
    )
    iconName.contains("fast-forward") || iconName.contains("lightning") -> emptyList<String>() to listOf(
        "M3.5 6.6v10.8a.6.6 0 0 0 .95.5l7.3-5.4a.6.6 0 0 0 0-1L4.45 6.1a.6.6 0 0 0-.95.5Z",
        "M12.5 6.6v10.8a.6.6 0 0 0 .95.5l7.3-5.4a.6.6 0 0 0 0-1l-7.3-5.4a.6.6 0 0 0-.95.5Z",
    )
    iconName.contains("couch") || iconName.contains("armchair") -> listOf(
        "M5 11V8.5A2 2 0 0 1 7 6.5h10a2 2 0 0 1 2 2V11",
        "M3.5 12.5a1.5 1.5 0 0 1 3 0V14h11v-1.5a1.5 1.5 0 0 1 3 0V17h-17Z",
        "M5.5 17v1.5M18.5 17v1.5",
    ) to emptyList()
    iconName.contains("clock") || iconName.contains("hourglass") -> listOf(
        "M12 3.5a8.5 8.5 0 1 0 0 17a8.5 8.5 0 1 0 0-17Z",
        "M12 7.5V12l3 2",
    ) to emptyList()
    iconName.contains("rss") -> listOf("M5 11a8 8 0 0 1 8 8", "M5 5a14 14 0 0 1 14 14") to listOf("M6 16.4a1.6 1.6 0 1 1 0 3.2a1.6 1.6 0 1 1 0-3.2Z")
    else -> listOf("M8.5 7h11M8.5 12h11M8.5 17h11") to listOf(
        "M4.5 5.8a1.2 1.2 0 1 1 0 2.4a1.2 1.2 0 1 1 0-2.4Z",
        "M4.5 10.8a1.2 1.2 0 1 1 0 2.4a1.2 1.2 0 1 1 0-2.4Z",
        "M4.5 15.8a1.2 1.2 0 1 1 0 2.4a1.2 1.2 0 1 1 0-2.4Z",
    )
}

/** The Feed's RSS mark for list headers. */
@Composable
internal fun FeedGlyph(modifier: Modifier, tint: Color) {
    PlaylistGlyph("feed-rss", tint, modifier)
}
