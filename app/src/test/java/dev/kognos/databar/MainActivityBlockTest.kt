package dev.kognos.databar

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
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

/** The Block apps and Blocked apps rows of the settings block, driven by taps. */
@RunWith(RobolectricTestRunner::class)
class MainActivityBlockTest {

    private val app = RuntimeEnvironment.getApplication()
    private val controller = Robolectric.buildActivity(MainActivity::class.java)
    private val activity: MainActivity by lazy { controller.setup().get() }

    @Before
    fun consentHeld() = ShadowVpnService.setPrepareResult(null)

    private fun <T : View> all(v: View, type: Class<T>): List<T> = when {
        type.isInstance(v) -> listOf(type.cast(v)!!)
        v is ViewGroup -> (0 until v.childCount).flatMap { all(v.getChildAt(it), type) }
        else -> emptyList()
    }

    private val labels get() = all(activity.window.decorView, TextView::class.java).map { it.text.toString() }

    private fun row(label: String): View {
        val t = all(activity.window.decorView, TextView::class.java).first { it.text.toString() == label }
        return generateSequence(t as View) { it.parent as? View }.first { it.hasOnClickListeners() }
    }

    private val switchedOn get() = all(row("Block apps"), Switch::class.java).single().isChecked

    private fun shownTexts(label: String) = all(row(label), TextView::class.java)
        .filter { it !is Switch && it.visibility == View.VISIBLE }.map { it.text.toString() }.drop(1)

    private fun install(pkg: String) = shadowOf(app.packageManager).installPackage(PackageInfo().apply {
        packageName = pkg
        applicationInfo = ApplicationInfo().apply { packageName = pkg; nonLocalizedLabel = pkg; uid = 10_100 + pkg.hashCode().mod(1000) }
    })

    @Test
    fun blockRowsComeAfterUsageAccessAndStartOff() {
        val settings = labels.filter { it in listOf("Flag alerts", "Include Wi-Fi", "Usage access", "Block apps", "Blocked apps") }
        assertEquals(listOf("Flag alerts", "Include Wi-Fi", "Usage access", "Block apps", "Blocked apps"), settings)
        assertFalse(switchedOn)
        assertEquals(emptyList<String>(), shownTexts("Block apps"))
        assertEquals(listOf("None"), shownTexts("Blocked apps"))
    }

    @Test
    fun asksForVpnConsentAndStaysOffWhenRefused() {
        val consent = Intent("android.net.vpn.CONSENT")
        ShadowVpnService.setPrepareResult(consent)

        row("Block apps").performClick()

        val asked = shadowOf(activity).nextStartedActivityForResult
        assertEquals(consent, asked.intent)
        shadowOf(activity).receiveResult(asked.intent, Activity.RESULT_CANCELED, null)
        assertNull("no second dialog after a refusal", shadowOf(activity).nextStartedActivityForResult)
        assertFalse(switchedOn)
        assertFalse(AppBlock.enabled(app))
    }

    @Test
    fun turnsOnOnceConsentIsGiven() {
        install("com.example.a")
        AppBlock.setBlocked(app, "com.example.a", true)
        val consent = Intent("android.net.vpn.CONSENT")
        ShadowVpnService.setPrepareResult(consent)
        row("Block apps").performClick()
        val asked = shadowOf(activity).nextStartedActivityForResult
        shadowOf(app).clearStartedServices()

        ShadowVpnService.setPrepareResult(null) // Android holds the consent once the user allows it
        shadowOf(activity).receiveResult(asked.intent, Activity.RESULT_OK, null)

        assertTrue(switchedOn)
        assertTrue(AppBlock.enabled(app))
        assertEquals(BlockService.ACTION_START, shadowOf(app).nextStartedService.action)
        assertEquals(listOf("Blocking 1 app"), shownTexts("Block apps"))
    }

    @Test
    fun withConsentHeldATapTurnsOnAndASecondTapOff() {
        row("Block apps").performClick()
        assertNull("no dialog when Android already allows the VPN", shadowOf(activity).nextStartedActivityForResult)
        assertTrue(switchedOn)
        assertEquals(listOf("No apps chosen"), shownTexts("Block apps"))

        row("Block apps").performClick()
        assertFalse(switchedOn)
        assertFalse(AppBlock.enabled(app))
        assertEquals(emptyList<String>(), shownTexts("Block apps"))
    }

    @Test
    fun openingDataBarSwitchesOffABlockThatLostItsVpnConsent() {
        install("com.example.a")
        AppBlock.setBlocked(app, "com.example.a", true)
        AppBlock.setEnabled(app, true)
        assertTrue(switchedOn)

        ShadowVpnService.setPrepareResult(Intent("android.net.vpn.CONSENT")) // e.g. restored from a backup
        controller.pause().resume()

        assertFalse(switchedOn)
        assertEquals(emptyList<String>(), shownTexts("Block apps"))
    }

    @Test
    fun turningOnAsksForNotificationsWhenTheyAreBlocked() {
        shadowOf(app.getSystemService(android.app.NotificationManager::class.java)).setNotificationsEnabled(false)

        row("Block apps").performClick()

        assertTrue(switchedOn)
        assertEquals(listOf(android.Manifest.permission.POST_NOTIFICATIONS), shadowOf(activity).lastRequestedPermission.requestedPermissions.toList())
        shadowOf(app.getSystemService(android.app.NotificationManager::class.java)).setNotificationsEnabled(true)
        activity.onRequestPermissionsResult(shadowOf(activity).lastRequestedPermission.requestCode,
            arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), intArrayOf(android.content.pm.PackageManager.PERMISSION_GRANTED))
        assertFalse("the answer is not taken as a yes to Flag alerts", FlagAlerts.enabled(app))
    }

    @Test
    fun blockedAppsRowCountsTheChosenAppsAndOpensThePicker() {
        install("com.example.a")
        install("com.example.b")
        activity
        AppBlock.setBlocked(app, "com.example.a", true)
        AppBlock.setBlocked(app, "com.example.b", true)
        controller.pause().resume() // back from the picker
        assertEquals(listOf("2 apps"), shownTexts("Blocked apps"))

        row("Blocked apps").performClick()

        assertEquals(BlockActivity::class.java.name, shadowOf(activity).nextStartedActivity.component!!.className)
    }
}
