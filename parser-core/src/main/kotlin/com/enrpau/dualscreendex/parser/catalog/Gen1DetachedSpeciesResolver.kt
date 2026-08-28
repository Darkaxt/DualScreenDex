package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
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
    fun resolve(
        rom: RomImage,
        table: TableLayout,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
    ): Map<Int, Gen1DetachedSpeciesRecord> {
        if (table.count <= 0 || table.recordSize != RECORD_SIZE) return emptyMap()
        val ordinaryEnd = table.offset.toLong() + table.count.toLong() * table.recordSize
        if (table.offset < 0 || ordinaryEnd > rom.size.toLong()) return emptyMap()
        cancellation.throwIfCancellationRequested()

        val searchableDexCount = minOf(table.count, MAX_DEX_NUMBER)
        val missingDexNumbers = BooleanArray(searchableDexCount + 1)
        var missingCount = 0
        for (dexNumber in 1..searchableDexCount) {
            val offset = table.offset + (dexNumber - 1) * table.recordSize
            if (!validRecord(rom, offset, dexNumber)) {
                missingDexNumbers[dexNumber] = true
                missingCount++
            }
        }
        if (missingCount == 0) return emptyMap()

        val consumers = farCopyConsumers(rom, cancellation) ?: return emptyMap()
        if (consumers.isEmpty()) return emptyMap()
        val candidates = arrayOfNulls<Gen1DetachedSpeciesRecord>(searchableDexCount + 1)
        val ambiguous = BooleanArray(searchableDexCount + 1)
        var candidateCount = 0
        var offset = 0
        while (offset <= rom.size - RECORD_SIZE) {
            if (offset % CANCELLATION_CHECK_INTERVAL_BYTES == 0) {
                cancellation.throwIfCancellationRequested()
            }
            if (offset < table.offset || offset.toLong() >= ordinaryEnd) {
                val dexNumber = rom.u8(offset)
                if (dexNumber in 1..searchableDexCount && missingDexNumbers[dexNumber] &&
                    validRecord(rom, offset, dexNumber)
                ) {
                    val candidate = detachedRecord(rom, offset, dexNumber, consumers)
                    if (candidate != null) {
                        candidateCount++
                        if (candidateCount > MAX_DETACHED_CANDIDATES) return emptyMap()
                        if (candidates[dexNumber] == null && !ambiguous[dexNumber]) {
                            candidates[dexNumber] = candidate
                        } else {
                            candidates[dexNumber] = null
                            ambiguous[dexNumber] = true
                        }
                    }
                }
            }
            offset++
        }
        return buildMap {
            for (dexNumber in 1..searchableDexCount) {
                if (!ambiguous[dexNumber]) candidates[dexNumber]?.let { put(dexNumber, it) }
            }
        }
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

    private fun detachedRecord(
        rom: RomImage,
        offset: Int,
        dexNumber: Int,
        consumers: Set<Int>,
    ): Gen1DetachedSpeciesRecord? {
        val bank = offset / BANK_SIZE
        if (bank <= 0) return null
        val address = BANKED_ADDRESS_START + offset % BANK_SIZE
        if (consumerKey(address, bank) !in consumers) return null
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

    /** Indexes `ld hl,record; ld de,destination; ld bc,28; ld a,bank; call FarCopyData`. */
    private fun farCopyConsumers(rom: RomImage, cancellation: ParserCancellationToken): Set<Int>? {
        val consumers = linkedSetOf<Int>()
        val instructionBytes = 14
        var offset = 0
        while (offset <= rom.size - instructionBytes) {
            if (offset % CANCELLATION_CHECK_INTERVAL_BYTES == 0) {
                cancellation.throwIfCancellationRequested()
            }
            if (rom.u8(offset) == 0x21 &&
                rom.u8(offset + 3) == 0x11 &&
                rom.u8(offset + 6) == 0x01 && rom.u16le(offset + 7) == RECORD_SIZE &&
                rom.u8(offset + 9) == 0x3E &&
                rom.u8(offset + 11) == 0xCD
            ) {
                consumers += consumerKey(rom.u16le(offset + 1), rom.u8(offset + 10))
                if (consumers.size > MAX_FAR_COPY_CONSUMERS) return null
            }
            offset++
        }
        return consumers
    }

    private fun consumerKey(address: Int, bank: Int): Int = bank shl 16 or address

    private const val RECORD_SIZE = 28
    private const val DIMENSIONS_OFFSET = 10
    private const val FRONT_POINTER_OFFSET = 11
    private const val BACK_POINTER_OFFSET = 13
    private const val BANK_SIZE = 0x4000
    private const val BANKED_ADDRESS_START = 0x4000
    private const val BANKED_ADDRESS_END = 0x7FFF
    private const val MAX_DEX_NUMBER = 0xFF
    private const val MAX_FAR_COPY_CONSUMERS = 4_096
    private const val MAX_DETACHED_CANDIDATES = 4_096
    private const val CANCELLATION_CHECK_INTERVAL_BYTES = 4 * 1_024
}
