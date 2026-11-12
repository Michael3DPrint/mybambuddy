package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.PrintActivity
import io.github.michael3dprint.mybambuddy.domain.model.PrinterCapabilities
import io.github.michael3dprint.mybambuddy.domain.model.PrinterTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolAvailabilityTest {
    private val ready = PrinterCapabilities(
        connected = true,
        developerMode = true,
        hasAms = true,
        supportsDrying = true,
        activity = PrintActivity.IDLE,
    )

    private fun status(tool: PrinterTool, caps: PrinterCapabilities) = ToolAvailability.statusOf(tool, caps, "X2D")

    @Test
    fun `everything is available when developer mode is on`() {
        assertTrue(ToolAvailability.evaluate(ready, "X2D").all { it.availability == Availability.AVAILABLE })
    }

    @Test
    fun `developer mode off switches off exactly the tools that need it, with the fix in the reason`() {
        val off = ready.copy(developerMode = false)
        ToolAvailability.evaluate(off, "X2D").forEach {
            if (it.tool.needsDeveloperMode) {
                assertEquals(it.tool.name, Availability.UNAVAILABLE, it.availability)
                assertTrue(it.reason!!.contains("Developer LAN mode") && it.reason!!.contains("network settings"))
            } else {
                assertEquals(it.tool.name, Availability.AVAILABLE, it.availability)
            }
        }
    }

    @Test
    fun `unknown developer mode keeps tools usable but flags them`() {
        val unknown = ready.copy(developerMode = null)
        ToolAvailability.evaluate(unknown, "X2D").filter { it.tool.needsDeveloperMode }.forEach {
            assertEquals(Availability.LIMITED, it.availability)
            assertTrue(it.isEnabled)
            assertNotNull(it.reason)
        }
    }

    @Test
    fun `offline printers lose command tools but keep local ones`() {
        val offline = ready.copy(connected = false, activity = PrintActivity.OFFLINE)
        assertEquals(Availability.UNAVAILABLE, status(PrinterTool.CHAMBER_LIGHT, offline).availability)
        assertEquals(Availability.AVAILABLE, status(PrinterTool.CLEAR_PLATE, offline).availability)
        assertTrue(status(PrinterTool.PAUSE_RESUME, offline).reason!!.contains("offline"))
    }

    @Test
    fun `no AMS and no drying support switch off the AMS tools only`() {
        assertEquals(Availability.UNAVAILABLE, status(PrinterTool.SLOT_CHANGE, ready.copy(hasAms = false)).availability)
        assertEquals(Availability.AVAILABLE, status(PrinterTool.CHAMBER_LIGHT, ready.copy(hasAms = false)).availability)
        val screenOnly = status(PrinterTool.DRYING, ready.copy(supportsDrying = false, dryingScreenOnly = true))
        assertTrue(screenOnly.reason!!.contains("printer's screen"))
        assertEquals(Availability.AVAILABLE, status(PrinterTool.SLOT_CHANGE, ready.copy(supportsDrying = false)).availability)
    }

    @Test
    fun `the printer name falls back when blank and an available tool has no reason`() {
        assertTrue(
            ToolAvailability.statusOf(PrinterTool.CHAMBER_LIGHT, ready.copy(developerMode = false), "").reason!!.startsWith("The printer"),
        )
        assertNull(status(PrinterTool.CHAMBER_LIGHT, ready).reason)
    }

    @Test
    fun `drying during a print is refused only when the printer says it cannot`() {
        val busy = ready.copy(activity = PrintActivity.BUSY)
        assertEquals(Availability.UNAVAILABLE, status(PrinterTool.DRYING, busy.copy(dryingWhilePrinting = false)).availability)
        assertEquals(Availability.AVAILABLE, status(PrinterTool.DRYING, busy.copy(dryingWhilePrinting = true)).availability)
        assertEquals(Availability.AVAILABLE, status(PrinterTool.DRYING, busy.copy(dryingWhilePrinting = null)).availability)
        assertEquals(Availability.AVAILABLE, status(PrinterTool.DRYING, ready.copy(dryingWhilePrinting = false)).availability)
    }

    @Test
    fun `isKnown is false only when nothing was reported`() {
        assertEquals(false, PrinterCapabilities.UNKNOWN.isKnown)
        assertEquals(true, PrinterCapabilities(connected = true).isKnown)
        assertEquals(true, PrinterCapabilities(activity = PrintActivity.IDLE).isKnown)
    }

    @Test
    fun `loading and unloading filament is refused while a print is running, and otherwise follows developer mode and the AMS`() {
        val busy = ready.copy(activity = PrintActivity.BUSY)
        assertEquals(Availability.UNAVAILABLE, status(PrinterTool.FEED_FILAMENT, busy).availability)
        assertTrue(status(PrinterTool.FEED_FILAMENT, busy).reason.orEmpty().contains("printing"))
        assertEquals(Availability.AVAILABLE, status(PrinterTool.FEED_FILAMENT, ready).availability)
        assertEquals(Availability.UNAVAILABLE, status(PrinterTool.FEED_FILAMENT, ready.copy(developerMode = false)).availability)
        assertEquals(Availability.UNAVAILABLE, status(PrinterTool.FEED_FILAMENT, ready.copy(hasAms = false)).availability)
        assertEquals(Availability.UNAVAILABLE, status(PrinterTool.FEED_FILAMENT, ready.copy(connected = false)).availability)
        // A tool that does not need an idle printer is not blocked by a print.
        assertEquals(Availability.AVAILABLE, status(PrinterTool.SLOT_CHANGE, busy).availability)
    }
}
