package ru.e1173.nookast.diagnostics

import android.app.Application
import android.os.Bundle
import android.view.KeyEvent
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ControlJournalFormatTest {
    @Test
    fun mediaKeyIsDescribedInFull() {
        val event = KeyEvent(1_000L, 1_010L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT, 2)

        val text = ControlJournalFormat.keyEvent(event)

        assertTrue(text, text.startsWith("action=DOWN key=MEDIA_NEXT(87) scan=0 repeat=2"))
        assertTrue(text.contains("eventTime=1010 downTime=1000"))
    }

    @Test
    fun printingKeysFromTheActivityAreRedacted() {
        val letter = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)

        val text = ControlJournalFormat.keyEvent(letter, redactText = true)

        assertTrue(text.contains("<printing key redacted>"))
        assertFalse(text.contains("KEYCODE_A") || text.contains("key=A("))
        // Non-printing keys keep their code even when redaction is requested.
        val next = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT)
        assertTrue(ControlJournalFormat.keyEvent(next, redactText = true).contains("MEDIA_NEXT(87)"))
    }

    @Test
    fun uriLikeMediaIdsAreRedacted() {
        assertEquals("pinepods:42", ControlJournalFormat.mediaId("pinepods:42"))
        assertEquals("<uri redacted>", ControlJournalFormat.mediaId("https://host/a.mp3?api_key=x"))
        assertEquals("<uri redacted>", ControlJournalFormat.mediaId("content://media/1"))
        assertEquals("null", ControlJournalFormat.mediaId(null))
        val many = (1..12).map { it.toString() }
        assertEquals("[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, +2]", ControlJournalFormat.mediaIds(many))
    }

    @Test
    fun bundlesExposeKeysOnly() {
        val bundle = Bundle().apply {
            putString("android.service.media.extra.RECENT", "secret value")
            putBoolean("a", true)
        }

        assertEquals(
            "[a, android.service.media.extra.RECENT]",
            ControlJournalFormat.bundleKeys(bundle),
        )
        assertEquals("[]", ControlJournalFormat.bundleKeys(null))
    }

    @Test
    fun playerCodesHaveNames() {
        assertEquals("SEEK_TO_NEXT", ControlJournalFormat.playerCommand(Player.COMMAND_SEEK_TO_NEXT))
        assertEquals("COMMAND_999", ControlJournalFormat.playerCommand(999))
        assertEquals(
            "AUDIO_BECOMING_NOISY",
            ControlJournalFormat.playWhenReadyReason(
                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY,
            ),
        )
        assertEquals(
            "TRANSIENT_AUDIO_FOCUS_LOSS",
            ControlJournalFormat.suppressionReason(
                Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS,
            ),
        )
    }
}
