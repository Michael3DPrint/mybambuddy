package io.github.michael3dprint.mybambuddy.data.api

import com.squareup.moshi.Moshi

/**
 * Pulls a short human reason out of a Bambuddy error body. The server uses three shapes for `detail`:
 * a string, a list of `{loc, msg}` (FastAPI validation, HTTP 422), or an object with a `message` (Spoolman refusals, HTTP 502).
 * Returns null when nothing readable is found, so callers fall back to their own text.
 */
internal object ServerDetail {
    private const val MAX = 300
    private val adapter = Moshi.Builder().build().adapter(Any::class.java).lenient()

    /** The readable reason in an error body, or null when there is none. */
    fun from(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val detail = (runCatching { adapter.fromJson(raw) }.getOrNull() as? Map<*, *>)?.get("detail") ?: return null
        val text = when (detail) {
            is String -> detail
            is List<*> -> detail.mapNotNull { fromValidationItem(it) }.joinToString("; ")
            is Map<*, *> -> fromObject(detail)
            else -> null
        }
        return text?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX)
    }

    private fun fromValidationItem(item: Any?): String? {
        val map = item as? Map<*, *> ?: return null
        val msg = map["msg"] as? String ?: return null
        val field = (map["loc"] as? List<*>)?.lastOrNull()?.toString()?.takeIf { it != "body" }
        return if (field == null) msg else "$field: $msg"
    }

    /** Bambuddy's machine-readable reason (`detail.code`) when the error body has one, else null. */
    fun codeOf(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val detail = (runCatching { adapter.fromJson(raw) }.getOrNull() as? Map<*, *>)?.get("detail") as? Map<*, *>
        return (detail?.get("code") as? String)?.takeIf { it.isNotBlank() }
    }

    private fun fromObject(map: Map<*, *>): String? {
        when (map["code"]) {
            "insufficient_filament" -> return shortfallText(map["deficit"])
            "unmatched_filament" -> return unmatchedText(map["missing"])
        }
        val message = map["message"] as? String ?: return (map["code"] as? String)?.let { "Bambuddy refused this: $it." }
        val upstream = (map["upstream_body"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        return if (upstream == null) message else "$message: $upstream"
    }

    /** "Not enough filament: filament 1 (PLA) needs about 31 g, the slot has about 12 g." for each short slot. */
    private fun shortfallText(deficit: Any?): String {
        val lines = (deficit as? List<*>).orEmpty().mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val type = (m["filament_type"] as? String)?.takeIf { it.isNotBlank() }
            val need = (m["required_grams"] as? Number)?.let { "about ${Math.round(it.toDouble())} g" }
            val have =
                (m["remaining_grams"] as? Number)?.let { "about ${Math.round(it.toDouble())} g" } ?: "an amount that could not be read"
            val slot = (m["slot_id"] as? Number)?.toInt()?.let { "filament $it" } ?: "a filament"
            "$slot${type?.let { " ($it)" }.orEmpty()} needs ${need ?: "more"}, the slot has $have"
        }
        return if (lines.isEmpty()) "Not enough filament for this job." else "Not enough filament: " + lines.joinToString("; ") + "."
    }

    /** "No loaded slot matches: PLA Basic Black." for each filament the printer has no tray for. */
    private fun unmatchedText(missing: Any?): String {
        val names = (missing as? List<*>).orEmpty().mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            listOf("brand", "material", "subtype", "color_name").mapNotNull { (m[it] as? String)?.takeIf { v -> v.isNotBlank() } }
                .joinToString(" ").takeIf { it.isNotBlank() }
        }
        return if (names.isEmpty()) {
            "No loaded slot matches a filament this job needs."
        } else {
            "No loaded slot matches: " +
                names.joinToString("; ") +
                "."
        }
    }
}
