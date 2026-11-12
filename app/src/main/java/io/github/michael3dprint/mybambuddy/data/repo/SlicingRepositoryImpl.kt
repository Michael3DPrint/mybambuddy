package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.SlicingApi
import io.github.michael3dprint.mybambuddy.data.api.dto.PresetTierDto
import io.github.michael3dprint.mybambuddy.data.api.dto.SlicerPresetsDto
import io.github.michael3dprint.mybambuddy.data.api.dto.SlicerSettingsDto
import io.github.michael3dprint.mybambuddy.data.api.safeApiCall
import io.github.michael3dprint.mybambuddy.domain.model.PresetSummary
import io.github.michael3dprint.mybambuddy.domain.model.PrinterRef
import io.github.michael3dprint.mybambuddy.domain.model.SlicerSettings
import io.github.michael3dprint.mybambuddy.domain.model.SlicingInputs
import io.github.michael3dprint.mybambuddy.domain.model.SlicingReadiness
import io.github.michael3dprint.mybambuddy.domain.policy.SlicingRule
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterRepository
import io.github.michael3dprint.mybambuddy.domain.repo.SlicingRepository

/** Reads Bambuddy's slicing settings and presets and the printer list, then applies [SlicingRule]. Every call is a GET. */
class SlicingRepositoryImpl(private val api: SlicingApi, private val printers: PrinterRepository) : SlicingRepository {
    override suspend fun check(): ApiResult<SlicingReadiness> {
        val settings = when (val r = safeApiCall { api.getSettings() }) {
            is ApiResult.Success -> r.data
            is ApiResult.Error -> return r
        }
        val presets = when (val r = safeApiCall { api.getPresets() }) {
            is ApiResult.Success -> r.data
            is ApiResult.Error -> return r
        }
        val list = when (val r = printers.fetchPrinters()) {
            is ApiResult.Success -> r.data
            is ApiResult.Error -> return r
        }
        return ApiResult.Success(
            SlicingRule.evaluate(
                SlicingInputs(
                    settings.toDomain(),
                    presets.toSummary(),
                    list.map {
                        PrinterRef(it.name, it.model)
                    },
                ),
            ),
        )
    }
}

/** The slicing settings as the check uses them; only whether the chosen slicer has an address saved is kept, never the address. */
internal fun SlicerSettingsDto.toDomain(): SlicerSettings {
    val preferred = preferredSlicer.orEmpty()
    val address = if (preferred == "orcaslicer") orcaslicerApiUrl else bambuStudioApiUrl
    return SlicerSettings(useSlicerApi == true, preferred, !address.isNullOrBlank())
}

/** Counts the presets across every tier; only the standard tier (the slicer app's own list) counts as the slicer app being reached. */
internal fun SlicerPresetsDto.toSummary(): PresetSummary {
    val tiers: List<PresetTierDto> = listOfNotNull(standard, cloud, local, orcaCloud)
    fun names(pick: (PresetTierDto) -> List<io.github.michael3dprint.mybambuddy.data.api.dto.PresetItemDto>?) =
        tiers.flatMap { pick(it).orEmpty() }.mapNotNull { it.name }
    return PresetSummary(
        standardPrinterCount = standard?.printer.orEmpty().size,
        printerPresetNames = names { it.printer },
        processCount = names { it.process }.size,
        filamentCount = names { it.filament }.size,
        cloudSignedIn = cloudStatus == "ok",
    )
}
