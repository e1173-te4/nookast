package ru.e1173.nookast.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistEditPolicyTest {
    @Test
    fun `reorder uses moves without replacing the active playlist`() {
        assertEquals(
            listOf(
                PlaylistEdit.Move(fromIndex = 2, toIndex = 0),
            ),
            planPlaylistEdits(
                currentEpisodeIds = listOf(10, 20, 30),
                desiredEpisodeIds = listOf(30, 10, 20),
            ),
        )
    }

    @Test
    fun `removal and insertion retain all matching items`() {
        assertEquals(
            listOf(
                PlaylistEdit.Remove(index = 2),
                PlaylistEdit.Insert(index = 1, episodeId = 40),
            ),
            planPlaylistEdits(
                currentEpisodeIds = listOf(10, 20, 30),
                desiredEpisodeIds = listOf(10, 40, 20),
            ),
        )
    }

    @Test
    fun `rss-only negative ids do not crash the reconciliation (S-16)`() {
        // Crash seen on the emulator 25.10.2026: a podcast playlist with an RSS-only episode.
        assertEquals(
            listOf(PlaylistEdit.Remove(index = 1)),
            planPlaylistEdits(
                currentEpisodeIds = listOf(5815, -3, 5775),
                desiredEpisodeIds = listOf(5815, 5775),
            ),
        )
    }

    @Test
    fun `duplicates skip the round instead of crashing`() {
        assertEquals(null, planPlaylistEdits(listOf(1, 1), listOf(1)))
    }
}
