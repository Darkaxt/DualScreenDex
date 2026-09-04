package com.enrpau.dualscreendex.parser.validate

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomBoundsException
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.text.LanguageTextPlausibility
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import kotlin.math.abs

object TableValidators {
    fun names(
        rom: RomImage,
        table: TableLayout,
        count: Int,
        codec: PokemonTextCodec,
        minimumRatio: Double = 0.85,
    ): ValidationEvidence {
        if (table.variableLength) return variableNames(rom, table.offset, count, codec)
        if (table.stride == null && !table.valuesArePointers) {
            return fixedNames(rom, table.offset, count, table.recordSize, codec, minimumRatio)
        }
        return safely(table.offset, table.recordSize, count) {
            var valid = 0
            repeat(count) { index ->
                val record = table.offset + index * (table.stride ?: table.recordSize)
                val value = if (table.valuesArePointers) rom.gbaPointer(record) else record
                if (value != null) {
                    val width = if (table.valuesArePointers) minOf(64, rom.size - value) else table.recordSize
                    val decoded = codec.decodeDetailed(rom.slice(value, width))
                    if (decoded.text.isNotBlank() && decoded.validRatio >= 0.8 && decoded.terminated) valid++
                }
            }
            val confidence = valid.toDouble() / count.coerceAtLeast(1)
            ValidationEvidence(
                compatible = confidence >= minimumRatio,
                validRecords = valid,
                totalRecords = count,
                confidence = confidence,
                reasons = if (confidence >= minimumRatio) emptyList() else listOf("valid strided names $valid/$count below $minimumRatio"),
                offset = table.offset,
                recordSize = table.recordSize,
            )
        }
    }

