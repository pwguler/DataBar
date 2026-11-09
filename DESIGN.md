# DataBar design

DataBar looks like a Samsung app. The phone's theme is the source: on a Samsung phone `Theme.DeviceDefault.DayNight` is One UI, so the system font, the text colors, the accent color and the switch come from the device and follow its light/dark setting and color palette. DataBar adds only what One UI leaves to each app: neutral backgrounds for the screen and its blocks, the bar colors, and one red for attention.

## Sources

- Samsung's One UI design guide: content in blocks with large rounded corners (26dp), side margins of at least 24dp, an expanded app bar of 39.67% of the screen height on phones, and the primary blue `#0381FE`.
- The screen and block neutrals come from the color images of that guide (light `#FCFCFC`, dark `#010101`).
- Android's widget guide: `Theme.DeviceDefault.DayNight` on the widget root for the device colors, `@android:id/background` for the launch transition, and `system_app_widget_background_radius` on Android 12+.

## Typography

The system font, nothing bundled. Text colors are theme attributes.

| Role | Size | Weight | Color |
|---|---|---|---|
| Header label "DataBar" | 15sp | regular | `textColorSecondary` |
| Total | 48sp | bold | `textColorPrimary` |
| Total unit | 22sp | bold | `textColorSecondary` |
| Summary line | 14sp | regular | `textColorSecondary` |
| Block title ("Watch · today") | 16sp | bold | `textColorPrimary` |
| Settings label | 16sp | regular | `textColorPrimary` |
| Settings value | 14sp | regular | `textColorSecondary`; `signal` when access is missing |
| Chip | 13sp | bold | `textColorSecondary`; selected: `colorAccent` |
| App name | 16sp | regular | `textColorPrimary` |
| App traffic line | 13sp | regular | `textColorPrimary` |
| App baseline line | 12sp | regular | `textColorSecondary`; `signal` when flagged |

Rows have minimum heights, not fixed ones, so they grow with the phone's font size.

## Color

| Token | Light | Dark | Use |
|---|---|---|---|
| `background` | `#F2F2F2` | `#010101` | screen, status and navigation bars |
| `block` | `#FCFCFC` | `#171717` | content blocks, widget tile |
| `bar` | `#8F8F8F` | `#6B6B6B` | bars of the days |
| `rule` | `#E6E6E6` | `#262626` | dividers, outline of unselected chips |
| `track` | `#EBEBEB` | `#2A2A2A` | track of the share bars |
| `signal` | `#E60F40` | `#F22C59` | flagged apps, missing Usage access |

From the theme: `textColorPrimary`, `textColorSecondary` and `colorAccent`. The accent marks the selected day: its bar, the share bars of its apps, the selected chip. The switch takes the accent on its own. `signal` marks only what needs attention.

Contrast (WCAG 2), light / dark: `bar` on `block` 3.15 / 3.36; `signal` on `block` 4.54 / 4.51.

## Layout

All values in dp.

- Header: the top 39.67% of the screen height. "DataBar", the total with its unit on one baseline, and the summary line, centered, 14 apart.
- Blocks: 12 from the screen edges and 12 apart, corner radius 26, padding 20; the settings block has 4 above and below its rows. 24 below the last block.
- Settings rows: at least 56 tall, a 1dp `rule` between them. Tapping anywhere on the Wi-Fi row toggles the switch.
- Apps block: the title, the chips 14 below it, then the rows. Chips are 30 tall pills with 14 side padding, 8 apart, inside a 48 tall tap area. Selected chips are filled with the accent at 16% opacity; the others have a 1dp `rule` outline.
- App rows: at least 12 above and below, a 28 icon, 12 to the name, and a 4 tall rounded share bar 10 below the text.
- The whole screen scrolls, the header with it.

## Bars

The 30-day chart in the app and the widget is a row of pill bars.

- One bar per day, oldest on the left. The pitch is the width divided by 30, and a bar is half the pitch wide with fully round ends.
- A day with no traffic has no bar. Any other day has at least a round dot, as tall as the bar is wide; the largest day fills the height, the others in proportion.
- Days are `bar`, the selected day is `colorAccent`.
- The app chart is 112 tall and is drawn at the size of its view.

## Widget

A tile in `block` with the system widget corner radius on Android 12+ and 26 before, padding 16. The widget root uses `Theme.DeviceDefault.DayNight`, so today's bar takes the device accent. The bars follow "Bars"; today is the selected day.

## Icon

Adaptive icon on a 108dp canvas: `#0381FE` background, three white pill bars 11 wide at x 33, 48.5 and 64, 21, 32 and 44 tall, standing on y 76. The monochrome layer has the same bars.
