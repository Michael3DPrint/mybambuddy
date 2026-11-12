package io.github.michael3dprint.mybambuddy.domain.policy

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsPeriodTest {
    private val today = LocalDate.of(2026, 11, 11)

    @Test
    fun `this month runs from the first to today`() {
        assertEquals(DateRange(LocalDate.of(2026, 11, 1), today), StatsPeriod.THIS_MONTH.range(today))
        assertEquals(LocalDate.of(2026, 3, 1), StatsPeriod.THIS_MONTH.range(LocalDate.of(2026, 3, 1)).from)
    }

    @Test
    fun `last 30 days includes today and the 29 days before`() {
        assertEquals(DateRange(LocalDate.of(2026, 10, 13), today), StatsPeriod.LAST_30_DAYS.range(today))
    }

    @Test
    fun `all time has no limits`() {
        assertEquals(DateRange(null, null), StatsPeriod.ALL_TIME.range(today))
    }
}
