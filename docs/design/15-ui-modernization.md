# 15: UI tokens and shared components

Screens take spacing, shape, type, loading and empty states from one token layer in `ui/theme` and a few components in `ui/common`, so the same thing looks the same everywhere.
Built; the Printers and Filament lists do not yet fade between loading, error and content (OI-23).

## Decisions

- Color carries elevation: prefer the `surfaceContainer*` roles of the theme over shadows and `copy(alpha = ...)`.
- Weights live in the type scale, so call sites do not pass `fontWeight`.
- Tabular figures (`tnum`) apply only to the two styles that carry live numbers, `titleSmall` and `labelSmall`; applying them to prose would look mechanical.
- A skeleton is used where the shape of the arriving content is known; a spinner stays for buttons and the camera photo.
- Motion explains a change and never decorates; the skeleton pulse stands still when the system animation scale is off.
- Dynamic (wallpaper) color is a switch in the Appearance settings and is off by default, so the teal brand holds.
  The fixed status palette in `LocalStatusColors` does not follow the wallpaper.
- Rejected: a custom font (an asset and a licence for little gain), expressive Material 3 shapes (this is a dense dashboard), a design-system module (a few shared files in `ui/common` are enough).

## Design

Spacing and sizes are in `Dimens`; every spacing value is a multiple of 4 dp and `DimensTest` keeps it so.

```text
 Dimens.Space.xs 4   s 8   m 12   l 16   xl 24   xxl 32
 Dimens.IconSize.s 16  m 24  l 48  xl 64
 Dimens.touchTarget 48   Dimens.cameraCard 160   Dimens.progressBar 8
```

Shapes are `AppShapes`, set on `MaterialTheme.shapes` by `MyBambuddyTheme`.

```text
 extraSmall 4   small 8   medium 12 (cards, banners)   large 16 (hero cards)   extraLarge 28 (dialogs, sheets)
```

Type is `Typography` in `Type.kt`: the Material 3 scale with these weights.

```text
 headlineSmall  SemiBold        labelLarge   SemiBold
 titleLarge     Bold            labelMedium  Medium
 titleMedium    SemiBold        labelSmall   SemiBold, tabular
 titleSmall     SemiBold, tabular
 bodyLarge      Normal, 16 sp / 24 sp
```

Shared components in `ui/common`:

- `StateChip`: one chip for printer and history states; its colors animate over 200 ms.
- `EmptyState`: icon, title, one line of guidance, optional action; `fillScreen` switches between a whole-screen and a compact form.
- `SkeletonBlock` and `ListSkeleton`: pulsing placeholders that stand still when the system animation scale is off.
- `OfflineBanner`: the one strip for offline state, see design 16.

Theme data flow:

```text
 Appearance section --> SettingsViewModel --> SettingsRepository (themeMode, dynamicColor)
 SettingsRepository flows --> MainActivity --> MyBambuddyTheme
                                                                  |- colorScheme (dynamic only on Android 12+ when switched on)
                                                                  |- typography, shapes
                                                                  '- LocalStatusColors (fixed)
```

## Behavior

- List screens show `ListSkeleton` while loading; the camera snapshot fetch, sheet spinners and "testing connection" buttons keep spinners.
- Motion in use: chip color change, print progress bar, offline banner slide and fade (200 ms in, 150 ms out), pulsing skeletons, `animateItem()` on list rows.

## Tests

- `DimensTest` checks the 4 dp grid; `StatusColorsTest` and `ContrastRulesTest` check contrast of the status palette.
- How the screens look in light and dark, and how the animations feel, only shows on a device.

## Open work

The fade between loading, error and content on the Printers and Filament lists: OI-23.
