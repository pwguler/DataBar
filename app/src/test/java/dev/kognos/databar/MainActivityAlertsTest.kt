package dev.kognos.databar

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.app.job.JobScheduler
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/** The Flag alerts row of the settings block, driven by taps as a user drives it. */
@RunWith(RobolectricTestRunner::class)
class MainActivityAlertsTest {

    private val app = RuntimeEnvironment.getApplication()
    private val notifications = shadowOf(app.getSystemService(NotificationManager::class.java))
    private val jobs = app.getSystemService(JobScheduler::class.java)
    private val controller = Robolectric.buildActivity(MainActivity::class.java)
    private val activity: MainActivity by lazy { controller.setup().get() }

    private fun <T : View> all(v: View, type: Class<T>): List<T> = when {
        type.isInstance(v) -> listOf(type.cast(v)!!)
        v is ViewGroup -> (0 until v.childCount).flatMap { all(v.getChildAt(it), type) }
        else -> emptyList()
    }

    private val labels get() = all(activity.window.decorView, TextView::class.java).map { it.text.toString() }

    /** The row is what takes the tap: the nearest clickable view around the label. */
    private val row: View
        get() {
            val label = all(activity.window.decorView, TextView::class.java).first { it.text.toString() == "Flag alerts" }
            return generateSequence(label as View) { it.parent as? View }.first { it.hasOnClickListeners() }
        }

    private val switchedOn get() = all(row, Switch::class.java).single().isChecked

    /** The visible text under the label; a Switch is a TextView too. */
    private val summary: String?
        get() = all(row, TextView::class.java).filter { it !is Switch }.drop(1)
            .singleOrNull { it.visibility == View.VISIBLE }?.text?.toString()

    private fun comeBack() {
        controller.pause().resume()
    }

    private fun answerThePermissionDialog(result: Int) {
        val asked = shadowOf(activity).lastRequestedPermission
        activity.onRequestPermissionsResult(asked.requestCode, asked.requestedPermissions, intArrayOf(result))
        // Android clears this when it delivers an answer, and refuses a new request until then.
        ReflectionHelpers.setField(Activity::class.java, activity, "mHasCurrentPermissionsRequest", false)
    }

    @Test
    fun flagAlertsIsTheFirstSettingsRowAndStartsOff() {
        val settings = labels.filter { it in listOf("Flag alerts", "Include Wi-Fi", "Usage access") }
        assertEquals(listOf("Flag alerts", "Include Wi-Fi", "Usage access"), settings)
        assertFalse(switchedOn)
        assertNull(summary)
        assertNull(shadowOf(activity).lastRequestedPermission)
    }

    @Test
    fun aTapTurnsAlertsOnAndChecksAtOnce() {
        row.performClick()

        assertTrue(switchedOn)
        assertTrue(FlagAlerts.enabled(app))
        assertEquals(1, jobs.allPendingJobs.size)
        assertTrue("summary: $summary", Regex("Last check [A-Z][a-z]{2} \\d{2}:\\d{2}").matches(summary.orEmpty()))
    }

    @Test
    fun aSecondTapTurnsAlertsOff() {
        row.performClick()
        row.performClick()

        assertFalse(switchedOn)
        assertFalse(FlagAlerts.enabled(app))
        assertTrue(jobs.allPendingJobs.isEmpty())
        assertNull(summary)
    }

    @Test
    fun asksForTheNotificationPermissionAndTurnsOnWhenItIsGranted() {
        notifications.setNotificationsEnabled(false)

        row.performClick()
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), shadowOf(activity).lastRequestedPermission.requestedPermissions.toList())
        assertFalse(switchedOn)
        assertFalse(FlagAlerts.enabled(app))

        notifications.setNotificationsEnabled(true)
        answerThePermissionDialog(PackageManager.PERMISSION_GRANTED)
        assertTrue(switchedOn)
        assertTrue(FlagAlerts.enabled(app))
    }

    @Test
    fun staysOffWhenThePermissionIsDeniedAndAsksAgainWhileAndroidStillShowsTheDialog() {
        val dialog = "android.content.pm.action.REQUEST_PERMISSIONS" // Android's permission dialog is an activity
        notifications.setNotificationsEnabled(false)
        row.performClick()
        assertEquals(dialog, shadowOf(activity).nextStartedActivity.action)
        answerThePermissionDialog(PackageManager.PERMISSION_DENIED)
        assertFalse(switchedOn)

        // After one denial Android reports a rationale: its dialog can still show.
        shadowOf(app.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, true)
        row.performClick()
        assertEquals("the dialog again, not the settings", dialog, shadowOf(activity).nextStartedActivity?.action)
    }

    @Test
    fun opensTheNotificationSettingsWhenAndroidNoLongerShowsItsDialog() {
        notifications.setNotificationsEnabled(false)
        row.performClick()
        answerThePermissionDialog(PackageManager.PERMISSION_DENIED)

        row.performClick() // denied for good: no rationale any more

        val opened = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, opened.action)
        assertEquals(app.packageName, opened.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertFalse(switchedOn)
    }

    @Test
    fun turnsOnAtTheReturnFromTheSettingsWhenNotificationsWereAllowedThere() {
        notifications.setNotificationsEnabled(false)
        row.performClick()
        answerThePermissionDialog(PackageManager.PERMISSION_DENIED)
        row.performClick()

        notifications.setNotificationsEnabled(true)
        comeBack()

        assertTrue(switchedOn)
        assertTrue(FlagAlerts.enabled(app))
    }

    @Test
    fun staysOffAtTheReturnFromTheSettingsWhenNotificationsStayedBlocked() {
        notifications.setNotificationsEnabled(false)
        row.performClick()
        answerThePermissionDialog(PackageManager.PERMISSION_DENIED)
        row.performClick()

        comeBack()
        assertFalse(switchedOn)

        notifications.setNotificationsEnabled(true) // allowed later, for another reason
        comeBack()
        assertFalse("alerts turn on only when the user asks", switchedOn)
    }

    @Test
    @Config(sdk = [32])
    fun onAndroid12ItOpensTheNotificationSettingsAtOnce() {
        notifications.setNotificationsEnabled(false)

        row.performClick()

        assertNull(shadowOf(activity).lastRequestedPermission)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, shadowOf(activity).nextStartedActivity.action)
    }

    @Test
    fun showsOffWhenNotificationsGetBlockedAndStaysOffWhenTheyAreAllowedAgain() {
        row.performClick()
        notifications.setNotificationsEnabled(false)

        comeBack()
        assertFalse(switchedOn)
        assertNull(summary)

        notifications.setNotificationsEnabled(true)
        comeBack()
        assertFalse("the row that showed off does not turn itself on", switchedOn)
    }

    @Test
    fun openingDataBarRestoresAMissingBackgroundCheck() {
        row.performClick()
        jobs.cancelAll() // as a force stop does

        comeBack()

        assertEquals(1, jobs.allPendingJobs.size)
    }
}
