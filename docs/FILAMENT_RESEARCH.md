# Filament API reference

This reference lists what the Bambuddy and Spoolman filament APIs offer, checked read-only against Bambuddy 1.3.0 and Spoolman 0.27.1 and the Bambuddy source (`maziggy/bambuddy`).
The app manages spools through Bambuddy's Spoolman passthrough when Spoolman is enabled, and through Bambuddy's built-in inventory otherwise.
NFC tags are written as OpenTag3D, which is byte-compatible with SpoolBuddy.

## Spoolman 0.27.1

- It has no authentication.
- It has full create, read, update and delete for `vendor`, `filament` and `spool`.
- Spools and filaments carry native NFC tags: `POST /spool/{id}/tag` and `/filament/{id}/tag`, which answer 409 with the holder's id on a conflict.
- `POST /tag/scan` takes a uid, a format and a base64 payload, and returns the matched spool or filament.
  It decodes `openprinttag` on the server and can create a spool with `create:true`.
- `GET /external/filament/search` searches SpoolmanDB (manufacturer, name, material, `color_hex`, weight, `spool_weight`, diameter, `extruder_temp`, `bed_temp`).
- The OpenAPI document is at `/api/v1/openapi.json`.

## Bambuddy

Bambuddy has two inventories.

- **Built-in `/inventory/*`.**
  Spools are created automatically from the AMS RFID (`tag_uid`, `tray_uuid`, `data_origin: rfid_auto`).
  It also has a catalog of empty-spool weights and a colour catalog with lookup and search.
- **Spoolman passthrough `/spoolman/inventory/*`.**
  It is active when `spoolman_enabled` is true in `GET settings/`, and `GET /spoolman/status` reports `connected` and `native_tags`.
  It uses a flat InventorySpool model: material, subtype, brand, `color_name`, `rgba`, `label_weight`, `core_weight`, `weight_used`, `storage_location` and more.
  - `POST /spoolman/inventory/spools` creates the vendor and filament in Spoolman automatically.
  - `PATCH /spoolman/inventory/spools/{id}/tag` links a tag (`tray_uuid` or `tag_uid`) with duplicate protection.
  - `POST /spoolman/inventory/slot-assignments` loads a spool into an AMS slot.
  - It does not expose SpoolmanDB search, `/tag/scan`, or filament and vendor creation.

SpoolBuddy is Bambuddy's ESP32 NFC and scale device.
It writes OpenTag3D on NTAG tags: NDEF MIME type `application/opentag3d`, 102-byte payload (see `backend/app/services/opentag3d.py` in the Bambuddy source).

## Phone NFC

The test phone lists NfcA, NfcV, Ndef, NdefFormatable, MifareClassic and MifareUltralight.
That is enough to read and write NTAG (OpenTag3D, OpenSpool), read ISO-15693 (OpenPrintTag) and read Bambu RFID UIDs.

## Integration choices

1. Spools and the catalog go through the Bambuddy passthrough, which uses the URL and authentication the app already has.
   When `spoolman_enabled` is false, the app uses `/inventory/*`, which has the same flat model.
2. SpoolmanDB search (manufacturer, type and color autofill) is an optional read-only call to Spoolman directly.
   The Spoolman URL comes from `GET /spoolman/status`.
   If that host is not reachable from the phone (localhost or a Docker name), use the Bambuddy host with the Spoolman port.
3. NFC tags are written as OpenTag3D on NTAG, then linked by UID with `PATCH .../tag`.
4. Scanning covers NFC read (OpenTag3D, OpenPrintTag, Bambu UID lookup), barcode or QR (article number), and manual SpoolmanDB search.
