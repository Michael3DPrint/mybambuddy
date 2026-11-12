package io.github.michael3dprint.mybambuddy.domain.nfc

import kotlinx.coroutines.flow.SharedFlow

/** Result of writing a tag. */
sealed interface TagWriteOutcome {
    data object Success : TagWriteOutcome

    data class TooSmall(val capacityBytes: Int) : TagWriteOutcome

    data object ReadOnly : TagWriteOutcome

    data class Failed(val reason: String) : TagWriteOutcome
}

/** A tag held against the phone. Reading/writing blocks on NFC I/O, so both are suspend functions. */
interface DetectedTag {
    /** Hardware UID as upper-case hex without separators. */
    val uidHex: String

    /** Payload of the first NDEF record with this MIME type, or null. */
    suspend fun readMimePayload(mimeType: String): ByteArray?

    /** Writes one NDEF record of the given MIME type to the tag and reports how it went. */
    suspend fun writeMimeRecord(mimeType: String, payload: ByteArray): TagWriteOutcome
}

/** The phone's NFC reader. Listening is switched on only while a read/write dialog is open. */
interface NfcGateway {
    /** True when the phone has NFC hardware. */
    val isAvailable: Boolean

    /** True when NFC is switched on in the phone's settings. */
    val isEnabled: Boolean

    /** Tags held against the phone while listening. */
    val tags: SharedFlow<DetectedTag>

    /** Starts or stops reading tags. It is on only while a read or write dialog is open. */
    fun setListening(on: Boolean)
}
