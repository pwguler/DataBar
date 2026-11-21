# app-block

## Goal
The user picks installed apps in DataBar and cuts all their network access, mobile data and Wi-Fi, without root, while every other app keeps its internet.

## Non-goals
- No INTERNET permission and no new dependency. DataBar forwards no packet: blocked traffic goes into a local VPN interface and is dropped there.
- No per-network block: a blocked app is cut on every network. No schedules, no allow-list mode, no count of dropped packets.
- System apps without a launcher entry are not offered, and DataBar is never in the blocked set.
- The flag rule, the chart, the app list and the widgets stay as they are; the screen changes only by the new settings rows.
- minSdk 29 and targetSdk 34 stay.

## Acceptance criteria
- AC-1: After Usage access, the settings block has a "Block apps" row with the One UI switch, off by default. Turning it on asks Android for VPN consent when DataBar does not have it yet; the block turns on only once consent is given, and the switch stays off when the user refuses. While the block is on, the row shows "Blocking N apps" under its label, or "No apps chosen" when the set is empty. Turning it on asks for the notification permission when notifications are blocked (Android 13+); the block runs whatever the answer. Turning it off stops the block.
- AC-2: The last settings row, "Blocked apps", shows how many apps are chosen ("None", "1 app", "N apps") and opens the picker.
- AC-3: The picker lists the installed apps that have a launcher entry and run as an app uid (not a system uid such as Settings'), plus the blocked apps that lost their launcher entry, except DataBar, each with its icon, name and a checkbox, blocked apps first and then by name, A to Z, ignoring case. A search field filters the list by name or package name, ignoring case. The order is set when the picker opens and when the search changes, not on each tick, so a row does not jump away from the finger. Ticking or unticking a row saves it at once.
- AC-4: The tunnel admits only the blocked apps (`addAllowedApplication` for each), has an IPv4 and an IPv6 address and DNS server, routes `0.0.0.0/0` and `::/0`, and drops every packet it reads, so the blocked apps' DNS lookups are dropped too. A blocked package that is no longer installed, or runs as a system uid, is skipped. With no admitted app the tunnel is not built at all, because a VPN with an empty allowed list captures every app. DataBar's own package is never admitted.
- AC-5: While the block is on, a change to the set rebuilds the tunnel, and so does the install of a blocked package (a reinstalled app gets a new uid). When another VPN takes over, the block switches off and stays off until the user turns it on again. After a restart, after DataBar is updated, and when DataBar opens, the block starts again when it is on; when DataBar no longer holds VPN consent (a restored backup, another app's Always-on VPN) it switches off instead. VPN consent is checked only when an app is chosen, since the check takes the VPN from another app. DataBar's VPN service tells Android it does not support Always-on.
- AC-6: While the tunnel runs, a foreground-service notification in the channel "App block" at low importance says "Blocking N apps", with the launcher bars as its small icon; tapping it opens the picker. The service declares `foregroundServiceType="specialUse"` with its subtype property.
- AC-7: The debug build succeeds. The APK reports version 0.3.0 with version code 44, declares the VPN service with `BIND_VPN_SERVICE` and an `android.net.VpnService` intent filter, requests FOREGROUND_SERVICE and FOREGROUND_SERVICE_SPECIAL_USE, and does not request INTERNET.
- AC-8: The two rows read as settings rows of `DESIGN.md`, and the picker uses its blocks, type and spacing. Judge: `rubric`, on renders of the app screen and the picker.

## Verification
./gradlew testDebugUnitTest
./gradlew assembleDebug
~/.local/android/build-tools/34.0.0/aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk
~/.local/android/build-tools/34.0.0/aapt2 dump xmltree --file AndroidManifest.xml app/build/outputs/apk/debug/app-debug.apk   # the service, its permission and intent filter; badging does not list them
~/.local/android/build-tools/34.0.0/apksigner verify dist/DataBar-0.3.0.apk
rubric: AC-8, on renders of the app screen and the picker
Demo: install `DataBar-0.3.0.apk`, open Blocked apps and tick a browser, go back and turn Block apps on, and accept Android's VPN dialog. The key icon shows in the status bar and the notification says "Blocking 1 app". The browser loads nothing on mobile data or on Wi-Fi, and another app still loads. Untick the browser: it loads again. Restart the phone: the block is back without opening DataBar.
