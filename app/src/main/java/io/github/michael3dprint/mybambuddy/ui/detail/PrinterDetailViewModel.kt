package io.github.michael3dprint.mybambuddy.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.toUiErrorState
import io.github.michael3dprint.mybambuddy.domain.connectivity.AlwaysOnline
import io.github.michael3dprint.mybambuddy.domain.connectivity.ConnectivityStatus
import io.github.michael3dprint.mybambuddy.domain.model.PrinterControlCommand
import io.github.michael3dprint.mybambuddy.domain.policy.Backoff
import io.github.michael3dprint.mybambuddy.domain.policy.PollInterval
import io.github.michael3dprint.mybambuddy.domain.repo.PrintControlRepository
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Loads and polls one printer and sends its control commands (pause, resume, cancel, speed, light, clear). Commands are single
 * attempts and are refused while the app is offline.
 */
class PrinterDetailViewModel(
    val printerId: String,
    private val printerRepository: PrinterRepository,
    private val printControlRepository: PrintControlRepository,
    private val connectivity: ConnectivityStatus =
        AlwaysOnline,
    /** The saved polling choice (Settings), read on every cycle so a change applies at the next poll. */
    private val pollIntervalMs: suspend () -> Long = { PollInterval.DEFAULT_MS },
) : ViewModel() {

    private val _uiState = MutableStateFlow<PrinterDetailUiState>(PrinterDetailUiState.Loading)
    val uiState: StateFlow<PrinterDetailUiState> = _uiState.asStateFlow()

    private val _userMessages = MutableSharedFlow<String>()
    val userMessages: SharedFlow<String> = _userMessages.asSharedFlow()

    private var statusPollingJob: Job? = null
    private var pollErrorShown = false
    private val pollBackoff = Backoff()

    init {
        loadPrinterDetails()
    }

    fun loadPrinterDetails() {
        viewModelScope.launch {
            _uiState.value = PrinterDetailUiState.Loading
            fetchPrinterDetailsInternal()
        }
    }

    fun startPolling() {
        startStatusPolling()
        // Take one photo when the screen opens.
        refreshSnapshot()
    }

    fun stopPolling() {
        statusPollingJob?.cancel()
        statusPollingJob = null
    }

    private fun startStatusPolling() {
        statusPollingJob?.cancel()
        statusPollingJob = viewModelScope.launch {
            var failures = 0
            while (isActive) {
                failures = if (fetchPrinterDetailsInternal()) 0 else failures + 1
                // Back off (with jitter) while the server is unreachable instead of hammering it every 5 s.
                delay(pollBackoff.delayMillis(pollIntervalMs(), failures))
            }
        }
    }

    /** Fetch a new camera photo. Photos are never polled; they are only taken when asked for. */
    fun refreshSnapshot() {
        val current = _uiState.value
        if (current is PrinterDetailUiState.Success) {
            _uiState.value = current.copy(snapshotTimestamp = System.currentTimeMillis())
        }
    }

    /** @return true if the refresh succeeded. */
    private suspend fun fetchPrinterDetailsInternal(): Boolean {
        val result = printerRepository.fetchPrinter(printerId)
        when (result) {
            is ApiResult.Success -> {
                val currentSnapshotTs = (_uiState.value as? PrinterDetailUiState.Success)?.snapshotTimestamp
                    ?: System.currentTimeMillis()
                pollErrorShown = false
                _uiState.value = PrinterDetailUiState.Success(
                    printer = result.data,
                    snapshotTimestamp = currentSnapshotTs,
                )
                return true
            }
            is ApiResult.Error -> {
                val errState = result.toUiErrorState()
                if (_uiState.value is PrinterDetailUiState.Loading) {
                    _uiState.value = PrinterDetailUiState.Error(errState)
                } else if (!pollErrorShown) {
                    // Show the outage once, not every 5 seconds.
                    pollErrorShown = true
                    _userMessages.emit("Failed to update status: ${errState.message}")
                }
                return false
            }
        }
    }

    /** Runs any control command, reports the result as a message, then refreshes the printer. */
    fun execute(command: PrinterControlCommand) {
        viewModelScope.launch {
            if (connectivity.state.value.isOffline) {
                // Never act on, or queue for later, a printer we can't currently see.
                _userMessages.emit("You're offline — controls are disabled until the server is reachable.")
                return@launch
            }
            when (val result = printControlRepository.executeCommand(printerId, command)) {
                is ApiResult.Success -> {
                    _userMessages.emit(successMessage(command))
                    fetchPrinterDetailsInternal()
                    if (command is PrinterControlCommand.SetChamberLight) {
                        // Give the light a moment to come on, then take a new photo.
                        delay(1500L)
                        refreshSnapshot()
                    }
                }
                is ApiResult.Error -> {
                    val errState = result.toUiErrorState()
                    _userMessages.emit("${failureLabel(command)} failed: ${errState.message}")
                }
            }
        }
    }

    private fun successMessage(command: PrinterControlCommand): String = when (command) {
        PrinterControlCommand.Pause -> "Pause command sent"
        PrinterControlCommand.Resume -> "Resume command sent"
        PrinterControlCommand.Cancel -> "Print canceled"
        PrinterControlCommand.RefreshStatus -> "Status refreshed"
        PrinterControlCommand.ClearHms -> "HMS errors cleared"
        PrinterControlCommand.ClearPlate -> "Plate marked as cleared"
        is PrinterControlCommand.SetPrintSpeed -> "Print speed set to ${speedName(command.mode)}"
        is PrinterControlCommand.SetChamberLight -> if (command.on) "Chamber light on" else "Chamber light off"
        is PrinterControlCommand.AmsLoadFilament -> "Load command sent"
        is PrinterControlCommand.AmsUnloadFilament -> "Unload command sent"
    }

    private fun failureLabel(command: PrinterControlCommand): String = when (command) {
        PrinterControlCommand.Pause -> "Pause"
        PrinterControlCommand.Resume -> "Resume"
        PrinterControlCommand.Cancel -> "Cancel"
        PrinterControlCommand.RefreshStatus -> "Refresh status"
        PrinterControlCommand.ClearHms -> "Clear HMS"
        PrinterControlCommand.ClearPlate -> "Clear plate"
        is PrinterControlCommand.SetPrintSpeed -> "Set speed"
        is PrinterControlCommand.SetChamberLight -> "Chamber light"
        is PrinterControlCommand.AmsLoadFilament -> "Load filament"
        is PrinterControlCommand.AmsUnloadFilament -> "Unload filament"
    }

    private fun speedName(mode: Int): String = when (mode) {
        1 -> "Silent (50%)"
        2 -> "Standard (100%)"
        3 -> "Sport (124%)"
        4 -> "Ludicrous (166%)"
        else -> "Mode $mode"
    }

    class Factory(
        private val printerId: String,
        private val printerRepository: PrinterRepository,
        private val printControlRepository: PrintControlRepository,
        private val connectivity: ConnectivityStatus =
            AlwaysOnline,
        private val pollIntervalMs: suspend () -> Long = { PollInterval.DEFAULT_MS },
    ) : ViewModelProvider.Factory {
        // Safe: this factory is only asked for the one ViewModel class it builds.
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PrinterDetailViewModel(
            printerId = printerId,
            printerRepository = printerRepository,
            printControlRepository = printControlRepository,
            connectivity = connectivity,
            pollIntervalMs = pollIntervalMs,
        ) as T
    }
}
