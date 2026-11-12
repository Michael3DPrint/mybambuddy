package io.github.michael3dprint.mybambuddy.domain.help

private const val READ = "2026-11-11"
private val COMPUTER = StepWhere.COMPUTER

private val bambuddyDockerPage = HelpSource("Bambuddy wiki: Docker", "https://wiki.bambuddy.cool/getting-started/docker/")
private val slicerWikiPage = HelpSource("Bambuddy wiki: Slicer API", "https://wiki.bambuddy.cool/features/slicer-api/")
private val haAppsPage = HelpSource("Home Assistant: adding an app repository", "https://www.home-assistant.io/common-tasks/os/apps/")
private val spegeliRepo = HelpSource("Spegeli: BamBuddy app (community)", "https://github.com/Spegeli/homeassistant-app-bambuddy")
private val tzimpelRepo =
    HelpSource("tzimpel: slicer sidecar apps (community)", "https://github.com/tzimpel/homeassistant-bambuddy-sidecar-apps")
private val spoolmanWiki = HelpSource("Spoolman wiki: Installation", "https://github.com/Donkie/Spoolman/wiki/Installation")
private val spoolmanSecurity = HelpSource("Spoolman wiki: Security", "https://github.com/Donkie/Spoolman/wiki/Security")
private val bytenoodleRepo = HelpSource("ByteNoodle: Spoolman app (community)", "https://github.com/bytenoodle/hassioaddon")
private val tailscaleHaApp = HelpSource("Tailscale app for Home Assistant (community)", "https://github.com/hassio-addons/app-tailscale")
private val tailscaleLinux = HelpSource("Tailscale: install on Linux", "https://tailscale.com/docs/install/linux")

/** The install pages: the only pages that differ between Docker and Home Assistant OS (design 47). */
internal object InstallGuides {
    /** Every page this group holds, in reading order. */
    val all: List<HelpTopic> = listOf(
        bambuddyDocker(),
        bambuddyHa(),
        spoolmanDocker(),
        spoolmanHa(),
        slicerDocker(),
        slicerHa(),
        tailscaleDocker(),
        tailscaleHa(),
    )

    private fun bambuddyDocker() = HelpTopic(
        id = HelpTopicId.INSTALL_BAMBUDDY_DOCKER,
        title = "Install Bambuddy with Docker",
        summary = "Run Bambuddy on a computer that stays on, using Docker Compose.",
        steps = listOf(
            HelpStep(
                COMPUTER,
                "Docker with Compose must already be installed on the computer that will run Bambuddy (see Docker's own pages).",
            ),
            HelpStep(COMPUTER, "Make a folder for it and go into it.", "mkdir bambuddy && cd bambuddy"),
            HelpStep(
                COMPUTER,
                "Download Bambuddy's compose file.",
                "curl -O https://raw.githubusercontent.com/maziggy/bambuddy/main/docker-compose.yml",
            ),
            HelpStep(COMPUTER, "Start it.", "docker compose up -d"),
            HelpStep(COMPUTER, "Open Bambuddy in a browser on the same network: http://<your-computer>:8000"),
        ),
        sources = listOf(bambuddyDockerPage),
        readOn = READ,
        method = InstallMethod.DOCKER,
        unverified = listOf(
            "Host network mode is recommended (the wiki says it is required for printer discovery and the camera). " +
                "Docker Desktop on Mac or Windows cannot use it; the wiki has a separate bridge-mode section for that.",
        ),
    )

