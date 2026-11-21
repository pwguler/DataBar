# DataBar 0.3.0: block apps

## What was built

Branch `feature/app-block` in `/home/kognos/workspace/DataBar`, 6 commits on top of `f9789cf` (0.2.0). Nothing pushed, tagged or released.

- **Block apps** (settings row, One UI switch): master on/off. First enable calls `VpnService.prepare()` and shows Android's VPN consent dialog; a refusal leaves the switch off. Summary under the label: "Blocking N apps" or "No apps chosen".
- **Blocked apps** (settings row): "None" / "1 app" / "N apps"; opens the picker.
- **Picker** (`BlockActivity`): installed launchable apps on app uids, minus DataBar, plus any blocked app that lost its launcher entry. Icon, name, checkbox. Blocked first, then A to Z ignoring case. The search matches name or package, ignoring case. A tick saves at once and rebuilds the tunnel.
- **Tunnel** (`BlockService : VpnService`): `addAllowedApplication` for each blocked app only, `10.111.222.1/32` + `fd00:db:db::1/128`, routes `0.0.0.0/0` + `::/0`, DNS servers `10.111.222.2` + `fd00:db:db::2` so DNS is dropped too, blocking fd read and discarded by a daemon thread. It is never established with zero admitted apps, because an empty allowed list captures every app. No INTERNET permission.
- **Lifecycle**: rebuilds on a set change and on install/removal of a blocked package. Restarts on `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED` and DataBar opening. Switches off on `onRevoke` (another VPN took over) and when consent is gone (restored backup, another app's Always-on VPN). `SUPPORTS_ALWAYS_ON=false`, so DataBar can't be set as Always-on with lockdown.
- **Notification**: channel "App block", `IMPORTANCE_LOW`, ongoing, "Blocking N apps", tap opens the picker. FGS type `specialUse` with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`. `systemExempted` is listed for VPN apps on API 34, but it throws when DataBar is not the prepared VPN, so I didn't use it (decisions.md #1). Turning the block on asks for `POST_NOTIFICATIONS` on Android 13+ when notifications are blocked.
- Spec `docs/specs/app-block.md`, README (feature, permissions, limits), DESIGN.md (picker, screen-title role, notification), version 0.3.0 / code 44.
- `dist/DataBar-0.3.0.apk` (sha256 `71d2e9fa…8919`), byte-identical to the verified `app-debug.apk`. `dist/` is git-ignored.

Defaults I chose are in `decisions.md`. Two independent defect reviewers both found five real problems: DNS leak, reinstall bypass, system-uid capture, Always-on lockdown, and `prepare()` taking another VPN. Those five and their smaller findings are fixed in commit `1b3c669` (decisions.md #18). Their reports are `ab-review1-findings.md` and `ab-review2-findings.md`.

## Done check

| Check | Result |
|---|---|
| `./gradlew clean testDebugUnitTest assembleDebug` | BUILD SUCCESSFUL. 103 tests, 0 failed (was 63 in 0.2.0; 40 new) |
| `aapt2 dump badging` | `versionCode='44' versionName='0.3.0'`. Permissions: PACKAGE_USAGE_STATS, ACCESS_NETWORK_STATE, QUERY_ALL_PACKAGES, POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE. `grep -c INTERNET` = 0 |
| VPN service in the APK | `badging` lists services only as `other-services`, so it can't show this. `aapt2 dump xmltree --file AndroidManifest.xml` on the same APK shows `BlockService` with `permission=BIND_VPN_SERVICE`, `exported=false`, `foregroundServiceType=0x40000000` (specialUse), intent filter `android.net.VpnService`, and meta-data `SUPPORTS_ALWAYS_ON` |
| `apksigner verify dist/DataBar-0.3.0.apk` | exit 0, v2 scheme. Signer `CN=Android Debug`, SHA-256 `bba6bffc…0aa5` |
| answer.md committed on `feature/app-block` | yes, as `answer.md` at the repo root (copy of this file) |

## Acceptance criteria

| AC | Status | Evidence |
|---|---|---|
| AC-1 Block apps row, consent, refusal, summary | PASS (Robolectric) | `MainActivityBlockTest`: `blockRowsComeAfterUsageAccessAndStartOff`, `asksForVpnConsentAndStaysOffWhenRefused` (also checks there is no second dialog), `turnsOnOnceConsentIsGiven`, `withConsentHeldATapTurnsOnAndASecondTapOff`, `turningOnAsksForNotificationsWhenTheyAreBlocked` |
| AC-2 Blocked apps count, opens picker | PASS (Robolectric) | `blockedAppsRowCountsTheChosenAppsAndOpensThePicker` |
| AC-3 Picker contents, order, search, no jump | PASS (Robolectric) | `BlockActivityTest` (4 tests, driven by taps and typing); `AppBlockTest.picker*`, `appsOnASystemUidAreNeverAdmittedOrOffered`, `pickerKeepsABlockedAppThatLostItsLauncherEntry` |
| AC-4 Tunnel config, DNS, skip uninstalled/system/DataBar, never empty | PASS against a recording `VpnService.Builder`; `establish()` itself is device-only | `theTunnelAdmitsOnlyTheBlockedAppsAndRoutesEverything`, `theTunnelSkipsUninstalledAppsAndDataBar`, `theTunnelIsNeverEstablishedWithoutAnAdmittedApp`, `theTunnelIsNotEstablishedWhileTheBlockIsOff` |
| AC-5 Rebuild, reinstall, revoke, boot/update, consent loss, no Always-on | PASS for the logic (Robolectric); real tunnel swap and boot are device-only | `aChangeToTheSetRebuildsTheTunnelWhileOn`, `aBlockedAppInstalledAgainRebuildsTheTunnel`, `anotherVpnTakingOverSwitchesTheBlockOff`, `startsAgainAfterARestartOrAnUpdateWhenOn`, `aBlockThatLostItsVpnConsentSwitchesOff`, `aTunnelThatAndroidRefusesSwitchesTheBlockOff`, `openingDataBarSwitchesOffABlockThatLostItsVpnConsent`, manifest test (receiver registration, `SUPPORTS_ALWAYS_ON=false`) |
| AC-6 Notification and FGS type | PASS (Robolectric + manifest) | `theNotificationSaysHowManyAppsAreBlockedAndOpensThePicker`, `theVpnServiceIsBoundOnlyByTheSystemAndIsASpecialUseForegroundService`, xmltree above |
| AC-7 Build, version, manifest, no INTERNET | PASS | Done check above |
| AC-8 Looks like DESIGN.md | PASS, 9/9, by an independent judge on Robolectric renders (not the phone) | `rubric-ac8.md`, `judge-verdict.md`, `renders/*.png`. Judge notes not acted on: the stock checkbox sits 7dp inside the 20dp edge; the white switch dot is faint on the dark accent in the render (Samsung's accent differs; check by eye) |

Every new test was checked by breaking the code it covers; the test failed each time. One guard has no test: `sync` checks for a chosen app before calling `prepare()`. Robolectric's `prepare()` has no side effect, so the order can't be observed there.

## Not device-tested

No phone or emulator was available. None of this has run on Android for real:

- `establish()` and whether traffic is really cut: blocked apps on mobile data and Wi-Fi, DNS included; other apps unaffected.
- The VPN consent dialog, the key icon, and the notification on One UI (Android 16).
- Closing the old tun fd unblocking the drain thread's `read` (libcore signals blocked threads on close; not observed here).
- The restart after reboot and after an update, the sticky restart, and `onRevoke` when another VPN connects.
- `PACKAGE_ADDED` delivery to the running service on a reinstall.
- How blocked traffic shows in DataBar's own numbers. Blocked packets stay on the tun interface, never on mobile or Wi-Fi, so a blocked app should show no new usage. This is reasoned from how NetworkStats counts interfaces, not measured.
- The look on Samsung: the One UI font, the One UI checkbox and the accent. Renders used Robolectric's Roboto and stock widgets.

## On-phone test steps (Samsung S25)

1. Install `dist/DataBar-0.3.0.apk` over 0.2.0 (same debug key on this machine; if Android refuses the update, uninstall 0.2.0 first). Open DataBar. The settings block ends with **Block apps** (off) and **Blocked apps** ("None").
2. Tap **Blocked apps**. The picker lists your launcher apps without DataBar and without Settings. Type "chr" in the search: only matching apps remain. Clear it, tick Chrome (or any browser) and a second app such as YouTube. Ticked rows stay where they are. Go back: the row says "2 apps".
3. Tap **Block apps**. Android shows "Connection request" for the VPN: tap **Cancel**. The switch stays off and no second dialog appears. Tap again and tap **OK**. If notifications are off, Android asks for them: allow. The switch is on, the summary says "Blocking 2 apps", the status bar shows the key icon, and the shade shows a silent "Blocking 2 apps". Tapping it opens the picker.
4. Turn Wi-Fi off (mobile data on). Chrome loads nothing ("No internet") and YouTube loads nothing. Another app (WhatsApp, Play Store) works. Turn Wi-Fi on, mobile data off, and check the same.
5. DNS: in Chrome, any site fails at once with a DNS error, not after a long connect timeout.
6. In the picker, untick Chrome. The notification says "Blocking 1 app" and Chrome loads again within a few seconds. Tick it again: it stops again.
7. Restart the phone and don't open DataBar. After unlock the key icon and "Blocking 2 apps" are back, and Chrome is still blocked.
8. Uninstall YouTube and reinstall it from Play Store while the block is on. YouTube is blocked right after the install.
9. Connect another VPN app, if you have one. DataBar's key icon goes away, and opening DataBar shows **Block apps** off. Disconnect the other VPN and turn Block apps on again: it asks for consent again (Android moved the consent to the other app).
10. Settings > Connections > More connection settings > VPN > DataBar gear: Always-on VPN is not offered, or is shown as not supported.
11. Turn **Block apps** off. The key icon and the notification go away, and Chrome loads.
12. Look at the settings rows and the picker in light and dark mode. Compare the label sizes, the switch and the row spacing with Flag alerts / Include Wi-Fi. Check that the picker's checkbox lines up with the row end.
