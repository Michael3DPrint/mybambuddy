package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.AmsTray
import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.domain.model.DryPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoolLocationsTest {
    private fun tray(id: Int, loaded: Int?) = AmsTray(id, "PLA", "", "FF0000", 50, loaded)

    private fun unit(printer: Int, ams: Int, vararg trays: AmsTray) =
        AmsUnit(printer, "P$printer", ams, false, null, null, false, DryPhase.OFF, 0, null, trays.toList())

    @Test
    fun `one printer gives plain places`() {
        val units = listOf(unit(1, 0, tray(2, 2), tray(3, 3), tray(0, null)))
        assertEquals(mapOf(2 to "AMS 1, slot 3", 3 to "AMS 1, slot 4"), SpoolLocations.of(units))
    }

    @Test
    fun `two printers put the printer name first`() {
        val units = listOf(unit(1, 0, tray(0, 5)), unit(2, 1, tray(1, 6)))
        assertEquals(mapOf(5 to "P1, AMS 1, slot 1", 6 to "P2, AMS 2, slot 2"), SpoolLocations.of(units))
    }

    @Test
    fun `a spool linked to two slots lists both and nothing loaded gives an empty map`() {
        val two = listOf(unit(1, 0, tray(0, 4), tray(1, 4)))
        assertEquals(mapOf(4 to "AMS 1, slot 1; AMS 1, slot 2"), SpoolLocations.of(two))
        assertTrue(SpoolLocations.of(listOf(unit(1, 0, tray(0, null)))).isEmpty())
        assertTrue(SpoolLocations.of(emptyList()).isEmpty())
    }
}
