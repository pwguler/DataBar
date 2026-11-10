package dev.kognos.databar

import android.app.usage.NetworkStats.Bucket.STATE_DEFAULT
import android.app.usage.NetworkStats.Bucket.STATE_FOREGROUND
import dev.kognos.databar.UsageRepo.Flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageRepoWatchTest {

    // Each uid uploaded 3000 B over a 30-day span: a 100 B daily average. App uids start at 10000 in each user.
    private val notApps = listOf(-5, -4, -3, 0, 1000, 1013, 1_099_000) // tethering, removed, VPN, kernel, system, a system process, an isolated process
    private val span = (listOf(10_010, 10_011, 10_012, 1_010_020) + notApps).map { Flow(it, STATE_FOREGROUND, rx = 0, tx = 3000) }

    private val day = listOf(
        Flow(10_010, STATE_DEFAULT, rx = 20, tx = 150), // 3x average, exactly half in background
        Flow(10_010, STATE_FOREGROUND, rx = 80, tx = 150),
        Flow(10_011, STATE_DEFAULT, rx = 0, tx = 149), // 3x average, just under half in background
        Flow(10_011, STATE_FOREGROUND, rx = 0, tx = 151),
        Flow(10_012, STATE_DEFAULT, rx = 0, tx = 299), // all background, just under 3x average
        Flow(10_013, STATE_DEFAULT, rx = 500, tx = 0), // download only
        Flow(1_010_020, STATE_DEFAULT, rx = 0, tx = 600), // an app in a second user, 6x average, all background
    ) + notApps.map { Flow(it, STATE_DEFAULT, rx = 0, tx = 600) } // 6x average, all background

    private val watched = UsageRepo.watch(day, span, days = 30).associateBy { it.uid }

    @Test
    fun backgroundUploadCountsDefaultStateBuckets() {
        val app = watched.getValue(10_010)
        assertEquals(300L, app.tx)
        assertEquals(100L, app.rx)
        assertEquals(150L, app.bgTx)
        assertEquals(50, app.bgPercent)
    }

    @Test
    fun flagsAppAtThreeTimesAverageWithHalfInBackground() {
        assertTrue(watched.getValue(10_010).flagged)
        assertTrue(watched.getValue(1_010_020).flagged)
    }

    @Test
    fun doesNotFlagAppBelowEitherThreshold() {
        assertFalse(watched.getValue(10_011).flagged)
        assertFalse(watched.getValue(10_012).flagged)
    }

    @Test
    fun neverFlagsTrafficThatIsNotAnInstalledApp() {
        for (uid in notApps) assertFalse("uid $uid", watched.getValue(uid).flagged)
    }

    @Test
    fun leavesOutAppsThatUploadedNothing() {
        assertFalse(10_013 in watched)
    }
}
