package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.JobOptions
import io.github.michael3dprint.mybambuddy.domain.model.RequiredFilament
import io.github.michael3dprint.mybambuddy.domain.model.TriState
import io.github.michael3dprint.mybambuddy.domain.print.SlotCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JobEditTest {
    private val original = JobSettings(1, 1, listOf(5, -1, 2), JobOptions())

    private fun slot(tray: Int, material: String = "PLA") = SlotCandidate(tray, "slot $tray", material, "FFFFFF", false)

    @Test
    fun `nothing changed means an empty change`() {
        assertTrue(JobEdit.diff(original, original).isEmpty)
    }

    @Test
    fun `only the fields that differ are in the change`() {
        val edited = original.copy(printerId = 2, options = JobOptions(bedLevelling = TriState.OFF, timelapse = true))
        val change = JobEdit.diff(original, edited)
        assertEquals(JobChange(printerId = 2, bedLevelling = TriState.OFF, timelapse = true), change)
        assertFalse(change.isEmpty)
        assertNull(change.plateId)
        assertNull(change.amsMapping)
    }

    @Test
    fun `a changed mapping is sent whole`() {
        assertEquals(listOf(5, 3, 2), JobEdit.diff(original, original.copy(amsMapping = listOf(5, 3, 2))).amsMapping)
    }

    @Test
    fun `the confirmation says each change in words`() {
        val change =
            JobChange(
                printerId = 2,
                plateId = 3,
                amsMapping = listOf(1),
                bedLevelling = TriState.ON,
                vibrationCalibration = false,
                useAms = true,
            )
        val lines = JobEdit.lines(change) { "P$it" }
        assertEquals(
            listOf(
                "Printer: P2",
                "Plate: 3",
                "Filament slots changed",
                "Bed levelling: On",
                "Vibration calibration: off",
                "Use the AMS: on",
            ),
            lines,
        )
    }

    @Test
    fun `the mapping is read back and built from the slot choices`() {
        val need = listOf(RequiredFilament(1, "PLA", "FFFFFF", 5.0), RequiredFilament(3, "PLA", "FFFFFF", 5.0))
        val slots = listOf(slot(5), slot(2))
        val chosen = JobEdit.chosenFrom(listOf(5, -1, 2), need, slots)
        assertEquals(5, chosen[1]?.tray)
        assertEquals(2, chosen[3]?.tray)
        assertEquals(listOf(5, -1, 2), JobEdit.mappingOf(chosen))
        assertNull(JobEdit.chosenFrom(listOf(9), need, slots)[1])
    }

    @Test
    fun `after a re-slice an earlier slot is kept and a new filament gets a proposal`() {
        val need = listOf(RequiredFilament(1, "PLA", "FFFFFF", 5.0), RequiredFilament(2, "PETG", "FFFFFF", 5.0))
        val slots = listOf(slot(5), slot(7, "PETG"))
        val carried = JobEdit.carryOver(need, mapOf(1 to slot(5)), slots)
        assertEquals(5, carried[1]?.tray)
        assertEquals(7, carried[2]?.tray)
    }

    @Test
    fun `server option values are read as text or as true and false`() {
        assertEquals(TriState.ON, TriState.fromServer("on"))
        assertEquals(TriState.OFF, TriState.fromServer("OFF"))
        assertEquals(TriState.ON, TriState.fromServer(true))
        assertEquals(TriState.OFF, TriState.fromServer(false))
        assertEquals(TriState.AUTO, TriState.fromServer(null))
        assertEquals(TriState.AUTO, TriState.fromServer("surprise"))
    }
}
