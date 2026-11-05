package ru.e1173.nookast.offline

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayedAutoDownloadTest {
    private val now = 10L * PLAYED_AUTO_DOWNLOAD_KEEP_MS

    @Test
    fun `a played automatic download is marked first and deleted a day later`() {
        assertEquals(PlayedAutoDownloadAction.MARK, playedAutoDownloadAction(played = true, playedAtMs = null, nowMs = now))
        assertEquals(
            PlayedAutoDownloadAction.KEEP,
            playedAutoDownloadAction(played = true, playedAtMs = now - PLAYED_AUTO_DOWNLOAD_KEEP_MS + 1, nowMs = now),
        )
        assertEquals(
            PlayedAutoDownloadAction.DELETE,
            playedAutoDownloadAction(played = true, playedAtMs = now - PLAYED_AUTO_DOWNLOAD_KEEP_MS, nowMs = now),
        )
    }

    @Test
    fun `marked unplayed again it stays and forgets the mark`() {
        assertEquals(PlayedAutoDownloadAction.FORGET, playedAutoDownloadAction(played = false, playedAtMs = now - 5, nowMs = now))
        assertEquals(PlayedAutoDownloadAction.KEEP, playedAutoDownloadAction(played = false, playedAtMs = null, nowMs = now))
    }
}
