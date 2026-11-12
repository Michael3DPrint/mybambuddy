# 44: Previous prints tab and Run again

The queue page has a Queue tab and a Previous prints tab, for all printers and for each printer.
Previous prints lists Bambuddy's archive and offers Run again, which opens the print review with the archive as the file and stages a new job with `archive_id`.
Built; a real Run again, paging past the first page and archives of prints started elsewhere have not been run against a server.

## Decisions

- The list comes from the archive, which is the permanent record and includes prints started from Bambu Studio or the printer, not only finished queue jobs.
- Run again always goes through the review (printer, plate, slots, plate-cleared and model checks), never one tap to print.
- Run again stages through `POST queue/` with `archive_id`, not `POST archives/{id}/reprint`, which Bambuddy 1.3.0 answers with 410 (observed; not verified against Bambuddy's source).
- The list is never cached and never changes or deletes an archive.

## Design

```text
 Queue page
 [ Queue | Previous prints ]                 (the shared tab row; scrolls at large font)

 Previous prints (in the server's order, 20 at a time, Load more)
 filter chips: All | Completed | Cancelled | Failed
 +---------------------------------------------------+
 | [ pic ]  Christmas Tree Ornament Hogwarts          |
 |          Cancelled · 12 Nov, 02:55 · 1 h 15 m      |
 |          X2D                                       |
 |          [ Run again ]                             |
 +---------------------------------------------------+
```

```text
 PreviousPrintsTab -> PreviousPrintsViewModel -> ArchiveRepository.page / picture
   -> ArchiveRepositoryImpl -> HistoryApi GET archives/ (limit, offset, printer) and GET archives/{id}/thumbnail
   -> toHistoryItem -> HistoryItem -> PreviousPrintsState (filter applied to the loaded items)

 Run again -> PrintFlowViewModel.begin(printerId, ArchiveRef) -> openAgain
   -> PrintFileRepository.openArchive -> PrintFileApi GET archives/{id}/plates (same shape as the library's) -> PrintFile (fromArchive)
   -> review: the existing validation (sliced for this model, slots, plate cleared)
   -> Add to queue: PrintFileRepository.stage -> QueueApi POST queue/ { archive_id, printer, plate, ams_mapping, manual start }
   -> Start print: the existing confirmation, stage, start once, then the start check (design 35)
 plates read fails (archive gone or file missing) -> back to the source step with the reason; nothing is created
```

## Behavior

- The filter chips never show a print that is still running; it stays on the Queue tab.
- A failed read shows an error with Retry.
- The staged job refers to the archive and sets no library file id, so the app never cleans it up, and backing out of the review deletes nothing.
- The review offers no slicing for an archive.
- The History tab stays the full record with its statistics; Previous prints has Run again.
- Archive retention is Bambuddy's; the app does not rely on archives being kept.

## Tests

Unit tests cover the archive reader, the queue create body carrying `archive_id` and no library id, the review never deleting an archive source, filtering by status, and paging.
Only a device and server show a real Run again, how the tab looks, and TalkBack.
