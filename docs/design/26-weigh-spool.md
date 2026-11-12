# 26: Weigh a spool and update its record

A spool's detail sheet has a "Weigh and update" action: the user types the total weight from a scale, and Bambuddy updates the Spoolman record.
The server subtracts the empty-spool weight itself, so the app sends the gross weight only (from the code's own comments; not verified against Bambuddy's source).
It works in Spoolman mode only; in the built-in inventory mode the button is hidden and the repository refuses.
Built; it changes the inventory record and sends nothing to the printer.

## Decisions

- `SpoolWeighRules` accepts a finite number from 0 to 100000 g, takes a comma as the decimal mark, and warns without blocking when the reading is more than 500 g above the label weight.
  The 100000 g limit is the one the code names as the server's; not verified against Bambuddy's source.
- `SpoolWeighViewModel` is separate from `FilamentViewModel`, which is at its function limit.
- One attempt, no retry.
- The tare the server uses may differ from the real empty spool, so the record is only as good as that number; the dialog says so.

## Design

```text
 Spool detail sheet                     Weigh dialog
 | Bambu Lab PLA Basic Blue   |         | Weigh this spool                       |
 | 630 g left of 1000 g       |         | Put the spool on a scale and enter the |
 | [ Weigh and update... ]    |         | total weight, spool included.          |
   (Spoolman mode only)                 | [ 812 ] g     (error or warning text)  |
                                        | Bambuddy subtracts the empty spool's   |
                                        | weight itself. This only changes the   |
                                        | record, and it is only as exact as     |
                                        | that empty-spool weight.               |
                                        |                    [Cancel] [Update]   |
```

```text
 FilamentScreen -> SpoolWeighViewModel.checkAvailable -> SpoolRepository.canWeigh (Spoolman active?) -> show or hide button
 dialog text -> SpoolWeighRules.parse(text, label weight)
      invalid -> error, Update off ; valid with warning -> warning text, Update on
 Update -> SpoolWeighViewModel.weigh(spool, grams)
      offline -> message, nothing sent
      -> SpoolRepository.updateWeight (CachingSpoolRepository passes it through to SpoolRepositoryImpl)
         built-in mode -> ApiResult.Error (UNSUPPORTED), nothing sent
         -> InventoryApi.setSpoolmanWeight: PATCH spoolman/inventory/spools/{id}/weight, body SpoolWeightRequest {"weight_grams": 812.0}
      success -> message + updated event -> FilamentScreen calls FilamentViewModel.refresh (list reloads)
      error   -> the server's reason in a message
```

The server's reply body is not read.

## Tests

Unit tests cover the rule, the repository (request, built-in mode refuses without a request) and the ViewModel (offline refusal, success event, server reason).
