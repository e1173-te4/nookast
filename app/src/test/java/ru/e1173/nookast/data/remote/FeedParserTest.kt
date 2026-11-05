package ru.e1173.nookast.data.remote

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FeedParserTest {
    @Test
    fun `newest episodes come first and broken items are skipped`() {
        val json = """
            {"episodes":[
              {"episodeid":1,"episodetitle":"Old","podcastname":"A","episodepubdate":"2026-09-28T09:49:33","episodeduration":600,"listenduration":0,"completed":false},
              {"episodeid":0,"episodetitle":"Broken"},
              {"episodeid":2,"episodetitle":"New","podcastname":"B","episodepubdate":"2026-10-25T02:41:25","episodeduration":1200,"listenduration":300,"completed":false}
            ],"total":3}
        """.trimIndent()
        val entries = parseFeedEpisodes(json)
        assertEquals(listOf(2, 1), entries.map { it.episodeId })
        assertEquals(listOf(0, 1), entries.map { it.queuePosition })
        assertEquals(300.0, entries.first().listenDurationSeconds!!, 0.0)
    }

    @Test
    fun `not json gives an empty feed`() {
        assertEquals(emptyList<Any>(), parseFeedEpisodes("<html>"))
    }
}
