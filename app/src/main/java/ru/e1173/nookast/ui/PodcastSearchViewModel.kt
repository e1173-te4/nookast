package ru.e1173.nookast.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.e1173.nookast.app.appGraph
import ru.e1173.nookast.data.remote.FeedPreview
import ru.e1173.nookast.data.remote.FeedPreviewClient
import ru.e1173.nookast.data.remote.FoundPodcast
import ru.e1173.nookast.data.remote.PodcastSearchClient
import ru.e1173.nookast.data.remote.SearchSource

internal data class PodcastSearchUiState(
    val query: String = "",
    val results: List<FoundPodcast> = emptyList(),
    val source: SearchSource? = null,
    val searching: Boolean = false,
    val failed: Boolean = false,
    /** Feeds being added and feeds added in this search, by URL. */
    val adding: Set<String> = emptySet(),
    val added: Set<String> = emptySet(),
    val addFailed: Set<String> = emptySet(),
    /** The found podcast whose card is open (user, 2.11.2026), and what its feed told. */
    val opened: FoundPodcast? = null,
    val preview: FeedPreview? = null,
    val previewFailed: Boolean = false,
)

/**
 * Search and add podcasts (user, 1.11.2026). A query is sent a moment after typing stops; a link to
 * a feed is offered as it is. Adding is an online server action: the podcast appears in Podcasts
 * once the server has read its feed ([onAdded] refreshes the catalogue).
 */
internal class PodcastSearchViewModel(application: Application) : AndroidViewModel(application) {
    private val session = application.appGraph.session
    private val client = PodcastSearchClient()
    private val feeds = FeedPreviewClient()
    private var previewJob: Job? = null
    private val _state = MutableStateFlow(PodcastSearchUiState())
    val state: StateFlow<PodcastSearchUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    fun setQuery(query: String) {
        _state.update { it.copy(query = query, failed = false) }
        searchJob?.cancel()
        val term = query.trim()
        if (term.length < MIN_QUERY || feedLink(term) != null) {
            _state.update { it.copy(results = emptyList(), searching = false, source = null) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(TYPING_PAUSE_MS)
            _state.update { it.copy(searching = true) }
            val result = withContext(Dispatchers.IO) {
                val current = session.load() ?: return@withContext null
                runCatching { client.search(current, term) }.getOrNull()
            }
            _state.update {
                if (result == null) {
                    it.copy(searching = false, failed = true, results = emptyList(), source = null)
                } else {
                    it.copy(searching = false, results = result.podcasts, source = result.source)
                }
            }
        }
    }

    fun add(podcast: FoundPodcast, onAdded: () -> Unit) {
        val feed = podcast.feedUrl
        if (feed in _state.value.adding || feed in _state.value.added) return
        _state.update { it.copy(adding = it.adding + feed, addFailed = it.addFailed - feed) }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val current = session.load() ?: return@withContext false
                runCatching { client.add(current, podcast) }.isSuccess
            }
            _state.update {
                it.copy(
                    adding = it.adding - feed,
                    added = if (ok) it.added + feed else it.added,
                    addFailed = if (ok) it.addFailed else it.addFailed + feed,
                )
            }
            if (ok) onAdded()
        }
    }

    /** Opens the card of [podcast] and reads its feed for the description and the latest episodes. */
    fun open(podcast: FoundPodcast) {
        previewJob?.cancel()
        _state.update { it.copy(opened = podcast, preview = null, previewFailed = false) }
        previewJob = viewModelScope.launch {
            val preview = withContext(Dispatchers.IO) { runCatching { feeds.load(podcast.feedUrl) }.getOrNull() }
            _state.update { if (it.opened != podcast) it else it.copy(preview = preview, previewFailed = preview == null) }
        }
    }

    fun close() {
        previewJob?.cancel()
        _state.update { it.copy(opened = null) }
    }

    companion object {
        private const val MIN_QUERY = 2
        private const val TYPING_PAUSE_MS = 450L

        /** The query itself when it is a link to a feed (http or https), else null. */
        fun feedLink(query: String): String? {
            val text = query.trim()
            val lower = text.lowercase()
            if (!(lower.startsWith("https://") || lower.startsWith("http://")) || text.any(Char::isWhitespace)) return null
            return text
        }
    }
}
