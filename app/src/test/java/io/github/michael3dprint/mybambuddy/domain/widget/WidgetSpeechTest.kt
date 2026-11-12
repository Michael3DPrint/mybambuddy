package io.github.michael3dprint.mybambuddy.domain.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetSpeechTest {
    @Test
    fun `a printing row is spoken with progress and time left in words`() {
        assertEquals("X2D, printing, 62 percent, 1 hour 12 minutes left", WidgetSpeech.row(WidgetRow("1", "X2D", "PRINTING", 62, "1h 12m")))
        assertEquals("P1S, printing, 90 percent, 5 minutes left", WidgetSpeech.row(WidgetRow("2", "P1S", "PRINTING", 90, "5m")))
    }

    @Test
    fun `an idle row has no progress or time`() {
        assertEquals("X2D, idle", WidgetSpeech.row(WidgetRow("1", "X2D", "IDLE", -1, "")))
    }

    @Test
    fun `singular units and odd text are handled`() {
        assertEquals("1 hour 1 minute", WidgetSpeech.spokenTimeLeft("1h 1m"))
        assertEquals("1 minute", WidgetSpeech.spokenTimeLeft("1m"))
        assertEquals("soon", WidgetSpeech.spokenTimeLeft("soon"))
        assertNull(WidgetSpeech.spokenTimeLeft(""))
    }
}
