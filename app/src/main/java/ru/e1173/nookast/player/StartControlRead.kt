package ru.e1173.nookast.player

import kotlin.math.abs

/** What to do with the server position read right after an episode started (SYNC.md §5.10). */
internal sealed interface StartReadAction {
    data object None : StartReadAction

    /** Within the start window: continue from the server position, offering to go back. */
    data class Jump(val toMs: Long, val fromMs: Long) : StartReadAction

    /** After the window: leave playback alone and offer the server position. */
    data class Offer(val toMs: Long) : StartReadAction
}

/**
 * Decides on a control read (§5.10). [startPositionMs] is where playback started; the read
 * answered [elapsedSinceStartMs] later with [serverPositionMs]. A local intent that was still
 * undelivered when playback started is newer than the server ([localPendingAtStart]), so nothing
 * happens then. A difference within [TOLERANCE_MS] is noise.
 */
internal fun decideStartRead(
    startPositionMs: Long,
    serverPositionMs: Long,
    serverCompleted: Boolean,
    localPendingAtStart: Boolean,
    elapsedSinceStartMs: Long,
    durationMs: Long?,
): StartReadAction {
    if (localPendingAtStart || serverCompleted) return StartReadAction.None
    if (serverPositionMs <= 0) return StartReadAction.None
    if (durationMs != null && durationMs > 0 && serverPositionMs >= durationMs) return StartReadAction.None
    if (abs(serverPositionMs - startPositionMs) <= TOLERANCE_MS) return StartReadAction.None
    return if (elapsedSinceStartMs <= START_WINDOW_MS) {
        StartReadAction.Jump(toMs = serverPositionMs, fromMs = startPositionMs + elapsedSinceStartMs)
    } else {
        StartReadAction.Offer(serverPositionMs)
    }
}

internal const val START_WINDOW_MS = 10_000L
internal const val TOLERANCE_MS = 15_000L

/** Reads that take longer than this are abandoned (§5.10). */
internal const val CONTROL_READ_TIMEOUT_MS = 60_000L
