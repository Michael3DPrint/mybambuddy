package io.github.michael3dprint.mybambuddy.domain.barcode

/** Answer of an online product lookup. */
sealed interface EanLookupResult {
    data class Found(val title: String) : EanLookupResult

    data object NotFound : EanLookupResult

    data class Unavailable(val reason: String) : EanLookupResult
}

/** Looks up a product title for a barcode on a public service. Only the digit string is ever sent. */
interface EanLookup {
    /** Looks up the product title for a barcode. Failure is returned as a result, not thrown. */
    suspend fun lookup(ean: String): EanLookupResult
}
