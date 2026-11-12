package io.github.michael3dprint.mybambuddy.domain.help

private const val READ = "2026-11-11"
private val COMPUTER = StepWhere.COMPUTER
private val PHONE = StepWhere.PHONE

private val firstPrinter = HelpSource("Bambuddy wiki: first printer", "https://wiki.bambuddy.cool/getting-started/first-printer/")
private val apiKeys = HelpSource("Bambuddy wiki: API keys", "https://wiki.bambuddy.cool/features/api-keys/")
private val slicerPage = HelpSource("Bambuddy wiki: Slicer API", "https://wiki.bambuddy.cool/features/slicer-api/")
private val dockerPage = HelpSource("Bambuddy wiki: Docker", "https://wiki.bambuddy.cool/getting-started/docker/")

/** Troubleshooting pages: the same for both install methods, cheapest check first (design 47). */
internal object TroubleshootingTopics {
    /** Every page this group holds, in reading order. */
    val all: List<HelpTopic> = listOf(
        cannotReach(), keyRejected(), printerOffline(), developerMode(), spoolman(), slicer(), cloud(), refusesFile(), serverError(),
    )

    private fun cannotReach() = HelpTopic(
        id = HelpTopicId.T_CANNOT_REACH,
        title = "Cannot reach the server",
        summary = "The app could not get a Bambuddy answer from the address in Settings.",
        steps = listOf(
            HelpStep(PHONE, "Check the address: http, the machine name or IP, and port 8000 (for example http://<machine-name>:8000)."),
            HelpStep(
                PHONE,
                "Away from home? Tailscale must be on (the Tailscale app shows connected). At home, use the home-network address.",
            ),
            HelpStep(
                PHONE,
                "Open the same address in the phone's browser. A Bambuddy page means the app's address is fine; no page means the server is not reachable.",
            ),
            HelpStep(
                PHONE,
                "On Android 17 the phone may block home-network connections until Local network access is allowed for this app (Android Settings, Apps).",
            ),
            HelpStep(COMPUTER, "Docker: run docker compose ps in the Bambuddy folder; the container should be running."),
            HelpStep(COMPUTER, "Home Assistant OS: open the BamBuddy app's Info tab, start it, and read its log."),
            HelpStep(
                COMPUTER,
                "A page that is not Bambuddy, or a wrong-format reply, usually means the wrong port or something else answering on it.",
            ),
        ),
        sources = listOf(dockerPage),
        readOn = READ,
        unverified = listOf("Whether Android treats a Tailscale address as local network is not stated in Android's documentation."),
    )

    private fun keyRejected() = HelpTopic(
        id = HelpTopicId.T_KEY_REJECTED,
        title = "The server rejected the API key",
        summary = "The key is wrong, missing, or lacks permission for what you tried.",
        steps = listOf(
            HelpStep(PHONE, "Paste the key again with nothing before or after it."),
            HelpStep(
                COMPUTER,
                "In Bambuddy open Settings, API Keys. The key is shown once, so if it was lost, create a new one and paste that.",
            ),
            HelpStep(COMPUTER, "Check the key's permissions (for example Read Status and Control Printer) cover what you are doing."),
            HelpStep(COMPUTER, "Bambuddy reads the key from the X-API-Key header."),
        ),
        sources = listOf(apiKeys),
        readOn = READ,
    )

    private fun printerOffline() = HelpTopic(
        id = HelpTopicId.T_PRINTER_OFFLINE,
        title = "Printer offline or not connected",
        summary = "Bambuddy is running but cannot reach the printer.",
        steps = listOf(
            HelpStep(
                StepWhere.PRINTER,
                "Check the printer is on, on the network, with an SD card in, and with LAN Only Mode and Developer Mode on.",
            ),
            HelpStep(
                COMPUTER,
                "In Bambuddy check the printer's IP address, serial number and access code. The code changes when Developer Mode is switched.",
            ),
            HelpStep(COMPUTER, "Watch the connection indicator on the Printers page turn green."),
        ),
        sources = listOf(firstPrinter),
        readOn = READ,
    )

