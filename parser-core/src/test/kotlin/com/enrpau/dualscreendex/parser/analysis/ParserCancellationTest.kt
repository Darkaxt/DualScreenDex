package com.enrpau.dualscreendex.parser.analysis

import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.parse.Gen2CompiledSpriteResolver
import com.enrpau.dualscreendex.parser.sprite.PngEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserCancellationTest {
    @Test
    fun sourceCancelsItsTokenAndInterruptedThreadsAlsoCancel() {
        val source = ParserCancellationSource()

        source.cancel()

        assertTrue(source.isCancellationRequested)
        assertThrows(ParserCancellationException::class.java) {
            source.token.throwIfCancellationRequested()
        }

        val interrupted = ParserCancellationSource()
        Thread.currentThread().interrupt()
        try {
            assertThrows(ParserCancellationException::class.java) {
                interrupted.token.throwIfCancellationRequested()
            }
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun gbaReferenceScanChecksCancellationAtFixedByteIntervals() {
        var checks = 0
        val cancellation = ParserCancellationToken {
            checks++
            if (checks == 2) throw ParserCancellationException()
        }

        assertThrows(ParserCancellationException::class.java) {
            SafeGbaReferenceIndexBuilder.build(
                RomImage(ByteArray(8_192)),
                ResolutionLimits(),
                cancellation,
            )
        }

        assertEquals(2, checks)
    }

    @Test
    fun gen2CompiledSpriteScanChecksCancellationInsideDenseOpcodeData() {
        var checks = 0
        val cancellation = ParserCancellationToken {
            checks++
            if (checks == 3) throw ParserCancellationException()
        }

        assertThrows(ParserCancellationException::class.java) {
            Gen2CompiledSpriteResolver.resolve(
                RomImage(ByteArray(16_384) { 0xFA.toByte() }),
                speciesCount = 251,
                cancellation = cancellation,
                limits = ResolutionLimits(maxProbeWorkPerDataset = 8_192),
            )
        }

        assertEquals(3, checks)
    }

    @Test(timeout = 5_000)
    fun gen2CompiledSpriteScanFailsClosedAtItsMatchBudget() {
        val result = Gen2CompiledSpriteResolver.resolve(
            RomImage(ByteArray(RomImage.MAX_SIZE_BYTES) { 0xFA.toByte() }),
            speciesCount = 251,
            limits = ResolutionLimits(
                maxProbeRootsPerDataset = 8,
                maxProbeWorkPerDataset = 32,
                maxCandidatesPerDataset = 4,
            ),
        )

        assertNull(result)
    }

    @Test(timeout = 5_000)
    fun gen2CompiledSpriteValidationConsumesTheSharedWorkBudgetBeforeAcceptance() {
        val result = Gen2CompiledSpriteResolver.resolve(
            validCompiledGen2SpriteConsumer(speciesCount = 251),
            speciesCount = 251,
            limits = ResolutionLimits(
                maxProbeRootsPerDataset = 8,
                maxProbeWorkPerDataset = 4,
                maxCandidatesPerDataset = 4,
            ),
        )

        assertNull(result)
    }

    @Test
    fun pngEncodingChecksCancellationDuringRasterRows() {
        var checks = 0
        val cancellation = ParserCancellationToken {
            checks++
            if (checks == 4) throw ParserCancellationException()
        }

        assertThrows(ParserCancellationException::class.java) {
            PngEncoder.encode(
                RgbaSprite(width = 8, height = 8, argb = IntArray(64)),
                cancellation,
            )
        }

        assertEquals(4, checks)
    }

    private fun validCompiledGen2SpriteConsumer(speciesCount: Int): RomImage {
        val bytes = ByteArray(0x10000)
        bytes[0] = 0xFA.toByte()
        putU16(bytes, 1, 0xC000)
        bytes[3] = 0xFE.toByte()
        bytes[4] = speciesCount.toByte()
        bytes[5] = 0x28
        bytes[6] = 7
        bytes[7] = 0xFA.toByte()
        putU16(bytes, 8, 0xC000)
        bytes[10] = 0x16
        bytes[11] = 1
        bytes[12] = 0x18
        bytes[13] = 5
        bytes[14] = 0xFA.toByte()
        bytes[17] = 0x16
        bytes[18] = 2
        bytes[19] = 0x21
        putU16(bytes, 20, 0x5000)
        bytes[22] = 0x3D
        bytes[23] = 0x01
        putU16(bytes, 24, 6)
        bytes[26] = 0xCD.toByte()
        bytes[29] = 0x7A
        bytes[30] = 0xCD.toByte()
        bytes[33] = 0xF5.toByte()
        bytes[34] = 0x23
        bytes[35] = 0x7A
        bytes[36] = 0xCD.toByte()
        bytes[39] = 0xC1.toByte()
        bytes[40] = 0xC9.toByte()

        fun fillTable(offset: Int, count: Int) {
            repeat(count) { index ->
                val record = offset + index * 6
                bytes[record] = 3
                putU16(bytes, record + 1, 0x5000)
                bytes[record + 3] = 3
                putU16(bytes, record + 4, 0x5000)
            }
        }
        fillTable(0x5000, speciesCount)
        fillTable(0x9000, 26)
        bytes[0xD000] = 0x60
        bytes[0xD001] = 0xFF.toByte()
        return RomImage.consume(bytes)
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }
}
