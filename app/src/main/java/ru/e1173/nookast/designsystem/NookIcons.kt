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
    val ChevronUp: ImageVector by lazy { stroked("chevron_up", 2.2f, "M18 15l-6-6-6 6") }
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

    // Screen icons (design audit 3.7): one icon per meaning, Lucide geometry with one 1.75 line
    // (Q-11). The bar's tabs have an outline and a filled form (selected).
    private const val LINE = 1.75f
    private const val HOUSE = "M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"
    private val GRID_CELLS = listOf(
        roundedRect(3f, 3f, 7f, 7f, 1.5f),
        roundedRect(14f, 3f, 7f, 7f, 1.5f),
        roundedRect(14f, 14f, 7f, 7f, 1.5f),
        roundedRect(3f, 14f, 7f, 7f, 1.5f),
    )
    private const val GEAR =
        "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38" +
            "a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38" +
            "a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18" +
            "a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08" +
            "a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08" +
            "a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"
    private const val GEAR_HUB = "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z"
    private const val RSS_DOT = "M5 18a1 1 0 1 0 0 2a1 1 0 1 0 0-2z"

    val Home: ImageVector by lazy { stroked("home", LINE, HOUSE, "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8") }
    val HomeFilled: ImageVector by lazy { filled("home_filled", HOUSE) }
    val Library: ImageVector by lazy { stroked("library", LINE, *GRID_CELLS.toTypedArray()) }
    val LibraryFilled: ImageVector by lazy {
        ImageVector.Builder("library_filled", 24.dp, 24.dp, 24f, 24f).apply { GRID_CELLS.forEach { fillPath(it) } }.build()
    }
    val Feed: ImageVector by lazy { stroked("feed", LINE, "M4 11a9 9 0 0 1 9 9", "M4 4a16 16 0 0 1 16 16", RSS_DOT) }
    val FeedFilled: ImageVector by lazy {
        ImageVector.Builder("feed_filled", 24.dp, 24.dp, 24f, 24f).apply {
            strokePath("M4 11a9 9 0 0 1 9 9", 2.6f)
            strokePath("M4 4a16 16 0 0 1 16 16", 2.6f)
            fillPath("M5 17.25a1.75 1.75 0 1 0 0 3.5a1.75 1.75 0 1 0 0-3.5z")
        }.build()
    }
    val UpNextOutline: ImageVector by lazy {
        ImageVector.Builder("up_next_outline", 24.dp, 24.dp, 24f, 24f).apply {
            listOf("M4 6h12", "M4 12h9", "M4 18h7").forEach { strokePath(it, LINE) }
            strokePath("M16 14.5L21 18L16 21.5Z", LINE)
        }.build()
    }
    val UpNextFilled: ImageVector by lazy {
        ImageVector.Builder("up_next_filled", 24.dp, 24.dp, 24f, 24f).apply {
            listOf("M4 6h12", "M4 12h9", "M4 18h7").forEach { strokePath(it, 2.6f) }
            fillPath("M15.5 14L21.5 18L15.5 22Z")
        }.build()
    }
    val Settings: ImageVector by lazy { stroked("settings", LINE, GEAR, GEAR_HUB) }
    val SettingsFilled: ImageVector by lazy {
        ImageVector.Builder("settings_filled", 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                pathData = PathParser().parsePathString(GEAR + GEAR_HUB).toNodes(),
                fill = SolidColor(Color.Black),
                pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd,
            )
        }.build()
    }
    val Grid: ImageVector get() = Library
    val ListView: ImageVector by lazy { stroked("list", LINE, "M3 6h.01", "M3 12h.01", "M3 18h.01", "M8 6h13", "M8 12h13", "M8 18h13") }
    val ListChecks: ImageVector by lazy { stroked("list_checks", LINE, "M3 17l2 2 4-4", "M3 7l2 2 4-4", "M13 6h8", "M13 12h8", "M13 18h8") }
    val ListPlus: ImageVector by lazy { stroked("list_plus", LINE, "M11 12H3", "M16 6H3", "M16 18H3", "M18 9v6", "M21 12h-6") }
    private val COPY = arrayOf(roundedRect(8f, 8f, 14f, 14f, 2f), "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2")
    val SelectAll: ImageVector by lazy { stroked("select_all", LINE, *COPY) }
    val SelectAllChecked: ImageVector by lazy { stroked("select_all_checked", LINE, *COPY, "M12 15l2 2 4-4") }
    val CircleDot: ImageVector by lazy { stroked("circle_dot", LINE, "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20z", "M12 11a1 1 0 1 0 0 2a1 1 0 1 0 0-2z") }
    val Trash: ImageVector by lazy { stroked("trash", LINE, "M3 6h18", "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6", "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2") }
    val Grip: ImageVector by lazy {
        ImageVector.Builder("grip", 24.dp, 24.dp, 24f, 24f).apply {
            listOf(9f, 15f).forEach { x -> listOf(5f, 12f, 19f).forEach { y -> fillPath("M${x - 1.25f} ${y}a1.25 1.25 0 1 0 2.5 0a1.25 1.25 0 1 0-2.5 0z") } }
        }.build()
    }
    val CirclePause: ImageVector by lazy { stroked("circle_pause", LINE, "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20z", "M10 15V9", "M14 15V9") }
    val Sun: ImageVector by lazy {
        stroked(
            "sun",
            LINE,
            "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8z",
            "M12 2v2", "M12 20v2", "M4.93 4.93l1.41 1.41", "M17.66 17.66l1.41 1.41",
            "M2 12h2", "M20 12h2", "M6.34 17.66l-1.41 1.41", "M19.07 4.93l-1.41 1.41",
        )
    }
    val Wifi: ImageVector by lazy {
        stroked("wifi", LINE, "M12 20h.01", "M2 8.82a15 15 0 0 1 20 0", "M5 12.86a10 10 0 0 1 14 0", "M8.5 16.43a5 5 0 0 1 7 0")
    }
    val Activity: ImageVector by lazy { stroked("activity", LINE, "M22 12h-4l-3 9L9 3l-3 9H2") }
    val Info: ImageVector by lazy { stroked("info", LINE, "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20z", "M12 16v-4", "M12 8h.01") }

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
