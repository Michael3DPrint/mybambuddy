package io.github.michael3dprint.mybambuddy.ui.setup

import io.github.michael3dprint.mybambuddy.data.api.PrinterApi
import io.github.michael3dprint.mybambuddy.data.api.SystemApi
import io.github.michael3dprint.mybambuddy.data.api.dto.HealthResponse
import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterDto
import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterStatusDto
import io.github.michael3dprint.mybambuddy.data.api.dto.PrintersResponse
import io.github.michael3dprint.mybambuddy.data.api.dto.SystemInfoResponse
import io.github.michael3dprint.mybambuddy.data.prefs.SettingsRepository
import io.github.michael3dprint.mybambuddy.data.repo.ServerProbeApis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeSettingsRepository: FakeSettingsRepository
    private lateinit var fakeServerApis: FakeServerApis

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSettingsRepository = FakeSettingsRepository(
            initialServerUrl = "http://100.64.0.1:8000",
            initialApiKey = "initial-api-key",
        )
        fakeServerApis = FakeServerApis()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads from settings repository`() = runTest(testDispatcher) {
        val viewModel = SetupViewModel(fakeSettingsRepository) { fakeServerApis.apis }
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("http://100.64.0.1:8000", state.serverUrl)
        assertEquals("initial-api-key", state.apiKey)
    }

    @Test
    fun `onServerUrlChanged updates state and resets test result`() = runTest(testDispatcher) {
        val viewModel = SetupViewModel(fakeSettingsRepository) { fakeServerApis.apis }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onServerUrlChanged("http://new-url.ts.net:8000")

        val state = viewModel.uiState.value
        assertEquals("http://new-url.ts.net:8000", state.serverUrl)
        assertNull(state.testResultSuccess)
    }

    @Test
    fun `toggleApiKeyVisibility flips visibility`() = runTest(testDispatcher) {
        val viewModel = SetupViewModel(fakeSettingsRepository) { fakeServerApis.apis }
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isApiKeyVisible)

        viewModel.toggleApiKeyVisibility()
        assertTrue(viewModel.uiState.value.isApiKeyVisible)

        viewModel.toggleApiKeyVisibility()
        assertFalse(viewModel.uiState.value.isApiKeyVisible)
    }

    @Test
    fun `testConnection with empty server url sets error`() = runTest(testDispatcher) {
        val viewModel = SetupViewModel(fakeSettingsRepository) { fakeServerApis.apis }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onServerUrlChanged("")
        viewModel.testConnection()

        val state = viewModel.uiState.value
        assertEquals(false, state.testResultSuccess)
        assertEquals("Please enter a valid Server URL.", state.testResultMessage)
    }

    @Test
    fun `testConnection success updates uiState with success message`() = runTest(testDispatcher) {
        fakeServerApis.healthResponse = Response.success(HealthResponse(status = "ok"))
        fakeServerApis.systemInfoResponse = Response.success(SystemInfoResponse(appName = "Bambuddy", version = "2.0"))

        val viewModel = SetupViewModel(fakeSettingsRepository) { fakeServerApis.apis }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.testConnection()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(true, state.testResultSuccess)
        assertTrue(state.testResultMessage?.contains("Bambuddy") == true)
        assertFalse(state.isLoading)
    }

    @Test
    fun `testConnection failure sets error message`() = runTest(testDispatcher) {
        fakeServerApis.printersResponse = Response.error(401, "Unauthorized".toResponseBody())

        val viewModel = SetupViewModel(fakeSettingsRepository) { fakeServerApis.apis }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.testConnection()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.testResultSuccess)
        assertTrue(
            state.testResultMessage?.contains("Invalid API key") == true || state.testResultMessage?.contains("permission denied") == true,
        )
        assertFalse(state.isLoading)
    }

    @Test
    fun `failed connection test does not overwrite saved settings`() = runTest(testDispatcher) {
        fakeServerApis.printersResponse = Response.error(401, "Unauthorized".toResponseBody())
        val viewModel = SetupViewModel(fakeSettingsRepository) { fakeServerApis.apis }
        testDispatcher.scheduler.advanceUntilIdle()
        val urlBefore = fakeSettingsRepository.getServerUrlSync()
        val keyBefore = fakeSettingsRepository.getApiKeySync()

        viewModel.onServerUrlChanged("http://bad.example:1")
        viewModel.onApiKeyChanged("bad-key")
        viewModel.testConnection()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(urlBefore, fakeSettingsRepository.getServerUrlSync())
        assertEquals(keyBefore, fakeSettingsRepository.getApiKeySync())
    }

    @Test
    fun `saveSettings persists server url and api key`() = runTest(testDispatcher) {
        val viewModel = SetupViewModel(fakeSettingsRepository) { fakeServerApis.apis }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onServerUrlChanged("http://192.168.1.100:8000")
        viewModel.onApiKeyChanged("saved-key-999")
        viewModel.saveSettings()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("http://192.168.1.100:8000", fakeSettingsRepository.getServerUrlSync())
        assertEquals("saved-key-999", fakeSettingsRepository.getApiKeySync())
        assertTrue(viewModel.uiState.value.isSaved)
    }

    private class FakeSettingsRepository(initialServerUrl: String, initialApiKey: String) : SettingsRepository {
        private val _serverUrlFlow = MutableStateFlow(initialServerUrl)
        private val _apiKeyFlow = MutableStateFlow(initialApiKey)
        private val _pollingIntervalFlow = MutableStateFlow(5)
        private val _themeModeFlow = MutableStateFlow(io.github.michael3dprint.mybambuddy.data.prefs.ThemeMode.SYSTEM)

        override val serverUrl: Flow<String> = _serverUrlFlow.asStateFlow()
        override val apiKey: Flow<String> = _apiKeyFlow.asStateFlow()
        override val pollingIntervalSeconds: Flow<Int> = _pollingIntervalFlow.asStateFlow()
        override val themeMode: Flow<io.github.michael3dprint.mybambuddy.data.prefs.ThemeMode> = _themeModeFlow.asStateFlow()

        override fun getServerUrlSync(): String = _serverUrlFlow.value
        override fun getApiKeySync(): String = _apiKeyFlow.value
        override suspend fun saveServerUrl(url: String) {
            _serverUrlFlow.value = url
        }

        override suspend fun saveApiKey(key: String, forceDelete: Boolean) {
            _apiKeyFlow.value = key
        }

        override suspend fun savePollingInterval(seconds: Int) {
            _pollingIntervalFlow.value = seconds
        }

        override suspend fun saveThemeMode(mode: io.github.michael3dprint.mybambuddy.data.prefs.ThemeMode) {
            _themeModeFlow.value = mode
        }

        override suspend fun saveSettings(url: String, key: String) {
            _serverUrlFlow.value = url
            _apiKeyFlow.value = key
        }
    }

    /** Fakes only the two narrow interfaces the connection test uses. */
    private class FakeServerApis {
        var healthResponse: Response<HealthResponse> = Response.success(HealthResponse(status = "ok"))
        var systemInfoResponse: Response<SystemInfoResponse> = Response.success(SystemInfoResponse(appName = "Bambuddy", version = "1.0"))
        var printersResponse: Response<PrintersResponse> = Response.success(PrintersResponse(printers = emptyList(), count = 0))
        var printerResponse: Response<PrinterDto> = Response.success(PrinterDto(id = "p1"))
        var printerStatusResponse: Response<PrinterStatusDto> = Response.success(PrinterStatusDto(state = "IDLE"))

        val apis = ServerProbeApis(
            object : PrinterApi {
                override suspend fun getPrinters(): Response<PrintersResponse> = printersResponse
                override suspend fun getPrinter(id: String): Response<PrinterDto> = printerResponse
                override suspend fun getPrinterStatus(id: String): Response<PrinterStatusDto> = printerStatusResponse
            },
            object : SystemApi {
                override suspend fun getHealth(): Response<HealthResponse> = healthResponse
                override suspend fun getSystemInfo(): Response<SystemInfoResponse> = systemInfoResponse
            },
        )
    }
}
