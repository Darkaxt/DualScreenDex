package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MoveDescriptionMaterializerTest {
    @Test
    fun selectsTheCompiledReferencedTableAndRetainsExplicitBlankDescriptions() {
        val bytes = ByteArray(0x2000)
        val adjacentDecoy = 0x0FC
        val tableOffset = 0x100
        putGbaPointer(bytes, adjacentDecoy, 0x700)
        encodeGbaText(bytes, 0x700, "No move information.")
        listOf("A small flame attack.", "-", "Raises the user's Defense.", "A strong water attack.")
            .forEachIndexed { index, value ->
                val textOffset = 0x800 + index * 0x80
                putGbaPointer(bytes, tableOffset + index * 4, textOffset)
                encodeGbaText(bytes, textOffset, value)
            }
        val references = GbaReferenceIndex.countsOnlyForTesting(mapOf(tableOffset to 2))

        val result = MoveDescriptionMaterializer.materialize(
            RomImage(bytes),
            layout(moveCount = 5),
            references,
        )

        assertEquals(tableOffset, result?.sourceOffset)
        assertEquals(4, result?.descriptions?.size)
        assertEquals("-", result?.descriptions?.get(2))
        assertEquals("A strong water attack.", result?.descriptions?.get(4))
    }

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
    fun decodesSparseGbaMoveDescriptionPointerTable() {
        val bytes = ByteArray(0x2000)
        val tableOffset = 0x100
        val descriptions = listOf(
            "A small flame attack.",
            "Raises the user's Defense.",
            "Lowers the foe's accuracy.",
            "A strong water attack.",
            "May lower the foe's Defense.",
            null,
            null,
            "A quick electric attack.",
            "Raises the user's Speed.",
            "May lower the foe's Speed.",
        )
        descriptions.forEachIndexed { index, text ->
            if (text == null) return@forEachIndexed
            val textOffset = 0x800 + index * 0x40
            putGbaPointer(bytes, tableOffset + index * 4, textOffset)
            encodeGbaText(bytes, textOffset, text)
        }
        putInt(bytes, tableOffset + 6 * 4, 0x12345678)

        val result = MoveDescriptionMaterializer.materialize(RomImage(bytes), layout(moveCount = 11))

        assertEquals(tableOffset, result?.sourceOffset)
        assertEquals(8, result?.descriptions?.size)
        assertEquals("May lower the foe's Speed.", result?.descriptions?.get(10))
    }

    @Test
    fun fallbackPointerScanChecksCancellationAtFixedIntervals() {
        var checks = 0
        val cancellation = ParserCancellationToken {
            checks++
            if (checks == 3) throw ParserCancellationException()
        }

        assertThrows(ParserCancellationException::class.java) {
            MoveDescriptionMaterializer.materialize(
                RomImage(ByteArray(16_384) { 0x08 }),
                layout(moveCount = 4),
                cancellation = cancellation,
                limits = ResolutionLimits(maxProbeWorkPerDataset = 128),
            )
        }

        assertEquals(3, checks)
    }

    @Test(timeout = 5_000)
    fun denseFallbackPointerDataFailsOnlyTheOptionalCapabilityAtItsBudget() {
        val result = MoveDescriptionMaterializer.materialize(
            RomImage(ByteArray(RomImage.MAX_SIZE_BYTES) { 0x08 }),
            layout(moveCount = 4),
            limits = ResolutionLimits(
                maxProbeRootsPerDataset = 16,
                maxProbeWorkPerDataset = 64,
                maxCandidatesPerDataset = 8,
            ),
        )

        assertNull(result)
    }

    @Test
    fun rejectsMoveCountWhosePointerTableCannotFitInTheRom() {
        val bytes = ByteArray(0x100)
        putGbaPointer(bytes, 0x20, 0x80)

        assertNull(MoveDescriptionMaterializer.materialize(RomImage(bytes), layout(moveCount = Int.MAX_VALUE)))
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
        putInt(target, offset, value)
    }

    private fun putInt(target: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> target[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
