package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.domain.model.CheckLevel
import io.github.michael3dprint.mybambuddy.domain.model.DiagnosticCheck
import io.github.michael3dprint.mybambuddy.domain.model.DiagnosticOverall
import io.github.michael3dprint.mybambuddy.domain.model.DiagnosticReport
import io.github.michael3dprint.mybambuddy.domain.model.DiagnosticStatus
import io.github.michael3dprint.mybambuddy.domain.model.DryPhase
import io.github.michael3dprint.mybambuddy.domain.model.HmsStatus
import io.github.michael3dprint.mybambuddy.domain.model.PrinterCapabilities
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import io.github.michael3dprint.mybambuddy.domain.model.SlicingCheck
import io.github.michael3dprint.mybambuddy.domain.model.SlicingReadiness
import io.github.michael3dprint.mybambuddy.domain.model.ToolingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionStatusTest {
    private fun status(level: StatusLevel, text: String) = SectionStatus(level, text)

    @Test
    fun `remote control words are the same for the card line and the section`() {
        val full = ReadinessEvaluator.evaluate("1", "X2D", PrinterCapabilities(connected = true, developerMode = true))
        val limited = ReadinessEvaluator.evaluate("1", "X2D", PrinterCapabilities(connected = true, developerMode = false))
        assertEquals("Remote control: Full", RemoteControlWords.line(full))
        assertEquals("Remote control: Limited", RemoteControlWords.line(limited))
        assertEquals(status(StatusLevel.OK, "Full"), RemoteControlWords.status(ToolingMode.FULL))
        assertEquals(status(StatusLevel.WARN, "Limited"), RemoteControlWords.status(ToolingMode.LIMITED))
    }

    @Test
    fun `every mode has a value, a level and an explanation, and only Full is good`() {
        ToolingMode.entries.forEach {
            assertTrue(RemoteControlWords.value(it).isNotBlank())
            assertTrue(RemoteControlWords.explain(it).isNotBlank())
        }
        assertEquals(listOf(ToolingMode.FULL), ToolingMode.entries.filter { RemoteControlWords.status(it).level == StatusLevel.OK })
        assertEquals(StatusLevel.WARN, RemoteControlWords.status(ToolingMode.UNVERIFIED).level)
        assertEquals(StatusLevel.NEUTRAL, RemoteControlWords.status(ToolingMode.UNKNOWN).level)
        assertEquals("Not confirmed", RemoteControlWords.value(ToolingMode.UNVERIFIED))
        assertTrue(RemoteControlWords.explain(ToolingMode.LIMITED).contains(ToolAvailability.DEVELOPER_MODE_FIX))
    }

    @Test
    fun `printer status is good only with no errors and a known state`() {
        val none = emptyList<HmsStatus>()
        assertEquals(status(StatusLevel.OK, "No errors"), SectionStatuses.printerStatus(none, PrinterState.IDLE))
        assertEquals(status(StatusLevel.ERROR, "1 error"), SectionStatuses.printerStatus(listOf(hms("1")), PrinterState.IDLE))
        assertEquals(status(StatusLevel.ERROR, "3 errors"), SectionStatuses.printerStatus(List(3) { hms("1") }, PrinterState.PRINTING))
        assertEquals(status(StatusLevel.ERROR, "Printer error"), SectionStatuses.printerStatus(none, PrinterState.ERROR))
        assertEquals(StatusLevel.NEUTRAL, SectionStatuses.printerStatus(none, PrinterState.OFFLINE).level)
        assertEquals(StatusLevel.NEUTRAL, SectionStatuses.printerStatus(none, PrinterState.UNKNOWN).level)
    }

    private fun hms(severity: String) = HmsStatus(code = "0300", description = "x", severity = severity)

    @Test
    fun `a notice is not called an error and a mix names each level`() {
        assertEquals(status(StatusLevel.NEUTRAL, "1 notice"), SectionStatuses.printerStatus(listOf(hms("3")), PrinterState.IDLE))
        assertEquals(status(StatusLevel.WARN, "2 warnings"), SectionStatuses.printerStatus(listOf(hms("2"), hms("2")), PrinterState.IDLE))
        assertEquals(
            status(StatusLevel.ERROR, "1 error, 1 warning, 2 notices"),
            SectionStatuses.printerStatus(listOf(hms("3"), hms("1"), hms("2"), hms("3")), PrinterState.IDLE),
        )
        assertEquals(HmsSeverity.NOTICE, HmsSummary.worst(listOf(hms("3"))))
        assertEquals("", HmsSummary.words(emptyList()))
    }

    private fun report(vararg s: DiagnosticStatus) =
        DiagnosticReport(DiagnosticOverall.OK, s.mapIndexed { i, st -> DiagnosticCheck("c$i", st) })

    @Test
    fun `diagnostics go from not run to running to the result`() {
        assertEquals(status(StatusLevel.NEUTRAL, "Not run"), SectionStatuses.diagnostics(false, false, null))
        assertEquals(status(StatusLevel.NEUTRAL, "Running…"), SectionStatuses.diagnostics(true, false, null))
        assertEquals(status(StatusLevel.ERROR, "Couldn't run"), SectionStatuses.diagnostics(false, true, null))
        assertEquals(
            status(StatusLevel.OK, "All passed"),
            SectionStatuses.diagnostics(false, false, report(DiagnosticStatus.PASS, DiagnosticStatus.PASS)),
        )
        assertEquals(
            status(StatusLevel.WARN, "2 warnings"),
            SectionStatuses.diagnostics(false, false, report(DiagnosticStatus.WARN, DiagnosticStatus.WARN)),
        )
        assertEquals(
            status(StatusLevel.ERROR, "1 failed"),
            SectionStatuses.diagnostics(false, false, report(DiagnosticStatus.FAIL, DiagnosticStatus.WARN)),
        )
        assertEquals(StatusLevel.NEUTRAL, SectionStatuses.diagnostics(false, false, report()).level)
    }

    private fun check(id: String, passed: Boolean, level: CheckLevel) = SlicingCheck(id, id, passed, level, "")

    @Test
    fun `slicing is good only when ready with no failed warning`() {
        val ok = SlicingReadiness(listOf(check("a", true, CheckLevel.BLOCKER), check("cloud", false, CheckLevel.INFO)))
        val notes = SlicingReadiness(listOf(check("a", true, CheckLevel.BLOCKER), check("printer-preset:X", false, CheckLevel.WARNING)))
        val blocked = SlicingReadiness(listOf(check("a", false, CheckLevel.BLOCKER)))
        assertEquals(status(StatusLevel.OK, "Ready"), SectionStatuses.slicing(ok, false))
        assertEquals(status(StatusLevel.WARN, "Ready, with notes"), SectionStatuses.slicing(notes, false))
        assertEquals(status(StatusLevel.ERROR, "Not ready"), SectionStatuses.slicing(blocked, false))
        assertEquals(status(StatusLevel.NEUTRAL, "Checking…"), SectionStatuses.slicing(null, false))
        assertEquals(status(StatusLevel.NEUTRAL, "Couldn't check"), SectionStatuses.slicing(ok, true))
    }

    @Test
    fun `slicing for one printer keeps the shared checks and only that printer's preset check`() {
        val all = SlicingReadiness(
            listOf(
                check("switch", true, CheckLevel.BLOCKER),
                check("printer-preset:A", false, CheckLevel.WARNING),
                check("printer-preset:B", true, CheckLevel.WARNING),
            ),
        )
        assertEquals(listOf("switch", "printer-preset:A"), all.forPrinter("A").checks.map { it.id })
        assertEquals(listOf("switch", "printer-preset:B"), all.forPrinter("B").checks.map { it.id })
        assertFalse(all.forPrinter("B").checks.any { !it.passed })
    }

    @Test
    fun `maintenance is good with nothing due or soon`() {
        assertEquals(status(StatusLevel.OK, "Nothing due"), SectionStatuses.maintenance(0, 0))
        assertEquals(status(StatusLevel.WARN, "2 due"), SectionStatuses.maintenance(2, 0))
        assertEquals(status(StatusLevel.WARN, "1 due, 3 soon"), SectionStatuses.maintenance(1, 3))
        assertEquals(status(StatusLevel.WARN, "3 soon"), SectionStatuses.maintenance(0, 3))
    }

    private fun st(level: StatusLevel, text: String = "x") = SectionStatus(level, text)

    @Test
    fun `the diagnostics group is as bad as its worst part and says how many`() {
        assertEquals(
            st(StatusLevel.ERROR, "2 need attention"),
            SectionStatuses.group(listOf(st(StatusLevel.ERROR), st(StatusLevel.OK), st(StatusLevel.ERROR))),
        )
        assertEquals(
            st(StatusLevel.WARN, "1 to look at"),
            SectionStatuses.group(listOf(st(StatusLevel.WARN), st(StatusLevel.OK), st(StatusLevel.NEUTRAL))),
        )
        assertEquals(st(StatusLevel.NEUTRAL, "Not all checked"), SectionStatuses.group(listOf(st(StatusLevel.OK), st(StatusLevel.NEUTRAL))))
        assertEquals(st(StatusLevel.OK, "All good"), SectionStatuses.group(listOf(st(StatusLevel.OK), st(StatusLevel.OK))))
        assertEquals(StatusLevel.NEUTRAL, SectionStatuses.group(emptyList()).level)
    }

    private fun unit(phase: DryPhase, humidity: Int? = 24, minutes: Int = 0) = AmsUnit(
        printerId = 1, printerName = "P", amsId = 0, isHt = false, humidityLevel = humidity, tempC = null, supportsDrying = true,
        dryPhase = phase, dryMinutesLeft = minutes, dryTargetTemp = null, trays = emptyList(),
    )

    @Test
    fun `an AMS unit shows drying, an error, or its humidity`() {
        assertEquals(st(StatusLevel.OK, "Humidity 24 %"), SectionStatuses.ams(unit(DryPhase.OFF)))
        assertEquals(st(StatusLevel.OK, "Drying, 2 h 5 m left"), SectionStatuses.ams(unit(DryPhase.DRYING, minutes = 125)))
        assertEquals(st(StatusLevel.ERROR, "Drying error"), SectionStatuses.ams(unit(DryPhase.ERROR)))
        assertEquals(st(StatusLevel.NEUTRAL, "Humidity not reported"), SectionStatuses.ams(unit(DryPhase.OFF, humidity = null)))
        assertEquals(StatusLevel.OK, SectionStatuses.ams(unit(DryPhase.COOLING)).level)
        assertEquals(StatusLevel.NEUTRAL, SectionStatuses.ams(unit(DryPhase.STOPPING)).level)
    }
}
