package ru.e1173.nookast.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sky moods over the night theme (user, 28.10.2026), from the sun over the user's own place. A dark theme in the morning
 * felt like the end of the day, so before the system switches to light the night palette gets a
 * dawn glow that grows towards sunrise. In the white-nights season the night is never black, so the
 * night theme gets a pale pearl sky. Palettes, geometry and type are not changed: only a wash.
 */
enum class SkyMood { NONE, MORNING, WHITE_NIGHT, EVENING }

data class SkyState(val mood: SkyMood, val intensity: Float) {
    companion object {
        val None = SkyState(SkyMood.NONE, 0f)
    }
}

val LocalSkyState = compositionLocalOf { SkyState.None }

/**
 * The accents follow the sky (immersion, kept as part of the sky since 29.10.2026): in the morning
 * the accent, buttons and timeline warm towards dawn amber, more so towards sunrise; in a white
 * night they cool towards pearl. Only the night theme; the palette itself is untouched.
 */
fun NookastColors.underSky(sky: SkyState): NookastColors {
    if (!isDark || sky.mood == SkyMood.NONE) return this
    val (target, share) = when (sky.mood) {
        SkyMood.MORNING -> DawnAmber to 0.30f * sky.intensity
        SkyMood.WHITE_NIGHT -> PearlLilac to 0.22f * sky.intensity
        SkyMood.EVENING -> SunsetCoral to 0.28f * sky.intensity
        SkyMood.NONE -> return this
    }
    fun Color.toward() = androidx.compose.ui.graphics.lerp(this, target.copy(alpha = alpha), share)
    return copy(
        accent = accent.toward(),
        accentStrong = accentStrong.toward(),
        trackFill = trackFill.toward(),
        tintContainer = tintContainer.toward(),
        accentText = accentText.toward(),
        accentFill = accentFill.toward(),
    )
}

/** The cover wash under a sky: its colours lean a quarter of the way to the sky's own colour. */
fun Color.blendedWithSky(sky: SkyState): Color = when (sky.mood) {
    SkyMood.MORNING -> androidx.compose.ui.graphics.lerp(this, DawnPeach.copy(alpha = alpha), 0.25f * sky.intensity)
    SkyMood.WHITE_NIGHT -> androidx.compose.ui.graphics.lerp(this, PearlLilac.copy(alpha = alpha), 0.25f * sky.intensity)
    SkyMood.EVENING -> androidx.compose.ui.graphics.lerp(this, DuskRose.copy(alpha = alpha), 0.25f * sky.intensity)
    SkyMood.NONE -> this
}

private val DawnAmber = Color(0xFFFFB27A)
private val SunsetCoral = Color(0xFFFF9472)

/**
 * The mood from where the sun actually is over the place (user, 29.10.2026: one switch, driven by
 * the sun): white nights while it is below the horizon in their season, the morning from nautical
 * dawn until it stands 12° high or reaches noon, the evening (user, 29.10.2026) the same way down
 * from 12° to nautical dusk, nothing otherwise. Each mood is switched on its own under «Immersion».
 */
