package ru.e1173.nookast.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Glyphs of the approved Now Playing mockup (np-playing-remote-healthy-b), path data copied
 * verbatim from its inline SVG (DEC-008: deterministic transfer). Stroke widths are those of the
 * mockup at its drawn size; tinting goes through Icon's tint.
 */
object NookIcons {
    val ChevronDown: ImageVector by lazy { stroked("chevron_down", 2.2f, "M6 9l6 6 6-6") }
    val ChevronLeft: ImageVector by lazy { stroked("chevron_left", 2.4f, "M15 18l-6-6 6-6") }
    val ChevronRight: ImageVector by lazy { stroked("chevron_right", 2.4f, "M9 18l6-6-6-6") }
    val Heart: ImageVector by lazy {
        stroked(
            "heart",
            2f,
            "M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09" +
                "C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z",
        )
    }
    val HeartFilled: ImageVector by lazy {
        filled(
            "heart_filled",
            "M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09" +
                "C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z",
        )
    }
    val UpNext: ImageVector by lazy {
        ImageVector.Builder("up_next", 24.dp, 24.dp, 24f, 24f).apply {
            listOf("M4 6h10", "M4 12h8", "M4 18h6").forEach { strokePath(it, 2f) }
            fillPath("M16 7.5L21.5 12L16 16.5Z")
        }.build()
    }
    val Download: ImageVector by lazy { stroked("download", 2.4f, "M12 3v12", "M7 10l5 5 5-5", "M5 19h14") }
    val Check: ImageVector by lazy { stroked("check", 2.6f, "M20 6L9 17L4 12") }
    val CheckBold: ImageVector by lazy { stroked("check_bold", 3f, "M20 6L9 17L4 12") }
    val Search: ImageVector by lazy { stroked("search", 2.4f, "M10.5 3.5a7 7 0 1 0 0 14a7 7 0 1 0 0-14z", "M15.6 15.6L20.5 20.5") }
    val Plus: ImageVector by lazy { stroked("plus", 2.6f, "M12 5v14", "M5 12h14") }
    val Close: ImageVector by lazy { stroked("close", 2.4f, "M6 6l12 12", "M18 6L6 18") }

    /** 36×36 viewport, two rounded bars. */
    val Pause: ImageVector by lazy {
        ImageVector.Builder("pause", 36.dp, 36.dp, 36f, 36f).apply {
            fillPath(roundedRect(8f, 5.5f, 6.5f, 25f, 3.2f))
            fillPath(roundedRect(21.5f, 5.5f, 6.5f, 25f, 3.2f))
        }.build()
    }

    /** 36×36 viewport triangle (drawn with a 4 px start inset in the mockup). */
    val Play: ImageVector by lazy {
        ImageVector.Builder("play", 36.dp, 36.dp, 36f, 36f).apply { fillPath("M10 6L29 18L10 30Z") }.build()
    }

    private fun stroked(name: String, width: Float, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply { paths.forEach { strokePath(it, width) } }.build()

    private fun filled(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply { fillPath(path) }.build()

    private fun ImageVector.Builder.strokePath(data: String, width: Float) {
        addPath(
            pathData = PathParser().parsePathString(data).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }

    private fun ImageVector.Builder.fillPath(data: String) {
        addPath(pathData = PathParser().parsePathString(data).toNodes(), fill = SolidColor(Color.Black))
    }

    private fun roundedRect(x: Float, y: Float, w: Float, h: Float, r: Float): String =
        "M${x + r} $y H${x + w - r} A$r $r 0 0 1 ${x + w} ${y + r} V${y + h - r} A$r $r 0 0 1 ${x + w - r} ${y + h} " +
            "H${x + r} A$r $r 0 0 1 $x ${y + h - r} V${y + r} A$r $r 0 0 1 ${x + r} $y Z"
}
