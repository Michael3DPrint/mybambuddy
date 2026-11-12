package io.github.michael3dprint.mybambuddy.ui.printers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.isUnreachable
import io.github.michael3dprint.mybambuddy.data.api.toUiErrorState
import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.domain.policy.MaintenanceBadge
import io.github.michael3dprint.mybambuddy.domain.policy.PollInterval
import io.github.michael3dprint.mybambuddy.domain.repo.MaintenanceRepository
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Polls the printers, keeps the last good list when a poll fails, and loads the maintenance badges. */
class PrintersViewModel(
    private val repository: PrinterRepository,
    /** The saved polling choice (Settings), read each time polling starts. */
    private val pollIntervalMs: suspend () -> Long = { PollInterval.DEFAULT_MS },
    /** Optional: when given, printer cards show a badge for upkeep that is due. One overview read, never one per card. */
    private val maintenance: MaintenanceRepository? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PrintersUiState>(PrintersUiState.Loading)
    val uiState: StateFlow<PrintersUiState> = _uiState.asStateFlow()

    private val _maintenanceBadges = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Printer id to badge text, only for printers with upkeep due or coming due. */
    val maintenanceBadges: StateFlow<Map<String, String>> = _maintenanceBadges.asStateFlow()

    private var pollingJob: Job? = null

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob?.cancel()
        loadMaintenance()
        pollingJob = viewModelScope.launch {
            repository.getPrintersFlow(pollIntervalMs()).collect { result ->
                handleResult(result)
            }
        }
    }

    private fun loadMaintenance() {
        val source = maintenance ?: return
        viewModelScope.launch {
            // A failed read keeps the badges already shown; they are a hint, not something to report an error for.
            val overview = (source.getOverview() as? ApiResult.Success)?.data ?: return@launch
            _maintenanceBadges.value = overview.mapNotNull { m -> MaintenanceBadge.label(m)?.let { m.printerId.toString() to it } }.toMap()
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun refresh() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is PrintersUiState.Success) {
                _uiState.value = currentState.copy(isRefreshing = true)
            } else if (currentState is PrintersUiState.Error) {
                _uiState.value = currentState.copy(isRefreshing = true)
            } else {
                _uiState.value = PrintersUiState.Loading
            }

            loadMaintenance()
            val result = repository.fetchPrinters()
            handleResult(result)
        }
    }

    private fun handleResult(result: ApiResult<List<Printer>>) {
        when (result) {
            is ApiResult.Success -> {
                _uiState.value = PrintersUiState.Success(
                    printers = result.data,
                    isRefreshing = false,
                )
            }
            is ApiResult.Error -> {
                val current = _uiState.value
                val errState = result.toUiErrorState()
                _uiState.value = if (current is PrintersUiState.Success) {
                    // Keep the last good data instead of replacing the dashboard. An unreachable server is already announced by the
                    // app-wide offline banner (design 16), so only a real answer (sign-in, rejected, bad reply) adds a strip here.
                    current.copy(isRefreshing = false, staleError = errState.takeUnless { result.isUnreachable() })
                } else {
                    PrintersUiState.Error(errorState = errState, isRefreshing = false)
                }
            }
        }
    }

    class Factory(
        private val repository: PrinterRepository,
        private val pollIntervalMs: suspend () -> Long = { PollInterval.DEFAULT_MS },
        private val maintenance: MaintenanceRepository? = null,
    ) : ViewModelProvider.Factory {
        // Safe: this factory is only asked for the one ViewModel class it builds.
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PrintersViewModel::class.java)) {
                return PrintersViewModel(repository, pollIntervalMs, maintenance) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
