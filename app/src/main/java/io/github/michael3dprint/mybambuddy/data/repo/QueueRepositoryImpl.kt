package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.QueueApi
import io.github.michael3dprint.mybambuddy.data.api.dto.QueueItemDto
import io.github.michael3dprint.mybambuddy.data.api.dto.QueueReorderBody
import io.github.michael3dprint.mybambuddy.data.api.dto.QueueReorderItem
import io.github.michael3dprint.mybambuddy.data.api.dto.QueueUpdateBody
import io.github.michael3dprint.mybambuddy.data.api.safeApiCall
import io.github.michael3dprint.mybambuddy.data.api.safeUnitCall
import io.github.michael3dprint.mybambuddy.domain.model.JobOptions
import io.github.michael3dprint.mybambuddy.domain.model.PrinterTool
import io.github.michael3dprint.mybambuddy.domain.model.QueueItem
import io.github.michael3dprint.mybambuddy.domain.model.QueueStatus
import io.github.michael3dprint.mybambuddy.domain.model.TriState
import io.github.michael3dprint.mybambuddy.domain.nfc.TagText
import io.github.michael3dprint.mybambuddy.domain.policy.JobChange
import io.github.michael3dprint.mybambuddy.domain.policy.QueuePicture
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterToolGate
import io.github.michael3dprint.mybambuddy.domain.repo.QueueRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The print queue over [QueueApi]. Starting a job goes through the tool gate first and is refused when the job has no printer. */
class QueueRepositoryImpl(private val api: QueueApi, private val gate: PrinterToolGate) : QueueRepository {
    override suspend fun getQueue(): ApiResult<List<QueueItem>> = when (val result = safeApiCall { api.getQueue() }) {
        is ApiResult.Success -> ApiResult.Success(result.data.take(MAX_ITEMS).mapNotNull { it.toDomain() })
        is ApiResult.Error -> result
    }

    override suspend fun reorder(order: List<Pair<Int, Int>>): ApiResult<Unit> {
        // The server rejects duplicate positions; refuse here instead of sending a request that cannot succeed.
        if (order.isEmpty() || order.map { it.second }.toSet().size != order.size) {
            return ApiResult.Error(message = "Invalid queue order", errorType = ApiErrorType.UNSUPPORTED)
        }
        return safeUnitCall { api.reorder(QueueReorderBody(order.map { QueueReorderItem(it.first, it.second) })) }
    }

    override suspend fun start(itemId: Int, printerId: Int?, skipFilamentCheck: Boolean): ApiResult<Unit> {
        // Starting a print is an MQTT command that needs Developer LAN mode. Without a printer id the server picks the printer, so
        // readiness cannot be checked: fail closed, the same rule as slot changes (design 19).
        if (printerId == null) {
            return ApiResult.Error(message = QueueRepository.NO_PRINTER_MESSAGE, errorType = ApiErrorType.UNSUPPORTED)
        }
        gate.refusal(printerId.toString(), PrinterTool.START_QUEUED_PRINT)
            ?.let { return ApiResult.Error(message = it, errorType = ApiErrorType.UNSUPPORTED) }
        return safeUnitCall { api.start(itemId, skipFilamentCheck) }
    }

    override suspend fun picture(item: QueueItem): ApiResult<ByteArray> {
        val call = when (val source = QueuePicture.sourceOf(item)) {
            is QueuePicture.Source.Library -> safeApiCall { api.libraryThumbnail(source.id) }
            is QueuePicture.Source.Archive -> safeApiCall { api.archiveThumbnail(source.id) }
            null -> return ApiResult.Error(message = "This job has no picture.", errorType = ApiErrorType.NOT_FOUND)
        }
        return when (call) {
            is ApiResult.Success -> withContext(Dispatchers.IO) {
                val bytes = call.data.use { it.bytes() }
                if (bytes.size > MAX_PICTURE_BYTES) {
                    ApiResult.Error(message = "The picture is too large.", errorType = ApiErrorType.UNSUPPORTED)
                } else {
                    ApiResult.Success(bytes)
                }
            }
            is ApiResult.Error -> call
        }
    }

    override suspend fun update(itemId: Int, change: JobChange): ApiResult<Unit> {
        if (change.isEmpty) return ApiResult.Error(message = "Nothing was changed.", errorType = ApiErrorType.UNSUPPORTED)
        return safeUnitCall { api.update(itemId, change.toBody()) }
    }

    override suspend fun cancel(itemId: Int) = safeUnitCall { api.cancel(itemId) }

    override suspend fun remove(itemId: Int) = safeUnitCall { api.remove(itemId) }

    override suspend fun resumeQueue(printerId: Int) = safeUnitCall { api.resume(printerId) }

    private companion object {
        const val MAX_ITEMS = 500
        const val MAX_PICTURE_BYTES = 3_000_000
    }
}

/** Maps a queue row; a row without an id gives null. */
internal fun QueueItemDto.toDomain(): QueueItem? {
    val itemId = id ?: return null
    return QueueItem(
        id = itemId,
        printerId = printerId,
        printerName = printerName?.let { TagText.sanitize(it, 64) }?.ifBlank { null } ?: printerId?.let { "Printer $it" } ?: "Unassigned",
        name = TagText.sanitize(archiveName ?: libraryFileName ?: "", 120).ifBlank { "Print job #$itemId" },
        status = QueueStatus.entries.firstOrNull { it.name.equals(status, ignoreCase = true) } ?: QueueStatus.UNKNOWN,
        position = position ?: 0,
        waitingReason = waitingReason?.let { TagText.sanitize(it, 160) }?.ifBlank { null },
        filamentShort = filamentShort == true,
        libraryFileId = libraryFileId,
        archiveId = archiveId,
        seconds = printTimeSeconds,
        grams = filamentUsedGrams,
        filamentType = filamentType?.let { TagText.sanitize(it, 16) }?.ifBlank { null },
        plateId = plateId,
        errorMessage = errorMessage?.let { TagText.sanitize(it, 200) }?.ifBlank { null },
        amsMapping = amsMapping.orEmpty(),
        options = JobOptions(
            bedLevelling = TriState.fromServer(bedLevelling),
            flowCalibration = TriState.fromServer(flowCali),
            vibrationCalibration = vibrationCali ?: true,
            layerInspection = layerInspect ?: false,
            timelapse = timelapse ?: false,
            useAms = useAms ?: true,
        ),
    )
}

private fun JobChange.toBody() = QueueUpdateBody(
    printerId = printerId,
    plateId = plateId,
    amsMapping = amsMapping,
    bedLevelling = bedLevelling?.api,
    flowCali = flowCalibration?.api,
    vibrationCali = vibrationCalibration,
    layerInspect = layerInspection,
    timelapse = timelapse,
    useAms = useAms,
)
