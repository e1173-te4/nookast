package ru.e1173.nookast.app

import android.content.Context
import ru.e1173.nookast.NookastApplication
import ru.e1173.nookast.core.session.SessionRepository
import ru.e1173.nookast.core.session.SessionStore
import ru.e1173.nookast.core.sync.PinePodsSyncApi
import ru.e1173.nookast.core.sync.PlaybackSessions
import ru.e1173.nookast.core.sync.SyncEngine
import ru.e1173.nookast.core.sync.SyncRepositories
import ru.e1173.nookast.core.sync.SyncRepository
import ru.e1173.nookast.core.sync.SyncScheduler
import ru.e1173.nookast.data.LibraryRefresher
import ru.e1173.nookast.data.local.SyncDatabase
import ru.e1173.nookast.diagnostics.ControlJournal
import ru.e1173.nookast.offline.ServerDownloads
import ru.e1173.nookast.player.ArtworkBitmapRepository
import ru.e1173.nookast.player.LegacyMetadataBroadcast

/**
 * Manual dependency graph (ADR-0003): one instance of each process-wide service, created lazily.
 * ViewModels, the playback service and workers obtain dependencies here instead of constructing
 * their own clients, stores and databases.
 */
class AppGraph(context: Context) {
    private val appContext = context.applicationContext

    val database: SyncDatabase by lazy { SyncDatabase.open(appContext) }

    val session: SessionRepository by lazy { SessionRepository(SessionStore(appContext)) }

    val controlJournal: ControlJournal by lazy { ControlJournal(appContext) }

    val legacyMetadataBroadcast: LegacyMetadataBroadcast by lazy {
        LegacyMetadataBroadcast(appContext, controlJournal)
    }

    /** Synchronization redesign (docs/SYNC.md): one repository, engine and scheduler per process. */
    val syncRepository: SyncRepository by lazy { SyncRepositories.of(database) }

    val syncEngine: SyncEngine by lazy {
        SyncEngine(
            repository = syncRepository,
            api = PinePodsSyncApi(networkValidated = { syncScheduler.networkValidated() }),
            session = { session.load() },
            activeSession = PlaybackSessions::currentFor,
        )
    }

    /** The one library refresh for the podcast screen and the background worker (N-04). */
    val libraryRefresher: LibraryRefresher by lazy {
        LibraryRefresher(database, ArtworkBitmapRepository.get(appContext), quarantined = { syncRepository.quarantined })
    }

    /** Episodes stored on the PinePods server and the cleanup of temporary ones (DEC-023). */
    val serverDownloads: ServerDownloads by lazy {
        ServerDownloads(
            appContext,
            session = { session.load() },
            progressCounter = { episodeId -> database.syncV8Dao().progress(episodeId)?.intentCounter ?: 0L },
        )
    }

    val syncScheduler: SyncScheduler by lazy {
        SyncScheduler(appContext, syncEngine, syncRepository) {
            serverDownloads.refreshIfStale()
            serverDownloads.cleanupPlayed { episodeId, markCounter ->
                val progress = database.syncV8Dao().progress(episodeId)
                progress != null && progress.completed && progress.intentCounter > markCounter
            }
        }
    }

    companion object {
        @Volatile
        private var fallback: AppGraph? = null

        /**
         * Graph for contexts whose application is not [NookastApplication] (plain Robolectric
         * applications in unit tests). Production always uses the application-owned graph.
         */
        internal fun fallback(context: Context): AppGraph = fallback ?: synchronized(this) {
            fallback ?: AppGraph(context).also { fallback = it }
        }
    }
}

val Context.appGraph: AppGraph
    get() = (applicationContext as? NookastApplication)?.graph ?: AppGraph.fallback(this)
