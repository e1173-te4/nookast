package ru.e1173.nookast.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colour themes of DEC-017 and DEC-061. A theme changes colours, the night glow and the timeline
 * (track and thumb) only; the geometry is shared by all of them.
 */
enum class ThemePalette {
    /** Signature theme of the app icon: plum night, lavender and a peach accent (user, 27.10.2026); the
     * default and first in the list (user, 2.11.2026). */
    SIGNATURE,
    LAVENDER,
    AUTUMN,
    WINTER,
    MINT,

    /** «Эфир»: night radio, graphite and fuchsia (user, 13.11.2026, the eight-theme mock). */
    ON_AIR,

    /** «Светлячок»: moss and wormwood with a firefly light (user, 13.11.2026). */
    FIREFLY,

    /** «Маяк»: a teal sea and a rust beacon (user, 13.11.2026). */
    BEACON;

    /** The three themes of 13.11.2026. */
    val isNew: Boolean get() = this == ON_AIR || this == FIREFLY || this == BEACON

    /**
     * Whether the settings list marks this theme «new» in the build [versionCode]: only in the two
     * releases that bring the themes, 1.2.6 (71) and 1.2.7 (72); from 1.2.8 the mark is gone
     * (user, 14.11.2026).
     */
    fun showsNewBadge(versionCode: Int): Boolean = isNew && versionCode <= NEW_BADGE_LAST_VERSION_CODE

