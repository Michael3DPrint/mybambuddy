package io.github.michael3dprint.mybambuddy.domain.model

/** A printer's overall state as the app shows it. */
enum class PrinterState {
    PRINTING,
    IDLE,
    PAUSED,
    FINISHED,

    /** The last print failed or was stopped. The printer reports no error codes and Bambuddy counts it as ready for the next job (design 43). */
    FAILED,
    ERROR,
    OFFLINE,
    UNKNOWN,
    ;

    companion object {
        /** Maps the server's state text to a [PrinterState]; a blank or unrecognised value gives UNKNOWN. */
        fun fromString(stateStr: String?): PrinterState {
            if (stateStr.isNullOrBlank()) return UNKNOWN
            when (stateStr.trim().uppercase()) {
                "IDLE", "READY", "STANDBY" -> return IDLE
                "PREPARE", "SLICING", "INIT", "RUNNING", "PRINTING", "PRINT" -> return PRINTING
                "PAUSE", "PAUSED" -> return PAUSED
                "FINISH", "FINISHED", "COMPLETED", "COMPLETE" -> return FINISHED
                "FAILED" -> return FAILED
                "ERROR", "FAULT" -> return ERROR
                "OFFLINE", "DISCONNECTED" -> return OFFLINE
            }
            val normalized = stateStr.trim().lowercase()
            return when {
                normalized == "printing" ||
                    normalized == "print" ||
                    normalized.contains(
                        "running",
                    ) ||
                    normalized.contains("prepare") -> PRINTING
                normalized == "paused" || normalized == "pause" -> PAUSED
                normalized == "idle" || normalized == "ready" || normalized == "standby" -> IDLE
                normalized == "failed" -> FAILED
                normalized == "error" || normalized.contains("fault") -> ERROR
                normalized == "offline" || normalized == "disconnected" -> OFFLINE
                normalized.contains("print") -> PRINTING
                normalized.contains("pause") -> PAUSED
                normalized.contains("idle") -> IDLE
                normalized.contains("error") -> ERROR
                normalized.contains("off") -> OFFLINE
                else -> UNKNOWN
            }
        }
    }
}
