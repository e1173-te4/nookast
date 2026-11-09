package ru.e1173.nookast.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackBehaviorPolicyTest {
    @Test
    fun `memory buffer ahead size is bounded`() {
        assertEquals(8 * 1024 * 1024, PlaybackMediaCache.targetBytes(-1))
        assertEquals(32 * 1024 * 1024, PlaybackMediaCache.targetBytes(32))
        assertEquals(128 * 1024 * 1024, PlaybackMediaCache.targetBytes(999))
    }

    @Test
    fun `memory budget adds back buffer and respects a third of the heap`() {
        val mb = 1024 * 1024
        assertEquals(104 * mb, PlaybackMediaCache.memoryBudgetBytes(32, heapMegabytes = 512))
        assertEquals(170 * mb, PlaybackMediaCache.memoryBudgetBytes(128, heapMegabytes = 512))
        assertEquals(85 * mb, PlaybackMediaCache.memoryBudgetBytes(128, heapMegabytes = 256))
        assertEquals(42 * mb, PlaybackMediaCache.memoryBudgetBytes(128, heapMegabytes = 0))
    }

    @Test
    fun `previous episode completes when another episode starts inside threshold`() {
        assertTrue(
            shouldAutoCompletePreviousEpisode(
                enabled = true,
                previousEpisodeId = 41,
                nextEpisodeId = 42,
                positionMs = 58 * 60_000L,
                durationMs = 60 * 60_000L,
                alreadyCompleted = false,
                remainingMinutesThreshold = 3,
            ),
        )
    }

    @Test
    fun `policy ignores disabled same completed and unknown episodes`() {
        val base = AutoCompletionInput()
        assertFalse(base.copy(enabled = false).evaluate())
        assertFalse(base.copy(nextEpisodeId = base.previousEpisodeId).evaluate())
        assertFalse(base.copy(alreadyCompleted = true).evaluate())
        assertFalse(base.copy(previousEpisodeId = 0).evaluate())
        assertFalse(base.copy(durationMs = null).evaluate())
        assertFalse(base.copy(positionMs = 0).evaluate())
    }

    @Test
    fun `rss only local identity follows the same completion policy`() {
        assertTrue(AutoCompletionInput(previousEpisodeId = -7).evaluate())
    }

    @Test
    fun `policy does not complete outside threshold`() {
        assertFalse(
            AutoCompletionInput(
                positionMs = 50 * 60_000L,
                durationMs = 60 * 60_000L,
                remainingMinutesThreshold = 3,
            ).evaluate(),
        )
    }

    @Test
    fun `threshold is clamped to supported one through thirteen minute range`() {
        assertFalse(
            AutoCompletionInput(
                positionMs = 47 * 60_000L,
                durationMs = 60 * 60_000L + 1,
                remainingMinutesThreshold = 99,
            ).evaluate(),
        )
        assertTrue(
            AutoCompletionInput(
                positionMs = 47 * 60_000L + 1,
                durationMs = 60 * 60_000L,
                remainingMinutesThreshold = 99,
            ).evaluate(),
        )
    }

    private data class AutoCompletionInput(
        val enabled: Boolean = true,
        val previousEpisodeId: Int = 41,
        val nextEpisodeId: Int = 42,
        val positionMs: Long = 58 * 60_000L,
        val durationMs: Long? = 60 * 60_000L,
        val alreadyCompleted: Boolean = false,
        val remainingMinutesThreshold: Int = 3,
    ) {
        fun evaluate(): Boolean = shouldAutoCompletePreviousEpisode(
            enabled = enabled,
            previousEpisodeId = previousEpisodeId,
            nextEpisodeId = nextEpisodeId,
            positionMs = positionMs,
            durationMs = durationMs,
            alreadyCompleted = alreadyCompleted,
            remainingMinutesThreshold = remainingMinutesThreshold,
        )
    }
}
