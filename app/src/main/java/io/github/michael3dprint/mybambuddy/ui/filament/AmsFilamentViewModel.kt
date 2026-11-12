package io.github.michael3dprint.mybambuddy.ui.filament

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.toUiErrorState
import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.domain.model.PrinterControlCommand
import io.github.michael3dprint.mybambuddy.domain.model.PrinterTool
import io.github.michael3dprint.mybambuddy.domain.policy.AmsTrayAddress
import io.github.michael3dprint.mybambuddy.domain.policy.ToolAvailability
import io.github.michael3dprint.mybambuddy.domain.repo.PrintControlRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Whether a slot's filament is being fed in or out. */
enum class FeedAction { LOAD, UNLOAD }

/** A load or unload waiting for the user's confirmation. [trayNumber] is the printer's own tray number for the slot. */
data class PendingFeed(val action: FeedAction, val unit: AmsUnit, val trayId: Int, val trayNumber: Int) {
    /** For example "X2D · AMS 1 · slot 3". */
    val slotLabel: String get() = "${unit.printerName} · ${unit.name} · slot ${trayId + 1}"
}

/**
 * Loads filament from an AMS slot into the printer, or unloads it (design 33). These move filament and may heat the nozzle, so a request
 * is checked first (the printer must be reachable, in Developer LAN mode, with an AMS and not printing), then waits for the user's
 * confirmation; only [confirm] sends, once, with no retry. The repository checks again on a fresh status read before anything leaves.
 */
class AmsFilamentViewModel(private val control: PrintControlRepository) : ViewModel() {
    private val _pending = MutableStateFlow<PendingFeed?>(null)

    /** The load or unload waiting to be confirmed, or null. */
    val pending: StateFlow<PendingFeed?> = _pending.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** What happened, in words for the user. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emitted after a command was accepted, so the screen can read the slots again. */
    val changed: SharedFlow<Unit> = _changed.asSharedFlow()

    /** Starts a load or unload for slot [trayId] of [unit]: refuses with the reason, or waits for confirmation. Sends nothing. */
    fun request(action: FeedAction, unit: AmsUnit, trayId: Int) {
        val number = AmsTrayAddress.forFeed(unit.amsId, unit.isHt, trayId)
        val status = ToolAvailability.statusOf(PrinterTool.FEED_FILAMENT, unit.capabilities, unit.printerName)
        when {
            number == null -> _messages.tryEmit("This slot can't be loaded or unloaded from the app.")
            !status.isEnabled -> _messages.tryEmit(status.reason ?: "That isn't available right now.")
            else -> _pending.value = PendingFeed(action, unit, trayId, number)
        }
    }

    /** The user confirmed: sends the command once. */
    fun confirm() {
        val p = _pending.value ?: return
        _pending.value = null
        val command = when (p.action) {
            FeedAction.LOAD -> PrinterControlCommand.AmsLoadFilament(p.trayNumber)
            FeedAction.UNLOAD -> PrinterControlCommand.AmsUnloadFilament(p.trayNumber)
        }
        viewModelScope.launch {
            when (val result = control.executeCommand(p.unit.printerId.toString(), command)) {
                is ApiResult.Success -> {
                    _messages.emit(
                        "${if (p.action == FeedAction.LOAD) "Load" else "Unload"} command sent for ${p.slotLabel}. Watch the printer.",
                    )
                    _changed.emit(Unit)
                }
                is ApiResult.Error -> _messages.emit(result.toUiErrorState().message)
            }
        }
    }

    /** The user backed out: nothing is sent. */
    fun cancel() {
        _pending.value = null
    }

    class Factory(private val control: PrintControlRepository) : ViewModelProvider.Factory {
        // Safe: this factory is only asked for the one ViewModel class it builds.
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AmsFilamentViewModel(control) as T
    }
}
