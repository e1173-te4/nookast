package ru.e1173.nookast.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wave of a Play/Pause tap (design audit 4.10), at the level of state: it rises to the start
 * or pause amplitude, and each reflection receives it after its distance delay (30 / 60 / 110 ms).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayLightPulseTest {
    private class VirtualFrames(private val scope: TestScope) : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(FRAME_MS)
            return onFrame(scope.testScheduler.currentTime * 1_000_000)
        }
    }

    private fun TestScope.light(): Pair<PlayLight, Map<Int, Animatable<Float, *>>> {
        val pulses = listOf(PlayLightDelay.PLAY, PlayLightDelay.SEEK, PlayLightDelay.CHAPTER, PlayLightDelay.DOCK)
            .associateWith { Animatable(0f) }
        val scope = CoroutineScope(coroutineContext + VirtualFrames(this))
        val light = PlayLight(mutableStateOf(1f), mutableStateOf(1f), mutableStateOf({ 1f }), pulses, scope, reducedMotion = false)
        return light to pulses
    }

    /** Peak value and the first moment (ms after the tap) each element's pulse moved. */
    private fun TestScope.trace(pulses: Map<Int, Animatable<Float, *>>): Map<Int, Pair<Float, Long>> {
        val peaks = pulses.keys.associateWith { 0f }.toMutableMap()
        val starts = HashMap<Int, Long>()
        val t0 = testScheduler.currentTime
        repeat(80) {
            advanceTimeBy(FRAME_MS)
            runCurrent()
            for ((delayMs, pulse) in pulses) {
                val v = pulse.value
                if (v > 0f && delayMs !in starts) starts[delayMs] = testScheduler.currentTime - t0
                if (v > peaks.getValue(delayMs)) peaks[delayMs] = v
            }
        }
        return pulses.keys.associateWith { peaks.getValue(it) to (starts[it] ?: -1L) }
    }

    @Test
    fun `a start sends the start amplitude to every element, later the farther it is`() = runTest {
        val (light, pulses) = light()
        light.flash(starting = true)
        val trace = trace(pulses)
        for ((delayMs, result) in trace) {
            assertEquals("peak at $delayMs ms", PULSE_START, result.first, 0.01f)
            assertTrue("element $delayMs starts after its delay: ${result.second}", result.second >= delayMs)
            assertTrue("element $delayMs starts promptly: ${result.second}", result.second <= delayMs + 3 * FRAME_MS)
        }
        val starts = trace.toSortedMap().values.map { it.second }
        assertEquals(starts.sorted(), starts)
        // The wave has passed half a second after the tap.
        pulses.values.forEach { assertEquals(0f, it.value, 0.001f) }
    }

    @Test
    fun `a pause sends the smaller pause amplitude`() = runTest {
        val (light, pulses) = light()
        light.flash(starting = false)
        trace(pulses).values.forEach { (peak, _) -> assertEquals(PULSE_PAUSE, peak, 0.01f) }
    }

    private companion object {
        const val FRAME_MS = 16L
        val PULSE_START = PlayLight.PULSE_START
        val PULSE_PAUSE = PlayLight.PULSE_PAUSE
    }
}
