# MyBambuddy project overview

MyBambuddy is an Android app (Kotlin, Jetpack Compose) that monitors and controls a Bambu Lab printer through a self-hosted Bambuddy server.
It also manages filament through Bambuddy's Spoolman passthrough, prints from the phone, and slices through Bambuddy's slicer sidecar.
The app talks only to the server address the user enters, plus an optional barcode lookup after consent; there is no service of ours.
This file gives the architecture, the data flow between areas, the safety model and the document map.
"PrintDeck" is only the name of the Printers tab; it survives in a few class names such as `PrintDeckApp`.

## What the app does

- **Printers:** status, camera, plain-language HMS errors, light, pause, resume, cancel, speed, and Remote control (Developer LAN mode) shown as Full or Limited.
- **Print from the phone:** a file or a MakerWorld link, the slice page, review with the slicer's estimate, then add to the queue or start.
- **Queue:** cards with a picture, a job page to change settings or re-slice, previous prints and run again.
- **Filament:** spool inventory, catalogue search, barcode and NFC (OpenTag3D on NTAG215), AMS units with humidity and drying, loading spools into slots.
- **Care:** alerts, maintenance, low-stock and usage, history and statistics, a home-screen widget, offline views and a tablet layout.
- **Setup:** a welcome screen, Setup help with Docker and Home Assistant OS guides, and Find Bambuddy on the home Wi-Fi.

## Architecture

Layers depend in one direction: screens on view models, view models on `domain/repo` interfaces, implementations in `data/`.

```text
 Bambuddy JSON
   -> DTO            data/api/dto   (every field nullable)
   -> mapper         domain/mapper
   -> domain model   domain/model   (non-null, safe fallbacks)
   -> repository     domain/repo interface, implemented in data/repo
   -> ViewModel      ui/*
   -> UiState -> Screen (Compose)
```

- `data/api`: small Retrofit interfaces per area, one shared OkHttp client with timeouts, a response size cap, no automatic resend of writes, and the token and auth interceptors.
- `data/repo`: the implementations, the offline-cache decorators (`CachingPrinterRepository` and its siblings), `DeveloperModeGuard` and `ReadinessCheckImpl`.
- `domain/policy`: pure rules with no I/O, for example `SlotLoadPolicy`, `ToolAvailability`, `ReadinessEvaluator`, `HmsExplainer`, `LowStockPolicy` and `ServerUrlPolicy`.
- `domain/model/PrinterTool.kt`: the one table of printer tools and which need Developer LAN mode.
- `ui/*`: one view model per screen; screens only render state. `di/AppContainer.kt` builds the repositories and `MainActivity` wires the view models.
- `platform/`: Android integrations for NFC, the barcode scanner, alerts and the widget.

## Data flow between areas

```text
 Screens <-> ViewModels <-> domain/repo interfaces
                                 |
        CachingXRepository decorators (JSON files)      offline views read the saved copy
                                 |
        WidgetFeedingPrinterRepository ---------------> WidgetSnapshotFileStore -> widget
                                 |
        live repositories -> Retrofit APIs -> shared OkHttp client
                                 |   (DynamicUrlInterceptor sets the server address,
                                 |    AuthInterceptor adds the API key)
                                 v
        Bambuddy (HTTP, JSON): printers, queue, library, archives, slicing, AMS, inventory
          |-- Spoolman, through Bambuddy's passthrough
          |-- slicer sidecar, through Bambuddy
        upcitemdb.com: barcode digits, only after consent

 Background:  AlertWorker (WorkManager) -> AlertRunner -> live repositories
                  -> AlertNotifier -> notification -> DeepLinks -> MainActivity
 Widget:      WidgetRefresher -> live printer repository -> WidgetSnapshotFileStore -> PrinterWidgetProvider
 Storage:     SettingsRepositoryImpl (DataStore plus the API key in encrypted storage),
              JSON cache files, SnapshotCache (camera images)
```

- The background alert check and the widget refresher read the live repositories, so a saved copy never hides an unreachable server from them.
- Control commands skip the cache and are never replayed.

## Safety model for printer commands

