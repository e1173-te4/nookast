package ru.e1173.nookast.player

import android.app.Application
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MediaCompatibilityRegistryTest {
    @Test
    fun choosesKnownControllerAndFallsBackToStandardProfile() {
        val registry = MediaCompatibilityRegistry.parse(
            """
            {
              "schema_version": 1,
              "default_profile": {
                "id": "standard",
                "controller_packages": [],
                "legacy_browser": true,
                "map_previous_next_to_seek": false,
                "source_name_metadata_key": null
              },
              "profiles": [
                {
                  "id": "jaecoo_j7",
                  "controller_packages": ["com.vendor.player"],
                  "legacy_browser": true,
                  "map_previous_next_to_seek": true,
                  "source_name_metadata_key": "vendor.source_name"
                }
              ]
            }
            """.trimIndent(),
        )

        val known = registry.forController("com.vendor.player")
        assertEquals("jaecoo_j7", known.id)
        assertTrue(known.mapPreviousNextToSeek)
        assertEquals("vendor.source_name", known.sourceNameMetadataKey)

        val unknown = registry.forController("com.example.unknown")
        assertEquals("standard", unknown.id)
        assertNull(unknown.sourceNameMetadataKey)

        val deviceDetected = registry.resolveFor(
            controllerPackageName = "com.example.unknown",
            deviceIdentity = MediaDeviceIdentity(
                manufacturer = "DesaySV",
                brand = "unknown",
                model = "J7 head unit",
                device = "unknown",
                product = "unknown",
                widthPx = 1_440,
                heightPx = 1_920,
                densityDpi = 160,
            ),
        )
        assertEquals("jaecoo_j7", deviceDetected.id)
        assertTrue(deviceDetected.mapPreviousNextToSeek)

        val forcedStandard = registry.resolveFor(
            controllerPackageName = "com.vendor.player",
            deviceIdentity = deviceDetectedIdentity(),
            forcedProfileId = "standard",
        )
        assertEquals("standard", forcedStandard.id)
        assertFalse(forcedStandard.mapPreviousNextToSeek)

        val forcedJaecoo = registry.resolveFor(
            controllerPackageName = "com.example.unknown",
            deviceIdentity = ordinaryPhoneIdentity(),
            forcedProfileId = "jaecoo_j7",
        )
        assertEquals("jaecoo_j7", forcedJaecoo.id)
        assertTrue(forcedJaecoo.mapPreviousNextToSeek)

        assertFalse(
            detectBuiltInMediaProfileId(
                MediaDeviceIdentity(
                    manufacturer = "DesaySV",
                    brand = "unknown",
                    model = "other",
                    device = "unknown",
                    product = "unknown",
                    widthPx = 1_080,
                    heightPx = 2_400,
                    densityDpi = 420,
                ),
            ) != null,
        )
    }

    private fun deviceDetectedIdentity() = MediaDeviceIdentity(
        manufacturer = "DesaySV",
        brand = "unknown",
        model = "J7 head unit",
        device = "unknown",
        product = "unknown",
        widthPx = 1_440,
        heightPx = 1_920,
        densityDpi = 160,
    )

    private fun ordinaryPhoneIdentity() = MediaDeviceIdentity(
        manufacturer = "Huawei",
        brand = "Huawei",
        model = "Phone",
        device = "phone",
        product = "phone",
        widthPx = 1_080,
        heightPx = 2_400,
        densityDpi = 420,
    )
}
