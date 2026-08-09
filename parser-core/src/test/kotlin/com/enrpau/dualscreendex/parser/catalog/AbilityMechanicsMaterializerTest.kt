package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AbilityMechanicsMaterializerTest {
    @Test
    fun resolvesValidatedPinchAbilityThresholdAndMultiplierFromThumbCode() {
        val bytes = ByteArray(0x1000)
        val code = 0x200
        val comparisons = listOf(12, 65, 10, 66, 11, 67, 6, 68)
        comparisons.forEachIndexed { index, immediate -> putHalfword(bytes, code + index * 2, 0x2800 or immediate) }
        putHalfword(bytes, code + 0x40, 0x2103) // movs r1, #3
        putHalfword(bytes, code + 0x42, 0x8DA8) // ldrh r0, [r5, #44]
        putHalfword(bytes, code + 0x44, 0xF000) // bl first half
        putHalfword(bytes, code + 0x46, 0xF800) // bl second half
        putHalfword(bytes, code + 0x50, 0x2096) // movs r0, #150
        putHalfword(bytes, code + 0x58, 0x2164) // movs r1, #100
        // An unrelated immediate 3 and later 150/100 pair in the same function
        // must not make the ROM-code result ambiguous.
        putHalfword(bytes, code + 0x80, 0x2203)
        putHalfword(bytes, code + 0x90, 0x2096)
        putHalfword(bytes, code + 0x98, 0x2164)
        val abilities = mapOf(
            65 to ability(65, "Overgrow"), 66 to ability(66, "Blaze"),
            67 to ability(67, "Torrent"), 68 to ability(68, "Swarm"),
        )
        val types = mapOf(
            6 to type(6, "Bug"), 10 to type(10, "Fire"),
            11 to type(11, "Water"), 12 to type(12, "Grass"),
        )

        val result = AbilityMechanicsMaterializer.materialize(RomImage(bytes), layout(), abilities, types)
        val blaze = result?.mechanicsByAbility?.get(66).orEmpty()

        assertEquals(listOf("HP ≤ 1/3", "Fire move power ×1.5"), blaze.map { it.value })
        assertEquals(3, blaze.first().denominator)
        assertEquals(150, blaze.last().numerator)
    }

    @Test
    fun resolvesOfficialGenThreeRepeatedDivisionBlocks() {
        val bytes = ByteArray(0x1000)
        val code = 0x200
        val definitions = listOf(12 to 65, 10 to 66, 11 to 67, 6 to 68)
        definitions.forEachIndexed { index, (typeId, abilityId) ->
            val block = code + index * 0x30
            putHalfword(bytes, block, 0x2800 or typeId)
            putHalfword(bytes, block + 2, 0x2800 or abilityId)
            putHalfword(bytes, block + 4, 0x2103) // movs r1, #3
            putHalfword(bytes, block + 6, 0xF000) // bl unsigned division, first half
            putHalfword(bytes, block + 8, 0xF800) // bl unsigned division, second half
            putHalfword(bytes, block + 10, 0x8D31) // ldrh after the division in the official build
            putHalfword(bytes, block + 12, 0x2096) // movs r0, #150
            putHalfword(bytes, block + 14, 0x2164) // movs r1, #100
        }
        val abilities = definitions.associate { (_, abilityId) ->
            abilityId to ability(abilityId, mapOf(65 to "Overgrow", 66 to "Blaze", 67 to "Torrent", 68 to "Swarm").getValue(abilityId))
        }
        val types = definitions.associate { (typeId, _) ->
            typeId to type(typeId, mapOf(6 to "Bug", 10 to "Fire", 11 to "Water", 12 to "Grass").getValue(typeId))
        }

        val result = AbilityMechanicsMaterializer.materialize(RomImage(bytes), layout(), abilities, types)

        assertEquals(4, result?.mechanicsByAbility?.size)
        assertEquals("Fire move power ×1.5", result?.mechanicsByAbility?.get(66)?.last()?.value)
    }

    @Test
    fun rejectsUncorroboratedNumericConstants() {
        val bytes = ByteArray(0x400)
        putHalfword(bytes, 0x100, 0x2103)
        putHalfword(bytes, 0x110, 0x2096)
        putHalfword(bytes, 0x118, 0x2164)

        assertNull(
            AbilityMechanicsMaterializer.materialize(
                RomImage(bytes), layout(), mapOf(66 to ability(66, "Blaze")), mapOf(10 to type(10, "Fire")),
            ),
        )
    }

    private fun ability(id: Int, name: String) = AbilityRecord(id, CatalogField.available(name))
    private fun type(id: Int, name: String) = TypeRecord(id, CatalogField.available(name))

    private fun layout() = ResolvedRomLayout(
        EngineFamily.EMERALD, 3, Platform.GBA, 4, 4, ProfileTables(),
    )

    private fun putHalfword(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
    }
}
