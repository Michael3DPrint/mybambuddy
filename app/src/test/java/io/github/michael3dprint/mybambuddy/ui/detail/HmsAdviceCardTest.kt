package io.github.michael3dprint.mybambuddy.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class HmsAdviceCardTest {
    @Test
    fun `a collapsed error is one line with severity, area and title`() {
        assertEquals("Error · AMS: Filament tangled", collapsedLine("Error", "AMS", "Filament tangled"))
    }
}
