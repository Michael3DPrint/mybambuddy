package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.PrintFileApi
import io.github.michael3dprint.mybambuddy.data.api.ProgressRequestBody
import io.github.michael3dprint.mybambuddy.data.api.SlicingApi
import io.github.michael3dprint.mybambuddy.data.api.dto.MakerWorldImportBody
import io.github.michael3dprint.mybambuddy.data.api.dto.MakerWorldResolveBody
import io.github.michael3dprint.mybambuddy.data.api.dto.PlatesDto
import io.github.michael3dprint.mybambuddy.data.api.dto.PresetItemDto
import io.github.michael3dprint.mybambuddy.data.api.dto.PresetRefDto
import io.github.michael3dprint.mybambuddy.data.api.dto.QueueCreateBody
import io.github.michael3dprint.mybambuddy.data.api.dto.SliceBody
import io.github.michael3dprint.mybambuddy.data.api.dto.SlicerPresetsDto
import io.github.michael3dprint.mybambuddy.data.api.safeApiCall
import io.github.michael3dprint.mybambuddy.data.api.safeUnitCall
import io.github.michael3dprint.mybambuddy.domain.model.MakerWorldAccess
import io.github.michael3dprint.mybambuddy.domain.model.MakerWorldInstance
import io.github.michael3dprint.mybambuddy.domain.model.MakerWorldModel
import io.github.michael3dprint.mybambuddy.domain.model.PlateInfo
import io.github.michael3dprint.mybambuddy.domain.model.PrintFile
import io.github.michael3dprint.mybambuddy.domain.model.PrinterTool
import io.github.michael3dprint.mybambuddy.domain.model.ProjectSlot
import io.github.michael3dprint.mybambuddy.domain.model.RequiredFilament
import io.github.michael3dprint.mybambuddy.domain.model.SliceJobState
import io.github.michael3dprint.mybambuddy.domain.model.SliceRequest
import io.github.michael3dprint.mybambuddy.domain.model.SlicerPreset
import io.github.michael3dprint.mybambuddy.domain.model.SlicerPresets
import io.github.michael3dprint.mybambuddy.domain.model.StagedPrint
import io.github.michael3dprint.mybambuddy.domain.print.MakerWorldLink
import io.github.michael3dprint.mybambuddy.domain.print.PrintFileRules
import io.github.michael3dprint.mybambuddy.domain.print.PrintSource
import io.github.michael3dprint.mybambuddy.domain.repo.PrintFileRepository
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterToolGate
import io.github.michael3dprint.mybambuddy.domain.slicing.ScaleRule
import io.github.michael3dprint.mybambuddy.domain.slicing.SliceJobReader
import io.github.michael3dprint.mybambuddy.domain.threemf.ScaleResult
import io.github.michael3dprint.mybambuddy.domain.threemf.ThreeMfScaler
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody

