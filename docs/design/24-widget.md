# 24: Home-screen widget

One resizable, read-only home-screen widget lists up to three printers with name, state, progress bar, time left and the time of the data.
Each printer sits in its own rounded box under a "Printers" title, so the tap target is visible.
The widget draws a saved snapshot and never reads the server itself; a 15-minute worker, the refresh button and the app's own printer reads keep the snapshot current.
Built.

## Decisions

- Classic `AppWidgetProvider` and `RemoteViews`: no new dependency.
- No control buttons: a widget is reachable from the home screen without opening the app, so it must not send commands.
  The refresh button only repeats a read.
- The snapshot holds printer names and states only, no address and no key.
- `updatePeriodMillis` is 0; WorkManager's 15-minute minimum is the cadence, and the stamp says when the data is from.
- One layout for every size, with room for three printer boxes (`targetCellWidth` 3, `targetCellHeight` 2, resizable both ways).
- The feed from the app sits under the app's cache layer, so cached data served while the server is unreachable is never written as fresh.

## Design

```text
+--------------------------------------------+
| Printers                          [refresh]|
| +----------------------------------------+ |
| | X2D                        PRINTING    | |
| | ##########.......   62%  1h 12m        | |
| +----------------------------------------+ |
| +----------------------------------------+ |
| | P1S                        IDLE        | |
| +----------------------------------------+ |
| updated 14:02                              |
+--------------------------------------------+
```

- Rows beyond the printer count are hidden with their box.
- Tap a box: open that printer (`DeepLinks`, printer id only).
- Tap the title: open the app.
- Offline or server unreachable: the old rows stay and the footer reads "saved 13:40".
- No data yet: the footer reads "Open My Bambuddy to set up".

```text
 widget added   -> PrinterWidgetProvider.onEnabled -> WidgetScheduler.start
                   (periodic WidgetUpdateWorker every 15 min, network required, plus one run now)
 last one removed -> onDisabled -> WidgetScheduler.stop
 refresh button -> ACTION_REFRESH -> WidgetScheduler.refreshNow -> WidgetUpdateWorker
 WidgetUpdateWorker -> WidgetRefresher.refresh()
     -> PrinterRepository.fetchPrinters()
          success -> WidgetSnapshotMapper.from -> WidgetSnapshotStore.save -> WidgetRenderer.render
          error   -> WidgetSnapshotStore.load().copy(stale = true) -> save -> render
                     (nothing saved: render(null), the empty message)
 onUpdate (resize, restart, theme, new widget) -> WidgetSnapshotStore.load -> render, no request
```

```text
 app polling and pull-to-refresh
   -> WidgetFeedingPrinterRepository (wraps the live printer repository, below the cache layer)
        success -> WidgetSnapshotMapper -> save + render   (skipped when the rows equal the saved fresh
                                                             ones and are under a minute old)
        error   -> nothing

 Bambuddy GET printers -> Printer list -> WidgetSnapshotMapper -> WidgetSnapshot (rows, takenAtMs, stale)
   -> WidgetSnapshotFileStore over JsonCacheStore "widget" (app-private cache file)
   -> AndroidWidgetRenderer -> RemoteViews on every instance
```

## Behavior

- `WidgetSnapshotMapper` keeps at most three rows.
  Progress shows only while printing or paused; time left only while printing.
- `WidgetSnapshotMapper.stamp` writes "updated HH:mm" for fresh data and "saved HH:mm" for stale data, using the time the data was read.
- The worker catches every exception and reports success; the next run tries again.
- Each row carries one spoken summary (`WidgetSpeech`); the texts inside the row are hidden from the screen reader.
- The row box fill and outline are drawables with colors for light and dark themes.

## Tests

Unit tests cover the mapper (row cap, state text, progress rule, time text) and the stamp.
They cover `WidgetSpeech`, the spoken summary of a row.
They cover `WidgetRefresher` (success, failure keeps old rows as stale, nothing saved).
They cover `WidgetFeedingPrinterRepository` (success saves and draws, failure changes nothing, same rows inside a minute are skipped).
How the widget looks on a home screen, in light and dark, is not covered by tests.
