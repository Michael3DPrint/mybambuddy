package io.github.michael3dprint.mybambuddy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeBitmapTest {
    @Test
    fun `normal camera frames are decoded at a sensible size`() {
        assertEquals(1, SafeBitmap.sampleSizeFor(1000, 700))
        val sample = SafeBitmap.sampleSizeFor(1920, 1080)!!
        assertTrue((1920L / sample) * (1080L / sample) <= SafeBitmap.TARGET_MAX_PIXELS)
    }

    @Test
    fun `absurd or invalid sizes are refused`() {
        assertNull(SafeBitmap.sampleSizeFor(0, 100))
        assertNull(SafeBitmap.sampleSizeFor(-5, 100))
        assertNull(SafeBitmap.sampleSizeFor(SafeBitmap.MAX_DIMENSION + 1, 10))
        assertNull(SafeBitmap.sampleSizeFor(Int.MAX_VALUE, Int.MAX_VALUE))
    }

    @Test
    fun `big but allowed images are scaled down without overflow`() {
        val sample = SafeBitmap.sampleSizeFor(8192, 8192)!!
        assertTrue(sample >= 8)
        assertTrue((8192L / sample) * (8192L / sample) <= SafeBitmap.TARGET_MAX_PIXELS)
    }
}
