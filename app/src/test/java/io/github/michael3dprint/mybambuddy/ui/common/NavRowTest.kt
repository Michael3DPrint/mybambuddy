package io.github.michael3dprint.mybambuddy.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class NavRowTest {
    @Test
    fun `a navigation row announces what it opens`() {
        assertEquals("Open Setup help", navRowAction("Setup help"))
    }
}
