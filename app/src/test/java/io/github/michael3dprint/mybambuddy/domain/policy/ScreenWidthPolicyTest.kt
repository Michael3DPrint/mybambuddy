package io.github.michael3dprint.mybambuddy.domain.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenWidthPolicyTest {
    @Test
    fun `phones are not wide`() {
        assertFalse(ScreenWidthPolicy.isWide(360))
        assertFalse(ScreenWidthPolicy.isWide(599))
    }

    @Test
    fun `600 dp and above is wide, including an unfolded foldable`() {
        assertTrue(ScreenWidthPolicy.isWide(600))
        assertTrue(ScreenWidthPolicy.isWide(601))
        assertTrue(ScreenWidthPolicy.isWide(673))
        assertTrue(ScreenWidthPolicy.isWide(1280))
    }

    @Test
    fun `a zero or negative width is never wide`() {
        assertFalse(ScreenWidthPolicy.isWide(0))
        assertFalse(ScreenWidthPolicy.isWide(-1))
    }
}
