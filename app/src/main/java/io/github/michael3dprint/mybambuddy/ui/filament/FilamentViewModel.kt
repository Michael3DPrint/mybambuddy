package io.github.michael3dprint.mybambuddy.ui.filament

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.toUiErrorState
import io.github.michael3dprint.mybambuddy.domain.model.CatalogFilament
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import io.github.michael3dprint.mybambuddy.domain.model.SpoolDraft
import io.github.michael3dprint.mybambuddy.domain.model.UiErrorState
import io.github.michael3dprint.mybambuddy.domain.model.UsageEntry
import io.github.michael3dprint.mybambuddy.domain.policy.SpoolDraftRules
import io.github.michael3dprint.mybambuddy.domain.repo.CatalogRepository
import io.github.michael3dprint.mybambuddy.domain.repo.SpoolRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the spool list shows: loading, the spools, or an error. */
sealed interface SpoolListState {
    data object Loading : SpoolListState
    data class Success(val spools: List<Spool>) : SpoolListState
    data class Error(val error: UiErrorState) : SpoolListState
}

/** The usage history of the spool being viewed: not asked yet, loading, the entries, or a failure. */
sealed interface UsageState {
    data object Idle : UsageState

    data object Loading : UsageState

    data class Loaded(val entries: List<UsageEntry>) : UsageState

    data class Failed(val message: String) : UsageState
}

/**
 * The Add spool form: the search, the draft being filled in, and the save state. [problem] says what is wrong with the draft, if
 * anything.
 */
data class AddSpoolState(
    val visible: Boolean = false,
    val query: String = "",
    val results: List<CatalogFilament> = emptyList(),
    val searching: Boolean = false,
    val searchMessage: String? = null,
    val draft: SpoolDraft = SpoolDraft(),
    val saving: Boolean = false,
    val saveError: String? = null,
) {
    /** What is wrong with the form right now, shown under the fields; null when it can be saved. */
    val problem: String? get() = SpoolDraftRules.problem(draft)
    val canSave: Boolean get() = problem == null && !saving
}

private const val CATALOG_NEEDS_CONNECTION =
    "The filament catalog needs a connection. Enter the details by hand, or try again when you're online."

/** Screen model for the spool list and the Add spool form, including the catalogue search. */
@OptIn(FlowPreview::class)
class FilamentViewModel(private val spools: SpoolRepository, private val catalog: CatalogRepository) : ViewModel() {

    private val _list = MutableStateFlow<SpoolListState>(SpoolListState.Loading)
    val list: StateFlow<SpoolListState> = _list.asStateFlow()

    private val _add = MutableStateFlow(AddSpoolState())
    val add: StateFlow<AddSpoolState> = _add.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _usage = MutableStateFlow<UsageState>(UsageState.Idle)
    val usage: StateFlow<UsageState> = _usage.asStateFlow()

    private val queries = MutableStateFlow("")

    init {
        refresh()
        viewModelScope.launch {
            queries.debounce(400).distinctUntilChanged().collect { runSearch(it) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            if (_list.value !is SpoolListState.Success) _list.value = SpoolListState.Loading
            _list.value = when (val r = spools.getSpools()) {
                is ApiResult.Success -> SpoolListState.Success(r.data)
                is ApiResult.Error -> SpoolListState.Error(r.toUiErrorState(aboutPrinter = false))
            }
        }
    }

    fun loadUsage(spoolId: Int) {
        _usage.value = UsageState.Loading
        viewModelScope.launch {
            _usage.value = when (val result = spools.getUsage(spoolId)) {
                is ApiResult.Success -> UsageState.Loaded(result.data)
                is ApiResult.Error -> UsageState.Failed(result.toUiErrorState().message)
            }
        }
    }

    fun openAdd(prefill: SpoolDraft = SpoolDraft()) {
        _add.value = AddSpoolState(visible = true, draft = prefill)
    }
    fun closeAdd() {
        _add.value = AddSpoolState()
    }

    fun onQueryChanged(q: String) {
        _add.update { it.copy(query = q, searchMessage = null) }
        queries.value = q
    }

    private suspend fun runSearch(q: String) {
        if (q.isBlank()) {
            _add.update { it.copy(results = emptyList(), searching = false, searchMessage = null) }
            return
        }
        _add.update { it.copy(searching = true) }
        when (val r = catalog.search(q)) {
            is ApiResult.Success -> _add.update {
                it.copy(
                    results = r.data,
                    searching = false,
                    searchMessage = if (r.data.isEmpty()) "No matches. Fill the fields in by hand below." else null,
                )
            }
            is ApiResult.Error -> _add.update {
                it.copy(
                    results = emptyList(),
                    searching = false,
                    searchMessage = if (r.errorType ==
                        ApiErrorType.NETWORK_ERROR
                    ) {
                        CATALOG_NEEDS_CONNECTION
                    } else {
                        r.toUiErrorState().message
                    },
                )
            }
        }
    }

    fun pick(item: CatalogFilament) {
        _add.update {
            it.copy(
                results = emptyList(),
                draft = SpoolDraft(
                    brand = item.manufacturer,
                    material = item.material,
                    colorName = item.name,
                    colorHex = item.colorHex,
                    labelWeightGrams = item.weightGrams,
                    coreWeightGrams = item.spoolWeightGrams,
                ),
            )
        }
    }

    fun updateDraft(transform: (SpoolDraft) -> SpoolDraft) {
        _add.update { it.copy(draft = transform(it.draft), saveError = null) }
    }

    fun save() {
        val state = _add.value
        if (!state.canSave) return
        viewModelScope.launch {
            _add.update { it.copy(saving = true, saveError = null) }
            when (val r = spools.createSpool(state.draft)) {
                is ApiResult.Success -> {
                    _add.value = AddSpoolState()
                    _messages.emit("Added ${r.data.title}")
                    refresh()
                }
                is ApiResult.Error -> _add.update { it.copy(saving = false, saveError = r.toUiErrorState(aboutPrinter = false).message) }
            }
        }
    }

    class Factory(private val spools: SpoolRepository, private val catalog: CatalogRepository) : ViewModelProvider.Factory {
        // Safe: this factory is only asked for the one ViewModel class it builds.
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FilamentViewModel(spools, catalog) as T
    }
}
