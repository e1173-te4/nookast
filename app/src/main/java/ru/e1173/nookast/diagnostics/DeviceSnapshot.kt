package ru.e1173.nookast.diagnostics

import android.app.Activity
import android.os.Build
import java.util.Locale
import kotlin.math.roundToInt

data class DeviceSnapshot(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
    val androidRelease: String,
    val sdkInt: Int,
    val windowWidthPx: Int,
    val windowHeightPx: Int,
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val smallestWidthDp: Int,
    val densityDpi: Int,
    val density: Float,
    val fontScale: Float,
) {
    /** The known car head unit this is ([ru.e1173.nookast.player.KnownHeadUnits]), if any. */
    val headUnit: ru.e1173.nookast.player.KnownHeadUnit?
        get() = ru.e1173.nookast.player.KnownHeadUnits.detect(
            ru.e1173.nookast.player.MediaDeviceIdentity(manufacturer, brand, model, device, product, windowWidthPx, windowHeightPx, densityDpi),
        )

    val isHeadUnit: Boolean get() = headUnit != null

    val prefersLargeControls: Boolean
        get() = isHeadUnit || (screenWidthDp >= 900 && screenHeightDp >= 1_100)

    val preferredFontScale: Float
        get() = headUnit?.let { maxOf(fontScale, it.minFontScale) } ?: when {
            prefersLargeControls -> maxOf(fontScale, 1.12f)
            else -> fontScale
        }

    val preferredDensityScale: Float
        get() = headUnit?.densityScale ?: 1f

    fun asReport(): String = buildString {
        appendLine("Manufacturer: $manufacturer")
        appendLine("Brand: $brand")
        appendLine("Model: $model")
        appendLine("Device: $device")
        appendLine("Product: $product")
        appendLine("Android: $androidRelease (SDK $sdkInt)")
        appendLine(
            "Profile: " + (headUnit?.label ?: "adaptive"),
        )
        appendLine("Window px: ${windowWidthPx}x$windowHeightPx")
        appendLine("Window dp: ${screenWidthDp}x$screenHeightDp")
        appendLine("Smallest width: $smallestWidthDp dp")
        appendLine("Density: $densityDpi dpi (${String.format(Locale.US, "%.2f", density)}x)")
        appendLine("Font scale: ${String.format(Locale.US, "%.2f", fontScale)}")
        appendLine(
            "Effective UI density scale: " +
                String.format(Locale.US, "%.2f", preferredDensityScale),
        )
        append(
            "Effective UI font scale: " +
                String.format(Locale.US, "%.2f", preferredFontScale),
        )
    }

    companion object {
        fun capture(
            activity: Activity,
            windowWidthPx: Int,
            windowHeightPx: Int,
        ): DeviceSnapshot {
            val resources = activity.resources
            val configuration = resources.configuration
            val displayMetrics = resources.displayMetrics

            return DeviceSnapshot(
                manufacturer = Build.MANUFACTURER.orUnknown(),
                brand = Build.BRAND.orUnknown(),
                model = Build.MODEL.orUnknown(),
                device = Build.DEVICE.orUnknown(),
                product = Build.PRODUCT.orUnknown(),
                androidRelease = Build.VERSION.RELEASE.orUnknown(),
                sdkInt = Build.VERSION.SDK_INT,
                windowWidthPx = windowWidthPx,
                windowHeightPx = windowHeightPx,
                screenWidthDp = (windowWidthPx / displayMetrics.density).roundToInt(),
                screenHeightDp = (windowHeightPx / displayMetrics.density).roundToInt(),
                smallestWidthDp = configuration.smallestScreenWidthDp,
                densityDpi = displayMetrics.densityDpi,
                density = displayMetrics.density,
                fontScale = configuration.fontScale,
            )
        }

        private fun String?.orUnknown(): String = this?.takeIf { it.isNotBlank() } ?: "unknown"
    }
}
