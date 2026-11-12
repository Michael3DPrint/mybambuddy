package io.github.michael3dprint.mybambuddy.data.mapper

import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterAmsStatusDto
import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterStatusDto
import io.github.michael3dprint.mybambuddy.domain.model.PrintActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityMapperTest {
    @Test
    fun `gcode states map to activity and unknown is never safe`() {
        for (s in listOf("IDLE", "FINISH", "FAILED", "idle", " Finish ")) assertEquals(s, PrintActivity.IDLE, PrintActivity.from(s, true))
        for (s in listOf(
            "RUNNING",
            "PAUSE",
            "PREPARE",
            "SLICING",
            "pause",
        )) {
            assertEquals(s, PrintActivity.BUSY, PrintActivity.from(s, true))
        }
        assertEquals(PrintActivity.UNKNOWN, PrintActivity.from(null, true))
        assertEquals(PrintActivity.UNKNOWN, PrintActivity.from("", true))
        assertEquals(PrintActivity.UNKNOWN, PrintActivity.from("SOMETHING_NEW", true))
        assertEquals(PrintActivity.OFFLINE, PrintActivity.from("RUNNING", false))
    }

    @Test
    fun `status payload maps every capability and leaves missing fields null`() {
        val full = PrinterStatusDto(
            connected = true,
            state = "IDLE",
            developerMode = true,
            amsExists = true,
            supportsDrying = true,
            supportsDryingWhilePrinting = true,
            dryingScreenOnly = false,
            supportsChamberHeater = true,
        ).toCapabilities()
        assertEquals(true, full.developerMode)
        assertEquals(true, full.hasAms)
        assertEquals(true, full.dryingWhilePrinting)
        assertEquals(false, full.dryingScreenOnly)
        assertEquals(true, full.hasChamberHeater)
        assertEquals(PrintActivity.IDLE, full.activity)

        val bare = PrinterStatusDto().toCapabilities()
        assertNull(bare.developerMode)
        assertNull(bare.connected)
        assertEquals(PrintActivity.UNKNOWN, bare.activity)
    }

    @Test
    fun `AMS status payload maps too and infers an AMS from its units`() {
        val dto =
            PrinterAmsStatusDto(
                connected = true,
                state = "RUNNING",
                developerMode = false,
                ams = listOf(io.github.michael3dprint.mybambuddy.data.api.dto.AmsUnitDto(id = 0)),
            )
        val caps = dto.toCapabilities()
        assertEquals(false, caps.developerMode)
        assertEquals(true, caps.hasAms)
        assertEquals(PrintActivity.BUSY, caps.activity)
    }

    @Test
    fun `an RFID tag is a non-blank id that is not all zeros`() {
        assertTrue(isRfidTag("A1B2C3D4", null))
        assertTrue(isRfidTag(null, "0000000000000000000000000000000A"))
        assertFalse(isRfidTag(null, null))
        assertFalse(isRfidTag("", "  "))
        assertFalse(isRfidTag("00000000", "0000000000000000000000000000000"))
    }
}
