package ru.e1173.nookast.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.e1173.nookast.app.appGraph
import ru.e1173.nookast.core.sync.AccountIdentity
import ru.e1173.nookast.core.sync.SyncRepository.PendingSummary
import ru.e1173.nookast.core.sync.SyncStatus
import ru.e1173.nookast.offline.EpisodeDownloadManager
import ru.e1173.nookast.player.PlaybackItemFactory

/** One intent the engine could not deliver (SYNC.md §8.1), as the status screen shows it. */
data class DeadLetterUi(
    val id: Long,
    val aggregate: String,
    val title: String?,
    val reason: String,
    val retryable: Boolean,
)

/** Sign-out barrier (SYNC.md §10.2). */
sealed interface SignOutStep {
    data object Idle : SignOutStep

    /** Final delivery, at most [SIGN_OUT_FLUSH_MS], with "Don't wait". */
    data object Flushing : SignOutStep

    /** Something is still not on the server: keep it, discard it, or stay signed in. */
    data class Pending(val summary: PendingSummary) : SignOutStep

    /** Everything was delivered: sign out and clear as before. */
    data object Clean : SignOutStep
}

/** Another account's data is in the database (SYNC.md §10.3). */
data class QuarantineUi(
    val previousOrigin: String,
    val previousUserId: Int,
    val currentOrigin: String,
    val currentUserId: Int,
    val pending: PendingSummary,
) {
    /** Same user id: possibly the same server at a new address. */
    val sameUser: Boolean get() = previousUserId == currentUserId
}

/**
 * Account-level synchronization UI (3.5.2, 3.5.6, 3.5.9): status, dead letters, the sign-out
 * barrier and the quarantine decision. The engine itself lives in the process graph.
 */
class SyncAccountViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = application.appGraph
    private val repository = graph.syncRepository
    private val scheduler = graph.syncScheduler
    private val itemFactory = PlaybackItemFactory(graph.database, EpisodeDownloadManager(application))

    val status: StateFlow<SyncStatus> = scheduler.status

    val deadLetters: StateFlow<List<DeadLetterUi>> = repository.observeDeadLetters()
        .map { letters ->
            withContext(Dispatchers.IO) {
                letters.map { letter ->
                    val episodeId = letter.aggregateKey.toIntOrNull()?.takeIf { letter.aggregate == "progress" || letter.aggregate == "saved" }
                    DeadLetterUi(
                        id = letter.id,
                        aggregate = letter.aggregate,
                        title = episodeId?.let { itemFactory.findEntry(it)?.episodeTitle },
                        reason = letter.reasonCode,
                        retryable = letter.aggregate == "progress",
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _signOut = MutableStateFlow<SignOutStep>(SignOutStep.Idle)
    val signOut: StateFlow<SignOutStep> = _signOut.asStateFlow()

    private val _quarantine = MutableStateFlow<QuarantineUi?>(null)
    val quarantine: StateFlow<QuarantineUi?> = _quarantine.asStateFlow()

    private var flushJob: Job? = null

    init {
        viewModelScope.launch {
            scheduler.status.map { it.quarantined }.distinctUntilChanged().collect { quarantined ->
                _quarantine.value = if (quarantined) loadQuarantine() else null
            }
        }
    }

    fun retry(id: Long) {
        viewModelScope.launch {
            if (repository.retryDeadLetter(id)) scheduler.requestSync()
        }
    }

    fun discard(id: Long) {
        viewModelScope.launch { repository.discardDeadLetter(id) }
    }

    // --- Sign-out barrier (§10.2) ---

    fun beginSignOut() {
        if (_signOut.value != SignOutStep.Idle) return
        scheduler.setSuspended(true)
        _signOut.value = SignOutStep.Flushing
        flushJob = viewModelScope.launch {
            withTimeoutOrNull(SIGN_OUT_FLUSH_MS) { scheduler.flushNow() }
            settleSignOut()
        }
    }

    /** "Don't wait": stop the final delivery and show what is left. */
    fun skipFlush() {
        if (_signOut.value != SignOutStep.Flushing) return
        flushJob?.cancel()
        viewModelScope.launch { settleSignOut() }
    }

    private suspend fun settleSignOut() {
        val summary = withContext(Dispatchers.IO) { repository.pendingSummary() }
        _signOut.value = if (summary.total == 0) SignOutStep.Clean else SignOutStep.Pending(summary)
    }

    fun cancelSignOut() {
        flushJob?.cancel()
        _signOut.value = SignOutStep.Idle
        scheduler.setSuspended(false)
    }

    /** The sign-out finished (data kept or cleared); the engine resumes for the next session. */
    fun signOutFinished() {
        _signOut.value = SignOutStep.Idle
        scheduler.setSuspended(false)
    }

    // --- Quarantine (§10.3) ---

    private suspend fun loadQuarantine(): QuarantineUi? = withContext(Dispatchers.IO) {
        val previous = repository.account() ?: return@withContext null
        val session = graph.session.load() ?: return@withContext null
        val current = AccountIdentity.of(session)
        QuarantineUi(
            previousOrigin = previous.serverOrigin,
            previousUserId = previous.userId,
            currentOrigin = current.origin,
            currentUserId = current.userId,
            pending = repository.pendingSummary(),
        )
    }

    /** "The same server, its address changed": the data now belongs to the current session. */
    fun adoptCurrentAccount() {
        viewModelScope.launch {
            val session = withContext(Dispatchers.IO) { graph.session.load() } ?: return@launch
            val identity = AccountIdentity.of(session)
            repository.adoptAccount(identity.fingerprint, identity.origin, identity.userId)
            _quarantine.value = null
            scheduler.requestSync()
        }
    }

    /** After "back to the previous account" or "delete its data": let the engine look again. */
    fun quarantineResolved() {
        _quarantine.value = null
        scheduler.setSuspended(false)
        scheduler.requestSync()
    }

    companion object {
        const val SIGN_OUT_FLUSH_MS = 15_000L
    }
}
