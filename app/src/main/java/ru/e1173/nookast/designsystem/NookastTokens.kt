package ru.e1173.nookast.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colour themes of DEC-017. A theme changes colours (and later the timeline thumb) only; the
 * geometry is shared by all of them.
 */
enum class ThemePalette {
    /** Signature theme of the app icon: plum night, lavender and a peach accent (user, 27.10.2026); the
     * default and first in the list (user, 2.11.2026). */
    SIGNATURE,
    LAVENDER,
    AUTUMN,
    WINTER,
    MINT;

    companion object {
        fun fromStoredValue(value: String?): ThemePalette = entries.firstOrNull { it.name == value } ?: SIGNATURE
    }
}

/**
 * Colour roles measured from the approved mockups (design/tokens/proposal.md §2). Alpha values
 * of the proposal are baked into ARGB here. Status: trial application before Q-10 approval.
 */
@Immutable
data class NookastColors(
    val background: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val accentStrong: Color,
    val onAccent: Color,
    val artworkPlaceholder: Color,
    val track: Color,
    val trackFill: Color,
    val surface: Color,
    val surfaceOutline: Color,
    val seekSurface: Color,
    val tintContainer: Color,
    val listened: Color,
    val success: Color,
    val divider: Color,
    val isDark: Boolean,
    /**
     * The outline of cards and rows (design audit 11.11.2026, 1.1): the divider at 70 % of its own
     * alpha. Lavender and Signature light take the divider as is (accent at 12 %, as in the mockups);
     * replacing the alpha there gave an accent outline at 70 %.
     */
    val cardOutline: Color = divider.scaleAlpha(0.7f),
    /** Text and thin glyphs in the accent: the strong accent by day, the accent at night (audit 1.4). */
    val accentText: Color = if (isDark) accent else accentStrong,
    /**
     * Filled controls with a label (audit 1.2, 11.11.2026): white text on it is at least 4.5:1. The
     * accent itself in Lavender and Signature by day and in every night theme; the strong accent in
     * Autumn, Winter and Mint by day. Discs with only a glyph keep the accent gradient (3:1 is enough).
     */
    val accentFill: Color = accent,
)

val LocalNookastColors = staticCompositionLocalOf { nookastColors(ThemePalette.SIGNATURE, dark = false) }

/** The palette in use: components that differ by more than colour (timeline thumb) read it. */
val LocalThemePalette = staticCompositionLocalOf { ThemePalette.SIGNATURE }

fun nookastColors(palette: ThemePalette, dark: Boolean): NookastColors = when (palette) {
    ThemePalette.LAVENDER -> if (dark) LavenderDark else LavenderLight
    ThemePalette.AUTUMN -> if (dark) AutumnDark else AutumnLight
    ThemePalette.WINTER -> if (dark) WinterDark else WinterLight
    ThemePalette.MINT -> if (dark) MintDark else MintLight
    ThemePalette.SIGNATURE -> if (dark) SignatureDark else SignatureLight
}

private val LavenderLight = NookastColors(
    background = Color(0xFFF7F4FD),
    textPrimary = Color(0xFF181428),
    textSecondary = Color(0xFF6E687A),
    accent = Color(0xFF6A53D6),
    accentStrong = Color(0xFF533EB4),
    onAccent = Color(0xFFFFFFFF),
    artworkPlaceholder = Color(0xFFECE6F8),
    track = Color(0x14181428),
    trackFill = Color(0xFF6A53D6),
    surface = Color(0xFFFFFFFF),
    surfaceOutline = Color(0x1F6A53D6),
    seekSurface = Color(0xFFFFFFFF),
    tintContainer = Color(0xFFEEE9FF),
    listened = Color(0xFF6A53D6),
    success = Color(0xFF139B69),
    divider = Color(0x1F6A53D6),
    isDark = false,
    cardOutline = Color(0x1F6A53D6),
)

