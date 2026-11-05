package ru.e1173.nookast.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.e1173.nookast.core.sync.ProgressIntentKind.COMPLETE
import ru.e1173.nookast.core.sync.ProgressIntentKind.PASSIVE
import ru.e1173.nookast.core.sync.ProgressIntentKind.REWIND
import ru.e1173.nookast.core.sync.ProgressIntentKind.UNCOMPLETE

/** docs/SYNC.md §5.3, row by row. */
class ProgressIntentReducerTest {
    private val clean = ProgressState.empty(7).copy(
        positionMs = 100_000,
        durationMs = 3_600_000,
        serverPositionSeconds = 100,
        serverTicket = 40,
    )

    private fun passive(ms: Long) = ProgressIntent.Passive(ms, 3_600_000, "s1")
    private fun rewind(ms: Long) = ProgressIntent.Rewind(ms, 3_600_000, "s1")

    @Test
    fun passiveOnACleanRowMovesForwardAndRecordsItsBase() {
        val next = ProgressIntentReducer.apply(clean, passive(105_000), counter = 50)!!

        assertTrue(next.dirty)
        assertEquals(PASSIVE, next.kind)
        assertEquals(105_000, next.positionMs)
        assertEquals(50, next.intentCounter)
        assertEquals(100L, next.baseServerPositionSeconds)
        assertEquals(40L, next.baseTicket)
    }

    @Test
    fun passiveBackwardsIsANoOp() {
        assertNull(ProgressIntentReducer.apply(clean, passive(90_000), counter = 50))
        val pending = ProgressIntentReducer.apply(clean, passive(105_000), counter = 50)!!
        assertNull(ProgressIntentReducer.apply(pending, passive(104_000), counter = 51))
        assertNull(ProgressIntentReducer.apply(pending, passive(105_000), counter = 51))
    }

    @Test
    fun ticksAfterARewindKeepItsPhaseAndBase() {
        val rewound = ProgressIntentReducer.apply(clean, rewind(20_000), counter = 60)!!
        assertEquals(REWIND, rewound.kind)
        assertEquals(60L, rewound.rewindBaseCounter)
        val phaseOne = rewound.copy(rewindPhase = 1, maxSentPositionMs = 0)

        val ticked = ProgressIntentReducer.apply(phaseOne, passive(25_000), counter = 61)!!

        assertEquals(REWIND, ticked.kind)
        assertEquals(25_000, ticked.positionMs)
        assertEquals(61, ticked.intentCounter)
        assertEquals("the base survives ticks (review F1)", 60L, ticked.rewindBaseCounter)
        assertEquals(1, ticked.rewindPhase)
    }

    @Test
    fun aDeeperRewindRestartsTheTwoPhaseDelivery() {
        val sent = ProgressIntentReducer.apply(clean, rewind(50_000), counter = 60)!!
            .copy(rewindPhase = 1, maxSentPositionMs = 50_000)

        val deeper = ProgressIntentReducer.apply(sent, rewind(30_000), counter = 61)!!
        assertEquals(0, deeper.rewindPhase)
        assertEquals(61L, deeper.rewindBaseCounter)

        val shallower = ProgressIntentReducer.apply(sent, rewind(55_000), counter = 62)!!
        assertEquals(1, shallower.rewindPhase)
        assertEquals(60L, shallower.rewindBaseCounter)
    }

    @Test
    fun uncompleteResetsToZeroAndTicksTurnItIntoARewind() {
        val played = clean.copy(completed = true, positionMs = 3_600_000)
        val uncompleted = ProgressIntentReducer.apply(played, ProgressIntent.Uncomplete, counter = 70)!!
        assertEquals(UNCOMPLETE, uncompleted.kind)
        assertEquals(0, uncompleted.positionMs)
        assertFalse(uncompleted.completed)

        val listening = ProgressIntentReducer.apply(uncompleted, passive(15_000), counter = 71)!!
        assertEquals(REWIND, listening.kind)
        assertEquals(15_000, listening.positionMs)
        assertEquals(70L, listening.rewindBaseCounter)
    }

    @Test
    fun completeWinsOverPassiveAndIgnoresLaterTicks() {
        val pending = ProgressIntentReducer.apply(clean, passive(200_000), counter = 50)!!
        val done = ProgressIntentReducer.apply(pending, ProgressIntent.Complete(3_600_000, "s1"), counter = 51)!!

        assertEquals(COMPLETE, done.kind)
        assertTrue(done.completed)
        assertEquals(3_600_000, done.positionMs)
        assertNull(ProgressIntentReducer.apply(done, passive(3_600_000), counter = 52))
        assertNull(ProgressIntentReducer.apply(done, ProgressIntent.Complete(3_600_000, "s1"), counter = 53))
    }

    @Test
    fun rewindAfterCompleteReplacesIt() {
        val done = ProgressIntentReducer.apply(clean, ProgressIntent.Complete(3_600_000, "s1"), counter = 51)!!

        val back = ProgressIntentReducer.apply(done, rewind(3_400_000), counter = 52)!!

        assertEquals(REWIND, back.kind)
        assertFalse(back.completed)
        assertEquals(3_400_000, back.positionMs)
    }

    @Test
    fun aPlayedCleanEpisodeIgnoresPassiveProgress() {
        val played = clean.copy(completed = true)
        assertNull(ProgressIntentReducer.apply(played, passive(200_000), counter = 50))
        assertNull(ProgressIntentReducer.apply(played, ProgressIntent.Complete(null, null), counter = 50))
    }
}
