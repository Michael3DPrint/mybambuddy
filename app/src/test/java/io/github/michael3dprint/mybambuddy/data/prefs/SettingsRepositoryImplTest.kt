package io.github.michael3dprint.mybambuddy.data.prefs

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.michael3dprint.mybambuddy.util.LogSink
import io.github.michael3dprint.mybambuddy.util.PrintDeckLogger
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The class that keeps the server address and the encrypted API key. It runs against a temporary DataStore and an in-memory
 * stand-in for the encrypted store, so it can never touch the app's real settings.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryImplTest {
    @get:Rule val folder = TemporaryFolder()

    private val legacyKey = stringPreferencesKey("api_key_backup")
    private lateinit var original: LogSink

    @Before
    fun quietLogs() {
        original = PrintDeckLogger.sink
        PrintDeckLogger.sink = LogSink { _, _, _, _ -> }
    }

    @After
    fun restoreLogs() {
        PrintDeckLogger.sink = original
    }

    /** An in-memory SharedPreferences: only string values are used by the class under test. */
    private class MemoryPrefs : SharedPreferences {
        val values = mutableMapOf<String, String>()

        override fun getString(key: String?, defValue: String?): String? = values[key] ?: defValue

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            override fun putString(key: String?, value: String?) = apply { values[key.orEmpty()] = value.orEmpty() }

            override fun remove(key: String?) = apply { values.remove(key) }

            override fun clear() = apply { values.clear() }

            override fun commit() = true

            override fun apply() = Unit

            override fun putStringSet(key: String?, values: MutableSet<String>?) = this

            override fun putInt(key: String?, value: Int) = this

            override fun putLong(key: String?, value: Long) = this

            override fun putFloat(key: String?, value: Float) = this

            override fun putBoolean(key: String?, value: Boolean) = this
        }

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues

        override fun getInt(key: String?, defValue: Int) = defValue

        override fun getLong(key: String?, defValue: Long) = defValue

        override fun getFloat(key: String?, defValue: Float) = defValue

        override fun getBoolean(key: String?, defValue: Boolean) = defValue

        override fun contains(key: String?) = values.containsKey(key)

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }

    private class Setup(val repo: SettingsRepositoryImpl, val store: DataStore<Preferences>, val secure: MemoryPrefs?)

    private fun kotlinx.coroutines.test.TestScope.setup(
        secure: MemoryPrefs? = MemoryPrefs(),
        before: suspend (DataStore<Preferences>) -> Unit = {},
    ): Setup {
        val scope: CoroutineScope = backgroundScope
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(folder.root, "settings.preferences_pb") })
        kotlinx.coroutines.runBlocking { before(store) }
        return Setup(SettingsRepositoryImpl(store, { secure }, scope), store, secure)
    }

    private suspend fun Setup.plainValues(): Collection<Any> = store.data.first().asMap().values

    @Test
    fun `defaults before anything is saved`() = runTest(UnconfinedTestDispatcher()) {
        val s = setup()
        assertEquals("", s.repo.serverUrl.first())
        assertEquals("", s.repo.getServerUrlSync())
        assertEquals("", s.repo.apiKey.first())
        assertEquals("", s.repo.getApiKeySync())
        assertEquals(5, s.repo.pollingIntervalSeconds.first())
        assertEquals(ThemeMode.SYSTEM, s.repo.themeMode.first())
        assertFalse(s.repo.dynamicColor.first())
    }

    @Test
    fun `the server address is tidied when saved`() = runTest(UnconfinedTestDispatcher()) {
        val s = setup()
        s.repo.saveServerUrl("  192.168.1.5:8000// ")
        assertEquals("http://192.168.1.5:8000", s.repo.getServerUrlSync())
        assertEquals("http://192.168.1.5:8000", s.repo.serverUrl.first())
        s.repo.saveServerUrl("https://bambuddy.example/")
        assertEquals("https://bambuddy.example", s.repo.getServerUrlSync())
        s.repo.saveServerUrl("   ")
        assertEquals("", s.repo.getServerUrlSync())
    }

    @Test
    fun `the API key goes to the encrypted store and never to the plain settings`() = runTest(UnconfinedTestDispatcher()) {
        val s = setup()
        s.repo.saveApiKey("sekret-123")
        assertEquals("sekret-123", s.secure!!.values["api_key_secure"])
        assertEquals("sekret-123", s.repo.getApiKeySync())
        assertEquals("sekret-123", s.repo.apiKey.first())
        assertFalse(s.plainValues().any { it.toString().contains("sekret-123") })
    }

    @Test
    fun `a blank key does not replace a saved key unless forced`() = runTest(UnconfinedTestDispatcher()) {
        val s = setup()
        s.repo.saveApiKey("sekret-123")
        s.repo.saveApiKey("")
        assertEquals("sekret-123", s.repo.getApiKeySync())
        s.repo.saveSettings("http://h:1", "")
        assertEquals("sekret-123", s.repo.getApiKeySync())
        s.repo.saveApiKey("", forceDelete = true)
        assertEquals("", s.repo.getApiKeySync())
        assertEquals("", s.secure!!.values["api_key_secure"])
    }

    @Test
    fun `a blank key with nothing saved is accepted quietly`() = runTest(UnconfinedTestDispatcher()) {
        val s = setup()
        s.repo.saveApiKey("")
        assertEquals("", s.repo.getApiKeySync())
    }

    @Test
    fun `an old plaintext key is moved into encrypted storage and removed from the plain settings`() = runTest(UnconfinedTestDispatcher()) {
        val s = setup { store -> store.edit { it[legacyKey] = "old-key" } }
        assertEquals("old-key", s.secure!!.values["api_key_secure"])
        assertEquals("old-key", s.repo.getApiKeySync())
        assertNull(s.store.data.first()[legacyKey])
    }

    @Test
    fun `an old plaintext key never overwrites a key already in encrypted storage, but is still deleted`() =
        runTest(UnconfinedTestDispatcher()) {
            val secure = MemoryPrefs().also { it.values["api_key_secure"] = "current-key" }
            val s = setup(secure) { store -> store.edit { it[legacyKey] = "old-key" } }
            assertEquals("current-key", s.repo.getApiKeySync())
            assertEquals("current-key", secure.values["api_key_secure"])
            assertNull(s.store.data.first()[legacyKey])
        }

    @Test
    fun `without encrypted storage the key lives in memory only, never on disk`() = runTest(UnconfinedTestDispatcher()) {
        val s = setup(secure = null)
        s.repo.saveApiKey("sekret-123")
        assertEquals("sekret-123", s.repo.apiKey.first())
        assertFalse(s.plainValues().any { it.toString().contains("sekret-123") })
    }

    @Test
    fun `the other settings are saved and read back`() = runTest(UnconfinedTestDispatcher()) {
        val s = setup()
        s.repo.savePollingInterval(15)
        s.repo.saveThemeMode(ThemeMode.DARK)
        s.repo.saveDynamicColor(true)
        assertEquals(15, s.repo.pollingIntervalSeconds.first())
        assertEquals(ThemeMode.DARK, s.repo.themeMode.first())
        assertTrue(s.repo.dynamicColor.first())
    }

    @Test
    fun `saveSettings saves the address and the key together`() = runTest(UnconfinedTestDispatcher()) {
        val s = setup()
        s.repo.saveSettings("10.0.0.2:8000", "k-1")
        assertEquals("http://10.0.0.2:8000", s.repo.getServerUrlSync())
        assertEquals("k-1", s.repo.getApiKeySync())
    }
}
