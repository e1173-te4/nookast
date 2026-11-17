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
            onEpisodeIdsMerged = { aliases ->
                val downloads = ru.e1173.nookast.offline.EpisodeDownloadManager(appContext)
                aliases.forEach { (old, new) ->
                    downloads.remapEpisodeId(old, new)
                    serverDownloads.remapEpisodeId(old, new)
                    downloadCleanupState.remapEpisodeId(old, new)
                }
            },
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

    internal val downloadCleanupState: ru.e1173.nookast.offline.CleanupPreferences by lazy {
        ru.e1173.nookast.offline.CleanupPreferences(appContext)
    }

    /**
     * On-device download cleanup (DEC-064). Played state comes from the server when it answers,
     * else from Room; it only ever deletes files on the phone.
     */
    internal val downloadCleanup: ru.e1173.nookast.offline.DownloadCleanupRunner by lazy {
        val preferences = ru.e1173.nookast.player.PlaybackPreferences(appContext)
        val api = PinePodsSyncApi(networkValidated = { syncScheduler.networkValidated() })
        ru.e1173.nookast.offline.DownloadCleanupRunner(
            downloads = {
                val manager = ru.e1173.nookast.offline.EpisodeDownloadManager(appContext)
                val local = manager.completedEpisodes()
                val cached = database.offlineCacheDao().episodesByPinePodsIds(local.map { it.episodeId }.filter { it > 0 })
                    .mapNotNull { row -> row.pinePodsEpisodeId?.let { it to row.podcastId } }.toMap()
                local.map { download ->
                    // The download record first, then the episode cache; the server fills the rest.
                    val podcastId = manager.podcastIdOf(download.episodeId) ?: cached[download.episodeId]?.takeIf { it > 0 }
                    ru.e1173.nookast.offline.CleanupCandidate(download.episodeId, podcastId, download.completedAtEpochMs)
                }
            },
            deleteLocal = { episodeId -> ru.e1173.nookast.offline.EpisodeDownloadManager(appContext).remove(episodeId) },
            rememberPodcastId = { episodeId, podcastId ->
                ru.e1173.nookast.offline.EpisodeDownloadManager(appContext).rememberPodcastId(episodeId, podcastId)
            },
            defaults = {
                ru.e1173.nookast.offline.CleanupRules(preferences.deletePlayedDownloads, preferences.deleteDownloadsAfterDays)
            },
            overrides = {
                preferences.cleanupOverrides().mapValues { (_, value) -> ru.e1173.nookast.offline.CleanupOverride(value.first, value.second) }
            },
            state = downloadCleanupState,
            serverPlayed = ru.e1173.nookast.offline.ServerPlayed { episodeId ->
                session.load()?.let { current ->
                    api.episode(current, episodeId)?.let { ru.e1173.nookast.offline.ServerAnswer(it.completed, it.podcastId) }
                }
            },
            localPlayed = { episodeId ->
                // A dirty row holds an unsent intent: newer than the server, it decides alone.
                val progress = database.syncV8Dao().progress(episodeId)
                when {
                    progress != null -> ru.e1173.nookast.offline.LocalPlayed(progress.completed, pending = progress.dirty)
                    else -> ru.e1173.nookast.offline.LocalPlayed(
                        database.offlineCacheDao().episodesByPinePodsIds(listOf(episodeId)).firstOrNull()?.completed ?: false,
                        pending = false,
                    )
                }
            },
            openEpisodeIds = {
                val open = ru.e1173.nookast.player.PlaybackActivity.openEpisodeId
                    // Before the player restores it on a cold start, the saved episode still counts as open.
                    ?: database.offlineCacheDao().getCurrentPlaybackContext()?.pinePodsEpisodeId
                val queue = syncRepository.localQueue()
                // The episode that plays next is about to be opened too.
                val next = queue.getOrNull((open?.let(queue::indexOf) ?: -1) + 1)
                setOfNotNull(open, next)
            },
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
