package ru.e1173.nookast.core.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.e1173.nookast.sync.SyncWorkScheduler

/** What the UI shows about synchronization (I12). */
data class SyncStatus(
    val lastSuccessWallMs: Long? = null,
    val lastFailure: SyncFailure? = null,
    val needsSignIn: Boolean = false,
    val running: Boolean = false,
    /** Another account's data is in the database; the engine waits for the user (§10.3). */
    val quarantined: Boolean = false,
)

/**
 * The one owner of synchronization in the process (docs/SYNC.md §9, I7).
 *
 * - One conflated trigger: any number of intents wake one pass.
 * - Polling follows the lifecycle: every 13 s while the UI is visible, every 30 s while playing,
 *   never otherwise (I13, S38).
 * - A validated network expires every backoff and syncs at once (S52).
 * - When the process stops being active with work pending, WorkManager insurance is armed.
 */
class SyncScheduler(
    private val context: Context,
    private val engine: SyncEngine,
    private val repository: SyncRepository,
    /** Work that follows a pass and shares its cadence, such as server-download cleanup (DEC-023). */
    private val afterPass: suspend () -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val trigger = Channel<Unit>(Channel.CONFLATED)
    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    @Volatile
    private var foreground = false

    @Volatile
    private var playing = false

    @Volatile
    private var started = false

    @Volatile
    private var suspended = false

    @Volatile
    private var lastValidatedNetwork: Network? = null

    /** JAECOO head unit (same detection as the steering-wheel keys). */
    private val inCar: Boolean by lazy {
        ru.e1173.nookast.player.detectBuiltInMediaProfileId(
            ru.e1173.nookast.player.currentMediaDeviceIdentity(context),
        ) != null
    }

    /** Wakes the engine; safe from any thread, cheap to call after every intent. */
    fun requestSync() {
        trigger.trySend(Unit)
    }

    fun setPlaying(isPlaying: Boolean) {
        val was = playing
        playing = isPlaying
        if (was != isPlaying) requestSync()
    }

    fun start() {
        if (started) return
        started = true
        scope.launch(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    foreground = true
                    requestSync()
                }

                override fun onStop(owner: LifecycleOwner) {
                    foreground = false
                    requestSync()
                }
            })
        }
        registerNetworkCallback()
        scope.launch {
            runCatching { engine.checkAccountNow() }
            loop()
        }
    }

    private suspend fun loop() {
        while (true) {
            val interval = when {
                foreground -> FOREGROUND_POLL_MS
                playing -> if (inCar) CAR_PLAYING_POLL_MS else PLAYING_POLL_MS
                else -> null
            }
            if (interval == null) {
                trigger.receive()
            } else {
                withTimeoutOrNull(interval) { trigger.receive() }
            }
            if (suspended) continue
            runPass()
            if (!foreground && !playing) armInsuranceIfPending()
        }
    }

    /**
     * Sign-out barrier and account decisions (§10.2, §10.3): while suspended, no pass runs,
     * so nothing is pulled or sent until the user decides. Resuming wakes the engine.
     */
    fun setSuspended(value: Boolean) {
        suspended = value
        if (!value) requestSync()
    }

    /** A session was just stored: check whose data the database holds before anything is written. */
    fun onSessionChanged() {
        scope.launch {
            runCatching { engine.checkAccountNow() }
            requestSync()
        }
    }

    /** One pass now, for the sign-out flush; runs even while suspended. */
    suspend fun flushNow(): SyncReport? = runPass()

    private suspend fun runPass(): SyncReport? {
        _status.value = _status.value.copy(running = true)
        val report = runCatching { engine.syncNow() }.getOrNull()
        _status.value = when {
            report?.quarantined == true -> _status.value.copy(running = false, quarantined = true)
            report == null -> _status.value.copy(running = false, lastFailure = SyncFailure.TRANSIENT)
            report.stoppedBy == SyncFailure.AUTH -> _status.value.copy(running = false, needsSignIn = true, lastFailure = SyncFailure.AUTH)
            report.failures.isEmpty() -> SyncStatus(lastSuccessWallMs = System.currentTimeMillis())
            else -> _status.value.copy(running = false, lastFailure = report.failures.values.first())
        }
        if (report != null && report.stoppedBy == null && !report.quarantined) {
            runCatching { withContext(Dispatchers.IO) { afterPass() } }
        }
        return report
    }

    private suspend fun armInsuranceIfPending() {
        val pending = withContext(Dispatchers.IO) {
            repository.dirtyProgress(1).isNotEmpty() || repository.queueIntents().isNotEmpty() ||
                repository.pendingSaved(1).isNotEmpty() || repository.pendingPodcastRemovals(1).isNotEmpty()
        }
        if (pending) SyncWorkScheduler.armInsurance(context)
    }

    private fun registerNetworkCallback() {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    // Only the transition to a validated network, not every capability update.
                    if (validated && network != lastValidatedNetwork) {
                        lastValidatedNetwork = network
                        scope.launch {
                            repository.expireBackoff()
                            requestSync()
                        }
                    }
                }
            })
        }
    }

    /** Whether the current default network passed validation (captive portal detection, §8). */
    fun networkValidated(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
    }

    private companion object {
        const val FOREGROUND_POLL_MS = 13_000L
        const val PLAYING_POLL_MS = 30_000L

        /** The car loses its modem at ignition off, often before the pause: deliver more often. */
        const val CAR_PLAYING_POLL_MS = 10_000L
    }
}
