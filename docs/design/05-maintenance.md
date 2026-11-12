# 05: Maintenance reminders

Each printer's Maintenance tab lists its upkeep items with progress and due state and lets the user mark an item done; printer cards show a "Maintenance: N due" or "Maintenance: N soon" badge.
Defining maintenance types stays in Bambuddy.
Built; marking done is a write that is confirmed, sent once and refused while offline.

## Decisions

- The app reads and marks done only; creating or editing types is out of scope.
- `DueState` comes from the server's `is_due` and `is_warning` flags; progress is clamped to 0..1.
- The Printers view model reads the overview once when the screen opens or refreshes, through the cached repository, so there is no request per card or per poll.
  A failed read keeps the badges already shown.
- Maintenance items the server marks disabled are not shown.

## Design

```text
Printer page > Maintenance                    Printer card
+----------------------------------+          +----------------------+
| X2D-Sample                       |          | X2D-Sample           |
| 12.3 print hours . 1 due . 1 soon|          | Maintenance: 1 due   |
| Lubricate rods   DUE . 3 d overdue          +----------------------+
|   [Mark done]                    |
| Clean rods       Soon . 5 h left |
|   [Mark done]                    |
+----------------------------------+
Empty: "No maintenance found" / "Nothing tracked yet"
```

```text
MaintenanceScreen -> MaintenanceViewModel -> MaintenanceRepository
  (CachingMaintenanceRepository -> MaintenanceRepositoryImpl) -> MaintenanceApi
  overview   GET maintenance/overview           PrinterMaintenanceDto -> PrinterMaintenance
  mark done  POST maintenance/items/{id}/perform  (confirmation dialog first, never cached)
PrintersViewModel -> MaintenanceRepository.getOverview -> MaintenanceBadge.label -> PrinterCard
AlertRunner -> getOverview -> DueState.DUE items -> AlertRules (design 01)
```

## Behavior

- The remaining label reads hours or days by the item's interval type, for example "50 h left" or "3 d overdue".
- "Mark done" appears only on items that are due or coming due.
- Offline, "Mark done" shows a message and sends nothing.
- `MaintenanceBadge` shows the due count first, then the coming-due count, otherwise nothing.

## Tests

`MaintenanceRepositoryTest` covers parsing, due-state mapping, clamping and the perform request.
`MaintenanceViewModelTest` covers loading and offline refusal; `MaintenanceBadgeTest` covers the badge.
The two routes are not verified against Bambuddy's source.
The screens' look is only visible on a device.
