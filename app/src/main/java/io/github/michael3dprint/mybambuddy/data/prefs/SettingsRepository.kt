package io.github.michael3dprint.mybambuddy.data.prefs

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** The user's saved settings: server address, API key, polling interval and theme. The API key is stored encrypted. */
interface SettingsRepository {
    /** The saved server address. */
    val serverUrl: Flow<String>

    /** The saved API key, read from encrypted storage. */
    val apiKey: Flow<String>

    /** The saved polling interval in seconds. */
    val pollingIntervalSeconds: Flow<Int>

    /** The saved theme choice. */
    val themeMode: Flow<ThemeMode>

    /** Colours taken from the wallpaper (Android 12+). Off by default so the app's own palette holds. */
    val dynamicColor: Flow<Boolean> get() = flowOf(false)

    /** The server address for the HTTP layer, which cannot wait for a flow. */
    fun getServerUrlSync(): String

    /** The API key for the HTTP layer, which cannot wait for a flow. */
    fun getApiKeySync(): String

    /** Saves the server address. */
    suspend fun saveServerUrl(url: String)

    /**
     * Saves the API key to encrypted storage. A blank key does not replace a saved key unless [forceDelete] is true, so an empty
     * field cannot wipe it by accident.
     */
    suspend fun saveApiKey(key: String, forceDelete: Boolean = false)

    /** Saves the polling interval in seconds. */
    suspend fun savePollingInterval(seconds: Int)

    /** Saves the theme choice. */
    suspend fun saveThemeMode(mode: ThemeMode)

    /** Saves the wallpaper-colours choice. The default does nothing. */
    suspend fun saveDynamicColor(enabled: Boolean) {}

    /** Saves the server address and API key together. */
    suspend fun saveSettings(url: String, key: String)
}
