package io.github.michael3dprint.mybambuddy.ui.readiness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.toUiErrorState
import io.github.michael3dprint.mybambuddy.domain.model.DiagnosticReport
import io.github.michael3dprint.mybambuddy.domain.repo.DiagnosticsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where a printer's diagnostics run stands. */
sealed interface DiagnosticsState {
    data object Running : DiagnosticsState

    data class Done(val report: DiagnosticReport) : DiagnosticsState

    data class Failed(val message: String) : DiagnosticsState
}

/**
 * Runs Bambuddy's diagnostics for a printer, only when the user taps the button, and one run per printer at a time
 * (docs/design/20-server-diagnostics.md). A printer with no entry has not been run.
 */
class DiagnosticsViewModel(private val diagnostics: DiagnosticsRepository) : ViewModel() {
    private val _states = MutableStateFlow<Map<String, DiagnosticsState>>(emptyMap())
    val states: StateFlow<Map<String, DiagnosticsState>> = _states.asStateFlow()

    /** Starts a run for [printerId] unless one is already running for it. */
    fun run(printerId: String) {
        val id = printerId.toIntOrNull() ?: return
        if (_states.value[printerId] is DiagnosticsState.Running) return
        _states.update { it + (printerId to DiagnosticsState.Running) }
        viewModelScope.launch {
            val next = when (val r = diagnostics.run(id)) {
                is ApiResult.Success -> DiagnosticsState.Done(r.data)
                is ApiResult.Error -> DiagnosticsState.Failed(r.toUiErrorState(aboutPrinter = false).message)
            }
            _states.update { it + (printerId to next) }
        }
    }

    class Factory(private val diagnostics: DiagnosticsRepository) : ViewModelProvider.Factory {
        // Safe: this factory is only asked for the one ViewModel class it builds.
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DiagnosticsViewModel(diagnostics) as T
    }
}
