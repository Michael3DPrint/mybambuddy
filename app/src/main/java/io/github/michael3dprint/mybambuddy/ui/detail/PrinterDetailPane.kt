package io.github.michael3dprint.mybambuddy.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import io.github.michael3dprint.mybambuddy.domain.connectivity.ConnectivityStatus
import io.github.michael3dprint.mybambuddy.domain.model.PrinterControlCommand
import io.github.michael3dprint.mybambuddy.domain.model.PrinterTool
import io.github.michael3dprint.mybambuddy.domain.policy.PrinterFilter
import io.github.michael3dprint.mybambuddy.domain.policy.ReadinessEvaluator
import io.github.michael3dprint.mybambuddy.domain.policy.ToolAvailability
import io.github.michael3dprint.mybambuddy.domain.repo.PrintControlRepository
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterRepository
import io.github.michael3dprint.mybambuddy.ui.filament.AmsListState
import io.github.michael3dprint.mybambuddy.ui.filament.AmsViewModel
import io.github.michael3dprint.mybambuddy.ui.filament.DryingViewModel
import io.github.michael3dprint.mybambuddy.ui.maintenance.MaintenanceScreen
import io.github.michael3dprint.mybambuddy.ui.maintenance.MaintenanceViewModel
import io.github.michael3dprint.mybambuddy.ui.queue.QueueScreen
import io.github.michael3dprint.mybambuddy.ui.queue.QueueViewModel
import io.github.michael3dprint.mybambuddy.ui.readiness.DiagnosticsGroup
import io.github.michael3dprint.mybambuddy.ui.readiness.DiagnosticsViewModel
import io.github.michael3dprint.mybambuddy.ui.readiness.SlicingCheckViewModel
import okhttp3.OkHttpClient

/**
 * One printer's page with its own view model and three tabs: Overview, this printer's Queue, and this printer's Maintenance (design 30).
 * The phone shows it as a page and the tablet layout as the right-hand pane.
 */
@Composable
fun PrinterDetailPane(
    printerId: String,
    printerRepository: PrinterRepository,
    printControlRepository: PrintControlRepository,
    connectivity: ConnectivityStatus,
    pollIntervalMs: suspend () -> Long,
    imageLoader: ImageLoader?,
    cameraClient: OkHttpClient?,
    tabSources: PrinterTabSources,
    openTools: Boolean,
    onToolsOpened: () -> Unit,
    onBack: () -> Unit,
) {
    val detailViewModel: PrinterDetailViewModel = viewModel(
        key = printerId,
        factory = PrinterDetailViewModel.Factory(
            printerId = printerId,
            printerRepository = printerRepository,
            printControlRepository = printControlRepository,
            connectivity = connectivity,
            pollIntervalMs = pollIntervalMs,
        ),
    )
    val amsViewModel: AmsViewModel = viewModel(
        key = "ams-$printerId",
        factory = AmsViewModel.Factory(tabSources.ams, tabSources.spools),
    )
    var tab by rememberSaveable(printerId) { mutableStateOf(PrinterTab.OVERVIEW) }
    // A tap on a printer card's Remote control line asks for the Tools tab once; the request is then cleared (design 37).
    LaunchedEffect(openTools) {
        if (openTools) {
            tab = PrinterTab.TOOLS
            onToolsOpened()
        }
    }
    // A printer id that is not a number matches no job and no upkeep, so the tabs stay empty rather than showing every printer.
    val numericId = printerId.toIntOrNull() ?: NO_PRINTER
    when (tab) {
        PrinterTab.OVERVIEW -> PrinterDetailScreen(
            viewModel = detailViewModel,
            imageLoader = imageLoader,
            cameraClient = cameraClient,
            onBackClick = onBack,
            tabs = { PrinterTabRow(tab) { tab = it } },
            filament = { snack ->
                PrinterFilamentSection(
                    numericId,
                    amsViewModel,
                    tabSources.slotViewModel,
                    tabSources.slotActions,
                    snack,
                    tabSources.dryingViewModel,
                )
            },
        )
        PrinterTab.QUEUE -> {
            val vm: QueueViewModel = viewModel(
                key = "queue-$printerId",
                factory = QueueViewModel.Factory(tabSources.queue, tabSources.connectivity, tabSources.readiness, numericId),
            )
            TabPage(detailViewModel, tab, { tab = it }, onBack, vm::refresh) { QueueScreen(vm, printerId = printerId) }
        }
        PrinterTab.TOOLS -> {
            val vm: MaintenanceViewModel = viewModel(
                key = "maintenance-$printerId",
                factory = MaintenanceViewModel.Factory(tabSources.maintenance, tabSources.connectivity, numericId),
            )
            val diagnostics: DiagnosticsViewModel = viewModel(
                key = "diagnostics-$printerId",
                factory = DiagnosticsViewModel.Factory(tabSources.diagnostics),
            )
            val slicing: SlicingCheckViewModel = viewModel(
                key = "slicing-$printerId",
                factory = SlicingCheckViewModel.Factory(tabSources.slicing),
            )
            TabPage(detailViewModel, tab, { tab = it }, onBack, onRefresh = {
                detailViewModel.loadPrinterDetails()
                vm.refresh()
                slicing.check()
            }) {
                ToolsPage(detailViewModel, vm, diagnostics, slicing, printerId, amsViewModel, tabSources.dryingViewModel)
            }
        }
    }
}

