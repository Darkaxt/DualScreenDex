package com.enrpau.dualscreendex.parser.dataset.abilities

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.AbilityRecord
import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.resolution.DatasetResolution
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** SHA-bound authority for selected-layout-only ordinary Gen III ability names. */
class AbilityNameLiveRomTest {
    @Test fun offendersRestoreTheirExactSparseDirectAbilityCatalogs() {
        listOf(
            LiveAbilityCase(
                "DUALDEX_BLAZED_GLAZED_ROM",
                "0b55d44bfd32a350202c0878754cfcacbbaee128de3b59297ee669b69269199f",
                0x1280000, 256, 86, (1..85).toSet(),
                "838fed75d50212e0acb90fe5a12e6dca146923944c886f24141b540b7ca193f6",
                CapabilityStatus.AVAILABLE,
            ),
            LiveAbilityCase(
                "DUALDEX_BLAZING_EMERALD_ROM",
                "2ff14043118132e9816fac3f20b3a85011b3e8ac5361a0499264dbebe4f096dc",
                0xF62E1C, 178, 126, (1..125).toSet(),
                "ab76bbf742418f248a88a15141a27c73e8f2669a09b5da31608b90651ec29202",
                CapabilityStatus.AVAILABLE,
            ),
            LiveAbilityCase(
                "DUALDEX_DARK_RISING_ORDER_DESTROYED_ROM",
                "71b44f3b4be1b17428dd3fcb1c37002268c7b832dc49626b9d57bf56de10f387",
                0x950000, 156, 131, (1..130).toSet() - setOf(78, 118, 122),
                "ec19b14515fbb8f797367bc41a6ecfaa1a2a1b41cc5c2d9439f93c1642f3250b",
                CapabilityStatus.AVAILABLE,
            ),
            LiveAbilityCase(
                "DUALDEX_DARK_VIOLET_ROM",
                "6b7e6df19c974371a4f80ea5c0f1e8d68a2cfee248faf34080a48ae3f0135e21",
                0xA4F19C, 156, 127, (1..126).toSet() - setOf(78, 102, 116, 118, 119, 120, 121, 122, 123),
                "b288903ec5ad7a5a90a83216875c5162c2b127a04ba59c1050202fb03c5038ee",
                CapabilityStatus.AVAILABLE,
            ),
            LiveAbilityCase(
                "DUALDEX_DARK_VIOLET_FAN_PATCH_ROM",
                "d171d29b691ced98178b4370826f0627f9c2ed6e0313d813f909ba147031c717",
                0xA4F19C, 156, 127, (1..126).toSet() - setOf(78, 102, 116, 118, 119, 120, 121, 122, 123),
                "b288903ec5ad7a5a90a83216875c5162c2b127a04ba59c1050202fb03c5038ee",
                CapabilityStatus.AVAILABLE,
            ),
        ).forEach(::assertOffenderParity)
    }

    @Test fun altairRetainsItsSelectedAbilityCatalog() {
        assertParity(
            "DUALDEX_ALTAIR_ROM",
            "333e4fcbf2b8039ad1848a84d0f6826e790109ed150243f6cf7c9934b22ae380",
            0x31B6DB,
            78,
            77,
            "98549a63f7ae0f6a53264e503fb544eaed9f074d070cbda1c785f1a63ac2f264",
        )
    }

    @Test fun arcoirisRetainsItsFixedCatalogAndQuarantinesTheOverwrittenEcologyTail() {
        val parsed = assertParity(
            "DUALDEX_ARCOIRIS_ROM",
            "fe428c3a45747c9d1466506b5f6d9245e2faf7337660664b6ba3ee28a86ca4ab",
            0x1FA248,
            78,
            77,
            "8186d738b65512a52d946626398ec81b92b99d9140620d16d4170de5b6f30939",
        )
        (87..90).forEach { speciesId ->
            assertEquals(null, parsed.catalog.speciesById.getValue(speciesId).abilityIds.value)
        }
    }