    private fun developerMode() = HelpTopic(
        id = HelpTopicId.T_DEVELOPER_MODE,
        title = "Control is switched off (Developer LAN mode)",
        summary = "Without Developer LAN mode, controls such as pause, cancel, light and drying are off. Monitoring still works.",
        steps = listOf(
            HelpStep(StepWhere.PRINTER, "Turn on LAN Only Mode and Developer Mode in the printer's network settings."),
            HelpStep(PHONE, "Open the printer's Tools page in this app and choose Re-check."),
        ),
        sources = listOf(firstPrinter),
        readOn = READ,
        unverified = listOf("The exact printer menu path is not copied here; follow the Bambuddy wiki and the printer's own screen."),
    )

    private fun spoolman() = HelpTopic(
        id = HelpTopicId.T_SPOOLMAN,
        title = "Spoolman is not connected",
        summary = "Bambuddy cannot reach Spoolman, so filament inventory is unavailable.",
        steps = listOf(
            HelpStep(COMPUTER, "Open Spoolman's own page (port 7912) from the Bambuddy computer to check it is running."),
            HelpStep(
                COMPUTER,
                "In Bambuddy, Settings, Filament, Spoolman: use the address the Bambuddy server can reach, with port 7912, then Test Connection.",
            ),
            HelpStep(COMPUTER, "Check the Spoolman switch is on in that card."),
        ),
        sources = listOf(HelpSource("Bambuddy wiki: Spoolman", "https://wiki.bambuddy.cool/features/spoolman/")),
        readOn = READ,
    )

    private fun slicer() = HelpTopic(
        id = HelpTopicId.T_SLICER,
        title = "The slicer is not reachable",
        summary = "Slicing needs the slicer sidecar, and Bambuddy must know where it is.",
        steps = listOf(
            HelpStep(COMPUTER, "Check the sidecar answers: open its /health page on port 3003 (OrcaSlicer) or 3001 (Bambu Studio)."),
            HelpStep(COMPUTER, "In Bambuddy, Settings, Workflow, Slicer: Use Slicer API is on and the address is right."),
            HelpStep(COMPUTER, "If the sidecar will not start, read its log for an address already in use. Do not use ports 3000 or 3002."),
            HelpStep(COMPUTER, "Big files can be refused for size; the sidecar's limit can be raised in its settings."),
        ),
        sources = listOf(slicerPage),
        readOn = READ,
        unverified = listOf("How the community Home Assistant apps handle their internal ports is not stated in their files."),
    )

    private fun cloud() = HelpTopic(
        id = HelpTopicId.T_CLOUD,
        title = "Bambu Cloud is not signed in",
        summary = "MakerWorld links and some slicer presets need the Bambu Cloud sign-in, which is done inside Bambuddy.",
        steps = listOf(
            HelpStep(COMPUTER, "Open Bambuddy and sign in to Bambu Cloud there."),
            HelpStep(PHONE, "Try the MakerWorld link or preset list again."),
        ),
        sources = emptyList(),
        readOn = READ,
        unverified = listOf("No Bambuddy page was read for the sign-in steps; use Bambuddy's own Settings."),
    )

    private fun refusesFile() = HelpTopic(
        id = HelpTopicId.T_PRINTER_REFUSES,
        title = "The printer refused the file or request",
        summary = "Bambuddy accepted it but the printer, or the server, refused.",
        steps = listOf(
            HelpStep(StepWhere.PRINTER, "Check the SD card is in and has free space."),
            HelpStep(COMPUTER, "Bambuddy needs the printer set to store sent files on the SD card or external storage."),
            HelpStep(PHONE, "Read the message under the error: it can name the exact reason."),
        ),
        sources = listOf(firstPrinter),
        readOn = READ,
        unverified = listOf("Based on a refusal seen on a real printer, not on a documented list of reasons."),
    )

    private fun serverError() = HelpTopic(
        id = HelpTopicId.T_SERVER_ERROR,
        title = "The server had an error",
        summary = "Bambuddy answered with an internal error.",
        steps = listOf(
            HelpStep(COMPUTER, "Docker: read the log with docker compose logs in the Bambuddy folder."),
            HelpStep(COMPUTER, "Home Assistant OS: read the BamBuddy app's log."),
            HelpStep(COMPUTER, "Restart Bambuddy and try again. If it keeps happening, report it to the Bambuddy project."),
        ),
        sources = listOf(dockerPage),
        readOn = READ,
    )
}
