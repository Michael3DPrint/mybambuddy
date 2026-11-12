package io.github.michael3dprint.mybambuddy.domain.model

/** One bar: a printer or material and how many prints it had. */
data class StatBar(val label: String, val count: Int)

/** Print statistics for a period, as Bambuddy counts them (one per print event, so a reprint counts again). */
data class PrintStats(
    val totalPrints: Int,
    val successfulPrints: Int,
    val failedPrints: Int,
    val cancelledPrints: Int,
    val totalHours: Double,
    val totalGrams: Double,
    val byPrinter: List<StatBar>,
    val byMaterial: List<StatBar>,
) {
    /** Share of finished prints that succeeded, as Bambuddy's own gauge counts it (cancelled prints excluded); null with nothing finished. */
    val successPercent: Int?
        get() {
            val finished = successfulPrints + failedPrints
            return if (finished > 0) successfulPrints * 100 / finished else null
        }
}
