# 40: Slice page, options and scaling

The slice page is a full page inside the print flow with four tabs: Plate, Filament, Process and Options.
The Options tab holds three slicer switches and a scale from 10 to 400 percent.
Bambuddy's slice request has no scale field, so a scaled slice first makes a scaled copy of the 3MF on the phone.
Built; no scaled slice has been printed on a real printer.

## Decisions

- Four tabs, so each screen holds one concern.
  A tab that still needs something says "(needed)" in words, and Options says what changed, for example "Options (scale 150 %, 1 changed)".
- The page state lives in `SliceFlow`, not in the screen, so Back to the review and rotation keep every choice.
- Slice is disabled with a reason that names the tab to open (`SliceState.blocker`).
- All options are off by default and each is described in words.
- Scaling is labeled experimental and never changes the original file: the copy is uploaded as a new library file and deleted after the slice.
- One scale applies to every object on the plate; there is no per-object or per-axis scale.

## Design

```text
 < Slice for X2D
 [ Plate | Filament | Process | Options ]
 Plate:    preview of the model, "Which build plate is on the printer?" (five choices, required)
 Filament: per filament the AMS tiles and its preset, with the recommended preset named
 Process:  printer preset, process preset dropdown, a note about the designer's tweaks
 Options:  Scale (percent field, chips 50 75 100 125 150 200), rotate, arrange, designer's settings
 ------------------------------------------------------------------
 reason Slice is off / summary line / [ Slice ] or [ Stop waiting ]
```

```text
 Slice
   scale = 100: startSlice(source file) -> poll -> review
   scale <> 100: "Making a copy of the file at N %"
        PrintFileRepository.scaledCopy: GET library/files/{id}/download (streamed to a work file)
          -> ThreeMfScaler.scale -> POST library/files/ (upload of "<name>-scaled-N.3mf") -> new library id
        copy refused or upload fails -> reason shown, nothing sliced
        -> startSlice(copy) -> poll -> the copy is deleted when the slice is done, refused or failed
```

```text
 SlicePage -> SliceFlow.choose / start -> SliceState.request -> SliceRequestBuilder -> SliceRequest
   -> PrintFileRepositoryImpl.startSlice -> SliceBody -> POST library/files/{id}/slice
 SliceFlow -> SliceMemoryStore: bed, process per model, filament per type and the three switches are saved once Bambuddy accepts a slice
 The scale is not remembered.
```

## Behavior

- Options sent with the request: `auto_orient`, `auto_arrange`, `use_embedded_settings` (request fields compared with Bambuddy's schema, see `docs/API_MAP.md`).
  The designer's-settings switch is offered only when the file's target model equals the printer's model (`SliceState.embeddedAvailable`); otherwise it is forced off and the page says why.
  When it is on, the process and filament presets are still sent but not used.
- Scaling is meant for an unsliced 3MF project, which is the only kind the slice page opens for; the scaler itself does not check that.
  `ThreeMfScaler` multiplies the linear part of every build item's transform by the factor and moves the item so the center of its footprint and its lowest point stay where they were.
  Without that move the slicer cuts off the part that grew below the bed.
  Only the build list changes; every other entry of the archive is copied unchanged, and the mesh is read as a stream.
- The scaler refuses with a reason when the file is not a readable 3MF, has no model description, has nothing on the plate, is over 120 MB, or its uncompressed size is over 600 MB.
- Several objects each scale about their own center, so they can overlap; the page tells the user to turn on arrange, and the app does not turn it on.
- The app does not know the bed size, so an oversized model fails in the slicer with Bambuddy's message.
- A file that stores placement outside the build list may behave differently (not verified).
- The scale field accepts a whole number from 10 to 400, with an optional percent sign (`ScaleRule`).
  An invalid entry disables Slice.
- Backing out of the review deletes the files this flow created, unless a job was queued from them: the sliced file and the source when it was imported or uploaded here.
- The Options tab, the summary line and the progress text name the scale when it is not 100.

## Tests

Unit tests cover `ScaleRule`, `SliceMemoryRules`, the scaled copy in `PrintFileRepositoryImpl` and the flow in the view-model tests.
`ThreeMfScalerTest` covers the kept footprint center and lowest point, a rotated component part, scaling down, several parts, a missing transform, refused files and factors out of range.
Only a device shows how the page looks and scrolls, TalkBack order and focus, and the largest font sizes.
Not verified: files near the 120 MB limit on a phone.
