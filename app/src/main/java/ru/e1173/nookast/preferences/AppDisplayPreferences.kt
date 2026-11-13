package ru.e1173.nookast.preferences

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromStoredValue(value: String?): AppThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

enum class InterfaceProfile {
    AUTO,
    STANDARD,
    LARGE,
    JAECOO_J7;

    companion object {
        fun fromStoredValue(value: String?): InterfaceProfile =
            entries.firstOrNull { it.name == value } ?: AUTO
    }
}

enum class PodcastSortMode {
    UPDATED,
    TITLE;

    companion object {
        fun fromStoredValue(value: String?): PodcastSortMode =
            entries.firstOrNull { it.name == value } ?: UPDATED
    }
}

enum class PodcastViewMode {
    GRID,
    LIST;

    companion object {
        fun fromStoredValue(value: String?): PodcastViewMode =
            entries.firstOrNull { it.name == value } ?: GRID
    }
}

data class DisplayPreferencesState(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val palette: ru.e1173.nookast.designsystem.ThemePalette = ru.e1173.nookast.designsystem.ThemePalette.SIGNATURE,
    val podcastSortMode: PodcastSortMode = PodcastSortMode.UPDATED,
    val podcastViewMode: PodcastViewMode = PodcastViewMode.GRID,
    val collapsedPodcastDescriptions: Set<Int> = emptySet(),
    val hidePlayedPodcastIds: Set<Int> = emptySet(),
    /** Smart playlists that hide played episodes, as podcast pages do (user, 29.10.2026). */
    val hidePlayedPlaylistIds: Set<Int> = emptySet(),
    /** How many of the newest episodes the Feed shows (user: 23 by default). */
    val feedEpisodeCount: Int = ru.e1173.nookast.data.remote.DEFAULT_FEED_EPISODE_COUNT,
    /** Smart playlists the user hid from the Dashboard (Settings, user 27.10.2026). */
    val hiddenDashboardPlaylists: Set<Int> = emptySet(),
    /** Rows or tiles for the Feed and smart playlists (user, 27.10.2026). */
    val episodeListViewMode: PodcastViewMode = PodcastViewMode.LIST,
    /** Now Playing washed with the cover colours (user trial 27.10.2026, switchable in Settings). */
    val artworkTint: Boolean = true,
    /** «Light follows the voice» (design audit 5.8, DEC-058): off by default. */
    val voiceLight: Boolean = false,
    /** «Immersion» (user, 29.10.2026): the morning, white-night and evening skies, each on its own. */
    val skyMorning: Boolean = true,
    val skyWhiteNights: Boolean = true,
    val skyEvening: Boolean = true,
    /** Where the sky's sun is worked out (user, 2.11.2026): no place until the user lets the app read it. */
    val skyPlaceMode: SkyPlaceMode = SkyPlaceMode.OFF,
    val skyPlace: SkyPlace? = null,
    /** New Year cover cap and snow (DEC-018): by dates, always (to try it) or off. */
    val newYearMode: ru.e1173.nookast.designsystem.NewYearMode = ru.e1173.nookast.designsystem.NewYearMode.DATES,
)

/**
 * How the sky learns where it is (user, 2.11.2026): not at all (the default: no city is assumed and
 * the skies stay off), once on request, or again by itself now and then (a trip to another city).
 */
enum class SkyPlaceMode {
    OFF,
    ONCE,
    AUTO;

    companion object {
        fun fromStoredValue(value: String?): SkyPlaceMode = entries.firstOrNull { it.name == value } ?: OFF
    }
}

/** A rough place for the sun: about 10 km, kept only in the app's settings and sent nowhere. */
data class SkyPlace(val latitude: Double, val longitude: Double, val fixedAtEpochMs: Long) {
    companion object {
        /** One decimal of a degree: the sun needs no more, and the settings keep no exact address. */
        fun rough(latitude: Double, longitude: Double, fixedAtEpochMs: Long) = SkyPlace(
            latitude = Math.round(latitude * 10) / 10.0,
            longitude = Math.round(longitude * 10) / 10.0,
            fixedAtEpochMs = fixedAtEpochMs,
        )
    }
}

