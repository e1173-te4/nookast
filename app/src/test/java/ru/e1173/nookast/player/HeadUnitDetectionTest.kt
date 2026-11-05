package ru.e1173.nookast.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Identities from the car journal of 24.10.2026 (finding P-05). */
class HeadUnitDetectionTest {
    private fun identity(product: String, width: Int, height: Int, dpi: Int) = MediaDeviceIdentity(
        manufacturer = "DesaySV",
        brand = "DesaySV",
        model = "DesaySV",
        device = "msmnile_gvmq",
        product = product,
        widthPx = width,
        heightPx = height,
        densityDpi = dpi,
    )

    @Test
    fun realJaecooHeadUnitIsDetectedByPlatformEvenWithTheLogicalWindowSize() {
        // The service used to pass the logical size (system bars excluded): 1440x1670.
        assertEquals("jaecoo_j7", detectBuiltInMediaProfileId(identity("g7ph_t22_int", 1_440, 1_670, 160)))
    }

    @Test
    fun physicalPanelSizeAloneStillIdentifiesADesaySvUnit() {
        assertEquals("jaecoo_j7", detectBuiltInMediaProfileId(identity("unknown", 1_440, 1_920, 160)))
    }

    @Test
    fun otherDesaySvUnitsAreNotAssumedToBeJaecoo() {
        assertNull(detectBuiltInMediaProfileId(identity("other_platform", 1_920, 720, 160)))
    }
}
