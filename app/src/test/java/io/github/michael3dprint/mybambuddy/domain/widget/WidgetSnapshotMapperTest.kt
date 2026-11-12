package io.github.michael3dprint.mybambuddy.domain.widget

import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSnapshotMapperTest {
    private fun printer(id: Int, state: PrinterState, progress: Int = 0, seconds: Long = 0) =
        Printer(id = id.toString(), name = "P$id", model = "X", state = state, progress = progress, remainingTimeSeconds = seconds)

    @Test
    fun `at most three rows are kept, in order`() {
        val snapshot = WidgetSnapshotMapper.from((1..5).map { printer(it, PrinterState.IDLE) }, 1000L)
        assertEquals(listOf("1", "2", "3"), snapshot.rows.map { it.id })
        assertEquals(false, snapshot.stale)
    }

    @Test
    fun `a printing row has a bar and time left, an idle row has neither`() {
        val rows = WidgetSnapshotMapper.from(
            listOf(printer(1, PrinterState.PRINTING, 62, 4320), printer(2, PrinterState.IDLE, 40, 900)),
            0L,
        ).rows
        assertEquals(62, rows[0].progress)
        assertEquals("1h 12m", rows[0].timeLeft)
        assertEquals(-1, rows[1].progress)
        assertEquals("", rows[1].timeLeft)
    }

    @Test
    fun `progress is clamped to 0 to 100`() {
        val rows = WidgetSnapshotMapper.from(
            listOf(printer(1, PrinterState.PRINTING, 140, 60), printer(2, PrinterState.PAUSED, -5)),
            0L,
        ).rows
        assertEquals(100, rows[0].progress)
        assertEquals(0, rows[1].progress)
    }

    @Test
    fun `time left text`() {
        assertEquals("", WidgetSnapshotMapper.timeLeft(0))
        assertEquals("1m", WidgetSnapshotMapper.timeLeft(20))
        assertEquals("45m", WidgetSnapshotMapper.timeLeft(2700))
        assertEquals("2h 0m", WidgetSnapshotMapper.timeLeft(7200))
    }

    @Test
    fun `the stamp says updated for live data, saved for old data and nothing when never read`() {
        val at = 14L * 3600 * 1000 + 2 * 60 * 1000
        assertEquals("updated 14:02", WidgetSnapshotMapper.stamp(WidgetSnapshot(takenAtMs = at), ZoneOffset.UTC))
        assertEquals("saved 14:02", WidgetSnapshotMapper.stamp(WidgetSnapshot(takenAtMs = at, stale = true), ZoneOffset.UTC))
        assertEquals("", WidgetSnapshotMapper.stamp(WidgetSnapshot(), ZoneOffset.UTC))
    }
}
