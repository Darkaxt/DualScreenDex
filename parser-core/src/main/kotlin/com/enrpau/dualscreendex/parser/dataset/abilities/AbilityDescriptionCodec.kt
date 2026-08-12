package com.enrpau.dualscreendex.parser.dataset.abilities

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

fun interface AbilityDescriptionTableDecoder {
    fun decode(
        session: RomAnalysisSession,
        layout: AbilityDescriptionTableLayout,
    ): AbilityDescriptionTableOutcome
}

/** Decodes each base ability pointer independently; malformed or placeholder rows never cut the tail. */
class AbilityDescriptionCodec : AbilityDescriptionTableDecoder {
    override fun decode(
        session: RomAnalysisSession,
        layout: AbilityDescriptionTableLayout,
    ): AbilityDescriptionTableOutcome {
        val extent = when (val check = checkStridedExtent(
            offset = layout.offset,
            count = layout.count,
            stride = layout.recordStride.toLong(),
            fieldOffset = layout.pointerOffset.toLong(),
            fieldSize = POINTER_BYTES,
            romSize = session.rom.size.toLong(),
            extentLimit = session.limits.maxDatasetExtentBytes,
        )) {
            is StridedExtentCheck.Valid -> check
            is StridedExtentCheck.Invalid -> return AbilityDescriptionTableOutcome.Rejected(layout, check.reason)
            is StridedExtentCheck.BudgetExceeded -> return AbilityDescriptionTableOutcome.ExtentBudgetExceeded(
                layout,
                check.observedBytes,
                check.limitBytes,
                "ability-description table extent ${check.observedBytes} exceeds deterministic budget " +
                    check.limitBytes,
            )
        }
        if (layout.count > Int.MAX_VALUE.toLong()) {
            return AbilityDescriptionTableOutcome.Rejected(
                layout,
                "ability-description count cannot be represented by indexed rows",
            )
        }
        val rows = List(layout.count.toInt()) { rowIndex ->
            val pointerLocation = extent.offset + rowIndex * layout.recordStride + layout.pointerOffset
            decodeRow(session, rowIndex, pointerLocation)
        }
        return AbilityDescriptionTableOutcome.Decoded(layout, rows)
    }

    private fun decodeRow(
        session: RomAnalysisSession,
        rowIndex: Int,
        pointerLocation: Int,
    ): AbilityDescriptionRowOutcome {
        val target = runCatching { session.rom.gbaPointer(pointerLocation) }.getOrNull()
            ?: return AbilityDescriptionRowOutcome.Malformed(rowIndex, listOf("description pointer is invalid"))
        if (target < 0 || target >= session.rom.size) {
            return AbilityDescriptionRowOutcome.Malformed(rowIndex, listOf("description pointer is outside the ROM"))
        }
        val decoded = runCatching {
            PokemonTextCodec.gbaEnglish.decodeDetailed(
                session.rom.slice(target, minOf(MAX_DESCRIPTION_BYTES, session.rom.size - target)),
            )
        }.getOrNull() ?: return AbilityDescriptionRowOutcome.Malformed(
            rowIndex,
            listOf("description bytes could not be decoded"),
        )
        if (!decoded.terminated) {
            return AbilityDescriptionRowOutcome.Malformed(
                rowIndex,
                listOf("description is unterminated"),
            )
        }
        val normalized = decoded.text.replace(WHITESPACE, " ").trim()
        if (normalized.isBlank() || normalized == "-") {
            return AbilityDescriptionRowOutcome.MissingProse(rowIndex, normalized)
        }
        if (decoded.validRatio < MINIMUM_VALID_BYTE_RATIO) {
            return AbilityDescriptionRowOutcome.Malformed(
                rowIndex,
                listOf("description contains too many undecodable bytes"),
            )
        }
        val words = normalized.split(WHITESPACE).count { word -> word.any(Char::isLetter) }
        return if (normalized.length >= MINIMUM_DESCRIPTION_LENGTH && words >= MINIMUM_WORDS) {
            AbilityDescriptionRowOutcome.Decoded(rowIndex, normalized)
        } else {
            AbilityDescriptionRowOutcome.Malformed(rowIndex, listOf("description does not contain natural prose"))
        }
    }

    private companion object {
        const val POINTER_BYTES = 4L
        const val MAX_DESCRIPTION_BYTES = 192
        const val MINIMUM_VALID_BYTE_RATIO = 0.85
        const val MINIMUM_DESCRIPTION_LENGTH = 8
        const val MINIMUM_WORDS = 2
        val WHITESPACE = Regex("\\s+")
    }
}
