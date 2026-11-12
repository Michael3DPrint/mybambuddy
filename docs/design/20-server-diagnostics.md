# 20: Server connection diagnostics

The Diagnostics group on a printer's Tools tab has a "Connection diagnostics" section.
A tap on Run calls Bambuddy's `GET printers/{id}/diagnostic`, where the server probes the printer, and the app shows one row per check with a short fix.
Built.
The reply shape follows the Bambuddy 1.3.0 source (`backend/app/schemas/printer.py` and `backend/app/services/printer_diagnostic.py`).
Not verified: a call against a live server.

## Decisions

- It runs only on a tap, never at startup, because the server opens test connections to the printer.
  The section says before the first press that Bambuddy tests its connection to the printer and that this takes up to about 15 seconds.
- The server sends only check ids and statuses, so `DiagnosticAdvice` (pure, domain) holds all wording.
  Fixes are general ("check", "may") and appear only for FAIL and WARN.
- The diagnostics call uses its own client with a 45 s read and 60 s call limit, since the server waits up to 10 s for the printer to publish.
- Informational only: the readiness check and the command gates do not use it.
- The reply holds the printer's IP address; the DTO does not read it, so it is never shown, stored or logged.

## Design

```text
 Diagnostics
   Connection diagnostics        [Not run | Running… | Couldn't run | All passed | 1 warning | 2 failed]
     Bambuddy tests its connection to the printer. This takes up to about 15 seconds.
     [ Run ]
   running:  spinner + "Running"
   result:   1 problem, 1 warning
             ✓ Printer reachable (control port)
             ✗ Developer LAN mode   <fix text>
             ! Camera port          <fix text>
             – <title>              (skipped: no fix shown)
             (no checks returned: "Bambuddy returned no checks.")
             [ Run again ]
   error:    "Couldn't run the diagnostics: <reason>"  [ Try again ]
```

```text
DiagnosticsGroup / DiagnosticsSection (Run tap)
  -> DiagnosticsViewModel.run(printerId)          state per printer: Running -> Done(report) | Failed(message)
  -> DiagnosticsRepository (domain/repo)
  -> DiagnosticsRepositoryImpl -> DiagnosticApi.getDiagnostic -> GET printers/{id}/diagnostic
  -> PrinterDiagnosticDto -> toReport() -> DiagnosticReport(overall, checks)
  -> DiagnosticsSection rows: DiagnosticAdvice.forCheck(id, status) -> title and fix
```

Nothing is cached.

## Behavior

- Known check ids: `port_mqtt`, `port_ftps`, `port_rtsps`, `macos_local_network`, `network_mode`, `subnet`, `external_storage`, `mqtt_auth`, `developer_mode`, `printer_publishing`.
  An unknown id is shown as its cleaned name.
- Statuses are pass, warn, fail and skip; anything else is shown as unknown.
- Every DTO field is optional; the mapper keeps at most 20 checks, cleans each id, caps it at 40 characters and drops a blank id.
- One run per printer at a time; a second tap while running is ignored.
- The summary line above the rows comes from the counts of failed and warned checks, so it matches the rows; Bambuddy's own `overall` verdict is not shown.
- The at-a-glance text beside the section title comes from `SectionStatuses.diagnostics`.
- The repository sends one request and does not retry it.

## Tests

Mapper (full reply, missing fields, unknown id and status, over 20 checks, empty list), `DiagnosticAdvice` (titles, fixes only for FAIL and WARN), repository against a mock server, and view model states.
The look of the section is not covered by unit tests.
