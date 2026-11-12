package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.PrinterApi
import io.github.michael3dprint.mybambuddy.data.api.safeApiCall
import io.github.michael3dprint.mybambuddy.data.mapper.toCapabilities
import io.github.michael3dprint.mybambuddy.domain.model.PrinterReadiness
import io.github.michael3dprint.mybambuddy.domain.nfc.TagText
import io.github.michael3dprint.mybambuddy.domain.policy.ReadinessEvaluator
import io.github.michael3dprint.mybambuddy.domain.repo.ReadinessCheck

/** Reads printer status over [PrinterApi] (GET only) and evaluates it with [ReadinessEvaluator]. */
class ReadinessCheckImpl(private val printerApi: PrinterApi) : ReadinessCheck {
    override suspend fun checkAll(): ApiResult<List<PrinterReadiness>> {
        val printers = when (val r = safeApiCall { printerApi.getPrinters() }) {
            is ApiResult.Success -> r.data.effectivePrinters
            is ApiResult.Error -> return r
        }
        val reports = printers.take(MAX_PRINTERS).mapNotNull { printer ->
            val id = printer.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = TagText.sanitize(printer.name ?: "Printer $id", MAX_NAME_LENGTH)
            val caps = (safeApiCall { printerApi.getPrinterStatus(id) } as? ApiResult.Success)?.data?.toCapabilities()
            ReadinessEvaluator.evaluate(id, name, caps)
        }
        return ApiResult.Success(reports)
    }

    override suspend fun check(printerId: String, printerName: String): ApiResult<PrinterReadiness> =
        when (val r = safeApiCall { printerApi.getPrinterStatus(printerId) }) {
            is ApiResult.Success -> ApiResult.Success(ReadinessEvaluator.evaluate(printerId, printerName, r.data.toCapabilities()))
            is ApiResult.Error -> r
        }

    private companion object {
        const val MAX_PRINTERS = 20
        const val MAX_NAME_LENGTH = 40
    }
}
