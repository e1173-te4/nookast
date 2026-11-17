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
        // Before nautical dawn the winter morning is night by the sun alone.
        assertEquals(SkyMood.NONE, skyState(at(11, 20, 7), NORTH_LATITUDE, NORTH_LONGITUDE, morningFromHours = null).mood)
        assertEquals(1f, skyState(at(11, 20, 10), NORTH_LATITUDE, NORTH_LONGITUDE).intensity)
        // A November afternoon with the sun low is the evening, unless the evening is off.
        assertEquals(SkyMood.EVENING, skyState(at(11, 20, 15), NORTH_LATITUDE, NORTH_LONGITUDE).mood)
        assertEquals(SkyMood.NONE, skyState(at(11, 20, 15), NORTH_LATITUDE, NORTH_LONGITUDE, evening = false).mood)
        assertEquals(SkyMood.NONE, skyState(at(11, 20, 8), NORTH_LATITUDE, NORTH_LONGITUDE, morning = false).mood)
        assertEquals(SkyMood.WHITE_NIGHT, skyState(at(6, 21, 23), NORTH_LATITUDE, NORTH_LONGITUDE, evening = false).mood)
        assertEquals(SkyMood.NONE, skyState(at(11, 20, 23), NORTH_LATITUDE, NORTH_LONGITUDE).mood)
    }

    @Test
    fun `the evening lasts after dusk and fades out by 23 00`() {
        // The drive home on a November evening (user, 14.11.2026): well after nautical dusk.
        val drive = skyState(at(11, 20, 18, 25), NORTH_LATITUDE, NORTH_LONGITUDE)
        assertEquals(SkyMood.EVENING, drive.mood)
        assertEquals(0.7f, drive.intensity, 0.001f)
        assertEquals(0.7f, skyState(at(11, 20, 22), NORTH_LATITUDE, NORTH_LONGITUDE).intensity, 0.001f)
        assertEquals(0.35f, skyState(at(11, 20, 22, 30), NORTH_LATITUDE, NORTH_LONGITUDE).intensity, 0.001f)
        assertEquals(SkyMood.NONE, skyState(at(11, 20, 23, 0), NORTH_LATITUDE, NORTH_LONGITUDE).mood)
        // Just after midnight it is night, not the evening.
        assertEquals(SkyMood.NONE, skyState(at(11, 21, 0, 30), NORTH_LATITUDE, NORTH_LONGITUDE).mood)
        // An August evening after sunset, outside the white-night season.
        assertEquals(SkyMood.EVENING, skyState(at(8, 10, 21, 30), NORTH_LATITUDE, NORTH_LONGITUDE).mood)
    }

    @Test
    fun `the morning starts by the clock before a late dawn`() {
        assertEquals(SkyMood.NONE, skyState(at(11, 20, 5, 59), NORTH_LATITUDE, NORTH_LONGITUDE).mood)
        assertEquals(0.35f, skyState(at(11, 20, 6, 30), NORTH_LATITUDE, NORTH_LONGITUDE).intensity, 0.001f)
        val seven = skyState(at(11, 20, 7), NORTH_LATITUDE, NORTH_LONGITUDE)
        assertEquals(SkyMood.MORNING, seven.mood)
        assertEquals(0.7f, seven.intensity, 0.001f)
        // Another hour from the settings, and the evening by the sun alone.
        assertEquals(SkyMood.NONE, skyState(at(11, 20, 6, 30), NORTH_LATITUDE, NORTH_LONGITUDE, morningFromHours = 7.0).mood)
        assertEquals(SkyMood.NONE, skyState(at(11, 20, 18, 25), NORTH_LATITUDE, NORTH_LONGITUDE, eveningUntilHours = null).mood)
        // A summer dawn earlier than the clock still comes by the sun.
        assertEquals(SkyMood.MORNING, skyState(at(8, 10, 4, 30), NORTH_LATITUDE, NORTH_LONGITUDE).mood)
    }

    @Test
    fun `the evening never takes the white nights' place`() {
        assertEquals(SkyMood.WHITE_NIGHT, skyState(at(6, 21, 22, 45), NORTH_LATITUDE, NORTH_LONGITUDE).mood)
        assertEquals(SkyMood.NONE, skyState(at(6, 21, 22, 45), NORTH_LATITUDE, NORTH_LONGITUDE, whiteNights = false).mood)
        // Before sunset a white-night day still has its evening.
        assertEquals(SkyMood.EVENING, skyState(at(6, 21, 21), NORTH_LATITUDE, NORTH_LONGITUDE).mood)
    }
}
