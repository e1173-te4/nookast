package ru.e1173.nookast.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigatorTest {
    private fun navigator() = AppNavigator(AppDestination.HOME, emptyMap())

    @Test
    fun `each tab keeps its own stack`() {
        val nav = navigator()
        nav.push(Route.Playlist(3))
        nav.selectTab(AppDestination.PODCASTS)
        nav.push(Route.Podcast(7))
        nav.selectTab(AppDestination.HOME)
        assertEquals(Route.Playlist(3), nav.top)
        nav.selectTab(AppDestination.PODCASTS)
        assertEquals(Route.Podcast(7), nav.top)
    }

    @Test
    fun `tapping the shown tab returns it to its root`() {
        val nav = navigator()
        nav.push(Route.Playlist(3))
        nav.push(Route.Episode(10))
        nav.selectTab(AppDestination.HOME)
        assertEquals(listOf<Route>(Route.Home), nav.stack())
    }

    @Test
    fun `back pops, then goes home, then lets the system finish`() {
        val nav = navigator()
        nav.selectTab(AppDestination.FEED)
        nav.push(Route.Episode(5))
        assertTrue(nav.pop())
        assertEquals(Route.Feed, nav.top)
        assertTrue(nav.pop())
        assertEquals(AppDestination.HOME, nav.tab)
        assertFalse(nav.pop())
    }

    @Test
    fun `settings and an episode card belong to the tab they were opened from`() {
        val nav = navigator()
        nav.push(Route.Settings)
        assertEquals(AppDestination.SETTINGS, nav.destination)
        nav.pop()
        nav.push(Route.Episode(1))
        assertEquals(AppDestination.HOME, nav.destination)
    }

    @Test
    fun `stacks survive saving`() {
        val nav = navigator()
        nav.push(Route.Playlist(3))
        nav.selectTab(AppDestination.PODCASTS)
        nav.push(Route.Podcast(7))
        nav.push(Route.Episode(9))
        val saved = with(AppNavigator.Saver) { androidx.compose.runtime.saveable.SaverScope { true }.save(nav) }!!
        val restored = AppNavigator.Saver.restore(saved)!!
        assertEquals(AppDestination.PODCASTS, restored.tab)
        assertEquals(listOf(Route.Podcasts, Route.Podcast(7), Route.Episode(9)), restored.stack())
        assertEquals(listOf(Route.Home, Route.Playlist(3)), restored.stack(AppDestination.HOME))
    }

    @Test
    fun `wide podcast taps replace the shown podcast`() {
        val nav = navigator()
        nav.selectTab(AppDestination.PODCASTS)
        nav.replaceTop(Route.Podcast(1))
        nav.replaceTop(Route.Podcast(2))
        assertEquals(listOf(Route.Podcasts, Route.Podcast(2)), nav.stack())
    }

    @Test
    fun `a re-tap at the root scrolls to the top, a re-tap deeper pops first`() {
        val nav = navigator()
        nav.selectTab(AppDestination.HOME)
        assertEquals(1, nav.scrollToTop)
        nav.push(Route.Playlist(3))
        nav.selectTab(AppDestination.HOME)
        assertEquals(1, nav.scrollToTop)
        assertEquals(listOf(Route.Home), nav.stack())
        nav.selectTab(AppDestination.FEED)
        assertEquals(1, nav.scrollToTop)
    }
}
