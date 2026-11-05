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
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.e1173.nookast.core.sync.AccountIdentity
import ru.e1173.nookast.testing.FakePinePodsServer
import ru.e1173.nookast.testing.SyncDevice

/** Account fingerprint, quarantine and dead-letter retry (docs/SYNC.md §8.1, §10; plan 3.5.2, 3.5.9). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AccountScenariosTest {
    private lateinit var server: FakePinePodsServer
    private lateinit var phone: SyncDevice

    @Before
    fun setUp() {
        server = FakePinePodsServer().start()
        (1..4).forEach { server.addEpisode(it) }
        phone = SyncDevice(RuntimeEnvironment.getApplication(), server, "phone")
    }

    @After
    fun tearDown() {
        phone.close()
        server.close()
    }

    @Test
    fun s20_anotherAccountsDataIsNeitherSentNorPulled() = runBlocking {
        phone.sync() // adopts the first account
        phone.listen(1, 300, 300)
        phone.session = server.session(userId = 77) // signed out keeping changes, then into another account

        val report = phone.sync()

        assertTrue(report.quarantined)
        assertTrue("the other account's position must not reach this one", (server.episode(1)!!.listenSeconds ?: 0) == 0)
        assertEquals(1, phone.repository.pendingSummary().progress)

        // Listening under the new session must not add intents to the previous account's data.
        phone.listen(2, 50, 50)
        assertEquals(null, phone.repository.progress(2))
    }

    @Test
    fun s19_signingInAgainToTheSameAccountResumesDelivery() = runBlocking {
        phone.sync()
        phone.listen(2, 120, 120)
        phone.session = server.session(apiKey = "key-2nd") // same user, new key

        val report = phone.sync()

        assertFalse(report.quarantined)
        assertEquals(120, server.episode(2)!!.listenSeconds)
    }

    @Test
    fun confirmingTheSameServerAtANewAddressAdoptsTheData() = runBlocking {
        phone.sync()
        phone.listen(3, 200, 200)
        phone.session = server.session(userId = 77)
        assertTrue(phone.sync().quarantined)

        val identity = AccountIdentity.of(phone.session)
        phone.repository.adoptAccount(identity.fingerprint, identity.origin, identity.userId)

        assertFalse(phone.sync().quarantined)
    }

    @Test
    fun aDeadLetteredProgressCanBeSentAgain() = runBlocking {
        server.deleteEpisode(4) // record_listen_duration answers 500 FK: dead letter (S53)
        phone.listen(4, 600, 600)
        syncUntilDeadLettered()
        assertEquals(1, phone.repository.deadLetterCount())
        server.addEpisode(4) // restored on the server, the user presses "Retry"

        val letter = phone.database.syncV8Dao().deadLetters().single()
        assertTrue(phone.repository.retryDeadLetter(letter.id))
        phone.sync()

        assertEquals(0, phone.repository.deadLetterCount())
        assertEquals(600, server.episode(4)!!.listenSeconds)
    }

    /** PERMANENT failures are dead-lettered after a few attempts spread over the backoff (§8). */
    private suspend fun syncUntilDeadLettered() {
        repeat(6) {
            phone.sync()
            if (phone.repository.deadLetterCount() > 0) return
            phone.elapsedMs += 24L * 3_600_000L
        }
    }

    @Test
    fun discardingADeadLetterKeepsTheLocalPosition() = runBlocking {
        server.deleteEpisode(4)
        phone.listen(4, 600, 600)
        syncUntilDeadLettered()
        val letter = phone.database.syncV8Dao().deadLetters().single()

        phone.repository.discardDeadLetter(letter.id)

        assertEquals(0, phone.repository.pendingSummary().total)
        assertEquals(600_000L, phone.repository.progress(4)!!.positionMs)
    }
}
