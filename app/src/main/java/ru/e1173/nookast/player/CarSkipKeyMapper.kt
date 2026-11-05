package ru.e1173.nookast.player

import android.view.KeyEvent

/** What a steering-wheel previous/next press means in the car profile (DEC-020). */
internal sealed interface CarSkipAction {
    data class Seek(val deltaMs: Long) : CarSkipAction
    data object NextEpisode : CarSkipAction
    data object PreviousEpisode : CarSkipAction
}

/**
 * Car profile mapping for MEDIA_PREVIOUS / MEDIA_NEXT: a short press seeks by the configured
 * intervals, holding for [LONG_PRESS_MS] or longer switches the episode.
 *
 * The action is taken on release, because only then is the press length known. The JAECOO head
 * unit sends a hold as one DOWN and one UP without repeats, and its UP carries `downTime` equal to
 * its own `eventTime` (journal of 24.10.2026), so the press length is measured from the DOWN this
 * mapper remembered, never from the UP's `downTime`.
 *
 * Not thread-safe: called on the session's application looper only.
 */
internal class CarSkipKeyMapper {
    private val downAt = mutableMapOf<Int, Long>()

    /**
     * Returns the action for a release, or null for presses (consumed silently) and other keys.
     * A release without a remembered press counts as a short press.
     */
    fun onKey(
        keyCode: Int,
        action: Int,
        repeatCount: Int,
        eventTimeMs: Long,
        seekBackSeconds: Int,
        seekForwardSeconds: Int,
    ): CarSkipAction? {
        if (keyCode != KeyEvent.KEYCODE_MEDIA_NEXT && keyCode != KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            return null
        }
        when (action) {
            KeyEvent.ACTION_DOWN -> {
                if (repeatCount == 0) downAt[keyCode] = eventTimeMs
                return null
            }
            KeyEvent.ACTION_UP -> Unit
            else -> return null
        }
        val pressedAt = downAt.remove(keyCode) ?: eventTimeMs
        val held = eventTimeMs - pressedAt
        val next = keyCode == KeyEvent.KEYCODE_MEDIA_NEXT
        return when {
            held >= LONG_PRESS_MS && next -> CarSkipAction.NextEpisode
            held >= LONG_PRESS_MS -> CarSkipAction.PreviousEpisode
            next -> CarSkipAction.Seek(seekForwardSeconds.coerceIn(5, 55) * 1_000L)
            else -> CarSkipAction.Seek(-seekBackSeconds.coerceIn(5, 55) * 1_000L)
        }
    }

    companion object {
        const val LONG_PRESS_MS = 600L
    }
}
