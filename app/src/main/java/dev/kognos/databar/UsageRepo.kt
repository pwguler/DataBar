package dev.kognos.databar

import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.os.Process
import java.util.Calendar
import java.util.Locale

/**
 * Sumber data: NetworkStatsManager (butuh izin "Usage access" / PACKAGE_USAGE_STATS).
 * Tidak ada izin INTERNET di app ini: semua angka dihitung lokal, tidak ada yang dikirim keluar.
 */
object UsageRepo {

    private const val PREF = "databar"
    const val KEY_CYCLE_DAY = "cycle_day"
    const val KEY_WIFI = "include_wifi"
    const val BARS = 14

    fun prefs(c: Context): SharedPreferences = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun cycleDay(c: Context): Int = prefs(c).getInt(KEY_CYCLE_DAY, 1).coerceIn(1, 28)

    fun includeWifi(c: Context): Boolean = prefs(c).getBoolean(KEY_WIFI, false)

    fun hasUsageAccess(c: Context): Boolean {
        return try {
            val ops = c.getSystemService(AppOpsManager::class.java) ?: return false
            val mode = ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (t: Throwable) {
            false
        }
    }

    /** Awal siklus tagihan pada/before [now], memakai tanggal [day]. */
    fun cycleStart(now: Long, day: Int): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, day.coerceAtMost(maxDay))
        if (cal.timeInMillis > now) {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.add(Calendar.MONTH, -1)
            val maxPrev = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            cal.set(Calendar.DAY_OF_MONTH, day.coerceAtMost(maxPrev))
        }
        return cal.timeInMillis
    }

    fun totalBytes(c: Context, start: Long, end: Long, wifi: Boolean): Long {
        if (end <= start) return 0L
        val nsm = c.getSystemService(NetworkStatsManager::class.java) ?: return 0L
        var sum = 0L
        try {
            val b = nsm.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, null, start, end)
            sum += b.rxBytes + b.txBytes
        } catch (t: Throwable) {
            // SecurityException kalau izin usage access belum diberikan
        }
        if (wifi) {
            try {
                val w = nsm.querySummaryForDevice(ConnectivityManager.TYPE_WIFI, null, start, end)
                sum += w.rxBytes + w.txBytes
            } catch (t: Throwable) {
            }
        }
        return sum
    }

    data class Day(val start: Long, val bytes: Long)

    /** Pemakaian per hari dari awal siklus sampai sekarang, dipotong ke [days] hari terakhir. */
    fun daily(c: Context, cycleStart: Long, days: Int, wifi: Boolean): List<Day> {
        val all = ArrayList<Day>()
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.timeInMillis = cycleStart
        var guard = 0
        while (cal.timeInMillis <= now && guard < 62) {
            val s = cal.timeInMillis
            cal.add(Calendar.DAY_OF_MONTH, 1)
            val e = if (cal.timeInMillis > now) now else cal.timeInMillis
            all.add(Day(s, totalBytes(c, s, e, wifi)))
            guard++
        }
        if (all.size <= days) return all
        return all.subList(all.size - days, all.size)
    }

    fun fmt(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.0f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}
