package io.github.michael3dprint.mybambuddy.domain.widget

import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Turns printers into widget rows and writes the "updated" or "saved" stamp. Pure, so it is unit tested. */
object WidgetSnapshotMapper {
    /** The most printers the widget lists. */
    const val MAX_ROWS = 3

    private const val MINUTE = 60L
    private const val HOUR = 3600L

    /** Builds a fresh snapshot from [printers], taken at [nowMs]. */
    fun from(printers: List<Printer>, nowMs: Long): WidgetSnapshot =
        WidgetSnapshot(printers.take(MAX_ROWS).map(::row), nowMs, stale = false)

    private fun row(printer: Printer): WidgetRow {
        val showsProgress = printer.state == PrinterState.PRINTING || printer.state == PrinterState.PAUSED
        return WidgetRow(
            id = printer.id,
            name = printer.name,
            stateText = printer.state.name,
            progress = if (showsProgress) printer.progress.coerceIn(0, 100) else -1,
            timeLeft = if (printer.state == PrinterState.PRINTING) timeLeft(printer.remainingTimeSeconds) else "",
        )
    }

    /** "1h 12m", "45m", or "" when nothing is left to say. */
    fun timeLeft(seconds: Long): String = when {
        seconds <= 0 -> ""
        seconds >= HOUR -> "${seconds / HOUR}h ${seconds % HOUR / MINUTE}m"
        else -> "${maxOf(1L, seconds / MINUTE)}m"
    }

    /** "updated 14:02" for live data, "saved 13:40" for old data, "" when nothing was ever read. */
    fun stamp(snapshot: WidgetSnapshot, zone: ZoneId = ZoneId.systemDefault()): String {
        if (snapshot.takenAtMs <= 0L) return ""
        val time = DateTimeFormatter.ofPattern("HH:mm").withZone(zone).format(Instant.ofEpochMilli(snapshot.takenAtMs))
        return (if (snapshot.stale) "saved " else "updated ") + time
    }
}
