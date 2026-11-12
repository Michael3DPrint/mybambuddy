# 43: Slice memory, clearing errors from the printer page, and the failed-print state

The slice page starts from what the last accepted slice used, and shows a reminder when the build plate comes from memory.
The error card on the printer's Overview tab has a Clear errors button that uses the same command, gate and confirmation as the Tools tab.
A print that ended in the `FAILED` state is its own state, "Last print failed", and behaves like a finished print.
Built; none of it has been seen on a phone or run against a real printer or server.

## Decisions

- Slice settings come back from memory with a visible reminder instead of a blank, because the build plate is the one setting the printer cannot report.
  A wrong plate fails the slice or prints badly, so the reminder is the mitigation.
- A loaded AMS spool's product line decides each filament preset before memory does; memory only fills a filament with no loaded match.
- The error card and the Tools tab share one command, one gate and one confirmation (`ClearErrorsDialog`), so they cannot disagree.
- `FAILED` is not an error.
  `PrintActivity` counts `FAILED` as idle, like `IDLE` and `FINISH`, because Bambuddy treats these as valid targets for new work (not verified against Bambuddy's source).
- Resuming or re-running the failed job itself is a separate feature (design 44).

## Design

What is remembered, after Bambuddy accepts a slice (a refused slice saves nothing):

- the build plate;
- the process preset for each printer model;
- the filament preset for each filament type;
- the three options: orient, arrange and designer's settings.

Applied when the slice page opens, in this order:

1. The remembered plate and options always apply.
2. The process preset applies when it is still offered for the printer.
3. For each filament, the preset that follows the loaded AMS spool's product line wins.
   A filament with no loaded match uses the remembered preset for its type, else the usual recommendation.
4. The designer's-settings switch applies only when the file targets this printer.

Anything remembered that no longer exists (a deleted preset, a plate not in `BedType`) is ignored.

```text
 Slice page, Plate tab
 Which build plate is on the printer?
 ( ) Cool Plate  ( ) Engineering Plate  ( ) High Temp Plate  (x) Textured PEI Plate  ( ) Supertack Plate
 Last used: Textured PEI Plate. Check it is still the plate on the printer.
```

```text
 SlicePageDialog -> SliceFlow (built by PrintFlowViewModel and by QueueJobViewModel)
   open:   SliceMemoryStore.load -> SliceMemoryRules (bed, process, filament, options) -> SliceState
   Slice:  PrintFileRepository.startSlice -> Bambuddy accepts -> SliceFlow.remember
             -> SliceMemoryRules.after -> SliceMemoryStore.save
             -> SliceMemoryFileStore -> JsonCacheStore file "slice-memory" in the cache directory
```

The file holds preset ids and names only, is size-capped, never expires and is written atomically.

```text
 Collapsed: Printer: 2 errors   v      "Tap for details or to clear."
 Expanded:  each error explained with a wiki link (design 03)
            [ Clear errors ]   (off with the tool's reason when clearing is unavailable)
            "Clearing tells the printer to drop these errors; it does not fix their cause."

 HmsAdviceCard (PrinterDetailScreen) -> ClearErrorsDialog -> PrinterDetailViewModel.execute(ClearHms)
   -> PrintControlRepository.clearHms (PrintControlRepositoryImpl) -> gated(PrinterTool.CLEAR_ERRORS) -> PrinterControlApi POST printers/{id}/hms/clear
   -> message "HMS errors cleared"; the card updates from the next poll
```

Nothing is cleared without the confirmation.
The Tools tab's Printer Status section uses the same `ClearErrorsDialog` and the same command.

## Behavior

- `PrinterState.FAILED` shows "Last print failed".
  The printer card shows the failure color and a line that says the printer is ready for the next print, so it is not read as idle.
  The Printer Status section shows "Last print failed" as a warning.
  The overview offers Print and the review does not block Start print.
- State `ERROR` still shows "Needs attention" and offers only Cancel (`OverviewAction.CANCEL_ONLY`); the error card carries Clear errors.
- The "print failed" alert fires for both `FAILED` and `ERROR`.
- The words and color of the error card and Printer Status follow the worst HMS level present: "1 error" (red), "1 warning" (amber), "2 notices" (neutral); a mix reads "1 error, 2 notices".
- A finished queue job has a Remove from queue button, and each printer's list has Clear finished jobs with a confirmation that says how many (`QueueViewModel.clearFinished`).
- A printer's queue list shows Resume queue after failure when one of its jobs failed.
- The print review has a Mark plate cleared button when the printer is awaiting a plate clear.
  It only tells Bambuddy (`PrinterControlApi` POST `printers/{id}/clear-plate`); nothing is sent to the printer.

## Tests

Unit tests cover:

- remembering and reading back, and ignoring entries that no longer exist;
- the AMS-derived filament preset winning over memory, and memory being used when no slot matched;
- the designer's switch applying only when available, and nothing saved when the slice start fails;
- the `FAILED` and `ERROR` state mapping, the overview action, the review's findings, the status wording and the failure alert.

Only a device shows how the error card, the Clear errors button and the queue's Clear finished jobs look and sound with TalkBack, and the first real read and write of the memory file.
