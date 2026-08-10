package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeciesIndexResolverTest {
    @Test
    fun findsGenOneInternalToDexPermutationWithMissingSlots() {
        val bytes = byteArrayOf(0x7F, 0x7F, 1, 0, 3, 2, 0x7F)
        val layout = ResolvedRomLayout(
            EngineFamily.RED_BLUE,
            generation = 1,
            platform = Platform.GB,
            speciesCount = 4,
            moveCount = 1,
            tables = ProfileTables(
                speciesNames = TableLayout(0, 4, 10),
                baseStats = TableLayout(0, 3, 28),
            ),
        )

        assertEquals(
            mapOf(1 to 1, 2 to 0, 3 to 3, 4 to 2),
            SpeciesIndexResolver.resolve(RomImage(bytes), layout),
        )
    }

    @Test
    fun findsGbaU16SpeciesToNationalDexTableWithReservedSlot() {
        val bytes = ByteArray(32) { 0x7F }
        val values = intArrayOf(1, 2, 0, 3)
        values.forEachIndexed { index, value ->
            bytes[4 + index * 2] = value.toByte()
            bytes[5 + index * 2] = (value ushr 8).toByte()
        }
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = 5,
            moveCount = 1,
            tables = ProfileTables(),
        )

        assertEquals(
            mapOf(0 to 0, 1 to 1, 2 to 2, 3 to 0, 4 to 3),
            SpeciesIndexResolver.resolve(RomImage(bytes), layout),
        )
    }

    @Test
    fun distinguishesSpeciesToDexTableFromItsReverseAtGenThreeBoundary() {
        val count = 413
        val bytes = ByteArray(2200) { 0x7F }
        val speciesToDex = IntArray(count) { index ->
            val id = index + 1
            when (id) {
                in 1..251 -> id
                in 252..276 -> 185 + id
                else -> id - 25
            }
        }
        val dexToSpecies = IntArray(count) { index ->
            val id = index + 1
            when (id) {
                in 1..251 -> id
                in 252..388 -> id + 25
                else -> id - 137
            }
        }
        speciesToDex.forEachIndexed { index, value -> putU16(bytes, index * 2, value) }
        dexToSpecies.forEachIndexed { index, value -> putU16(bytes, 1000 + index * 2, value) }
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = count + 1,
            moveCount = 1,
            tables = ProfileTables(),
        )

        val result = SpeciesIndexResolver.resolve(RomImage(bytes), layout)

        assertEquals(252, result[277])
        assertEquals(279, result[304])
    }

    @Test
    fun findsExpandedGbaPermutationWhenTheFirstInternalSpeciesIsNotDexOne() {
        val count = 412
        val bytes = ByteArray(1800) { 0x7F }
        val mapping = IntArray(count) { it }
        for (internalId in 1..276) mapping[internalId] = internalId + 135
        for (internalId in 277..411) mapping[internalId] = internalId - 276
        mapping.forEachIndexed { index, value -> putU16(bytes, 128 + index * 2, value) }
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = count,
            moveCount = 1,
            tables = ProfileTables(),
        )

        val result = SpeciesIndexResolver.resolve(RomImage(bytes), layout)

        assertEquals(136, result[1])
        assertEquals(1, result[277])
        assertEquals(135, result[411])
    }

    @Test
    fun canonicalSpeciesToDexTableWinsOverAnUnrelatedCompletePermutation() {
        val speciesCount = 412
        val bytes = ByteArray(2200) { 0x7F }
        val unrelatedPermutation = IntArray(speciesCount) { index ->
            if (index == 0) 0 else ((index + 201) % (speciesCount - 1)) + 1
        }
        val speciesToDex = IntArray(speciesCount - 1) { index ->
            val internalId = index + 1
            when (internalId) {
                in 1..251 -> internalId
                in 252..276 -> internalId + 135
                else -> internalId - 25
            }
        }
        unrelatedPermutation.forEachIndexed { index, value -> putU16(bytes, 64 + index * 2, value) }
        speciesToDex.forEachIndexed { index, value -> putU16(bytes, 1100 + index * 2, value) }
        val layout = ResolvedRomLayout(
            EngineFamily.RUBY_SAPPHIRE,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = speciesCount,
            moveCount = 1,
            tables = ProfileTables(),
        )

        val result = SpeciesIndexResolver.resolve(RomImage(bytes), layout)

        assertEquals(1, result[1])
        assertEquals(252, result[277])
    }

    @Test
    fun ignoresPrefixMatchedTablesThatReuseManyPositiveDexNumbers() {
        val bytes = ByteArray(64) { 0x7F }
        listOf(1, 2, 3, 4, 5, 6, 1, 2).forEachIndexed { index, value ->
            putU16(bytes, 8 + index * 2, value)
        }
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = 9,
            moveCount = 1,
            tables = ProfileTables(),
        )

        val result = SpeciesIndexResolver.resolve(RomImage(bytes), layout)

        assertEquals(7, result[7])
        assertEquals(8, result[8])
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }
}
