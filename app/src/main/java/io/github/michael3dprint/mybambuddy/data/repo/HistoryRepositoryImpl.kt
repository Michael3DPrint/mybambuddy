package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.HistoryApi
import io.github.michael3dprint.mybambuddy.data.api.safeApiCall
import io.github.michael3dprint.mybambuddy.domain.mapper.toHistoryItem
import io.github.michael3dprint.mybambuddy.domain.model.HistoryItem
import io.github.michael3dprint.mybambuddy.domain.repo.HistoryRepository

/** Reads the print archive through [HistoryApi]. */
class HistoryRepositoryImpl(private val api: HistoryApi) : HistoryRepository {

    override suspend fun getHistory(limit: Int, offset: Int): ApiResult<List<HistoryItem>> {
        val result = safeApiCall { api.getArchives(limit = limit, offset = offset) }
        return when (result) {
            is ApiResult.Success -> {
                val items = result.data.effectiveItems.map { it.toHistoryItem() }
                ApiResult.Success(items)
            }
            is ApiResult.Error -> {
                ApiResult.Error(
                    code = result.code,
                    message = result.message,
                    errorType = result.errorType,
                    serverDetail = result.serverDetail,
                )
            }
        }
    }
}
