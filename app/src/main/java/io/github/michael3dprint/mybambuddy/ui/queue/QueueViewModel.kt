package io.github.michael3dprint.mybambuddy.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.toUiErrorState
import io.github.michael3dprint.mybambuddy.domain.connectivity.AlwaysOnline
import io.github.michael3dprint.mybambuddy.domain.connectivity.ConnectivityStatus
import io.github.michael3dprint.mybambuddy.domain.model.PrinterTool
import io.github.michael3dprint.mybambuddy.domain.model.QueueItem
import io.github.michael3dprint.mybambuddy.domain.model.UiErrorState
import io.github.michael3dprint.mybambuddy.domain.policy.PrinterFilter
import io.github.michael3dprint.mybambuddy.domain.policy.QueueOrdering
import io.github.michael3dprint.mybambuddy.domain.policy.QueuePicture
import io.github.michael3dprint.mybambuddy.domain.policy.StartCheck
import io.github.michael3dprint.mybambuddy.domain.policy.StartWatcher
import io.github.michael3dprint.mybambuddy.domain.repo.QueueRepository
import io.github.michael3dprint.mybambuddy.domain.repo.ReadinessCheck
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A job whose start Bambuddy refused for filament, with the reason in words; the user may choose to print anyway. */
data class ShortfallPrompt(val item: QueueItem, val text: String)

/** True when Bambuddy refused with its filament codes (not enough filament, or no loaded slot for a filament); both can be overridden once. */
private fun ApiResult.Error.isFilamentProblem() =
    code == 409 && (serverCode == "insufficient_filament" || serverCode == "unmatched_filament")

/** What the queue screen shows: loading, the jobs, or an error. */
sealed interface QueueState {
    data object Loading : QueueState

    data class Success(val items: List<QueueItem>) : QueueState

    data class Error(val error: UiErrorState) : QueueState
}

/**
 * Every action here acts on a printer or its queue: confirmed by the screen, single attempt, blocked while offline.
 * [printerId] limits the list to one printer's jobs; null shows every printer's.
 */
