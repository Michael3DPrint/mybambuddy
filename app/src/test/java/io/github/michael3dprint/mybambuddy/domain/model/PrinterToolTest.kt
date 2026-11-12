package io.github.michael3dprint.mybambuddy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PrinterToolTest {
    /** Pins docs/design/10-print-state-gate.md: change the table there and here together. */
    @Test
    fun `the tools that need developer mode are exactly the documented ones`() {
        assertEquals(
            setOf(
                PrinterTool.PAUSE_RESUME,
                PrinterTool.CANCEL_PRINT,
                PrinterTool.CLEAR_ERRORS,
                PrinterTool.PRINT_SPEED,
                PrinterTool.CHAMBER_LIGHT,
                PrinterTool.DRYING,
                PrinterTool.SLOT_CHANGE,
                PrinterTool.START_QUEUED_PRINT,
                PrinterTool.FEED_FILAMENT,
            ),
            PrinterTool.entries.filter { it.needsDeveloperMode }.toSet(),
        )
        assertEquals(
            setOf(PrinterTool.REFRESH_STATUS, PrinterTool.CLEAR_PLATE),
            PrinterTool.entries.filterNot {
                it.needsDeveloperMode
            }.toSet(),
        )
    }

    @Test
    fun `every control command maps to its tool`() {
        assertEquals(PrinterTool.PAUSE_RESUME, PrinterTool.forCommand(PrinterControlCommand.Pause))
        assertEquals(PrinterTool.PAUSE_RESUME, PrinterTool.forCommand(PrinterControlCommand.Resume))
        assertEquals(PrinterTool.CANCEL_PRINT, PrinterTool.forCommand(PrinterControlCommand.Cancel))
        assertEquals(PrinterTool.CLEAR_ERRORS, PrinterTool.forCommand(PrinterControlCommand.ClearHms))
        assertEquals(PrinterTool.CLEAR_PLATE, PrinterTool.forCommand(PrinterControlCommand.ClearPlate))
        assertEquals(PrinterTool.REFRESH_STATUS, PrinterTool.forCommand(PrinterControlCommand.RefreshStatus))
        assertEquals(PrinterTool.PRINT_SPEED, PrinterTool.forCommand(PrinterControlCommand.SetPrintSpeed(2)))
        assertEquals(PrinterTool.CHAMBER_LIGHT, PrinterTool.forCommand(PrinterControlCommand.SetChamberLight(true)))
        assertEquals(PrinterTool.FEED_FILAMENT, PrinterTool.forCommand(PrinterControlCommand.AmsLoadFilament(2)))
        assertEquals(PrinterTool.FEED_FILAMENT, PrinterTool.forCommand(PrinterControlCommand.AmsUnloadFilament(2)))
    }
}
