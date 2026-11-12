# 36: Slicing readiness check

The Slicing section on a printer's Tools tab says whether server slicing in Bambuddy is ready and, for each failed check, what to do about it.
It reads three things through Bambuddy (its settings, its slicer presets and its printers) and applies the pure rule `SlicingRule`.
The section status is "Ready", "Ready, with notes", "Not ready" or "Couldn't check", and a "Start with" line names the first failed check.
Built; it only reads and never changes a setting or starts a slice.

## Decisions

- Every request is a GET to Bambuddy.
  The phone never calls the slicer app.
- The settings reply holds every Bambuddy setting, so the app parses four fields (use of the slicer API, preferred slicer, the two slicer addresses) and never logs or shows the rest.
  It records only whether an address is set, never the address.
- A failed request shows an error with a retry.
  Nothing is assumed ready.
- A missed printer preset is a warning, never a blocker on its own.
- The fix steps are fixed text in `SlicingHelp`.

## Design

```text
 Tools tab (ToolsPage) -> SlicingCheckViewModel.check()  (on open, on tab refresh, on Check again)
   -> SlicingRepository.check() -> SlicingRepositoryImpl
        -> SlicingApi.getSettings()  GET settings/        -> SlicerSettingsDto -> SlicerSettings
        -> SlicingApi.getPresets()   GET slicer/presets   -> SlicerPresetsDto  -> PresetSummary
        -> PrinterRepository.fetchPrinters()              -> PrinterRef (name, model)
        -> SlicingRule.evaluate(SlicingInputs)            -> SlicingReadiness
   -> SlicingState (Loading, Done, Error) -> SlicingSection inside DiagnosticsGroup
```

A failed request returns its error, and the section shows `ErrorNotice` with a retry.

```text
Slicing                                   Not ready
 ✓ Server slicing is switched on
 ✓ Slicer chosen: Bambu Studio
 ✗ Bambuddy can't reach the slicer app
     How to fix: 1. ... 2. ...   [Slicer help]
 ✓ Printer preset for X2D (X2D)
 i Bambu Cloud presets are available
 Checked 14:02. This reads what Bambuddy reports; it changes nothing.   [Check again]
```

## Behavior

| Check | Passes when | If it fails |
|---|---|---|
| Server slicing is switched on | the slicer API setting is true | blocker |
| A slicer is chosen | the preferred slicer is Bambu Studio or OrcaSlicer | blocker |
| Bambuddy can reach the slicer app | the standard tier's printer list is not empty | blocker |
| Printer preset, one per printer | a preset name in any tier contains the printer's model, ignoring case | warning |
| Process and filament presets | at least one of each exists in some tier | blocker |
| Bambu Cloud presets | the cloud status is ok | information only |

- The standard tier is the list bundled in the slicer app, so it is empty when Bambuddy cannot reach the app.
  The reach check cannot tell "unreachable" from "reachable but returned nothing", and its text says both.
- An empty slicer address is not a failure: Bambuddy then uses its built-in default, and the reach check shows whether that works.
- A printer whose model Bambuddy does not report gets an information line and is not checked.
- Ready means no failed blocker.
  `SlicingReadiness.forPrinter` limits the preset check to the printer whose page is open.
  The section status is "Ready", "Ready, with notes" when a warning failed, "Not ready", or "Couldn't check".
- Fix steps, from `SlicingHelp`:
  - Switch on or choose a slicer: in Bambuddy, Settings, Workflow tab, Slicer card (the Settings location is not verified against a Bambuddy release).
    Bambuddy saves by itself; there is no Save button.
  - Reach the slicer app: for Home Assistant OS, add the community repository, install and start the sidecar app, and set its address in the Slicer card to `http://<Home Assistant address>:<port>`.
    The port is 3001 for Bambu Studio and 3003 for OrcaSlicer.
    For Docker, run Bambuddy's `slicer-api` Compose stack.
    Sources: github.com/tzimpel/homeassistant-bambuddy-sidecar-apps (community Home Assistant apps, not made by this app), wiki.bambuddy.cool/features/slicer-api and github.com/maziggy/bambuddy/tree/main/slicer-api.
  - Missing presets: sign in to Bambu Cloud in Bambuddy, or import profiles exported from Bambu Studio.
- Not verified: that these steps work on every setup.

## Tests

- `SlicingRule` is tested for every row of the table.
- The response mapping is tested against trimmed recorded replies that hold no addresses or tokens, and the view model against its states.
- How the section looks on a device is not covered by tests.
