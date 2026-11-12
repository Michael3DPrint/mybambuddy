# 49: Spool locations

The Spools list and the AMS slot sheet show which printer slot each spool is loaded in, for example "AMS 1, slot 3".
`SpoolLocations` builds the text for the Spools list; the slot sheet builds its per-slot wording with `whereLoaded` from the same AMS units.
Built; it reads only and sends nothing to the printer.

## Decisions

- The link from spool to slot is Bambuddy's slot assignments, which `AmsRepositoryImpl` reads into each tray as `loadedSpoolId`: `GET spoolman/inventory/slot-assignments/all` in Spoolman mode, `GET inventory/assignments` in inventory mode.
- `SpoolLocations.of` returns spool id to text.
  The printer name comes first only when more than one printer has units, and a spool in two slots lists both.
- The AMS list is read once when the Spools section opens, with no polling there; a failed read shows no location line and the spool list still works.
- Nothing is written to the server.

## Design

```text
 Spools tab                                    Slot sheet (tap a slot)
 +------------------------------------+        AMS 1 · Slot 1
 | (o) Bambu PLA Basic Blue           |        Current filament
 |     [printer] Loaded: AMS 1, slot 3|        In your inventory
 |     ===----  620 g left of 1000 g  |        (o) Bambu PLA Basic Blue    [printer] In AMS 1 slot 3
 +------------------------------------+        (o) Spare PLA Grey
```

```text
 FilamentScreen (Spools section opens) -> AmsViewModel.refresh
   -> AmsRepository.getAms -> AmsRepositoryImpl
        -> AmsApi.getSpoolmanSlotAssignments or AmsApi.getInventoryAssignments (by Spoolman mode) -> SlotAssignmentDto -> loadedSpoolId per AmsTray
   -> AmsListState.Success(units) -> SpoolLocations.of(units) -> spool id to text -> "Loaded: ..." on each spool card

 slot sheet (tap a slot) -> the units already loaded -> whereLoaded(units, slot)
   -> "In this slot" or "In AMS n slot m" per spool row
```

## Behavior

- A spool card that is not loaded shows nothing extra.
- In the slot sheet, `whereLoaded` says "In this slot" for the tapped slot and "In AMS 1 slot 4" for others.
- The slot sheet's current-filament line also names the inventory spool when one is linked.

## Tests

Pure-rule tests cover one printer, two printers (printer name included), a spool in two slots, and no assignments.
Only a device shows how both screens look.
