package io.github.michael3dprint.mybambuddy.ui.maintenance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.domain.model.DueState
import io.github.michael3dprint.mybambuddy.domain.model.MaintenanceItem
import io.github.michael3dprint.mybambuddy.domain.policy.SectionStatuses
import io.github.michael3dprint.mybambuddy.ui.common.ConfirmDialog
import io.github.michael3dprint.mybambuddy.ui.common.EmptyState
import io.github.michael3dprint.mybambuddy.ui.common.ErrorNotice
import io.github.michael3dprint.mybambuddy.ui.common.Spoken
import io.github.michael3dprint.mybambuddy.ui.common.StatusSection
import io.github.michael3dprint.mybambuddy.ui.common.showMessage
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Upkeep per printer, with a button to mark an item done after confirming. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceScreen(viewModel: MaintenanceViewModel, moreMessages: Flow<String> = emptyFlow(), header: LazyListScope.() -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirm by remember { mutableStateOf<MaintenanceItem?>(null) }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showMessage(it) } }
    // Messages from the page around this screen (for example the result of clearing printer errors) use the same snackbar.
    LaunchedEffect(moreMessages) { moreMessages.collect { snackbar.showMessage(it) } }

    Scaffold(
        // Only shown inside a printer page, which has its own bar (designs 30 and 32).
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(Dimens.Space.l),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space.l),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            // Items the page wants above the upkeep, shown at once whatever the upkeep is doing.
            header()
            when (val s = state) {
                MaintenanceState.Loading -> item {
                    Text(stringResource(R.string.maintenance_loading), style = MaterialTheme.typography.bodyMedium)
                }
                is MaintenanceState.Error -> item { ErrorNotice(s.error, viewModel::refresh) }
                is MaintenanceState.Success -> {
                    if (s.printers.isEmpty()) {
                        item {
                            EmptyState(
                                Icons.Default.Build,
                                "No maintenance found",
                                "Maintenance is tracked per printer. Set it up in Bambuddy.",
                                fillScreen = false,
                            )
                        }
                    }
                    s.printers.forEach { printer ->
                        item(key = "p${printer.printerId}") {
                            StatusSection(
                                title = stringResource(R.string.printer_tab_maintenance),
                                status = SectionStatuses.maintenance(printer.dueCount, printer.soonCount),
                            ) {
                                Spoken(
                                    "${"%.1f".format(
                                        printer.printHours,
                                    )} print hours · ${printer.dueCount} due · ${printer.soonCount} soon",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                printer.items.forEach { item -> ItemRow(item) { confirm = item } }
                                if (printer.items.isEmpty()) {
                                    EmptyState(
                                        Icons.Default.Build,
                                        "Nothing tracked yet",
                                        "Set up maintenance for this printer in Bambuddy.",
                                        fillScreen = false,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirm?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.maintenance_mark_as_done),
            confirmLabel = stringResource(R.string.maintenance_mark_done),
            onConfirm = {
                confirm = null
                viewModel.markDone(item.id, item.name)
            },
            onCancel = { confirm = null },
        ) {
            Text("${item.name}: this resets its counter in Bambuddy.")
        }
    }
}

@Composable
private fun ItemRow(item: MaintenanceItem, onDone: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space.xs)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(item.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Spoken(
                when (item.dueState) {
                    DueState.DUE -> "DUE · ${item.remainingLabel}"
                    DueState.SOON -> "Soon · ${item.remainingLabel}"
                    DueState.OK -> item.remainingLabel
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (item.dueState == DueState.OK) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
        LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
        if (item.dueState != DueState.OK) TextButton(onClick = onDone) { Text(stringResource(R.string.maintenance_mark_done)) }
    }
}
