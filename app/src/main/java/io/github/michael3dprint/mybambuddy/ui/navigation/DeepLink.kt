package io.github.michael3dprint.mybambuddy.ui.navigation

/** Where the app should go when it is opened from outside: a tapped notification today. */
sealed interface DeepLink {
    /** Open one printer's details. */
    data class Printer(val id: String) : DeepLink

    /** Open one spool's details on the Filament tab. */
    data class Spool(val id: Int) : DeepLink
}

/** Reads a [DeepLink] from the numbers an intent carries. Pure, so it is tested without Android. */
object DeepLinks {
    /** The intent extra holding a printer id. */
    const val EXTRA_PRINTER_ID = "printer_id"

    /** The intent extra holding a spool id. */
    const val EXTRA_SPOOL_ID = "spool_id"

    /**
     * The link for the ids found in an intent, or null when neither is a positive number (a missing extra is read as -1). A spool wins if
     * both are present. Only numbers are accepted, so nothing else from an intent can reach the app's navigation.
     */
    fun parse(printerId: Int, spoolId: Int): DeepLink? = when {
        spoolId > 0 -> DeepLink.Spool(spoolId)
        printerId > 0 -> DeepLink.Printer(printerId.toString())
        else -> null
    }
}
