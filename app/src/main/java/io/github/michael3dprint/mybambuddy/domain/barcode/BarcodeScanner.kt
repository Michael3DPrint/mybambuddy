package io.github.michael3dprint.mybambuddy.domain.barcode

/** Opens the system barcode/QR scanner. */
interface BarcodeScanner {
    /** The raw scanned value, or null if the user cancelled or scanning isn't available. */
    suspend fun scan(): String?
}
