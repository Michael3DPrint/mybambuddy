# 34: Accessibility

The app gives screen readers spoken descriptions instead of symbols, marks headings and live regions, switches to larger-text layouts from a font scale of 1.3, and checks colour contrast in the build.
Pure rules (`SpokenText`, `SlotSpeech`, `WidgetSpeech`, `LargeFont`) hold the decisions, so they are tested without a device.
Built; how it sounds and looks on a device is not verified by the build.

## Decisions

- Large-font layouts apply only from a font scale of `LargeFont.FROM_SCALE` (1.3).
  Below it, sizes and layouts are unchanged.
- State is never shown by colour alone: low stock also has a warning icon and the word "low" in speech.
- Live regions are polite, so announcements do not interrupt the reader.
- Hard-coded English is not treated as an accessibility defect while the app is English only.

## Design

```text
 AmsTray + AmsUnit + Spool -> SlotFillRule -> SlotSpeech.describe -> semantics of the slot tile
 any display string -> SpokenText.of -> Spoken / Heading / spokenAs -> contentDescription of that Text
 WidgetRow -> WidgetSpeech.row -> content description of the widget row
 LocalDensity.fontScale -> LargeFont.isLarge -> layout choice
 theme and widget colour resources -> ContrastRulesTest
```

```text
normal                                   large font (scale 1.3 or more)
AMS 1  [1][2][3][4]                      AMS 1
                                         [1][2][3][4]      (material names may use two lines)
[Overview][Queue][Tools]                 <[Overview][Queue][Tools]>  scrolls
```

## Behavior

**Spoken descriptions**

- `SpokenText.of` turns symbols into words: "~" to "about", "g" after a number to "grams", "%" to "percent", "°C" to "degrees Celsius", a lone dash to "unknown", the warning sign to "warning", and the middle dot and the em dash to a pause.
  `Spoken` is a `Text` that carries this description, and screens use it where a line is built from those symbols.
- Each slot tile is one button with one description from `SlotSpeech.describe`, for example "AMS 1, slot 3, PLA Basic, about 750 grams left", with ", low" when low, "amount unknown" without an estimate, and "empty" for an empty slot.
  Its click action is labelled "Open options for this slot".
  The swatch, texts and bar inside are not read separately.
- `SwitchRow` makes the whole row one toggleable control with the label attached and the Switch role.
- Widget: each row has one spoken summary from `WidgetSpeech.row`, for example "X2D, printing, 62 percent, 1 hour 12 minutes left".
  The inner texts and progress bar are hidden from the reader, and the refresh button is 48 dp.

**Headings and disclosure**

- `Heading` is a `Text` marked as a heading.
  Screen titles, dialog titles, card and section titles, and sheet section labels use it.
- Expandable headers (Statistics, status sections, the HMS card) announce "Expanded" or "Collapsed"; the Statistics and status section headers are at least 48 dp tall.

**Live regions and messages**

- `politeLive` (or an equivalent semantic) announces results and errors when they appear: the connection test result, the offline banner, the printer list's stale-data notice, error notices and the print and slice messages.
- `showMessage` shows every snackbar for the long duration with a dismiss button, so command results stay long enough to read.

**Large fonts**

- The AMS unit name moves above its tiles instead of a fixed 52 dp column, and tile material names may use two lines.
- The bottom bar and the rail show labels only on the selected item.
- Tab rows scroll (`AppTabRow`), names that are one line at normal size use two (`nameMaxLines`), and confirmation buttons wrap onto a second line.

**Contrast**

- `ContrastRulesTest` computes the theme, status chip, status icon and widget colour pairs in light and dark.
  It fails below 4.5:1 for text and 3:1 for boundaries such as the widget row outline and status icons.
- The Compose UI tests in the instrumented test source enable Compose's accessibility checks; they need a device to run.

## Tests

- Unit tests cover `SpokenText`, `SlotSpeech`, `WidgetSpeech`, `LargeFont` and the contrast pairs.
- TalkBack output, the largest font setting, reduced motion and the Accessibility Scanner need a pass on a device; they are not verified by the build.
