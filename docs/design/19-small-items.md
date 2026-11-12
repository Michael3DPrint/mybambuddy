# 19: Small behaviors

Small behaviors that do not need their own design: release-safe logging, single-tray read-back, blocking queue start without a printer, the check that documents name real code, and the instrumented UI tests.
All are built.

## Release-safe logging

`PrintDeckLogger` writes through a `LogSink`.
When `LogPolicy.verbose` is off, which is every non-debuggable build, it drops debug and info lines and writes error and warning lines with only the exception's class name, so no stack trace or server-supplied text reaches logcat.
`PrintDeckApp` sets the flag from the debuggable flag of the app.
`PrintDeckLoggerTest` checks it with a fake sink.

## Single-tray read-back

After a slot assignment, `SlotAssignmentViewModel` re-reads the one tray with `AmsRepository.getTray`, which reads `GET printers/{id}/status` for that printer only, never the whole AMS list or the saved copy.
It looks after 3 s and again after 7 s, compares the result to the assigned spool and reports confirmed, still different, or unreadable.

```text
SlotAssignmentViewModel.readBack -> AmsRepository.getTray -> AmsRepositoryImpl -> AmsApi.getPrinterAmsStatus
  -> SlotReadBack.compare -> message; a changed event refreshes the AMS list
```

## Queue start without a printer

A queue job with no printer id is picked by the server, so the app cannot check readiness.
The app blocks Start for it: `QueueRepositoryImpl` returns `UNSUPPORTED` with `QueueRepository.NO_PRINTER_MESSAGE` ("Choose a printer for this job in Bambuddy first."), and the Queue screen shows the same text.
Guessing a printer was rejected: Start fails closed, as slot changes do.

## Documents name real code

`DocumentationIdentifiersTest` reads `docs/design` and `docs/ENGINEERING_STANDARDS.md`, extracts backticked CamelCase names and fails if one has no declaration in the app code and is not on `docs/doc-identifier-allowlist.txt`.
An allowlist entry that exists in code, or that no document names, also fails.

## Compose UI tests

`GatingUiTest` and `AccessibilityUiTest` are instrumented tests that need a device; the `ui-test-junit4` dependency is `androidTestImplementation` only.
They are not run by the unit-test check.
