package ru.e1173.nookast.player

/**
 * Replaying a played episode (docs/SYNC.md §5.13, Q-18, finding S-30). A replay counts only when
 * it starts from the beginning (before 60 s or 5 %) and really plays for [THRESHOLD_MS]; a
 * fragment from the middle changes nothing. Then the episode becomes unplayed through a REWIND.
 * Main-thread only, like the player.
 */
internal class ReplayDetector {
    private var mediaId: String? = null
    private var playedMs = 0L

    /** Playback of [itemMediaId] started; [completed] is the local played mark. */
    fun onStart(itemMediaId: String, completed: Boolean, positionMs: Long, durationMs: Long?) {
        if (mediaId == itemMediaId) return
        val fromBeginning = positionMs < FROM_START_MS ||
            (durationMs != null && durationMs > 0 && positionMs * 100 < durationMs * FROM_START_PERCENT)
        mediaId = itemMediaId.takeIf { completed && fromBeginning }
        playedMs = 0
    }

    /** Real playback of [itemMediaId] for [elapsedMs]; true once, when the replay counts. */
    fun onPlayed(itemMediaId: String, elapsedMs: Long): Boolean {
        if (mediaId != itemMediaId) return false
        playedMs += elapsedMs
        if (playedMs < THRESHOLD_MS) return false
        mediaId = null
        return true
    }

    /** Another item, or the user seeked away: nothing is pending any more. */
    fun reset() {
        mediaId = null
        playedMs = 0
    }

    companion object {
        const val THRESHOLD_MS = 45_000L
        private const val FROM_START_MS = 60_000L
        private const val FROM_START_PERCENT = 5
    }
}
