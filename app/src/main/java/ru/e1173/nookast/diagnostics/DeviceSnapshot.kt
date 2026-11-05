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
    val isJaecooJ7HeadUnit: Boolean
        get() {
            val identity = listOf(manufacturer, brand, model, device, product)
            val identifiesJaecoo = identity.any { it.contains("jaecoo", ignoreCase = true) }
            val identifiesDesaySv = identity.any { it.contains("desaysv", ignoreCase = true) }
            val matchesKnownPanel = windowWidthPx == 1_440 && windowHeightPx == 1_920 &&
                densityDpi == 160

            return identifiesJaecoo || (identifiesDesaySv && matchesKnownPanel)
        }

    val prefersLargeControls: Boolean
        get() = isJaecooJ7HeadUnit || (screenWidthDp >= 900 && screenHeightDp >= 1_100)

    val preferredFontScale: Float
        get() = when {
            isJaecooJ7HeadUnit -> maxOf(fontScale, 1.25f)
            prefersLargeControls -> maxOf(fontScale, 1.12f)
            else -> fontScale
        }

    val preferredDensityScale: Float
        get() = if (isJaecooJ7HeadUnit) 1.5f else 1f

    fun asReport(): String = buildString {
        appendLine("Manufacturer: $manufacturer")
        appendLine("Brand: $brand")
        appendLine("Model: $model")
        appendLine("Device: $device")
        appendLine("Product: $product")
        appendLine("Android: $androidRelease (SDK $sdkInt)")
        appendLine(
            "Profile: " + if (isJaecooJ7HeadUnit) {
                "JAECOO J7 14.8 / DesaySV"
            } else {
                "adaptive"
            },
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
