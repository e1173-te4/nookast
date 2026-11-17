package ru.e1173.nookast.player

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Building blocks of [SoundProcessor] (audio rework З, DEC-060): biquads from the RBJ audio EQ
 * cookbook, a BS.1770 K-weighted loudness meter and a lookahead peak limiter. Everything here
 * works in place on preallocated arrays: nothing allocates per buffer. Double precision is used
 * for filter state, which keeps low cut-offs (60 Hz at 48 kHz) quiet.
 */
internal class Biquad(private val channels: Int) {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    // State of the first two channels in fields (1.2.6 speed fix: no array access per sample on the
    // hot path); the arrays serve only sources with more channels.
    private var s10 = 0.0
    private var s20 = 0.0
    private var s11 = 0.0
    private var s21 = 0.0
    private val z1 = DoubleArray(channels)
    private val z2 = DoubleArray(channels)

    /** True when the filter is exactly the identity (a peak or shelf at 0 dB): it is skipped. */
    var identity = true
        private set

    fun process(channel: Int, x: Double): Double {
        if (identity) return x
        when (channel) {
            0 -> {
                val y = b0 * x + s10
                s10 = b1 * x - a1 * y + s20
                s20 = b2 * x - a2 * y
                return y
            }
            1 -> {
                val y = b0 * x + s11
                s11 = b1 * x - a1 * y + s21
                s21 = b2 * x - a2 * y
                return y
            }
            else -> {
                val y = b0 * x + z1[channel]
                z1[channel] = b1 * x - a1 * y + z2[channel]
                z2[channel] = b2 * x - a2 * y
                return y
            }
        }
    }

    fun reset() {
        s10 = 0.0
        s20 = 0.0
        s11 = 0.0
        s21 = 0.0
        z1.fill(0.0)
        z2.fill(0.0)
    }

    /** The magnitude response in dB at [frequency]. */
    fun magnitudeDb(sampleRate: Int, frequency: Double): Double {
        val w = 2 * PI * frequency / sampleRate
        val nr = b0 + b1 * cos(w) + b2 * cos(2 * w)
        val ni = -(b1 * sin(w) + b2 * sin(2 * w))
        val dr = 1 + a1 * cos(w) + a2 * cos(2 * w)
        val di = -(a1 * sin(w) + a2 * sin(2 * w))
        return 10 * log10((nr * nr + ni * ni) / (dr * dr + di * di))
    }

    fun setIdentity() {
        if (!identity) reset()
        identity = true
        b0 = 1.0; b1 = 0.0; b2 = 0.0; a1 = 0.0; a2 = 0.0
    }

    fun peaking(sampleRate: Int, frequency: Double, q: Double, gainDb: Double) {
        if (gainDb == 0.0) return setIdentity()
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2 * PI * frequency / sampleRate
        val alpha = sin(w0) / (2 * q)
        val c = cos(w0)
        set(1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a)
    }

    fun lowShelf(sampleRate: Int, frequency: Double, slope: Double, gainDb: Double) {
        if (gainDb == 0.0) return setIdentity()
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2 * PI * frequency / sampleRate
        val c = cos(w0)
        val alpha = sin(w0) / 2 * sqrt((a + 1 / a) * (1 / slope - 1) + 2)
        val root = 2 * sqrt(a) * alpha
        set(
            a * ((a + 1) - (a - 1) * c + root),
            2 * a * ((a - 1) - (a + 1) * c),
            a * ((a + 1) - (a - 1) * c - root),
            (a + 1) + (a - 1) * c + root,
            -2 * ((a - 1) + (a + 1) * c),
            (a + 1) + (a - 1) * c - root,
        )
    }

    fun highShelf(sampleRate: Int, frequency: Double, q: Double, gainDb: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2 * PI * frequency / sampleRate
        val c = cos(w0)
        val alpha = sin(w0) / (2 * q)
        val root = 2 * sqrt(a) * alpha
        set(
            a * ((a + 1) + (a - 1) * c + root),
            -2 * a * ((a - 1) + (a + 1) * c),
            a * ((a + 1) + (a - 1) * c - root),
            (a + 1) - (a - 1) * c + root,
            2 * ((a - 1) - (a + 1) * c),
            (a + 1) - (a - 1) * c - root,
        )
    }

