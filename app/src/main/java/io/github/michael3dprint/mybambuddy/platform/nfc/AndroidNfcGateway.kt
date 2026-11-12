package io.github.michael3dprint.mybambuddy.platform.nfc

import android.app.Activity
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import io.github.michael3dprint.mybambuddy.domain.nfc.DetectedTag
import io.github.michael3dprint.mybambuddy.domain.nfc.NfcGateway
import io.github.michael3dprint.mybambuddy.domain.nfc.TagWriteOutcome
import io.github.michael3dprint.mybambuddy.util.PrintDeckLogger
import java.io.IOException
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/**
 * Android implementation. Owned by the app, attached to the visible activity (weak reference, so a
 * rotated activity is never leaked). Reader mode is on only while [setListening] is true.
 */
class AndroidNfcGateway : NfcGateway {
    private var activity: WeakReference<Activity>? = null
    private var adapter: NfcAdapter? = null
    private var listening = false
    private val _tags = MutableSharedFlow<DetectedTag>(extraBufferCapacity = 1)

    override val tags: SharedFlow<DetectedTag> = _tags.asSharedFlow()
    override val isAvailable: Boolean get() = adapter != null
    override val isEnabled: Boolean get() = adapter?.isEnabled == true

    fun attach(host: Activity) {
        activity = WeakReference(host)
        adapter = NfcAdapter.getDefaultAdapter(host)
        apply()
    }

    fun detach() {
        val host = activity?.get()
        if (host != null) runCatching { adapter?.disableReaderMode(host) }
        activity = null
    }

    /** The tag that started [intent] (a tag tapped outside the app), or null when the intent is not a tag discovery. Untrusted input. */
    fun tagFrom(intent: Intent): DetectedTag? {
        if (intent.action != NfcAdapter.ACTION_NDEF_DISCOVERED) return null
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION") // The typed overload needs API 33; minSdk is 26.
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
        return tag?.let { AndroidDetectedTag(it) }
    }

    override fun setListening(on: Boolean) {
        listening = on
        apply()
    }

    private fun apply() {
        val host = activity?.get() ?: return
        val nfc = adapter ?: return
        if (listening) {
            nfc.enableReaderMode(host, { tag -> _tags.tryEmit(AndroidDetectedTag(tag)) }, READER_FLAGS, null)
        } else {
            nfc.disableReaderMode(host)
        }
    }

    private companion object {
        // NTAG215 / Bambu RFID are NFC-A; OpenPrintTag is NFC-V.
        const val READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_V
    }
}

private class AndroidDetectedTag(private val tag: Tag) : DetectedTag {
    override val uidHex: String = tag.id.joinToString("") { "%02X".format(it.toInt() and 0xFF) }

    override suspend fun readMimePayload(mimeType: String): ByteArray? = withContext(Dispatchers.IO) {
        val ndef = Ndef.get(tag) ?: return@withContext null
        try {
            ndef.connect()
            val message = ndef.ndefMessage ?: ndef.cachedNdefMessage
            message?.records?.firstOrNull { it.tnf == NdefRecord.TNF_MIME_MEDIA && String(it.type, Charsets.US_ASCII) == mimeType }?.payload
        } catch (e: IOException) {
            PrintDeckLogger.w(message = "NFC read failed: ${e.message}")
            null
        } finally {
            runCatching { ndef.close() }
        }
    }

    override suspend fun writeMimeRecord(mimeType: String, payload: ByteArray): TagWriteOutcome = withContext(Dispatchers.IO) {
        val message = NdefMessage(NdefRecord.createMime(mimeType, payload))
        val size = message.toByteArray().size
        try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.use {
                    it.connect()
                    when {
                        !it.isWritable -> TagWriteOutcome.ReadOnly
                        it.maxSize < size -> TagWriteOutcome.TooSmall(it.maxSize)
                        else -> {
                            it.writeNdefMessage(message)
                            TagWriteOutcome.Success
                        }
                    }
                }
            } else {
                val formatable = NdefFormatable.get(tag) ?: return@withContext TagWriteOutcome.Failed("This tag type can't hold NDEF data.")
                formatable.use {
                    it.connect()
                    it.format(message)
                    TagWriteOutcome.Success
                }
            }
        } catch (e: IOException) {
            PrintDeckLogger.w(message = "NFC write failed: ${e.message}")
            TagWriteOutcome.Failed("Lost contact with the tag. Hold it steady against the back of the phone and try again.")
        } catch (e: android.nfc.FormatException) {
            TagWriteOutcome.Failed("The tag could not be formatted.")
        }
    }
}
