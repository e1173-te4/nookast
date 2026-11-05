package ru.e1173.nookast.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineScrubTest {
    @Test
    fun `thumb sticks to a nearby chapter boundary only`() {
        val boundaries = listOf(0.25f, 0.5f)
        assertEquals(0.25f, snapToChapterBoundary(0.26f, boundaries, threshold = 0.02f))
        assertEquals(0.3f, snapToChapterBoundary(0.3f, boundaries, threshold = 0.02f))
        assertEquals(0.7f, snapToChapterBoundary(0.7f, emptyList(), threshold = 0.02f))
    }

    @Test
    fun `scrub bubble shows hours only when needed`() {
        assertEquals("0:05", formatScrubTime(5_400))
        assertEquals("12:34", formatScrubTime(754_000))
        assertEquals("1:02:03", formatScrubTime(3_723_000))
    }

    @Test
    fun `chapter starts become segments`() {
        assertEquals(listOf(0f to 0.5f, 0.5f to 1f), timelineSegments(listOf(500L), 1_000L))
    }
}
