package io.github.michael3dprint.mybambuddy.ui.navigation

import io.github.michael3dprint.mybambuddy.AppTab

/** The names of the pages `MainActivity` switches between. One definition, used by the activity and by [BackPolicy]. */
object Routes {
    /** The tabbed home page. */
    const val MAIN = "main"

    /** First-run page where the server address is entered. */
    const val SETUP = "setup"

    /** Alert settings, opened from the Settings tab. */
    const val ALERTS = "alerts"

    /** One printer's details. */
    const val DETAIL = "detail"
}

/** What the Back button should do. */
enum class BackStep {
    /** Show the main page again. */
    GO_TO_MAIN,

    /** Select the Printers tab. */
    SELECT_PRINTERS,

    /** Nothing sits before this page: let the system close the app. */
    LEAVE_APP,
}

/** The rule for the system Back button (docs/design/22-back-navigation.md). Pure, so it is tested without a device. */
object BackPolicy {
    private val nestedPages = setOf(Routes.DETAIL, Routes.ALERTS)

    /**
     * Where Back goes from [route] with [tab] selected. A nested page returns to the main page, another tab returns to the Printers tab,
     * and the Printers tab, the setup page and any unknown route leave the app, so Back can never trap the user.
     */
    fun onBack(route: String, tab: AppTab): BackStep = when {
        route in nestedPages -> BackStep.GO_TO_MAIN
        route == Routes.MAIN && tab != AppTab.PRINTERS -> BackStep.SELECT_PRINTERS
        else -> BackStep.LEAVE_APP
    }
}
