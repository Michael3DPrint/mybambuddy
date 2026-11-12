package io.github.michael3dprint.mybambuddy.domain.policy

/**
 * One definition of "running low" shared by the spool list, the AMS slots and the alerts, so they can never disagree.
 * An unknown amount is never reported as low.
 */
object LowStockPolicy {
    /** The low-stock threshold, in percent, for a spool that has none of its own. */
    const val DEFAULT_THRESHOLD_PCT = 20

    /** Extra room above the threshold before an alert may fire again, so a value hovering at the line doesn't flap. */
    const val REARM_MARGIN_PCT = 5

    /**
     * True when the remaining share is at or below the threshold. An unknown or negative amount is never low. The threshold is kept
     * within 1 to 100.
     */
    fun isLow(remainingPercent: Int?, thresholdPct: Int? = null): Boolean {
        if (remainingPercent == null || remainingPercent < 0) return false
        val threshold = (thresholdPct ?: DEFAULT_THRESHOLD_PCT).coerceIn(1, 100)
        return remainingPercent <= threshold
    }

    /** True when the remaining share is above the threshold by more than [REARM_MARGIN_PCT], so an alert may fire again. */
    fun isRecovered(remainingPercent: Int?, thresholdPct: Int? = null): Boolean {
        if (remainingPercent == null) return false
        val threshold = (thresholdPct ?: DEFAULT_THRESHOLD_PCT).coerceIn(1, 100)
        return remainingPercent > threshold + REARM_MARGIN_PCT
    }
}
