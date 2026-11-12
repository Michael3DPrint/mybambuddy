# Filament tab: spools, AMS, drying, NFC tags and barcode

The Filament tab has two sections: Spools (list, add, detail) and AMS & Drying.
Spools can be added by hand, from the product catalog, or by scanning a barcode or QR code.
A spool can get an OpenTag3D NFC tag, and a scanned tag opens its spool or loads it into an AMS slot.
Built.
Slot loading rules are in design 9 and design 33, spool locations in design 49.

## Decisions

- Spools are read and created through Bambuddy's inventory routes, which hide whether Spoolman or Bambuddy's own inventory is behind them.
  `InventoryModeProvider` reads `spoolman/status` once and picks the `spoolman/inventory/...` or `inventory/...` routes.
- The catalog (SpoolmanDB) is read directly from Spoolman, because the Bambuddy routes do not expose it.
  Its address comes from Bambuddy; `CatalogRepositoryImpl` swaps in the Bambuddy host when Spoolman reports an address the phone cannot reach.
- Tags use OpenTag3D on NTAG215, the format Bambuddy's own tag encoder writes (`backend/app/services/opentag3d.py` in Bambuddy), so tags work with both.
  `OpenTag3d` writes a 102-byte payload: material 5 characters, modifiers 5, brand 16, color name 32; longer text is cut.
  A tag with no OpenTag3D record, such as a Bambu spool's own tag, gives the app only its UID.
- The NFC reader (NFC-A and NFC-V) is on only while a read or write dialog is open (`NfcGateway.setListening`).
  A tag tapped outside a dialog arrives through the manifest's `NDEF_DISCOVERED` filter for `application/opentag3d`; the app only reads it and shows the scan dialog.
- Barcode scanning uses Google's code scanner (`play-services-code-scanner`), which needs Google Play services; the app declares no camera permission.
- Drying and slot assignment are control calls: one attempt, never retried.
  Drying shows the server's refusal message as is and starts only after a confirmation dialog.

## Design

Filament tab:

```text
 Filament                                   (read tag)
 [ Spools ] [ AMS & Drying ]                 <- AppTabRow
 Spools: one card per spool, remaining grams, where it is loaded (SpoolLocations)   [ + ] opens Add spool
 AMS & Drying: one card per AMS unit: humidity, temperature, slots, drying panel
```

Add spool sheet: a scan button, a catalog search box (results fill the form), then brand, material, type, color name, color hex and net weight.
Spool detail sheet: remaining grams, usage list, Weigh (only when the Spoolman inventory is in use), Load into AMS, then Write NFC tag with the linked tag or "No NFC tag linked yet" (without NFC, a note says so).

Add by barcode (`BarcodeResolver`):

```text
 Scan -> GmsBarcodeScanner -> text (cancel or scanner failure: back to the sheet)
   -> BarcodeParser.parse
      JSON with a material field   -> Draft: form filled, user reviews and saves
      8 to 14 digits (EAN or UPC)  -> consent ASK:    ask the user, then look up the digits online
                                       consent ALWAYS: look up online
                                       consent NEVER:  "not known locally, search by name"
                                       online hit -> catalog search box filled with the cleaned title; miss or error -> message
      other text                   -> catalog search box filled (empty text -> message)
```

Write and read an NFC tag (`NfcTagViewModel`):

```text
 Spool detail -> Write NFC tag -> dialog, reader on
   tag held -> OpenTag3d.encode -> writeMimeRecord (application/opentag3d)
      TooSmall / ReadOnly / Failed -> message, no link made
      Success -> SpoolRepository.linkTag(uid)
                   ok    -> "Tag written and linked", spool list reloads
                   error -> "Tag written, but linking failed: <server message>" (for example the tag belongs to another spool)
 Scan NFC tag -> read -> match the UID against the spool list
      known spool   -> Open spool, or Load into AMS (see below)
      unknown tag   -> shows the OpenTag3D data it carries, which can fill the add form (a tag without such data shows only its ID)
```

