package ru.e1173.nookast.designsystem

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyModesTest {
    // A point at 60° north, UTC+3: white nights in June, short winter days.
    private val zone = ZoneId.of("Europe/Moscow")
    private val NORTH_LATITUDE = 60.0
    private val NORTH_LONGITUDE = 30.0

    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, zone)

    @Test
    fun `white nights cover June at 60 degrees north but not May or August`() {
        assertTrue(isWhiteNightSeason(at(6, 21, 0).dayOfYear, NORTH_LATITUDE))
        assertFalse(isWhiteNightSeason(at(5, 10, 0).dayOfYear, NORTH_LATITUDE))
        assertFalse(isWhiteNightSeason(at(8, 10, 0).dayOfYear, NORTH_LATITUDE))
        assertFalse(isWhiteNightSeason(at(6, 21, 0).dayOfYear, 55.75))
    }

    @Test
    fun `sunrise at 60 degrees north is near known times`() {
        val december = sunriseLocalHours(at(12, 21, 0).dayOfYear, NORTH_LATITUDE, NORTH_LONGITUDE, 3.0)!!
        val june = sunriseLocalHours(at(6, 21, 0).dayOfYear, NORTH_LATITUDE, NORTH_LONGITUDE, 3.0)!!
        // Real values: about 10:00 and 3:35.
        assertEquals(10.0, december, 0.25)
        assertEquals(3.6, june, 0.25)
    }

    @Test
    fun `mood follows the sun`() {
        assertEquals(SkyMood.WHITE_NIGHT, skyState(at(6, 21, 23), NORTH_LATITUDE, NORTH_LONGITUDE).mood)
        // A June evening before sunset and a summer morning after sunrise.
        assertEquals(SkyMood.NONE, skyState(at(6, 21, 19), NORTH_LATITUDE, NORTH_LONGITUDE).mood)
        assertEquals(SkyMood.MORNING, skyState(at(6, 21, 5), NORTH_LATITUDE, NORTH_LONGITUDE).mood)
        val earlyWinterMorning = skyState(at(11, 20, 8), NORTH_LATITUDE, NORTH_LONGITUDE)
        assertEquals(SkyMood.MORNING, earlyWinterMorning.mood)
        assertTrue(earlyWinterMorning.intensity in 0.35f..0.99f)
        // Before nautical dawn the winter morning is still night.
        assertEquals(SkyMood.NONE, skyState(at(11, 20, 7), NORTH_LATITUDE, NORTH_LONGITUDE).mood)
        assertEquals(1f, skyState(at(11, 20, 10), NORTH_LATITUDE, NORTH_LONGITUDE).intensity)
        // A November afternoon with the sun low is the evening, unless the evening is off.
        assertEquals(SkyMood.EVENING, skyState(at(11, 20, 15), NORTH_LATITUDE, NORTH_LONGITUDE).mood)
        assertEquals(SkyMood.NONE, skyState(at(11, 20, 15), NORTH_LATITUDE, NORTH_LONGITUDE, evening = false).mood)
        assertEquals(SkyMood.NONE, skyState(at(11, 20, 8), NORTH_LATITUDE, NORTH_LONGITUDE, morning = false).mood)
        assertEquals(SkyMood.WHITE_NIGHT, skyState(at(6, 21, 23), NORTH_LATITUDE, NORTH_LONGITUDE, evening = false).mood)
        assertEquals(SkyMood.NONE, skyState(at(11, 20, 23), NORTH_LATITUDE, NORTH_LONGITUDE).mood)
    }
}
