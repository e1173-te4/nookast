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
 * The second version of the sound (DEC-071). The user does not listen before the release (12.11.2026:
 * «я тебе доверяю»), so every requirement is a measurement on synthetic speech. Numbers are printed
 * for the report.
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class SoundV2Test {
    private val rate = 48_000

    // Clarity.

    @Test
    fun `clarity is a little louder, never quieter, and lifts dull recordings more than bright ones`() {
        val report = StringBuilder()
        val fails = mutableListOf<String>()
        for ((name, signal) in listOf("normal" to speech(Tilt.NORMAL), "dull" to speech(Tilt.DULL), "bright" to speech(Tilt.BRIGHT))) {
            val from = 7 * rate
            val count = 5 * rate
            val bypass = processed(signal, SoundParameters())
            for (step in 1..3) {
                val probe = processor().apply { parameters = SoundParameters(clarity = step) }
                val out = run(probe, signal)
                report.appendLine("  $name balance ${"%.1f".format(probe.balanceNow)} adapt ${"%.2f".format(probe.presenceAdaptNow)}")
                val louder = lufs(out, from, count) - lufs(bypass, from, count)
                val balanceRise = balance(out, from, count) - balance(bypass, from, count)
                val crest = crestDb(out, from, count) - crestDb(bypass, from, count)
                report.appendLine("clarity $step $name: louder ${"%.2f".format(louder)} LU, balance +${"%.2f".format(balanceRise)} dB, crest ${"%.2f".format(crest)} dB")
                if (louder !in .5..1.5) fails += "$name step $step louder $louder"
                if (abs(crest) > 1.0) fails += "$name step $step crest $crest"
                if (step == 3 && name == "dull" && balanceRise < 4.0) fails += "dull balance $balanceRise"
                if (step == 3 && name == "bright" && balanceRise > 2.0) fails += "bright balance $balanceRise"
            }
        }
        println(report)
        assertTrue(fails.joinToString(" | "), fails.isEmpty())
    }

    @Test
    fun `a 7 kHz whistle is not lifted more than 2 dB`() {
        val base = speech(Tilt.NORMAL)
        // 100 ms of 7 kHz every half second, as loud as the speech around it.
        val signal = ShortArray(base.size) { i ->
            val t = i.toDouble() / rate
            val burst = if ((t % .5) < .1) sin(2 * PI * 7_000 * t) * 6_000 else 0.0
            (base[i] + burst).toInt().coerceIn(-32_768, 32_767).toShort()
        }
        val from = 7 * rate
        val count = 5 * rate
        val bypass = processed(signal, SoundParameters())
        val out = processed(signal, SoundParameters(clarity = 3))
        val lift = 20 * log10(tone(out, from, count, 7_000.0) / tone(bypass, from, count, 7_000.0))
        println("whistle 7 kHz lift ${"%.2f".format(lift)} dB")
        assertTrue("lift $lift", lift <= 2.0)
    }

    // Depth.

    @Test
    fun `depth lifts the voice's body at equal loudness without boom`() {
        val report = StringBuilder()
        val fails = mutableListOf<String>()
        val low = voice(110.0)
        val from = 7 * rate
        val count = 5 * rate
        val bypass = processed(low, SoundParameters())
        for (step in 1..3) {
            val out = processed(low, SoundParameters(depth = step))
            val louder = lufs(out, from, count) - lufs(bypass, from, count)
            val body = harmonicsDb(out, bypass, from, count, 110.0, 80.0, 200.0) - louder
            val boom = harmonicsDb(out, bypass, from, count, 110.0, 250.0, 450.0) - louder
            // On the phone's own speaker: the route the harmonics are for.
            val onSpeaker = processed(low, SoundParameters(depth = step, speakerRoute = true))
            val speakerLouder = lufs(onSpeaker, from, count) - lufs(bypass, from, count)
            val speaker = harmonicsDb(smallSpeaker(onSpeaker), smallSpeaker(bypass), from, count, 110.0, 180.0, 500.0) - speakerLouder
            report.appendLine("depth $step f0 110: louder ${"%.2f".format(louder)} LU, 80–200 +${"%.2f".format(body)}, 250–450 ${"%.2f".format(boom)}, small speaker 180–500 +${"%.2f".format(speaker)} dB")
            if (louder !in 0.0..0.5) fails += "depth $step louder $louder"
            if (boom > .5) fails += "depth $step 250–450 $boom"
            if (step == 2 && body < 4.0) fails += "depth 2 body $body"
            if (step == 3 && body < 6.0) fails += "depth 3 body $body"
            if (step == 3 && speaker < 1.5) fails += "depth 3 small speaker $speaker"
        }
        val high = voice(210.0)
        val bypassHigh = processed(high, SoundParameters())
        val outHigh = processed(high, SoundParameters(depth = 3))
        val louder = lufs(outHigh, from, count) - lufs(bypassHigh, from, count)
        val rise = harmonicsDb(outHigh, bypassHigh, from, count, 210.0, 200.0, 260.0) - louder
        report.appendLine("depth 3 f0 210: 200–260 +${"%.2f".format(rise)} dB")
        println(report)
        if (rise > 3.0) fails += "f0 210 rise $rise"
        assertTrue(fails.joinToString(" | "), fails.isEmpty())
    }

    // Volume.

    @Test
    fun `the new maximum is denser than 1_2_5's without passing the ceiling`() {
        val signal = speech(Tilt.NORMAL)
        scaleToLufs(signal, -16.0)
        val ceiling = (32_768 * 10.0.pow(-1.0 / 20)).toInt()
        for (i in signal.indices) signal[i] = signal[i].toInt().coerceIn(-ceiling, ceiling).toShort()
        val from = 7 * rate
        val count = 5 * rate
        val old = processor().apply { densityEnabled = false; parameters = SoundParameters(volumeDb = 24) }
        val oldOut = run(old, signal)
        val new = processor().apply { parameters = SoundParameters(volumeDb = 30) }
        val newOut = run(new, signal)
        val oldLufs = lufs(oldOut, from, count)
        val newLufs = lufs(newOut, from, count)
        val peak = newOut.maxOf { abs(it.toInt()) }
        println("volume max: 1.2.5 (+24) ${"%.2f".format(oldLufs)} LUFS, new (+30) ${"%.2f".format(newLufs)} LUFS, peak ${"%.2f".format(20 * log10(peak / 32_768.0))} dBFS")
        assertTrue("new $newLufs", newLufs >= -8.0)
        assertTrue("gain ${newLufs - oldLufs}", newLufs - oldLufs >= 2.5)
        assertTrue("peak $peak", peak <= ceiling + 1)
        assertEquals(0L, new.clampedSamples)
    }

    @Test
    fun `up to +12 dB nothing new happens, a gain stays a gain`() {
        val signal = speech(Tilt.NORMAL)
        for (db in listOf(1, 6, 12)) {
            val with = run(processor().apply { parameters = SoundParameters(volumeDb = db) }, signal)
            val without = run(processor().apply { densityEnabled = false; parameters = SoundParameters(volumeDb = db) }, signal)
            assertArrayEquals("+$db dB", without, with)
        }
        val sine = ShortArray(4 * rate) { (sin(2 * PI * 1_000.0 * it / rate) * 3_000).toInt().toShort() }
        val out = run(processor().apply { parameters = SoundParameters(volumeDb = 1) }, sine)
        val gain = 20 * log10(tone(out, rate, 2 * rate, 1_000.0) / tone(sine, rate, 2 * rate, 1_000.0))
        assertEquals(1.0, gain, .1)
    }

    @Test
    fun `a 1 kHz sine at +12 dB stays under 0_5 percent distortion`() {
        val sine = ShortArray(4 * rate) { (sin(2 * PI * 1_000.0 * it / rate) * 32_767 * 10.0.pow(-30.0 / 20)).toInt().toShort() }
        val out = run(processor().apply { parameters = SoundParameters(volumeDb = 12) }, sine)
        val fundamental = tone(out, rate, 2 * rate, 1_000.0)
        var harmonics = 0.0
        for (k in 2..7) harmonics += tone(out, rate, 2 * rate, 1_000.0 * k).pow(2)
        val thd = sqrt(harmonics) / fundamental * 100
        println("THD at +12 dB: ${"%.4f".format(thd)} %")
        assertTrue("thd $thd", thd < .5)
    }

    @Test
    fun `the soft clip does not fold its harmonics back into the band`() {
        val sine = ShortArray(4 * rate) { (sin(2 * PI * 7_000.0 * it / rate) * 32_767 * 10.0.pow(-6.0 / 20)).toInt().toShort() }
        val out = run(processor().apply { parameters = SoundParameters(volumeDb = 30) }, sine)
        val worst = listOf(1_000.0, 6_000.0, 13_000.0, 20_000.0).maxOf { 20 * log10(tone(out, rate, 2 * rate, it) / 32_768.0 + 1e-12) }
        println("aliasing at +30 dB, 7 kHz: worst ${"%.1f".format(worst)} dBFS")
        assertTrue("alias $worst", worst < -60.0)
    }

    // Helpers.

    private enum class Tilt { NORMAL, DULL, BRIGHT }

    private fun processor() = SoundProcessor().apply {
        configure(AudioProcessor.AudioFormat(rate, 1, C.ENCODING_PCM_16BIT))
        flush()
    }

    private fun processed(input: ShortArray, parameters: SoundParameters): ShortArray =
        run(processor().apply { this.parameters = parameters }, input)

    private fun run(processor: SoundProcessor, input: ShortArray): ShortArray {
        val output = ShortArray(input.size + 8_192)
        var written = 0
        var position = 0
        while (position < input.size) {
            val end = minOf(input.size, position + 4_096)
            val buffer = ByteBuffer.allocateDirect((end - position) * 2).order(ByteOrder.nativeOrder())
            buffer.asShortBuffer().put(input, position, end - position)
            processor.queueInput(buffer)
            val out = processor.output.order(ByteOrder.nativeOrder())
            while (out.remaining() >= 2) output[written++] = out.short
            position = end
        }
        processor.queueEndOfStream()
        val out = processor.output.order(ByteOrder.nativeOrder())
        while (out.remaining() >= 2) output[written++] = out.short
        return output.copyOf(written)
    }

    /** Noise shaped like speech (a 4 Hz syllable envelope with gaps), with a dull or bright tilt. */
    private fun speech(tilt: Tilt, seconds: Double = 12.0, seed: Int = 7): ShortArray {
        val random = java.util.Random(seed.toLong())
        // The long-term speech spectrum: a peak near 500 Hz, 6 dB an octave down on either side.
        var lp500 = 0.0
        var lp150 = 0.0
        var lp1500 = 0.0
        var lp2000 = 0.0
        val out = ShortArray((rate * seconds).toInt()) { i ->
            val t = i.toDouble() / rate
            val envelope = (0.5 + 0.5 * sin(2 * PI * 4 * t)).pow(2) * if ((t % 2.3) < 2.0) 1.0 else 0.15
            val white = random.nextDouble() * 2 - 1
            lp500 += .0634 * (white - lp500)
            lp150 += .0194 * (lp500 - lp150)
            val band = (lp500 - lp150) * 3
            lp1500 += .178 * (band - lp1500)
            lp2000 += .23 * (white - lp2000)
            val x = when (tilt) {
                Tilt.NORMAL -> band
                Tilt.DULL -> lp1500
                Tilt.BRIGHT -> band + .55 * (white - lp2000)
            }
            (x * envelope * 20_000).toInt().coerceIn(-32_768, 32_767).toShort()
        }
        scaleToLufs(out, -20.0)
        return out
    }

    /**
     * A voiced sound: harmonics of [f0] under the long-term speech spectrum (rising to its peak near
     * 450 Hz, falling 6 dB an octave above), up to 4 kHz, on a syllable envelope.
     */
    private fun voice(f0: Double, seconds: Double = 12.0): ShortArray {
        val harmonics = (1..(4_000 / f0).toInt()).toList()
        val phases = harmonics.map { it * 1.3 }
        val out = ShortArray((rate * seconds).toInt()) { i ->
            val t = i.toDouble() / rate
            val envelope = 0.6 + 0.4 * sin(2 * PI * 3 * t)
            var x = 0.0
            for ((n, k) in harmonics.withIndex()) {
                val r = f0 * k / 450.0
                x += sin(2 * PI * f0 * k * t + phases[n]) * 2 * r / (1 + r * r)
            }
            (x * envelope * 6_000).toInt().coerceIn(-32_768, 32_767).toShort()
        }
        scaleToLufs(out, -20.0)
        return out
    }

    /** A phone speaker: nothing below 150 Hz (24 dB an octave). */
    private fun smallSpeaker(x: ShortArray): ShortArray {
        val a = Biquad(1).apply { highPass(rate, 150.0) }
        val b = Biquad(1).apply { highPass(rate, 150.0) }
        return ShortArray(x.size) { (b.process(0, a.process(0, x[it].toDouble()))).toInt().coerceIn(-32_768, 32_767).toShort() }
    }

    /** The level of the harmonics of [f0] between [low] and [high] Hz, out against in, in dB. */
    private fun harmonicsDb(out: ShortArray, input: ShortArray, from: Int, count: Int, f0: Double, low: Double, high: Double): Double {
        var o = 0.0
        var i = 0.0
        var k = 1
        while (f0 * k <= high) {
            if (f0 * k >= low) {
                o += tone(out, from, count, f0 * k).pow(2)
                i += tone(input, from, count, f0 * k).pow(2)
            }
            k++
        }
        return 10 * log10(o / i)
    }

    /** The 2–6 kHz to 250–1000 Hz energy balance, with fourth-order band-passes. */
    private fun balance(x: ShortArray, from: Int, count: Int): Double {
        fun band(lowHz: Double, highHz: Double): Double {
            val a = Biquad(1).apply { bandPass(rate, bandCentre(lowHz, highHz), bandQ(lowHz, highHz)) }
            val b = Biquad(1).apply { bandPass(rate, bandCentre(lowHz, highHz), bandQ(lowHz, highHz)) }
            var sum = 0.0
            for (n in 0 until from + count) {
                val y = b.process(0, a.process(0, x[n].toDouble()))
                if (n >= from) sum += y * y
            }
            return sum
        }
        return 10 * log10(band(2_000.0, 6_000.0) / band(250.0, 1_000.0))
    }

    /** Peak to RMS, the peak read as the 99.9th percentile so one sample does not decide it. */
    private fun crestDb(x: ShortArray, from: Int, count: Int): Double {
        val magnitudes = DoubleArray(count) { abs(x[from + it].toDouble()) }
        var sum = 0.0
        for (v in magnitudes) sum += v * v
        magnitudes.sort()
        val peak = magnitudes[(count * .999).toInt()]
        return 20 * log10(peak / sqrt(sum / count))
    }

    private fun tone(x: ShortArray, from: Int, count: Int, frequency: Double): Double {
        var re = 0.0
        var im = 0.0
        for (n in 0 until count) {
            val w = 2 * PI * frequency * n / rate
            val v = x[from + n].toDouble()
            re += v * cos(w)
            im -= v * sin(w)
        }
        return hypot(re, im) * 2 / count
    }

    /** BS.1770 loudness of a mono signal with the processor's own (verified, R3) K-weighting. */
    private fun lufs(x: ShortArray, from: Int, count: Int): Double {
        val meter = LoudnessMeter(rate, 1)
        var energy = 0.0
        var blocks = 0
        for (n in 0 until from + count) {
            if (meter.add(0, x[n] / 32_768.0) && n >= from) {
                val l = meter.loudness(1)
                if (l > -70) {
                    energy += 10.0.pow((l + 0.691) / 10)
                    blocks++
                }
            }
        }
        return LoudnessMeter.lufs(energy / blocks)
    }

    private fun scaleToLufs(x: ShortArray, target: Double) {
        val factor = 10.0.pow((target - lufs(x, 0, x.size)) / 20)
        for (i in x.indices) x[i] = (x[i] * factor).toInt().coerceIn(-32_768, 32_767).toShort()
    }
}
