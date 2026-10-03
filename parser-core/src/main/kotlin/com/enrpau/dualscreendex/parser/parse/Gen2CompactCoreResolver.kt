package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Gen2CompactCoreMetadata
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.text.Gen2PlainNameCodec
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

internal data class Gen2CompactCoreResolution(
    val metadata: Gen2CompactCoreMetadata,
    val tables: ProfileTables,
    val codec: PokemonTextCodec,
    val typeNames: TableLayout,
    val namesEvidence: ValidationEvidence,
    val statsEvidence: ValidationEvidence,
)

/** Coherent compact name/base/index authority; no inherited or fuzzy table geometry. */
internal object Gen2CompactCoreResolver {
    private data class Names(
        val oneBased: Boolean,
        val bank: Int,
        val address: Int,
        val formAddress: Int,
        val indexHelper: Int,
        val copy: GbCompiledFarCopyAuthority,
    )

    private data class Base(
        val oneBased: Boolean,
        val bank: Int,
        val address: Int,
        val formAddress: Int,
        val indexHelper: Int,
        val size: Int,
        val destination: Int,
        val copy: GbCompiledFarCopyAuthority,
    )

    /** Negative fallback gate only; these landmarks never authorize table decoding. */
    fun hasCompactConsumers(session: RomAnalysisSession): Boolean {
        val rom = session.rom
        var names = false
        var bases = false
        for (offset in 0..minOf(0x4000, rom.size) - 45) {
            if (offset and 0x3FF == 0) session.cancellation.throwIfCancellationRequested()
            names = names || (homeBytes(rom, offset, 0xE5, 0x21) &&
                homeBytes(rom, offset + 33, 0x62, 0x6B, 0x36, 0x53, 0xD1, 0xE1, 0xC9)) ||
                (homeBytes(rom, offset, 0xE5, 0xC5, 0xFA) &&
                    homeBytes(rom, offset + 38, 0x62, 0x6B, 0x36, 0x53, 0xD1, 0xE1, 0xC9))
            bases = bases || (homeBytes(rom, offset, 0xE5, 0xD5, 0xC5, 0xFA) &&
                homeBytes(rom, offset + 6, 0x4F, 0xFA) && homeBytes(rom, offset + 10, 0x47, 0xCD))
            if (names && bases) return true
        }
        return false
    }

    fun resolve(session: RomAnalysisSession): Gen2CompactCoreResolution? {
        val rom = session.rom
        val names = mutableListOf<Names>()
        val bases = mutableListOf<Base>()
        for (offset in 0..minOf(0x4000, rom.size) - 45) {
            if (offset and 0x3FF == 0) session.cancellation.throwIfCancellationRequested()
            if (rom.u8(offset) != 0xE5) continue
            parseNames(rom, offset, false)?.let(names::add)
            parseNames(rom, offset, true)?.let(names::add)
            parseBase(rom, offset)?.let(bases::add)
        }
        if (names.isEmpty() || bases.isEmpty()) return null
        val plain = Gen2PlainNameCodec.english53
        val codec = if (GbCompiledNgramResolver.resolve(rom) != null) Gen2PlainNameCodec.english53Ngrams else plain
        val typeNames = CompiledTypeNameResolver.resolve(session, 2, codec) ?: return null
        val types = CompiledTypeNameResolver.decode(rom, 2, typeNames, codec) ?: return null
        val candidates = names.flatMap { name -> bases.mapNotNull { base ->
            if (name.oneBased != base.oneBased || name.copy != base.copy ||
                name.formAddress != base.formAddress
            ) return@mapNotNull null
            val indexes = if (name.oneBased) {
                Gen2CompactIndexResolver.oneBased(rom, base.indexHelper, name.indexHelper)
            } else {
                Gen2CompactIndexResolver.zeroBased(rom, base.indexHelper, name.indexHelper)
            } ?: return@mapNotNull null
            val nameTable = table(rom, name.bank, name.address, indexes.nameSlots, 10) ?: return@mapNotNull null
            val statsTable = table(rom, base.bank, base.address, indexes.baseSlots, base.size)
                ?.copy(format = TableRecordFormat.GEN2_COMPACT_BASE_STATS) ?: return@mapNotNull null
            if (!sixStatFields(rom, base.destination, session)) return@mapNotNull null
            for (slot in indexes.metadata.slots) {
                val decoded = plain.decodeDetailed(rom, nameTable.offset + slot.nameIndex * 10, 10, session.cancellation)
                if (decoded.invalidUnits != 0 || decoded.controlUnits != 0 || decoded.substitutionUnits != 0 ||
                    decoded.text.none(Char::isLetter)
                ) return@mapNotNull null
                val row = statsTable.offset + slot.baseIndex * base.size
                if ((0 until 6).any { rom.u8(row + it) == 0 } ||
                    rom.u8(row + 6) !in types.keys || rom.u8(row + 7) !in types.keys
                ) return@mapNotNull null
            }
            val count = indexes.metadata.slots.size
            Gen2CompactCoreResolution(indexes.metadata, ProfileTables(speciesNames = nameTable, baseStats = statsTable),
                codec, typeNames, evidence(nameTable, count, "compiled compact canonical names"),
                evidence(statsTable, count, "compiled compact six-stat fields and native type references"))
        } }
        return candidates.distinct().singleOrNull()
    }

