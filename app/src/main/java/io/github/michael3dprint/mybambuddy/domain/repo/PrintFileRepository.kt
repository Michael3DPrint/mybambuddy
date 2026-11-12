package io.github.michael3dprint.mybambuddy.domain.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.MakerWorldAccess
import io.github.michael3dprint.mybambuddy.domain.model.MakerWorldModel
import io.github.michael3dprint.mybambuddy.domain.model.PrintFile
import io.github.michael3dprint.mybambuddy.domain.model.ProjectSlot
import io.github.michael3dprint.mybambuddy.domain.model.SliceJobState
import io.github.michael3dprint.mybambuddy.domain.model.SliceRequest
import io.github.michael3dprint.mybambuddy.domain.model.SlicerPresets
import io.github.michael3dprint.mybambuddy.domain.model.StagedPrint
import io.github.michael3dprint.mybambuddy.domain.print.PrintSource

/** Sending a file to Bambuddy and printing it (design 35 and 38). Every call is a single attempt and nothing is replayed. */
interface PrintFileRepository {
    /** Uploads [source] and reads its plates. [onProgress] gets 0 to 1 while the bytes are sent. */
    suspend fun upload(source: PrintSource, onProgress: (Float) -> Unit): ApiResult<PrintFile>

    /** Whether Bambuddy can download from MakerWorld. */
    suspend fun makerWorldAccess(): ApiResult<MakerWorldAccess>

    /** Looks up a MakerWorld link. */
    suspend fun resolveMakerWorld(link: String): ApiResult<MakerWorldModel>

    /** A MakerWorld picture as image bytes, fetched through Bambuddy, so the user can see the model before importing it. */
    suspend fun makerWorldImage(url: String): ApiResult<ByteArray>

    /** Has Bambuddy download one plate configuration into its library, then reads its plates. */
    suspend fun importMakerWorld(model: MakerWorldModel, profileId: Int?): ApiResult<PrintFile>

    /** Creates a staged queue item that never starts by itself; returns its id. */
    suspend fun stage(print: StagedPrint): ApiResult<Int>

    /**
     * Starts a staged item. Refused on the phone when the printer cannot take a start. A filament shortfall comes back as a 409 error,
     * and only [skipFilamentCheck] true (after the user confirmed) sends it again past that check.
     */
    suspend fun start(itemId: Int, printerId: Int, skipFilamentCheck: Boolean): ApiResult<Unit>

    /** Deletes a library file this flow created, when the user backs out. */
    suspend fun discard(libraryId: Int): ApiResult<Unit>

    /** Reads a library file's plates, for a file the flow did not upload itself (a slice result). [createdHere] says whether it may be deleted on cancel. */
    suspend fun open(libraryId: Int, name: String, createdHere: Boolean, alsoCreated: List<Int>): ApiResult<PrintFile>

    /** The picture of plate [plate] (else the file's own thumbnail) as image bytes; an error when the file has none. */
    suspend fun preview(libraryId: Int, plate: Int, hasPlatePicture: Boolean): ApiResult<ByteArray>

    /**
     * Makes a copy of [source] with everything on its plate scaled to [percent] and puts it in the library (design 40): the file is
     * downloaded, rewritten on the phone and uploaded again. The original is never changed. The copy is the caller's to delete.
     */
    suspend fun scaledCopy(source: PrintFile, percent: Int): ApiResult<PrintFile>

    /** Reads an archived print as a file that can be printed again; the file is the archive's own and is never deleted by the app. */
    suspend fun openArchive(archiveId: Int, name: String): ApiResult<PrintFile>

    /** The picture of plate [plate] of an archive (else its own thumbnail) as image bytes. */
    suspend fun archivePreview(archiveId: Int, plate: Int, hasPlatePicture: Boolean): ApiResult<ByteArray>

    /** Every slicer preset Bambuddy offers. */
    suspend fun slicerPresets(): ApiResult<SlicerPresets>

    /** Every filament slot of the file's project for [plate] (design 39). */
    suspend fun projectSlots(libraryId: Int, plate: Int): ApiResult<List<ProjectSlot>>

    /** Starts one slice; returns the job id. A single attempt, never repeated. */
    suspend fun startSlice(libraryId: Int, request: SliceRequest): ApiResult<Int>

    /** Reads a slice job's state once. */
    suspend fun sliceJob(jobId: Int): ApiResult<SliceJobState>
}
