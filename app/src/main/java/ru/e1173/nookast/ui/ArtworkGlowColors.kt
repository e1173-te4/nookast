package ru.e1173.nookast.ui

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The two glow colours of a cover from its ARGB [pixels]: the dominant hue weighted by saturation
 * and brightness, then the strongest hue at least 45° away (or the first again). Each is lifted to
 * a visible but not loud tone. A cover without colour gives a neutral grey glow, never a tinted one.
 */
internal fun artworkGlowColors(pixels: IntArray): List<Int> {
    val weights = DoubleArray(HUE_BINS)
    val sums = Array(HUE_BINS) { DoubleArray(3) }
    var greySum = 0.0
    var greyCount = 0
    pixels.forEach { pixel ->
        if ((pixel ushr 24) < 128) return@forEach
        val hsv = rgbToHsv(pixel)
        if (hsv[1] < MIN_SATURATION || hsv[2] < MIN_VALUE) {
            greySum += hsv[2]
            greyCount++
            return@forEach
        }
        val bin = ((hsv[0] / 360f) * HUE_BINS).toInt().coerceIn(0, HUE_BINS - 1)
        val weight = (hsv[1] * hsv[2]).toDouble()
        weights[bin] += weight
        sums[bin][0] += hsv[0] * weight
        sums[bin][1] += hsv[1] * weight
        sums[bin][2] += hsv[2] * weight
    }
    val total = weights.sum()
    if (total < MIN_COLOURED_SHARE * pixels.size) {
        val value = if (greyCount > 0) (greySum / greyCount).toFloat() else 0.6f
        val grey = hsvToRgb(0f, 0f, value.coerceIn(0.55f, 0.85f))
        return listOf(grey, grey)
    }
    // Neighbouring bins count with their peak so a hue on a bin edge is not split in two.
    fun score(bin: Int) = weights[bin] + 0.5 * (weights[(bin + 1) % HUE_BINS] + weights[(bin + HUE_BINS - 1) % HUE_BINS])
    val first = (0 until HUE_BINS).maxBy(::score)
    val second = (0 until HUE_BINS)
        .filter { binDistance(it, first) >= MIN_SECOND_DISTANCE && weights[it] >= weights[first] * 0.25 }
        .maxByOrNull(::score) ?: first
    return listOf(first, second).map { bin ->
        val w = weights[bin]
        val hue = (sums[bin][0] / w).toFloat()
        val saturation = ((sums[bin][1] / w).toFloat() * 1.15f).coerceIn(0.3f, 0.85f)
        val value = (sums[bin][2] / w).toFloat().coerceIn(0.55f, 0.95f)
        hsvToRgb(hue, saturation, value)
    }
}

private const val HUE_BINS = 24
private const val MIN_SATURATION = 0.18f
private const val MIN_VALUE = 0.15f
private const val MIN_COLOURED_SHARE = 0.03
private const val MIN_SECOND_DISTANCE = 3

private fun binDistance(a: Int, b: Int): Int = min(abs(a - b), HUE_BINS - abs(a - b))

internal fun rgbToHsv(pixel: Int): FloatArray {
    val r = ((pixel shr 16) and 0xFF) / 255f
    val g = ((pixel shr 8) and 0xFF) / 255f
    val b = (pixel and 0xFF) / 255f
    val high = max(r, max(g, b))
    val low = min(r, min(g, b))
    val delta = high - low
    val hue = when {
        delta == 0f -> 0f
        high == r -> 60f * (((g - b) / delta) % 6f)
        high == g -> 60f * ((b - r) / delta + 2f)
        else -> 60f * ((r - g) / delta + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return floatArrayOf(hue, if (high == 0f) 0f else delta / high, high)
}

internal fun hsvToRgb(hue: Float, saturation: Float, value: Float): Int {
    val c = value * saturation
    val x = c * (1 - abs((hue / 60f) % 2f - 1))
    val m = value - c
    val (r, g, b) = when ((hue / 60f).toInt().coerceIn(0, 5)) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun channel(v: Float) = ((v + m) * 255f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
}
