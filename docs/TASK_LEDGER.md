# Task ledger

This file lists what the app has gained, one line per finished item, grouped by area.
Open work is in `docs/OPEN_ITEMS.md`, and the behavior and reasons are in `docs/design/`.
When an open item is finished, move it here in the same change.

## Printers and control

- Printer list and detail pages with status, progress, camera snapshot and live stream.
- HMS errors in plain language, with clearing from the overview and a failed-print state.
- Pause, resume, cancel, print speed, chamber light and plate cleared, each behind a gate that checks Developer LAN mode.
- Remote control shown as Full or Limited, with the tools that are off and why.
- Printer pages with Overview, Queue and Tools tabs, and a Tools tab made of expandable status sections.
- Connection diagnostics per printer, and a slicing readiness check.

## Print from the phone and the queue

- Print from a file or a MakerWorld link, with a review that shows the slicer's time and filament estimate.
- A slice page with plate, AMS filament, process, options and scale, and settings that remember the last use.
- Re-slicing a file that has no G-code.
- Add to queue as a staged item, then start with a start verdict and a filament-shortfall prompt.
- Queue cards with a picture, a job page to change settings or re-slice, and previous prints with run again.

## Filament and the AMS

- Spool inventory through Bambuddy and Spoolman, add spool and SpoolmanDB catalogue search.
- Barcode scan with a consent-based online lookup.
- NFC: write OpenTag3D tags, link and read tags, and open a spool from a tag tapped outside the app.
- AMS units with humidity and drying presets, shown in a box with a settings popup.
- Safe slot assignment, an AMS slot sheet, load and unload filament, and update the inventory from the printer.
- Weigh a spool and update its remaining weight.
- Which printer slot each spool is loaded in, on the Spools list and the slot sheet.

## Alerts, history and care

- Alerts for print finished or failed, HMS errors, offline, low filament and maintenance, with deep links to the spool.
- A help card for instant alerts through ntfy or Pushover.
- Maintenance reminders with a badge on the printer card.
- Low-stock warnings, usage history, print history and statistics.
- A home-screen widget that keeps the last known state.
- Offline views of the last data, with an offline banner.

## Setup and settings

- Server connection test and a warning for plain-HTTP addresses to public hosts.
- Setup help with Docker and Home Assistant OS guides, a welcome screen with phone checks, Find Bambuddy on the home Wi-Fi, and Help buttons on errors.
- Light and dark theme that applies at once, and wallpaper colours on Android 12 and later.
- A Settings screen with Connection and App tabs.

## Layout and accessibility

- Back goes to the previous page.
- A navigation rail and side-by-side layout on tablets and foldables.
- Spoken descriptions, headings, live regions and large-font layouts, with contrast checked in unit tests.
- Shared UI patterns: one tab row, one error notice with a Help link, and navigation rows.

## Engineering

- The API split into small interfaces per area, and an API map that the build checks.
- Build checks for standards, documentation and dependency hashes, run by CI on GitHub.
- Secret and vulnerability scans, a security policy and third-party notices.
- The package name `io.github.michael3dprint.mybambuddy` and the Apache-2.0 licence.
