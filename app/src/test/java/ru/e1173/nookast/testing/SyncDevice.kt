package ru.e1173.nookast.testing

import android.content.Context
import androidx.room.Room
import ru.e1173.nookast.auth.AuthSession
import ru.e1173.nookast.core.sync.PinePodsSyncApi
import ru.e1173.nookast.core.sync.ProgressIntent
import ru.e1173.nookast.core.sync.QueueAnchor
import ru.e1173.nookast.core.sync.QueueIntent
import ru.e1173.nookast.core.sync.SyncClock
import ru.e1173.nookast.core.sync.SyncEngine
import ru.e1173.nookast.core.sync.SyncReport
import ru.e1173.nookast.core.sync.SyncRepository
import ru.e1173.nookast.data.local.SyncDatabase
import ru.e1173.nookast.sync.ProgressCause
import kotlin.random.Random

/**
 * One client device in a scenario test: its own in-memory Room v8 and the synchronization engine
 * of docs/SYNC.md (SyncRepository + SyncEngine), talking to a shared [FakePinePodsServer].
 *
 * Speaks in user-level operations. Time is virtual: [elapsedMs] drives backoff.
 */
class SyncDevice(
    context: Context,
    private val server: FakePinePodsServer,
    val name: String,
    /** Mutable so a scenario can sign in to another account on the same database (§10.3). */
    var session: AuthSession = server.session(),
) : AutoCloseable {
    val database: SyncDatabase = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    var elapsedMs: Long = 1_000_000L
    var wallMs: Long = 1_793_000_000_000L
    var bootCount: Int = 1

    /** Playback session currently engaged on this device (§5.2); null when nothing plays. */
    var activeSession: String? = "$name-session-1"

    private val clock = object : SyncClock {
        override fun elapsedRealtimeMs() = elapsedMs
        override fun wallTimeMs() = wallMs
        override fun bootCount() = bootCount
    }
    val repository = SyncRepository(database, clock)
    private val engine = SyncEngine(
        repository = repository,
        api = PinePodsSyncApi(),
        session = { session },
        activeSession = { activeSession },
        random = Random(1),
    )

    // --- Playback progress ---

    suspend fun progress(episodeId: Int, seconds: Int, cause: ProgressCause, durationSeconds: Int = 3_600) {
        val positionMs = seconds * 1_000L
        val durationMs = durationSeconds * 1_000L
        val sessionId = activeSession ?: "$name-detached"
        when (cause) {
            ProgressCause.COMPLETE -> complete(episodeId, durationSeconds)
            ProgressCause.UNCOMPLETE -> repository.recordProgress(episodeId, ProgressIntent.Uncomplete)
            ProgressCause.SEEK -> {
                val current = repository.progress(episodeId)?.positionMs ?: 0
                val intent = if (positionMs < current) {
                    ProgressIntent.Rewind(positionMs, durationMs, sessionId)
                } else {
                    ProgressIntent.Passive(positionMs, durationMs, sessionId)
                }
                repository.recordProgress(episodeId, intent)
            }
            else -> repository.recordProgress(episodeId, ProgressIntent.Passive(positionMs, durationMs, sessionId))
        }
    }

    suspend fun listen(episodeId: Int, fromSeconds: Int, toSeconds: Int, everySeconds: Int = 5) {
        var position = fromSeconds
        while (position <= toSeconds) {
            progress(episodeId, position, ProgressCause.PERIODIC)
            position += everySeconds
        }
    }

    suspend fun complete(episodeId: Int, durationSeconds: Int = 3_600) =
        repository.complete(episodeId, durationSeconds * 1_000L, activeSession)

    /** Opening an episode in the player does a control read (docs/SYNC.md §5.10). */
    suspend fun open(episodeId: Int) {
        val ticket = repository.reserveTicket()
        PinePodsSyncApi().episode(session, episodeId)?.let { repository.observeProgress(ticket, it) }
    }

    /** Resume position as the player would use it (§5.9). Played episodes start from 0. */
    suspend fun resumeSeconds(episodeId: Int): Long {
        val state = repository.progress(episodeId) ?: return 0
        return if (state.completed) 0 else state.positionMs / 1_000
    }

    // --- Queue ---

    suspend fun add(episodeId: Int, index: Int? = null) = repository.addQueueIntent { counter, op, local ->
        QueueIntent.Add(episodeId, anchorFor(local - episodeId, index), counter, op)
    }

    suspend fun remove(episodeId: Int) = repository.addQueueIntent { counter, op, _ ->
        QueueIntent.Remove(episodeId, counter, op)
    }

    suspend fun move(episodeId: Int, index: Int) = repository.addQueueIntent { counter, op, local ->
        QueueIntent.Move(episodeId, anchorFor(local - episodeId, index), counter, op)
    }

    suspend fun clear() = repository.addQueueIntent { counter, op, local ->
        QueueIntent.Clear(local.toSet(), counter, op)
    }

    /** UI drops index into an anchor: first, after the item above, or last (§6.1). */
    private fun anchorFor(others: List<Int>, index: Int?): QueueAnchor = when {
        index == null -> QueueAnchor.LAST
        index <= 0 -> QueueAnchor.FIRST
        index > others.size -> QueueAnchor.LAST
        else -> QueueAnchor.after(others[index - 1])
    }

    suspend fun localQueue(): List<Int> = repository.localQueue()

    suspend fun pendingCount(): Int =
        repository.dirtyProgress(Int.MAX_VALUE).size + repository.queueIntents().size

    // --- Synchronization ---

    suspend fun sync(): SyncReport = engine.syncNow()

    /** Several passes with backoff time passing, as the app would over a few minutes. */
    suspend fun syncRepeatedly(rounds: Int = 5): SyncReport {
        var last = sync()
        repeat(rounds - 1) {
            elapsedMs += 6 * 60_000L
            wallMs += 6 * 60_000L
            last = sync()
        }
        return last
    }

    override fun close() = database.close()
}