/** Print-from-the-phone calls over [PrintFileApi]. Refusals that can be decided on the phone are made before anything is sent. */
class PrintFileRepositoryImpl(
    private val api: PrintFileApi,
    private val slicing: SlicingApi,
    private val gate: PrinterToolGate,
    private val workDir: File,
) : PrintFileRepository {
    override suspend fun upload(source: PrintSource, onProgress: (Float) -> Unit): ApiResult<PrintFile> {
        PrintFileRules.refusal(source.name)?.let { return refused(it) }
        val part = MultipartBody.Part.createFormData("file", source.name, ProgressRequestBody(source, onProgress))
        val uploaded = when (val result = safeApiCall { api.upload(part) }) {
            is ApiResult.Success -> result.data
            is ApiResult.Error -> return result
        }
        val id = uploaded.id ?: return refused("Bambuddy didn't return an id for the uploaded file.")
        // The server makes a new library row even for a duplicate, so this flow always owns what it uploaded.
        val model = uploaded.metadata?.get("sliced_for_model") as? String
        return readPlates(id, uploaded.filename ?: source.name, model, createdHere = true)
    }

    override suspend fun makerWorldAccess(): ApiResult<MakerWorldAccess> = when (val result = safeApiCall { api.makerWorldStatus() }) {
        is ApiResult.Success -> ApiResult.Success(
            when {
                result.data.signInExpired == true -> MakerWorldAccess.SIGN_IN_EXPIRED
                result.data.canDownload == true -> MakerWorldAccess.READY
                else -> MakerWorldAccess.NOT_SIGNED_IN
            },
        )
        is ApiResult.Error -> result
    }

    override suspend fun resolveMakerWorld(link: String): ApiResult<MakerWorldModel> {
        if (MakerWorldLink.parse(link) == null) return refused("That doesn't look like a MakerWorld model link.")
        return when (val result = safeApiCall { api.resolve(MakerWorldResolveBody(link.trim())) }) {
            is ApiResult.Success -> {
                val dto = result.data
                val id = dto.modelId ?: return refused("MakerWorld didn't return a model id.")
                ApiResult.Success(
                    MakerWorldModel(
                        modelId = id,
                        title = dto.design?.get("title") as? String ?: "Model $id",
                        instances = dto.instances.orEmpty().mapIndexedNotNull { i, map -> instanceOf(i, map) },
                        alreadyImported = dto.alreadyImported.orEmpty(),
                        coverUrl = dto.design?.get("coverUrl") as? String,
                    ),
                )
            }
            is ApiResult.Error -> result
        }
    }

    override suspend fun importMakerWorld(model: MakerWorldModel, profileId: Int?): ApiResult<PrintFile> {
        val imported = when (val result = safeApiCall { api.import(MakerWorldImportBody(model.modelId, profileId)) }) {
            is ApiResult.Success -> result.data
            is ApiResult.Error -> return result
        }
        val id = imported.libraryFileId ?: return refused("Bambuddy didn't return an id for the imported file.")
        // An import of a model already in the library returns that file and downloads nothing: it is not ours to delete.
        return readPlates(id, imported.filename ?: model.title, null, createdHere = id !in model.alreadyImported)
    }

    override suspend fun stage(print: StagedPrint): ApiResult<Int> {
        val body = QueueCreateBody(
            printerId = print.printerId,
            libraryFileId = print.libraryFileId.takeIf { !print.fromArchive },
            archiveId = print.libraryFileId.takeIf { print.fromArchive },
            plateId = print.plateIndex,
            amsMapping = print.amsMapping,
            manualStart = true,
            cleanupLibraryAfterDispatch = print.cleanup,
            insertPosition = print.insertPosition,
            bedLevelling = print.options?.bedLevelling?.api,
            flowCali = print.options?.flowCalibration?.api,
            vibrationCali = print.options?.vibrationCalibration,
            layerInspect = print.options?.layerInspection,
            timelapse = print.options?.timelapse,
            useAms = print.options?.useAms,
        )
        return when (val result = safeApiCall { api.createQueueItem(body) }) {
            is ApiResult.Success -> result.data.id?.let { ApiResult.Success(it) }
                ?: refused("Bambuddy didn't return an id for the queue item.")
            is ApiResult.Error -> result
        }
    }

    override suspend fun start(itemId: Int, printerId: Int, skipFilamentCheck: Boolean): ApiResult<Unit> {
        gate.refusal(printerId.toString(), PrinterTool.START_QUEUED_PRINT)?.let { return refused(it) }
        return safeUnitCall { api.start(itemId, skipFilamentCheck) }
    }

    override suspend fun discard(libraryId: Int): ApiResult<Unit> = safeUnitCall { api.deleteFile(libraryId) }

    override suspend fun open(libraryId: Int, name: String, createdHere: Boolean, alsoCreated: List<Int>): ApiResult<PrintFile> =
        when (val result = safeApiCall { api.plates(libraryId) }) {
            is ApiResult.Success -> ApiResult.Success(
                result.data.toFile(libraryId, name, null, createdHere).copy(alsoCreated = alsoCreated),
            )
            is ApiResult.Error -> result
        }

    override suspend fun makerWorldImage(url: String): ApiResult<ByteArray> = imageBytes(safeApiCall { api.makerWorldThumbnail(url) })

    override suspend fun preview(libraryId: Int, plate: Int, hasPlatePicture: Boolean): ApiResult<ByteArray> =
        imageBytes(if (hasPlatePicture) safeApiCall { api.plateThumbnail(libraryId, plate) } else safeApiCall { api.thumbnail(libraryId) })

    private suspend fun imageBytes(result: ApiResult<okhttp3.ResponseBody>): ApiResult<ByteArray> = when (result) {
        is ApiResult.Success -> withContext(Dispatchers.IO) {
            val bytes = result.data.use { it.bytes() }
            if (bytes.size > MAX_PREVIEW_BYTES) refused("The preview is too large to show.") else ApiResult.Success(bytes)
        }
        is ApiResult.Error -> result
    }

    override suspend fun scaledCopy(source: PrintFile, percent: Int): ApiResult<PrintFile> {
        val dir = File(workDir, "scale").apply { mkdirs() }
        val input = File(dir, "in-${source.libraryId}.3mf")
        val output = File(dir, "out-${source.libraryId}-$percent.3mf")
        try {
            val body = when (val r = safeApiCall { api.download(source.libraryId) }) {
                is ApiResult.Success -> r.data
                is ApiResult.Error -> return r
            }
            withContext(Dispatchers.IO) { body.use { b -> input.outputStream().use { b.byteStream().copyTo(it) } } }
            val scaled = withContext(Dispatchers.IO) { ThreeMfScaler.scale(input, output, ScaleRule.factor(percent)) }
            return when (scaled) {
                is ScaleResult.Refused -> refused(scaled.reason)
                is ScaleResult.Scaled -> upload(FileSource(output, ScaleRule.copyName(source.name, percent))) {}
            }
        } finally {
            input.delete()
            output.delete()
        }
    }

    override suspend fun openArchive(archiveId: Int, name: String): ApiResult<PrintFile> =
        when (val result = safeApiCall { api.archivePlates(archiveId) }) {
            is ApiResult.Success -> ApiResult.Success(
                result.data.toFile(
                    archiveId,
                    name,
                    null,
                    createdHere = false,
                ).copy(origin = io.github.michael3dprint.mybambuddy.domain.model.FileOrigin.ARCHIVE),
            )
            is ApiResult.Error -> result
        }

    override suspend fun archivePreview(archiveId: Int, plate: Int, hasPlatePicture: Boolean): ApiResult<ByteArray> = imageBytes(
        if (hasPlatePicture) {
            safeApiCall {
                api.archivePlateThumbnail(archiveId, plate)
            }
        } else {
            safeApiCall { api.archiveThumbnail(archiveId) }
        },
    )

    override suspend fun slicerPresets(): ApiResult<SlicerPresets> = when (val result = safeApiCall { slicing.getPresets() }) {
        is ApiResult.Success -> ApiResult.Success(result.data.toPresets())
        is ApiResult.Error -> result
    }

    override suspend fun projectSlots(libraryId: Int, plate: Int): ApiResult<List<ProjectSlot>> =
        when (val result = safeApiCall { api.requirements(libraryId, plate) }) {
            is ApiResult.Success -> ApiResult.Success(
                result.data.filaments.orEmpty().mapNotNull { f ->
                    f.slotId?.let { ProjectSlot(it, f.type.orEmpty(), f.color.orEmpty(), f.usedInPlate ?: true) }
                },
            )
            is ApiResult.Error -> result
        }

    override suspend fun startSlice(libraryId: Int, request: SliceRequest): ApiResult<Int> {
        val body = SliceBody(
            printerPreset = request.printer.ref(),
            processPreset = request.process.ref(),
            filamentPresets = request.filaments.map { it.ref() },
            filamentColours = request.colours,
            plate = request.plate,
            bedType = request.bed.apiName,
            autoOrient = request.options.autoOrient,
            autoArrange = request.options.autoArrange,
            useEmbeddedSettings = request.options.useEmbeddedSettings,
            export3mf = true,
        )
        return when (val result = safeApiCall { api.slice(libraryId, body) }) {
            is ApiResult.Success -> result.data.jobId?.let { ApiResult.Success(it) } ?: refused("Bambuddy didn't return a slice job.")
            is ApiResult.Error -> result
        }
    }

    override suspend fun sliceJob(jobId: Int): ApiResult<SliceJobState> = when (val result = safeApiCall { api.sliceJob(jobId) }) {
        is ApiResult.Success -> ApiResult.Success(
            SliceJobReader.read(result.data.status, result.data.progress, result.data.result, result.data.errorDetail),
        )
        is ApiResult.Error -> result
    }

    private suspend fun readPlates(id: Int, name: String, model: String?, createdHere: Boolean): ApiResult<PrintFile> =
        when (val result = safeApiCall { api.plates(id) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.toFile(id, name, model, createdHere))
            is ApiResult.Error -> {
                // The file is on the server but cannot be reviewed: remove the one this flow just created, then report the reason.
                if (createdHere) discard(id)
                result
            }
        }

    private fun refused(message: String) = ApiResult.Error(message = message, errorType = ApiErrorType.UNSUPPORTED)

    private fun instanceOf(position: Int, map: Map<String, Any?>): MakerWorldInstance? {
        val profile = (map["profileId"] as? Number)?.toInt() ?: return null
        val title = (map["title"] as? String)?.takeIf { it.isNotBlank() } ?: "Plate configuration ${position + 1}"
        return MakerWorldInstance(profile, title, map["cover"] as? String)
    }
}

