# 33: AMS slot actions

Tapping an AMS slot, on the Filament tab or on a printer's page, opens one slot sheet with the actions for that slot.
The actions are: load or unload filament, scan an NFC tag, pick a spool from the inventory, update the inventory from the printer, and remove the slot's record.
The same `AmsSlotRow` tiles and the same `SlotPickerHost` serve both screens.
Load, unload, assignment and inventory update each ask first, send once with no retry, and refuse with a reason when not allowed.
Remove record clears a link in Bambuddy at once, without a confirmation, and sends nothing to the printer.
Built.

## Decisions

- One slot sheet in two labelled groups, "On the printer" and "In your inventory", so moving filament is not confused with changing records.
- The sheet stays open under every dialog it starts, so Cancel returns to it.
  It closes when a load, unload or inventory update is confirmed, when Remove record is tapped, when a load, unload or update message is shown (the message bar sits beneath the sheet), or when the user swipes it away.
- "Remove the record of this slot's spool" only clears the link in Bambuddy and says so; it moves no filament.
- Load and unload are real control commands: they feed or pull filament and may heat the nozzle.
  They are one tool, `FEED_FILAMENT`, in the tools rules, so they follow the rules of slot changes: Developer LAN mode on, printer reachable, an AMS, and no print running.
- Update from printer matches a spool only by the tray UUID the slot reports, never by guesswork.
- One shared `ConfirmDialog` draws every two-button confirmation in the app.

## Design

```text
AMS 1 · Slot 3          (swatch) PLA Basic · spool: ... · ~750 g left
 ON THE PRINTER
  [Load filament] [Unload filament]    off with a reason when not allowed; asks first
 IN YOUR INVENTORY
  [Scan NFC tag to load a spool]       only when the phone has NFC
  Search your spools ____
  (swatch) Bambu Lab PLA Basic   750 g left · Blue   [In AMS 1 slot 2]
  [Update inventory from printer]      only when the slot reports a tray UUID
  Remove the record of this slot's spool
```

Flow:

```text
 tap slot -> AmsViewModel.chooseSlot -> slot sheet
   Load / Unload -> AmsFilamentViewModel.request (AmsTrayAddress, ToolAvailability) -> refusal message, or confirm dialog
        -> confirm -> PrintControlRepository.executeCommand (gated, fresh status read) -> message -> changed -> refresh
   Scan NFC -> SlotNfcScanViewModel.scan -> tag -> spool found -> SlotAssignmentViewModel.requestLoad; no spool: message
   tap spool -> SlotAssignmentViewModel.requestLoad -> checks -> ConfirmLoadDialog -> confirm -> AmsRepository.loadSpool -> changed -> refresh
   Update -> SlotWeightSyncViewModel.prepare (WeightSyncRule) -> refusal message, or confirm -> SpoolRepository.updateWeight -> changed -> refresh
   Remove record -> SlotAssignmentViewModel.unload -> AmsRepository.unloadSpool
```

Data flow:

```text
 AmsRepositoryImpl.getAms (printers, AMS status, loaded spools) + SpoolRepository.getSpools -> AmsViewModel (units, spoolsById)
 AmsFilamentViewModel -> PrintControlRepositoryImpl.amsLoad / amsUnload -> PrinterControlApi POST printers/{id}/ams/load | unload ?tray_id=
 SlotWeightSyncViewModel -> SpoolRepositoryImpl.updateWeight -> InventoryApi PATCH spoolman/inventory/spools/{id}/weight
 each view model's changed event -> AmsViewModel.refresh
```

## Behavior

- **Tray address.** `AmsTrayAddress.forFeed` gives the printer's tray number: unit times four plus the slot for a regular AMS, the unit id (128 to 135) for an AMS HT.
  Any other unit gets no number, and its Load and Unload buttons are off.
- **Load and unload.** The dialog says, for example, "This feeds filament from X2D · AMS 1 · slot 3 into the printer."
  There is no read-back; the slots are read again after the command is accepted.
- **Update from printer** (`WeightSyncRule`).
  It refuses, with the reason, when:
  - the slot's tray UUID is not a 32-character non-zero id (a third-party tag);
  - the printer reports no percent or 0;
  - the inventory is not Spoolman (the built-in inventory syncs itself);
  - the printer is printing;
  - no inventory spool, or more than one, carries the tray UUID;
  - the slot's recorded spool is a different spool;
  - the spool's label or core weight is unknown;
  - the inventory already matches.
  Otherwise the dialog shows what the inventory says and what the printer says, and one weight update is sent per tap.
  The value sent is the new remaining grams (label weight times percent) plus the core weight, because the weight route takes a gross reading.
  The printer's percent is coarse, about 10 g steps on a 1 kg spool.
- **Scan NFC.** A tag that belongs to a spool goes straight to the usual assignment check and confirmation for this slot.
  A tag no spool carries shows a message and does nothing.
- **Assignment confirmation** (`ConfirmLoadDialog`) shows the spool (color, material, grams left of label weight, location) and the target slot with its current content.
  It also says what will happen (the inventory records the spool; Bambuddy may set the slot on the printer) and lists any warnings from the assignment policy.
  Both entry points, a picked spool and a scanned tag, reach it through the same check.
- **Confirm dialog.** Buttons are end-aligned with Cancel on the left, 16 dp apart, each at least 48 dp tall and 96 dp wide.
  Confirm is filled; Cancel is outlined.
  For a destructive confirm, Confirm is filled in the error color and Cancel is a plain text button.
  The body scrolls when tall, and the buttons wrap onto a second line at large font sizes.
  A dialog with a null confirm label shows only Cancel (the NFC scan wait).
  Dialogs with several different choices (the NFC tag result and the barcode lookup consent) do not use it.
  The filled and outlined emphasis, the red destructive confirm and the 16 dp gap are this app's choice.
  The 48 dp height and the 8 dp minimum gap between targets are Google's touch-target minimums (as the code's comments state; not verified against the Google page).

## Tests

- Unit tests cover `AmsTrayAddress` (including AMS HT and unaddressable units), `WeightSyncRule`, `SlotFillRule`, and the gated feed commands.
- View model tests cover feed, scan, sync and assignment.
- The dialog spacing and cancel-style rule are tested against the 48 dp and 8 dp minimums.
- A real load or unload on a printer, and how the sheet and dialogs look, are not covered by tests.
