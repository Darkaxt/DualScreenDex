package com.enrpau.dualscreendex.parser.dataset.learnsets

import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.LearnsetEntry
import com.enrpau.dualscreendex.parser.io.RomImage
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Real-ROM authority for selected-layout-only ordinary Gen III level-up propagation. */
class LearnsetLiveRomTest {
    @Test fun modernRetainsTwoSelectorBoundPackedRulesets() = assertParity(
        environmentVariable = "DUALDEX_MODERN_EMERALD_ROM",
        expectedRomSha256 = "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895",
        expectedPrimaryRoot = 0x8EDB24,
        expectedFormat = "packed-u16-m9",
        expectedSpecies = 428,
        expectedPrimaryEntries = 4559,
        expectedSemanticSha256 = "473d008be04f988ad3138042b79fd4cf1b90d60541e4b16a10a34d09d8af87e5",
        expectedRulesetRoots = listOf(0x8ED3EC, 0x8EDB24),
        expectedSelector = Selector(0x3DA6, 2, 0x13C14),
        expectedRulesets = listOf(
            Ruleset("ruleset-008ed3ec", "Expanded 1", 0x8ED3EC, 6150,
                "899e734e3aacfba4787134c69d135cdd28f20c5c45bc4886e4ae2b6c12243664", 2),
            Ruleset("ruleset-008edb24", "Base", 0x8EDB24, 4638,
                "4bb0bc466d042d5b4f7e1c40b7f81d6068455cd7df81df8bae50781837367054", 0),
        ),
    )

    @Test fun classicRetainsTheWideSelectedAbiInsteadOfItsPackedAlias() = assertParity(
        environmentVariable = "DUALDEX_CLASSIC_ROM",
        expectedRomSha256 = "01c0177b2498e1842a1bf9ee2ddac145fb95275321bd3813dbf17341d63ad16c",
        expectedPrimaryRoot = 0x371098,
        expectedFormat = "move-u16-level-u16",
        expectedSpecies = 403,
        expectedPrimaryEntries = 6628,
        expectedSemanticSha256 = "2f531157ba917a6c164f9006148672ec64e1aac5089b61dd52e0a1bb6690fb0b",
        expectedRulesetRoots = listOf(0x371098),
        expectedRulesets = listOf(Ruleset("ruleset-00371098", "Default", 0x371098, 6667,
            "32e3319d0efeebb09839c0c102db79f04870f2f9aea7f49fbe234daa9fb67e32")),
    )

    @Test fun cawpsRetainsItsPackedSelectedAbi() = assertParity(
        environmentVariable = "DUALDEX_CAWPS_ROM",
        expectedRomSha256 = "88c2e3f60924a126b842f03817315c0525bc6dec71aa79bde57a7900c7e416d3",
        expectedPrimaryRoot = 0x32937C,
        expectedFormat = "packed-u16-m9",
        expectedSpecies = 386,
        expectedPrimaryEntries = 3947,
        expectedSemanticSha256 = "596c95154d1615d3d515847c2de07c1a442fac575347e0f51ca05c53a2cce5e0",
        expectedRulesetRoots = listOf(0x32937C),
        expectedRulesets = listOf(Ruleset("ruleset-0032937c", "Default", 0x32937C, 3983,
            "e2bed515dc88f52facff7e48b1e6e333d5c869d5016bdf0081c4d81938e23a54")),
    )

    @Test fun cloverRetainsItsLevelThenMoveThreeByteAbi() = assertParity(
        environmentVariable = "DUALDEX_CLOVER_ROM",
        expectedRomSha256 = "42f99abd548934d77999ac3eb563fb9bc70a34701d37a262b21b882a43a8bdd9",
        expectedPrimaryRoot = 0x25D7B4,
        expectedFormat = "level-u8-move-u16",
        expectedSpecies = 387,
        expectedPrimaryEntries = 8763,
        expectedSemanticSha256 = "0c28697e5da1c7a7221e48e2cbb1f9f0fe9380f267c22e870191a425dcdbe1fa",
        expectedRulesetRoots = listOf(0x25D7B4),
        expectedRulesets = listOf(Ruleset("ruleset-0025d7b4", "Default", 0x25D7B4, 8763,
            "3e42203ac4b458c9f9585d44437f31c1a528f73a983f1692df9603ae89ad3b73")),
    )

    @Test fun unboundRetainsItsMoveThenLevelThreeByteAbi() = assertParity(
        environmentVariable = "DUALDEX_UNBOUND_ROM",
        expectedRomSha256 = "7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7",
        expectedPrimaryRoot = 0x1A2457C,
        expectedFormat = "move-u16-level-u8",
        expectedSpecies = 1266,
        expectedPrimaryEntries = 21862,
        expectedSemanticSha256 = "d99630f41540fd6ca3141b592b575e55b2dd9a99d447030e1f9667c8f87e3101",
        expectedRulesetRoots = listOf(0x1A2457C),
        expectedRulesets = listOf(Ruleset("ruleset-01a2457c", "Default", 0x1A2457C, 21874,
            "147796cb131f8033e04f0ce938bc82635f7e59c2363664946f73c2da44db2c09")),
    )

