package ru.e1173.nookast.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ServerNowPlayingTest {
    private fun row(id: Int, at: Long, completed: Boolean = false, position: Long = 600) =
        ListenedRow(id, at, position, completed, "E$id", "P", "", "https://example.org/$id.mp3", 3_600)

    @Test
    fun `an episode finished elsewhere gives way to the one started after it`() {
        // User, 9.11.2026: the car finished 1 and started 2; the phone last listened at 1_000_000.
        val rows = listOf(row(2, 5_000_000), row(1, 4_000_000, completed = true))
        assertEquals(2, ServerNowPlaying.choose(rows, localListenedAtEpochMs = 1_000_000, localDirty = false)?.episodeId)
    }

    @Test
    fun `the phone keeps its episode when it listened last or holds an undelivered position`() {
        val rows = listOf(row(2, 5_000_000))
        assertNull(ServerNowPlaying.choose(rows, localListenedAtEpochMs = 5_000_000, localDirty = false))
        assertNull(ServerNowPlaying.choose(rows, localListenedAtEpochMs = 4_990_000, localDirty = false))
        assertNull(ServerNowPlaying.choose(rows, localListenedAtEpochMs = 1_000_000, localDirty = true))
    }

    @Test
    fun `only finished episodes elsewhere change nothing`() {
        assertNull(ServerNowPlaying.choose(listOf(row(1, 5_000_000, completed = true)), 1_000_000, false))
    }

    @Test
    fun `history rows read with UTC dates, one per episode`() {
        val body = """{"data":[
            {"episodeid":580,"episodetitle":"Episode 938","listendate":"2026-11-08T19:53:43","listenduration":2238,"completed":false,"episodeurl":"https://e/938.mp3","podcastname":"P","episodeduration":3601},
            {"episodeid":580,"episodetitle":"Episode 938","listendate":"2026-11-08T19:53:43","listenduration":2238,"completed":false,"episodeurl":"https://e/938.mp3"},
            {"episodeid":581,"listendate":"2026-11-07T17:38:00","listenduration":3647,"completed":true,"episodeurl":"https://e/942.mp3"}
        ]}"""
        val rows = ServerNowPlaying.parse(body)
        assertEquals(listOf(580, 581), rows.map { it.episodeId })
        assertEquals(java.time.Instant.parse("2026-11-08T19:53:43Z").toEpochMilli(), rows[0].listenedAtEpochMs)
        assertEquals(2238L, rows[0].positionSeconds)
    }
}
