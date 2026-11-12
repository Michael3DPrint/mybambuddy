package io.github.michael3dprint.mybambuddy.domain.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterDto
import kotlinx.coroutines.flow.Flow

/** Streams of printer updates, one result per poll. The implementation is polling. */
interface LiveUpdates {
    /** The printers, emitted once per poll every [periodMillis], slowing down while the server cannot be reached. */
    fun pollPrinters(periodMillis: Long = 5000L): Flow<ApiResult<List<PrinterDto>>>
}
