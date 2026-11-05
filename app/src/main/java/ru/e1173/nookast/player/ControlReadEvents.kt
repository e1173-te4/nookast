package ru.e1173.nookast.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The outcome of a control read that the app screen shows (SYNC.md §5.10). */
data class ControlReadEvent(
    val id: Long,
    val mediaId: String,
    /** True: playback already moved to [serverMs]; false: only an offer to move there. */
    val jumped: Boolean,
    /** Where the server was. */
    val serverMs: Long,
    /** Where the action button seeks: back to the own position after a jump, or to the server. */
    val targetMs: Long,
)

/**
 * In-process channel from [PlaybackService] to the app screen. The message is shown inside the
 * app, never in the notification shade (user decision, 25.10.2026); in the car nothing is shown.
 */
object ControlReadEvents {
    private val _latest = MutableStateFlow<ControlReadEvent?>(null)
    val latest: StateFlow<ControlReadEvent?> = _latest.asStateFlow()
    private var nextId = 0L

    internal fun publish(mediaId: String, jumped: Boolean, serverMs: Long, targetMs: Long) {
        _latest.value = ControlReadEvent(++nextId, mediaId, jumped, serverMs, targetMs)
    }

    fun consume(id: Long) {
        if (_latest.value?.id == id) _latest.value = null
    }
}
