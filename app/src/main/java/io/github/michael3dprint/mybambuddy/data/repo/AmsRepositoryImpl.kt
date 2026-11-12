package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.AmsApi
import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.PrinterApi
import io.github.michael3dprint.mybambuddy.data.api.discardBody
import io.github.michael3dprint.mybambuddy.data.api.dto.AmsTrayDto
import io.github.michael3dprint.mybambuddy.data.api.dto.AmsUnitDto
import io.github.michael3dprint.mybambuddy.data.api.dto.InventoryAssignRequest
import io.github.michael3dprint.mybambuddy.data.api.dto.SlotAssignResultDto
import io.github.michael3dprint.mybambuddy.data.api.dto.SpoolSlotAssignRequest
import io.github.michael3dprint.mybambuddy.data.api.safeApiCall
import io.github.michael3dprint.mybambuddy.data.api.safeUnitCall
import io.github.michael3dprint.mybambuddy.data.mapper.isRfidTag
import io.github.michael3dprint.mybambuddy.data.mapper.toCapabilities
import io.github.michael3dprint.mybambuddy.domain.model.AmsTray
import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.domain.model.DryPhase
import io.github.michael3dprint.mybambuddy.domain.model.PrinterCapabilities
import io.github.michael3dprint.mybambuddy.domain.model.PrinterTool
import io.github.michael3dprint.mybambuddy.domain.model.SlotLoadResult
import io.github.michael3dprint.mybambuddy.domain.nfc.TagText
import io.github.michael3dprint.mybambuddy.domain.policy.SlotLoadPolicy
import io.github.michael3dprint.mybambuddy.domain.repo.AmsRepository
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterToolGate

/**
 * AMS overview, slot assignment and drying over Bambuddy. Assignments are refused before any request when the preflight finds a
 * blocker ([SlotLoadPolicy.preflight]); drying goes through the [gate]. Control calls are single attempts, never retried.
 */
