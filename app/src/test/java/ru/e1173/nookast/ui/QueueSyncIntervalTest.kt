package ru.e1173.nookast.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueSyncIntervalTest {
    @Test
    fun activeQueueRefreshUsesThirteenSecondCadence() {
        assertEquals(13_000L, ACTIVE_QUEUE_SYNC_INTERVAL_MS)
    }
}
