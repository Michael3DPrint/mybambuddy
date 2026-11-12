package io.github.michael3dprint.mybambuddy.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.ImageLoader
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.domain.model.PrinterControlCommand
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import io.github.michael3dprint.mybambuddy.domain.model.PrinterTool
import io.github.michael3dprint.mybambuddy.domain.policy.ToolAvailability
import io.github.michael3dprint.mybambuddy.ui.common.ListSkeleton
import io.github.michael3dprint.mybambuddy.ui.common.showMessage
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/**
 * Details for one printer: status, camera, errors and controls. Connects the view model to [PrinterDetailScreenContent] and polls
 * while the screen is visible.
 */
@Composable
fun PrinterDetailScreen(
    viewModel: PrinterDetailViewModel,
    imageLoader: ImageLoader? = null,
    cameraClient: okhttp3.OkHttpClient? = null,
    onBackClick: () -> Unit = {},
    tabs: @Composable () -> Unit = {},
    filament: @Composable (SnackbarHostState) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.startPolling()
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                viewModel.stopPolling()
            }
        }
    }

    LaunchedEffect(viewModel.userMessages) {
        viewModel.userMessages.collect { message ->
            snackbarHostState.showMessage(message)
        }
    }

    PrinterDetailScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        imageLoader = imageLoader,
        cameraClient = cameraClient,
        onBackClick = onBackClick,
        tabs = tabs,
        filament = filament,
        onRetry = { viewModel.loadPrinterDetails() },
        onPauseClick = { viewModel.execute(PrinterControlCommand.Pause) },
        onResumeClick = { viewModel.execute(PrinterControlCommand.Resume) },
        onCancelClick = { viewModel.execute(PrinterControlCommand.Cancel) },
        onRefreshStatus = { viewModel.execute(PrinterControlCommand.RefreshStatus) },
        onClearPlate = { viewModel.execute(PrinterControlCommand.ClearPlate) },
        onClearErrors = { viewModel.execute(PrinterControlCommand.ClearHms) },
        onSetSpeed = { mode -> viewModel.execute(PrinterControlCommand.SetPrintSpeed(mode)) },
        onSetChamberLight = { on -> viewModel.execute(PrinterControlCommand.SetChamberLight(on)) },
        onRefreshSnapshot = { viewModel.refreshSnapshot() },
    )
}

