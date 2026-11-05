package ru.e1173.nookast.core.session

import java.security.KeyStoreException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.e1173.nookast.auth.AuthSession

class SessionRepositoryTest {
    private val session = AuthSession(serverUrl = "https://pods.example", userId = 7, apiKey = "test-key")

    @Test
    fun decryptsOnceAndServesFromMemory() {
        var reads = 0
        val repository = SessionRepository(
            read = { reads++; SessionRead.Present(session) },
            write = {},
            erase = { true },
        )

        repeat(50) { assertEquals(session, repository.load()) }

        assertEquals(1, reads)
    }

    @Test
    fun transientKeystoreFailureIsRetriedAndNotTreatedAsSignOut() {
        var attempt = 0
        val repository = SessionRepository(
            read = {
                attempt++
                if (attempt == 1) SessionRead.Unavailable(KeyStoreException("busy")) else SessionRead.Present(session)
            },
            write = {},
            erase = { error("must not erase on a transient failure") },
        )

        assertNull(repository.load())
        assertEquals(SessionLossReason.TEMPORARILY_UNAVAILABLE, repository.state.value.lossReason)
        assertEquals(session, repository.load())
        assertEquals(SessionLossReason.NONE, repository.state.value.lossReason)
    }

    @Test
    fun corruptSessionIsReportedOnceAndNotRetried() {
        var reads = 0
        val repository = SessionRepository(
            read = { reads++; SessionRead.Corrupt(AEADBadTagException()) },
            write = {},
            erase = { true },
        )

        assertNull(repository.load())
        assertNull(repository.load())
        assertEquals(1, reads)
        assertEquals(SessionLossReason.CORRUPT, repository.state.value.lossReason)
    }

    @Test
    fun saveAndClearUpdateTheCachedState() {
        var stored: AuthSession? = null
        val repository = SessionRepository(
            read = { SessionRead.Absent },
            write = { stored = it },
            erase = { stored = null; true },
        )

        assertNull(repository.load())
        repository.save(session)
        assertEquals(session, stored)
        assertEquals(session, repository.load())
        assertTrue(repository.clear())
        assertNull(repository.load())
        assertEquals(SessionLossReason.NONE, repository.state.value.lossReason)
    }

    @Test
    fun onlyUnrecoverableErrorsArePermanent() {
        assertTrue(SessionStore.isPermanent(AEADBadTagException()))
        assertTrue(SessionStore.isPermanent(UnrecoverableKeyException()))
        assertTrue(SessionStore.isPermanent(JSONException("bad")))
        assertFalse(SessionStore.isPermanent(KeyStoreException("busy")))
        assertFalse(SessionStore.isPermanent(ProviderException("keystore2 died")))
        assertFalse(SessionStore.isPermanent(RuntimeException("binder")))
    }
}
