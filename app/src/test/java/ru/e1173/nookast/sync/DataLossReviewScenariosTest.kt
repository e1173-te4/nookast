package ru.e1173.nookast.sync

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.e1173.nookast.testing.FakePinePodsServer
import ru.e1173.nookast.testing.FakePinePodsServer.Fault
import ru.e1173.nookast.testing.SyncDevice

/** Findings of the data-loss review 3.6 (docs/SYNC.md §10.5). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DataLossReviewScenariosTest {
    private lateinit var server: FakePinePodsServer
    private lateinit var phone: SyncDevice

    @Before
    fun setUp() {
        server = FakePinePodsServer().start()
        (1..4).forEach { server.addEpisode(it) }
        server.addEpisode(9, durationSeconds = null) // PinePods sometimes has no duration
        phone = SyncDevice(RuntimeEnvironment.getApplication(), server, "phone")
    }

    @After
    fun tearDown() {
        phone.close()
        server.close()
    }

    @Test
    fun f2_playedIsNotConfirmedWhenTheServerIgnoredIt() = runBlocking {
        phone.complete(9)
        phone.sync()

        assertFalse("the server ignored mark_episode_completed", server.episode(9)!!.completed)
        assertTrue("the local played mark must stay pending", phone.repository.progress(9)!!.dirty)
        assertTrue(phone.repository.progress(9)!!.completed)
    }

    @Test
    fun f5_queuingAgainAfterPlayedKeepsTheEpisodeQueued() = runBlocking {
        phone.add(2)
        phone.sync()
        server.failNext(COMPLETED, Fault.Status(503)) // "played" is not delivered yet
        phone.repository.complete(2, 3_600_000L, phone.activeSession, removeFromQueue = true)
        phone.sync()
        phone.add(2) // the user changes their mind
        phone.elapsedMs += 24L * 3_600_000L // backoff over

        phone.sync()
        phone.sync()

        assertTrue("the explicit re-add wins", 2 in server.queueIds())
        assertTrue(2 in phone.localQueue())
    }

    @Test
    fun f6_retryResendsTheDeadLetteredPosition() = runBlocking {
        val car = SyncDevice(RuntimeEnvironment.getApplication(), server, "car")
        try {
            car.listen(3, 2_000, 2_000)
            car.sync()
            phone.open(3)
            car.listen(3, 2_005, 3_000)
            car.sync()
            phone.progress(3, 1_990, ProgressCause.SEEK) // stale rewind: dead-lettered (S42)
            phone.sync()
            phone.open(3) // the row adopts the server's 3000 meanwhile
            val letter = phone.database.syncV8Dao().deadLetters().single()

            phone.repository.retryDeadLetter(letter.id)

            assertEquals("the user's choice, not the adopted server position", 1_990_000L, phone.repository.progress(3)!!.positionMs)
        } finally {
            car.close()
        }
    }

    private companion object {
        const val COMPLETED = "/api/data/mark_episode_completed"
    }
}
