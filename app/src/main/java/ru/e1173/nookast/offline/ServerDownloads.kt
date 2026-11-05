package ru.e1173.nookast.offline

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.e1173.nookast.auth.AuthSession
import ru.e1173.nookast.data.remote.ServerDownloadStart
import ru.e1173.nookast.data.remote.ServerDownloadsClient
import ru.e1173.nookast.data.remote.ServerTaskStatus

/** Progress of a download to the PinePods server that the user asked for from the player. */
sealed interface ServerDownloadProgress {
    data class Running(val percent: Int) : ServerDownloadProgress
    data object Ready : ServerDownloadProgress
    data object Disabled : ServerDownloadProgress
    data object Failed : ServerDownloadProgress
}

/**
 * Episodes stored on the PinePods server (DEC-023): which ones exist, which ones the app
 * downloaded only because the origin was unreachable ("temporary"), and their cleanup.
 *
 * Temporary downloads are deleted from the server once the episode is played; downloads the user
 * made on purpose (web UI, another client) are never touched. The temporary set is persisted so a
 * failed delete is retried on the next sync pass; losing it only leaves a file on the server.
 */
class ServerDownloads(
    context: Context,
    private val session: () -> AuthSession?,
    /** The episode's local progress intent counter (0 when none): marks when a copy was made. */
    private val progressCounter: suspend (Int) -> Long = { 0L },
    private val client: ServerDownloadsClient = ServerDownloadsClient(),
) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<Int, Job>()

    private val _onServer = MutableStateFlow<Set<Int>>(emptySet())

    /** Last known server downloads; refreshed at playback start and on every sync pass. */
    val onServer: StateFlow<Set<Int>> = _onServer.asStateFlow()

    private val _progress = MutableStateFlow<Map<Int, ServerDownloadProgress>>(emptyMap())
    val progress: StateFlow<Map<Int, ServerDownloadProgress>> = _progress.asStateFlow()

    @Volatile
    private var rssKey: String? = null

    @Volatile
    private var lastRefreshElapsedMs = 0L

    fun isOnServer(episodeId: Int): Boolean = episodeId in _onServer.value

    /** Reads the server list; keeps the previous set when the server cannot be reached. */
    fun refresh(): Set<Int> {
        val current = session() ?: return _onServer.value
        val ids = runCatching { client.downloadedEpisodeIds(current) }.getOrNull() ?: return _onServer.value
        lastRefreshElapsedMs = android.os.SystemClock.elapsedRealtime()
        _onServer.value = ids
        return ids
    }

    /** [refresh] at most once a minute: the sync pass runs every 10 to 30 s. */
    fun refreshIfStale() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (lastRefreshElapsedMs != 0L && now - lastRefreshElapsedMs < STALE_MS) return
        refresh()
    }

    /**
     * [refresh] bounded for playback start: a slow server must not delay the episode much. A list
     * read within the last minute is used as it is (user, 29.10.2026: opening another episode
     * showed the previous one for seconds while this check ran).
     */
    suspend fun refreshForPlayback(timeoutMs: Long = PLAYBACK_CHECK_TIMEOUT_MS): Set<Int> = when {
        lastRefreshElapsedMs == 0L -> withTimeoutOrNull(timeoutMs) { scope.async { refresh() }.await() } ?: _onServer.value
        android.os.SystemClock.elapsedRealtime() - lastRefreshElapsedMs < STALE_MS -> _onServer.value
        // Known once already: play by it at once and check again in the background (user,
        // 31.10.2026: switching episodes waited for the server each time).
        else -> {
            if (backgroundRefresh?.isActive != true) backgroundRefresh = scope.launch { refresh() }
            _onServer.value
        }
    }

    private var backgroundRefresh: kotlinx.coroutines.Job? = null

    /** The key `/api/data/stream` accepts in its query; fetched once per process. */
    fun streamKey(): String? {
        rssKey?.let { return it }
        val current = session() ?: return null
        return runCatching { client.rssKey(current) }.getOrNull()?.also { rssKey = it }
    }

    /**
     * Downloads [episodeId] to the server because its origin is unreachable for this device, and
     * marks it temporary. [onReady] runs once the file is on the server.
     */
    fun requestTemporary(episodeId: Int, onReady: () -> Unit) {
        synchronized(jobs) {
            if (jobs[episodeId]?.isActive == true) return
            jobs[episodeId] = scope.launch { download(episodeId, onReady) }
        }
    }

    private suspend fun download(episodeId: Int, onReady: () -> Unit) {
        val current = session() ?: return publish(episodeId, ServerDownloadProgress.Failed)
        publish(episodeId, ServerDownloadProgress.Running(0))
        val start = runCatching { client.requestDownload(current, episodeId) }.getOrNull()
            ?: return publish(episodeId, ServerDownloadProgress.Failed)
        when (start) {
            ServerDownloadStart.Disabled -> return publish(episodeId, ServerDownloadProgress.Disabled)
            ServerDownloadStart.AlreadyDownloaded -> Unit
            is ServerDownloadStart.Queued -> {
                markTemporary(episodeId, progressCounter(episodeId))
                if (!awaitTask(current, episodeId, start.taskId)) {
                    unmarkTemporary(episodeId)
                    return publish(episodeId, ServerDownloadProgress.Failed)
                }
            }
        }
        _onServer.update { it + episodeId }
        publish(episodeId, ServerDownloadProgress.Ready)
        onReady()
    }

    private suspend fun awaitTask(current: AuthSession, episodeId: Int, taskId: String?): Boolean {
        val deadline = System.currentTimeMillis() + TASK_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_MS)
            val status = taskId?.let { runCatching { client.taskStatus(current, it) }.getOrNull() }
            when (status?.state) {
                ServerTaskStatus.State.SUCCESS -> return true
                ServerTaskStatus.State.FAILED -> return false
                ServerTaskStatus.State.PENDING, ServerTaskStatus.State.RUNNING ->
                    publish(episodeId, ServerDownloadProgress.Running(status.progress.toInt()))
                ServerTaskStatus.State.UNKNOWN, null ->
                    // Task unknown or dropped: the server list is the source of truth.
                    if (episodeId in refresh()) return true
            }
        }
        return episodeId in refresh()
    }

    /**
     * Deletes temporary server copies of episodes marked played after the copy was made:
     * [playedSince] gets the progress counter recorded with the copy. An episode that was already
     * played and is merely replayed keeps its copy (no new completion to observe). Called after
     * each sync pass, so the delete waits for connectivity like any other change.
     */
    suspend fun cleanupPlayed(playedSince: suspend (episodeId: Int, markCounter: Long) -> Boolean) {
        val temporary = temporaryMarks()
        if (temporary.isEmpty()) return
        val current = session() ?: return
        temporary.filter { (id, mark) -> playedSince(id, mark) }.keys.forEach { episodeId ->
            val deleted = runCatching { client.deleteDownload(current, episodeId) }.isSuccess
            if (deleted) {
                unmarkTemporary(episodeId)
                _onServer.update { it - episodeId }
                _progress.update { it - episodeId }
            }
        }
    }

    fun isTemporary(episodeId: Int): Boolean = episodeId in temporaryMarks()

    private fun publish(episodeId: Int, progress: ServerDownloadProgress) {
        _progress.update { it + (episodeId to progress) }
    }

    /** Temporary copies: episode id to the progress counter at the time of the copy. */
    private fun temporaryMarks(): Map<Int, Long> =
        preferences.getStringSet(KEY_TEMPORARY, emptySet()).orEmpty().mapNotNull { value ->
            val id = value.substringBefore(':').toIntOrNull() ?: return@mapNotNull null
            id to (value.substringAfter(':', "0").toLongOrNull() ?: 0L)
        }.toMap()

    private fun writeMarks(marks: Map<Int, Long>) =
        preferences.edit { putStringSet(KEY_TEMPORARY, marks.map { (id, mark) -> "$id:$mark" }.toSet()) }

    private fun markTemporary(episodeId: Int, counter: Long) = synchronized(preferences) {
        writeMarks(temporaryMarks() + (episodeId to counter))
    }

    private fun unmarkTemporary(episodeId: Int) = synchronized(preferences) {
        writeMarks(temporaryMarks() - episodeId)
    }

    /** Sign-out: temporary marks belong to the account that made them. */
    fun clear() {
        preferences.edit { clear() }
        _onServer.value = emptySet()
        _progress.value = emptyMap()
        rssKey = null
    }

    private companion object {
        const val PREFERENCES = "server_downloads"
        const val KEY_TEMPORARY = "temporary_episode_ids"
        const val PLAYBACK_CHECK_TIMEOUT_MS = 1_500L
        const val POLL_MS = 2_000L
        const val STALE_MS = 60_000L
        const val TASK_TIMEOUT_MS = 30L * 60L * 1_000L
    }
}
