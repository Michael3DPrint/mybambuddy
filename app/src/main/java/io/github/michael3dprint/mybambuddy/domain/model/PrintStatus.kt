package io.github.michael3dprint.mybambuddy.domain.model

/**
 * The live state of a printer's job: state, progress, time left, layers, temperatures and HMS errors. The defaults describe an
 * unknown printer.
 */
data class PrintStatus(
    val state: PrinterState = PrinterState.UNKNOWN,
    val progress: Int = 0,
    val remainingTimeSeconds: Long = 0,
    val remainingTimeFormatted: String = "0m left",
    val estimatedFinishTime: String = "--",
    val currentLayer: Int = 0,
    val totalLayers: Int = 0,
    val temperatures: Temperatures = Temperatures(),
    val hmsErrors: List<HmsStatus> = emptyList(),
    val printName: String? = null,
) {
    val hasHmsWarning: Boolean get() = hmsErrors.isNotEmpty()
    val hmsCount: Int get() = hmsErrors.size
    val layerFormatted: String
        get() = if (totalLayers > 0 || currentLayer > 0) "Layer $currentLayer of $totalLayers" else "--"
}
