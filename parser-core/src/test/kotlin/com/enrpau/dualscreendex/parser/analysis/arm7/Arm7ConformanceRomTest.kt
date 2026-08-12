package com.enrpau.dualscreendex.parser.analysis.arm7

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Arm7ConformanceRomTest {
    @Test
    fun committedMitCpuRomsReachZeroVerdictDeterministically() {
        FIXTURES.forEach { resource ->
            val bytes = requireNotNull(javaClass.getResourceAsStream("/arm7/$resource")) { "missing $resource" }
                .use { it.readBytes() }
            val first = Arm7ConformanceRunner.run(bytes, Arm7ExecutionBudget(12_000_000))
            val second = Arm7ConformanceRunner.run(bytes, Arm7ExecutionBudget(12_000_000))

            assertTrue("$resource did not finish: ${first.canonicalSummary()}", first is Arm7ConformanceResult.Verdict)
            assertEquals(0L, (first as Arm7ConformanceResult.Verdict).value)
            assertEquals(first.canonicalSummary(), second.canonicalSummary())
        }
    }

    private companion object {
        val FIXTURES = listOf("arm.gba", "thumb.gba")
    }
}
