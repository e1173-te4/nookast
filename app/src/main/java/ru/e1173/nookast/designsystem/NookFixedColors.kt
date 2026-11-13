package ru.e1173.nookast.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Colours that do not follow the theme (design audit 3, guard 2.6): status signals, the iOS-style
 * tiles of the settings list, the launcher icon's layers and the placeholder covers. The values were
 * moved here unchanged from the screens.
 */
object NookFixedColors {
    /** «Playback will stop in …»: the critical warning of a stream. */
    val WarningCoral = Color(0xFFFF6B57)

    /** A failed or stale state: a feed the server cannot update, a failed refresh. */
    fun alertCoral(dark: Boolean) = if (dark) Color(0xFFFF6E61) else Color(0xFFED6575)

    /** Waiting or partial (the phone reads a feed the server cannot). */
    val Amber = Color(0xFFE0A030)

    /** The red badge over a cover. */
    val AlarmBadge = Color(0xE6D9475A)

    /** Everything is synchronised. */
    val SyncedGreen = Color(0xFF16864B)

    /** Settings section tiles (solid) and their tinted accents. */
    val TileAppearance = Color(0xFF5E5CE6)
    val TileServer = Color(0xFF0A84FF)
    val TileDiagnostics = Color(0xFF8E8E93)
    val TileAbout = Color(0xFFFF9F0A)
    fun serverAccent(dark: Boolean) = if (dark) Color(0xFF4ED39B) else Color(0xFF16875C)
    val AboutAccent = Color(0xFFE08600)

    /** The launcher icon's background, drawn in About (painterResource cannot read an adaptive icon). */
    val LauncherGradient = listOf(Color(0xFF4F337D), Color(0xFF1F0838))

    /** The initial over a placeholder cover. */
    val CoverCaption = Color(0x660C0918)

    /** Placeholder covers: a gradient chosen by the title's hash. */
    val PlaceholderGradients = listOf(
        Color(0xFF873F72) to Color(0xFFD383AD),
        Color(0xFF174A8D) to Color(0xFF7C82DD),
        Color(0xFF3D775C) to Color(0xFF94AD70),
        Color(0xFFBF5D35) to Color(0xFFF0AE5A),
        Color(0xFF146F80) to Color(0xFF64C4BB),
        Color(0xFF7757D6) to Color(0xFFF07186),
    )

    /** The preview cover of the design catalog: four backgrounds and the drawing's colours. */
    val PreviewBackgrounds = listOf(
        listOf(Color(0xFF7546D8), Color(0xFFFF8A72)),
        listOf(Color(0xFF1266C9), Color(0xFFFFB15E)),
        listOf(Color(0xFF332375), Color(0xFFF19A5F)),
        listOf(Color(0xFF0D787A), Color(0xFF68BE8B)),
    )
    val PreviewMoon = Color(0xFFFFB4CA)
    val PreviewSun = Color(0xFFFF7654)
    val PreviewSilhouette = Color(0xFF2E1B69)
    val PreviewMicrophone = Color(0xFFFFA2BC)
}
