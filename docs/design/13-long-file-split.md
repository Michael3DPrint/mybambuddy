# 13: Long files and large composables

Screens are split into small composables in separate files, and the Moshi adapters live apart from `ApiClient`.
No source file may exceed 400 lines (`StandardsEnforcementTest` enforces it), and a long file is fixed by splitting the oversized function, not only the file.
Built.

## Structure

The main split files:

```text
ui/detail/    PrinterDetailScreen (PrinterDetailScreen, PrinterDetailScreenContent: layout and state)
              PrinterCameraCard, PrinterStatusBlock, PrinterControlRow, PrinterDetailDialogs, TemperatureMetricItem
ui/printers/  PrintersScreen (list, error and empty branches: ErrorStateView, EmptyPrintersView), PrinterCard
ui/history/   HistoryScreen (HistoryScreen, HistoryContent), HistoryCard
ui/settings/  SettingsScreen (SettingsScreen, SettingsContent: scroll and section order)
              TailscaleNoteCard, ConnectionSection, PollingSection, AppearanceSection
ui/common/    StateChip, EmptyState
data/api/     ApiClient (the object only)
              ResponseAdapters, PrinterAdapters, HmsErrorDtoAdapter (the Moshi adapters)
```

## Decisions

- Extracted composables are plain functions of state plus callbacks.
- `StateChip` is the one pill for printer states (`PrinterStateChip`) and print results (`StatusChip`).
- The hand-written Moshi adapters are in their own files.
- `PrintersResponseAdapter` must consume or skip every value in each read branch, or parsing never ends; `PrintersResponseAdapterTest` covers its reply shapes.
- Rejected: raising the line limit, which would leave the oversized composables in place.

## Tests

`:app:check` enforces the file-length rule through `StandardsEnforcementTest`.
How each split screen looks is not checked by the build.
