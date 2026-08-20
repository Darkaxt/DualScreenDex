package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class OfficialGen12CompletionLiveRomTest {
    @Test
    fun redCompletesTheDetachedMewRecordAndAllDexDescriptions() = assertGen1(
        "DUALDEX_POKERED_ROM",
        "5ca7ba01642a3b27b0cc0b5349b52792795b62d3ed977e98a09390659af96b7b",
        EngineFamily.RED_BLUE,
    )

    @Test
    fun blueCompletesTheDetachedMewRecordAndAllDexDescriptions() = assertGen1(
        "DUALDEX_POKEBLUE_ROM",
        "2a951313c2640e8c2cb21f25d1db019ae6245d9c7121f754fa61afd7bee6452d",
        EngineFamily.RED_BLUE,
    )

    @Test
    fun yellowPreservesItsCompleteContiguousSpeciesTable() = assertGen1(
        "DUALDEX_POKEYELLOW_ROM",
        "8cbaa499397e4f1a679c992ea9382a2dd7942ab398b48c19829c2d9529de47bf",
        EngineFamily.YELLOW,
    )

    @Test
    fun goldDecodesEveryMoveDescription() = assertGen2(
        "DUALDEX_POKEGOLD_ROM",
        "fb0016d27b1e5374e1ec9fcad60e6628d8646103b5313ca683417f52b97e7e4e",
        EngineFamily.GOLD_SILVER,
        0x1B4000,
    )

    @Test
    fun silverDecodesEveryMoveDescription() = assertGen2(
        "DUALDEX_POKESILVER_ROM",
        "72b190859a59623cbef6c49d601f8de52c1d2331b4f08a8d2acc17274fc19a8c",
        EngineFamily.GOLD_SILVER,
        0x1B4000,
    )

    @Test
    fun crystalRevOneDecodesEveryMoveDescription() = assertGen2(
        "DUALDEX_POKECRYSTAL_ROM",
        "fdcc3c8c43813cf8731fc037d2a6d191bac75439c34b24ba1c27526e6acdc8a2",
        EngineFamily.CRYSTAL,
        0x2CB52,
    )

    private fun assertGen1(environment: String, sha256: String, family: EngineFamily) {
        val catalog = parse(environment, sha256, family)
        val mew = catalog.speciesById.values.single { it.dexNumber.value == 151 }
        val stats = assertNotNull(mew.baseStats.value).let { requireNotNull(mew.baseStats.value) }
        assertEquals(listOf(100, 100, 100, 100, 100, 100), listOf(
            stats.hp,
            stats.attack,
            stats.defense,
            stats.speed,
            stats.specialAttack,
            stats.specialDefense,
        ))
        assertNotNull(mew.sprite.value)
        assertEquals(151, catalog.navigableSpecies().size)
        assertCapability(catalog.capabilities.getValue(RomCapability.SPECIES_CATALOG), 151)
        assertCapability(catalog.capabilities.getValue(RomCapability.BASE_STATS), 151)
        assertCapability(catalog.capabilities.getValue(RomCapability.SPRITES), 151)
        assertCapability(catalog.capabilities.getValue(RomCapability.POKEDEX_DESCRIPTIONS), 151)
        assertCapability(catalog.capabilities.getValue(RomCapability.MOVE_CATALOG), 165)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.MACHINE_MOVES).status)
        assertCapability(catalog.capabilities.getValue(RomCapability.EVOLUTIONS), 190)
        assertCapability(catalog.capabilities.getValue(RomCapability.LEARNSETS), 190)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.TYPE_CHART).status)
        assertTrue(catalog.typeChart.isNotEmpty())
        assertTrue(catalog.speciesById.values.any { it.evolutionEdges.value?.isNotEmpty() == true })
        assertTrue(catalog.speciesById.values.any { it.learnset.value?.isNotEmpty() == true })
    }

    private fun assertGen2(environment: String, sha256: String, family: EngineFamily, tableOffset: Int) {
        val catalog = parse(environment, sha256, family)
        val evidence = catalog.capabilities.getValue(RomCapability.MOVE_DESCRIPTIONS)
        assertEquals(CapabilityStatus.AVAILABLE, evidence.status)
        assertEquals(tableOffset, evidence.offset)
        assertEquals(251, evidence.count)
        assertEquals(251, catalog.movesById.values.count { it.effectText.status == CapabilityStatus.AVAILABLE })
        assertEquals("Pounds with fore- legs or tail.", catalog.movesById.getValue(1).effectText.value)
        assertEquals("Used only if all PP are exhausted.", catalog.movesById.getValue(165).effectText.value)
        assertEquals("Party MON join in the attack.", catalog.movesById.getValue(251).effectText.value)
    }

    private fun parse(environment: String, sha256: String, family: EngineFamily): ParsedCatalog {
        val configured = System.getenv(environment)
        assumeTrue("set $environment to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals(sha256, rom.sha256)
        val parsed = CatalogParser.parse(rom)
        assertEquals(SelectionStatus.SELECTED, parsed.analysis.status)
        assertEquals(family, parsed.analysis.selectedFamily)
        assertNotNull(parsed.catalog)
        return requireNotNull(parsed.catalog)
    }

    private fun assertCapability(evidence: CapabilityEvidence, expected: Int) {
        assertEquals(CapabilityStatus.AVAILABLE, evidence.status)
        assertEquals(expected, evidence.coveredRecords)
        assertEquals(expected, evidence.expectedRecords)
        assertTrue(evidence.compatible)
    }
}
