package io.github.michael3dprint.mybambuddy.domain.slicing

import io.github.michael3dprint.mybambuddy.domain.model.SliceJobState

/** Turns a slice job's status fields into a [SliceJobState]. An unknown status counts as still running, so a new status never fails a slice. */
object SliceJobReader {
    /** [status] is the server's text, [progress] its free-form progress object, [result] and [errorDetail] come with done and failed. */
    fun read(status: String?, progress: Map<String, Any?>?, result: Map<String, Any?>?, errorDetail: String?): SliceJobState =
        when (status) {
            "completed" -> {
                val id = (result?.get("library_file_id") as? Number)?.toInt()
                if (id == null) {
                    SliceJobState.Failed("Bambuddy finished the slice but did not say which file it made.")
                } else {
                    SliceJobState.Done(id, result["name"] as? String ?: "Sliced file")
                }
            }
            "failed" -> SliceJobState.Failed(errorDetail?.takeIf { it.isNotBlank() } ?: "The slice failed.")
            else -> SliceJobState.Running(percentOf(progress))
        }

    /** A "percent" or "pct" number is taken as is; a "progress" number of 1 or less is a fraction. Unknown shapes give no percent. */
    private fun percentOf(progress: Map<String, Any?>?): Int? {
        fun number(key: String) = (progress?.get(key) as? Number)?.toDouble()
        val value = number("percent") ?: number("pct") ?: number("progress")?.let { if (it <= 1.0) it * PERCENT else it } ?: return null
        return value.toInt().coerceIn(0, PERCENT)
    }

    private const val PERCENT = 100
}