    @Test fun crystalAdvanceExtendsThroughEveryValidReferencedAbilityName() {
        val rom = loadRom(
            "DUALDEX_CRYSTAL_ADVANCE_ROM",
            "fbbcbf32afd427afa5de45799923c414c21b77917004477f214c9f5cd87537b6",
        )
        val parsed = CatalogParser.parse(rom)
        val layout = requireNotNull(parsed.layout)
        val stats = requireNotNull(layout.tables.baseStats)
        val activeIds = buildSet {
            repeat(stats.count) { index ->
                val offset = stats.offset + index * (stats.stride ?: stats.recordSize)
                val validEcology = rom.u8(offset + 19) in 0..5 && rom.u8(offset + 20) in 0..15 &&
                    rom.u8(offset + 21) in 0..15 && (rom.u8(offset + 25) and 0x7F) in 0..13 &&
                    rom.u8(offset + 26) == 0 && rom.u8(offset + 27) == 0
                if (validEcology) {
                    add(rom.u8(offset + 22))
                    add(rom.u8(offset + 23))
                }
            }
            remove(0)
        }
        assertEquals(207, activeIds.maxOrNull())
        assertTrue(203 in activeIds && 207 in activeIds)
        val direct = AbilityNameResolver().resolve(
            RomAnalysisSession(rom, RomHeaderReader.read(rom)),
            AbilitySemanticDomain(activeIds),
            AbilityNameTableLayout(0xFC17A0, 190, 13),
        )
        val directLayout = when (direct) {
            is DatasetResolution.Resolved -> direct.candidate.layout
            is DatasetResolution.Partial -> direct.candidate.layout
            else -> error("direct selected Crystal ability resolution failed: $direct")
        }
        assertEquals(208L, directLayout.table.count)
        val finalTyped = requireNotNull(layout.resolvedDatasets.abilityNames)
        val capability = parsed.analysis.capabilities.single { it.capability == RomCapability.ABILITIES }
        println("CRYSTAL_ABILITY_DIAGNOSTIC domainMax=${activeIds.maxOrNull()} direct=${directLayout.table.count} " +
            "semantic=${finalTyped.table.count} reasons=${capability.reasons}")
        assertEquals("semantic phase must preserve the validated direct-ID domain", 208L, finalTyped.table.count)
        val catalog = requireNotNull(parsed.catalog)
        assertTrue(78 !in catalog.abilitiesById)
        assertEquals(listOf(19), catalog.speciesById.getValue(693).abilityIds.value)
        assertTrue(catalog.speciesById.values.none { 78 in it.abilityIds.value.orEmpty() })
        assertEquals("Slush Rush", catalog.abilitiesById.getValue(203).name.value)
        assertEquals("Galvanize", catalog.abilitiesById.getValue(207).name.value)
        assertEquals(0, missingAbilityReferences(catalog))
        println("CRYSTAL_ABILITY_SHA256 ${abilityNameSha256(catalog.abilitiesById.values)}")
    }

    @Test fun modernRetainsItsSelectedAbilityCatalog() {
        assertParity(
            "DUALDEX_MODERN_EMERALD_ROM",
            "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895",
            0x67F52C,
            82,
            81,
            "4fcfb1870bb47b1522c4c1a674d45afffded9fc25e72a455e3141ae462b64440",
        )
    }

    private fun assertParity(
        environmentVariable: String,
        expectedSha256: String,
        expectedRoot: Int,
        expectedPhysicalCount: Int,
        expectedAbilityCount: Int,
        expectedNameSha256: String,
    ): Parsed {
        val rom = loadRom(environmentVariable, expectedSha256)
        val parsed = CatalogParser.parse(rom)
        val layout = requireNotNull(parsed.layout)
        val catalog = requireNotNull(parsed.catalog)
        val selected = requireNotNull(layout.tables.abilities)
        val typed = requireNotNull(layout.resolvedDatasets.abilityNames)
        assertEquals(expectedRoot, selected.offset)
        assertEquals(expectedPhysicalCount, selected.count)
        assertEquals(expectedRoot.toLong(), typed.table.offset)
        assertEquals(expectedPhysicalCount.toLong(), typed.table.count)
        assertEquals(expectedAbilityCount, catalog.abilitiesById.keys.count { it > 0 })
        assertEquals((1..expectedAbilityCount).toSet(), catalog.abilitiesById.keys.filter { it > 0 }.toSet())
        assertEquals(expectedNameSha256, abilityNameSha256(catalog.abilitiesById.values.filter { it.id > 0 }))
        val capability = parsed.analysis.capabilities.single { it.capability == RomCapability.ABILITIES }
        assertEquals(CapabilityStatus.AVAILABLE, capability.status)
        assertTrue(capability.compatible)
        assertEquals(0, missingAbilityReferences(catalog))
        println("ABILITY_NAME_PARITY root=0x${expectedRoot.toString(16)} count=$expectedAbilityCount sha256=${abilityNameSha256(catalog.abilitiesById.values.filter { it.id > 0 })}")
        return Parsed(catalog)
    }