    private fun parseNames(rom: RomImage, offset: Int, oneBased: Boolean): Names? {
        val rootOperand: Int
        val destinationOperand: Int
        val bankOperand: Int
        val copyOperand: Int
        val helper: Int
        val form: Int
        if (oneBased) {
            if (!homeBytes(rom, offset, 0xE5, 0xC5, 0xFA) ||
                !homeBytes(rom, offset + 5, 0x4F, 0xFA) || !homeBytes(rom, offset + 9, 0x47, 0xCD) ||
                !homeBytes(rom, offset + 13, 0x50, 0x59, 0xC1, 0x62, 0x6B, 0x29, 0x29, 0x19, 0x29, 0x11) ||
                !homeBytes(rom, offset + 25, 0x19, 0x11) ||
                !homeBytes(rom, offset + 29, 0xD5, 0x01, 0x0A, 0x00, 0x3E) ||
                !homeBytes(rom, offset + 35, 0xCD) ||
                !homeBytes(rom, offset + 38, 0x62, 0x6B, 0x36, 0x53, 0xD1, 0xE1, 0xC9) ||
                rom.u16le(offset + 3) !in 0xC000..0xDFFF
            ) return null
            rootOperand = 23
            destinationOperand = 27
            bankOperand = 34
            copyOperand = 36
            helper = rom.u16le(offset + 11)
            form = rom.u16le(offset + 7)
        } else {
            if (!homeBytes(rom, offset, 0xE5, 0x21) ||
                !homeBytes(rom, offset + 4, 0x2A, 0x5F, 0x7E, 0xCD) ||
                !homeBytes(rom, offset + 10, 0x57, 0x62, 0x6B, 0x29, 0x29, 0x19, 0x29, 0x11) ||
                !homeBytes(rom, offset + 20, 0x19, 0x11) ||
                !homeBytes(rom, offset + 24, 0xD5, 0x01, 0x0A, 0x00, 0x3E) ||
                !homeBytes(rom, offset + 30, 0xCD) ||
                !homeBytes(rom, offset + 33, 0x62, 0x6B, 0x36, 0x53, 0xD1, 0xE1, 0xC9)
            ) return null
            val named = rom.u16le(offset + 2)
            val bridge = offset - 13
            if (named !in 0xC000..0xDFFE || !homeBytes(rom, bridge, 0xE5, 0x21, named and 0xFF, named ushr 8, 0xFA) ||
                !homeBytes(rom, bridge + 7, 0x22, 0xFA) || !homeBytes(rom, bridge + 11, 0x77, 0xE1) ||
                rom.u16le(bridge + 5) !in 0xC000..0xDFFF
            ) return null
            rootOperand = 18
            destinationOperand = 22
            bankOperand = 29
            copyOperand = 31
            helper = rom.u16le(offset + 8)
            form = rom.u16le(bridge + 9)
        }
        val destination = rom.u16le(offset + destinationOperand)
        val copy = GbCompiledFarCopy.resolve(rom, rom.u16le(offset + copyOperand)) ?: return null
        val bank = rom.u8(offset + bankOperand)
        if (form !in 0xC000..0xDFFF || destination !in 0xC000..0xDFF5 || bank !in 1..copy.maximumBank) return null
        return Names(oneBased, bank, rom.u16le(offset + rootOperand), form, helper, copy)
    }

