package ru.e1173.nookast.player

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import org.json.JSONObject

data class MediaCompatibilityProfile(
    val id: String,
    val controllerPackages: Set<String>,
    val legacyBrowser: Boolean,
    val mapPreviousNextToSeek: Boolean,
    val sourceNameMetadataKey: String?,
)

data class MediaDeviceIdentity(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
)

class MediaCompatibilityRegistry private constructor(
    val defaultProfile: MediaCompatibilityProfile,
    private val profiles: List<MediaCompatibilityProfile>,
) {
    fun forController(packageName: String): MediaCompatibilityProfile =
        profiles.firstOrNull { packageName in it.controllerPackages } ?: defaultProfile

    fun byId(id: String): MediaCompatibilityProfile? =
        if (defaultProfile.id == id) defaultProfile else profiles.firstOrNull { it.id == id }

    fun resolveFor(
        controllerPackageName: String,
        deviceIdentity: MediaDeviceIdentity,
        forcedProfileId: String? = null,
    ): MediaCompatibilityProfile {
        if (forcedProfileId != null) return byId(forcedProfileId) ?: defaultProfile
        val controllerProfile = forController(controllerPackageName)
        if (controllerProfile.id != defaultProfile.id) return controllerProfile
        val deviceProfileId = detectBuiltInMediaProfileId(deviceIdentity) ?: return defaultProfile
        return byId(deviceProfileId) ?: defaultProfile
    }

    companion object {
        fun load(context: Context): MediaCompatibilityRegistry =
            context.assets.open(ASSET_NAME).bufferedReader().use { reader ->
                parse(reader.readText())
            }

        internal fun parse(json: String): MediaCompatibilityRegistry {
            val root = JSONObject(json)
            require(root.getInt("schema_version") == 1)
            val defaultProfile = root.getJSONObject("default_profile").toProfile()
            val profilesJson = root.getJSONArray("profiles")
            val profiles = buildList(profilesJson.length()) {
                for (index in 0 until profilesJson.length()) {
                    add(profilesJson.getJSONObject(index).toProfile())
                }
            }
            require(profiles.none { it.id == defaultProfile.id })
            return MediaCompatibilityRegistry(defaultProfile, profiles)
        }

        private const val ASSET_NAME = "media_compatibility_profiles.json"
    }
}

/**
 * JAECOO J7 head unit (DesaySV, Qualcomm msmnile, platform `g7ph`). Matched by the platform
 * product or by the physical panel. Sizes are the REAL display size: the logical window excludes
 * system bars (1440x1670 on the car, journal of 24.10.2026), which is why the old check failed.
 */
internal fun detectBuiltInMediaProfileId(identity: MediaDeviceIdentity): String? {
    val buildIdentity = listOf(
        identity.manufacturer,
        identity.brand,
        identity.model,
        identity.device,
        identity.product,
    )
    val identifiesJaecoo = buildIdentity.any { it.contains("jaecoo", ignoreCase = true) }
    val identifiesDesaySv = buildIdentity.any { it.contains("desaysv", ignoreCase = true) }
    val knownPlatform = identity.product.startsWith("g7ph", ignoreCase = true)
    val knownPanel = identity.widthPx == 1_440 && identity.heightPx == 1_920 &&
        identity.densityDpi == 160
    return if (identifiesJaecoo || (identifiesDesaySv && (knownPlatform || knownPanel))) {
        "jaecoo_j7"
    } else {
        null
    }
}

/** Identity of this device with the physical size of the default display. */
internal fun currentMediaDeviceIdentity(context: Context): MediaDeviceIdentity {
    val (widthPx, heightPx, densityDpi) = realDisplaySize(context)
    return MediaDeviceIdentity(
        manufacturer = Build.MANUFACTURER.orEmpty(),
        brand = Build.BRAND.orEmpty(),
        model = Build.MODEL.orEmpty(),
        device = Build.DEVICE.orEmpty(),
        product = Build.PRODUCT.orEmpty(),
        widthPx = widthPx,
        heightPx = heightPx,
        densityDpi = densityDpi,
    )
}

/** Physical width, height and density of the default display (not the app window). */
internal fun realDisplaySize(context: Context): Triple<Int, Int, Int> {
    val display = context.getSystemService(DisplayManager::class.java)
        ?.getDisplay(Display.DEFAULT_DISPLAY)
    val metrics = DisplayMetrics()
    if (display != null) {
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
    } else {
        metrics.setTo(context.resources.displayMetrics)
    }
    return Triple(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
}

private fun JSONObject.toProfile(): MediaCompatibilityProfile {
    val packagesJson = getJSONArray("controller_packages")
    val packages = buildSet(packagesJson.length()) {
        for (index in 0 until packagesJson.length()) add(packagesJson.getString(index))
    }
    return MediaCompatibilityProfile(
        id = getString("id").also { require(it.isNotBlank()) },
        controllerPackages = packages,
        legacyBrowser = getBoolean("legacy_browser"),
        mapPreviousNextToSeek = getBoolean("map_previous_next_to_seek"),
        sourceNameMetadataKey = if (isNull("source_name_metadata_key")) {
            null
        } else {
            getString("source_name_metadata_key").takeIf(String::isNotBlank)
        },
    )
}
