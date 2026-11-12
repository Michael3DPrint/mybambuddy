package io.github.michael3dprint.mybambuddy.domain.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmsTrayAddressTest {
    @Test
    fun `a regular AMS is unit times four plus the slot`() {
        assertEquals(0, AmsTrayAddress.forFeed(0, false, 0))
        assertEquals(2, AmsTrayAddress.forFeed(0, false, 2))
        assertEquals(7, AmsTrayAddress.forFeed(1, false, 3))
        assertEquals(15, AmsTrayAddress.forFeed(3, false, 3))
    }

    @Test
    fun `an AMS HT is addressed by its unit id`() {
        assertEquals(128, AmsTrayAddress.forFeed(128, true, 0))
        assertEquals(135, AmsTrayAddress.forFeed(135, true, 0))
        // A unit in the HT range is an HT even if the flag is missing.
        assertEquals(129, AmsTrayAddress.forFeed(129, false, 0))
    }

    @Test
    fun `anything it cannot address gives null so no button is offered`() {
        assertNull(AmsTrayAddress.forFeed(4, false, 0))
        assertNull(AmsTrayAddress.forFeed(-1, false, 0))
        assertNull(AmsTrayAddress.forFeed(0, false, 4))
        assertNull(AmsTrayAddress.forFeed(0, false, -1))
        assertNull(AmsTrayAddress.forFeed(2, true, 0))
        assertNull(AmsTrayAddress.forFeed(136, true, 0))
    }
}