    companion object {
        /** The last build whose theme list shows «new»: 1.2.7. */
        const val NEW_BADGE_LAST_VERSION_CODE = 72

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
    ThemePalette.ON_AIR -> if (dark) OnAirDark else OnAirLight
    ThemePalette.FIREFLY -> if (dark) FireflyDark else FireflyLight
    ThemePalette.BEACON -> if (dark) BeaconDark else BeaconLight
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

// The three themes of the eight-theme mock (user, 13.11.2026, DEC-061): values of its THEMES array;
// surfaces follow the existing night themes (a nearly opaque card, the seek glass at 75 %).
private val OnAirLight = NookastColors(
    background = Color(0xFFF1F5F9),
    textPrimary = Color(0xFF121212),
    textSecondary = Color(0xFF5B6880),
    accent = Color(0xFFDB2777),
    accentStrong = Color(0xFF9D174D),
    onAccent = Color(0xFFFFFFFF),
    artworkPlaceholder = Color(0xFFE2E8F0),
    track = Color(0xFFCBD5E1),
    trackFill = Color(0xFFDB2777),
    surface = Color(0xFFFFFFFF),
    surfaceOutline = Color(0x22334155),
    seekSurface = Color(0xFFFFFFFF),
    tintContainer = Color(0xFFFCE9F1),
    listened = Color(0xFF0891B2),
    success = Color(0xFF0891B2),
    divider = Color(0x22334155),
    isDark = false,
    cardOutline = Color(0x22334155),
    accentText = Color(0xFFBE185D),
    // White on the fuchsia itself is 4.4:1: labelled fills take the deep one.
    accentFill = Color(0xFF9D174D),
)

private val OnAirDark = NookastColors(
    background = Color(0xFF121318),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF8E9BB0),
    accent = Color(0xFFEC4899),
    accentStrong = Color(0xFF9D174D),
    onAccent = Color(0xFF1A0610),
    artworkPlaceholder = Color(0xFF1F2937),
    track = Color(0xFF1F2937),
    trackFill = Color(0xFFEC4899),
    surface = Color(0xE61F2937),
    surfaceOutline = Color(0x99334155),
    seekSurface = Color(0xBF1F2937),
    tintContainer = Color(0x33EC4899),
    listened = Color(0xFF22D3EE),
    success = Color(0xFF22D3EE),
    divider = Color(0xFF2A3444),
    isDark = true,
    accentText = Color(0xFFF472B6),
)

private val FireflyLight = NookastColors(
    background = Color(0xFFEEF0E6),
    textPrimary = Color(0xFF0F1F17),
    textSecondary = Color(0xFF56664F),
    accent = Color(0xFF5E7F2C),
    accentStrong = Color(0xFF3E5A3F),
    onAccent = Color(0xFFFFFFFF),
    artworkPlaceholder = Color(0xFFE3E6D8),
    track = Color(0xFFC7CBB8),
    trackFill = Color(0xFF5E7F2C),
    surface = Color(0xFFFFFFFF),
    surfaceOutline = Color(0x263E5A3F),
    seekSurface = Color(0xFFFFFFFF),
    tintContainer = Color(0xFFE7EDDD),
    listened = Color(0xFF3E7A3F),
    success = Color(0xFF3E7A3F),
    divider = Color(0x263E5A3F),
    isDark = false,
    cardOutline = Color(0x263E5A3F),
    accentText = Color(0xFF3E5A3F),
)

private val FireflyDark = NookastColors(
    background = Color(0xFF0C1812),
    textPrimary = Color(0xFFE4E7D8),
    textSecondary = Color(0xFF93A28F),
    accent = Color(0xFFDCF27A),
    accentStrong = Color(0xFF7E9B3A),
    onAccent = Color(0xFF14200B),
    artworkPlaceholder = Color(0xFF1E3A2E),
    track = Color(0xFF2A4536),
    trackFill = Color(0xFFDCF27A),
    surface = Color(0xE61E3A2E),
    surfaceOutline = Color(0x993E5A3F),
    seekSurface = Color(0xBF1E3A2E),
    tintContainer = Color(0x26DCF27A),
    listened = Color(0xFFA7D58A),
    success = Color(0xFFA7D58A),
    divider = Color(0xFF2C4636),
    isDark = true,
    accentText = Color(0xFFC9DE74),
)

private val BeaconLight = NookastColors(
    background = Color(0xFFF3ECE2),
    textPrimary = Color(0xFF0A0F14),
    textSecondary = Color(0xFF2E5F63),
    accent = Color(0xFFC6601F),
    accentStrong = Color(0xFF8E4013),
    onAccent = Color(0xFFFFFFFF),
    artworkPlaceholder = Color(0xFFE8DFD2),
    track = Color(0xFFBFD6D4),
    trackFill = Color(0xFFC6601F),
    surface = Color(0xFFFFFFFF),
    surfaceOutline = Color(0x260F3D3E),
    seekSurface = Color(0xFFFFFFFF),
    tintContainer = Color(0xFFF9EEE6),
    listened = Color(0xFF2E6F73),
    success = Color(0xFF2E6F73),
    divider = Color(0x260F3D3E),
    isDark = false,
    cardOutline = Color(0x220F3D3E),
    accentText = Color(0xFFA84F18),
    // White on the rust itself is 4.1:1.
    accentFill = Color(0xFF8E4013),
)

private val BeaconDark = NookastColors(
    background = Color(0xFF0A1114),
    textPrimary = Color(0xFFEADCC8),
    textSecondary = Color(0xFF86B4B8),
    accent = Color(0xFFE07433),
    accentStrong = Color(0xFF9A4413),
    onAccent = Color(0xFF1A0A02),
    artworkPlaceholder = Color(0xFF0F3D3E),
    track = Color(0xFF1C4A4C),
    trackFill = Color(0xFFE07433),
    surface = Color(0xE60F3D3E),
    surfaceOutline = Color(0x992E6F73),
    seekSurface = Color(0xBF0F3D3E),
    tintContainer = Color(0x33E07433),
    listened = Color(0xFF7FB3B8),
    success = Color(0xFF7FB3B8),
    divider = Color(0xFF1E4A4C),
    isDark = true,
    accentText = Color(0xFFF09A62),
)

/** This colour with its alpha scaled by [factor]; `copy(alpha = …)` would replace a token's own alpha. */
fun Color.scaleAlpha(factor: Float): Color = copy(alpha = (alpha * factor).coerceIn(0f, 1f))
