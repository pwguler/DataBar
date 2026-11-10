# one-ui-theme

## Goal
The app screen, two home-screen widgets (the 30-day bars and today's flagged apps) and the launcher icon look native to One UI as `DESIGN.md` describes, follow the system light/dark setting and the phone's accent color, and flag apps that upload heavily in the background.

## Non-goals
- Copy and data stay as in 9.1, except the widget copy below: the same labels, 30 days, the Highlight and Volume sorts, up to 15 apps, no axis labels.
- The widget title does not follow One UI's Widget labels switch: it shows whether the switch is on or off.
- No bundled fonts and no INTERNET permission.
- No in-app theme picker, and backgrounds do not follow the color palette.
- minSdk 29 and targetSdk 34 stay.
- The selected day and the sort mode are not kept when the activity is recreated, the same as on rotation in 9.1.

## Acceptance criteria
- AC-1: Background upload counts the `NetworkStats.Bucket.STATE_DEFAULT` buckets of an app. An app is flagged when its upload for the day is at least 3× its average daily upload over the 30 days and at least half of that upload is in the background. An app below either threshold is not flagged. Traffic that is not an installed app (tethering, VPN, removed apps, the kernel and system uids below 10000) is never flagged.
- AC-2: A day has no bar for zero bytes, at least a round dot as tall as the bar is wide for any traffic, the full height for the largest day, and a proportional height in between.
- AC-3: The debug build succeeds, and the APK reports version 0.1.0 with version code 42.
- AC-4: On the phone, in light and in dark mode, the app screen, both widgets and the icon match `DESIGN.md`: the theme's font, one accent (the switch's), the centered One UI header, the blocks, the One UI switch, and "DataBar" below each widget like One UI's own widget labels. The widgets follow a dark-mode switch without the app being opened, and the bars keep round ends after a resize. Apps that sync in the background show a non-zero bg %. Judge: `rubric`, on screenshots from the phone.
- AC-5: The Flagged apps widget lists today's flagged apps, highest × first, as many as fit; when some don't fit, the last row says "+N more" ("N flagged" when it is the only row). With nothing flagged it says "Nothing flagged today", and without Usage access "Usage access needed". Both widgets show "DataBar" below the tile. Flagged apps is added 4x2 and cannot be made shorter than 2 rows, and at its smallest size it shows app rows. Both widgets show a preview in the widget picker: a preview layout, and a preview image for launchers without preview layouts.
- AC-6: The × values use a decimal comma, like the byte amounts.

## Verification
JAVA_HOME=~/.local/jdk ANDROID_HOME=~/.local/android ~/.local/gradle-8.7/bin/gradle testDebugUnitTest --offline
JAVA_HOME=~/.local/jdk ANDROID_HOME=~/.local/android ~/.local/gradle-8.7/bin/gradle assembleDebug --offline
~/.local/android/build-tools/34.0.0/aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk
rubric: AC-4, on screenshots from the phone
Demo: install `DataBar-0.1.0.apk` from the v0.1.0 GitHub release, open the app in light and in dark mode, add the DataBar widget at 2x1 and the Flagged apps widget at 4x2, switch dark mode from quick settings with the app closed, then resize the widgets.
