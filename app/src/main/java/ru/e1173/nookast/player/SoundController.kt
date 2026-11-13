package ru.e1173.nookast.player

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The service's side of the sound processing (audio rework З, DEC-060): which podcast's settings
 * apply, the output route, «Compare», the even-loudness memory, the limiter line of the popover
 * and «Shorten pauses». Runs on the player's main thread; storage is read and written there too
 * (SharedPreferences, small and cached) and the podcast of an episode is looked up on IO.
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal class SoundController(
    private val context: Context,
    private val processor: SoundProcessor,
    private val player: () -> ExoPlayer?,
    private val mainScope: CoroutineScope,
    private val account: () -> String?,
    private val podcastIdOf: suspend (MediaItem) -> Int?,
    private val wideVehicleProfile: Boolean,
) {
    private val store = SoundSettingsStore.open(context)
    private val preferences = SoundSettingsStore.preferences(context)
    private var slot: SoundSlot? = null
    private var gainBefore: Float? = null
    private var episodeKey: String? = null
    private var episodes = 0
    private var mediaId: String? = null
    private var compare = false
    private var speakerRoute = false
    private var lookup: Job? = null
    private var meterJob: Job? = null
    private var holdingFor = 0L

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> apply() }

    fun start() {
        preferences.registerOnSharedPreferenceChangeListener(listener)
        SoundSession.onCompare = { held ->
            mainScope.launch {
                compare = held
                apply()
            }
        }
        processor.onLoudnessMemory = { key, average -> mainScope.launch { remember(key, average) } }
        routeChanged()
        apply()
        player()?.currentMediaItem?.let(::itemChanged)
    }

    fun stop() {
        saveMemory()
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
        SoundSession.onCompare = null
        processor.onLoudnessMemory = null
        meterJob?.cancel()
        lookup?.cancel()
    }

    /** A new episode is playing: its podcast's values apply within a second of its start (З12). */
    fun itemChanged(item: MediaItem?, repeat: Boolean = false) {
        // The same episode again without a repeat (a rebuilt playlist): its loudness memory goes on (review of З).
        if (item != null && !repeat && item.mediaId == mediaId) return
        saveMemory()
        // What the processor still works on now is the next episode's sound: keep it out of this memory.
        processor.freezeMemory()
        lookup?.cancel()
        mediaId = item?.mediaId
        if (item == null) return
        val key = "${item.mediaId}#${++episodes}"
        lookup = mainScope.launch {
            val podcastId = withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { podcastIdOf(item) }.getOrNull() }
            val newSlot = account()?.let { SoundSlot(it, podcastId) }
            slot = newSlot
            gainBefore = newSlot?.let { store.podcast(it).rememberedGainDb }
            episodeKey = key
            SoundSession.update {
                it.copy(slot = newSlot, podcastTitle = item.mediaMetadata.artist?.toString().orEmpty())
            }
            apply()
        }
    }

    fun playingChanged(playing: Boolean) {
        meterJob?.cancel()
        if (!playing) {
            saveMemory()
            // Paused: the limiter line goes, the format support is current (review of З).
            holdingFor = 0L
            val supported = processor.supported
            SoundSession.update { it.copy(limiterHolding = false, supported = supported) }
            return
        }
        // The limiter line (З13) and the format support, four times a second while playing.
        meterJob = mainScope.launch {
            while (isActive) {
                delay(METER_INTERVAL_MS)
                holdingFor = if (processor.limitingDb > HOLDING_DB) holdingFor + METER_INTERVAL_MS else 0L
                val holding = holdingFor >= HOLDING_FOR_MS
                val supported = processor.supported
                SoundSession.update {
                    if (it.limiterHolding == holding && it.supported == supported) it
                    else it.copy(limiterHolding = holding, supported = supported)
                }
            }
        }
    }

    /** The output route changed (З8): the speaker profile follows within 100 ms. */
    fun routeChanged() {
        val manager = context.getSystemService(AudioManager::class.java) ?: return
        val types = runCatching { manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type } }.getOrDefault(emptyList())
        val onSpeaker = speakerProfileActive(types, wideVehicleProfile)
        if (onSpeaker == speakerRoute) return
        speakerRoute = onSpeaker
        SoundSession.update { it.copy(speakerRoute = onSpeaker) }
        apply()
    }

    private fun apply() {
        val global = store.global()
        val podcast = slot?.let(store::podcast) ?: PodcastSound()
        processor.parameters = soundParameters(global, podcast, speakerRoute, compare, episodeKey)
        player()?.let { if (it.skipSilenceEnabled != global.shortenPauses) it.skipSilenceEnabled = global.shortenPauses }
        SoundSession.shortenPauses = global.shortenPauses
    }

    private fun saveMemory() {
        processor.loudnessMemoryNow()?.let { (key, average) -> remember(key, average) }
    }

    private fun remember(key: String, average: Float) {
        val current = slot ?: return
        if (key != episodeKey) return
        store.rememberGain(current, gainBefore, average)
    }

    private companion object {
        const val METER_INTERVAL_MS = 250L
        const val HOLDING_DB = 6f
        const val HOLDING_FOR_MS = 2_000L
    }
}
