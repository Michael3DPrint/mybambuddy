# 10: Print-state gate and slot reconfiguration

Assigning a spool to an AMS slot is not a record-only change: Bambuddy pushes the spool's settings to the printer over MQTT.
A pure preflight rule, `SlotLoadPolicy`, refuses the change before anything is sent when the printer is printing, offline, without an AMS, has Developer LAN mode off, cannot be read, or when the slot holds a Bambu RFID spool.
After the request the app reads the slot back and reports what the printer actually shows, because a refusal by the printer does not fail the HTTP call.
Built; the Bambuddy facts below come from its 1.3.0 source.

## Decisions

- One preflight function, `SlotLoadPolicy.preflight`, returns all blockers.
  It runs twice: early on the data on screen, and again in `AmsRepositoryImpl.loadSpool` on a fresh status read right before the request.
  All entry points go through the repository, so stale data cannot slip through.
- Printer-wide block while printing: the app does not know which slot feeds the print.
  Passing the feeding slot through would allow changes to idle slots; that is not built.
- The RFID blocker keys on the tag fields (`AmsTray.hasRfidTag`), not on the sub-brand text, which is free text.
- `PrintActivity` is derived from the raw `state` text of the status reply, so FAILED counts as idle, as Bambuddy treats it.
- The read-back polls the status; the app does not open a WebSocket.
- Rejected: treating HTTP 200 as success, and any client-side retry of a refused push (control commands are never retried).

## Design

How Bambuddy handles an assign request (from the Bambuddy 1.3.0 source, not re-checked when this document was last reviewed):

```text
 PHONE                       BAMBUDDY                                     PRINTER (MQTT)
 1 POST assign ------------> 2 validate spool (404 / 400)
                             3 read last known tray state (cached telemetry, no live query)
                             4 DB under locks: move=true deletes the spool's other slots,
                               replaces the row for this slot, inserts, commits
                               (from here the record is saved)
                             5 slot definitely empty (state 9/10, no presence bit)?
                                 yes: skip the push, mark pending_config
                                 no:  6
                             6 publish ams_filament_setting ---------> firmware applies or refuses
                                 (type, sub-brand, color, nozzle temps,   (a refusal is only logged)
                                  tray_info_idx, setting_id)
                             7 publish extrusion_cali_sel -------------> stored K profile, or
                                                                         cali_idx = -1 = reset to default
                             8 register read-back (30 s) + request a status push
 9 HTTP 200 <--------------- (built-in route: configured / pending_config; Spoolman route: spool only)
                             10 printer pushes tray telemetry; Bambuddy compares tray_info_idx
                                match: WebSocket spool_assignment_verified true
                                no match after 30 s: same event, verified false (web UI only)
```

The app's flow:

```text
 requestLoad
   validate ids ---bad---> message
   early preflight (shown data) ---blocker---> message naming it, no dialog
   ConfirmLoadDialog (warnings + calibration note) ---Cancel---> nothing
 loadSpool
   validate ids again
   GET printers/{id}/status ---failed---> "Can't check <printer> right now", nothing sent
   slot not in the reply ---> "That slot was not found on the printer.", nothing sent
   SlotLoadPolicy.preflight(status) ---blocker---> message, nothing sent
   POST assign (one attempt)
   Spoolman route: reply body not read, so no pending_config and the read-back follows
   pending_config true ---> "Saved. The slot looks empty, so Bambuddy will set it up when you insert the spool."
   else "Sent to the printer. Checking the slot..." then read the slot back (after 3 s, then 7 s more)
     match ---> "Printer confirmed: ..."
     no match ---> "Saved, but the printer still shows ... It may have refused the change."
     never readable ---> "Saved, but the slot couldn't be re-read to confirm it."
```

```text
 ConfirmLoadDialog -> SlotAssignmentViewModel.requestLoad / confirmLoad -> AmsRepository.loadSpool -> AmsRepositoryImpl
   early: AmsUnit.capabilities + AmsTray.hasRfidTag (from AmsRepositoryImpl.getAms) -> SlotLoadPolicy.preflight -> first blocker's message
   late:  AmsApi.getPrinterAmsStatus (GET printers/{id}/status) -> PrinterAmsStatusDto -> toCapabilities() -> SlotLoadPolicy.preflight
   write: AmsApi.assignInventorySlot or assignSpoolmanSlot (POST) -> reply body -> SlotAssignResultDto.parseOrNull -> SlotLoadResult(pendingConfig)
   back:  SlotAssignmentViewModel.readBack -> AmsRepository.getTray -> AmsApi.getPrinterAmsStatus -> SlotReadBack.compare -> message
   event: SlotAssignmentViewModel.changed -> FilamentScreen and PrinterFilamentSection call AmsViewModel.refresh
```

