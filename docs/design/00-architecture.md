# 00: Architecture and shared building blocks

The app has four layers: Compose screens, one ViewModel per screen, repository interfaces with pure domain rules, and data implementations behind small Retrofit interfaces.
`AppContainer` is the only place that builds concrete classes; everything else receives interfaces.
Built; the feature designs only describe what is specific to them.

## Decisions

- Each piece has one reason to change and can be replaced by a fake in tests.
  `MainActivity` does not build repositories.
- Decisions live in small pure functions (`domain/policy`, `domain/alerts`), so unit tests need no device.
- The Retrofit surface is split by area (design 08): `PrinterApi`, `PrinterControlApi`, `CameraApi`, `HistoryApi`, `InventoryApi`, `AmsApi`, `SystemApi`, `QueueApi`, `MaintenanceApi`, `SpoolUsageApi`, `SpoolmanDbApi` and others.
  A new endpoint goes into the interface of its area.
- Offline mode is a decorator per repository (for example `CachingPrinterRepository`), so the live repositories do not change (design 04, design 16).
- Control commands never use the cache and are never retried; see the rule in `ENGINEERING_STANDARDS.md`.
- Dependencies beyond the base set: `androidx.work:work-runtime-ktx` for the periodic alert check, and `play-services-code-scanner` for barcode scanning without a camera permission.
  Versions are pinned in `libs.versions.toml`.
- Permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_LOCAL_NETWORK`, `NFC` and `POST_NOTIFICATIONS` (requested when the user turns alerts on).

## Design

```text
Screen (Compose)        events up, UiState down
  |
ViewModel               one per screen, takes repository interfaces
  |
domain/repo             interfaces: PrinterRepository, AmsRepository, SpoolRepository,
                        QueueRepository, MaintenanceRepository, HistoryRepository, ...
domain/policy, alerts   pure Kotlin rules: LowStockPolicy, HmsExplainer, AlertRules,
                        ReadinessEvaluator, QueueOrdering, MaintenanceBadge
  |
data/repo               implementations (XRepositoryImpl) and CachingXRepository decorators
data/api (+ dto)        Retrofit interfaces and DTOs; mappers turn DTOs into domain models
data/store, data/prefs  JsonCacheStore files, SettingsRepositoryImpl (DataStore plus encrypted key), AlertPrefs
platform/               Android adapters: AlertWorker, AndroidAlertNotifier, GmsBarcodeScanner,
                        AndroidNfcGateway, widget provider
di/AppContainer         composition root
```

Read path with offline mode:

```text
Screen -> ViewModel -> XRepository (interface)
                         |
                  CachingXRepository --> XRepositoryImpl --> XApi (Retrofit) --> Bambuddy
                         |  success: CachedSource saves to JsonCacheStore, ConnectivityStatus.markOnline
                         |  unreachable: serve saved copy, ConnectivityStatus.markOffline
                         v
                  ApiResult.Success(data)

ConnectivityStatus.state --> MainActivity --> OfflineBanner; ViewModels read it to refuse actions

AlertWorker --> AlertRunner --> live (undecorated) repositories --> AlertRules --> AndroidAlertNotifier
                                 AlertMemoryFileStore keeps the last observation
```

## Behavior

- `ApiResult` and `ApiErrorType` carry every outcome.
  A cache hit is `ApiResult.Success` plus an offline `ConnectivityStatus`, so ViewModels stay simple.
- `AppContainer` exposes the decorated repositories to the UI and keeps the live ones private for the alert check and the widget refresh.
- `ReadinessCheckImpl` reads printer status (GET only), and `ReadinessEvaluator` decides the tooling mode and which tools are available.
- `DeveloperModeGuard` implements `PrinterToolGate`, which the repositories that send commands call first (designs 10 and 11).
- `Routes` names the pages `MainActivity` switches between, `AppTab` names the tabs, and `BackPolicy` decides the back step (design 22, design 30).
- A notification tap reaches the app through `DeepLinks`, which accepts only numeric ids.

## Tests

Pure rules, mappers, repositories (MockWebServer) and ViewModels with fakes run in `./gradlew :app:check`.
StandardsEnforcementTest enforces the structural rules.
How screens look and the real printer, notification and NFC behavior are only visible on a device.
