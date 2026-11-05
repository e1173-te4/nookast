package ru.e1173.nookast.player

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** DEC-020, with event timings taken from the car journal of 24.10.2026. */
class CarSkipKeyMapperTest {
    private val mapper = CarSkipKeyMapper()

    private fun key(code: Int, action: Int, at: Long, repeat: Int = 0) =
        mapper.onKey(code, action, repeat, at, seekBackSeconds = 10, seekForwardSeconds = 30)

    @Test
    fun shortPressSeeksOnRelease() {
        // MEDIA_NEXT DOWN at 141541, UP at 141744 (203 ms).
        assertNull(key(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_DOWN, 141_541))
        assertEquals(CarSkipAction.Seek(30_000), key(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_UP, 141_744))

        assertNull(key(KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.ACTION_DOWN, 144_640))
        assertEquals(CarSkipAction.Seek(-10_000), key(KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.ACTION_UP, 144_915))
    }

    @Test
    fun holdSwitchesEpisodeEvenThoughTheHeadUnitResetsDownTime() {
        // A 3-second hold: one DOWN, one UP; the UP's own downTime is useless.
        assertNull(key(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_DOWN, 177_100))
        assertEquals(CarSkipAction.NextEpisode, key(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_UP, 180_097))

        assertNull(key(KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.ACTION_DOWN, 183_954))
        assertEquals(CarSkipAction.PreviousEpisode, key(KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.ACTION_UP, 188_179))
    }

    @Test
    fun repeatsDoNotRestartThePressAndOtherKeysAreIgnored() {
        key(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_DOWN, 1_000)
        key(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_DOWN, 1_500, repeat = 1)
        assertEquals(CarSkipAction.NextEpisode, key(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_UP, 1_700))

        assertNull(key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.ACTION_UP, 2_000))
        // A release without a remembered press is a short press.
        assertEquals(CarSkipAction.Seek(30_000), key(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_UP, 3_000))
    }

    @Test
    fun intervalsAreClampedToTheSettingsRange() {
        val wide = mapper.onKey(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_UP, 0, 10, seekBackSeconds = 1, seekForwardSeconds = 90)
        assertEquals(CarSkipAction.Seek(55_000), wide)
    }
}
