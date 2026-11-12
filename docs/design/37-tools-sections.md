# 37: Tools tab as expandable status sections

The Tools tab of a printer page is a list of expandable sections, each with a heading, a status (icon shape plus words) and a body that opens on tap.
The setting that decides whether the app may send commands to the printer is called "Remote control" (Full or Limited) on the printer card and in the Tools tab, from one set of words.
Built; the arrangement of the sections is in design 45, which puts Remote control and Slicing inside a Diagnostics section.

## Decisions

- **The name is "Remote control", with the values Full, Limited, Not confirmed, Offline and Couldn't check.**
  "Developer LAN mode" is the printer's setting; the app needs a name for what it decides: whether this app may send commands (pause, cancel, speed, light, filament moves, drying, starting a queued print).
  Monitoring, camera, history and inventory work either way, so "Capabilities: Limited" was rejected because it reads as if the printer is limited.
- **One name everywhere.** `RemoteControlWords` is the single source for the card line and the Tools section, so the same function carries the same label.
- **"Status" is not used for Remote control.** "Printer Status" is the health section and the card already shows the print state chip.
- **Remote control lists only controls that depend on it.** Always-available actions are not listed.
- **No separate all-printers tools screen.** Everything lives per printer in the Tools tab.
- **Status is never color alone.** Each level has an icon shape (check, exclamation mark, cross, dash) and a word.

## Design

The Tools tab shows these sections, collapsed by default:

| Section | Collapsed status | Expanded body |
|---|---|---|
| Printer Status | The worst HMS level with a count, a printer error, or No errors | The explained errors and Clear HMS errors (with its confirmation) |
| AMS units | Humidity, drying state | One section per unit (design 45) |
| Diagnostics | The worst of its three parts | Connection test with Run, Remote control, Slicing |
| Maintenance | Nothing due, or how many are due and soon | The items with Mark done (after its confirmation) |

```text
 Printer card                      Tools tab, collapsed             Remote control expanded
  X2D                 PRINTING     Printer Status   ✓ No errors     Remote control   ! Limited
  Remote control: Full  >          Diagnostics      ✓ All good      Developer LAN mode is off ...
                                   Maintenance      ✓ Nothing due    ✗ Chamber light  <reason>
```

```text
 PrinterReadiness (ReadinessEvaluator) -> RemoteControlWords -> card line (RemoteControlLine) and RemoteControlSection
 HmsStatus list + PrinterState -> SectionStatuses.printerStatus -> PrinterStatusSection
 AmsUnit -> SectionStatuses.ams -> AmsToolsSection (one StatusSection per unit)
 DiagnosticsViewModel state -> SectionStatuses.diagnostics --+
 SlicingCheckViewModel state -> SectionStatuses.slicing -----+--> SectionStatuses.group -> DiagnosticsGroup
 maintenance due and soon counts -> SectionStatuses.maintenance -> MaintenanceScreen
 every section -> StatusSection (heading, StatusMark, chevron, body)
 tap on the card's Remote control line -> MainActivity.toolsFor = ToolsRequest.afterLineTap
   -> PrinterDetailPane(openTools) -> Tools tab -> ToolsRequest.afterHandled clears it
```

## Behavior

- `StatusSection` is one button at least 48 dp tall.
  Its spoken text is "<title>, <status>" with Expanded or Collapsed, and it is a disclosure, not a menu.
  The expanded state is remembered by section title while the screen is open.
- `SectionStatuses` is pure; an unknown or unreported state is neutral, never good.
- `ToolsRequest` is a one-shot request that names one printer.
  Tapping the Remote control line raises it, the printer page uses it once, and tapping the card cancels it, so an ordinary tap never lands on Tools.
- Status icon colors come from the existing status colors and are checked at 3:1 against the card surfaces in light and dark by a build test.

## Tests

- Unit tests cover `RemoteControlWords`, the `SectionStatuses` rules and the `ToolsRequest` sequence.
- `ContrastRulesTest` covers the icon contrast.
- Not verified here: how the sections look and read with a screen reader on a device.
