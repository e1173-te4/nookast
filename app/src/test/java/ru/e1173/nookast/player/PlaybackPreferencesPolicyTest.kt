package ru.e1173.nookast.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPreferencesPolicyTest {
    @Test
    fun queueInsertionPositionUsesCallerSelectedSafeDefault() {
        assertEquals(
            QueueInsertionPosition.FIRST,
            QueueInsertionPosition.fromStoredValue(null, QueueInsertionPosition.FIRST),
        )
        assertEquals(
            QueueInsertionPosition.LAST,
            QueueInsertionPosition.fromStoredValue("removed", QueueInsertionPosition.LAST),
        )
        assertEquals(
            QueueInsertionPosition.FIRST,
            QueueInsertionPosition.fromStoredValue("FIRST", QueueInsertionPosition.LAST),
        )
    }
}
