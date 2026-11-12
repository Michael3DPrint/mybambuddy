package io.github.michael3dprint.mybambuddy.data.prefs

import android.content.Context
import androidx.core.content.edit
import io.github.michael3dprint.mybambuddy.domain.alerts.AlertSettings
import io.github.michael3dprint.mybambuddy.domain.alerts.AlertSettingsSource

/** Which alert categories are on. Not secret, so plain app-private SharedPreferences. */
class AlertPrefs(context: Context) : AlertSettingsSource {
    private val prefs = context.getSharedPreferences("alert_prefs", Context.MODE_PRIVATE)

    override fun load(): AlertSettings = AlertSettings(
        enabled = prefs.getBoolean("enabled", false),
        printFinished = prefs.getBoolean("print_finished", true),
        printFailed = prefs.getBoolean("print_failed", true),
        newError = prefs.getBoolean("new_error", true),
        printerOffline = prefs.getBoolean("printer_offline", false),
        lowFilament = prefs.getBoolean("low_filament", true),
        maintenanceDue = prefs.getBoolean("maintenance_due", true),
    )

    /** Whether the user hid the instant-alerts help card on the Alerts screen. */
    var helpHidden: Boolean
        get() = prefs.getBoolean("help_hidden", false)
        set(value) = prefs.edit { putBoolean("help_hidden", value) }

    /** Saves the alert choices. */
    fun save(settings: AlertSettings) {
        prefs.edit {
            putBoolean("enabled", settings.enabled)
            putBoolean("print_finished", settings.printFinished)
            putBoolean("print_failed", settings.printFailed)
            putBoolean("new_error", settings.newError)
            putBoolean("printer_offline", settings.printerOffline)
            putBoolean("low_filament", settings.lowFilament)
            putBoolean("maintenance_due", settings.maintenanceDue)
        }
    }
}
