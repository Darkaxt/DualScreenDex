package com.enrpau.dualscreendex.parser.validate

import com.enrpau.dualscreendex.parser.io.RomBoundsException
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ValidationEvidence

object SpriteValidators {
    fun gen1(
        rom: RomImage,
        baseStatsOffset: Int,
        speciesCount: Int,
        recordSize: Int,
        candidatePicBanks: IntArray,
    ): ValidationEvidence = safely(baseStatsOffset, recordSize, speciesCount) {
        var validPointers = 0
        repeat(speciesCount) { index ->
            val base = baseStatsOffset + index * recordSize
            val dimensions = rom.u8(base + GEN1_DIMENSIONS_OFFSET)
            val width = dimensions ushr 4
            val height = dimensions and 0x0F
            val front = rom.u16le(base + GEN1_FRONT_POINTER_OFFSET)
            val back = rom.u16le(base + GEN1_BACK_POINTER_OFFSET)
            if (width in 1..7 && height in 1..7 && front in 0x4000..0x7FFF && back in 0x4000..0x7FFF) {
                validPointers++
            }
        }
        val samples = sampleIndices(speciesCount)
        val validSamples = samples.count { index ->
            val base = baseStatsOffset + index * recordSize
            val dimensions = rom.u8(base + GEN1_DIMENSIONS_OFFSET)
            val front = rom.u16le(base + GEN1_FRONT_POINTER_OFFSET)
            candidatePicBanks.any { bank ->
                rom.gbBankAddress(bank, front)?.let { validGen1Stream(rom, it, dimensions) } == true
            }
        }
        result(validPointers, speciesCount, validSamples, samples.size, baseStatsOffset, recordSize, "Gen 1 sprite references")
    }

    fun gen2(
        rom: RomImage,
        pointerTableOffset: Int,
        speciesCount: Int,
        bankAdjustment: Int,
    ): ValidationEvidence = safely(pointerTableOffset, GEN2_POINTER_RECORD_SIZE, speciesCount) {
        var validPointers = 0
        repeat(speciesCount) { index ->
            val base = pointerTableOffset + index * GEN2_POINTER_RECORD_SIZE
            if (gen2Pointers(rom, base, bankAdjustment).all { it != null }) validPointers++
        }
        val samples = sampleIndices(speciesCount)
        val validSamples = samples.count { index ->
            val base = pointerTableOffset + index * GEN2_POINTER_RECORD_SIZE
            gen2Pointers(rom, base, bankAdjustment).all { offset -> offset != null && validLz3Stream(rom, offset) }
        }
        result(
            validPointers, speciesCount, validSamples, samples.size, pointerTableOffset,
            GEN2_POINTER_RECORD_SIZE, "Gen 2 sprite references",
        )
    }

    fun gen3(
        rom: RomImage,
        pointerTableOffset: Int,
        speciesCount: Int,
        recordSize: Int,
    ): ValidationEvidence = safely(pointerTableOffset, recordSize, speciesCount) {
        var validPointers = 0
        repeat(speciesCount) { index ->
            val base = pointerTableOffset + index * recordSize
            val pointer = rom.gbaPointer(base)
            val size = rom.u16le(base + 4)
            if (pointer != null && size in 1..MAX_SPRITE_OUTPUT) validPointers++
        }
        val samples = sampleIndices(speciesCount)
        val validSamples = samples.count { index ->
            val base = pointerTableOffset + index * recordSize
            val pointer = rom.gbaPointer(base)
            val size = rom.u16le(base + 4)
            pointer != null && validGbaLz77Stream(rom, pointer, size)
        }
        result(validPointers, speciesCount, validSamples, samples.size, pointerTableOffset, recordSize, "Gen 3 sprite references")
    }

    private fun gen2Pointers(rom: RomImage, base: Int, bankAdjustment: Int): List<Int?> = listOf(
        rom.gbBankAddress(rom.u8(base) + bankAdjustment, rom.u16le(base + 1)),
        rom.gbBankAddress(rom.u8(base + 3) + bankAdjustment, rom.u16le(base + 4)),
    )

