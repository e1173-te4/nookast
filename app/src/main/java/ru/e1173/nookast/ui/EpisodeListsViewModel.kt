package ru.e1173.nookast.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.e1173.nookast.app.appGraph
import ru.e1173.nookast.data.local.QueueListEntry
import ru.e1173.nookast.data.remote.FeedClient
import ru.e1173.nookast.data.remote.PlaylistClient
import ru.e1173.nookast.data.remote.PlaylistEpisodes
import ru.e1173.nookast.data.remote.QueueLoadError
import ru.e1173.nookast.data.remote.parseFeedEpisodes

/** The Feed and the smart playlists: their own narrow state, apart from the queue (plan 5.2). */
data class EpisodeListsUiState(
    /** Feed tab: newest episodes of all podcasts (server window), cached for offline start. */
    val feedEntries: List<QueueListEntry> = emptyList(),
    val feedRefreshing: Boolean = false,
    val feedError: QueueLoadError? = null,
    /** Smart playlists opened from the Dashboard, by playlist id. */
    val playlistEpisodes: Map<Int, PlaylistEpisodes> = emptyMap(),
    val playlistLoading: Set<Int> = emptySet(),
    val playlistErrors: Map<Int, QueueLoadError> = emptyMap(),
)

/** The Feed's last answer, kept so the tab opens offline; cleared with the account's data. */
internal fun feedCacheFile(context: Context) = java.io.File(context.noBackupFilesDir, "feed-cache.json")

/**
 * The Feed and smart playlists (plan 5.2: QueueViewModel split by feature). Read-only server lists:
 * nothing here writes to the server, so the sync rules of the queue do not apply.
 */
class EpisodeListsViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionStore = application.appGraph.session
    private val feedClient = FeedClient()
    private val playlistClient = PlaylistClient()
    private var feedJob: Job? = null

    private val _state = MutableStateFlow(EpisodeListsUiState())
    val state: StateFlow<EpisodeListsUiState> = _state.asStateFlow()

    init {
        loadCachedFeed()
    }

    /** Reloads the feed; the last answer is kept in a file so the tab opens offline. */
    fun refreshFeed() {
        if (feedJob?.isActive == true) return
        val session = sessionStore.load() ?: return
        feedJob = viewModelScope.launch {
            _state.update { it.copy(feedRefreshing = true, feedError = null) }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val json = feedClient.fetchJson(session)
                    val entries = parseFeedEpisodes(json)
                    runCatching { feedCacheFile(getApplication()).writeText(json) }
                    entries
                }
            }
            _state.update { current ->
                current.copy(
                    feedEntries = result.getOrNull() ?: current.feedEntries,
                    feedRefreshing = false,
                    feedError = result.exceptionOrNull().toQueueLoadError(),
                )
            }
        }
    }

    /** Loads a smart playlist's episodes and rules for its list screen. */
    fun loadPlaylist(playlistId: Int) {
        val session = sessionStore.load() ?: return
        if (playlistId in _state.value.playlistLoading) return
        viewModelScope.launch {
            _state.update { it.copy(playlistLoading = it.playlistLoading + playlistId) }
            val result = runCatching {
                withContext(Dispatchers.IO) { playlistClient.fetch(session, playlistId) }
            }
            _state.update { current ->
                current.copy(
                    playlistEpisodes = result.getOrNull()?.let { current.playlistEpisodes + (playlistId to it) }
                        ?: current.playlistEpisodes,
                    playlistLoading = current.playlistLoading - playlistId,
                    playlistErrors = result.exceptionOrNull().toQueueLoadError()
                        ?.let { current.playlistErrors + (playlistId to it) }
                        ?: (current.playlistErrors - playlistId),
                )
            }
        }
    }

    /** Signed out with the data cleared: nothing of the old account stays on screen. */
    fun clear() {
        feedJob?.cancel()
        _state.value = EpisodeListsUiState()
    }

    private fun loadCachedFeed() {
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) {
                runCatching { feedCacheFile(getApplication()).takeIf { it.isFile }?.readText() }.getOrNull()
                    ?.let { parseFeedEpisodes(it) }
            }
            if (!cached.isNullOrEmpty()) {
                _state.update { if (it.feedEntries.isEmpty()) it.copy(feedEntries = cached) else it }
            }
        }
    }
}
