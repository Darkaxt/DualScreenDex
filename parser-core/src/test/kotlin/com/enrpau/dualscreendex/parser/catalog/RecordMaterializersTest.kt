package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import org.junit.Assert.assertEquals
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
        bytes[stats + 28 + 22] = 7
        bytes[stats + 28 + 23] = 9

        val records = RecordMaterializers.species(RomImage(bytes), gbaLayout(stats))
        val bulba = records.getValue(1)

        assertEquals("BULBA", bulba.name.value)
        assertEquals(listOf(12, 3), bulba.typeIds.value)
        assertEquals(BaseStats(45, 49, 49, 45, 65, 65), bulba.baseStats.value)
        assertEquals(listOf(7, 9), bulba.abilityIds.value)
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
}
