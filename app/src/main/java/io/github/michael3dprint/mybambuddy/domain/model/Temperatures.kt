package io.github.michael3dprint.mybambuddy.domain.model

/**
 * Nozzle, bed and chamber temperatures in degrees Celsius, with the nozzle and bed targets. Null means the printer did not report
 * it.
 */
data class Temperatures(
    val nozzle: Float? = null,
    val bed: Float? = null,
    val chamber: Float? = null,
    val nozzleTarget: Float? = null,
    val bedTarget: Float? = null,
) {
    val formattedNozzle: String
        get() = nozzle?.let { "${it.toInt()}°C" } ?: "--°C"

    val formattedBed: String
        get() = bed?.let { "${it.toInt()}°C" } ?: "--°C"

    val formattedChamber: String
        get() = chamber?.let { "${it.toInt()}°C" } ?: "--°C"
}
