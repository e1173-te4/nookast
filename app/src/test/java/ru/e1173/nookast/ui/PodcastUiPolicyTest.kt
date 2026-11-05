package ru.e1173.nookast.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.e1173.nookast.data.local.PodcastSubscriptionEntity
import ru.e1173.nookast.offline.EpisodeDownloadState
import ru.e1173.nookast.preferences.PodcastSortMode

class PodcastUiPolicyTest {
    @Test
    fun titleSortGroupsCyrillicThenLatinThenOther() {
        val sorted = sortPodcastSubscriptions(
            listOf(
                podcast(1, "123 waves"),
                podcast(2, "beta"),
                podcast(3, "Ясно"),
                podcast(4, "Альфа"),
                podcast(5, "Alpha"),
            ),
            PodcastSortMode.TITLE,
        )

        assertEquals(listOf("Альфа", "Ясно", "Alpha", "beta", "123 waves"), sorted.map { it.name })
    }

    @Test
    fun updatedSortUsesNewestFirstAndStableTitleTieBreaker() {
        val sorted = sortPodcastSubscriptions(
            listOf(
                podcast(1, "Бета", "2026-10-14T10:00:00Z"),
                podcast(2, "Альфа", "2026-10-14T10:00:00Z"),
                podcast(3, "Gamma", "2026-10-16T10:00:00Z"),
            ),
            PodcastSortMode.UPDATED,
        )

        assertEquals(listOf(3, 2, 1), sorted.map { it.podcastId })
    }

    @Test
    fun updatedOrderStaysStableDuringRefreshAndReordersOnceRefreshCompletes() {
        val initial = listOf(
            podcast(1, "Первый", "2026-10-16T10:00:00Z"),
            podcast(2, "Второй", "2026-10-15T10:00:00Z"),
        )
        val initialOrder = updateStableUpdatedPodcastOrder(
            currentOrder = emptyList(),
            podcasts = initial,
            refreshCompleted = false,
        )
        val enrichedDuringRefresh = listOf(
            podcast(1, "Первый", "2026-10-16T10:00:00Z"),
            podcast(2, "Второй", "2026-10-17T10:00:00Z"),
        )

        val orderDuringRefresh = updateStableUpdatedPodcastOrder(
            currentOrder = initialOrder,
            podcasts = enrichedDuringRefresh,
            refreshCompleted = false,
        )
        val orderAfterRefresh = updateStableUpdatedPodcastOrder(
            currentOrder = orderDuringRefresh,
            podcasts = enrichedDuringRefresh,
            refreshCompleted = true,
        )

        assertEquals(listOf(1, 2), orderDuringRefresh)
        assertEquals(listOf(2, 1), orderAfterRefresh)
    }

    @Test
    fun selectionClosesOnlyAfterSuccessfulLocalCommit() {
        val selected = setOf(7, 11)

        assertEquals(selected, selectionAfterLocalCommit(selected, succeeded = false))
        assertEquals(emptySet<Int>(), selectionAfterLocalCommit(selected, succeeded = true))
    }

    @Test
    fun onlyCompletedDownloadHasStatusGlyph() {
        assertFalse(shouldShowPodcastDownloadStatus(EpisodeDownloadState.NOT_DOWNLOADED))
        assertFalse(shouldShowPodcastDownloadStatus(EpisodeDownloadState.FAILED))
        assertFalse(shouldShowPodcastDownloadStatus(EpisodeDownloadState.QUEUED))
        assertFalse(shouldShowPodcastDownloadStatus(EpisodeDownloadState.DOWNLOADING))
        assertTrue(shouldShowPodcastDownloadStatus(EpisodeDownloadState.DOWNLOADED))
    }

    private fun podcast(
        id: Int,
        name: String,
        updated: String = "",
    ) = PodcastSubscriptionEntity(
        podcastId = id,
        name = name,
        artworkUrl = "",
        description = "",
        episodeCount = 0,
        episodesPlayed = 0,
        feedUrl = "",
        author = "",
        latestEpisodeId = null,
        lastUpdatedAt = updated,
        refreshedAtEpochMs = 0,
    )
}
