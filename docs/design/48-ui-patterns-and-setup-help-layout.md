# 48: UI patterns, and the Setup help and Settings layout

This is the standing reference for the app's layout patterns (P1 to P9), plus the layout of the Setup help screen and of Settings.
New screens use these patterns, and a screen that departs from one says why in its own design.
Setup help has three tabs (Guide, Fix a problem, This phone) and Settings has two (Connection, App) with Save pinned at the bottom.
Built; how it looks and sounds on a phone (TalkBack, Accessibility Scanner, large font) is not verified here.

## Decisions

- Material 3 components are used as they are; a custom component exists only where a pattern below needs one.
- A tappable summary card has no chevron (P4b), because the card itself shows it is an item.
  A plain row that opens another page has one (P4).
- Troubleshooting is a tab of its own, so it is always visible in the tab row.
- Find Bambuddy is a setup action, so it lives in Setup help, with a link row in Settings that opens that tab.
- Save sits outside the tab content so it is in reach on both tabs.

## Design

| # | Pattern | Use it when | Rules | Used in |
|---|---|---|---|---|
| P1 | Screen | any full page | top bar with a heading title and a Back arrow with a spoken name when it is not a tab; content scrolls; the primary action is at the bottom or in the bar, never mid-list | all screens |
| P2 | Tabs (`AppTabRow`) | one screen has two to four peer parts | text labels only; the selected tab is marked by shape as well as color; the row scrolls at large font; a tab that still needs something says so in words | slice page, printer tabs, queue tabs, queue job page, Filament, Settings, Setup help |
| P3 | Expandable section (`StatusSection`) | a status with details behind it | one 48 dp header button with the title, a status icon plus word, and Expanded or Collapsed state; body hidden until opened | Tools tab |
| P4 | Navigation row (`NavRow`) | a row that opens another screen or page | at least 56 dp; leading icon, title, one line of supporting text, trailing chevron; one button with role Button and a spoken "Open ..." label; a plain text row without a chevron is never tappable | Settings, Setup help |
| P4b | Tappable summary card | a card that summarizes one item and opens its details | the whole card is one button with role and merged description; no chevron; at least 48 dp; its title is not a heading | printer, spool, queue and history cards |
| P5 | Section group | several related rows on one tab | a heading, then rows, about five at most | Settings tabs |
| P6 | Callout | one thing the user must notice | one per screen part, tonal card with icon and words; never decoration, never stacked | community notice, errors |
| P7 | Help link (`HelpButton`) | an error, a failed check or a setup step the user may be stuck on | a text button "Help" with a question-mark icon beside the error's own buttons; it opens the topic for that error (design 47) | `ErrorNotice`, Setup, Settings connection test, slicing, print source step, history |
| P8 | Confirm, then act (`ConfirmDialog`) | anything that changes the printer or the server | a dialog that shows the numbers; one attempt, no automatic retry | queue, drying, clear errors, weighing, loading a spool, maintenance |
| P9 | Accessibility baseline | every pattern | headings marked; targets at least 48 dp with 8 dp between; role and state spoken; no meaning by color alone; text scales and the layout adapts from 1.3 times font; decorative icons hidden, informative ones described; results announced politely | designs 15 and 34 |

`ErrorNotice` shows a failed load (heading, message, Retry, Help) and is used by Filament, AMS, the printer filament section, slicing, statistics, queue and maintenance.

```text
 Settings (Connection tab)                 Setup help                       A topic page
 +----------------------------+            +---------------------------+    +-----------------------+
 | Settings                   |            | < Setup help              |    | < Install Bambuddy... |
 | [Connection] [App]         |            | [Guide][Fix a problem][This phone]
 | (?) Setup help           > |  -- tap -> |  How is the server        |    | numbered steps        |
 | (s) Find Bambuddy        > |            |  installed?               |    | Sources               |
 | Server address ...         |            |  ( Docker | Home Assistant OS )  +-----------------------+
 | API key ...                |            |  1 Install Bambuddy     > |
 | [Test connection]          |            |  2 First run            > |
 | [Save settings]  (pinned)  |            |  [Send this guide to my computer]
 +----------------------------+            +---------------------------+

 Fix a problem tab:                          This phone tab:
 | (?) Cannot reach the server       > |     | Wi-Fi, VPN, Tailscale checks      |
 | (?) The server rejected the key   > |     | [Find Bambuddy on this network]   |
```

```text
 Settings -> NavRow "Setup help" -> LocalOpenHelpTab(GUIDE) -> ProvideHelp -> HelpIndexScreen (Guide)
 Settings -> NavRow "Find Bambuddy" -> LocalOpenHelpTab(PHONE) -> This phone tab -> result -> onUseAddress
     -> the Settings address field is filled; the user presses Save settings
 error or failed check -> HelpButton -> ProvideHelp opens the topic; Back returns to the tab chosen by HelpTabs.forTopic
 HelpButton with no topic for the error -> the Fix a problem tab
 no result from Find Bambuddy -> the words say why and the manual address field is one tap away
```

The help library, the error-to-topic mapping and the checks are shared with the welcome screen, which uses the same checks and the same Find Bambuddy button.

## Behavior

- Setup help, Guide tab: a heading "How is the server installed?" and one grouped choice (a segmented control: Docker or Home Assistant OS apps, read as one radio group).
  Below it, the guide as numbered `NavRow`s with a title and one line each, then the Send this guide to my computer button.

- Setup help, Fix a problem tab: each troubleshooting page is a `NavRow` with a help icon.
  `HelpTabs.forTopic` returns this tab for troubleshooting pages (ids starting `T_`) and Guide for every other page, so Back from a topic lands where the user came from.
- Setup help, This phone tab: the connection checks as rows with icon and word, and the Find Bambuddy button with its results.
- Settings, Connection tab: the Setup help row, the Find Bambuddy row, the server address and API key (with show), Test connection with its result, and the Tailscale note.
- Settings, App tab: an Alerts `NavRow`, polling, and appearance (theme, wallpaper colors).
- Save settings sits in `SaveBar`, below the scrolling tab content; the saved confirmation shows above the button.

## Tests

Unit tests cover the `NavRow` spoken label and `HelpTabs.forTopic`.
`StandardsEnforcementTest` fails the build when a tab row is built anywhere but `AppTabRow`, or when a screen reads an error's title or message without `ErrorNotice` or a `HelpButton`.
Not enforced and not checked screen by screen: that every screen follows P1, P4b, P5, P6 and P9.
Also not enforced: that titles are headings, touch-target sizes, large-font behavior and the Compose accessibility checks.
These need TalkBack, Accessibility Scanner and large-font passes on a phone.
