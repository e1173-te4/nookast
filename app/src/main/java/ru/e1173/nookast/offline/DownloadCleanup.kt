package ru.e1173.nookast.offline

import android.content.Context
import androidx.core.content.edit

/**
 * Automatic removal of episode files on THIS DEVICE (DEC-064), modelled on the official PinePods
 * app (tag 0.9.2, mobile/lib/core/download_cleanup.dart and
 * services/auto_download/download_cleanup_service.dart):
 * - «delete played»: an hour after the episode was first seen played, so a mistaken mark can be undone;
 * - «delete after N days» since the download finished (0 = never);
 * - per-podcast overrides of both;
 * - an episode downloaded while already played is a deliberate re-listen and is spared by «played»;
 * - the episode the player has open is never touched;
 * - played comes from the server when it answers, else from the local mark;
 * - an episode removed for being played is not filled again by «download on playback».
 *
 * HARD RULE (user, 13.11.2026): this only deletes files on the phone. It has no way to delete
 * anything on the server; Nookast's temporary server copies are cleaned by [ServerDownloads]
 * (DEC-023) and the server's own auto-delete settings are never read or changed.
 */
internal const val PLAYED_GRACE_MS = 60L * 60 * 1_000
internal const val CLEANUP_MIN_INTERVAL_MS = 10L * 60 * 1_000
internal val DELETE_AFTER_DAY_CHOICES = listOf(0, 1, 3, 7, 14, 30, 60, 90)
private const val DAY_MS = 24L * 60 * 60 * 1_000
private const val MAX_CLEANED = 500

internal data class CleanupRules(val deletePlayed: Boolean, val deleteAfterDays: Int) {
    val anyEnabled: Boolean get() = deletePlayed || deleteAfterDays > 0

    fun withOverride(override: CleanupOverride?): CleanupRules = CleanupRules(
        deletePlayed = override?.deletePlayed ?: deletePlayed,
        deleteAfterDays = override?.deleteAfterDays ?: deleteAfterDays,
    )
}

/** A podcast's own choice; null fields follow the defaults. */
internal data class CleanupOverride(val deletePlayed: Boolean? = null, val deleteAfterDays: Int? = null)

internal enum class CleanupReason { PLAYED, EXPIRED }

/** Why a download goes now, or null to keep it. */
internal fun cleanupReason(
    rules: CleanupRules,
    nowMs: Long,
    downloadedAtMs: Long,
    playedSinceMs: Long?,
    exemptFromPlayed: Boolean,
): CleanupReason? {
    if (rules.deletePlayed && !exemptFromPlayed && playedSinceMs != null && nowMs - playedSinceMs >= PLAYED_GRACE_MS) {
        return CleanupReason.PLAYED
    }
    if (rules.deleteAfterDays > 0 && nowMs - downloadedAtMs >= rules.deleteAfterDays * DAY_MS) return CleanupReason.EXPIRED
    return null
}

/** One finished download on the device; [podcastId] null when nothing local knows it. */
internal data class CleanupCandidate(val episodeId: Int, val podcastId: Int?, val downloadedAtMs: Long)

/** The phone's own played state: [pending] when an unsent intent (outbox) decides it. */
internal data class LocalPlayed(val played: Boolean, val pending: Boolean)

/** What the server says about one episode. */
internal data class ServerAnswer(val played: Boolean, val podcastId: Int?)

/** What the cleanup remembers between runs (SharedPreferences in the app, memory in tests). */
internal interface CleanupState {
    var lastRunMs: Long

    /** Downloads the cleanup has seen, with the "already played when first seen" mark. */
    fun known(): Map<Int, Boolean>
    fun setKnown(known: Map<Int, Boolean>)
    fun playedSince(): Map<Int, Long>
    fun setPlayedSince(values: Map<Int, Long>)

    /** Removed for being played: «download on playback» skips them. Oldest first. */
    fun cleaned(): List<Int>
    fun setCleaned(ids: List<Int>)
}

