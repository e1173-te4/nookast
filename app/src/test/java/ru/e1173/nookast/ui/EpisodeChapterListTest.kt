package ru.e1173.nookast.ui

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.e1173.nookast.player.PlaybackChapter

/** The one chapter list of Now Playing and the episode card (APK-3.20). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EpisodeChapterListTest {
    private val resources get() = RuntimeEnvironment.getApplication().resources
    private val description = "00:45 Вступление\n12:30 Главная тема\n1:02:03 Финал"

    @Test
    fun `real chapters win over the description and are sorted without duplicates`() {
        val chapters = listOf(
            PlaybackChapter(startTimeMs = 60_000, endTimeMs = null, title = "Вторая"),
            PlaybackChapter(startTimeMs = 0, endTimeMs = null, title = ""),
            PlaybackChapter(startTimeMs = 60_000, endTimeMs = null, title = "Дубль"),
        )

        val list = episodeChapterList(chapters, description, resources)

        assertEquals(listOf(0L, 60_000L), list.map(DescriptionTimestamp::positionMs))
        assertEquals("Вторая", list[1].label)
        assertTrue(list[0].label.isNotBlank())
        assertFalse(list.any(DescriptionTimestamp::fromDescription))
    }

    @Test
    fun `without chapters the description's timestamps stand in and are marked`() {
        val list = episodeChapterList(emptyList(), description, resources)

        assertEquals(listOf(45_000L, 750_000L, 3_723_000L), list.map(DescriptionTimestamp::positionMs))
        assertTrue(list.all(DescriptionTimestamp::fromDescription))
    }

    @Test
    fun `a single time in the description is not a chapter list`() {
        assertTrue(episodeChapterList(emptyList(), "Начало в 12:30", resources).isEmpty())
    }
}
