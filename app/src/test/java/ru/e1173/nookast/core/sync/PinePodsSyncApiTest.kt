package ru.e1173.nookast.core.sync

import android.app.Application
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.e1173.nookast.testing.FakePinePodsServer
import ru.e1173.nookast.testing.FakePinePodsServer.Fault

/** Classification of every server answer (docs/SYNC.md §8) against the verified fake. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PinePodsSyncApiTest {
    private lateinit var server: FakePinePodsServer
    private var validated = true
    private val api = PinePodsSyncApi(networkValidated = { validated })
    private val session get() = server.session()

    @Before
    fun setUp() {
        server = FakePinePodsServer().start()
        (1..3).forEach { server.addEpisode(it, durationSeconds = 3_600) }
    }

    @After
    fun tearDown() = server.close()

    private fun failureOf(block: () -> Unit): SyncCallException {
        try {
            block()
        } catch (error: SyncCallException) {
            return error
        }
        fail("expected SyncCallException")
        error("unreachable")
    }

    @Test
    fun happyPathReadsAndWrites() {
        api.queuePod(session, 2)
        api.queuePod(session, 1)
        api.reorder(session, listOf(1, 2))
        api.recordListen(session, 1, 120)

        assertEquals(listOf(1, 2), api.queue(session).sortedBy { it.position }.map { it.episodeId })
        val episode = api.episode(session, 1)!!
        assertEquals(120L, episode.listenSeconds)
        assertTrue(episode.queued)
        server.autoCompleteSeconds = 180
        assertEquals(180, api.autoCompleteSeconds(session))
        assertEquals("0.9.0", api.version(session))
    }

    @Test
    fun deletedEpisodeIsPermanentAndMetadataIsNull() {
        server.deleteEpisode(3)

        assertEquals(SyncFailure.PERMANENT, failureOf { api.recordListen(session, 3, 10) }.failure)
        assertNull(api.episode(session, 3))
    }

    @Test
    fun revokedKeyIsSuspectedOnProgressAndAuthElsewhere() {
        val s = session
        server.revoke(s.apiKey)

        assertEquals(SyncFailure.AUTH_SUSPECT, failureOf { api.recordListen(s, 1, 10) }.failure)
        assertEquals(SyncFailure.AUTH, failureOf { api.queuePod(s, 1) }.failure)
        assertEquals(SyncFailure.AUTH, failureOf { api.queue(s) }.failure)
    }

    @Test
    fun htmlIsACaptivePortalOnlyOnAnUnvalidatedNetwork() {
        server.failNext(QUEUE_POD, Fault.Html200)
        assertEquals(SyncFailure.PROTOCOL, failureOf { api.queuePod(session, 1) }.failure)

        validated = false
        server.failNext(QUEUE_POD, Fault.Html200)
        assertEquals(SyncFailure.NETWORK_LOGIN, failureOf { api.queuePod(session, 1) }.failure)
    }

    @Test
    fun overloadIsTransientWithRetryAfter() {
        server.failNext(QUEUE_POD, Fault.Status(503, headers = mapOf("Retry-After" to "17")))

        val failure = failureOf { api.queuePod(session, 1) }

        assertEquals(SyncFailure.TRANSIENT, failure.failure)
        assertEquals(17L, failure.retryAfterSeconds)
    }

    @Test
    fun lostResponseIsTransientAndTheWriteHappened() {
        server.failNext(QUEUE_POD, Fault.LoseResponse)

        assertEquals(SyncFailure.TRANSIENT, failureOf { api.queuePod(session, 1) }.failure)
        assertEquals(listOf(1), server.queueIds())
    }

    @Test
    fun malformedRequestsAndMissingAcknowledgementsAreProtocol() {
        server.failNext(QUEUE_POD, Fault.Status(422, "Failed to deserialize the JSON body"))
        assertEquals(SyncFailure.PROTOCOL, failureOf { api.queuePod(session, 1) }.failure)

        server.failNext(QUEUE_POD, Fault.Status(200, """{"something":"else"}""", "application/json"))
        assertEquals(SyncFailure.PROTOCOL, failureOf { api.queuePod(session, 1) }.failure)
    }

    @Test
    fun negativeIdsAreNeverSent() {
        try {
            api.recordListen(session, -4, 10)
            fail("RSS-only IDs must not reach the server")
        } catch (_: IllegalArgumentException) {
            // expected
        }
        assertTrue(server.calls().isEmpty())
    }

    private companion object {
        const val QUEUE_POD = "/api/data/queue_pod"
    }
}
