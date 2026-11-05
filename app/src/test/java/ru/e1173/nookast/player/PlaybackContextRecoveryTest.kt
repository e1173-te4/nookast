package ru.e1173.nookast.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.e1173.nookast.data.local.CurrentPlaybackContextEntity

class PlaybackContextRecoveryTest {
    @Test
    fun pinePodsContextRestoresRealServerIdentity() {
        assertEquals(42, restorablePlaybackEpisodeId(context(pinePodsEpisodeId = 42)))
    }

    @Test
    fun rssContextRestoresDeterministicLocalIdentity() {
        val restored = restorablePlaybackEpisodeId(
            context(pinePodsEpisodeId = null, cacheKey = "rss:12:stable-hash"),
        )

        assertTrue(requireNotNull(restored) < 0)
        assertEquals(
            restored,
            restorablePlaybackEpisodeId(
                context(pinePodsEpisodeId = null, cacheKey = "rss:12:stable-hash"),
            ),
        )
    }

    @Test
    fun completedContextNeverRestoresMiniPlayer() {
        assertNull(restorablePlaybackEpisodeId(context(pinePodsEpisodeId = 42, completed = true)))
    }

    private fun context(
        pinePodsEpisodeId: Int?,
        cacheKey: String = "pinepods:42",
        completed: Boolean = false,
    ) = CurrentPlaybackContextEntity(
        episodeCacheKey = cacheKey,
        pinePodsEpisodeId = pinePodsEpisodeId,
        podcastId = 7,
        episodeTitle = "Episode",
        podcastName = "Podcast",
        artworkUrl = "https://cdn.example.test/art.jpg",
        mediaUrl = "https://media.example.test/episode.mp3",
        description = "Description",
        publishedAt = "2026-10-17T00:00:00Z",
        positionMs = 15_000,
        durationMs = 60_000,
        completed = completed,
        updatedAtEpochMs = 1_000,
    )
}
