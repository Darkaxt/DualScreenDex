package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AbilityDescriptionMaterializerTest {
    @Test
    fun decodesAValidatedPointerTableAdjacentToAbilityNames() {
        val bytes = ByteArray(0x1000) { 0xFF.toByte() }
        val namesOffset = 0x100
        val descriptionsOffset = 0x134
        listOf("NO SPECIAL ABILITY", "HELPS REPEL WILD POKEMON", "SUMMONS RAIN IN BATTLE", "BOOSTS SPEED EACH TURN")
            .forEachIndexed { id, description ->
                val textOffset = 0x400 + id * 0x40
                putGbaPointer(bytes, descriptionsOffset + id * 4, textOffset)
                encodeGbaText(bytes, textOffset, description)
            }

        val result = AbilityDescriptionMaterializer.materialize(RomImage(bytes), layout(namesOffset))

        assertEquals(descriptionsOffset, result?.sourceOffset)
        assertEquals("HELPS REPEL WILD POKEMON", result?.descriptions?.get(1))
        assertEquals("BOOSTS SPEED EACH TURN", result?.descriptions?.get(3))
    }

    @Test
    fun decodesAValidatedPointerTableOutsideTheNamesSearchRadius() {
        val bytes = ByteArray(0x24000) { 0xFF.toByte() }
        val descriptionsOffset = 0x22000
        listOf("NO SPECIAL ABILITY", "HELPS REPEL WILD POKEMON", "SUMMONS RAIN IN BATTLE", "BOOSTS SPEED EACH TURN")
            .forEachIndexed { id, description ->
                val textOffset = 0x23000 + id * 0x40
                putGbaPointer(bytes, descriptionsOffset + id * 4, textOffset)
                encodeGbaText(bytes, textOffset, description)
            }

        val result = AbilityDescriptionMaterializer.materialize(RomImage(bytes), layout(0x100))

        assertEquals(descriptionsOffset, result?.sourceOffset)
        assertEquals("SUMMONS RAIN IN BATTLE", result?.descriptions?.get(2))
    }

    @Test
    fun rejectsAPointerTableWithUndecodableDescriptions() {
        val bytes = ByteArray(0x1000)
        val descriptionsOffset = 0x134
        repeat(4) { id ->
            val textOffset = 0x400 + id * 0x40
            putGbaPointer(bytes, descriptionsOffset + id * 4, textOffset)
        }

        assertNull(AbilityDescriptionMaterializer.materialize(RomImage(bytes), layout(0x100)))
    }

    private fun layout(namesOffset: Int) = ResolvedRomLayout(
        family = EngineFamily.EMERALD,
        generation = 3,
        platform = Platform.GBA,
        speciesCount = 4,
        moveCount = 4,
        tables = ProfileTables(abilities = TableLayout(namesOffset, 4, 13)),
    )

    private fun encodeGbaText(target: ByteArray, offset: Int, value: String) {
        value.forEachIndexed { index, char ->
            target[offset + index] = when (char) {
                ' ' -> 0
                in 'A'..'Z' -> (0xBB + char.code - 'A'.code).toByte()
                else -> error("unsupported fixture character")
            }
        }
        target[offset + value.length] = 0xFF.toByte()
    }

    private fun putGbaPointer(target: ByteArray, offset: Int, targetOffset: Int) {
        val value = 0x08000000 + targetOffset
        repeat(4) { index -> target[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