    private fun validGen1Stream(rom: RomImage, offset: Int, expectedDimensions: Int): Boolean = try {
        val bankEnd = minOf(rom.size, ((offset / GB_BANK_SIZE) + 1) * GB_BANK_SIZE)
        val reader = RomBitReader(rom, offset, bankEnd)
        val width = reader.readBits(4)
        val height = reader.readBits(4)
        if (width !in 1..15 || height != width || expectedDimensions != (width shl 4 or height)) return false
        reader.readBit()
        if (!fillGen1Plane(reader, width * height * 32)) return false
        if (reader.readBit() != 0) reader.readBit()
        fillGen1Plane(reader, width * height * 32)
    } catch (_: RomBoundsException) {
        false
    }

    private fun fillGen1Plane(reader: RomBitReader, expectedGroups: Int): Boolean {
        var zeroMode = reader.readBit() == 0
        var groups = 0
        while (groups < expectedGroups) {
            if (zeroMode) {
                var width = 0
                while (reader.readBit() != 0) {
                    width++
                    if (width >= 16) return false
                }
                val run = (1 shl (width + 1)) - 1 + reader.readBits(width + 1)
                if (groups + run > expectedGroups) return false
                groups += run
            } else {
                while (groups < expectedGroups) {
                    if (reader.readBits(2) == 0) break
                    groups++
                }
            }
            zeroMode = !zeroMode
        }
        return groups == expectedGroups
    }

    private fun validLz3Stream(rom: RomImage, offset: Int): Boolean = try {
        val bankEnd = minOf(rom.size, ((offset / GB_BANK_SIZE) + 1) * GB_BANK_SIZE)
        var cursor = offset
        var output = 0
        var commands = 0
        while (cursor < bankEnd && commands++ < MAX_LZ_COMMANDS) {
            val control = rom.u8(cursor++)
            if (control == LZ3_END) return output > 0
            var command = control ushr 5
            val length = if (command == LZ3_LONG) {
                command = (control ushr 2) and 0x07
                if (command == LZ3_LONG || cursor >= bankEnd) return false
                (((control and 0x03) shl 8) or rom.u8(cursor++)) + 1
            } else {
                (control and 0x1F) + 1
            }
            when (command) {
                LZ3_LITERAL -> cursor += length
                LZ3_ITERATE -> cursor += 1
                LZ3_ALTERNATE -> cursor += 2
                LZ3_ZERO -> Unit
                LZ3_REPEAT, LZ3_FLIP, LZ3_REVERSE -> {
                    if (cursor >= bankEnd || output == 0) return false
                    val first = rom.u8(cursor++)
                    val source = if (first and 0x80 != 0) {
                        val distance = (first and 0x7F) + 1
                        if (distance > output) return false
                        output - distance
                    } else {
                        if (cursor >= bankEnd) return false
                        ((first and 0x7F) shl 8) or rom.u8(cursor++)
                    }
                    if (source !in 0 until output || command == LZ3_REVERSE && source - length + 1 < 0) return false
                }
                else -> return false
            }
            if (cursor > bankEnd || output + length > MAX_SPRITE_OUTPUT) return false
            output += length
        }
        false
    } catch (_: RomBoundsException) {
        false
    }

    private fun validGbaLz77Stream(rom: RomImage, offset: Int, expectedSize: Int): Boolean = try {
        if (rom.u8(offset) != GBA_LZ77_HEADER) return false
        val declaredSize = rom.u24le(offset + 1)
        if (declaredSize !in 1..MAX_SPRITE_OUTPUT || declaredSize < expectedSize || declaredSize % expectedSize != 0) return false
        var cursor = offset + 4
        var output = 0
        while (output < declaredSize) {
            val flags = rom.u8(cursor++)
            for (bit in 7 downTo 0) {
                if (output == declaredSize) break
                if (flags and (1 shl bit) == 0) {
                    rom.u8(cursor++)
                    output++
                } else {
                    val first = rom.u8(cursor++)
                    val second = rom.u8(cursor++)
                    val length = (first ushr 4) + 3
                    val distance = ((first and 0x0F) shl 8 or second) + 1
                    if (distance > output || output + length > declaredSize) return false
                    output += length
                }
            }
        }
        output == declaredSize
    } catch (_: RomBoundsException) {
        false
    }

