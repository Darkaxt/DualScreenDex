package com.enrpau.dualscreendex.parser.validate

import com.enrpau.dualscreendex.parser.io.RomBoundsException
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

object TableValidators {
    fun fixedNames(
        rom: RomImage,
        offset: Int,
        count: Int,
        width: Int,
        codec: PokemonTextCodec,
        minimumRatio: Double = 0.85,
    ): ValidationEvidence = safely(offset, width, count) {
        var valid = 0
        val reasons = mutableListOf<String>()
        repeat(count) { index ->
            val decoded = codec.decodeDetailed(rom.slice(offset + index * width, width))
            val plausible = decoded.terminated && decoded.text.isNotBlank() && decoded.validRatio >= minimumRatio
            if (plausible) valid++
        }
        val confidence = valid.toDouble() / count.coerceAtLeast(1)
        if (confidence < minimumRatio) reasons += "valid fixed names $valid/$count below $minimumRatio"
        ValidationEvidence(confidence >= minimumRatio, valid, count, confidence, reasons, offset, width)
    }

    fun inferFixedNameCount(
        rom: RomImage,
        offset: Int,
        width: Int,
        codec: PokemonTextCodec,
        minimumCount: Int,
        maximumCount: Int,
    ): Int? {
        if (offset < 0 || width <= 0 || offset >= rom.size) return null
        var lastGood = 0
        var consecutiveInvalid = 0
        for (index in 0 until maximumCount) {
            val recordOffset = offset.toLong() + index.toLong() * width
            if (recordOffset + width > rom.size) break
            val decoded = codec.decodeDetailed(rom.slice(recordOffset.toInt(), width))
            val valid = decoded.terminated && decoded.text.isNotBlank() && decoded.validRatio >= 0.8
            if (valid) {
                lastGood = index + 1
                consecutiveInvalid = 0
            } else {
                consecutiveInvalid++
                if (consecutiveInvalid >= 2 && index + 1 >= minimumCount) break
            }
        }
        return lastGood.takeIf { it >= minimumCount }
    }

    fun inferCountFromFollowingTable(
        offset: Int,
        recordSize: Int,
        followingOffsets: List<Int>,
        minimumCount: Int,
        maximumCount: Int,
        maximumAlignmentPadding: Int = 3,
    ): Int? {
        if (offset < 0 || recordSize <= 0) return null
        val following = followingOffsets.filter { it > offset }.minOrNull() ?: return null
        val distance = following - offset
        val count = distance / recordSize
        val padding = distance % recordSize
        return count.takeIf {
            it in minimumCount..maximumCount && padding <= maximumAlignmentPadding
        }
    }

    fun inferBaseStatsRecordSize(
        rom: RomImage,
        offset: Int,
        count: Int,
        generation: Int,
        candidateSizes: IntRange = 20..64,
    ): Int? = candidateSizes.asSequence()
        .filter { it % 2 == 0 }
        .map { size -> size to baseStats(rom, offset, count, size, generation) }
        .filter { (_, evidence) -> evidence.compatible }
        .sortedWith(compareByDescending<Pair<Int, ValidationEvidence>> { it.second.confidence }.thenBy { it.first })
        .firstOrNull()
        ?.first

    fun variableNames(
        rom: RomImage,
        offset: Int,
        count: Int,
        codec: PokemonTextCodec,
        maximumWidth: Int = 24,
    ): ValidationEvidence = safely(offset, 0, count) {
        var cursor = offset
        var valid = 0
        val reasons = mutableListOf<String>()
        repeat(count) {
            val buffer = ArrayList<Byte>()
            var terminated = false
            var length = 0
            while (length < maximumWidth && cursor < rom.size && !terminated) {
                val value = rom.u8(cursor++)
                buffer += value.toByte()
                if (value == codec.terminator) {
                    terminated = true
                }
                length++
            }
            val decoded = codec.decodeDetailed(buffer.toByteArray())
            if (terminated && decoded.text.isNotBlank() && decoded.validRatio >= 0.8) valid++
        }
        val confidence = valid.toDouble() / count.coerceAtLeast(1)
        if (confidence < 0.85) reasons += "valid variable names $valid/$count below 85%"
        ValidationEvidence(confidence >= 0.85, valid, count, confidence, reasons, offset, null)
    }

    fun baseStats(
        rom: RomImage,
        offset: Int,
        count: Int,
        recordSize: Int,
        generation: Int,
    ): ValidationEvidence = safely(offset, recordSize, count) {
        var valid = 0
        repeat(count) { index ->
            val base = offset + index * recordSize
            val statStart = if (generation <= 2) 1 else 0
            val statCount = if (generation == 1) 5 else 6
            val statsValid = (0 until statCount).all { rom.u8(base + statStart + it) in 1..255 }
            val typeOffset = if (generation == 1) 6 else if (generation == 2) 7 else 6
            val maxType = if (generation == 3) 18 else 27
            val typesValid = rom.u8(base + typeOffset) in 0..maxType && rom.u8(base + typeOffset + 1) in 0..maxType
            if (statsValid && typesValid) valid++
        }
        val confidence = valid.toDouble() / count.coerceAtLeast(1)
        val compatible = confidence >= 0.90
        ValidationEvidence(
            compatible, valid, count, confidence,
            if (compatible) emptyList() else listOf("plausible base stats $valid/$count below 90%"),
            offset, recordSize,
        )
    }

