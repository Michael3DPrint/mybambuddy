# 08: Split the Bambuddy API by area

The Retrofit layer is one small interface per area instead of one large one, so each repository depends only on the calls it uses.
Each interface is built by `ApiClient.createService`, and the connection test builds its own pair from a temporary client.
Built.

## Decisions

- Split by area, not by HTTP verb: each interface has one reason to change.
- The AMS status call (`getPrinterAmsStatus`) lives in `AmsApi`, not `PrinterApi`, because its DTO and consumer differ even though the URL matches the printer status call.
- Both inventory back ends (Spoolman passthrough and built-in) share `InventoryApi` and `AmsApi`; `InventoryModeProvider` chooses which method runs.
- Bambuddy interfaces are created by `ApiClient.createService`.
  Most share one `OkHttpClient`; `PrintFileApi` uses a client with longer limits, `DiagnosticApi` a copy with a longer read timeout, and `SpoolmanDbApi` is built by `ApiClient.createSpoolmanDbApi` on its own plain client.
- Tests fake only the small interface they need; there is no facade that extends all of them.
- Rejected: one interface per endpoint (no cohesion) and a facade over all interfaces (keeps the wide dependency).

## Design

```text
Screen ► ViewModel ► domain repository ► data/repo implementation ► data/api interface ► OkHttp ► Bambuddy

PrinterRepositoryImpl, PollingLiveUpdates, ReadinessCheckImpl ► PrinterApi
PrintControlRepositoryImpl                                    ► PrinterControlApi
HistoryRepositoryImpl, ArchiveRepositoryImpl                  ► HistoryApi
SpoolRepositoryImpl, InventoryModeProvider                    ► InventoryApi
AmsRepositoryImpl                                             ► AmsApi and PrinterApi
CameraTokenProvider, CoilImageLoaderFactory                    ► CameraApi
ServerProbeApis (Setup, Settings test connection)             ► PrinterApi and SystemApi
```

`AppContainer` builds the shared client and each service.
The connection test builds its own client from the typed-in address and key, so `ServerProbeApis.create` makes a `PrinterApi` and a `SystemApi` from that one client.
Other area interfaces (`MaintenanceApi`, `QueueApi`, `SpoolUsageApi`, `SpoolmanDbApi`, `StatsApi`, `SlicingApi`, `PrintFileApi`, `DiagnosticApi`) follow the same rule.

## Tests

`ServerContractTest` and `BambuddyApiTest` call endpoints against a mock server and assert method, path and query, including the trailing-slash endpoints.
The repository tests cover the other areas, for example `QueueRepositoryTest` for `QueueApi`.