    private fun parseBase(rom: RomImage, offset: Int): Base? {
        if (!homeBytes(rom, offset, 0xE5, 0xD5, 0xC5, 0xFA) ||
            !homeBytes(rom, offset + 6, 0x4F, 0xFA) || !homeBytes(rom, offset + 10, 0x47, 0xCD) ||
            rom.u16le(offset + 4) !in 0xC000..0xDFFF
        ) return null
        val oneBased = rom.u8(offset + 14) == 0x0B
        val shift = if (oneBased) 1 else 0
        if (!homeBytes(rom, offset + 14 + shift, 0x3E) ||
            !homeBytes(rom, offset + 16 + shift, 0x21) ||
            !homeBytes(rom, offset + 20 + shift, 0x11) ||
            !homeBytes(rom, offset + 23 + shift, 0x01) ||
            !homeBytes(rom, offset + 26 + shift, 0x3E) ||
            !homeBytes(rom, offset + 28 + shift, 0xCD) ||
            !homeBytes(rom, offset + 31 + shift, 0xC3) ||
            !restartMultiply(rom, rom.u8(offset + 19 + shift)) ||
            !homeBytes(rom, rom.u16le(offset + 32 + shift), 0xC1, 0xD1, 0xE1, 0xC9)
        ) return null
        val size = rom.u8(offset + 15 + shift).takeIf { it in 20..64 } ?: return null
        if (rom.u16le(offset + 24 + shift) != size) return null
        val destination = rom.u16le(offset + 21 + shift)
        val form = rom.u16le(offset + 8)
        val copy = GbCompiledFarCopy.resolve(rom, rom.u16le(offset + 29 + shift)) ?: return null
        val bank = rom.u8(offset + 27 + shift)
        if (form !in 0xC000..0xDFFF || destination !in 0xC000..0xDFFF ||
            destination + size > 0xE000 || bank !in 1..copy.maximumBank
        ) return null
        return Base(oneBased, bank, rom.u16le(offset + 17 + shift), form,
            rom.u16le(offset + 12), size, destination, copy)
    }

    private fun sixStatFields(rom: RomImage, destination: Int, session: RomAnalysisSession): Boolean =
        rom.findAll(byteArrayOf(0x0E, 0x00, 0x0C)).any { offset ->
            session.cancellation.throwIfCancellationRequested()
            if (!withinBank(rom, offset, 20) || rom.u8(offset + 3) != 0xCD ||
                !bytesAt(rom, offset + 6, 0xF0) || !bytesAt(rom, offset + 8, 0x12, 0x13, 0xF0) ||
                rom.u8(offset + 7) !in 0x80..0xFD || rom.u8(offset + 11) != rom.u8(offset + 7) + 1 ||
                !bytesAt(rom, offset + 12, 0x12, 0x13, 0x79, 0xFE, 0x06, 0x20, 0xEF, 0xC9)
            ) return@any false
            val calculator = rom.gbBankAddress(offset / 0x4000, rom.u16le(offset + 4)) ?: return@any false
            val previous = destination - 1
            bytesAt(rom, calculator, 0xE5, 0xD5, 0xC5, 0x50, 0xE5, 0x21,
                previous and 0xFF, previous ushr 8, 0x06, 0x00, 0x09, 0x5E, 0xE1) ||
                bytesAt(rom, calculator, 0xE5, 0xD5, 0xC5, 0x78, 0x57, 0xE5, 0x21,
                    previous and 0xFF, previous ushr 8, 0x06, 0x00, 0x09, 0x7E, 0x5F, 0xE1)
        }

    private fun table(rom: RomImage, bank: Int, address: Int, count: Int, width: Int): TableLayout? {
        if (address !in 0x4000..0x7FFF || address.toLong() + count.toLong() * width > 0x8000) return null
        val root = rom.gbBankAddress(bank, address) ?: return null
        if (root.toLong() + count.toLong() * width > rom.size) return null
        return TableLayout(root, count, width, bank = bank)
    }

    private fun evidence(table: TableLayout, count: Int, reason: String): ValidationEvidence = ValidationEvidence(
        true, count, table.count, 1.0, listOf(reason), table.offset, table.recordSize,
        coveredRecords = count, expectedRecords = count, format = table.format,
    )

    private fun restartMultiply(rom: RomImage, opcode: Int): Boolean {
        if (opcode and 0xC7 != 0xC7) return false
        val vector = opcode and 0x38
        if (!homeWithin(rom, vector, 3)) return false
        val target = if (rom.u8(vector) == 0xC3) rom.u16le(vector + 1) else vector
        return GbCompiledBankCalls.repeatedAdd(rom, target)
    }

    private fun homeBytes(rom: RomImage, offset: Int, vararg values: Int): Boolean =
        homeWithin(rom, offset, values.size) && values.indices.all { rom.u8(offset + it) == values[it] }

    private fun bytesAt(rom: RomImage, offset: Int, vararg values: Int): Boolean =
        withinBank(rom, offset, values.size) && values.indices.all { rom.u8(offset + it) == values[it] }

    private fun homeWithin(rom: RomImage, offset: Int, count: Int): Boolean =
        offset >= 0 && offset.toLong() + count <= minOf(0x4000, rom.size)

    private fun withinBank(rom: RomImage, offset: Int, count: Int): Boolean =
        offset >= 0 && offset.toLong() + count <= rom.size && offset % 0x4000 + count <= 0x4000
}
