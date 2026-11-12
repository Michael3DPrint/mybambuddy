package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.HistoryApi
import io.github.michael3dprint.mybambuddy.data.api.safeApiCall
import io.github.michael3dprint.mybambuddy.domain.mapper.toHistoryItem
import io.github.michael3dprint.mybambuddy.domain.model.HistoryItem
import io.github.michael3dprint.mybambuddy.domain.repo.ArchiveRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The archive over [HistoryApi]. Never cached: the list is a live record and is read page by page. */
class ArchiveRepositoryImpl(private val api: HistoryApi) : ArchiveRepository {
    override suspend fun page(limit: Int, offset: Int, printerId: Int?): ApiResult<List<HistoryItem>> =
        when (val result = safeApiCall { api.getArchives(limit = limit, offset = offset, printerId = printerId) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.effectiveItems.map { it.toHistoryItem() })
            is ApiResult.Error -> result
        }

    override suspend fun picture(archiveId: Int): ApiResult<ByteArray> = when (val result = safeApiCall { api.thumbnail(archiveId) }) {
        is ApiResult.Success -> withContext(Dispatchers.IO) {
            val bytes = result.data.use { it.bytes() }
            if (bytes.size > MAX_PICTURE_BYTES) {
                ApiResult.Error(message = "The picture is too large.", errorType = ApiErrorType.UNSUPPORTED)
            } else {
                ApiResult.Success(bytes)
            }
        }
        is ApiResult.Error -> result
    }

    private companion object {
        const val MAX_PICTURE_BYTES = 3_000_000
    }
}
