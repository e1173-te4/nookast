package ru.e1173.nookast.core.sync

/** Kind of the last unconfirmed progress intent of an episode (docs/SYNC.md §5.1). */
enum class ProgressIntentKind { NONE, PASSIVE, REWIND, COMPLETE, UNCOMPLETE }

/** What the user or the player asked for. Positions are milliseconds. */
sealed interface ProgressIntent {
    /** Tick, pause or stop of an engaged session: progress can only move forward (I4). */
    data class Passive(val positionMs: Long, val durationMs: Long?, val session: String) : ProgressIntent

    /** An explicit move backwards: long rewind, "from the start", replay of a played episode. */
    data class Rewind(val positionMs: Long, val durationMs: Long?, val session: String) : ProgressIntent

    data class Complete(val durationMs: Long?, val session: String?) : ProgressIntent

    data object Uncomplete : ProgressIntent
}

/** Local state of one episode's progress, the in-memory form of `playback_progress` (§3.2). */
data class ProgressState(
    val episodeId: Int,
    val positionMs: Long,
    val durationMs: Long?,
    val completed: Boolean,
    val dirty: Boolean = false,
    val kind: ProgressIntentKind = ProgressIntentKind.NONE,
    val intentCounter: Long = 0,
    val intentSession: String? = null,
    val rewindBaseCounter: Long? = null,
    val rewindPhase: Int = 0,
    val maxSentPositionMs: Long = 0,
    val baseServerPositionSeconds: Long? = null,
    val baseTicket: Long? = null,
    val confirmedCounter: Long = 0,
    val serverPositionSeconds: Long? = null,
    val serverCompleted: Boolean? = null,
    val serverTicket: Long = 0,
    val migrated: Boolean = false,
) {
    companion object {
        fun empty(episodeId: Int) = ProgressState(episodeId, positionMs = 0, durationMs = null, completed = false)
    }
}

/**
 * Merges a new intent into an episode's state (SYNC.md §5.3). Returns null for a no-op: nothing
 * is written and delivery is not woken (finding S-09).
 *
 * [counter] is the value the caller took from `sync_counter` for this intent. The current server
 * knowledge ([ProgressState.serverPositionSeconds] and ticket) becomes the intent's base, used by
 * the conditional delivery of rewinds and the freshness check of passive intents (§5.5, §5.6).
 */
object ProgressIntentReducer {
    fun apply(state: ProgressState, intent: ProgressIntent, counter: Long): ProgressState? {
        require(counter > 0)
        val pending = state.dirty && state.kind != ProgressIntentKind.NONE
        return when (intent) {
            is ProgressIntent.Passive -> passive(state, pending, intent, counter)
            is ProgressIntent.Rewind -> rewind(state, pending, intent.positionMs, intent.durationMs, intent.session, counter, ProgressIntentKind.REWIND)
            ProgressIntent.Uncomplete -> rewind(state, pending, 0, state.durationMs, state.intentSession, counter, ProgressIntentKind.UNCOMPLETE)
            is ProgressIntent.Complete -> complete(state, pending, intent, counter)
        }
    }

    private fun passive(
        state: ProgressState,
        pending: Boolean,
        intent: ProgressIntent.Passive,
        counter: Long,
    ): ProgressState? {
        // A played episode ignores passive progress; replay is an explicit Rewind (§5.13).
        if (state.completed) return null
        if (pending && (state.kind == ProgressIntentKind.REWIND || state.kind == ProgressIntentKind.UNCOMPLETE)) {
            // Ticks after a rewind continue it: the phase and its base are kept (§5.3, F1).
            return state.copy(
                positionMs = maxOf(state.positionMs, intent.positionMs),
                durationMs = intent.durationMs ?: state.durationMs,
                kind = ProgressIntentKind.REWIND,
                intentCounter = counter,
                intentSession = intent.session,
            )
        }
        // Not forward: nothing to tell the server (also the 0 → 0 pause of an unplayed item).
        if (intent.positionMs <= state.positionMs) return null
        return state.copy(
            positionMs = intent.positionMs,
            durationMs = intent.durationMs ?: state.durationMs,
            dirty = true,
            kind = ProgressIntentKind.PASSIVE,
            intentCounter = counter,
            intentSession = intent.session,
            baseServerPositionSeconds = if (pending) state.baseServerPositionSeconds else state.serverPositionSeconds,
            baseTicket = if (pending) state.baseTicket else state.serverTicket,
        )
    }

    private fun rewind(
        state: ProgressState,
        pending: Boolean,
        positionMs: Long,
        durationMs: Long?,
        session: String?,
        counter: Long,
        kind: ProgressIntentKind,
    ): ProgressState {
        val continuesRewind = pending &&
            (state.kind == ProgressIntentKind.REWIND || state.kind == ProgressIntentKind.UNCOMPLETE)
        // A further rewind keeps the started two-phase delivery unless it goes below what was
        // already sent; then phase 0 must run again (§5.3).
        val restart = !continuesRewind || positionMs < state.maxSentPositionMs
        return state.copy(
            positionMs = positionMs,
            durationMs = durationMs ?: state.durationMs,
            completed = false,
            dirty = true,
            kind = kind,
            intentCounter = counter,
            intentSession = session,
            rewindBaseCounter = if (restart) counter else state.rewindBaseCounter,
            rewindPhase = if (restart) 0 else state.rewindPhase,
            baseServerPositionSeconds = if (continuesRewind) state.baseServerPositionSeconds else state.serverPositionSeconds,
            baseTicket = if (continuesRewind) state.baseTicket else state.serverTicket,
        )
    }

    private fun complete(
        state: ProgressState,
        pending: Boolean,
        intent: ProgressIntent.Complete,
        counter: Long,
    ): ProgressState? {
        if (pending && state.kind == ProgressIntentKind.COMPLETE) return null
        if (!pending && state.completed) return null
        val durationMs = intent.durationMs ?: state.durationMs
        return state.copy(
            positionMs = durationMs ?: state.positionMs,
            durationMs = durationMs,
            completed = true,
            dirty = true,
            kind = ProgressIntentKind.COMPLETE,
            intentCounter = counter,
            intentSession = intent.session ?: state.intentSession,
            rewindBaseCounter = null,
            rewindPhase = 0,
            baseServerPositionSeconds = state.serverPositionSeconds,
            baseTicket = state.serverTicket,
        )
    }
}
