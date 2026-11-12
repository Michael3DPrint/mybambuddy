package io.github.michael3dprint.mybambuddy.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeFontTest {
    @Test
    fun `nothing changes at normal and slightly larger text, and the large layouts start at 1_3`() {
        assertFalse(LargeFont.isLarge(1.0f))
        assertFalse(LargeFont.isLarge(1.15f))
        assertFalse(LargeFont.isLarge(1.299f))
        assertTrue(LargeFont.isLarge(1.3f))
        assertTrue(LargeFont.isLarge(2.0f))
        assertEquals(1.3f, LargeFont.FROM_SCALE, 0f)
    }
}
