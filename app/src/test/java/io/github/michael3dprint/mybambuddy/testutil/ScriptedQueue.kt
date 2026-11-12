package io.github.michael3dprint.mybambuddy.testutil

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.QueueItem
import io.github.michael3dprint.mybambuddy.domain.policy.JobChange
import io.github.michael3dprint.mybambuddy.domain.repo.QueueRepository

/** A queue whose answers to getQueue come from a script, one per call (the last repeats), for tests of what happens after a start. */
class ScriptedQueue(private val script: List<List<QueueItem>>) : QueueRepository {
    var reads = 0
        private set
    var startResult: ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun getQueue(): ApiResult<List<QueueItem>> = ApiResult.Success(script[minOf(reads++, script.size - 1)])

    override suspend fun reorder(order: List<Pair<Int, Int>>): ApiResult<Unit> = ApiResult.Success(Unit)

    var lastSkip: Boolean? = null

    override suspend fun start(itemId: Int, printerId: Int?, skipFilamentCheck: Boolean): ApiResult<Unit> {
        lastSkip = skipFilamentCheck
        return startResult
    }

    override suspend fun picture(item: QueueItem): ApiResult<ByteArray> =
        ApiResult.Error(message = "none", errorType = ApiErrorType.NOT_FOUND)

    override suspend fun update(itemId: Int, change: JobChange): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun cancel(itemId: Int): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun remove(itemId: Int): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun resumeQueue(printerId: Int): ApiResult<Unit> = ApiResult.Success(Unit)
}
