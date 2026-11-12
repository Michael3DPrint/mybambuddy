# 12: AMS screen state holders

The AMS and drying UI is driven by three view models, each with one reason to change.
The rule: a view model stays small (the build limits public functions), and view models do not know each other; they signal through events the screen collects.
Built.

## Design

```text
AmsViewModel               refresh, chooseSlot, dismissSlot       list, slot sheet, spool choices
SlotAssignmentViewModel    beginLoad, cancelLoad, requestLoad,    assignment, confirmation, read-back
                           cancelConfirmation, confirmLoad, unload
DryingViewModel            draftFor, updateDraft, startDrying,    drying drafts, start and stop
                           stopDrying

FilamentScreen
  AmsViewModel ► AmsRepository.getAms (list, slot sheet)
  SlotAssignmentViewModel ► AmsRepository.loadSpool, unloadSpool
  DryingViewModel ► AmsRepository.startDrying, stopDrying
  SlotAssignmentViewModel.changed, DryingViewModel.changed ► screen calls AmsViewModel.refresh()
  SlotAssignmentViewModel.messages, DryingViewModel.messages ► the screen's one snackbar
```

## Decisions

- Refresh after a change is the `changed` event, not a direct call between view models.
- `requestLoad` receives the list of units from the screen, so assignment does not depend on `AmsViewModel`.
- The screen closes the slot sheet with `AmsViewModel.dismissSlot` before calling the assignment view model.

## Tests

`AmsViewModelTest`, `SlotAssignmentViewModelTest` and `DryingViewModelTest` share one file of test fixtures; they assert that `changed` is emitted after a successful load, unload, drying start and stop, and not after a refusal.
