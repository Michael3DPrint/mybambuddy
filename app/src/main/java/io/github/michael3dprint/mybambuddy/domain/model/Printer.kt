package io.github.michael3dprint.mybambuddy.domain.model

/** A printer with its live status and, in [capabilities], what Bambuddy says it can currently do. */
data class Printer(
    val id: String,
    val name: String,
    val model: String,
    val state: PrinterState = PrinterState.UNKNOWN,
    val progress: Int = 0,
    val remainingTimeSeconds: Long = 0,
    val remainingTimeFormatted: String = "0m left",
    val estimatedFinishTime: String = "--",
    val currentLayer: Int = 0,
    val totalLayers: Int = 0,
    val hasHmsWarning: Boolean = false,
    val hmsCount: Int = 0,
    val hmsMessages: List<String> = emptyList(),
    val hmsErrors: List<HmsStatus> = emptyList(),
    val snapshotUrl: String = "",
    val printName: String? = null,
    val temperatures: Temperatures = Temperatures(),
    val speedLevel: Int? = null,
    val awaitingPlateClear: Boolean = false,
    val chamberLight: Boolean = false,
    val capabilities: PrinterCapabilities = PrinterCapabilities.UNKNOWN,
) {
    val nozzleTemp: Float? get() = temperatures.nozzle
    val bedTemp: Float? get() = temperatures.bed
    val chamberTemp: Float? get() = temperatures.chamber

    val layerFormatted: String
        get() = if (totalLayers > 0 || currentLayer > 0) "Layer $currentLayer of $totalLayers" else "--"
}
