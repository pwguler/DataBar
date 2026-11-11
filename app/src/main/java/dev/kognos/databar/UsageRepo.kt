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
 * Data source: NetworkStatsManager, which needs Usage access (PACKAGE_USAGE_STATS).
 * The app has no INTERNET permission: every number is computed on the phone and nothing leaves it.
 */
object UsageRepo {

    private const val PREF = "databar"
    const val KEY_WIFI = "include_wifi"
    const val BARS = 30

    fun prefs(c: Context): SharedPreferences = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

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

    // NetworkStatsManager takes the network type as ConnectivityManager.TYPE_MOBILE or TYPE_WIFI.
    @Suppress("DEPRECATION")
    fun totalBytes(c: Context, start: Long, end: Long, wifi: Boolean): Long {
        if (end <= start) return 0L
        val nsm = c.getSystemService(NetworkStatsManager::class.java) ?: return 0L
        var sum = 0L
        try {
            val b = nsm.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, null, start, end)
            sum += b.rxBytes + b.txBytes
        } catch (t: Throwable) {
            // SecurityException until Usage access is granted
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

    /** The last [days] days, today included. */
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

    /** Traffic of one uid in one state (foreground or background). */
    data class Flow(val uid: Int, val state: Int, val rx: Long, val tx: Long)

    /** One app's traffic on the watched day, against its own daily average over the span. [watch] decides [flagged]. */
    data class Watch(val uid: Int, val tx: Long, val rx: Long, val bgTx: Long, val ratio: Float, val flagged: Boolean) {
        val bgPercent: Int get() = if (tx > 0) (bgTx * 100 / tx).toInt() else 0
    }

    // Tethering, VPN, removed apps and system uids have no app to act on, so only app uids (per user) count.
    private fun isApp(uid: Int): Boolean = uid % 100_000 in Process.FIRST_APPLICATION_UID..Process.LAST_APPLICATION_UID

    /**
     * Apps that uploaded on the watched day, from per-state flows of that day and of the whole [days]-day span.
     * An app is flagged at 3x its own average day with half of that upload in the background, when the upload
     * is also a tenth of what all apps together upload on an average day.
     */
    fun watch(day: List<Flow>, span: List<Flow>, days: Int): List<Watch> {
        val d = days.coerceAtLeast(1)
        val spanTx = HashMap<Int, Long>()
        for (f in span) spanTx[f.uid] = (spanTx[f.uid] ?: 0L) + f.tx
        val appsTx = spanTx.filterKeys(::isApp).values.sum()
        val agg = HashMap<Int, LongArray>() // uid -> [tx, rx, bgTx]
        for (f in day) {
            val a = agg.getOrPut(f.uid) { LongArray(3) }
            a[0] += f.tx
            a[1] += f.rx
            if (f.state == NetworkStats.Bucket.STATE_DEFAULT) a[2] += f.tx
        }
        return agg.filter { it.value[0] > 0L }.map { (uid, v) ->
            val baseline = ((spanTx[uid] ?: 0L) / d).coerceAtLeast(1L)
            val ratio = v[0].toFloat() / baseline
            Watch(uid, v[0], v[1], v[2], ratio, isApp(uid) && ratio >= 3f && v[2] * 2 >= v[0] && v[0] * 10 * d >= appsTx)
        }
    }

    /** [watch] for today against the last [BARS] days, the span the app uses for today. */
    fun watchToday(c: Context, wifi: Boolean): List<Watch> {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val today = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, -(BARS - 1))
        return watch(flows(c, today, now, wifi), flows(c, cal.timeInMillis, now, wifi), BARS)
    }

    private fun collectState(
        nsm: NetworkStatsManager, type: Int, start: Long, end: Long, out: HashMap<Long, Flow>
    ) {
        // querySummary keeps buckets split by state; queryDetails merges them into STATE_ALL.
        val stats = nsm.querySummary(type, null, start, end)
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

    /** Bytes per uid and state over [start]..[end]. */
    // NetworkStatsManager takes the network type as ConnectivityManager.TYPE_MOBILE or TYPE_WIFI.
    @Suppress("DEPRECATION")
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

    private var uidLabels: HashMap<Int, Pair<String, String?>>? = null

    /** Map of uid to app name, built once from all installed apps. */
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

    /** Display name and package of a NetworkStats uid, including system uids that are not regular apps. */
    fun resolve(c: Context, uid: Int): Pair<String, String?> {
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

    /** Number and unit apart, with a decimal comma. */
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

    fun fmtRatio(ratio: Float): String = String.format(Locale.US, "%.1f", ratio).replace('.', ',') + "\u00d7"
}
