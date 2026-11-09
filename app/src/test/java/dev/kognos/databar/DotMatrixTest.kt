package dev.kognos.databar

import org.junit.Assert.assertEquals
import org.junit.Test

class DotMatrixTest {

    @Test
    fun zeroBytesLightsNoDot() {
        assertEquals(0, DotMatrix.lit(0, 1000, 12))
    }

    @Test
    fun anyTrafficLightsAtLeastOneDot() {
        assertEquals(1, DotMatrix.lit(1, 1000, 12))
    }

    @Test
    fun largestDayLightsEveryRow() {
        assertEquals(12, DotMatrix.lit(1000, 1000, 12))
    }

    @Test
    fun otherDaysLightInProportionRounded() {
        assertEquals(6, DotMatrix.lit(500, 1000, 12))
        assertEquals(6, DotMatrix.lit(541, 1000, 12))
        assertEquals(7, DotMatrix.lit(542, 1000, 12))
    }
}
