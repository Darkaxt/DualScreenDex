package com.enrpau.dualscreendex.parser.dataset.abilities

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

fun interface AbilityNameTableDecoder {
    fun decode(
        session: RomAnalysisSession,
        layout: AbilityNameTableLayout,
        semanticDomain: AbilitySemanticDomain,
    ): AbilityNameTableOutcome
}

/** Sole byte-level interpreter for Gen III fixed-width and direct-record ability names. */
class AbilityNameCodec : AbilityNameTableDecoder {
    override fun decode(
        session: RomAnalysisSession,
        layout: AbilityNameTableLayout,
        semanticDomain: AbilitySemanticDomain,
    ): AbilityNameTableOutcome {
        val extent = when (val check = checkStridedExtent(
            offset = layout.offset,
            count = layout.count,
            stride = layout.stride.toLong(),
            fieldOffset = layout.nameOffset.toLong(),
            fieldSize = layout.nameWidth.toLong(),
            romSize = session.rom.size.toLong(),
            extentLimit = session.limits.maxDatasetExtentBytes,
        )) {
            is StridedExtentCheck.Valid -> check
            is StridedExtentCheck.Invalid -> return AbilityNameTableOutcome.Rejected(layout, check.reason)
            is StridedExtentCheck.BudgetExceeded -> return AbilityNameTableOutcome.ExtentBudgetExceeded(
                layout,
                check.observedBytes,
                check.limitBytes,
                "ability-name table extent ${check.observedBytes} exceeds deterministic budget ${check.limitBytes}",
            )
        }
        if (layout.count > Int.MAX_VALUE.toLong()) {
            return AbilityNameTableOutcome.Rejected(layout, "ability-name count cannot be represented by indexed rows")
        }

        val rows = List(layout.count.toInt()) { rowIndex ->
            val recordOffset = extent.offset + rowIndex * layout.stride + layout.nameOffset
            decodeRow(session, rowIndex, recordOffset, layout.nameWidth)
        }
        if (rows.firstOrNull() !is AbilityNameRowOutcome.StructuralSentinel) {
            return AbilityNameTableOutcome.Rejected(layout, "ability-name row zero is not a structural none sentinel")
        }

        val boundaryCandidates = rows.indices.asSequence()
            .drop(1)
            .filter { index -> index > semanticDomain.maximumDirectAbilityId }
            .filter { index -> rows[index] is AbilityNameRowOutcome.StructuralSentinel }
            .filter { index ->
                rows.drop(index + 1).take(3).let { following ->
                    following.size >= 2 && following.all { it is AbilityNameRowOutcome.Decoded }
                }
            }
            .toList()
        if (boundaryCandidates.size > 1) {
            return AbilityNameTableOutcome.Rejected(
                layout,
                "multiple post-domain ability sentinels could bound the direct-ID catalog",
            )
        }
        val boundary = boundaryCandidates.singleOrNull()
        val lastDecodedRow = rows.indexOfLast { it is AbilityNameRowOutcome.Decoded }
        val terminalPaddingStart = (lastDecodedRow + 1).takeIf { start ->
            lastDecodedRow >= semanticDomain.maximumDirectAbilityId &&
                start in 2 until rows.size &&
                rows.subList(start, rows.size).all { it is AbilityNameRowOutcome.StructuralSentinel }
        }
        val baseRowCount = boundary ?: terminalPaddingStart ?: rows.size
        val activeOutsideCatalog = semanticDomain.activeAbilityIds.filter { it >= baseRowCount }
        if (activeOutsideCatalog.isNotEmpty()) {
            return AbilityNameTableOutcome.Rejected(
                layout,
                "decoded base stats reference ability IDs outside the proposed direct catalog: $activeOutsideCatalog",
            )
        }
        val unresolvedActive = semanticDomain.activeAbilityIds.filter { rows[it] is AbilityNameRowOutcome.Malformed }
        val unnamedActive = semanticDomain.activeAbilityIds.filter { rows[it] !is AbilityNameRowOutcome.Decoded }
        val expectedBase = baseRowCount - 1
        val decodedBase = rows.subList(1, baseRowCount).count { it is AbilityNameRowOutcome.Decoded }
        val denseEnough = decodedBase.toLong() * 100L >= expectedBase.toLong() * MINIMUM_BASE_COVERAGE_PERCENT
        val completeCompiledDomain = semanticDomain.activeAbilityIds.isNotEmpty() && unnamedActive.isEmpty()
        if (!denseEnough && !completeCompiledDomain) {
            return AbilityNameTableOutcome.Rejected(
                layout,
                "named direct ability coverage $decodedBase/$expectedBase is below " +
                    "$MINIMUM_BASE_COVERAGE_PERCENT% and the compiled base-stat domain is incomplete: " +
                    unnamedActive,
            )
        }
        val aliases = boundary?.let { separator ->
            rows.drop(separator + 1).mapNotNull { row ->
                (row as? AbilityNameRowOutcome.Decoded)?.let { AbilityAliasLabel(it.rowIndex, it.name) }
            }
        }.orEmpty()
        return AbilityNameTableOutcome.Decoded(
            layout,
            ResolvedAbilityNameLayout(layout, rows, baseRowCount, aliases, unresolvedActive),
        )
    }

