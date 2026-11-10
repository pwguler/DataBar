# DataBar design

DataBar looks like a Samsung app. The phone's theme is the source: on a Samsung phone `Theme.DeviceDefault.DayNight` is One UI, so the font, the text colors and the accent color come from the device and follow its light/dark setting. DataBar adds only what One UI leaves to each app: neutral backgrounds for the screen and its blocks, the bar colors, the One UI switch shape, and one red for attention.

## Sources

- Samsung's One UI design guide: content in blocks with large rounded corners (26dp), side margins of at least 24dp, and the primary blue `#0381FE`.
- The screen and block neutrals come from the color images of that guide (light `#FCFCFC`, dark `#010101`).
- Android's widget guide: `Theme.DeviceDefault.DayNight` on the widget root for the device colors, `@android:id/background` for the launch transition, and `system_app_widget_background_radius` on Android 12+.
- Samsung's "Changes to Home Screen & Wallpaper" support page for One UI 7: labels were added under widgets so that they line up with labeled app icons; App labels and Widget labels are switches under Settings > Home screen > App and widget style, and Widget labels needs App labels on; the labels take height from the widget, and they do not apply to widgets from third-party apps. On the phone, One UI labels Clock and Digital Wellbeing but not DataBar.
- `WallpaperColors.HINT_SUPPORTS_DARK_TEXT` (Android 12+): dark text is preferred over the wallpaper, and a launcher may set its text color to black.
- Samsung Community answers: One UI sets its home-screen labels black on a light wallpaper and white on a dark one; the exact rule has changed between One UI versions.
- Measured on the phone's screenshots (420 dpi: 12dp measures 31 px): One UI Home draws DataBar's widget at about 0.79 of its size (its 16dp padding measures 33 px), and its labels are set at about 11dp.

## Typography

The theme's font, nothing bundled: the text styles inherit `TextAppearance.DeviceDefault`, so they keep its font family. Text colors are theme attributes. Numbers use a decimal comma: "1,24 GB", "5,5×".

| Role | Size | Weight | Color |
|---|---|---|---|
| Header label "DataBar" | 15sp | regular | `textColorSecondary` |
| Total | 48sp | bold | `textColorPrimary` |
| Total unit | 22sp | bold | `textColorSecondary` |
| Summary line | 14sp | regular | `textColorSecondary` |
| Block title ("Watch · today") | 16sp | bold | `textColorPrimary` |
| Settings label | 16sp | regular | `textColorPrimary` |
| Settings value | 14sp | regular | `textColorSecondary`; `signal` when access is missing |
| Chip | 13sp | bold | `textColorSecondary`; selected: accent |
| App name | 16sp | regular | `textColorPrimary` |
| App traffic line | 13sp | regular | `textColorPrimary` |
| App baseline line | 12sp | regular | `textColorSecondary`; `signal` when flagged |
| Widget title "DataBar" | 14sp | regular | `widget_title`, or `widget_title_dark_text` when the wallpaper prefers dark text |
| Widget app name | 14sp | regular | `textColorPrimary` |
| Widget upload | 13sp | regular | `textColorSecondary` |
| Widget ratio | 13sp | bold | `signal` |
| Widget message ("Nothing flagged today", "+3 more") | 14sp | regular | `textColorSecondary` |

Rows have minimum heights, not fixed ones, so they grow with the phone's font size. The widget title is 14sp so that, at One UI's 0.79 widget scale, it renders at the size of One UI's own labels.

## Color

| Token | Light | Dark | Use |
|---|---|---|---|
| `background` | `#F2F2F2` | `#010101` | screen, status and navigation bars |
| `block` | `#FCFCFC` | `#171717` | content blocks, widget tile |
| `bar` | `#8F8F8F` | `#6B6B6B` | bars of the days |
| `rule` | `#E6E6E6` | `#262626` | dividers, outline of unselected chips |
| `track` | `#EBEBEB` | `#2A2A2A` | track of the share bars |
| `signal` | `#E60F40` | `#F22C59` | flagged apps, missing Usage access |
| `widget_title` | `#FFFFFF` | `#FFFFFF` | widget title over the wallpaper |
| `widget_title_dark_text` | `#000000` | `#000000` | widget title when the wallpaper prefers dark text |

From the theme: `textColorPrimary`, `textColorSecondary` and the accent, `colorControlActivated`: the color the system draws checked controls with. On a Samsung phone `colorAccent` is a different color, so it is not used. The accent marks the selected day: its bar, the share bars of its apps, the selected chip, the switch when on, and today's bar in the widget. `signal` marks only what needs attention.

Contrast (WCAG 2), light / dark: `bar` on `block` 3.15 / 3.36; `signal` on `block` 4.54 / 4.51.

## Layout

All values in dp.

- Header: sized to its text, with 32 above and 24 below. "DataBar", the total with its unit on one baseline, and the summary line, centered, 14 apart.
- Blocks: 12 from the screen edges and 12 apart, corner radius 26, padding 20; the settings block has 4 above and below its rows. 24 below the last block.
- Settings rows: at least 56 tall, a 1dp `rule` between them. Tapping anywhere on the Wi-Fi row toggles the switch.
- Switch: a 40 x 22 pill, the accent when on and `bar` when off, with a 16 white dot 3 from the top and 2 from the end.
- Apps block: the title, the chips 14 below it, then the rows 6 below the chips. Chips are 30 tall pills with 14 side padding, 8 apart, inside a 48 tall tap area. Selected chips are filled with the accent at 16% opacity; the others have a 1dp `rule` outline.
- App rows: at least 12 above and below, a 28 icon (the system's generic app icon for traffic that is not an app), 12 to the name, and a 4 tall rounded share bar 10 below the text. Any share shows at least a round dot.
- The whole screen scrolls, the header with it.

## Bars

The 30-day chart in the app and the widget is a row of pill bars.

- One bar per day, oldest on the left. The pitch is the width divided by 30, and a bar is half the pitch wide with fully round ends.
- A day with no traffic has no bar. Any other day has at least a round dot, as tall as the bar is wide; the largest day fills the height, the others in proportion.
- Days are `bar`, the selected day is the accent.
- The app chart is 112 tall and is drawn at the size of its view.

## Widgets

Two widgets, each a tile with the title "DataBar" below it, the way One UI labels its own widgets.

- Tile: `block`, the system widget corner radius on Android 12+ and 26 before, padding 16. The widget root uses `Theme.DeviceDefault.DayNight`, so the accent and the text colors follow the device. Tapping the tile opens DataBar.
- Title: centered, 2 below the tile. Its color is chosen when the widget updates: every 30 minutes and when DataBar opens.
- Each tile is drawn at every size the launcher lists; before Android 12, at the portrait or landscape size of the cell.
- **DataBar** (2x1): the bars as in "Bars"; today is the selected day.
- **Flagged apps** (4x2, and never shorter: one home-screen row leaves room for one row of apps at most): today's flagged apps, highest × first, one row each, at least 24 tall: a 20 icon, 8 to the name, then on the right the upload today ("↑ 188 MB") and the ratio ("5,5×"), 8 apart. As many rows as fit. When some flagged apps don't fit, the last row says "+N more", or "N flagged" when it is the only row. With nothing flagged the tile says "Nothing flagged today"; without Usage access, "Usage access needed".

## Icon

Adaptive icon on a 108dp canvas: `#0381FE` background, three white pill bars 11 wide at x 33, 48.5 and 64, 21, 32 and 44 tall, standing on y 76. The monochrome layer has the same bars.
