package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.InventoryApi
import io.github.michael3dprint.mybambuddy.data.api.SpoolUsageApi
import io.github.michael3dprint.mybambuddy.data.api.dto.SpoolCreateRequest
import io.github.michael3dprint.mybambuddy.data.api.dto.SpoolTagLinkRequest
import io.github.michael3dprint.mybambuddy.data.api.dto.SpoolWeightRequest
import io.github.michael3dprint.mybambuddy.data.api.safeApiCall
import io.github.michael3dprint.mybambuddy.data.api.safeUnitCall
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import io.github.michael3dprint.mybambuddy.domain.model.SpoolDraft
import io.github.michael3dprint.mybambuddy.domain.model.UsageEntry
import io.github.michael3dprint.mybambuddy.domain.nfc.TagText
import io.github.michael3dprint.mybambuddy.domain.repo.SpoolRepository

/** Spools over Bambuddy, using the Spoolman route when Spoolman is connected and Bambuddy's own inventory otherwise. */
class SpoolRepositoryImpl(private val api: InventoryApi, private val mode: InventoryModeProvider, private val usageApi: SpoolUsageApi) :
    SpoolRepository {

    override suspend fun getSpools(): ApiResult<List<Spool>> {
        val useSpoolman = mode.current().useSpoolman
        return when (val result = safeApiCall { if (useSpoolman) api.getSpoolmanSpools() else api.getInventorySpools() }) {
            is ApiResult.Success -> ApiResult.Success(result.data.filter { it.archivedAt == null }.mapNotNull { it.toSpool() })
            is ApiResult.Error -> result
        }
    }

    override suspend fun createSpool(draft: SpoolDraft): ApiResult<Spool> {
        val body = SpoolCreateRequest(
            material = draft.material.trim(),
            subtype = draft.subtype.trim().ifBlank { null },
            brand = draft.brand.trim().ifBlank { null },
            colorName = draft.colorName.trim().ifBlank { null },
            rgba = draft.colorHex.trim().removePrefix("#").ifBlank { null },
            labelWeight = draft.labelWeightGrams,
            coreWeight = draft.coreWeightGrams,
            note = draft.note.trim().ifBlank { null },
        )
        val useSpoolman = mode.current().useSpoolman
        return when (val result = safeApiCall { if (useSpoolman) api.createSpoolmanSpool(body) else api.createInventorySpool(body) }) {
            is ApiResult.Success ->
                result.data.toSpool()?.let { ApiResult.Success(it) }
                    ?: ApiResult.Error(message = "Server returned a spool without an id", errorType = ApiErrorType.PARSE_ERROR)
            is ApiResult.Error -> result
        }
    }

    override suspend fun getUsage(spoolId: Int): ApiResult<List<UsageEntry>> {
        val useSpoolman = mode.current().useSpoolman
        return when (
            val result = safeApiCall {
                if (useSpoolman) usageApi.getSpoolmanUsage(spoolId) else usageApi.getInventoryUsage(spoolId)
            }
        ) {
            is ApiResult.Success -> ApiResult.Success(
                result.data.take(MAX_USAGE_ROWS).map {
                    UsageEntry(
                        date = it.createdAt?.take(10).orEmpty(),
                        printName = TagText.sanitize(it.printName.orEmpty(), 80).ifBlank { "Print" },
                        grams = (it.weightUsed ?: 0.0).toInt().coerceAtLeast(0),
                        status = it.status.orEmpty(),
                    )
                }.sortedByDescending { it.date },
            )
            is ApiResult.Error -> result
        }
    }

    private companion object {
        const val MAX_USAGE_ROWS = 200
    }

    override suspend fun canWeigh(): Boolean = mode.current().useSpoolman

    override suspend fun updateWeight(spoolId: Int, grossGrams: Double): ApiResult<Unit> {
        if (!canWeigh()) {
            return ApiResult.Error(
                message = "Weighing needs Spoolman. Bambuddy's built-in inventory can't take a scale reading.",
                errorType = ApiErrorType.UNSUPPORTED,
            )
        }
        return safeUnitCall { api.setSpoolmanWeight(spoolId, SpoolWeightRequest(grossGrams)) }
    }

    override suspend fun linkTag(spoolId: Int, tagUid: String): ApiResult<Unit> {
        val uid = tagUid.filter { it.isLetterOrDigit() }.uppercase()
        val body = SpoolTagLinkRequest(tagUid = uid)
        val useSpoolman = mode.current().useSpoolman
        return safeUnitCall { if (useSpoolman) api.linkSpoolmanTag(spoolId, body) else api.linkInventoryTag(spoolId, body) }
    }
}
