package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.HistoryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviousPrintFilterTest {
    @Test
    fun `each chip keeps its own statuses and a running print is never listed`() {
        assertTrue(PreviousPrintFilter.ALL.matches(HistoryStatus.SUCCESS))
        assertTrue(PreviousPrintFilter.ALL.matches(HistoryStatus.CANCELLED))
        assertTrue(PreviousPrintFilter.ALL.matches(HistoryStatus.FAILED))
        assertFalse(PreviousPrintFilter.ALL.matches(HistoryStatus.IN_PROGRESS))
        assertEquals(listOf(HistoryStatus.SUCCESS), HistoryStatus.entries.filter { PreviousPrintFilter.COMPLETED.matches(it) })
        assertEquals(listOf(HistoryStatus.CANCELLED), HistoryStatus.entries.filter { PreviousPrintFilter.CANCELLED.matches(it) })
        assertEquals(listOf(HistoryStatus.FAILED), HistoryStatus.entries.filter { PreviousPrintFilter.FAILED.matches(it) })
    }
}
