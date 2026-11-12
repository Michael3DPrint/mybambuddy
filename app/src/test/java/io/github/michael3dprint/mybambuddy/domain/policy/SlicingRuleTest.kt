package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.CheckLevel
import io.github.michael3dprint.mybambuddy.domain.model.PresetSummary
import io.github.michael3dprint.mybambuddy.domain.model.PrinterRef
import io.github.michael3dprint.mybambuddy.domain.model.SlicerSettings
import io.github.michael3dprint.mybambuddy.domain.model.SlicingInputs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlicingRuleTest {
    private val good = SlicingInputs(
        settings = SlicerSettings(useSlicerApi = true, preferredSlicer = "bambu_studio", addressSet = true),
        presets = PresetSummary(14, listOf("Bambu Lab X2D", "Bambu Lab X1 Carbon"), 250, 2111, cloudSignedIn = true),
        printers = listOf(PrinterRef("Shop printer", "X2D")),
    )

    private fun check(inputs: SlicingInputs, id: String) = SlicingRule.evaluate(inputs).checks.first { it.id == id }

    @Test
    fun `everything in place is ready with no problem`() {
        val r = SlicingRule.evaluate(good)
        assertTrue(r.ready)
        assertEquals(null, r.firstProblem)
        assertTrue(r.checks.all { it.passed })
    }

    @Test
    fun `slicing switched off is a blocker with the steps to turn it on`() {
        val c = check(good.copy(settings = good.settings.copy(useSlicerApi = false)), "switch")
        assertFalse(c.passed)
        assertEquals(CheckLevel.BLOCKER, c.level)
        assertTrue(c.fixSteps.any { it.contains("Use Slicer API") })
        assertFalse(SlicingRule.evaluate(good.copy(settings = good.settings.copy(useSlicerApi = false))).ready)
    }

    @Test
    fun `an unknown or missing slicer is a blocker`() {
        val c = check(good.copy(settings = good.settings.copy(preferredSlicer = "")), "slicer")
        assertFalse(c.passed)
        assertEquals(CheckLevel.BLOCKER, c.level)
        assertEquals(
            "Slicer chosen: OrcaSlicer",
            check(good.copy(settings = good.settings.copy(preferredSlicer = "orcaslicer")), "slicer").title,
        )
    }

    @Test
    fun `no presets from the slicer app means it cannot be reached, with the install steps and the right port`() {
        val none = good.copy(presets = good.presets.copy(standardPrinterCount = 0))
        val c = check(none, "reach")
        assertFalse(c.passed)
        assertEquals(CheckLevel.BLOCKER, c.level)
        assertTrue(c.fixSteps.joinToString().contains(SlicingHelp.APP_REPOSITORY))
        assertFalse(c.fixSteps.joinToString().contains("Michael3DPrint"))
        assertTrue(c.fixSteps.joinToString().contains("3001"))
        val orca = none.copy(settings = none.settings.copy(preferredSlicer = "orcaslicer"))
        assertTrue(check(orca, "reach").fixSteps.joinToString().contains("3003"))
        assertFalse(SlicingRule.evaluate(none).ready)
    }

    @Test
    fun `an empty address is not a failure by itself but is mentioned`() {
        val c = check(good.copy(settings = good.settings.copy(addressSet = false)), "reach")
        assertTrue(c.passed)
        assertTrue(c.detail.contains("default"))
    }

    @Test
    fun `a printer without a matching preset is a warning that does not block`() {
        val inputs = good.copy(
            printers = listOf(PrinterRef("Old one", "A1 mini"), PrinterRef("Shop printer", "X2D")),
            presets = good.presets.copy(printerPresetNames = listOf("Bambu Lab X2D")),
        )
        val miss = check(inputs, "printer-preset:Old one")
        assertFalse(miss.passed)
        assertEquals(CheckLevel.WARNING, miss.level)
        assertTrue(check(inputs, "printer-preset:Shop printer").passed)
        assertTrue(SlicingRule.evaluate(inputs).ready)
        assertEquals("printer-preset:Old one", SlicingRule.evaluate(inputs).firstProblem?.id)
    }

    @Test
    fun `matching the model ignores case and a blank model is not checked`() {
        val lower = good.copy(presets = good.presets.copy(printerPresetNames = listOf("bambu lab x2d 0.4 nozzle")))
        assertTrue(check(lower, "printer-preset:Shop printer").passed)
        val blank = good.copy(printers = listOf(PrinterRef("Mystery", "  ")))
        val c = check(blank, "printer-preset:Mystery")
        assertTrue(c.passed)
        assertEquals(CheckLevel.INFO, c.level)
    }

    @Test
    fun `missing process or filament presets block`() {
        assertFalse(check(good.copy(presets = good.presets.copy(processCount = 0)), "profiles").passed)
        assertFalse(check(good.copy(presets = good.presets.copy(filamentCount = 0)), "profiles").passed)
        assertFalse(SlicingRule.evaluate(good.copy(presets = good.presets.copy(filamentCount = 0))).ready)
    }

    @Test
    fun `not being signed in to the cloud is only information`() {
        val c = check(good.copy(presets = good.presets.copy(cloudSignedIn = false)), "cloud")
        assertFalse(c.passed)
        assertEquals(CheckLevel.INFO, c.level)
        assertTrue(SlicingRule.evaluate(good.copy(presets = good.presets.copy(cloudSignedIn = false))).ready)
        assertEquals(null, SlicingRule.evaluate(good.copy(presets = good.presets.copy(cloudSignedIn = false))).firstProblem)
    }

    @Test
    fun `only the checks that failed carry fix steps`() {
        assertTrue(SlicingRule.evaluate(good).checks.all { it.fixSteps.isEmpty() })
    }
}
