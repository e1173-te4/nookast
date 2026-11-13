package ru.e1173.nookast.player

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Where a podcast's own sound settings live (З12): this account and this podcast, on this device
 * only — never synced, never through the outbox. Episodes without a podcast share one slot.
 */
data class SoundSlot(val account: String, val podcastId: Int?) {
    internal val key: String get() = "$account.${podcastId ?: NO_PODCAST}"

    private companion object {
        const val NO_PODCAST = "none"
    }
}

/** The settings for all podcasts (З12). */
data class GlobalSound(
    val evenLoudness: Boolean = false,
    val clarity: Int = 0,
    val depth: Int = 0,
    val shortenPauses: Boolean = false,
    val speakerTrimDb: Int = 0,
)

/** One podcast's settings (З3, З9) and its remembered even-loudness gain (З5). */
data class PodcastSound(
    val volumeDb: Int = 0,
    val mono: Boolean = false,
    val rememberedGainDb: Float? = null,
)

/** The few calls the store needs, so it is tested without Android. */
interface SoundKeyValues {
    fun int(key: String, default: Int): Int
    fun float(key: String): Float?
    fun bool(key: String, default: Boolean): Boolean
    fun edit(block: SoundKeyValueEditor.() -> Unit)
}

interface SoundKeyValueEditor {
    fun put(key: String, value: Int)
    fun put(key: String, value: Float)
    fun put(key: String, value: Boolean)
    fun remove(key: String)
}

/** The sound settings (audio rework З, DEC-060). */
class SoundSettingsStore(private val values: SoundKeyValues) {
    fun global(): GlobalSound = GlobalSound(
        evenLoudness = values.bool(KEY_EVEN, false),
        clarity = values.int(KEY_CLARITY, 0).coerceIn(0, SoundParameters.MAX_STEP),
        depth = values.int(KEY_DEPTH, 0).coerceIn(0, SoundParameters.MAX_STEP),
        shortenPauses = values.bool(KEY_SHORTEN_PAUSES, false),
        speakerTrimDb = values.int(KEY_SPEAKER_TRIM, 0).coerceIn(0, SoundParameters.MAX_SPEAKER_TRIM_DB),
    )

    fun setGlobal(global: GlobalSound) = values.edit {
        put(KEY_EVEN, global.evenLoudness)
        put(KEY_CLARITY, global.clarity.coerceIn(0, SoundParameters.MAX_STEP))
        put(KEY_DEPTH, global.depth.coerceIn(0, SoundParameters.MAX_STEP))
        put(KEY_SHORTEN_PAUSES, global.shortenPauses)
        put(KEY_SPEAKER_TRIM, global.speakerTrimDb.coerceIn(0, SoundParameters.MAX_SPEAKER_TRIM_DB))
    }

    fun podcast(slot: SoundSlot): PodcastSound = PodcastSound(
        volumeDb = values.int(PODCAST + slot.key + VOLUME, 0).coerceIn(0, SoundParameters.MAX_VOLUME_DB),
        mono = values.bool(PODCAST + slot.key + MONO, false),
        rememberedGainDb = values.float(PODCAST + slot.key + GAIN),
    )

    fun setPodcast(slot: SoundSlot, sound: PodcastSound) = values.edit {
        put(PODCAST + slot.key + VOLUME, sound.volumeDb.coerceIn(0, SoundParameters.MAX_VOLUME_DB))
        put(PODCAST + slot.key + MONO, sound.mono)
    }

    /**
     * Even loudness memory (З5): the first value is taken as it is, later ones blend 0.7 of what
     * the podcast had when the episode began ([before]) with 0.3 of the episode's average.
     */
    fun rememberGain(slot: SoundSlot, before: Float?, episodeAverageDb: Float) = values.edit {
        val blended = if (before == null) episodeAverageDb else 0.7f * before + 0.3f * episodeAverageDb
        put(PODCAST + slot.key + GAIN, blended.coerceIn(SoundProcessor.MIN_EVEN_DB.toFloat(), SoundProcessor.MAX_EVEN_DB.toFloat()))
    }

