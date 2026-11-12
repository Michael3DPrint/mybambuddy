package io.github.michael3dprint.mybambuddy.ui.setup

import io.github.michael3dprint.mybambuddy.domain.model.UiErrorState

/** The values being entered on the Setup screen, the connection-test result, and the public-address warning if any. */
data class SetupUiState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val isApiKeyVisible: Boolean = false,
    val isLoading: Boolean = false,
    val testResultSuccess: Boolean? = null,
    val testResultMessage: String? = null,
    val testErrorState: UiErrorState? = null,
    val isSaved: Boolean = false,
    val saveMessage: String? = null,
    /** Shown under the URL field when the address is plain http outside a private network. */
    val urlWarning: String? = null,
)
