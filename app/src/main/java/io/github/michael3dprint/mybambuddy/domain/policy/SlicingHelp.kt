package io.github.michael3dprint.mybambuddy.domain.policy

/** The setup instructions shown when a slicing check fails (design 36). Plain text, kept in one place so it is easy to change. */
object SlicingHelp {
    /** The community Home Assistant slicer apps that Bambuddy's own community page lists. Not made or supported by this app's author. */
    const val APP_REPOSITORY = "https://github.com/tzimpel/homeassistant-bambuddy-sidecar-apps"

    /** Bambuddy's guide to the slicer sidecar. */
    const val WIKI = "wiki.bambuddy.cool/features/slicer-api"

    /** Steps to switch server slicing on. */
    fun switchOn(): List<String> = listOf(
        "In Bambuddy open Settings, then the Workflow tab.",
        "Find the Slicer card and switch on Use Slicer API. Bambuddy saves it by itself.",
        "Come back here and press Check again.",
    )

    /** Steps to choose a slicer. */
    fun chooseSlicer(): List<String> = listOf(
        "In Bambuddy open Settings, then the Workflow tab.",
        "In the Slicer card set Preferred Slicer to Bambu Studio or OrcaSlicer.",
        "Press Check again.",
    )

    /** Steps to get Bambuddy talking to the slicer app, for the slicer named [slicer] (for example "Bambu Studio") and its [port]. */
    fun reachSlicer(slicer: String, port: Int): List<String> = listOf(
        "Home Assistant OS: in Home Assistant open Settings, Apps, Install app, the top-right menu, Repositories, " +
            "and add $APP_REPOSITORY. These are community apps, not made or supported by this app's author.",
        "Install the $slicer sidecar app from that repository and start it. Its Log should show the server listening.",
        "In Bambuddy, Settings, Workflow tab, Slicer card, set the $slicer address to http://<your Home Assistant address>:$port.",
        "Docker: run Bambuddy's slicer-api Docker Compose stack and use its address. Guide: $WIKI",
        "Press Check again. Full steps: Settings, Setup help, then the slicer pages.",
    )

    /** Steps to get printer, process and filament presets into Bambuddy. */
    fun importPresets(): List<String> = listOf(
        "Bambuddy needs a printer, a process and a filament preset to slice.",
        "Sign in to Bambu Cloud in Bambuddy so its presets appear, or import profiles exported from Bambu Studio on your computer " +
            "(Bambuddy, Settings, Workflow tab, Slicer card).",
        "Press Check again.",
    )
}
