package ru.e1173.nookast.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

/** The sky's place is rough and off unless chosen (user, 2.11.2026). */
class SkyPlaceTest {
    @Test
    fun `a place keeps one decimal of a degree`() {
        val place = SkyPlace.rough(latitude = 48.85661, longitude = 2.35222, fixedAtEpochMs = 7L)

        assertEquals(48.9, place.latitude, 1e-9)
        assertEquals(2.4, place.longitude, 1e-9)
        assertEquals(7L, place.fixedAtEpochMs)
    }

    @Test
    fun `no stored mode means the place is not detected`() {
        assertEquals(SkyPlaceMode.OFF, SkyPlaceMode.fromStoredValue(null))
        assertEquals(SkyPlaceMode.OFF, SkyPlaceMode.fromStoredValue("SOMETHING"))
        assertEquals(SkyPlaceMode.AUTO, SkyPlaceMode.fromStoredValue("AUTO"))
    }
}
