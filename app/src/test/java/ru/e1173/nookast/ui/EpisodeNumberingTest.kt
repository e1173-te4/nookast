package ru.e1173.nookast.ui

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.e1173.nookast.player.EpisodeChapters

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EpisodeNumberingTest {
    @Test
    fun takesSeasonAndEpisodeMarksOutOfTitles() {
        assertEquals(EpisodeTitleParts("Big News", EpisodeNumber(2, 99)), episodeTitleParts("S02E99 - Big News"))
        assertEquals(EpisodeTitleParts("Big News", EpisodeNumber(2, 5)), episodeTitleParts("Big News (S2 E5)"))
        assertEquals(EpisodeTitleParts("Шоу — Гости", EpisodeNumber(3, 12)), episodeTitleParts("Шоу | Сезон 3, выпуск 12 | Гости"))
        assertEquals(EpisodeTitleParts("Finale", EpisodeNumber(1, 10)), episodeTitleParts("Season 1 Episode 10: Finale"))
    }

    @Test
    fun leavesOrdinaryTitlesAlone() {
        val parts = episodeTitleParts("BeardyCast 473: Space S-class")
        assertEquals("BeardyCast 473: Space S-class", parts.title)
        assertNull(parts.number)
        // A title that is only the mark keeps it rather than becoming empty.
        assertEquals("S01E01", episodeTitleParts("S01E01").title)
    }

    @Test
    fun readsPodcastingTwoChaptersWithPictures() {
        val chapters = EpisodeChapters.parse(
            """{"chapters":[
                {"startTime":0,"title":"Intro","img":"https://example.org/a.jpg"},
                {"startTime":95.5,"title":"Hidden","toc":false},
                {"startTime":120,"title":"Main"}
            ],"transcripts":[],"people":[]}""",
        )
        assertEquals(listOf(0L, 120_000L), chapters.map { it.startTimeMs })
        assertEquals("https://example.org/a.jpg", chapters[0].imageUrl)
        assertNull(chapters[1].imageUrl)
    }
}
