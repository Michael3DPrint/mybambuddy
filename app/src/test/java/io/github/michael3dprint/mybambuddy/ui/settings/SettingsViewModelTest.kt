package io.github.michael3dprint.mybambuddy.ui.settings

import io.github.michael3dprint.mybambuddy.data.api.PrinterApi
import io.github.michael3dprint.mybambuddy.data.api.SystemApi
import io.github.michael3dprint.mybambuddy.data.api.dto.HealthResponse
import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterDto
import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterStatusDto
import io.github.michael3dprint.mybambuddy.data.api.dto.PrintersResponse
import io.github.michael3dprint.mybambuddy.data.api.dto.SystemInfoResponse
import io.github.michael3dprint.mybambuddy.data.prefs.SettingsRepository
import io.github.michael3dprint.mybambuddy.data.prefs.ThemeMode
import io.github.michael3dprint.mybambuddy.data.repo.ServerProbeApis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeSettingsRepository: FakeSettingsRepository
    private lateinit var fakeServerApis: FakeServerApis

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSettingsRepository = FakeSettingsRepository(
            initialServerUrl = "http://100.64.0.10:8000",
            initialApiKey = "my-secret-api-key",
            initialPollingInterval = 5,
            initialThemeMode = ThemeMode.SYSTEM,
        )
        fakeServerApis = FakeServerApis()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads preferences from repository`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(fakeSettingsRepository) { fakeServerApis.apis }
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("http://100.64.0.10:8000", state.serverUrl)
        assertEquals("my-secret-api-key", state.apiKey)
        assertEquals(5, state.pollingIntervalSeconds)
        assertEquals(ThemeMode.SYSTEM, state.themeMode)
    }

    @Test
    fun `onServerUrlChanged and onApiKeyChanged update state`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(fakeSettingsRepository) { fakeServerApis.apis }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onServerUrlChanged("http://new-server.tailnet.ts.net:8000")
        viewModel.onApiKeyChanged("new-key-456")

        val state = viewModel.uiState.value
        assertEquals("http://new-server.tailnet.ts.net:8000", state.serverUrl)
        assertEquals("new-key-456", state.apiKey)
    }

    @Test
    fun `onPollingIntervalChanged and onThemeModeChanged update state`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(fakeSettingsRepository) { fakeServerApis.apis }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onPollingIntervalChanged(10)
        viewModel.onThemeModeChanged(ThemeMode.DARK)

        val state = viewModel.uiState.value
        assertEquals(10, state.pollingIntervalSeconds)
        assertEquals(ThemeMode.DARK, state.themeMode)
    }

    @Test
    fun `toggleApiKeyVisibility flips state`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(fakeSettingsRepository) { fakeServerApis.apis }
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isApiKeyVisible)

        viewModel.toggleApiKeyVisibility()
        assertTrue(viewModel.uiState.value.isApiKeyVisible)

        viewModel.toggleApiKeyVisibility()
        assertFalse(viewModel.uiState.value.isApiKeyVisible)
    }

    @Test
    fun `testConnection success updates testResultSuccess and message`() = runTest(testDispatcher) {
        fakeServerApis.healthResponse = Response.success(HealthResponse(status = "ok"))
        fakeServerApis.systemInfoResponse = Response.success(SystemInfoResponse(appName = "Bambuddy Server", version = "2.1"))

        val viewModel = SettingsViewModel(fakeSettingsRepository) { fakeServerApis.apis }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.testConnection()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(true, state.testResultSuccess)
        assertTrue(state.testResultMessage?.contains("v2.1") == true)
        assertFalse(state.isLoading)
    }

    @Test
    fun `failed connection test does not overwrite saved settings`() = runTest(testDispatcher) {
        fakeServerApis.printersResponse = Response.error(401, "Unauthorized".toResponseBody())
        val viewModel = SettingsViewModel(fakeSettingsRepository) { fakeServerApis.apis }
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
    fun `testConnection failure sets error message`() = runTest(testDispatcher) {
        fakeServerApis.printersResponse = Response.error(401, "Unauthorized".toResponseBody())

        val viewModel = SettingsViewModel(fakeSettingsRepository) { fakeServerApis.apis }
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
    fun `choosing a theme mode is stored at once without pressing Save`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(fakeSettingsRepository) { fakeServerApis.apis }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onThemeModeChanged(ThemeMode.DARK)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ThemeMode.DARK, fakeSettingsRepository.themeMode.first())
        assertFalse(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `choosing wallpaper colours is stored at once without pressing Save`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(fakeSettingsRepository) { fakeServerApis.apis }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDynamicColorChanged(true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(fakeSettingsRepository.dynamicColor.first())
    }

    @Test
    fun `saveSettings persists all settings to repository`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(fakeSettingsRepository) { fakeServerApis.apis }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onServerUrlChanged("http://saved-url.ts.net:8000")
        viewModel.onApiKeyChanged("saved-key-777")
        viewModel.onPollingIntervalChanged(3)
        viewModel.onThemeModeChanged(ThemeMode.LIGHT)

        viewModel.saveSettings()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("http://saved-url.ts.net:8000", fakeSettingsRepository.getServerUrlSync())
        assertEquals("saved-key-777", fakeSettingsRepository.getApiKeySync())
        assertEquals(3, fakeSettingsRepository.pollingIntervalSeconds.first())
        assertEquals(ThemeMode.LIGHT, fakeSettingsRepository.themeMode.first())
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `the wallpaper colours switch is off by default and is saved when turned on`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(fakeSettingsRepository) { fakeServerApis.apis }
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.dynamicColor)

        viewModel.onDynamicColorChanged(true)
        viewModel.saveSettings()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, fakeSettingsRepository.dynamicColor.first())
    }

    @Test
    fun `a public plain-http address shows the warning and a tailnet address does not`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(fakeSettingsRepository) { fakeServerApis.apis }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onServerUrlChanged("http://example.com:8000")
        assertTrue(viewModel.uiState.value.urlWarning != null)
        viewModel.onServerUrlChanged("http://100.64.0.1:8000")
        assertEquals(null, viewModel.uiState.value.urlWarning)
    }

    private class FakeSettingsRepository(
        initialServerUrl: String,
        initialApiKey: String,
        initialPollingInterval: Int,
        initialThemeMode: ThemeMode,
    ) : SettingsRepository {
        private val _serverUrlFlow = MutableStateFlow(initialServerUrl)
        private val _apiKeyFlow = MutableStateFlow(initialApiKey)
        private val _pollingIntervalFlow = MutableStateFlow(initialPollingInterval)
        private val _themeModeFlow = MutableStateFlow(initialThemeMode)
        private val _dynamicColorFlow = MutableStateFlow(false)

        override val serverUrl: Flow<String> = _serverUrlFlow.asStateFlow()
        override val apiKey: Flow<String> = _apiKeyFlow.asStateFlow()
        override val pollingIntervalSeconds: Flow<Int> = _pollingIntervalFlow.asStateFlow()
        override val themeMode: Flow<ThemeMode> = _themeModeFlow.asStateFlow()
        override val dynamicColor: Flow<Boolean> = _dynamicColorFlow.asStateFlow()

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

        override suspend fun saveDynamicColor(enabled: Boolean) {
            _dynamicColorFlow.value = enabled
        }

        override suspend fun saveThemeMode(mode: ThemeMode) {
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
