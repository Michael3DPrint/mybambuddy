package io.github.michael3dprint.mybambuddy.domain.barcode

/** The user's standing answer to "may I look this barcode up online?". */
enum class OnlineLookupConsent { ASK, ALWAYS, NEVER }

/** Stores whether the user allowed barcode lookups on the internet. */
interface BarcodePrefs {
    /** The user's saved answer to whether a barcode may be looked up online. */
    var onlineLookup: OnlineLookupConsent
}
