package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import org.junit.Assert.assertEquals
import org.junit.Test

class OverviewActionTest {
    @Test
    fun `an active print offers pause and cancel`() {
        assertEquals(OverviewAction.PAUSE_AND_CANCEL, OverviewAction.forState(PrinterState.PRINTING))
        assertEquals(OverviewAction.PAUSE_AND_CANCEL, OverviewAction.forState(PrinterState.PAUSED))
    }

    @Test
    fun `a failed last print offers Print again, not Cancel print`() {
        assertEquals(OverviewAction.PRINT, OverviewAction.forState(PrinterState.FAILED))
    }

    @Test
    fun `an error offers only cancel`() {
        assertEquals(OverviewAction.CANCEL_ONLY, OverviewAction.forState(PrinterState.ERROR))
    }

    @Test
    fun `every state with nothing active offers print`() {
        val idle = PrinterState.entries - setOf(PrinterState.PRINTING, PrinterState.PAUSED, PrinterState.ERROR)
        idle.forEach { assertEquals("$it", OverviewAction.PRINT, OverviewAction.forState(it)) }
    }
}
