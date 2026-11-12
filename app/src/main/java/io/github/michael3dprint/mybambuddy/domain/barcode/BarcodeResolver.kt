package io.github.michael3dprint.mybambuddy.domain.barcode

import io.github.michael3dprint.mybambuddy.domain.model.SpoolDraft

/** What the scan screen should do next. */
sealed interface BarcodeOutcome {
    data class Draft(val draft: SpoolDraft) : BarcodeOutcome

    /** Put this text in the catalog search box. */
    data class Search(val query: String) : BarcodeOutcome

    /** Ask the user before sending [ean] to the online service. */
    data class AskOnlineLookup(val ean: String) : BarcodeOutcome

    data class NotFound(val message: String) : BarcodeOutcome
}

/**
 * The lookup chain: structured code -> (with consent) online product lookup -> plain search text.
 * Single responsibility: decide the outcome; the screen decides how to show it.
 */
class BarcodeResolver(private val lookup: EanLookup, private val prefs: BarcodePrefs) {
    /**
     * Decides the next step for a scanned code: structured data becomes a spool draft, an EAN is looked up online only with the
     * user's consent (asking when they have not answered), and other text becomes a catalogue search.
     */
    suspend fun resolve(raw: String): BarcodeOutcome = when (val parsed = BarcodeParser.parse(raw)) {
        is ParsedBarcode.Structured -> BarcodeOutcome.Draft(parsed.draft)
        is ParsedBarcode.Text ->
            if (parsed.text.isBlank()) {
                BarcodeOutcome.NotFound(
                    "The code was empty.",
                )
            } else {
                BarcodeOutcome.Search(parsed.text.take(SEARCH_MAX))
            }
        is ParsedBarcode.Ean -> when (prefs.onlineLookup) {
            OnlineLookupConsent.ASK -> BarcodeOutcome.AskOnlineLookup(parsed.digits)
            OnlineLookupConsent.ALWAYS -> resolveOnline(parsed.digits)
            OnlineLookupConsent.NEVER -> BarcodeOutcome.NotFound(
                "Barcode ${parsed.digits} isn't known locally. Search the catalog by name instead.",
            )
        }
    }

    /** Called after the user answered the consent question (or directly when consent is ALWAYS). */
    suspend fun resolveOnline(ean: String): BarcodeOutcome = when (val result = lookup.lookup(ean)) {
        is EanLookupResult.Found -> BarcodeOutcome.Search(cleanQuery(result.title))
        EanLookupResult.NotFound -> BarcodeOutcome.NotFound("No product found for barcode $ean. Search the catalog by name instead.")
        is EanLookupResult.Unavailable -> BarcodeOutcome.NotFound(
            "Online lookup isn't available right now (${result.reason}). Search by name instead.",
        )
    }

    /** Saves the user's answer to the online-lookup question. */
    fun remember(consent: OnlineLookupConsent) {
        prefs.onlineLookup = consent
    }

    /** Keeps letters, digits and spaces so a product title becomes a safe catalog search phrase. */
    internal fun cleanQuery(title: String): String =
        title.filter { it.isLetterOrDigit() || it == ' ' }.split(' ').filter { it.isNotBlank() }.take(8).joinToString(" ").take(SEARCH_MAX)

    private companion object {
        const val SEARCH_MAX = 80
    }
}
