package io.github.michael3dprint.mybambuddy.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DimensTest {
    @Test
    fun `every spacing and size token is on the 4 dp grid`() {
        val values = listOf(
            Dimens.Space.xs, Dimens.Space.s, Dimens.Space.m, Dimens.Space.l, Dimens.Space.xl, Dimens.Space.xxl,
            Dimens.IconSize.s, Dimens.IconSize.m, Dimens.IconSize.l, Dimens.IconSize.xl,
            Dimens.touchTarget, Dimens.cameraCard, Dimens.progressBar,
        )
        values.forEach { assertEquals("$it is off the 4 dp grid", 0f, it.value % 4f, 0f) }
    }

    @Test
    fun `tap targets meet the accessibility floor`() {
        assertTrue(Dimens.touchTarget.value >= 48f)
    }

    @Test
    fun `only the two live-number text styles use tabular figures`() {
        assertEquals("tnum", Typography.titleSmall.fontFeatureSettings)
        assertEquals("tnum", Typography.labelSmall.fontFeatureSettings)
        assertEquals(null, Typography.bodyMedium.fontFeatureSettings)
    }
}
