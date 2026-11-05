package ru.e1173.nookast.core.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.e1173.nookast.auth.AuthSession

/** Why there is no usable session right now. */
enum class SessionLossReason {
    /** Never signed in, or signed out explicitly. */
    NONE,

    /** The stored session could not be decrypted and was deleted; the user must sign in again. */
    CORRUPT,

    /** A transient keystore failure; the session is kept on disk and will be retried. */
    TEMPORARILY_UNAVAILABLE,
}

data class SessionState(
    val session: AuthSession?,
    val lossReason: SessionLossReason = SessionLossReason.NONE,
)

/**
 * Process-wide owner of the authenticated session.
 *
 * The session is decrypted at most once per process (plus retries after transient keystore
 * failures) and then served from memory, so hot paths such as media DataSpec resolution, artwork
 * cache keys and periodic progress ticks never touch AndroidKeyStore.
 */
class SessionRepository internal constructor(
    private val read: () -> SessionRead,
    private val write: (AuthSession) -> Unit,
    private val erase: () -> Boolean,
) {
    constructor(store: SessionStore) : this(store::read, store::save, store::clear)

    private val lock = Any()
    private var loaded = false
    private var attempted = false
    private val _state = MutableStateFlow(SessionState(session = null))

    /**
     * Observable session state. Accessing it performs the very first read if none happened yet,
     * but never retries a transient failure; retries happen only through [load].
     */
    val state: StateFlow<SessionState>
        get() {
            if (!attempted) ensureLoaded()
            return _state.asStateFlow()
        }

    /**
     * Returns the current session from memory. The first call per process (or the first call after
     * a transient failure) reads the encrypted store; call [warmUp] off the main thread at startup
     * so that this never blocks the UI.
     */
    fun load(): AuthSession? {
        ensureLoaded()
        return _state.value.session
    }

    /** Reads the store once. Safe to call repeatedly and from any thread. */
    fun warmUp() {
        ensureLoaded()
    }

    fun save(session: AuthSession) {
        synchronized(lock) {
            write(session)
            loaded = true
            _state.value = SessionState(session)
        }
    }

    fun clear(): Boolean = synchronized(lock) {
        val cleared = erase()
        loaded = true
        _state.value = SessionState(session = null)
        cleared
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            attempted = true
            _state.value = when (val result = read()) {
                is SessionRead.Present -> {
                    loaded = true
                    SessionState(result.session)
                }
                SessionRead.Absent -> {
                    loaded = true
                    SessionState(session = null)
                }
                is SessionRead.Corrupt -> {
                    loaded = true
                    SessionState(session = null, lossReason = SessionLossReason.CORRUPT)
                }
                // Not marked loaded: the next call retries the keystore.
                is SessionRead.Unavailable ->
                    SessionState(session = null, lossReason = SessionLossReason.TEMPORARILY_UNAVAILABLE)
            }
        }
    }
}
