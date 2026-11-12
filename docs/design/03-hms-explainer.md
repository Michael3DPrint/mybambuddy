# 03: Plain-language HMS explanations

`HmsExplainer` turns a printer's HMS error into severity, affected area, the actions the printer offers in plain words, a code label and a link to the official Bambu wiki page.
It adds structure to the server's own description and does not ship an error dictionary.
Built; the card is on the printer's Overview tab and appears only when the printer reports an error.

## Decisions

- The description text comes from the server; a local HMS dictionary would be more to maintain and to get wrong.
- The wiki link (`https://wiki.bambulab.com/en/hms/error-code?e=<code>`) is built from a validated 16-digit hex code on a fixed host, never copied from the server, so a hostile response cannot inject a URL.
- Unknown action keys are hidden, never shown raw.
- The card and the Tools tab's Printer Status section both offer Clear errors, which sends `PrinterControlCommand.ClearHms` after one shared confirmation (design 43).

## Design

```text
Collapsed                                   Expanded (tap)
+--------------------------------------+    +--------------------------------------+
| Printer: 1 error                  v  |    | Printer: 1 error                  ^  |
| Error . AMS: <server description>    |    | Error . AMS                          |
| Tap for details or to clear.         |    | <server description>                 |
+--------------------------------------+    | The printer offers: Resume the print |
                                            | Code 0500-0400-0001-0006             |
                                            | [Bambu wiki link]   [Clear errors]   |
                                            +--------------------------------------+
```

```text
PrinterStatusDto.hms_errors -> HmsErrorDto (HmsErrorDtoAdapter) -> mapToHmsStatus -> HmsStatus
  -> HmsExplainer.explain -> HmsAdvice -> HmsAdviceCard (PrinterDetailScreen)
Learn more: LocalUriHandler opens HmsAdvice.learnMoreUrl, which HmsExplainer.explain builds with wikiUrl.
```

## Behavior

- Severity: "1" is `HmsSeverity.ERROR`, "3" is `NOTICE`, anything else is `WARNING`.
  `HmsSummary` counts levels for the card title and colors.
- Area comes from the module number (main board, motion controller, AMS, toolhead, camera); an unknown module gives "Printer".
- The link and the dashed code label appear only when `fullCode` is exactly 16 hex digits; otherwise the card shows the short code or "unknown code".
- The card shows at most five errors and "+ N more".

## Tests

HmsExplainerTest covers severity, area, options, unknown keys and URL validation, including rejected shapes.
HmsAdviceCardTest covers the collapsed line and labels.
How the card looks is only visible on a device.
