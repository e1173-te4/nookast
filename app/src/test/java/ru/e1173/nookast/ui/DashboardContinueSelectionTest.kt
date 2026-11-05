package ru.e1173.nookast.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.e1173.nookast.data.local.DashboardContinueLocalEntry

class DashboardContinueSelectionTest {
    @Test
    fun keepsLatestThirteenStartedIncompleteEpisodesByRealTimestamp() {
        val valid = (1..15).map { episodeId ->
            localEntry(
                episodeId = episodeId,
                positionMs = episodeId * 1_000L,
                updatedAtEpochMs = episodeId * 10L,
            )
        }
        val invalid = listOf(
            localEntry(101, positionMs = 0L, updatedAtEpochMs = 1_000L),
            localEntry(102, positionMs = 4_000L, updatedAtEpochMs = 1_001L, completed = true),
            localEntry(103, positionMs = 60_000L, updatedAtEpochMs = 1_002L, durationMs = 60_000L),
        )

        val selected = selectDashboardContinueEpisodes(valid + invalid)

        assertEquals(13, selected.size)
        assertEquals((15 downTo 3).toList(), selected.map { it.entry.episodeId })
        assertTrue(selected.zipWithNext().all { (first, second) ->
            first.lastListenedAtEpochMs >= second.lastListenedAtEpochMs
        })
    }

    @Test
    fun localProgressOverridesCachedProgressAndCompletion() {
        val selected = selectDashboardContinueEpisodes(
            listOf(
                localEntry(
                    episodeId = 7,
                    positionMs = 30_000L,
                    updatedAtEpochMs = 50L,
                    durationMs = 120_000L,
                    cachedCompleted = true,
                    cachedListenedSeconds = 3.0,
                ),
            ),
        ).single()

        assertEquals(30.0, selected.entry.listenDurationSeconds ?: -1.0, 0.0)
        assertFalse(selected.entry.completed ?: true)
        assertEquals(0.25f, selected.progressFraction, 0.0001f)
    }

    private fun localEntry(
        episodeId: Int,
        positionMs: Long,
        updatedAtEpochMs: Long,
        completed: Boolean = false,
        durationMs: Long? = 120_000L,
        cachedCompleted: Boolean = false,
        cachedListenedSeconds: Double? = null,
    ) = DashboardContinueLocalEntry(
        episodeId = episodeId,
        episodeTitle = "Episode $episodeId",
        podcastName = "Podcast",
        artworkUrl = "",
        mediaUrl = "https://example.invalid/$episodeId.mp3",
        durationSeconds = 120,
        listenDurationSeconds = cachedListenedSeconds,
        completed = cachedCompleted,
        progressCompleted = completed,
        description = "",
        publishedAt = "",
        downloaded = false,
        refreshedAtEpochMs = 1L,
        positionMs = positionMs,
        durationMs = durationMs,
        lastListenedAtEpochMs = updatedAtEpochMs,
    )
}
