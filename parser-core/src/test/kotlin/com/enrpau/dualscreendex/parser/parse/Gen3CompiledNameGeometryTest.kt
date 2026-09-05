package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs
import org.junit.Assert.assertEquals
import org.junit.Test

class Gen3CompiledNameGeometryTest {
    @Test fun recoversBothRootsFromCompleteScaledStringConsumersWithoutPublishedHeader() {
        val result = GbaPublishedHeaderResolver.resolve(RomImage(nativeGbaNameGeometry()), JapanesePokemonTextCodecs.gen3RubySapphire)
        assertEquals(0x2000, result.speciesNames)
        assertEquals(0x2018, result.moveNames)
        assertEquals(6, result.nameGeometry.speciesNames?.recordSize)
        assertEquals(8, result.nameGeometry.moveNames?.recordSize)
        assertEquals(4, result.nameGeometry.speciesNames?.count)
        assertEquals(4, result.nameGeometry.moveNames?.count)
    }

    @Test fun rejectsContradictoryPublishedAndCompiledRoots() {
        val bytes = nativeGbaNameGeometry()
        putPointer(bytes, 0x144, 0x2400)
        val result = GbaPublishedHeaderResolver.resolve(RomImage(bytes), JapanesePokemonTextCodecs.gen3Later)
        org.junit.Assert.assertTrue(result.nameGeometry.ambiguous)
        org.junit.Assert.assertNull(result.speciesNames)
        org.junit.Assert.assertNull(result.moveNames)
    }

    @Test fun competingCompletePairsCannotFallBackToPublishedRoot() {
        val bytes = nativeGbaNameGeometry().copyOf(0x6000)
        putGbaNameConsumer(bytes, 0x400, 0x4000, false)
        putGbaNameConsumer(bytes, 0x500, 0x4018, true)
        bytes.copyInto(bytes, 0x4000, 0x2000, 0x2038)
        putPointer(bytes, 0x144, 0x2000)
        putPointer(bytes, 0x148, 0x2018)
        val result = GbaPublishedHeaderResolver.resolve(RomImage(bytes), JapanesePokemonTextCodecs.gen3Later)
        org.junit.Assert.assertTrue(result.nameGeometry.ambiguous)
        org.junit.Assert.assertNull(result.speciesNames)
    }

    @Test fun rejectsBrokenArithmeticOutOfBoundsRootsAndTruncatedControls() {
        val broken = nativeGbaNameGeometry()
        broken[0x200 + 22 + 16] = 0x89.toByte() // x6 no longer proved
        org.junit.Assert.assertNull(GbaPublishedHeaderResolver.resolve(RomImage(broken), JapanesePokemonTextCodecs.gen3Later).speciesNames)
        val bounds = nativeGbaNameGeometry()
        putPointer(bounds, 0x240, 0x4000)
        org.junit.Assert.assertNull(GbaPublishedHeaderResolver.resolve(RomImage(bounds), JapanesePokemonTextCodecs.gen3Later).speciesNames)
        val truncated = nativeGbaNameGeometry()
        truncated[0x2005] = 0xfd.toByte()
        org.junit.Assert.assertNull(GbaPublishedHeaderResolver.resolve(RomImage(truncated), JapanesePokemonTextCodecs.gen3Later).speciesNames)
    }

    @Test fun cancellationPrecedesEmptyInputAndInterruptsConsumerScan() {
        for (cancelAt in listOf(1, 19)) {
            var checks = 0
            val token = ParserCancellationToken { if (++checks == cancelAt) throw ParserCancellationException() }
            org.junit.Assert.assertThrows(ParserCancellationException::class.java) {
                GbaPublishedHeaderResolver.resolve(RomImage(nativeGbaNameGeometry()), JapanesePokemonTextCodecs.gen3Later, token)
            }
        }
    }

    private fun putPointer(bytes: ByteArray, offset: Int, target: Int) {
        repeat(4) { bytes[offset + it] = ((target + 0x08000000) ushr (it * 8)).toByte() }
    }
}

/** Synthetic script-buffer name consumers, matching the independently verified native x6/x8 ABIs. */
internal fun nativeGbaNameGeometry(): ByteArray {
    val bytes = ByteArray(0x3000)
    putGbaNameConsumer(bytes, 0x200, 0x2000, move = false)
    putGbaNameConsumer(bytes, 0x300, 0x2018, move = true)
    repeat(4) { i ->
        repeat(5) { j -> bytes[0x2000 + i * 6 + j] = (1 + i + j).toByte() }
        bytes[0x2000 + i * 6 + 5] = 0xff.toByte()
        repeat(7) { j -> bytes[0x2018 + i * 8 + j] = (1 + i + j).toByte() }
        bytes[0x2018 + i * 8 + 7] = 0xff.toByte()
    }
    return bytes
}

internal fun putGbaNameConsumer(bytes: ByteArray, offset: Int, root: Int, move: Boolean) {
    val prefix = intArrayOf(0xb510, 0x6881, 0x780c, 0x3101, 0x6081, 0xf000, 0xf800,
        0x0400, 0x0c00, 0xf000, 0xf800)
    val body = if (move) intArrayOf(0x1c01, 0x0409, 0x4806, 0x00a4, 0x1824, 0x6820,
        0x0b49, 0x4a04, 0x1889, 0xf000, 0xf800, 0x2000, 0xbc10, 0xbc02, 0x4708)
    else intArrayOf(0x0400, 0x0c00, 0x4908, 0x00a4, 0x1864, 0x6822, 0x0041, 0x1809,
        0x0049, 0x4805, 0x1809, 0x1c10, 0xf000, 0xf800, 0x2000, 0xbc10, 0xbc02, 0x4708, 0)
    (prefix + body).forEachIndexed { i, value ->
        bytes[offset + i * 2] = value.toByte()
        bytes[offset + i * 2 + 1] = (value ushr 8).toByte()
    }
    val literal = offset + if (move) 56 else 64
    val pointer = root + 0x08000000
    repeat(4) { bytes[literal + it] = (pointer ushr (it * 8)).toByte() }
}
