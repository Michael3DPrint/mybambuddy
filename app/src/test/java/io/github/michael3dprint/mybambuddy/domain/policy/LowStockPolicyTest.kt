package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.Spool
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LowStockPolicyTest {
    @Test
    fun `boundaries around the default threshold`() {
        assertTrue(LowStockPolicy.isLow(0))
        assertTrue(LowStockPolicy.isLow(20))
        assertFalse(LowStockPolicy.isLow(21))
        assertFalse(LowStockPolicy.isLow(100))
    }

    @Test
    fun `unknown or invalid amounts are never low`() {
        assertFalse(LowStockPolicy.isLow(null))
        assertFalse(LowStockPolicy.isLow(-1))
        assertFalse(LowStockPolicy.isRecovered(null))
    }

    @Test
    fun `a spool's own threshold wins and is clamped`() {
        assertTrue(LowStockPolicy.isLow(35, 40))
        assertFalse(LowStockPolicy.isLow(35, 30))
        assertTrue(LowStockPolicy.isLow(1, 0)) // 0 is treated as 1, never "always false"
        assertFalse(LowStockPolicy.isLow(150, 9999))
    }

    @Test
    fun `re-arm needs a margin above the threshold so it cannot flap`() {
        assertFalse(LowStockPolicy.isRecovered(22))
        assertFalse(LowStockPolicy.isRecovered(25))
        assertTrue(LowStockPolicy.isRecovered(26))
    }

    @Test
    fun `spool exposes the policy`() {
        val spool = Spool(1, "b", "PLA", "", "", "", 1000, 850, null, null)
        assertTrue(spool.isLow)
        assertFalse(spool.copy(usedGrams = 100).isLow)
        assertFalse(spool.copy(lowStockPct = 10).isLow)
    }
}
