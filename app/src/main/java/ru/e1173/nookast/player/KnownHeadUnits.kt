package ru.e1173.nookast.player

import android.content.Context

/**
 * A car head unit the app knows (user, 6.11.2026): the app recognises the device itself, there is no
 * manual choice. Everything only the car needs (the media-button profile, the large interface, the
 * steering-wheel markers in the journal) shows only on a device from [KnownHeadUnits]. A new head
 * unit is one more entry there.
 */
data class KnownHeadUnit(
    /** The media compatibility profile id in `media_compatibility_profiles.json`. */
    val mediaProfileId: String,
    /** Shown in the diagnostics report. */
    val label: String,
    val minFontScale: Float,
    val densityScale: Float,
    val matches: (MediaDeviceIdentity) -> Boolean,
)

object KnownHeadUnits {
    /**
     * JAECOO J7 (DesaySV, Qualcomm msmnile, platform `g7ph`). Matched by the platform product or by
     * the physical panel: the logical window excludes system bars (1440x1670 on the car, journal of
     * 24.10.2026).
     */
    val JAECOO_J7 = KnownHeadUnit(
        mediaProfileId = "jaecoo_j7",
        label = "JAECOO J7 14.8 / DesaySV",
        minFontScale = 1.25f,
        densityScale = 1.5f,
    ) { identity ->
        val build = listOf(identity.manufacturer, identity.brand, identity.model, identity.device, identity.product)
        val jaecoo = build.any { it.contains("jaecoo", ignoreCase = true) }
        val desaySv = build.any { it.contains("desaysv", ignoreCase = true) }
        val platform = identity.product.startsWith("g7ph", ignoreCase = true)
        val panel = identity.widthPx == 1_440 && identity.heightPx == 1_920 && identity.densityDpi == 160
        jaecoo || (desaySv && (platform || panel))
    }

    val all: List<KnownHeadUnit> = listOf(JAECOO_J7)

    fun detect(identity: MediaDeviceIdentity): KnownHeadUnit? = all.firstOrNull { it.matches(identity) }

    /** The head unit this app runs on, or null; the answer does not change while it runs. */
    fun current(context: Context): KnownHeadUnit? {
        if (!resolved) {
            cached = detect(currentMediaDeviceIdentity(context.applicationContext))
            resolved = true
        }
        return cached
    }

    @Volatile private var cached: KnownHeadUnit? = null

    @Volatile private var resolved = false
}
