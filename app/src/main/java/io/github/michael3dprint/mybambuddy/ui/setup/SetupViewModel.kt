package io.github.michael3dprint.mybambuddy.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.michael3dprint.mybambuddy.data.api.ApiClient
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.toUiErrorState
import io.github.michael3dprint.mybambuddy.data.prefs.SettingsRepository
import io.github.michael3dprint.mybambuddy.data.repo.ServerProbeApis
import io.github.michael3dprint.mybambuddy.data.repo.testServerConnection
import io.github.michael3dprint.mybambuddy.data.repo.toMessage
import io.github.michael3dprint.mybambuddy.domain.model.ErrorKind
import io.github.michael3dprint.mybambuddy.domain.model.UiErrorState
import io.github.michael3dprint.mybambuddy.domain.policy.ServerUrlPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Tests and saves the server address and API key entered on first run. */
class SetupViewModel(private val settingsRepository: SettingsRepository, private val apiSupplier: (() -> ServerProbeApis)? = null) :
    ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val initialUrl = settingsRepository.serverUrl.first()
            val initialKey = settingsRepository.apiKey.first()
            _uiState.update {
                it.copy(
                    serverUrl = initialUrl,
                    apiKey = initialKey,
                )
            }
        }
    }

    fun onServerUrlChanged(url: String) {
        _uiState.update {
            it.copy(
                serverUrl = url,
                urlWarning = ServerUrlPolicy.warning(url),
                isSaved = false,
                testResultSuccess = null,
                testResultMessage = null,
                testErrorState = null,
            )
        }
    }

    fun onApiKeyChanged(key: String) {
        _uiState.update {
            it.copy(
                apiKey = key,
                isSaved = false,
                testResultSuccess = null,
                testResultMessage = null,
                testErrorState = null,
            )
        }
    }

    fun toggleApiKeyVisibility() {
        _uiState.update {
            it.copy(isApiKeyVisible = !it.isApiKeyVisible)
        }
    }

    fun testConnection() {
        val currentUrl = _uiState.value.serverUrl.trim()
        val currentKey = _uiState.value.apiKey.trim()

        if (currentUrl.isEmpty()) {
            _uiState.update {
                it.copy(
                    testResultSuccess = false,
                    testResultMessage = "Please enter a valid Server URL.",
                    testErrorState = UiErrorState(
                        kind = ErrorKind.UNKNOWN,
                        title = "Missing Server URL",
                        message = "Please enter a valid Server URL to test connection.",
                        primaryActionLabel = "Retry",
                    ),
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    testResultSuccess = null,
                    testResultMessage = null,
                    testErrorState = null,
                    isSaved = false,
                )
            }

            val api = apiSupplier?.invoke()
                ?: ServerProbeApis.create(
                    ApiClient.createOkHttpClient({ currentUrl }, { currentKey }),
                )

            when (val result = testServerConnection(api)) {
                is ApiResult.Success -> {
                    // Save ONLY after a successful test, so a failed test never overwrites working settings.
                    settingsRepository.saveSettings(currentUrl, currentKey)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            testResultSuccess = true,
                            testResultMessage = result.data.toMessage(),
                            testErrorState = null,
                        )
                    }
                }
                is ApiResult.Error -> {
                    val errState = result.toUiErrorState()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            testResultSuccess = false,
                            testResultMessage = errState.message,
                            testErrorState = errState,
                        )
                    }
                }
            }
        }
    }

    fun saveSettings() {
        viewModelScope.launch {
            settingsRepository.saveSettings(
                url = _uiState.value.serverUrl,
                key = _uiState.value.apiKey,
            )
            _uiState.update {
                it.copy(
                    isSaved = true,
                    saveMessage = "Settings saved securely.",
                )
            }
        }
    }

    class Factory(private val settingsRepository: SettingsRepository, private val apiSupplier: (() -> ServerProbeApis)? = null) :
        ViewModelProvider.Factory {
        // Safe: this factory is only asked for the one ViewModel class it builds.
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SetupViewModel(settingsRepository, apiSupplier) as T
    }
}
