# 16: Caching and offline policy

Read-only data is saved after each live success and served when the server cannot be reached.
Control actions, printer status and secrets are never cached, and the background alert check never reads saved data.
Built.

## Decisions

- Caching is a decorator around each read repository, so the real repositories stay unaware of it.
- All decorators share one set of rules, `CachedSource`, so they cannot drift apart.
- Only unreachable errors (network failure, 5xx, 503) are served from the saved copy.
  A 401 or 403 shows the real error instead of hiding behind old data.
- Each cache has a maximum age; `AppContainer` passes the ages from `CacheAges` and the other caches use the 7-day default of `JsonCacheStore`.
  Older copies are ignored and the real error shows.
- Printer status (`fetchPrinterStatus`) is never cached: a saved "IDLE" must not let a slot change or command through while the printer is busy.
- Control results are never cached or queued for replay; a pause replayed an hour later on another print would be a hazard.
- The background alert runner gets the live repositories, not the decorators.
  A decorator turns "server unreachable" into saved data, and the runner could not tell that apart from current data.
- Rejected: Room, because a few small JSON snapshots need no relational store, and a longer age for printer state, because stale printer state is worse than none.

## Design

```text
 screen --> ViewModel --> domain/repo interface --> Caching*Repository --+--> live *RepositoryImpl --> Retrofit API --> server
                                                                        '--> JsonCacheStore (filesDir/cache/<name>.json)
 Caching*Repository --> CachedSource --> ConnectivityStatus --> OfflineBanner, ViewModel control checks
 Printers only: Caching*Repository --> WidgetFeedingPrinterRepository --> live repository (also feeds the widget snapshot store)
 Background alerts: AlertRunner --> live repositories directly (no cache, no ConnectivityStatus)
```

`JsonCacheStore` writes `{version, savedAt, data}` to a temp file and renames it, ignores files over 1 MB, other versions or unreadable content, and treats all of these as no cache.
`ConnectivityStatus` holds one `OfflineState`; the banner and the control-disabling checks in the detail, queue, maintenance and spool-weighing ViewModels read it.

```text
 live result
   Success -> save (throttled), markOnline, return it
   Error, not unreachable -> return the error
   Error, unreachable
     saved copy within max age -> markOffline(savedAt), return Success(copy)
     no usable copy            -> markUnreachable, return the error
```

`DefaultConnectivityStatus(failuresBeforeOffline = 2)` shows offline only after two failures in a row; any success resets the count.
One dropped request is served from the saved copy without a banner.

## Behavior

| Data | Cached | Max age |
|---|---|---|
| Printer list and single printer (read from the saved list) | yes | 1 hour; saved at most every 30 s because the list is re-emitted each poll |
| AMS overview | yes | 1 hour |
| Queue | yes | 1 hour |
| Spools | yes | 7 days |
| Maintenance | yes | 7 days |
| History, first page only | yes | 30 days; later pages return the real error when unreachable |
| Alert memory | yes | never expires |
| Inventory mode | memory 60 s, plus the last known mode saved on disk | a failed probe is never remembered as an answer |
| Camera snapshots | yes, memory and private cache folder (`SnapshotCache`) | no age limit |
| SpoolmanDB catalog search | no | the search needs a connection |
| Printer status, control results, API key, tokens | never | not applicable |

- With no saved copy and an unreachable server the screen shows the error and the banner reads "Can't reach the server and there is no saved data."
  Controls are disabled in both banner states.
- With a copy, the banner reads "Offline — showing saved data from <time>" and says controls are disabled.
  `OfflineBanner` has no retry button; screens refresh on their own poll or pull.
- `ReadRetryInterceptor` retries a GET or HEAD once when the network drops it, which is how a server-closed keep-alive connection shows up.
  The client sets `retryOnConnectionFailure(false)`, so a write is never sent twice, and timeouts are not retried.
- A cached read still arrives as `ApiResult.Success` with no marker on the value; only `ConnectivityStatus` says it is a copy.
  The state is one flag written by every decorator, last writer wins.
- An HTML reply (captive portal or proxy) becomes a parse error, which is shown and never replaced by saved data.
  `ResponseSizeLimitInterceptor` cuts off oversize replies.

## Tests

- OfflineCacheTest and CachedSourceTest cover write, serve, expiry, corrupt and oversize files, atomic rename, throttled writes and the unreachable-without-cache path.
- ReadRetryInterceptorTest covers one retry for GET and none for POST or timeouts; DefaultConnectivityStatusTest covers the two-failure rule and reset on success.
- Not verified by a test: that `AppContainer` passes the live repositories to `AlertRunner`; no wiring test pins it.
