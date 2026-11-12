package io.github.michael3dprint.mybambuddy.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatterTest {

    @Test
    fun `formatRemainingTime formats 0 or negative or null seconds to 0m left`() {
        assertEquals("0m left", formatRemainingTime(null))
        assertEquals("0m left", formatRemainingTime(0L))
        assertEquals("0m left", formatRemainingTime(-100L))
    }

    @Test
    fun `formatRemainingTime formats minutes correctly`() {
        assertEquals("5m left", formatRemainingTime(300L))
        assertEquals("45m left", formatRemainingTime(2700L))
        assertEquals("1m left", formatRemainingTime(60L))
    }

    @Test
    fun `formatRemainingTime formats hours and minutes correctly`() {
        assertEquals("1h 12m left", formatRemainingTime(4320L))
        assertEquals("1h 0m left", formatRemainingTime(3600L))
        assertEquals("2h 30m left", formatRemainingTime(9000L))
    }

    @Test
    fun `calculateEstimatedFinishTime handles 0, null, or negative seconds`() {
        assertEquals("--", calculateEstimatedFinishTime(null))
        assertEquals("--", calculateEstimatedFinishTime(0L))
        assertEquals("--", calculateEstimatedFinishTime(-50L))
    }

    @Test
    fun `calculateEstimatedFinishTime calculates correct clock time`() {
        // Set fixed currentTimeMillis: 2026-01-01 12:00:00 UTC/Local
        val cal = java.util.Calendar.getInstance()
        cal.set(2026, java.util.Calendar.JANUARY, 1, 12, 0, 0)
        val baseTimeMillis = cal.timeInMillis

        // Add 1 hour (3600 seconds) -> 1:00 PM
        val result = calculateEstimatedFinishTime(3600L, currentTimeMillis = baseTimeMillis)
        val expectedSdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        val expectedStr = expectedSdf.format(java.util.Date(baseTimeMillis + 3600000L))

        assertEquals(expectedStr, result)
    }
}
