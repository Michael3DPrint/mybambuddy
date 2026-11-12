# 47: Setup guides, welcome screen, Find Bambuddy and error help

The app carries its own setup help.
It has install guides for Bambuddy, Spoolman, the slicer sidecar and Tailscale (Docker and Home Assistant OS), a welcome screen with read-only phone checks, Find Bambuddy on the home Wi-Fi, and a Help link on setup errors.
The app never installs or changes anything on the server; it shows steps, runs read-only checks and opens links.
Built, except the printer scan and some Help links; see OI-59.

## Decisions

- Two install methods, Docker and Home Assistant OS apps, share every page after the install: first run, Spoolman link, slicer link, network advice, Tailscale on the phone, connecting the app and troubleshooting.
- Server setup is done on a computer.
  Every step carries a tag from `StepWhere`: on your computer, on this phone, or on the printer.
  "Send to my computer" opens Android's share sheet with page titles and source links only (`HelpShare`), never an address, key or anything from the phone.
- The network advice is Tailscale or the home network.
  The app speaks plain HTTP to Bambuddy, so the server must not face the open internet; Spoolman and the slicer sidecar have no sign-in either.
- Community Home Assistant apps are named, and each page that uses one carries `COMMUNITY_NOTICE`.
  The notice says the project is community-made, not supported by this app's author, and that problems go to its own repository.
- The guides show one route per setup, not a menu of alternatives.
  Home Assistant OS: the community Tailscale app on the Home Assistant machine, with Bambuddy and the sidecar as apps there.
  Docker: Tailscale on the computer that runs Docker, Bambuddy on host networking, other services with published ports.
  Tailscale sidecar containers, Tailscale Serve and subnet routers are left out of the guides.
- Find Bambuddy uses its own HTTP client (`OkHttpHealthProbe`), which never carries the API key.

## Design

Layout of the help screens (the Settings split and tabs are in design 48):

```text
 First launch (no server saved)               Settings
 +--------------------------+      +--------------------------+
 | Welcome                  |      | Setup help               |--+
 |  checks, Find Bambuddy   |      | Find Bambuddy            |  |
 |  [I have an address]     |      +--------------------------+  |
 |  [guides]  --------------+--+                                 v
 +-----+--------------------+  |   +------------------------------+
       v                       +-->| Setup help: Guide | Fix a    |
 Server setup screen               |  problem | This phone        |
                                   +--------------+---------------+
 Error card --[Help]--> topic page <---------------+
```

Flow:

```text
 SetupGate -> WelcomeScreen -> [Find Bambuddy] -> sweep -> found: address prefilled in the setup screen
                |                                       -> none or denied: "type the address"
                +-> [I have an address and key] -> SetupScreen
                +-> [guides] -> Setup help (Guide tab)
 error card -> HelpButton -> ErrorHelp.topicFor(kind) -> topic page; unmapped kind -> "Fix a problem" tab
```

Data flow:

```text
 InstallGuides, SharedGuides, TroubleshootingTopics (static text, source URL, read date)
     -> HelpLibrary -> HelpIndexScreen / HelpTopicScreen   (opened through ProvideHelp: LocalOpenHelp, LocalOpenHelpTab)
 ErrorNotice -> HelpButton -> ErrorHelp.topicFor(ErrorKind) -> HelpTopicId -> HelpTopicScreen
 WelcomeScreen / "This phone" tab -> WelcomeViewModel -> NetworkInfo (AndroidNetworkInfo: Wi-Fi subnet, VPN transport, installed package)
     -> checkRows (words and level)
 WelcomeViewModel.find -> SubnetRules.hosts -> BambuddyFinder -> HealthProbe (OkHttpHealthProbe: GET http://host:8000/health) -> FindState
 Found host -> onUseHost / onUseAddress -> server address in the setup screen or in Settings (not saved until the user saves)
```

## Behavior

Help library:

- `HelpLibrary` joins `InstallGuides`, `SharedGuides` and `TroubleshootingTopics`.
  `guide(method)` returns the pages for one `InstallMethod` plus the shared ones, in reading order.
  A page with no method is shared.
- Each page has numbered steps, source links, a read date and an `unverified` list shown on the page, so gaps are visible.
- Every `HelpTopicId` has exactly one page; ids starting with `T_` are troubleshooting pages (`HelpTabs.forTopic` picks the tab).
- The troubleshooting pages are: cannot reach the server, key rejected, printer offline, Developer LAN mode off, Spoolman not connected, slicer unreachable, cloud not signed in, printer refuses the file, server error.
- The community notice appears on the four Home Assistant community pages (Bambuddy, Spoolman, slicer sidecar, Tailscale), marked by `HelpTopic.community`.

Error mapping (`ErrorHelp`, one table over `ErrorKind`):

- Network unreachable, not found and parse error go to cannot reach the server.
- Unauthorized and forbidden go to key rejected.
- Printer unavailable goes to printer offline, unsupported goes to Developer LAN mode off, action rejected goes to printer refuses the file, server error goes to server error.
- Unknown has no page and opens the "Fix a problem" tab.
- `ErrorNotice` is the shared error card and always shows the Help link.
  The slicing section links to the slicer page and the print-source steps link to the cloud page directly.

Welcome screen and checks:

- Three read-only checks: Wi-Fi connected, whether a VPN is on (`TRANSPORT_VPN`), and whether the Tailscale app is installed (`com.tailscale.ipn`, from store listings; the manifest declares it in `queries`).
  The VPN check says "a VPN is on", never "Tailscale is on".
