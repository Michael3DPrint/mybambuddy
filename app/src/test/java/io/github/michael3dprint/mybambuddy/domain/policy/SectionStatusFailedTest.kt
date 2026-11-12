package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import org.junit.Assert.assertEquals
import org.junit.Test

class SectionStatusFailedTest {
    @Test
    fun `a failed last print is a warning in words, not an error`() {
        assertEquals(SectionStatus(StatusLevel.WARN, "Last print failed"), SectionStatuses.printerStatus(emptyList(), PrinterState.FAILED))
        assertEquals(SectionStatus(StatusLevel.ERROR, "Printer error"), SectionStatuses.printerStatus(emptyList(), PrinterState.ERROR))
        assertEquals(
            SectionStatus(StatusLevel.ERROR, "1 error"),
            SectionStatuses.printerStatus(
                listOf(io.github.michael3dprint.mybambuddy.domain.model.HmsStatus(severity = "1")),
                PrinterState.FAILED,
            ),
        )
    }
}
