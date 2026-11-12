# MyBambuddy

[![check](https://github.com/Michael3DPrint/mybambuddy/actions/workflows/check.yml/badge.svg)](https://github.com/Michael3DPrint/mybambuddy/actions/workflows/check.yml)

MyBambuddy is an Android app for monitoring and controlling a Bambu Lab printer through your own [Bambuddy](https://github.com/maziggy/bambuddy) server.
It is a personal project written for one Bambu Lab X2D, tested against Bambuddy 1.3.0 and Spoolman 0.27.1.
It speaks plain HTTP and can send real commands to your printer, so read the warnings below before you use it.

**Unofficial.**
This project is not made by, affiliated with or endorsed by Bambu Lab, Bambuddy or Spoolman.
"Bambu Lab", "MakerWorld" and the other product names belong to their owners.
It is a separate program that talks to Bambuddy's HTTP API.
Bambuddy is licensed separately (AGPL-3.0) and is not part of this repository.

## Read this first

- **Personal project, one printer.**
  Many control features are covered by unit tests that use fakes and a local test server, and have **not** all been tried on a real printer.
  Use it at your own risk, and expect rough edges on other printers or other Bambuddy versions.
- **No HTTPS.**
  The app speaks plain HTTP to your server (Bambuddy's documentation describes port 8000 over HTTP, and HTTPS needs a reverse proxy in front).
  Use it on your home network or over a VPN such as [Tailscale](https://tailscale.com), and never put the server on the open internet.
  Spoolman and the slicer sidecar have no sign-in at all.
- **It can send real commands to your printer.**
  The commands are pause and resume, cancel, print speed, chamber light, drying, start a print, clear errors, and load or change AMS filament.
  All of these ask you to confirm first except the chamber light switch, which acts at once.
  Each command is sent once, with no automatic retry.
  The printer must have Developer LAN mode on for these controls to work.

## What it does

- Printers: live status, camera, print progress, errors explained in plain language, and clearing errors.
- Queue and print from the phone: upload a 3MF or import from a MakerWorld link, review the slice (time, plate, filament), start, pause or cancel, previous prints and run again, and change settings of a queued job.
- Slicing through Bambuddy's slicer sidecar: plate type, AMS filament choice, presets, options and scale.
- Filament: spool inventory (through Bambuddy and Spoolman), AMS slots and which spools are loaded, drying, NFC tags and barcode lookup.
- Alerts, maintenance reminders, history and statistics, a home-screen widget, and offline views of the last data.
- Setup help inside the app: Docker and Home Assistant OS guides, a welcome screen with checks, a way to find Bambuddy on your Wi-Fi, and troubleshooting pages linked from errors.

## Requirements

- A running Bambuddy server with your printer added (the app needs its address, and an API key if you have turned sign-in on).
- Android 8.0 or later.
- Optional: Spoolman, the Bambuddy slicer sidecar, Tailscale.

## Privacy

- The API key is stored in encrypted storage on the phone and is not logged.
- The app talks only to the server address you enter.
  The one other service is an optional barcode lookup (upcitemdb.com), which asks you first and sends only the barcode digits.
- The app has no analytics, no account and no server of ours.
  Barcode scanning uses Google Play services and ML Kit, which have their own terms (see `THIRD_PARTY_NOTICES.md`).

## Build

You need a JDK 25 (the toolchain setting in `gradle/gradle-daemon-jvm.properties` can download it).
You also need the Android SDK (`sdk.dir` in `local.properties`, or the `ANDROID_HOME` environment variable).

```bash
./gradlew :app:assembleDebug                    # debug APK in app/build/outputs/apk/debug
./gradlew :app:ktlintSourcesFormat :app:check   # formatting, unit tests, lint and the project's own standards checks
```

Dependencies are checked against `gradle/verification-metadata.xml`.
After you add or upgrade a dependency, regenerate the file:

```bash
./gradlew --write-verification-metadata sha256 :app:ktlintSourcesFormat :app:check :app:assembleDebug :app:assembleRelease
```

## Contributing, and agents

Read `AGENTS.md` first, then `docs/PROJECT_OVERVIEW.md` (reading order and document map).
The rules are in `docs/ENGINEERING_STANDARDS.md`.
The build enforces several of them: no `!!`, files at most 400 lines, public declarations documented, no dead code, no real server address anywhere.
It also enforces the UI patterns in `docs/design/48-ui-patterns-and-setup-help-layout.md`.
New behavior starts with a design note in `docs/design/`.
A change is done only when the check command above is green.
Live status is `docs/OPEN_ITEMS.md`, history is `docs/TASK_LEDGER.md`.

Never commit a server address, API key, personal name or machine-generated identity.
A secrets scanner such as gitleaks in a pre-commit hook is recommended.
The build itself fails on a real-looking server address (`StandardsEnforcementTest`).

## Security

See `SECURITY.md` for how to report a problem, the design limits (plain HTTP) and what has been scanned.

## License

Apache License 2.0, see `LICENSE`.
`THIRD_PARTY_NOTICES.md` summarizes third-party components and their licenses.
