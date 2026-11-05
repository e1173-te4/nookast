package ru.e1173.nookast.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackQueuePolicyTest {
    @Test
    fun followsServerOrderWhenCurrentEpisodeIsStillQueued() {
        assertEquals(
            listOf(30, 20, 10),
            desiredPlaybackOrder(currentEpisodeId = 20, queueEpisodeIds = listOf(30, 20, 10)),
        )
    }

    @Test
    fun keepsCurrentEpisodeWhenItWasRemovedOnAnotherClient() {
        assertEquals(
            listOf(20, 30, 10),
            desiredPlaybackOrder(currentEpisodeId = 20, queueEpisodeIds = listOf(30, 10)),
        )
    }

    @Test
    fun normalizesDuplicateOrInvalidServerItems() {
        assertEquals(
            listOf(20, 30, 10),
            desiredPlaybackOrder(
                currentEpisodeId = 20,
                queueEpisodeIds = listOf(30, 0, 30, -1, 10),
            ),
        )
    }

    @Test
    fun emptyRemoteQueueStillAllowsCurrentEpisodeToFinish() {
        assertEquals(
            listOf(20),
            desiredPlaybackOrder(currentEpisodeId = 20, queueEpisodeIds = emptyList()),
        )
    }
}
