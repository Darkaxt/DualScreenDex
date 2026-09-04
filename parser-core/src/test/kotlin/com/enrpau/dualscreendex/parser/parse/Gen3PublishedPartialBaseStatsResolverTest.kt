package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.RecordMaterializers
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityReviewStatus
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.validate.TableValidators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3PublishedPartialBaseStatsResolverTest {
    @Test
    fun retainsUniquelyPublishedReferencedZeroGappedStatsForAuthoritativeSemanticCoverage() {
        val fixture = fixture(activeStatCount = 353, statsReferenceCount = 3)

        val probe = probe(fixture.bytes)
        val layout = requireNotNull(probe.resolvedLayout)
        val semantic = ParserOrchestrator.applySpeciesSemanticDomain(RomImage(fixture.bytes), layout, probe.capabilities)
        val stats = semantic.single { it.capability == RomCapability.BASE_STATS }
        val speciesCatalog = semantic.single { it.capability == RomCapability.SPECIES_CATALOG }

        assertNotNull(layout.tables.baseStats)
        assertTrue(stats.compatible)
        assertEquals(CapabilityStatus.PARTIAL, stats.status)
        assertEquals(CapabilityReviewStatus.MANUAL_REVIEW, stats.reviewStatus)
        assertEquals(353, stats.validRecords)
        assertEquals(412, stats.totalRecords)
        assertEquals(353, stats.coveredRecords)
        assertEquals(383, stats.expectedRecords)
        assertEquals(30, stats.incompleteRecords)
        assertTrue(stats.reasons.any { it.contains("published Gen 3 base-stat root") })
        assertTrue(stats.reasons.any { it.contains("authoritative semantic species coverage 353/383") })
        assertEquals(1, stats.reasons.count { it.contains("compiled-referenced species-to-Dex map") })
        assertEquals(CapabilityStatus.PARTIAL, speciesCatalog.status)
        assertFalse(speciesCatalog.status == CapabilityStatus.AVAILABLE)

        val species = RecordMaterializers.species(RomImage(fixture.bytes), layout)
        assertNotNull(species.getValue(1).baseStats.value)
        assertEquals(listOf(12, 3), species.getValue(1).typeIds.value)
        assertNull(species.getValue(354).baseStats.value)
        assertEquals(28, species.values.count { it.id > 0 && it.dexNumber.value == 0 })
        assertEquals(0, species.getValue(384).dexNumber.value)
    }

    @Test
    fun selectsTheOnlyQualifying32BytePublishedPartialStatsLayout() {
        val fixture = fixture(activeStatCount = 353, statsReferenceCount = 3, recordSize = 32)
        val raw32 = TableValidators.baseStats(
            RomImage(fixture.bytes), fixture.statsOffset, SPECIES_COUNT, 32, generation = 3,
        )

        assertFalse(raw32.compatible)
        assertEquals(353, raw32.validRecords)

        val probe = probe(fixture.bytes)
        val stats = probe.capabilities.single { it.capability == RomCapability.BASE_STATS }

        assertEquals(32, probe.resolvedLayout?.tables?.baseStats?.recordSize)
        assertTrue(stats.compatible)
        assertEquals(CapabilityStatus.PARTIAL, stats.status)
        assertEquals(353, stats.validRecords)
        assertEquals(383, stats.expectedRecords)

        val species = RecordMaterializers.species(RomImage(fixture.bytes), requireNotNull(probe.resolvedLayout))
        assertNotNull(species.getValue(1).baseStats.value)
        assertEquals(listOf(12, 3), species.getValue(1).typeIds.value)
    }

    @Test
    fun recovered32ByteStatsDriveExpandedTypeChartSelection() {
        val fixture = downstream32Fixture()
        val rom = RomImage(fixture.bytes)

        assertNull(TableValidators.inferGen3ActiveTypeCount(rom, TableLayout(fixture.statsOffset, SPECIES_COUNT, 28), SPECIES_COUNT))
        assertEquals(20, TableValidators.inferGen3ActiveTypeCount(rom, TableLayout(fixture.statsOffset, SPECIES_COUNT, 32), SPECIES_COUNT))

        val probe = probe(fixture.bytes)
        val typeChart = probe.capabilities.single { it.capability == RomCapability.TYPE_CHART }

        assertEquals(32, probe.resolvedLayout?.tables?.baseStats?.recordSize)
        assertEquals(CapabilityStatus.AVAILABLE, typeChart.status)
        assertEquals(TYPE_CHART_20_OFFSET, typeChart.offset)
        assertEquals(400, typeChart.totalRecords)
        assertEquals(4, typeChart.elementSize)
    }

    @Test
    fun recovered32ByteStatsDriveThreeU16SlotAbilityBoundaryScan() {
        val fixture = downstream32Fixture()
        val rom = RomImage(fixture.bytes)

        assertTrue(
            requireNotNull(maximumDirectAbilityId(rom, TableLayout(fixture.statsOffset, SPECIES_COUNT, 28), SPECIES_COUNT)) <
                ABILITY_BOUNDARY_INDEX,
        )
        assertEquals(260, maximumDirectAbilityId(rom, TableLayout(fixture.statsOffset, SPECIES_COUNT, 32), SPECIES_COUNT))

        val probe = probe(fixture.bytes)
        val abilities = probe.capabilities.single { it.capability == RomCapability.ABILITIES }

        assertEquals(32, probe.resolvedLayout?.tables?.baseStats?.recordSize)
        assertEquals(CapabilityStatus.AVAILABLE, abilities.status)
        assertEquals(ABILITY_NAME_COUNT, abilities.totalRecords)
        assertFalse(abilities.reasons.any { it.contains("bounded direct ability IDs") })
    }

    @Test
    fun rejectsPublishedPartialStatsWhenBoth28And32ByteLayoutsQualify() {
        val fixture = fixture(activeStatCount = 0, statsReferenceCount = 3)
        repeat(336) { index ->
            putOverlapSafeValidStats(fixture.bytes, fixture.statsOffset + index * 28)
        }
        repeat(294) { index ->
            putOverlapSafeValidStats(fixture.bytes, fixture.statsOffset + index * 32)
        }
        val rom = RomImage(fixture.bytes)
        val raw28 = TableValidators.baseStats(rom, fixture.statsOffset, SPECIES_COUNT, 28, generation = 3)
        val raw32 = TableValidators.baseStats(rom, fixture.statsOffset, SPECIES_COUNT, 32, generation = 3)

        assertFalse(raw28.compatible)
        assertFalse(raw32.compatible)
        assertEquals(336, raw28.validRecords)
        assertEquals(294, raw32.validRecords)

        val probe = probe(fixture.bytes)

        assertNull(probe.resolvedLayout?.tables?.baseStats)
        assertEquals(
            CapabilityStatus.NOT_FOUND,
            probe.capabilities.single { it.capability == RomCapability.BASE_STATS }.status,
        )
    }

    @Test
    fun rejectsPublishedPartialStatsWithoutCompiledReferences() {
        val probe = probe(fixture(activeStatCount = 353, statsReferenceCount = 0).bytes)

        assertNull(probe.resolvedLayout?.tables?.baseStats)
        assertEquals(
            CapabilityStatus.NOT_FOUND,
            probe.capabilities.single { it.capability == RomCapability.BASE_STATS }.status,
        )
    }

    @Test
    fun rejectsAmbiguousPublishedPointerWindows() {
        val fixture = fixture(activeStatCount = 353, statsReferenceCount = 3)
        repeat(11) { index -> putPointer(fixture.bytes, 0x1AC + index * 4, 0x12000 + index * 0x100) }

        val probe = probe(fixture.bytes)

        assertEquals(GbaPublishedDataState.AMBIGUOUS, GbaPublishedHeaderResolver.resolve(RomImage(fixture.bytes), PokemonTextCodec.gbaEnglish).publishedDataState)
        assertNull(probe.resolvedLayout?.tables?.baseStats)
    }

    @Test
    fun activeZeroRowsRemainIncompleteInsteadOfBecomingAvailable() {
        val fixture = fixture(activeStatCount = 382, statsReferenceCount = 3)

        val probe = probe(fixture.bytes)
        val layout = requireNotNull(probe.resolvedLayout)
        val stats = ParserOrchestrator.applySpeciesSemanticDomain(RomImage(fixture.bytes), layout, probe.capabilities)
            .single { it.capability == RomCapability.BASE_STATS }

        assertEquals(CapabilityStatus.PARTIAL, stats.status)
        assertEquals(382, stats.coveredRecords)
        assertEquals(383, stats.expectedRecords)
        assertEquals(1, stats.incompleteRecords)
        assertEquals(CapabilityReviewStatus.MANUAL_REVIEW, stats.reviewStatus)
    }

    @Test
    fun rejectsPublishedPartialStatsJustBelowTheRawCoverageFloor() {
        val probe = probe(fixture(activeStatCount = 288, statsReferenceCount = 3).bytes)

        assertNull(probe.resolvedLayout?.tables?.baseStats)
        assertEquals(
            CapabilityStatus.NOT_FOUND,
            probe.capabilities.single { it.capability == RomCapability.BASE_STATS }.status,
        )
    }

    @Test
    fun rejectsStatsWhoseRawCoverageIsInflatedByRowsOutsideTheAuthoritativeDomain() {
        val fixture = fixture(activeStatCount = 267, statsReferenceCount = 3)
        (384 until SPECIES_COUNT).forEach { id ->
            putValidStats(fixture.bytes, fixture.statsOffset + id * BASE_STAT_SIZE)
        }

        val probe = probe(fixture.bytes)

        assertNull(probe.resolvedLayout?.tables?.baseStats)
    }

    @Test
    fun rejectsNonzeroMalformedRowsEvenWhenAggregateCoverageIsHigh() {
        val fixture = fixture(activeStatCount = 353, statsReferenceCount = 3)
        fixture.bytes[fixture.statsOffset + 354 * BASE_STAT_SIZE + 8] = 1

        val probe = probe(fixture.bytes)

        assertNull(probe.resolvedLayout?.tables?.baseStats)
    }

    @Test
    fun rejectsPublishedStatsThatDoNotFitTheRom() {
        val fixture = fixture(activeStatCount = 353, statsReferenceCount = 3)
        putPointer(fixture.bytes, STANDARD_DATA_ROOT, fixture.bytes.size - 4)

        val probe = probe(fixture.bytes)

        assertNull(probe.resolvedLayout?.tables?.baseStats)
    }

    private fun probe(bytes: ByteArray) = FamilyParsers.all
        .single { it.family == EngineFamily.FIRERED_LEAFGREEN }
        .probe(RomImage(bytes), RomHeader(Platform.GBA, "POKEMON FIRE", "BPRE"))

    private fun downstream32Fixture(): Fixture {
        val fixture = fixture(activeStatCount = 353, statsReferenceCount = 3, recordSize = 32)
        repeat(353) { index ->
            val record = fixture.statsOffset + (index + 1) * 32
            fixture.bytes[record + 6] = 19
            fixture.bytes[record + 7] = 19
        }
        putU16(fixture.bytes, fixture.statsOffset + 32 + 22, 65)
        putU16(fixture.bytes, fixture.statsOffset + 32 + 24, 145)
        putU16(fixture.bytes, fixture.statsOffset + 32 + 26, 260)

        repeat(ABILITY_NAME_COUNT) { index ->
            val value = when (index) {
                0, ABILITY_BOUNDARY_INDEX -> "-"
                in 1 until ABILITY_BOUNDARY_INDEX -> "ABILITY"
                else -> "RUNTIME"
            }
            putAbilityName(fixture.bytes, ABILITY_NAMES_OFFSET + index * 13, value)
        }

        writePlausibleQ412Matrix(fixture.bytes, TYPE_CHART_18_OFFSET, 18)
        writePlausibleQ412Matrix(fixture.bytes, TYPE_CHART_20_OFFSET, 20)
        putPointer(fixture.bytes, TYPE_CHART_POINTERS_OFFSET, TYPE_CHART_18_OFFSET)
        putPointer(fixture.bytes, TYPE_CHART_POINTERS_OFFSET + 4, TYPE_CHART_18_OFFSET + 18 * 18 * 4)
        putPointer(fixture.bytes, TYPE_CHART_POINTERS_OFFSET + 8, TYPE_CHART_20_OFFSET)
        putPointer(fixture.bytes, TYPE_CHART_POINTERS_OFFSET + 12, TYPE_CHART_20_OFFSET + 20 * 20 * 4)
        return fixture
    }

    private fun fixture(activeStatCount: Int, statsReferenceCount: Int, recordSize: Int = 28): Fixture {
        require(activeStatCount in 0..ACTIVE_SPECIES_COUNT)
        require(recordSize == 28 || recordSize == 32)
        val bytes = ByteArray(ROM_SIZE)
        GBA_LOGO_PREFIX.copyInto(bytes, 0x04)
        "POKEMON FIRE".toByteArray().copyInto(bytes, 0xA0)
        "BPRE".toByteArray().copyInto(bytes, 0xAC)
        putPointer(bytes, SPECIES_NAMES_SLOT, SPECIES_NAMES_OFFSET)
        putPointer(bytes, MOVE_NAMES_SLOT, MOVE_NAMES_OFFSET)
        listOf(
            STATS_OFFSET,
            ABILITY_NAMES_OFFSET,
            ABILITY_DESCRIPTIONS_OFFSET,
            ITEM_DATA_OFFSET,
            MOVE_DATA_OFFSET,
            BALL_GRAPHICS_OFFSET,
            BALL_PALETTES_OFFSET,
        ).forEachIndexed { index, target -> putPointer(bytes, STANDARD_DATA_ROOT + index * 4, target) }

        repeat(SPECIES_COUNT) { id ->
            putFixedName(bytes, SPECIES_NAMES_OFFSET, id, 11, if (id == 0) "NONE" else "MON")
            if (id in 1..activeStatCount) putValidStats(bytes, STATS_OFFSET + id * recordSize)
        }
        repeat(MOVE_COUNT) { id ->
            putFixedName(
                bytes,
                MOVE_NAMES_OFFSET,
                id,
                13,
                when (id) {
                    0 -> "NONE"
                    1 -> "POUND"
                    2 -> "KARATE CHOP"
                    3 -> "DOUBLESLAP"
                    else -> when (id % 3) {
                        0 -> "SMOLDER JAB"
                        1 -> "DUSKY BREAK"
                        else -> "BROOK WARD"
                    }
                },
            )
            if (id > 0) {
                val base = MOVE_DATA_OFFSET + id * 12
                bytes[base + 1] = 40
                bytes[base + 2] = (id % 18).toByte()
                bytes[base + 3] = 100
                bytes[base + 4] = 20
            }
        }

        val dexValues = List(SPECIES_COUNT - 1) { index ->
            val id = index + 1
            when (id) {
                1 -> 2
                2 -> 1
                in 3..ACTIVE_SPECIES_COUNT -> id
                else -> 0
            }
        }
        dexValues.forEachIndexed { index, dex -> putU16(bytes, DEX_MAP_OFFSET + index * 2, dex) }
        putCompiledSpeciesToDexLookup(bytes, DEX_MAP_OFFSET)
        putThumbLiteralReference(bytes, 0x740, 0x7A0, DEX_MAP_OFFSET)
        putThumbLiteralReference(bytes, 0x742, 0x7A4, DEX_MAP_OFFSET)
        repeat(statsReferenceCount) { index ->
            putThumbLiteralReference(bytes, 0x300 + index * 2, 0x500 + index * 4, STATS_OFFSET)
        }
        return Fixture(bytes, STATS_OFFSET)
    }

    private fun putCompiledSpeciesToDexLookup(bytes: ByteArray, target: Int) {
        putThumbLiteralReference(bytes, 0x700, 0x780, target)
        putU16(bytes, 0x702, 0x3901)
        putU16(bytes, 0x704, 0x0049)
        putU16(bytes, 0x706, 0x1809)
        putU16(bytes, 0x708, 0x8808)
    }

    private fun putThumbLiteralReference(bytes: ByteArray, instructionOffset: Int, literalOffset: Int, target: Int) {
        val pc = (instructionOffset + 4) and -4
        val distance = literalOffset - pc
        require(distance in 0..1_020 && distance % 4 == 0)
        putU16(bytes, instructionOffset, 0x4800 or (distance / 4))
        putPointer(bytes, literalOffset, target)
    }

    private fun putFixedName(bytes: ByteArray, tableOffset: Int, index: Int, width: Int, value: String) {
        val offset = tableOffset + index * width
        value.forEachIndexed { characterIndex, character ->
            bytes[offset + characterIndex] = when (character) {
                ' ' -> 0
                else -> (0xBB + character.code - 'A'.code).toByte()
            }
        }
        bytes[offset + value.length] = 0xFF.toByte()
    }

    private fun putValidStats(bytes: ByteArray, offset: Int) {
        repeat(6) { field -> bytes[offset + field] = (40 + field).toByte() }
        bytes[offset + 6] = 12
        bytes[offset + 7] = 3
    }

    private fun putAbilityName(bytes: ByteArray, offset: Int, value: String) {
        value.forEachIndexed { index, character ->
            bytes[offset + index] = when (character) {
                '-' -> 0xAE.toByte()
                else -> (0xBB + character.code - 'A'.code).toByte()
            }
        }
        bytes[offset + value.length] = 0xFF.toByte()
    }

    private fun writePlausibleQ412Matrix(bytes: ByteArray, offset: Int, typeCount: Int) {
        repeat(typeCount * typeCount) { index -> putU32(bytes, offset + index * 4, 4096) }
        val nonNeutral = listOf(0, 819, 2048, 8192, 20480)
        repeat(typeCount) { row ->
            repeat(4) { variant ->
                putU32(
                    bytes,
                    offset + (row * typeCount + (row + variant + 1) % typeCount) * 4,
                    nonNeutral[(row + variant) % nonNeutral.size],
                )
            }
        }
    }

    private fun putOverlapSafeValidStats(bytes: ByteArray, offset: Int) {
        repeat(8) { field -> bytes[offset + field] = 1 }
    }

    private fun putPointer(bytes: ByteArray, offset: Int, target: Int) {
        val value = 0x08000000L + target
        repeat(4) { index -> bytes[offset + index] = ((value ushr (index * 8)) and 0xFF).toByte() }
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private data class Fixture(val bytes: ByteArray, val statsOffset: Int)

    companion object {
        private const val ROM_SIZE = 0x20000
        private const val SPECIES_COUNT = 412
        private const val ACTIVE_SPECIES_COUNT = 383
        private const val MOVE_COUNT = 355
        private const val BASE_STAT_SIZE = 28
        private const val SPECIES_NAMES_SLOT = 0x144
        private const val MOVE_NAMES_SLOT = 0x148
        private const val STANDARD_DATA_ROOT = 0x1BC
        private const val SPECIES_NAMES_OFFSET = 0x2000
        private const val STATS_OFFSET = 0x4000
        private const val MOVE_NAMES_OFFSET = 0x8000
        private const val MOVE_DATA_OFFSET = 0xA000
        private const val ABILITY_NAMES_OFFSET = 0xC000
        private const val ABILITY_DESCRIPTIONS_OFFSET = 0xC800
        private const val ITEM_DATA_OFFSET = 0xD000
        private const val BALL_GRAPHICS_OFFSET = 0xE000
        private const val BALL_PALETTES_OFFSET = 0xF000
        private const val DEX_MAP_OFFSET = 0x11000
        private const val TYPE_CHART_POINTERS_OFFSET = 0x1000
        private const val TYPE_CHART_18_OFFSET = 0x12000
        private const val TYPE_CHART_20_OFFSET = 0x13000
        private const val ABILITY_BOUNDARY_INDEX = 255
        private const val ABILITY_NAME_COUNT = 291
        private val GBA_LOGO_PREFIX = byteArrayOf(
            0x24, 0xFF.toByte(), 0xAE.toByte(), 0x51, 0x69, 0x9A.toByte(), 0xA2.toByte(), 0x21,
            0x3D, 0x84.toByte(), 0x82.toByte(), 0x0A,
        )
    }
}
