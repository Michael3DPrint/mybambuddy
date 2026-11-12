package io.github.michael3dprint.mybambuddy.domain.model

/** How an archived print ended, or that it is still running. */
enum class HistoryStatus {
    SUCCESS,
    CANCELLED,
    FAILED,
    IN_PROGRESS,
    UNKNOWN,
    ;

    companion object {
        /** Maps the server's status text to a [HistoryStatus]; a blank or unrecognised value gives UNKNOWN. */
        fun fromString(statusStr: String?): HistoryStatus {
            if (statusStr.isNullOrBlank()) return UNKNOWN
            return when (statusStr.lowercase().trim()) {
                "completed", "complete", "finish", "finished", "success", "successful", "ok" -> SUCCESS
                "cancelled", "canceled", "stop", "stopped", "abort", "aborted", "skipped" -> CANCELLED
                "printing", "running" -> IN_PROGRESS
                "failed", "error", "failed_print", "fail" -> FAILED
                else -> UNKNOWN
            }
        }
    }
}
