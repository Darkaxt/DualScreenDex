package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.text.Gen2PlainNameCodec
import com.enrpau.dualscreendex.parser.text.PokemonTextToken
import java.util.Collections
import java.util.WeakHashMap

/** Static ROM fragments only; runtime names and text commands are never expanded. */
internal object GbCompiledNgramResolver {
    private val dictionaries = WeakHashMap<RomImage, Map<Int, String>?>()

    fun resolve(rom: RomImage): Map<Int, String>? {
        synchronized(dictionaries) {
            if (dictionaries.containsKey(rom)) return dictionaries[rom]
        }
        val candidates = linkedSetOf<Map<Int, String>>()
        for (offset in 1..minOf(HOME_BYTES, rom.size) - 17) {
            if (rom.u8(offset) != 0x1A) continue
            parseDispatch(rom, offset)?.let(candidates::add)
            if (candidates.size > 1) break
        }
        val result = candidates.singleOrNull()
        synchronized(dictionaries) { dictionaries[rom] = result }
        return result
    }

    private fun parseDispatch(rom: RomImage, entry: Int): Map<Int, String>? {
        if (!homeBytes(rom, entry - 1, 0xE5, 0x1A, 0xFE, 0x5F, 0x30) ||
            !homeBytes(rom, entry + 5, 0xFE, 0x53, 0x30) ||
            !homeBytes(rom, entry + 9, 0xFE, 0x09, 0x30) ||
            !homeBytes(rom, entry + 13, 0x1B, 0xC3) || !within(rom, entry, 17)
        ) return null
        val literal = entry + 5 + rom.u8(entry + 4).toByte().toInt()
        val special = entry + 9 + rom.u8(entry + 8).toByte().toInt()
        val ngram = entry + 13 + rom.u8(entry + 12).toByte().toInt()
        val finish = rom.u16le(entry + 15)
        if (!within(rom, literal, 7) || !homeBytes(rom, literal, 0x22, 0xCD) ||
            !within(rom, rom.u16le(literal + 2), 1) ||
            !homeBytes(rom, literal + 4, 0x13, 0x18) ||
            literal + 7 + rom.u8(literal + 6).toByte().toInt() != entry ||
            !homeBytes(rom, finish, 0x44, 0x4D, 0xE1, 0xC9)
        ) return null
        if (!within(rom, special, 17) ||
            !homeBytes(rom, special, 0xD6, 0x53, 0xE5, 0x87, 0x4F, 0x06, 0, 0x21) ||
            !homeBytes(rom, special + 10, 0x09, 0x2A, 0x46, 0x4F, 0xE1, 0xC5, 0xC9)
        ) return null
        val specialTable = rom.u16le(special + 8)
        if (!within(rom, specialTable, 2) || rom.u16le(specialTable) != finish) return null
        if (!within(rom, ngram, 19) ||
            !homeBytes(rom, ngram, 0xD6, 0x09, 0xD5, 0xE5, 0x87, 0x5F, 0x16, 0, 0x21) ||
            !homeBytes(rom, ngram + 11, 0x19, 0x2A, 0x56, 0x5F, 0xE1, 0xC3)
        ) return null
        val callback = rom.u16le(ngram + 17)
        if (!within(rom, callback, 7) ||
            !homeBytes(rom, callback + 1, 0x60, 0x69, 0xD1, 0xC3) ||
            rom.u16le(callback + 5) != literal + 4
        ) return null
        val opcode = rom.u8(callback)
        if (opcode and 0xC7 != 0xC7) return null
        val vector = opcode and 0x38
        if (!within(rom, vector, 3)) return null
        val printer = if (rom.u8(vector) == 0xC3) rom.u16le(vector + 1) else vector
        if (printer != entry - 1) return null
        return dictionary(rom, rom.u16le(ngram + 9))
    }

    private fun dictionary(rom: RomImage, root: Int): Map<Int, String>? {
        val tableEnd = root + 74 * 2
        if (!within(rom, root, 74 * 2)) return null
        val fragments = linkedMapOf<Int, String>()
        for (value in 0x09..0x52) {
            val pointer = rom.u16le(root + (value - 0x09) * 2)
            if (value >= 0x50) {
                if (pointer !in 0xC000..0xDFFF) return null
            } else {
                if (pointer !in tableEnd until minOf(HOME_BYTES, rom.size)) return null
                fragments[value] = fragment(rom, pointer) ?: return null
            }
        }
        return Collections.unmodifiableMap(fragments)
    }

    private fun fragment(rom: RomImage, start: Int): String? {
        val end = minOf(start + 8, HOME_BYTES, rom.size)
        val text = StringBuilder()
        for (offset in start until end) {
            when (val token = Gen2PlainNameCodec.english53.decodeToken(rom, offset, end)) {
                is PokemonTextToken.Glyph -> text.append(token.text)
                is PokemonTextToken.Whitespace -> text.append(token.text)
                is PokemonTextToken.Terminator -> return text.toString().takeIf { it.isNotBlank() }
                else -> return null
            }
        }
        return null
    }

    private fun homeBytes(rom: RomImage, offset: Int, vararg values: Int): Boolean =
        within(rom, offset, values.size) && values.indices.all { rom.u8(offset + it) == values[it] }

    private fun within(rom: RomImage, offset: Int, count: Int): Boolean =
        offset >= 0 && offset.toLong() + count <= minOf(HOME_BYTES, rom.size)

    private const val HOME_BYTES = 0x4000
}