private val LavenderDark = NookastColors(
    background = Color(0xFF0E0C16),
    textPrimary = Color(0xFFE2E8F0),
    textSecondary = Color(0xFF94A3B8),
    accent = Color(0xFFB35CFF),
    accentStrong = Color(0xFF7C2CFF),
    onAccent = Color(0xFF1A0B33),
    artworkPlaceholder = Color(0xFF171324),
    track = Color(0xFF201A33),
    trackFill = Color(0xFFB35CFF),
    surface = Color(0xFF171324),
    surfaceOutline = Color(0xFF2E2648),
    seekSurface = Color(0xBF171324),
    tintContainer = Color(0x33B35CFF),
    listened = Color(0xFFB35CFF),
    success = Color(0xFF21C57B),
    divider = Color(0xFF2E2648),
    isDark = true,
)

private val AutumnLight = NookastColors(
    background = Color(0xFFFAF4ED),
    textPrimary = Color(0xFF261A14),
    textSecondary = Color(0xFF78685E),
    accent = Color(0xFFC86134),
    accentStrong = Color(0xFFA94E27),
    onAccent = Color(0xFFFFFFFF),
    artworkPlaceholder = Color(0xFFEFE4D6),
    track = Color(0xFFE8DDD0),
    trackFill = Color(0xFFD86A34),
    surface = Color(0xFFFFFFFF),
    surfaceOutline = Color(0xFFEDE2D4),
    seekSurface = Color(0xFFFFFFFF),
    tintContainer = Color(0xFFF5ECE0),
    listened = Color(0xFFED6575),
    success = Color(0xFF139B69),
    divider = Color(0xFFE8DDD0),
    isDark = false,
    accentFill = Color(0xFFA94E27),
)

private val AutumnDark = NookastColors(
    background = Color(0xFF0E0D18),
    textPrimary = Color(0xFFE6DFD5),
    textSecondary = Color(0xFF9E9187),
    accent = Color(0xFFEB7A44),
    accentStrong = Color(0xFFC85A26),
    onAccent = Color(0xFF2A1206),
    artworkPlaceholder = Color(0xFF211B18),
    track = Color(0xFF26201D),
    trackFill = Color(0xFFEB7A44),
    surface = Color(0xFF1E1916),
    surfaceOutline = Color(0x73EB7A44),
    seekSurface = Color(0xFF1E1916),
    tintContainer = Color(0xFF2D2420),
    listened = Color(0xFFED6575),
    success = Color(0xFF139B69),
    divider = Color(0xFF352C27),
    isDark = true,
)

private val WinterLight = NookastColors(
    background = Color(0xFFF1F6FA),
    textPrimary = Color(0xFF0C1A29),
    textSecondary = Color(0xFF54667A),
    accent = Color(0xFF0284C7),
    accentStrong = Color(0xFF0369A1),
    onAccent = Color(0xFFFFFFFF),
    artworkPlaceholder = Color(0xFFE2EDF5),
    track = Color(0x140C1A29),
    trackFill = Color(0xFF0284C7),
    surface = Color(0xE6FFFFFF),
    surfaceOutline = Color(0xFFDCE7F0),
    seekSurface = Color(0xA6FFFFFF),
    tintContainer = Color(0xFFE0F2FE),
    listened = Color(0xFF22C55E),
    success = Color(0xFF059669),
    divider = Color(0xFFDCE7F0),
    isDark = false,
    accentFill = Color(0xFF0369A1),
)

private val WinterDark = NookastColors(
    background = Color(0xFF0B0E17),
    textPrimary = Color(0xFFF1F6FC),
    textSecondary = Color(0xFF8C9EB5),
    accent = Color(0xFF38BDF8),
    accentStrong = Color(0xFF0EA5E9),
    onAccent = Color(0xFF06202E),
    artworkPlaceholder = Color(0xFF131B2A),
    track = Color(0xFF1E293B),
    trackFill = Color(0xFF38BDF8),
    surface = Color(0xE6131B2A),
    surfaceOutline = Color(0x5938BDF8),
    seekSurface = Color(0xB3131B2A),
    tintContainer = Color(0x330284C7),
    listened = Color(0xFF22C55E),
    success = Color(0xFF21C57B),
    divider = Color(0xFF202E42),
    isDark = true,
)

