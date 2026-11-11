package dev.kognos.databar

import android.app.Notification
import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Process
import org.junit.Assert.assertEquals
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
    fun tappingANotificationOpensDataBar() {
        FlagAlerts.alert(app, listOf(watch(10_001)), morning)

        val tap = shadowOf(posted.single().contentIntent)
        assertTrue(tap.isActivityIntent)
        assertEquals(MainActivity::class.java.name, tap.savedIntent.component?.className)
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
}
