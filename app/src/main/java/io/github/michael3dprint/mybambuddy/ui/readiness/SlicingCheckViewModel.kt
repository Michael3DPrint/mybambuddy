package io.github.michael3dprint.mybambuddy.ui.readiness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.toUiErrorState
import io.github.michael3dprint.mybambuddy.domain.model.SlicingReadiness
import io.github.michael3dprint.mybambuddy.domain.model.UiErrorState
import io.github.michael3dprint.mybambuddy.domain.repo.SlicingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the slicing check stands. */
sealed interface SlicingState {
    data object Loading : SlicingState

    data class Done(val readiness: SlicingReadiness, val checkedAtMillis: Long) : SlicingState

    data class Error(val error: UiErrorState) : SlicingState
}

/** Runs the slicing readiness check (design 36) on request. Read only: it never changes a setting or starts a slice. */
class SlicingCheckViewModel(private val repository: SlicingRepository, private val clock: () -> Long = { System.currentTimeMillis() }) :
    ViewModel() {
    private val _state = MutableStateFlow<SlicingState>(SlicingState.Loading)
    val state: StateFlow<SlicingState> = _state.asStateFlow()

    /** Reads everything afresh. A failure replaces the result with an error; nothing is assumed ready. */
    fun check() {
        _state.value = SlicingState.Loading
        viewModelScope.launch {
            _state.value = when (val r = repository.check()) {
                is ApiResult.Success -> SlicingState.Done(r.data, clock())
                is ApiResult.Error -> SlicingState.Error(r.toUiErrorState())
            }
        }
    }

    class Factory(private val repository: SlicingRepository) : ViewModelProvider.Factory {
        // Safe: this factory is only asked for the one ViewModel class it builds.
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SlicingCheckViewModel(repository) as T
    }
}