    fun moveData(
        rom: RomImage,
        offset: Int,
        count: Int,
        recordSize: Int,
        generation: Int,
    ): ValidationEvidence = safely(offset, recordSize, count) {
        var valid = 0
        repeat(count) { index ->
            val base = offset + index * recordSize
            if (generation == 3 && index == 0) {
                valid++
            } else {
                val type = rom.u8(base + if (generation == 1) 3 else if (generation == 2) 3 else 2)
                val pp = rom.u8(base + if (generation == 3) 4 else 5)
                val accuracy = rom.u8(base + if (generation == 3) 3 else 4)
                val maxType = if (generation == 3) 17 else 27
                if (type in 0..maxType && pp in 1..64 && accuracy in 0..255) valid++
            }
        }
        val confidence = valid.toDouble() / count.coerceAtLeast(1)
        val compatible = confidence >= 0.90
        ValidationEvidence(
            compatible, valid, count, confidence,
            if (compatible) emptyList() else listOf("plausible move records $valid/$count below 90%"),
            offset, recordSize,
        )
    }

    fun gbaPointerTable(
        rom: RomImage,
        offset: Int,
        count: Int,
        recordSize: Int,
    ): ValidationEvidence = safely(offset, recordSize, count) {
        var valid = 0
        repeat(count) { index ->
            if (rom.gbaPointer(offset + index * recordSize) != null) valid++
        }
        val confidence = valid.toDouble() / count.coerceAtLeast(1)
        val compatible = confidence >= 0.90
        ValidationEvidence(
            compatible, valid, count, confidence,
            if (compatible) emptyList() else listOf("valid GBA pointers $valid/$count below 90%"),
            offset, recordSize,
        )
    }

    fun typeChart(
        rom: RomImage,
        offset: Int,
        generation: Int,
        maximumEntries: Int = 256,
        maximumType: Int = if (generation == 3) 18 else 27,
    ): ValidationEvidence = safely(offset, 3, maximumEntries) {
        var cursor = offset
        var valid = 0
        var entries = 0
        var terminated = false
        while (entries < maximumEntries) {
            val attacker = rom.u8(cursor)
            if (attacker == 0xFE || attacker == 0xFF) {
                terminated = true
                break
            }
            val defender = rom.u8(cursor + 1)
            val multiplier = rom.u8(cursor + 2)
            if (attacker in 0..maximumType && defender in 0..maximumType && multiplier in TYPE_MULTIPLIERS) {
                valid++
            }
            entries++
            cursor += 3
        }
        val confidence = valid.toDouble() / entries.coerceAtLeast(1)
        val compatible = terminated && entries >= 10 && valid == entries
        ValidationEvidence(
            compatible, valid, entries, confidence,
            if (compatible) emptyList() else listOf("type chart lacks a valid terminator or enough entries"),
            offset, 3,
        )
    }

    fun locateGen3TypeCharts(rom: RomImage): List<ValidationEvidence> {
        val canonical = rom.findAll(GEN3_TYPE_CHART_PREFIX)
            .map { offset -> typeChart(rom, offset, generation = 3) }
            .filter { it.compatible }
        if (canonical.isNotEmpty()) return canonical

        return rom.findAll(GEN3_TYPE_CHART_TERMINATOR).mapNotNull { terminatorOffset ->
            var cursor = terminatorOffset - 3
            var records = 0
            val attackers = mutableSetOf<Int>()
            val defenders = mutableSetOf<Int>()
            while (cursor >= 0 && records < 256) {
                val attacker = rom.u8(cursor)
                val defender = rom.u8(cursor + 1)
                val multiplier = rom.u8(cursor + 2)
                if (attacker !in 0..31 || defender !in 0..31 || multiplier !in TYPE_MULTIPLIERS) break
                attackers += attacker
                defenders += defender
                records++
                cursor -= 3
            }
            val offset = cursor + 3
            if (records < 80 || attackers.size < 12 || defenders.size < 12) {
                null
            } else {
                typeChart(rom, offset, generation = 3, maximumType = 31).takeIf { it.compatible }
            }
        }
    }

    fun resolveGen3TypeChart(rom: RomImage, inheritedOffset: Int?): ValidationEvidence {
        val inherited = inheritedOffset?.let { typeChart(rom, it, generation = 3) }
        if (inherited?.compatible == true) return inherited

        val relocated = locateGen3TypeCharts(rom)
        return relocated.firstOrNull()?.copy(
            reasons = if (relocated.size > 1) {
                listOf("found ${relocated.size} valid relocated type charts; selected the lowest offset")
            } else {
                listOf("resolved relocated Gen 3 type chart")
            },
        ) ?: inherited ?: ValidationEvidence(
            compatible = false,
            validRecords = 0,
            totalRecords = 0,
            confidence = 0.0,
            reasons = listOf("type-chart table not resolved"),
        )
    }

    private val GEN3_TYPE_CHART_PREFIX = byteArrayOf(
        0, 5, 5,
        0, 8, 5,
        10, 10, 5,
        10, 11, 5,
        10, 12, 20,
        10, 15, 20,
    )

    private val GEN3_TYPE_CHART_TERMINATOR = byteArrayOf(0xFE.toByte(), 0xFE.toByte(), 0)

    private val TYPE_MULTIPLIERS = setOf(0, 5, 10, 20)

    private inline fun safely(
        offset: Int,
        recordSize: Int?,
        count: Int,
        block: () -> ValidationEvidence,
    ): ValidationEvidence = try {
        block()
    } catch (error: RomBoundsException) {
        ValidationEvidence(false, 0, count, 0.0, listOf(error.message ?: "out-of-bounds table"), offset, recordSize)
    }
}
