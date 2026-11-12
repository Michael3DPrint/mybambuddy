# 22: Back navigation

The system Back button goes to the previous page instead of closing the app.
`BackPolicy.onBack(route, tab)` is a pure rule that `MainActivity` wires to a Compose `BackHandler`.
Built.

## Decisions

- One level only: the nested pages (printer detail and alerts) open from the main page, so Back returns there.
  A full back stack was rejected because no page is reachable from two parents.
- From another tab Back selects the Printers tab, and the next press leaves the app.
  Remembering the previous tab was rejected because Back would loop between tabs.
- Setup has nothing before it, so Back leaves the app.
- An unknown route leaves the app, so Back can never trap the user.
- Dialogs and sheets handle Back themselves before the activity handler runs.

## Design

```text
Back pressed
  |-- route detail or alerts          -> BackStep.GO_TO_MAIN        (selected tab is kept)
  |-- route main, tab not Printers    -> BackStep.SELECT_PRINTERS
  |-- route main on Printers, setup,
  |   or any other route              -> BackStep.LEAVE_APP         (handler disabled, system closes the app)
```

```text
system Back -> BackHandler (MainActivity) -> BackPolicy.onBack(screen, selectedTab) -> BackStep
  GO_TO_MAIN -> currentScreen = Routes.MAIN
  SELECT_PRINTERS -> selectedTab = AppTab.PRINTERS
```

`Routes` holds the route names used by both `MainActivity` and the policy.
On a wide layout `MainActivity` passes main instead of detail, because detail is a pane beside the list.
A tapped notification opens the detail route; Back from it goes to main like a tap in the list.

## Tests

`BackPolicyTest` covers each rule, the unknown route, and a walk from detail to exit.
Dialog and sheet Back handling is not covered by unit tests.
