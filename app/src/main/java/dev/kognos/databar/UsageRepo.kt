package dev.kognos.databar

import android.app.AppOpsManager
import android.app.usage.NetworkStats
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
    const val BARS = 30

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

    /** [days] hari terakhir (termasuk hari ini), terlepas dari siklus tagihan. */
    fun dailyLast(c: Context, days: Int, wifi: Boolean): List<Day> {
        val out = ArrayList<Day>()
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_MONTH, -(days - 1))
        var guard = 0
        while (guard < days) {
            val s = cal.timeInMillis
            cal.add(Calendar.DAY_OF_MONTH, 1)
            val e = if (cal.timeInMillis > now) now else cal.timeInMillis
            if (s > now) break
            out.add(Day(s, totalBytes(c, s, e, wifi)))
            guard++
        }
        return out
    }

    data class AppUsage(
        val uid: Int,
        val label: String,
        val pkg: String?,
        val rx: Long,
        val tx: Long
    ) {
        val bytes: Long get() = rx + tx
    }

    data class RxTx(val rx: Long, val tx: Long) {
        val bytes: Long get() = rx + tx
    }

    /** Aliran data dipisah per uid + state (foreground / background). */
    data class Flow(val uid: Int, val state: Int, val rx: Long, val tx: Long)

    /** One app's traffic on the watched day, against its own daily average over the span. */
    data class Watch(val uid: Int, val tx: Long, val rx: Long, val bgTx: Long, val ratio: Float) {
        val bgPercent: Int get() = TODO("not implemented")
        val flagged: Boolean get() = TODO("not implemented")
    }

    /** Apps that uploaded on the watched day, from per-state flows of that day and of the whole [days]-day span. */
    fun watch(day: List<Flow>, span: List<Flow>, days: Int): List<Watch> = TODO("not implemented")

    private fun collectState(
        nsm: NetworkStatsManager, type: Int, start: Long, end: Long, out: HashMap<Long, Flow>
    ) {
        val stats = nsm.queryDetails(type, null, start, end)
        try {
            val b = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(b)
                val key = (b.uid.toLong() shl 32) or (b.state.toLong() and 0xffffffffL)
                val prev = out[key]
                out[key] = if (prev == null) {
                    Flow(b.uid, b.state, b.rxBytes, b.txBytes)
                } else {
                    Flow(b.uid, b.state, prev.rx + b.rxBytes, prev.tx + b.txBytes)
                }
            }
        } finally {
            stats.close()
        }
    }

    /** Byte per uid per state pada rentang [start]..[end]. */
    fun flows(c: Context, start: Long, end: Long, wifi: Boolean): List<Flow> {
        if (end <= start) return emptyList()
        val nsm = c.getSystemService(NetworkStatsManager::class.java) ?: return emptyList()
        val acc = HashMap<Long, Flow>()
        try {
            collectState(nsm, ConnectivityManager.TYPE_MOBILE, start, end, acc)
        } catch (t: Throwable) {
        }
        if (wifi) {
            try {
                collectState(nsm, ConnectivityManager.TYPE_WIFI, start, end, acc)
            } catch (t: Throwable) {
            }
        }
        return acc.values.toList()
    }

    const val STATE_BG = 3
    const val STATE_FG = 2

    private fun collect(
        nsm: NetworkStatsManager, type: Int, start: Long, end: Long, out: HashMap<Int, RxTx>
    ) {
        val stats = nsm.queryDetails(type, null, start, end)
        try {
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val prev = out[bucket.uid]
                out[bucket.uid] = RxTx(
                    (prev?.rx ?: 0L) + bucket.rxBytes,
                    (prev?.tx ?: 0L) + bucket.txBytes
                )
            }
        } finally {
            stats.close()
        }
    }

    private var uidLabels: HashMap<Int, Pair<String, String?>>? = null

    /** Peta uid -> nama app, dibangun sekali dari semua aplikasi terpasang. */
    @Synchronized
    private fun uidMap(c: Context): HashMap<Int, Pair<String, String?>> {
        uidLabels?.let { return it }
        val map = HashMap<Int, Pair<String, String?>>()
        val pm = c.packageManager
        try {
            for (ai in pm.getInstalledApplications(0)) {
                try {
                    val label = pm.getApplicationLabel(ai).toString()
                    if (label.isNotBlank()) map.putIfAbsent(ai.uid, label to ai.packageName)
                } catch (ignored: Throwable) {
                }
            }
        } catch (t: Throwable) {
        }
        uidLabels = map
        return map
    }

    /** Nama tampilan untuk uid NetworkStats, termasuk uid sistem yang bukan aplikasi biasa. */
    /** Nama + package untuk sebuah uid (dipakai UI). */
    fun resolvePublic(c: Context, uid: Int): Pair<String, String?> = resolve(c, uid)

    private fun resolve(c: Context, uid: Int): Pair<String, String?> {
        when (uid) {
            -5 -> return "Hotspot / tethering" to null
            -4 -> return "Removed app" to null
            -3 -> return "VPN" to null
            -2 -> return "All" to null
            -1 -> return "Combined" to null
            0 -> return "Kernel" to null
            1000 -> return "Android system" to null
        }
        val map = uidMap(c)
        map[uid]?.let { return it }
        if (uid >= 100000) map[uid % 100000]?.let { return it }
        if (uid in 1001..1099) return "Android system" to null
        val pm = c.packageManager
        try {
            val pkgs = pm.getPackagesForUid(uid)
            if (!pkgs.isNullOrEmpty()) return pkgs.first() to pkgs.first()
        } catch (t: Throwable) {
        }
        try {
            val name = pm.getNameForUid(uid)
            if (!name.isNullOrBlank()) return name to null
        } catch (t: Throwable) {
        }
        return "System uid " + uid to null
    }

    /** Pemakaian per aplikasi pada rentang [start]..[end], terbesar dulu. */
    fun perApp(c: Context, start: Long, end: Long, wifi: Boolean): List<AppUsage> {
        if (end <= start) return emptyList()
        val nsm = c.getSystemService(NetworkStatsManager::class.java) ?: return emptyList()
        val totals = HashMap<Int, RxTx>()
        try {
            collect(nsm, ConnectivityManager.TYPE_MOBILE, start, end, totals)
        } catch (t: Throwable) {
        }
        if (wifi) {
            try {
                collect(nsm, ConnectivityManager.TYPE_WIFI, start, end, totals)
            } catch (t: Throwable) {
            }
        }
        return totals.entries
            .filter { it.value.bytes > 0L }
            .map { e ->
                val r = resolve(c, e.key)
                AppUsage(e.key, r.first, r.second, e.value.rx, e.value.tx)
            }
            .sortedByDescending { it.bytes }
    }

    /** Total byte per uid pada rentang [start]..[end]. */
    fun perAppTotals(c: Context, start: Long, end: Long, wifi: Boolean): HashMap<Int, RxTx> {
        val out = HashMap<Int, RxTx>()
        if (end <= start) return out
        val nsm = c.getSystemService(NetworkStatsManager::class.java) ?: return out
        try {
            collect(nsm, ConnectivityManager.TYPE_MOBILE, start, end, out)
        } catch (t: Throwable) {
        }
        if (wifi) {
            try {
                collect(nsm, ConnectivityManager.TYPE_WIFI, start, end, out)
            } catch (t: Throwable) {
            }
        }
        return out
    }

    /** Angka & satuan terpisah, desimal pakai koma (gaya Indonesia). */
    fun fmtParts(bytes: Long): Pair<String, String> {
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        val pair = when {
            bytes >= gb -> String.format(Locale.US, "%.2f", bytes / gb) to "GB"
            bytes >= mb -> String.format(Locale.US, "%.0f", bytes / mb) to "MB"
            bytes >= kb -> String.format(Locale.US, "%.0f", bytes / kb) to "KB"
            else -> "$bytes" to "B"
        }
        return pair.first.replace('.', ',') to pair.second
    }

    fun fmt(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(Locale.US, "%.2f", bytes / gb).replace('.', ',') + " GB"
            bytes >= mb -> String.format(Locale.US, "%.0f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}
