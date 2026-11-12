package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.AmsTray
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import org.junit.Assert.assertEquals
import org.junit.Test

class SlotReadBackTest {
    private val spool = Spool(5, "Polymaker", "PLA", "Pro", "Teal", "008080", 1000, 0, null, null)
    private fun tray(material: String, color: String) = AmsTray(0, material, "", color, null, null)

    @Test
    fun `same material and colour is confirmed`() = assertEquals(ReadBack.CONFIRMED, SlotReadBack.compare(spool, tray("PLA", "008080FF")))

    @Test
    fun `case hash and alpha are ignored`() {
        assertEquals(ReadBack.CONFIRMED, SlotReadBack.compare(spool.copy(colorHex = "#008080"), tray("pla", "008080ff")))
        assertEquals(ReadBack.CONFIRMED, SlotReadBack.compare(spool.copy(colorHex = "00808080"), tray("PLA", "008080FF")))
    }

    @Test
    fun `a different material or colour is different`() {
        assertEquals(ReadBack.DIFFERENT, SlotReadBack.compare(spool, tray("PETG", "008080FF")))
        assertEquals(ReadBack.DIFFERENT, SlotReadBack.compare(spool, tray("PLA", "FFFFFFFF")))
    }

    @Test
    fun `an empty or missing slot is different`() {
        assertEquals(ReadBack.DIFFERENT, SlotReadBack.compare(spool, null))
        assertEquals(ReadBack.DIFFERENT, SlotReadBack.compare(spool, tray("", "")))
    }

    @Test
    fun `colour is only compared when both sides have one`() {
        assertEquals(ReadBack.CONFIRMED, SlotReadBack.compare(spool.copy(colorHex = ""), tray("PLA", "FFFFFFFF")))
        assertEquals(ReadBack.CONFIRMED, SlotReadBack.compare(spool, tray("PLA", "")))
    }
}
