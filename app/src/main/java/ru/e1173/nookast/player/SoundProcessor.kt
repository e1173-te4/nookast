package ru.e1173.nookast.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * What the sound processor does now (audio rework З, DEC-060): one immutable snapshot, replaced
 * as a whole from the main thread and read by the playback thread once per 32 frames.
 */
data class SoundParameters(
    /** «Volume boost» of the playing podcast, 0…+24 dB. */
    val volumeDb: Int = 0,
    /** «Mono» of the playing podcast: L = R = (L + R) / 2 for stereo sources. */
    val mono: Boolean = false,
    val evenLoudness: Boolean = false,
    /** «Speech clarity» 0–3: high-pass, 300 Hz cut, 3 kHz lift and a gentle compressor. */
    val clarity: Int = 0,
    /** «Voice depth» 0–3: a low shelf at 110 Hz and a cut at 280 Hz. */
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
        const val MAX_VOLUME_DB = 24
        const val MAX_SPEAKER_TRIM_DB = 12
        const val MAX_STEP = 3
    }
}

/**
 * The app's own sound processing (audio rework З, DEC-060), in Media3's chain right after the
 * voice tee. 16-bit PCM with one or two channels at any rate; any other format passes through
 * and [supported] turns false. Order: mono → tone (clarity, depth, speaker high-pass) → gain
 * (even loudness + volume + speaker trim, at most +30 dB) → clarity compressor → limiter.
 *
 * A change of parameters never reconfigures or flushes: every value ramps (40 ms; route 100 ms;
 * «Compare» crossfades in 30 ms). The limiter's 5 ms lookahead line is always in the path, so
 * switching never jumps: with everything off the output is the input bit for bit, and every frame
 * that goes in comes out, the last ones at the end of the stream.
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
    private lateinit var clarityHighPass: Biquad
    private lateinit var cut300: Biquad
    private lateinit var lift3k: Biquad
    private lateinit var depthShelf: Biquad
    private lateinit var cut280: Biquad
    private lateinit var speakerHighPass: Biquad

    private val frame = DoubleArray(2)
    private val limited = DoubleArray(2)

    // Ramped values, advanced once per block.
    private val wet = Ramp(0.0)
    private val monoMix = Ramp(0.0)
    private val clarityHpMix = Ramp(0.0)
    private val clarityHpHz = Ramp(CLARITY_HP_HZ)
    private val cut300Db = Ramp(0.0)
    private val lift3kDb = Ramp(0.0)
    private val shelfDb = Ramp(0.0)
    private val cut280Db = Ramp(0.0)
    private val speakerHpMix = Ramp(0.0)
    private val volume = Ramp(0.0)
    private val trim = Ramp(0.0)
    private val even = Ramp(0.0)
    private val slope = Ramp(0.0)
    private val toneRamps = arrayOf(clarityHpHz, cut300Db, lift3kDb, shelfDb, cut280Db)
    private val chainRamps = arrayOf(monoMix, clarityHpMix, clarityHpHz, cut300Db, lift3kDb, shelfDb, cut280Db, speakerHpMix, volume, trim, even, slope)

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

    // Clarity compressor (З6).
    private var detector = 0.0
    private var detectorCoefficient = 0.0
    private var attack = 0.0
    private var release = 0.0
    private var reductionDb = 0.0
    private val levels = DoubleArray(LoudnessMeter.SHORT_TERM_BLOCKS)
    private var levelNext = 0
    private var levelFilled = 0
    private var levelFrames = 0
    private var levelSum = 0.0
    private var levelRunningEnergy = 0.0
    private var levelRunningBlocks = 0
    private var measuredBlocks = 0
    private var thresholdDb = 0.0
    private var compressorEngaged = false
    private var makeupDb = 0.0

    /** The total gain now in dB; the threshold follows it (R4). */
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
        channels = channelCount
        delay = (sampleRate * LOOKAHEAD_MS / 1_000.0).roundToInt().coerceAtLeast(1)
        raw = ShortArray(delay * channels)
        limiter = PeakLimiter(sampleRate, channels, delay, CEILING_DB, RELEASE_MS)
        meter = LoudnessMeter(sampleRate, channels)
        clarityHighPass = Biquad(channels)
        cut300 = Biquad(channels)
        lift3k = Biquad(channels)
        depthShelf = Biquad(channels)
        cut280 = Biquad(channels)
        speakerHighPass = Biquad(channels).apply { highPass(sampleRate, SPEAKER_HP_HZ) }
        detectorCoefficient = 1 - exp(-1.0 / (DETECTOR_MS / 1_000.0 * sampleRate))
        attack = 1 - exp(-1.0 / (ATTACK_MS / 1_000.0 * sampleRate))
        release = 1 - exp(-1.0 / (COMPRESSOR_RELEASE_MS / 1_000.0 * sampleRate))
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
        rawHead = (rawHead + 1) % delay
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
        // Tone.
        for (c in 0 until channels) {
            var x = frame[c]
            val hp = clarityHpMix.value
            val filtered = clarityHighPass.process(c, x)
            if (hp > 0.0) x += hp * (filtered - x)
            x = cut300.process(c, x)
            x = lift3k.process(c, x)
            x = depthShelf.process(c, x)
            x = cut280.process(c, x)
            val sp = speakerHpMix.value
            val speaker = speakerHighPass.process(c, x)
            if (sp > 0.0) x += sp * (speaker - x)
            frame[c] = x
        }
        // The compressor's 3 s level is taken before the gain, so a volume change moves the threshold
        // with it at once instead of over 3 s (audit of З, R4).
        var prePower = 0.0
        for (c in 0 until channels) prePower += frame[c] * frame[c]
        prePower /= channels
        for (c in 0 until channels) frame[c] *= gainNow
        compress(prePower)
        limiter.process(frame, limited)
    }

    private fun compress(prePower: Double) {
        var power = 0.0
        for (c in 0 until channels) power += frame[c] * frame[c]
        power /= channels
        detector += detectorCoefficient * (power - detector)
        // The 3 s level behind the threshold, before the gain, frozen in pauses (З6).
        levelSum += prePower
        levelFrames++
        if (levelFrames >= rate / 10) closeLevelBlock()
        val s = slope.value
        val engaged = s > 0.0 && measuredBlocks >= MEASURED_BEFORE_COMPRESSING
        // No make-up before the compressor works; at the switch the net gain stays continuous (R5).
        if (engaged && !compressorEngaged) reductionDb -= MAKEUP_DB_PER_SLOPE * s
        if (!engaged && compressorEngaged) reductionDb += makeupDb
        compressorEngaged = engaged
        makeupDb = if (engaged) MAKEUP_DB_PER_SLOPE * s else 0.0
        if (!engaged) {
            reductionDb += release * (0.0 - reductionDb)
        } else {
            val over = 10 * log10(detector.coerceAtLeast(1e-12)) - (thresholdDb + gainDbNow)
            val target = when {
                2 * over < -KNEE_DB -> 0.0
                2 * abs(over) <= KNEE_DB -> -s * (over + KNEE_DB / 2) * (over + KNEE_DB / 2) / (2 * KNEE_DB)
                else -> -s * over
            }
            reductionDb += (if (target < reductionDb) attack else release) * (target - reductionDb)
        }
        val g = dbToGain(reductionDb + makeupDb)
        for (c in 0 until channels) frame[c] *= g
    }

    private fun closeLevelBlock() {
        val mean = levelSum / levelFrames
        levelSum = 0.0
        levelFrames = 0
        val db = 10 * log10(mean.coerceAtLeast(1e-12))
        val running = if (levelRunningBlocks > 0) 10 * log10(levelRunningEnergy / levelRunningBlocks) else db
        // A pause is quiet as heard: a faint source lifted by a large gain still engages the compressor
        // (audit of З, after R4); the relative test compares like with like before the gain.
        if (db + gainDbNow < PAUSE_DB || db < running - PAUSE_UNDER_DB) return
        levelRunningEnergy += mean
        levelRunningBlocks++
        levels[levelNext] = mean
        levelNext = (levelNext + 1) % levels.size
        if (levelFilled < levels.size) levelFilled++
        measuredBlocks++
        var energy = 0.0
        for (i in 0 until levelFilled) energy += levels[i]
        thresholdDb = 10 * log10(energy / levelFilled) - THRESHOLD_UNDER_DB
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
        clarityHpMix.aim(if (p.clarity > 0) 1.0 else 0.0, rampFrames)
        clarityHpHz.aim(if (p.depth > 0) CLARITY_HP_DEEP_HZ else CLARITY_HP_HZ, rampFrames)
        var c300 = CUT_300_DB[p.clarity]
        var c280 = CUT_280_DB[p.depth]
        if (c300 + c280 < MAX_TOTAL_CUT_DB) {
            val scale = MAX_TOTAL_CUT_DB / (c300 + c280)
            c300 *= scale
            c280 *= scale
        }
        cut300Db.aim(c300, rampFrames)
        cut280Db.aim(c280, rampFrames)
        lift3kDb.aim(LIFT_3K_DB[p.clarity], rampFrames)
        shelfDb.aim(if (p.speakerRoute) 0.0 else SHELF_DB[p.depth], routeFrames)
        volume.aim(p.volumeDb.toDouble(), rampFrames)
        trim.aim(if (p.speakerRoute) p.speakerTrimDb.toDouble() else 0.0, routeFrames)
        even.aim(if (p.evenLoudness) evenTargetDb else 0.0, rampFrames)
        val speakerGain = p.volumeDb + (if (p.speakerRoute) p.speakerTrimDb else 0) + maxOf(evenTargetDb, 0.0)
        speakerHpMix.aim(if (p.speakerRoute && speakerGain > 0.0) 1.0 else 0.0, routeFrames)
        val ratio = RATIO[p.clarity]
        slope.aim(1 - 1 / ratio, rampFrames)

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
        clarityHpMix.advance()
        speakerHpMix.advance()
        slope.advance()
        volume.advance()
        trim.advance()
        even.advance()
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

    /** The loudness window and the compressor's level ring start again. */
    private fun restartWindows() {
        if (rate != 0) meter.restartWindow()
        levelFilled = 0
        levelNext = 0
        levelRunningEnergy = 0.0
        levelRunningBlocks = 0
        measuredBlocks = 0
    }

    private fun totalGain(): Double = dbToGain(minOf(volume.value + trim.value + even.value, MAX_TOTAL_GAIN_DB))

    private fun rampFrames(ms: Double): Int = (rate * ms / 1_000.0).roundToInt().coerceAtLeast(1)

    private fun updateFilters() {
        if (rate == 0) return
        clarityHighPass.highPass(rate, clarityHpHz.value)
        cut300.peaking(rate, 300.0, 1.0, cut300Db.value)
        lift3k.peaking(rate, 3_000.0, 0.8, lift3kDb.value)
        depthShelf.lowShelf(rate, 110.0, 0.8, shelfDb.value)
        cut280.peaking(rate, 280.0, 1.2, cut280Db.value)
    }

    private fun resetFilters() {
        if (rate == 0) return
        clarityHighPass.reset()
        cut300.reset()
        lift3k.reset()
        depthShelf.reset()
        cut280.reset()
        speakerHighPass.reset()
        detector = 0.0
        reductionDb = 0.0
        compressorEngaged = false
        makeupDb = 0.0
        levelFrames = 0
        levelSum = 0.0
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
        const val MAX_TOTAL_GAIN_DB = 30.0
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

        const val CLARITY_HP_HZ = 90.0
        const val CLARITY_HP_DEEP_HZ = 60.0
        const val SPEAKER_HP_HZ = 140.0
        val CUT_300_DB = doubleArrayOf(0.0, -1.5, -3.0, -4.0)
        val LIFT_3K_DB = doubleArrayOf(0.0, 2.0, 4.0, 5.0)
        val SHELF_DB = doubleArrayOf(0.0, 2.0, 4.0, 6.0)
        val CUT_280_DB = doubleArrayOf(0.0, -1.0, -2.0, -3.0)
        const val MAX_TOTAL_CUT_DB = -5.0
        val RATIO = doubleArrayOf(1.0, 1.5, 2.0, 3.0)

        private const val DETECTOR_MS = 10.0
        private const val ATTACK_MS = 5.0
        private const val COMPRESSOR_RELEASE_MS = 150.0
        const val KNEE_DB = 6.0
        const val THRESHOLD_UNDER_DB = 8.0
        private const val MAKEUP_DB_PER_SLOPE = 8.0
        private const val PAUSE_DB = -50.0
        private const val MEASURED_BEFORE_COMPRESSING = 4
    }
}
