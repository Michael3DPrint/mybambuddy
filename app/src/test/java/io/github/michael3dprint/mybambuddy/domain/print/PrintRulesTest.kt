package io.github.michael3dprint.mybambuddy.domain.print

import io.github.michael3dprint.mybambuddy.domain.model.PlateInfo
import io.github.michael3dprint.mybambuddy.domain.model.PrintFile
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import io.github.michael3dprint.mybambuddy.domain.model.RequiredFilament
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintRulesTest {
    @Test
    fun `only 3mf files are accepted`() {
        assertNull(PrintFileRules.refusal("Benchy.gcode.3mf"))
        assertNull(PrintFileRules.refusal("Model.3MF"))
        assertTrue(PrintFileRules.refusal("part.gcode")!!.contains("Raw G-code"))
        assertNotNull(PrintFileRules.refusal("part.stl"))
        assertNotNull(PrintFileRules.refusal("3mf"))
    }

    @Test
    fun `makerworld links are read with or without scheme and profile`() {
        assertEquals(MakerWorldLinkParts(12345, null), MakerWorldLink.parse("https://makerworld.com/en/models/12345-benchy"))
        assertEquals(MakerWorldLinkParts(12345, 678), MakerWorldLink.parse("makerworld.com/models/12345#profileId-678"))
        assertEquals(MakerWorldLinkParts(9, null), MakerWorldLink.parse("  https://www.makerworld.com/models/9  "))
    }

    @Test
    fun `other links and junk are not makerworld links`() {
        assertNull(MakerWorldLink.parse("https://example.com/models/12345"))
        assertNull(MakerWorldLink.parse("https://makerworld.com.evil.test/models/1"))
        assertNull(MakerWorldLink.parse("https://makerworld.com/en/users/1"))
        assertNull(MakerWorldLink.parse("not a link"))
        assertNull(MakerWorldLink.parse(""))
    }

    private fun need(slot: Int, type: String, color: String = "FFFFFF", grams: Double = 10.0) = RequiredFilament(slot, type, color, grams)

    private fun slot(tray: Int, material: String, color: String = "FFFFFF", empty: Boolean = false, grams: Int? = null) =
        SlotCandidate(tray, "AMS 1 slot ${tray + 1}", material, color, empty, grams)

    @Test
    fun `matching picks the same type with the nearest colour and never reuses a slot`() {
        val slots = listOf(slot(0, "PLA", "FFFFFF"), slot(1, "PLA", "101010"), slot(2, "PETG", "FFFFFF"))
        val result = FilamentMatcher.propose(listOf(need(1, "PLA", "000000"), need(2, "PLA", "F0F0F0")), slots)
        assertEquals(1, result[1]?.tray)
        assertEquals(0, result[2]?.tray)
    }

    @Test
    fun `a filament with no slot of its type stays unmatched and empty slots are skipped`() {
        val slots = listOf(slot(0, "PLA", empty = true), slot(1, "PETG"))
        val result = FilamentMatcher.propose(listOf(need(1, "PLA")), slots)
        assertTrue(result.containsKey(1))
        assertNull(result[1])
    }

    @Test
    fun `the ams mapping has one entry per project slot`() {
        assertEquals(listOf(5, -1, 2), AmsMapping.build(mapOf(1 to 5, 3 to 2)))
        assertEquals(emptyList<Int>(), AmsMapping.build(emptyMap()))
    }

    private fun file(sliced: Boolean = true, model: String? = "Bambu Lab X2D 0.4 nozzle") = PrintFile(
        7,
        "a.gcode.3mf",
        model,
        listOf(PlateInfo(1, null, if (sliced) 600 else null, 5.0, emptyList())),
        createdHere = true,
    )

    private fun input(
        file: PrintFile = file(),
        state: PrinterState = PrinterState.IDLE,
        refusal: String? = null,
        required: List<RequiredFilament> = listOf(need(1, "PLA")),
        chosen: Map<Int, SlotCandidate?> = mapOf(1 to slot(0, "PLA")),
        errors: Boolean = false,
        drying: Set<Int> = emptySet(),
    ) = PrintCheckInput(
        file = file,
        printerModel = "X2D",
        printerState = state,
        startRefusal = refusal,
        hasPrinterErrors = errors,
        required = required,
        chosen = chosen,
        dryingTrays = drying,
    )

    private fun levels(i: PrintCheckInput) = PrintValidation.evaluate(i).map { it.level }

    @Test
    fun `a good sliced file on an idle printer has no findings`() {
        val findings = PrintValidation.evaluate(input())
        assertEquals(emptyList<Finding>(), findings)
        assertTrue(PrintValidation.canAdd(findings) && PrintValidation.canStart(findings))
    }

    @Test
    fun `an unsliced file blocks both actions`() {
        val findings = PrintValidation.evaluate(input(file = file(sliced = false)))
        assertFalse(PrintValidation.canAdd(findings))
        assertFalse(PrintValidation.canStart(findings))
    }

    @Test
    fun `another printer model blocks and an unknown model only warns`() {
        assertTrue(levels(input(file = file(model = "Bambu Lab P1S 0.4 nozzle"))).contains(FindingLevel.BLOCKER))
        assertEquals(listOf(FindingLevel.WARNING), levels(input(file = file(model = null))))
    }

    @Test
    fun `slot problems are blockers and a type mismatch is a warning`() {
        assertTrue(levels(input(chosen = mapOf(1 to null))).contains(FindingLevel.BLOCKER))
        assertTrue(levels(input(chosen = mapOf(1 to slot(0, "PLA", empty = true)))).contains(FindingLevel.BLOCKER))
        val two = listOf(need(1, "PLA"), need(2, "PLA"))
        assertTrue(levels(input(required = two, chosen = mapOf(1 to slot(0, "PLA"), 2 to slot(0, "PLA")))).contains(FindingLevel.BLOCKER))
        assertEquals(listOf(FindingLevel.WARNING), levels(input(chosen = mapOf(1 to slot(0, "PETG")))))
        assertTrue(levels(input(chosen = mapOf(1 to slot(0, "")))).contains(FindingLevel.BLOCKER))
    }

    @Test
    fun `low filament, drying and colour are warnings or info`() {
        assertEquals(listOf(FindingLevel.WARNING), levels(input(chosen = mapOf(1 to slot(0, "PLA", grams = 3)))))
        assertEquals(listOf(FindingLevel.WARNING), levels(input(drying = setOf(0))))
        assertEquals(listOf(FindingLevel.INFO), levels(input(chosen = mapOf(1 to slot(0, "PLA", "000000")))))
    }

    @Test
    fun `a failed last print does not block the next print`() {
        val findings = PrintValidation.evaluate(input(state = PrinterState.FAILED))
        assertTrue(PrintValidation.canAdd(findings) && PrintValidation.canStart(findings))
    }

    @Test
    fun `printer problems block start but not adding to the queue`() {
        for (state in listOf(PrinterState.PRINTING, PrinterState.PAUSED, PrinterState.OFFLINE, PrinterState.ERROR)) {
            val findings = PrintValidation.evaluate(input(state = state))
            assertTrue("$state", PrintValidation.canAdd(findings))
            assertFalse("$state", PrintValidation.canStart(findings))
        }
        val refused = PrintValidation.evaluate(input(refusal = "Developer LAN mode is off."))
        assertTrue(PrintValidation.canAdd(refused))
        assertFalse(PrintValidation.canStart(refused))
        assertEquals(listOf(FindingLevel.WARNING), levels(input(errors = true)))
        val plate = PrintValidation.evaluate(input().copy(awaitingPlateClear = true))
        assertTrue(PrintValidation.canAdd(plate))
        assertFalse(PrintValidation.canStart(plate))
    }

    @Test
    fun `findings are listed most serious first`() {
        val order = levels(input(state = PrinterState.OFFLINE, errors = true, chosen = mapOf(1 to slot(0, "PETG"))))
        assertEquals(order.sortedBy { it.ordinal }, order)
    }
}
