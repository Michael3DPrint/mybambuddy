package io.github.michael3dprint.mybambuddy.ui.filament

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.toUiErrorState
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import io.github.michael3dprint.mybambuddy.domain.nfc.DetectedTag
import io.github.michael3dprint.mybambuddy.domain.nfc.NfcGateway
import io.github.michael3dprint.mybambuddy.domain.nfc.OPENTAG3D_MIME
import io.github.michael3dprint.mybambuddy.domain.nfc.OpenTag3d
import io.github.michael3dprint.mybambuddy.domain.nfc.OpenTag3dData
import io.github.michael3dprint.mybambuddy.domain.nfc.TagWriteOutcome
import io.github.michael3dprint.mybambuddy.domain.repo.SpoolRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the NFC dialog shows. */
sealed interface NfcDialogState {
    data object Closed : NfcDialogState

    data class WaitingToWrite(val spool: Spool) : NfcDialogState

    data class WaitingToRead(val note: String? = null) : NfcDialogState

    data object Working : NfcDialogState

    data class Done(val message: String) : NfcDialogState

    data class Problem(val message: String) : NfcDialogState

    /** A tag was read: the spool it belongs to (if known) and what the tag itself says. */
    data class Scanned(val uid: String, val spool: Spool?, val fromTag: OpenTag3dData?) : NfcDialogState
}

/**
 * Writes OpenTag3D tags for spools (and links the tag UID to the spool) and reads tags to find a spool.
 * The reader is only switched on while a dialog is open.
 */
class NfcTagViewModel(private val gateway: NfcGateway, private val spools: SpoolRepository) : ViewModel() {
    private val _dialog = MutableStateFlow<NfcDialogState>(NfcDialogState.Closed)
    val dialog: StateFlow<NfcDialogState> = _dialog.asStateFlow()

    private var listenJob: Job? = null

    private val _loadRequests = MutableSharedFlow<Spool>(extraBufferCapacity = 1)

    /** Emitted when the user chose "Load into AMS" for a scanned spool. Handling it is the screen's job (SRP). */
    val loadRequests: SharedFlow<Spool> = _loadRequests.asSharedFlow()

    private val _linked = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emitted after a tag was written and its UID linked to a spool, so the screen can reload the spool list and show the link. */
    val linked: SharedFlow<Unit> = _linked.asSharedFlow()

    val isAvailable: Boolean get() = gateway.isAvailable

    fun startWrite(spool: Spool) {
        if (!checkReady()) return
        _dialog.value = NfcDialogState.WaitingToWrite(spool)
        listen { tag -> write(tag, spool) }
    }

    fun startRead() {
        if (!checkReady()) return
        _dialog.value = NfcDialogState.WaitingToRead()
        listen { tag -> read(tag) }
    }

    /**
     * A tag was tapped while the app was closed or on another screen. Looks the tag up and shows the result in the scan dialog.
     * Read only: nothing is written to the tag and nothing is assigned.
     */
    fun openFromTag(tag: DetectedTag) {
        listenJob?.cancel()
        _dialog.value = NfcDialogState.Working
        listenJob = viewModelScope.launch { read(tag) }
    }

    fun requestLoad(spool: Spool) {
        close()
        _loadRequests.tryEmit(spool)
    }

    fun close() {
        listenJob?.cancel()
        gateway.setListening(false)
        _dialog.value = NfcDialogState.Closed
    }

    private fun checkReady(): Boolean {
        val message = when {
            !gateway.isAvailable -> "This phone has no NFC."
            !gateway.isEnabled -> "NFC is turned off. Turn it on in the phone's settings."
            else -> return true
        }
        _dialog.value = NfcDialogState.Problem(message)
        return false
    }

    private fun listen(onTag: suspend (DetectedTag) -> Unit) {
        listenJob?.cancel()
        gateway.setListening(true)
        listenJob = viewModelScope.launch {
            gateway.tags.collect { tag ->
                _dialog.value = NfcDialogState.Working
                onTag(tag)
            }
        }
    }

    private suspend fun write(tag: DetectedTag, spool: Spool) {
        val payload = OpenTag3d.encode(spool.toTagData())
        when (val outcome = tag.writeMimeRecord(OPENTAG3D_MIME, payload)) {
            TagWriteOutcome.Success -> link(spool, tag.uidHex)
            is TagWriteOutcome.TooSmall -> problem("This tag only holds ${outcome.capacityBytes} bytes. Use an NTAG215 or larger.")
            TagWriteOutcome.ReadOnly -> problem("This tag is read-only.")
            is TagWriteOutcome.Failed -> problem(outcome.reason)
        }
    }

    private suspend fun link(spool: Spool, uid: String) {
        when (val result = spools.linkTag(spool.id, uid)) {
            is ApiResult.Success -> {
                _dialog.value = NfcDialogState.Done("Tag written and linked to ${spool.title}.")
                gateway.setListening(false)
                _linked.tryEmit(Unit)
            }
            // The tag itself was written; only the link failed (e.g. the tag already belongs to another spool).
            is ApiResult.Error -> problem("Tag written, but linking failed: ${result.toUiErrorState().message}")
        }
    }

    private suspend fun read(tag: DetectedTag) {
        val data = tag.readMimePayload(OPENTAG3D_MIME)?.let { OpenTag3d.decode(it) }
        val known = (spools.getSpools() as? ApiResult.Success)?.data.orEmpty()
        val spool = known.firstOrNull { it.tagUid.equals(tag.uidHex, ignoreCase = true) }
        gateway.setListening(false)
        _dialog.value = NfcDialogState.Scanned(tag.uidHex, spool, data)
    }

    private fun problem(message: String) {
        _dialog.value = NfcDialogState.Problem(message)
    }

    override fun onCleared() {
        gateway.setListening(false)
    }

    class Factory(private val gateway: NfcGateway, private val spools: SpoolRepository) : ViewModelProvider.Factory {
        // Safe: this factory is only asked for the one ViewModel class it builds.
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = NfcTagViewModel(gateway, spools) as T
    }
}

/** The OpenTag3D data to write to a tag for this spool. */
internal fun Spool.toTagData(): OpenTag3dData = OpenTag3dData(
    material = material,
    modifiers = subtype,
    manufacturer = brand,
    colorName = colorName,
    rgba = if (colorHex.length == 6) colorHex + "FF" else colorHex,
    diameterMm = 1.75,
    weightGrams = labelWeightGrams,
    printTempC = 0,
)
