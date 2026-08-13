package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in regressions bound to the real corpus ROMs that exposed cross-catalog reference failures. */
class CatalogReferenceLiveRomTest {
    @Test
    fun crystalAdvanceMaterializesReferencedAbilityTargetsPastTheInferredNamePrefix() {
        val catalog = parseLiveRom(
            environmentVariable = "DUALDEX_CRYSTAL_ADVANCE_ROM",
            expectedSha256 = "fbbcbf32afd427afa5de45799923c414c21b77917004477f214c9f5cd87537b6",
        )

        assertEquals(listOf(109, 203), catalog.speciesById.getValue(567).abilityIds.value)
        assertEquals(listOf(42, 207), catalog.speciesById.getValue(577).abilityIds.value)
        assertEquals("Slush Rush", catalog.abilitiesById.getValue(203).name.value)
        assertEquals("Galvanize", catalog.abilitiesById.getValue(207).name.value)
    }

    @Test
    fun deltaEmeraldKeepsNamedMoveIdentitiesWhenMoveDetailsAreUnresolved() {
        val catalog = parseLiveRom(
            environmentVariable = "DUALDEX_DELTA_EMERALD_ROM",
            expectedSha256 = "7f4aa1aa68b1df783c3a44b38984640227a5eec22debffbf18db3713de2616bc",
        )

        assertEquals(719, catalog.movesById.size)
        assertEquals("Tearful Look", catalog.movesById.getValue(715).name.value)
        assertNull(catalog.movesById.getValue(715).typeId.value)
        assertEquals(0, catalog.speciesById.values.sumOf { species ->
            species.learnset.value.orEmpty().count { it.moveId !in catalog.movesById }
        })
    }

    @Test
    fun arcoirisQuarantinesAbilitySlotsFromTextOverwrittenBaseStatRows() {
        val catalog = parseLiveRom(
            environmentVariable = "DUALDEX_ARCOIRIS_ROM",
            expectedSha256 = "fe428c3a45747c9d1466506b5f6d9245e2faf7337660664b6ba3ee28a86ca4ab",
        )

        assertEquals(listOf(47), catalog.speciesById.getValue(86).abilityIds.value)
        (87..90).forEach { speciesId ->
            assertNull("species $speciesId", catalog.speciesById.getValue(speciesId).abilityIds.value)
            assertNull("species $speciesId growth", catalog.speciesById.getValue(speciesId).growthRate.value)
        }
        assertEquals(listOf(26), catalog.speciesById.getValue(92).abilityIds.value)
    }

    @Test
    fun dreamsClosesMachineMoveReferencesWithoutPromotingUnresolvedNames() {
        val configuredPath = System.getenv("DUALDEX_DREAMS_ROM")
        assumeTrue("set DUALDEX_DREAMS_ROM to run this live-ROM regression", !configuredPath.isNullOrBlank())
        val path = Path.of(requireNotNull(configuredPath))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals("ad73b864873f17add4f931315d3162b792b19c65133c7a6819a85866b1afa403", rom.sha256)

        val parsed = CatalogParser.parse(rom)
        val catalog = requireNotNull(parsed.catalog)
        val referenced = catalog.speciesById.values.flatMap { species ->
            species.moveAcquisitions.value.orEmpty().map(MoveAcquisition::moveId)
        }.filter { it > 0 }.toSet()
        val moveCatalog = parsed.analysis.capabilities.single { it.capability == RomCapability.MOVE_CATALOG }
        val moveDetails = parsed.analysis.capabilities.single { it.capability == RomCapability.MOVE_DETAILS }

        assertTrue(referenced.isNotEmpty())
        assertTrue(referenced.all(catalog.movesById::containsKey))
        assertTrue(referenced.any { catalog.movesById.getValue(it).name.value == null })
        assertEquals(CapabilityStatus.NOT_FOUND, moveCatalog.status)
        assertFalse(moveCatalog.compatible)
        assertEquals(CapabilityStatus.AVAILABLE, moveDetails.status)
    }

    private fun parseLiveRom(environmentVariable: String, expectedSha256: String): ParsedCatalog {
        val configuredPath = System.getenv(environmentVariable)
        assumeTrue("set $environmentVariable to run this live-ROM regression", !configuredPath.isNullOrBlank())
        val path = Path.of(requireNotNull(configuredPath))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals(expectedSha256, rom.sha256)
        val parsed = CatalogParser.parse(rom)
        assertNotNull(parsed.catalog)
        return requireNotNull(parsed.catalog)
    }
}