internal fun skyState(
    now: ZonedDateTime,
    latitude: Double,
    longitude: Double,
    morning: Boolean = true,
    whiteNights: Boolean = true,
    evening: Boolean = true,
): SkyState {
    val hours = now.hour + now.minute / 60.0 + now.second / 3_600.0
    val day = now.dayOfYear
    val offsetHours = now.offset.totalSeconds / 3_600.0
    val noon = solarNoonLocalHours(day, longitude, offsetHours)
    val elevation = solarElevationDegrees(day, hours - noon, latitude)
    if (whiteNights && isWhiteNightSeason(day, latitude) && elevation < SUNRISE_ELEVATION) {
        // Fuller as the sun sinks: half at sunset, all of it 4° below.
        val depth = ((SUNRISE_ELEVATION - elevation) / 3.2).coerceIn(0.0, 1.0)
        return SkyState(SkyMood.WHITE_NIGHT, (0.5 + 0.5 * depth).toFloat())
    }
    val afternoon = hours >= noon
    if (elevation < MORNING_FROM_ELEVATION || elevation > MORNING_UNTIL_ELEVATION) return SkyState.None
    if (if (afternoon) !evening else !morning) return SkyState.None
    val intensity = when {
        elevation < MORNING_FULL_ELEVATION ->
            0.35 + 0.65 * (elevation - MORNING_FROM_ELEVATION) / (MORNING_FULL_ELEVATION - MORNING_FROM_ELEVATION)
        elevation < MORNING_FADE_ELEVATION -> 1.0
        else -> 1.0 - 0.8 * (elevation - MORNING_FADE_ELEVATION) / (MORNING_UNTIL_ELEVATION - MORNING_FADE_ELEVATION)
    }
    // The evening mirrors the morning: it starts faint high up and is fullest around sunset.
    return SkyState(if (afternoon) SkyMood.EVENING else SkyMood.MORNING, intensity.toFloat().coerceIn(0f, 1f))
}

/** The sun's height above the horizon in degrees, [hoursFromNoon] local solar hours from noon. */
internal fun solarElevationDegrees(dayOfYear: Int, hoursFromNoon: Double, latitude: Double): Double {
    val phi = Math.toRadians(latitude)
    val delta = Math.toRadians(solarDeclinationDegrees(dayOfYear))
    val hourAngle = Math.toRadians(15.0 * hoursFromNoon)
    return Math.toDegrees(asin(sin(phi) * sin(delta) + cos(phi) * cos(delta) * cos(hourAngle)))
}

/** Local clock time of solar noon in hours. */
internal fun solarNoonLocalHours(dayOfYear: Int, longitude: Double, utcOffsetHours: Double): Double {
    val b = 2 * PI * (dayOfYear - 81) / 364.0
    val equationOfTimeMinutes = 9.87 * sin(2 * b) - 7.53 * cos(b) - 1.5 * sin(b)
    return 12.0 - longitude / 15.0 + utcOffsetHours - equationOfTimeMinutes / 60.0
}

/** The sun stays less than 8° below the horizon all night (at 60° north: early June to mid July). */
internal fun isWhiteNightSeason(dayOfYear: Int, latitude: Double): Boolean =
    latitude + solarDeclinationDegrees(dayOfYear) - 90.0 > -WHITE_NIGHT_DEPRESSION_DEGREES

internal fun solarDeclinationDegrees(dayOfYear: Int): Double =
    23.44 * sin(2 * PI * (284 + dayOfYear) / 365.0)

/** Local clock time of sunrise in hours, or null in polar day or night. */
internal fun sunriseLocalHours(dayOfYear: Int, latitude: Double, longitude: Double, utcOffsetHours: Double): Double? {
    val phi = Math.toRadians(latitude)
    val delta = Math.toRadians(solarDeclinationDegrees(dayOfYear))
    val cosH = (sin(Math.toRadians(SUNRISE_ELEVATION)) - sin(phi) * sin(delta)) / (cos(phi) * cos(delta))
    if (cosH !in -1.0..1.0) return null
    val halfDayHours = Math.toDegrees(acos(cosH)) / 15.0
    val solarNoon = solarNoonLocalHours(dayOfYear, longitude, utcOffsetHours)
    return solarNoon - halfDayHours
}

private const val SUNRISE_ELEVATION = -0.833
private const val MORNING_FROM_ELEVATION = -12.0
private const val MORNING_FULL_ELEVATION = -4.0
private const val MORNING_FADE_ELEVATION = 6.0
private const val MORNING_UNTIL_ELEVATION = 12.0
private const val WHITE_NIGHT_DEPRESSION_DEGREES = 8.0

/**
 * The cover's main colour while Now Playing shows its wash; the sky takes part of it so the two
 * read as one light (user, 29.10.2026: the cover's effect on the sky was not visible).
 */
val skyCoverTint = androidx.compose.runtime.mutableStateOf<Color?>(null)

