package ru.e1173.nookast.sync

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.e1173.nookast.core.sync.SyncFailure
import ru.e1173.nookast.testing.FakePinePodsServer
import ru.e1173.nookast.testing.FakePinePodsServer.Fault
import ru.e1173.nookast.testing.SyncDevice

/**
 * Synchronization scenarios from docs/SYNC.md §15, written before the redesign (3.4).
 *
 * Expectations are the target behaviour of SYNC.md. They run against the new engine (3.5) through
 * [SyncDevice]; until 3.5 the same scenarios ran against v7 (history). Where the engine still
 * violates a scenario, the test carries @KnownDefect with the finding ID
 * and is reported as skipped until the fix lands (see [ru.e1173.nookast.testing.KnownDefectRule]).
 *
 * Covered here: data-level scenarios. Scenarios that need the player, the account flows or the
 * migration (S02, S17, S19, S20, S25, S40, S43, S45–S67 …) are added with the SyncEngine in 3.5,
 * when their entry points exist.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SyncScenariosTest {
    @get:Rule
    val knownDefects = ru.e1173.nookast.testing.KnownDefectRule()

    private lateinit var server: FakePinePodsServer
    private val devices = mutableListOf<SyncDevice>()

    @Before
    fun setUp() {
        server = FakePinePodsServer().start()
        (1..9).forEach { server.addEpisode(it) }
        server.addEpisode(40)
    }

    @After
    fun tearDown() {
        devices.forEach(SyncDevice::close)
        server.close()
    }

    private fun device(name: String) =
        SyncDevice(RuntimeEnvironment.getApplication(), server, name).also { devices += it }

    // --- Progress ---

    @Test
    fun s01_offlineProgressReachesTheServerLater() = runBlocking {
        val phone = device("phone")
        phone.listen(1, fromSeconds = 100, toSeconds = 200)
        server.failNext(RECORD, Fault.LoseRequest)
        phone.sync()

        phone.syncRepeatedly()

        assertEquals(200, server.episode(1)!!.listenSeconds)
    }

    @Test
    fun s03_rewindElsewhereDoesNotBlockActiveListening() = runBlocking {
        val car = device("car")
        val phone = device("phone")
        car.listen(1, 1_500, 1_500)
        car.sync()
        phone.progress(1, 100, ProgressCause.SEEK)
        phone.sync()

        car.listen(1, 1_505, 1_600)
        car.sync()

        assertEquals(1_600, server.episode(1)!!.listenSeconds)
    }

    @Test
    fun s06_explicitRewindReachesTheServer() = runBlocking {
        val phone = device("phone")
        phone.listen(1, 1_500, 1_500)
        phone.sync()

        phone.progress(1, 100, ProgressCause.SEEK)
        phone.sync()

        assertEquals(100, server.episode(1)!!.listenSeconds)
    }

    @Test
    fun s07_lostResponseAfterCommitIsRetriedWithoutLoss() = runBlocking {
        val phone = device("phone")
        phone.listen(1, 300, 300)
        server.failNext(RECORD, Fault.LoseResponse)
        phone.sync()

        phone.syncRepeatedly()

        assertEquals(300, server.episode(1)!!.listenSeconds)
        assertEquals(0, phone.pendingCount())
    }

    @Test
    fun s15_deletedEpisodeDoesNotBlockOtherDeliveries() = runBlocking {
        val phone = device("phone")
        phone.listen(40, 60, 60)
        phone.add(2)
        server.deleteEpisode(40)

        phone.syncRepeatedly()

        assertEquals(listOf(2), server.queueIds())
    }

    @Test
    fun s18_revokedKeyIsRecognisedAsAuthentication() = runBlocking {
        val phone = device("phone")
        phone.listen(1, 60, 60)
        server.revoke("key-${FakePinePodsServer.USER_ID}")

        val result = phone.sync()

        assertEquals(SyncFailure.AUTH, result.stoppedBy)
    }

    @Test
    fun s31_completionMarksPlayedAndLeavesTheQueue() = runBlocking {
        val phone = device("phone")
        server.webQueue(3)
        phone.sync()

        phone.complete(3)
        phone.sync()

        assertTrue(server.episode(3)!!.completed)
        assertFalse(3 in server.queueIds())
        val calls = server.calls().map { it.path }
        assertTrue(calls.indexOf(COMPLETED) < calls.indexOf(REMOVE_QUEUED))
    }

    @Test
    fun s41_nightlyAutoCompleteIsAcceptedByACleanClient() = runBlocking {
        server.autoCompleteSeconds = 180
        val phone = device("phone")
        server.webQueue(4)
        phone.listen(4, 3_500, 3_500)
        phone.sync()

        server.runNightlyAutoComplete()
        phone.sync()

        assertEquals(0L, phone.resumeSeconds(4))
    }

    @Test
    fun s74_recordInsideAutoCompleteWindowIsKnownLocallyAsPlayed() = runBlocking {
        server.autoCompleteSeconds = 180
        val phone = device("phone")
        phone.listen(5, 3_500, 3_500)
        phone.sync()

        assertTrue(server.episode(5)!!.completed)
        val local = phone.database.progressDao().getProgress(5)!!
        assertTrue("the client must learn that the server completed the episode", local.completed)
    }

    // --- Queue ---

    @Test
    fun s08_lostAddResponseCreatesNoDuplicate() = runBlocking {
        val phone = device("phone")
        phone.add(1)
        server.failNext(QUEUE_POD, Fault.LoseResponse)
        phone.sync()

        phone.syncRepeatedly()

        assertEquals(listOf(1), server.queueIds())
        assertEquals(1, server.rawQueue().size)
    }

    @Test
    fun s09_timedOutAddThenRemoveEndsOutsideTheQueue() = runBlocking {
        val phone = device("phone")
        phone.add(1)
        server.failNext(QUEUE_POD, Fault.LoseRequest)
        phone.sync()
        phone.remove(1)

        phone.syncRepeatedly()

        assertFalse(1 in server.queueIds())
        assertFalse(1 in phone.localQueue())
    }

    @Test
    fun s10_failedRemoveThenAddEndsInTheQueue() = runBlocking {
        val phone = device("phone")
        server.webQueue(1)
        phone.sync()
        phone.remove(1)
        server.failNext(REMOVE_QUEUED, Fault.LoseRequest)
        phone.sync()
        phone.add(1)

        phone.syncRepeatedly()

        assertEquals(listOf(1), server.queueIds())
        assertEquals(listOf(1), phone.localQueue())
    }

    @Test
    fun s11_staleAddFirstKeepsAnotherDevicesReorder() = runBlocking {
        val phone = device("phone")
        val car = device("car")
        listOf(1, 2, 3).forEach(server::webQueue)
        phone.sync()
        car.sync()

        phone.move(3, 0)
        phone.sync()
        car.add(9, index = 0)
        car.sync()

        assertEquals(listOf(9, 3, 1, 2), server.queueIds())
    }

    @Test
    fun s13_offlineReorderKeepsAWebAddition() = runBlocking {
        val phone = device("phone")
        listOf(1, 2, 3).forEach(server::webQueue)
        phone.sync()

        phone.move(3, 0)
        server.webQueue(7)
        phone.sync()

        assertEquals(listOf(3, 1, 2, 7), server.queueIds())
        assertEquals(listOf(3, 1, 2, 7), phone.localQueue())
    }

    @Test
    fun s33_clearThenAddKeepsTheAddition() = runBlocking {
        val phone = device("phone")
        listOf(1, 2).forEach(server::webQueue)
        phone.sync()

        phone.clear()
        phone.add(5)
        phone.sync()

        assertEquals(listOf(5), server.queueIds())
    }

    @Test
    fun s35_rapidAddRemoveSettlesOnTheLastIntent() = runBlocking {
        val phone = device("phone")
        repeat(5) {
            phone.add(6)
            phone.remove(6)
        }

        phone.syncRepeatedly()

        assertFalse(6 in server.queueIds())
        assertFalse(6 in phone.localQueue())
    }

    @Test
    fun s71_offlineClearKeepsAnItemAddedLaterOnTheWeb() = runBlocking {
        val phone = device("phone")
        listOf(1, 2).forEach(server::webQueue)
        phone.sync()

        phone.clear()
        server.webQueue(8)
        phone.sync()

        assertEquals(listOf(8), server.queueIds())
    }

    @Test
    fun s75_equalServerPositionsGiveAStableLocalOrder() = runBlocking {
        val phone = device("phone")
        listOf(1, 2, 3).forEach(server::webQueue)
        server.webReorder(listOf(3)) // positions now 1, 2, 1: a tie
        server.unstableTieOrder = true

        phone.sync()
        val first = phone.localQueue()
        phone.sync()

        assertEquals(first, phone.localQueue())
    }

    private companion object {
        const val RECORD = "/api/data/record_listen_duration"
        const val COMPLETED = "/api/data/mark_episode_completed"
        const val QUEUE_POD = "/api/data/queue_pod"
        const val REMOVE_QUEUED = "/api/data/remove_queued_pod"
    }
}
