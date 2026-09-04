package com.enrpau.dualscreendex.parser.dataset.descriptions

import com.enrpau.dualscreendex.parser.analysis.ExtentCheck
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

fun interface DescriptionTableDecoder {
    fun decode(
        session: RomAnalysisSession,
        layout: DescriptionTableLayout,
    ): DescriptionTableOutcome
}

/** The sole byte-level interpreter for ordinary and dynamic Gen III description records. */
class DescriptionCodec(
    private val textCodec: PokemonTextCodec = PokemonTextCodec.gbaEnglish,
) : DescriptionTableDecoder {
    override fun decode(
        session: RomAnalysisSession,
        layout: DescriptionTableLayout,
    ): DescriptionTableOutcome {
        session.cancellation.throwIfCancellationRequested()
        validateShape(layout)?.let { reason ->
            return DescriptionTableOutcome.Rejected(layout, reason)
        }
        val extent = when (
            val checked = session.limits.checkTableExtent(
                offset = layout.offset,
                count = layout.count,
                recordSize = layout.recordSize.toLong(),
                romSize = session.rom.size.toLong(),
            )
        ) {
            is ExtentCheck.Valid -> checked.extent
            is ExtentCheck.Invalid -> return DescriptionTableOutcome.Rejected(layout, checked.reason)
            is ExtentCheck.BudgetExceeded -> return DescriptionTableOutcome.ExtentBudgetExceeded(
                layout = layout,
                observedBytes = checked.observedBytes,
                limitBytes = checked.limitBytes,
                reason = "description table extent ${checked.observedBytes} exceeds deterministic budget " +
                    checked.limitBytes,
            )
        }

        val rowCount = layout.count.toInt()
        val tableOffset = extent.offset
        val rawPointers = buildList {
            repeat(rowCount) { rowIndex ->
                session.cancellation.throwIfCancellationRequested()
                val record = tableOffset + rowIndex * layout.recordSize
                layout.pointerOffsets.forEach { fieldOffset ->
                    session.rom.gbaPointer(record + fieldOffset)?.let(::add)
                }
            }
        }.distinct().sorted()
        val directPages = rawPointers.mapNotNull { pointer ->
            val nextPointer = rawPointers.firstOrNull { it > pointer }
            val maximumLength = nextPointer
                ?.let { minOf(MAX_DESCRIPTION_BYTES, it - pointer) }
                ?: MAX_DESCRIPTION_BYTES
            decodeDirectPage(session, pointer, maximumLength)?.let { pointer to it }
        }.toMap()
        val rows = List(rowCount) { rowIndex ->
            decodeRow(
                session = session,
                layout = layout,
                tableOffset = tableOffset,
                rowIndex = rowIndex,
                directPages = directPages,
                referencedBoundaries = rawPointers,
            )
        }
        return DescriptionTableOutcome.Decoded(layout, rows)
    }

    private fun validateShape(layout: DescriptionTableLayout): String? {
        if (layout.pointerOffsets.size != layout.pointerOffsets.distinct().size) {
            return "duplicate description pointer fields are not supported"
        }
        val canonical = when (layout.recordSize) {
            32 -> setOf(listOf(16))
            36 -> setOf(listOf(16), listOf(16, 20))
            else -> return "unsupported Gen III description record size ${layout.recordSize}"
        }
        if (layout.pointerOffsets !in canonical) {
            return "unsupported description pointer fields ${layout.pointerOffsets} for " +
                "${layout.recordSize}-byte records"
        }
        if (layout.pointerOffsets.any { it < 0 || it.toLong() + GBA_POINTER_BYTES > layout.recordSize }) {
            return "description pointer field falls outside its record"
        }
        return null
    }

    private fun decodeRow(
        session: RomAnalysisSession,
        layout: DescriptionTableLayout,
        tableOffset: Int,
        rowIndex: Int,
        directPages: Map<Int, String>,
        referencedBoundaries: List<Int>,
    ): DescriptionRowOutcome {
        session.cancellation.throwIfCancellationRequested()
        val rom = session.rom
        val record = tableOffset + rowIndex * layout.recordSize
        val bytes = rom.slice(record, layout.recordSize)
        if (bytes.all { it == 0.toByte() } || bytes.all { it == 0xFF.toByte() }) {
            return DescriptionRowOutcome.StructuralEmpty(rowIndex)
        }

        val reasons = mutableListOf<String>()
        val category = decodeInlineCategory(session, record)
            ?: "".also { reasons += "category is not terminated readable text" }
        val height = rom.u16le(record + HEIGHT_OFFSET)
        val weight = rom.u16le(record + WEIGHT_OFFSET)
        if (height !in 0..MAX_HEIGHT) reasons += "height $height is outside the structural range"
        if (weight !in 0..MAX_WEIGHT) reasons += "weight $weight is outside the structural range"

        val pageInterpretations = layout.pointerOffsets.mapIndexed { pageIndex, fieldOffset ->
            val pointer = rom.gbaPointer(record + fieldOffset)
            val direct = pointer?.let(directPages::get)?.let { text ->
                DecodedDescriptionPage(
                    text = text,
                    provenance = DescriptionRecoveryProvenance.Direct(requireNotNull(pointer)),
                )
            }
            val recovery = if (pointer != null && direct == null) {
                recoverOffByOne(session, pointer, referencedBoundaries)
            } else {
                null
            }
            PageInterpretation(pageIndex, pointer, direct, recovery)
        }
        val distinctRecoveries = pageInterpretations
            .mapNotNull { it.recovery }
            .distinctBy { page ->
                val provenance = page.provenance as
                    DescriptionRecoveryProvenance.OffByOneWithinNextReferencedBoundary
                provenance.recoveredPointer to page.text
            }
        val acceptedRecovery = distinctRecoveries.singleOrNull()
        if (distinctRecoveries.size > 1) {
            reasons += "multiple bounded recovery alternatives exist for this row"
        }
        val pages = pageInterpretations.mapNotNull { interpretation ->
            when {
                interpretation.pointer == null -> {
                    reasons += "page ${interpretation.pageIndex + 1} has no supported ROM pointer"
                    null
                }
                interpretation.direct != null -> interpretation.direct
                acceptedRecovery != null && interpretation.recovery == acceptedRecovery -> acceptedRecovery
                else -> {
                    reasons += "page ${interpretation.pageIndex + 1} is not terminated readable text"
                    null
                }
            }
        }

        // Page parity is strict: a two-page row is usable only when both pages use the same codec
        // contract. A readable first page must never hide a malformed second page.
        if (pages.size != layout.pointerOffsets.size) {
            if (reasons.none { it.startsWith("page ") }) reasons += "not every description page decoded"
        }
        return if (reasons.isEmpty()) {
            DescriptionRowOutcome.Decoded(rowIndex, category, height, weight, pages)
        } else {
            DescriptionRowOutcome.Malformed(rowIndex, reasons.toList())
        }
    }

    private fun decodeInlineCategory(session: RomAnalysisSession, offset: Int): String? =
        decodeTerminated(session, offset, CATEGORY_BYTES, MIN_CATEGORY_VALID_RATIO)

    private fun decodeDirectPage(session: RomAnalysisSession, offset: Int, maximumLength: Int): String? =
        decodeTerminated(session, offset, maximumLength, MIN_PAGE_VALID_RATIO)

    private fun recoverOffByOne(
        session: RomAnalysisSession,
        pointer: Int,
        directBoundaries: List<Int>,
    ): DecodedDescriptionPage? {
        if (session.rom.u8(pointer) != textCodec.terminator) return null
        val nextBoundary = directBoundaries.firstOrNull { it > pointer } ?: return null
        val recoveredPointer = pointer + 1
        if (recoveredPointer !in 0 until nextBoundary) return null
        val text = decodeTerminated(
            session = session,
            offset = recoveredPointer,
            maximumLength = minOf(MAX_DESCRIPTION_BYTES, nextBoundary - recoveredPointer),
            minimumValidRatio = MIN_RECOVERY_VALID_RATIO,
        )?.takeIf(::looksNaturalForRecovery) ?: return null
        return DecodedDescriptionPage(
            text = text,
            provenance = DescriptionRecoveryProvenance.OffByOneWithinNextReferencedBoundary(
                originalPointer = pointer,
                recoveredPointer = recoveredPointer,
                nextReferencedBoundary = nextBoundary,
            ),
        )
    }

    private data class PageInterpretation(
        val pageIndex: Int,
        val pointer: Int?,
        val direct: DecodedDescriptionPage?,
        val recovery: DecodedDescriptionPage?,
    )

    private fun decodeTerminated(
        session: RomAnalysisSession,
        offset: Int,
        maximumLength: Int,
        minimumValidRatio: Double,
    ): String? {
        val rom = session.rom
        if (offset !in 0 until rom.size || maximumLength <= 0) return null
        val decoded = textCodec.decodeDetailed(rom, offset, maximumLength, session.cancellation)
        return decoded.text.takeIf {
            decoded.terminated && decoded.validRatio >= minimumValidRatio && it.isNotBlank()
        }
    }

    private fun looksNaturalForRecovery(text: String): Boolean {
        val letters = text.count(Char::isLetter)
        return text.length >= MIN_RECOVERY_TEXT_LENGTH && letters * 2 >= text.length
    }

    private companion object {
        const val GBA_POINTER_BYTES = 4L
        const val CATEGORY_BYTES = 12
        const val HEIGHT_OFFSET = 12
        const val WEIGHT_OFFSET = 14
        const val MAX_HEIGHT = 10_000
        const val MAX_WEIGHT = 100_000
        const val MAX_DESCRIPTION_BYTES = 512
        const val MIN_CATEGORY_VALID_RATIO = 0.70
        const val MIN_PAGE_VALID_RATIO = 0.65
        const val MIN_RECOVERY_VALID_RATIO = 0.85
        const val MIN_RECOVERY_TEXT_LENGTH = 8
    }
}
