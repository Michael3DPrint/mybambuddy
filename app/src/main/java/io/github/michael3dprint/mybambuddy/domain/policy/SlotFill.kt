package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.AmsTray
import io.github.michael3dprint.mybambuddy.domain.model.Spool

/** How full a slot looks: [fraction] 0 to 1 for the bar and the estimated [grams] left. */
data class SlotFill(val fraction: Float, val grams: Int) {
    /** The fill as a whole percent, for the low-stock rule. */
    val percent: Int get() = (fraction * PERCENT).toInt()
}

/** The estimate behind the slot bar and its grams (design 32). Pure, so it is tested without a screen. */
object SlotFillRule {
    /** The common Bambu spool size, used only when the slot has no linked spool and the printer reports just a percent. */
    const val ASSUMED_SPOOL_GRAMS = 1000

    /**
     * A Bambu spool read by the AMS (RFID) reports its own percent, which is measured, so that wins; it is applied to the linked spool's
     * label weight when there is one, otherwise to [ASSUMED_SPOOL_GRAMS]. A spool without a tag (or with no percent) uses the linked
     * inventory spool's remaining grams over its label weight. With neither, or for an empty slot, there is no estimate (null).
     */
    fun estimate(tray: AmsTray, linkedSpool: Spool?): SlotFill? {
        if (tray.isEmpty) return null
        val linked = linkedSpool?.takeIf { it.id == tray.loadedSpoolId && it.labelWeightGrams > 0 }
        val percent = tray.remainPercent?.coerceIn(0, PERCENT)
        if (tray.hasRfidTag && percent != null) {
            return SlotFill(percent / PERCENT.toFloat(), (linked?.labelWeightGrams ?: ASSUMED_SPOOL_GRAMS) * percent / PERCENT)
        }
        if (linked != null) {
            return SlotFill((linked.remainingGrams.toFloat() / linked.labelWeightGrams).coerceIn(0f, 1f), linked.remainingGrams)
        }
        return percent?.let { SlotFill(it / PERCENT.toFloat(), ASSUMED_SPOOL_GRAMS * it / PERCENT) }
    }
}

/** One colour stop of the slot bar: how far along the bar (0 to 1) and how opaque (0 to 1) it is there. */
data class BarStop(val position: Float, val alpha: Float)

/**
 * The shading of the slot bar for a fill of [fraction] (null means unknown). The bar keeps its full length: solid up to the fraction, a fade
 * to [MIN_BAR_ALPHA] over the next [FADE_WIDTH] of the bar, then faint to the end. Unknown is faint all along.
 */
fun barStops(fraction: Float?): List<BarStop> {
    if (fraction == null) return listOf(BarStop(0f, MIN_BAR_ALPHA), BarStop(1f, MIN_BAR_ALPHA))
    val f = fraction.coerceIn(0f, 1f)
    return listOf(BarStop(0f, 1f), BarStop(f, 1f), BarStop((f + FADE_WIDTH).coerceAtMost(1f), MIN_BAR_ALPHA), BarStop(1f, MIN_BAR_ALPHA))
}

private const val MIN_BAR_ALPHA = 0.1f
private const val FADE_WIDTH = 0.08f
private const val PERCENT = 100