- The same checks and Find Bambuddy appear in the "This phone" tab of Setup help.
- `NetworkVerdictRule` turns server address, install state, VPN state and reachability into one sentence in the Tailscale note in Settings (`TailscaleNoteCard`).

Find Bambuddy:

- `SubnetRules.hosts` lists the other addresses on the phone's own private IPv4 subnet (10/8, 172.16/12, 192.168/16); a larger network is cut to the /24 around the phone's address, and an unusable prefix gives an empty list.
- `BambuddyFinder` probes up to 40 hosts at a time on port 8000.
  `OkHttpHealthProbe` has a 400 ms connect timeout, no redirects and no retries, and accepts only a short JSON reply with `"status": "healthy"` (`BambuddyFingerprint`).
- Limits shown to the user: Wi-Fi only (a server reached over Tailscale is not found; type its address), default port only.
- On API 37 (Android 17) and later the sweep first asks for `ACCESS_LOCAL_NETWORK` (declared in the manifest); if refused it says so and asks for a typed address.
  Reading the Wi-Fi address needs only `ACCESS_NETWORK_STATE`.

## Verified facts behind the guide text

Read from the pages below; no page version was recorded, so a page may have changed since.

- Bambuddy Docker (https://wiki.bambuddy.cool/getting-started/docker/):
  - Download the project's `docker-compose.yml` and run `docker compose up -d`; the web page is on port 8000.
  - Host network mode is recommended and needed for printer discovery and the camera; Docker Desktop on Mac or Windows cannot use it.
- Bambuddy first printer, API keys and authentication (https://wiki.bambuddy.cool/getting-started/first-printer/, https://wiki.bambuddy.cool/features/api-keys/, https://wiki.bambuddy.cool/features/authentication/):
  - A printer needs name, IP, access code and serial number, plus an SD card and Developer Mode.
  - An API key is created under Settings, API Keys, shown once, and sent in the `X-API-Key` header.
- Bambuddy Spoolman and Slicer API (https://wiki.bambuddy.cool/features/spoolman/, https://wiki.bambuddy.cool/features/slicer-api/, https://github.com/maziggy/bambuddy/tree/main/slicer-api):
  - The sidecar runs from the `slicer-api` folder: OrcaSlicer on port 3003, Bambu Studio on 3001 with the `bambu` profile, health check at `/health`.
  - Ports 3000 and 3002 are reserved by the virtual printer.
  - The images are amd64 (arm64 is experimental and slow), and the sidecar has no authentication.
- Spoolman (https://github.com/Donkie/Spoolman/wiki/Installation, https://github.com/Donkie/Spoolman/wiki/Security): Docker compose, port 7912, a `data` folder owned by 1000:1000, no authentication.
- Home Assistant apps (https://www.home-assistant.io/common-tasks/os/apps/): a repository is added from Settings, Apps; Home Assistant cannot vouch for apps from other repositories.
- Community Bambuddy app (https://github.com/Spegeli/homeassistant-app-bambuddy):
  - It needs Home Assistant OS, uses host network and serves port 8000.
  - Its docs call the virtual-printer clash with the Mosquitto broker on port 8883 the most common conflict.
- Community slicer sidecar apps (https://github.com/tzimpel/homeassistant-bambuddy-sidecar-apps): ports 3001 and 3003, health check at `/health`.
- Community Spoolman app (https://github.com/bytenoodle/hassioaddon) and community Tailscale app (https://github.com/hassio-addons/app-tailscale).
- Tailscale (https://tailscale.com/docs/install/android, https://tailscale.com/docs/features/magicdns, https://tailscale.com/docs/install/linux):
  - Android 8.0 or later is needed, and MagicDNS gives machine names.
  - Tailscale addresses are in 100.64.0.0/10 and are not public.
- Android: a VPN on the active network reports `TRANSPORT_VPN`; apps targeting API 37 need `ACCESS_LOCAL_NETWORK` for local-network connections (Android's local-network page).

Not verified, and shown as "not confirmed" on the pages where it matters:

- Whether Bambuddy authentication is on by default.
- How the community sidecar apps handle internal ports against Bambuddy's reserved 3000 and 3002.
- The Mosquitto clash comes only from the community Bambuddy app's docs; the Docker guide has no such note.
- The printer menu path for Developer Mode.
- The Bytenoodle README names two data locations, so the guide does not state one.
- Whether the wiki path is Settings, Workflow, Slicer or Settings, Slicer.
- Whether Android counts a Tailscale address as local network.
- Tailscale's pages do not describe the Docker host-network route; it is ordinary host networking.

## Tests

- Unit tests cover the help library:
  - every topic id has one page, and every page has steps, a read date and sources (except the app's own pages);
  - no page holds a real address, and community pages carry the notice;
  - every `ErrorKind` maps to a page or the index, and shared text holds titles and links only.
- Unit tests cover the subnet host list, the health fingerprint, the finder with a fake probe, `WelcomeViewModel`, and every row of the verdict table.
- `OkHttpHealthProbe` has no unit test.
- Not testable here: the checks and the sweep on a real phone with Tailscale.

## Open work

The printer scan through Bambuddy's discovery routes is not built.
It is a server action, so it would run only after the user confirms the subnet shown.
Help links on the Spoolman "not connected" message and the filament catalog error, and the local-network permission outside the welcome and Find Bambuddy screens, are also open.
See OI-59.
