package io.github.michael3dprint.mybambuddy.domain.help

private const val READ = "2026-11-11"
private val COMPUTER = StepWhere.COMPUTER
private val PHONE = StepWhere.PHONE

private val firstPrinter = HelpSource("Bambuddy wiki: first printer", "https://wiki.bambuddy.cool/getting-started/first-printer/")
private val apiKeys = HelpSource("Bambuddy wiki: API keys", "https://wiki.bambuddy.cool/features/api-keys/")
private val authPage = HelpSource("Bambuddy wiki: authentication", "https://wiki.bambuddy.cool/features/authentication/")
private val spoolmanFeature = HelpSource("Bambuddy wiki: Spoolman", "https://wiki.bambuddy.cool/features/spoolman/")
private val slicerPage = HelpSource("Bambuddy wiki: Slicer API", "https://wiki.bambuddy.cool/features/slicer-api/")
private val androidInstall = HelpSource("Tailscale: install on Android", "https://tailscale.com/docs/install/android")
private val magicDns = HelpSource("Tailscale: MagicDNS", "https://tailscale.com/docs/features/magicdns")

/** The pages both install methods share (design 47): everything after the install. */
internal object SharedGuides {
    /** Every page this group holds, in reading order. */
    val all: List<HelpTopic> = listOf(firstRun(), spoolmanLink(), slicerLink(), network(), tailscalePhone(), connect())

    private fun firstRun() = HelpTopic(
        id = HelpTopicId.FIRST_RUN,
        title = "Bambuddy first run: printer and API key",
        summary = "Add your printer to Bambuddy, then make a key for this app.",
        steps = listOf(
            HelpStep(
                StepWhere.PRINTER,
                "Turn on LAN Only Mode and Developer Mode, and put an SD card in. Note the printer's IP address, serial number and access code.",
            ),
            HelpStep(
                COMPUTER,
                "Open Bambuddy in a browser. If it shows a Setup page, you can turn authentication on (a username and a password of at least 6 characters).",
            ),
            HelpStep(
                COMPUTER,
                "Go to the Printers page and choose Add Printer. Fill in the name, IP address, access code and serial number, then Save.",
            ),
            HelpStep(COMPUTER, "Wait for the connection indicator to turn green."),
            HelpStep(COMPUTER, "Go to Settings, API Keys, Create API Key. Name it, choose its permissions, and select Create."),
            HelpStep(COMPUTER, "Copy the key straight away: it is shown only once. Paste it into this app (Connect this app)."),
        ),
        sources = listOf(firstPrinter, apiKeys, authPage),
        readOn = READ,
        unverified = listOf(
            "Authentication is optional and the wiki does not say what the default is, so your server may or may not ask you to sign in.",
            "The printer-side menu for Developer Mode is not copied here; the wiki's printer pages cover it.",
        ),
    )

    private fun spoolmanLink() = HelpTopic(
        id = HelpTopicId.SPOOLMAN_LINK,
        title = "Link Spoolman in Bambuddy",
        summary = "Tell Bambuddy where Spoolman is, so this app can use your inventory.",
        steps = listOf(
            HelpStep(COMPUTER, "In Bambuddy open Settings, Filament, and find the Spoolman card."),
            HelpStep(
                COMPUTER,
                "Turn it on and enter Spoolman's address with port 7912, as the Bambuddy server sees it (not as your phone sees it).",
            ),
            HelpStep(COMPUTER, "Enter an API key only if your Spoolman needs one, then Test Connection and Save."),
        ),
        sources = listOf(spoolmanFeature),
        readOn = READ,
    )

    private fun slicerLink() = HelpTopic(
        id = HelpTopicId.SLICER_LINK,
        title = "Point Bambuddy at the slicer sidecar",
        summary = "Tell Bambuddy where the slicer sidecar is.",
        steps = listOf(
            HelpStep(COMPUTER, "In Bambuddy open Settings, Workflow, Slicer."),
            HelpStep(COMPUTER, "Pick your preferred slicer and turn on Use Slicer API."),
            HelpStep(
                COMPUTER,
                "Paste the sidecar address: port 3003 for OrcaSlicer, 3001 for Bambu Studio, on the computer or Home Assistant machine that runs it.",
            ),
        ),
        sources = listOf(slicerPage),
        readOn = READ,
        unverified = listOf(
            "The wiki says Settings, Workflow, Slicer; the project README says Settings, Slicer. Look for it under either.",
        ),
    )

    private fun network() = HelpTopic(
        id = HelpTopicId.NETWORK_ADVICE,
        title = "Network: Tailscale or home network",
        summary = "This app talks to Bambuddy over plain http. There is no HTTPS.",
        steps = listOf(
            HelpStep(PHONE, "Do not put the server on the open internet. Use your home network when you are home."),
            HelpStep(
                PHONE,
                "Use Tailscale to reach it away from home. Spoolman and the slicer sidecar have no sign-in at all, which is a second reason.",
            ),
            HelpStep(
                COMPUTER,
                "Want HTTPS anyway? Bambuddy's README shows a reverse proxy in front of it; that is outside this app's setup help.",
            ),
        ),
        sources = listOf(HelpSource("Bambuddy README", "https://github.com/maziggy/bambuddy"), spoolmanSecurity()),
        readOn = READ,
    )

    private fun spoolmanSecurity() = HelpSource("Spoolman wiki: Security", "https://github.com/Donkie/Spoolman/wiki/Security")

    private fun tailscalePhone() = HelpTopic(
        id = HelpTopicId.TAILSCALE_PHONE,
        title = "Tailscale on this phone",
        summary = "Install Tailscale on the phone and sign in to the same account as the server.",
        steps = listOf(
            HelpStep(PHONE, "Install the Tailscale app from your app store (Android 8.0 or later)."),
            HelpStep(PHONE, "Open it, choose Get Started, accept the VPN prompt and allow notifications."),
            HelpStep(PHONE, "Sign in with the same account the server uses."),
            HelpStep(
                PHONE,
                "Use the server's machine name (or its full name ending in the tailnet's domain) in this app's server address.",
            ),
        ),
        sources = listOf(androidInstall, magicDns),
        readOn = READ,
    )

    private fun connect() = HelpTopic(
        id = HelpTopicId.CONNECT_APP,
        title = "Connect this app",
        summary = "Enter the server address and API key.",
        steps = listOf(
            HelpStep(PHONE, "In Settings enter the server address, for example http://<machine-name>:8000, and paste the API key."),
            HelpStep(PHONE, "Choose Test connection. If it fails, use the Help link on the error."),
        ),
        sources = emptyList(),
        readOn = READ,
    )
}
