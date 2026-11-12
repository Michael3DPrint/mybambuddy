package io.github.michael3dprint.mybambuddy.domain.slicing

import io.github.michael3dprint.mybambuddy.domain.model.BedType
import io.github.michael3dprint.mybambuddy.domain.model.SliceOptions
import io.github.michael3dprint.mybambuddy.domain.model.SliceRequest
import io.github.michael3dprint.mybambuddy.domain.model.SlicerPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SliceMemoryRulesTest {
    private fun p(id: String) = SlicerPreset("cloud", id, id, null, emptyList())

    @Test
    fun `the remembered plate is read back and an unknown name is ignored`() {
        assertEquals(BedType.TEXTURED_PEI, SliceMemoryRules.bed(SliceMemory(bed = "Textured PEI Plate")))
        assertNull(SliceMemoryRules.bed(SliceMemory(bed = "Glass Plate")))
        assertNull(SliceMemoryRules.bed(SliceMemory()))
    }

    @Test
    fun `a remembered preset is used only while it still exists`() {
        val memory = SliceMemory(processByModel = mapOf("x2d" to "GP1"), filamentByType = mapOf("PLA" to "F1"))
        assertEquals("GP1", SliceMemoryRules.process(memory, "Bambu Lab X2D 0.4 nozzle", listOf(p("GP2"), p("GP1")))?.id)
        assertNull(SliceMemoryRules.process(memory, "X2D", listOf(p("GP2"))))
        assertNull(SliceMemoryRules.process(memory, "P1S", listOf(p("GP1"))))
        assertEquals("F1", SliceMemoryRules.filament(memory, "pla", listOf(p("F1")))?.id)
        assertNull(SliceMemoryRules.filament(memory, "PETG", listOf(p("F1"))))
    }

    @Test
    fun `the designer's settings count only when the file targets this printer`() {
        val memory = SliceMemory(autoOrient = true, useEmbeddedSettings = true)
        assertEquals(
            SliceOptions(autoOrient = true, useEmbeddedSettings = true),
            SliceMemoryRules.options(memory, embeddedAvailable = true),
        )
        assertEquals(SliceOptions(autoOrient = true), SliceMemoryRules.options(memory, embeddedAvailable = false))
    }

    @Test
    fun `a slice is remembered without losing other printers or types`() {
        val before = SliceMemory(processByModel = mapOf("p1s" to "GPX"), filamentByType = mapOf("PETG" to "FX"))
        val request = SliceRequest(
            printer = p("GM045"),
            process = p("GP1"),
            filaments = listOf(p("F1"), p("F2")),
            colours = listOf("", ""),
            plate = 1,
            bed = BedType.ENGINEERING,
            options = SliceOptions(autoArrange = true),
        )
        val after = SliceMemoryRules.after(before, request, "Bambu Lab X2D", listOf("pla", "TPU"))
        assertEquals("Engineering Plate", after.bed)
        assertEquals(mapOf("p1s" to "GPX", "x2d" to "GP1"), after.processByModel)
        assertEquals(mapOf("PETG" to "FX", "PLA" to "F1", "TPU" to "F2"), after.filamentByType)
        assertEquals(true, after.autoArrange)
        assertEquals(false, after.autoOrient)
    }
}
