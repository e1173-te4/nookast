package ru.e1173.nookast.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * What the sound processor does now (audio rework З, DEC-060; second version DEC-071): one immutable
 * snapshot, replaced as a whole from the main thread and read by the playback thread once per 32 frames.
 */
data class SoundParameters(
    /** «Volume boost» of the playing podcast, 0…+30 dB. */
    val volumeDb: Int = 0,
    /** «Mono» of the playing podcast: L = R = (L + R) / 2 for stereo sources. */
    val mono: Boolean = false,
    val evenLoudness: Boolean = false,
    /** «Speech clarity» 0–3: high-pass, a soft mud cut and a presence lift that adapts to the recording. */
    val clarity: Int = 0,
    /** «Voice depth» 0–3: a low shelf at 170 Hz, a cut at 320 Hz and harmonics for small speakers. */
    val depth: Int = 0,
    /** The sole output is the built-in speaker (and not the wide in-vehicle profile). */
    val speakerRoute: Boolean = false,
    /** «On the built-in speaker», 0…+12 dB, only on that route. */
    val speakerTrimDb: Int = 0,
    /** «Compare» is held: the original sound, after a 30 ms crossfade. */
    val compare: Boolean = false,
    /** Changes when a new episode starts: even loudness restarts from [startGainDb]. */
    val episodeKey: String? = null,
    /** The even-loudness gain remembered for the episode's podcast. */
    val startGainDb: Float = 0f,
) {
    /** Anything changes the sound; off, the output is the input, bit for bit. */
    val active: Boolean
        get() = volumeDb > 0 || mono || evenLoudness || clarity > 0 || depth > 0 || (speakerRoute && speakerTrimDb > 0)

    /** Active for a source of [channels]: mono changes nothing on a mono source (review of З). */
    fun activeFor(channels: Int): Boolean =
        volumeDb > 0 || (mono && channels >= 2) || evenLoudness || clarity > 0 || depth > 0 || (speakerRoute && speakerTrimDb > 0)

    fun normalized(): SoundParameters = copy(
        volumeDb = volumeDb.coerceIn(0, MAX_VOLUME_DB),
        clarity = clarity.coerceIn(0, MAX_STEP),
        depth = depth.coerceIn(0, MAX_STEP),
        speakerTrimDb = speakerTrimDb.coerceIn(0, MAX_SPEAKER_TRIM_DB),
    )

    companion object {
        /** +30 dB for very quiet recordings (DEC-071; +24 in 1.2.5). */
        const val MAX_VOLUME_DB = 30
        const val MAX_SPEAKER_TRIM_DB = 12
        const val MAX_STEP = 3
    }
}

