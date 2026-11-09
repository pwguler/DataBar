package dev.kognos.databar

import org.junit.Assert.assertEquals
import org.junit.Test

class BarsTest {

    // A 300 x 120 chart of 30 days: pitch 10, so bars are 5 wide.
    private fun height(bytes: Long) = Bars.barHeight(bytes, max = 1000, width = 300, height = 120, count = 30)

    @Test
    fun zeroBytesHasNoBar() {
        assertEquals(0f, height(0), 0f)
    }

    @Test
    fun anyTrafficShowsARoundDotAsTallAsTheBarIsWide() {
        assertEquals(Bars.barWidth(300, 120, 30), height(1), 0f)
        assertEquals(5f, height(25), 0f)
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

    @Test
    fun barsInAShortChartAreNoWiderThanItIsTall() {
        assertEquals(4f, Bars.barWidth(300, 4, 30), 0f)
        assertEquals(4f, Bars.barHeight(1, max = 1000, width = 300, height = 4, count = 30), 0f)
    }
}
