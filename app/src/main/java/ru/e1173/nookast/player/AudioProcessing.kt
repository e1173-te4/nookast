package ru.e1173.nookast.player

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import kotlin.math.roundToInt

data class AudioProcessingSettings(
    val overallGainDb: Int = 0,
    val bassDb: Int = 0,
    val speechClarity: Int = 0,
) {
    fun normalized(): AudioProcessingSettings = copy(
        overallGainDb = overallGainDb.coerceIn(MIN_OVERALL_GAIN_DB, MAX_OVERALL_GAIN_DB),
        bassDb = bassDb.coerceIn(MIN_BASS_DB, MAX_BASS_DB),
        speechClarity = speechClarity.coerceIn(MIN_SPEECH_CLARITY, MAX_SPEECH_CLARITY),
    )

    companion object {
        const val MIN_OVERALL_GAIN_DB = 0
        const val MAX_OVERALL_GAIN_DB = 12
        const val MIN_BASS_DB = 0
        const val MAX_BASS_DB = 12
        const val MIN_SPEECH_CLARITY = 0
        const val MAX_SPEECH_CLARITY = 3
    }
}

data class AudioProcessingSupport(
    val overallGain: Boolean = false,
    val bass: Boolean = false,
    val speechClarity: Boolean = false,
) {
    companion object {
        fun detect(): AudioProcessingSupport {
            val types = runCatching {
                AudioEffect.queryEffects().orEmpty().map(AudioEffect.Descriptor::type).toSet()
            }.getOrDefault(emptySet())
            return AudioProcessingSupport(
                overallGain = AudioEffect.EFFECT_TYPE_LOUDNESS_ENHANCER in types,
                bass = AudioEffect.EFFECT_TYPE_BASS_BOOST in types,
                speechClarity = AudioEffect.EFFECT_TYPE_EQUALIZER in types,
            )
        }
    }
}

internal fun bassStrengthForDb(valueDb: Int): Short = (
    valueDb.coerceIn(AudioProcessingSettings.MIN_BASS_DB, AudioProcessingSettings.MAX_BASS_DB)
        .toFloat() / AudioProcessingSettings.MAX_BASS_DB * 1_000f
    ).roundToInt().coerceIn(0, 1_000).toShort()

internal fun clarityBandLevelFor(
    value: Int,
    minimumMillibels: Int,
    maximumMillibels: Int,
): Short = (value.coerceIn(
    AudioProcessingSettings.MIN_SPEECH_CLARITY,
    AudioProcessingSettings.MAX_SPEECH_CLARITY,
) * CLARITY_STEP_MILLIBELS).coerceIn(minimumMillibels, maximumMillibels).toShort()

/** Equalizer level for the bass bands: the slider's dB, limited to what the device allows. */
internal fun bassBandLevelFor(valueDb: Int, minimumMillibels: Int, maximumMillibels: Int): Short =
    (valueDb.coerceIn(AudioProcessingSettings.MIN_BASS_DB, AudioProcessingSettings.MAX_BASS_DB) * 100)
        .coerceIn(minimumMillibels, maximumMillibels).toShort()

// One clarity step is 3 dB on the speech bands (it was 1 dB: barely audible, user, APK-3.1).
private const val CLARITY_STEP_MILLIBELS = 300

/**
 * Owns optional platform audio effects for one ExoPlayer audio session. Every effect is isolated:
 * unsupported vendor implementations are skipped without disabling the other controls.
 */
class AudioEffectsController {
    private var audioSessionId: Int = 0
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var bassBoost: BassBoost? = null
    private var equalizer: Equalizer? = null

    fun attach(audioSessionId: Int, settings: AudioProcessingSettings) {
        if (audioSessionId <= 0) {
            release()
            return
        }
        if (this.audioSessionId == audioSessionId) {
            apply(settings)
            return
        }
        release()
        this.audioSessionId = audioSessionId
        loudnessEnhancer = runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull()
        bassBoost = runCatching { BassBoost(0, audioSessionId) }.getOrNull()
        equalizer = runCatching { Equalizer(0, audioSessionId) }.getOrNull()
        apply(settings)
    }

    fun apply(rawSettings: AudioProcessingSettings) {
        val settings = rawSettings.normalized()
        loudnessEnhancer?.let { effect ->
            runCatching {
                effect.setTargetGain(settings.overallGainDb * 100)
                effect.enabled = settings.overallGainDb > 0
            }
        }
        bassBoost?.let { effect ->
            runCatching {
                if (effect.strengthSupported) effect.setStrength(bassStrengthForDb(settings.bassDb))
                effect.enabled = settings.bassDb > 0 && effect.strengthSupported
            }
        }
        equalizer?.let { effect ->
            runCatching {
                val range = effect.bandLevelRange
                val minimum = range.getOrElse(0) { 0 }.toInt()
                val maximum = range.getOrElse(1) { 0 }.toInt()
                val speechLevel = clarityBandLevelFor(settings.speechClarity, minimum, maximum)
                // Bass also goes through the equalizer: many vendor BassBoost implementations (Huawei
                // among them) accept a strength and change nothing.
                val bassLevel = bassBandLevelFor(settings.bassDb, minimum, maximum)
                for (band in 0 until effect.numberOfBands.toInt()) {
                    val bandIndex = band.toShort()
                    val centerFrequencyMillihertz = effect.getCenterFreq(bandIndex)
                    val isSpeechBand = centerFrequencyMillihertz in
                        SPEECH_BAND_MIN_MILLIHERTZ..SPEECH_BAND_MAX_MILLIHERTZ
                    val isBassBand = centerFrequencyMillihertz <= BASS_BAND_MAX_MILLIHERTZ
                    effect.setBandLevel(
                        bandIndex,
                        when {
                            isBassBand -> bassLevel
                            isSpeechBand -> speechLevel
                            else -> 0
                        },
                    )
                }
                effect.enabled = settings.speechClarity > 0 || settings.bassDb > 0
            }
        }
    }

    fun release() {
        runCatching { loudnessEnhancer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { equalizer?.release() }
        loudnessEnhancer = null
        bassBoost = null
        equalizer = null
        audioSessionId = 0
    }

    private companion object {
        /** Bands centred at or below 250 Hz carry the bass. */
        const val BASS_BAND_MAX_MILLIHERTZ = 250_000
        const val SPEECH_BAND_MIN_MILLIHERTZ = 1_000_000
        const val SPEECH_BAND_MAX_MILLIHERTZ = 4_000_000
    }
}
