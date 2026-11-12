# 11: Readiness check

The readiness check says which PrintDeck tools work on each printer, from the status Bambuddy already reports.
Each printer is in one of five modes: FULL (Developer LAN mode on), LIMITED (off), UNVERIFIED (not reported), OFFLINE, or UNKNOWN (status unreadable).
One pure evaluator decides the mode and the state of every tool, so the printer card, the Tools tab and the repository gates cannot disagree.
Built; it reads only (GET) and never sends a command.

## Decisions

- The "API" is an in-app domain interface, `ReadinessCheck`, because Bambuddy has no readiness endpoint.
  Bambuddy's `GET printers/{id}/diagnostic` is an active probe and is not used here (design 20).
- Reading status (`ReadinessCheckImpl`), deciding (`ReadinessEvaluator` and `ToolAvailability`, both pure) and showing (UI) are separate.
- Missing information never counts as full: unreadable status is UNKNOWN, and Developer LAN mode not reported is UNVERIFIED.
- The check does not replace the safety gates.
  A status read cannot prove the firmware will accept a command, so the slot-change preflight, the command gates and the read-back stay (design 10).
- The printer card reuses the status the Printers screen already polls, so the check adds no requests.
  Only the command gates and the queue read a fresh status.
- Display only: the app cannot change printer settings, so there is no fix button.

## Design

```text
 GET printers/{id}/status -> PrinterStatusDto -> toCapabilities() -> PrinterCapabilities
   -> ReadinessEvaluator.evaluate(id, name, caps) -> PrinterReadiness(mode, tools)
   -> DeveloperModeGuard -> ReadinessCheck.check(id) -> refusal text or send
   -> QueueViewModel.refreshStartBlocked -> ReadinessCheck.checkAll() -> START_QUEUED_PRINT per printer

 GET printers/{id}/status (polled) -> PrinterMapper -> Printer.capabilities
   -> ReadinessEvaluator.evaluate -> PrintersUiState.readiness -> PrinterCard RemoteControlLine
   -> ReadinessEvaluator.evaluate in PrinterDetailPane -> DiagnosticsGroup -> RemoteControlSection (Tools tab)
```

`DeveloperModeGuard` is injected as `PrinterToolGate` into the print-control, AMS, print-file and queue repositories.

The printer card shows one line; tapping it opens that printer's Tools tab:

```text
 [ ✓ Remote control: Full          > ]
 [ ! Remote control: Limited       > ]
 [ ! Remote control: Not confirmed > ]
 [ - Remote control: Offline       > ]
 [ - Remote control: Couldn't check > ]
```

In the Tools tab the Remote control section explains the mode in one sentence (`RemoteControlWords`).
When the status is known and the printer is not offline, it lists each tool that needs Developer LAN mode with a check, exclamation mark or cross and the reason.
`AmsScreen` shows a one-line `ToolsBanner` when Developer LAN mode is off on a printer.

## Behavior

Modes, from `ReadinessEvaluator.modeOf`:

| Mode | Condition | Effect |
|---|---|---|
| FULL | connected and `developer_mode == true` | Every tool |
| LIMITED | connected and `developer_mode == false` | Every tool that needs the mode is off. Monitoring, camera, history, inventory, NFC, barcode, maintenance, queue view, reorder and remove, alerts still work |
| UNVERIFIED | connected and `developer_mode` not reported | Tools stay on, each marked "may be refused" |
| OFFLINE | `connected == false` | Command tools off; cached read-only views |
| UNKNOWN | status could not be read | Nothing assumed; shown as "Couldn't check" |

Tools come from the `PrinterTool` enum; each entry carries `needsDeveloperMode`, and some need an AMS, drying support or an idle printer.
`ToolAvailability.statusOf` checks in this order: offline, Developer LAN mode off, no AMS, no drying support, drying while printing unsupported, printer busy (for tools that need idle), Developer LAN mode not reported (LIMITED).

Which tools need Developer LAN mode, from the Bambuddy 1.3.0 source (the files are not named here and were not re-checked):

| Tool | Reaches the printer by | Evidence |
|---|---|---|
| `PAUSE_RESUME`, `CANCEL_PRINT`, `CLEAR_ERRORS`, `PRINT_SPEED`, `CHAMBER_LIGHT`, `DRYING` | MQTT command | Inferred: the printer verifies every MQTT message (not verified) |
| `SLOT_CHANGE` | MQTT `ams_filament_setting` and `extrusion_cali_sel` | In the Bambuddy source: its own probe uses this command |
| `START_QUEUED_PRINT` | MQTT project_file | In the Bambuddy source: "without it, prints will not start" |
| `FEED_FILAMENT` | MQTT command | Inferred (not verified); also refused while a print runs (design 33) |
| `REFRESH_STATUS` | MQTT status request (read) | Not gated |
| `CLEAR_PLATE` | Local Bambuddy flag only | In the Bambuddy source; not gated |

Everything read-only (status, camera, history, spools, catalog, NFC, barcode, maintenance, queue list, alerts) does not use MQTT commands and is never gated.

Where it takes effect:

- UI: buttons for an unavailable tool are disabled and show the reason (printer controls, the HMS Clear button, AMS drying, queue Start per printer).
  Slot change stays tappable and explains itself through the preflight message.
- Repository: `DeveloperModeGuard` re-reads the status before a command that needs Developer LAN mode and refuses it with the same reason.
  If the status cannot be read it allows the command, because the command would fail with its own error and blocking on a status hiccup could stop a pause or cancel when it matters.
  Slot change uses the full preflight instead, which blocks when the status cannot be read.
- A queued print without a printer id is refused, because the server would pick the printer and readiness cannot be checked.
- Drying is unavailable while a print runs when the printer reports `supports_drying_while_printing == false`.

## Tests

- Evaluator: each mode, and UNKNOWN never maps to FULL.
- `ToolAvailability`: each limiting case carries a reason.
- `ReadinessCheckImplTest`, `DeveloperModeGuardTest` and `QueueViewModelTest`: fresh reads, fail-open guard, queue Start disabled per printer.
- Only a real printer shows whether the firmware refuses a command in a mode the check calls available.
