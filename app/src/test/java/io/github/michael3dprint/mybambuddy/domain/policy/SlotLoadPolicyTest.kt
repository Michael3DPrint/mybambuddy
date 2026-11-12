package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.AmsTray
import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.domain.model.DryPhase
import io.github.michael3dprint.mybambuddy.domain.model.PrintActivity
import io.github.michael3dprint.mybambuddy.domain.model.PrinterCapabilities
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotLoadPolicyTest {
    private val spool = Spool(5, "Polymaker", "PLA", "Pro", "Teal", "008080", 1000, 0, null, null)

    private fun tray(id: Int, material: String = "", loaded: Int? = null, color: String = if (material.isBlank()) "" else "FFFFFF") =
        AmsTray(id, material, "", color, null, loaded)

    private val readyCaps = PrinterCapabilities(connected = true, developerMode = true, hasAms = true, activity = PrintActivity.IDLE)

    private fun unit(vararg trays: AmsTray, phase: DryPhase = DryPhase.OFF, ams: Int = 0, printer: Int = 1) =
        AmsUnit(printer, "X2D", ams, false, null, null, true, phase, 0, null, trays.toList(), readyCaps)

    private fun assess(u: AmsUnit, trayId: Int, all: List<AmsUnit> = listOf(u)) =
        SlotLoadPolicy.assess(spool, u, u.trays.first { it.trayId == trayId }, all)

    @Test
    fun `valid ids pass`() = assertNull(SlotLoadPolicy.validate(5, 1, 0, 3))

    @Test
    fun `invalid ids are rejected with a message`() {
        assertNotNull(SlotLoadPolicy.validate(0, 1, 0, 0))
        assertNotNull(SlotLoadPolicy.validate(5, 0, 0, 0))
        assertNotNull(SlotLoadPolicy.validate(5, 1, -1, 0))
        assertNotNull(SlotLoadPolicy.validate(5, 1, 0, -1))
        assertNotNull(SlotLoadPolicy.validate(5, 1, 0, 4))
    }

    @Test
    fun `an empty slot has only the empty-slot note`() {
        val a = assess(unit(tray(0)), 0)
        assertEquals(listOf<SlotWarning>(SlotWarning.SlotLooksEmpty), a.warnings)
        assertEquals("PLA, Teal", a.pushSummary)
    }

    @Test
    fun `another recorded spool is a replace warning`() {
        val a = assess(unit(tray(0, "PLA", loaded = 9)), 0)
        assertTrue(SlotWarning.ReplacesSpool(9) in a.warnings)
        assertTrue(a.warnings.none { it is SlotWarning.OverwritesDetected })
    }

    @Test
    fun `the same spool already in the slot is not a replace`() {
        val a = assess(unit(tray(0, "PLA", loaded = 5)), 0)
        assertTrue(a.warnings.isEmpty())
    }

    @Test
    fun `filament detected by the printer with no record warns about overwrite`() {
        val a = assess(unit(tray(0, "PLA")), 0)
        assertEquals(listOf<SlotWarning>(SlotWarning.OverwritesDetected("PLA")), a.warnings)
    }

    @Test
    fun `material mismatch ignores case and punctuation and blanks`() {
        assertTrue(assess(unit(tray(0, "PETG", loaded = 5)), 0).warnings.any { it is SlotWarning.MaterialMismatch })
        assertTrue(assess(unit(tray(0, "pla", loaded = 5)), 0).warnings.isEmpty())
        assertTrue(assess(unit(tray(0, "P-L-A", loaded = 5)), 0).warnings.isEmpty())
    }

    @Test
    fun `spool recorded in another slot warns that it moves`() {
        val target = unit(tray(0))
        val other = unit(tray(1, "PLA", loaded = 5), ams = 1)
        val a = assess(target, 0, listOf(target, other))
        assertTrue(a.warnings.any { it is SlotWarning.MovesFromOtherSlot })
    }

    @Test
    fun `drying unit warns`() {
        assertTrue(SlotWarning.UnitDrying in assess(unit(tray(0), phase = DryPhase.DRYING), 0).warnings)
    }

    @Test
    fun `summary falls back to the title when material and colour are blank`() {
        val blank = spool.copy(material = "", colorName = "")
        val u = unit(tray(0))
        assertEquals(blank.title, SlotLoadPolicy.assess(blank, u, u.trays.first(), listOf(u)).pushSummary)
    }

    // --- preflight ---------------------------------------------------------------------------------------------------

    private val ready = PrinterCapabilities(connected = true, developerMode = true, hasAms = true, activity = PrintActivity.IDLE)
    private val plain = AmsTray(0, "PLA", "", "FFFFFF", null, null)

    @Test
    fun `an idle printer with developer mode on and a plain slot has no blockers`() =
        assertTrue(SlotLoadPolicy.preflight(ready, plain, "X2D").isEmpty())

    @Test
    fun `each busy state blocks`() {
        assertEquals(
            listOf<SlotBlocker>(SlotBlocker.PrinterBusy),
            SlotLoadPolicy.preflight(ready.copy(activity = PrintActivity.BUSY), plain, "X2D"),
        )
    }

    @Test
    fun `an RFID spool in the slot blocks`() {
        val b = SlotLoadPolicy.preflight(ready, plain.copy(hasRfidTag = true, subBrand = "PLA Basic"), "X2D")
        assertEquals(listOf<SlotBlocker>(SlotBlocker.RfidSpool(1, "PLA Basic")), b)
        assertTrue(SlotLoadPolicy.message(b.single(), "X2D").contains("Bambu RFID spool (PLA Basic)"))
    }

    @Test
    fun `developer mode off offline and no AMS block through the tool table`() {
        assertTrue(SlotLoadPolicy.preflight(ready.copy(developerMode = false), plain, "X2D").single() is SlotBlocker.ToolUnavailable)
        assertTrue(
            SlotLoadPolicy.preflight(ready.copy(connected = false, activity = PrintActivity.OFFLINE), plain, "X2D").any {
                it is SlotBlocker.ToolUnavailable
            },
        )
        assertTrue(SlotLoadPolicy.preflight(ready.copy(hasAms = false), plain, "X2D").single() is SlotBlocker.ToolUnavailable)
    }

    @Test
    fun `an unknown state blocks but an unknown developer mode does not`() {
        assertEquals(
            listOf<SlotBlocker>(SlotBlocker.CannotTell),
            SlotLoadPolicy.preflight(ready.copy(activity = PrintActivity.UNKNOWN), plain, "X2D"),
        )
        assertTrue(SlotLoadPolicy.preflight(ready.copy(developerMode = null), plain, "X2D").isEmpty())
    }

    @Test
    fun `several blockers are all reported in a stable order`() {
        val b = SlotLoadPolicy.preflight(
            ready.copy(developerMode = false, activity = PrintActivity.BUSY),
            plain.copy(hasRfidTag = true),
            "X2D",
        )
        assertEquals(listOf("ToolUnavailable", "PrinterBusy", "RfidSpool"), b.map { it::class.simpleName })
    }

    @Test
    fun `an empty slot and an unknown developer mode produce notes not blockers`() {
        val u = unit(tray(0)).copy(capabilities = ready.copy(developerMode = null))
        val a = SlotLoadPolicy.assess(spool, u, u.trays.first(), listOf(u))
        assertTrue(a.warnings.any { it is SlotWarning.SlotLooksEmpty })
        assertTrue(a.warnings.any { it is SlotWarning.MayBeRefused })
    }
}
