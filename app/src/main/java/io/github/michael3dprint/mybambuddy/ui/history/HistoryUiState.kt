package io.github.michael3dprint.mybambuddy.ui.history

import io.github.michael3dprint.mybambuddy.domain.model.HistoryItem
import io.github.michael3dprint.mybambuddy.domain.model.UiErrorState

/** What the History tab shows: the items loaded so far, loading and refreshing flags, and any error. */
data class HistoryUiState(
    val isLoading: Boolean = true,
    val isLoadingNextPage: Boolean = false,
    val isRefreshing: Boolean = false,
    val items: List<HistoryItem> = emptyList(),
    val canLoadMore: Boolean = true,
    val errorState: UiErrorState? = null,
)
