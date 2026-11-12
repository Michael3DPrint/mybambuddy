# 41: Queue cards

Each queue job is a card with a small picture of the model, its name, status, the slicer's estimate and any reason it cannot start.
A waiting job has one filled "Start now" button and a three-dot More menu that holds Move up, Move down and, after a divider, the two destructive actions.
The whole card opens the job page (design 42).
Built.

## Decisions

- Start now is the only prominent action; Cancel and Remove sit in the More menu in the error color, with icons and words, so they are not next to Start now.
- Start now, Cancel and Remove each ask for confirmation before they send anything.
- The picture is decorative, because the job's name is beside it.
- A finished job shows a "Remove from queue" button instead, so it can leave the list; a printing job shows no action.

## Design

```text
 +-----------------------------------------------------+
 | [ pic ]  Benchy.gcode.3mf                           |
 | 72 dp    Waiting, number 1                          |
 |          1 h 15 m · 31 g · PLA (slicer estimate)    |
 |          reason it can't start (if any)             |
 |  [        Start now        ]  [ : ]                 |
 +-----------------------------------------------------+
   [ : ] menu: Move up / Move down / ---- / Cancel this job / Remove from queue
```

```text
 QueueScreen -> QueueViewModel.refresh -> QueueRepository.getQueue (CachingQueueRepository -> QueueRepositoryImpl -> QueueApi GET queue)
   -> QueueItemDto.toDomain -> QueueItem -> QueueState.Success: cards drawn at once with a placeholder picture
   -> loadPictures: one job after another, at most 30
        QueueRepository.picture -> QueuePicture.sourceOf (library file, else archive, else none)
          -> QueueApi GET library/files/{id}/thumbnail or archives/{id}/thumbnail -> bytes (refused over 3 MB)
        -> QueueViewModel.pictures (by job id) -> QueueCard -> QueueThumb decodes at about 256 px
   a failed picture or a job with no file keeps the placeholder
 Actions: Start now -> confirmation -> QueueViewModel.start (gated)
          Move -> QueueViewModel.move -> reorder; Cancel or Remove -> confirmation -> cancel or remove; all then refresh the list
```

## Behavior

- Pictures are fetched after the list is shown, so the list never waits for them, and are kept for the session in the view model.
- A job with neither a library file nor an archive has no picture.
- Move up and Move down are disabled at the ends of the list.
- The card reads as one button that opens the job; Start now and More keep their own targets of at least 48 dp.
- Text wraps at large font sizes and the picture stays fixed at 72 dp.

## Tests

Unit tests cover `QueuePicture`, picture loading in `QueueViewModel` (a failed one is skipped) and the picture routes in `QueueRepositoryImpl`.
Only a device shows how the cards look and how TalkBack reads the menu and a card that holds two buttons.
