package ru.e1173.nookast.player

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * «Light follows the voice» (design audit 5.8, DEC-058): the loudness of speech that is audible
 * now, 0..1, for Play's glow in the open player. Written from the playback thread, read by the UI.
 */
object VoiceLevel {
    /** Null: nothing measured at the audible position (off, another format, a gap): the light rests. */
    private val mutable = MutableStateFlow<Float?>(null)
    val level: StateFlow<Float?> = mutable.asStateFlow()

    internal fun publish(value: Float?) {
        mutable.value = value
    }
}

/**
 * The envelope of speech over 20 ms windows: it rises with a 60 ms attack and falls with a 300 ms
 * release, and is normalised by its loudest value of the last 10 s, so quiet and loud speakers both
 * move the light (and the volume, equaliser or loudness do not). Silence stays at 0.
 */
class VoiceEnvelope {
    private var envelope = 0f
    private val history = FloatArray(PEAK_WINDOWS)
    private var next = 0

    var level = 0f
        private set

    /** One 20 ms window of RMS loudness (0..1). */
    fun window(rms: Float) {
        val coefficient = if (rms > envelope) ATTACK else RELEASE
        envelope += coefficient * (rms - envelope)
        history[next] = envelope
        next = (next + 1) % PEAK_WINDOWS
        val peak = history.max()
        level = if (peak < SILENCE) 0f else (envelope / peak).coerceIn(0f, 1f)
    }

    fun reset() {
        envelope = 0f
        history.fill(0f)
        next = 0
        level = 0f
    }

    companion object {
        const val WINDOW_MS = 20
        const val PEAK_WINDOWS = 10_000 / WINDOW_MS
        private val ATTACK = (1.0 - exp(-WINDOW_MS / 60.0)).toFloat()
        private val RELEASE = (1.0 - exp(-WINDOW_MS / 300.0)).toFloat()
        private const val SILENCE = 0.003f
    }
}

/**
 * The listener behind Media3's pass-through [TeeAudioProcessor]: the tee is always in the chain and
 * hands the sound on unchanged, so the switch works at once in both directions without touching
 * the chain. Switched off it only checks [enabled] and returns. It measures 16-bit PCM; any other
 * encoding passes through unmeasured and the light stays at rest.
 *
 * Each 20 ms window is stored with the media time it will be heard at ([bufferStartsAt], from the
 * sink), so [levelAt] gives the loudness of what is audible, not of what was just written ahead.
 * The platform effects (equaliser, bass, loudness) act after this chain; the normalised envelope
 * does not depend on them.
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class VoiceLevelTap(private val enabled: () -> Boolean) : TeeAudioProcessor.AudioBufferSink {
    private val envelope = VoiceEnvelope()
    private var sampleRate = 0
    private var channels = 1
    private var measurable = false

    @Volatile private var bufferStartUs = 0L
    private var framesSinceBufferStart = 0L
    private var windowFrames = 0
    private var frames = 0
    private var sumOfSquares = 0.0

    private val times = LongArray(HISTORY)
    private val levels = FloatArray(HISTORY)
    private var stored = 0
    private var newest = -1

    internal var windowsMeasured = 0
        private set
    internal var flushes = 0
        private set

    /** The media time of the buffer the sink is about to process (the playback thread). */
    fun bufferStartsAt(presentationTimeUs: Long) {
        bufferStartUs = presentationTimeUs
        framesSinceBufferStart = 0L
    }

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        flushes++
        sampleRate = sampleRateHz
        channels = channelCount.coerceAtLeast(1)
        measurable = encoding == C.ENCODING_PCM_16BIT && sampleRateHz > 0
        windowFrames = if (measurable) (sampleRateHz * VoiceEnvelope.WINDOW_MS / 1_000).coerceAtLeast(1) else 0
        frames = 0
        sumOfSquares = 0.0
        framesSinceBufferStart = 0L
        synchronized(this) {
            stored = 0
            newest = -1
        }
        envelope.reset()
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (!enabled() || !measurable) return
        // A fault in the light must never stop the sound (independent review of audit 5).
        try {
            measure(buffer)
        } catch (_: RuntimeException) {
            measurable = false
        }
    }

    private fun measure(buffer: ByteBuffer) {
        val samples = buffer.duplicate().order(ByteOrder.nativeOrder()).asShortBuffer()
        var channel = 0
        var frameSum = 0.0
        while (samples.hasRemaining()) {
            frameSum += samples.get() / SHORT_SCALE
            channel++
            if (channel == channels) {
                val mono = frameSum / channels
                sumOfSquares += mono * mono
                frames++
                framesSinceBufferStart++
                channel = 0
                frameSum = 0.0
                if (frames == windowFrames) closeWindow()
            }
        }
    }

    private fun closeWindow() {
        envelope.window(sqrt(sumOfSquares / frames).toFloat())
        windowsMeasured++
        val heardAtUs = bufferStartUs + framesSinceBufferStart * MICROS_PER_SECOND / sampleRate
        synchronized(this) {
            newest = (newest + 1) % HISTORY
            times[newest] = heardAtUs
            levels[newest] = envelope.level
            if (stored < HISTORY) stored++
        }
        frames = 0
        sumOfSquares = 0.0
    }

    /** The level of the window being heard at [positionUs], or null when nothing near was measured. */
    fun levelAt(positionUs: Long): Float? = synchronized(this) {
        var best: Float? = null
        var bestTime = Long.MIN_VALUE
        for (i in 0 until stored) {
            val t = times[i]
            if (t in (bestTime + 1)..positionUs) {
                bestTime = t
                best = levels[i]
            }
        }
        best?.takeIf { positionUs - bestTime <= STALE_US }
    }

    private companion object {
        const val SHORT_SCALE = 32_768.0
        const val MICROS_PER_SECOND = 1_000_000L

        /** 10 s of 20 ms windows: more than any output buffer holds ahead. */
        const val HISTORY = 500
        const val STALE_US = 200_000L
    }
}

/**
 * The default audio sink, telling the tap the media time of each buffer and publishing the level
 * at the position the sink reports as audible — it includes the AudioTrack and Bluetooth latency,
 * and follows speed and skipped silence — at most 15 times a second.
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class VoiceAlignedAudioSink(sink: AudioSink, private val tap: VoiceLevelTap, private val enabled: () -> Boolean) :
    ForwardingAudioSink(sink) {
    private var lastPublishMs = 0L
    private var published = false

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        tap.bufferStartsAt(presentationTimeUs)
        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val position = super.getCurrentPositionUs(sourceEnded)
        if (!enabled()) {
            if (published) {
                published = false
                VoiceLevel.publish(null)
            }
            return position
        }
        val now = SystemClock.uptimeMillis()
        if (position != AudioSink.CURRENT_POSITION_NOT_SET && now - lastPublishMs >= PUBLISH_INTERVAL_MS) {
            lastPublishMs = now
            published = true
            VoiceLevel.publish(tap.levelAt(position))
        }
        return position
    }

    private companion object {
        const val PUBLISH_INTERVAL_MS = 66L
    }
}
