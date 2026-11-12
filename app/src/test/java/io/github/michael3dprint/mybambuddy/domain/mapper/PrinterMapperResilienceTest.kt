package io.github.michael3dprint.mybambuddy.domain.mapper

import io.github.michael3dprint.mybambuddy.data.api.dto.HmsErrorDto
import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterDto
import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterStatusDto
import io.github.michael3dprint.mybambuddy.data.api.dto.TemperaturesDto
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterMapperResilienceTest {

    @Test
    fun `mapToPrinter handles null and missing fields with safe fallbacks`() {
        val emptyDto = PrinterDto()
        val emptyStatus = PrinterStatusDto()

        val printer = mapToPrinter(emptyDto, emptyStatus)

        assertEquals("", printer.id)
        assertEquals("Unknown Printer", printer.name)
        assertEquals("Bambu Printer", printer.model)
        assertEquals(PrinterState.UNKNOWN, printer.state)
        assertEquals(0, printer.progress)
        assertEquals(0L, printer.remainingTimeSeconds)
        assertEquals("0m left", printer.remainingTimeFormatted)
        assertEquals("--", printer.estimatedFinishTime)
        assertEquals(0, printer.currentLayer)
        assertEquals(0, printer.totalLayers)
        assertEquals("--", printer.layerFormatted)
        assertFalse(printer.hasHmsWarning)
        assertEquals(0, printer.hmsCount)
        assertEquals("--°C", printer.temperatures.formattedNozzle)
        assertEquals("--°C", printer.temperatures.formattedBed)
        assertEquals("--°C", printer.temperatures.formattedChamber)
    }

    @Test
    fun `progress outside 0 to 100 is clamped`() {
        assertEquals(100, mapToPrinter(PrinterDto(), PrinterStatusDto(progress = 150f)).progress)
        assertEquals(0, mapToPrinter(PrinterDto(), PrinterStatusDto(progress = -5f)).progress)
        assertEquals(100, mapToPrintStatus(PrinterStatusDto(progress = 150f)).progress)
        assertEquals(0, mapToPrintStatus(PrinterStatusDto(progress = -5f)).progress)
    }

    @Test
    fun `mapToPrintStatus maps DTO to clean domain model`() {
        val statusDto = PrinterStatusDto(
            state = "PRINTING",
            progress = 72.8f,
            remainingTimeSeconds = 1800L,
            currentLayer = 45,
            totalLayers = 100,
            temperatures = TemperaturesDto(nozzle = 215.0f, bed = 60.0f, chamber = 35.0f),
            hmsErrors = listOf(HmsErrorDto(code = "HMS_0100", description = "Nozzle clogged")),
        )

        val status = mapToPrintStatus(statusDto)

        assertEquals(PrinterState.PRINTING, status.state)
        assertEquals(72, status.progress)
        assertEquals(1800L, status.remainingTimeSeconds)
        assertEquals("30m left", status.remainingTimeFormatted)
        assertEquals(45, status.currentLayer)
        assertEquals(100, status.totalLayers)
        assertEquals("Layer 45 of 100", status.layerFormatted)
        assertEquals("215°C", status.temperatures.formattedNozzle)
        assertEquals("60°C", status.temperatures.formattedBed)
        assertEquals("35°C", status.temperatures.formattedChamber)
        assertTrue(status.hasHmsWarning)
        assertEquals(1, status.hmsCount)
        assertEquals("HMS_0100", status.hmsErrors.first().code)
        assertEquals("Nozzle clogged", status.hmsErrors.first().displayMessage)
    }

    @Test
    fun `mapToPrinter handles malformed state string by resolving to UNKNOWN`() {
        val dto = PrinterDto(id = "p1", state = "some_invalid_malformed_state")
        val printer = mapToPrinter(dto)
        assertEquals(PrinterState.UNKNOWN, printer.state)
    }

    @Test
    fun `mapToPrinter clamps progress between 0 and 100`() {
        val dtoNegative = PrinterDto(id = "p1", progress = -45.0f)
        val printerNegative = mapToPrinter(dtoNegative)
        assertEquals(0, printerNegative.progress)

        val dtoOverflow = PrinterDto(id = "p2", progress = 185.0f)
        val printerOverflow = mapToPrinter(dtoOverflow)
        assertEquals(100, printerOverflow.progress)
    }

    @Test
    fun `mapToPrinter formats remainingTime seconds or defaults to 0m left`() {
        val dtoValid = PrinterDto(id = "p1", remainingTimeSeconds = 3660L)
        val printerValid = mapToPrinter(dtoValid)
        assertEquals("1h 1m left", printerValid.remainingTimeFormatted)

        val dtoZero = PrinterDto(id = "p2", remainingTimeSeconds = 0L)
        val printerZero = mapToPrinter(dtoZero)
        assertEquals("0m left", printerZero.remainingTimeFormatted)

        val dtoNull = PrinterDto(id = "p3")
        val printerNull = mapToPrinter(dtoNull)
        assertEquals("0m left", printerNull.remainingTimeFormatted)
    }
}
