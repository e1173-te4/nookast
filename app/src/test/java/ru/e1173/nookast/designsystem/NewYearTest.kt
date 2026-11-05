package ru.e1173.nookast.designsystem

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewYearTest {
    @Test
    fun `season is 25 December to 7 January inclusive`() {
        assertFalse(isNewYearSeason(LocalDate.of(2026, 12, 24)))
        assertTrue(isNewYearSeason(LocalDate.of(2026, 12, 25)))
        assertTrue(isNewYearSeason(LocalDate.of(2027, 1, 1)))
        assertTrue(isNewYearSeason(LocalDate.of(2027, 1, 7)))
        assertFalse(isNewYearSeason(LocalDate.of(2027, 1, 8)))
    }

    @Test
    fun `mode overrides the dates`() {
        val summer = LocalDate.of(2027, 7, 1)
        assertTrue(newYearActive(NewYearMode.ALWAYS, summer))
        assertFalse(newYearActive(NewYearMode.DATES, summer))
        assertFalse(newYearActive(NewYearMode.OFF, LocalDate.of(2027, 1, 1)))
    }
}
