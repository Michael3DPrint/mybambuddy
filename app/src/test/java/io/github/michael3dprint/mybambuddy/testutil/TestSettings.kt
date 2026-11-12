package io.github.michael3dprint.mybambuddy.testutil

import io.github.michael3dprint.mybambuddy.data.prefs.SettingsRepository
import io.github.michael3dprint.mybambuddy.data.prefs.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Minimal in-memory settings for tests that only need a server URL. Prefer this over a new private copy in each test. */
class TestSettings(url: String) : SettingsRepository {
    private val u = MutableStateFlow(url)
    override val serverUrl: Flow<String> = u
    override val apiKey: Flow<String> = MutableStateFlow("")
    override val pollingIntervalSeconds: Flow<Int> = MutableStateFlow(5)
    override val themeMode: Flow<ThemeMode> = MutableStateFlow(ThemeMode.SYSTEM)

    override fun getServerUrlSync() = u.value

    override fun getApiKeySync() = ""

    override suspend fun saveServerUrl(url: String) {
        u.value = url
    }

    override suspend fun saveApiKey(key: String, forceDelete: Boolean) = Unit

    override suspend fun savePollingInterval(seconds: Int) = Unit

    override suspend fun saveThemeMode(mode: ThemeMode) = Unit

    override suspend fun saveSettings(url: String, key: String) {
        u.value = url
    }
}
