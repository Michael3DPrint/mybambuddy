# 07: UI refresh, app icon, light and dark mode

Screens read colour, spacing and shape from the theme.
The app follows the system light or dark setting by default; Settings can force either.
Built; how it looks on a device is not checked by the build.

## Decisions

- Printer states use semantic `StatusColors` (printing, paused, finished, error, offline, unknown), each a container and content pair defined for light and for dark.
- Brand colours come from a fixed light and dark scheme in `MyBambuddyTheme`; dynamic (wallpaper) colour is off by default and a switch in Settings turns it on for Android 12 and later.
- `ThemeMode.SYSTEM` is the default and follows `isSystemInDarkTheme()`.
- One `StateChip` renders every state chip, so no screen keeps its own.
- Spacing and shape values come from `Dimens` and the app shapes; the Material 3 type scale is used.
- The app is edge to edge.
- The launcher icon is an adaptive icon with a monochrome layer for themed icons, delivered as vector drawables (background, foreground, monochrome).

## Design

```text
SettingsRepository.themeMode, dynamicColor
        v
MainActivity ──► MyBambuddyTheme(themeMode, dynamicColor)
                    ├─ SYSTEM ► isSystemInDarkTheme(); LIGHT/DARK ► forced
                    ├─ dynamicColor and Android 12+ ► dynamic scheme, else brand light/dark scheme
                    └─ LocalStatusColors provides StatusColors.Light or StatusColors.Dark
        v
screens read MaterialTheme.colorScheme and MaterialTheme.statusColors
```

## Tests

- `StatusColorsTest` checks that every state pair and the brand pairs reach WCAG AA contrast (4.5) in light and dark.
- `ContrastRulesTest` checks the theme, chip, widget and icon colour pairs (design 34).
- Not checked by the build: that no screen uses a literal colour, and the look of each screen in both modes.