/**
 * The app's own sound processing (audio rework З, DEC-060; DEC-071), in Media3's chain right after
 * the voice tee. 16-bit PCM with one or two channels at any rate; any other format passes through and
 * [supported] turns false.
 *
 * Order: mono → tone (high-pass, clarity's mud cut and adaptive presence with its whistle guard,
 * depth's shelf, cut and harmonics, speaker high-pass) → gain (even loudness + volume + speaker trim +
 * the tone's loudness match, at most +36 dB) → density above +12 dB (high-pass, fast compressor,
 * 2× oversampled soft clip) → limiter.
 *
 * Clarity and depth no longer compress (DEC-071: «как будто ты сам немного оглох»): a K-weighted
 * loudness match after the tone keeps clarity 0.5–1.5 LU louder than the original and depth 0–0.5 LU,
 * moving 1 dB/s and standing still in pauses.
 *
 * A change of parameters never reconfigures or flushes: every value ramps (40 ms; route 100 ms;
 * «Compare» crossfades in 30 ms). The lookahead line is always in the path, so switching never
 * jumps: with everything off the output is the input bit for bit, and every frame that goes in comes
 * out, the last ones at the end of the stream.
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class SoundProcessor : BaseAudioProcessor() {
    /** Normalised on the way in, so the playback thread reads it without copying. */
    @Volatile
    var parameters: SoundParameters = SoundParameters()
        set(value) {
            field = value.normalized()
        }

    /** False while the current format cannot be processed («Not available for this episode»). */
    @Volatile
    var supported: Boolean = true
        private set

    /** The limiter's gain reduction in dB, smoothed over 300 ms, for the popover. */
    @Volatile
    var limitingDb: Float = 0f
        private set

    /** The even-loudness gain now, in dB. */
    @Volatile
    var evenGainDb: Float = 0f
        private set

    /** The sample rate being processed, 0 before the first format. */
    @Volatile
    var sampleRate: Int = 0
        private set

    /**
     * Even loudness memory (З5): every 60 s of gated audio once 120 s are reached, the average
     * wanted gain of the episode so far, from the playback thread.
     */
    @Volatile
    var onLoudnessMemory: ((episodeKey: String, averageGainDb: Float) -> Unit)? = null

    /** The same average whenever asked (on stop), or null below 120 s of gated audio. */
    fun loudnessMemoryNow(): Pair<String, Float>? {
        val now = report
        val key = now.key ?: return null
        return if (now.blocks >= MEMORY_MIN_BLOCKS) key to now.average else null
    }

    /** Key, gated time and average published together, so a stop never pairs one episode's key with another's average. */
    private class Report(val key: String?, val blocks: Int, val average: Float)

    @Volatile private var report = Report(null, 0, 0f)

    /** From an item change until the next episode starts here, nothing is counted into the memory. */
    @Volatile private var memoryFrozen = false

    /** The service saw the item change: the frames still processed belong to the next episode. */
    fun freezeMemory() {
        memoryFrozen = true
    }

    /** Tests only: the density stage off, as in 1.2.5. */
    internal var densityEnabled = true

    /** Tests only: the tone's fixed filters alone (no adaptation, loudness match, harmonics or whistle guard). */
    internal var analytic = false

    internal var configures = 0
        private set
    internal var flushes = 0
        private set

    /** Samples the final clamp had to catch; the limiter is built so that it never does. */
    internal var clampedSamples = 0L
        private set

    private var rate = 0
    private var channels = 0
    private var delay = 0
    private lateinit var raw: ShortArray
    private var rawHead = 0
    private var held = 0

    private lateinit var limiter: PeakLimiter
    private lateinit var meter: LoudnessMeter
    private lateinit var preTone: LoudnessMeter
    private lateinit var postTone: LoudnessMeter
    private lateinit var highPass: Biquad
    private lateinit var mud: Biquad
    private lateinit var presence: Biquad
    private lateinit var whistle: Biquad
    private lateinit var shelf: Biquad
    private lateinit var cut320: Biquad
    private lateinit var harmonicSource: Biquad
    private lateinit var harmonicBand: Biquad
    private lateinit var speakerHighPass: Biquad
    private lateinit var denseHighPass: Biquad
    private lateinit var clipper: SoftClipper
    private lateinit var adaptHigh: Biquad
    private lateinit var adaptLow: Biquad
    private lateinit var whistleLevel: Follower
    private lateinit var wholeLevel: Follower
    private lateinit var harmonicLevel: Follower
    private lateinit var denseLevel: Follower

    private val frame = DoubleArray(2)
    private val limited = DoubleArray(2)

    // Ramped values, advanced once per block.
    private val wet = Ramp(0.0)
    private val monoMix = Ramp(0.0)
    private val hpMix = Ramp(0.0)
    private val hpHz = Ramp(CLARITY_HP_HZ)
    private val mudDb = Ramp(0.0)
    private val presenceDb = Ramp(0.0)
    private val shelfDb = Ramp(0.0)
    private val cut320Db = Ramp(0.0)
    private val harmonicMix = Ramp(0.0)
    private val speakerHpMix = Ramp(0.0)
    private val volume = Ramp(0.0)
    private val trim = Ramp(0.0)
    private val even = Ramp(0.0)
    private val match = Ramp(0.0)
    private val density = Ramp(0.0)
    private val toneRamps = arrayOf(hpHz, mudDb, shelfDb, cut320Db)
    private val chainRamps = arrayOf(monoMix, hpMix, hpHz, mudDb, presenceDb, shelfDb, cut320Db, harmonicMix, speakerHpMix, volume, trim, even, match, density)

    private var blockLeft = 0
    private var gainFrom = 1.0
    private var gainTo = 1.0
    private var gainStep = 0.0
    private var gainNow = 1.0
    private var processing = false
    private var lastCompare = false
    private var episodeKey: String? = null

    // Even loudness (З5).
    private var evenTargetDb = 0.0
    private var evenMoving = false
    private var integratedEnergy = 0.0
    private var integratedBlocks = 0
    private var gatedEnergy = 0.0
    private var gatedLevelBlocks = 0
    private var wantedSum = 0.0
    private var gatedBlocks = 0

    // Clarity's adaptation (DEC-071): the 2–6 kHz to 250–1000 Hz balance of speech over 3 s.
    private val adaptHighBlocks = DoubleArray(LoudnessMeter.SHORT_TERM_BLOCKS)
    private val adaptLowBlocks = DoubleArray(LoudnessMeter.SHORT_TERM_BLOCKS)
    private var adaptNext = 0
    private var adaptFilled = 0
    private var adaptHighSum = 0.0
    private var adaptLowSum = 0.0
    private var presenceAdaptDb = 0.0

    internal val presenceAdaptNow: Double get() = presenceAdaptDb
    internal var balanceNow = 0.0
        private set

    // The tone's loudness match.
    private var matchTargetDb = 0.0
    private var preRunningEnergy = 0.0
    private var preRunningBlocks = 0

    // Density (DEC-071).
    private var denseReductionDb = 0.0

    /** The presence lift as a gain, once per block (the whistle guard divides it per frame). */
    private var presenceLift = 1.0
    private var denseAttack = 0.0
    private var denseRelease = 0.0

    /** The total gain now in dB. */
    private var gainDbNow = 0.0
    private var limitingSmoothing = 0.0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        configures++
        val fits = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT &&
            inputAudioFormat.channelCount in 1..2 &&
            inputAudioFormat.sampleRate > 0
        supported = fits
        return if (fits) inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET
    }

    override fun onFlush() {
        flushes++
        val format = inputAudioFormat
        // Unsupported formats pass through untouched: nothing to build for them (review of З).
        if (format == AudioProcessor.AudioFormat.NOT_SET || !supported) return
        if (format.sampleRate != rate || format.channelCount != channels) allocate(format.sampleRate, format.channelCount)
        // A seek: the held frames belong to the old position; the gain stays, the windows restart.
        raw.fill(0)
        rawHead = 0
        held = 0
        limiter.reset()
        resetFilters()
        meter.restartWindow()
        preTone.restartWindow()
        postTone.restartWindow()
        evenMoving = false
        blockLeft = 0
        // Nothing is heard yet after a start or a seek: begin at the wanted mix instead of fading
        // in from the original, which may itself be over the limiter's ceiling.
        val p = parameters
        wet.snap(if (p.activeFor(format.channelCount) && !p.compare) 1.0 else 0.0)
        lastCompare = p.compare
        processing = false
    }

    override fun onReset() {
        rate = 0
        channels = 0
        processing = false
        wet.snap(0.0)
        episodeKey = null
    }

    private fun allocate(sampleRate: Int, channelCount: Int) {
        rate = sampleRate
        this.sampleRate = sampleRate
        channels = channelCount
        val limiterDelay = (sampleRate * LOOKAHEAD_MS / 1_000.0).roundToInt().coerceAtLeast(1)
        // The original waits as long as the processed sound: the limiter's lookahead and the clipper's filters.
        delay = limiterDelay + SoftClipper.LATENCY
        raw = ShortArray(delay * channels)
        limiter = PeakLimiter(sampleRate, channels, limiterDelay, CEILING_DB, RELEASE_MS)
        meter = LoudnessMeter(sampleRate, channels)
        preTone = LoudnessMeter(sampleRate, channels)
        postTone = LoudnessMeter(sampleRate, channels)
        highPass = Biquad(channels)
        mud = Biquad(channels)
        presence = Biquad(channels).apply { bandPass(sampleRate, PRESENCE_HZ, PRESENCE_Q) }
        whistle = Biquad(channels).apply { bandPass(sampleRate, bandCentre(5_500.0, 9_000.0), bandQ(5_500.0, 9_000.0)) }
        shelf = Biquad(channels)
        cut320 = Biquad(channels)
        harmonicSource = Biquad(channels).apply { bandPass(sampleRate, bandCentre(70.0, 180.0), bandQ(70.0, 180.0)) }
        harmonicBand = Biquad(channels).apply { bandPass(sampleRate, bandCentre(140.0, 540.0), bandQ(140.0, 540.0)) }
        speakerHighPass = Biquad(channels).apply { highPass(sampleRate, SPEAKER_HP_HZ) }
        denseHighPass = Biquad(channels).apply { highPass(sampleRate, DENSE_HP_HZ) }
        clipper = SoftClipper(channels, dbToGain(CEILING_DB), CLIP_KNEE_DB)
        adaptHigh = Biquad(1).apply { bandPass(sampleRate, bandCentre(2_000.0, 6_000.0), bandQ(2_000.0, 6_000.0)) }
        adaptLow = Biquad(1).apply { bandPass(sampleRate, bandCentre(250.0, 1_000.0), bandQ(250.0, 1_000.0)) }
        whistleLevel = Follower(sampleRate, WHISTLE_ATTACK_MS, WHISTLE_RELEASE_MS)
        wholeLevel = Follower(sampleRate, WHISTLE_ATTACK_MS, WHISTLE_RELEASE_MS)
        harmonicLevel = Follower(sampleRate, 5.0, 60.0)
        denseLevel = Follower(sampleRate, DENSE_ATTACK_MS, DENSE_RELEASE_MS)
        denseAttack = 1 - exp(-1.0 / (DENSE_ATTACK_MS / 1_000.0 * sampleRate))
        denseRelease = 1 - exp(-1.0 / (DENSE_RELEASE_MS / 1_000.0 * sampleRate))
        limitingSmoothing = 1 - exp(-BLOCK.toDouble() / (LIMITING_DISPLAY_MS / 1_000.0 * sampleRate))
        updateFilters()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val bytesPerFrame = 2 * channels
        val frames = inputBuffer.remaining() / bytesPerFrame
        val emitted = (frames - (delay - held)).coerceAtLeast(0)
        val output = replaceOutputBuffer(emitted * bytesPerFrame)
        inputBuffer.order(ByteOrder.nativeOrder())
        for (i in 0 until frames) {
            for (c in 0 until channels) input[c] = inputBuffer.short
            val emit = held == delay
            step(output, emit)
            if (!emit) held++
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onQueueEndOfStream() {
        // The last frames still in the lookahead line come out: frames out = frames in. Silence
        // pushes them through; when fewer than the line were ever written, the first pushes only
        // fill it, so the original and the limited path stay aligned.
        if (rate == 0 || held == 0) return
        val output = replaceOutputBuffer(held * 2 * channels)
        val skip = delay - held
        input.fill(0)
        for (i in 0 until delay) step(output, emit = i >= skip)
        held = 0
        output.flip()
    }

    /** One frame from [input] in; when [emit], the frame leaving the line goes to [output]. */
    private fun step(output: ByteBuffer, emit: Boolean) {
        if (blockLeft == 0) startBlock()
        blockLeft--
        for (c in 0 until channels) frame[c] = input[c] / FULL_SCALE
        if (processing) {
            process()
            gainNow += gainStep
        } else {
            for (c in 0 until channels) frame[c] = clipper.bypass(c, frame[c])
            limiter.process(frame, limited)
        }
        val w = wet.value
        val base = rawHead * channels
        for (c in 0 until channels) {
            // The original frame leaving the line now, before this one takes its place.
            val original = raw[base + c]
            raw[base + c] = input[c]
            if (!emit) continue
            if (w == 0.0) {
                output.putShort(original)
            } else {
                var y = limited[c]
                if (abs(y) > limiter.ceiling + CLAMP_TOLERANCE) {
                    clampedSamples++
                    y = if (y > 0) limiter.ceiling else -limiter.ceiling
                }
                val mixed = original + w * (y * FULL_SCALE - original)
                output.putShort(mixed.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }
        }
        rawHead = if (rawHead + 1 == delay) 0 else rawHead + 1
    }

    private val input = ShortArray(2)

    private fun process() {
        // Loudness of the input (З5), before anything changes it.
        var closed = false
        for (c in 0 until channels) if (meter.add(c, frame[c])) closed = true
        if (closed) evenLoudnessBlock()
        // Mono.
        if (channels == 2 && monoMix.value > 0.0) {
            val m = monoMix.value
            val average = (frame[0] + frame[1]) / 2
            frame[0] += m * (average - frame[0])
            frame[1] += m * (average - frame[1])
        }
        // The balance clarity adapts to, and the loudness before the tone.
        var sum = 0.0
        for (c in 0 until channels) sum += frame[c]
        val m = sum / channels
        val hi = adaptHigh.process(0, m)
        val lo = adaptLow.process(0, m)
        adaptHighSum += hi * hi
        adaptLowSum += lo * lo
        var toneClosed = false
        for (c in 0 until channels) if (preTone.add(c, frame[c])) toneClosed = true
        // The whistle guard: when 5.5–9 kHz stands out, the presence lift steps back fast (1 ms, 50 ms).
        var whistlePower = 0.0
        var wholePower = 0.0
        for (c in 0 until channels) {
            val w = whistle.process(c, frame[c])
            whistlePower += w * w
            wholePower += frame[c] * frame[c]
        }
        // In amplitude, without a logarithm per frame: the guard is √(whistle / whole) over the threshold,
        // held to 1…WHISTLE_MAX_DB, and the lift never goes below none.
        val presenceGain = if (presenceLift > 1.0 && !analytic) {
            val ratio = (whistleLevel.add(whistlePower) + 1e-15) / (wholeLevel.add(wholePower) + 1e-15)
            val guard = sqrt(ratio * WHISTLE_OVER_POWER).coerceIn(1.0, WHISTLE_MAX_GAIN)
            (presenceLift / guard).coerceAtLeast(1.0) - 1
        } else {
            presenceLift - 1
        }
        // Harmonics for small speakers: the fundamental's band, shaped, back into 140–540 Hz.
        var harmonicPower = 0.0
        val level = sqrt(harmonicLevel.value * 2 + 1e-12)
        val toLevel = 1 / level
        // Tone.
        for (c in 0 until channels) {
            var x = frame[c]
            val hp = hpMix.value
            val filtered = highPass.process(c, x)
            if (hp > 0.0) x += hp * (filtered - x)
            x = mud.process(c, x)
            val band = presence.process(c, x)
            if (presenceGain != 0.0) x += presenceGain * band
            x = shelf.process(c, x)
            x = cut320.process(c, x)
            val source = harmonicSource.process(c, x)
            harmonicPower += source * source
            val u = (source * toLevel).coerceIn(-3.0, 3.0)
            // u² makes the second harmonic at the fundamental's own level (2u²: cos 2ωt at 1), u³ a little of the third.
            val shaped = harmonicBand.process(c, (2 * u * u + u * u * u * HARMONIC_THIRD) * level)
            if (harmonicMix.value > 0.0) x += harmonicMix.value * shaped
            val sp = speakerHpMix.value
            val speaker = speakerHighPass.process(c, x)
            if (sp > 0.0) x += sp * (speaker - x)
            frame[c] = x
        }
        harmonicLevel.add(harmonicPower / channels)
        for (c in 0 until channels) if (postTone.add(c, frame[c])) toneClosed = true
        if (toneClosed) toneBlock()
        for (c in 0 until channels) frame[c] *= gainNow
        dense()
        limiter.process(frame, limited)
    }

    /** Above +12 dB (DEC-071): a high-pass frees headroom, a fast compressor packs, a soft clip rounds the peaks. */
    private fun dense() {
        val k = density.value
        if (k <= 0.0) {
            // No density, no bend: the clipper is a plain delay of the same length (1.2.6 speed fix).
            clipper.amount = 0.0
            for (c in 0 until channels) frame[c] = clipper.process(c, frame[c])
            return
        }
        var power = 0.0
        for (c in 0 until channels) {
            val hp = denseHighPass.process(c, frame[c])
            frame[c] += k * (hp - frame[c])
            power += frame[c] * frame[c]
        }
        val levelDb = 10 * log10(denseLevel.add(power / channels) + 1e-15)
        val slope = k * (1 - 1 / DENSE_RATIO)
        val over = levelDb - DENSE_THRESHOLD_DB
        val target = when {
            2 * over < -DENSE_KNEE_DB -> 0.0
            2 * abs(over) <= DENSE_KNEE_DB -> -slope * (over + DENSE_KNEE_DB / 2) * (over + DENSE_KNEE_DB / 2) / (2 * DENSE_KNEE_DB)
            else -> -slope * over
        }
        denseReductionDb += (if (target < denseReductionDb) denseAttack else denseRelease) * (target - denseReductionDb)
        // Make-up: what the compressor took from the loud parts comes back as density; the limiter keeps the peaks.
        val g = dbToGain(denseReductionDb + slope * (CEILING_DB - DENSE_THRESHOLD_DB) * DENSE_MAKEUP_SHARE)
        clipper.amount = k
        for (c in 0 until channels) frame[c] = clipper.process(c, frame[c] * g)
    }

    /** Every 100 ms: clarity's balance over 3 s of speech and the tone's loudness match. */
    private fun toneBlock() {
        if (analytic) {
            adaptHighSum = 0.0
            adaptLowSum = 0.0
            return
        }
        val pre = preTone.loudness(LoudnessMeter.MOMENTARY_BLOCKS)
        val running = if (preRunningBlocks > 0) LoudnessMeter.lufs(preRunningEnergy / preRunningBlocks) else pre
        val pause = pre < PAUSE_LUFS || pre < running - PAUSE_UNDER_DB
        if (!pause) {
            preRunningEnergy += Math.pow(10.0, (pre + 0.691) / 10)
            preRunningBlocks++
            adaptHighBlocks[adaptNext] = adaptHighSum
            adaptLowBlocks[adaptNext] = adaptLowSum
            adaptNext = (adaptNext + 1) % adaptHighBlocks.size
            if (adaptFilled < adaptHighBlocks.size) adaptFilled++
        }
        adaptHighSum = 0.0
        adaptLowSum = 0.0
        if (pause) return
        // A dull recording gets up to +4 dB more presence, a bright one up to 3 dB less (1 dB/s).
        if (adaptFilled >= ADAPT_MIN_BLOCKS) {
            var high = 0.0
            var low = 0.0
            for (i in 0 until adaptFilled) {
                high += adaptHighBlocks[i]
                low += adaptLowBlocks[i]
            }
            val balance = 10 * log10((high + 1e-15) / (low + 1e-15))
            balanceNow = balance
            val wanted = (BALANCE_REFERENCE_DB - balance).coerceIn(-PRESENCE_LESS_DB, PRESENCE_MORE_DB)
            presenceAdaptDb += (wanted - presenceAdaptDb).coerceIn(-MATCH_STEP_DB, MATCH_STEP_DB)
        }
        // The loudness match: the tone leaves clarity 0.5–1.5 LU louder, depth 0–0.5 LU (1 dB/s).
        val filled = minOf(preTone.filled, postTone.filled)
        if (filled >= ADAPT_MIN_BLOCKS) {
            val p = parameters
            val wantedOffset = when {
                p.clarity > 0 -> CLARITY_LOUDER_LU
                p.depth > 0 -> DEPTH_LOUDER_LU
                else -> 0.0
            }
            val change = postTone.loudness(LoudnessMeter.SHORT_TERM_BLOCKS) - preTone.loudness(LoudnessMeter.SHORT_TERM_BLOCKS)
            val wanted = if (p.clarity > 0 || p.depth > 0) (wantedOffset - change).coerceIn(-MATCH_RANGE_DB, MATCH_RANGE_DB) else 0.0
            matchTargetDb += (wanted - matchTargetDb).coerceIn(-MATCH_STEP_DB, MATCH_STEP_DB)
        }
    }

    /** Every 100 ms of input (З5): the wanted gain, its dead band and slew, and the memory. */
    private fun evenLoudnessBlock() {
        val momentary = meter.loudness(LoudnessMeter.MOMENTARY_BLOCKS)
        if (momentary > ABSOLUTE_GATE_LUFS) {
            val energy = Math.pow(10.0, (momentary + 0.691) / 10)
            integratedEnergy += energy
            integratedBlocks++
            // BS.1770's relative gate, 10 LU under the absolute-gated level: quiet stretches (a guest, a
            // fade) do not pull the episode's own level down (audit of З, R1).
            if (momentary >= LoudnessMeter.lufs(integratedEnergy / integratedBlocks) - RELATIVE_GATE_LU) {
                gatedEnergy += energy
                gatedLevelBlocks++
            }
        }
        if (!parameters.evenLoudness) return
        val running = if (gatedLevelBlocks > 0) LoudnessMeter.lufs(gatedEnergy / gatedLevelBlocks) else momentary
        // Fast down (audit of З, R1 b): when the output runs 6 LU hot — the host back after a quiet
        // guest — the gain falls up to 1.2 dB per 100 ms (12 dB/s), before any hold or dead band.
        var fell = false
        if (meter.filled >= LoudnessMeter.MOMENTARY_BLOCKS) {
            val excess = momentary + evenTargetDb - (TARGET_LUFS + FAST_DOWN_OVER_LU)
            if (excess > 0) {
                evenTargetDb = (evenTargetDb - minOf(excess, FAST_DOWN_STEP_DB)).coerceAtLeast(MIN_EVEN_DB)
                evenMoving = true
                fell = true
            }
        }
        val hold = meter.filled < LoudnessMeter.SHORT_TERM_BLOCKS ||
            momentary < PAUSE_LUFS || momentary < running - PAUSE_UNDER_DB
        if (hold) return
        var wanted = (TARGET_LUFS - meter.loudness(LoudnessMeter.SHORT_TERM_BLOCKS)).coerceIn(MIN_EVEN_DB, MAX_EVEN_DB)
        // At most 8 dB above what the episode as a whole needs (R1 a): a quiet stretch is not pulled
        // up so far that the return of the normal level blasts.
        if (gatedLevelBlocks >= LoudnessMeter.SHORT_TERM_BLOCKS) {
            wanted = minOf(wanted, TARGET_LUFS - running + ABOVE_BASELINE_DB)
        }
        if (!memoryFrozen) {
            wantedSum += wanted
            gatedBlocks++
        }
        val difference = wanted - evenTargetDb
        if (!evenMoving && abs(difference) > DEAD_BAND_DB) evenMoving = true
        if (evenMoving && !fell) {
            val limit = if (difference > 0) SLEW_UP_DB_PER_S / 10 else SLEW_DOWN_DB_PER_S / 10
            evenTargetDb += difference.coerceIn(-limit, limit)
            if (abs(difference) <= SETTLED_DB) evenMoving = false
        }
        if (memoryFrozen || gatedBlocks == 0) return
        val key = episodeKey
        // Published once a second (and at each memory point): one small object, not per buffer.
        val memoryPoint = key != null && gatedBlocks >= MEMORY_MIN_BLOCKS && gatedBlocks % MEMORY_EVERY_BLOCKS == 0
        if (gatedBlocks % REPORT_EVERY_BLOCKS == 0 || memoryPoint) {
            val average = (wantedSum / gatedBlocks).toFloat()
            report = Report(key, gatedBlocks, average)
            if (memoryPoint && key != null) onLoudnessMemory?.invoke(key, average)
        }
    }

    /** Once per [BLOCK] frames: read the snapshot, aim and advance the ramps, new coefficients. */
    private fun startBlock() {
        blockLeft = BLOCK
        val p = parameters
        if (p.episodeKey != episodeKey) startEpisode(p)
        val rampFrames = rampFrames(RAMP_MS)
        val routeFrames = rampFrames(ROUTE_RAMP_MS)
        val compareChanged = p.compare != lastCompare
        lastCompare = p.compare
        val active = p.activeFor(channels)
        wet.aim(if (active && !p.compare) 1.0 else 0.0, rampFrames(if (compareChanged || p.compare) COMPARE_MS else RAMP_MS))
        // Before the first frame has left the line since a start or seek nothing was heard: no fade.
        if (held < delay) wet.snap(wet.target)
        val stereo = channels == 2
        monoMix.aim(if (p.mono && stereo) 1.0 else 0.0, rampFrames)
        hpMix.aim(if (p.clarity > 0 || p.depth > 0) 1.0 else 0.0, rampFrames)
        hpHz.aim(if (p.depth > 0) DEPTH_HP_HZ else CLARITY_HP_HZ, rampFrames)
        // A bright recording is rarely muddy: as the presence steps back, the mud cut eases with it.
        val easing = if (presenceAdaptDb < 0.0) 1 + presenceAdaptDb / PRESENCE_LESS_DB else 1.0
        mudDb.aim(MUD_DB[p.clarity] * easing, rampFrames)
        presenceDb.aim(if (p.clarity > 0) (PRESENCE_DB[p.clarity] + presenceAdaptDb).coerceAtLeast(0.0) else 0.0, rampFrames)
        // On the phone's speaker the shelf cannot be heard, and the cut that balances it has nothing to balance:
        // the harmonics carry the depth there, 6 dB stronger.
        cut320Db.aim(if (p.speakerRoute) 0.0 else CUT_320_DB[p.depth], routeFrames)
        shelfDb.aim(if (p.speakerRoute) 0.0 else SHELF_DB[p.depth], routeFrames)
        val harmonics = HARMONIC_DB[p.depth] + if (p.speakerRoute) SPEAKER_HARMONIC_DB else 0.0
        harmonicMix.aim(if (p.depth > 0 && !analytic) dbToGain(harmonics) else 0.0, rampFrames)
        volume.aim(p.volumeDb.toDouble(), rampFrames)
        trim.aim(if (p.speakerRoute) p.speakerTrimDb.toDouble() else 0.0, routeFrames)
        even.aim(if (p.evenLoudness) evenTargetDb else 0.0, rampFrames)
        match.aim(if (p.clarity > 0 || p.depth > 0) matchTargetDb else 0.0, rampFrames)
        val speakerGain = p.volumeDb + (if (p.speakerRoute) p.speakerTrimDb else 0) + maxOf(evenTargetDb, 0.0)
        speakerHpMix.aim(if (p.speakerRoute && speakerGain > 0.0) 1.0 else 0.0, routeFrames)
        // Density follows the listener's own boost only: even loudness has its target and must not overshoot it.
        val boost = p.volumeDb + (if (p.speakerRoute) p.speakerTrimDb else 0)
        density.aim(if (densityEnabled) ((boost - DENSE_FROM_DB) / (SoundParameters.MAX_VOLUME_DB - DENSE_FROM_DB)).coerceIn(0.0, 1.0) else 0.0, rampFrames)

        val wasProcessing = processing
        wet.advance()
        // While anything is on the chain keeps running, also under «Compare», so letting go is seamless.
        processing = active || wet.value > 0.0
        if (processing && !wasProcessing) {
            // Coming out of rest: the chain and its windows start clean under the crossfade; what they held
            // was heard minutes ago (audit of З, R6).
            resetFilters()
            restartWindows()
            for (ramp in chainRamps) ramp.snap(ramp.target)
            updateFilters()
            gainNow = totalGain()
        }
        var moved = false
        for (ramp in toneRamps) if (ramp.advance()) moved = true
        if (moved) updateFilters()
        monoMix.advance()
        hpMix.advance()
        presenceDb.advance()
        presenceLift = dbToGain(presenceDb.value.coerceAtLeast(0.0))
        harmonicMix.advance()
        speakerHpMix.advance()
        volume.advance()
        trim.advance()
        even.advance()
        match.advance()
        if (density.advance()) limiter.setRelease(RELEASE_MS - (RELEASE_MS - DENSE_LIMITER_RELEASE_MS) * density.value)
        evenGainDb = even.value.toFloat()
        val next = totalGain()
        gainDbNow = gainToDb(next)
        gainStep = (next - gainNow) / BLOCK
        // Limiting shown in the popover, smoothed over 300 ms.
        limitingDb += ((-gainToDb(limiter.gain)).toFloat() - limitingDb) * limitingSmoothing.toFloat()
    }

    private fun startEpisode(p: SoundParameters) {
        episodeKey = p.episodeKey
        evenTargetDb = if (p.evenLoudness) p.startGainDb.toDouble().coerceIn(MIN_EVEN_DB, MAX_EVEN_DB) else 0.0
        evenMoving = false
        integratedEnergy = 0.0
        integratedBlocks = 0
        gatedEnergy = 0.0
        gatedLevelBlocks = 0
        wantedSum = 0.0
        gatedBlocks = 0
        report = Report(p.episodeKey, 0, 0f)
        memoryFrozen = false
        restartWindows()
    }

    /** The loudness windows and clarity's balance start again. */
    private fun restartWindows() {
        if (rate != 0) {
            meter.restartWindow()
            preTone.restartWindow()
            postTone.restartWindow()
        }
        adaptFilled = 0
        adaptNext = 0
        adaptHighSum = 0.0
        adaptLowSum = 0.0
        preRunningEnergy = 0.0
        preRunningBlocks = 0
    }

    private fun totalGain(): Double = dbToGain(minOf(volume.value + trim.value + even.value + match.value, MAX_TOTAL_GAIN_DB))

    private fun rampFrames(ms: Double): Int = (rate * ms / 1_000.0).roundToInt().coerceAtLeast(1)

    private fun updateFilters() {
        if (rate == 0) return
        highPass.highPass(rate, hpHz.value)
        mud.peaking(rate, MUD_HZ, MUD_Q, mudDb.value)
        shelf.lowShelf(rate, SHELF_HZ, 0.8, shelfDb.value)
        cut320.peaking(rate, CUT_320_HZ, CUT_320_Q, cut320Db.value)
    }

    private fun resetFilters() {
        if (rate == 0) return
        highPass.reset()
        mud.reset()
        presence.reset()
        whistle.reset()
        shelf.reset()
        cut320.reset()
        harmonicSource.reset()
        harmonicBand.reset()
        speakerHighPass.reset()
        denseHighPass.reset()
        clipper.reset()
        adaptHigh.reset()
        adaptLow.reset()
        whistleLevel.reset()
        wholeLevel.reset()
        harmonicLevel.reset()
        denseLevel.reset()
        denseReductionDb = 0.0
    }

    /** A value that moves to its target in a straight line over a given number of frames. */
    private class Ramp(initial: Double) {
        var value = initial
            private set
        var target = initial
            private set
        private var step = 0.0

        fun aim(newTarget: Double, frames: Int) {
            if (newTarget == target) return
            target = newTarget
            step = abs(target - value) / maxOf(1, frames / BLOCK)
        }

        fun snap(to: Double) {
            value = to
            target = to
        }

        fun advance(): Boolean {
            if (value == target) return false
            value = if (abs(target - value) <= step) target else value + if (target > value) step else -step
            return true
        }
    }

    companion object {
        const val LOOKAHEAD_MS = 5.0
        const val CEILING_DB = -1.0
        const val RELEASE_MS = 120.0
        const val BLOCK = 32
        const val RAMP_MS = 40.0
        const val ROUTE_RAMP_MS = 100.0
        const val COMPARE_MS = 30.0

        /** The volume slider's +30 dB with even loudness and the speaker trim (DEC-071; +30 in 1.2.5). */
        const val MAX_TOTAL_GAIN_DB = 36.0
        const val FULL_SCALE = 32_768.0
        private const val CLAMP_TOLERANCE = 1e-9
        private const val LIMITING_DISPLAY_MS = 300.0

        const val TARGET_LUFS = -16.0
        const val MIN_EVEN_DB = -12.0
        const val MAX_EVEN_DB = 20.0
        const val DEAD_BAND_DB = 2.0
        private const val SETTLED_DB = 0.25
        const val SLEW_UP_DB_PER_S = 1.5
        const val SLEW_DOWN_DB_PER_S = 4.0
        const val PAUSE_LUFS = -50.0
        const val PAUSE_UNDER_DB = 20.0
        const val FAST_DOWN_OVER_LU = 6.0
        const val FAST_DOWN_STEP_DB = 1.2
        const val ABOVE_BASELINE_DB = 8.0
        private const val RELATIVE_GATE_LU = 10.0
        private const val ABSOLUTE_GATE_LUFS = -70.0
        const val MEMORY_MIN_BLOCKS = 1_200
        const val MEMORY_EVERY_BLOCKS = 600
        private const val REPORT_EVERY_BLOCKS = 10

        // Clarity (DEC-071): no compressor; a softer mud cut and a presence lift that adapts.
        const val CLARITY_HP_HZ = 85.0
        const val DEPTH_HP_HZ = 50.0
        const val SPEAKER_HP_HZ = 140.0
        const val MUD_HZ = 250.0
        const val MUD_Q = 1.4
        val MUD_DB = doubleArrayOf(0.0, -1.0, -1.5, -2.0)
        const val PRESENCE_HZ = 3_200.0
        const val PRESENCE_Q = 0.7
        val PRESENCE_DB = doubleArrayOf(0.0, 2.5, 4.0, 5.5)

        /** The 2–6 kHz to 250–1000 Hz balance of ordinary speech; a recording below it is dull. */
        const val BALANCE_REFERENCE_DB = -4.5
        const val PRESENCE_MORE_DB = 4.0
        const val PRESENCE_LESS_DB = 3.0
        const val WHISTLE_OVER_DB = -9.0
        const val WHISTLE_MAX_DB = 4.0
        private const val WHISTLE_ATTACK_MS = 1.0
        private const val WHISTLE_RELEASE_MS = 50.0
        private val WHISTLE_OVER_POWER = 10.0.pow(-WHISTLE_OVER_DB / 10)
        private val WHISTLE_MAX_GAIN = dbToGain(WHISTLE_MAX_DB)
        const val CLARITY_LOUDER_LU = 1.0
        const val DEPTH_LOUDER_LU = 0.25
        private const val MATCH_STEP_DB = 0.1
        private const val MATCH_RANGE_DB = 12.0
        private const val ADAPT_MIN_BLOCKS = 10

        // Depth (DEC-071).
        const val SHELF_HZ = 260.0
        const val SPEAKER_HARMONIC_DB = 14.0
        val SHELF_DB = doubleArrayOf(0.0, 2.5, 4.5, 7.0)
        const val CUT_320_HZ = 320.0
        const val CUT_320_Q = 1.1
        val CUT_320_DB = doubleArrayOf(0.0, -1.5, -2.5, -3.5)
        val HARMONIC_DB = doubleArrayOf(-120.0, -16.0, -12.0, -9.0)
        private const val HARMONIC_THIRD = .12

        // Density above +12 dB (DEC-071).
        const val DENSE_FROM_DB = 12.0
        const val DENSE_HP_HZ = 90.0
        private const val DENSE_ATTACK_MS = 2.5
        private const val DENSE_RELEASE_MS = 75.0
        const val DENSE_RATIO = 4.0
        const val DENSE_THRESHOLD_DB = -14.0
        const val DENSE_KNEE_DB = 6.0
        const val CLIP_KNEE_DB = 2.5
        const val DENSE_LIMITER_RELEASE_MS = 60.0
        const val DENSE_MAKEUP_SHARE = 1.0
    }
}
