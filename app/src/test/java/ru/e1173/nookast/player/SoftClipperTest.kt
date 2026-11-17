package ru.e1173.nookast.player

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.tanh

/** The polyphase clipper gives the same samples as the plain 47-tap form it replaced (1.2.6 speed fix). */
class SoftClipperTest {

    @Test
    fun `polyphase clipper matches the plain half-band form`() {
        for (amount in doubleArrayOf(0.4, 1.0)) {
            val fast = SoftClipper(2, CEILING, KNEE_DB).apply { this.amount = amount }
            val plain = PlainClipper(CEILING, KNEE_DB).apply { this.amount = amount }
            var worst = 0.0
            for (n in 0 until 20_000) worst = maxOf(worst, abs(fast.process(0, signal(n)) - plain.process(signal(n))))
            assertEquals("amount $amount", 0.0, worst, 1e-12)
        }
    }

    @Test
    fun `at amount 0 it is a plain delay and switching on is seamless`() {
        val fast = SoftClipper(1, CEILING, KNEE_DB)
        val plain = PlainClipper(CEILING, KNEE_DB)
        var worst = 0.0
        for (n in 0 until 30_000) {
            // Density comes and goes: off, on, off, on.
            val amount = if ((n / 5_000) % 2 == 1) 0.7 else 0.0
            fast.amount = amount
            plain.amount = amount
            val y = fast.process(0, signal(n))
            val reference = plain.process(signal(n))
            if (amount == 0.0) {
                assertEquals(if (n >= SoftClipper.LATENCY) signal(n - SoftClipper.LATENCY) else 0.0, y, 1e-15)
            } else {
                worst = maxOf(worst, abs(y - reference))
            }
        }
        assertEquals(0.0, worst, 1e-12)
    }

    /** Loud enough to cross the knee: partials up to 15 kHz at 48 kHz. */
    private fun signal(n: Int) = 1.3 * (0.5 * sin(n * 0.031) + 0.3 * sin(n * 0.47 + 1) + 0.2 * sin(n * 1.96))

    /** The original form: zero-stuffing, three full 47-tap filters per sample. */
    private class PlainClipper(private val ceiling: Double, kneeDb: Double) {
        var amount = 0.0
        private val knee = ceiling * Math.pow(10.0, -kneeDb / 20)
        private val taps = SoftClipper.HALF_BAND
        private val up = ArrayDeque(List(taps.size) { 0.0 })
        private val down = ArrayDeque(List(taps.size) { 0.0 })

        private fun shape(x: Double): Double {
            val a = abs(x)
            if (a <= knee) return x
            val room = ceiling - knee
            val y = a + amount * (knee + room * tanh((a - knee) / room) - a)
            return if (x < 0) -y else y
        }

        private fun push(line: ArrayDeque<Double>, x: Double) {
            line.addFirst(x)
            line.removeLast()
        }

        private fun fir(line: ArrayDeque<Double>): Double = taps.indices.sumOf { taps[it] * line[it] }

        fun process(x: Double): Double {
            push(up, 2 * x)
            val a = shape(fir(up))
            push(up, 0.0)
            val b = shape(fir(up))
            push(down, a)
            push(down, b)
            return fir(down)
        }
    }

    private companion object {
        const val CEILING = 0.891
        const val KNEE_DB = 2.5
    }
}