1. Every command that reaches the printer is a `PrinterTool` with `needsDeveloperMode`.
2. Buttons for an unavailable tool are disabled, from the same evaluator that draws the cards.
3. A repository gate (`PrinterToolGate`, implemented by `DeveloperModeGuard` over `ReadinessCheck`) re-reads state right before a command and refuses with a reason; it fails open when the state cannot be read.
4. A slot change uses a stricter preflight (`SlotLoadPolicy`) that fails closed and re-reads state right before the write.
5. Control calls are single attempts and are never retried.
6. After a slot change the slot is read back and the user is told what it shows.
7. An HTTP 200 alone is never reported as "worked".

## External systems

- **Bambuddy 1.3.0.** Collection URLs need a trailing slash.
  Print control is `print/pause`, `print/resume` and `print/stop`.
  `remaining_time` is in minutes.
  The camera snapshot and stream need a short-lived token when authentication is on.
- **Slot assignment is not only a record.** The assign endpoints save a record, then push type, colour, temperatures and a calibration reset to the printer over MQTT.
  A refused push does not fail the HTTP call, so success means "saved".
  See design 10.
- **Developer LAN mode** off means the printer rejects commands; reading status still works.
- **Busy states** are PREPARE, SLICING, RUNNING and PAUSE.
  IDLE, FINISH and FAILED are safe.
- **Spoolman 0.27.1** is reached through Bambuddy; the app learns its address from `GET spoolman/status`.
- **Tags** are NTAG215 (ISO 14443A) carrying OpenTag3D, the same bytes Bambuddy's SpoolBuddy writes.
  OpenPrintTag needs ISO 15693 tags and is not used.

## Decisions that shape the code

- Use Bambuddy's Spoolman passthrough, not Spoolman directly, except for the read-only SpoolmanDB catalogue search: one address and one set of credentials.
- Write OpenTag3D tags to NTAG215, because Spoolman links tags by UID and the format does not matter to it.
- Implement the offline view as decorators over the repositories; never cache or replay a control action.
- Block slot changes outright during a print, with an RFID spool, or with Developer LAN mode off, because prevention is wanted over warnings.
- Split the API into small interfaces per area, and give tests small fakes instead of one catch-all stub.

## Working on the project

```bash
./gradlew :app:ktlintSourcesFormat :app:check   # format, then style, warnings, tests, lint and standards checks
./gradlew :app:assembleDebug                     # the debug APK
adb install -r app/build/outputs/apk/debug/app-debug.apk   # install only; never drive the phone
```

1. Pick an item from `docs/OPEN_ITEMS.md`.
2. Write the design first if it is new behavior.
3. Code in small steps with tests, and get `:app:check` green.
4. Update the design, the open item and the ledger in the same change.
5. Re-read a file after ktlint reformats it before a scripted edit, and run Gradle from the repository root.

Reading order for a newcomer: `README.md`, this file, `docs/ENGINEERING_STANDARDS.md`, `docs/WRITING_STANDARDS.md`, then the design for what you are changing.

## Document map

