package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.sprite.Gen1SpriteDecoder
import com.enrpau.dualscreendex.parser.sprite.IndexedSprite

internal data class Gen1DetachedSpeciesRecord(
    val dexNumber: Int,
    val offset: Int,
    val bank: Int,
)

/** Resolves the source-defined Gen I record stored outside the ordinary Dex-ordered table. */
internal object Gen1DetachedSpeciesResolver {
    fun resolve(rom: RomImage, table: TableLayout): Map<Int, Gen1DetachedSpeciesRecord> {
        if (table.count <= 0 || table.recordSize != RECORD_SIZE) return emptyMap()
        val ordinaryEnd = table.offset.toLong() + table.count.toLong() * table.recordSize
        if (table.offset < 0 || ordinaryEnd > rom.size.toLong()) return emptyMap()

        val missingDexNumbers = (1..table.count).filter { dexNumber ->
            val offset = table.offset + (dexNumber - 1) * table.recordSize
            !validRecord(rom, offset, dexNumber)
        }
        if (missingDexNumbers.isEmpty()) return emptyMap()

        return missingDexNumbers.mapNotNull { dexNumber ->
            val candidates = (0..rom.size - RECORD_SIZE).asSequence()
                .filterNot { offset -> offset >= table.offset && offset.toLong() < ordinaryEnd }
                .filter { offset -> rom.u8(offset) == dexNumber }
                .filter { offset -> validRecord(rom, offset, dexNumber) }
                .mapNotNull { offset -> detachedRecord(rom, offset, dexNumber) }
                .toList()
            candidates.singleOrNull()?.let { dexNumber to it }
        }.toMap()
    }

    fun completeEvidence(
        evidence: ValidationEvidence,
        detached: Map<Int, Gen1DetachedSpeciesRecord>,
        label: String,
    ): ValidationEvidence {
        if (detached.isEmpty() || evidence.totalRecords <= 0) return evidence
        val valid = minOf(evidence.totalRecords, evidence.validRecords + detached.size)
        val expected = evidence.expectedRecords ?: evidence.totalRecords
        val covered = minOf(expected, (evidence.coveredRecords ?: evidence.validRecords) + detached.size)
        return evidence.copy(
            validRecords = valid,
            confidence = valid.toDouble() / evidence.totalRecords,
            reasons = evidence.reasons + "resolved ${detached.size} detached Gen I $label through the compiled far-copy consumer",
            coveredRecords = covered,
            expectedRecords = expected,
        )
    }

    fun decodeFrontSprite(rom: RomImage, record: Gen1DetachedSpeciesRecord): IndexedSprite? {
        val dimensions = rom.u8(record.offset + DIMENSIONS_OFFSET)
        return decodeSprite(rom, record.bank, rom.u16le(record.offset + FRONT_POINTER_OFFSET), dimensions)
    }

    private fun detachedRecord(rom: RomImage, offset: Int, dexNumber: Int): Gen1DetachedSpeciesRecord? {
        val bank = offset / BANK_SIZE
        if (bank <= 0) return null
        val address = BANKED_ADDRESS_START + offset % BANK_SIZE
        if (!hasFarCopyConsumer(rom, address, bank)) return null
        val dimensions = rom.u8(offset + DIMENSIONS_OFFSET)
        decodeSprite(rom, bank, rom.u16le(offset + FRONT_POINTER_OFFSET), dimensions) ?: return null
        decodeSprite(rom, bank, rom.u16le(offset + BACK_POINTER_OFFSET), expectedDimensions = null) ?: return null
        return Gen1DetachedSpeciesRecord(dexNumber, offset, bank)
    }

    private fun validRecord(rom: RomImage, offset: Int, expectedDexNumber: Int): Boolean = runCatching {
        if (offset < 0 || offset + RECORD_SIZE > rom.size || rom.u8(offset) != expectedDexNumber) return false
        val statsValid = (1..5).all { field -> rom.u8(offset + field) in 1..255 }
        val typesValid = rom.u8(offset + 6) in 0..27 && rom.u8(offset + 7) in 0..27
        val dimensions = rom.u8(offset + DIMENSIONS_OFFSET)
        val width = dimensions ushr 4
        val height = dimensions and 0x0F
        val pointersValid = rom.u16le(offset + FRONT_POINTER_OFFSET) in BANKED_ADDRESS_START..BANKED_ADDRESS_END &&
            rom.u16le(offset + BACK_POINTER_OFFSET) in BANKED_ADDRESS_START..BANKED_ADDRESS_END
        statsValid && typesValid && width in 1..7 && height in 1..7 && pointersValid
    }.getOrDefault(false)

    private fun decodeSprite(rom: RomImage, bank: Int, address: Int, expectedDimensions: Int?): IndexedSprite? {
        val pointer = rom.gbBankAddress(bank, address) ?: return null
        val bankEnd = minOf(rom.size, (bank + 1) * BANK_SIZE)
        return runCatching { Gen1SpriteDecoder.decode(rom.slice(pointer, bankEnd - pointer)) }
            .getOrNull()
            ?.takeIf { expectedDimensions == null || expectedDimensions == ((it.width / 8) shl 4 or (it.height / 8)) }
    }

    /** `ld hl,record; ld de,destination; ld bc,28; ld a,bank; call FarCopyData`. */
    private fun hasFarCopyConsumer(rom: RomImage, address: Int, bank: Int): Boolean {
        val instructionBytes = 14
        for (offset in 0..rom.size - instructionBytes) {
            if (rom.u8(offset) == 0x21 && rom.u16le(offset + 1) == address &&
                rom.u8(offset + 3) == 0x11 &&
                rom.u8(offset + 6) == 0x01 && rom.u16le(offset + 7) == RECORD_SIZE &&
                rom.u8(offset + 9) == 0x3E && rom.u8(offset + 10) == bank &&
                rom.u8(offset + 11) == 0xCD
            ) return true
        }
        return false
    }

    private const val RECORD_SIZE = 28
    private const val DIMENSIONS_OFFSET = 10
    private const val FRONT_POINTER_OFFSET = 11
    private const val BACK_POINTER_OFFSET = 13
    private const val BANK_SIZE = 0x4000
    private const val BANKED_ADDRESS_START = 0x4000
    private const val BANKED_ADDRESS_END = 0x7FFF
}