    private fun decodeRow(
        session: RomAnalysisSession,
        rowIndex: Int,
        offset: Int,
        width: Int,
    ): AbilityNameRowOutcome {
        val raw = session.rom.slice(offset, width)
        val decoded = PokemonTextCodec.gbaEnglish.decodeDetailed(raw)
        if (rowIndex == 0) {
            val terminator = raw.indexOfFirst { it.toInt() and 0xFF == 0xFF }
            val blankNone = terminator == 0 && raw.drop(1).all { it.toInt() == 0 }
            val placeholderNone = terminator > 0 &&
                raw.take(terminator).all { it.toInt() and 0xFF in ROW_ZERO_PLACEHOLDER_BYTES } &&
                raw.drop(terminator + 1).all { it.toInt() and 0xFF in ROW_ZERO_PADDING_BYTES }
            return if (blankNone || placeholderNone) {
                AbilityNameRowOutcome.StructuralSentinel(rowIndex, decoded.text)
            } else {
                AbilityNameRowOutcome.Malformed(
                    rowIndex,
                    listOf("ability-name row zero is not a terminated structural none sentinel with padding"),
                )
            }
        }
        val complete = decoded.terminated || decoded.contentBytes == width
        if (!complete || decoded.validRatio < MINIMUM_VALID_BYTE_RATIO || decoded.text.isBlank()) {
            return AbilityNameRowOutcome.Malformed(
                rowIndex,
                listOf("ability name is blank, unterminated, or contains too many undecodable bytes"),
            )
        }
        return if (decoded.text.any(Char::isLetterOrDigit)) {
            AbilityNameRowOutcome.Decoded(rowIndex, decoded.text)
        } else {
            AbilityNameRowOutcome.StructuralSentinel(rowIndex, decoded.text)
        }
    }

    private companion object {
        const val MINIMUM_VALID_BYTE_RATIO = 0.80
        const val MINIMUM_BASE_COVERAGE_PERCENT = 85L
        val ROW_ZERO_PLACEHOLDER_BYTES = setOf(0xAD, 0xAE, 0xAF, 0xB0)
        val ROW_ZERO_PADDING_BYTES = setOf(0x00, 0xFF)
    }
}

internal sealed interface StridedExtentCheck {
    data class Valid(
        val offset: Int,
        val endExclusive: Int,
        val observedBytes: Long,
    ) : StridedExtentCheck

    data class Invalid(val reason: String) : StridedExtentCheck

    data class BudgetExceeded(val observedBytes: Long, val limitBytes: Long) : StridedExtentCheck
}

internal fun checkStridedExtent(
    offset: Long,
    count: Long,
    stride: Long,
    fieldOffset: Long,
    fieldSize: Long,
    romSize: Long,
    extentLimit: Long,
): StridedExtentCheck {
    if (offset < 0 || count <= 0 || stride <= 0 || fieldOffset < 0 || fieldSize <= 0 || romSize < 0) {
        return StridedExtentCheck.Invalid("strided extent requires non-negative offsets and positive sizes")
    }
    val lastRecordDistance = checkedMultiply(count - 1L, stride)
        ?: return StridedExtentCheck.Invalid("strided table record distance overflows Long")
    val fieldEnd = checkedAdd(fieldOffset, fieldSize)
        ?: return StridedExtentCheck.Invalid("strided table field extent overflows Long")
    val observedBytes = checkedAdd(lastRecordDistance, fieldEnd)
        ?: return StridedExtentCheck.Invalid("strided table byte length overflows Long")
    if (observedBytes > extentLimit) return StridedExtentCheck.BudgetExceeded(observedBytes, extentLimit)
    val endExclusive = checkedAdd(offset, observedBytes)
        ?: return StridedExtentCheck.Invalid("strided table end offset overflows Long")
    if (endExclusive > romSize) {
        return StridedExtentCheck.Invalid("strided table span $offset..<$endExclusive exceeds ROM size $romSize")
    }
    if (offset > Int.MAX_VALUE || endExclusive > Int.MAX_VALUE) {
        return StridedExtentCheck.Invalid("strided table span cannot be represented by indexed Int offsets")
    }
    return StridedExtentCheck.Valid(offset.toInt(), endExclusive.toInt(), observedBytes)
}

private fun checkedAdd(left: Long, right: Long): Long? = try {
    Math.addExact(left, right)
} catch (_: ArithmeticException) {
    null
}

private fun checkedMultiply(left: Long, right: Long): Long? = try {
    Math.multiplyExact(left, right)
} catch (_: ArithmeticException) {
    null
}
