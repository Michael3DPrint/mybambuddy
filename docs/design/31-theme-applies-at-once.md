# 31: Theme applies at once

Choosing the theme mode (System Default, Light, Dark) or the wallpaper colors on the Settings App tab is saved and applied immediately, without the Save Settings button.
The button covers only the connection and polling settings: server address, API key and polling interval.
Built.

## Design

```text
 Settings, App tab                                   Settings, Connection tab
  Polling interval                                    Server address, API key, Test
  App Theme Mode  [System Default] [Light] [Dark]     (both tabs)  [ Save Settings ]
  Use wallpaper colours                    [ o ]      Save covers URL, key and polling only
  theme chip and switch apply at once
```

```text
 theme chip -> SettingsViewModel.onThemeModeChanged(mode)
     -> uiState.themeMode updated, and SettingsRepository.saveThemeMode(mode) in the same step
 wallpaper switch -> SettingsViewModel.onDynamicColorChanged(enabled) -> SettingsRepository.saveDynamicColor
 SettingsRepository (DataStore) -> themeMode and dynamicColor flows -> MainActivity -> MyBambuddyTheme recolors
```

## Behavior

- The wallpaper switch appears only on Android 12 (API 31) or newer.
- `SettingsViewModel.saveSettings` stores the server address, API key and polling interval and does not touch the theme.

## Tests

Unit tests cover that choosing a mode or wallpaper colors stores it without Save.
How light and dark look is not covered by tests.
