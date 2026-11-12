# 09: Safe AMS slot assignment

Every assignment of a spool to an AMS slot goes through one validation step and one confirmation dialog, because Bambuddy's assign call can reconfigure the physical slot (type, color, temperatures, calibration).
`SlotLoadPolicy` is a pure object that owns the rules; screens render its result.
Built; blocking rules for print state and RFID spools are in design 10.

## Decisions

- One entry point is `SlotAssignmentViewModel.requestLoad` then `confirmLoad`; slot tap, spool detail ("Load into AMS…") and NFC scan all reach it.
- Always confirm, even with no warnings, because the action can reconfigure hardware, with no "don't ask again".
- Validate twice: `SlotAssignmentViewModel` for fast feedback and `AmsRepositoryImpl.loadSpool` so a bad request is never sent (spool and printer ids above zero, AMS id zero or more, tray 0 to 3).
- A material mismatch compares names by their letters and digits only, ignoring case, and only when both sides are non-blank.
- A slot that reports a filament with no spool recorded raises `SlotWarning.OverwritesDetected`.
- The confirmation text says Bambuddy "may" set the slot, because the printer can still refuse.
- "Remove the record of this slot's spool" is one tap and sends only the unassign call; it clears the record and does not touch the printer.
- `SlotLoadPolicy.validate` rejects tray ids outside 0 to 3.
- Rejected: warn only when risky, because that hides the hardware effect; and block outright, because the user may want the change.

## Design

```text
tap slot ► pick spool   |  spool detail ► "Load into AMS..." ► pick slot  |  NFC scan ► slot
                                  v
         SlotAssignmentViewModel.requestLoad(spool, unit, trayId, units)
                                  v
   SlotLoadPolicy.validate ► invalid: message, stop
   SlotLoadPolicy.preflight ► first blocker: message, stop
   SlotLoadPolicy.assess ► PendingAssignment(spool, unit, trayId, SlotLoadAssessment)
                                  v
                       ConfirmLoadDialog ── Cancel: nothing sent
                                  v Load
      confirmLoad ► AmsRepository.loadSpool (one attempt, never retried)
      AmsRepositoryImpl: validate, fresh AmsApi status read, preflight, then assign
      success: changed event ► AmsViewModel.refresh; slot is read back and the result shown
      error: server message shown
```

```text
Load into the printer?
  SPOOL           title, color, material, grams left
  INTO THIS SLOT  printer · unit · slot n; what the slot holds now
  WHAT WILL HAPPEN  inventory records the spool;
                    Bambuddy may set the slot to <material, color>;
                    calibration note
  ! one line per warning
                    [Cancel] [Load]
```

Warnings (`SlotWarning`): `ReplacesSpool`, `OverwritesDetected`, `MaterialMismatch`, `MovesFromOtherSlot`, `UnitDrying`, `SlotLooksEmpty` (shown as information), `MayBeRefused`.
Blockers (`SlotBlocker`) are checked before the dialog.
`SlotLoadAssessment` holds the push summary (what the slot may be set to) and the warnings.

## Tests

- `SlotLoadPolicyTest` covers each warning, combinations, blank materials and the id and tray boundaries.
- `SlotAssignmentViewModelTest` covers invalid slots and refused printers being stopped before any dialog or request, and the read-back outcomes.
- `ConfirmLoadTextTest` covers the dialog text.
- Not checked by the build: the real printer's reaction to an assignment, which only the owner can try.
