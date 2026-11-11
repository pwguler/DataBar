# flag-alerts

## Goal
DataBar notifies the user when an app is flagged today, and flags an app only when its upload is also a real share of what all apps usually upload.

## Non-goals
- No INTERNET permission and no new dependency.
- The other terms of the flag rule stay: at least 3× the app's own average daily upload, at least half of it in the background, installed apps only.
- No settings for the thresholds: the 3×, the half, the 10% and the 30 minutes are fixed.
- The widgets, the chart and the app list keep their layout; the screen changes only by the new settings row.
- An alert names the app and its amounts. It carries no servers or URLs.
- minSdk 29 and targetSdk 34 stay.

## Acceptance criteria
- AC-1: An app is flagged only when its upload for the day is also at least 10% of what all installed apps together upload on an average day over the 30 days. An app under that share is not flagged, whatever its ratio.
- AC-2: A check with alerts on posts one notification for each app that is flagged today and has had none today: the title is the app's name and "is uploading a lot", the text is "↑ <upload> today · <ratio> its average · <bg>% in background". It posts none for an app that is not flagged and none a second time on the same day, and it posts again, with its sound, on a later day. Tapping the notification opens DataBar on today and removes the notification.
- AC-3: Turning alerts on schedules a background check every 30 minutes that survives a restart, and turning them off cancels it. Opening DataBar schedules the check again when it is missing. Each check that reads the usage records its time. A check with alerts off or without Usage access posts nothing. Notifications that get blocked switch alerts off and cancel the background check, and alerts stay off until the user switches them on again.
- AC-4: The settings block starts with a "Flag alerts" row with the One UI switch, off by default. Turning it on when notifications are allowed turns alerts on and checks at once. When notifications are not allowed, it asks for the notification permission (Android 13+), or opens DataBar's notification settings when Android no longer shows that dialog and on Android 12 and lower; alerts turn on once notifications are allowed. While alerts are on, the row shows "Last check" with the day and time of the last check under its label. The switch shows off when notifications are blocked.
- AC-5: The debug build succeeds. The APK reports version 0.2.0 with version code 43, and requests POST_NOTIFICATIONS and RECEIVE_BOOT_COMPLETED but not INTERNET.
- AC-6: The Flag alerts row matches `DESIGN.md` and reads as one of the settings rows: the same label type, switch and spacing as Include Wi-Fi, with the summary under the label. Judge: `rubric`, on a render of the app screen.

## Verification
./gradlew testDebugUnitTest
./gradlew assembleDebug
~/.local/android/build-tools/34.0.0/aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk
rubric: AC-6, on a render of the app screen
Demo: install `DataBar-0.2.0.apk` from the v0.2.0 GitHub release and turn Flag alerts on: Android asks to allow notifications, and the row then shows "Last check" with the current time. Leave DataBar closed for an hour and open it again: "Last check" shows a time after you left (it can be older when the phone slept). When the Flagged apps widget lists an app, its notification is in the notification panel.
