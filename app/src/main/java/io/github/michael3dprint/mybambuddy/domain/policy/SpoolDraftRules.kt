package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.SpoolDraft

/**
 * Checks a new-spool form against the limits Bambuddy enforces (Spoolman route, v1.3.0), so a bad value is explained
 * on the phone instead of coming back as a bare server refusal. Pure.
 */
object SpoolDraftRules {
    /** Longest material name the server accepts. */
    const val MAX_MATERIAL = 64

    /** Heaviest net weight the server accepts, in grams. */
    const val MAX_WEIGHT_GRAMS = 100_000
    private val HEX = Regex("^[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$")

    /** The first thing wrong with [draft], or null when it can be saved. */
    fun problem(draft: SpoolDraft): String? = when {
        draft.material.isBlank() -> "Enter a material, for example PLA."
        draft.material.trim().length > MAX_MATERIAL -> "Material is too long (max $MAX_MATERIAL characters)."
        draft.colorHex.isNotBlank() && !HEX.matches(draft.colorHex.trim().removePrefix("#")) ->
            "Colour must be 6 hex digits (like FF8800) or empty."
        draft.labelWeightGrams !in 1..MAX_WEIGHT_GRAMS -> "Net weight must be 1 to $MAX_WEIGHT_GRAMS g."
        draft.coreWeightGrams?.let { it !in 0..10_000 } == true -> "Spool weight must be 0 to 10000 g."
        else -> null
    }
}
