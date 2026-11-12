package io.github.michael3dprint.mybambuddy.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.di.AppContainer
import io.github.michael3dprint.mybambuddy.domain.connectivity.ConnectivityStatus
import io.github.michael3dprint.mybambuddy.domain.repo.AmsRepository
import io.github.michael3dprint.mybambuddy.domain.repo.DiagnosticsRepository
import io.github.michael3dprint.mybambuddy.domain.repo.MaintenanceRepository
import io.github.michael3dprint.mybambuddy.domain.repo.QueueRepository
import io.github.michael3dprint.mybambuddy.domain.repo.ReadinessCheck
import io.github.michael3dprint.mybambuddy.domain.repo.SlicingRepository
import io.github.michael3dprint.mybambuddy.domain.repo.SpoolRepository
import io.github.michael3dprint.mybambuddy.ui.common.AppTabRow
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.common.nameMaxLines
import io.github.michael3dprint.mybambuddy.ui.filament.DryingViewModel
import io.github.michael3dprint.mybambuddy.ui.filament.SlotActions
import io.github.michael3dprint.mybambuddy.ui.filament.SlotAssignmentViewModel

/** The sections of one printer's page (designs 30 and 32): what it is doing now, its own queue, and the tools page (what works, diagnostics, upkeep). */
enum class PrinterTab {
    OVERVIEW,
    QUEUE,
    TOOLS,
}

/** What the printer page needs to build its Queue, Maintenance and filament parts, kept together so the activity passes one value. */
class PrinterTabSources(
    val queue: QueueRepository,
    val maintenance: MaintenanceRepository,
    val diagnostics: DiagnosticsRepository,
    val readiness: ReadinessCheck,
    val connectivity: ConnectivityStatus,
    val ams: AmsRepository,
    val spools: SpoolRepository,
    val slotViewModel: SlotAssignmentViewModel,
    val slotActions: SlotActions,
    val slicing: SlicingRepository,
    val dryingViewModel: DryingViewModel,
)

/** The text tabs under a printer's top bar. Words, not icons, so it is clear what each one holds. */
@Composable
fun PrinterTabRow(selected: PrinterTab, onSelect: (PrinterTab) -> Unit) {
    AppTabRow(PrinterTab.entries.map { stringResource(it.label()) }, selected.ordinal) { onSelect(PrinterTab.entries[it]) }
}

/** The printer's name with a Back button and a Refresh button, then the tabs: the header of the Queue and Maintenance tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterTabHeader(title: String, selected: PrinterTab, onSelect: (PrinterTab) -> Unit, onBack: () -> Unit, onRefresh: () -> Unit) {
    Column {
        TopAppBar(
            title = {
                Heading(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = nameMaxLines(),
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.alerts_back))
                }
            },
            actions = {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.filament_refresh))
                }
            },
        )
        PrinterTabRow(selected, onSelect)
    }
}

private fun PrinterTab.label(): Int = when (this) {
    PrinterTab.OVERVIEW -> R.string.printer_tab_overview
    PrinterTab.QUEUE -> R.string.printer_tab_queue
    PrinterTab.TOOLS -> R.string.printer_tab_tools
}

/** Gathers the printer page's sources from the app container, so the activity passes one call. */
fun AppContainer.printerTabSources(slotViewModel: SlotAssignmentViewModel, slotActions: SlotActions, dryingViewModel: DryingViewModel) =
    PrinterTabSources(
        queue, maintenance, diagnostics, readiness, connectivity, ams, spools, slotViewModel, slotActions, slicing, dryingViewModel,
    )
