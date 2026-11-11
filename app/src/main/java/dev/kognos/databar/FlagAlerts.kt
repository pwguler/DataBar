package dev.kognos.databar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import java.time.Instant
import java.time.ZoneId

/** Alerts for the apps flagged today: the switch, the background check, and one notification per app per day. */
object FlagAlerts {

    private const val CHANNEL = "flag_alerts"

    /** The alerts of the latest day that had any, each as "<epoch day>:<uid>". */
    private const val KEY_ALERTED = "alerted"

    private fun notifications(c: Context): NotificationManager = c.getSystemService(NotificationManager::class.java)

    /** The channel of the alerts. Creating a channel that exists keeps the user's settings for it. */
    private fun channel(c: Context): NotificationChannel {
        notifications(c).createNotificationChannel(
            NotificationChannel(CHANNEL, "Flag alerts", NotificationManager.IMPORTANCE_DEFAULT)
        )
        return notifications(c).getNotificationChannel(CHANNEL)
    }

    /** Whether DataBar can post alerts: notifications are allowed for the app and for the alerts channel. */
    fun allowed(c: Context): Boolean = TODO("not implemented")

    /** Whether alerts are on: switched on, and [allowed]. */
    fun enabled(c: Context): Boolean = TODO("not implemented")

    /** Switches alerts on or off, and the background check with them. */
    fun setEnabled(c: Context, on: Boolean): Unit = TODO("not implemented")

    /** When the last check read the usage, or null before the first one. */
    fun lastCheck(c: Context): Long? = TODO("not implemented")

    /** Reads today's usage and alerts. Turns alerts off when they are no longer [enabled]. */
    fun check(c: Context, now: Long = System.currentTimeMillis()): Unit = TODO("not implemented")

    /** Posts one notification for each app in [today] that is flagged and has had none on the day of [now]. */
    @Synchronized
    internal fun alert(c: Context, today: List<UsageRepo.Watch>, now: Long) {
        val day = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        val prefs = UsageRepo.prefs(c)
        val sent = prefs.getStringSet(KEY_ALERTED, emptySet())!!.filter { it.startsWith("$day:") }.toSet()
        val fresh = today.filter { it.flagged && "$day:${it.uid}" !in sent }
        if (fresh.isEmpty()) return
        val channel = channel(c).id
        // Posted before it is stored: a crash in between repeats the alert, which replaces the same notification.
        for (app in fresh) notifications(c).notify(app.uid, notification(c, channel, app))
        prefs.edit().putStringSet(KEY_ALERTED, sent + fresh.map { "$day:${it.uid}" }).apply()
    }

    private fun notification(c: Context, channel: String, app: UsageRepo.Watch): Notification {
        val text = "\u2191 " + UsageRepo.fmt(app.tx) + " today \u00b7 " + UsageRepo.fmtRatio(app.ratio) +
            " its average \u00b7 " + app.bgPercent + "% in background"
        return Notification.Builder(c, channel)
            .setSmallIcon(R.drawable.ic_stat_bars)
            .setContentTitle(UsageRepo.resolve(c, app.uid).first + " is uploading a lot")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(MainActivity.open(c))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}

/** The background check. */
class FlagAlertJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean = TODO("not implemented")

    override fun onStopJob(params: JobParameters): Boolean = TODO("not implemented")
}