/** Asks the server about one episode; null when it does not know it. Throws when unreachable. */
internal fun interface ServerPlayed {
    suspend fun episode(episodeId: Int): ServerAnswer?
}

/**
 * One cleanup pass. Every side effect goes through [deleteLocal], which removes a file and its
 * download record on the device, and [rememberPodcastId], which only notes a podcast id in that
 * record; there is no other effect.
 */
internal class DownloadCleanupRunner(
    private val downloads: suspend () -> List<CleanupCandidate>,
    private val deleteLocal: (Int) -> Boolean,
    private val defaults: () -> CleanupRules,
    private val overrides: () -> Map<Int, CleanupOverride>,
    private val state: CleanupState,
    private val serverPlayed: ServerPlayed?,
    private val localPlayed: suspend (Int) -> LocalPlayed,
    /**
     * The episodes the player has open, will resume (a cold start has not restored it yet) or
     * plays next. Read again right before every deletion.
     */
    private val openEpisodeIds: suspend () -> Set<Int>,
    private val rememberPodcastId: (episodeId: Int, podcastId: Int) -> Unit = { _, _ -> },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val running = kotlinx.coroutines.sync.Mutex()

    /** Returns the episode ids whose files were removed. A pass already running makes this a no-op. */
    suspend fun run(force: Boolean = false): List<Int> {
        if (!running.tryLock()) return emptyList()
        try {
            return pass(force)
        } finally {
            running.unlock()
        }
    }

    private suspend fun pass(force: Boolean): List<Int> {
        val now = clock()
        val previousRun = state.lastRunMs
        if (!force && previousRun > 0 && now - previousRun in 0 until CLEANUP_MIN_INTERVAL_MS) return emptyList()
        val firstRun = previousRun == 0L
        // Taken at the start, so a pass that dies half-way does not run again at once.
        state.lastRunMs = now
        val defaults = defaults()
        val overrides = overrides()
        val anyPlayedRule = defaults.deletePlayed || overrides.values.any { it.deletePlayed == true }
        val anyRule = anyPlayedRule || defaults.deleteAfterDays > 0 || overrides.values.any { (it.deleteAfterDays ?: 0) > 0 }

        val present = downloads()
        val known = state.known().filterKeys { id -> present.any { it.episodeId == id } }.toMutableMap()
        val playedSince = state.playedSince().filterKeys { id -> present.any { it.episodeId == id } }.toMutableMap()
        val cleaned = state.cleaned().toMutableList()
        val removed = mutableListOf<Int>()
        var serverFailures = 0

        for (download in present) {
            val id = download.episodeId
            if (!anyRule && id in known) continue
            val local = localPlayed(id)
            var podcastId = download.podcastId
            // An unsent intent is newer than anything the server knows: «unplayed» marked here and
            // still in the outbox must not lose to the server's «played» (review 13.11.2026).
            // A clean row already played needs no question either; nor do RSS-only ids (≤ 0).
            val needPlayed = anyPlayedRule && !local.pending && !local.played
            val needPodcast = podcastId == null && overrides.isNotEmpty()
            val ask = anyRule && serverPlayed != null && id > 0 && serverFailures < SERVER_FAILURE_LIMIT &&
                (needPlayed || needPodcast)
            val answer = if (ask) {
                try {
                    serverPlayed?.episode(id).also { serverFailures = 0 }
                } catch (_: Exception) {
                    serverFailures++ // one failed request does not stop the others; several in a row do
                    null
                }
            } else {
                null
            }
            if (podcastId == null && answer?.podcastId != null) {
                podcastId = answer.podcastId
                rememberPodcastId(id, podcastId)
            }
            val played = if (needPlayed) answer?.played ?: local.played else local.played
            // First sight: a download that is already played was made to hear it again. Downloads
            // that predate this feature are not exempt (their clock starts now, as in the app).
            if (id !in known) known[id] = played && !firstRun
            if (played) {
                playedSince.putIfAbsent(id, now)
            } else {
                playedSince.remove(id)
                known[id] = false
                cleaned.remove(id)
            }
            if (!anyRule) continue
            // Unknown podcast while some podcast has its own rules: it might be one of them, so keep it.
            if (podcastId == null && overrides.isNotEmpty()) continue
            val rules = defaults.withOverride(podcastId?.let(overrides::get))
            val reason = cleanupReason(
                rules = rules,
                nowMs = now,
                // A download without a finish time (made by a very old version) never expires.
                downloadedAtMs = download.downloadedAtMs.takeIf { it > 0 } ?: now,
                playedSinceMs = playedSince[id],
                exemptFromPlayed = known[id] == true,
            ) ?: continue
            if (id in openEpisodeIds()) continue
            if (!deleteLocal(id)) continue
            removed += id
            known.remove(id)
            playedSince.remove(id)
            if (reason == CleanupReason.PLAYED && id !in cleaned) cleaned += id
        }

        state.setKnown(known)
        state.setPlayedSince(playedSince)
        state.setCleaned(cleaned.takeLast(MAX_CLEANED))
        return removed
    }

    private companion object {
        const val SERVER_FAILURE_LIMIT = 3
    }
}