internal fun parsePodcastIdSet(values: Set<String>?): Set<Int> = values.orEmpty()
    .mapNotNull(String::toIntOrNull)
    .filter { it > 0 }
    .toSet()

internal fun updatePodcastIdSet(
    current: Set<Int>,
    podcastId: Int,
    included: Boolean,
): Set<Int> {
    require(podcastId > 0)
    return current.toMutableSet().apply {
        if (included) add(podcastId) else remove(podcastId)
    }
}

data class EffectiveInterfacePresentation(
    val largeControls: Boolean,
    val fontScale: Float,
    val densityScale: Float = 1f,
)

internal fun resolveInterfacePresentation(
    profile: InterfaceProfile,
    automaticLargeControls: Boolean,
    automaticFontScale: Float,
    systemFontScale: Float,
    automaticDensityScale: Float = 1f,
): EffectiveInterfacePresentation = when (profile) {
    InterfaceProfile.AUTO -> EffectiveInterfacePresentation(
        largeControls = automaticLargeControls,
        fontScale = automaticFontScale,
        densityScale = automaticDensityScale,
    )

    InterfaceProfile.STANDARD -> EffectiveInterfacePresentation(
        largeControls = false,
        fontScale = systemFontScale,
    )

    InterfaceProfile.LARGE -> EffectiveInterfacePresentation(
        largeControls = true,
        fontScale = maxOf(systemFontScale, 1.12f),
    )

    InterfaceProfile.JAECOO_J7 -> EffectiveInterfacePresentation(
        largeControls = true,
        fontScale = maxOf(systemFontScale, 1.25f),
        densityScale = 1.5f,
    )
}

