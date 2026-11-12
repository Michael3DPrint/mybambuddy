# 35: Print from the phone

Print a sliced 3MF from a file on the phone or from a MakerWorld link, through the user's own Bambuddy server.
The app uploads or imports the file into Bambuddy's library, shows a review with checks, creates a staged queue item, and starts it only after a separate confirmed tap.
Built; the plate-type check, an options screen, an SD-card warning and receiving a file from the Share sheet are not built (see OI-53).
Where the flow starts and the MakerWorld source are in design 38; slicing an unsliced file is in designs 39 and 40.

## Decisions

- **Sliced files only.** An unsliced file cannot print without slicing; the review blocks it and offers the slice dialog (design 39).
- **Two buttons, always staged.** Add to queue and Start print both create a staged queue item (`manual_start` true), which Bambuddy never starts by itself.
  Start print then calls the start route once, after a confirmation.
- **The app proposes the filament match and the user can change it.** `FilamentMatcher` picks a loaded slot per required filament: type first, then nearest color, never the same slot twice.
  The mapping is sent as `ams_mapping`.
  Sending no mapping and leaving the choice to Bambuddy was rejected because the user could not see it.
- **Temporary uploads are cleaned up.** A file this flow created is marked for cleanup after dispatch, and Cancel before anything is queued deletes it.
  A pre-existing library file or archive is never deleted.
- **Print options stay at Bambuddy's defaults.** There is no options screen.

## Design

What Bambuddy provides (read from the Bambuddy 1.3.0 source):

- `POST library/files/` uploads a multipart file and refuses raw G-code and anything that is not a valid 3MF.
- `GET library/files/{id}/plates` gives plates, filaments, grams and time; the thumbnail routes give the pictures.
- `POST queue/` creates a queue item with `printer_id`, `plate_id` and `ams_mapping`.
- `POST queue/{id}/start` releases a staged item.
  It can answer 409 with a filament problem, which the app repeats once with `skip_filament_check` only after the user accepts it.

```text
 Source (phone picker or MakerWorld link, design 38)
   -> upload / import (progress, Cancel)        server refuses: show its reason, nothing stored
   -> Review: printer, plate, filament per slot, findings
        [Add to queue]  -> staged item -> "Added to the queue. It waits there until you start it."
        [Start print]   -> confirmation (file, plate, estimate, filament per slot, warnings with a tick)
                        -> staged item -> start (one attempt)
                             409 filament problem -> shown, "Print anyway" repeats once
                        -> watch the job (see Behavior)
   -> Cancel before anything is queued: delete what this flow created
```

```text
 PrintFlowScreen (OpenDocument picker, UriPrintSource) -> PrintFlowViewModel -> PrintFileRepository (domain/repo)
 PrintFileRepositoryImpl (data/repo) -> PrintFileApi (Retrofit):
     upload             POST library/files/, then GET library/files/{id}/plates
     stage              POST queue/ (QueueCreateBody, manualStart true)
     start              PrinterToolGate refusal check, then POST queue/{id}/start
     discard            DELETE library/files/{id}
 PlatesDto.toFile -> PrintFile -> PrintFlowViewModel
 AmsRepository units + PrinterRepository printers -> SlotCandidates, FilamentMatcher, PrintValidation -> PrintFlowState (StateFlow) -> screen
 after start: StartWatcher polls QueueRepository.getQueue -> StartCheck verdict -> PrintFlowState message
```

Nothing is cached or stored on the phone; the flow state lives in the view model.
Picking a printer, plate or slot, or marking the plate cleared, flows back from the screen to `PrintFlowViewModel.select`.
A printer or plate choice re-runs the matcher and the validation, a slot choice re-runs the validation, and marking the plate cleared calls `PrintControlRepository.clearPlate` and then re-runs the validation.

## Behavior

- `PrintFileRules` refuses by name before upload: only `.3mf` (including `.gcode.3mf`) is accepted, and raw `.gcode` gets its own message.
- `PrintValidation` returns findings at three levels.
  A blocker disables Start print, and also Add to queue unless it is marked start-only.
  A warning must be ticked on the confirmation.
  Info is only shown.
- Blockers: not sliced; sliced for a different printer model; a required filament with no slot, an empty slot, a shared slot or an unknown slot type.
  For Start print only: the remote-control gate is off, the printer is busy, unreachable or in error, or the plate is not cleared.
- Warnings: the file or the printer does not name a model; the slot type differs from the file; the slot has less filament than the file needs; the AMS unit is drying; the printer reports active errors.
- Info: the slot color differs from the file's.
- Starting goes through the existing tool gate (`PrinterTool.START_QUEUED_PRINT`), checked in the review and again inside the repository before the start call.
- After a start is accepted, `StartWatcher` reads the queue every 2 seconds for up to about 30 seconds.
  It reports printing, failed, skipped or cancelled with Bambuddy's reason, a waiting reason seen three times in a row, still waiting with no reason, or gone from the queue.
  A refusal recorded after the watch ends shows only on the job in the queue.
  `StartCheck` turns each result into the words shown.
- Every server call is a single attempt; the app never retries a start by itself.
- The file goes only to the user's own Bambuddy server.
- Bambuddy does not report which build plate is fitted, so the plate cannot be checked.

## Tests

- Unit tests cover the file rules, the matcher and mapping, every validation rule, the estimate text, the start verdicts and the watcher's stopping rules with scripted answers.
- Uploads, queue creation and starts are tested against fakes and a local test server only, never against a real server or printer.
- Not verified: a real upload, staged item and start on the user's server.
  Large files are also untried; the server reads an upload into memory and no size limit was found in its route.

## Open work

OI-53 lists the unbuilt checks and the Share sheet intake.
