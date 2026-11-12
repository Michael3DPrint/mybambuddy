package io.github.michael3dprint.mybambuddy.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.toUiErrorState
import io.github.michael3dprint.mybambuddy.domain.repo.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Loads the print archive a page at a time and refreshes it on request. */
class HistoryViewModel(private val historyRepository: HistoryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val pageSize = 20

    init {
        loadInitialHistory()
    }

    fun loadInitialHistory() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorState = null,
                )
            }
            fetchPage(offset = 0, isInitial = true)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRefreshing = true,
                    errorState = null,
                )
            }
            fetchPage(offset = 0, isRefresh = true)
        }
    }

    fun loadNextPage() {
        val currentState = _uiState.value
        if (currentState.isLoadingNextPage || !currentState.canLoadMore || currentState.isLoading || currentState.isRefreshing) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingNextPage = true) }
            fetchPage(offset = currentState.items.size, isNextPage = true)
        }
    }

    private suspend fun fetchPage(offset: Int, isInitial: Boolean = false, isRefresh: Boolean = false, isNextPage: Boolean = false) {
        when (val result = historyRepository.getHistory(limit = pageSize, offset = offset)) {
            is ApiResult.Success -> {
                val newItems = result.data
                val updatedItems = if (isNextPage) {
                    val existingIds = _uiState.value.items.map { it.id }.toSet()
                    _uiState.value.items + newItems.filterNot { existingIds.contains(it.id) }
                } else {
                    newItems
                }
                val canLoadMore = newItems.size >= pageSize

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingNextPage = false,
                        isRefreshing = false,
                        items = updatedItems,
                        canLoadMore = canLoadMore,
                        errorState = null,
                    )
                }
            }
            is ApiResult.Error -> {
                val err = result.toUiErrorState()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingNextPage = false,
                        isRefreshing = false,
                        errorState = if (isNextPage) it.errorState else err,
                    )
                }
            }
        }
    }

    class Factory(private val historyRepository: HistoryRepository) : ViewModelProvider.Factory {
        // Safe: this factory is only asked for the one ViewModel class it builds.
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HistoryViewModel(historyRepository) as T
    }
}
