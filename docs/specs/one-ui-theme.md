# one-ui-theme

## Goal
The app screen, the home-screen widget and the launcher icon look native to One UI as `DESIGN.md` describes, follow the system light/dark setting and the phone's accent color, and flag apps that upload heavily in the background.

## Non-goals
- Copy and data stay as in 9.1: the same labels, 30 days, the Highlight and Volume sorts, up to 15 apps, no axis labels.
- No bundled fonts and no INTERNET permission.
- No in-app theme picker, and backgrounds do not follow the color palette.
- `DonutChart.kt` and the billing-cycle code in `UsageRepo.kt` (`cycleDay`, `cycleStart`, `daily`, `KEY_CYCLE_DAY`) stay as they are.
- minSdk 29 and targetSdk 34 stay.
- The selected day and the sort mode are not kept when the activity is recreated, the same as on rotation in 9.1.

## Acceptance criteria
- AC-1: Background upload counts the `NetworkStats.Bucket.STATE_DEFAULT` buckets of an app. An app is flagged when its upload for the day is at least 3× its average daily upload over the 30 days and at least half of that upload is in the background. An app below either threshold is not flagged.
- AC-2: A day has no bar for zero bytes, at least a round dot as tall as the bar is wide for any traffic, the full height for the largest day, and a proportional height in between.
- AC-3: The debug build succeeds, and the APK reports version 11.1 with version code 39.
- AC-4: On the phone, in light and in dark mode, the app screen, the widget at 2x1 and 4x1, and the icon match `DESIGN.md`: the system font, the phone's accent, the One UI header and blocks. The widget follows a dark-mode switch without the app being opened, and its bars keep round ends after a resize. Apps that sync in the background show a non-zero bg %. Judge: `rubric`, on screenshots from the phone.

## Verification
JAVA_HOME=~/.local/jdk ANDROID_HOME=~/.local/android ~/.local/gradle-8.7/bin/gradle testDebugUnitTest --offline
JAVA_HOME=~/.local/jdk ANDROID_HOME=~/.local/android ~/.local/gradle-8.7/bin/gradle assembleDebug --offline
~/.local/android/build-tools/34.0.0/aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk
rubric: AC-4, on screenshots from the phone
Demo: install `dist/DataBar-11.1.apk`, open the app in light and in dark mode, add the widget at 2x1 and at 4x1, switch dark mode from quick settings with the app closed, then resize the widget.
