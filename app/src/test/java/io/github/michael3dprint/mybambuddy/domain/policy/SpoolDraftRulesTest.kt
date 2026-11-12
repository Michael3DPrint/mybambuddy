package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.SpoolDraft
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SpoolDraftRulesTest {
    private val ok = SpoolDraft(material = "PLA", colorHex = "FF8800", labelWeightGrams = 1000)

    @Test
    fun `a normal draft passes, with or without a colour`() {
        assertNull(SpoolDraftRules.problem(ok))
        assertNull(SpoolDraftRules.problem(ok.copy(colorHex = "")))
        assertNull(SpoolDraftRules.problem(ok.copy(colorHex = "#FF880080")))
    }

    @Test
    fun `values the server would refuse are caught here`() {
        assertNotNull(SpoolDraftRules.problem(ok.copy(material = " ")))
        assertNotNull(SpoolDraftRules.problem(ok.copy(material = "x".repeat(65))))
        assertNotNull(SpoolDraftRules.problem(ok.copy(colorHex = "teal")))
        assertNotNull(SpoolDraftRules.problem(ok.copy(colorHex = "FF88")))
        assertNotNull(SpoolDraftRules.problem(ok.copy(colorHex = "FF88001")))
        assertNotNull(SpoolDraftRules.problem(ok.copy(labelWeightGrams = 0)))
        assertNotNull(SpoolDraftRules.problem(ok.copy(labelWeightGrams = 100_001)))
        assertNotNull(SpoolDraftRules.problem(ok.copy(coreWeightGrams = 10_001)))
    }

    @Test
    fun `boundaries are accepted`() {
        assertNull(SpoolDraftRules.problem(ok.copy(labelWeightGrams = 1)))
        assertNull(SpoolDraftRules.problem(ok.copy(labelWeightGrams = 100_000, coreWeightGrams = 10_000)))
    }
}