    /** Second-order high-pass; q = 1/√2 is Butterworth. */
    fun highPass(sampleRate: Int, frequency: Double, q: Double = BUTTERWORTH_Q) {
        val w0 = 2 * PI * frequency / sampleRate
        val c = cos(w0)
        val alpha = sin(w0) / (2 * q)
        set((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha)
    }

    /** Second-order low-pass; q = 1/√2 is Butterworth. */
    fun lowPass(sampleRate: Int, frequency: Double, q: Double = BUTTERWORTH_Q) {
        val w0 = 2 * PI * frequency / sampleRate
        val c = cos(w0)
        val alpha = sin(w0) / (2 * q)
        set((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha)
    }

    /** Band-pass with 0 dB at [frequency] (constant peak gain). */
    fun bandPass(sampleRate: Int, frequency: Double, q: Double) {
        val w0 = 2 * PI * frequency / sampleRate
        val alpha = sin(w0) / (2 * q)
        set(alpha, 0.0, -alpha, 1 + alpha, -2 * cos(w0), 1 - alpha)
    }

    private fun set(nb0: Double, nb1: Double, nb2: Double, na0: Double, na1: Double, na2: Double) {
        b0 = nb0 / na0
        b1 = nb1 / na0
        b2 = nb2 / na0
        a1 = na1 / na0
        a2 = na2 / na0
        identity = false
    }

    companion object {
        val BUTTERWORTH_Q = 1 / sqrt(2.0)
    }
}

/**
 * ITU-R BS.1770 loudness of the input: K-weighting (the usual high-shelf and RLB high-pass pair),
 * mean square over 100 ms sub-blocks, momentary over 400 ms and short-term over 3 s.
 */
internal class LoudnessMeter(private val sampleRate: Int, private val channels: Int) {
    // BS.1770 publishes the shelf as pole frequency 1681.97 Hz, Q 0.70718, +4 dB; the RBJ high shelf
    // reaches the same response at f0 / 10^(G/80) ≈ 1499.1 Hz (audit of З: 0.44 dB off at 1.5 kHz before).
    internal val shelf = Biquad(channels).apply {
        highShelf(sampleRate, SHELF_POLE_HZ / 10.0.pow(SHELF_GAIN_DB / 80), SHELF_Q, SHELF_GAIN_DB)
    }
    private val rlb = Biquad(channels).apply { highPass(sampleRate, 38.13547087602444, 0.5003270373238773) }
    private val blockFrames = (sampleRate / 10).coerceAtLeast(1)
    private val blocks = DoubleArray(SHORT_TERM_BLOCKS)
    private var next = 0

    /** Sub-blocks since the last restart, capped at the short-term window. */
    var filled = 0
        private set
    private var frames = 0
    private var sum = 0.0

    /** Called once per frame per channel in order; returns true when a 100 ms sub-block closed. */
    fun add(channel: Int, x: Double): Boolean {
        val k = rlb.process(channel, shelf.process(channel, x))
        sum += k * k
        if (channel != channels - 1) return false
        frames++
        if (frames < blockFrames) return false
        // A one-channel source is measured as dual mono: the phone plays it on both sides, and BS.1770
        // sums channel energies, so it would read 3 LU low (audit of З).
        blocks[next] = sum / frames * if (channels == 1) 2.0 else 1.0
        next = (next + 1) % SHORT_TERM_BLOCKS
        if (filled < SHORT_TERM_BLOCKS) filled++
        frames = 0
        sum = 0.0
        return true
    }

    /** Loudness of the last [count] sub-blocks in LUFS, or [SILENT_LUFS] without them. */
    fun loudness(count: Int): Double {
        val n = minOf(count, filled)
        if (n == 0) return SILENT_LUFS
        var energy = 0.0
        for (i in 1..n) energy += blocks[(next - i + SHORT_TERM_BLOCKS) % SHORT_TERM_BLOCKS]
        return lufs(energy / n)
    }

    /** The window restarts (a seek, a new episode); the filters keep running. */
    fun restartWindow() {
        filled = 0
        frames = 0
        sum = 0.0
    }

    fun reset() {
        shelf.reset()
        rlb.reset()
        restartWindow()
    }

    companion object {
        const val MOMENTARY_BLOCKS = 4
        private const val SHELF_POLE_HZ = 1_681.974450955533
        private const val SHELF_Q = 0.7071752369554196
        private const val SHELF_GAIN_DB = 3.999843853973347
        const val SHORT_TERM_BLOCKS = 30
        const val SILENT_LUFS = -120.0

        fun lufs(meanSquare: Double): Double =
            if (meanSquare <= 1e-12) SILENT_LUFS else -0.691 + 10 * log10(meanSquare)
    }
}

/**
 * The lookahead peak limiter (З4): every frame enters a [delayFrames] line; the gain that leaves
 * with it is the running average, over the line, of the running minimum of the gains each frame
 * needs — so a peak is already met when it leaves, and the gain moves without steps. Release is a
 * one-pole of [releaseMs]. Channels are linked.
 */
internal class PeakLimiter(private val sampleRate: Int, private val channels: Int, val delayFrames: Int, ceilingDb: Double, releaseMs: Double) {
    val ceiling = 10.0.pow(ceilingDb / 20.0)
    private val window = delayFrames + 1
    private val line = DoubleArray(window * channels)
    private var head = 0

    // Monotonic queue of the window's minimum, in ring arrays.
    private val minValues = DoubleArray(window)
    private val minIndex = LongArray(window)
    private var minFirst = 0
    private var minCount = 0
    private var index = 0L

    private val box = DoubleArray(window)
    private var boxSum = window.toDouble()
    private var boxHead = 0
    private var release = 1 - exp(-1.0 / (releaseMs / 1_000.0 * sampleRate))

    /** A new release time; 60 ms in the dense mode of DEC-071, 120 ms otherwise. */
    fun setRelease(releaseMs: Double) {
        release = 1 - exp(-1.0 / (releaseMs / 1_000.0 * sampleRate))
    }

    var gain = 1.0
        private set

    init {
        box.fill(1.0)
    }

    /** Pushes a frame from [input] and writes the frame leaving the line, limited, into [output]. */
    fun process(input: DoubleArray, output: DoubleArray) {
        var peak = 0.0
        for (c in 0 until channels) {
            val v = kotlin.math.abs(input[c])
            if (v > peak) peak = v
        }
        val needed = if (peak > ceiling) ceiling / peak else 1.0
        if (needed == 1.0 && gain == 1.0 && minCount > 0 && minValues[minFirst] == 1.0 && boxSum == window.toDouble()) {
            // Nothing over the ceiling in the whole window and none still releasing (most of a podcast): the
            // queue would end as this one 1.0 and the box stays all ones, so only the indices move.
            val tail = wrap(minFirst + minCount - 1)
            minValues[tail] = 1.0
            minIndex[tail] = index
            minFirst = tail
            minCount = 1
            index++
            boxHead = wrap(boxHead + 1)
            delay(input, output)
            return
        }
        // Sliding minimum over the last `window` frames. Expired entries leave before the new one
        // enters, so the ring never holds more than `window` (independent review of З: a rising
        // gain over a whole window, on loud content below ~50 Hz, overwrote the front).
        while (minCount > 0 && minIndex[minFirst] <= index - window) {
            minFirst = wrap(minFirst + 1)
            minCount--
        }
        while (minCount > 0 && minValues[wrap(minFirst + minCount - 1)] >= needed) minCount--
        val tail = wrap(minFirst + minCount)
        minValues[tail] = needed
        minIndex[tail] = index
        minCount++
        val minimum = minValues[minFirst]
        index++
        // Box average of the minimum over the same window.
        boxSum += minimum - box[boxHead]
        box[boxHead] = minimum
        boxHead = wrap(boxHead + 1)
        if (boxHead == 0) {
            // Re-sum once per window so the running sum cannot drift over hours.
            boxSum = 0.0
            for (v in box) boxSum += v
        }
        val average = boxSum / window
        gain = if (average < gain) average else gain + (average - gain) * release
        // The release only nears 1 and in doubles can stall a hair below it; 1e-12 under is 1 for 16 bits.
        if (average == 1.0 && gain > 1 - 1e-12) gain = 1.0
        delay(input, output)
    }

    /** The delay line: the frame `delayFrames` behind leaves now, times the gain. */
    private fun delay(input: DoubleArray, output: DoubleArray) {
        val base = head * channels
        val outBase = wrap(head + 1) * channels
        for (c in 0 until channels) {
            line[base + c] = input[c]
            output[c] = line[outBase + c] * gain
        }
        head = wrap(head + 1)
    }

    /** i mod window for 0 <= i < 2 window: a compare, not a division, on the per-frame path. */
    private fun wrap(i: Int): Int = if (i >= window) i - window else i

    fun reset() {
        line.fill(0.0)
        head = 0
        minFirst = 0
        minCount = 0
        index = 0L
        box.fill(1.0)
        boxSum = window.toDouble()
        boxHead = 0
        gain = 1.0
    }
}

/** exp is cheaper than pow; the dense path calls this once per frame. */
internal fun dbToGain(db: Double): Double = exp(db * DB_TO_LN)

private const val DB_TO_LN = 0.11512925464970229 // ln 10 / 20

internal fun gainToDb(gain: Double): Double = if (gain <= 1e-9) -180.0 else 20 * log10(gain)

/** The geometric centre and the Q of a band from [low] to [high] Hz, for [Biquad.bandPass]. */
internal fun bandCentre(low: Double, high: Double): Double = sqrt(low * high)

internal fun bandQ(low: Double, high: Double): Double = sqrt(high / low) / (high / low - 1)

/** A one-pole follower of a power with separate attack and release (seconds of time constant). */
internal class Follower(sampleRate: Int, attackMs: Double, releaseMs: Double) {
    private val attack = 1 - exp(-1.0 / (attackMs / 1_000.0 * sampleRate))
    private val release = 1 - exp(-1.0 / (releaseMs / 1_000.0 * sampleRate))
    var value = 0.0
        private set

    fun add(x: Double): Double {
        value += (if (x > value) attack else release) * (x - value)
        return value
    }

    fun reset() {
        value = 0.0
    }
}

/**
 * A 2× oversampled soft clipper (DEC-071): the last [kneeDb] below [ceiling] bend smoothly instead of
 * waiting for the limiter. The clipper runs at twice the rate between two half-band filters, so its
 * harmonics above the original Nyquist are filtered out instead of folding back into the band.
 */
internal class SoftClipper(private val channels: Int, private val ceiling: Double, kneeDb: Double) {
    private val knee = ceiling * 10.0.pow(-kneeDb / 20.0)
    private val room = ceiling - knee

    // The half-band pair in polyphase form (release check of 1.2.6: 47 taps three times per sample made
    // the chain 3.5× slower). Every odd tap but the centre is zero, so the zero-stuffed input and the
    // high-rate output each need only the 24 even taps; the centre tap is a plain 11-sample delay. Each
    // line is stored twice (at i and i + its length): a window is one straight run, no modulo per tap.
    private val even = DoubleArray(PHASE) { HALF_BAND[2 * it] }
    private val centre = HALF_BAND[LATENCY]

    // The input, 2 × PHASE deep: enough to rebuild the high-rate lines when clipping starts again.
    private val xs = Array(channels) { DoubleArray(2 * HISTORY) }
    private val xHead = IntArray(channels)
    private val firsts = Array(channels) { DoubleArray(2 * PHASE) }
    private val seconds = Array(channels) { DoubleArray(2 * PHASE) }
    private val head = IntArray(channels)
    private val live = BooleanArray(channels)
    private val plain = Array(channels) { DoubleArray(LATENCY) }
    private var plainHead = IntArray(channels)

    /**
     * The amount of clipping, 0 (none) to 1. At 0 nothing reaches the knee's bend, so the samples only
     * pass the two filters, whose sole effect is above 18 kHz: the clipper is then a plain delay.
     */
    var amount = 0.0

    fun reset() {
        xs.forEach { it.fill(0.0) }
        xHead.fill(0)
        firsts.forEach { it.fill(0.0) }
        seconds.forEach { it.fill(0.0) }
        head.fill(0)
        live.fill(false)
        plain.forEach { it.fill(0.0) }
        plainHead.fill(0)
    }

    private fun shape(x: Double): Double {
        val a = abs(x)
        if (a <= knee || amount == 0.0) return x
        val bent = knee + room * kotlin.math.tanh((a - knee) / room)
        val y = a + amount * (bent - a)
        return if (x < 0) -y else y
    }

    /** The same [LATENCY] samples of delay without any filtering: the path while nothing is processed. */
    fun bypass(c: Int, x: Double): Double {
        val line = plain[c]
        val at = plainHead[c]
        val y = line[at]
        line[at] = x
        plainHead[c] = if (at + 1 == LATENCY) 0 else at + 1
        return y
    }

    /** One sample of channel [c] in, one out, [LATENCY] samples late. */
    fun process(c: Int, x: Double): Double {
        val delayed = bypass(c, x)
        val xh = if (xHead[c] == 0) HISTORY - 1 else xHead[c] - 1
        xHead[c] = xh
        val line = xs[c]
        val doubled = 2 * x
        line[xh] = doubled
        line[xh + HISTORY] = doubled
        if (amount == 0.0) {
            live[c] = false
            return delayed
        }
        if (!live[c]) {
            // Clipping starts again: the high-rate lines are rebuilt from the input they would have seen
            // (with no bend, as at amount 0), so the switch is seamless.
            live[c] = true
            for (back in PHASE downTo 1) push(c, upFirst(line, xh + back), centre * line[xh + back + CENTRE_DELAY])
        }
        // Up: a zero between the samples, the half-band filter (gain 2 keeps the level).
        push(c, shape(upFirst(line, xh)), shape(centre * line[xh + CENTRE_DELAY]))
        // Down: filter at the high rate and keep every second sample.
        val h = head[c]
        val second = seconds[c]
        var y0 = centre * firsts[c][h + CENTRE_DELAY]
        var y1 = 0.0
        var y2 = 0.0
        var y3 = 0.0
        var j = 0
        while (j < PHASE) {
            y0 += even[j] * second[h + j]
            y1 += even[j + 1] * second[h + j + 1]
            y2 += even[j + 2] * second[h + j + 2]
            y3 += even[j + 3] * second[h + j + 3]
            j += 4
        }
        return (y0 + y1) + (y2 + y3)
    }

    /** The even phase of the up filter at [at] in the input line; four sums run side by side. */
    private fun upFirst(line: DoubleArray, at: Int): Double {
        var s0 = 0.0
        var s1 = 0.0
        var s2 = 0.0
        var s3 = 0.0
        var j = 0
        while (j < PHASE) {
            s0 += even[j] * line[at + j]
            s1 += even[j + 1] * line[at + j + 1]
            s2 += even[j + 2] * line[at + j + 2]
            s3 += even[j + 3] * line[at + j + 3]
            j += 4
        }
        return (s0 + s1) + (s2 + s3)
    }

    /** The two high-rate samples of one input sample into channel [c]'s down line. */
    private fun push(c: Int, a: Double, b: Double) {
        val h = if (head[c] == 0) PHASE - 1 else head[c] - 1
        head[c] = h
        val first = firsts[c]
        first[h] = a
        first[h + PHASE] = a
        val second = seconds[c]
        second[h] = b
        second[h + PHASE] = b
    }

    companion object {
        /** A 47-tap half-band low-pass at a quarter of the high rate (Blackman-windowed sinc). */
        val HALF_BAND: DoubleArray = DoubleArray(47) { i ->
            val m = i - 23
            val sinc = if (m == 0) 0.5 else sin(PI * m / 2) / (PI * m)
            val w = 0.42 - 0.5 * cos(2 * PI * i / 46) + 0.08 * cos(4 * PI * i / 46)
            sinc * w
        }.let { h -> val s = h.sum(); DoubleArray(h.size) { h[it] / s } }

        /** The clipper's delay at the original rate: 23 high-rate samples each way. */
        const val LATENCY = 23

        /** Taps in each phase of the half-band filter, and the centre tap's delay at the original rate. */
        private const val PHASE = 24
        private const val CENTRE_DELAY = 11

        /** Input kept: the rebuild reaches PHASE samples back plus a whole filter window. */
        private const val HISTORY = 2 * PHASE
    }
}
