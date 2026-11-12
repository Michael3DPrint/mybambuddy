package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.AmsTray
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SlotFillRuleTest {
    private fun tray(percent: Int?, spool: Int? = null, material: String = "PLA", rfid: Boolean = false) =
        AmsTray(0, material, "", if (material.isBlank()) "" else "FF0000", percent, spool, rfid)

    private fun spool(id: Int, label: Int, used: Int) = Spool(id, "Bambu", "PLA", "", "Red", "FF0000", label, used, null, null)

    @Test
    fun `an untagged slot with a linked spool uses the inventory's grams and fraction`() {
        val fill = SlotFillRule.estimate(tray(percent = 10, spool = 7), spool(7, 1000, 250))
        assertEquals(SlotFill(0.75f, 750), fill)
    }

    @Test
    fun `a Bambu RFID slot uses the printer's percent even when the inventory says the spool is used up`() {
        val usedUp = spool(7, 1000, 1000)
        assertEquals(SlotFill(0.75f, 750), SlotFillRule.estimate(tray(percent = 75, spool = 7, rfid = true), usedUp))
    }

    @Test
    fun `a Bambu RFID slot applies its percent to the linked spool's label weight`() {
        assertEquals(SlotFill(0.5f, 500), SlotFillRule.estimate(tray(percent = 50, spool = 7, rfid = true), spool(7, 1000, 0)))
        assertEquals(SlotFill(0.5f, 1500), SlotFillRule.estimate(tray(percent = 50, spool = 7, rfid = true), spool(7, 3000, 0)))
    }

    @Test
    fun `a Bambu RFID slot with no percent falls back to the linked spool`() {
        assertEquals(SlotFill(0.75f, 750), SlotFillRule.estimate(tray(percent = null, spool = 7, rfid = true), spool(7, 1000, 250)))
    }

    @Test
    fun `without a linked spool the percent applies to an assumed 1000 g spool`() {
        assertEquals(SlotFill(0.8f, 800), SlotFillRule.estimate(tray(percent = 80), null))
    }

    @Test
    fun `a spool that is not the one linked to this slot is ignored`() {
        assertEquals(SlotFill(0.5f, 500), SlotFillRule.estimate(tray(percent = 50, spool = 7), spool(8, 1000, 100)))
    }

    @Test
    fun `no percent and no linked spool means no estimate, and an empty slot has none`() {
        assertNull(SlotFillRule.estimate(tray(percent = null), null))
        assertNull(SlotFillRule.estimate(tray(percent = 90, material = ""), null))
    }

    @Test
    fun `values outside the range are held inside it`() {
        assertEquals(SlotFill(1f, 1000), SlotFillRule.estimate(tray(percent = 140), null))
        assertEquals(0f, SlotFillRule.estimate(tray(percent = 5, spool = 1), spool(1, 1000, 1200))!!.fraction, 0f)
    }

    @Test
    fun `percent of a fill is its fraction as a whole number`() {
        assertEquals(75, SlotFill(0.75f, 750).percent)
    }

    @Test
    fun `a full slot is solid along the whole bar`() {
        val stops = barStops(1f)
        assertEquals(1f, stops.first().alpha, 0f)
        assertEquals(1f, stops[1].position, 0f)
        assertEquals(1f, stops[1].alpha, 0f)
    }

    @Test
    fun `the fade begins where the amount ends and then stays faint`() {
        val stops = barStops(0.5f)
        assertEquals(listOf(0f, 0.5f, 0.58f, 1f), stops.map { it.position })
        assertEquals(listOf(1f, 1f, 0.1f, 0.1f), stops.map { it.alpha })
    }

    @Test
    fun `a nearly empty slot is mostly faint and an unknown one is faint all along`() {
        assertEquals(listOf(0f, 0.05f, 0.13f, 1f), barStops(0.05f).map { it.position })
        assertEquals(listOf(0.1f, 0.1f), barStops(null).map { it.alpha })
        assertEquals(listOf(0f, 1f), barStops(null).map { it.position })
    }

    @Test
    fun `stops are in order and held inside the bar for any input`() {
        listOf(-1f, 0f, 0.3f, 0.9f, 1f, 7f).forEach { f ->
            val positions = barStops(f).map { it.position }
            assertEquals(positions.sorted(), positions)
            assertEquals(true, positions.all { it in 0f..1f })
        }
    }
}
