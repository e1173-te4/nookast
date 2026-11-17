package ru.e1173.nookast.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ThemeNewBadgeTest {
    @Test
    fun `the three new themes are marked in 1_2_6 and 1_2_7 only`() {
        val marked = { code: Int -> ThemePalette.entries.filter { it.showsNewBadge(code) } }
        val three = listOf(ThemePalette.ON_AIR, ThemePalette.FIREFLY, ThemePalette.BEACON)

        assertEquals(three, marked(71))
        assertEquals(three, marked(72))
        assertEquals(emptyList<ThemePalette>(), marked(73))
    }

    @Test
    fun `older themes are never marked`() {
        assertFalse(ThemePalette.SIGNATURE.showsNewBadge(71))
    }
}
