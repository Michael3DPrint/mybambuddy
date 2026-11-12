package io.github.michael3dprint.mybambuddy.data.api

import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterDto
import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterStatusDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PrinterStatusDtoTest {

    private val moshi = ApiClient.createMoshi()
    private val statusAdapter = moshi.adapter(PrinterStatusDto::class.java)
    private val printerAdapter = moshi.adapter(PrinterDto::class.java)

    @Test
    fun `deserializes complete PrinterStatusDto successfully`() {
        val json = """
            {
                "state": "PRINTING",
                "progress": 45.5,
                "remaining_time_seconds": 3600,
                "current_layer": 120,
                "total_layers": 300,
                "temperatures": {
                    "nozzle": 220.0,
                    "bed": 60.0,
                    "chamber": 35.0
                },
                "hms_errors": [
                    {
                        "code": "HMS_0300_0100",
                        "description": "Filament runout",
                        "severity": "warning"
                    }
                ]
            }
        """.trimIndent()

        val dto = statusAdapter.fromJson(json)

        assertNotNull(dto)
        assertEquals("PRINTING", dto?.state)
        assertEquals(45.5f, dto?.progress)
        assertEquals(3600L, dto?.effectiveRemainingTimeSeconds)
        assertEquals(120, dto?.currentLayer)
        assertEquals(300, dto?.totalLayers)
        assertEquals(220.0f, dto?.temperatures?.nozzle)
        assertEquals(60.0f, dto?.temperatures?.bed)
        assertEquals(35.0f, dto?.temperatures?.chamber)
        assertEquals(1, dto?.allHmsErrors?.size)
        assertEquals("HMS_0300_0100", dto?.allHmsErrors?.first()?.code)
        assertEquals("warning", dto?.allHmsErrors?.first()?.severity)
    }

    @Test
    fun `deserializes status JSON with missing and unknown fields leniency`() {
        val json = """
            {
                "state": "IDLE",
                "unknown_extra_field": "some_value",
                "random_number": 12345
            }
        """.trimIndent()

        val dto = statusAdapter.fromJson(json)

        assertNotNull(dto)
        assertEquals("IDLE", dto?.state)
        assertNull(dto?.progress)
        assertNull(dto?.remainingTimeSeconds)
        assertNull(dto?.temperatures)
        assertEquals(0, dto?.allHmsErrors?.size)
    }

    @Test
    fun `deserializes status JSON with string list HMS errors`() {
        val json = """
            {
                "state": "PRINTING",
                "hms_status": [
                    "HMS_0300_0100_0001_0002",
                    "HMS_0500_0200_0001_0001"
                ]
            }
        """.trimIndent()

        val dto = statusAdapter.fromJson(json)

        assertNotNull(dto)
        assertEquals(2, dto?.allHmsErrors?.size)
        assertEquals("HMS_0300_0100_0001_0002", dto?.allHmsErrors?.get(0)?.code)
        assertEquals("HMS_0500_0200_0001_0001", dto?.allHmsErrors?.get(1)?.code)
    }

    @Test
    fun `deserializes PrinterDto with string status`() {
        val json = """
            {
                "id": "p1",
                "name": "Bambu X1C",
                "model": "X1C",
                "status": "idle"
            }
        """.trimIndent()

        val dto = printerAdapter.fromJson(json)

        assertNotNull(dto)
        assertEquals("p1", dto?.id)
        assertEquals("Bambu X1C", dto?.name)
        assertEquals("idle", dto?.status?.state)
    }
}
