package io.github.michael3dprint.mybambuddy.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinksTest {
    @Test
    fun `a printer id opens that printer`() {
        assertEquals(DeepLink.Printer("3"), DeepLinks.parse(printerId = 3, spoolId = -1))
    }

    @Test
    fun `a spool id opens that spool, and wins when both are present`() {
        assertEquals(DeepLink.Spool(7), DeepLinks.parse(printerId = -1, spoolId = 7))
        assertEquals(DeepLink.Spool(7), DeepLinks.parse(printerId = 3, spoolId = 7))
    }

    @Test
    fun `missing, zero and negative ids open nothing`() {
        assertNull(DeepLinks.parse(printerId = -1, spoolId = -1))
        assertNull(DeepLinks.parse(printerId = 0, spoolId = 0))
        assertNull(DeepLinks.parse(printerId = -5, spoolId = -9))
    }

    @Test
    fun `the extra names are the ones the notifier writes`() {
        assertEquals("printer_id", DeepLinks.EXTRA_PRINTER_ID)
        assertEquals("spool_id", DeepLinks.EXTRA_SPOOL_ID)
    }
}
