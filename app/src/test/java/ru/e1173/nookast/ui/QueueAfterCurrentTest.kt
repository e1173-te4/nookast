package ru.e1173.nookast.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueAfterCurrentTest {
    @Test
    fun `after current lands right behind the playing episode or at the top`() {
        assertEquals(2, queueIndexAfter(listOf(5, 7, 9), currentEpisodeId = 7))
        assertEquals(3, queueIndexAfter(listOf(5, 7, 9), currentEpisodeId = 9))
        assertEquals(0, queueIndexAfter(listOf(5, 7, 9), currentEpisodeId = 42))
        assertEquals(0, queueIndexAfter(emptyList(), currentEpisodeId = 7))
    }
}
