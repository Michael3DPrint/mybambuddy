package io.github.michael3dprint.mybambuddy.ui.queue

import androidx.compose.runtime.Composable
import io.github.michael3dprint.mybambuddy.ui.common.AppTabRow

/** The two tabs of the queue page (design 44): what is queued now, and the record of previous prints. */
enum class QueueTab(val title: String) {
    QUEUE("Queue"),
    PREVIOUS("Previous prints"),
}

/** The queue's tab row; at large font sizes it scrolls instead of squeezing the labels, like the other tab rows (design 34). */
@Composable
internal fun QueueTabRow(selected: QueueTab, onSelect: (QueueTab) -> Unit) {
    AppTabRow(QueueTab.entries.map { it.title }, selected.ordinal) { onSelect(QueueTab.entries[it]) }
}
