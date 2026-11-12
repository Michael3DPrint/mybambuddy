package io.github.michael3dprint.mybambuddy.ui.filament

import io.github.michael3dprint.mybambuddy.domain.model.AmsTray
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfirmLoadTextTest {
    private val spool = Spool(7, "Bambu Lab", "PLA", "Basic", "Blue", "307FE2", 1000, 250, null, "Dry box")

    @Test
    fun `the spool line gives colour, material, what is left and where it is kept`() {
        assertEquals("Blue · PLA · 750 g left of 1000 g · Dry box", spoolDetails(spool))
    }

    @Test
    fun `blank parts of the spool line are left out`() {
        val bare = spool.copy(colorName = "", location = null)
        assertEquals("PLA · 750 g left of 1000 g", spoolDetails(bare))
    }

    @Test
    fun `the slot line says what is there now and the recorded spool`() {
        assertEquals("Now: PLA Basic, 75 % · recorded spool #3", slotNow(AmsTray(2, "PLA", "PLA Basic", "307FE2FF", 75, 3)))
        assertEquals("Now: PLA", slotNow(AmsTray(2, "PLA", "", "307FE2FF", null, null)))
    }

    @Test
    fun `an empty or missing slot is described as empty`() {
        assertEquals("Now: empty", slotNow(AmsTray(0, "", "", "", null, null)))
        assertEquals("Now: empty", slotNow(null))
    }
}
