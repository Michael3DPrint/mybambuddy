package io.github.michael3dprint.mybambuddy.domain.widget

/** One printer line on the widget. [progress] is 0 to 100, or -1 when no bar should show. */
data class WidgetRow(val id: String, val name: String, val stateText: String, val progress: Int, val timeLeft: String)

/** What the widget shows: up to [WidgetSnapshotMapper.MAX_ROWS] rows, when they were read, and whether they are old data. */
data class WidgetSnapshot(val rows: List<WidgetRow> = emptyList(), val takenAtMs: Long = 0L, val stale: Boolean = false)

/** Where the widget's last snapshot is kept. */
interface WidgetSnapshotStore {
    /** The saved snapshot, or null when none was saved yet. */
    fun load(): WidgetSnapshot?

    /** Saves [snapshot]. */
    fun save(snapshot: WidgetSnapshot)
}

/** Draws a snapshot onto every widget on the home screen. */
interface WidgetRenderer {
    /** Shows [snapshot] on all widget instances. */
    fun render(snapshot: WidgetSnapshot?)
}
