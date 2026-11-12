package io.github.michael3dprint.mybambuddy.domain.help

/** Where a setup step is done: the server computer, this phone, or the printer itself (design 47). */
enum class StepWhere(val label: String) {
    COMPUTER("On your computer"),
    PHONE("On this phone"),
    PRINTER("On the printer"),
}

/** How the server side is installed; only the install pages differ between the two (design 47, decision 1). */
enum class InstallMethod(val label: String) {
    DOCKER("Docker"),
    HA_OS("Home Assistant OS apps"),
}

/** One numbered step, with where it is done and optional text to copy. */
data class HelpStep(val where: StepWhere, val text: String, val command: String? = null)

/** A page the steps came from; the link opens it in the browser. */
data class HelpSource(val title: String, val url: String)

/** Every help page the app has. Troubleshooting pages start with `T_`. */
enum class HelpTopicId {
    INSTALL_BAMBUDDY_DOCKER,
    INSTALL_BAMBUDDY_HA,
    FIRST_RUN,
    SPOOLMAN_DOCKER,
    SPOOLMAN_HA,
    SPOOLMAN_LINK,
    SLICER_DOCKER,
    SLICER_HA,
    SLICER_LINK,
    NETWORK_ADVICE,
    TAILSCALE_SERVER_DOCKER,
    TAILSCALE_SERVER_HA,
    TAILSCALE_PHONE,
    CONNECT_APP,
    T_CANNOT_REACH,
    T_KEY_REJECTED,
    T_PRINTER_OFFLINE,
    T_DEVELOPER_MODE,
    T_SPOOLMAN,
    T_SLICER,
    T_CLOUD,
    T_PRINTER_REFUSES,
    T_SERVER_ERROR,
}

/**
 * One help page. [method] is null for a page both installs share. [community] marks a page that uses a community project, which then
 * carries [COMMUNITY_NOTICE]. [unverified] lists what the page could not confirm, shown on the page so nothing is hidden.
 */
data class HelpTopic(
    val id: HelpTopicId,
    val title: String,
    val summary: String,
    val steps: List<HelpStep>,
    val sources: List<HelpSource>,
    val readOn: String,
    val method: InstallMethod? = null,
    val community: Boolean = false,
    val unverified: List<String> = emptyList(),
)

/** Shown on every page that uses a community project (design 47, decision 3). */
const val COMMUNITY_NOTICE: String =
    "Community project. Not made, maintained or supported by the author of this app. Report problems to its own repository."
