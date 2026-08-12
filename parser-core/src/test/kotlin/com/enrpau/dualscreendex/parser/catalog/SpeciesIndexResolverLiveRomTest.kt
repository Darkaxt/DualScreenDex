package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import com.enrpau.dualscreendex.parser.parse.ParserOrchestrator
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in live-ROM regressions. These are deliberately not synthetic fixtures: the exact SHA-256
 * and decoded mapping values bind the assertions to the corpus evidence that exposed the bug.
 */
class SpeciesIndexResolverLiveRomTest {
    @Test
    fun altairUsesItsCompiledConsumedSpeciesPermutation() {
        assertLiveCompiledPermutation(
            environmentVariable = "DUALDEX_ALTAIR_ROM",
            expectedSha256 = "333e4fcbf2b8039ad1848a84d0f6826e790109ed150243f6cf7c9934b22ae380",
            expectedFirstDexValues = listOf(287, 204, 205),
        )
    }

    @Test
    fun blazingEmeraldUsesItsCompiledConsumedSpeciesPermutation() {
        assertLiveCompiledPermutation(
            environmentVariable = "DUALDEX_BLAZING_EMERALD_ROM",
            expectedSha256 = "2ff14043118132e9816fac3f20b3a85011b3e8ac5361a0499264dbebe4f096dc",
            expectedFirstDexValues = listOf(213, 214, 215),
        )
    }

    private fun assertLiveCompiledPermutation(
        environmentVariable: String,
        expectedSha256: String,
        expectedFirstDexValues: List<Int>,
    ) {
        val configuredPath = System.getenv(environmentVariable)
        assumeTrue("set $environmentVariable to run this live-ROM regression", !configuredPath.isNullOrBlank())
        val path = Path.of(requireNotNull(configuredPath))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals(expectedSha256, rom.sha256)

        val analysis = ParserOrchestrator.analyze(rom)
        assertEquals(SelectionStatus.SELECTED, analysis.status)
        assertEquals(EngineFamily.EMERALD, analysis.selectedFamily)
        val layout = analysis.probes.single { it.family == analysis.selectedFamily }.resolvedLayout
        val resolution = SpeciesIndexResolver.resolveWithEvidence(rom, requireNotNull(layout))

        assertTrue("expected a resolved compiled species permutation, got $resolution", resolution is SpeciesIndexResolution.Resolved)
        assertEquals(expectedFirstDexValues, (1..3).map(resolution.values::get))
        assertEquals((1..411).toSet(), (1..411).mapNotNull(resolution.values::get).toSet())
    }
}
