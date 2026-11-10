package dev.kognos.databar

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageRepoFmtTest {

    @Test
    fun ratioUsesADecimalCommaLikeTheByteAmounts() {
        assertEquals("5,5\u00d7", UsageRepo.fmtRatio(5.5f))
        assertEquals("3,0\u00d7", UsageRepo.fmtRatio(3f))
        assertEquals("1,24 GB", UsageRepo.fmt(1_331_439_861L))
    }
}
