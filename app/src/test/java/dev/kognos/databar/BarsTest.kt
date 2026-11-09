package dev.kognos.databar

import org.junit.Assert.assertEquals
import org.junit.Test

class BarsTest {

    private fun height(bytes: Long) = Bars.height(bytes, max = 1000, full = 120f, min = 6f)

    @Test
    fun zeroBytesHasNoBar() {
        assertEquals(0f, height(0), 0f)
    }

    @Test
    fun anyTrafficShowsAtLeastARoundDot() {
        assertEquals(6f, height(1), 0f)
        assertEquals(6f, height(25), 0f)
    }

    @Test
    fun largestDayFillsTheHeight() {
        assertEquals(120f, height(1000), 0f)
    }

    @Test
    fun otherDaysAreProportional() {
        assertEquals(60f, height(500), 0.001f)
        assertEquals(30f, height(250), 0.001f)
    }
}
