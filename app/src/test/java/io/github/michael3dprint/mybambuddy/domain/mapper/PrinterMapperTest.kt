package io.github.michael3dprint.mybambuddy.domain.mapper

import io.github.michael3dprint.mybambuddy.data.api.dto.HmsErrorDto
import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterDto
import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterStatusDto
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterMapperTest {

    @Test
    fun `PrinterState fromString resolves state enum correctly`() {
        assertEquals(PrinterState.PRINTING, PrinterState.fromString("PRINTING"))
        assertEquals(PrinterState.PRINTING, PrinterState.fromString("printing"))
        assertEquals(PrinterState.PRINTING, PrinterState.fromString("print"))
        assertEquals(PrinterState.PAUSED, PrinterState.fromString("PAUSED"))
        assertEquals(PrinterState.PAUSED, PrinterState.fromString("pause"))
        assertEquals(PrinterState.IDLE, PrinterState.fromString("IDLE"))
        assertEquals(PrinterState.IDLE, PrinterState.fromString("idle"))
        assertEquals(PrinterState.ERROR, PrinterState.fromString("ERROR"))
        assertEquals(PrinterState.ERROR, PrinterState.fromString("error"))
        assertEquals(PrinterState.OFFLINE, PrinterState.fromString("OFFLINE"))
        assertEquals(PrinterState.OFFLINE, PrinterState.fromString("offline"))
        assertEquals(PrinterState.UNKNOWN, PrinterState.fromString("UNKNOWN"))
        assertEquals(PrinterState.UNKNOWN, PrinterState.fromString(null))
        assertEquals(PrinterState.UNKNOWN, PrinterState.fromString(""))
        assertEquals(PrinterState.UNKNOWN, PrinterState.fromString("random_unrecognized_state"))
    }

    @Test
    fun `mapToPrinter maps PrinterDto and PrinterStatusDto to Printer domain model`() {
        val printerDto = PrinterDto(
            id = "printer_001",
            name = "Bambu X1-Carbon",
            model = "X1C",
            state = "idle",
            snapshotUrl = "http://192.168.1.100/printers/printer_001/camera/snapshot",
        )

        val statusDto = PrinterStatusDto(
            state = "PRINTING",
            progress = 68.4f,
            remainingTimeSeconds = 4320L,
            currentLayer = 150,
            totalLayers = 220,
            hmsErrors = listOf(
                HmsErrorDto(code = "HMS_0300_0100", description = "Runout", severity = "warning"),
            ),
        )

        val printer = mapToPrinter(printerDto, statusDto)

        assertEquals("printer_001", printer.id)
        assertEquals("Bambu X1-Carbon", printer.name)
        assertEquals("X1C", printer.model)
        assertEquals(PrinterState.PRINTING, printer.state)
        assertEquals(68, printer.progress)
        assertEquals(4320L, printer.remainingTimeSeconds)
        assertEquals("1h 12m left", printer.remainingTimeFormatted)
        assertEquals(150, printer.currentLayer)
        assertEquals(220, printer.totalLayers)
        assertTrue(printer.hasHmsWarning)
        assertEquals(1, printer.hmsCount)
        assertEquals("http://192.168.1.100/printers/printer_001/camera/snapshot", printer.snapshotUrl)
    }

    @Test
    fun `mapToPrinter falls back to printerDto when statusDto is null`() {
        val printerDto = PrinterDto(
            id = "p2",
            name = "Bambu P1S",
            model = "P1S",
            state = "idle",
            progress = 0f,
        )

        val printer = mapToPrinter(printerDto, null)

        assertEquals("p2", printer.id)
        assertEquals("Bambu P1S", printer.name)
        assertEquals(PrinterState.IDLE, printer.state)
        assertEquals(0, printer.progress)
        assertEquals(0L, printer.remainingTimeSeconds)
        assertEquals("0m left", printer.remainingTimeFormatted)
        assertFalse(printer.hasHmsWarning)
        assertEquals(0, printer.hmsCount)
        assertEquals("http://localhost/printers/p2/camera/snapshot", printer.snapshotUrl)
    }
}
