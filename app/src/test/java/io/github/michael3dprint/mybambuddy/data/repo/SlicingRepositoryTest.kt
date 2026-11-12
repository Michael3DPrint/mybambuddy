package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiClient
import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.SlicingApi
import io.github.michael3dprint.mybambuddy.data.api.dto.PresetItemDto
import io.github.michael3dprint.mybambuddy.data.api.dto.PresetTierDto
import io.github.michael3dprint.mybambuddy.data.api.dto.SlicerPresetsDto
import io.github.michael3dprint.mybambuddy.data.api.dto.SlicerSettingsDto
import io.github.michael3dprint.mybambuddy.domain.model.PrintStatus
import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class SlicingRepositoryTest {
    private fun tier(vararg printer: String, process: Int = 0, filament: Int = 0) = PresetTierDto(
        printer = printer.map { PresetItemDto(it) },
        process = List(process) { PresetItemDto("p$it") },
        filament = List(filament) { PresetItemDto("f$it") },
    )

    private class Api(var settings: Response<SlicerSettingsDto>, var presets: Response<SlicerPresetsDto>) : SlicingApi {
        var calls = 0

        override suspend fun getSettings(): Response<SlicerSettingsDto> {
            calls++
            return settings
        }

        override suspend fun getPresets(): Response<SlicerPresetsDto> {
            calls++
            return presets
        }
    }

    private class Printers(var result: ApiResult<List<Printer>>) : PrinterRepository {
        override fun getPrintersFlow(pollIntervalMs: Long): Flow<ApiResult<List<Printer>>> = emptyFlow()

        override suspend fun fetchPrinters() = result

        override suspend fun fetchPrinter(printerId: String): ApiResult<Printer> = error("unused")

        override suspend fun fetchPrinterStatus(printerId: String): ApiResult<PrintStatus> = error("unused")
    }

    private val x2d = Printer("1", "Shop printer", "X2D", PrinterState.IDLE)
    private val okSettings = SlicerSettingsDto(true, "bambu_studio", "", "http://box:3001")
    private val okPresets = SlicerPresetsDto(
        standard = tier("Bambu Lab X2D", "Bambu Lab A1"),
        cloud = tier("Bambu Lab X2D 0.4 nozzle", process = 250, filament = 2111),
        local = tier(),
        cloudStatus = "ok",
    )

    @Test
    fun `the three reads are combined into one check list`() = runTest {
        val repo =
            SlicingRepositoryImpl(Api(Response.success(okSettings), Response.success(okPresets)), Printers(ApiResult.Success(listOf(x2d))))
        val r = (repo.check() as ApiResult.Success).data
        assertTrue(r.ready)
        assertNotNull(r.checks.firstOrNull { it.id == "printer-preset:Shop printer" && it.passed })
    }

    @Test
    fun `the address of the chosen slicer decides whether an address is set`() {
        assertTrue(SlicerSettingsDto(true, "bambu_studio", "", "http://x").toDomain().addressSet)
        assertFalse(SlicerSettingsDto(true, "bambu_studio", "http://x", "").toDomain().addressSet)
        assertTrue(SlicerSettingsDto(true, "orcaslicer", "http://x", "").toDomain().addressSet)
        assertFalse(SlicerSettingsDto(null, null, null, null).toDomain().useSlicerApi)
    }

    @Test
    fun `presets are counted across every tier and only the standard tier counts as the slicer app`() {
        val s = okPresets.toSummary()
        assertEquals(2, s.standardPrinterCount)
        assertEquals(3, s.printerPresetNames.size)
        assertEquals(250, s.processCount)
        assertEquals(2111, s.filamentCount)
        assertTrue(s.cloudSignedIn)
        assertFalse(SlicerPresetsDto(cloudStatus = "not_authenticated").toSummary().cloudSignedIn)
        assertEquals(0, SlicerPresetsDto().toSummary().standardPrinterCount)
    }

    @Test
    fun `a failed read gives an error and nothing is assumed ready`() = runTest {
        val bad = Response.error<SlicerSettingsDto>(500, "".toResponseBody(null))
        val repo = SlicingRepositoryImpl(Api(bad, Response.success(okPresets)), Printers(ApiResult.Success(listOf(x2d))))
        assertTrue(repo.check() is ApiResult.Error)
        val noPrinters = SlicingRepositoryImpl(
            Api(Response.success(okSettings), Response.success(okPresets)),
            Printers(ApiResult.Error(null, "down", ApiErrorType.NETWORK_ERROR)),
        )
        assertTrue(noPrinters.check() is ApiResult.Error)
    }

    @Test
    fun `a real settings reply with many unrelated keys parses, and secrets in it are ignored`() {
        val json = """{"use_slicer_api": false, "preferred_slicer": "bambu_studio", "bambu_studio_api_url": "", "orcaslicer_api_url": "",
            "ha_token": "not-a-real-token", "smtp_password": "not-a-real-password", "anything_else": [1, 2, 3]}"""
        val dto = ApiClient.createMoshi().adapter(SlicerSettingsDto::class.java).fromJson(json)!!
        assertEquals(false, dto.useSlicerApi)
        assertEquals("bambu_studio", dto.preferredSlicer)
        assertFalse(dto.toString().contains("not-a-real"))
    }

    @Test
    fun `a real presets reply parses`() {
        val json = """{"orca_cloud": {"printer": [], "process": [], "filament": []},
            "cloud": {"printer": [{"id": "1", "name": "Bambu Lab X2D 0.4 nozzle", "source": "cloud"}], "process": [{"name": "0.20mm"}], "filament": [{"name": "Generic PLA"}]},
            "standard": {"printer": [{"name": "Bambu Lab X2D"}], "process": [], "filament": []},
            "cloud_status": "ok", "orca_cloud_status": "not_authenticated", "stock_aliases": {}}"""
        val dto = ApiClient.createMoshi().adapter(SlicerPresetsDto::class.java).fromJson(json)!!
        val s = dto.toSummary()
        assertEquals(1, s.standardPrinterCount)
        assertEquals(1, s.processCount)
        assertTrue(s.cloudSignedIn)
    }
}