class AmsRepositoryImpl(
    private val printerApi: PrinterApi,
    private val api: AmsApi,
    private val mode: InventoryModeProvider,
    private val gate: PrinterToolGate,
) : AmsRepository {

    private data class Slot(val printerId: Int, val amsId: Int, val trayId: Int)

    override suspend fun getAms(): ApiResult<List<AmsUnit>> {
        val printers = when (val r = safeApiCall { printerApi.getPrinters() }) {
            is ApiResult.Success -> r.data.effectivePrinters
            is ApiResult.Error -> return r
        }
        val loaded = loadedSpools()
        val units = mutableListOf<AmsUnit>()
        for (printer in printers) {
            val id = printer.id ?: continue
            val printerId = id.toIntOrNull() ?: continue
            val status = (safeApiCall { api.getPrinterAmsStatus(id) } as? ApiResult.Success)?.data ?: continue
            val supportsDrying = status.supportsDrying == true
            status.ams.orEmpty().forEach { unit ->
                units += unit.toDomain(printerId, printer.name ?: "Printer $printerId", supportsDrying, loaded, status.toCapabilities())
            }
        }
        return ApiResult.Success(units)
    }

    override suspend fun getTray(printerId: Int, amsId: Int, trayId: Int): ApiResult<AmsTray?> {
        val status = when (val r = safeApiCall { api.getPrinterAmsStatus(printerId.toString()) }) {
            is ApiResult.Success -> r.data
            is ApiResult.Error -> return r
        }
        val tray = status.ams.orEmpty().firstOrNull { (it.id ?: 0) == amsId }?.tray.orEmpty()
            .firstNotNullOfOrNull { dto -> dto.toDomain { null }?.takeIf { it.trayId == trayId } }
        return ApiResult.Success(tray)
    }

    private suspend fun loadedSpools(): Map<Slot, Int> {
        val useSpoolman = mode.current().useSpoolman
        val rows = (
            safeApiCall { if (useSpoolman) api.getSpoolmanSlotAssignments() else api.getInventoryAssignments() }
                as? ApiResult.Success
            )?.data.orEmpty()
        return rows.mapNotNull { row ->
            val spool = row.spoolmanSpoolId ?: row.spoolId ?: return@mapNotNull null
            Slot(row.printerId ?: return@mapNotNull null, row.amsId ?: return@mapNotNull null, row.trayId ?: return@mapNotNull null) to
                spool
        }.toMap()
    }

    private fun AmsUnitDto.toDomain(
        printerId: Int,
        printerName: String,
        supportsDrying: Boolean,
        loaded: Map<Slot, Int>,
        capabilities: PrinterCapabilities,
    ): AmsUnit {
        val amsId = id ?: 0
        return AmsUnit(
            printerId = printerId,
            printerName = printerName,
            amsId = amsId,
            isHt = isAmsHt == true,
            humidityLevel = humidity,
            tempC = temp,
            supportsDrying = supportsDrying,
            dryPhase = when (dryStatus) {
                1 -> DryPhase.CHECKING
                2 -> DryPhase.DRYING
                3 -> DryPhase.COOLING
                4 -> DryPhase.STOPPING
                5 -> DryPhase.ERROR
                else -> DryPhase.OFF
            },
            dryMinutesLeft = dryTime ?: 0,
            dryTargetTemp = dryTargetTemp,
            trays = tray.orEmpty().mapNotNull { t -> t.toDomain { id -> loaded[Slot(printerId, amsId, id)] } },
            capabilities = capabilities,
        )
    }

    private fun AmsTrayDto.toDomain(loadedSpoolFor: (Int) -> Int?): AmsTray? {
        val trayId = id ?: return null
        return AmsTray(
            trayId = trayId,
            // Tray text comes from the printer and ends up in messages: strip control characters and cap the length.
            material = TagText.sanitize(trayType.orEmpty(), MAX_MATERIAL_LENGTH),
            subBrand = TagText.sanitize(subBrands.orEmpty(), MAX_SUB_BRAND_LENGTH),
            colorHex = color.orEmpty(),
            remainPercent = remain?.takeIf { it in 0..100 },
            loadedSpoolId = loadedSpoolFor(trayId),
            hasRfidTag = isRfidTag(tagUid, trayUuid),
            trayUuid = trayUuid.orEmpty().filter { it.isLetterOrDigit() }.uppercase(),
        )
    }

    override suspend fun loadSpool(spoolId: Int, printerId: Int, amsId: Int, trayId: Int, printerName: String): ApiResult<SlotLoadResult> {
        // The name comes from the server and ends up in a message: strip control characters and cap the length.
        val name = TagText.sanitize(printerName, MAX_PRINTER_NAME_LENGTH).ifBlank { AmsRepository.GENERIC_PRINTER_NAME }
        SlotLoadPolicy.validate(spoolId, printerId, amsId, trayId)?.let { return refused(it) }
        // A fresh read right before the write, so cached or stale data cannot let a change through.
        val status = (safeApiCall { api.getPrinterAmsStatus(printerId.toString()) } as? ApiResult.Success)?.data
            ?: return refused("Can't check $name right now, so the change is not allowed.")
        val tray = status.ams.orEmpty().firstOrNull { (it.id ?: 0) == amsId }?.tray.orEmpty()
            .firstNotNullOfOrNull { dto -> dto.toDomain { null }?.takeIf { it.trayId == trayId } }
            ?: return refused("That slot was not found on the printer.")
        SlotLoadPolicy.preflight(status.toCapabilities(), tray, name).firstOrNull()
            ?.let { return refused(SlotLoadPolicy.message(it, name)) }

        val useSpoolman = mode.current().useSpoolman
        if (useSpoolman) {
            return when (val r = safeUnitCall { api.assignSpoolmanSlot(SpoolSlotAssignRequest(spoolId, printerId, amsId, trayId)) }) {
                is ApiResult.Success -> ApiResult.Success(SlotLoadResult(pendingConfig = null))
                is ApiResult.Error -> r
            }
        }
        var pending: Boolean? = null
        val r = safeUnitCall {
            val response = api.assignInventorySlot(InventoryAssignRequest(spoolId, printerId, amsId, trayId))
            // The assignment is already saved; a reply that cannot be read just means we do not know whether it is pending.
            pending = try {
                response.body()?.string()?.let { SlotAssignResultDto.parseOrNull(it) }?.pendingConfig
            } catch (_: java.io.IOException) {
                null
            }
            response.discardBody()
        }
        return when (r) {
            is ApiResult.Success -> ApiResult.Success(SlotLoadResult(pendingConfig = pending))
            is ApiResult.Error -> r
        }
    }

    override suspend fun unloadSpool(spoolId: Int, printerId: Int, amsId: Int, trayId: Int): ApiResult<Unit> {
        val useSpoolman = mode.current().useSpoolman
        return safeUnitCall {
            if (useSpoolman) api.unassignSpoolmanSlot(spoolId) else api.unassignInventorySlot(printerId, amsId, trayId)
        }
    }

    override suspend fun startDrying(printerId: Int, amsId: Int, tempC: Int, hours: Int, filament: String): ApiResult<Unit> {
        // The server enforces the same limits; checking here avoids a pointless request.
        if (tempC !in 45..85) return refused("Temperature must be 45-85 °C")
        if (hours !in 1..24) return refused("Duration must be 1-24 hours")
        gate.refusal(printerId.toString(), PrinterTool.DRYING)?.let { return refused(it) }
        return safeUnitCall { api.startDrying(printerId.toString(), amsId, tempC, hours, filament) }
    }

    override suspend fun stopDrying(printerId: Int, amsId: Int): ApiResult<Unit> {
        gate.refusal(printerId.toString(), PrinterTool.DRYING)?.let { return refused(it) }
        return safeUnitCall { api.stopDrying(printerId.toString(), amsId) }
    }

    /** A client-side refusal: nothing was sent to change anything. */
    private fun refused(msg: String) = ApiResult.Error(message = msg, errorType = ApiErrorType.UNSUPPORTED)

    private companion object {
        /** The status payload carries no printer name, so the late check words its messages for "the printer". */
        const val MAX_PRINTER_NAME_LENGTH = 40
        const val MAX_MATERIAL_LENGTH = 16
        const val MAX_SUB_BRAND_LENGTH = 40
    }
}
