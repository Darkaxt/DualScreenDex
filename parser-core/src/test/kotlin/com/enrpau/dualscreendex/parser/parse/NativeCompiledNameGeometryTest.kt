package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeCompiledNameGeometryTest {
    @Test fun resolvesFiveByteInlineBankCopyConsumer() {
        val bytes = nativeGbNames(generation = 1)
        val result = Gen1CompiledNameResolver.resolve(RomImage(bytes), 3, JapanesePokemonTextCodecs.gen1RedBlue)
        assertEquals(0x8000, result?.offset)
        assertEquals(5, result?.recordSize)
        assertEquals(3, result?.count)
    }

    @Test fun resolvesFiveByteHelperBankCopyConsumer() {
        val bytes = nativeGbNames(generation = 1, helper = true)
        val result = Gen1CompiledNameResolver.resolve(RomImage(bytes), 3, JapanesePokemonTextCodecs.gen1Yellow)
        assertEquals(0x8000, result?.offset)
        assertEquals(5, result?.recordSize)
    }

    @Test fun resolvesGenTwoFiveByteConsumerAndAdjacentFarPointers() {
        val result = Gen2CompiledNamePairResolver.resolve(
            RomImage(nativeGbNames(generation = 2)), 3, 3,
            JapanesePokemonTextCodecs.gen2, ParserCancellationToken.NONE,
        )
        assertEquals(0x8000, result?.speciesNames?.offset)
        assertEquals(5, result?.speciesNames?.recordSize)
        assertEquals(0x9000, result?.moveNames?.offset)
    }

    @Test fun rejectsBrokenHelperAndMismatchedCopyWidth() {
        val helper = nativeGbNames(generation = 1, helper = true)
        helper[0x502] = 0x00
        assertNull(Gen1CompiledNameResolver.resolve(RomImage(helper), 3, JapanesePokemonTextCodecs.gen1Yellow))
        val inline = nativeGbNames(generation = 1)
        // Copy count disagrees with the five additions establishing the source stride.
        inline[0x100 + 31] = 6
        assertNull(Gen1CompiledNameResolver.resolve(RomImage(inline), 3, JapanesePokemonTextCodecs.gen1RedBlue))
    }

    @Test fun rejectsUnreferencedGenTwoFarPointerPair() {
        val bytes = nativeGbNames(generation = 2)
        bytes.fill(0, 0x350, 0x36b)
        assertNull(Gen2CompiledNamePairResolver.resolve(
            RomImage(bytes), 3, 3, JapanesePokemonTextCodecs.gen2, ParserCancellationToken.NONE,
        ))
    }

    @Test fun rejectsGenTwoPairContradictingTheCopyConsumerWidth() {
        val bytes = nativeGbNames(generation = 2)
        bytes[0x100 + 27] = 6
        assertNull(Gen2CompiledNamePairResolver.resolve(
            RomImage(bytes), 3, 3, JapanesePokemonTextCodecs.gen2, ParserCancellationToken.NONE,
        ))
    }

    @Test fun resolvesTenByteRepeatedAddGenTwoConsumer() {
        val result = Gen2CompiledNamePairResolver.resolve(
            RomImage(nativeGbNames(generation = 2, width = 10)), 3, 3,
            JapanesePokemonTextCodecs.gen2, ParserCancellationToken.NONE,
        )
        assertEquals(10, result?.speciesNames?.recordSize)
    }

    @Test fun propagatesCancellationBeforeEarlyRejectionAndDuringHomeBankScan() {
        for (cancelAt in listOf(1, 37)) {
            var checks = 0
            val cancellation = ParserCancellationToken {
                if (++checks == cancelAt) throw com.enrpau.dualscreendex.parser.analysis.ParserCancellationException()
            }
            org.junit.Assert.assertThrows(com.enrpau.dualscreendex.parser.analysis.ParserCancellationException::class.java) {
                Gen1CompiledNameResolver.resolve(RomImage(nativeGbNames(1)), 3, JapanesePokemonTextCodecs.gen1RedBlue, cancellation)
            }
            assertEquals(cancelAt, checks)
        }
    }

    @Test fun nativeTenBytePairCannotFallBackAfterItsConsumerIsRemoved() {
        val bytes = nativeGbNames(2, 10)
        bytes.fill(0, 0x100, 0x180)
        assertNull(Gen2CompiledNamePairResolver.resolve(RomImage(bytes), 3, 3, JapanesePokemonTextCodecs.gen2, ParserCancellationToken.NONE))
    }

    @Test fun fixedWidthControlTokensCannotPretendToBeCompleteGlyphNames() {
        val bytes = nativeGbNames(1)
        repeat(3) { bytes[0x8000 + it * 5 + 2] = 0x4f }
        assertNull(Gen1CompiledNameResolver.resolve(RomImage(bytes), 3, JapanesePokemonTextCodecs.gen1RedBlue))
    }

    @Test fun genTwoCancellationInterruptsRecordDecodingNotOnlyTheNextScan() {
        var decoded = 0
        val delegate = JapanesePokemonTextCodecs.gen2
        val codec = com.enrpau.dualscreendex.parser.text.PokemonTextCodec("cancel-test", 1, delegate.language,
            delegate.applicableGenerations, delegate.applicablePlatforms, delegate.terminator) { rom, offset, end ->
            decoded++
            delegate.decodeToken(rom, offset, end)
        }
        val cancellation = ParserCancellationToken {
            if (decoded > 0) throw com.enrpau.dualscreendex.parser.analysis.ParserCancellationException()
        }
        org.junit.Assert.assertThrows(com.enrpau.dualscreendex.parser.analysis.ParserCancellationException::class.java) {
            Gen2CompiledNamePairResolver.resolve(RomImage(nativeGbNames(2)), 3, 3, codec, cancellation)
        }
        assertEquals(1, decoded)
    }

    @Test fun rejectsCompetingCompleteNativeSpeciesConsumers() {
        val bytes = nativeGbNames(generation = 1)
        nativeGbConsumer(1, root = 0x5000).copyInto(bytes, 0x200)
        bytes.copyInto(bytes, 0x9000, 0x8000, 0x800f)
        assertNull(Gen1CompiledNameResolver.resolve(RomImage(bytes), 3, JapanesePokemonTextCodecs.gen1RedBlue))
    }
}

