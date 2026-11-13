package ru.e1173.nookast.player

import kotlin.math.PI
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
    private val z1 = DoubleArray(channels)
    private val z2 = DoubleArray(channels)

    /** True when the filter is exactly the identity (a peak or shelf at 0 dB): it is skipped. */
    var identity = true
        private set

    fun process(channel: Int, x: Double): Double {
        if (identity) return x
        val y = b0 * x + z1[channel]
        z1[channel] = b1 * x - a1 * y + z2[channel]
        z2[channel] = b2 * x - a2 * y
        return y
    }

    fun reset() {
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
internal class PeakLimiter(sampleRate: Int, private val channels: Int, val delayFrames: Int, ceilingDb: Double, releaseMs: Double) {
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
    private val release = 1 - exp(-1.0 / (releaseMs / 1_000.0 * sampleRate))

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
        // Sliding minimum over the last `window` frames. Expired entries leave before the new one
        // enters, so the ring never holds more than `window` (independent review of З: a rising
        // gain over a whole window, on loud content below ~50 Hz, overwrote the front).
        while (minCount > 0 && minIndex[minFirst] <= index - window) {
            minFirst = (minFirst + 1) % window
            minCount--
        }
        while (minCount > 0 && minValues[(minFirst + minCount - 1) % window] >= needed) minCount--
        minValues[(minFirst + minCount) % window] = needed
        minIndex[(minFirst + minCount) % window] = index
        minCount++
        val minimum = minValues[minFirst]
        index++
        // Box average of the minimum over the same window.
        boxSum += minimum - box[boxHead]
        box[boxHead] = minimum
        boxHead = (boxHead + 1) % window
        if (boxHead == 0) {
            // Re-sum once per window so the running sum cannot drift over hours.
            boxSum = 0.0
            for (v in box) boxSum += v
        }
        val average = boxSum / window
        gain = if (average < gain) average else gain + (average - gain) * release
        // The delay line: the frame `delayFrames` behind leaves now.
        val base = head * channels
        val outBase = ((head + 1) % window) * channels
        for (c in 0 until channels) {
            line[base + c] = input[c]
            output[c] = line[outBase + c] * gain
        }
        head = (head + 1) % window
    }

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

internal fun dbToGain(db: Double): Double = 10.0.pow(db / 20.0)

internal fun gainToDb(gain: Double): Double = if (gain <= 1e-9) -180.0 else 20 * log10(gain)
