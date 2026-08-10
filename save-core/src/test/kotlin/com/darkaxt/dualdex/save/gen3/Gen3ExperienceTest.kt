package com.darkaxt.dualdex.save.gen3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Gen3ExperienceTest {
    @Test
    fun resolvesEveryOfficialGrowthCurveAtExactBoundaries() {
        for (rate in 0..5) {
            val experience = Gen3Experience.required(rate, 50)
            assertEquals("growth rate $rate", 50, Gen3Experience.level(rate, experience))
            assertEquals("growth rate $rate below boundary", 49, Gen3Experience.level(rate, experience - 1))
        }
    }

    @Test
    fun rejectsUnknownGrowthRates() {
        assertNull(Gen3Experience.level(9, 1000))
    }
}
