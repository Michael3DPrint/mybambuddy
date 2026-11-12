package io.github.michael3dprint.mybambuddy.ui.filament

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.AmsTray
import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.domain.model.DryPhase
import io.github.michael3dprint.mybambuddy.domain.model.PrintActivity
import io.github.michael3dprint.mybambuddy.domain.model.PrinterCapabilities
import io.github.michael3dprint.mybambuddy.domain.model.SlotLoadResult
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import io.github.michael3dprint.mybambuddy.domain.model.SpoolDraft
import io.github.michael3dprint.mybambuddy.domain.repo.AmsRepository
import io.github.michael3dprint.mybambuddy.domain.repo.SpoolRepository

/** Shared sample data and fakes for the three AMS screen-model tests. */
internal val testAmsUnit = AmsUnit(
    printerId = 1, printerName = "P", amsId = 0, isHt = false, humidityLevel = 23, tempC = 21.0,
    supportsDrying = true, dryPhase = DryPhase.OFF, dryMinutesLeft = 0, dryTargetTemp = null,
    trays = listOf(AmsTray(2, "PLA", "PLA Basic", "307FE2FF", 75, null)),
    capabilities = PrinterCapabilities(
        connected = true,
        developerMode = true,
        hasAms = true,
        supportsDrying = true,
        activity = PrintActivity.IDLE,
    ),
)
internal val testSpool = Spool(5, "Bambu Lab", "PLA", "Basic", "Blue", "307FE2FF", 1000, 250, null, null)

internal class FakeAms : AmsRepository {
    var units: ApiResult<List<AmsUnit>> = ApiResult.Success(emptyList())
    var drying: ApiResult<Unit> = ApiResult.Success(Unit)
    val calls = mutableListOf<String>()

    override suspend fun getAms() = units

    override suspend fun getTray(printerId: Int, amsId: Int, trayId: Int): ApiResult<AmsTray?> = when (val r = units) {
        is ApiResult.Success ->
            ApiResult.Success(
                r.data.firstOrNull { it.printerId == printerId && it.amsId == amsId }?.trays?.firstOrNull {
                    it.trayId ==
                        trayId
                },
            )
        is ApiResult.Error -> r
    }

    var pendingConfig: Boolean? = false
    var loadError: ApiResult.Error? = null

    override suspend fun loadSpool(spoolId: Int, printerId: Int, amsId: Int, trayId: Int, printerName: String): ApiResult<SlotLoadResult> {
        calls += "load:$spoolId:$printerId:$amsId:$trayId"
        loadError?.let { return it }
        return ApiResult.Success(SlotLoadResult(pendingConfig))
    }

    override suspend fun unloadSpool(spoolId: Int, printerId: Int, amsId: Int, trayId: Int): ApiResult<Unit> {
        calls += "unload:$spoolId"
        return ApiResult.Success(Unit)
    }

    override suspend fun startDrying(printerId: Int, amsId: Int, tempC: Int, hours: Int, filament: String): ApiResult<Unit> {
        calls += "dry:$printerId:$amsId:$tempC:$hours:$filament"
        return drying
    }

    override suspend fun stopDrying(printerId: Int, amsId: Int): ApiResult<Unit> {
        calls += "stop"
        return ApiResult.Success(Unit)
    }
}

internal class FakeSpools(private val list: List<Spool>) : SpoolRepository {
    override suspend fun getSpools() = ApiResult.Success(list)

    override suspend fun createSpool(draft: SpoolDraft): ApiResult<Spool> = ApiResult.Success(list.first())

    override suspend fun linkTag(spoolId: Int, tagUid: String): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun getUsage(spoolId: Int): ApiResult<List<io.github.michael3dprint.mybambuddy.domain.model.UsageEntry>> =
        ApiResult.Success(emptyList())

    override suspend fun canWeigh(): Boolean = false

    override suspend fun updateWeight(spoolId: Int, grossGrams: Double): ApiResult<Unit> =
        ApiResult.Error(message = "n/a", errorType = io.github.michael3dprint.mybambuddy.data.api.ApiErrorType.UNSUPPORTED)
}
