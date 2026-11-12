package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.PrinterControlApi
import io.github.michael3dprint.mybambuddy.domain.model.PrinterControlCommand
import io.github.michael3dprint.mybambuddy.domain.model.PrinterTool
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterToolGate
import io.github.michael3dprint.mybambuddy.testutil.AllowAllToolGate
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class PrintControlRepositoryTest {

    private lateinit var fakeApi: FakePrinterControlApi
    private lateinit var repository: PrintControlRepositoryImpl

    @Before
    fun setUp() {
        fakeApi = FakePrinterControlApi()
        repository = PrintControlRepositoryImpl(fakeApi, AllowAllToolGate)
    }

    @Test
    fun `pausePrint returns Success when API returns successful response`() = runTest {
        fakeApi.shouldSucceed = true
        val result = repository.pausePrint("p1")

        assertTrue(result is ApiResult.Success)
        assertEquals("p1", fakeApi.lastPrinterId)
    }

    @Test
    fun `resumePrint returns Success when API returns successful response`() = runTest {
        fakeApi.shouldSucceed = true
        val result = repository.resumePrint("p1")

        assertTrue(result is ApiResult.Success)
        assertEquals("p1", fakeApi.lastPrinterId)
    }

    @Test
    fun `cancelPrint returns Success when API returns successful response`() = runTest {
        fakeApi.shouldSucceed = true
        val result = repository.cancelPrint("p1")

        assertTrue(result is ApiResult.Success)
        assertEquals("p1", fakeApi.lastPrinterId)
    }

    @Test
    fun `refreshStatus returns Success when API returns successful response`() = runTest {
        fakeApi.shouldSucceed = true
        val result = repository.refreshStatus("p1")

        assertTrue(result is ApiResult.Success)
        assertEquals("p1", fakeApi.lastPrinterId)
    }

    @Test
    fun `clearHms returns Success when API returns successful response`() = runTest {
        fakeApi.shouldSucceed = true
        val result = repository.clearHms("p1")

        assertTrue(result is ApiResult.Success)
        assertEquals("p1", fakeApi.lastPrinterId)
    }

    @Test
    fun `clearPlate returns Success when API returns successful response`() = runTest {
        fakeApi.shouldSucceed = true
        val result = repository.clearPlate("p1")

        assertTrue(result is ApiResult.Success)
        assertEquals("p1", fakeApi.lastPrinterId)
    }

    @Test
    fun `setPrintSpeed returns Success when API returns successful response`() = runTest {
        fakeApi.shouldSucceed = true
        val result = repository.setPrintSpeed("p1", 3)

        assertTrue(result is ApiResult.Success)
        assertEquals("p1", fakeApi.lastPrinterId)
        assertEquals(3, fakeApi.lastSpeedMode)
    }

    @Test
    fun `returns Error when API returns HTTP 404 error`() = runTest {
        fakeApi.shouldSucceed = false
        fakeApi.errorCode = 404
        val result = repository.pausePrint("p1")

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(404, error.code)
        assertEquals(ApiErrorType.NOT_FOUND, error.errorType)
    }

    @Test
    fun `a refused tool sends nothing and returns the reason`() = runTest {
        val seen = mutableListOf<PrinterTool>()
        val refusing = object : PrinterToolGate {
            override suspend fun refusal(printerId: String, tool: PrinterTool, printerName: String): String? {
                seen += tool
                return "Developer LAN mode is off"
            }
        }
        val gated = PrintControlRepositoryImpl(fakeApi, refusing)
        val commands = listOf(
            PrinterControlCommand.Pause,
            PrinterControlCommand.Resume,
            PrinterControlCommand.Cancel,
            PrinterControlCommand.ClearHms,
            PrinterControlCommand.SetPrintSpeed(2),
            PrinterControlCommand.SetChamberLight(true),
            PrinterControlCommand.AmsLoadFilament(2),
            PrinterControlCommand.AmsUnloadFilament(2),
        )
        commands.forEach { assertEquals("Developer LAN mode is off", (gated.executeCommand("1", it) as ApiResult.Error).message) }
        assertEquals(null, fakeApi.lastPrinterId)
        assertTrue(fakeApi.feedCalls.isEmpty())
        assertEquals(
            listOf(
                PrinterTool.PAUSE_RESUME,
                PrinterTool.PAUSE_RESUME,
                PrinterTool.CANCEL_PRINT,
                PrinterTool.CLEAR_ERRORS,
                PrinterTool.PRINT_SPEED,
                PrinterTool.CHAMBER_LIGHT,
                PrinterTool.FEED_FILAMENT,
                PrinterTool.FEED_FILAMENT,
            ),
            seen,
        )
    }

    @Test
    fun `load and unload filament send once each to the named slot through the gate`() = runTest {
        assertTrue(repository.executeCommand("1", PrinterControlCommand.AmsLoadFilament(6)) is ApiResult.Success)
        assertTrue(repository.executeCommand("1", PrinterControlCommand.AmsUnloadFilament(6)) is ApiResult.Success)
        assertEquals(listOf("load 1 6", "unload 1 6"), fakeApi.feedCalls)
    }

    private class FakePrinterControlApi : PrinterControlApi {
        var shouldSucceed = true
        var errorCode = 500
        var lastPrinterId: String? = null
        var lastSpeedMode: Int? = null
        val feedCalls = mutableListOf<String>()

        override suspend fun pausePrint(id: String): Response<Unit> {
            lastPrinterId = id
            return if (shouldSucceed) Response.success(Unit) else Response.error(errorCode, "".toResponseBody(null))
        }

        override suspend fun resumePrint(id: String): Response<Unit> {
            lastPrinterId = id
            return if (shouldSucceed) Response.success(Unit) else Response.error(errorCode, "".toResponseBody(null))
        }

        override suspend fun cancelPrint(id: String): Response<Unit> {
            lastPrinterId = id
            return if (shouldSucceed) Response.success(Unit) else Response.error(errorCode, "".toResponseBody(null))
        }

        override suspend fun refreshStatus(id: String): Response<Unit> {
            lastPrinterId = id
            return if (shouldSucceed) Response.success(Unit) else Response.error(errorCode, "".toResponseBody(null))
        }

        override suspend fun clearHms(id: String): Response<Unit> {
            lastPrinterId = id
            return if (shouldSucceed) Response.success(Unit) else Response.error(errorCode, "".toResponseBody(null))
        }

        override suspend fun clearPlate(id: String): Response<Unit> {
            lastPrinterId = id
            return if (shouldSucceed) Response.success(Unit) else Response.error(errorCode, "".toResponseBody(null))
        }

        override suspend fun setPrintSpeed(id: String, mode: Int): Response<Unit> {
            lastPrinterId = id
            lastSpeedMode = mode
            return if (shouldSucceed) Response.success(Unit) else Response.error(errorCode, "".toResponseBody(null))
        }

        override suspend fun setChamberLight(id: String, on: Boolean): Response<Unit> = Response.success(Unit)

        override suspend fun amsLoad(id: String, trayId: Int): Response<Unit> {
            feedCalls += "load $id $trayId"
            return Response.success(Unit)
        }

        override suspend fun amsUnload(id: String, trayId: Int): Response<Unit> {
            feedCalls += "unload $id $trayId"
            return Response.success(Unit)
        }
    }
}