    private fun assertParity(
        environmentVariable: String,
        expectedRomSha256: String,
        expectedPrimaryRoot: Int,
        expectedFormat: String,
        expectedSpecies: Int,
        expectedPrimaryEntries: Int,
        expectedSemanticSha256: String,
        expectedRulesetRoots: List<Int>,
        expectedSelector: Selector? = null,
        expectedRulesets: List<Ruleset>,
    ) {
        val configured = System.getenv(environmentVariable)
        assumeTrue("set $environmentVariable to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals(expectedRomSha256, rom.sha256)
        val parsed = CatalogParser.parse(rom)
        val layout = requireNotNull(parsed.layout)
        val catalog = requireNotNull(parsed.catalog)
        assertEquals(3, layout.generation)
        assertNull("expansion learnsets retain their established path", layout.pokeemeraldExpansion)
        val typed = requireNotNull(layout.resolvedDatasets.learnsets)
        val primary = requireNotNull(typed.primary)
        assertEquals(expectedPrimaryRoot.toLong(), primary.layout.table.offset)
        assertEquals(expectedFormat, primary.layout.table.format.stableId)
        assertEquals(expectedRulesetRoots.map(Int::toLong).toSet(), typed.tables.map { it.layout.table.offset }.toSet())

        val semantic = catalog.navigableSpecies().associate { species ->
            species.id to species.learnset.value.orEmpty()
        }
        val typedSemantic = typed.catalogPrimaryEntries().filterKeys(semantic::containsKey)
        assertEquals(typedSemantic, semantic)
        assertEquals(expectedSpecies, semantic.size)
        assertEquals(expectedPrimaryEntries, semantic.values.sumOf(List<*>::size))

        val rulesets = catalog.learnsetRulesets.sortedBy { it.sourceOffset }
        assertEquals(expectedRulesetRoots, rulesets.map { it.sourceOffset })
        if (rulesets.size == 1) {
            assertTrue(rulesets.single().primary)
            assertEquals("Default", rulesets.single().label)
            assertNull(rulesets.single().levelUpSelector)
        } else {
            assertTrue("selector-bound alternatives must not invent an Auto primary", rulesets.none { it.primary })
            assertEquals(setOf("Base", "Expanded 1"), rulesets.map { it.label }.toSet())
            assertTrue(rulesets.all { it.levelUpSelector != null })
        }
        if (expectedSelector == null) {
            assertNull(typed.selector)
        } else {
            val selector = requireNotNull(typed.selector)
            assertEquals(expectedSelector.saveByteOffset, selector.saveBlock1ByteOffset)
            assertEquals(expectedSelector.mask, selector.mask)
            assertEquals(expectedSelector.codeOffset, selector.codeOffset)
        }

        val semanticHash = learnsetSha256(semantic)
        assertEquals(expectedSemanticSha256, semanticHash)
        assertEquals(expectedRulesets.size, rulesets.size)
        expectedRulesets.zip(rulesets).forEach { (expected, actual) ->
            assertEquals(expected.id, actual.id)
            assertEquals(expected.label, actual.label)
            assertEquals(expected.root, actual.sourceOffset)
            assertEquals(expected.entries, actual.entriesBySpecies.values.sumOf(List<*>::size))
            assertEquals(expected.sha256, learnsetSha256(actual.entriesBySpecies))
            assertEquals(expected.expectedSelectorValue, actual.levelUpSelector?.expectedValue)
        }
        val rulesetEvidence = rulesets.joinToString(";") { ruleset ->
            val selector = ruleset.levelUpSelector
            "${ruleset.id}|${ruleset.label}|0x${ruleset.sourceOffset.toString(16)}|" +
                "${ruleset.entriesBySpecies.values.sumOf(List<*>::size)}|${learnsetSha256(ruleset.entriesBySpecies)}|" +
                "selector=${selector?.saveBlock1ByteOffset},${selector?.mask},${selector?.expectedValue}"
        }
        assertFalse(semanticHash.isBlank())
        println(
            "LEARNSET_CODEC_PARITY $environmentVariable species=${semantic.size} entries=$expectedPrimaryEntries " +
                "sha256=$semanticHash primary=0x${expectedPrimaryRoot.toString(16)} format=$expectedFormat " +
                "rulesets=[$rulesetEvidence]",
        )
    }

    private fun learnsetSha256(values: Map<Int, List<LearnsetEntry>>): String {
        val bytes = values.toSortedMap().entries.joinToString("\u001e") { (id, entries) ->
            "$id\u001f" + entries.joinToString("\u001d") { entry -> "${entry.level},${entry.moveId}" }
        }.toByteArray()
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xFF)
        }
    }

    private data class Selector(val saveByteOffset: Int, val mask: Int, val codeOffset: Int)
    private data class Ruleset(
        val id: String,
        val label: String,
        val root: Int,
        val entries: Int,
        val sha256: String,
        val expectedSelectorValue: Int? = null,
    )
}
