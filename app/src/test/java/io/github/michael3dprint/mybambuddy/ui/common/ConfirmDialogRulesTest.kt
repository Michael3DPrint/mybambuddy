package io.github.michael3dprint.mybambuddy.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmDialogRulesTest {
    // Google's accessibility guidance: targets of at least 48 dp, with at least 8 dp between adjacent targets.
    private val googleMinTarget = 48f
    private val googleMinGap = 8f

    @Test
    fun `buttons are at least 48 dp tall and the gap is at least twice the 8 dp minimum`() {
        assertTrue(DialogSpacing.MinButtonHeight.value >= googleMinTarget)
        assertTrue(DialogSpacing.MinButtonWidth.value >= googleMinTarget)
        assertTrue(DialogSpacing.ActionGap.value >= googleMinGap * 2)
    }

    @Test
    fun `cancel is outlined beside a normal confirm and plain text beside a destructive one`() {
        assertTrue(cancelIsOutlined(destructive = false))
        assertFalse(cancelIsOutlined(destructive = true))
        assertEquals(16f, DialogSpacing.ActionGap.value, 0f)
    }
}
