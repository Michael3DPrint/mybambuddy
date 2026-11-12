package io.github.michael3dprint.mybambuddy.data.prefs

import android.content.Context
import androidx.core.content.edit
import io.github.michael3dprint.mybambuddy.domain.barcode.BarcodePrefs
import io.github.michael3dprint.mybambuddy.domain.barcode.OnlineLookupConsent

/** Stores the barcode consent choice. Not a secret, so plain app-private SharedPreferences. */
class BarcodePrefsImpl(context: Context) : BarcodePrefs {
    private val prefs = context.getSharedPreferences("barcode_prefs", Context.MODE_PRIVATE)

    override var onlineLookup: OnlineLookupConsent
        get() = OnlineLookupConsent.entries.firstOrNull { it.name == prefs.getString(KEY, null) } ?: OnlineLookupConsent.ASK
        set(value) {
            prefs.edit { putString(KEY, value.name) }
        }

    private companion object {
        const val KEY = "online_lookup"
    }
}
