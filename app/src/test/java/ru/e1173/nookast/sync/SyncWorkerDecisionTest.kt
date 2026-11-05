package ru.e1173.nookast.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.e1173.nookast.core.sync.SyncFailure

/** WorkManager insurance decisions (docs/SYNC.md §9). */
class SyncWorkerDecisionTest {
    @Test
    fun nothingPendingSucceeds() {
        assertEquals(SyncWorkerDecision.SUCCESS, syncWorkerDecision(stoppedBy = null, pending = false, runAttemptCount = 0))
    }

    @Test
    fun authStopsRetryingUntilTheUserSignsIn() {
        assertEquals(SyncWorkerDecision.SUCCESS, syncWorkerDecision(SyncFailure.AUTH, pending = true, runAttemptCount = 0))
    }

    @Test
    fun pendingWorkRetriesWithABound() {
        assertEquals(SyncWorkerDecision.RETRY, syncWorkerDecision(SyncFailure.TRANSIENT, pending = true, runAttemptCount = 3))
        assertEquals(SyncWorkerDecision.SUCCESS, syncWorkerDecision(SyncFailure.TRANSIENT, pending = true, runAttemptCount = 8))
    }
}
