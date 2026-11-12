# 45: AMS unit popup, AMS under Tools, and Diagnostics grouping

Each AMS unit on the printer page is one outlined box whose name and gear open a popup with the unit's humidity, temperature and drying controls.
The Tools tab has an AMS section per unit and one Diagnostics section that groups the connection test, Remote control and Slicing.
AMS drying is also on the Filament tab ("AMS & Drying"); all three places share one status line and one drying panel.
Built; the look, TalkBack and a real drying start have not been checked on a phone.

## Decisions

- The printer page popup, the Filament tab and Tools draw the same controls (`AmsStatusLine`, `AmsDryingControls`), so the gate and confirmation are one piece of code.
- Drying is a real printer command.
  It keeps its gate (`ToolAvailability` for the drying tool, and the unit must support drying), a confirmation naming the unit, material, temperature and hours, and one attempt.
- Connection test, Remote control and Slicing are sub-sections of one Diagnostics section to keep the Tools tab short.

## Design

```text
 Printer page, Filament area                        Tap the unit name -> popup (bottom sheet)
 +--------------------------------------------+     +------------------------------------+
 | AMS 1 (gear)   [PLA][PETG][ - ][ - ]       |     | <printer> · AMS 1                  |
 +--------------------------------------------+     | Humidity 24 %  ·  26.5 °C  ·  idle |
   one outlined box per unit; tapping a slot        | Drying   [presets] [temp] [hours]  |
   still opens the slot options                     |          [ Start drying ]          |
                                                    +------------------------------------+

 Tools tab
 [ Printer Status   1 error        v ]
 [ AMS 1            Humidity 24 %  v ]   one section per unit of this printer: status line and drying panel
 [ Diagnostics      All good       v ]
      Connection test (Run) / Remote control / Slicing   (nested sub-sections)
 Maintenance ...
```

```text
 PrinterFilamentSection -> AmsSlotRow(onUnit) -> AmsUnitSheet(unit, DryingViewModel)
 ToolsPage (PrinterDetailPane) -> AmsViewModel.refresh -> AmsRepository.getAms -> AmsListState.Success(units)
     -> PrinterFilter.ams(units, printerId) -> AmsToolsSection -> one StatusSection per unit (SectionStatuses.ams)
 drying panel -> DryingViewModel (confirm, one attempt) -> AmsRepository.startDrying or stopDrying
   -> changed event and messages: collected by FilamentScreen only (AmsViewModel.refresh, snackbar)
 ToolsPage -> DiagnosticsGroup(DiagnosticsViewModel, readiness, SlicingCheckViewModel) -> SectionStatuses.group
```

## Behavior

- The unit name is a 48 dp button with a gear; its spoken name is "AMS 1 settings".
- A unit that cannot dry says so.
  Nothing starts without the confirmation.
- The AMS section status (`SectionStatuses.ams`): a drying error is red; checking, drying (with the time left) and cooling are green; stopping is neutral; otherwise the humidity, or neutral "Humidity not reported".
- The Diagnostics header (`SectionStatuses.group`) is the worst of its parts: red with a count if any needs attention, amber if any needs a look, neutral if any is not checked yet, else good.
- The Remote control line on the printer card opens the Tools tab, where Remote control sits inside Diagnostics.
- `FilamentScreen` refreshes the AMS list and shows the message after a drying start or stop; the printer page and Tools do not collect those events.

## Tests

Unit tests cover the AMS section status for each state and the Diagnostics group's worst-of rule.
Only a device shows the box, the popup, the nested sections, TalkBack on the gear button and nested headers, and a real drying start, which is never sent as a test.
