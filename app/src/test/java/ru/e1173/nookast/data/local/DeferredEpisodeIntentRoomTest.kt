package ru.e1173.nookast.data.local

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.e1173.nookast.sync.ProgressCause
import ru.e1173.nookast.sync.QueueMutation

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class DeferredEpisodeIntentRoomTest {
    private lateinit var database: SyncDatabase
    private lateinit var store: SyncStore

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = SyncStore(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deferredDaoKeepsOnlyNewestIntentPerKind() = runTest {
        val key = rssEpisodeCacheKey(12, "guid", "", "Episode", "2026-10-18")

        // Since v8 the counter orders intents: the later action wins, caller sequences are ignored.
        assertTrue(store.stageDeferredSaved(key, true, sequence = 2, createdAtEpochMs = 20))
        assertTrue(store.stageDeferredSaved(key, false, sequence = 1, createdAtEpochMs = 10))

        val saved = database.deferredEpisodeIntentDao().getForEpisode(key).single()
        assertEquals(false, saved.desiredState)
    }

    @Test
    fun resolvedEpisodePromotesEveryServerIntentIntoOrdinaryOutboxes() = runTest {
        val key = rssEpisodeCacheKey(12, "guid", "", "Episode", "2026-10-18")
        database.offlineCacheDao().upsertEpisodes(listOf(pinePodsEpisode()))
        store.stageDeferredProgress(
            episodeCacheKey = key,
            positionMs = 90_000,
            durationMs = 120_000,
            cause = ProgressCause.PAUSE,
            sequence = 10,
            createdAtEpochMs = 100,
        )
        store.stageDeferredQueue(
            episodeCacheKey = key,
            desiredQueued = true,
            destinationIndex = 0,
            sequence = 11,
            createdAtEpochMs = 110,
        )
        store.stageDeferredSaved(
            episodeCacheKey = key,
            desiredSaved = true,
            sequence = 12,
            createdAtEpochMs = 120,
        )

        assertEquals(3, store.promoteDeferredEpisodeIntents(mapOf(key to 42)))

        assertEquals(0, database.deferredEpisodeIntentDao().pendingCount())
        assertTrue(requireNotNull(database.syncV8Dao().progress(42)).dirty)
        assertEquals(listOf(42), database.queueDao().getQueue().map { it.episodeId })
        assertEquals(42, database.syncV8Dao().queueIntents().single().episodeId)
        assertEquals(true, database.savedEpisodeDao().getReady(Long.MAX_VALUE, 10).single().desiredSaved)
    }

    @Test
    fun completionAndQueueRemovalStayAtomicWhenIdentityIsResolved() = runTest {
        val key = rssEpisodeCacheKey(12, "guid", "", "Episode", "2026-10-18")
        store.stageDeferredCompletionAndQueueRemoval(
            episodeCacheKey = key,
            positionMs = 120_000,
            durationMs = 120_000,
            progressSequence = 20,
            queueSequence = 21,
            createdAtEpochMs = 200,
        )

        assertEquals(2, store.promoteDeferredEpisodeIntents(mapOf(key to 42)))

        val progress = requireNotNull(database.syncV8Dao().progress(42))
        assertTrue(progress.completed)
        assertEquals(SyncV8.IntentKind.COMPLETE, progress.intentKind)
        val queue = database.syncV8Dao().queueIntents().single()
        assertEquals(SyncV8.QueueKind.REMOVE, queue.kind)
        assertEquals(42, queue.episodeId)
        assertEquals(0, database.deferredEpisodeIntentDao().pendingCount())
    }

    @Test
    fun olderDeferredSavedIntentCannotOverwriteNewerAddressableIntent() = runTest {
        val key = rssEpisodeCacheKey(12, "guid", "", "Episode", "2026-10-18")
        store.stageDeferredSaved(key, desiredSaved = true, sequence = 10, createdAtEpochMs = 100)
        store.stageSavedEpisode(
            SavedEpisodeOutboxEntity(
                episodeId = 42,
                desiredSaved = false,
                sequence = 20,
                createdAtEpochMs = 200,
            ),
        )

        assertEquals(1, store.promoteDeferredEpisodeIntents(mapOf(key to 42)))

        val saved = requireNotNull(database.savedEpisodeDao().get(42))
        assertEquals(false, saved.desiredSaved)
    }

    @Test
    fun olderDeferredQueueIntentCannotRegressNewerAddressableIntent() = runTest {
        val key = rssEpisodeCacheKey(12, "guid", "", "Episode", "2026-10-18")
        database.offlineCacheDao().upsertEpisodes(listOf(pinePodsEpisode()))
        store.stageDeferredQueue(
            key,
            desiredQueued = true,
            destinationIndex = 0,
            sequence = 10,
            createdAtEpochMs = 100,
        )
        store.enqueueQueueMutation(
            QueueMutation.Remove(
                episodeId = 42,
                operationId = "newer-remove",
                sequence = 20,
            ),
            createdAtEpochMs = 200,
        )

        assertEquals(1, store.promoteDeferredEpisodeIntents(mapOf(key to 42)))

        assertEquals(listOf(SyncV8.QueueKind.REMOVE), database.syncV8Dao().queueIntents().map { it.kind })
        assertTrue(database.queueDao().getQueue().isEmpty())
    }

    private fun pinePodsEpisode() = PodcastEpisodeCacheEntity(
        cacheKey = "pinepods:42",
        podcastId = 12,
        pinePodsEpisodeId = 42,
        guid = "guid",
        source = PodcastCacheSource.PINEPODS,
        title = "Episode",
        description = "Description",
        publishedAt = "2026-10-18",
        durationSeconds = 120,
        listenedSeconds = 0.0,
        completed = false,
        podcastName = "Show",
        artworkUrl = "https://example.test/art.jpg",
        mediaUrl = "https://example.test/episode.mp3",
        downloaded = false,
        refreshedAtEpochMs = 100,
    )
}
