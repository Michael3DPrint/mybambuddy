# 30: Printer-centric navigation

Anything that belongs to one printer lives on that printer's page: its queue, its upkeep and its tools.
The bottom bar holds the app-wide tabs: Printers, Queue (all printers), Filament, History and Settings.
Each printer card shows its own Remote control line (the Developer LAN mode state), which opens that printer's Tools tab.
Built; the tabs of the printer page are described in designs 32 and 37.

## Decisions

- Settings appears once, on the bottom bar.
- The queue is available twice: all printers on the bottom bar, and only one printer's jobs on its page.
- Upkeep (maintenance) is reachable only through a printer.
- Readiness is shown per printer card, not as one banner, because the evaluator already runs per printer.
- Filtering is a pure rule, `PrinterFilter`, applied after the lists load; the start, cancel and remove gates and the confirmation dialogs are the same code for both queues.
- The printer id reaches `QueueViewModel` and `MaintenanceViewModel` as a constructor value, to keep their public function count down.

## Design

```text
Printers tab                          Printer page (tap a card)
+------------------------------+      +--------------------------------+
| X2D            PRINTING      |      | < X2D                          |
| Remote control: Full       > |      | [Overview] [Queue] [Tools]     |
+------------------------------+      |  Overview: status, camera,     |
| P1S               IDLE       |      |   controls, AMS                |
| Remote control: Limited    > |      |  Queue: this printer's jobs    |
+------------------------------+      |  Tools: this printer's upkeep  |
[Printers][Queue][Filament][History][Settings]   and tools
 Queue here = all printers
```

On a wide screen the printer page is the right pane of the Printers tab (design 28).

```text
 tap a card              -> printer page, Overview tab
 tap the Remote control line -> the same page, Tools tab (ToolsRequest)
 printer page Queue tab  -> QueueScreen with this printer's id
 printer page Tools tab  -> MaintenanceScreen (and tools) with this printer's id
 bottom bar Queue        -> QueueScreen with no printer id = all printers
 Back from the printer page -> BackPolicy: Routes.DETAIL returns to the main page
```

```text
 PrintersViewModel (polled Printer.capabilities) -> ReadinessEvaluator -> PrintersUiState.readiness -> PrinterCard line
 QueueRepository -> QueueViewModel(printerId?) -> PrinterFilter.queue -> QueueScreen
 MaintenanceRepository -> MaintenanceViewModel(printerId?) -> PrinterFilter.maintenance -> MaintenanceScreen
```

## Behavior

- A null printer id returns the list unchanged.
- A queue job not yet assigned to a printer appears only in the all-printers list.
- Nothing extra is sent to the printer or server; the filter runs on data already read.

## Tests

Unit tests cover `PrinterFilter` and `BackPolicy`.
How the screens look is not covered by tests.
