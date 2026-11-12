package io.github.michael3dprint.mybambuddy.domain.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.CatalogFilament

/** The read-only filament product catalog (SpoolmanDB). */
interface CatalogRepository {
    /** Empty query returns an empty list. */
    suspend fun search(query: String): ApiResult<List<CatalogFilament>>
}