    companion object {
        const val NAME = "sound_settings"
        private const val KEY_EVEN = "even_loudness"
        private const val KEY_CLARITY = "speech_clarity"
        private const val KEY_DEPTH = "voice_depth"
        private const val KEY_SHORTEN_PAUSES = "shorten_pauses"
        private const val KEY_SPEAKER_TRIM = "speaker_trim_db"
        private const val PODCAST = "podcast."
        private const val VOLUME = ".volume_db"
        private const val MONO = ".mono"
        private const val GAIN = ".even_gain_db"

        fun open(context: Context): SoundSettingsStore =
            SoundSettingsStore(SharedPreferencesKeyValues(context.getSharedPreferences(NAME, Context.MODE_PRIVATE)))

        fun preferences(context: Context): SharedPreferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    }
}

internal class SharedPreferencesKeyValues(private val preferences: SharedPreferences) : SoundKeyValues {
    override fun int(key: String, default: Int): Int = preferences.getInt(key, default)
    override fun float(key: String): Float? = if (preferences.contains(key)) preferences.getFloat(key, 0f) else null
    override fun bool(key: String, default: Boolean): Boolean = preferences.getBoolean(key, default)
    override fun edit(block: SoundKeyValueEditor.() -> Unit) {
        val editor = preferences.edit()
        object : SoundKeyValueEditor {
            override fun put(key: String, value: Int) { editor.putInt(key, value) }
            override fun put(key: String, value: Float) { editor.putFloat(key, value) }
            override fun put(key: String, value: Boolean) { editor.putBoolean(key, value) }
            override fun remove(key: String) { editor.remove(key) }
        }.block()
        editor.apply()
    }
}

/** What the processor does for this podcast, these settings and this route (З12). */
fun soundParameters(
    global: GlobalSound,
    podcast: PodcastSound,
    speakerRoute: Boolean,
    compare: Boolean,
    episodeKey: String?,
): SoundParameters = SoundParameters(
    volumeDb = podcast.volumeDb,
    mono = podcast.mono,
    evenLoudness = global.evenLoudness,
    clarity = global.clarity,
    depth = global.depth,
    speakerRoute = speakerRoute,
    speakerTrimDb = global.speakerTrimDb,
    compare = compare,
    episodeKey = episodeKey,
    startGainDb = podcast.rememberedGainDb ?: 0f,
)

/**
 * The built-in speaker profile (З8): only when nothing else plays the sound — no headphones,
 * Bluetooth, USB, HDMI or line output — and never in the wide in-vehicle profile.
 */
fun speakerProfileActive(outputTypes: Collection<Int>, wideVehicleProfile: Boolean): Boolean =
    !wideVehicleProfile && outputTypes.contains(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) &&
        outputTypes.none { it in EXTERNAL_OUTPUTS }

private val EXTERNAL_OUTPUTS = setOf(
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_LINE_ANALOG,
    AudioDeviceInfo.TYPE_LINE_DIGITAL,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_HDMI,
    AudioDeviceInfo.TYPE_HDMI_ARC,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_ACCESSORY,
    AudioDeviceInfo.TYPE_DOCK,
    AudioDeviceInfo.TYPE_DOCK_ANALOG,
    AudioDeviceInfo.TYPE_AUX_LINE,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_HEARING_AID,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    AudioDeviceInfo.TYPE_BLE_BROADCAST,
    AudioDeviceInfo.TYPE_HDMI_EARC,
    AudioDeviceInfo.TYPE_REMOTE_SUBMIX,
    AudioDeviceInfo.TYPE_BUS,
)

/** What the popover shows about the playing sound; written by the service. */
data class SoundSessionState(
    val slot: SoundSlot? = null,
    val podcastTitle: String = "",
    val speakerRoute: Boolean = false,
    val supported: Boolean = true,
    /** The limiter has held more than 6 dB for 2 s («The limiter is holding the peaks»). */
    val limiterHolding: Boolean = false,
)

/** The bridge between the popover and the service's processor, in one process (like [VoiceLevel]). */
object SoundSession {
    private val mutable = MutableStateFlow(SoundSessionState())
    val state: StateFlow<SoundSessionState> = mutable.asStateFlow()

    @Volatile
    internal var onCompare: ((Boolean) -> Unit)? = null

    /** «Shorten pauses» is on: the voice light rests, its alignment with skipped silence is not verified (З10). */
    @Volatile
    internal var shortenPauses: Boolean = false

    /** «Compare» pressed or released in the popover. */
    fun compare(held: Boolean) {
        onCompare?.invoke(held)
    }

    internal fun update(transform: (SoundSessionState) -> SoundSessionState) = mutable.update(transform)
}
