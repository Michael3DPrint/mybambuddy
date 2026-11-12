package io.github.michael3dprint.mybambuddy.ui.settings

import io.github.michael3dprint.mybambuddy.data.prefs.ThemeMode
import io.github.michael3dprint.mybambuddy.domain.model.UiErrorState

/** The values being edited on the Settings screen, the connection-test result, and whether they are saved. */
data class SettingsUiState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val isApiKeyVisible: Boolean = false,
    val pollingIntervalSeconds: Int = 5,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    /** Set when the server address is a plain-HTTP public host (the same check as the Setup screen). */
    val urlWarning: String? = null,
    val isLoading: Boolean = false,
    val testResultSuccess: Boolean? = null,
    val testResultMessage: String? = null,
    val testErrorState: UiErrorState? = null,
    val isSaved: Boolean = false,
    val saveMessage: String? = null,
)
