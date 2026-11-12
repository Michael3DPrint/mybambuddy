# 06: Low stock and usage history

`LowStockPolicy` is the one definition of "running low" for the spool list, the spool detail sheet, the AMS slot tiles and the alerts.
The spool detail sheet also lists how a spool was used.
Built; the usage reply shape is only partly verified against a live server, so every field is optional.

## Decisions

- One pure object, so the screens and the alerts cannot disagree.
- A spool's own `low_stock_threshold_pct` wins and is kept within 1 to 100; otherwise `LowStockPolicy.DEFAULT_THRESHOLD_PCT` (20 percent) applies.
  There is no setting for the default.
- The AMS slot tile has only a fill estimate, so it always uses the default threshold.
- An unknown or negative remaining amount is never low.
- An alert re-arms only when the spool is above the threshold plus `LowStockPolicy.REARM_MARGIN_PCT`, so a hovering value does not repeat it.

## Design

```text
Spool list row          AMS slot tile        Spool detail > Usage
⚠ LOW · PLA Metal       low state in the     2026-11-08  benchy  12 g
(error color)           tile and its         Empty: "No usage recorded yet."
                        spoken text
```

```text
FilamentScreen -> FilamentViewModel.loadUsage -> SpoolRepository.getUsage (SpoolRepositoryImpl)
  Spoolman mode:  SpoolUsageApi GET spoolman/inventory/spools/{id}/usage
  built-in mode:  SpoolUsageApi GET inventory/spools/{id}/usage
  SpoolUsageDto -> UsageEntry(date, printName, grams, status) -> UsageState -> SpoolDetailSheet
Spool.isLow (list row, detail sheet), SlotTile (AmsSlotRow), AlertRules -> LowStockPolicy
```

## Behavior

- Usage rows are capped at 200, sorted newest first; the sheet shows the first 10.
- The usage date is the first 10 characters of `created_at`; a missing print name shows "Print".
- A failed usage read shows the error text in the sheet.
- A low spool also shows "Running low" text in the detail sheet.
- Tapping a low-filament notification opens the spool (design 01).

## Tests

`LowStockPolicyTest` covers the boundaries, unknown and negative values, a spool's own threshold and re-arming.
`FilamentViewModelTest` covers usage loading.
Real usage rows have not been checked against a server with usage data.
