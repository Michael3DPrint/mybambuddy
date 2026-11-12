# 42: Queue job page

Tapping a queue card opens a full-screen page for that job with Settings and Slicing tabs.
While the job is waiting, the user can change its printer, plate, AMS slot per filament and print options, or re-slice the file through the slice page of design 40.
Bambuddy cannot swap a job's file, so a re-slice creates a new staged job in the old job's place and then removes the old one.
Built; the PATCH, the create with `insert_position` and a re-slice of an already sliced file have not been run against a real server.

## Decisions

- A re-slice replaces the job, because the update body the app sends (`PATCH queue/{id}`) has no file field.
  Not verified against the Bambuddy source.
- The new job is always staged and waits until the user starts it, because the app never creates a job that starts by itself.
- Editable: printer, plate, filament slot per filament, bed levelling, flow calibration, vibration calibration, layer inspection, timelapse and use AMS.
  Not editable: scheduling, power-off after, preheat, G-code injection and cost fields.
- Only a waiting job is editable; printing and finished jobs open read-only.
- One Save sends only the fields that differ, after a confirmation that lists each change in words.

## Design

```text
 < Benchy.gcode.3mf
 [ picture ]   Waiting, number 1
               1 h 15 m · 31 g · PLA (slicer estimate)
 [ Settings | Slicing ]
 Settings:  Printer [ X2D v ]   Plate [ 1 v ] (several plates only)
            Filament 1: PLA   [ AMS tiles, chosen slot outlined ]
            Print options: bed levelling, flow calibration, vibration calibration, layer inspection, timelapse, use AMS
            findings from `PrintValidation`, as in the print review
 Slicing:   [ Re-slice ] -> slice page (design 40, shown over this page)
 bottom bar: [ Save changes ]  (off until something differs, says why it is off)
```

```text
 open: QueueJobViewModel.open -> QueueRepository.getQueue (the list is read again, so the job is fresh)
       + PrinterRepository.fetchPrinters + AmsRepository.getAms + PrintFileRepository.open (plates of the job's library file)
       -> QueueJobState (settings, chosen slots from JobEdit.chosenFrom, findings from PrintValidation) -> picture via QueueRepository.picture
 edit -> QueueJobViewModel.edit -> JobEdit.diff -> JobChange; PrintValidation re-runs
 Save -> confirmation (JobEdit.lines) -> QueueRepository.update -> QueueApi PATCH queue/{id} (only changed fields)
       409 -> "no longer waiting", reload;  other error -> message;  success -> reload and mark the queue changed
 Re-slice -> SliceFlow.open (printer model, AMS slots) -> sliced PrintFile -> QueueJobState.replacement -> confirmation
       -> PrintFileRepository.stage (POST queue/, manual_start true, insert_position = old position, options carried, cleanup true)
          fails -> nothing removed, reason shown
          ok -> QueueRepository.remove (DELETE queue/{id})
                fails -> "The new job was added, but the old one could not be removed ... Both are in the queue."
                ok -> page closes
 close -> the queue list reloads through the callback given to LocalOpenQueueJob
```

## Behavior

- The new job keeps the old job's print options and each filament's earlier slot where the new file still needs that slot (`JobEdit.carryOver`); other filaments get the matcher's proposal.
- If the job is no longer in the queue when the page opens, the page shows "This job is no longer in the queue."
- The validation on this page has no remote-control gate and no plate-cleared check; a busy or offline printer does not block saving.
- Leaving with unsaved changes asks "Discard your changes?" with Keep editing.
- The Save bar says "Nothing changed yet" or, when validation blocks saving, tells the user to fix the problems on the Settings tab.
- Declining a new slice deletes only the file the slice made.
- Option values from older Bambuddy versions (true or false instead of text) are read, so an older server cannot break the queue list.
- Each server call is a single attempt; nothing here starts a print.
- A job with no library file, such as one from an archive, has nothing to re-slice.

## Tests

Unit tests cover `JobEdit` (the diff, the lines, mapping both ways, carry-over), `QueueRepositoryImpl` mapping and update, and `QueueJobViewModel` (read-only for non-waiting jobs, the 409 path, the replace order and both failure paths).
Only a device shows how the page looks, TalkBack on a card that holds two buttons, focus order when the page and the slice page open and close, and the largest font sizes.
