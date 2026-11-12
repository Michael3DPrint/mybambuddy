# 01: Alerts

A background check every 15 minutes tells the user on the phone when a print finishes or fails, a printer reports a new error or goes offline, a spool runs low, or upkeep becomes due.
`AlertRules` is a pure function over the last and the current observation; `AlertRunner` fetches, decides, notifies and remembers.
Built; the check only reads from the server.

## Decisions

- WorkManager periodic work (`AlertWorker`, 15 minutes, network required) instead of a foreground service: no permanent notification, little battery use.
  The cost is a delay of up to one interval; the Alerts screen points to Bambuddy's own ntfy or Pushover providers for instant alerts (`AlertsHelpCard`).
- The first run only records the situation, so existing conditions do not flood the user.
- The check reads the undecorated repositories, so an unreachable server is not mistaken for saved data (design 16).
- Nothing is alerted when every source fails.
  A source that failed keeps its old memory, so nothing is lost or repeated when it returns.
- Notification text never contains the API key or the server address.
  There are four notification channels (print results, printer errors and offline, filament, maintenance) so each can be muted in Android settings.

## Design

```text
Settings > Alerts
+-------------------------------+
| Alerts                  [on]  |
| Print finished          [x]   |
| Print failed / stopped  [x]   |
| New printer error       [x]   |
| Printer went offline    [ ]   |
| Filament running low    [x]   |
| Maintenance due         [x]   |
| Instant alerts help card      |
+-------------------------------+
```

```text
WorkManager tick (15 min, network) -> AlertWorker -> AlertRunner.run
  alerts off? -> stop
  fetch printers, spools, maintenance overview (live repositories)
  all three failed? -> stop
  AlertRules.evaluate(previous memory, Observation, AlertSettings) -> alerts + new memory
  merge memory of failed sources, save with AlertMemoryFileStore
  post at most 5 with AndroidAlertNotifier, one summary for the rest
```

```text
Alerts screen -> AlertSettingsViewModel -> AlertSettingsStore (AppContainer)
                   -> AlertPrefs.save + AlertScheduler.apply (enqueue or cancel work)
Notification tap -> PendingIntent (immutable, numeric printer or spool id)
                   -> MainActivity -> DeepLinks.parse -> DeepLink.Printer or DeepLink.Spool
```

## Behavior

- Print finished: the printer was `PRINTING` or `PAUSED` and is now `FINISHED`.
  Print failed: the printer was printing or paused and is now `ERROR` or `FAILED`.
- New error: an HMS code is present now that was not in the previous memory.
- Offline: the printer was reported `OFFLINE` on two checks in a row, once per streak.
- Low filament: `LowStockPolicy.isLow` becomes true for a spool that was not low; it re-arms when the spool is above the threshold plus the margin (design 06).
- Maintenance due: an item is newly `DueState.DUE`.
- Defaults: alerts off; offline alerts off; the other categories on.
  Settings are plain `SharedPreferences` in `AlertPrefs`.
- A low-filament alert opens the spool; the others open the printer.

## Tests

`AlertsTest` covers every transition, the first run, the offline streak, re-arming and the per-run limit with fakes.
`AlertSettingsViewModelTest` covers the settings.
`DeepLinksTest` covers the id parsing.
Delivery of real notifications, the permission prompt and the 15-minute timing are only visible on a device.
