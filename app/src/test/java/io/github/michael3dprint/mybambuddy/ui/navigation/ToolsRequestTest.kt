package io.github.michael3dprint.mybambuddy.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsRequestTest {
    @Test
    fun `a tap on the Remote control line is for that printer only`() {
        val pending = ToolsRequest.afterLineTap("1")
        assertTrue(ToolsRequest.appliesTo(pending, "1"))
        assertFalse(ToolsRequest.appliesTo(pending, "2"))
    }

    @Test
    fun `once handled it is gone, so opening the printer again lands on Overview`() {
        val pending = ToolsRequest.afterHandled()
        assertNull(pending)
        assertFalse(ToolsRequest.appliesTo(pending, "1"))
    }

    @Test
    fun `tapping the card cancels a request that was never handled`() {
        var pending = ToolsRequest.afterLineTap("1")
        pending = ToolsRequest.afterCardTap()
        assertFalse(ToolsRequest.appliesTo(pending, "1"))
        assertEquals(null, pending)
    }

    @Test
    fun `the full sequence from the owner's report ends on Overview for a plain card tap`() {
        var pending: String? = null
        pending = ToolsRequest.afterLineTap("1")
        assertTrue(ToolsRequest.appliesTo(pending, "1"))
        pending = ToolsRequest.afterHandled()
        pending = ToolsRequest.afterCardTap()
        assertFalse(ToolsRequest.appliesTo(pending, "1"))
    }
}
