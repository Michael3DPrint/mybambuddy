package io.github.michael3dprint.mybambuddy.ui.maintenance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.toUiErrorState
import io.github.michael3dprint.mybambuddy.domain.connectivity.AlwaysOnline
import io.github.michael3dprint.mybambuddy.domain.connectivity.ConnectivityStatus
import io.github.michael3dprint.mybambuddy.domain.model.PrinterMaintenance
import io.github.michael3dprint.mybambuddy.domain.model.UiErrorState
import io.github.michael3dprint.mybambuddy.domain.policy.PrinterFilter
import io.github.michael3dprint.mybambuddy.domain.repo.MaintenanceRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the maintenance screen shows: loading, the printers' upkeep, or an error. */
sealed interface MaintenanceState {
    data object Loading : MaintenanceState

    data class Success(val printers: List<PrinterMaintenance>) : MaintenanceState

    data class Error(val error: UiErrorState) : MaintenanceState
}

/**
 * Loads the maintenance overview and marks items done. Marking done is refused while the app is offline.
 * [printerId] limits the list to one printer; null shows every printer.
 */
class MaintenanceViewModel(
    private val repository: MaintenanceRepository,
    private val connectivity: ConnectivityStatus = AlwaysOnline,
    private val printerId: Int? = null,
) : ViewModel() {
    private val _state = MutableStateFlow<MaintenanceState>(MaintenanceState.Loading)
    val state: StateFlow<MaintenanceState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            if (_state.value !is MaintenanceState.Success) _state.value = MaintenanceState.Loading
            when (val result = repository.getOverview()) {
                is ApiResult.Success -> _state.value = MaintenanceState.Success(PrinterFilter.maintenance(result.data, printerId))
                is ApiResult.Error -> if (_state.value !is MaintenanceState.Success) {
                    _state.value = MaintenanceState.Error(result.toUiErrorState())
                }
            }
        }
    }

    /** The screen has already asked the user to confirm. */
    fun markDone(itemId: Int, name: String) {
        viewModelScope.launch {
            if (connectivity.state.value.isOffline) {
                _messages.emit("You're offline — can't mark maintenance done right now.")
                return@launch
            }
            when (val result = repository.markDone(itemId)) {
                is ApiResult.Success -> {
                    _messages.emit("$name marked as done")
                    refresh()
                }
                is ApiResult.Error -> _messages.emit(result.toUiErrorState().message)
            }
        }
    }

    class Factory(
        private val repository: MaintenanceRepository,
        private val connectivity: ConnectivityStatus = AlwaysOnline,
        private val printerId: Int? = null,
    ) : ViewModelProvider.Factory {
        // Safe: this factory is only asked for the one ViewModel class it builds.
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MaintenanceViewModel(repository, connectivity, printerId) as T
    }
}
