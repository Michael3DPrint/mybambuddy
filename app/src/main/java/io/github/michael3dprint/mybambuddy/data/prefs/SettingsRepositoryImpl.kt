package io.github.michael3dprint.mybambuddy.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.michael3dprint.mybambuddy.util.PrintDeckLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings_prefs")

/**
 * Settings kept in DataStore, with the API key in encrypted storage. The two stores are parameters so unit tests can use a temporary
 * DataStore and an in-memory stand-in for the encrypted store; the secondary constructor keeps the app's behaviour.
 *
 * @param store where the plain settings live.
 * @param encryptedStore opens the encrypted store for the API key, or returns null when the Android Keystore is unavailable.
 */
class SettingsRepositoryImpl(
    private val store: DataStore<Preferences>,
    private val encryptedStore: () -> SharedPreferences?,
    externalScope: CoroutineScope,
) : SettingsRepository {
    /** The app's real stores: the settings DataStore and the Keystore-backed encrypted file. */
    constructor(context: Context, externalScope: CoroutineScope = CoroutineScope(Dispatchers.IO)) :
        this(context.dataStore, { createEncryptedSharedPreferences(context) }, externalScope)

    /** Encrypted store for the API key. null if the Android Keystore is unavailable: then the key is kept in memory only, never in plaintext on disk. */
    private val sharedPreferences: SharedPreferences? by lazy { encryptedStore() }

    private val _apiKeyFlow = MutableStateFlow(readStoredApiKey())
    override val apiKey: Flow<String> = _apiKeyFlow.asStateFlow()

    @Volatile
    private var cachedServerUrl: String = ""

    @Volatile private var serverUrlLoaded = false

    override val serverUrl: Flow<String> = store.data.map { preferences ->
        val url = preferences[SERVER_URL_KEY] ?: ""
        cachedServerUrl = url
        serverUrlLoaded = true
        url
    }

    override val pollingIntervalSeconds: Flow<Int> = store.data.map { preferences ->
        preferences[POLLING_INTERVAL_KEY] ?: 5
    }

    override val dynamicColor: Flow<Boolean> = store.data.map { it[DYNAMIC_COLOR_KEY] ?: false }

    override val themeMode: Flow<ThemeMode> = store.data.map { preferences ->
        ThemeMode.fromString(preferences[THEME_MODE_KEY])
    }

    init {
        externalScope.launch {
            store.data.collect { preferences ->
                cachedServerUrl = preferences[SERVER_URL_KEY] ?: ""
                serverUrlLoaded = true

                // One-time migration: an older version kept a PLAINTEXT copy of the API key in DataStore.
                // Move it into the encrypted store (if that is empty) and delete the plaintext copy.
                val legacyPlaintextKey = preferences[API_KEY_BACKUP_KEY] ?: ""
                if (legacyPlaintextKey.isNotBlank()) {
                    val encrypted = try {
                        sharedPreferences?.getString(API_KEY_PREF_KEY, "") ?: ""
                    } catch (_: Exception) {
                        ""
                    }
                    if (encrypted.isBlank()) {
                        try {
                            sharedPreferences?.edit()?.putString(API_KEY_PREF_KEY, legacyPlaintextKey)?.apply()
                        } catch (e: Exception) {
                            PrintDeckLogger.e("PrintDeckTrace", "Failed to migrate API key to encrypted storage", e)
                        }
                        _apiKeyFlow.value = legacyPlaintextKey
                    }
                    store.edit { prefs -> prefs.remove(API_KEY_BACKUP_KEY) }
                }
            }
        }
    }

    override fun getServerUrlSync(): String {
        if (!serverUrlLoaded) {
            // Cold start: DataStore has not emitted yet. Only called from OkHttp background threads.
            cachedServerUrl = try {
                kotlinx.coroutines.runBlocking { store.data.first()[SERVER_URL_KEY] ?: "" }
            } catch (_: Exception) {
                ""
            }
            serverUrlLoaded = true
        }
        return cachedServerUrl
    }

    /** Reads the key from encrypted storage only. Safe to call during construction. */
    private fun readStoredApiKey(): String = try {
        sharedPreferences?.getString(API_KEY_PREF_KEY, "") ?: ""
    } catch (_: Exception) {
        ""
    }

    override fun getApiKeySync(): String = readStoredApiKey().ifBlank { _apiKeyFlow.value }

    override suspend fun saveServerUrl(url: String) {
        val formattedUrl = formatUrl(url)
        cachedServerUrl = formattedUrl
        serverUrlLoaded = true
        store.edit { preferences ->
            preferences[SERVER_URL_KEY] = formattedUrl
        }
    }

    override suspend fun saveApiKey(key: String, forceDelete: Boolean) {
        val existingKey = getApiKeySync().ifBlank { _apiKeyFlow.value }
        if (key.isBlank() && !forceDelete && existingKey.isNotBlank()) {
            PrintDeckLogger.w(
                "PrintDeckTrace",
                "Accidental overwrite safeguard: Blocked attempt to overwrite non-blank API key with blank key without forceDelete=true.",
            )
            return
        }

        _apiKeyFlow.value = key
        try {
            val secure = sharedPreferences
            if (secure != null) {
                secure.edit().putString(API_KEY_PREF_KEY, key).apply()
            } else {
                PrintDeckLogger.w("PrintDeckTrace", "Encrypted storage unavailable: API key kept in memory only for this session.")
            }
            // Never keep a plaintext copy on disk.
            store.edit { preferences -> preferences.remove(API_KEY_BACKUP_KEY) }
        } catch (e: Exception) {
            PrintDeckLogger.e("PrintDeckTrace", "Failed to save API key", e)
        }
    }

    override suspend fun savePollingInterval(seconds: Int) {
        store.edit { preferences ->
            preferences[POLLING_INTERVAL_KEY] = seconds
        }
    }

    override suspend fun saveDynamicColor(enabled: Boolean) {
        store.edit { preferences -> preferences[DYNAMIC_COLOR_KEY] = enabled }
    }

    override suspend fun saveThemeMode(mode: ThemeMode) {
        store.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }

    override suspend fun saveSettings(url: String, key: String) {
        saveServerUrl(url)
        saveApiKey(key)
    }

    private fun formatUrl(url: String): String {
        var trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://$trimmed"
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.dropLast(1)
        }
        return trimmed
    }

    companion object {
        private val SERVER_URL_KEY = stringPreferencesKey("server_url")
        private val POLLING_INTERVAL_KEY = intPreferencesKey("polling_interval_seconds")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
        private val API_KEY_BACKUP_KEY = stringPreferencesKey("api_key_backup")
        private const val API_KEY_PREF_KEY = "api_key_secure"

        private fun createEncryptedSharedPreferences(context: Context): SharedPreferences? = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Throwable) {
            // Fail closed: never fall back to an unencrypted file for the API key.
            PrintDeckLogger.e("PrintDeckTrace", "EncryptedSharedPreferences unavailable", e)
            null
        }
    }
}
