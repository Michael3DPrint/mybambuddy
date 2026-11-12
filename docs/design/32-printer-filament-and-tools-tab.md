# 32: Printer filament and Tools tab

A printer's page shows the filament loaded in its AMS on the Overview tab and moves tools, diagnostics and upkeep to the Tools tab.
The slot tiles are shared with the Filament tab, and a slot tap opens the slot sheet described in design 33.
Reading is a plain GET; nothing is sent unless a confirmed action says so.
Built.

## Decisions

- The printer page has three tabs: Overview, Queue and Tools (`PrinterTab`).
  The sections of the Tools tab are described in design 37.
- Overview shows one `AmsSlotRow` per AMS unit of this printer: the unit name with a gear that opens the unit sheet (design 45), then the slot tiles, all in one outlined card.
- `PrinterFilter.ams` filters the whole AMS list to this printer.
  The whole list is read because slot-change warnings (for example a spool already in another slot) need every unit.
- The slot tile and the slot sheet are shared composables, so the printer page and the Filament tab behave the same.
- The external spool holder is not part of the AMS data the app reads, so it is not listed.
- Overview lists and explains the errors on `HmsAdviceCard`; Clear HMS errors is offered there and on the Tools tab, with the same confirmation.
- The slot bar keeps one length and fades out from where the amount ends.
  Rejected: a bar that changes length, or one opacity for the whole bar, because neither shows the level.

## Design

```text
Overview                                     Tools tab
 ... status, camera, controls ...             [Overview][Queue][Tools]
 AMS 1 +-------+ +-------+ +-------+ +-------+   Printer Status (errors, Clear HMS errors)
 (gear)|  PLA  | |  PETG | | Empty | |  PLA  |   AMS (status line, drying)
       |  (1)  | |  (2)  | |  (o)3 | |  (4)  |   Diagnostics (connection, remote control, slicing)
       | ===== | | ===== | |       | | ===== |   Maintenance
       | ~800 g| | ~630 g| |   -   | | !~120 |
       +-------+ +-------+ +-------+ +-------+
```

Slot tile, top to bottom: the material, the colour swatch with the slot number inside, the bar, the estimated grams.
An empty slot shows "Empty", an outlined circle and a dash.

Data flow:

```text
 PrinterFilamentSection (Overview) / ToolsPage (Tools tab)
   -> AmsViewModel.refresh()
        -> AmsRepository.getAms()  (AmsRepositoryImpl: printers, then slot assignments, then each printer's AMS status)
        -> SpoolRepository.getSpools()  (spoolsById, for linked spools)
   -> AmsListState.Success -> PrinterFilter.ams(units, printerId) -> AmsSlotRow -> SlotTile (SlotFillRule, barStops)
 slot tap -> AmsViewModel.chooseSlot -> SlotPickerHost (design 33)
 SlotAssignmentViewModel.changed / AmsFilamentViewModel.changed / SlotWeightSyncViewModel.changed -> AmsViewModel.refresh()
 PrinterDetailViewModel (capabilities) -> ToolAvailability -> Tools tab controls and reasons
```

## Behavior

- Without an AMS the section says "No AMS reported for this printer." in one line.
  A failed read shows `ErrorNotice` with a retry; a refresh that fails keeps the last data.
  A printer whose status read fails is left out of the list, so it shows the same "No AMS" line.
- The estimate behind the bar and grams is `SlotFillRule.estimate`, in this order:
  1. A Bambu RFID slot with a printer percent: that percent of the linked spool's label weight, or of an assumed 1000 g.
     The printer measured it, so it beats an inventory record that may be stale.
  2. A linked inventory spool: its remaining grams over its label weight.
  3. A printer percent on an assumed 1000 g spool.
  4. Otherwise a dash.
- Grams always show a leading "~" because they are estimates.
- The bar is solid up to the fraction, fades to a tint of 10 percent opacity over the next 8 percent of its length, then stays faint.
  With no estimate it is faint all along.
- Low stock uses `LowStockPolicy` on the fill percent: the bar and grams turn red and a warning icon appears, so low stock is not shown by colour alone.
- The slot number is drawn in black or white, whichever reads on the swatch colour.
- Clear HMS errors asks first, is off with the reason when the tools rule says clearing is unavailable, and sends one command through `PrintControlRepositoryImpl.clearHms`, which checks the same rule.

## Tests

- Unit tests cover `SlotFillRule`, `barStops`, `PrinterFilter`, the scoped view models, and the clear command with its gate.
- How the tiles and the bar look on a device is not covered by tests.
