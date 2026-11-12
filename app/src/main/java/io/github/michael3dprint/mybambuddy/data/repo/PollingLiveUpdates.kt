package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.PrinterApi
import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterDto
import io.github.michael3dprint.mybambuddy.data.api.safeApiCall
import io.github.michael3dprint.mybambuddy.domain.policy.Backoff
import io.github.michael3dprint.mybambuddy.domain.repo.LiveUpdates
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Printer updates by polling, backing off while the server cannot be reached. */
class PollingLiveUpdates(private val api: PrinterApi, private val backoff: Backoff = Backoff()) : LiveUpdates {

    override fun pollPrinters(periodMillis: Long): Flow<ApiResult<List<PrinterDto>>> = flow {
        var failures = 0
        while (true) {
            val result = safeApiCall { api.getPrinters() }
            failures = if (result is ApiResult.Error) failures + 1 else 0
            val mapped: ApiResult<List<PrinterDto>> = when (result) {
                is ApiResult.Success -> ApiResult.Success(result.data.printers ?: emptyList())
                is ApiResult.Error -> result
            }
            emit(mapped)
            delay(backoff.delayMillis(periodMillis, failures))
        }
    }
}
