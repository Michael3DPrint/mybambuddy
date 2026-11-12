package io.github.michael3dprint.mybambuddy.domain.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.HistoryItem

/** Print history from Bambuddy's archive, read one page at a time. */
interface HistoryRepository {
    /** One page of the print archive: up to [limit] items starting at [offset]. */
    suspend fun getHistory(limit: Int = 20, offset: Int = 0): ApiResult<List<HistoryItem>>
}
