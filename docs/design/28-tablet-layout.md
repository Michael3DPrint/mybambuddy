# 28: Tablet and foldable layout

At a screen width of 600 dp or more, the bottom bar becomes a navigation rail on the left, and on the Printers tab the printer list sits beside the selected printer's page.
Below 600 dp nothing changes.
The width is read live, so folding or rotating switches layout and keeps the selection.
Built; layout only, no server calls.

## Decisions

- `ScreenWidthPolicy.isWide` is the one rule: 600 dp or more.
- `PrinterDetailPane` is the printer page used by both the phone page and the tablet pane, so both gate and show the same things.
- `AppNavigation.kt` holds both `AppBottomBar` and `AppNavigationRail` over one tab list.
- Back on a wide screen follows the main page rule, because the detail page does not exist as a separate page there: another tab goes to Printers, Printers leaves the app (design 22).
- The adaptive-layout libraries in the build file are not used; OI-26 tracks removing them.

## Design

```text
 wide (600 dp or more)                                   phone (under 600 dp)
 +-------+---------------------+----------------------+   +------------------+
 | rail  | Printers list       | Selected printer     |   | Printers list    |
 | Print |  X2D  PRINTING 62%  |  tabs, camera,       |   |  tap = detail    |
 | Queue |  P1S  IDLE          |  status, controls    |   | [bottom bar]     |
 | Filam |                     |                      |   +------------------+
 | Hist  |   (nothing selected: "Select a printer")   |
 | Sett  |                                            |
 +-------+---------------------+----------------------+
```

```text
 screenWidthDp -> ScreenWidthPolicy.isWide
   wide:  rail instead of bottom bar; PrintersTab shows list and detail pane side by side
          tap a printer -> selectedPrinterId changes, the right pane follows; the pane's Back clears it
   phone: tap a printer -> Routes.DETAIL page; Back returns
 Routes.DETAIL on a phone, then unfolded -> drawn as the main page with that printer in the right pane
```

State is `selectedPrinterId` and the current route in `MainActivity`, kept across rotation and folding; nothing is persisted or sent.

## Tests

Unit tests cover the width policy at the 600 dp boundary.
