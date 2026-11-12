package io.github.michael3dprint.mybambuddy.domain.model

/** The state of a queued print job. */
enum class QueueStatus { PENDING, PRINTING, COMPLETED, FAILED, SKIPPED, CANCELLED, UNKNOWN }

/** A print option that Bambuddy takes as off, on or automatic. */
enum class TriState(val api: String, val label: String) {
    AUTO("auto", "Automatic"),
    ON("on", "On"),
    OFF("off", "Off"),
    ;

    companion object {
        /** Reads the server's value, which is text ("auto", "on", "off") or, from older versions, true or false; anything else is automatic. */
        fun fromServer(value: Any?): TriState = when (value) {
            is String -> entries.firstOrNull { it.api.equals(value, ignoreCase = true) } ?: AUTO
            true -> ON
            false -> OFF
            else -> AUTO
        }
    }
}

/** The print options of a job that the app can change; the defaults are Bambuddy's own. */
data class JobOptions(
    val bedLevelling: TriState = TriState.AUTO,
    val flowCalibration: TriState = TriState.AUTO,
    val vibrationCalibration: Boolean = true,
    val layerInspection: Boolean = false,
    val timelapse: Boolean = false,
    val useAms: Boolean = true,
)

/** One job in Bambuddy's print queue. [printerId] is null while the server has not picked a printer. */
data class QueueItem(
    val id: Int,
    val printerId: Int?,
    val printerName: String,
    val name: String,
    val status: QueueStatus,
    val position: Int,
    val waitingReason: String?,
    val filamentShort: Boolean,
    /** The library file or archive the job prints, which names its picture; both null when the server gave neither. */
    val libraryFileId: Int? = null,
    val archiveId: Int? = null,
    /** The slicer's own estimates for the job, when the server has them. */
    val seconds: Int? = null,
    val grams: Double? = null,
    val filamentType: String? = null,
    /** How the job will print: the plate, which tray supplies each project filament (-1 unused), and the print options. */
    val plateId: Int? = null,
    val amsMapping: List<Int> = emptyList(),
    val options: JobOptions = JobOptions(),
    /** Bambuddy's own reason when the job failed or was skipped. */
    val errorMessage: String? = null,
) {
    /** A job that is over (finished, failed, skipped or cancelled) and can be removed from the list; a job that is printing cannot. */
    val isFinished: Boolean
        get() = status == QueueStatus.COMPLETED ||
            status == QueueStatus.FAILED ||
            status == QueueStatus.SKIPPED ||
            status == QueueStatus.CANCELLED

    /** Only waiting jobs can be moved, started or cancelled. */
    val isPending: Boolean get() = status == QueueStatus.PENDING
}
