package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsAbi
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsCodec
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsTableLayout
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsTableOutcome
import com.enrpau.dualscreendex.parser.dataset.moves.ResolvedMoveDetailsLayout
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameCodec
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameTableLayout
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameTableOutcome
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilitySemanticDomain
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionCodec
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionTableLayout
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionTableOutcome
import com.enrpau.dualscreendex.parser.dataset.descriptions.ResolvedDescriptionLayout
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.GbaCompiledReferenceIndex
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.PokeemeraldExpansionMetadata
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.ResolvedDatasetLayouts
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordMaterializersTest {
    @Test
    fun completeCompiledPokedexDomainExcludesOnlyItsConsecutiveOverflowBlock() {
        val speciesCount = 8
        val namesOffset = 0x100
        val statsOffset = 0x200
        val descriptionsOffset = 0x400
        val descriptionTextOffset = 0x600
        val dexMapOffset = 0x700
        val descriptionCount = 6
        val bytes = ByteArray(0x900) { 0xFF.toByte() }
        repeat(speciesCount) { id ->
            encodeGbaName(bytes, namesOffset + id * 11, if (id == 0) "NONE" else "MON")
            if (id > 0) {
                val base = statsOffset + id * 28
                repeat(6) { stat -> bytes[base + stat] = (40 + stat).toByte() }
                bytes[base + 6] = 1
                bytes[base + 7] = 2
            }
        }
        val dexValues = listOf(1, 2, 3, 6, 7, 4, 5)
        dexValues.forEachIndexed { index, dex -> writeU16(bytes, dexMapOffset + index * 2, dex) }
        repeat(descriptionCount) { dex ->
            val base = descriptionsOffset + dex * 32
            encodeGbaName(bytes, base, if (dex == 0) "UNKNOWN" else "ENTRY")
            writeU16(bytes, base + 12, dex + 1)
            writeU16(bytes, base + 14, dex + 2)
            writeGbaPointer(bytes, base + 16, descriptionTextOffset)
        }
        encodeGbaName(bytes, descriptionTextOffset, "DESCRIPTION")
        val descriptionTable = DescriptionTableLayout(
            descriptionsOffset.toLong(), descriptionCount.toLong(), 32, listOf(16),
        )
        val rom = RomImage(bytes)
        val decoded = DescriptionCodec().decode(
            RomAnalysisSession(rom, RomHeader(Platform.GBA, "POKEDEX DOMAIN TEST")),
            descriptionTable,
        ) as DescriptionTableOutcome.Decoded
        val layout = ResolvedRomLayout(
            family = EngineFamily.FIRERED_LEAFGREEN,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                baseStats = TableLayout(statsOffset, speciesCount, 28),
                descriptions = TableLayout(descriptionsOffset, descriptionCount, 32, pointerOffsets = listOf(16)),
            ),
            compiledGbaReferences = GbaCompiledReferenceIndex(
                counts = mapOf(descriptionsOffset to 2, dexMapOffset to 2),
            ),
            resolvedDatasets = ResolvedDatasetLayouts(
                descriptions = ResolvedDescriptionLayout(descriptionTable, decoded.rows),
            ),
        )

        val records = RecordMaterializers.species(rom, layout)

        assertEquals(
            (1..5).toSet(),
            records.values.mapNotNull { it.dexNumber.value }.filter { it > 0 }.toSet(),
        )
        assertEquals(CapabilityStatus.NOT_APPLICABLE, records.getValue(4).dexNumber.status)
        assertEquals(CapabilityStatus.NOT_APPLICABLE, records.getValue(5).dexNumber.status)
    }

    @Test
    fun unresolvedExpandedSpeciesIndexDoesNotMaterializeAllZeroDexRecords() {
        val speciesCount = 420
        val namesOffset = 0x100
        val statsOffset = 0x1400
        val bytes = ByteArray(0x5000) { 0x7F }
        repeat(speciesCount) { id -> encodeGbaName(bytes, namesOffset + id * 11, "MON") }
        val layout = ResolvedRomLayout(
            EngineFamily.FIRERED_LEAFGREEN,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(
                speciesNames = TableLayout(namesOffset, speciesCount, 11),
                baseStats = TableLayout(statsOffset, speciesCount, 28),
            ),
        )

        val records = RecordMaterializers.species(RomImage(bytes), layout)

        assertTrue(records.isEmpty())
    }

    @Test
    fun materializesGbaSpeciesUsingRomNativeIds() {
        val bytes = ByteArray(256) { 0xFF.toByte() }
        putIdentitySpeciesIndexEvidence(bytes)
        encodeGbaName(bytes, 0, "??????????")
        encodeGbaName(bytes, 11, "BULBA")
        encodeGbaName(bytes, 22, "IVY")
        val stats = 64
        bytes[stats + 28] = 45
        bytes[stats + 29] = 49
        bytes[stats + 30] = 49
        bytes[stats + 31] = 45
        bytes[stats + 32] = 65
        bytes[stats + 33] = 65
        bytes[stats + 34] = 12
        bytes[stats + 35] = 3
        bytes[stats + 28 + 19] = 4
        bytes[stats + 28 + 20] = 1
        bytes[stats + 28 + 21] = 1
        bytes[stats + 28 + 22] = 7
        bytes[stats + 28 + 23] = 9
        bytes[stats + 28 + 25] = 0
        bytes[stats + 28 + 26] = 0
        bytes[stats + 28 + 27] = 0

        val records = RecordMaterializers.species(RomImage(bytes), gbaLayout(stats))
        val bulba = records.getValue(1)

        assertEquals("BULBA", bulba.name.value)
        assertEquals(listOf(12, 3), bulba.typeIds.value)
        assertEquals(BaseStats(45, 49, 49, 45, 65, 65), bulba.baseStats.value)
        assertEquals(4, bulba.growthRate.value)
        assertEquals(listOf(7, 9), bulba.abilityIds.value)
    }

    @Test
    fun excludesTheAbilityNoneSentinelFromSpeciesAbilities() {
        val bytes = ByteArray(256) { 0xFF.toByte() }
        putIdentitySpeciesIndexEvidence(bytes)
        encodeGbaName(bytes, 0, "??????????")
        encodeGbaName(bytes, 11, "CHARIZARD")
        val stats = 64
        repeat(6) { bytes[stats + 28 + it] = 50 }
        bytes[stats + 28 + 6] = 0
        bytes[stats + 28 + 7] = 0
        bytes[stats + 28 + 19] = 0
        bytes[stats + 28 + 20] = 0
        bytes[stats + 28 + 21] = 0
        bytes[stats + 28 + 22] = 66
        bytes[stats + 28 + 23] = 0
        bytes[stats + 28 + 25] = 0
        bytes[stats + 28 + 26] = 0
        bytes[stats + 28 + 27] = 0

        val charizard = RecordMaterializers.species(RomImage(bytes), gbaLayout(stats)).getValue(1)

        assertEquals(listOf(66), charizard.abilityIds.value)
    }

    @Test
    fun rejectsAbilitySlotsFromAStatRowWithAnInvalidRetailEcologyTail() {
        val bytes = ByteArray(256)
        putIdentitySpeciesIndexEvidence(bytes)
        encodeGbaName(bytes, 0, "??????????")
        encodeGbaName(bytes, 11, "DEWGONG")
        val stats = 64
        repeat(6) { bytes[stats + 28 + it] = 50 }
        bytes[stats + 28 + 6] = 11
        bytes[stats + 28 + 7] = 15
        bytes[stats + 28 + 19] = 224.toByte()
        bytes[stats + 28 + 20] = 224.toByte()
        bytes[stats + 28 + 21] = 213.toByte()
        bytes[stats + 28 + 22] = 0
        bytes[stats + 28 + 23] = 220.toByte()

        val dewgong = RecordMaterializers.species(RomImage(bytes), gbaLayout(stats)).getValue(1)

        assertNull(dewgong.abilityIds.value)
        assertEquals(CapabilityStatus.NOT_FOUND, dewgong.abilityIds.status)
    }

    @Test
    fun acceptsEveryLegalRetailEcologyBoundaryWhenReadingAbilitySlots() {
        val bytes = ByteArray(256)
        putIdentitySpeciesIndexEvidence(bytes)
        encodeGbaName(bytes, 0, "??????????")
        encodeGbaName(bytes, 11, "BOUNDARY")
        val stats = 64
        repeat(6) { bytes[stats + 28 + it] = 50 }
        bytes[stats + 28 + 6] = 0
        bytes[stats + 28 + 7] = 31
        bytes[stats + 28 + 19] = 5
        bytes[stats + 28 + 20] = 15
        bytes[stats + 28 + 21] = 15
        bytes[stats + 28 + 22] = 77
        bytes[stats + 28 + 23] = 76
        bytes[stats + 28 + 25] = (0x80 or 13).toByte()
        bytes[stats + 28 + 26] = 0
        bytes[stats + 28 + 27] = 0

        val record = RecordMaterializers.species(RomImage(bytes), gbaLayout(stats)).getValue(1)

        assertEquals(listOf(77, 76), record.abilityIds.value)
    }

    @Test
    fun materializesBattleEngineU16AbilitySlotsFromThirtyTwoByteBaseStats() {
        val bytes = ByteArray(256) { 0xFF.toByte() }
        putIdentitySpeciesIndexEvidence(bytes)
        encodeGbaName(bytes, 0, "??????????")
        encodeGbaName(bytes, 11, "PIKACHU")
        val stats = 64
        val pikachu = stats + 32
        bytes[pikachu] = 35
        bytes[pikachu + 1] = 55
        bytes[pikachu + 2] = 40
        bytes[pikachu + 3] = 90.toByte()
        bytes[pikachu + 4] = 50
        bytes[pikachu + 5] = 50
        bytes[pikachu + 6] = 13
        bytes[pikachu + 7] = 13
        writeU16(bytes, pikachu + 22, 9)
        writeU16(bytes, pikachu + 24, 31)
        writeU16(bytes, pikachu + 26, 145)

        val record = RecordMaterializers.species(RomImage(bytes), gbaLayout(stats, 32)).getValue(1)

        assertEquals(listOf(9, 31, 145), record.abilityIds.value)
    }

    @Test
    fun rejectsUnsupportedGenThreeBaseStatAbilityWidths() {
        listOf(24, 26, 30).forEach { recordSize ->
            val bytes = ByteArray(256)
            putIdentitySpeciesIndexEvidence(bytes)
            encodeGbaName(bytes, 0, "??????????")
            encodeGbaName(bytes, 11, "PIKACHU")
            val stats = 64
            val pikachu = stats + recordSize
            bytes[pikachu] = 35
            bytes[pikachu + 1] = 55
            bytes[pikachu + 2] = 40
            bytes[pikachu + 3] = 90.toByte()
            bytes[pikachu + 4] = 50
            bytes[pikachu + 5] = 50
            bytes[pikachu + 6] = 13
            bytes[pikachu + 7] = 13
            bytes[pikachu + 22] = 9
            bytes[pikachu + 23] = 31

            val record = RecordMaterializers.species(RomImage(bytes), gbaLayout(stats, recordSize)).getValue(1)

            assertNull("recordSize=$recordSize", record.abilityIds.value)
        }
    }

    @Test
    fun rejectsBattleEngineAbilityLayoutWhoseStrideIsShorterThanItsRecord() {
        val bytes = ByteArray(256)
        putIdentitySpeciesIndexEvidence(bytes)
        encodeGbaName(bytes, 0, "??????????")
        encodeGbaName(bytes, 11, "PIKACHU")
        val stats = 64
        val pikachu = stats + 24
        bytes[pikachu] = 35
        bytes[pikachu + 1] = 55
        bytes[pikachu + 2] = 40
        bytes[pikachu + 3] = 90.toByte()
        bytes[pikachu + 4] = 50
        bytes[pikachu + 5] = 50
        bytes[pikachu + 6] = 13
        bytes[pikachu + 7] = 13
        writeU16(bytes, pikachu + 22, 9)
        writeU16(bytes, pikachu + 24, 31)
        writeU16(bytes, pikachu + 26, 145)
        val base = gbaLayout(stats, 32)
        val layout = base.copy(
            tables = base.tables.copy(baseStats = TableLayout(stats, 3, 32, stride = 24)),
        )

        val record = RecordMaterializers.species(RomImage(bytes), layout).getValue(1)

        assertNull(record.abilityIds.value)
    }

    @Test
    fun omitsZeroAndDuplicateBattleEngineAbilitySlots() {
        val bytes = ByteArray(256)
        putIdentitySpeciesIndexEvidence(bytes)
        encodeGbaName(bytes, 0, "??????????")
        encodeGbaName(bytes, 11, "PIKACHU")
        val stats = 64
        val pikachu = stats + 32
        bytes[pikachu] = 35
        bytes[pikachu + 1] = 55
        bytes[pikachu + 2] = 40
        bytes[pikachu + 3] = 90.toByte()
        bytes[pikachu + 4] = 50
        bytes[pikachu + 5] = 50
        bytes[pikachu + 6] = 13
        bytes[pikachu + 7] = 13
        writeU16(bytes, pikachu + 22, 9)
        writeU16(bytes, pikachu + 24, 0)
        writeU16(bytes, pikachu + 26, 9)

        val record = RecordMaterializers.species(RomImage(bytes), gbaLayout(stats, 32)).getValue(1)

        assertEquals(listOf(9), record.abilityIds.value)
    }

    @Test
    fun rejectsTruncatedBaseStatTableBeforeReadingAnySpeciesRecord() {
        assertMalformedGenThreeBaseStatsAreNotMaterialized(
            TableLayout(offset = 96, count = 3, recordSize = 28),
        )
    }

    @Test
    fun reportsMissingAbilitiesWhenGenThreeBaseStatCardinalityIsZero() {
        assertMalformedGenThreeBaseStatsAreNotMaterialized(
            TableLayout(offset = 64, count = 0, recordSize = 28),
        )
    }

    @Test
    fun reportsMissingAbilitiesWhenSpeciesExceedsGenThreeBaseStatCardinality() {
        val base = gbaLayout(64)
        val layout = base.copy(
            tables = base.tables.copy(baseStats = TableLayout(offset = 64, count = 1, recordSize = 28)),
        )

        val records = RecordMaterializers.species(RomImage(genThreeSpeciesFixture()), layout)

        listOf(1, 2).forEach { id ->
            val abilities = records.getValue(id).abilityIds
            assertEquals(CapabilityStatus.NOT_FOUND, abilities.status)
            assertEquals(listOf("base-stat ability layout is unsupported or malformed"), abilities.reasons)
        }
    }

    @Test
    fun rejectsBaseStatOffsetNearIntegerMaximumWithoutThrowing() {
        assertMalformedGenThreeBaseStatsAreNotMaterialized(
            TableLayout(offset = Int.MAX_VALUE - 8, count = 3, recordSize = 28),
        )
    }

    @Test
    fun rejectsBaseStatAdditionOverflowWithoutThrowing() {
        assertMalformedGenThreeBaseStatsAreNotMaterialized(
            TableLayout(offset = 64, count = 3, recordSize = 28, stride = Int.MAX_VALUE),
        )
    }

    @Test
    fun rejectsInvalidNegativeStrideBeforeReadingDecoyBaseStats() {
        val bytes = genThreeSpeciesFixture()
        val decoy = 36
        bytes[decoy] = 35
        bytes[decoy + 1] = 55
        bytes[decoy + 2] = 40
        bytes[decoy + 3] = 90.toByte()
        bytes[decoy + 4] = 50
        bytes[decoy + 5] = 50
        bytes[decoy + 6] = 13
        bytes[decoy + 7] = 13
        val base = gbaLayout(64)
        val layout = base.copy(
            tables = base.tables.copy(baseStats = TableLayout(64, 3, 28, stride = -28)),
        )

        val records = RecordMaterializers.species(RomImage(bytes), layout)

        assertMalformedSpeciesFields(records)
    }

    @Test
    fun rejectsBaseStatMultiplicationOverflowWithoutThrowing() {
        val bytes = ByteArray(256)
        bytes[0] = 3
        bytes[1] = 1
        bytes[2] = 2
        encodeGbFixedName(bytes, 32, "BULBA")
        encodeGbFixedName(bytes, 42, "IVY")
        encodeGbFixedName(bytes, 52, "VENUS")
        val layout = ResolvedRomLayout(
            family = EngineFamily.RED_BLUE,
            generation = 1,
            platform = Platform.GB,
            speciesCount = 3,
            moveCount = 0,
            tables = ProfileTables(
                speciesNames = TableLayout(32, 3, 10),
                baseStats = TableLayout(64, 3, 28, stride = 1_100_000_000),
            ),
        )

        val records = RecordMaterializers.species(RomImage(bytes), layout)

        records.values.forEach { record ->
            assertNull(record.baseStats.value)
            assertNull(record.typeIds.value)
            assertNull(record.abilityIds.value)
            assertEquals(CapabilityStatus.NOT_APPLICABLE, record.abilityIds.status)
            assertEquals(listOf("abilities are not part of this engine"), record.abilityIds.reasons)
        }
    }

    @Test
    fun doesNotMaterializeReservedZeroBaseStatRows() {
        val bytes = ByteArray(256) { 0xFF.toByte() }
        putIdentitySpeciesIndexEvidence(bytes)
        encodeGbaName(bytes, 0, "??????????")
        encodeGbaName(bytes, 11, "RESERVED")

        val reserved = RecordMaterializers.species(RomImage(bytes), gbaLayout(64)).getValue(1)

        assertNull(reserved.baseStats.value)
        assertNull(reserved.typeIds.value)
    }

    private fun assertMalformedGenThreeBaseStatsAreNotMaterialized(table: TableLayout) {
        val base = gbaLayout(64)
        val layout = base.copy(tables = base.tables.copy(baseStats = table))

        val records = RecordMaterializers.species(RomImage(genThreeSpeciesFixture()), layout)

        assertMalformedSpeciesFields(records)
    }

    private fun assertMalformedSpeciesFields(records: Map<Int, SpeciesRecord>) {
        records.values.forEach { record ->
            assertNull(record.baseStats.value)
            assertNull(record.typeIds.value)
            assertNull(record.abilityIds.value)
            assertEquals(CapabilityStatus.NOT_FOUND, record.abilityIds.status)
            assertEquals(listOf("base-stat ability layout is unsupported or malformed"), record.abilityIds.reasons)
            assertNull(record.growthRate.value)
        }
    }

    private fun genThreeSpeciesFixture(): ByteArray = ByteArray(128).also { bytes ->
        putIdentitySpeciesIndexEvidence(bytes)
        encodeGbaName(bytes, 0, "??????????")
        encodeGbaName(bytes, 11, "PIKACHU")
        encodeGbaName(bytes, 22, "RAICHU")
    }

    @Test
    fun materializesMoveMetadataAndSignedPriority() {
        val bytes = ByteArray(128) { 0xFF.toByte() }
        encodeGbaName(bytes, 0, "-")
        encodeGbaName(bytes, 13, "EMBER")
        val data = 48
        bytes[data + 12] = 4
        bytes[data + 13] = 40
        bytes[data + 14] = 10
        bytes[data + 15] = 100.toByte()
        bytes[data + 16] = 25
        bytes[data + 19] = 0xFF.toByte()
        val rawLayout = gbaLayout(80).copy(
            moveCount = 2,
            tables = gbaLayout(80).tables.copy(
                moveNames = TableLayout(0, 2, 13),
                moveData = TableLayout(data, 2, 12),
            ),
        )
        val layout = rawLayout.withTypedMoveDetails(bytes, MoveDetailsAbi.RETAIL_12)

        val ember = RecordMaterializers.moves(RomImage(bytes), layout).getValue(1)

        assertEquals("EMBER", ember.name.value)
        assertEquals(40, ember.power.value)
        assertEquals(10, ember.typeId.value)
        assertEquals(100, ember.accuracy.value)
        assertEquals(25, ember.pp.value)
        assertEquals(-1, ember.priority.value)
        assertEquals(MoveCategory.SPECIAL, ember.category.value)
        assertEquals(4, ember.effectId.value)
    }

    @Test
    fun ordinaryGen3MoveDetailsFailClosedWithoutTheTypedSelectedOutcome() {
        val bytes = ByteArray(128) { 0xFF.toByte() }
        encodeGbaName(bytes, 0, "-")
        encodeGbaName(bytes, 13, "EMBER")
        val data = 48
        bytes[data + 12] = 4
        bytes[data + 13] = 40
        bytes[data + 14] = 10
        bytes[data + 15] = 100.toByte()
        bytes[data + 16] = 25
        bytes[data + 19] = 0xFF.toByte()
        val layout = gbaLayout(80).copy(
            moveCount = 2,
            tables = gbaLayout(80).tables.copy(
                moveNames = TableLayout(0, 2, 13),
                moveData = TableLayout(data, 2, 12),
            ),
        )

        val ember = RecordMaterializers.moves(RomImage(bytes), layout).getValue(1)

        assertEquals("EMBER", ember.name.value)
        listOf(ember.typeId, ember.category, ember.power, ember.accuracy, ember.pp, ember.priority, ember.effectId)
            .forEach { assertNull(it.value) }
    }

    @Test
    fun materializesValidatedSixteenByteCfruMoveMetadata() {
        val bytes = ByteArray(160)
        encodeGbaName(bytes, 0, "-")
        encodeGbaName(bytes, 13, "POUND")
        val data = 48
        writeU16(bytes, data + 16, 43)
        writeU16(bytes, data + 18, 60)
        bytes[data + 20] = 1
        bytes[data + 21] = 100
        bytes[data + 22] = 35
        bytes[data + 23] = 10
        bytes[data + 25] = 0xFF.toByte()
        bytes[data + 26] = 1
        val rawLayout = gbaLayout(96).copy(
            moveCount = 2,
            tables = gbaLayout(96).tables.copy(
                moveNames = TableLayout(0, 2, 13),
                moveData = TableLayout(data, 2, 16, format = TableRecordFormat.CFRU_MOVE_16),
            ),
        )
        val layout = rawLayout.withTypedMoveDetails(bytes, MoveDetailsAbi.CFRU_16)

        val pound = RecordMaterializers.moves(RomImage(bytes), layout).getValue(1)

        assertEquals("POUND", pound.name.value)
        assertEquals(60, pound.power.value)
        assertEquals(1, pound.typeId.value)
        assertEquals(100, pound.accuracy.value)
        assertEquals(35, pound.pp.value)
        assertEquals(-1, pound.priority.value)
        assertEquals(MoveCategory.SPECIAL, pound.category.value)
        assertEquals(43, pound.effectId.value)
    }

    @Test
    fun materializesValidatedTwentyByteBattleEngineMoveMetadata() {
        val bytes = ByteArray(192)
        encodeGbaName(bytes, 0, "-")
        encodeGbaName(bytes, 13, "POUND")
        val data = 48
        writeU16(bytes, data + 20, 43)
        writeU16(bytes, data + 22, 300)
        bytes[data + 24] = 18
        bytes[data + 25] = 100
        bytes[data + 26] = 5
        bytes[data + 27] = 10
        writeU16(bytes, data + 28, 1)
        bytes[data + 30] = 0xFE.toByte()
        bytes[data + 32] = 0x33
        bytes[data + 36] = 1
        bytes[data + 38] = 200.toByte()
        val rawLayout = gbaLayout(128).copy(
            moveCount = 2,
            tables = gbaLayout(128).tables.copy(
                moveNames = TableLayout(0, 2, 13),
                moveData = TableLayout(data, 2, 20, format = TableRecordFormat.BATTLE_ENGINE_MOVE_20),
            ),
        )
        val layout = rawLayout.withTypedMoveDetails(bytes, MoveDetailsAbi.BATTLE_ENGINE_20)

        val pound = RecordMaterializers.moves(RomImage(bytes), layout).getValue(1)

        assertEquals("POUND", pound.name.value)
        assertEquals(300, pound.power.value)
        assertEquals(18, pound.typeId.value)
        assertEquals(100, pound.accuracy.value)
        assertEquals(5, pound.pp.value)
        assertEquals(-2, pound.priority.value)
        assertEquals(MoveCategory.SPECIAL, pound.category.value)
        assertEquals(43, pound.effectId.value)
    }

    @Test
    fun materializesTerminatedTypeChartWithoutSentinel() {
        val bytes = byteArrayOf(10, 12, 20, 13, 12, 5, 0xFF.toByte(), 0xFF.toByte(), 0)
        val layout = gbaLayout(0).copy(
            tables = gbaLayout(0).tables.copy(typeChart = TableLayout(0, 2, 3, variableLength = true)),
        )

        val chart = RecordMaterializers.typeChart(RomImage(bytes), layout)

        assertEquals(
            listOf(TypeMatchup(10, 12, 200), TypeMatchup(13, 12, 50)),
            chart,
        )
    }

    @Test
    fun materializesQ412TypeChartWithoutExpansionMetadata() {
        val typeCount = 3
        val bytes = ByteArray(typeCount * typeCount * 4)
        repeat(typeCount * typeCount) { index -> writeU32le(bytes, index * 4, 4096) }
        writeU32le(bytes, (0 * typeCount + 1) * 4, 819)
        writeU32le(bytes, (1 * typeCount + 2) * 4, 0)
        writeU32le(bytes, (2 * typeCount + 0) * 4, 20480)
        val layout = gbaLayout(0).copy(
            tables = gbaLayout(0).tables.copy(
                typeChart = TableLayout(
                    offset = 0,
                    count = typeCount * typeCount,
                    recordSize = typeCount * 4,
                    elementSize = 4,
                ),
            ),
            pokeemeraldExpansion = null,
        )

        val chart = RecordMaterializers.typeChart(RomImage(bytes), layout)

        assertEquals(
            listOf(TypeMatchup(0, 1, 20), TypeMatchup(1, 2, 0), TypeMatchup(2, 0, 500)),
            chart,
        )
    }

    @Test
    fun materializesU16Q412TypeChartWithRoundedPercentages() {
        val typeCount = 3
        val bytes = ByteArray(typeCount * typeCount * 2)
        repeat(typeCount * typeCount) { index -> writeU16(bytes, index * 2, 4096) }
        writeU16(bytes, (0 * typeCount + 1) * 2, 819)
        writeU16(bytes, (1 * typeCount + 2) * 2, 0)
        writeU16(bytes, (2 * typeCount + 0) * 2, 8192)
        val layout = gbaLayout(0).copy(
            tables = gbaLayout(0).tables.copy(
                typeChart = TableLayout(
                    offset = 0,
                    count = typeCount * typeCount,
                    recordSize = typeCount * 2,
                    elementSize = 2,
                ),
            ),
            pokeemeraldExpansion = null,
        )

        val chart = RecordMaterializers.typeChart(RomImage(bytes), layout)

        assertEquals(
            listOf(TypeMatchup(0, 1, 20), TypeMatchup(1, 2, 0), TypeMatchup(2, 0, 200)),
            chart,
        )
    }

    @Test
    fun preservesUnknownHackTypeIdsInsteadOfSubstitutingModernData() {
        val layout = gbaLayout(0)
        val species = mapOf(
            1 to SpeciesRecord(
                id = 1,
                dexNumber = CatalogField.available(1),
                name = CatalogField.available("TEST"),
                typeIds = CatalogField.available(listOf(10, 18)),
                baseStats = CatalogField.notFound("fixture"),
                sprite = CatalogField.notFound("fixture"),
            ),
        )

        val types = RecordMaterializers.types(layout, species, listOf(TypeMatchup(18, 10, 200)))

        assertEquals("FIRE", types.getValue(10).name.value)
        assertEquals("TYPE 18", types.getValue(18).name.value)
        assertEquals(18, types.getValue(18).id)
    }

    @Test
    fun materializesAbilityNamesFromTheResolvedRomTable() {
        val bytes = ByteArray(64) { 0xFF.toByte() }
        encodeGbaName(bytes, 0, "-")
        encodeGbaName(bytes, 13, "OVERGROW")
        val baseLayout = gbaLayout(40).copy(
            tables = gbaLayout(40).tables.copy(abilities = TableLayout(0, 2, 13)),
        )
        val typed = (AbilityNameCodec().decode(
            RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBA, "ABILITY TEST")),
            AbilityNameTableLayout(0, 2, 13),
            AbilitySemanticDomain(setOf(1)),
        ) as AbilityNameTableOutcome.Decoded).resolved
        val layout = baseLayout.copy(resolvedDatasets = ResolvedDatasetLayouts(abilityNames = typed))

        val abilities = RecordMaterializers.abilities(RomImage(bytes), layout)

        assertEquals("OVERGROW", abilities.getValue(1).name.value)
    }

    @Test
    fun ordinaryGenThreeAbilityNamesFailClosedWithoutTheTypedSelectedOutcome() {
        val bytes = ByteArray(64) { 0xFF.toByte() }
        encodeGbaName(bytes, 0, "-")
        encodeGbaName(bytes, 13, "OVERGROW")
        val layout = gbaLayout(40).copy(
            tables = gbaLayout(40).tables.copy(abilities = TableLayout(0, 2, 13)),
        )

        assertTrue(RecordMaterializers.abilities(RomImage(bytes), layout).isEmpty())
    }

    @Test
    fun extendsAbilityNamesOnlyThroughACompletelyValidReferencedSuffix() {
        val bytes = ByteArray(96)
        encodeGbaName(bytes, 0, "-")
        encodeGbaName(bytes, 13, "OVERGROW")
        encodeGbaName(bytes, 26, "SLUSH RUSH")
        encodeGbaName(bytes, 39, "GALVANIZE")
        val baseLayout = gbaLayout(64).copy(
            tables = gbaLayout(64).tables.copy(abilities = TableLayout(0, 2, 13)),
        )
        val typed = (AbilityNameCodec().decode(
            RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBA, "ABILITY TEST")),
            AbilityNameTableLayout(0, 4, 13),
            AbilitySemanticDomain(setOf(1, 3)),
        ) as AbilityNameTableOutcome.Decoded).resolved
        val layout = baseLayout.copy(resolvedDatasets = ResolvedDatasetLayouts(abilityNames = typed))

        val abilities = RecordMaterializers.abilities(RomImage(bytes), layout)

        assertEquals(setOf(1, 2, 3), abilities.keys)
    }

    @Test
    fun doesNotExtendAbilityNamesAcrossAnInvalidReferencedSuffixRow() {
        val bytes = ByteArray(96)
        encodeGbaName(bytes, 0, "-")
        encodeGbaName(bytes, 13, "OVERGROW")
        encodeGbaName(bytes, 26, "-")
        encodeGbaName(bytes, 39, "GALVANIZE")
        val baseLayout = gbaLayout(64).copy(
            tables = gbaLayout(64).tables.copy(abilities = TableLayout(0, 2, 13)),
        )
        val typed = (AbilityNameCodec().decode(
            RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBA, "ABILITY TEST")),
            AbilityNameTableLayout(0, 2, 13),
            AbilitySemanticDomain(setOf(1)),
        ) as AbilityNameTableOutcome.Decoded).resolved
        val layout = baseLayout.copy(resolvedDatasets = ResolvedDatasetLayouts(abilityNames = typed))

        val abilities = RecordMaterializers.abilities(RomImage(bytes), layout)

        assertEquals(setOf(1), abilities.keys)
    }

    @Test
    fun materializesNameOnlyMovesWithoutInventingDetailsOrPlaceholderIds() {
        val bytes = ByteArray(64)
        encodeGbaName(bytes, 0, "-")
        encodeGbaName(bytes, 13, "POUND")
        encodeGbaName(bytes, 26, "-")
        val layout = gbaLayout(48).copy(
            moveCount = 3,
            tables = gbaLayout(48).tables.copy(moveNames = TableLayout(0, 3, 13), moveData = null),
        )

        val moves = RecordMaterializers.moves(RomImage(bytes), layout)

        assertEquals(setOf(1), moves.keys)
        assertEquals("POUND", moves.getValue(1).name.value)
        listOf(
            moves.getValue(1).typeId,
            moves.getValue(1).category,
            moves.getValue(1).power,
            moves.getValue(1).accuracy,
            moves.getValue(1).pp,
            moves.getValue(1).priority,
            moves.getValue(1).effectId,
        ).forEach { field ->
            assertEquals(CapabilityStatus.NOT_FOUND, field.status)
            assertNull(field.value)
        }
    }

    @Test
    fun excludesGenThreeMoveZeroFromNameOnlyCatalogEvenWhenItsNameIsAlphanumeric() {
        val bytes = ByteArray(32)
        encodeGbaName(bytes, 0, "NONE")
        encodeGbaName(bytes, 13, "POUND")
        val layout = gbaLayout(32).copy(
            moveCount = 2,
            tables = gbaLayout(32).tables.copy(moveNames = TableLayout(0, 2, 13), moveData = null),
        )

        val moves = RecordMaterializers.moves(RomImage(bytes), layout)

        assertEquals(setOf(1), moves.keys)
        assertEquals("POUND", moves.getValue(1).name.value)
        assertEquals(CapabilityStatus.NOT_FOUND, moves.getValue(1).typeId.status)
        assertNull(moves.getValue(1).typeId.value)
    }

    @Test
    fun materializesStridedPokeemeraldExpansionRecordsAndPointerNames() {
        val bytes = ByteArray(1400)
        val species = 100
        val speciesStride = 180
        val bulba = species + speciesStride
        repeat(6) { bytes[bulba + it] = listOf(45, 49, 49, 45, 65, 65)[it].toByte() }
        bytes[bulba + 6] = 12
        bytes[bulba + 7] = 3
        bytes[bulba + 21] = 4
        bytes[bulba + 24] = 65
        bytes[bulba + 25] = 0
        bytes[bulba + 26] = 66
        bytes[bulba + 27] = 0
        encodeGbaName(bytes, bulba + 44, "BULBA")
        bytes[bulba + 60] = 1

        val moves = 600
        val pound = moves + 64
        writeGbaPointer(bytes, pound, 1000)
        encodeGbaName(bytes, 1000, "POUND")
        val packedMove = 0 or (0 shl 5) or (40 shl 7)
        bytes[pound + 10] = packedMove.toByte()
        bytes[pound + 11] = (packedMove ushr 8).toByte()
        bytes[pound + 12] = 100
        bytes[pound + 14] = 35

        val abilities = 1100
        encodeGbaName(bytes, abilities + 28, "STENCH")
        val metadata = expansionMetadata(speciesStride)
        val layout = ResolvedRomLayout(
            family = EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = 2,
            moveCount = 2,
            tables = ProfileTables(
                speciesNames = TableLayout(species + 44, 2, 13, stride = speciesStride),
                baseStats = TableLayout(species, 2, speciesStride, stride = speciesStride),
                moveNames = TableLayout(moves, 2, 4, stride = 64, valuesArePointers = true),
                moveData = TableLayout(moves, 2, 64, stride = 64),
                abilities = TableLayout(abilities, 2, 20, stride = 28),
            ),
            pokeemeraldExpansion = metadata,
        )

        val speciesRecords = RecordMaterializers.species(RomImage(bytes), layout)
        val moveRecords = RecordMaterializers.moves(RomImage(bytes), layout)
        val abilityRecords = RecordMaterializers.abilities(RomImage(bytes), layout)

        assertEquals("BULBA", speciesRecords.getValue(1).name.value)
        assertEquals(1, speciesRecords.getValue(1).dexNumber.value)
        assertEquals(listOf(65, 66), speciesRecords.getValue(1).abilityIds.value)
        assertEquals("POUND", moveRecords.getValue(1).name.value)
        assertEquals(40, moveRecords.getValue(1).power.value)
        assertEquals(100, moveRecords.getValue(1).accuracy.value)
        assertEquals(MoveCategory.PHYSICAL, moveRecords.getValue(1).category.value)
        assertEquals("STENCH", abilityRecords.getValue(1).name.value)
    }

    @Test
    fun joinsGenOneInternalNamesToDexOrderedStats() {
        val bytes = ByteArray(512)
        byteArrayOf(1, 0, 3, 2).copyInto(bytes, 50)
        encodeGbFixedName(bytes, 100, "BULBA")
        encodeGbFixedName(bytes, 110, "MISSING")
        encodeGbFixedName(bytes, 120, "VENU")
        encodeGbFixedName(bytes, 130, "IVY")
        repeat(3) { dexIndex ->
            val base = 200 + dexIndex * 28
            bytes[base + 1] = ((dexIndex + 1) * 10).toByte()
            bytes[base + 2] = 20
            bytes[base + 3] = 20
            bytes[base + 4] = 20
            bytes[base + 5] = 20
            bytes[base + 6] = 0
            bytes[base + 7] = 0
        }
        val layout = ResolvedRomLayout(
            family = EngineFamily.RED_BLUE,
            generation = 1,
            platform = Platform.GB,
            speciesCount = 4,
            moveCount = 1,
            tables = ProfileTables(
                speciesNames = TableLayout(100, 4, 10),
                baseStats = TableLayout(200, 3, 28),
            ),
        )

        val records = RecordMaterializers.species(RomImage(bytes), layout)

        assertEquals("IVY", records.getValue(4).name.value)
        assertEquals(2, records.getValue(4).dexNumber.value)
        assertEquals(20, records.getValue(4).baseStats.value?.hp)
        assertEquals(0, records.getValue(2).dexNumber.value)
        assertEquals(null, records.getValue(2).baseStats.value)
    }

    private fun gbaLayout(statsOffset: Int, baseStatRecordSize: Int = 28) = ResolvedRomLayout(
        family = EngineFamily.EMERALD,
        generation = 3,
        platform = Platform.GBA,
        speciesCount = 3,
        moveCount = 0,
        tables = ProfileTables(
            speciesNames = TableLayout(0, 3, 11),
            baseStats = TableLayout(statsOffset, 3, baseStatRecordSize),
        ),
    )

    private fun encodeGbaName(target: ByteArray, offset: Int, value: String) {
        value.forEachIndexed { index, char ->
            target[offset + index] = when (char) {
                in 'A'..'Z' -> (0xBB + char.code - 'A'.code).toByte()
                ' ' -> 0x00
                '?' -> 0xAC.toByte()
                '-' -> 0xAE.toByte()
                else -> error("unsupported fixture character")
            }
        }
        target[offset + value.length] = 0xFF.toByte()
    }

    private fun encodeGbFixedName(target: ByteArray, offset: Int, value: String) {
        value.forEachIndexed { index, char ->
            target[offset + index] = (0x80 + char.code - 'A'.code).toByte()
        }
        target[offset + value.length] = 0x50
    }

    private fun writeGbaPointer(target: ByteArray, offset: Int, romOffset: Int) {
        val value = 0x08000000 + romOffset
        repeat(4) { index -> target[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun writeU16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
    }

    private fun putIdentitySpeciesIndexEvidence(target: ByteArray, speciesCount: Int = 3) {
        val offset = target.size - 8
        (1 until speciesCount).forEach { speciesId ->
            writeU16(target, offset + (speciesId - 1) * 2, speciesId)
        }
    }

    private fun writeU32le(target: ByteArray, offset: Int, value: Long) {
        repeat(4) { index -> target[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun expansionMetadata(speciesStride: Int) = PokeemeraldExpansionMetadata(
        headerOffset = 0x204,
        versionMajor = 1,
        versionMinor = 15,
        versionPatch = 3,
        speciesRecordSize = speciesStride,
        speciesNameOffset = 44,
        speciesNameWidth = 13,
        categoryOffset = 31,
        nationalDexOffset = 60,
        heightOffset = 62,
        weightOffset = 64,
        descriptionPointerOffset = 76,
        frontSpritePointerOffset = 88,
        normalPalettePointerOffset = 96,
        abilitiesOffset = 24,
        growthRateOffset = 21,
        levelUpPointerOffset = 148,
        teachablePointerOffset = 152,
        eggMovePointerOffset = 156,
        evolutionPointerOffset = 160,
        moveRecordSize = 64,
        abilityRecordSize = 28,
        abilityNameWidth = 20,
        abilityDescriptionPointerOffset = 20,
    )
}

private fun ResolvedRomLayout.withTypedMoveDetails(bytes: ByteArray, abi: MoveDetailsAbi): ResolvedRomLayout {
    val data = requireNotNull(tables.moveData)
    val selected = MoveDetailsTableLayout(data.offset.toLong(), data.count.toLong(), abi)
    val decoded = MoveDetailsCodec().decode(
        RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBA, "MOVE DETAILS TEST")),
        selected,
    ) as MoveDetailsTableOutcome.Decoded
    return copy(
        resolvedDatasets = ResolvedDatasetLayouts(
            moveDetails = ResolvedMoveDetailsLayout(selected, decoded.rows),
        ),
    )
}
