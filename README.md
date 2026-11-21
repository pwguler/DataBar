# DataBar

An Android app and two home-screen widgets for mobile data: the last 30 days as bars, the apps that upload heavily in the background, and a switch that cuts all data of the apps you pick.

- **App screen:** the total for the last 30 days, today and the daily average, and a 30-day chart. Tap a day to list the apps that uploaded data that day. **Highlight** sorts them by upload compared with their own daily average, and **Volume** by total bytes.
- **Flagged apps:** an app is flagged when its upload for the day is at least 3× its average daily upload over the 30 days, at least half of that upload happened in the background, and the upload is at least a tenth of what all apps together upload on an average day. Only installed apps can be flagged; tethering, VPN and system traffic cannot.
- **Flag alerts:** a notification when an app is flagged today, once a day per app. They are off until you turn on **Flag alerts** in the app. DataBar then checks about every 30 minutes in the background, and the row shows the time of the last check.
- **Block apps:** pick apps under **Blocked apps** and turn on **Block apps**: those apps lose all network access, on mobile data and on Wi-Fi, while every other app keeps its internet. DataBar does this with a local VPN that admits only the blocked apps and drops every packet they send, their DNS lookups included; nothing is forwarded anywhere, and no root is needed. Android asks once to allow the VPN, shows its key icon in the status bar while the block runs, and DataBar shows a quiet "Blocking N apps" notification (on Android 13 and later, once notifications are allowed; DataBar asks when you turn the block on). The block starts again after a restart.
- **DataBar widget** (2x1): the 30-day bars, with today in the accent color.
- **Flagged apps widget** (4x2): today's flagged apps, highest first.

DataBar counts mobile data only, unless **Include Wi-Fi** is on.

## Privacy

DataBar has no INTERNET permission. It reads Android's own usage statistics (`NetworkStatsManager`) and computes every number on the phone, so nothing leaves it. The VPN of **Block apps** is local: it carries only the traffic of the blocked apps, and DataBar drops that traffic without reading it or sending it on.

Permissions:

- `PACKAGE_USAGE_STATS` (Usage access): to read data usage per app. Android grants it only in Settings; the **Usage access** row in the app opens that page.
- `QUERY_ALL_PACKAGES`: to show app names and icons. Without it, Android 11+ hides the installed apps.
- `ACCESS_NETWORK_STATE`.
- `POST_NOTIFICATIONS`: to show the flag alerts and the notification of the app block. Android asks for it when you turn **Flag alerts** or **Block apps** on.
- `RECEIVE_BOOT_COMPLETED`: to keep the background check of the alerts, and the app block, after a restart.
- `BIND_VPN_SERVICE` (on DataBar's VPN service, so that only Android can bind it): the app block. Android asks you to allow the VPN the first time you turn **Block apps** on.
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE`: the app block runs as a foreground service with its notification, so Android keeps it running.

## Install

Download `DataBar-0.3.0.apk` from the [latest release](https://github.com/pwguler/DataBar/releases/latest) and open it on the phone (Android 10 or later). The APK is signed with a debug key, so Android asks you to allow installs from the browser or file manager. It installs as an update over earlier DataBar builds.

## Build and test

Needs JDK 17 and the Android SDK (platform 34). Point `ANDROID_HOME` at the SDK, or set `sdk.dir` in `local.properties`.

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # unit and Robolectric tests
```

## Layout

```
app/src/main/java/dev/kognos/databar/
  MainActivity.kt    app screen: header, 30-day chart, settings, app list
  UsageRepo.kt       usage queries, the flag rule, app names, number formats
  FlagAlerts.kt      flag alerts: the switch, the background check, the notifications
  AppBlock.kt        app block: the switch and the blocked set, the local VPN, the restart after boot
  BlockActivity.kt   picker of the blocked apps
  Bars.kt            pill-bar chart, drawn to a bitmap
  TileWidget.kt      widget base: sizes, the "DataBar" title, tap to open
  DataBarWidget.kt   30-day bars widget
  FlaggedWidget.kt   Flagged apps widget
app/src/main/res/    layouts, drawables, colors and text styles
app/src/test/        unit and Robolectric tests
DESIGN.md            design: type, colors, layout, widgets, notification, icon
docs/specs/          specs of the One UI design, the flag alerts and the app block, with their acceptance criteria
```

## Limits

- The numbers are measured on the phone and can differ a little from the carrier's count.
- The widgets update every 30 minutes and each time the app opens; they are not live.
- An alert can come late: Android delays background checks while the phone sleeps, and limits them for apps that are rarely opened. A flag that appears after the last check of a day gets no alert.
- Without Usage access, the app and the widgets show no data.
- Android runs one VPN at a time. Turning on **Block apps** disconnects another VPN app, and connecting another VPN switches **Block apps** off; turn it on again afterwards.
- When another app is set as Always-on VPN (Settings > Network & internet > VPN), Android does not let DataBar start its VPN, so the block cannot run; **Block apps** switches off when DataBar opens. DataBar itself cannot be set as Always-on VPN.
- Private DNS is unaffected: it applies to the apps that are not blocked as before. Blocked apps resolve nothing.
- System apps can't be blocked reliably: the picker lists only apps with a launcher icon and leaves out apps that run as a system uid (such as Settings), and traffic that system services make on behalf of an app (downloads, push messages, location) is outside the VPN. Android blocks by uid, so apps that share one uid are blocked together.
- A blocked app's traffic stops on the phone, so its usage in DataBar drops to nothing from then on; what it used before stays in the numbers.
