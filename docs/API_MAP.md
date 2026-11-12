# API map

This reference lists every Bambuddy endpoint the app declares in `data/api`, with the reply class, how far the reply or request shape was compared with the server, and how failures reach the screen.
The checks were made against Bambuddy 1.3.0.
A build check (`ApiMapTest`) fails when an endpoint is declared in the app and missing here, or listed here and gone from the app.

## What the Checked column means

- **Reply compared with the real server (GET):** the response was fetched read-only and compared field by field with the app's reply class.
  Server fields the app does not read are not defects.
- **Request fields compared with the server schema:** the body the app sends was compared with the server's request schema, read from its source.
  No field the server does not know was found in the queue, slicing, MakerWorld, maintenance or inventory bodies.
- **Picture route; bytes only, read from source:** the route returns image bytes, so there is no shape to compare.
- **Not compared with the server:** the shape was read from the server source, or not checked.
- Not covered anywhere: commands that act on the printer (never sent), behavior when the printer or Spoolman is down, and HMS error bodies.

## Endpoints

| Area | Route | App function | Reply | Checked |
|---|---|---|---|---|
| AMS and slots | `GET printers/{id}/status` | getPrinterAmsStatus | PrinterAmsStatusDto | reply compared with the real server (GET) |
| AMS and slots | `GET spoolman/inventory/slot-assignments/all` | getSpoolmanSlotAssignments | List<SlotAssignmentDto> | reply compared with the real server (GET) |
| AMS and slots | `GET inventory/assignments` | getInventoryAssignments | List<SlotAssignmentDto> | reply compared with the real server (GET) |
| AMS and slots | `POST spoolman/inventory/slot-assignments` | assignSpoolmanSlot | Unit | request fields compared with the server schema |
| AMS and slots | `DELETE spoolman/inventory/slot-assignments/{spoolId}` | unassignSpoolmanSlot | Unit | not compared with the server |
| AMS and slots | `POST inventory/assignments` | assignInventorySlot | ResponseBody | request fields compared with the server schema |
| AMS and slots | `DELETE inventory/assignments/{printerId}/{amsId}/{trayId}` | unassignInventorySlot | Unit | not compared with the server |
| AMS and slots | `POST printers/{id}/drying/start` | startDrying | Unit | not compared with the server |
| AMS and slots | `POST printers/{id}/drying/stop` | stopDrying | ? | not compared with the server |
| Camera | `POST printers/camera/stream-token` | getCameraStreamToken | ? | not compared with the server |
| Diagnostics | `GET printers/{id}/diagnostic` | getDiagnostic | ? | fetched from the real server; shape not compared |
| History and archives | `GET archives/` | getArchives | ArchivesResponseDto | reply compared with the real server (GET) |
| History and archives | `GET archives/{id}/thumbnail` | thumbnail | ? | picture route; bytes only, read from source |
| Inventory | `GET spoolman/status` | getSpoolmanStatus | SpoolmanStatusDto | reply compared with the real server (GET) |
| Inventory | `GET spoolman/inventory/spools` | getSpoolmanSpools | List<InventorySpoolDto> | reply compared with the real server (GET) |
| Inventory | `POST spoolman/inventory/spools` | createSpoolmanSpool | InventorySpoolDto | request fields compared with the server schema |
| Inventory | `GET inventory/spools` | getInventorySpools | List<InventorySpoolDto> | reply compared with the real server (GET) |
| Inventory | `POST inventory/spools` | createInventorySpool | InventorySpoolDto | request fields compared with the server schema |
| Inventory | `PATCH spoolman/inventory/spools/{id}/tag` | linkSpoolmanTag | Unit | request fields compared with the server schema |
| Inventory | `PATCH spoolman/inventory/spools/{id}/weight` | setSpoolmanWeight | Unit | request fields compared with the server schema |
| Inventory | `PATCH inventory/spools/{id}/link-tag` | linkInventoryTag | ? | request fields compared with the server schema |
| Maintenance | `GET maintenance/overview` | getOverview | List<PrinterMaintenanceDto> | reply compared with the real server (GET) |
| Maintenance | `POST maintenance/items/{id}/perform` | perform | ? | request fields compared with the server schema |
| Print from the phone | `POST library/files/` | upload | LibraryUploadDto | not compared with the server |
| Print from the phone | `GET library/files/{id}/plates` | plates | PlatesDto | reply compared with the real server (GET) |
| Print from the phone | `DELETE library/files/{id}` | deleteFile | Unit | not compared with the server |
| Print from the phone | `POST queue/` | createQueueItem | QueueItemDto | request fields compared with the server schema |
| Print from the phone | `POST queue/{id}/start` | start | Unit | not compared with the server |
| Print from the phone | `GET makerworld/status` | makerWorldStatus | MakerWorldStatusDto | reply compared with the real server (GET) |
| Print from the phone | `POST makerworld/resolve` | resolve | MakerWorldResolvedDto | request fields compared with the server schema |
| Print from the phone | `GET makerworld/thumbnail` | makerWorldThumbnail | ResponseBody | picture route; bytes only, read from source |
| Print from the phone | `POST makerworld/import` | import | MakerWorldImportedDto | request fields compared with the server schema |
| Print from the phone | `GET library/files/{id}/filament-requirements` | requirements | RequirementsDto | reply compared with the real server (GET) |
| Print from the phone | `GET library/files/{id}/download` | download | ResponseBody | not compared with the server (read from the server source) |
| Print from the phone | `GET library/files/{id}/plate-thumbnail/{plate}` | plateThumbnail | ResponseBody | picture route; bytes only, read from source |
| Print from the phone | `GET archives/{id}/plates` | archivePlates | PlatesDto | reply compared with the real server (GET) |
| Print from the phone | `GET archives/{id}/plate-thumbnail/{plate}` | archivePlateThumbnail | ResponseBody | picture route; bytes only, read from source |
| Print from the phone | `GET archives/{id}/thumbnail` | archiveThumbnail | ResponseBody | picture route; bytes only, read from source |
| Print from the phone | `GET library/files/{id}/thumbnail` | thumbnail | ResponseBody | picture route; bytes only, read from source |
| Print from the phone | `POST library/files/{id}/slice` | slice | SliceStartedDto | request fields compared with the server schema |
| Print from the phone | `GET slice-jobs/{id}` | sliceJob | ? | not compared with the server |
| Printers | `GET printers/` | getPrinters | PrintersResponse | reply compared with the real server (GET) |
| Printers | `GET printers/{id}` | getPrinter | PrinterDto | reply compared with the real server (GET) |
| Printers | `GET printers/{id}/status` | getPrinterStatus | ? | reply compared with the real server (GET) |
| Printer control | `POST printers/{id}/print/pause` | pausePrint | Unit | not compared with the server |
| Printer control | `POST printers/{id}/print/resume` | resumePrint | Unit | not compared with the server |
| Printer control | `POST printers/{id}/print/stop` | cancelPrint | Unit | not compared with the server |
| Printer control | `POST printers/{id}/refresh-status` | refreshStatus | Unit | not compared with the server |
| Printer control | `POST printers/{id}/hms/clear` | clearHms | Unit | not compared with the server |
| Printer control | `POST printers/{id}/clear-plate` | clearPlate | Unit | not compared with the server |
| Printer control | `POST printers/{id}/print-speed` | setPrintSpeed | Unit | not compared with the server |
| Printer control | `POST printers/{id}/chamber-light` | setChamberLight | Unit | not compared with the server |
| Printer control | `POST printers/{id}/ams/load` | amsLoad | Unit | not compared with the server |
| Printer control | `POST printers/{id}/ams/unload` | amsUnload | ? | not compared with the server |
| Queue | `GET queue/` | getQueue | List<QueueItemDto> | reply compared with the real server (GET) |
| Queue | `POST queue/reorder` | reorder | Unit | request fields compared with the server schema |
| Queue | `PATCH queue/{id}` | update | Unit | request fields compared with the server schema |
| Queue | `POST queue/{id}/start` | start | Unit | not compared with the server |
| Queue | `POST queue/{id}/cancel` | cancel | Unit | not compared with the server |
| Queue | `DELETE queue/{id}` | remove | Unit | not compared with the server |
| Queue | `GET library/files/{id}/thumbnail` | libraryThumbnail | ResponseBody | picture route; bytes only, read from source |
| Queue | `GET archives/{id}/thumbnail` | archiveThumbnail | ResponseBody | picture route; bytes only, read from source |
| Queue | `POST queue/printer/{printerId}/resume` | resume | ? | not compared with the server |
| Slicing settings | `GET settings/` | getSettings | SlicerSettingsDto | reply compared with the real server (GET) |
| Slicing settings | `GET slicer/presets` | getPresets | ? | reply compared with the real server (GET) |
| Inventory | `GET spoolman/inventory/spools/{id}/usage` | getSpoolmanUsage | List<SpoolUsageDto> | not compared with the server |
| Inventory | `GET inventory/spools/{id}/usage` | getInventoryUsage | ? | not compared with the server |
| Inventory | `GET external/filament/search` | searchFilaments | ? | not compared with the server |
| Statistics | `GET archives/stats` | getStats | ? | fetched from the real server; shape not compared |
| System | `GET health` | getHealth | HealthResponse | real server answers 404 for this path (unused, see findings) |
| System | `GET system/info` | getSystemInfo | ? | reply compared with the real server (GET) |

