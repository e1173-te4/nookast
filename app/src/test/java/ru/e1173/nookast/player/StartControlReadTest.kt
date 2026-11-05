package ru.e1173.nookast.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** docs/SYNC.md §5.10 (S45, S46, S47). */
class StartControlReadTest {
    @Test
    fun serverAheadWithinTheWindowJumps() {
        assertEquals(
            StartReadAction.Jump(toMs = 2_480_000, fromMs = 603_000),
            decideStartRead(600_000, 2_480_000, false, false, elapsedSinceStartMs = 3_000, durationMs = 3_600_000),
        )
    }

    @Test
    fun serverBehindAfterARewindElsewhereAlsoJumps() {
        assertEquals(
            StartReadAction.Jump(toMs = 300_000, fromMs = 2_401_000),
            decideStartRead(2_400_000, 300_000, false, false, elapsedSinceStartMs = 1_000, durationMs = 3_600_000),
        )
    }

    @Test
    fun aLateAnswerOnlyOffers() {
        assertEquals(
            StartReadAction.Offer(2_480_000),
            decideStartRead(600_000, 2_480_000, false, false, elapsedSinceStartMs = 25_000, durationMs = 3_600_000),
        )
    }

    @Test
    fun smallDifferencesUndeliveredLocalIntentsAndPlayedAreLeftAlone() {
        assertEquals(StartReadAction.None, decideStartRead(600_000, 610_000, false, false, 2_000, 3_600_000))
        assertEquals(StartReadAction.None, decideStartRead(600_000, 2_480_000, false, true, 2_000, 3_600_000))
        assertEquals(StartReadAction.None, decideStartRead(600_000, 3_600_000, true, false, 2_000, 3_600_000))
        assertEquals(StartReadAction.None, decideStartRead(600_000, 0, false, false, 2_000, 3_600_000))
    }
}
