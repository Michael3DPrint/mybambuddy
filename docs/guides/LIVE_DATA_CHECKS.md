# Live-data checks

The print queue, the usage history and the online barcode lookup were built against mocks and documented schemas only.
These steps check each one once with real data against your own server.
No step sends a command to the printer unless it says so, and those steps need the printer idle.

## Check the print queue

1. In Bambuddy, add two or more jobs to the queue for a printer (the app cannot add jobs).
2. Open MyBambuddy and the queue screen.
3. Check that each job's name, printer, position and status match Bambuddy.
4. Check that a job with no printer shows "Choose a printer for this job in Bambuddy first." and that its Start button is off.
5. Reorder two jobs, then open Bambuddy and check the order matches.
6. Remove a waiting job and check that it disappears in both places.
7. Printer command, idle printer only: tap "Start now" on a job whose printer is ready.
   If the printer's Developer LAN mode is off, the button must be disabled with the reason.

## Check the usage history

1. After a print has consumed filament, open that spool in the Spools list and find its usage section.
2. Check that the date, print name, grams used and status are plausible and match Bambuddy's own usage view for the spool.

## Check the online barcode lookup

1. Scan a retail barcode (EAN or UPC) on a spool box.
   The first time, the app asks for consent before it sends the digits to upcitemdb.com.
2. Check that a product with a sensible brand and title is found and the Add spool form is prefilled.
3. Scan a barcode the service does not know and check that the app says so instead of failing.
4. If you get a "too many requests" message, wait and try again later, because the free tier is limited.
5. Decline the consent once.
   Check that nothing is sent and that you can still add the spool by hand.

## Record the result

1. For each of the three checks, note "matches" or what differed (a screenshot helps) in `docs/TASK_LEDGER.md`.
2. File each mismatch as a small item in `docs/OPEN_ITEMS.md`.
