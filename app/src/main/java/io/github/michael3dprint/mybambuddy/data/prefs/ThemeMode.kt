package io.github.michael3dprint.mybambuddy.data.prefs

/** Light, dark, or follow the system. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        /** Reads a saved theme name; a blank or unknown value gives SYSTEM. */
        fun fromString(value: String?): ThemeMode {
            if (value.isNullOrBlank()) return SYSTEM
            return try {
                valueOf(value.uppercase())
            } catch (_: Exception) {
                SYSTEM
            }
        }
    }
}
