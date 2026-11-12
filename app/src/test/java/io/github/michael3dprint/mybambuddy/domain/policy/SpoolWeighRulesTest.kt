package io.github.michael3dprint.mybambuddy.domain.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoolWeighRulesTest {
    @Test
    fun `a normal reading is valid with no warning`() {
        assertEquals(WeighInput.Valid(812.0), SpoolWeighRules.parse("812", 1000))
        assertEquals(WeighInput.Valid(812.5), SpoolWeighRules.parse(" 812,5 ", 1000))
    }

    @Test
    fun `limits are 0 and 100000 inclusive`() {
        assertEquals(WeighInput.Valid(0.0), SpoolWeighRules.parse("0", 1000))
        assertTrue(SpoolWeighRules.parse("-1", 1000) is WeighInput.Invalid)
        assertTrue(SpoolWeighRules.parse("100000.5", 1000) is WeighInput.Invalid)
    }

    @Test
    fun `text and non finite values are refused`() {
        listOf("", "abc", "12 g", "NaN", "Infinity").forEach { assertTrue(it, SpoolWeighRules.parse(it, 1000) is WeighInput.Invalid) }
    }

    @Test
    fun `far above the label weight warns but still allows saving`() {
        val high = SpoolWeighRules.parse("1600", 1000) as WeighInput.Valid
        assertEquals(1600.0, high.grams, 0.0)
        assertTrue(high.warning!!.contains("1000"))
        assertNull((SpoolWeighRules.parse("1500", 1000) as WeighInput.Valid).warning)
    }

    @Test
    fun `an unknown label weight never warns`() {
        assertNull((SpoolWeighRules.parse("5000", 0) as WeighInput.Valid).warning)
    }
}
