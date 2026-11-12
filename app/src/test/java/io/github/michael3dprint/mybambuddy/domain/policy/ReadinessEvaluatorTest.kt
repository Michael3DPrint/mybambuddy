package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.PrintActivity
import io.github.michael3dprint.mybambuddy.domain.model.PrinterCapabilities
import io.github.michael3dprint.mybambuddy.domain.model.PrinterTool
import io.github.michael3dprint.mybambuddy.domain.model.ToolingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessEvaluatorTest {
    private val ready = PrinterCapabilities(
        connected = true,
        developerMode = true,
        hasAms = true,
        supportsDrying = true,
        activity = PrintActivity.IDLE,
    )

    private fun evaluate(caps: PrinterCapabilities?) = ReadinessEvaluator.evaluate("1", "X2D", caps)

    @Test
    fun `developer mode on and connected is FULL with every tool on`() {
        val r = evaluate(ready)
        assertEquals(ToolingMode.FULL, r.mode)
        assertTrue(r.unavailable.isEmpty())
        assertEquals(PrinterTool.entries.size, r.tools.size)
    }

    @Test
    fun `developer mode off is LIMITED and switches off exactly the developer-mode tools`() {
        val r = evaluate(ready.copy(developerMode = false))
        assertEquals(ToolingMode.LIMITED, r.mode)
        assertEquals(PrinterTool.entries.filter { it.needsDeveloperMode }.toSet(), r.unavailable.map { it.tool }.toSet())
    }

    @Test
    fun `unreported developer mode is UNVERIFIED and never FULL`() {
        val r = evaluate(ready.copy(developerMode = null))
        assertEquals(ToolingMode.UNVERIFIED, r.mode)
        assertTrue(r.unavailable.isEmpty())
        assertTrue(r.tools.filter { it.tool.needsDeveloperMode }.all { it.availability == Availability.LIMITED })
    }

    @Test
    fun `offline wins over developer mode`() {
        assertEquals(ToolingMode.OFFLINE, evaluate(ready.copy(connected = false, activity = PrintActivity.OFFLINE)).mode)
    }

    @Test
    fun `an unreadable status is UNKNOWN and never FULL`() {
        assertEquals(ToolingMode.UNKNOWN, evaluate(null).mode)
        assertEquals(ToolingMode.UNKNOWN, evaluate(PrinterCapabilities.UNKNOWN).mode)
        assertFalse(ReadinessEvaluator.modeOf(PrinterCapabilities.UNKNOWN) == ToolingMode.FULL)
    }

    @Test
    fun `statusOf finds a tool and the report carries the printer name`() {
        val r = evaluate(ready.copy(developerMode = false))
        assertTrue(r.statusOf(PrinterTool.CHAMBER_LIGHT)!!.reason!!.startsWith("X2D"))
        assertEquals("X2D", r.printerName)
    }
}
