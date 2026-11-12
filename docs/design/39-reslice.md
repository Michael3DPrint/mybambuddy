# 39: Slice for this printer

A file that has no G-code for the printer, such as a MakerWorld project, can be sliced from the print review through Bambuddy's slicer.
The review shows "Slice for <model>"; the slice page (design 40) collects the choices, `SliceFlow` sends one slice request and polls the job, and the sliced file opens in the normal review.
Built; it writes only a new library file in Bambuddy and never touches the printer.
No real slice has been run on the owner's slicer, so the real result, the progress keys and the X2D presets are unverified (OI-53).

## Decisions

- Slicing starts only on a tap, with one POST per tap and no retry.
- Presets are chosen by pure rules in `SlicePicker`: the printer preset that names the model (0.4 nozzle first), process presets that fit it ("0.20mm Standard" first), filament presets of the right type ("Bambu TYPE Basic" first).
- Each filament comes from a loaded AMS slot, so the sliced file's filaments and colors match what is in the printer.
  `FilamentMatcher` proposes a slot per used filament, `SlicePicker.presetForSpool` picks the preset for that spool's product line, and the slot's color is sent instead of the file's.
- The build plate type is required before slicing, because the printer does not report which plate is fitted.
  The last plate is remembered with the other choices (design 43), with a reminder to check it.
- The default is that the designer's own process tweaks are not carried over; the Options tab can switch on the file's own settings (design 40).
- Cancelling only stops waiting; the server job keeps running and the app does not track a result file created later.

## Design

```text
 Review (file not sliced) -> [Slice for X2D]
   -> slice page opens (design 40): presets and project slots load
        load fails (presets, no preset for the model, no filaments) -> only the reason, with Close
        no process preset fits -> message on the page; Slice stays off
   -> Slice -> POST once -> job id
   -> poll every 2 s, up to 450 polls; three failed polls in a row stop with the error
        failed -> Bambuddy's error_detail, back to the page
        completed -> read plates of the result file -> review (the sliced file counts as created here)
   Stop waiting -> polling stops; the server job continues
```

```text
 PrintReviewStep / PrintFlowViewModel (QueueJobViewModel for a queue job) -> SliceFlow
   SliceFlow -> PrintFileRepository (PrintFileRepositoryImpl) -> PrintFileApi
        slicerPresets: SlicingApi GET slicer/presets -> SlicerPresetsDto -> SlicerPresets
        projectSlots: GET library/files/{id}/filament-requirements -> ProjectSlot
        startSlice: POST library/files/{id}/slice (SliceBody, export_3mf true) -> job id
        sliceJob: GET slice-jobs/{id} -> SliceJobReader -> SliceJobState (Running, Done, Failed)
        open: GET library/files/{id}/plates -> PrintFile -> onSliced -> review
   SliceFlow <-> SliceMemoryStore (SliceMemoryFileStore, a JSON file in the cache directory)
```

## Behavior

- The request holds one filament preset and one color per project slot, in slot order, the plate, `bed_type`, the options and `export_3mf` true.
  `SliceRequestBuilder` returns nothing while the printer, process, build plate or any slot's preset is missing.
- `SliceJobReader` treats an unknown status as still running and reads a percent from `percent`, `pct` or `progress`; other shapes show a bar without a percent.
- The slice calls share the print-from-the-phone client with a 3 minute read limit and a 6 minute call limit, because Bambuddy slices a preview when it reads an unsliced project's filaments, which can take about a minute (not verified).
- The remembered process preset is kept per printer model and the filament preset per filament type; a remembered preset that no longer exists is ignored.
- A scale other than 100 percent first downloads the file, scales it on the phone, uploads the copy and slices the copy; the upload is deleted when the slice ends or cannot start (design 40).
- After a successful add or start, the sliced file is marked for cleanup after dispatch (design 35) and the file the slice came from stays in the library.
  Cancelling the review deletes the sliced file, and the source too when the flow imported or uploaded it.
- The app reads no warnings from a successful slice.
- Based on Bambuddy 1.3.0 source: presets are `{source, id}` with source `standard`, `local`, `cloud` or `orca_cloud`; jobs are held in memory, so a Bambuddy restart loses them.

## Tests

Unit tests cover `SlicePicker`, `SliceRequestBuilder`, `SliceJobReader`, `SliceMemoryRules`, and the flow through `PrintFlowViewModel` and `QueueJobViewModel` with a fake repository.
A device or real server is needed for the slice itself, the preview image on the build plate step and how the pages look.