## Behavior

Facts about Bambuddy 1.3.0 and the printer, from the Bambuddy source (the issue numbers are Bambuddy GitHub issues); not re-checked when this document was last reviewed:

- An assign can change the slot's type, color, temperature range and filament preset on the physical printer.
- With no stored K profile for the spool, Bambuddy resets the slot's flow calibration to the default.
  The confirmation dialog says so.
- Neither assign route (built-in inventory, Spoolman) checks whether the printer is printing.
- The printer refuses the setting for the slot that feeds a running print (Bambuddy issue #3434).
  Whether it accepts settings for other slots mid-print is not stated in the source.
- A refusal by the printer does not make the HTTP call fail (Bambuddy issue #2756).
- The only confirmation is the read-back, which Bambuddy reports over WebSocket to its web UI.
  The REST reply does not carry it.
- The built-in route returns `configured` and `pending_config`.
  The Spoolman route returns the spool only, and a push failure there is swallowed, so only the app's read-back tells the user.
- Bambuddy defines busy as gcode_state PREPARE, SLICING, RUNNING or PAUSE.
  IDLE, FINISH and FAILED are valid targets.
  `PrintActivity` also counts PAUSED and PRINTING as busy.
- Nothing in the source distinguishes an RFID-read Bambu spool when pushing.
  What the firmware does with a push onto an RFID slot is not in the source.
- With Developer LAN mode off, the printer rejects MQTT commands ("mqtt message verify failed").
  Bambuddy reports it as `developer_mode` (true, false or unknown) on the status API.
- Without a live MQTT client the push is silently skipped and the assignment is still saved.
- Assigning over a slot that holds another spool deletes that row. `move=true` also removes the spool from its other slots in the same commit.
- The status API carries what the gate needs: per tray `tag_uid`, `tray_uuid`, `exists`, `state`; per printer `state`, `connected`, `developer_mode`.

Blockers (no override; nothing is sent):

| Blocker | Detected from | Evidence |
|---|---|---|
| Printer is printing, preparing, slicing or paused | `PrintActivity.BUSY` | Bambuddy's busy states; the printer refuses the feeding slot |
| RFID-identified Bambu spool in the slot | tray `tag_uid` or `tray_uuid` present and not all zeros | The source has no guard and the firmware refusal is not confirmed. Blocked because the printer is the authority for tagged spools |
| Developer LAN mode off | `developer_mode == false`, through the `SLOT_CHANGE` tool | Bambuddy source |
| Printer offline, or the printer has no AMS | `connected == false`, `ams_exists == false`, through the `SLOT_CHANGE` tool | Bambuddy source |
| Cannot tell | state missing or unrecognized while the printer is not reported offline; or the fresh status read failed | Fail safe |
| Invalid ids | `SlotLoadPolicy.validate`, tray limit `SlotLoadPolicy.MAX_TRAY_ID` | The tray limit is the server's, not verified |

Allowed, with a note or warning:

- Slot empty: Bambuddy saves the assignment and configures the slot when a spool is inserted.
- Developer mode not reported: allowed with a "may be refused" warning, because some printers never report it.
- Filament in the slot with no recorded spool and no tag: the `SlotWarning.OverwritesDetected` warning.
- Replacing a recorded spool, moving a spool from another slot, material mismatch and AMS drying: warnings from `SlotLoadPolicy.assess`.

Limits:

- A print can start between the fresh status read and the request.
  The client cannot close that gap.
- `SlotReadBack` compares material and the RGB part of the color only.
  A wrong preset id with the same material and color reads as confirmed.
- A blank printer name is worded as "The printer".

## Tests

- `SlotLoadPolicyTest`: each busy state, RFID, developer mode off, offline and no AMS blocking, an unknown state blocking, several blockers in a stable order, an empty slot and an unknown developer mode giving notes only.
- `CapabilityMapperTest`: IDLE, FINISH and FAILED map to idle; RFID by tag and by UUID; an all-zero tag is not RFID.
- `AmsRepositoryTest`: busy, offline, RFID, developer mode off, a missing slot or a failed status read gives an error and no POST; IDLE, FINISH and FAILED proceed; `pending_config` is parsed.
- `SlotReadBackTest`: match, color-only difference, empty or missing tray, case, `#` and alpha handling.
- `SlotAssignmentViewModelTest`: a busy or RFID slot shows a message and no dialog; pending configuration, read-back confirmed, refused and unreadable wording.
- Only a real printer shows whether the firmware accepts or refuses the push, and whether the RFID blocker is stricter than the firmware.
