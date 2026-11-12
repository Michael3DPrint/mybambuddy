package io.github.michael3dprint.mybambuddy.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.michael3dprint.mybambuddy.domain.alerts.AlertSettings
import io.github.michael3dprint.mybambuddy.domain.policy.BambuddyAddress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the chosen settings are stored and applied (kept behind an interface so the screen is testable). */
interface AlertSettingsStore {
    fun load(): AlertSettings

    /** Saves the settings and switches the background check on or off to match. */
    fun apply(settings: AlertSettings)

    /** The saved server address, used to open Bambuddy's web page from the help card. */
    fun serverUrl(): String

    /** Whether the user hid the instant-alerts help card. */
    fun isHelpHidden(): Boolean

    /** Remembers whether the instant-alerts help card is hidden. */
    fun setHelpHidden(hidden: Boolean)
}

/** Holds the alert choices and saves each change through the store. */
class AlertSettingsViewModel(private val store: AlertSettingsStore) : ViewModel() {
    private val _settings = MutableStateFlow(store.load())
    val settings: StateFlow<AlertSettings> = _settings.asStateFlow()

    private val _helpHidden = MutableStateFlow(store.isHelpHidden())

    /** True once the user has hidden the instant-alerts help card. */
    val helpHidden: StateFlow<Boolean> = _helpHidden.asStateFlow()

    /** Where "Open Bambuddy" goes, or null when the saved server address cannot be turned into a web address. */
    val webAddress: String? = BambuddyAddress.webAddress(store.serverUrl())

    fun update(transform: (AlertSettings) -> AlertSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        store.apply(next)
    }

    /** Hides or shows the instant-alerts help card, and remembers the choice. */
    fun setHelpHidden(hidden: Boolean) {
        _helpHidden.value = hidden
        store.setHelpHidden(hidden)
    }

    class Factory(private val store: AlertSettingsStore) : ViewModelProvider.Factory {
        // Safe: this factory is only asked for the one ViewModel class it builds.
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AlertSettingsViewModel(store) as T
    }
}
