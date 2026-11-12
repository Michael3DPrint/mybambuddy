package io.github.michael3dprint.mybambuddy.domain.barcode

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.github.michael3dprint.mybambuddy.domain.model.SpoolDraft
import io.github.michael3dprint.mybambuddy.domain.nfc.TagText

/** What a scanned code turned out to be. */
sealed interface ParsedBarcode {
    /** A machine-readable spool description (JSON such as OpenSpool). */
    data class Structured(val draft: SpoolDraft) : ParsedBarcode

    /** A product barcode (EAN-8/UPC/EAN-13/GTIN-14): digits only. */
    data class Ean(val digits: String) : ParsedBarcode

    /** Anything else; shown or used as a search phrase, never executed or opened. */
    data class Text(val text: String) : ParsedBarcode
}

/**
 * Turns the raw string from a barcode/QR scanner into something the app understands.
 * The input is untrusted: it is stripped of control characters and capped at [MAX_LENGTH] before any parsing,
 * JSON is parsed with Moshi into a plain map (no reflection into app classes), and nothing is ever opened or executed.
 */
object BarcodeParser {
    /** Longest scanned text read; anything longer is cut off. */
    const val MAX_LENGTH = 256
    private val EAN = Regex("^[0-9]{8,14}$")
    private val moshi = Moshi.Builder().build()
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java),
    )

    /** Cleans the scanned text and classifies it as an EAN or UPC number, structured JSON, or plain text. */
    fun parse(raw: String): ParsedBarcode {
        val text = TagText.sanitize(raw, MAX_LENGTH)
        if (EAN.matches(text)) return ParsedBarcode.Ean(text)
        if (text.startsWith("{")) structured(text)?.let { return ParsedBarcode.Structured(it) }
        return ParsedBarcode.Text(text)
    }

    private fun structured(json: String): SpoolDraft? {
        val map = try {
            mapAdapter.fromJson(json)
        } catch (_: Exception) {
            null
        } ?: return null
        fun field(vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
            (map[key] as? String)?.let { TagText.sanitize(it, 64) }?.takeIf { it.isNotBlank() }
        }.orEmpty()
        val material = field("material", "type")
        if (material.isBlank()) return null
        val weight = (map["weight"] as? Number)?.toInt()?.coerceIn(1, 100_000) ?: 1000
        return SpoolDraft(
            brand = field("brand", "manufacturer", "vendor"),
            material = material,
            subtype = field("subtype", "modifiers"),
            colorName = field("color_name", "colour", "color"),
            colorHex = field("color_hex", "rgba", "hex").removePrefix("#").filter { it.isLetterOrDigit() }.take(8),
            labelWeightGrams = weight,
        )
    }
}
