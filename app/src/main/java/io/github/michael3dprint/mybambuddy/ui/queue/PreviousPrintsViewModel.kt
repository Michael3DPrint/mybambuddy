package io.github.michael3dprint.mybambuddy.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.toUiErrorState
import io.github.michael3dprint.mybambuddy.domain.model.HistoryItem
import io.github.michael3dprint.mybambuddy.domain.policy.PreviousPrintFilter
import io.github.michael3dprint.mybambuddy.domain.repo.ArchiveRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the Previous prints list shows (design 44). */
data class PreviousPrintsState(
    val items: List<HistoryItem> = emptyList(),
    val filter: PreviousPrintFilter = PreviousPrintFilter.ALL,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    /** The small pictures by archive id, filled in after each page; a print without one is simply absent. */
    val pictures: Map<Int, ByteArray> = emptyMap(),
) {
    /** The prints under the chosen filter. */
    val visible: List<HistoryItem> get() = items.filter { filter.matches(it.status) }
}

/**
 * The permanent record of previous prints for one printer, or for all of them when [printerId] is null (design 44). It only reads: nothing here
 * changes or deletes an archive. Pages of [PAGE] are loaded on demand and the filter is applied to what has been loaded.
 */
class PreviousPrintsViewModel(private val archives: ArchiveRepository, private val printerId: Int?) : ViewModel() {
    private val _state = MutableStateFlow(PreviousPrintsState())
    val state: StateFlow<PreviousPrintsState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Starts again from the newest print. */
    fun refresh() {
        _state.value = PreviousPrintsState(filter = _state.value.filter)
        viewModelScope.launch { loadPage(0) }
    }

    /** Loads the next page. */
    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.endReached) return
        _state.value = s.copy(loadingMore = true)
        viewModelScope.launch { loadPage(s.items.size) }
    }

    /** Applies a filter chip. */
    fun setFilter(filter: PreviousPrintFilter) {
        _state.value = _state.value.copy(filter = filter)
    }

    private suspend fun loadPage(offset: Int) {
        when (val r = archives.page(PAGE, offset, printerId)) {
            is ApiResult.Error ->
                _state.value =
                    _state.value.copy(loading = false, loadingMore = false, error = r.toUiErrorState(aboutPrinter = false).message)
            is ApiResult.Success -> {
                val known = _state.value.items.map { it.id }.toSet()
                val fresh = r.data.filter { it.id !in known }
                _state.value = _state.value.copy(
                    items = _state.value.items + fresh,
                    loading = false,
                    loadingMore = false,
                    endReached = r.data.size < PAGE,
                    error = null,
                )
                for (item in fresh) {
                    val id = item.id.toIntOrNull() ?: continue
                    val bytes = (archives.picture(id) as? ApiResult.Success)?.data ?: continue
                    _state.value = _state.value.copy(pictures = _state.value.pictures + (id to bytes))
                }
            }
        }
    }

    /** Builds the view model for one printer or for all. */
    class Factory(private val archives: ArchiveRepository, private val printerId: Int?) : ViewModelProvider.Factory {
        // Safe: this factory is only asked for the one ViewModel class it builds.
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PreviousPrintsViewModel(archives, printerId) as T
    }

    private companion object {
        const val PAGE = 20
    }
}