/**
 * Draws the printer detail screen from its state. Every action is a callback, and a control is disabled when its tool is off for
 * this printer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterDetailScreenContent(
    uiState: PrinterDetailUiState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    imageLoader: ImageLoader? = null,
    cameraClient: okhttp3.OkHttpClient? = null,
    onBackClick: () -> Unit = {},
    onRetry: () -> Unit = {},
    onPauseClick: () -> Unit = {},
    onResumeClick: () -> Unit = {},
    onCancelClick: () -> Unit = {},
    onRefreshStatus: () -> Unit = {},
    onClearPlate: () -> Unit = {},
    onClearErrors: () -> Unit = {},
    onSetSpeed: (Int) -> Unit = {},
    onSetChamberLight: (Boolean) -> Unit = {},
    onRefreshSnapshot: () -> Unit = {},
    tabs: @Composable () -> Unit = {},
    filament: @Composable (SnackbarHostState) -> Unit = {},
) {
    var showOverflowMenu by remember { mutableStateOf(false) }
    // Live view is opt-in: it keeps the printer camera streaming while on.
    val liveViewState = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var liveView by liveViewState
    val showFullScreenImageState = remember { mutableStateOf(false) }
    var showFullScreenImage by showFullScreenImageState
    val showCancelDialogState = remember { mutableStateOf(false) }
    var showCancelDialog by showCancelDialogState
    val showPauseResumeDialogState = remember { mutableStateOf(false) }
    var showPauseResumeDialog by showPauseResumeDialogState
    val showSpeedPresetDialogState = remember { mutableStateOf(false) }
    var showSpeedPresetDialog by showSpeedPresetDialogState

    val printer = (uiState as? PrinterDetailUiState.Success)?.printer
    // One definition decides which tools are usable; buttons for a tool that is off are disabled (see docs/design/10).
    val toolStatus = remember(printer?.capabilities, printer?.name) {
        printer?.let { p -> ToolAvailability.evaluate(p.capabilities, p.name).associateBy { it.tool } }.orEmpty()
    }
    fun toolOn(tool: PrinterTool) = toolStatus[tool]?.isEnabled != false
    val snapshotTimestamp = (uiState as? PrinterDetailUiState.Success)?.snapshotTimestamp ?: 0L

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        PrinterTitle(name = printer?.name ?: "Printer Details", model = printer?.model)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.alerts_back),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.detail_more_actions),
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.detail_refresh_status)) },
                                onClick = {
                                    showOverflowMenu = false
                                    onRefreshStatus()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                },
                            )
                            // Only offered when the server would accept it (awaiting clear, finished, or failed).
                            if (printer == null ||
                                printer.awaitingPlateClear ||
                                printer.state == PrinterState.FINISHED ||
                                printer.state == PrinterState.ERROR ||
                                printer.state == PrinterState.FAILED
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.detail_mark_plate_cleared)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onClearPlate()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (printer?.chamberLight == true) "Turn chamber light off" else "Turn chamber light on") },
                                enabled = toolOn(PrinterTool.CHAMBER_LIGHT),
                                onClick = {
                                    showOverflowMenu = false
                                    onSetChamberLight(printer?.chamberLight != true)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.detail_speed_presets)) },
                                enabled = toolOn(PrinterTool.PRINT_SPEED),
                                onClick = {
                                    showOverflowMenu = false
                                    showSpeedPresetDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Speed, contentDescription = null)
                                },
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                tabs()
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when (uiState) {
                is PrinterDetailUiState.Loading -> {
                    ListSkeleton(rowHeight = 160.dp, rows = 3)
                }
                is PrinterDetailUiState.Error -> {
                    PrinterDetailError(uiState.errorState, onRetry)
                }
                is PrinterDetailUiState.Success -> {
                    val currentPrinter = uiState.printer
                    val isPaused = currentPrinter.state == PrinterState.PAUSED
                    val scrollState = rememberScrollState()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(Dimens.Space.l),
                        verticalArrangement = Arrangement.spacedBy(Dimens.Space.l),
                    ) {
                        // Printer errors, explained
                        if (currentPrinter.hasHmsWarning) {
                            HmsAdviceCard(
                                errors = currentPrinter.hmsErrors,
                                clearEnabled = toolOn(PrinterTool.CLEAR_ERRORS),
                                clearReason = toolStatus[PrinterTool.CLEAR_ERRORS]?.reason,
                                onClear = onClearErrors,
                            )
                        }

                        filament(snackbarHostState)

                        PrinterCameraCard(
                            currentPrinter = currentPrinter,
                            imageLoader = imageLoader,
                            cameraClient = cameraClient,
                            snapshotTimestamp = snapshotTimestamp,
                            onRefreshSnapshot = onRefreshSnapshot,
                            liveViewState = liveViewState,
                            showFullScreenImageState = showFullScreenImageState,
                        )
                        PrinterStatusBlock(
                            currentPrinter = currentPrinter,
                        )
                        PrinterControlRow(
                            currentPrinter = currentPrinter,
                            toolOn = { toolOn(it) },
                            showCancelDialogState = showCancelDialogState,
                            showPauseResumeDialogState = showPauseResumeDialogState,
                        )
                    }

                    PrinterDetailDialogs(
                        currentPrinter = currentPrinter,
                        imageLoader = imageLoader,
                        snapshotTimestamp = snapshotTimestamp,
                        onPauseClick = onPauseClick,
                        onResumeClick = onResumeClick,
                        onCancelClick = onCancelClick,
                        onSetSpeed = onSetSpeed,
                        showFullScreenImageState = showFullScreenImageState,
                        showCancelDialogState = showCancelDialogState,
                        showPauseResumeDialogState = showPauseResumeDialogState,
                        showSpeedPresetDialogState = showSpeedPresetDialogState,
                    )
                }
            }
        }
    }
}
