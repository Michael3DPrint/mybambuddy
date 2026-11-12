package io.github.michael3dprint.mybambuddy.domain.print

import org.junit.Assert.assertEquals
import org.junit.Test

class PrintEstimateTest {
    @Test
    fun `time reads as hours and minutes`() {
        assertEquals("2 h 14 m", PrintEstimate.time(8040))
        assertEquals("45 m", PrintEstimate.time(2700))
        assertEquals("1 h 0 m", PrintEstimate.time(3600))
    }

    @Test
    fun `a missing estimate says so instead of guessing`() {
        assertEquals("not in the file", PrintEstimate.time(null))
        assertEquals("not in the file", PrintEstimate.time(0))
        assertEquals("not in the file · weight not in the file (slicer estimate)", PrintEstimate.line(null, null))
        assertEquals("2 h 14 m · 31 g (slicer estimate)", PrintEstimate.line(8040, 31.2))
    }
}
