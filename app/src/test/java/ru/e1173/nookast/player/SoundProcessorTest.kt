package ru.e1173.nookast.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sound processor (audio rework З, DEC-060). The player is P0: these are the scenario tests of
 * З14, written against the specification with their own reference maths (analytic filter
 * responses, the published BS.1770 coefficients), not against the processor's code.
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class SoundProcessorTest {

    // a) and j)

    @Test
    fun `everything off the output is the input bit for bit, every frame comes out`() {
        for (rate in listOf(22_050, 44_100, 48_000)) {
            for (channels in listOf(1, 2)) {
                val input = noise(rate, channels, seconds = 0.7, amplitude = 0.9, seed = rate + channels)
                for (splits in listOf(intArrayOf(4_096), intArrayOf(1, 37, 999, 3, 512), intArrayOf(7))) {
                    val processor = configured(rate, channels)
                    val output = run(processor, input, channels, splits)
                    assertArrayEquals("rate=$rate channels=$channels splits=${splits.toList()}", input, output)
                }
            }
        }
    }

    @Test
    fun `a stream shorter than the lookahead still comes out whole`() {
        val input = noise(48_000, 2, seconds = 0.001, amplitude = 0.5, seed = 3)
        val processor = configured(48_000, 2)
        processor.parameters = SoundParameters(volumeDb = 6)
        assertEquals(input.size, run(processor, input, 2, intArrayOf(4_096)).size)
        val off = configured(48_000, 2)
        assertArrayEquals(input, run(off, input, 2, intArrayOf(4_096)))
    }

    @Test
    fun `with everything on frames out equal frames in`() {
        val input = noise(44_100, 2, seconds = 1.3, amplitude = 0.5, seed = 5)
        val processor = configured(44_100, 2)
        processor.parameters = everythingOn()
        assertEquals(input.size, run(processor, input, 2, intArrayOf(1, 333, 4_096)).size)
    }

    @Test
    fun `other formats pass through and are reported unsupported`() {
        for (encoding in listOf(C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_8BIT)) {
            val processor = SoundProcessor()
            processor.configure(AudioProcessor.AudioFormat(48_000, 2, encoding))
            processor.flush()
            assertTrue(!processor.isActive)
            assertTrue(!processor.supported)
        }
        val six = SoundProcessor()
        six.configure(AudioProcessor.AudioFormat(48_000, 6, C.ENCODING_PCM_16BIT))
        assertTrue(!six.supported)
    }

    // b)

    @Test
    fun `changing any parameter during playback neither reconfigures nor flushes`() {
        val processor = configured(48_000, 2)
        val configures = processor.configures
        val flushes = processor.flushes
        val chunk = sine(48_000, 2, 440.0, -20.0, seconds = 0.05)
        val variants = listOf(
            SoundParameters(),
            SoundParameters(volumeDb = 12),
            SoundParameters(volumeDb = 12, mono = true),
            SoundParameters(clarity = 3, depth = 2, evenLoudness = true),
            everythingOn().copy(compare = true),
            everythingOn(),
            everythingOn().copy(episodeKey = "next", startGainDb = 4f),
            SoundParameters(),
        )
        var framesIn = 0
        var framesOut = 0
        repeat(40) { i ->
            processor.parameters = variants[i % variants.size]
            processor.queueInput(pcm(chunk))
            framesIn += chunk.size / 2
            framesOut += processor.output.remaining() / 4
        }
        assertEquals(configures, processor.configures)
        assertEquals(flushes, processor.flushes)
        assertTrue(processor.isActive)
        val delay = 240
        assertEquals(framesIn - delay, framesOut)
    }

    @Test
    fun `switching on and off mid-tone never leaves a gap`() {
        val rate = 48_000
        val input = sine(rate, 2, 300.0, -12.0, seconds = 2.0)
        val processor = configured(rate, 2)
        val output = ShortArray(input.size)
        var written = 0
        var position = 0
        var step = 0
        while (position < input.size) {
            processor.parameters = if (step % 2 == 0) everythingOn() else SoundParameters()
            if (step == 7) processor.parameters = everythingOn().copy(episodeKey = "other")
            if (step == 9) processor.parameters = everythingOn().copy(compare = true)
            val end = minOf(input.size, position + 2 * 2_400)
            processor.queueInput(pcm(input.copyOfRange(position, end)))
            written = drain(processor, output, written)
            position = end
            step++
        }
        processor.queueEndOfStream()
        drain(processor, output, written)
        // A 300 Hz tone crosses zero every 1.7 ms: no run of near-silence longer than 3 ms inside.
        var run = 0
        var longest = 0
        for (i in 2 * 480 until output.size - 2 * 480 step 2) {
            run = if (abs(output[i].toInt()) < 40) run + 1 else 0
            longest = maxOf(longest, run)
        }
        assertTrue("longest near-silent run $longest frames", longest < rate * 3 / 1_000)
    }

    @Test
    fun `mono on a mono source changes nothing, bit for bit`() {
        val input = noise(44_100, 1, seconds = 0.5, amplitude = 0.99, seed = 4)
        val processor = configured(44_100, 1)
        processor.parameters = SoundParameters(mono = true)
        assertArrayEquals(input, run(processor, input, 1, intArrayOf(512)))
    }

    @Test
    fun `holding Compare gives the original bit for bit once the crossfade is over`() {
        val rate = 48_000
        val input = sine(rate, 2, 440.0, -6.0, seconds = 1.0)
        val processor = configured(rate, 2)
        processor.parameters = everythingOn().copy(compare = true)
        val output = run(processor, input, 2, intArrayOf(4_096))
        // After the 30 ms crossfade and the 5 ms line, the original comes out unchanged.
        val from = 2 * rate / 10
        assertArrayEquals(input.copyOfRange(from, input.size), output.copyOfRange(from, output.size))
    }

    @Test
    fun `a seek keeps the even-loudness gain`() {
        val rate = 48_000
        val speech = speechLike(rate, seconds = 20.0, seed = 8)
        scaleToLufs(speech, rate, -30.0)
        val processor = configured(rate, 1)
        processor.parameters = SoundParameters(evenLoudness = true, episodeKey = "s")
        run(processor, speech, 1, intArrayOf(4_096), end = false)
        val before = processor.evenGainDb
        assertTrue("gain built up: $before", before > 8f)
        processor.flush()
        run(processor, speech.copyOf(rate), 1, intArrayOf(4_096), end = false)
        assertEquals(before, processor.evenGainDb, 0.3f)
    }

    @Test
    fun `after an item change nothing more is counted into the old episode's memory`() {
        val rate = 16_000
        val speech = speechLike(rate, seconds = 125.0, seed = 12)
        scaleToLufs(speech, rate, -30.0)
        val processor = configured(rate, 1)
        processor.parameters = SoundParameters(evenLoudness = true, episodeKey = "old")
        run(processor, speech, 1, intArrayOf(4_096), end = false)
        val saved = processor.loudnessMemoryNow() ?: error("no memory")
        processor.freezeMemory()
        // Louder sound of the next episode, still under the old key.
        val loud = speechLike(rate, seconds = 70.0, seed = 13)
        scaleToLufs(loud, rate, -14.0)
        var reported = false
        processor.onLoudnessMemory = { _, _ -> reported = true }
        run(processor, loud, 1, intArrayOf(4_096), end = false)
        assertTrue(!reported)
        assertEquals(saved, processor.loudnessMemoryNow())
    }

    // c)

    @Test
    fun `the limiter holds every peak under -1 dBFS with +24 dB`() {
        val rate = 48_000
        val ceiling = 10.0.pow(-1.0 / 20) * 32_768 + 1.0
        val signals = mapOf(
            "sine" to sine(rate, 2, 1_000.0, 0.0, seconds = 2.0),
            "square" to square(rate, 2, 200.0, seconds = 2.0),
            "noise" to noise(rate, 2, seconds = 2.0, amplitude = 1.0, seed = 11),
            // Low tones whose quarter period outlasts the lookahead window (review of З).
            "sine 30 Hz" to sine(rate, 2, 30.0, 0.0, seconds = 2.0),
            "sine 45 Hz" to sine(rate, 2, 45.0, -6.0, seconds = 2.0),
        )
        for ((name, signal) in signals) {
            val processor = configured(rate, 2)
            processor.parameters = SoundParameters(volumeDb = 24)
            val output = run(processor, signal, 2, intArrayOf(4_096))
            val peak = output.maxOf { abs(it.toInt()) }
            assertTrue("$name peak $peak over the ceiling", peak <= ceiling)
            assertEquals("$name final clamp", 0L, processor.clampedSamples)
        }
    }

    @Test
    fun `6 dB of limiting on a 1 kHz sine stays under 2 percent THD`() {
        val rate = 48_000
        val processor = configured(rate, 1)
        processor.parameters = SoundParameters(volumeDb = 6)
        val input = sine(rate, 1, 1_000.0, -1.0, seconds = 2.0)
        val output = run(processor, input, 1, intArrayOf(4_096))
        val steady = DoubleArray(rate) { output[rate / 2 + it] / 32_768.0 }
        val fundamental = goertzel(steady, rate, 1_000.0)
        var harmonics = 0.0
        for (h in 2..10) harmonics += goertzel(steady, rate, 1_000.0 * h).pow(2)
        val thd = sqrt(harmonics) / fundamental
        assertTrue("THD $thd", thd <= 0.02)
        assertTrue("limiting happened: ${processor.limitingDb}", processor.limitingDb > 4f)
    }

    // d)

    @Test
    fun `clarity and depth steps follow the analytic response of their filters`() {
        val rate = 48_000
        val cases = (1..3).map { it to 0 } + (1..3).map { 0 to it } + listOf(3 to 3, 2 to 1)
        for ((clarity, depth) in cases) {
            for (frequency in listOf(60.0, 110.0, 300.0, 1_000.0, 3_000.0, 8_000.0)) {
                val processor = configured(rate, 1)
                processor.parameters = SoundParameters(clarity = clarity, depth = depth)
                val input = sine(rate, 1, frequency, -30.0, seconds = 4.0)
                val output = run(processor, input, 1, intArrayOf(4_096))
                val measured = 20 * log10(rms(output, rate * 3, rate) / rms(input, rate * 3, rate))
                val expected = expectedToneDb(rate, frequency, clarity, depth)
                assertEquals("clarity=$clarity depth=$depth $frequency Hz", expected, measured, 1.0)
            }
        }
    }

    @Test
    fun `depth shelf is off on the built-in speaker, its cut stays`() {
        val rate = 48_000
        val processor = configured(rate, 1)
        processor.parameters = SoundParameters(depth = 3, speakerRoute = true)
        val input = sine(rate, 1, 110.0, -30.0, seconds = 2.0)
        val output = run(processor, input, 1, intArrayOf(4_096))
        val measured = 20 * log10(rms(output, rate, rate / 2) / rms(input, rate, rate / 2))
        assertEquals(peakingDb(rate, 110.0, 280.0, 1.2, -3.0), measured, 1.0)
    }

    // e)

    @Test
    fun `the clarity compressor shrinks a 12 dB difference to 12 over its ratio`() {
        val rate = 48_000
        for ((step, ratio) in listOf(1 to 1.5, 2 to 2.0, 3 to 3.0)) {
            val processor = configured(rate, 1)
            processor.parameters = SoundParameters(clarity = step)
            val burst = rate / 2
            val input = ShortArray(rate * 8)
            for (i in input.indices) {
                val loud = (i / burst) % 2 == 0
                val amplitude = 10.0.pow((if (loud) -20.0 else -32.0) / 20) * sqrt(2.0)
                input[i] = (sin(2 * PI * 1_000.0 * i / rate) * amplitude * 32_767).toInt().toShort()
            }
            val output = run(processor, input, 1, intArrayOf(4_096))
            // The last pair, each burst over its settled last quarter (the release is 150 ms).
            val loud = rms(output, 13 * burst - burst / 4, burst / 4)
            val quiet = rms(output, 14 * burst - burst / 4, burst / 4)
            val difference = 20 * log10(loud / quiet)
            assertEquals("clarity $step", 12 / ratio, difference, 1.5)
        }
    }

    // f)

    @Test
    fun `even loudness brings -36 LUFS speech to -16 within 20 s and holds in silence`() {
        val rate = 48_000
        val speech = speechLike(rate, seconds = 20.0, seed = 21)
        scaleToLufs(speech, rate, -36.0)
        val processor = configured(rate, 1)
        processor.parameters = SoundParameters(evenLoudness = true, episodeKey = "a")
        val output = run(processor, speech, 1, intArrayOf(4_096), end = false)
        // The processor measures a mono source as dual mono (R2): read the output the same way.
        val reached = referenceLufs(output, rate, output.size - 3 * rate, 3 * rate) + DUAL_MONO_LU
        assertEquals(-16.0, reached, 1.5)
        val before = processor.evenGainDb
        run(processor, ShortArray(rate * 10), 1, intArrayOf(4_096), end = false)
        assertEquals(before, processor.evenGainDb, 0.5f)
    }

    @Test
    fun `the remembered gain is reported after 120 s and starts the next episode`() {
        val rate = 16_000
        val speech = speechLike(rate, seconds = 130.0, seed = 5)
        scaleToLufs(speech, rate, -30.0)
        val processor = configured(rate, 1)
        var remembered: Pair<String, Float>? = null
        processor.onLoudnessMemory = { key, gain -> remembered = key to gain }
        processor.parameters = SoundParameters(evenLoudness = true, episodeKey = "first")
        run(processor, speech, 1, intArrayOf(4_096), end = false)
        val (key, gain) = remembered ?: error("no memory reported")
        assertEquals("first", key)
        assertEquals(14f, gain, 2f)
        val now = processor.loudnessMemoryNow() ?: error("no memory on stop")
        assertEquals("first", now.first)
        assertEquals(gain, now.second, 1f)
        // The next episode of the same podcast starts from the remembered gain at once.
        processor.parameters = SoundParameters(evenLoudness = true, episodeKey = "second", startGainDb = gain)
        val next = speechLike(rate, seconds = 0.5, seed = 6).also { scaleToLufs(it, rate, -30.0) }
        run(processor, next, 1, intArrayOf(4_096), end = false)
        assertEquals(gain, processor.evenGainDb, 0.3f)
    }

    // l) (added by the audit session, 12.11.2026)

    @Test
    fun `every control at its maximum at once never breaks`() {
        val rate = 48_000
        val ceiling = 10.0.pow(-1.0 / 20) * 32_768 + 1.0
        val maximum = SoundParameters(
            volumeDb = 24, clarity = 3, depth = 3, evenLoudness = true,
            speakerRoute = true, speakerTrimDb = 12, episodeKey = "max",
        )
        for (level in listOf(-16.0, -36.0)) {
            val speech = speechLike(rate, seconds = 30.0, seed = level.toInt())
            scaleToLufs(speech, rate, level)
            val processor = configured(rate, 1)
            processor.parameters = maximum
            val output = run(processor, speech, 1, intArrayOf(4_096))
            assertEquals(speech.size, output.size)
            val peak = output.maxOf { abs(it.toInt()) }
            assertTrue("$level LUFS: peak $peak over the ceiling", peak <= ceiling)
            assertEquals("$level LUFS: final clamp", 0L, processor.clampedSamples)
            assertTrue("$level LUFS: gain is finite", processor.evenGainDb.isFinite() && processor.limitingDb.isFinite())
            var loudest = -200.0
            for (start in 0..output.size - 3 * rate step rate / 2) {
                loudest = maxOf(loudest, referenceLufs(output, rate, start, 3 * rate) + DUAL_MONO_LU)
            }
            assertTrue("$level LUFS: loudest short-term $loudest", loudest < -6.0)
        }
    }

    // Audit of З (13.11.2026), R1–R5.

    @Test
    fun `R1 a quiet guest is not pulled up so far that the host's return blasts`() {
        val rate = 48_000
        fun part(seconds: Double, lufs: Double, seed: Int) = speechLike(rate, seconds, seed).also { scaleToLufs(it, rate, lufs) }
        val processor = configured(rate, 1)
        processor.parameters = SoundParameters(evenLoudness = true, episodeKey = "r1")
        run(processor, part(30.0, -20.0, 1), 1, intArrayOf(4_096), end = false)
        // What the episode as a whole wants (the dead band may keep the gain itself a little under it).
        val baseline = maxOf(processor.evenGainDb, (-16.0 - (-20.0 + DUAL_MONO_LU)).toFloat())
        // The quiet guest, 15 s, watched every half second.
        val quiet = part(15.0, -30.0, 2)
        var highest = baseline
        for (i in 0 until 30) {
            run(processor, quiet.copyOfRange(i * rate / 2, (i + 1) * rate / 2), 1, intArrayOf(4_096), end = false)
            highest = maxOf(highest, processor.evenGainDb)
        }
        assertTrue("quiet part: gain $highest over baseline $baseline + 8.5", highest <= baseline + 8.5f)
        // The host returns.
        val back = run(processor, part(10.0, -20.0, 3), 1, intArrayOf(4_096), end = false)
        for (start in 0..back.size - rate * 4 / 10 step rate / 10) {
            val momentary = referenceLufs(back, rate, start, rate * 4 / 10) + DUAL_MONO_LU
            assertTrue("return at ${start * 1_000 / rate} ms: $momentary", momentary <= -16.0 + 9)
            if (start >= rate * 3 / 2) assertTrue("settled at ${start * 1_000 / rate} ms: $momentary", momentary <= -16.0 + 5)
        }
    }

    @Test
    fun `R2 a mono file and the same content as identical stereo end at the same gain`() {
        val rate = 48_000
        val mono = speechLike(rate, seconds = 25.0, seed = 30)
        scaleToLufs(mono, rate, -30.0)
        val stereo = ShortArray(mono.size * 2) { mono[it / 2] }
        val one = configured(rate, 1).apply { parameters = SoundParameters(evenLoudness = true, episodeKey = "m") }
        val two = configured(rate, 2).apply { parameters = SoundParameters(evenLoudness = true, episodeKey = "s") }
        run(one, mono, 1, intArrayOf(4_096), end = false)
        run(two, stereo, 2, intArrayOf(4_096), end = false)
        assertEquals(one.evenGainDb, two.evenGainDb, 0.3f)
    }

    @Test
    fun `R3 the meter's shelf matches the published BS_1770 coefficients`() {
        val meter = LoudnessMeter(48_000, 1)
        val b = doubleArrayOf(1.53512485958697, -2.69169618940638, 1.19839281085285)
        val a = doubleArrayOf(1.0, -1.69065929318241, 0.73248077421585)
        for (f in listOf(100.0, 1_000.0, 1_500.0, 2_000.0, 5_000.0)) {
            assertEquals("$f Hz", magnitudeDb(48_000, f, b, a), meter.shelf.magnitudeDb(48_000, f), 0.05)
        }
    }

    @Test
    fun `R4 a volume step with clarity does not creep`() {
        val rate = 48_000
        val steady = steadySpeech(rate, seconds = 10.0, lufs = -30.0)
        val processor = configured(rate, 1)
        processor.parameters = SoundParameters(clarity = 3)
        run(processor, steady.copyOfRange(0, 5 * rate), 1, intArrayOf(4_096), end = false)
        processor.parameters = SoundParameters(clarity = 3, volumeDb = 12)
        val after = run(processor, steady.copyOfRange(5 * rate, 10 * rate), 1, intArrayOf(4_096), end = false)
        val early = rms(after, rate * 34 / 100, rate / 10)
        val late = rms(after, rate * 434 / 100, rate / 10)
        assertEquals(0.0, 20 * log10(early / late), 2.0)
    }

    @Test
    fun `R5 clarity has no loud first moments before the compressor works`() {
        val rate = 48_000
        val steady = steadySpeech(rate, seconds = 3.0, lufs = -30.0)
        val processor = configured(rate, 1)
        processor.parameters = SoundParameters(clarity = 3)
        val output = run(processor, steady, 1, intArrayOf(4_096))
        val first = rms(output, rate / 10, rate / 5)
        val settled = rms(output, 2 * rate, rate)
        assertEquals(0.0, 20 * log10(first / settled), 1.5)
    }

    @Test
    fun `R6 coming out of rest starts with fresh windows, not minutes-old audio`() {
        val rate = 48_000
        val processor = configured(rate, 1)
        processor.parameters = SoundParameters(evenLoudness = true, episodeKey = "r6")
        run(processor, steadySpeech(rate, seconds = 12.0, lufs = -14.0), 1, intArrayOf(4_096), end = false)
        val before = processor.evenGainDb
        // Everything off over a quiet stretch: the windows are not fed at rest.
        processor.parameters = SoundParameters(episodeKey = "r6")
        run(processor, steadySpeech(rate, seconds = 5.0, lufs = -36.0), 1, intArrayOf(4_096), end = false)
        processor.parameters = SoundParameters(evenLoudness = true, episodeKey = "r6")
        // The first 2.5 s after switching on: the restarted window holds the gain instead of acting
        // on the loud audio from before the rest.
        run(processor, steadySpeech(rate, seconds = 2.5, lufs = -36.0), 1, intArrayOf(4_096), end = false)
        assertEquals(before, processor.evenGainDb, 0.5f)
    }

    /** Lowpassed noise at a constant level: speech-like spectrum without its rhythm. */
    private fun steadySpeech(rate: Int, seconds: Double, lufs: Double): ShortArray {
        val random = java.util.Random(77)
        var low = 0.0
        val samples = ShortArray((rate * seconds).toInt()) {
            low += 0.2 * ((random.nextDouble() * 2 - 1) - low)
            (low * 20_000).toInt().toShort()
        }
        scaleToLufs(samples, rate, lufs)
        return samples
    }

    // k)

    @Test
    fun `a minute of 48 kHz stereo with everything on takes well under 1_5 s`() {
        val rate = 48_000
        val input = noise(rate, 2, seconds = 60.0, amplitude = 0.3, seed = 9)
        val warm = configured(rate, 2)
        warm.parameters = everythingOn()
        run(warm, input.copyOf(rate * 2 * 5), 2, intArrayOf(4_096))
        val processor = configured(rate, 2)
        processor.parameters = everythingOn()
        val started = System.nanoTime()
        run(processor, input, 2, intArrayOf(4_096))
        val seconds = (System.nanoTime() - started) / 1e9
        assertTrue("took $seconds s", seconds < 1.5)
    }

    // Helpers.

    private fun everythingOn() = SoundParameters(
        volumeDb = 6, mono = true, evenLoudness = true, clarity = 3, depth = 3,
        speakerRoute = true, speakerTrimDb = 6, episodeKey = "e",
    )

    private fun configured(rate: Int, channels: Int) = SoundProcessor().apply {
        configure(AudioProcessor.AudioFormat(rate, channels, C.ENCODING_PCM_16BIT))
        flush()
    }

    private fun run(processor: SoundProcessor, input: ShortArray, channels: Int, splits: IntArray, end: Boolean = true): ShortArray {
        val output = ShortArray(input.size + 4_096 * channels)
        var written = 0
        var position = 0
        var i = 0
        while (position < input.size) {
            val frames = splits[i++ % splits.size]
            val endAt = minOf(input.size, position + frames * channels)
            processor.queueInput(pcm(input.copyOfRange(position, endAt)))
            written = drain(processor, output, written)
            position = endAt
        }
        if (end) {
            processor.queueEndOfStream()
            written = drain(processor, output, written)
        }
        return output.copyOf(written)
    }

    private fun drain(processor: SoundProcessor, into: ShortArray, from: Int): Int {
        var n = from
        val buffer = processor.output.order(ByteOrder.nativeOrder())
        while (buffer.remaining() >= 2) into[n++] = buffer.short
        return n
    }

    private fun pcm(samples: ShortArray): ByteBuffer =
        ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder()).apply {
            asShortBuffer().put(samples)
        }

    private fun sine(rate: Int, channels: Int, frequency: Double, dbfsPeak: Double, seconds: Double): ShortArray {
        val frames = (rate * seconds).toInt()
        val amplitude = 10.0.pow(dbfsPeak / 20) * 32_767
        return ShortArray(frames * channels) { (sin(2 * PI * frequency * (it / channels) / rate) * amplitude).toInt().toShort() }
    }

    private fun square(rate: Int, channels: Int, frequency: Double, seconds: Double): ShortArray {
        val frames = (rate * seconds).toInt()
        return ShortArray(frames * channels) { if (sin(2 * PI * frequency * (it / channels) / rate) >= 0) 32_767 else -32_768 }
    }

    private fun noise(rate: Int, channels: Int, seconds: Double, amplitude: Double, seed: Int): ShortArray {
        val random = java.util.Random(seed.toLong())
        return ShortArray((rate * seconds).toInt() * channels) {
            ((random.nextDouble() * 2 - 1) * amplitude * 32_767).toInt().toShort()
        }
    }

    /** Noise shaped like speech: a 4 Hz syllable envelope with short gaps, lowpassed. */
    private fun speechLike(rate: Int, seconds: Double, seed: Int): ShortArray {
        val random = java.util.Random(seed.toLong())
        var low = 0.0
        return ShortArray((rate * seconds).toInt()) { i ->
            val t = i.toDouble() / rate
            val envelope = (0.5 + 0.5 * sin(2 * PI * 4 * t)).pow(2) * if ((t % 2.3) < 2.0) 1.0 else 0.15
            low += 0.2 * ((random.nextDouble() * 2 - 1) - low)
            (low * envelope * 20_000).toInt().toShort()
        }
    }

    private fun rms(samples: ShortArray, from: Int, count: Int): Double {
        var sum = 0.0
        for (i in from until from + count) sum += samples[i].toDouble() * samples[i]
        return sqrt(sum / count)
    }

    private fun goertzel(x: DoubleArray, rate: Int, frequency: Double): Double {
        var re = 0.0
        var im = 0.0
        for (i in x.indices) {
            val w = 2 * PI * frequency * i / rate
            re += x[i] * cos(w)
            im -= x[i] * sin(w)
        }
        return hypot(re, im) * 2 / x.size
    }

    /** The specified tone filters' magnitude, from their transfer functions on the unit circle. */
    private fun expectedToneDb(rate: Int, f: Double, clarity: Int, depth: Int): Double {
        var db = 0.0
        var cut300 = doubleArrayOf(0.0, -1.5, -3.0, -4.0)[clarity]
        var cut280 = doubleArrayOf(0.0, -1.0, -2.0, -3.0)[depth]
        if (cut300 + cut280 < -5.0) {
            val scale = -5.0 / (cut300 + cut280)
            cut300 *= scale
            cut280 *= scale
        }
        if (clarity > 0) {
            db += highPassDb(rate, f, if (depth > 0) 60.0 else 90.0)
            db += peakingDb(rate, f, 3_000.0, 0.8, doubleArrayOf(0.0, 2.0, 4.0, 5.0)[clarity])
        }
        if (cut300 != 0.0) db += peakingDb(rate, f, 300.0, 1.0, cut300)
        if (depth > 0) db += lowShelfDb(rate, f, 110.0, 0.8, doubleArrayOf(0.0, 2.0, 4.0, 6.0)[depth])
        if (cut280 != 0.0) db += peakingDb(rate, f, 280.0, 1.2, cut280)
        return db
    }

    private fun magnitudeDb(rate: Int, f: Double, b: DoubleArray, a: DoubleArray): Double {
        val w = 2 * PI * f / rate
        fun eval(c: DoubleArray): Pair<Double, Double> =
            (c[0] + c[1] * cos(-w) + c[2] * cos(-2 * w)) to (c[1] * sin(-w) + c[2] * sin(-2 * w))
        val (nr, ni) = eval(b)
        val (dr, di) = eval(a)
        return 20 * log10(hypot(nr, ni) / hypot(dr, di))
    }

    private fun peakingDb(rate: Int, f: Double, f0: Double, q: Double, gain: Double): Double {
        val a = 10.0.pow(gain / 40)
        val w0 = 2 * PI * f0 / rate
        val alpha = sin(w0) / (2 * q)
        return magnitudeDb(rate, f, doubleArrayOf(1 + alpha * a, -2 * cos(w0), 1 - alpha * a), doubleArrayOf(1 + alpha / a, -2 * cos(w0), 1 - alpha / a))
    }

    private fun lowShelfDb(rate: Int, f: Double, f0: Double, s: Double, gain: Double): Double {
        val a = 10.0.pow(gain / 40)
        val w0 = 2 * PI * f0 / rate
        val c = cos(w0)
        val alpha = sin(w0) / 2 * sqrt((a + 1 / a) * (1 / s - 1) + 2)
        val r = 2 * sqrt(a) * alpha
        return magnitudeDb(
            rate, f,
            doubleArrayOf(a * ((a + 1) - (a - 1) * c + r), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - r)),
            doubleArrayOf((a + 1) + (a - 1) * c + r, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - r),
        )
    }

    private fun highPassDb(rate: Int, f: Double, f0: Double): Double {
        val w0 = 2 * PI * f0 / rate
        val c = cos(w0)
        val alpha = sin(w0) / (2 / sqrt(2.0))
        return magnitudeDb(rate, f, doubleArrayOf((1 + c) / 2, -(1 + c), (1 + c) / 2), doubleArrayOf(1 + alpha, -2 * c, 1 - alpha))
    }

    /** BS.1770 loudness with the standard's own 48 kHz coefficients (other rates: re-derived). */
    private fun referenceLufs(samples: ShortArray, rate: Int, from: Int, count: Int): Double {
        val (b1, a1, b2, a2) = if (rate == 48_000) {
            listOf(
                doubleArrayOf(1.53512485958697, -2.69169618940638, 1.19839281085285),
                doubleArrayOf(1.0, -1.69065929318241, 0.73248077421585),
                doubleArrayOf(1.0, -2.0, 1.0),
                doubleArrayOf(1.0, -1.99004745483398, 0.99007225036621),
            )
        } else {
            error("reference meter is 48 kHz only")
        }
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        var u1 = 0.0; var u2 = 0.0; var v1 = 0.0; var v2 = 0.0
        var sum = 0.0
        for (i in 0 until from + count) {
            val x = samples[i] / 32_768.0
            val y = b1[0] * x + b1[1] * x1 + b1[2] * x2 - a1[1] * y1 - a1[2] * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            val v = b2[0] * y + b2[1] * u1 + b2[2] * u2 - a2[1] * v1 - a2[2] * v2
            u2 = u1; u1 = y; v2 = v1; v1 = v
            if (i >= from) sum += v * v
        }
        return -0.691 + 10 * log10(sum / count)
    }

    private fun scaleToLufs(samples: ShortArray, rate: Int, target: Double) {
        val meterRate = 48_000
        val measured = if (rate == meterRate) {
            referenceLufs(samples, rate, 0, samples.size)
        } else {
            // Below 48 kHz: measure with the processor's own meter (the reference is 48 kHz only).
            val meter = LoudnessMeter(rate, 1)
            var energy = 0.0
            var blocks = 0
            for (s in samples) {
                if (meter.add(0, s / 32_768.0)) {
                    energy += 10.0.pow((meter.loudness(1) + 0.691) / 10)
                    blocks++
                }
            }
            LoudnessMeter.lufs(energy / blocks)
        }
        val factor = 10.0.pow((target - measured) / 20)
        for (i in samples.indices) samples[i] = (samples[i] * factor).toInt().coerceIn(-32_768, 32_767).toShort()
    }
}

/** A mono signal played on both sides reads this much louder than measured on one channel (BS.1770). */
private const val DUAL_MONO_LU = 3.0103