    fun pokeemeraldExpansionMoveData(
        rom: RomImage,
        table: TableLayout,
        count: Int,
    ): ValidationEvidence = safely(table.offset, table.recordSize, count) {
        var valid = 0
        repeat(count) { index ->
            val base = table.offset + index * (table.stride ?: table.recordSize)
            val packed = rom.u16le(base + 10)
            val category = (packed ushr 5) and 0x3
            val power = packed ushr 7
            val accuracy = rom.u16le(base + 12) and 0x7F
            val pp = rom.u8(base + 14)
            val reserved = (0 until minOf(table.recordSize, 16)).all { rom.u8(base + it) == 0 }
            if (index == 0 || reserved || (category in 0..2 && power in 0..511 && accuracy in 0..100 && pp in 0..64)) {
                valid++
            }
        }
        val confidence = valid.toDouble() / count.coerceAtLeast(1)
        ValidationEvidence(
            compatible = confidence >= 0.98,
            validRecords = valid,
            totalRecords = count,
            confidence = confidence,
            reasons = if (confidence >= 0.98) emptyList() else listOf("plausible expansion move records $valid/$count below 98%"),
            offset = table.offset,
            recordSize = table.recordSize,
        )
    }

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
            val plausible = plausibleFixedName(decoded, width, minimumRatio)
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
        var pendingFullWidth = false
        var fullWidthRunStart = -1
        var lastFullWidthGood = 0
        var consecutiveInvalid = 0
        for (index in 0 until maximumCount) {
            val recordOffset = offset.toLong() + index.toLong() * width
            if (recordOffset + width > rom.size) break
            val decoded = codec.decodeDetailed(rom.slice(recordOffset.toInt(), width))
            val valid = plausibleFixedName(decoded, width, minimumRatio = 0.8)
            if (valid && !decoded.terminated) {
                if (
                    pendingFullWidth &&
                    LanguageTextPlausibility.startsWithLowercaseName(decoded.text, codec.language)
                ) break
                if (!pendingFullWidth) fullWidthRunStart = index
                pendingFullWidth = true
                lastFullWidthGood = index + 1
                consecutiveInvalid = 0
            } else if (valid) {
                if (
                    pendingFullWidth &&
                    !LanguageTextPlausibility.looksLikeStandaloneFixedName(decoded.text, codec.language)
                ) break
                lastGood = index + 1
                pendingFullWidth = false
                fullWidthRunStart = -1
                lastFullWidthGood = 0
                consecutiveInvalid = 0
            } else {
                if (pendingFullWidth) {
                    val fullWidthRunLength = lastFullWidthGood - fullWidthRunStart
                    if (
                        codec.applicablePlatforms.any { it == Platform.GB || it == Platform.GBC } &&
                        fullWidthRunLength >= MIN_TRAILING_FULL_WIDTH_NAME_RUN &&
                        lastFullWidthGood >= minimumCount
                    ) {
                        lastGood = lastFullWidthGood
                    }
                    break
                }
                consecutiveInvalid++
                if (consecutiveInvalid >= 2 && index + 1 >= minimumCount) break
            }
        }
        return lastGood.takeIf { it >= minimumCount }
    }

    private const val MIN_TRAILING_FULL_WIDTH_NAME_RUN = 3

    fun locateFixedNameTable(
        rom: RomImage,
        count: Int,
        candidateWidths: IntRange,
        codec: PokemonTextCodec,
        preferredOffset: Int? = null,
    ): ValidationEvidence? = locateFixedTable(rom, count, candidateWidths, preferredOffset) { offset, width ->
        val decoded = codec.decodeDetailed(rom.slice(offset, width))
        plausibleFixedName(decoded, width, minimumRatio = 0.8)
    }

    fun locateFixedNameSequenceNear(
        rom: RomImage,
        approximateOffset: Int,
        candidateWidths: IntRange,
        codec: PokemonTextCodec,
        expectedNames: List<String>,
        searchRadius: Int = 0x10000,
    ): ValidationEvidence? {
        if (expectedNames.isEmpty()) return null
        val start = maxOf(0, approximateOffset - searchRadius)
        return candidateWidths.filter { it > 0 }.asSequence().flatMap { width ->
            val end = minOf(rom.size - expectedNames.size * width, approximateOffset + searchRadius)
            if (end < start) emptySequence() else (start..end).asSequence().mapNotNull { offset ->
                val matches = expectedNames.indices.all { index ->
                    codec.decode(rom.slice(offset + index * width, width)).equals(expectedNames[index], ignoreCase = true)
                }
                if (!matches) null else ValidationEvidence(
                    compatible = true,
                    validRecords = expectedNames.size,
                    totalRecords = expectedNames.size,
                    confidence = 1.0,
                    reasons = listOf("matched leading canonical records"),
                    offset = offset,
                    recordSize = width,
                )
            }
        }.minWithOrNull(compareBy<ValidationEvidence> { abs(requireNotNull(it.offset) - approximateOffset) }.thenBy { it.offset })
    }

    fun locateVariableNameSequenceNear(
        rom: RomImage,
        approximateOffset: Int,
        codec: PokemonTextCodec,
        expectedNames: List<String>,
        searchRadius: Int = 0x10000,
        maximumWidth: Int = 24,
    ): Int? {
        if (expectedNames.isEmpty()) return null
        val start = maxOf(0, approximateOffset - searchRadius)
        val end = minOf(rom.size - 1, approximateOffset + searchRadius)
        return (start..end).asSequence().filter { offset ->
            offset == 0 || rom.u8(offset - 1) == codec.terminator
        }.filter { offset ->
            var cursor = offset
            expectedNames.all { expected ->
                val bytes = ArrayList<Byte>()
                var terminated = false
                repeat(maximumWidth) {
                    if (!terminated && cursor < rom.size) {
                        val value = rom.u8(cursor++)
                        bytes += value.toByte()
                        terminated = value == codec.terminator
                    }
                }
                terminated && codec.decode(bytes.toByteArray()).equals(expected, ignoreCase = true)
            }
        }.minWithOrNull(compareBy<Int> { abs(it - approximateOffset) }.thenBy { it })
    }

    private fun plausibleFixedName(
        decoded: com.enrpau.dualscreendex.parser.text.DecodedText,
        width: Int,
        minimumRatio: Double,
    ): Boolean {
        if (decoded.text.isBlank() || decoded.validRatio < minimumRatio) return false
        if (decoded.terminated) return true
        val distinctNameCharacters = decoded.text.asSequence()
            .filter(Char::isLetterOrDigit)
            .map(Char::lowercaseChar)
            .toSet()
        return decoded.contentBytes == width && decoded.validBytes == width && distinctNameCharacters.size >= 2
    }

    fun locateBaseStatTable(
        rom: RomImage,
        count: Int,
        candidateSizes: IntRange,
        generation: Int,
    ): ValidationEvidence? {
        if (generation == 2 && count in 1..255) {
            val candidates = mutableListOf<ValidationEvidence>()
            candidateSizes.filter { it > 0 && it % 2 == 0 }.forEach { size ->
                val finalStart = rom.size - count * size
                for (offset in 0..finalStart.coerceAtLeast(-1)) {
                    if (rom.u8(offset) != 1) continue
                    if ((0 until count).any { index -> rom.u8(offset + index * size) != index + 1 }) continue
                    baseStats(rom, offset, count, size, generation)
                        .takeIf { it.compatible }
                        ?.let(candidates::add)
                }
            }
            return candidates.singleOrNull()?.copy(reasons = listOf("resolved relocated Gen 2 base-stat table"))
        }
        return locateFixedTable(rom, count, candidateSizes) { offset, _ ->
        val statStart = if (generation <= 2) 1 else 0
        val statCount = if (generation == 1) 5 else 6
        val statsValid = (0 until statCount).all { rom.u8(offset + statStart + it) in 1..255 }
        val typeOffset = if (generation == 1) 6 else if (generation == 2) 7 else 6
        val maxType = if (generation == 3) 31 else 27
        statsValid && rom.u8(offset + typeOffset) in 0..maxType && rom.u8(offset + typeOffset + 1) in 0..maxType
        }
    }

    private fun locateFixedTable(
        rom: RomImage,
        count: Int,
        candidateSizes: IntRange,
        preferredOffset: Int? = null,
        recordIsValid: (offset: Int, size: Int) -> Boolean,
    ): ValidationEvidence? {
        if (count <= 0) return null
        val candidates = mutableListOf<ValidationEvidence>()
        candidateSizes.filter { it > 0 }.forEach { size ->
            var best = 0
            val bestOffsets = mutableListOf<Int>()
            for (alignment in 0 until size) {
                val records = (rom.size - alignment) / size
                if (records < count) continue
                val valid = BooleanArray(records) { index -> recordIsValid(alignment + index * size, size) }
                var window = (0 until count).count { valid[it] }
                for (start in 0..records - count) {
                    if (window > best) {
                        best = window
                        bestOffsets.clear()
                        bestOffsets += alignment + start * size
                    } else if (window == best) {
                        bestOffsets += alignment + start * size
                    }
                    if (start < records - count) {
                        if (valid[start]) window--
                        if (valid[start + count]) window++
                    }
                }
            }
            val confidence = best.toDouble() / count
            if (confidence >= 0.90) {
                bestOffsets.distinct().forEach { offset ->
                    candidates += ValidationEvidence(
                        compatible = true,
                        validRecords = best,
                        totalRecords = count,
                        confidence = confidence,
                        reasons = listOf("resolved relocated fixed table"),
                        offset = offset,
                        recordSize = size,
                    )
                }
            }
        }
        val ordered = candidates.sortedWith(
            compareByDescending<ValidationEvidence> { it.validRecords }
                .thenByDescending { it.confidence }
                .thenBy { if (preferredOffset == null) 0 else abs(requireNotNull(it.offset) - preferredOffset) }
                .thenBy { it.recordSize }
                .thenBy { it.offset },
        )
        val winner = ordered.firstOrNull() ?: return null
        val runnerUp = ordered.drop(1).firstOrNull()
        return winner.takeIf {
            runnerUp == null || runnerUp.validRecords < winner.validRecords ||
                (preferredOffset != null && abs(requireNotNull(winner.offset) - preferredOffset) <
                    abs(requireNotNull(runnerUp.offset) - preferredOffset)) ||
                (runnerUp.offset == winner.offset && runnerUp.recordSize == winner.recordSize)
        }
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
        if (offset !in 0 until rom.size || maximumWidth <= 0 || count <= 0) {
            return@safely ValidationEvidence(
                false, 0, count, 0.0, listOf("invalid variable-name byte window or count"), offset, null,
            )
        }
        var cursor = offset
        var valid = 0
        val reasons = mutableListOf<String>()
        for (index in 0 until count) {
            val decoded = codec.decodeDetailed(rom, cursor, maximumWidth, ParserCancellationToken.NONE)
            if (!decoded.terminated || decoded.consumedBytes <= 0) {
                reasons += "unterminated variable name at record $index; next boundary is unknown"
                break
            }
            cursor += decoded.consumedBytes
            if (decoded.text.isNotBlank() && decoded.validRatio >= 0.8) valid++
        }
        val confidence = valid.toDouble() / count
        val compatible = reasons.isEmpty() && confidence >= 0.85
        if (confidence < 0.85) reasons += "valid variable names $valid/$count below 85%"
        ValidationEvidence(compatible, valid, count, confidence, reasons, offset, null)
    }

    fun baseStats(
        rom: RomImage,
        offset: Int,
        count: Int,
        recordSize: Int,
        generation: Int,
    ): ValidationEvidence = safely(offset, recordSize, count) {
        var valid = 0
        var firstRecordValid = false
        repeat(count) { index ->
            val base = offset + index * recordSize
            val statStart = if (generation <= 2) 1 else 0
            val statCount = if (generation == 1) 5 else 6
            val statsValid = (0 until statCount).all { rom.u8(base + statStart + it) in 1..255 }
            val typeOffset = if (generation == 1) 6 else if (generation == 2) 7 else 6
            val maxType = if (generation == 3) 31 else 27
            val typesValid = rom.u8(base + typeOffset) in 0..maxType && rom.u8(base + typeOffset + 1) in 0..maxType
            if (statsValid && typesValid) {
                valid++
                if (index == 0) firstRecordValid = true
            }
        }
        val confidence = valid.toDouble() / count.coerceAtLeast(1)
        val minimumRatio = if (generation == 3 && count >= 1_000) 0.895 else 0.90
        val compatible = confidence >= minimumRatio
        ValidationEvidence(
            compatible, valid, count, confidence,
            if (compatible) emptyList() else listOf("plausible base stats $valid/$count below ${minimumRatio * 100}%"),
            offset, recordSize,
            coveredRecords = if (generation == 3 && count > 0) {
                valid - if (firstRecordValid) 1 else 0
            } else {
                valid
            },
            expectedRecords = if (generation == 3 && count > 0) count - 1 else count,
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
        var populated = 0
        val plausibleRecords = BooleanArray(count)
        var consecutiveInvalid = 0
        var invalidRunStart = -1
        repeat(count) { index ->
            val base = offset + index * recordSize
            val plausible = if (generation == 3) {
                val power = rom.u8(base + 1)
                val type = rom.u8(base + 2)
                val accuracy = rom.u8(base + 3)
                val pp = rom.u8(base + 4)
                val reserved = (0 until recordSize).all { rom.u8(base + it) == 0 }
                if (!reserved) populated++
                index == 0 || reserved || (
                    type in 0..31 &&
                        pp in 0..64 &&
                        (accuracy == 0 || accuracy in 10..100) &&
                        power in 0..255
                    )
            } else {
                val type = rom.u8(base + 3)
                val pp = rom.u8(base + 5)
                type in 0..27 && pp in 1..64
            }
            if (plausible) {
                plausibleRecords[index] = true
                valid++
                consecutiveInvalid = 0
                invalidRunStart = -1
            } else {
                if (consecutiveInvalid == 0) invalidRunStart = index
                consecutiveInvalid++
                if (
                    generation == 3 &&
                    consecutiveInvalid >= 8 &&
                    invalidRunStart >= maxOf(3, count / 2) &&
                    valid == invalidRunStart
                ) {
                    return@safely ValidationEvidence(
                        compatible = true,
                        validRecords = invalidRunStart,
                        totalRecords = invalidRunStart,
                        confidence = 1.0,
                        reasons = listOf("trimmed adjacent non-move data after a complete Gen 3 move-record prefix"),
                        offset = offset,
                        recordSize = recordSize,
                    )
                }
            }
        }
        var prefixIsComplete = true
        val windowSize = 16
        for (start in 0..count - windowSize) {
            if (start > 0 && !plausibleRecords[start - 1]) prefixIsComplete = false
            if (start >= maxOf(3, count / 2) && prefixIsComplete && !plausibleRecords[start]) {
                val invalidInWindow = (start until start + windowSize).count { !plausibleRecords[it] }
                if (invalidInWindow >= 8) {
                    return@safely ValidationEvidence(
                        compatible = true,
                        validRecords = start,
                        totalRecords = start,
                        confidence = 1.0,
                        reasons = listOf("trimmed adjacent non-move data after a complete Gen 3 move-record prefix"),
                        offset = offset,
                        recordSize = recordSize,
                    )
                }
            }
        }
        if (generation == 3) {
            val firstInvalid = plausibleRecords.indexOfFirst { !it }
            val suffixSize = if (firstInvalid >= 0) count - firstInvalid else 0
            val invalidInSuffix = if (firstInvalid >= 0) {
                (firstInvalid until count).count { !plausibleRecords[it] }
            } else {
                0
            }
            if (
                firstInvalid >= maxOf(3, count / 2) &&
                suffixSize >= 8 &&
                invalidInSuffix * 5 >= suffixSize * 2
            ) {
                return@safely ValidationEvidence(
                    compatible = true,
                    validRecords = firstInvalid,
                    totalRecords = firstInvalid,
                    confidence = 1.0,
                    reasons = listOf("trimmed a mostly invalid trailing suffix after a complete Gen 3 move-record prefix"),
                    offset = offset,
                    recordSize = recordSize,
                )
            }
        }
        val confidence = valid.toDouble() / count.coerceAtLeast(1)
        val populatedRatio = populated.toDouble() / (count - 1).coerceAtLeast(1)
        val compatible = if (generation == 3) {
            valid == count && (count <= 1 || populatedRatio >= 0.80)
        } else {
            confidence >= 0.90
        }
        ValidationEvidence(
            compatible, valid, count, confidence,
            if (compatible) emptyList() else listOf(
                "plausible move records $valid/$count do not form a complete populated table; populated $populated/${(count - 1).coerceAtLeast(1)}",
            ),
            offset, recordSize,
        )
    }

    /**
     * Validates the 16-byte move records used by CFRU/DPE forks that widened both the effect and
     * power fields to 16 bits. Zero is the reserved move row; sparse reserved rows are tolerated,
     * but an empty allocation is not evidence of a move table.
     */
    fun cfruMoveData(
        rom: RomImage,
        offset: Int,
        count: Int,
    ): ValidationEvidence = safely(offset, 16, count) {
        var valid = 0
        var populated = 0
        repeat(count) { index ->
            val base = offset + index * 16
            val reserved = (0 until 16).all { rom.u8(base + it) == 0 }
            val accuracy = rom.u8(base + 5)
            val plausible = reserved || (
                rom.u8(base + 4) in 0..31 &&
                    (accuracy in 0..100 || accuracy == 0xFF) &&
                    rom.u8(base + 6) in 0..64 &&
                    rom.u8(base + 9).toByte().toInt() in -8..7 &&
                    (12 until 16).all { rom.u8(base + it) == 0 }
                )
            if (plausible) valid++
            if (!reserved) populated++
        }
        val confidence = valid.toDouble() / count.coerceAtLeast(1)
        val populatedRatio = populated.toDouble() / (count - 1).coerceAtLeast(1)
        val compatible = confidence >= 0.95 && populatedRatio >= 0.80
        ValidationEvidence(
            compatible = compatible,
            validRecords = valid,
            totalRecords = count,
            confidence = minOf(confidence, populatedRatio),
            reasons = if (compatible) {
                listOf(
                    "validated widened 16-byte CFRU/DPE move records; " +
                        "${count - valid} sparse custom records use extension values",
                )
            } else {
                listOf("plausible CFRU/DPE move records $valid/$count; populated $populated/${(count - 1).coerceAtLeast(1)}")
            },
            offset = offset,
            recordSize = 16,
        )
    }

    /**
     * Validates the widened retail BattleMove ABI used by source-derived engines that retain
     * byte flags/string/dance fields and align each record to 16 bytes. The independently
     * resolved type-chart cardinality supplies the type domain; the final invalid row may only
     * trim when it is the sole suffix after a complete populated prefix.
     */
    fun widenedRetailMoveData(
        rom: RomImage,
        offset: Int,
        count: Int,
        maximumTypeId: Int,
    ): ValidationEvidence = safely(offset, 16, count) {
        val plausible = BooleanArray(count)
        var populated = 0
        var flagged = 0
        repeat(count) { index ->
            val base = offset + index * 16
            val reserved = (0 until 16).all { rom.u8(base + it) == 0 }
            if (!reserved) populated++
            if (rom.u8(base + 11) != 0) flagged++
            plausible[index] = reserved || (
                rom.u8(base + 4) in 0..maximumTypeId &&
                    rom.u8(base + 6) in 0..64 &&
                    rom.u16le(base + 8) <= 0x1FF &&
                    rom.u8(base + 10).toByte().toInt() in -8..7 &&
                    rom.u8(base + 13) in 0..1 &&
                    rom.u8(base + 14) == 0 &&
                    rom.u8(base + 15) == 0
                )
        }
        val firstInvalid = plausible.indexOfFirst { !it }
        if (firstInvalid == count - 1 && firstInvalid > 1 &&
            plausible.copyOfRange(0, firstInvalid).all { it } && flagged > 0 &&
            populated - 1 >= firstInvalid - 1
        ) {
            return@safely ValidationEvidence(
                compatible = true,
                validRecords = firstInvalid,
                totalRecords = firstInvalid,
                confidence = 1.0,
                reasons = listOf(
                    "trimmed one non-record suffix after a complete widened retail move table",
                ),
                offset = offset,
                recordSize = 16,
            )
        }
        val valid = plausible.count { it }
        val populatedRatio = populated.toDouble() / (count - 1).coerceAtLeast(1)
        val compatible = valid == count && populatedRatio >= 0.80 && flagged > 0
        ValidationEvidence(
            compatible = compatible,
            validRecords = valid,
            totalRecords = count,
            confidence = minOf(valid.toDouble() / count.coerceAtLeast(1), populatedRatio),
            reasons = if (compatible) {
                listOf("validated widened retail move records with byte flags and aligned tail padding")
            } else {
                listOf(
                    "plausible widened retail move records $valid/$count; " +
                        "populated $populated/${(count - 1).coerceAtLeast(1)}; flagged=$flagged",
                )
            },
            offset = offset,
            recordSize = 16,
        )
    }

    /**
     * Validates the aligned 20-byte BattleMove ABI with widened effect, byte power/type fields,
     * u16 target, u32 flags, split, and argument fields. All compiler padding must remain zero.
     */
    fun hybridBattleMoveData(
        rom: RomImage,
        offset: Int,
        count: Int,
    ): ValidationEvidence = safely(offset, 20, count) {
        val plausibleRows = BooleanArray(count)
        val reservedRows = BooleanArray(count)
        var populated = 0
        repeat(count) { index ->
            val base = offset + index * 20
            val reserved = (0 until 20).all { rom.u8(base + it) == 0 }
            val accuracy = rom.u8(base + 4)
            val secondaryChance = rom.u8(base + 6)
            val priority = rom.u8(base + 10).toByte().toInt()
            val plausible = reserved || (
                rom.u8(base + 3) in 0..31 &&
                    (accuracy == 0 || accuracy in 10..100 || accuracy == 0xFF) &&
                    rom.u8(base + 5) in 0..64 &&
                    (secondaryChance in 0..100 || secondaryChance == 0xFF) &&
                    priority in -8..7 &&
                    rom.u8(base + 7) == 0 &&
                    rom.u8(base + 11) == 0 &&
                    rom.u8(base + 16) in 0..2 &&
                    rom.u8(base + 18) == 0 &&
                    rom.u8(base + 19) == 0
                )
            plausibleRows[index] = plausible
            reservedRows[index] = reserved
            if (!reserved) populated++
        }
        val firstInvalid = plausibleRows.indexOfFirst { !it }
        if (firstInvalid in maxOf(3, count / 2) until count - 1 &&
            !reservedRows[firstInvalid] &&
            plausibleRows.copyOfRange(0, firstInvalid).all { it } &&
            reservedRows.copyOfRange(firstInvalid + 1, count).all { it } &&
            populated - 1 >= firstInvalid - 1
        ) {
            return@safely ValidationEvidence(
                compatible = true,
                validRecords = firstInvalid,
                totalRecords = firstInvalid,
                confidence = 1.0,
                reasons = listOf(
                    "trimmed adjacent non-move data and an empty suffix after a complete hybrid BattleMove table",
                ),
                offset = offset,
                recordSize = 20,
            )
        }
        val valid = plausibleRows.count { it }
        val confidence = valid.toDouble() / count.coerceAtLeast(1)
        val populatedRatio = populated.toDouble() / (count - 1).coerceAtLeast(1)
        val compatible = valid == count && populatedRatio >= 0.80
        ValidationEvidence(
            compatible = compatible,
            validRecords = valid,
            totalRecords = count,
            confidence = minOf(confidence, populatedRatio),
            reasons = if (compatible) {
                listOf("validated aligned 20-byte hybrid BattleMove records")
            } else {
                listOf("plausible hybrid BattleMove records $valid/$count; populated $populated/${(count - 1).coerceAtLeast(1)}")
            },
            offset = offset,
            recordSize = 20,
        )
    }

    /** Validates the later 20-byte Battle Engine move ABI with flags, split, and Z-move fields. */
    fun battleEngineMoveData(
        rom: RomImage,
        offset: Int,
        count: Int,
    ): ValidationEvidence = safely(offset, 20, count) {
        var valid = 0
        var populated = 0
        repeat(count) { index ->
            val base = offset + index * 20
            val reserved = (0 until 20).all { rom.u8(base + it) == 0 }
            val accuracy = rom.u8(base + 5)
            val secondaryChance = rom.u8(base + 7)
            val priority = rom.u8(base + 10).toByte().toInt()
            val plausible = reserved || (
                rom.u16le(base + 2) <= 2048 &&
                    rom.u8(base + 4) in 0..31 &&
                    (accuracy in 0..100 || accuracy == 0xFF) &&
                    rom.u8(base + 6) in 0..64 &&
                    (secondaryChance in 0..100 || secondaryChance == 0xFF) &&
                    priority in -8..7 &&
                    rom.u8(base + 11) == 0 &&
                    rom.u8(base + 16) in 0..2
                )
            if (plausible) valid++
            if (!reserved) populated++
        }
        val confidence = valid.toDouble() / count.coerceAtLeast(1)
        val populatedRatio = populated.toDouble() / (count - 1).coerceAtLeast(1)
        val compatible = confidence >= 0.95 && populatedRatio >= 0.80
        ValidationEvidence(
            compatible = compatible,
            validRecords = valid,
            totalRecords = count,
            confidence = minOf(confidence, populatedRatio),
            reasons = if (compatible) {
                listOf("validated expanded 20-byte Battle Engine move records")
            } else {
                listOf("plausible 20-byte Battle Engine move records $valid/$count; populated $populated/${(count - 1).coerceAtLeast(1)}")
            },
            offset = offset,
            recordSize = 20,
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
        maximumType: Int = when (generation) {
            1 -> 63
            3 -> 18
            else -> 27
        },
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

    /**
     * Locates decomp-style square type charts stored as unsigned Q4.12 multipliers.
     *
     * Both the candidate root and its exact one-past-end boundary must be compiled GBA pointer
     * targets. Base-stat types constrain only the minimum active dimension, because unused chart
     * types need not appear in a species record.
     */
    fun locateReferencedGen3Q412TypeCharts(
        rom: RomImage,
        activeTypeLowerBound: Int? = null,
    ): List<ValidationEvidence> {
        val minimumTypeCount = maxOf(MINIMUM_Q412_TYPE_COUNT, activeTypeLowerBound ?: MINIMUM_Q412_TYPE_COUNT)
        if (minimumTypeCount > MAXIMUM_Q412_TYPE_COUNT) return emptyList()
        val references = HashMap<Int, Int>()
        var pointerOffset = 0
        while (pointerOffset <= rom.size - 4) {
            rom.gbaPointer(pointerOffset)?.let { target ->
                references[target] = (references[target] ?: 0) + 1
            }
            pointerOffset += 4
        }
        val u32Candidates = references.asSequence()
            .flatMap { (offset, count) ->
                (minimumTypeCount..MAXIMUM_Q412_TYPE_COUNT).asSequence().mapNotNull { typeCount ->
                    val end = offset.toLong() + typeCount.toLong() * typeCount * 4
                    if (end > Int.MAX_VALUE || end.toInt() !in references) return@mapNotNull null
                    q412SquareTypeChart(rom, offset, typeCount, elementSize = 4)
                        ?.let { ReferencedQ412Chart(it, count, occupiedMatrices = 1) }
                }
            }
            .toList()
        val u16Candidates = activeTypeLowerBound
            ?.takeIf { it in MINIMUM_Q412_TYPE_COUNT..MAXIMUM_Q412_TYPE_COUNT }
            ?.let { lowerBound ->
                references.flatMap { (offset, count) ->
                    (lowerBound..MAXIMUM_Q412_TYPE_COUNT).mapNotNull { typeCount ->
                        val pairEnd = offset.toLong() + typeCount.toLong() * typeCount * 4
                        if (pairEnd > Int.MAX_VALUE || pairEnd.toInt() !in references) return@mapNotNull null
                        u16Q412TypeChartWithInverseCrossTable(rom, offset, typeCount)
                            ?.let { ReferencedQ412Chart(it, count, occupiedMatrices = 2) }
                    }
                }
            }.orEmpty()
        val candidates = u32Candidates + u16Candidates
        val nonInterior = buildList<ReferencedQ412Chart> {
            candidates.sortedWith(
                compareBy<ReferencedQ412Chart> { it.start }
                    .thenByDescending { it.occupiedEndExclusive },
            ).forEach { candidate ->
                if (none { enclosing -> enclosing.startsBeforeAndOverlaps(candidate) }) add(candidate)
            }
        }
        return nonInterior.asSequence()
            .sortedWith(
                compareByDescending<ReferencedQ412Chart> { it.references }
                    .thenByDescending { it.evidence.confidence }
                    .thenByDescending { it.evidence.totalRecords }
                    .thenBy { it.evidence.offset },
            )
            .map { it.evidence }
            .toList()
    }

    fun resolveGen3TypeChart(
        rom: RomImage,
        inheritedOffset: Int?,
        activeTypeLowerBound: Int? = null,
    ): ValidationEvidence {
        val inherited = inheritedOffset?.let { typeChart(rom, it, generation = 3) }
        if (inherited?.compatible == true) return inherited

        val relocated = locateGen3TypeCharts(rom)
        relocated.firstOrNull()?.let { selected ->
            return selected.copy(
            reasons = if (relocated.size > 1) {
                listOf("found ${relocated.size} valid relocated type charts; selected the lowest offset")
            } else {
                listOf("resolved relocated Gen 3 type chart")
            },
            )
        }

        val matrices = locateReferencedGen3Q412TypeCharts(rom, activeTypeLowerBound)
        if (matrices.size > 1) {
            return ValidationEvidence(
                compatible = false,
                validRecords = 0,
                totalRecords = matrices.size,
                confidence = 0.0,
                reasons = listOf(
                    "ambiguous referenced Q4.12 type-chart roots: " +
                        matrices.joinToString { evidence -> "0x${requireNotNull(evidence.offset).toString(16)}" },
                ),
                ambiguous = true,
                reviewRecommended = true,
            )
        }
        val selectedMatrix = matrices.singleOrNull()
        return selectedMatrix?.copy(
            reasons = when {
                selectedMatrix.offset == inheritedOffset ->
                    listOf("validated referenced inherited Gen 3 Q4.12 type-effectiveness matrix")
                else -> listOf("resolved referenced Gen 3 Q4.12 type-effectiveness matrix")
            },
        ) ?: inherited?.copy(
            reasons = inherited.reasons + "no referenced root/end-bounded Q4.12 matrix validated",
        ) ?: ValidationEvidence(
            compatible = false,
            validRecords = 0,
            totalRecords = 0,
            confidence = 0.0,
            reasons = listOf("type-chart table not resolved"),
        )
    }

    fun inferGen3ActiveTypeCount(
        rom: RomImage,
        table: TableLayout,
        count: Int,
    ): Int? {
        if (count <= 0 || table.recordSize < 8) return null
        val stride = table.stride ?: table.recordSize
        val requiredEnd = table.offset.toLong() + (count - 1L) * stride + 8
        if (stride < 8 || table.offset < 0 || requiredEnd > rom.size) return null
        var plausibleRows = 0
        var maximumType = -1
        repeat(count) { index ->
            val base = table.offset + index * stride
            if ((0 until 6).any { rom.u8(base + it) !in 1..255 }) return@repeat
            val primary = rom.u8(base + 6)
            val secondary = rom.u8(base + 7)
            if (primary !in 0..63 || secondary !in 0..63) return@repeat
            plausibleRows++
            maximumType = maxOf(maximumType, primary, secondary)
        }
        if (plausibleRows < 2 || plausibleRows * 10 < count * 8) return null
        return maxOf(MINIMUM_Q412_TYPE_COUNT, maximumType + 1)
            .takeIf { it <= MAXIMUM_Q412_TYPE_COUNT }
    }

    private fun q412SquareTypeChart(
        rom: RomImage,
        offset: Int,
        typeCount: Int,
        elementSize: Int,
    ): ValidationEvidence? {
        if (elementSize !in setOf(2, 4) || offset % elementSize != 0 || offset !in 0 until rom.size) return null
        val values = typeCount * typeCount
        if (offset.toLong() + values.toLong() * elementSize > rom.size) return null
        val matrix = (0 until values).map { index ->
            when (elementSize) {
                2 -> rom.u16le(offset + index * elementSize).toLong()
                else -> rom.u32le(offset + index * elementSize)
            }
        }
        if (matrix.any { it > Q412_MAXIMUM }) return null
        val neutral = matrix.count { it == Q412_ONE }
        val common = matrix.count { it in COMMON_Q412_MULTIPLIERS }
        val nonNeutral = values - neutral
        if (neutral * 5 < values * 2 || nonNeutral < typeCount || matrix.toSet().size < 3 || common * 10 < values * 9) return null
        return ValidationEvidence(
            compatible = true,
            validRecords = values,
            totalRecords = values,
            confidence = common.toDouble() / values,
            reasons = listOf(
                "validated referenced ${typeCount}x$typeCount u${elementSize * 8} Q4.12 type-effectiveness matrix",
            ),
            offset = offset,
            recordSize = typeCount * elementSize,
            elementSize = elementSize,
        )
    }

    private fun u16Q412TypeChartWithInverseCrossTable(
        rom: RomImage,
        offset: Int,
        typeCount: Int,
    ): ValidationEvidence? {
        val primary = q412SquareTypeChart(rom, offset, typeCount, elementSize = 2) ?: return null
        val values = typeCount * typeCount
        val inverseOffset = offset + values * 2
        val inverse = q412SquareTypeChart(rom, inverseOffset, typeCount, elementSize = 2) ?: return null
        repeat(values) { index ->
            val value = rom.u16le(offset + index * 2)
            val expectedInverse = when {
                value < Q412_ONE -> Q412_DOUBLE
                value > Q412_ONE -> Q412_HALF
                else -> Q412_ONE.toInt()
            }
            if (rom.u16le(inverseOffset + index * 2) != expectedInverse) return null
        }
        return primary.copy(
            confidence = minOf(primary.confidence, inverse.confidence),
            reasons = listOf(
                "validated referenced ${typeCount}x$typeCount u16 Q4.12 type-effectiveness matrix " +
                    "with adjacent inverse cross-table and compiled pair boundary",
            ),
        )
    }

    private data class ReferencedQ412Chart(
        val evidence: ValidationEvidence,
        val references: Int,
        val occupiedMatrices: Int,
    ) {
        val start = requireNotNull(evidence.offset).toLong()
        val occupiedEndExclusive = start +
            evidence.totalRecords.toLong() * requireNotNull(evidence.elementSize) * occupiedMatrices

        fun startsBeforeAndOverlaps(other: ReferencedQ412Chart): Boolean =
            start < other.start && occupiedEndExclusive > other.start
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

    private const val MINIMUM_Q412_TYPE_COUNT = 18
    private const val MAXIMUM_Q412_TYPE_COUNT = 64
    private const val Q412_ONE = 4096L
    private const val Q412_HALF = 2048
    private const val Q412_DOUBLE = 8192
    private const val Q412_MAXIMUM = 0xFFFFL
    private val COMMON_Q412_MULTIPLIERS = setOf(
        0L,
        512L, // 1/8
        819L, // 1/5
        1024L, // 1/4
        1365L, // 1/3
        2048L, // 1/2
        2730L, // 2/3
        4096L,
        6144L, // 3/2
        8192L,
        12288L,
        16384L,
        20480L,
        32768L,
    )

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
