package dev.kognos.databar

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context

/** Alerts for the apps flagged today: the switch, the background check, and one notification per app per day. */
object FlagAlerts {

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
    internal fun alert(c: Context, today: List<UsageRepo.Watch>, now: Long): Unit = TODO("not implemented")
}

/** The background check. */
class FlagAlertJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean = TODO("not implemented")

    override fun onStopJob(params: JobParameters): Boolean = TODO("not implemented")
}
