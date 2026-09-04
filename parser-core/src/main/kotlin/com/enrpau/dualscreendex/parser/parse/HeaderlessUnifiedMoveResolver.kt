package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ExtentCheck
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsAbi
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsCodec
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsRowOutcome
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsTableLayout
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsTableOutcome
import com.enrpau.dualscreendex.parser.dataset.moves.ResolvedMoveDetailsLayout
import com.enrpau.dualscreendex.parser.io.RomBoundsException
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

/**
 * Resolves a headerless expansion-derived `MoveInfo` table from compiled-reference targets.
 * Complete relationship data supplies the ordinary dense move domain; table bytes validate a
 * compiled-nominated root but never nominate a raw ROM offset.
 */
internal object HeaderlessUnifiedMoveResolver {
    private const val RECORD_SIZE = 48
    private const val NAME_POINTER_OFFSET = 0
    private const val DESCRIPTION_POINTER_OFFSET = 4
    private const val MAX_NAME_BYTES = 64
    private const val MAX_DESCRIPTION_BYTES = 256

    data class Resolution(
        val moveCount: Int,
        val tables: ProfileTables,
        val moveNamesEvidence: ValidationEvidence,
        val moveDataEvidence: ValidationEvidence,
        val resolvedMoveDetails: ResolvedMoveDetailsLayout,
    )

    fun resolve(
        session: RomAnalysisSession,
        ordinaryMoveCount: Int,
        codec: PokemonTextCodec,
    ): Resolution? {
        if (ordinaryMoveCount !in 2..4096) return null
        val index = session.gbaReferenceIndex ?: return null
        if (index.overflowed) return null
        val plausibleRoots = index.targets.keys.filter { root ->
            plausibleSample(session, root, ordinaryMoveCount, codec)
        }
        val candidates = plausibleRoots.asSequence()
            .mapNotNull { root ->
                val indexed = index.target(root)
                val references = if (indexed?.siteEvidenceAvailable == true) {
                    indexed
                } else {
                    session.nominatedGbaReferenceSites(root)
                }
                if (references?.siteEvidenceAvailable != true || references.instructionSites.isEmpty()) {
                    null
                } else {
                    resolveRoot(session, root, ordinaryMoveCount, codec)
                }
            }
            .toList()
        return candidates.singleOrNull()
    }

    private fun resolveRoot(
        session: RomAnalysisSession,
        root: Int,
        moveCount: Int,
        codec: PokemonTextCodec,
    ): Resolution? {
        when (
            session.limits.checkTableExtent(
                offset = root.toLong(),
                count = moveCount.toLong(),
                recordSize = RECORD_SIZE.toLong(),
                romSize = session.rom.size.toLong(),
            )
        ) {
            is ExtentCheck.Valid -> Unit
            is ExtentCheck.Invalid, is ExtentCheck.BudgetExceeded -> return null
        }
        for (moveId in 0 until moveCount) {
            if (!plausibleRow(session, root + moveId * RECORD_SIZE, moveId, codec)) return null
        }
        val typedTable = MoveDetailsTableLayout(
            offset = root.toLong(),
            count = moveCount.toLong(),
            abi = MoveDetailsAbi.UNIFIED_MOVE_INFO_48,
        )
        val decoded = MoveDetailsCodec().decode(session, typedTable) as? MoveDetailsTableOutcome.Decoded
            ?: return null
        if (decoded.rows.any { it !is MoveDetailsRowOutcome.Decoded }) return null
        val resolved = ResolvedMoveDetailsLayout(typedTable, decoded.rows)
        val moveNames = TableLayout(
            offset = root + NAME_POINTER_OFFSET,
            count = moveCount,
            recordSize = 4,
            stride = RECORD_SIZE,
            valuesArePointers = true,
        )
        val moveData = TableLayout(
            offset = root,
            count = moveCount,
            recordSize = RECORD_SIZE,
            stride = RECORD_SIZE,
            format = TableRecordFormat.UNIFIED_MOVE_INFO_48,
        )
        return Resolution(
            moveCount = moveCount,
            tables = ProfileTables(moveNames = moveNames, moveData = moveData),
            moveNamesEvidence = ValidationEvidence(
                compatible = true,
                validRecords = moveCount,
                totalRecords = moveCount,
                confidence = 1.0,
                reasons = listOf(
                    "decoded every ordinary move name pointer from one compiled-referenced unified table",
                ),
                offset = root,
                recordSize = 4,
                coveredRecords = moveCount,
                expectedRecords = moveCount,
            ),
            moveDataEvidence = ValidationEvidence(
                compatible = true,
                validRecords = moveCount,
                totalRecords = moveCount,
                confidence = 1.0,
                reasons = listOf(
                    "decoded every ordinary move through the typed 48-byte unified MoveInfo ABI",
                ),
                offset = root,
                recordSize = RECORD_SIZE,
                coveredRecords = moveCount,
                expectedRecords = moveCount,
                format = TableRecordFormat.UNIFIED_MOVE_INFO_48,
            ),
            resolvedMoveDetails = resolved,
        )
    }

    private fun plausibleSample(
        session: RomAnalysisSession,
        root: Int,
        moveCount: Int,
        codec: PokemonTextCodec,
    ): Boolean {
        val last = moveCount - 1
        val sample = linkedSetOf(0, 1, last)
        return sample.all { moveId ->
            val record = root.toLong() + moveId.toLong() * RECORD_SIZE
            record >= 0L &&
                record <= session.rom.size.toLong() - RECORD_SIZE.toLong() &&
                plausibleRow(session, record.toInt(), moveId, codec)
        }
    }

    private fun plausibleRow(
        session: RomAnalysisSession,
        record: Int,
        moveId: Int,
        codec: PokemonTextCodec,
    ): Boolean = try {
        val name = session.rom.gbaPointer(record + NAME_POINTER_OFFSET) ?: return false
        val description = session.rom.gbaPointer(record + DESCRIPTION_POINTER_OFFSET) ?: return false
        val nameIsPlausible = plausibleText(
            session = session,
            offset = name,
            maximumBytes = MAX_NAME_BYTES,
            allowEmpty = false,
            codec = codec,
        )
        if (!nameIsPlausible) return false
        val descriptionIsPlausible = plausibleText(
            session = session,
            offset = description,
            maximumBytes = MAX_DESCRIPTION_BYTES,
            allowEmpty = moveId == 0,
            codec = codec,
        )
        if (!descriptionIsPlausible) return false
        val packedMove = session.rom.u16le(record + 10)
        val packedAccuracy = session.rom.u16le(record + 12)
        val category = (packedMove ushr 5) and 0x3
        val accuracy = packedAccuracy and 0x7F
        category in 0..2 && accuracy in 0..100 && session.rom.u8(record + 14) in 0..64
    } catch (_: RomBoundsException) {
        false
    }

    private fun plausibleText(
        session: RomAnalysisSession,
        offset: Int,
        maximumBytes: Int,
        allowEmpty: Boolean,
        codec: PokemonTextCodec,
    ): Boolean {
        val width = minOf(maximumBytes, session.rom.size - offset)
        if (width <= 0) return false
        val decoded = codec.decodeDetailed(session.rom.slice(offset, width))
        if (!decoded.terminated) return false
        if (decoded.contentBytes == 0) return allowEmpty
        return decoded.text.isNotBlank()
    }
}
