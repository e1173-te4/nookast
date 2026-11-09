package ru.e1173.nookast.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.e1173.nookast.player.PlaybackPreferences
import ru.e1173.nookast.player.QueueInsertionPosition
import ru.e1173.nookast.player.QueueSwipeAction

/** The player and queue settings as Settings and the queue show them. */
internal data class PlayerSettings(
    val downloadWholeEpisodeOnPlayback: Boolean = false,
    val aheadCacheMegabytes: Int = PlaybackPreferences.DEFAULT_AHEAD_CACHE_MB,
    val playbackStartQueuePosition: QueueInsertionPosition = QueueInsertionPosition.FIRST,
    val manualQueuePosition: QueueInsertionPosition = QueueInsertionPosition.LAST,
    val queueSwipeStartAction: QueueSwipeAction = QueueSwipeAction.DOWNLOAD_OR_DELETE,
    val queueSwipeEndAction: QueueSwipeAction = QueueSwipeAction.REMOVE_FROM_QUEUE,
    val autoAddStartedEpisodeToQueue: Boolean = true,
)

/**
 * The player settings (plan 5.2): they only read and write [PlaybackPreferences], so they left the
 * queue's ViewModel, which keeps the synchronized state. The queue, the service and the player
 * read the same preferences when they act.
 */
internal class PlayerSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = PlaybackPreferences(application)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<PlayerSettings> = _settings.asStateFlow()

    // Settings brought in by a transfer file (plan 8.2) show at once, not after a restart.
    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> _settings.update { read() } }
        .also(preferences::registerListener)

    override fun onCleared() {
        preferences.unregisterListener(listener)
    }

    private fun read() = PlayerSettings(
        downloadWholeEpisodeOnPlayback = preferences.downloadWholeEpisodeOnPlayback,
        aheadCacheMegabytes = preferences.aheadCacheMegabytes,
        playbackStartQueuePosition = preferences.playbackStartQueuePosition,
        manualQueuePosition = preferences.manualQueuePosition,
        queueSwipeStartAction = preferences.queueSwipeStartAction,
        queueSwipeEndAction = preferences.queueSwipeEndAction,
        autoAddStartedEpisodeToQueue = preferences.autoAddStartedEpisodeToQueue,
    )

    private fun change(write: PlaybackPreferences.() -> Unit) {
        preferences.write()
        _settings.update { read() }
    }

    fun setDownloadWholeEpisodeOnPlayback(enabled: Boolean) = change { downloadWholeEpisodeOnPlayback = enabled }

    fun setAheadCacheMegabytes(megabytes: Int) = change { aheadCacheMegabytes = megabytes }

    fun setPlaybackStartQueuePosition(position: QueueInsertionPosition) = change { playbackStartQueuePosition = position }

    fun setManualQueuePosition(position: QueueInsertionPosition) = change { manualQueuePosition = position }

    fun setQueueSwipeStartAction(action: QueueSwipeAction) = change { queueSwipeStartAction = action }

    fun setQueueSwipeEndAction(action: QueueSwipeAction) = change { queueSwipeEndAction = action }

    fun setAutoAddStartedEpisodeToQueue(enabled: Boolean) = change { autoAddStartedEpisodeToQueue = enabled }
}
