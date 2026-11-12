package io.github.michael3dprint.mybambuddy.domain.mapper

import io.github.michael3dprint.mybambuddy.data.api.dto.HmsErrorDto
import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterDto
import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterStatusDto
import io.github.michael3dprint.mybambuddy.data.api.dto.TemperaturesDto
import io.github.michael3dprint.mybambuddy.data.mapper.toCapabilities
import io.github.michael3dprint.mybambuddy.domain.model.HmsStatus
import io.github.michael3dprint.mybambuddy.domain.model.PrintStatus
import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.domain.model.PrinterCapabilities
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import io.github.michael3dprint.mybambuddy.domain.model.Temperatures
import io.github.michael3dprint.mybambuddy.domain.util.calculateEstimatedFinishTime
import io.github.michael3dprint.mybambuddy.domain.util.formatRemainingTime
import io.github.michael3dprint.mybambuddy.util.PrintDeckLogger

/** Maps one HMS error; a null input gives an empty [HmsStatus]. */
fun mapToHmsStatus(dto: HmsErrorDto?): HmsStatus {
    if (dto == null) return HmsStatus()
    return HmsStatus(
        code = dto.code ?: "",
        description = dto.description ?: "",
        severity = dto.severity ?: "",
        attr = dto.attr,
        module = dto.module,
        fullCode = dto.fullCode.orEmpty(),
        actions = dto.actions.orEmpty(),
    )
}

/** Maps the temperatures; a null input gives all-null temperatures. */
fun mapToTemperatures(dto: TemperaturesDto?): Temperatures {
    if (dto == null) return Temperatures()
    return Temperatures(
        nozzle = dto.nozzle,
        bed = dto.bed,
        chamber = dto.chamber,
        nozzleTarget = dto.nozzleTarget,
        bedTarget = dto.bedTarget,
    )
}

/**
 * Maps a status reply. A printer that is not connected becomes OFFLINE, progress is clamped to 0..100, and a null input gives the
 * default status.
 */
fun mapToPrintStatus(statusDto: PrinterStatusDto?): PrintStatus {
    if (statusDto == null) return PrintStatus()
    return try {
        val state = if (statusDto.connected == false) {
            PrinterState.OFFLINE
        } else {
            PrinterState.fromString(statusDto.effectiveState ?: statusDto.state)
        }
        val rawProgress = statusDto.progress ?: 0f
        val progress = rawProgress.toInt().coerceIn(0, 100)
        val remainingTimeSec = statusDto.effectiveRemainingTimeSeconds ?: 0L
        val currentLayer = statusDto.effectiveCurrentLayer ?: 0
        val totalLayers = statusDto.totalLayers ?: 0
        val temps = mapToTemperatures(statusDto.temperatures)
        val hmsList = statusDto.allHmsErrors.map { mapToHmsStatus(it) }

        PrintStatus(
            state = state,
            progress = progress,
            remainingTimeSeconds = remainingTimeSec,
            remainingTimeFormatted = if (remainingTimeSec > 0) formatRemainingTime(remainingTimeSec) else "0m left",
            estimatedFinishTime = if (remainingTimeSec > 0) calculateEstimatedFinishTime(remainingTimeSec) else "--",
            currentLayer = currentLayer,
            totalLayers = totalLayers,
            temperatures = temps,
            hmsErrors = hmsList,
            printName = statusDto.effectivePrintName,
        )
    } catch (e: Exception) {
        PrintDeckLogger.e("PrintDeckTrace", "Failed to map PrinterStatusDto to PrintStatus, applying fallbacks", e)
        PrintStatus()
    }
}

/**
 * Combines a printer row and its status into a [Printer]. Missing fields get safe fallbacks (for example "Unknown Printer") rather
 * than failing.
 */
