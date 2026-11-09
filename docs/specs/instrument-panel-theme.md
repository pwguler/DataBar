# instrument-panel-theme

## Goal
The app screen, the home-screen widget and the launcher icon share the Instrument panel look in `DESIGN.md`, follow the system light/dark setting, and flag apps that upload heavily in the background.

## Non-goals
- The layout, copy and data stay as in 9.1: the same sections and labels, 30 days, the Highlight and Volume sorts, no axis labels.
- No INTERNET permission; the fonts ship inside the APK.
- No in-app theme picker and no wallpaper colors.
- `DonutChart.kt` and the billing-cycle code in `UsageRepo.kt` (`cycleDay`, `cycleStart`, `daily`, `KEY_CYCLE_DAY`) stay as they are.
- minSdk 29 and targetSdk 34 stay.
- The selected day and the sort mode are not kept when the activity is recreated, the same as on rotation in 9.1.

## Acceptance criteria
- AC-1: Background upload counts the `NetworkStats.Bucket.STATE_DEFAULT` buckets of an app. An app is flagged when its upload for the day is at least 3× its average daily upload over the 30 days and at least half of that upload is in the background. An app below either threshold is not flagged.
- AC-2: A day's column in the dot matrix lights no dot for zero bytes, at least one dot for any traffic, every row for the largest day, and a proportional count in between.
- AC-3: The debug build succeeds, and the APK reports version 10.0 with version code 37.
- AC-4: On the phone, in light and in dark mode, the app screen, the widget at 2x1 and 4x1, and the icon match `DESIGN.md`. The widget follows a dark-mode switch without the app being opened, and its dots stay round after a resize. Apps that sync in the background show a non-zero bg %. Judge: `rubric`, on screenshots from the phone.

## Verification
JAVA_HOME=~/.local/jdk ANDROID_HOME=~/.local/android ~/.local/gradle-8.7/bin/gradle testDebugUnitTest --offline
JAVA_HOME=~/.local/jdk ANDROID_HOME=~/.local/android ~/.local/gradle-8.7/bin/gradle assembleDebug --offline
~/.local/android/build-tools/34.0.0/aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk
rubric: AC-4, on screenshots from the phone
Demo: install `dist/DataBar-10.0.apk`, grant Usage access, open the app in light and in dark mode, add the widget at 2x1 and at 4x1, switch dark mode from quick settings with the app closed, then resize the widget.
