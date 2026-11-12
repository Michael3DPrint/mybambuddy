# 04: Offline view

When the server or the network is unreachable, the app shows the last saved printers, AMS, spools, maintenance, queue and first history page, marks them as old and disables controls.
One `CachedSource` holds the rules and every `CachingXRepository` uses it; `ConnectivityStatus` drives the banner.
Built; design 16 holds the wider caching policy.

## Decisions

- A decorator per repository keeps the live repositories unchanged.
- Each cache is one JSON file in `filesDir/cache`, written to a temp file and renamed, versioned, capped at 1 MB; a corrupt, oversize or other-version file counts as no cache.
- Controls are disabled while offline instead of queued, so nothing is replayed later.
- `CachingPrinterRepository.fetchPrinterStatus` is never cached: a saved "IDLE" must not let a command through while the printer is busy.
- Never cached: control results, tokens, camera frames (`SnapshotCache` handles pictures) and the API key.
- Offline starts after two failures in a row (`DefaultConnectivityStatus`), so one dropped request does not disable the app.
- The background alert check and the widget refresh read the live repositories (design 01, design 24).

## Design

```text
+----------------------------------------------------------+
| Offline - showing saved data from 14:02. Controls are    |
| disabled.                                                |
+----------------------------------------------------------+
Without a saved copy: "Can't reach the server and there is no saved data. Controls are disabled."
```

```text
CachedSource.serve(result)
  Success -> save (throttled by minWriteGapMs) -> markOnline -> result
  Error, not unreachable -> error unchanged
  Error, unreachable, saved copy younger than max age -> markOffline(savedAt) -> Success(copy)
  Error, unreachable, no usable copy -> markUnreachable -> error unchanged
```

```text
CachingXRepository -> CachedSource -> JsonCacheStore<XCache> (filesDir/cache/<name>.json)
ConnectivityStatus.state -> OfflineBanner (MainActivity); QueueViewModel, MaintenanceViewModel,
SpoolWeighViewModel and PrinterDetailViewModel read it to refuse actions
```

## Behavior

- Max age per cache (`CacheAges`): printers, AMS and queue one hour; history 30 days; spools and maintenance the 7-day default of `JsonCacheStore`.
- Printer saves are at most 30 seconds apart because the list is re-emitted on every poll.
- History caches only the first page; later pages return the real error.
- The banner has no Retry button; pull-to-refresh and the refresh buttons retry.
- The banner is a polite live region for screen readers.

## Tests

`CachedSourceTest` and `OfflineCacheTest` cover save, serve, expiry, corrupt and oversize files, and the offline marks.
`DefaultConnectivityStatusTest` covers the failure count.
The banner's look and real network loss are only visible on a device.