    private fun bambuddyHa() = HelpTopic(
        id = HelpTopicId.INSTALL_BAMBUDDY_HA,
        title = "Install Bambuddy on Home Assistant OS",
        summary = "Add the community repository, then install the BamBuddy app.",
        steps = listOf(
            HelpStep(COMPUTER, "In Home Assistant open Settings, Apps, Install app. Use Menu, Repositories, then Add."),
            HelpStep(COMPUTER, "Paste the repository address and select Add.", "https://github.com/Spegeli/homeassistant-app-bambuddy"),
            HelpStep(COMPUTER, "Install BamBuddy (the stable one, which the author recommends for most people) and start it."),
            HelpStep(COMPUTER, "Turn on Watchdog on the app's Info tab, so it restarts if it stops."),
            HelpStep(COMPUTER, "Open Bambuddy at http://<your-home-assistant>:8000. The port is fixed at 8000 and there is no Ingress."),
            HelpStep(
                COMPUTER,
                "Only if you also run the Mosquitto broker app: Bambuddy's virtual printer can clash with it on port 8883. " +
                    "The app's docs describe it; move one of them. This only matters if you use the virtual printer.",
            ),
        ),
        sources = listOf(haAppsPage, spegeliRepo),
        readOn = READ,
        method = InstallMethod.HA_OS,
        community = true,
        unverified = listOf(
            "Needs Home Assistant OS (apps need the Supervisor). Home Assistant says it cannot vouch for apps from other repositories.",
        ),
    )

    private fun spoolmanDocker() = HelpTopic(
        id = HelpTopicId.SPOOLMAN_DOCKER,
        title = "Install Spoolman with Docker",
        summary = "Spoolman keeps the filament inventory. It has no sign-in of its own.",
        steps = listOf(
            HelpStep(COMPUTER, "Make a folder called spoolman and go into it."),
            HelpStep(COMPUTER, "Copy the sample compose file from the Spoolman wiki into a file called docker-compose.yml."),
            HelpStep(COMPUTER, "Make a data folder next to it and give it to the container's user.", "mkdir data && chown 1000:1000 data"),
            HelpStep(COMPUTER, "Start it.", "docker compose up -d"),
            HelpStep(COMPUTER, "Open Spoolman at http://<your-computer>:7912."),
            HelpStep(COMPUTER, "Check that the data folder now holds spoolman.db. The wiki warns your data will be lost if it does not."),
        ),
        sources = listOf(spoolmanWiki, spoolmanSecurity),
        readOn = READ,
        method = InstallMethod.DOCKER,
        unverified = listOf(
            "Spoolman has no authentication. Keep it on your home network or Tailscale; never forward a port straight to it.",
        ),
    )

    private fun spoolmanHa() = HelpTopic(
        id = HelpTopicId.SPOOLMAN_HA,
        title = "Install Spoolman on Home Assistant OS",
        summary = "Spoolman as a community Home Assistant app.",
        steps = listOf(
            HelpStep(COMPUTER, "In Home Assistant open Settings, Apps, Install app, Menu, Repositories, Add."),
            HelpStep(COMPUTER, "Paste the repository address and select Add.", "https://github.com/bytenoodle/hassioaddon"),
            HelpStep(COMPUTER, "Install the Spoolman app and start it."),
            HelpStep(COMPUTER, "Open Spoolman at http://<your-home-assistant>:7912. The port is fixed."),
        ),
        sources = listOf(haAppsPage, bytenoodleRepo, spoolmanSecurity),
        readOn = READ,
        method = InstallMethod.HA_OS,
        community = true,
        unverified = listOf(
            "Not endorsed by the Spoolman project. Its README names two different places for the data, so where your data lives is not stated here.",
            "Spoolman has no authentication. Keep it on your home network or Tailscale.",
        ),
    )

    private fun slicerDocker() = HelpTopic(
        id = HelpTopicId.SLICER_DOCKER,
        title = "Install the slicer sidecar with Docker",
        summary = "Lets Bambuddy slice files for you (OrcaSlicer, and optionally Bambu Studio).",
        steps = listOf(
            HelpStep(COMPUTER, "In the Bambuddy project's slicer-api folder, copy the example settings.", "cp .env.example .env"),
            HelpStep(COMPUTER, "Start OrcaSlicer only (port 3003).", "docker compose up -d"),
            HelpStep(COMPUTER, "Or start both slicers (Bambu Studio on port 3001).", "docker compose --profile bambu up -d"),
            HelpStep(COMPUTER, "Check it answers.", "curl http://localhost:3003/health"),
            HelpStep(COMPUTER, "Do not use ports 3000 or 3002: Bambuddy's virtual printer reserves them."),
        ),
        sources = listOf(
            slicerWikiPage,
            HelpSource("Bambuddy repository: slicer-api", "https://github.com/maziggy/bambuddy/tree/main/slicer-api"),
        ),
        readOn = READ,
        method = InstallMethod.DOCKER,
        unverified = listOf(
            "The images are amd64. On arm64 the wiki calls running them experimental and 3 to 6 times slower.",
            "The sidecar has no sign-in. Keep it on your home network or Tailscale.",
        ),
    )

