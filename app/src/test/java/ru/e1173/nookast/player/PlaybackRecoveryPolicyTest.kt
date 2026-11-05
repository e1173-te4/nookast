package ru.e1173.nookast.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackRecoveryPolicyTest {
    @Test
    fun `failed rss source selects pinepods and never repeats an attempted url`() {
        val rss = "https://cdn.example/episode.mp3"
        val pinePods = "https://pinepods.example/api/episodes/7/download"
        assertEquals(
            pinePods,
            nextRecoverySource(rss, rss, pinePods, setOf(rss)),
        )
        assertNull(nextRecoverySource(pinePods, rss, pinePods, setOf(rss, pinePods)))
    }

    @Test
    fun `new retry cycle uses bounded exponential delay`() {
        assertEquals(2_000L, recoveryRetryDelayMs(0))
        assertEquals(16_000L, recoveryRetryDelayMs(3))
        assertEquals(60_000L, recoveryRetryDelayMs(20))
    }
}