class QueueViewModel(
    private val repository: QueueRepository,
    private val connectivity: ConnectivityStatus = AlwaysOnline,
    private val readiness: ReadinessCheck? = null,
    private val printerId: Int? = null,
) : ViewModel() {
    private val watcher = StartWatcher(repository)
    private val _startBlocked = MutableStateFlow<Map<Int, String>>(emptyMap())

    /** Printers that cannot start a queued print right now (printer id to the reason), so the screen can turn Start off. */
    val startBlocked: StateFlow<Map<Int, String>> = _startBlocked.asStateFlow()

    private val _state = MutableStateFlow<QueueState>(QueueState.Loading)
    val state: StateFlow<QueueState> = _state.asStateFlow()

    private val _pictures = MutableStateFlow<Map<Int, ByteArray>>(emptyMap())

    /** The small pictures of the jobs by job id, filled in after the list is shown; a job without one is simply absent. */
    val pictures: StateFlow<Map<Int, ByteArray>> = _pictures.asStateFlow()

    private val _shortfalls = MutableSharedFlow<ShortfallPrompt>()

    /** Raised when Bambuddy refuses a start for a filament reason; the screen asks whether to print anyway. */
    val shortfalls: SharedFlow<ShortfallPrompt> = _shortfalls.asSharedFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            if (_state.value !is QueueState.Success) _state.value = QueueState.Loading
            when (val result = repository.getQueue()) {
                is ApiResult.Success -> {
                    _state.value = QueueState.Success(PrinterFilter.queue(result.data, printerId))
                    refreshStartBlocked()
                    loadPictures(PrinterFilter.queue(result.data, printerId))
                }
                is ApiResult.Error -> if (_state.value !is QueueState.Success) _state.value = QueueState.Error(result.toUiErrorState())
            }
        }
    }

    /** Fetches the pictures one after another (at most [MAX_PICTURES]); one that fails is skipped and the list never waits for them. */
    private suspend fun loadPictures(items: List<QueueItem>) {
        for (item in items.take(MAX_PICTURES)) {
            if (item.id in _pictures.value || QueuePicture.sourceOf(item) == null) continue
            val bytes = (repository.picture(item) as? ApiResult.Success)?.data ?: continue
            _pictures.value = _pictures.value + (item.id to bytes)
        }
    }

    private suspend fun refreshStartBlocked() {
        val reports = (readiness?.checkAll() as? ApiResult.Success)?.data ?: return
        _startBlocked.value = reports.mapNotNull { r ->
            val reason = r.statusOf(PrinterTool.START_QUEUED_PRINT)?.takeIf { !it.isEnabled }?.reason
            r.printerId.toIntOrNull()?.let { id -> reason?.let { id to it } }
        }.toMap()
    }

    fun move(itemId: Int, up: Boolean) {
        val items = (_state.value as? QueueState.Success)?.items ?: return
        val order = QueueOrdering.move(items, itemId, up) ?: return
        act("Queue reordered") { repository.reorder(order) }
    }

    /**
     * Starts a job and then checks what became of it: Bambuddy accepting the start does not mean the printer took the file, so the message
     * names the outcome (printing, or the reason it will not print) instead of saying "Started".
     */
    fun start(item: QueueItem, skipFilamentCheck: Boolean = false) {
        viewModelScope.launch {
            if (connectivity.state.value.isOffline) {
                _messages.emit("You're offline — queue actions are disabled until the server is reachable.")
                return@launch
            }
            when (val result = repository.start(item.id, item.printerId, skipFilamentCheck)) {
                is ApiResult.Error -> if (!skipFilamentCheck && result.isFilamentProblem()) {
                    _shortfalls.emit(ShortfallPrompt(item, result.serverDetail ?: "Bambuddy reports a filament problem."))
                } else {
                    _messages.emit(result.toUiErrorState(aboutPrinter = result.code != CONFLICT).message)
                }
                is ApiResult.Success -> {
                    _messages.emit("Bambuddy accepted the start of ${item.name}. Checking that the printer takes it…")
                    val verdict = watcher.watch(item.id)
                    _messages.emit(StartCheck.words(verdict, item.printerName))
                    refresh()
                }
            }
        }
    }

    /** Removes every finished job in [items] from the queue, one request each; says how many went and whether any did not. */
    fun clearFinished(items: List<QueueItem>) {
        val finished = items.filter { it.isFinished }
        if (finished.isEmpty()) return
        viewModelScope.launch {
            var failed = 0
            for (item in finished) if (repository.remove(item.id) is ApiResult.Error) failed++
            val done = finished.size - failed
            _messages.emit(
                if (failed == 0) "Cleared $done finished job(s)." else "Cleared $done finished job(s); $failed could not be removed.",
            )
            refresh()
        }
    }

    fun cancel(item: QueueItem) = act("Cancelled ${item.name}") { repository.cancel(item.id) }

    fun remove(item: QueueItem) = act("Removed ${item.name}") { repository.remove(item.id) }

    fun resume(printerId: Int) = act("Queue resumed") { repository.resumeQueue(printerId) }

    private fun act(successMessage: String, call: suspend () -> ApiResult<Unit>) {
        viewModelScope.launch {
            if (connectivity.state.value.isOffline) {
                _messages.emit("You're offline — queue actions are disabled until the server is reachable.")
                return@launch
            }
            when (val result = call()) {
                is ApiResult.Success -> {
                    _messages.emit(successMessage)
                    refresh()
                }
                is ApiResult.Error -> _messages.emit(result.toUiErrorState().message)
            }
        }
    }

    private companion object {
        const val MAX_PICTURES = 30
        const val CONFLICT = 409
    }

    class Factory(
        private val repository: QueueRepository,
        private val connectivity: ConnectivityStatus = AlwaysOnline,
        private val readiness: ReadinessCheck? = null,
        private val printerId: Int? = null,
    ) : ViewModelProvider.Factory {
        // Safe: this factory is only asked for the one ViewModel class it builds.
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = QueueViewModel(repository, connectivity, readiness, printerId) as T
    }
}
