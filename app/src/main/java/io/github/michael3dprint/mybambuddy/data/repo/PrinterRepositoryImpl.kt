package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.PrinterApi
import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterDto
import io.github.michael3dprint.mybambuddy.data.api.safeApiCall
import io.github.michael3dprint.mybambuddy.data.prefs.SettingsRepository
import io.github.michael3dprint.mybambuddy.domain.mapper.mapToPrintStatus
import io.github.michael3dprint.mybambuddy.domain.mapper.mapToPrinter
import io.github.michael3dprint.mybambuddy.domain.model.PrintStatus
import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.domain.repo.LiveUpdates
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Printers and their status over [PrinterApi], with a polled stream from [LiveUpdates]. */
class PrinterRepositoryImpl(
    private val api: PrinterApi,
    private val settingsRepository: SettingsRepository,
    private val liveUpdates: LiveUpdates = PollingLiveUpdates(api),
) : PrinterRepository {

    override fun getPrintersFlow(pollIntervalMs: Long): Flow<ApiResult<List<Printer>>> =
        liveUpdates.pollPrinters(pollIntervalMs).map { result ->
            when (result) {
                is ApiResult.Success -> {
                    val printers = processPrinterDtos(result.data)
                    ApiResult.Success(printers)
                }
                is ApiResult.Error -> result
            }
        }

    override suspend fun fetchPrinters(): ApiResult<List<Printer>> {
        val result = safeApiCall { api.getPrinters() }
        return when (result) {
            is ApiResult.Success -> {
                val dtoList = result.data.effectivePrinters
                val printers = processPrinterDtos(dtoList)
                ApiResult.Success(printers)
            }
            is ApiResult.Error -> result
        }
    }

    override suspend fun fetchPrinter(printerId: String): ApiResult<Printer> {
        val printerResult = safeApiCall { api.getPrinter(printerId) }
        return when (printerResult) {
            is ApiResult.Success -> {
                val dto = printerResult.data
                val statusResult = safeApiCall { api.getPrinterStatus(printerId) }
                val statusDto = if (statusResult is ApiResult.Success) statusResult.data else null
                val baseUrl = settingsRepository.getServerUrlSync()
                val printer = mapToPrinter(dto, statusDto, baseUrl)
                ApiResult.Success(printer)
            }
            is ApiResult.Error -> printerResult
        }
    }

    override suspend fun fetchPrinterStatus(printerId: String): ApiResult<PrintStatus> {
        val result = safeApiCall { api.getPrinterStatus(printerId) }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(mapToPrintStatus(result.data))
            is ApiResult.Error -> result
        }
    }

    private suspend fun processPrinterDtos(dtos: List<PrinterDto>): List<Printer> = coroutineScope {
        val baseUrl = settingsRepository.getServerUrlSync()
        val deferredPrinters = dtos.map { dto ->
            async {
                val statusDto = dto.id?.let { id ->
                    val statusResult = safeApiCall { api.getPrinterStatus(id) }
                    if (statusResult is ApiResult.Success) statusResult.data else null
                }
                mapToPrinter(dto, statusDto, baseUrl)
            }
        }
        deferredPrinters.awaitAll()
    }
}
