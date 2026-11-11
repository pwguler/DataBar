package dev.kognos.databar

import android.Manifest
import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationManager
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Process
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FlagAlertsTest {

    private val app = RuntimeEnvironment.getApplication()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val posted get() = shadowOf(manager).allNotifications
    private val jobs = app.getSystemService(JobScheduler::class.java)

    // 10:00 local time, so an hour later is the same day and 24 hours later is the next one.
    private val morning = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 10, 10, 0, 0) }.timeInMillis
    private val hour = 60 * 60 * 1000L
    private val mb = 1024L * 1024

    private fun watch(uid: Int, flagged: Boolean = true) =
        UsageRepo.Watch(uid, tx = 188 * mb, rx = 0, bgTx = 117 * mb, ratio = 5.5f, flagged = flagged)

    private fun text(n: Notification, key: String) = n.extras.getCharSequence(key).toString()

    @Test
    fun postsANotificationForAFlaggedAppOnly() {
        // DataBar itself is the one installed app with a name here.
        FlagAlerts.alert(app, listOf(watch(Process.myUid()), watch(10_777, flagged = false)), morning)

        val n = posted.single()
        assertEquals("DataBar is uploading a lot", text(n, Notification.EXTRA_TITLE))
        assertEquals("\u2191 188 MB today \u00b7 5,5\u00d7 its average \u00b7 62% in background", text(n, Notification.EXTRA_TEXT))
        assertEquals(text(n, Notification.EXTRA_TEXT), text(n, Notification.EXTRA_BIG_TEXT))
    }

    @Test
    fun postsInTheFlagAlertsChannelAtTheDefaultImportance() {
        FlagAlerts.alert(app, listOf(watch(10_001)), morning)

        val channel = manager.notificationChannels.single()
        assertEquals("Flag alerts", channel.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertEquals(channel.id, posted.single().channelId)
    }

    @Test
    fun smallIconIsAShapeOnATransparentCanvas() {
        FlagAlerts.alert(app, listOf(watch(10_001)), morning)

        val icon = posted.single().smallIcon.loadDrawable(app)!!
        val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 96, 96)
        icon.draw(Canvas(bmp))
        val solid = (0 until 96).sumOf { x -> (0 until 96).count { y -> Color.alpha(bmp.getPixel(x, y)) == 255 } }
        assertTrue("the icon covers $solid of ${96 * 96} pixels", solid in 96 * 96 / 10..96 * 96 * 6 / 10)
    }

    @Test
    fun tappingANotificationOpensANewDataBarScreenAndRemovesIt() {
        FlagAlerts.alert(app, listOf(watch(10_001)), morning)

        val n = posted.single()
        val tap = shadowOf(n.contentIntent)
        assertTrue(tap.isActivity)
        assertEquals(MainActivity::class.java.name, tap.savedIntent.component?.className)
        // Clearing the task to a new screen is what shows today, and what puts no second screen on the first.
        val anew = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        assertEquals(anew, tap.savedIntent.flags and anew)
        assertTrue(n.flags and Notification.FLAG_AUTO_CANCEL != 0)
    }

    @Test
    fun postsOneNotificationPerFlaggedApp() {
        FlagAlerts.alert(app, listOf(watch(10_001), watch(10_002)), morning)

        assertEquals(2, posted.size)
    }

    @Test
    fun doesNotRepeatAnAppOnTheSameDayButStillAlertsANewOne() {
        FlagAlerts.alert(app, listOf(watch(10_001)), morning)
        manager.cancelAll() // the user dismissed it

        FlagAlerts.alert(app, listOf(watch(10_001)), morning + hour)
        assertEquals(0, posted.size)

        FlagAlerts.alert(app, listOf(watch(10_001), watch(10_002)), morning + 2 * hour)
        assertEquals(1, posted.size)
    }

    @Test
    fun alertsAgainOnALaterDay() {
        FlagAlerts.alert(app, listOf(watch(10_001)), morning)
        manager.cancelAll()

        FlagAlerts.alert(app, listOf(watch(10_001)), morning + 24 * hour)
        assertEquals(1, posted.size)
    }

    @Test
    fun aLaterDaysAlertSoundsAgainWhenTheEarlierOneIsStillShown() {
        FlagAlerts.alert(app, listOf(watch(10_001)), morning)
        FlagAlerts.alert(app, listOf(watch(10_001)), morning + 24 * hour) // the first one was not dismissed

        // Android plays no sound for an update of a shown notification that only alerts once.
        assertEquals(0, posted.single().flags and Notification.FLAG_ONLY_ALERT_ONCE)
    }

    @Test
    fun alertsAreOffUntilSwitchedOn() {
        assertFalse(FlagAlerts.enabled(app))
        assertTrue(jobs.allPendingJobs.isEmpty())
    }

    @Test
    fun switchingOnSchedulesACheckEvery30MinutesThatSurvivesARestart() {
        FlagAlerts.setEnabled(app, true)

        assertTrue(FlagAlerts.enabled(app))
        val job = jobs.allPendingJobs.single()
        assertEquals(ComponentName(app, FlagAlertJob::class.java), job.service)
        assertTrue(job.isPeriodic)
        assertEquals(30 * 60 * 1000L, job.intervalMillis)
        assertTrue(job.isPersisted)
    }

    @Test
    fun switchingOffCancelsTheCheck() {
        FlagAlerts.setEnabled(app, true)
        FlagAlerts.setEnabled(app, false)

        assertFalse(FlagAlerts.enabled(app))
        assertTrue(jobs.allPendingJobs.isEmpty())
    }

    @Test
    fun switchingOnAgainRestoresAMissingCheck() {
        FlagAlerts.setEnabled(app, true)
        jobs.cancelAll() // as a force stop does

        FlagAlerts.setEnabled(app, true)
        assertEquals(1, jobs.allPendingJobs.size)
    }

    @Test
    fun switchingOnAgainKeepsTheRunningCheck() {
        FlagAlerts.setEnabled(app, true)
        val running = jobs.allPendingJobs.single()

        FlagAlerts.setEnabled(app, true)
        assertSame("scheduling it again would start the 30 minutes anew", running, jobs.allPendingJobs.single())
    }

    @Test
    fun blockedNotificationsSwitchAlertsOffForGood() {
        FlagAlerts.setEnabled(app, true)
        shadowOf(manager).setNotificationsEnabled(false)
        assertFalse(FlagAlerts.enabled(app))

        shadowOf(manager).setNotificationsEnabled(true)
        assertFalse("alerts come back only when the user switches them on", FlagAlerts.enabled(app))
        assertTrue(jobs.allPendingJobs.isEmpty())
    }

    @Test
    fun aCheckRecordsItsTime() {
        FlagAlerts.setEnabled(app, true)
        assertNull(FlagAlerts.lastCheck(app))

        FlagAlerts.check(app, morning)
        assertEquals(morning, FlagAlerts.lastCheck(app))
    }

    @Test
    fun aCheckWithoutUsageAccessRecordsNothing() {
        // As on a fresh install; Robolectric allows every app op by default.
        shadowOf(app.getSystemService(AppOpsManager::class.java)).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName, AppOpsManager.MODE_DEFAULT
        )
        FlagAlerts.setEnabled(app, true)

        FlagAlerts.check(app, morning)
        assertNull(FlagAlerts.lastCheck(app))
    }

    @Test
    fun aCheckWithNotificationsBlockedTurnsAlertsOff() {
        FlagAlerts.setEnabled(app, true)
        shadowOf(manager).setNotificationsEnabled(false)

        FlagAlerts.check(app, morning)

        assertNull(FlagAlerts.lastCheck(app))
        assertTrue(jobs.allPendingJobs.isEmpty())
        shadowOf(manager).setNotificationsEnabled(true)
        assertFalse("alerts stay off until switched on again", FlagAlerts.enabled(app))
    }

    @Test
    fun aBlockedChannelBlocksAlerts() {
        FlagAlerts.alert(app, listOf(watch(10_001)), morning)
        assertTrue(FlagAlerts.allowed(app))

        manager.notificationChannels.single().importance = NotificationManager.IMPORTANCE_NONE
        assertFalse(FlagAlerts.allowed(app))
    }

    @Test
    fun theBackgroundCheckIsRegisteredAsAJobService() {
        val service = app.packageManager.getServiceInfo(ComponentName(app, FlagAlertJob::class.java), 0)
        assertEquals(JobService.PERMISSION_BIND, service.permission)
        val requested = app.packageManager
            .getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.toList()
        assertTrue("a job that survives a restart needs it", Manifest.permission.RECEIVE_BOOT_COMPLETED in requested)
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in requested)
    }
}