    private fun assertOffenderParity(case: LiveAbilityCase) {
        val rom = loadRom(case.environmentVariable, case.expectedSha256)
        val parsed = CatalogParser.parse(rom)
        val layout = requireNotNull(parsed.layout)
        val catalog = requireNotNull(parsed.catalog)
        val selected = requireNotNull(layout.tables.abilities)
        val typed = requireNotNull(layout.resolvedDatasets.abilityNames)
        val decodedIds = typed.decodedDirectAbilityIds()
        assertEquals(case.expectedRoot, selected.offset)
        assertEquals(case.expectedPhysicalCount, selected.count)
        assertEquals(case.expectedRoot.toLong(), typed.table.offset)
        assertEquals(case.expectedPhysicalCount.toLong(), typed.table.count)
        assertEquals(case.expectedBaseRowCount, typed.baseRowCount)
        assertEquals(case.expectedDecodedIds, decodedIds)
        assertTrue(decodedIds.all { it < typed.baseRowCount })
        assertEquals(case.expectedDecodedIds, catalog.abilitiesById.keys.filter { it > 0 }.toSet())
        assertEquals(
            case.expectedNameSha256,
            abilityNameSha256(catalog.abilitiesById.values.filter { it.id > 0 }),
        )
        val capability = parsed.analysis.capabilities.single { it.capability == RomCapability.ABILITIES }
        assertTrue(capability.compatible)
        assertEquals(case.expectedCapabilityStatus, capability.status)
        assertEquals(case.expectedBaseRowCount, capability.count)
        assertEquals(0, missingAbilityReferences(catalog))
        println(
            "ABILITY_SUFFIX_PARITY sha=${case.expectedSha256} root=0x${case.expectedRoot.toString(16)} " +
                "physical=${case.expectedPhysicalCount} base=${case.expectedBaseRowCount} " +
                "decoded=${decodedIds.size} last=${decodedIds.maxOrNull()} " +
                "hash=${case.expectedNameSha256} status=${capability.status}",
        )
    }

    private fun loadRom(environmentVariable: String, expectedSha256: String): RomImage {
        val configured = System.getenv(environmentVariable)
        assumeTrue("set $environmentVariable to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        return RomImage(Files.readAllBytes(path)).also { assertEquals(expectedSha256, it.sha256) }
    }

    private fun missingAbilityReferences(catalog: ParsedCatalog): Int = catalog.speciesById.values.sumOf { species ->
        species.abilityIds.value.orEmpty().count { it !in catalog.abilitiesById }
    }

    private fun abilityNameSha256(abilities: Collection<AbilityRecord>): String {
        val payload = abilities.sortedBy(AbilityRecord::id).joinToString("\n") { ability ->
            "${ability.id}|${ability.name.value}"
        }
        return MessageDigest.getInstance("SHA-256").digest(payload.toByteArray()).joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xFF)
        }
    }

    data class Parsed(val catalog: ParsedCatalog)

    private data class LiveAbilityCase(
        val environmentVariable: String,
        val expectedSha256: String,
        val expectedRoot: Int,
        val expectedPhysicalCount: Int,
        val expectedBaseRowCount: Int,
        val expectedDecodedIds: Set<Int>,
        val expectedNameSha256: String,
        val expectedCapabilityStatus: CapabilityStatus,
    )
}
