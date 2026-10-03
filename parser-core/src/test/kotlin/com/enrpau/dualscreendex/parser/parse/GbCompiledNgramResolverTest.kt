package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.text.Gen2PlainNameCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GbCompiledNgramResolverTest {
    @Test fun readsStaticFragmentsFromCompleteCompiledDispatch() {
        val rom = fixture()
        val dictionary = requireNotNull(GbCompiledNgramResolver.resolve(rom))
        assertEquals(71, dictionary.size)
        assertEquals("or", dictionary[0x20])
        assertEquals("ight", dictionary[0x49])
        assertEquals("Normal", decode(rom, "8d 20 ac a0 ab 53"))
        assertEquals("Fighting", decode(rom, "85 49 3f 53"))
    }

    @Test fun preservesFragmentWhitespaceUntilFinalTextNormalization() {
        val rom = fixture()
        val dictionary = requireNotNull(GbCompiledNgramResolver.resolve(rom))
        assertEquals("e ", dictionary[9])
        assertEquals(" t", dictionary[10])
        assertEquals("Ae tB", decode(rom, "80 09 0a 81 53"))
    }

    @Test fun keepsDifferentRomDictionariesIndependent() {
        val first = fixture()
        val second = fixture(orFragment = "xy")
        assertEquals("or", requireNotNull(GbCompiledNgramResolver.resolve(first))[0x20])
        assertEquals("xy", requireNotNull(GbCompiledNgramResolver.resolve(second))[0x20])
        assertEquals("or", requireNotNull(GbCompiledNgramResolver.resolve(first))[0x20])
        assertEquals("Normal", decode(first, "8d 20 ac a0 ab 53"))
        assertEquals("Nxymal", decode(second, "8d 20 ac a0 ab 53"))
        assertEquals("Normal", decode(first, "8d 20 ac a0 ab 53"))
    }

    @Test fun rejectsMissingAuthorityAndRuntimeNameTokens() {
        val absent = Gen2PlainNameCodec.english53Ngrams.decodeDetailed(bytes("80 20 53"))
        assertEquals(1, absent.invalidUnits)
        assertNull(GbCompiledNgramResolver.resolve(RomImage(ByteArray(32))))
        for (value in 0x50..0x52) {
            val source = fixture().slice(0, 0x4000)
            source[TEXT] = value.toByte()
            source[TEXT + 1] = 0x53
            val decoded = Gen2PlainNameCodec.english53Ngrams.decodeDetailed(
                RomImage(source), TEXT, 2, ParserCancellationToken.NONE,
            )
            assertEquals(1, decoded.invalidUnits)
            assertEquals(0, decoded.substitutionUnits)
        }
    }

    @Test fun rejectsBrokenDispatchRestorationAndDictionaryPointers() {
        val source = fixture().slice(0, 0x4000)
        val mutations = listOf(
            RANGE + 2, NGRAM + 2, NGRAM + 3, NGRAM + 11, NGRAM + 15,
            CALLBACK + 1, CALLBACK + 3, FINISH + 2, SPECIAL + 13,
        )
        mutations.forEach { offset ->
            val altered = source.copyOf().also { it[offset] = (it[offset].toInt() xor 1).toByte() }
            assertNull("broken dictionary authority at $offset", GbCompiledNgramResolver.resolve(RomImage(altered)))
        }
        assertNull(GbCompiledNgramResolver.resolve(RomImage(source.copyOf().also { it.word(DICTIONARY, 0xC000) })))
        assertNull(GbCompiledNgramResolver.resolve(RomImage(source.copyOf().also { it.word(DICTIONARY + 71 * 2, 0x3000) })))
        assertNull(GbCompiledNgramResolver.resolve(RomImage(source.copyOf().also { it.word(0x31, 0x600) })))
    }

    private fun decode(rom: RomImage, text: String): String {
        val source = rom.slice(0, rom.size)
        bytes(text).copyInto(source, TEXT)
        val decoded = Gen2PlainNameCodec.english53Ngrams.decodeDetailed(
            RomImage(source), TEXT, bytes(text).size, ParserCancellationToken.NONE,
        )
        assertEquals(0, decoded.invalidUnits)
        return decoded.text
    }

    private fun fixture(orFragment: String = "or"): RomImage {
        val source = ByteArray(0x4000)
        source.put(0x30, "c3 00 05")
        source[ENTRY] = 0xE5.toByte()
        source.put(RANGE, "1a fe 5f 30 00 fe 53 30 00 fe 09 30 00 1b c3 90 05")
        source[RANGE + 4] = (LITERAL - RANGE - 5).toByte()
        source[RANGE + 8] = (SPECIAL - RANGE - 9).toByte()
        source[RANGE + 12] = (NGRAM - RANGE - 13).toByte()
        source.put(LITERAL, "22 cd 00 04 13 18 00")
        source[LITERAL + 6] = (RANGE - LITERAL - 7).toByte()
        source.put(NGRAM, "d6 09 d5 e5 87 5f 16 00 21 00 07 19 2a 56 5f e1 c3 80 05")
        source.put(CALLBACK, "f7 60 69 d1 c3 24 05")
        source.put(SPECIAL, "d6 53 e5 87 4f 06 00 21 00 06 09 2a 46 4f e1 c5 c9")
        source.put(FINISH, "44 4d e1 c9")
        source[0x400] = 0xC9.toByte()
        source.word(0x600, FINISH)
        var cursor = DICTIONARY + 74 * 2
        for (value in 9..0x4F) {
            val fragment = when (value) {
                9 -> "e "
                10 -> " t"
                0x20 -> orFragment
                0x3F -> "ing"
                0x49 -> "ight"
                else -> "aa"
            }
            source.word(DICTIONARY + (value - 9) * 2, cursor)
            val encoded = fragment.map { char ->
                if (char == ' ') 0x7F.toByte() else (0xA0 + (char - 'a')).toByte()
            }.toByteArray() + 0x53.toByte()
            encoded.copyInto(source, cursor)
            cursor += encoded.size
        }
        for (value in 0x50..0x52) source.word(DICTIONARY + (value - 9) * 2, 0xC000 + value)
        return RomImage(source)
    }

    private fun ByteArray.put(offset: Int, value: String) {
        bytes(value).copyInto(this, offset)
    }

    private fun ByteArray.word(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
    }

    private fun bytes(value: String): ByteArray =
        value.split(' ').map { it.toInt(16).toByte() }.toByteArray()

    private companion object {
        const val ENTRY = 0x500
        const val RANGE = ENTRY + 1
        const val LITERAL = 0x520
        const val NGRAM = 0x540
        const val SPECIAL = 0x560
        const val CALLBACK = 0x580
        const val FINISH = 0x590
        const val DICTIONARY = 0x700
        const val TEXT = 0x2800
    }
}
