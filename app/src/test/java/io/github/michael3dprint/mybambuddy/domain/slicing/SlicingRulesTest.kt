package io.github.michael3dprint.mybambuddy.domain.slicing

import io.github.michael3dprint.mybambuddy.domain.model.BedType
import io.github.michael3dprint.mybambuddy.domain.model.ProjectSlot
import io.github.michael3dprint.mybambuddy.domain.model.SliceJobState
import io.github.michael3dprint.mybambuddy.domain.model.SliceOptions
import io.github.michael3dprint.mybambuddy.domain.model.SlicerPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlicingRulesTest {
    private fun p(name: String, type: String? = null, compat: List<String> = emptyList(), source: String = "cloud") =
        SlicerPreset(source, name, name, type, compat)

    private val printers = listOf(
        p("Bambu Lab X2D 0.2 nozzle"),
        p("Bambu Lab X2D 0.4 nozzle"),
        p("Bambu Lab X1 Carbon 0.4 nozzle"),
        p("Bambu Lab X2D", source = "standard"),
    )
    private val x2d04 = printers[1]
    private val processes = listOf(
        p("0.20mm Standard @BBL X2D - Copy"),
        p("0.10mm Standard @BBL X2D 0.2 nozzle", compat = listOf("Bambu Lab X2D 0.2 nozzle")),
        p("0.20mm Standard @BBL X1C", compat = listOf("Bambu Lab X1 Carbon 0.4 nozzle")),
        p("0.20mm Standard @BBL X2D", compat = listOf("Bambu Lab X2D 0.4 nozzle")),
        p("0.08mm High Quality @BBL X2D", compat = listOf("Bambu Lab X2D 0.4 nozzle")),
    )
    private val filaments = listOf(
        p("Polymaker PLA Pro @Bambu Lab X2D 0.4 nozzle"),
        p("Bambu PLA Basic @BBL X2D 0.2 nozzle", "PLA"),
        p("Bambu PLA Basic @BBL X2D", "PLA"),
        p("Generic PLA @BBL X2D", "PLA"),
        p("Bambu PETG Basic @BBL X2D", "PETG"),
        p("Bambu PLA Basic @BBL X1C", "PLA"),
    )

    @Test
    fun `the printer preset is the 0 point 4 nozzle one for the model and never another model`() {
        assertEquals("Bambu Lab X2D 0.4 nozzle", SlicePicker.printer("X2D", printers)?.name)
        assertEquals("Bambu Lab X1 Carbon 0.4 nozzle", SlicePicker.printer("X1 Carbon", printers)?.name)
        assertNull(SlicePicker.printer("P2S", printers))
        assertNull(SlicePicker.printer("X1", listOf(p("Bambu Lab X1C 0.4 nozzle"))))
    }

    @Test
    fun `process presets fit the printer and the standard one comes first, copies later`() {
        val names = SlicePicker.processOptions(x2d04, "X2D", processes).map { it.name }
        assertEquals("0.20mm Standard @BBL X2D", names.first())
        assertTrue(names.contains("0.08mm High Quality @BBL X2D"))
        assertTrue(names.contains("0.20mm Standard @BBL X2D - Copy"))
        assertTrue(names.none { it.contains("X1C") || it.contains("0.2 nozzle") })
        assertTrue(names.indexOf("0.20mm Standard @BBL X2D - Copy") > names.indexOf("0.20mm Standard @BBL X2D"))
    }

    @Test
    fun `filament presets match the type and nozzle, Bambu Basic first`() {
        val names = SlicePicker.filamentOptions("PLA", x2d04, "X2D", filaments).map { it.name }
        assertEquals("Bambu PLA Basic @BBL X2D", names.first())
        assertTrue(names.contains("Polymaker PLA Pro @Bambu Lab X2D 0.4 nozzle"))
        assertTrue(names.none { it.contains("PETG") || it.contains("0.2 nozzle") || it.contains("X1C") })
        assertEquals("Bambu PETG Basic @BBL X2D", SlicePicker.filamentOptions("PETG", x2d04, "X2D", filaments).single().name)
    }

    @Test
    fun `defaults give an unused slot the first pick and leave a used slot without a match empty`() {
        val slots =
            listOf(ProjectSlot(1, "PLA", "FF0000", true), ProjectSlot(2, "TPU", "000000", false), ProjectSlot(3, "TPU", "000000", true))
        val picks = SlicePicker.defaultFilaments(slots, x2d04, "X2D", filaments)
        assertEquals("Bambu PLA Basic @BBL X2D", picks[1]?.name)
        assertEquals("Bambu PLA Basic @BBL X2D", picks[2]?.name)
        assertNull(picks[3])
    }

    @Test
    fun `the request needs every choice, keeps slot order and sends a colour per slot`() {
        val slots = listOf(ProjectSlot(2, "PLA", "00FF00FF", true), ProjectSlot(1, "PLA", "#ff0000", true))
        val pla = filaments[2]
        val proc = processes[3]
        val request = SliceRequestBuilder.build(x2d04, proc, slots, mapOf(1 to pla, 2 to pla), plate = 1, bed = BedType.COOL)
        assertNotNull(request)
        assertEquals(listOf("#FF0000", "#00FF00"), request!!.colours)
        assertEquals(2, request.filaments.size)
        assertNull(SliceRequestBuilder.build(x2d04, proc, slots, mapOf(1 to pla, 2 to null), 1, BedType.COOL))
        assertNull(SliceRequestBuilder.build(x2d04, null, slots, mapOf(1 to pla, 2 to pla), 1, BedType.COOL))
        assertNull(SliceRequestBuilder.build(x2d04, proc, slots, mapOf(1 to pla, 2 to pla), 1, null))
        assertNull(SliceRequestBuilder.build(null, proc, slots, mapOf(1 to pla, 2 to pla), 1, BedType.COOL))
        assertNull(SliceRequestBuilder.build(x2d04, proc, emptyList(), emptyMap(), 1, BedType.COOL))
        assertEquals("", SliceRequestBuilder.colour("zz"))
        assertEquals("", SliceRequestBuilder.colour(""))
    }

    @Test
    fun `job states are read, an unknown status keeps waiting and a bad completion fails`() {
        assertEquals(SliceJobState.Running(null), SliceJobReader.read("pending", null, null, null))
        assertEquals(SliceJobState.Running(42), SliceJobReader.read("running", mapOf("percent" to 42.0), null, null))
        assertEquals(SliceJobState.Running(50), SliceJobReader.read("running", mapOf("progress" to 0.5), null, null))
        assertEquals(SliceJobState.Running(null), SliceJobReader.read("something-new", null, null, null))
        assertEquals(
            SliceJobState.Done(9, "x.gcode.3mf"),
            SliceJobReader.read("completed", null, mapOf("library_file_id" to 9.0, "name" to "x.gcode.3mf"), null),
        )
        assertTrue(SliceJobReader.read("completed", null, null, null) is SliceJobState.Failed)
        assertEquals(SliceJobState.Failed("no slicer"), SliceJobReader.read("failed", null, null, "no slicer"))
        assertEquals(SliceJobState.Failed("The slice failed."), SliceJobReader.read("failed", null, null, ""))
    }

    @Test
    fun `a loaded spool's product line picks its preset, else the best of the type`() {
        val options = listOf(p("Bambu PLA Basic @BBL X2D", "PLA"), p("Bambu PLA Matte @BBL X2D", "PLA"), p("Generic PLA @BBL X2D", "PLA"))
        assertEquals("Bambu PLA Matte @BBL X2D", SlicePicker.presetForSpool("PLA", "PLA Matte", x2d04, "X2D", options)?.name)
        assertEquals("Bambu PLA Basic @BBL X2D", SlicePicker.presetForSpool("PLA", "", x2d04, "X2D", options)?.name)
        assertEquals("Bambu PLA Basic @BBL X2D", SlicePicker.presetForSpool("PLA", "Unheard Of", x2d04, "X2D", options)?.name)
        assertNull(SlicePicker.presetForSpool("TPU", "TPU 95A", x2d04, "X2D", options))
    }

    @Test
    fun `plate names are the ones Bambuddy's slice request takes`() {
        assertEquals(
            listOf("Cool Plate", "Engineering Plate", "High Temp Plate", "Textured PEI Plate", "Supertack Plate"),
            BedType.entries.map { it.apiName },
        )
    }

    @Test
    fun `options go into the request and model names compare across spellings`() {
        val slots = listOf(ProjectSlot(1, "PLA", "FF0000", true))
        val pla = filaments[2]
        val request = SliceRequestBuilder.build(
            x2d04,
            processes[3],
            slots,
            mapOf(1 to pla),
            1,
            BedType.COOL,
            SliceOptions(autoArrange = true),
        )
        assertEquals(SliceOptions(autoArrange = true), request!!.options)
        assertEquals(SliceOptions(), SliceRequestBuilder.build(x2d04, processes[3], slots, mapOf(1 to pla), 1, BedType.COOL)!!.options)
        assertTrue(io.github.michael3dprint.mybambuddy.domain.print.ModelNames.same("X2D", "Bambu Lab X2D 0.4 nozzle"))
        assertFalse(io.github.michael3dprint.mybambuddy.domain.print.ModelNames.same("X1", "Bambu Lab X1 Carbon 0.4 nozzle"))
        assertFalse(io.github.michael3dprint.mybambuddy.domain.print.ModelNames.same(null, "X2D"))
        assertFalse(io.github.michael3dprint.mybambuddy.domain.print.ModelNames.same("", ""))
    }
}