| File | Purpose |
|---|---|
| `README.md` | what the app is, its limits and how to build it |
| `AGENTS.md` | instructions for AI agents; `CLAUDE.md` imports it |
| `SECURITY.md` | how to report a problem, the design limits and the scans |
| `THIRD_PARTY_NOTICES.md` | licences of the libraries the app ships |
| `LICENSE` | the Apache-2.0 licence |
| `docs/PROJECT_OVERVIEW.md` | this file |
| `docs/ENGINEERING_STANDARDS.md` | code rules, the definition of done and the build checks |
| `docs/WRITING_STANDARDS.md` | how documents are written |
| `docs/OPEN_ITEMS.md` | open work |
| `docs/TASK_LEDGER.md` | finished work |
| `docs/API_MAP.md` | every endpoint the app calls and how errors reach the screen |
| `docs/FILAMENT_RESEARCH.md` | verified facts about the filament APIs |
| `docs/guides/INSTANT_ALERTS.md` | steps to set up instant alerts |
| `docs/guides/LIVE_DATA_CHECKS.md` | checks to run on live data |
| `docs/doc-identifier-allowlist.txt` | names allowed in documents that the code does not declare |
| `docs/standards-allowlist.txt` | exceptions to the standards checks |
| `docs/design/00-architecture.md` | Architecture and shared building blocks |
| `docs/design/01-alerts.md` | Alerts |
| `docs/design/02-print-queue.md` | Print queue |
| `docs/design/03-hms-explainer.md` | Plain-language HMS explanations |
| `docs/design/04-offline-cache.md` | Offline view |
| `docs/design/05-maintenance.md` | Maintenance reminders |
| `docs/design/06-low-stock-usage.md` | Low stock and usage history |
| `docs/design/07-ui-refresh.md` | UI refresh, app icon, light and dark mode |
| `docs/design/08-api-split.md` | Split the Bambuddy API by area |
| `docs/design/09-slot-assignment-safety.md` | Safe AMS slot assignment |
| `docs/design/10-print-state-gate.md` | Print-state gate and slot reconfiguration |
| `docs/design/11-readiness-check.md` | Readiness check |
| `docs/design/12-ams-viewmodel-split.md` | AMS screen state holders |
| `docs/design/13-long-file-split.md` | Long files and large composables |
| `docs/design/15-ui-modernization.md` | UI tokens and shared components |
| `docs/design/16-caching-and-offline-policy.md` | Caching and offline policy |
| `docs/design/18-cleartext-and-url-warning.md` | Cleartext HTTP and the server address warning |
| `docs/design/19-small-items.md` | Small behaviors |
| `docs/design/20-server-diagnostics.md` | Server connection diagnostics |
| `docs/design/22-back-navigation.md` | Back navigation |
| `docs/design/23-feature-plan.md` | Feature overview |
| `docs/design/24-widget.md` | Home-screen widget |
| `docs/design/26-weigh-spool.md` | Weigh a spool and update its record |
| `docs/design/27-statistics.md` | Print statistics card on the History tab |
| `docs/design/28-tablet-layout.md` | Tablet and foldable layout |
| `docs/design/29-widget-boxes.md` | Widget boxes and title |
| `docs/design/30-printer-centric-navigation.md` | Printer-centric navigation |
| `docs/design/31-theme-applies-at-once.md` | Theme applies at once |
| `docs/design/32-printer-filament-and-tools-tab.md` | Printer filament and Tools tab |
| `docs/design/33-ams-slot-actions.md` | AMS slot actions |
| `docs/design/34-accessibility-fixes.md` | Accessibility |
| `docs/design/35-print-from-phone.md` | Print from the phone |
| `docs/design/36-slicing-readiness-check.md` | Slicing readiness check |
| `docs/design/37-tools-sections.md` | Tools tab as expandable status sections |
| `docs/design/38-print-entry-points.md` | Print entry points and MakerWorld source |
| `docs/design/39-reslice.md` | Slice for this printer |
| `docs/design/40-slice-page-and-options.md` | Slice page, options and scaling |
| `docs/design/41-queue-cards.md` | Queue cards |
| `docs/design/42-queue-job-page.md` | Queue job page |
| `docs/design/43-slice-memory-and-clear-errors.md` | Slice memory, clearing errors from the printer page, and the failed-print state |
| `docs/design/44-previous-prints-and-run-again.md` | Previous prints tab and Run again |
| `docs/design/45-ams-unit-popup-and-tools-diagnostics.md` | AMS unit popup, AMS under Tools, and Diagnostics grouping |
| `docs/design/47-setup-guides-welcome-scan-and-error-help.md` | Setup guides, welcome screen, Find Bambuddy and error help |
| `docs/design/48-ui-patterns-and-setup-help-layout.md` | UI patterns, and the Setup help and Settings layout |
| `docs/design/49-spool-locations.md` | Spool locations |
| `docs/design/filament-ams-drying-nfc-barcode.md` | Filament tab: spools, AMS, drying, NFC tags and barcode |

## Glossary

- **Bambuddy:** the self-hosted server that talks to the printer.
- **Spoolman:** the filament inventory server.
- **AMS:** Bambu's automatic filament system, in units of four slots.
- **HMS:** the printer's health and error codes.
- **MQTT:** the printer's command channel.
- **Developer LAN mode:** the printer setting that lets local software send commands.
- **RFID spool:** a Bambu spool the AMS reads from a tag.
- **OpenTag3D, OpenPrintTag:** NFC data formats for filament.
- **Preflight:** the checks before a slot change.
- **Readiness:** a printer's tooling mode (FULL, LIMITED, UNVERIFIED, OFFLINE, UNKNOWN).
- **Gate:** a check that refuses a command before it is sent.