@Composable
private fun TabPage(
    detail: PrinterDetailViewModel,
    tab: PrinterTab,
    onSelect: (PrinterTab) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state by detail.uiState.collectAsStateWithLifecycle()
    val name = (state as? PrinterDetailUiState.Success)?.printer?.name.orEmpty()
    Column(Modifier.fillMaxSize()) {
        PrinterTabHeader(name, tab, onSelect, onBack, onRefresh)
        content()
    }
}

/**
 * The Tools tab (designs 37 and 45): expandable status sections, each with a check or a mark: Printer Status, AMS (one per unit), then
 * Diagnostics, which holds the connection test (only runs on a tap), Remote control and Slicing, then Maintenance.
 */
@Composable
private fun ToolsPage(
    detail: PrinterDetailViewModel,
    maintenance: MaintenanceViewModel,
    diagnostics: DiagnosticsViewModel,
    slicing: SlicingCheckViewModel,
    printerId: String,
    amsViewModel: AmsViewModel,
    dryingViewModel: DryingViewModel,
) {
    val amsState by amsViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(printerId) { amsViewModel.refresh() }
    val state by detail.uiState.collectAsStateWithLifecycle()
    val diagnosticStates by diagnostics.states.collectAsStateWithLifecycle()
    val slicingState by slicing.state.collectAsStateWithLifecycle()
    LaunchedEffect(printerId) { slicing.check() }
    val printer = (state as? PrinterDetailUiState.Success)?.printer
    MaintenanceScreen(maintenance, moreMessages = detail.userMessages) {
        printer?.let { p ->
            item {
                val clear = ToolAvailability.statusOf(PrinterTool.CLEAR_ERRORS, p.capabilities, p.name)
                PrinterStatusSection(
                    errors = p.hmsErrors,
                    state = p.state,
                    clearEnabled = clear.isEnabled,
                    reason = clear.reason,
                    onClear = { detail.execute(PrinterControlCommand.ClearHms) },
                )
            }
        }
        item {
            val units = (amsState as? AmsListState.Success)?.units?.let {
                PrinterFilter.ams(it, printerId.toIntOrNull() ?: NO_PRINTER)
            }.orEmpty()
            AmsToolsSection(units, dryingViewModel)
        }
        item {
            DiagnosticsGroup(
                diagnostics = diagnosticStates[printerId],
                onRun = { diagnostics.run(printerId) },
                readiness = printer?.let { ReadinessEvaluator.evaluate(it.id, it.name, it.capabilities) },
                slicing = slicingState,
                printerName = printer?.name.orEmpty(),
                onCheckSlicing = slicing::check,
            )
        }
    }
}

private const val NO_PRINTER = -1
