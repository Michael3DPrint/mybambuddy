package io.github.michael3dprint.mybambuddy.domain.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.QueueItem

/** The print queue. Everything except [getQueue] acts on a printer: single attempt, never cached or replayed. */
interface QueueRepository {
    /** The print queue across all printers. */
    suspend fun getQueue(): ApiResult<List<QueueItem>>

    /** [order] is (jobId, newPosition) pairs with unique positions. */
    suspend fun reorder(order: List<Pair<Int, Int>>): ApiResult<Unit>

    /** [printerId] (from the queue item, when it has one) lets the repository refuse the start when the printer cannot take it. */
    suspend fun start(itemId: Int, printerId: Int? = null, skipFilamentCheck: Boolean = false): ApiResult<Unit>

    /** The small picture of a job's model as image bytes; an error when the job has none or it cannot be read. */
    suspend fun picture(item: QueueItem): ApiResult<ByteArray>

    /** Changes a waiting job in place with only the fields that differ; refused on the phone when nothing differs. */
    suspend fun update(itemId: Int, change: io.github.michael3dprint.mybambuddy.domain.policy.JobChange): ApiResult<Unit>

    /** Cancels a job. */
    suspend fun cancel(itemId: Int): ApiResult<Unit>

    /** Removes a job from the queue. */
    suspend fun remove(itemId: Int): ApiResult<Unit>

    /** Resumes a printer's paused queue. */
    suspend fun resumeQueue(printerId: Int): ApiResult<Unit>

    companion object {
        /** Shown instead of Start for a job that has no printer yet (the server would pick one, so readiness cannot be checked). */
        const val NO_PRINTER_MESSAGE = "Choose a printer for this job in Bambuddy first."
    }
}
