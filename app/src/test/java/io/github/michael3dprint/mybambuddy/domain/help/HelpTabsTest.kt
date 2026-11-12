package io.github.michael3dprint.mybambuddy.domain.help

import org.junit.Assert.assertEquals
import org.junit.Test

class HelpTabsTest {
    @Test
    fun `a troubleshooting page opens on Fix a problem and every other page on Guide`() {
        HelpTopicId.values().forEach { id ->
            val expected = if (id.name.startsWith("T_")) HelpTab.FIX else HelpTab.GUIDE
            assertEquals(id.name, expected, HelpTabs.forTopic(id))
        }
    }

    @Test
    fun `the tabs are the three the design names, with Fix a problem always in the row`() {
        assertEquals(listOf("Guide", "Fix a problem", "This phone"), HelpTab.entries.map { it.title })
    }
}
