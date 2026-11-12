package io.github.michael3dprint.mybambuddy.ui.printers

import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterCardRulesTest {
    private fun printer(state: PrinterState, progress: Int = 0) =
        Printer(id = "1", name = "X2D", model = "X2D", state = state, progress = progress)

    @Test
    fun `progress is shown only when there is a print to measure`() {
        assertTrue(showsProgress(printer(PrinterState.PRINTING)))
        assertTrue(showsProgress(printer(PrinterState.PAUSED)))
        assertTrue(showsProgress(printer(PrinterState.ERROR, progress = 40)))
        assertFalse(showsProgress(printer(PrinterState.ERROR, progress = 0)))
        assertFalse(showsProgress(printer(PrinterState.IDLE)))
        assertFalse(showsProgress(printer(PrinterState.FINISHED, progress = 100)))
        assertFalse(showsProgress(printer(PrinterState.OFFLINE)))
    }

    @Test
    fun `every state without progress has a line`() {
        assertEquals("Ready. Nothing is printing.", idleLine(PrinterState.IDLE))
        assertEquals("Print finished.", idleLine(PrinterState.FINISHED))
        assertEquals("Offline.", idleLine(PrinterState.OFFLINE))
        assertEquals("Status unknown.", idleLine(PrinterState.UNKNOWN))
    }
}
