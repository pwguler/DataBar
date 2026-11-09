package dev.kognos.databar

import android.app.usage.NetworkStats.Bucket.STATE_DEFAULT
import android.app.usage.NetworkStats.Bucket.STATE_FOREGROUND
import dev.kognos.databar.UsageRepo.Flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageRepoWatchTest {

    // Each app uploaded 3000 B over a 30-day span: a 100 B daily average.
    private val span = listOf(10, 11, 12).map { Flow(it, STATE_FOREGROUND, rx = 0, tx = 3000) }

    private val day = listOf(
        Flow(10, STATE_DEFAULT, rx = 20, tx = 150), // 3x average, exactly half in background
        Flow(10, STATE_FOREGROUND, rx = 80, tx = 150),
        Flow(11, STATE_DEFAULT, rx = 0, tx = 149), // 3x average, just under half in background
        Flow(11, STATE_FOREGROUND, rx = 0, tx = 151),
        Flow(12, STATE_DEFAULT, rx = 0, tx = 299), // all background, just under 3x average
        Flow(13, STATE_DEFAULT, rx = 500, tx = 0), // download only
    )

    private val watched = UsageRepo.watch(day, span, days = 30).associateBy { it.uid }

    @Test
    fun backgroundUploadCountsDefaultStateBuckets() {
        val app = watched.getValue(10)
        assertEquals(300L, app.tx)
        assertEquals(100L, app.rx)
        assertEquals(150L, app.bgTx)
        assertEquals(50, app.bgPercent)
    }

    @Test
    fun flagsAppAtThreeTimesAverageWithHalfInBackground() {
        assertTrue(watched.getValue(10).flagged)
    }

    @Test
    fun doesNotFlagAppBelowEitherThreshold() {
        assertFalse(watched.getValue(11).flagged)
        assertFalse(watched.getValue(12).flagged)
    }

    @Test
    fun leavesOutAppsThatUploadedNothing() {
        assertFalse(13 in watched)
    }
}
