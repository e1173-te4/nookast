package ru.e1173.nookast.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/SYNC.md §5.13: replay from the beginning for 45 s makes a played episode unplayed. */
class ReplayDetectorTest {
    @Test
    fun replayFromTheBeginningCountsAfter45Seconds() {
        val detector = ReplayDetector()
        detector.onStart("7", completed = true, positionMs = 0, durationMs = 3_600_000)
        repeat(8) { assertFalse(detector.onPlayed("7", 5_000)) }
        assertTrue(detector.onPlayed("7", 5_000))
        assertFalse("only once", detector.onPlayed("7", 5_000))
    }

    @Test
    fun aFragmentFromTheMiddleChangesNothing() {
        val detector = ReplayDetector()
        detector.onStart("7", completed = true, positionMs = 1_200_000, durationMs = 3_600_000)
        repeat(20) { assertFalse(detector.onPlayed("7", 5_000)) }
    }

    @Test
    fun unplayedEpisodesAndOtherItemsAreIgnored() {
        val detector = ReplayDetector()
        detector.onStart("7", completed = false, positionMs = 0, durationMs = 3_600_000)
        repeat(20) { assertFalse(detector.onPlayed("7", 5_000)) }
        detector.onStart("8", completed = true, positionMs = 0, durationMs = 3_600_000)
        repeat(20) { assertFalse(detector.onPlayed("7", 5_000)) }
    }
}
