package ru.e1173.nookast.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «Light follows the voice» (design audit 5.8, DEC-058). The player is P0: the listener is always
 * in the chain as Media3's pass-through tee, so the switch works at once in both directions; these
 * pin down that the sound is never changed, that off costs nothing, that toggling touches neither the
 * chain nor playback, that every format still plays, and that the light follows what is audible.
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class VoiceLevelTest {
    @Test
    fun `the sound passes unchanged, on and off, in every common format`() {
        for (on in listOf(true, false)) {
            for (rate in listOf(22_050, 44_100, 48_000)) {
                for (channels in listOf(1, 2)) {
                    val tap = VoiceLevelTap(enabled = { on })
                    val tee = TeeAudioProcessor(tap)
                    tee.configure(AudioProcessor.AudioFormat(rate, channels, C.ENCODING_PCM_16BIT))
                    tee.flush()
                    // 37 ms: not a whole number of 20 ms windows.
                    for (millis in listOf(37, 20, 113, 3)) {
                        val input = pcm(tone(rate, channels, 0.4, millis))
                        val expected = ByteArray(input.remaining()).also { input.duplicate().get(it) }
                        tee.queueInput(input)
                        val output = tee.output
                        val actual = ByteArray(output.remaining()).also { output.get(it) }
                        assertArrayEquals("on=$on rate=$rate channels=$channels $millis ms", expected, actual)
                    }
                }
            }
        }
    }

    @Test
    fun `switched off nothing is measured`() {
        val tap = VoiceLevelTap(enabled = { false })
        playStream(tap, seconds = 3)
        assertEquals(0, tap.windowsMeasured)
    }

    @Test
    fun `toggling during playback neither reconfigures nor flushes the chain`() {
        var on = false
        val tap = VoiceLevelTap(enabled = { on })
        val tee = TeeAudioProcessor(tap)
        tee.configure(FORMAT)
        tee.flush()
        val flushesBefore = tap.flushes
        repeat(10) { step ->
            on = step % 2 == 0
            tee.queueInput(pcm(tone(44_100, 2, 0.4, 50)))
            tee.output
        }
        assertEquals(flushesBefore, tap.flushes)
        assertTrue(tee.isActive)
        assertTrue("measured while on", tap.windowsMeasured > 0)
    }

    @Test
    fun `formats it cannot measure still play and leave the light at rest`() {
        for (encoding in listOf(C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_8BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT)) {
            val tap = VoiceLevelTap(enabled = { true })
            val tee = TeeAudioProcessor(tap)
            val format = AudioProcessor.AudioFormat(44_100, 2, encoding)
            assertEquals(format, tee.configure(format))
            tee.flush()
            val input = ByteBuffer.allocateDirect(4_096).order(ByteOrder.nativeOrder())
            val expected = ByteArray(input.remaining()).also { input.duplicate().get(it) }
            tee.queueInput(input)
            val output = tee.output
            assertArrayEquals(expected, ByteArray(output.remaining()).also { output.get(it) })
            assertEquals(0, tap.windowsMeasured)
            assertNull(tap.levelAt(500_000L))
        }
    }

    @Test
    fun `the light follows the audible position, not the sound just written`() {
        val tap = VoiceLevelTap(enabled = { true })
        val tee = TeeAudioProcessor(tap)
        tee.configure(FORMAT)
        tee.flush()
        // One second of speech, then one second of silence, written ahead of playback.
        var timeUs = 0L
        repeat(10) {
            tap.bufferStartsAt(timeUs)
            tee.queueInput(pcm(tone(44_100, 2, 0.5, 100)))
            tee.output
            timeUs += 100_000L
        }
        repeat(10) {
            tap.bufferStartsAt(timeUs)
            tee.queueInput(pcm(tone(44_100, 2, 0.0, 100)))
            tee.output
            timeUs += 100_000L
        }
        // Everything is written; the listener hears 0.6 s while the written end is silent.
        val audibleSpeech = tap.levelAt(600_000L)!!
        val audibleSilence = tap.levelAt(1_900_000L)!!
        assertTrue("speech heard at 0.6 s: $audibleSpeech", audibleSpeech > 0.8f)
        assertTrue("silence heard at 1.9 s: $audibleSilence", audibleSilence < 0.2f)
    }

    @Test
    fun `a flush forgets what was written before it`() {
        val tap = VoiceLevelTap(enabled = { true })
        playStream(tap, seconds = 1)
        assertTrue(tap.levelAt(500_000L) != null)
        tap.flush(44_100, 2, C.ENCODING_PCM_16BIT)
        assertNull(tap.levelAt(500_000L))
    }

    @Test
    fun `the envelope rises fast and falls slowly`() {
        val envelope = VoiceEnvelope()
        repeat(3) { envelope.window(rms = 1f) }
        val afterAttack = envelope.level
        repeat(3) { envelope.window(rms = 0f) }
        val afterShortRelease = envelope.level
        assertTrue("attack reached $afterAttack", afterAttack > 0.6f)
        assertTrue("release fell to $afterShortRelease", afterShortRelease > 0.4f)
    }

    @Test
    fun `the level is normalised by the loudest moment of the last ten seconds`() {
        val envelope = VoiceEnvelope()
        repeat(100) { envelope.window(rms = 0.1f) }
        assertTrue("steady quiet speech reads as full: ${envelope.level}", envelope.level > 0.9f)
        repeat(50) { envelope.window(rms = 0.4f) }
        repeat(100) { envelope.window(rms = 0.1f) }
        assertTrue("quiet after loud reads lower: ${envelope.level}", envelope.level < 0.5f)
        repeat(VoiceEnvelope.PEAK_WINDOWS + 50) { envelope.window(rms = 0.1f) }
        assertTrue("the loud moment is forgotten after 10 s: ${envelope.level}", envelope.level > 0.9f)
    }

    @Test
    fun `silence stays dark instead of amplifying noise`() {
        val envelope = VoiceEnvelope()
        repeat(200) { envelope.window(rms = 0f) }
        assertEquals(0f, envelope.level, 0.001f)
    }

    private fun playStream(tap: VoiceLevelTap, seconds: Int) {
        val tee = TeeAudioProcessor(tap)
        tee.configure(FORMAT)
        tee.flush()
        var timeUs = 0L
        repeat(seconds * 10) {
            tap.bufferStartsAt(timeUs)
            tee.queueInput(pcm(tone(44_100, 2, 0.5, 100)))
            tee.output
            timeUs += 100_000L
        }
    }

    private fun tone(rate: Int, channels: Int, amplitude: Double, millis: Int): ShortArray {
        val frames = rate * millis / 1_000
        return ShortArray(frames * channels) { i ->
            val frame = i / channels
            (sin(2 * PI * 220.0 * frame / rate) * amplitude * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun pcm(samples: ShortArray): ByteBuffer =
        ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder()).apply {
            asShortBuffer().put(samples)
        }

    private companion object {
        val FORMAT = AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT)
    }
}
