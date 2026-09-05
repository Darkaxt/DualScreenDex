package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.text.LanguageTextPlausibility
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

/** Static section names only. Geometry and runtime-specific name overrides are independent. */
internal object CompiledRegionSectionNames {
    fun resolve(
        rom: RomImage,
        references: GbaReferenceIndex,
        sections: Set<Int>,
        codec: PokemonTextCodec?,
        cancellation: ParserCancellationToken,
        extentLimit: Long = ResolutionLimits().maxDatasetExtentBytes,
    ): Map<Int, String> {
        cancellation.throwIfCancellationRequested()
        if (codec == null || sections.isEmpty() || references.overflowed) return emptyMap()
        val candidates = references.targets.keys.flatMap { root ->
            cancellation.throwIfCancellationRequested()
            executableGbaTextSites(references, root).orEmpty().mapNotNull { site ->
                prove(rom, root, site)?.takeIf {
                    it.count.toLong() * 4 <= extentLimit && root.toLong() + it.count.toLong() * 4 <= rom.size
                }
            }
        }.distinct()
        val table = candidates.singleOrNull() ?: return emptyMap()
        return buildMap {
            sections.sorted().forEach { section ->
                cancellation.throwIfCancellationRequested()
                val index = section - table.bias
                if (index !in 0 until table.count) return@forEach
                val text = rom.gbaPointer(table.root + index * 4) ?: return@forEach
                val decoded = codec.decodeDetailed(rom, text, minOf(64, rom.size - text), cancellation)
                if (decoded.terminated && decoded.invalidUnits == 0 &&
                    LanguageTextPlausibility.looksLikeStandaloneFixedName(decoded.text, codec.language)
                ) put(section, decoded.text.trim())
            }
        }
    }

    private data class Table(val root: Int, val bias: Int, val count: Int)

    private fun prove(rom: RomImage, root: Int, site: Int): Table? {
        if (site < 12 || site.toLong() + 8 > rom.size) return null
        val load = rom.u16le(site)
        val shift = rom.u16le(site + 2)
        val add = rom.u16le(site + 4)
        val indirect = rom.u16le(site + 6)
        if (load and 0xF800 != 0x4800 || shift and 0xFFC0 != 0x0080 ||
            add and 0xFE00 != 0x1800 || indirect and 0xFFC0 != 0x6800
        ) return null
        val base = (load ushr 8) and 7
        val index = (shift ushr 3) and 7
        val product = shift and 7
        val address = add and 7
        if (base == product || base == index || setOf((add ushr 3) and 7, (add ushr 6) and 7) != setOf(base, product) ||
            (indirect ushr 3) and 7 != address
        ) return null
        val proofs = (maxOf(0xC0, site - 64) until site - 8 step 2).mapNotNull { at ->
            val constantLoad = rom.u16le(at)
            val sum = rom.u16le(at + 2)
            val narrow = rom.u16le(at + 4)
            val compare = rom.u16le(at + 6)
            val branch = rom.u16le(at + 8)
            if (constantLoad and 0xF800 != 0x4800 || sum and 0xFE00 != 0x1800 ||
                narrow and 0xFFC0 != 0x0C00 || narrow and 7 != index ||
                compare and 0xFF00 != (0x2800 or (index shl 8)) || branch and 0xFF00 != 0xD800
            ) return@mapNotNull null
            val target = at + 12 + ((branch and 255).toByte().toInt() shl 1)
            if (target <= site + 6 || target > rom.size) return@mapNotNull null
            val constantRegister = (constantLoad ushr 8) and 7
            val valueRegister = sum and 7
            if ((narrow ushr 3) and 7 != valueRegister || valueRegister == constantRegister ||
                setOf((sum ushr 3) and 7, (sum ushr 6) and 7) != setOf(valueRegister, constantRegister)
            ) return@mapNotNull null
            // The section was shifted to the high halfword before adding the signed bias.
            val origin = (maxOf(0xC0, at - 8) until at step 2).lastOrNull { previous ->
                val op = rom.u16le(previous)
                op and 0xFFC0 == 0x0400 && op and 7 == valueRegister
            } ?: return@mapNotNull null
            if ((origin + 2 until at step 2).any { writesRegister(rom.u16le(it), valueRegister) }) return@mapNotNull null
            val literal = ((at + 4) and -4) + (constantLoad and 255) * 4
            if (literal.toLong() + 4 > rom.size) return@mapNotNull null
            val raw = rom.u32le(literal).toInt()
            if (raw and 0xFFFF != 0) return@mapNotNull null
            val bias = -(raw shr 16)
            if (bias !in 0..0xFFFF) return@mapNotNull null
            // Only callee-preserved index registers may cross an intervening BL.
            fun skippedLiteral(loadSite: Int, pool: Int): Boolean =
                pool >= loadSite + 2 && pool.toLong() + 4 <= rom.size &&
                    (loadSite + 2 until minOf(pool, site) step 2).any { jumpSite ->
                        val jump = rom.u16le(jumpSite)
                        val encoded = jump and 0x7FF
                        val displacement = if (encoded and 0x400 != 0) encoded - 0x800 else encoded
                        jump and 0xF800 == 0xE000 && jumpSite + 4 + displacement * 2 >= pool + 4
                    }
            val literalData = mutableSetOf<Int>()
            if (skippedLiteral(at, literal)) { literalData += literal; literalData += literal + 2 }
            var cursor = at + 10
            while (cursor < site) {
                val op = rom.u16le(cursor)
                if (cursor in literalData) { cursor += 2; continue }
                if (op and 0xF800 == 0x4800) {
                    val pool = ((cursor + 4) and -4) + (op and 255) * 4
                    if (skippedLiteral(cursor, pool)) { literalData += pool; literalData += pool + 2 }
                }
                if (op and 0xF800 == 0xF000 && cursor + 4 <= site && rom.u16le(cursor + 2) and 0xF800 == 0xF800) {
                    if (index < 4) return@mapNotNull null
                    cursor += 4; continue
                }
                if (writesRegister(op, index)) return@mapNotNull null
                cursor += 2
            }
            Table(root, bias, (compare and 255) + 1)
        }
        return proofs.distinct().singleOrNull()
    }

    private fun writesRegister(op: Int, register: Int): Boolean = when {
        op and 0xE000 == 0 -> op and 7 == register
        op and 0xE000 == 0x2000 -> op and 0xF800 != 0x2800 && (op ushr 8) and 7 == register
        op and 0xF800 == 0x4800 -> (op ushr 8) and 7 == register
        op and 0xFC00 == 0x4000 -> op and 0x03C0 !in setOf(0x0280, 0x02C0) && op and 7 == register
        op and 0xF000 == 0xD000 || op and 0xF800 == 0xE000 -> false
        else -> true // unsupported intervening instructions cannot establish preservation
    }
}
