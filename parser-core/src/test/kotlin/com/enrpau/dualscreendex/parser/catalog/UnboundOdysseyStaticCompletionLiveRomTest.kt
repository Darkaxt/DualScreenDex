package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class UnboundOdysseyStaticCompletionLiveRomTest {
    @Test fun unboundCompletesEveryMoveDescription() {
        val rom = configuredRom(
            "DUALDEX_UNBOUND_ROM",
            "7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7",
        )
        val parsed = CatalogParser.parse(rom)
        val catalog = requireNotNull(parsed.catalog)
        val moveIds = catalog.movesById.keys.filter { it > 0 }.toSortedSet()
        val describedIds = catalog.movesById.values
            .filter { it.id > 0 && it.effectText.status == CapabilityStatus.AVAILABLE }
            .mapTo(sortedSetOf()) { it.id }
        val missingIds = moveIds - describedIds
        val capability = catalog.capabilities.getValue(RomCapability.MOVE_DESCRIPTIONS)
        val session = RomAnalysisSession(rom, RomHeaderReader.read(rom))
        val selected = requireNotNull(parsed.layout?.tables?.moveData)
        val descriptions = decodeMoveDescriptions(rom, 0x99F194, 922)
        val references = requireNotNull(session.gbaReferenceIndex?.target(0x99F194))
        assertEquals((1..922).toSet(), moveIds)
        assertEquals(CapabilityStatus.AVAILABLE, capability.status)
        assertEquals(922, describedIds.size)
        assertEquals(emptySet<Int>(), missingIds)
        assertEquals(0x99F194, capability.offset)
        assertEquals(null, session.gbaReferenceIndex?.overflowReason)
        assertEquals(3, references.count)
        assertEquals(true, references.siteEvidenceAvailable)
        assertEquals(922, descriptions.size)
        assertEquals("A physical attack delivered with a long tail or a foreleg, etc.", descriptions.getValue(1))
        assertEquals((769..819).associateWith { "-" }, descriptions.filterKeys { it in 769..819 })
        assertEquals(
            "Enables the user to evade all attacks. It may fail if used in succession.",
            descriptions.getValue(820),
        )
        assertEquals("Normal-Type Dynamax move. It lowers the target's Speed stat.", descriptions.getValue(821))
        assertEquals(923, selected.count)
    }

    @Test fun odysseyCompletesEveryActiveSpeciesDescription() {
        val rom = configuredRom(
            "DUALDEX_ODYSSEY_ROM",
            "44c7e3eafab19c39df7c39d54bafb78a1d9caf7c371244b6f5efb12cfd98d0d0",
        )
        val parsed = CatalogParser.parse(rom)
        val catalog = requireNotNull(parsed.catalog)
        val pokedexSpecies = catalog.navigableSpecies().sortedBy { it.id }
        val describedIds = pokedexSpecies
            .filter { it.description.status == CapabilityStatus.AVAILABLE }
            .mapTo(sortedSetOf()) { it.id }
        val missingIds = pokedexSpecies.mapTo(sortedSetOf()) { it.id } - describedIds
        val capability = catalog.capabilities.getValue(RomCapability.POKEDEX_DESCRIPTIONS)
        val layout = requireNotNull(parsed.layout)
        val session = RomAnalysisSession(rom, RomHeaderReader.read(rom))
        val descriptionReferences = requireNotNull(session.gbaReferenceIndex?.target(0x120C980))
        val rawDexMap = SpeciesIndexResolver.resolveWithEvidence(rom, layout).values
        val abyssEye = catalog.speciesById.getValue(275)
        val tentacle = catalog.speciesById.getValue(276)
        val speciesCount = requireNotNull(layout.tables.speciesNames).count
        val encodedDexMap = ByteArray((speciesCount - 1) * 2).also { encoded ->
            (1 until speciesCount).forEach { id ->
                val dex = rawDexMap.getValue(id)
                encoded[(id - 1) * 2] = dex.toByte()
                encoded[(id - 1) * 2 + 1] = (dex ushr 8).toByte()
            }
        }

        assertEquals(null, layout.compiledGbaReferences?.overflowReason)
        assertEquals(8, layout.compiledGbaReferences?.counts?.get(0x120C980))
        assertEquals(2, layout.compiledGbaReferences?.counts?.get(0x251CB8))
        assertEquals(listOf(0x251CB8), rom.findAll(encodedDexMap))
        assertEquals(410, rawDexMap.getValue(275))
        assertEquals(411, rawDexMap.getValue(276))
        assertEquals(409, pokedexSpecies.size)
        assertEquals(CapabilityStatus.AVAILABLE, capability.status)
        assertEquals(409, capability.expectedRecords)
        assertEquals(409, capability.coveredRecords)
        assertEquals(409, describedIds.size)
        assertEquals(emptySet<Int>(), missingIds)
        assertEquals(0x120C980, layout.tables.descriptions?.offset)
        assertEquals(410, layout.tables.descriptions?.count)
        assertEquals(8, descriptionReferences.count)
        assertEquals(true, descriptionReferences.siteEvidenceAvailable)
        assertBattleOnlySpecies(abyssEye, "Abyss Eye")
        assertBattleOnlySpecies(tentacle, "Tentacle")
    }

    private fun assertBattleOnlySpecies(species: SpeciesRecord, expectedName: String) {
        assertEquals(expectedName, species.name.value)
        assertEquals(CapabilityStatus.NOT_APPLICABLE, species.dexNumber.status)
        assertEquals(CapabilityStatus.AVAILABLE, species.typeIds.status)
        assertEquals(CapabilityStatus.AVAILABLE, species.baseStats.status)
        assertEquals(CapabilityStatus.AVAILABLE, species.sprite.status)
        assertEquals(CapabilityStatus.NOT_APPLICABLE, species.description.status)
        assertEquals(CapabilityStatus.NOT_APPLICABLE, species.height.status)
        assertEquals(CapabilityStatus.NOT_APPLICABLE, species.weight.status)
    }

    private fun configuredRom(environmentVariable: String, expectedSha256: String): RomImage {
        val configured = System.getenv(environmentVariable)
        assumeTrue("set $environmentVariable to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        return RomImage(Files.readAllBytes(path)).also { rom ->
            assertEquals(expectedSha256, rom.sha256)
        }
    }

    private fun decodeMoveDescriptions(rom: RomImage, root: Int, count: Int): Map<Int, String> = buildMap {
        repeat(count) { index ->
            val target = rom.gbaPointer(root + index * 4) ?: return@repeat
            val length = minOf(256, rom.size - target)
            if (length <= 0) return@repeat
            val decoded = PokemonTextCodec.gbaEnglish.decodeDetailed(rom.slice(target, length))
            val normalized = decoded.text.replace(Regex("\\s+"), " ").trim()
            if (decoded.terminated && decoded.validRatio >= 0.85) put(index + 1, normalized)
        }
    }
}
