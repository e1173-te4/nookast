package ru.e1173.nookast.player

/** How one node answered its last check (DEC-069). */
enum class ProbeStatus { UNKNOWN, RUNNING, OK, FAIL }

/**
 * One node of the connection: the internet (Google or Yandex), the user's server or the episode's
 * host. [history] holds the last five checks in milliseconds, null for a failure; it lives in memory
 * only and no address is stored.
 */
data class NodeProbe(
    val status: ProbeStatus = ProbeStatus.UNKNOWN,
    val latencyMs: Long? = null,
    val history: List<Long?> = emptyList(),
) {
    fun running() = copy(status = ProbeStatus.RUNNING)

    fun answered(ok: Boolean, latencyMs: Long): NodeProbe = NodeProbe(
        status = if (ok) ProbeStatus.OK else ProbeStatus.FAIL,
        latencyMs = latencyMs.takeIf { ok },
        history = (history + latencyMs.takeIf { ok }).takeLast(HISTORY_SIZE),
    )

    companion object {
        const val HISTORY_SIZE = 5
    }
}

/** The three nodes and when they were last checked (elapsed realtime, 0 never). */
data class ConnectionProbes(
    val internet: NodeProbe = NodeProbe(),
    val server: NodeProbe = NodeProbe(),
    val origin: NodeProbe = NodeProbe(),
    val checkedAtElapsedMs: Long = 0L,
    val running: Boolean = false,
) {
    val done: Int get() = listOf(internet, server, origin).count { it.status != ProbeStatus.RUNNING }
}

/** The result of a hold on the pill: shown in the pill for 2 s. */
data class QuickCheck(
    val running: Boolean,
    val internet: ProbeStatus = ProbeStatus.UNKNOWN,
    val server: NodeProbe = NodeProbe(),
    val origin: NodeProbe = NodeProbe(),
)

/** The buffer as the pill shows it: only at the line (DEC-069). */
data class RunwayStatus(val shown: Boolean = false, val seconds: Int = 0, val critical: Boolean = false)

/**
 * When the pill shows how much sound is buffered (DEC-069): at 45 s or less, once the buffer has not
 * grown for 5 s while sound plays; never in the first 15 s after a start or a seek, nor while the
 * buffer grows; it goes away above 60 s. At 15 s or less it is critical. The line comes from Media3's
 * minimum buffer of 50 s.
 */
class RunwayWatch(
    private val showAtMs: Long = SHOW_AT_MS,
    private val hideAboveMs: Long = HIDE_ABOVE_MS,
    private val criticalAtMs: Long = CRITICAL_AT_MS,
    private val stallMs: Long = STALL_MS,
    private val settleMs: Long = SETTLE_MS,
) {
    private var lastAhead = -1L
    private var grewAtMs = 0L
    private var settleFromMs = 0L
    private var shown = false

    /** A start, a seek or a new episode: the next [settleMs] the pill keeps quiet. */
    fun restart(nowMs: Long) {
        settleFromMs = nowMs
        grewAtMs = nowMs
        lastAhead = -1L
        shown = false
    }

    fun update(nowMs: Long, aheadMs: Long, playing: Boolean, local: Boolean): RunwayStatus {
        if (lastAhead < 0 || aheadMs > lastAhead + GROWTH_STEP_MS) grewAtMs = nowMs
        lastAhead = aheadMs
        if (local || !playing) {
            shown = false
            return RunwayStatus()
        }
        if (shown && aheadMs > hideAboveMs) shown = false
        val quietOver = nowMs - settleFromMs >= settleMs
        val stalled = nowMs - grewAtMs >= stallMs
        if (!shown && quietOver && stalled && aheadMs <= showAtMs) shown = true
        if (!shown) return RunwayStatus()
        val seconds = ((aheadMs + 999) / 1000).toInt()
        return RunwayStatus(shown = true, seconds = seconds, critical = aheadMs <= criticalAtMs)
    }

    companion object {
        const val SHOW_AT_MS = 45_000L
        const val HIDE_ABOVE_MS = 60_000L
        const val CRITICAL_AT_MS = 15_000L
        const val STALL_MS = 5_000L
        const val SETTLE_MS = 15_000L

        /** A rise smaller than the ticker's jitter is not growth. */
        private const val GROWTH_STEP_MS = 300L
    }
}
