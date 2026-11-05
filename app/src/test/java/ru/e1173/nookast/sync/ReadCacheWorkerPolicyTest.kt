package ru.e1173.nookast.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.e1173.nookast.data.remote.QueueLoadError
import ru.e1173.nookast.data.remote.SyncErrorCategory

class ReadCacheWorkerPolicyTest {
    @Test
    fun readCacheRunsOnlyAfterOutboxIsDrained() {
        assertEquals(
            ReadCacheWorkerDecision.RETRY,
            readCacheWorkerDecision(null, pendingCount = 1, readError = null),
        )
        assertEquals(
            ReadCacheWorkerDecision.SUCCESS,
            readCacheWorkerDecision(null, pendingCount = 0, readError = null),
        )
    }

    @Test
    fun authenticationStopsBackgroundRetryUntilUserSignsInAgain() {
        assertEquals(
            ReadCacheWorkerDecision.STOP_FOR_AUTH,
            readCacheWorkerDecision(SyncErrorCategory.AUTHENTICATION, 1, null),
        )
        assertEquals(
            ReadCacheWorkerDecision.STOP_FOR_AUTH,
            readCacheWorkerDecision(null, 0, QueueLoadError.AUTHENTICATION),
        )
    }

    @Test
    fun transientReadFailureIsRetried() {
        assertEquals(
            ReadCacheWorkerDecision.RETRY,
            readCacheWorkerDecision(null, 0, QueueLoadError.NETWORK),
        )
    }
}
