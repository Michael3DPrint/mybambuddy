package io.github.michael3dprint.mybambuddy.domain.policy

/** The result of checking a typed scale reading. */
sealed interface WeighInput {
    /** A usable reading of [grams]; [warning] is shown but does not block saving. */
    data class Valid(val grams: Double, val warning: String? = null) : WeighInput

    /** Not usable; [reason] is shown under the field. */
    data class Invalid(val reason: String) : WeighInput
}

/** Checks the scale reading the user types when weighing a spool. Pure, so it is unit tested. */
object SpoolWeighRules {
    /** The server's own upper limit for a reading. */
    const val MAX_GRAMS = 100_000.0

    /** How far above the label weight a reading can be before the user is asked to double-check (spool and holder weight). */
    const val WARN_MARGIN_GRAMS = 500

    /** Checks [text]: a comma counts as the decimal mark; the number must be finite and from 0 to [MAX_GRAMS]. */
    fun parse(text: String, labelWeightGrams: Int): WeighInput {
        val value = text.trim().replace(',', '.').toDoubleOrNull()
        return when {
            value == null || !value.isFinite() -> WeighInput.Invalid("Enter the weight in grams, for example 812")
            value < 0 || value > MAX_GRAMS -> WeighInput.Invalid("Enter a weight from 0 to 100000 g")
            labelWeightGrams > 0 && value > labelWeightGrams + WARN_MARGIN_GRAMS ->
                WeighInput.Valid(value, "That is well above the label weight of $labelWeightGrams g. Check the scale reading.")
            else -> WeighInput.Valid(value)
        }
    }
}
