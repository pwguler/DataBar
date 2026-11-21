package dev.kognos.databar

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.net.VpnService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowVpnService

@RunWith(RobolectricTestRunner::class)
class AppBlockTest {

    private val app = RuntimeEnvironment.getApplication()
    private val started get() = shadowOf(app).nextStartedService

    @Before
    fun consentHeld() = ShadowVpnService.setPrepareResult(null) // Robolectric's default asks for consent

    private var nextUid = 10_100

    private fun install(pkg: String, label: String, launcher: Boolean = true, appUid: Int = nextUid++) {
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName = pkg
            applicationInfo = ApplicationInfo().apply {
                packageName = pkg
                nonLocalizedLabel = label
                uid = appUid
            }
        })
        if (launcher) {
            shadowOf(app.packageManager).addResolveInfoForIntent(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = pkg; name = "$pkg.Main" } }
            )
        }
    }

    // --- persistence

    @Test
    fun isOffWithNothingBlockedByDefault() {
        assertFalse(AppBlock.enabled(app))
        assertEquals(emptySet<String>(), AppBlock.blocked(app))
    }

    @Test
    fun keepsTheBlockedSetAndTheSwitch() {
        AppBlock.setBlocked(app, "com.example.a", true)
        AppBlock.setBlocked(app, "com.example.b", true)
        AppBlock.setBlocked(app, "com.example.a", false)
        AppBlock.setEnabled(app, true)

        assertEquals(setOf("com.example.b"), AppBlock.blocked(app))
        assertTrue(AppBlock.enabled(app))
    }

    @Test
    fun neverBlocksDataBarItself() {
        AppBlock.setBlocked(app, app.packageName, true)
        assertEquals(emptySet<String>(), AppBlock.blocked(app))
    }

    // --- the service follows the switch and the set

    @Test
    fun turningOnStartsTheTunnelWhenAnAppIsChosen() {
        install("com.example.a", "A")
        AppBlock.setBlocked(app, "com.example.a", true)
        assertEquals("off: no tunnel", BlockService.ACTION_STOP, started?.action)

        AppBlock.setEnabled(app, true)

        val s = started!!
        assertEquals(BlockService::class.java.name, s.component!!.className)
        assertEquals(BlockService.ACTION_START, s.action)
    }

    @Test
    fun aChangeToTheSetRebuildsTheTunnelWhileOn() {
        install("com.example.a", "A")
        install("com.example.b", "B")
        AppBlock.setBlocked(app, "com.example.a", true)
        AppBlock.setEnabled(app, true)
        shadowOf(app).clearStartedServices()

        AppBlock.setBlocked(app, "com.example.b", true)

        assertEquals(BlockService.ACTION_START, started!!.action)
    }

    @Test
    fun anEmptySetStopsTheTunnel() {
        install("com.example.a", "A")
        AppBlock.setBlocked(app, "com.example.a", true)
        AppBlock.setEnabled(app, true)
        shadowOf(app).clearStartedServices()

        AppBlock.setBlocked(app, "com.example.a", false)

        assertEquals("an empty allowed list would capture every app", BlockService.ACTION_STOP, started!!.action)
        assertTrue(AppBlock.enabled(app))
    }

    @Test
    fun onlyUninstalledAppsInTheSetStartNoTunnel() {
        AppBlock.setBlocked(app, "com.example.gone", true)
        AppBlock.setEnabled(app, true)

        assertEquals(BlockService.ACTION_STOP, started!!.action)
    }

    @Test
    fun turningOffStopsTheTunnel() {
        install("com.example.a", "A")
        AppBlock.setBlocked(app, "com.example.a", true)
        AppBlock.setEnabled(app, true)
        shadowOf(app).clearStartedServices()

        AppBlock.setEnabled(app, false)

        assertEquals(BlockService.ACTION_STOP, started!!.action)
    }

    @Test
    fun withoutVpnConsentNoTunnelStarts() {
        install("com.example.a", "A")
        ShadowVpnService.setPrepareResult(Intent("consent"))
        AppBlock.setBlocked(app, "com.example.a", true)
        AppBlock.setEnabled(app, true)

        assertEquals(BlockService.ACTION_STOP, started!!.action)
    }

    // --- restart

    @Test
    fun startsAgainAfterARestartOrAnUpdateWhenOn() {
        install("com.example.a", "A")
        AppBlock.setBlocked(app, "com.example.a", true)
        AppBlock.setEnabled(app, true)
        for (action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
            shadowOf(app).clearStartedServices()
            BootReceiver().onReceive(app, Intent(action))
            assertEquals(action, BlockService.ACTION_START, started?.action)
        }
    }

    @Test
    fun staysOffAfterARestartWhenOff() {
        install("com.example.a", "A")
        AppBlock.setBlocked(app, "com.example.a", true)
        shadowOf(app).clearStartedServices()

        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertNull(started)
    }

    @Test
    fun ignoresOtherBroadcasts() {
        install("com.example.a", "A")
        AppBlock.setBlocked(app, "com.example.a", true)
        AppBlock.setEnabled(app, true)
        shadowOf(app).clearStartedServices()

        BootReceiver().onReceive(app, Intent("com.example.SPOOF"))

        assertNull(started)
    }

    @Test
    fun aBlockThatLostItsVpnConsentSwitchesOff() {
        install("com.example.a", "A")
        AppBlock.setBlocked(app, "com.example.a", true)
        AppBlock.setEnabled(app, true)
        ShadowVpnService.setPrepareResult(Intent("consent")) // a restored backup, or another app's Always-on VPN
        shadowOf(app).clearStartedServices()

        AppBlock.sync(app)

        assertFalse(AppBlock.enabled(app))
        assertEquals(BlockService.ACTION_STOP, started!!.action)
    }

    @Test
    fun withNothingToBlockTheSwitchStaysOnWithoutConsent() {
        AppBlock.setEnabled(app, true)
        ShadowVpnService.setPrepareResult(Intent("consent"))

        AppBlock.sync(app)

        assertTrue("no app chosen: consent is not checked yet", AppBlock.enabled(app))
    }

    @Test
    fun aBlockedAppInstalledAgainRebuildsTheTunnel() {
        install("com.example.a", "A")
        AppBlock.setBlocked(app, "com.example.a", true)
        AppBlock.setEnabled(app, true)
        Robolectric.buildService(BlockService::class.java).create()
        shadowOf(app).clearStartedServices()

        app.sendBroadcast(Intent(Intent.ACTION_PACKAGE_ADDED, android.net.Uri.parse("package:com.example.other")))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNull("another app: no rebuild", started)

        app.sendBroadcast(Intent(Intent.ACTION_PACKAGE_ADDED, android.net.Uri.parse("package:com.example.a")))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(BlockService.ACTION_START, started?.action)
    }

    @Test
    fun anotherVpnTakingOverSwitchesTheBlockOff() {
        install("com.example.a", "A")
        AppBlock.setBlocked(app, "com.example.a", true)
        AppBlock.setEnabled(app, true)

        Robolectric.buildService(BlockService::class.java).create().get().onRevoke()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertFalse(AppBlock.enabled(app))
        assertEquals(setOf("com.example.a"), AppBlock.blocked(app))
    }

    // --- the tunnel

    /** A builder that records what DataBar asks of it; Robolectric cannot run the real one. */
    class Vpn : VpnService() {
        val allowed = mutableListOf<String>()
        val addresses = mutableListOf<String>()
        val routes = mutableListOf<String>()
        val dns = mutableListOf<String>()
        var blocking = false
        var established = false

        inner class Recorder : Builder() {
            override fun addAllowedApplication(packageName: String): Builder = apply { allowed += packageName }
            override fun addDisallowedApplication(packageName: String): Builder = throw AssertionError("disallowed $packageName")
            override fun addAddress(address: String, prefixLength: Int): Builder = apply { addresses += "$address/$prefixLength" }
            override fun addRoute(address: String, prefixLength: Int): Builder = apply { routes += "$address/$prefixLength" }
            override fun setBlocking(blocking: Boolean): Builder = apply { this@Vpn.blocking = blocking }
            override fun setSession(session: String): Builder = this
            override fun addDnsServer(address: String): Builder = apply { dns += address }
            override fun establish(): android.os.ParcelFileDescriptor? = null.also { established = true } // as without consent
        }
    }

    private fun configured(pkgs: Set<String>): Pair<Int, Vpn> {
        val vpn = Vpn()
        return AppBlock.configure(app, vpn.Recorder(), pkgs) to vpn
    }

    @Test
    fun theTunnelAdmitsOnlyTheBlockedAppsAndRoutesEverything() {
        install("com.example.a", "A")
        install("com.example.b", "B")

        val (n, vpn) = configured(setOf("com.example.a", "com.example.b"))

        assertEquals(2, n)
        assertEquals(setOf("com.example.a", "com.example.b"), vpn.allowed.toSet())
        assertEquals(setOf("0.0.0.0/0", "::/0"), vpn.routes.toSet())
        assertTrue("an IPv4 address: ${vpn.addresses}", vpn.addresses.any { '.' in it })
        assertTrue("an IPv6 address: ${vpn.addresses}", vpn.addresses.any { ':' in it })
        assertTrue("blocking reads, so the drain thread waits", vpn.blocking)
        assertTrue("an IPv4 DNS server: ${vpn.dns}", vpn.dns.any { '.' in it })
        assertTrue("an IPv6 DNS server: ${vpn.dns}", vpn.dns.any { ':' in it })
    }

    @Test
    fun theTunnelIsNeverEstablishedWithoutAnAdmittedApp() {
        AppBlock.setBlocked(app, "com.example.gone", true)
        AppBlock.setEnabled(app, true)
        val vpn = Vpn()

        assertNull(AppBlock.establish(app, vpn.Recorder()))
        assertFalse("an empty allowed list would capture every app", vpn.established)
        assertTrue(AppBlock.enabled(app))
    }

    @Test
    fun theTunnelIsNotEstablishedWhileTheBlockIsOff() {
        install("com.example.a", "A")
        AppBlock.setBlocked(app, "com.example.a", true)
        val vpn = Vpn()

        assertNull(AppBlock.establish(app, vpn.Recorder()))
        assertFalse(vpn.established)
    }

    @Test
    fun aTunnelThatAndroidRefusesSwitchesTheBlockOff() {
        install("com.example.a", "A")
        AppBlock.setBlocked(app, "com.example.a", true)
        AppBlock.setEnabled(app, true)
        val vpn = Vpn()

        assertNull(AppBlock.establish(app, vpn.Recorder()))
        assertTrue(vpn.established)
        assertFalse(AppBlock.enabled(app))
    }

    @Test
    fun appsOnASystemUidAreNeverAdmittedOrOffered() {
        install("com.android.settings", "Settings", appUid = 1000)

        val (n, _) = configured(setOf("com.android.settings"))

        assertEquals(0, n)
        assertEquals(emptyList<AppBlock.App>(), AppBlock.launchable(app))
    }

    @Test
    fun theTunnelSkipsUninstalledAppsAndDataBar() {
        install("com.example.a", "A")

        val (n, vpn) = configured(setOf("com.example.a", "com.example.gone", app.packageName))

        assertEquals(1, n)
        assertEquals(listOf("com.example.a"), vpn.allowed)
    }

    @Test
    fun theTunnelAdmitsNoneWhenNoBlockedAppIsInstalled() {
        val (n, vpn) = configured(setOf("com.example.gone"))
        assertEquals(0, n)
        assertEquals(emptyList<String>(), vpn.allowed)
    }

    // --- the notification

    @Test
    fun theNotificationSaysHowManyAppsAreBlockedAndOpensThePicker() {
        val n: Notification = AppBlock.notification(app, 3)

        assertEquals("Blocking 3 apps", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        val channel = app.getSystemService(NotificationManager::class.java).getNotificationChannel(n.channelId)
        assertEquals("App block", channel.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        val tap = shadowOf(n.contentIntent).savedIntent
        assertEquals(BlockActivity::class.java.name, tap.component!!.className)
        assertEquals("Blocking 1 app", AppBlock.notification(app, 1).extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }

    // --- the picker list

    private fun a(pkg: String, label: String) = AppBlock.App(pkg, label)

    @Test
    fun pickerListsBlockedAppsFirstThenByNameIgnoringCase() {
        val apps = listOf(a("p.zoo", "zoo"), a("p.alpha", "Alpha"), a("p.beta", "beta"), a("p.yak", "Yak"))

        val order = AppBlock.order(apps, setOf("p.zoo", "p.yak"), "").map { it.label }

        assertEquals(listOf("Yak", "zoo", "Alpha", "beta"), order)
    }

    @Test
    fun pickerSearchMatchesNameOrPackageIgnoringCase() {
        val apps = listOf(a("com.chat.app", "Messenger"), a("com.maps", "Maps"), a("org.x", "Browser"))

        assertEquals(listOf("Messenger"), AppBlock.order(apps, emptySet(), "CHAT").map { it.label })
        assertEquals(listOf("Maps", "Messenger"), AppBlock.order(apps, emptySet(), " m ").map { it.label })
        assertEquals(emptyList<String>(), AppBlock.order(apps, emptySet(), "zzz").map { it.label })
    }

    @Test
    fun pickerOffersLaunchableAppsExceptDataBar() {
        install("com.example.chat", "Chat")
        install("com.example.service", "Service", launcher = false)
        install(app.packageName, "DataBar")

        val apps = AppBlock.launchable(app)

        assertEquals(listOf(a("com.example.chat", "Chat")), apps)
    }

    @Test
    fun pickerKeepsABlockedAppThatLostItsLauncherEntry() {
        install("com.example.service", "Service", launcher = false)
        AppBlock.setBlocked(app, "com.example.service", true)

        assertEquals(listOf(a("com.example.service", "Service")), AppBlock.launchable(app))
    }

    // --- the manifest

    @Test
    fun theVpnServiceIsBoundOnlyByTheSystemAndIsASpecialUseForegroundService() {
        val info = app.packageManager.getServiceInfo(
            android.content.ComponentName(app, BlockService::class.java), PackageManager.GET_META_DATA
        )
        assertEquals(android.Manifest.permission.BIND_VPN_SERVICE, info.permission)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, info.foregroundServiceType)
        assertFalse(info.exported)
        assertEquals("Settings must not offer Always-on with lockdown", false, info.metaData.getBoolean("android.net.VpnService.SUPPORTS_ALWAYS_ON", true))
        for (action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
            val receivers = app.packageManager.queryBroadcastReceivers(Intent(action).setPackage(app.packageName), 0)
            assertEquals(action, listOf(BootReceiver::class.java.name), receivers.map { it.activityInfo.name })
        }
        val requested = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions!!.toList()
        assertTrue(android.Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE in requested)
        assertFalse("DataBar never sends anything itself", android.Manifest.permission.INTERNET in requested)
    }
}
