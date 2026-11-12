# 38: Print entry points and MakerWorld source

The print flow of design 35 starts from two places: the Print button on a printer's overview when nothing is active, and the Print a file button on the queue.
It takes a file from the phone or a MakerWorld link that Bambuddy downloads.
The overview header shows the printer name with the printer type beside it.
Built; what is not built is listed in OI-53.

## Decisions

- **One flow, two entry points.** Both buttons open the same flow through `LocalOpenPrintFlow`, hosted once in the main activity by `ProvidePrintFlow`.
  The overview passes its printer id so the review is pre-set; the queue passes the printer when opened from one printer's queue, otherwise the review asks.
- **Pause and Cancel appear only while something is active.** `OverviewAction` decides: printing or paused shows Pause or Resume and Cancel print, error shows Cancel print only (it clears the error), every other state shows Print.
  Print is hidden while something is active.
- **Print is shown in every state with nothing active,** including offline; the review explains a busy, offline or not-ready printer.
- **Bambuddy downloads from MakerWorld, not the phone.** Bambuddy already has the Bambu Cloud sign-in and de-duplicates imports, so the app holds no MakerWorld client or credentials.
- **Cleanup deletes only what this flow created.** A model Bambuddy already had in its library is never deleted.

## Design

```text
 Overview, idle                          Overview, printing or paused
 +-------------------------------+       +-------------------------------+
 | X2D  Bambu Lab X2D     [Idle] |       | X2D  Bambu Lab X2D [Printing] |
 | No Active Print               |       | Benchy.gcode.3mf              |
 | [          Print         ]    |       | [ Pause ]   [ Cancel print ]  |
 +-------------------------------+       +-------------------------------+

 Print / Print a file -> Source step: [From this phone] [From a MakerWorld link]
   phone     -> system document picker -> upload (design 35)
   MakerWorld -> paste link -> look up -> choose a plate configuration -> Import
              -> design 35 review
```

MakerWorld path and its error paths:

```text
 link text not a MakerWorld model link -> field error, nothing sent
 GET makerworld/status:  server cannot download (not signed in, or sign-in expired) -> help text, stop
 POST makerworld/resolve -> error: its reason is shown, one tap is one attempt
 choose plate configuration -> POST makerworld/import (once) -> library file id
 -> plates read -> design 35 review
```

```text
 link text -> MakerWorldLink.parse -> PrintFlowViewModel.lookUpLink
   -> PrintFileRepository.makerWorldAccess / resolveMakerWorld / importMakerWorld
   -> PrintFileRepositoryImpl -> PrintFileApi (makerworld/status, resolve, import, thumbnail)
   -> MakerWorldModel, PrintFile -> PrintFlowState -> PrintLinkStep, review
 PrinterState -> OverviewAction.forState -> PrinterControlRow (Print or Pause and Cancel)
 header: Printer name and model -> PrinterTitle
```

## Behavior

- The app accepts a model link with or without a scheme and with an optional `#profileId-N` (`MakerWorldLink`); anything else is refused before a request.
- `makerworld/resolve` returns the design, its plate configurations and the library file ids already imported from it.
- `makerworld/import` downloads the 3MF into Bambuddy's library and answers the library file id.
  An already imported model returns the existing file and downloads nothing.
  The route behavior comes from Bambuddy's API as the code uses it; Bambuddy's source is not verified here.
- Import needs a Bambu Cloud sign-in on the Bambuddy server (`makerworld/status`).
  The flow checks this first and explains how to fix it instead of failing at import.
- After import the file is an ordinary library file, so the review, checks, staging and start of design 35 apply.
  Whether a MakerWorld file arrives sliced for the user's printer is not verified; the review blocks a file that is not sliced or is sliced for another model, and offers the slice dialog (design 39).
- The review shows the slicer's estimate read from the 3MF by Bambuddy: print time and grams, labeled as an estimate.
  A missing time or weight reads "not in the file", never a guess.
- Phone files are picked with the system document picker, so no storage permission is needed.
- The app sends the link text only to the user's own Bambuddy; Bambuddy contacts MakerWorld.
  MakerWorld's terms of use are not verified.

## Tests

- Unit tests cover `MakerWorldLink` (valid, no scheme, profile fragment, wrong host, junk), `OverviewAction` for every printer state, and the MakerWorld calls against a local test server.
- Not verified: a real MakerWorld import, upload and start on the user's server.

## Open work

OI-53 lists the unbuilt print-flow items.
