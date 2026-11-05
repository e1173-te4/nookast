package ru.e1173.nookast.offline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeDownloadIntegrityTest {
    @Test
    fun partialFileIsNeverAcceptedWithoutCompletionMarker() {
        assertFalse(
            isCompletedFileUsable(
                markedComplete = false,
                fileLength = 4_000,
                expectedBytes = 10_000,
            ),
        )
    }

    @Test
    fun completedFileMustReachKnownExpectedLength() {
        assertFalse(isCompletedFileUsable(true, 9_999, 10_000))
        assertTrue(isCompletedFileUsable(true, 10_000, 10_000))
        assertTrue(isCompletedFileUsable(true, 10_001, 10_000))
    }

    @Test
    fun successfulLegacyDownloadCanBeAcceptedWhenLengthWasUnknown() {
        assertTrue(isCompletedFileUsable(true, 1, null))
        assertFalse(isCompletedFileUsable(true, 0, null))
    }
}
