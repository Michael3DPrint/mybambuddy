# 02: Print queue

The Queue tab and each printer's Queue tab list Bambuddy's print queue, let the user reorder waiting jobs, start, cancel and remove jobs, clear finished ones and resume a printer's queue after a failure.
Adding a job is the print flow (design 35), not this design.
Built; every action is confirmed, sent once and refused while offline.

## Decisions

- Reorder uses move up and move down in a menu on the job card: accessible, no drag library.
- Only `QueueStatus.PENDING` jobs can be moved, started or cancelled; a finished job can only be removed.
- `QueueOrdering.move` returns the full list of positions for one printer, unique and contiguous, because the server rejects duplicate positions.
- Start, cancel, remove and resume are never cached or retried.
  Only the list read is saved (`CachingQueueRepository`).
  Start checks printer readiness first (`QueueViewModel.startBlocked`, `PrinterTool.START_QUEUED_PRINT`) and then watches the job to report whether the printer took it.
- A job with no printer cannot be started from the phone (`QueueRepository.NO_PRINTER_MESSAGE`).
- A filament refusal from the server (409 with `insufficient_filament` or `unmatched_filament`) opens a "Print anyway" prompt; if the user confirms, the start is sent once more with `skip_filament_check`.

## Design

```text
Queue tab: [Queue] [Previous prints]
+--------------------------------+
| X2D-Sample                     |
| [Clear finished jobs (1)]      |
| benchy.3mf  Waiting · #1       |
|   [Start now]            [:]   |  menu: Move up, Move down, Cancel this job, Remove from queue
| cube.3mf    Completed          |
|   [Remove from queue]          |
| [Resume queue after failure]   |  only when a job of the printer failed
|                [Print a file]  |
+--------------------------------+
Empty: "Nothing is queued"
```

```text
QueueScreen -> QueueViewModel -> QueueRepository (CachingQueueRepository -> QueueRepositoryImpl) -> QueueApi
  getQueue       GET queue/                     (saved by CachedSource, ignored after one hour)
  move           POST queue/reorder             (QueueOrdering.move)
  start          POST queue/{id}/start?skip_filament_check=
  cancel         POST queue/{id}/cancel
  remove         DELETE queue/{id}
  resume         POST queue/printer/{printerId}/resume
  update         PATCH queue/{id}               (job page, design 42)
  picture        GET library/files/{id}/thumbnail or archives/{id}/thumbnail
QueueItemDto -> QueueItem -> QueueState.Success(PrinterFilter.queue(items, printerId))
After a write succeeds the ViewModel calls refresh.
The repository reads at most 500 jobs.
```

## Behavior

- Offline, every write shows "You're offline" and sends nothing; the list may show the saved copy.
- On a list error with no earlier list the screen shows an error state; with an earlier list it keeps it.
- Pictures load one after another for at most 30 jobs; a failed picture is skipped.
- A picture over 3 MB is refused.
- The card and job page are described in designs 41 and 42; previous prints in design 44.

## Tests

`QueueRepositoryTest` checks paths and bodies with MockWebServer, and covers `QueueOrdering`.
`QueueViewModelTest` covers start blocking, the printer filter, pictures, the start outcome message, clearing finished jobs and the filament prompt.
`QueuePictureTest` covers picture sources.
Real queue behavior on a printer is only visible on a device.
