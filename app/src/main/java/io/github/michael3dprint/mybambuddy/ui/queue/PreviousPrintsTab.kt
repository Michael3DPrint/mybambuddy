package io.github.michael3dprint.mybambuddy.ui.queue

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.michael3dprint.mybambuddy.domain.model.ArchiveRef
import io.github.michael3dprint.mybambuddy.domain.model.HistoryItem
import io.github.michael3dprint.mybambuddy.domain.model.HistoryStatus
import io.github.michael3dprint.mybambuddy.domain.policy.PreviousPrintFilter
import io.github.michael3dprint.mybambuddy.ui.common.Spoken
import io.github.michael3dprint.mybambuddy.ui.common.politeLive
import io.github.michael3dprint.mybambuddy.ui.print.LocalRunAgain
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/** Makes the factory of the Previous prints list for one printer (or all printers when null); provided once by the print flow host. */
val LocalPreviousPrintsFactory = compositionLocalOf<
    (
        Int?,
    ) -> ViewModelProvider.Factory,
    > { { throw IllegalStateException("No factory provided") } }

/**
 * The Previous prints tab (design 44): completed, cancelled and failed prints from Bambuddy's archive with filter chips, paging and a Run
 * again button that opens the print review. Reading only: nothing here changes or deletes an archive.
 */
@Composable
internal fun PreviousPrintsTab(viewModel: PreviousPrintsViewModel, printerId: String?) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val runAgain = LocalRunAgain.current
    LazyColumn(
        contentPadding = PaddingValues(start = Dimens.Space.l, top = Dimens.Space.l, end = Dimens.Space.l, bottom = FAB_CLEARANCE),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space.m),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "filters") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
                PreviousPrintFilter.entries.forEach { f ->
                    FilterChip(selected = state.filter == f, onClick = { viewModel.setFilter(f) }, label = { Text(f.label) })
                }
            }
        }
        state.error?.let { msg ->
            item(key = "error") {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
                    Text(msg, Modifier.politeLive(), color = MaterialTheme.colorScheme.error)
                    Button(onClick = viewModel::refresh) { Text("Retry") }
                }
            }
        }
        if (state.loading) item(key = "loading") { Text("Loading previous prints…", Modifier.politeLive()) }
        if (!state.loading && state.visible.isEmpty() && state.error == null) {
            item(key = "empty") {
                Text(
                    if (state.endReached) "No previous prints match this filter." else "Nothing here yet. Load more to look further back.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(state.visible, key = { "p${it.id}" }) { print ->
            PreviousPrintCard(print, state.pictures[print.id.toIntOrNull()]) {
                val id = print.id.toIntOrNull() ?: return@PreviousPrintCard
                runAgain(ArchiveRef(id, print.name), (print.printerId ?: printerId?.toIntOrNull())?.toString())
            }
        }
        if (!state.loading && !state.endReached) {
            item(key = "more") {
                OutlinedButton(onClick = viewModel::loadMore, enabled = !state.loadingMore, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (state.loadingMore) "Loading…" else "Load more")
                }
            }
        }
    }
}

@Composable
private fun PreviousPrintCard(print: HistoryItem, picture: ByteArray?, onRunAgain: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(Dimens.Space.m), horizontalArrangement = Arrangement.spacedBy(Dimens.Space.m)) {
            QueueThumb(picture, Modifier.size(72.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.Space.xs)) {
                Text(print.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                Spoken(
                    "${statusWord(print.status)} · ${print.formattedDate} · ${print.formattedDuration}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(print.printerName, style = MaterialTheme.typography.bodySmall)
                Button(onClick = onRunAgain, modifier = Modifier.heightIn(min = 48.dp)) { Text("Run again") }
            }
        }
    }
}

private fun statusWord(status: HistoryStatus) = when (status) {
    HistoryStatus.SUCCESS -> "Completed"
    HistoryStatus.CANCELLED -> "Cancelled"
    HistoryStatus.FAILED -> "Failed"
    HistoryStatus.IN_PROGRESS -> "Printing"
    HistoryStatus.UNKNOWN -> "Unknown"
}
