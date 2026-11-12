package io.github.michael3dprint.mybambuddy.domain.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class PollIntervalTest {
    @Test
    fun `the settings choices map to milliseconds`() {
        assertEquals(3_000L, PollInterval.toMillis(3))
        assertEquals(5_000L, PollInterval.toMillis(5))
        assertEquals(10_000L, PollInterval.toMillis(10))
    }

    @Test
    fun `a bad stored value is kept inside sane bounds`() {
        assertEquals(2_000L, PollInterval.toMillis(0))
        assertEquals(2_000L, PollInterval.toMillis(-30))
        assertEquals(60_000L, PollInterval.toMillis(100_000))
    }
}
