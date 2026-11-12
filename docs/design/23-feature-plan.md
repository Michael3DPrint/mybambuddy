# 23: Feature overview

This is the index of the later features and the decisions that no feature design records.
Every feature below is built except spool QR labels, which are parked (OI-43).
The linked designs hold the details.

## Features and where they are described

| Feature | Where |
|---|---|
| Instant-alerts help card on the Alerts screen | below; guide `docs/guides/INSTANT_ALERTS.md` |
| Home-screen widget | design 24, design 29 |
| An NFC tag opens its spool from outside the app | below |
| A low-filament alert opens its spool | below |
| Print statistics on the History tab | design 27 |
| Weigh and update a spool | design 26 |
| Print a 3MF from the phone, staged in the queue | design 35, design 38 |
| Slicing readiness check | design 36 |
| Tablet and foldable layout | design 28 |
| Spool QR labels | parked, see OI-43 |

## Decisions

- The features in the table add no network host; each talks only to the saved Bambuddy server.
- Tag text is untrusted: `TagText.sanitize` strips control characters and caps the length.
- Spool QR labels are parked.
  The app would draw the code itself and need one small QR library; whether to accept that dependency is open.
  Not verified: that Bambuddy has no spool-label route.
- Server-side slicing is optional.
  The app talks only to Bambuddy, which talks to an optional slicer service, so a file already sliced on a desktop (`.gcode.3mf`) queues without it.

## Instant-alerts card

`AlertsHelpCard` sits at the top of the Alerts screen with the setup steps from `docs/guides/INSTANT_ALERTS.md`.
The Open button opens the saved server address in the browser and is disabled when there is no address.
Hide is stored through `AlertSettingsViewModel` in the alert preferences, and a small link brings the card back.
Android runs the background alert check about every 15 minutes at best, which is why the card points to Bambuddy's own push providers.

## NFC tag opens its spool

The manifest gives `MainActivity` an NDEF intent filter for MIME `application/opentag3d`, the type the app writes.
The tag goes through `AndroidNfcGateway` into the same read path as the in-app scan: match the tag UID against the spool list (read-only), then show the spool or the unknown-tag card.
Nothing is written, assigned or sent without a further tap.
Not verified: whether Bambu's own RFID spool tags trigger this; they are believed not to be NDEF, so only OpenTag3D tags do.

## Low-filament alert opens the spool

`AndroidAlertNotifier` adds the alert's spool id to the tap intent as `DeepLinks.EXTRA_SPOOL_ID`.
`DeepLinks.parse` accepts only positive integers and turns the intent into a link; a spool link opens that spool on the Filament tab.
Back follows design 22.

## Tests

Each feature's tests are listed in its own design.
The on-device look of the widget, the tablet layout and the tag intent is not covered by unit tests.
