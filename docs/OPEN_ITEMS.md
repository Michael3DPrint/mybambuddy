# Open items

This file lists work that is still open or in progress, each with what is left and when it is done.
Finished work is in `docs/TASK_LEDGER.md`.
The rules and the environment are in `AGENTS.md` and `docs/ENGINEERING_STANDARDS.md`.

| ID | Item | Who | Priority |
|---|---|---|---|
| OI-60 | Repository hardening that is left | agent and owner | medium |
| OI-59 | Setup help: printer scan and remaining Help links | agent | medium |
| OI-53 | Print flow follow-ups | agent | low |
| OI-22 | UI findings and the user-facing text policy | agent and owner | medium |
| OI-23 | Look and feel: fades on two lists | agent | low |
| OI-26 | Remove the unused dependency groups | agent | medium |
| OI-06 | Restrict cleartext HTTP | agent and owner | medium |
| OI-62 | Drying on the printer page does not refresh or report | agent | medium |
| OI-61 | Remove the layering departures | agent | low |
| OI-02 | Test limited mode | owner | medium |
| OI-17 | Run the instrumented UI tests | owner | low |
| OI-20 | Live-data checks | owner | low |
| OI-21 | Instant alerts | owner | low |
| OI-43 | Spool QR labels (parked) | none | low |

## OI-60 Repository hardening

The public repository has CI, branch protection, secret scanning with push protection, Dependabot alerts and private vulnerability reporting.
Left: confirm that CodeQL code scanning finds the Kotlin sources and runs, delete the old public slicer repository (needs the `delete_repo` permission), and uninstall the old `com.example.mybambuddy` app from the phone.
Done when those three are done.

## OI-59 Setup help

Setup help, the welcome screen, Find Bambuddy and the Help buttons are built (design 47).
Left:

- The printer scan through Bambuddy's discovery routes, behind a confirmation.
  It is a server action.
- Help links on the Spoolman "not connected" message and the filament catalogue error.
- Android's local-network permission outside the welcome and Find Bambuddy screens.
  On Android 17 a home-network address can be blocked until it is granted.

Done when these are built and tested.

## OI-53 Print flow follow-ups

Re-slice, the slice page, options, scale and slice memory are built (designs 35 and 38 to 40).
Left: the plate-type check once a sourced filament and plate table exists, a thumbnail on the review, an SD-card warning, an options screen, receiving a file from the Share sheet, and carrying a designer's own process settings into a slice.
Not verified: whether the slicer reports progress in the keys the app reads, so the dialog may show a moving bar without a percent.
Done when each is built with tests or dropped.

## OI-22 UI findings and the user-facing text policy

Left:

- Texts built from values (for example "$count printers") still live in code and need rewriting as formatted resources.
- String routes versus navigation3, which is tied to OI-26.
- Window insets, which need a check on a device.
- The text policy needs a decision.
  Older screens keep plain texts in string resources, and the print flow, queue job and Setup help screens use literals.
  Either convert them, or record that literals are accepted while the app is English only.

Done when each item is fixed or recorded as accepted in the design that owns it, and the text policy is decided.

## OI-23 Look and feel

The type and shape scales, spacing tokens, skeleton loaders and list animations are built (design 15).
Left: the same fade between loading, error and content on the Printers and Filament lists.
  Their branches read the state directly and need a rewrite.
Done when those two lists fade like the others.

## OI-26 Remove the unused dependency groups

No app code imports Room (runtime, ktx and its compiler), CameraX, navigation3, Compose adaptive, kotlinx-serialization or accompanist-permissions.
Removing them cuts build time, size and one service entry in the merged manifest.
They add no permissions.
Remove one group per commit while online, then run `:app:check` and regenerate `gradle/verification-metadata.xml`.
Removing them with Gradle offline downgrades transitive androidx libraries.
Done when every unused group is gone or has a one-line reason to stay.

## OI-06 Restrict cleartext HTTP

The manifest and `network_security_config.xml` allow cleartext to any host, because the server is a home-network or Tailscale address that cannot be listed in advance.
Options: a code-level check in `ServerUrlPolicy` plus a Settings switch that needs explicit consent for a public host (design 18).
Done when plain HTTP to a public host is refused or needs consent, and a tailnet or home-network setup still works.

## OI-62 Drying on the printer page does not refresh or report

Only `FilamentScreen` collects `DryingViewModel.changed` and `DryingViewModel.messages`.
Starting or stopping drying from the AMS popup or the Tools tab of a printer sends the command, but the unit list is not refreshed and no result message is shown there (design 45).
Collect both flows where `AmsDryingControls` is shown, then refresh the AMS list and show the message.
Done when drying started on the printer page updates the unit and reports the result, with a test.

## OI-61 Remove the layering departures

`docs/ENGINEERING_STANDARDS.md` section 2 names the departures from the layering.
Left:

- The Setup and Settings view models build the connection-test client themselves (`ApiClient`, `ServerProbeApis`).
- The Setup screens create `AndroidNetworkInfo` and `OkHttpHealthProbe` in composable code instead of receiving them from `AppContainer`.

Move the construction into `AppContainer` and give the view models interfaces.
Done when no view model or composable builds a data-layer class, and the standards sentence about it is removed.

## OI-02 Test limited mode

Turn Developer LAN mode off on the printer; the access code can change when it is toggled.
Confirm that:

- The Printers card shows the limited banner.
- Tools shows the controls off with the fix text.
- Pause, cancel, light, speed, clear errors, drying and queue Start are disabled.
- A slot change is refused with the Developer LAN reason.
- Monitoring, camera, history, inventory and NFC still work.

Turn it back on afterwards.
Done when the result is confirmed or differences are reported.

## OI-17 Run the instrumented UI tests

Gating tests in `app/src/androidTest` compile but need a device.
Run `./gradlew :app:connectedDebugAndroidTest` with the phone idle, because the tests open screens over the app.
Not covered yet: the load confirmation dialog, the detail-screen control buttons and the queue Start button.
Done when the tests have been run once.

## OI-20 Live-data checks

The print queue, usage history and the online barcode lookup were tested only against mocks and documented schemas.
`docs/guides/LIVE_DATA_CHECKS.md` lists the checks.
Done when the checks are run and mismatches are filed.

## OI-21 Instant alerts

Android runs the background alert check about every 15 minutes at best, so alerts can arrive late.
For instant alerts, set up ntfy or Pushover inside Bambuddy; `docs/guides/INSTANT_ALERTS.md` has the steps.
Nothing needs building in the app.
Done when the owner decides.

## OI-43 Spool QR labels

Parked.
It needs one small QR library (design 23).
Revisit only on request.
