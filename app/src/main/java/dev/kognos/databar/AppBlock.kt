package dev.kognos.databar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.IOException

/**
 * Cuts all network access of the apps the user picks: a local VPN admits only those apps and drops every
 * packet they send. DataBar forwards nothing, so it needs no INTERNET permission.
 */
object AppBlock {

    private const val KEY_ON = "block"
    private const val KEY_BLOCKED = "blocked"
    private const val CHANNEL = "app_block"
    const val NOTIFICATION = 1 // ids of flag alerts are app uids, 10000 and up

    fun enabled(c: Context): Boolean = UsageRepo.prefs(c).getBoolean(KEY_ON, false)

    fun blocked(c: Context): Set<String> = UsageRepo.prefs(c).getStringSet(KEY_BLOCKED, emptySet())!!.toSet()

    /** Switches the block on or off. Turning it on needs VPN consent first: see [VpnService.prepare]. */
    fun setEnabled(c: Context, on: Boolean) {
        save(c, on)
        sync(c)
    }

    /** Records the switch without touching the tunnel, for the service when Android takes the VPN away. */
    internal fun save(c: Context, on: Boolean) = UsageRepo.prefs(c).edit().putBoolean(KEY_ON, on).apply()

    /** Adds [pkg] to the blocked set or takes it out, and rebuilds the tunnel. DataBar itself is never added. */
    @Synchronized
    fun setBlocked(c: Context, pkg: String, on: Boolean) {
        if (pkg == c.packageName) return
        val set = if (on) blocked(c) + pkg else blocked(c) - pkg
        UsageRepo.prefs(c).edit().putStringSet(KEY_BLOCKED, set).apply()
        sync(c)
    }

    /** The blocked apps that the tunnel can admit: installed, and never DataBar. */
    fun admissible(c: Context): List<String> = blocked(c).filter { admits(c, it) }

    private fun admits(c: Context, pkg: String): Boolean = pkg != c.packageName && try {
        c.packageManager.getApplicationInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Whether the tunnel should run: the block is on, DataBar holds VPN consent, and an app can be admitted. */
    fun shouldRun(c: Context): Boolean = enabled(c) && VpnService.prepare(c) == null && admissible(c).isNotEmpty()

    /**
     * Starts or rebuilds the tunnel when the block is on, DataBar holds VPN consent and an app can be admitted;
     * otherwise stops it. Called from DataBar's screens and at boot, where Android allows starting a service.
     */
    fun sync(c: Context) {
        val intent = Intent(c, BlockService::class.java)
        if (shouldRun(c)) {
            c.startForegroundService(intent.setAction(BlockService.ACTION_START))
        } else {
            try {
                c.startService(intent.setAction(BlockService.ACTION_STOP))
            } catch (e: IllegalStateException) {
                // Android refuses a service start from the background; no tunnel can be running then anyway.
            }
        }
    }

    /**
     * Sets up [b] to admit only [pkgs] and route all their traffic into the tunnel. Returns how many apps it
     * admitted; with none the caller must not establish, because an empty allowed list admits every app.
     */
    fun configure(c: Context, b: VpnService.Builder, pkgs: Set<String>): Int {
        // Checked here: on Android 14 addAllowedApplication accepts a package that is not installed.
        val admitted = pkgs.filter { admits(c, it) }
        for (pkg in admitted) b.addAllowedApplication(pkg)
        b.setSession("DataBar")
            .setBlocking(true)
            .addAddress("10.111.222.1", 32)
            .addAddress("fd00:db:db::1", 128)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
        return admitted.size
    }

    fun notification(c: Context, n: Int): Notification {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "App block", NotificationManager.IMPORTANCE_LOW))
        val tap = PendingIntent.getActivity(
            c, 2, Intent(c, BlockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bars)
            .setContentTitle(count(n))
            .setContentIntent(tap)
            .setOngoing(true)
            .build()
    }

    fun count(n: Int): String = if (n == 1) "Blocking 1 app" else "Blocking $n apps"

    data class App(val pkg: String, val label: String)

    /** The installed apps with a launcher entry, except DataBar. */
    fun launchable(c: Context): List<App> {
        val pm = c.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != c.packageName }
            .map { App(it, UsageRepo.label(c, it)) }
    }

    /** The picker's list: [apps] that match [query] by name or package, blocked first, then by name. */
    fun order(apps: List<App>, blocked: Set<String>, query: String): List<App> {
        val q = query.trim()
        return apps
            .filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) || it.pkg.contains(q, ignoreCase = true) }
            .sortedWith(compareBy<App> { it.pkg !in blocked }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }
}

/** The tunnel: admits the blocked apps and drops what they send. */
class BlockService : VpnService() {

    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A null intent is a restart by Android after it killed the service: rebuild as for a start.
        if (intent?.action == ACTION_STOP) {
            stop()
            return START_NOT_STICKY
        }
        // Foreground first: Android ends an app that is started this way and does not call it in time.
        try {
            startForeground(AppBlock.NOTIFICATION, AppBlock.notification(this, 0))
        } catch (e: IllegalStateException) {
            // Android 12+ can refuse a restart from the background; the next change or restart starts the block again.
            stopSelf()
            return START_NOT_STICKY
        }
        val builder = Builder()
        val n = AppBlock.configure(this, builder, AppBlock.blocked(this))
        val fresh = if (n > 0 && AppBlock.enabled(this)) builder.establish() else null // null without consent
        if (fresh == null) {
            stop()
            return START_NOT_STICKY
        }
        startForeground(AppBlock.NOTIFICATION, AppBlock.notification(this, n))
        val old = tun
        tun = fresh
        drain(fresh)
        old?.close() // after the new interface is up, so no packet slips out between the two
        return START_STICKY
    }

    /** Reads and drops every packet, until the interface closes. */
    private fun drain(fd: ParcelFileDescriptor) = Thread {
        val buf = ByteArray(32767)
        try {
            FileInputStream(fd.fileDescriptor).use { input -> while (input.read(buf) >= 0) Unit }
        } catch (e: IOException) {
            // Closed by a rebuild, a stop, or Android.
        }
    }.apply { isDaemon = true }.start()

    private fun stop() {
        tun?.close()
        tun = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Another VPN took over, or the user disconnected DataBar in Settings: the block switches off. */
    override fun onRevoke() {
        AppBlock.save(this, false)
        stop()
    }

    override fun onDestroy() {
        tun?.close()
        tun = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "dev.kognos.databar.BLOCK_START"
        const val ACTION_STOP = "dev.kognos.databar.BLOCK_STOP"
    }
}

/** Starts the block again after a restart and after an update of DataBar, when it is on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (AppBlock.shouldRun(c)) AppBlock.sync(c)
    }
}