/** Maps the plates reply; the printer the file was sliced for comes from the upload metadata, else from the embedded printer preset. */
internal fun PlatesDto.toFile(id: Int, name: String, modelFromUpload: String?, createdHere: Boolean) = PrintFile(
    libraryId = id,
    name = name,
    slicedForModel = modelFromUpload ?: embeddedPrinter?.takeIf { it.isNotBlank() },
    plates = plates.orEmpty().mapNotNull { plate ->
        val index = plate.index ?: return@mapNotNull null
        PlateInfo(
            index = index,
            name = plate.name,
            seconds = plate.printTimeSeconds,
            grams = plate.filamentUsedGrams,
            hasPreview = plate.hasThumbnail == true,
            filaments = plate.filaments.orEmpty().mapNotNull { f ->
                val slot = f.slotId ?: return@mapNotNull null
                RequiredFilament(slot, f.type.orEmpty(), f.color.orEmpty(), f.usedGrams ?: 0.0)
            },
        )
    },
    createdHere = createdHere,
)

private fun SlicerPreset.ref() = PresetRefDto(source, id)

/** Flattens the tiers of the presets reply; an item without a name or id is dropped because a slice cannot refer to it. */
internal fun SlicerPresetsDto.toPresets(): SlicerPresets {
    fun items(
        kind: (io.github.michael3dprint.mybambuddy.data.api.dto.PresetTierDto) -> List<PresetItemDto>?,
        tier: String,
        dto: io.github.michael3dprint.mybambuddy.data.api.dto.PresetTierDto?,
    ) = dto?.let(kind).orEmpty().mapNotNull { i ->
        val name = i.name ?: return@mapNotNull null
        val id = i.id ?: return@mapNotNull null
        SlicerPreset(i.source ?: tier, id, name, i.filamentType, i.compatiblePrinters.orEmpty())
    }
    val tiers = listOf("standard" to standard, "cloud" to cloud, "local" to local, "orca_cloud" to orcaCloud)
    return SlicerPresets(
        printers = tiers.flatMap { (t, d) -> items({ it.printer }, t, d) },
        processes = tiers.flatMap { (t, d) -> items({ it.process }, t, d) },
        filaments = tiers.flatMap { (t, d) -> items({ it.filament }, t, d) },
    )
}

private const val MAX_PREVIEW_BYTES = 5_000_000

/** A file on the phone's own storage, read as a stream, for uploading a copy made by the app. */
private class FileSource(private val file: File, override val name: String) : PrintSource {
    override val size: Long = file.length()

    override fun open(): InputStream = file.inputStream()
}