    private fun slicerHa() = HelpTopic(
        id = HelpTopicId.SLICER_HA,
        title = "Install the slicer sidecar on Home Assistant OS",
        summary = "The slicer sidecar as community Home Assistant apps, listed on Bambuddy's own community page.",
        steps = listOf(
            HelpStep(COMPUTER, "In Home Assistant open Settings, Apps, Install app, Menu, Repositories, Add."),
            HelpStep(
                COMPUTER,
                "Paste the repository address and select Add.",
                "https://github.com/tzimpel/homeassistant-bambuddy-sidecar-apps",
            ),
            HelpStep(
                COMPUTER,
                "Install Bambuddy Bambu Studio Sidecar (port 3001, amd64 only) or Bambuddy Orca Slicer Sidecar (port 3003), and start it.",
            ),
            HelpStep(COMPUTER, "The app shows its address on its Info tab; its health page is /health on that port."),
        ),
        sources = listOf(tzimpelRepo, HelpSource("Bambuddy wiki: community projects", "https://wiki.bambuddy.cool/community/")),
        readOn = READ,
        method = InstallMethod.HA_OS,
        community = true,
        unverified = listOf(
            "Works on the app owner's own Home Assistant OS setup; its files do not say how the port numbers inside it are handled.",
        ),
    )

    private fun tailscaleDocker() = HelpTopic(
        id = HelpTopicId.TAILSCALE_SERVER_DOCKER,
        title = "Tailscale on the Docker computer",
        summary = "Install Tailscale on the computer that runs Docker, so your phone can reach it from anywhere.",
        steps = listOf(
            HelpStep(COMPUTER, "Install Tailscale on that computer.", "curl -fsSL https://tailscale.com/install.sh | sh"),
            HelpStep(COMPUTER, "Sign in.", "sudo tailscale up"),
            HelpStep(
                COMPUTER,
                "Note the computer's Tailscale name (shown in the Tailscale admin console, Machines page) or run tailscale ip.",
            ),
            HelpStep(
                StepWhere.PHONE,
                "To confirm, open http://<that-name>:8000 in the phone's browser (Tailscale must be on, section Tailscale on this phone).",
            ),
        ),
        sources = listOf(tailscaleLinux),
        readOn = READ,
        method = InstallMethod.DOCKER,
        unverified = listOf(
            "Tailscale's pages do not describe reaching Docker containers this way. It is ordinary host networking: " +
                "Bambuddy on host network mode listens on the computer itself, so the computer's Tailscale name and port 8000 reach it.",
        ),
    )

    private fun tailscaleHa() = HelpTopic(
        id = HelpTopicId.TAILSCALE_SERVER_HA,
        title = "Tailscale on Home Assistant OS",
        summary = "The community Tailscale app, so your phone can reach Home Assistant's apps from anywhere.",
        steps = listOf(
            HelpStep(COMPUTER, "Install the Tailscale app from the Home Assistant community apps and start it."),
            HelpStep(COMPUTER, "Read the app's log, then open its Web UI to sign in and link Home Assistant to your Tailscale account."),
            HelpStep(COMPUTER, "Consider turning off key expiry for this machine in the Tailscale admin console."),
            HelpStep(StepWhere.PHONE, "Use the machine's Tailscale name with the app's port, for example port 8000 for Bambuddy."),
        ),
        sources = listOf(tailscaleHaApp),
        readOn = READ,
        method = InstallMethod.HA_OS,
        community = true,
        unverified = listOf(
            "Reaching other devices on your home network needs subnet routes approved in Tailscale; not needed for the apps on Home Assistant itself.",
        ),
    )
}