/** Source-derived instruction shapes; synthetic addresses and text, never copied control payloads.
 * RB: pokered-jp 258d1a89/home.asm GetMonName; Yellow: pokeyellow-jp f282e72a/home/names.asm.
 * Gen II: native verified five/ten-add GetPokemonName and home/names.asm NamesPointers ABI.
 */
internal fun nativeGbConsumer(generation: Int, width: Int = 5, helper: Boolean = false, root: Int = 0x4000): ByteArray {
    val result = mutableListOf<Int>()
    fun put(vararg values: Int) { result.addAll(values.toList()) }
    if (generation == 1) {
        put(0xe5, 0xf0, 0xb8, 0xf5, 0x3e, 2)
        if (helper) put(0xcd, 0x00, 0x05) else put(0xe0, 0xb8, 0xea, 0x00, 0x20)
    } else put(0xf0, 0x9f, 0xf5, 0xe5, 0x3e, 2, 0xd7)
    put(0xfa, 0x00, 0xd1, 0x3d, 0x21, root and 255, root ushr 8, 0x5f, 0x16, 0)
    repeat(width) { put(0x19) }
    put(0x11, 0x00, 0xcd, 0xd5, 0x01, width, 0, 0xcd, 0x00, 0x06,
        0x21, width, 0xcd, 0x36, 0x50, 0xd1)
    if (generation == 1) {
        put(0xf1)
        if (helper) put(0xcd, 0x00, 0x05) else put(0xe0, 0xb8, 0xea, 0x00, 0x20)
        put(0xe1, 0xc9)
    } else put(0xe1, 0xf1, 0xd7, 0xc9)
    return result.map(Int::toByte).toByteArray()
}

internal fun nativeGbNames(generation: Int, width: Int = 5, helper: Boolean = false): ByteArray {
    val bytes = ByteArray(0xc000)
    nativeGbConsumer(generation, width, helper).copyInto(bytes, 0x100)
    byteArrayOf(0xe0.toByte(), 0xb8.toByte(), 0xea.toByte(), 0, 0x20, 0xc9.toByte()).copyInto(bytes, 0x500)
    repeat(3) { row -> repeat(width) { column -> bytes[0x8000 + row * width + column] = (0x80 + row + column).toByte() } }
    byteArrayOf(0x80.toByte(), 0x81.toByte(), 0x50, 0x82.toByte(), 0x83.toByte(), 0x50,
        0x84.toByte(), 0x85.toByte(), 0x50).copyInto(bytes, 0x9000)
    byteArrayOf(2, 0, 0x40, 2, 0, 0x50).copyInto(bytes, 0x300)
    // Complete adjacent far-pointer consumer with count selector and bounded string-copy call.
    byteArrayOf(0x21, 0, 3, 0x19, 0x19, 0x19, 0x2a, 0xd7.toByte(), 0x2a, 0x66, 0x6f,
        0xfa.toByte(), 0x01, 0xd1.toByte(), 0x3d, 0xcd.toByte(), 0x00, 0x07,
        0x11, 0x00, 0xcd.toByte(), 0x01, 0x15, 0, 0xcd.toByte(), 0x00, 0x06).copyInto(bytes, 0x350)
    byteArrayOf(0xa7.toByte(), 0xc8.toByte(), 0xc5.toByte(), 0x47, 0x0e, 0x50, 0x2a, 0xb9.toByte(),
        0x20, 0xfc.toByte(), 0x05, 0x20, 0xf9.toByte(), 0xc1.toByte(), 0xc9.toByte()).copyInto(bytes, 0x700)
    return bytes
}
