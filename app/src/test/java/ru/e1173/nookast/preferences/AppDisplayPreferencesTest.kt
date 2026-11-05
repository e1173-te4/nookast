package ru.e1173.nookast.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDisplayPreferencesTest {
    @Test
    fun unknownStoredValuesUseSafeAutomaticDefaults() {
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStoredValue(null))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStoredValue("CUSTOM_REMOVED"))
        assertEquals(InterfaceProfile.AUTO, InterfaceProfile.fromStoredValue(null))
        assertEquals(InterfaceProfile.AUTO, InterfaceProfile.fromStoredValue("OLD_HEAD_UNIT"))
        assertEquals(PodcastSortMode.UPDATED, PodcastSortMode.fromStoredValue(null))
        assertEquals(PodcastSortMode.UPDATED, PodcastSortMode.fromStoredValue("RECENT_REMOVED"))
        assertEquals(PodcastViewMode.GRID, PodcastViewMode.fromStoredValue(null))
        assertEquals(PodcastViewMode.GRID, PodcastViewMode.fromStoredValue("CARDS_REMOVED"))
    }

    @Test
    fun jaecooOverrideForcesLargeControlsAndReadableFont() {
        val result = resolveInterfacePresentation(
            profile = InterfaceProfile.JAECOO_J7,
            automaticLargeControls = false,
            automaticFontScale = 1f,
            systemFontScale = 0.9f,
        )

        assertTrue(result.largeControls)
        assertEquals(1.25f, result.fontScale)
        assertEquals(1.5f, result.densityScale)
    }

    @Test
    fun standardOverrideCanDisableAccidentalLargeScreenDetection() {
        val result = resolveInterfacePresentation(
            profile = InterfaceProfile.STANDARD,
            automaticLargeControls = true,
            automaticFontScale = 1.25f,
            systemFontScale = 1.1f,
        )

        assertFalse(result.largeControls)
        assertEquals(1.1f, result.fontScale)
        assertEquals(1f, result.densityScale)
    }

    @Test
    fun podcastIdSetsAreIndependentAndIgnoreInvalidValues() {
        val collapsed = parsePodcastIdSet(setOf("7", "bad", "0", "-3"))
        val hidden = updatePodcastIdSet(emptySet(), podcastId = 11, included = true)

        assertEquals(setOf(7), collapsed)
        assertEquals(setOf(11), hidden)
        assertEquals(setOf(7), updatePodcastIdSet(collapsed, podcastId = 11, included = false))
    }
}
