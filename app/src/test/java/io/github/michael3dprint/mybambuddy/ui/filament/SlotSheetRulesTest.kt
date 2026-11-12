package io.github.michael3dprint.mybambuddy.ui.filament

import io.github.michael3dprint.mybambuddy.domain.model.AmsTray
import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.domain.model.DryPhase
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import org.junit.Assert.assertEquals
import org.junit.Test

class SlotSheetRulesTest {
    private fun spool(id: Int, material: String, sub: String = "", color: String = "") =
        Spool(id, "Bambu Lab", material, sub, color, "FF0000", 1000, 0, null, null)

    private fun tray(id: Int, loaded: Int?) = AmsTray(id, "PLA", "", "FF0000", 50, loaded)

    private fun unit(printer: Int, ams: Int, vararg trays: AmsTray) =
        AmsUnit(printer, "P$printer", ams, false, null, null, false, DryPhase.OFF, 0, null, trays.toList())

    @Test
    fun `search matches title, colour name or material, ignoring case`() {
        val spools = listOf(spool(1, "PLA", "Basic", "Blue"), spool(2, "PETG", "", "Red"), spool(3, "PLA", "Matte", "Black"))
        assertEquals(listOf(1, 3), filterAndRank(spools, "pla", "").map { it.id })
        assertEquals(listOf(2), filterAndRank(spools, "red", "").map { it.id })
        assertEquals(listOf(1), filterAndRank(spools, "  blue ", "").map { it.id })
        assertEquals(emptyList<Int>(), filterAndRank(spools, "nylon", "").map { it.id })
    }

    @Test
    fun `spools of the slot's own material come first, then by title`() {
        val spools = listOf(spool(1, "PETG", "Basic"), spool(2, "PLA", "Matte"), spool(3, "PLA", "Basic"))
        assertEquals(listOf(3, 2, 1), filterAndRank(spools, "", "pla").map { it.id })
        assertEquals(listOf(1, 3, 2), filterAndRank(spools, "", "").map { it.id })
    }

    @Test
    fun `where a spool is loaded says this slot for the tapped one and the place for the others`() {
        val units = listOf(unit(1, 0, tray(0, 7), tray(1, 8)), unit(1, 1, tray(0, 9)))
        val slot = SlotTarget(units[0], 0)
        assertEquals(mapOf(7 to "In this slot", 8 to "In AMS 1 slot 2", 9 to "In AMS 2 slot 1"), whereLoaded(units, slot))
    }
}