fun mapToPrinter(printerDto: PrinterDto, statusDto: PrinterStatusDto? = null, baseUrl: String = ""): Printer = try {
    val id = printerDto.id ?: ""
    val name = printerDto.name ?: if (id.isNotEmpty()) "Printer $id" else "Unknown Printer"
    val model = printerDto.model ?: "Bambu Printer"

    val rawState = statusDto?.effectiveState
        ?: statusDto?.state
        ?: printerDto.state
        ?: printerDto.status?.effectiveState
        ?: printerDto.status?.state
    val state = if (statusDto?.connected == false) PrinterState.OFFLINE else PrinterState.fromString(rawState)

    val rawProgress = statusDto?.progress
        ?: printerDto.progress
        ?: printerDto.status?.progress
        ?: 0f
    val progress = rawProgress.toInt().coerceIn(0, 100)

    val remainingTimeSec = statusDto?.effectiveRemainingTimeSeconds
        ?: printerDto.effectiveRemainingTimeSeconds
        ?: printerDto.status?.effectiveRemainingTimeSeconds
        ?: 0L

    val currentLayer = statusDto?.effectiveCurrentLayer
        ?: printerDto.currentLayer
        ?: printerDto.status?.effectiveCurrentLayer
        ?: 0

    val totalLayers = statusDto?.totalLayers
        ?: printerDto.totalLayers
        ?: printerDto.status?.totalLayers
        ?: 0

    val rawHmsDtos = when {
        statusDto != null && statusDto.allHmsErrors.isNotEmpty() -> statusDto.allHmsErrors
        printerDto.hmsErrors != null -> printerDto.hmsErrors
        printerDto.hmsStatus != null -> printerDto.hmsStatus
        printerDto.status != null -> printerDto.status.allHmsErrors
        else -> emptyList()
    }
    val hmsDomainList = rawHmsDtos.map { mapToHmsStatus(it) }
    val hmsCount = hmsDomainList.size
    val hasHmsWarning = hmsCount > 0
    val hmsMessages = hmsDomainList.map { it.displayMessage }

    val rawSnapshotUrl = printerDto.snapshotUrl
        ?: printerDto.cameraUrl
        ?: if (id.isNotEmpty()) "http://localhost/printers/$id/camera/snapshot" else ""

    val printName = statusDto?.effectivePrintName ?: printerDto.effectivePrintName

    val nozzleTemp = statusDto?.temperatures?.nozzle ?: printerDto.temperatures?.nozzle
    val bedTemp = statusDto?.temperatures?.bed ?: printerDto.temperatures?.bed
    val chamberTemp = statusDto?.temperatures?.chamber ?: printerDto.temperatures?.chamber
    val temperatures = Temperatures(
        nozzle = nozzleTemp,
        bed = bedTemp,
        chamber = chamberTemp,
        nozzleTarget = statusDto?.temperatures?.nozzleTarget ?: printerDto.temperatures?.nozzleTarget,
        bedTarget = statusDto?.temperatures?.bedTarget ?: printerDto.temperatures?.bedTarget,
    )

    Printer(
        id = id,
        name = name,
        model = model,
        state = state,
        progress = progress,
        remainingTimeSeconds = remainingTimeSec,
        remainingTimeFormatted = if (remainingTimeSec > 0) formatRemainingTime(remainingTimeSec) else "0m left",
        estimatedFinishTime = if (remainingTimeSec > 0) calculateEstimatedFinishTime(remainingTimeSec) else "--",
        currentLayer = currentLayer,
        totalLayers = totalLayers,
        hasHmsWarning = hasHmsWarning,
        hmsCount = hmsCount,
        hmsMessages = hmsMessages,
        hmsErrors = hmsDomainList,
        snapshotUrl = rawSnapshotUrl,
        printName = printName,
        temperatures = temperatures,
        speedLevel = statusDto?.speedLevel,
        awaitingPlateClear = statusDto?.awaitingPlateClear ?: false,
        chamberLight = statusDto?.chamberLight ?: false,
        capabilities = statusDto?.toCapabilities() ?: PrinterCapabilities.UNKNOWN,
    )
} catch (e: Exception) {
    PrintDeckLogger.e("PrintDeckTrace", "Failed to map PrinterDto to Printer domain model, applying fallbacks", e)
    Printer(
        id = printerDto.id ?: "unknown",
        name = printerDto.name ?: "Unknown Printer",
        model = printerDto.model ?: "Bambu Printer",
        state = PrinterState.UNKNOWN,
    )
}
