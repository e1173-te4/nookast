package ru.e1173.nookast.player

import android.content.Context
import androidx.core.content.edit
import android.content.SharedPreferences

class PlaybackPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    init {
        if (RETIRED_AUDIO_KEYS.any(preferences::contains)) preferences.edit { RETIRED_AUDIO_KEYS.forEach(::remove) }
    }

    var downloadWholeEpisodeOnPlayback: Boolean
        get() = preferences.getBoolean(KEY_DOWNLOAD_ON_PLAYBACK, false)
        set(value) = preferences.edit { putBoolean(KEY_DOWNLOAD_ON_PLAYBACK, value) }

    var aheadCacheMegabytes: Int
        get() = preferences.getInt(KEY_AHEAD_CACHE_MB, DEFAULT_AHEAD_CACHE_MB)
            .coerceIn(MIN_AHEAD_CACHE_MB, MAX_AHEAD_CACHE_MB)
        set(value) = preferences.edit { putInt(KEY_AHEAD_CACHE_MB, value.coerceIn(MIN_AHEAD_CACHE_MB, MAX_AHEAD_CACHE_MB)) }

    var seekBackSeconds: Int
        get() = preferences.getInt(KEY_SEEK_BACK_SECONDS, DEFAULT_SEEK_BACK_SECONDS)
            .coerceIn(MIN_SEEK_SECONDS, MAX_SEEK_SECONDS)
        set(value) = preferences.edit { putInt(KEY_SEEK_BACK_SECONDS, value.coerceIn(MIN_SEEK_SECONDS, MAX_SEEK_SECONDS)) }

    var seekForwardSeconds: Int
        get() = preferences.getInt(KEY_SEEK_FORWARD_SECONDS, DEFAULT_SEEK_FORWARD_SECONDS)
            .coerceIn(MIN_SEEK_SECONDS, MAX_SEEK_SECONDS)
        set(value) = preferences.edit { putInt(KEY_SEEK_FORWARD_SECONDS, value.coerceIn(MIN_SEEK_SECONDS, MAX_SEEK_SECONDS)) }

    var playbackStartQueuePosition: QueueInsertionPosition
        get() = QueueInsertionPosition.fromStoredValue(
            preferences.getString(KEY_PLAYBACK_START_QUEUE_POSITION, null),
            default = QueueInsertionPosition.FIRST,
        )
        set(value) = preferences.edit { putString(KEY_PLAYBACK_START_QUEUE_POSITION, value.name) }

    var manualQueuePosition: QueueInsertionPosition
        get() = QueueInsertionPosition.fromStoredValue(
            preferences.getString(KEY_MANUAL_QUEUE_POSITION, null),
            default = QueueInsertionPosition.AFTER_CURRENT,
        )
        set(value) = preferences.edit { putString(KEY_MANUAL_QUEUE_POSITION, value.name) }

    var queueSwipeStartAction: QueueSwipeAction
        get() = QueueSwipeAction.fromStoredValue(
            preferences.getString(KEY_QUEUE_SWIPE_START_ACTION, null),
            default = QueueSwipeAction.DOWNLOAD_OR_DELETE,
        )
        set(value) = preferences.edit { putString(KEY_QUEUE_SWIPE_START_ACTION, value.name) }

    var queueSwipeEndAction: QueueSwipeAction
        get() = QueueSwipeAction.fromStoredValue(
            preferences.getString(KEY_QUEUE_SWIPE_END_ACTION, null),
            default = QueueSwipeAction.REMOVE_FROM_QUEUE,
        )
        set(value) = preferences.edit { putString(KEY_QUEUE_SWIPE_END_ACTION, value.name) }

    var autoMarkPlayedNearEndEnabled: Boolean
        get() = preferences.getBoolean(KEY_AUTO_MARK_PLAYED_NEAR_END_ENABLED, false)
        set(value) = preferences.edit { putBoolean(KEY_AUTO_MARK_PLAYED_NEAR_END_ENABLED, value) }

    var autoMarkPlayedRemainingMinutes: Int
        get() = preferences.getInt(
            KEY_AUTO_MARK_PLAYED_REMAINING_MINUTES,
            DEFAULT_AUTO_MARK_PLAYED_REMAINING_MINUTES,
        ).coerceIn(
            MIN_AUTO_MARK_PLAYED_REMAINING_MINUTES,
            MAX_AUTO_MARK_PLAYED_REMAINING_MINUTES,
        )
        set(value) = preferences.edit {
            putInt(
                KEY_AUTO_MARK_PLAYED_REMAINING_MINUTES,
                value.coerceIn(
                    MIN_AUTO_MARK_PLAYED_REMAINING_MINUTES,
                    MAX_AUTO_MARK_PLAYED_REMAINING_MINUTES,
                ),
            )
        }

    var autoAddStartedEpisodeToQueue: Boolean
        get() = preferences.getBoolean(KEY_AUTO_ADD_STARTED_EPISODE_TO_QUEUE, true)
        set(value) = preferences.edit { putBoolean(KEY_AUTO_ADD_STARTED_EPISODE_TO_QUEUE, value) }

    var aheadCacheClearRequested: Boolean
        get() = preferences.getBoolean(KEY_AHEAD_CACHE_CLEAR_REQUESTED, false)
        set(value) = preferences.edit { putBoolean(KEY_AHEAD_CACHE_CLEAR_REQUESTED, value) }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        const val DEFAULT_AHEAD_CACHE_MB = 32
        const val MIN_AHEAD_CACHE_MB = 8
        const val MAX_AHEAD_CACHE_MB = 128
        const val DEFAULT_SEEK_BACK_SECONDS = 10
        const val DEFAULT_SEEK_FORWARD_SECONDS = 30
        const val MIN_SEEK_SECONDS = 5
        const val MAX_SEEK_SECONDS = 55
        const val DEFAULT_AUTO_MARK_PLAYED_REMAINING_MINUTES = 3
        const val MIN_AUTO_MARK_PLAYED_REMAINING_MINUTES = 1
        const val MAX_AUTO_MARK_PLAYED_REMAINING_MINUTES = 13

        fun isSeekPreference(key: String?): Boolean =
            key == KEY_SEEK_BACK_SECONDS || key == KEY_SEEK_FORWARD_SECONDS

        private const val NAME = "playback_preferences"
        private const val KEY_DOWNLOAD_ON_PLAYBACK = "download_whole_episode_on_playback"
        private const val KEY_AHEAD_CACHE_MB = "ahead_cache_megabytes"
        private const val KEY_SEEK_BACK_SECONDS = "seek_back_seconds"
        private const val KEY_SEEK_FORWARD_SECONDS = "seek_forward_seconds"
        private const val KEY_PLAYBACK_START_QUEUE_POSITION =
            "playback_start_queue_position"
        private const val KEY_MANUAL_QUEUE_POSITION = "manual_queue_position"
        private const val KEY_QUEUE_SWIPE_START_ACTION = "queue_swipe_start_action"
        private const val KEY_QUEUE_SWIPE_END_ACTION = "queue_swipe_end_action"
        private const val KEY_AUTO_MARK_PLAYED_NEAR_END_ENABLED =
            "auto_mark_played_near_end_enabled"
        private const val KEY_AUTO_MARK_PLAYED_REMAINING_MINUTES =
            "auto_mark_played_remaining_minutes"
        private const val KEY_AUTO_ADD_STARTED_EPISODE_TO_QUEUE =
            "auto_add_started_episode_to_queue"

        // The platform-effect settings before the rework (З12): removed, not migrated (new scales).
        private val RETIRED_AUDIO_KEYS = listOf("audio_overall_gain_db", "audio_bass_db", "audio_speech_clarity")
        private const val KEY_AHEAD_CACHE_CLEAR_REQUESTED = "ahead_cache_clear_requested"
    }
}

enum class QueueSwipeAction {
    NONE,
    DOWNLOAD_OR_DELETE,
    MARK_PLAYED,
    REMOVE_FROM_QUEUE;

    companion object {
        fun fromStoredValue(value: String?, default: QueueSwipeAction): QueueSwipeAction =
            entries.firstOrNull { it.name == value } ?: default
    }
}

enum class QueueInsertionPosition {
    /** Right after the episode playing now; the top when it is not in the queue (user, 11.11.2026). */
    AFTER_CURRENT,
    FIRST,
    LAST;

    companion object {
        fun fromStoredValue(
            value: String?,
            default: QueueInsertionPosition,
        ): QueueInsertionPosition = entries.firstOrNull { it.name == value } ?: default
    }
}