## How errors reach the app

The server answers failures with a status code and a body whose `detail` has one of four shapes.
The shapes are text, a list of field messages (422), an object with a message (Spoolman refusals), and an object with a machine code.
The machine code is a filament refusal (`insufficient_filament` or `unmatched_filament`) with the slots that are short or the filaments with no tray.
`ServerDetail` turns each into one readable sentence and also reads the code.

| Status | Kind in the app | Wording |
|---|---|---|
| 400, 409, 410, 413, 422 | refused | the server's reason when it gave one, else a generic line |
| 401 | not signed in | invalid key text with a Retry and Open Settings |
| 403 | forbidden | the key lacks permission |
| 404 | not found | the route or item is missing |
| 503 | unavailable | a service is busy or down |
| 500 to 599 (including 502, 504 and 507) | server error | a server error line |
| no answer | unreachable | the connection text with Retry and Open Settings |

Lists and pages (printers, queue, history, previous prints, AMS, inventory, maintenance) show an error state with the reason and Retry, or the saved copy with a banner when the server cannot be reached.
Printer commands and queue actions are confirmed first, sent once, and the result or reason appears as a message that stays long enough to read.
The print flow shows the reason on its current step, and the slice dialog shows the server's reason and stays open.
The queue reads a filament refusal body and offers to print anyway after asking.
The error screens for inventory writes, maintenance and AMS were not re-read against this table.

## Known gaps

- `GET health` answers 404 on the tested server.
  No screen uses it (`getHealth` is on the dead-code allowlist); fix or remove it.
- The server reports whether the printer has an SD card (`sdcard`, `store_to_sdcard`), the stage name, door state and firmware version; the app does not read them.
- Not done: reply shapes for the routes marked "not re-checked".
- Not done: the error text of every write route as the user sees it.
- Not done: a recorded fixture per route (real replies hold the printer's serial and address).
- Not done: behavior with the printer or Spoolman unavailable.