private class AppDisplayPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    val state: DisplayPreferencesState
        get() = DisplayPreferencesState(
            themeMode = AppThemeMode.fromStoredValue(preferences.getString(KEY_THEME_MODE, null)),
            palette = ru.e1173.nookast.designsystem.ThemePalette.fromStoredValue(preferences.getString(KEY_PALETTE, null)),
            podcastSortMode = PodcastSortMode.fromStoredValue(
                preferences.getString(KEY_PODCAST_SORT_MODE, null),
            ),
            podcastViewMode = PodcastViewMode.fromStoredValue(
                preferences.getString(KEY_PODCAST_VIEW_MODE, null),
            ),
            collapsedPodcastDescriptions = preferences
                .getStringSet(KEY_COLLAPSED_PODCAST_DESCRIPTIONS, emptySet())
                .let(::parsePodcastIdSet),
            hidePlayedPlaylistIds = preferences
                .getStringSet(KEY_HIDE_PLAYED_PLAYLIST_IDS, emptySet())
                .let(::parsePodcastIdSet),
            hidePlayedPodcastIds = preferences
                .getStringSet(KEY_HIDE_PLAYED_PODCAST_IDS, emptySet())
                .let(::parsePodcastIdSet),
            feedEpisodeCount = preferences
                .getInt(KEY_FEED_EPISODE_COUNT, ru.e1173.nookast.data.remote.DEFAULT_FEED_EPISODE_COUNT)
                .coerceIn(ru.e1173.nookast.data.remote.MIN_FEED_EPISODE_COUNT, ru.e1173.nookast.data.remote.MAX_FEED_EPISODE_COUNT),
            hiddenDashboardPlaylists = preferences
                .getStringSet(KEY_HIDDEN_DASHBOARD_PLAYLISTS, emptySet())
                .let(::parsePodcastIdSet),
            episodeListViewMode = preferences.getString(KEY_EPISODE_LIST_VIEW_MODE, null)
                ?.let(PodcastViewMode::fromStoredValue) ?: PodcastViewMode.LIST,
            artworkTint = preferences.getBoolean(KEY_ARTWORK_TINT, true),
            voiceLight = preferences.getBoolean(KEY_VOICE_LIGHT, false),
            // The one former switch is the default of all three.
            skyMorning = preferences.getBoolean(KEY_SKY_MORNING, preferences.getBoolean(KEY_SKY_MOODS, true)),
            skyWhiteNights = preferences.getBoolean(KEY_SKY_WHITE_NIGHTS, preferences.getBoolean(KEY_SKY_MOODS, true)),
            skyEvening = preferences.getBoolean(KEY_SKY_EVENING, preferences.getBoolean(KEY_SKY_MOODS, true)),
            skyPlaceMode = SkyPlaceMode.fromStoredValue(preferences.getString(KEY_SKY_PLACE_MODE, null)),
            skyPlace = if (preferences.contains(KEY_SKY_PLACE_LATITUDE) && preferences.contains(KEY_SKY_PLACE_LONGITUDE)) {
                SkyPlace(
                    latitude = preferences.getFloat(KEY_SKY_PLACE_LATITUDE, 0f).toDouble(),
                    longitude = preferences.getFloat(KEY_SKY_PLACE_LONGITUDE, 0f).toDouble(),
                    fixedAtEpochMs = preferences.getLong(KEY_SKY_PLACE_AT, 0L),
                )
            } else {
                null
            },
            newYearMode = ru.e1173.nookast.designsystem.NewYearMode.fromStoredValue(preferences.getString(KEY_NEW_YEAR_MODE, null)),
        )

    fun setThemeMode(mode: AppThemeMode) {
        preferences.edit { putString(KEY_THEME_MODE, mode.name) }
    }

    fun setPalette(palette: ru.e1173.nookast.designsystem.ThemePalette) {
        preferences.edit { putString(KEY_PALETTE, palette.name) }
    }

    fun setDashboardPlaylistHidden(playlistId: Int, hidden: Boolean) {
        val current = state.hiddenDashboardPlaylists
        val next = if (hidden) current + playlistId else current - playlistId
        preferences.edit { putStringSet(KEY_HIDDEN_DASHBOARD_PLAYLISTS, next.map(Int::toString).toSet()) }
    }

    fun setFeedEpisodeCount(count: Int) {
        preferences.edit {
            putInt(
                KEY_FEED_EPISODE_COUNT,
                count.coerceIn(ru.e1173.nookast.data.remote.MIN_FEED_EPISODE_COUNT, ru.e1173.nookast.data.remote.MAX_FEED_EPISODE_COUNT),
            )
        }
    }

    fun setPodcastSortMode(mode: PodcastSortMode) {
        preferences.edit { putString(KEY_PODCAST_SORT_MODE, mode.name) }
    }

    fun setEpisodeListViewMode(mode: PodcastViewMode) {
        preferences.edit { putString(KEY_EPISODE_LIST_VIEW_MODE, mode.name) }
    }

    fun setArtworkTint(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_ARTWORK_TINT, enabled) }
    }

    fun setVoiceLight(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_VOICE_LIGHT, enabled) }
        VoiceLightSwitch.on = enabled
    }

    fun setSkyMood(mood: ru.e1173.nookast.designsystem.SkyMood, enabled: Boolean) {
        val key = when (mood) {
            ru.e1173.nookast.designsystem.SkyMood.MORNING -> KEY_SKY_MORNING
            ru.e1173.nookast.designsystem.SkyMood.WHITE_NIGHT -> KEY_SKY_WHITE_NIGHTS
            ru.e1173.nookast.designsystem.SkyMood.EVENING -> KEY_SKY_EVENING
            ru.e1173.nookast.designsystem.SkyMood.NONE -> return
        }
        preferences.edit { putBoolean(key, enabled) }
    }

    fun setNewYearMode(mode: ru.e1173.nookast.designsystem.NewYearMode) {
        preferences.edit { putString(KEY_NEW_YEAR_MODE, mode.name) }
    }

    /** Off forgets the place too: nothing about where the user is stays in the settings. */
    fun setSkyPlaceMode(mode: SkyPlaceMode) {
        preferences.edit {
            putString(KEY_SKY_PLACE_MODE, mode.name)
            if (mode == SkyPlaceMode.OFF) {
                remove(KEY_SKY_PLACE_LATITUDE)
                remove(KEY_SKY_PLACE_LONGITUDE)
                remove(KEY_SKY_PLACE_AT)
            }
        }
    }

    fun setSkyPlace(place: SkyPlace) {
        preferences.edit {
            putFloat(KEY_SKY_PLACE_LATITUDE, place.latitude.toFloat())
            putFloat(KEY_SKY_PLACE_LONGITUDE, place.longitude.toFloat())
            putLong(KEY_SKY_PLACE_AT, place.fixedAtEpochMs)
        }
    }

    fun setPodcastViewMode(mode: PodcastViewMode) {
        preferences.edit { putString(KEY_PODCAST_VIEW_MODE, mode.name) }
    }

    fun setPodcastDescriptionCollapsed(podcastId: Int, collapsed: Boolean) {
        require(podcastId > 0)
        val values = updatePodcastIdSet(
            current = state.collapsedPodcastDescriptions,
            podcastId = podcastId,
            included = collapsed,
        )
        preferences.edit { putStringSet(KEY_COLLAPSED_PODCAST_DESCRIPTIONS, values.map(Int::toString).toSet()) }
    }

    fun setPodcastHidePlayed(podcastId: Int, hidePlayed: Boolean) {
        val values = updatePodcastIdSet(
            current = state.hidePlayedPodcastIds,
            podcastId = podcastId,
            included = hidePlayed,
        )
        preferences.edit { putStringSet(KEY_HIDE_PLAYED_PODCAST_IDS, values.map(Int::toString).toSet()) }
    }

    fun setPlaylistHidePlayed(playlistId: Int, hidePlayed: Boolean) {
        val values = updatePodcastIdSet(
            current = state.hidePlayedPlaylistIds,
            podcastId = playlistId,
            included = hidePlayed,
        )
        preferences.edit { putStringSet(KEY_HIDE_PLAYED_PLAYLIST_IDS, values.map(Int::toString).toSet()) }
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private companion object {
        const val NAME = "display_preferences"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_PALETTE = "theme_palette"
        const val KEY_FEED_EPISODE_COUNT = "feed_episode_count"
        const val KEY_HIDDEN_DASHBOARD_PLAYLISTS = "hidden_dashboard_playlists"
        const val KEY_PODCAST_SORT_MODE = "podcast_sort_mode"
        const val KEY_PODCAST_VIEW_MODE = "podcast_view_mode"
        const val KEY_EPISODE_LIST_VIEW_MODE = "episode_list_view_mode"
        const val KEY_ARTWORK_TINT = "artwork_tint"
        const val KEY_VOICE_LIGHT = "voice_light"
        const val KEY_SKY_MOODS = "sky_moods"
        const val KEY_SKY_MORNING = "sky_morning"
        const val KEY_SKY_WHITE_NIGHTS = "sky_white_nights"
        const val KEY_SKY_EVENING = "sky_evening"
        const val KEY_SKY_PLACE_MODE = "sky_place_mode"
        const val KEY_SKY_PLACE_LATITUDE = "sky_place_latitude"
        const val KEY_SKY_PLACE_LONGITUDE = "sky_place_longitude"
        const val KEY_SKY_PLACE_AT = "sky_place_at"
        const val KEY_NEW_YEAR_MODE = "new_year_mode"
        const val KEY_COLLAPSED_PODCAST_DESCRIPTIONS = "collapsed_podcast_descriptions"
        const val KEY_HIDE_PLAYED_PODCAST_IDS = "hide_played_podcast_ids"
        const val KEY_HIDE_PLAYED_PLAYLIST_IDS = "hide_played_playlist_ids"
    }
}

class AppPreferencesViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = AppDisplayPreferences(application)
    private val _state = MutableStateFlow(preferences.state)
    val state: StateFlow<DisplayPreferencesState> = _state.asStateFlow()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _state.update { preferences.state }
    }

    init {
        preferences.registerListener(listener)
    }

    fun setThemeMode(mode: AppThemeMode) {
        preferences.setThemeMode(mode)
        _state.value = preferences.state
    }

    fun setPalette(palette: ru.e1173.nookast.designsystem.ThemePalette) {
        preferences.setPalette(palette)
        _state.value = preferences.state
    }

    fun setDashboardPlaylistHidden(playlistId: Int, hidden: Boolean) {
        preferences.setDashboardPlaylistHidden(playlistId, hidden)
        _state.value = preferences.state
    }

    fun setFeedEpisodeCount(count: Int) {
        preferences.setFeedEpisodeCount(count)
        _state.value = preferences.state
    }

    fun setPodcastSortMode(mode: PodcastSortMode) {
        preferences.setPodcastSortMode(mode)
        _state.value = preferences.state
    }

    fun setEpisodeListViewMode(mode: PodcastViewMode) {
        preferences.setEpisodeListViewMode(mode)
        _state.value = preferences.state
    }

    fun setArtworkTint(enabled: Boolean) {
        preferences.setArtworkTint(enabled)
        _state.value = preferences.state
    }

    fun setVoiceLight(enabled: Boolean) {
        preferences.setVoiceLight(enabled)
        _state.value = preferences.state
    }

    fun setSkyMood(mood: ru.e1173.nookast.designsystem.SkyMood, enabled: Boolean) {
        preferences.setSkyMood(mood, enabled)
        _state.value = preferences.state
    }

    fun setNewYearMode(mode: ru.e1173.nookast.designsystem.NewYearMode) {
        preferences.setNewYearMode(mode)
        _state.value = preferences.state
    }

    fun setSkyPlaceMode(mode: SkyPlaceMode) {
        preferences.setSkyPlaceMode(mode)
        _state.value = preferences.state
    }

    fun setSkyPlace(place: SkyPlace) {
        preferences.setSkyPlace(place)
        _state.value = preferences.state
    }

    fun setPodcastViewMode(mode: PodcastViewMode) {
        preferences.setPodcastViewMode(mode)
        _state.value = preferences.state
    }

    fun setPodcastDescriptionCollapsed(podcastId: Int, collapsed: Boolean) {
        preferences.setPodcastDescriptionCollapsed(podcastId, collapsed)
        _state.value = preferences.state
    }

    fun setPodcastHidePlayed(podcastId: Int, hidePlayed: Boolean) {
        preferences.setPodcastHidePlayed(podcastId, hidePlayed)
        _state.value = preferences.state
    }

    fun setPlaylistHidePlayed(playlistId: Int, hidePlayed: Boolean) {
        preferences.setPlaylistHidePlayed(playlistId, hidePlayed)
        _state.value = preferences.state
    }

    override fun onCleared() {
        preferences.unregisterListener(listener)
    }
}

/**
 * Whether «Light follows the voice» is on (design audit 5.8), for the player service: it decides,
 * each time the audio chain is configured, whether the level listener joins it.
 */
fun voiceLightEnabled(context: Context): Boolean = AppDisplayPreferences(context).state.voiceLight

/**
 * The switch as the playback thread reads it, many times a second (design audit 5.8): a plain flag,
 * loaded once by the player service and kept current by the setting.
 */
object VoiceLightSwitch {
    @Volatile var on: Boolean = false
}
