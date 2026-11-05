package ru.e1173.nookast.data.local

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.test.runTest
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
import ru.e1173.nookast.data.remote.PodcastEpisodeProgress

/** Library cache rules of plan 3.5.8: RSS twins (N-03) and the full episode list (N-05). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LibraryCacheRoomTest {
    private lateinit var database: SyncDatabase
    private lateinit var cache: OfflinePodcastCacheRepository
    private val promoted = mutableListOf<Map<String, Int>>()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cache = OfflinePodcastCacheRepository(
            podcastDao = database.podcastDao(),
            offlineCacheDao = database.offlineCacheDao(),
            promoteDeferredIntents = { promoted += it; it.size },
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun anRssTwinGoesOncePinePodsKnowsTheEpisode() = runTest {
        val key = rssEpisodeCacheKey(7, "guid-1", "https://cdn.example/1.mp3", "One", "2026-10-20")
        database.offlineCacheDao().upsertEpisodes(listOf(rssRow(key)))

        // The feed answers 304 from now on; only PinePods learns about the episode.
        cache.replacePinePodsEpisodes(
            podcastId = 7,
            episodes = listOf(
                PodcastEpisodeProgress(
                    episodeId = 501,
                    title = "One",
                    publishedAt = "2026-10-20",
                    durationSeconds = 600,
                    listenedSeconds = 0.0,
                    completed = false,
                    mediaUrl = "https://cdn.example/1.mp3",
                ),
            ),
            refreshedAtEpochMs = 1_000,
        )

        val episodes = database.offlineCacheDao().getEpisodes(7)
        assertEquals("listed once", listOf(501), episodes.map { it.pinePodsEpisodeId })
        assertEquals("intents move before the row goes", listOf(mapOf(key to 501)), promoted)
    }

    @Test
    fun theFullEpisodeListIsReadAgainOnlyWhenTheCountChangesOrADayPasses() = runTest {
        assertTrue(cache.isFullEpisodeListDue(7, 120, nowEpochMs = 0))
        cache.recordFullEpisodeList(7, 120, refreshedAtEpochMs = 0)

        assertFalse(cache.isFullEpisodeListDue(7, 120, nowEpochMs = 60 * 60_000L))
        assertTrue("a new episode", cache.isFullEpisodeListDue(7, 121, nowEpochMs = 60 * 60_000L))
        assertTrue("a day later", cache.isFullEpisodeListDue(7, 120, nowEpochMs = FULL_EPISODE_LIST_TTL_MS))
    }

    private fun rssRow(key: String) = PodcastEpisodeCacheEntity(
        cacheKey = key,
        podcastId = 7,
        pinePodsEpisodeId = null,
        guid = "guid-1",
        source = PodcastCacheSource.RSS,
        title = "One",
        description = "",
        publishedAt = "2026-10-20",
        durationSeconds = 600,
        listenedSeconds = 0.0,
        completed = false,
        podcastName = "Podcast",
        artworkUrl = "",
        mediaUrl = "https://cdn.example/1.mp3",
        downloaded = false,
        refreshedAtEpochMs = 0,
    )
}