    private fun sampleIndices(count: Int, maximumSamples: Int = 16): List<Int> {
        if (count <= 0) return emptyList()
        val samples = minOf(count, maximumSamples)
        if (samples == 1) return listOf(0)
        return (0 until samples).map { index -> index * (count - 1) / (samples - 1) }.distinct()
    }

    private fun result(
        validPointers: Int,
        count: Int,
        validSamples: Int,
        totalSamples: Int,
        offset: Int,
        recordSize: Int,
        label: String,
    ): ValidationEvidence {
        val confidence = validPointers.toDouble() / count.coerceAtLeast(1)
        val sampleConfidence = validSamples.toDouble() / totalSamples.coerceAtLeast(1)
        val compatible = count > 0 && confidence >= MINIMUM_POINTER_RATIO && sampleConfidence >= MINIMUM_SAMPLE_RATIO
        val reasons = buildList {
            if (confidence < MINIMUM_POINTER_RATIO) add("valid $label $validPointers/$count below $MINIMUM_POINTER_RATIO")
            if (sampleConfidence < MINIMUM_SAMPLE_RATIO) {
                add("valid representative compressed sprite samples $validSamples/$totalSamples below $MINIMUM_SAMPLE_RATIO")
            }
        }
        return ValidationEvidence(compatible, validPointers, count, confidence, reasons, offset, recordSize)
    }

    private inline fun safely(
        offset: Int,
        recordSize: Int,
        count: Int,
        block: () -> ValidationEvidence,
    ): ValidationEvidence = try {
        block()
    } catch (error: RomBoundsException) {
        ValidationEvidence(false, 0, count, 0.0, listOf(error.message ?: "out-of-bounds table"), offset, recordSize)
    }

    private class RomBitReader(
        private val rom: RomImage,
        private val start: Int,
        private val endExclusive: Int,
    ) {
        private var bitIndex = 0

        fun readBit(): Int {
            val byteOffset = start + bitIndex / 8
            if (byteOffset >= endExclusive) throw RomBoundsException("compressed bitstream crosses bank boundary")
            val value = rom.u8(byteOffset) ushr (7 - bitIndex % 8) and 1
            bitIndex++
            return value
        }

        fun readBits(count: Int): Int {
            var value = 0
            repeat(count) { value = value shl 1 or readBit() }
            return value
        }
    }

    private const val GEN1_DIMENSIONS_OFFSET = 10
    private const val GEN1_FRONT_POINTER_OFFSET = 11
    private const val GEN1_BACK_POINTER_OFFSET = 13
    private const val GEN2_POINTER_RECORD_SIZE = 6
    private const val GB_BANK_SIZE = 0x4000
    private const val MAX_LZ_COMMANDS = 4096
    private const val MAX_SPRITE_OUTPUT = 0x10000
    private const val MINIMUM_POINTER_RATIO = 0.90
    private const val MINIMUM_SAMPLE_RATIO = 0.65
    private const val LZ3_LITERAL = 0
    private const val LZ3_ITERATE = 1
    private const val LZ3_ALTERNATE = 2
    private const val LZ3_ZERO = 3
    private const val LZ3_REPEAT = 4
    private const val LZ3_FLIP = 5
    private const val LZ3_REVERSE = 6
    private const val LZ3_LONG = 7
    private const val LZ3_END = 0xFF
    private const val GBA_LZ77_HEADER = 0x10
}
