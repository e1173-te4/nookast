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
import ru.e1173.nookast.core.sync.ProgressIntent
import ru.e1173.nookast.core.sync.ServerEpisode
import ru.e1173.nookast.testing.FakePinePodsServer
import ru.e1173.nookast.testing.FakePinePodsServer.Fault
import ru.e1173.nookast.testing.KnownDefectRule
import ru.e1173.nookast.testing.SyncDevice

/** Scenarios added by the adversarial review 3.3 (docs/SYNC.md §15, S29–S80). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SyncReviewScenariosTest {
    @get:Rule
    val knownDefects = KnownDefectRule()

    private lateinit var server: FakePinePodsServer
    private val devices = mutableListOf<SyncDevice>()

    @Before
    fun setUp() {
        server = FakePinePodsServer().start()
        (1..9).forEach { server.addEpisode(it) }
    }

    @After
    fun tearDown() {
        devices.forEach(SyncDevice::close)
        server.close()
    }

    private fun device(name: String) =
        SyncDevice(RuntimeEnvironment.getApplication(), server, name).also { devices += it }

    @Test
    fun s29_activeListeningOnTwoDevicesKeepsTheFurthestPosition() = runBlocking {
        val car = device("car")
        val phone = device("phone")
        car.listen(1, 1_000, 1_010)
        phone.listen(1, 400, 410)
        car.sync()
        phone.sync()

        assertEquals(1_010, server.episode(1)!!.listenSeconds)
    }

    @Test
    fun s42_passiveIntentIsDroppedOnlyWhenTheServerIsAlreadyAhead() = runBlocking {
        val car = device("car")
        car.listen(2, 300, 300)
        car.activeSession = null // the trip ended: the intent belongs to a past session
        server.webSetProgress(2, 900)

        car.sync()

        assertEquals(900, server.episode(2)!!.listenSeconds)
        assertEquals(0, car.pendingCount())
        assertEquals(900L, car.resumeSeconds(2))
    }

    @Test
    fun s48_rewindIsNotSentWhenAnotherDeviceWentFurther() = runBlocking {
        val car = device("car")
        val phone = device("phone")
        car.listen(3, 2_000, 2_000)
        car.sync()
        phone.open(3) // the phone shows the episode at 2000
        car.listen(3, 2_005, 3_000) // the evening listen on the car
        car.sync()

        phone.progress(3, 1_990, ProgressCause.SEEK) // "back 10 s" on stale knowledge
        phone.sync()

        assertEquals("the car's hour must survive", 3_000, server.episode(3)!!.listenSeconds)
        assertEquals(1, phone.repository.deadLetterCount())
    }

    @Test
    fun s49_shortRewindAboveTheServerPositionDoesNotResetTheServer() = runBlocking {
        val phone = device("phone")
        phone.listen(4, 500, 500)
        phone.sync()
        phone.listen(4, 505, 600)
        phone.progress(4, 590, ProgressCause.SEEK) // still ahead of the server's 500

        phone.sync()

        assertTrue(server.callsTo(UNCOMPLETED).isEmpty())
        assertEquals(590, server.episode(4)!!.listenSeconds)
    }

    @Test
    fun s53_deletedQueuedEpisodeIsDeadLetteredAndTheRestSyncs() = runBlocking {
        val phone = device("phone")
        phone.add(5)
        phone.add(6)
        server.deleteEpisode(5)

        phone.sync()

        assertEquals(listOf(6), server.queueIds())
        assertTrue("the orphan row is removed", server.rawQueue().none { it.episodeId == 5 })
        assertEquals(1, phone.repository.deadLetterCount())
        assertEquals(0, phone.pendingCount())
    }

    @Test
    fun s68_ticksDuringARewindDoNotStallItsPhases() = runBlocking {
        val phone = device("phone")
        phone.listen(6, 1_500, 1_500)
        phone.sync()
        phone.progress(6, 100, ProgressCause.SEEK)
        server.failNext(UNCOMPLETED, Fault.LoseResponse) // phase 0 committed, answer lost
        phone.sync()
        phone.listen(6, 105, 120) // ticks while phase 0 is unconfirmed

        phone.syncRepeatedly()

        assertEquals(120, server.episode(6)!!.listenSeconds)
        assertEquals(0, phone.pendingCount())
    }

    @Test
    fun s69_aStaleReadDoesNotUndoAConfirmedUncomplete() = runBlocking {
        val phone = device("phone")
        server.webMarkCompleted(7)
        val staleTicket = phone.repository.reserveTicket() // a read sent before the intent
        phone.progress(7, 0, ProgressCause.UNCOMPLETE)
        phone.sync()

        val applied = phone.repository.observeProgress(
            staleTicket,
            ServerEpisode(7, listenSeconds = 3_600, completed = true, durationSeconds = 3_600, queued = false),
        )

        assertFalse(applied)
        assertFalse(phone.repository.progress(7)!!.completed)
    }

    @Test
    fun s70_aPullDuringAnAddDoesNotDropIt() = runBlocking {
        val phone = device("phone")
        server.webQueue(1)
        phone.sync()
        val ticket = phone.repository.reserveTicket()
        phone.add(8) // arrives while the read is in flight

        phone.repository.observeQueue(ticket, emptyList()) // web cleared the queue meanwhile

        assertTrue(8 in phone.localQueue())
    }

    @Test
    fun s73_rewindIntoTheAutoCompleteWindowStaysLocal() = runBlocking {
        server.autoCompleteSeconds = 180
        val phone = device("phone")
        phone.complete(9)
        phone.sync()

        phone.repository.recordProgress(9, ProgressIntent.Rewind(3_500_000, 3_600_000, "s"))
        phone.sync()

        assertTrue(server.episode(9)!!.completed)
        assertTrue(server.callsTo(UNCOMPLETED).isEmpty())
        assertEquals(0, phone.pendingCount())
    }

    @Test
    fun s77_stalePassiveAfterARewindElsewhereIsDropped() = runBlocking {
        val car = device("car")
        val phone = device("phone")
        car.listen(2, 1_000, 1_000)
        car.sync()
        car.listen(2, 1_005, 1_500) // offline, not delivered
        car.activeSession = null // trip over
        phone.open(2)
        phone.progress(2, 50, ProgressCause.SEEK) // phone rewinds to 50
        phone.sync()

        car.sync() // the car comes back online

        assertEquals("the stale car position must not undo the rewind", 50, server.episode(2)!!.listenSeconds)
    }

    @Test
    fun s31_removalWaitsForTheCompletion() = runBlocking {
        val phone = device("phone")
        server.webQueue(3)
        phone.sync()
        phone.complete(3)
        server.failNext(COMPLETED, Fault.Status(503))

        phone.sync()

        assertTrue("the queue removal waits while COMPLETE is pending", 3 in server.queueIds())
        phone.syncRepeatedly()
        assertTrue(server.episode(3)!!.completed)
        assertFalse(3 in server.queueIds())
    }

    private companion object {
        const val UNCOMPLETED = "/api/data/mark_episode_uncompleted"
        const val COMPLETED = "/api/data/mark_episode_completed"
    }
}
