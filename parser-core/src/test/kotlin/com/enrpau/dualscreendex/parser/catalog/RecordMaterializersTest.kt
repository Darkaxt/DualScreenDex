package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.PokeemeraldExpansionMetadata
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordMaterializersTest {
    @Test
    fun materializesGbaSpeciesUsingRomNativeIds() {
        val bytes = ByteArray(256) { 0xFF.toByte() }
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
        bytes[stats + 28 + 22] = 7
        bytes[stats + 28 + 23] = 9

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
        encodeGbaName(bytes, 0, "??????????")
        encodeGbaName(bytes, 11, "CHARIZARD")
        val stats = 64
        bytes[stats + 28 + 22] = 66
        bytes[stats + 28 + 23] = 0

        val charizard = RecordMaterializers.species(RomImage(bytes), gbaLayout(stats)).getValue(1)

        assertEquals(listOf(66), charizard.abilityIds.value)
    }

    @Test
    fun doesNotMaterializeReservedZeroBaseStatRows() {
        val bytes = ByteArray(256) { 0xFF.toByte() }
        encodeGbaName(bytes, 0, "??????????")
        encodeGbaName(bytes, 11, "RESERVED")

        val reserved = RecordMaterializers.species(RomImage(bytes), gbaLayout(64)).getValue(1)

        assertNull(reserved.baseStats.value)
        assertNull(reserved.typeIds.value)
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
        val layout = gbaLayout(80).copy(
            moveCount = 2,
            tables = gbaLayout(80).tables.copy(
                moveNames = TableLayout(0, 2, 13),
                moveData = TableLayout(data, 2, 12),
            ),
        )

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
        val layout = gbaLayout(96).copy(
            moveCount = 2,
            tables = gbaLayout(96).tables.copy(
                moveNames = TableLayout(0, 2, 13),
                moveData = TableLayout(data, 2, 16, format = TableRecordFormat.CFRU_MOVE_16),
            ),
        )

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
        val layout = gbaLayout(40).copy(
            tables = gbaLayout(40).tables.copy(abilities = TableLayout(0, 2, 13)),
        )

        val abilities = RecordMaterializers.abilities(RomImage(bytes), layout)

        assertEquals("OVERGROW", abilities.getValue(1).name.value)
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

    private fun gbaLayout(statsOffset: Int) = ResolvedRomLayout(
        family = EngineFamily.EMERALD,
        generation = 3,
        platform = Platform.GBA,
        speciesCount = 3,
        moveCount = 0,
        tables = ProfileTables(
            speciesNames = TableLayout(0, 3, 11),
            baseStats = TableLayout(statsOffset, 3, 28),
        ),
    )

    private fun encodeGbaName(target: ByteArray, offset: Int, value: String) {
        value.forEachIndexed { index, char ->
            target[offset + index] = when (char) {
                in 'A'..'Z' -> (0xBB + char.code - 'A'.code).toByte()
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