/**
 * The sky as light falling over the whole app, not a backdrop: screens and cards have their own
 * opaque backgrounds that hid it (user, 28.10.2026). It is drawn after the content in Screen
 * blending, which only brightens — dark surfaces take the dawn or pearl colour, light text stays as
 * it is. Gradients are dithered and fade with an eased curve, so a low glow over near-black shows
 * no bands (user, 29.10.2026: the white-night top looked rough). Nothing in a light theme.
 */
@Composable
fun skyOverlay(): Modifier {
    val sky = LocalSkyState.current
    if (sky.mood == SkyMood.NONE || !LocalNookastColors.current.isDark) return Modifier
    val cover by skyCoverTint
    return Modifier.drawWithCache {
        val glows = skyGlows(sky, cover, size.width, size.height)
        onDrawWithContent {
            drawContent()
            drawIntoCanvas { canvas -> glows.forEach { canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, it) } }
        }
    }
}

private class Glow(val color: Color, val alpha: Float, val x: Float, val y: Float, val radius: Float)

private fun skyGlows(sky: SkyState, cover: Color?, w: Float, h: Float): List<android.graphics.Paint> {
    val k = sky.intensity
    val big = maxOf(w, h)

    // The main glow leans towards the cover when Now Playing is open.
    fun main(color: Color) = if (cover == null) color else androidx.compose.ui.graphics.lerp(color, cover, COVER_SHARE)
    val glows = when (sky.mood) {
        // Dawn: a peach glow from the top right, a rose one from the left.
        SkyMood.MORNING -> listOf(
            Glow(main(DawnPeach), 0.26f * k, w * 0.65f, -h * 0.06f, big * 0.62f),
            Glow(DawnRose, 0.14f * k, 0f, h * 0.04f, big * 0.46f),
        )
        // White night: one wide pearl dome over the top and a cool silver side light.
        SkyMood.WHITE_NIGHT -> listOf(
            Glow(main(PearlLilac), 0.22f * k, w * 0.5f, -h * 0.12f, big * 0.75f),
            Glow(PearlSilver, 0.10f * k, w, h * 0.12f, big * 0.42f),
        )
        // Sunset: a coral glow low from the top left, a violet dusk from the right.
        SkyMood.EVENING -> listOf(
            Glow(main(SunsetAmber), 0.24f * k, w * 0.3f, -h * 0.06f, big * 0.62f),
            Glow(DuskViolet, 0.14f * k, w, h * 0.04f, big * 0.46f),
        )
        SkyMood.NONE -> emptyList()
    }
    val lift = if (sky.mood == SkyMood.WHITE_NIGHT) {
        // The whole sky a little lighter: a flat layer cannot band.
        listOf(android.graphics.Paint().apply {
            color = PearlLift.copy(alpha = 0.07f * k).toArgb()
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SCREEN)
        })
    } else {
        emptyList()
    }
    return lift + glows.map { glow -> glow.paint() }
}

private fun Glow.paint(): android.graphics.Paint {
    val stops = floatArrayOf(0f, 0.3f, 0.6f, 0.85f, 1f)
    val shape = floatArrayOf(1f, 0.72f, 0.34f, 0.09f, 0f)
    val colors = IntArray(stops.size) { color.copy(alpha = (alpha * shape[it]).coerceIn(0f, 1f)).toArgb() }
    return android.graphics.Paint().apply {
        isDither = true
        shader = android.graphics.RadialGradient(x, y, radius.coerceAtLeast(1f), colors, stops, android.graphics.Shader.TileMode.CLAMP)
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SCREEN)
    }
}

private const val COVER_SHARE = 0.45f

private val DawnPeach = Color(0xFFFFA36C)
private val DawnRose = Color(0xFFE86A8C)
private val PearlLift = Color(0xFF9A96B8)
private val PearlLilac = Color(0xFFCFC8EE)
private val PearlSilver = Color(0xFFB8D4E6)
private val SunsetAmber = Color(0xFFFF8A5C)
private val DuskViolet = Color(0xFF9B6BD6)
private val DuskRose = Color(0xFFE88AA0)