/** [CleanupState] in its own preferences file (not exported by the transfer file: it is per device). */
internal class CleanupPreferences(context: Context) : CleanupState {
    private val preferences = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    override var lastRunMs: Long
        get() = preferences.getLong(KEY_LAST_RUN, 0L)
        set(value) = preferences.edit { putLong(KEY_LAST_RUN, value) }

    override fun known(): Map<Int, Boolean> = pairs(KEY_KNOWN).mapValues { it.value == 1L }

    override fun setKnown(known: Map<Int, Boolean>) = write(KEY_KNOWN, known.mapValues { if (it.value) 1L else 0L })

    override fun playedSince(): Map<Int, Long> = pairs(KEY_PLAYED_SINCE)

    override fun setPlayedSince(values: Map<Int, Long>) = write(KEY_PLAYED_SINCE, values)

    override fun cleaned(): List<Int> =
        preferences.getString(KEY_CLEANED, null).orEmpty().split(',').mapNotNull(String::toIntOrNull)

    override fun setCleaned(ids: List<Int>) = preferences.edit { putString(KEY_CLEANED, ids.joinToString(",")) }

    fun isCleaned(episodeId: Int): Boolean = episodeId in cleaned()

    /** The server merged [old] into [new] (DEC-062): what the cleanup remembers follows the download. */
    @Synchronized
    fun remapEpisodeId(old: Int, new: Int) {
        if (old == new) return
        known().let { values -> values[old]?.let { setKnown(values - old + (new to it)) } }
        playedSince().let { values -> values[old]?.let { setPlayedSince(values - old + (new to (values[new] ?: it))) } }
        cleaned().let { ids -> if (old in ids) setCleaned((ids - old - new) + new) }
    }

    /** The user downloads it by hand: «download on playback» may fill it again later. */
    fun forgetCleaned(episodeId: Int) {
        val ids = cleaned()
        if (episodeId in ids) setCleaned(ids - episodeId)
    }

    private fun pairs(key: String): Map<Int, Long> =
        preferences.getString(key, null).orEmpty().split(',').mapNotNull { item ->
            val id = item.substringBefore(':').toIntOrNull() ?: return@mapNotNull null
            val value = item.substringAfter(':', "").toLongOrNull() ?: return@mapNotNull null
            id to value
        }.toMap()

    private fun write(key: String, values: Map<Int, Long>) =
        preferences.edit { putString(key, values.entries.joinToString(",") { "${it.key}:${it.value}" }) }

    private companion object {
        const val NAME = "download_cleanup"
        const val KEY_LAST_RUN = "last_run_ms"
        const val KEY_KNOWN = "known"
        const val KEY_PLAYED_SINCE = "played_since"
        const val KEY_CLEANED = "cleaned"
    }
}