private val MintLight = NookastColors(
    background = Color(0xFFF3FAF6),
    textPrimary = Color(0xFF064E3B),
    textSecondary = Color(0xFF54667A),
    accent = Color(0xFF059669),
    accentStrong = Color(0xFF047857),
    onAccent = Color(0xFFFFFFFF),
    artworkPlaceholder = Color(0xFFE2EDF5),
    track = Color(0x140C1A29),
    trackFill = Color(0xFF059669),
    surface = Color(0xF2FFFFFF),
    surfaceOutline = Color(0xFFD1FAE5),
    seekSurface = Color(0xD9FFFFFF),
    tintContainer = Color(0xFFD1FAE5),
    listened = Color(0xFF059669),
    success = Color(0xFF059669),
    divider = Color(0xFFD1FAE5),
    isDark = false,
    // The 70 % default measured 1.10:1, under the audit's 1.12 floor: the full divider (1.14).
    cardOutline = Color(0xFFD1FAE5),
    accentFill = Color(0xFF047857),
)

private val MintDark = NookastColors(
    background = Color(0xFF081713),
    textPrimary = Color(0xFFE2E8F0),
    textSecondary = Color(0xFF86B3A2),
    accent = Color(0xFF10B981),
    accentStrong = Color(0xFF059669),
    onAccent = Color(0xFF04241A),
    artworkPlaceholder = Color(0xFF0D231E),
    track = Color(0xFF112F26),
    trackFill = Color(0xFF10B981),
    surface = Color(0xEB0C241E),
    surfaceOutline = Color(0x7310B981),
    seekSurface = Color(0xBF0C241E),
    tintContainer = Color(0x3310B981),
    listened = Color(0xFF10B981),
    success = Color(0xFF10B981),
    divider = Color(0xFF19453B),
    isDark = true,
)

// Icon gradient #4F337D -> #1F0838, the lavender of the profile and its peach light.
private val SignatureLight = NookastColors(
    background = Color(0xFFF8F4F7),
    textPrimary = Color(0xFF1F0838),
    textSecondary = Color(0xFF6F6280),
    accent = Color(0xFF5A3A8C),
    accentStrong = Color(0xFF3E2468),
    onAccent = Color(0xFFFFFFFF),
    artworkPlaceholder = Color(0xFFEDE4F2),
    track = Color(0x141F0838),
    trackFill = Color(0xFF5A3A8C),
    surface = Color(0xFFFFFFFF),
    surfaceOutline = Color(0x1F5A3A8C),
    seekSurface = Color(0xFFFFFFFF),
    tintContainer = Color(0xFFF1E8F8),
    listened = Color(0xFFE8896A),
    success = Color(0xFF139B69),
    divider = Color(0x1F5A3A8C),
    isDark = false,
    cardOutline = Color(0x1F5A3A8C),
)

private val SignatureDark = NookastColors(
    background = Color(0xFF14081F),
    textPrimary = Color(0xFFF3ECF7),
    textSecondary = Color(0xFFA796B8),
    accent = Color(0xFFFFB497),
    accentStrong = Color(0xFFF08E6E),
    onAccent = Color(0xFF2A0F3D),
    artworkPlaceholder = Color(0xFF221233),
    track = Color(0xFF2A1A3E),
    trackFill = Color(0xFFFFB497),
    surface = Color(0xFF1F1030),
    surfaceOutline = Color(0xFF3A2752),
    seekSurface = Color(0xBF1F1030),
    tintContainer = Color(0x33C9B2F0),
    listened = Color(0xFFC9B2F0),
    success = Color(0xFF21C57B),
    divider = Color(0xFF34224A),
    isDark = true,
)

/** This colour with its alpha scaled by [factor]; `copy(alpha = …)` would replace a token's own alpha. */
fun Color.scaleAlpha(factor: Float): Color = copy(alpha = (alpha * factor).coerceIn(0f, 1f))
