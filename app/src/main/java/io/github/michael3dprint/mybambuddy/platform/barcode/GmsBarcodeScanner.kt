package io.github.michael3dprint.mybambuddy.platform.barcode

import android.content.Context
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import io.github.michael3dprint.mybambuddy.domain.barcode.BarcodeScanner
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Google's code scanner (no camera permission needed, runs in Google Play services). */
class GmsBarcodeScanner(private val context: Context) : BarcodeScanner {
    override suspend fun scan(): String? = suspendCancellableCoroutine { continuation ->
        GmsBarcodeScanning.getClient(context).startScan()
            .addOnSuccessListener { barcode -> continuation.resume(barcode.rawValue) }
            .addOnCanceledListener { continuation.resume(null) }
            .addOnFailureListener { continuation.resume(null) }
    }
}
