package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoveDescriptionMaterializerTest {
    @Test
    fun decodesAValidatedGbaMoveDescriptionPointerTable() {
        val bytes = ByteArray(0x1000)
        val tableOffset = 0x100
        listOf("A small flame attack.", "Raises the user's Defense.", "Lowers the foe's accuracy.").forEachIndexed { index, text ->
            val textOffset = 0x400 + index * 0x40
            putGbaPointer(bytes, tableOffset + index * 4, textOffset)
            encodeGbaText(bytes, textOffset, text)
        }

        val result = MoveDescriptionMaterializer.materialize(RomImage(bytes), layout(moveCount = 4))

        assertEquals(tableOffset, result?.sourceOffset)
        assertEquals("A small flame attack.", result?.descriptions?.get(1))
        assertEquals("Lowers the foe's accuracy.", result?.descriptions?.get(3))
    }

    @Test
    fun rejectsPointerTablesWithUndecodableText() {
        val bytes = ByteArray(0x800)
        repeat(3) { index -> putGbaPointer(bytes, 0x100 + index * 4, 0x400 + index * 0x40) }

        assertNull(MoveDescriptionMaterializer.materialize(RomImage(bytes), layout(moveCount = 4)))
    }

    @Test
    fun rejectsReadableMusicIdentifierPointerTable() {
        val bytes = ByteArray(0x1000)
        listOf("MUS-PL-TY-BROADCAST", "MUS-HG-NEW-BARK", "BW-SEQ-BGM-PALPARK").forEachIndexed { index, text ->
            val textOffset = 0x400 + index * 0x40
            putGbaPointer(bytes, 0x100 + index * 4, textOffset)
            encodeGbaText(bytes, textOffset, text)
        }

        assertNull(MoveDescriptionMaterializer.materialize(RomImage(bytes), layout(moveCount = 4)))
    }

    private fun layout(moveCount: Int) = ResolvedRomLayout(
        family = EngineFamily.EMERALD,
        generation = 3,
        platform = Platform.GBA,
        speciesCount = 4,
        moveCount = moveCount,
        tables = ProfileTables(),
    )

    private fun encodeGbaText(target: ByteArray, offset: Int, value: String) {
        value.forEachIndexed { index, char ->
            target[offset + index] = when (char) {
                ' ' -> 0
                in 'A'..'Z' -> (0xBB + char.code - 'A'.code).toByte()
                in 'a'..'z' -> (0xD5 + char.code - 'a'.code).toByte()
                '-' -> 0xAE.toByte()
                '.' -> 0xAD.toByte()
                '\'' -> 0xB4.toByte()
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