Load a scanned spool into a slot:

1. `NfcTagViewModel.requestLoad` emits a request, and `FilamentScreen` passes it to `SlotAssignmentViewModel.beginLoad`.
2. The user picks a slot, and `SlotAssignmentViewModel.requestLoad` runs the preflight and warnings.
3. `confirmLoad` sends one assignment after the user confirms.

The assignment can reconfigure the slot's type, color and temperatures on the printer (design 9); it does not feed filament.
A slot can also be filled by scanning a tag for that slot (`SlotNfcScanViewModel`).

Drying (`AmsDryingControls`): a material preset, temperature in steps of 5 within 45 to 85 °C, duration 1 to 24 h, then a confirmation dialog, then start.
While drying, the panel shows the state and a Stop button.
Presets in `DryingPresets`: PLA 55 °C, PETG 65, TPU 70, ABS/ASA 80 and PC 80 for 8 h; PA/Nylon 85 °C for 12 h.
A unit that cannot dry says so.

Data flow:

```text
 FilamentScreen -> FilamentViewModel -> SpoolRepository (CachingSpoolRepository -> SpoolRepositoryImpl)
                                         -> InventoryApi: GET/POST (spoolman/)inventory/spools, PATCH tag link and weight
                                         -> InventoryModeProvider: GET spoolman/status (cached)
                                         -> JsonCacheStore: saved spool list, served when the server is unreachable
                -> CatalogRepository (CatalogRepositoryImpl) -> SpoolmanDbApi on Spoolman, address from InventoryModeProvider
 FilamentScreen -> BarcodeIntakeViewModel -> BarcodeScanner (GmsBarcodeScanner), BarcodeResolver -> EanLookup (UpcItemDbLookup, https to upcitemdb.com)
                -> BarcodeOutcome -> FilamentViewModel.openAdd / onQueryChanged
 FilamentScreen -> NfcTagViewModel -> NfcGateway (AndroidNfcGateway) and SpoolRepository.linkTag
                -> linked event -> FilamentViewModel.refresh
 FilamentScreen -> AmsViewModel -> AmsRepository (CachingAmsRepository -> AmsRepositoryImpl)
                                    -> PrinterApi: printers; AmsApi: printer status, slot assignments; JsonCacheStore for the AMS list
 FilamentScreen -> SlotAssignmentViewModel / DryingViewModel -> AmsRepository.loadSpool / unloadSpool / startDrying / stopDrying
                                    -> AmsApi: POST/DELETE slot assignments, POST printers/{id}/drying/start and stop
                -> changed event -> AmsViewModel.refresh
```

## Behavior

- The AMS list refreshes every 10 s while the AMS & Drying section is visible and the screen is started; the Spools section reads it once to show spool locations.
- Writing to a tag replaces its contents.
  `AndroidNfcGateway` refuses a tag that is read-only or smaller than the encoded message, and formats a blank tag that supports NDEF formatting.
- Tag, scan and lookup text is untrusted: `TagText.sanitize` strips control characters, `OpenTag3d.decode` rejects payloads outside 102 to 1024 bytes, and the barcode text is cut at 256 characters.
- The online lookup sends only the scanned digits, over https to one fixed host, and only with the user's consent (ask, always or never, remembered through `BarcodePrefs`).
- The NFC buttons are shown only when the phone has NFC; the barcode scan button is always shown.
- Not verified: what a real spool's barcode contains, since the text depends on the manufacturer; the lookup chain falls back to manual search.

## Tests

- Unit tests cover the OpenTag3D encoder and decoder, barcode parsing and the resolver chain, the AMS repository, and the view models for barcode intake, NFC, AMS, slot assignment and drying, using fakes.
- No test sends a control call to a real server.
- Only a device shows the scanner, NFC write and read, and drying.
