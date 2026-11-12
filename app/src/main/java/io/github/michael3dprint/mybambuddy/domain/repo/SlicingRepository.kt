package io.github.michael3dprint.mybambuddy.domain.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.SlicingReadiness

/** Asks Bambuddy whether server slicing is ready (design 36). Read only. */
interface SlicingRepository {
    /** Reads the slicing settings, the presets and the printers, and works out the check list; an error if any read fails. */
    suspend fun check(): ApiResult<SlicingReadiness>
}
