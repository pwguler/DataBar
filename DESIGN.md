# DataBar design

The Instrument panel look: black and white like a measuring instrument, numbers set in dot matrix, and one red signal for anything that needs attention. The reference is the widget style of Nothing OS. Nothing publishes no design tokens, so the colors below are chosen for this app and checked for contrast.

App screen, home-screen widget and launcher icon use the same tokens. Every color has a light and a dark value and follows the system dark-mode setting.

## Typography

| Role | Font | Size | Tracking | Case | Color |
|---|---|---|---|---|---|
| Title | Space Mono Bold | 12sp | 0.16em | upper | `ink` |
| Total | Doto, `wght` 900, `ROND` 100 | 84sp | -0.01em | as is | `ink` |
| Total unit | Space Mono | 14sp | 0 | as is | `ink_secondary` |
| Summary line | Space Mono | 11sp | 0.08em | upper | `ink_secondary` |
| Section label | Space Mono | 11sp | 0.14em | upper | `ink_secondary` |
| Chip | Space Mono | 11sp | 0.10em | upper | `ink_secondary`; selected: `background` on `ink` |
| Settings label | Space Grotesk | 15sp | 0 | as is | `ink` |
| Settings value | Space Mono | 11sp | 0.10em | upper | `ink_secondary`; `signal` when access is missing |
| App name | Space Grotesk | 16sp | 0 | as is | `ink` |
| App traffic line | Space Mono | 12sp | 0 | as is | `ink` |
| App baseline line | Space Mono | 11sp | 0 | as is | `ink_secondary`; `signal` when flagged |

The total sits on the same baseline as its unit, 10dp apart. Doto draws only the digits and the decimal comma.

Fonts, all under the SIL Open Font License from Google Fonts: Doto by Óliver Lalan, Space Grotesk by Florian Karsten, Space Mono by Colophon Foundry. They ship inside the APK with their license texts.

## Color

| Token | Light | Dark | Use |
|---|---|---|---|
| `background` | `#FFFFFF` | `#000000` | screen |
| `surface` | `#F2F2F2` | `#141414` | settings card |
| `ink` | `#000000` | `#FFFFFF` | primary text, the selected day, share bars, "on" states |
| `ink_secondary` | `#5C5C5C` | `#8A8A8A` | secondary text, switch when off |
| `rule` | `#E3E3E3` | `#262626` | dividers, chip outline, share bar track |
| `dot` | `#8A8A8A` | `#5C5C5C` | lit dots of the days |
| `dot_unlit` | `#EFEFEF` | `#171717` | unlit dots of the matrix |
| `signal` | `#D71920` | `#FF4545` | flagged apps, missing Usage access |
| `widget_background` | `#FFFFFF` | `#000000` | widget tile |

`ink` marks the data of the selected day and "on" states. `signal` marks only what needs attention, and nothing else is red.

Contrast (WCAG 2), light / dark: `ink_secondary` on `background` 6.69 / 6.08, on `surface` 5.97 / 5.34; `signal` on `background` 5.19 / 6.19; `dot` on `background` 3.45 / 3.14.

## Spacing

All values in dp.

- Screen padding: 30 top and bottom, 20 at the sides.
- Title to total 30, total to summary line 16, summary line to chart 26, chart to settings card 26, card to section label 30, section label to chips 12, chips to app list 6.
- Settings rows: 52 tall, 18 side padding, 1dp `rule` between rows.
- App rows: 14 above, 12 below, 28 icon with 12 to the name, share bar 10 below the text.
- Touch targets are at least 48 tall: chips have a 48 tap area around the drawn pill, and the whole Wi-Fi row toggles the switch.

## Shape

- Settings card: corner radius 20.
- Chips: pill, 13 side padding, 30 tall; 1dp `rule` outline, selected chips filled with `ink`, 8 apart.
- Switch: 40 × 22 pill. Off: 1.5dp `ink_secondary` outline, 12dp `ink_secondary` thumb on the left. On: filled `ink`, 12dp `background` thumb on the right.
- Widget tile: corner radius 24, padding 14.

## Dot matrix

The 30-day chart in the app and the widget is a dot matrix.

- One column per day, oldest on the left. Pitch is the width divided by 30; rows are the height divided by the pitch, rounded down. Dot diameter is 0.62 × pitch.
- A day with no traffic lights no dot. Any other day lights at least one dot and at most every row: the largest day lights every row, the others in proportion, rounded.
- Lit dots are `dot`, the selected day's lit dots are `ink`, unlit dots are `dot_unlit`.
- When the pitch is under 6dp, as in the 2x1 widget, the dots grow to 0.8 × pitch and unlit dots are left out.
- The app chart is 132dp tall. It is drawn at the size of its view, so dots stay round.

Share bars in the app list use the same language: 3dp dots every 6dp, the track in `rule`, the share in `ink`, or `signal` for a flagged app.

## Icon

Adaptive icon on a 108dp canvas: `#000000` background, three columns of white dots at x 38, 54 and 70 with 3, 4 and 6 dots, radius 3.6, pitch 9, bottom row at y 76. The top dot of the tallest column is `#D71920`. The monochrome layer has the same dots, all white.
