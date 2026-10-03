package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.parse.GbCompiledBankCalls
import com.enrpau.dualscreendex.parser.parse.Gen1CompiledBaseResolver

/** Binds a bank-local base consumer to its index lookup and separate non-Dex branch. */
internal object Gen1CompiledSpeciesIndexResolver {
    fun resolve(
        rom: RomImage,
        base: TableLayout,
        internalCount: Int,
        cancellation: ParserCancellationToken,
    ): SpeciesIndexResolution? {
        val authority = Gen1CompiledBaseResolver.resolveWithAuthority(rom, base.count, cancellation)
            ?.takeIf { it.bankLocal && it.table == base } ?: return null
        val fallback = (1..internalCount).associateWith { 0 }
        if (internalCount !in 1..254) {
            return SpeciesIndexResolution.Unavailable(fallback, "bank-local species-index extent is unsupported")
        }
        val targets = GbCompiledBankCalls.discover(rom, cancellation)
        val candidates = linkedSetOf<Map<Int, Int>>()
        for (target in targets) {
            val end = minOf(rom.size, (target.offset / BANK_BYTES + 1) * BANK_BYTES, target.offset + 128)
            for (consumer in target.offset..end - 17) {
                cancellation.throwIfCancellationRequested()
                if (Gen1CompiledBaseResolver.bankLocalConsumerAt(rom, consumer, base.count) != authority.table ||
                    consumer - 29 < target.offset || !bytes(rom, consumer - 6, 0xCD) ||
                    rom.u8(consumer - 3) != 0xFA
                ) continue
                val field = rom.u16le(consumer - 2)
                if (field !in 0xC000..0xDFFF) continue
                val excluded = excludedSpecies(rom, target.offset, consumer, field, base, internalCount) ?: continue
                val wrapper = rom.u16le(consumer - 5)
                for (indexTarget in targets.filter { it.callerOffset == wrapper }) {
                    val values = indexValues(rom, indexTarget.offset, field, base.count, internalCount) ?: continue
                    val mapping = (1..internalCount).associateWith { id -> if (id in excluded) 0 else values[id - 1] }
                    if (mapping.values.filter { it > 0 }.toSet() != (1..base.count).toSet()) continue
                    candidates += mapping
                }
            }
        }
        val values = candidates.singleOrNull()
        return if (values != null) {
            SpeciesIndexResolution.Resolved(values)
        } else {
            SpeciesIndexResolution.Unavailable(
                fallback,
                "bank-local species-to-Dex lookup or alternate-form exclusion is unproven or conflicting",
                ambiguous = candidates.size > 1,
            )
        }
    }

    private fun indexValues(
        rom: RomImage,
        offset: Int,
        field: Int,
        dexCount: Int,
        count: Int,
    ): List<Int>? {
        val bank = offset / BANK_BYTES
        val end = minOf(rom.size, (bank + 1) * BANK_BYTES)
        if (offset + 20 > end || !bytes(rom, offset, 0xC5, 0xE5, 0xFA) ||
            rom.u16le(offset + 3) != field || !bytes(rom, offset + 5, 0x3D, 0x21) ||
            !bytes(rom, offset + 9, 0x06, 0x00, 0x4F, 0x09, 0x7E, 0xEA) ||
            rom.u16le(offset + 15) != field || !bytes(rom, offset + 17, 0xE1, 0xC1, 0xC9)
        ) return null
        val address = rom.u16le(offset + 7)
        if (address !in 0x4000..0x7FFF) return null
        val root = rom.gbBankAddress(bank, address) ?: return null
        if (root.toLong() + count > end) return null
        return List(count) { rom.u8(root + it) }.takeIf { values -> values.all { it in 0..dexCount } }
    }

    private fun excludedSpecies(
        rom: RomImage,
        target: Int,
        consumer: Int,
        field: Int,
        base: TableLayout,
        count: Int,
    ): Set<Int>? {
        val branch = consumer - 29
        val bank = consumer / BANK_BYTES
        val end = minOf(rom.size, (bank + 1) * BANK_BYTES)
        if (target + 13 > end || !bytes(rom, target, 0xC5, 0xD5, 0xE5, 0xFA) ||
            rom.u16le(target + 4) != field || !bytes(rom, target + 6, 0xF5, 0xFA) ||
            rom.u8(target + 10) != 0xEA || rom.u16le(target + 11) != field ||
            rom.u8(branch) != 0xFA || rom.u16le(branch + 1) != rom.u16le(target + 8) ||
            rom.u16le(branch + 1) !in 0xC000..0xDFFF || rom.u8(branch + 3) != 0x21 ||
            rom.u8(branch + 6) != 0xCD || !singleByteSearch(rom, rom.u16le(branch + 7)) ||
            !bytes(rom, branch + 9, 0x30, 0x0C, 0x78, 0x21) ||
            rom.u8(branch + 15) != 0x01 || rom.u16le(branch + 16) != base.recordSize ||
            rom.u8(branch + 18) != 0xCD ||
            !GbCompiledBankCalls.repeatedAdd(rom, rom.u16le(branch + 19)) ||
            !bytes(rom, branch + 21, 0x18, 0x10)
        ) return null
        val address = rom.u16le(branch + 4)
        val alternateAddress = rom.u16le(branch + 13)
        if (address !in 0x4000..0x7FFF || alternateAddress !in 0x4000..0x7FFF) return null
        val root = rom.gbBankAddress(bank, address) ?: return null
        val alternate = rom.gbBankAddress(bank, alternateAddress) ?: return null
        val excluded = linkedSetOf<Int>()
        for (offset in root until minOf(end, root + count + 1)) {
            val id = rom.u8(offset)
            if (id == 0xFF) {
                return excluded.takeIf {
                    it.isNotEmpty() && alternate.toLong() + it.size.toLong() * base.recordSize <= end
                }
            }
            if (id !in 1..count || !excluded.add(id)) return null
        }
        return null
    }

    private fun singleByteSearch(rom: RomImage, address: Int): Boolean =
        address >= 0 && address + 22 <= minOf(BANK_BYTES, rom.size) && bytes(
            rom, address,
            0x11, 0x01, 0x00, 0x06, 0x00, 0x4F, 0x7E, 0xFE, 0xFF,
            0x28, 0x07, 0xB9, 0x28, 0x06, 0x04, 0x19, 0x18, 0xF4,
            0xA7, 0xC9, 0x37, 0xC9,
        )

    private fun bytes(rom: RomImage, offset: Int, vararg values: Int): Boolean =
        offset >= 0 && offset.toLong() + values.size <= rom.size &&
            values.indices.all { rom.u8(offset + it) == values[it] }

    private const val BANK_BYTES = 0x4000
}
