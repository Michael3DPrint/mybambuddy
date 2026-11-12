package io.github.michael3dprint.mybambuddy.domain.slicing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScaleRuleTest {
    @Test
    fun `only whole percents from ten to four hundred are accepted`() {
        assertEquals(150, ScaleRule.parse("150"))
        assertEquals(150, ScaleRule.parse(" 150 % "))
        assertEquals(10, ScaleRule.parse("10"))
        assertEquals(400, ScaleRule.parse("400"))
        assertNull(ScaleRule.parse("9"))
        assertNull(ScaleRule.parse("401"))
        assertNull(ScaleRule.parse("12.5"))
        assertNull(ScaleRule.parse(""))
        assertNull(ScaleRule.parse("abc"))
    }

    @Test
    fun `the factor and the copy name follow the percent`() {
        assertEquals(1.5, ScaleRule.factor(150), 1e-9)
        assertEquals("Benchy-scaled-150.3mf", ScaleRule.copyName("Benchy.gcode.3mf", 150))
        assertEquals("Model-scaled-50.3mf", ScaleRule.copyName("Model.3mf", 50))
        assertEquals("model-scaled-200.3mf", ScaleRule.copyName(".3mf", 200))
    }
}
