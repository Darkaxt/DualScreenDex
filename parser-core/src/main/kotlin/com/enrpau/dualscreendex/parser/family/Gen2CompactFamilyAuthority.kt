package com.enrpau.dualscreendex.parser.family

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.language.LocalizedTableLayout
import com.enrpau.dualscreendex.parser.language.RomLanguageManifest
import com.enrpau.dualscreendex.parser.language.RomLanguageProjection
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.parse.CompiledTypeNameResolver
import com.enrpau.dualscreendex.parser.parse.Gen2CompactCoreResolution
import com.enrpau.dualscreendex.parser.parse.Gen2CompactCoreResolver
import com.enrpau.dualscreendex.parser.parse.Gen2CompactMoveNamesResolver
import com.enrpau.dualscreendex.parser.parse.Gen2CompactRelationshipResolver
import com.enrpau.dualscreendex.parser.model.Gen2CompactCoreMetadata
import com.enrpau.dualscreendex.parser.model.TableRecordFormat

/** A single admission unit: canonical species, named moves, native types, and compatible text. */
internal class Gen2CompactFamilyAuthority private constructor(
    val core: Gen2CompactCoreResolution,
    val tables: ProfileTables,
    val moveNamesEvidence: ValidationEvidence,
    val moveDataEvidence: ValidationEvidence,
    val manifest: RomLanguageManifest,
    /** Species slots plus the compiled relationship contract, when one is proven. */
    val metadata: Gen2CompactCoreMetadata,
    val evolutionsEvidence: ValidationEvidence?,
    val learnsetsEvidence: ValidationEvidence?,
) {
    companion object {
        fun resolve(session: RomAnalysisSession): Gen2CompactFamilyAuthority? {
            val core = Gen2CompactCoreResolver.resolve(session) ?: return null
            val types = CompiledTypeNameResolver.decode(session.rom, 2, core.typeNames, core.codec) ?: return null
            val moves = Gen2CompactMoveNamesResolver.resolve(session, core.codec, types.keys) ?: return null
            val moveCount = moves.moveNames.count
            val contract = Gen2CompactRelationshipResolver.resolve(session, core, moveCount)
            val metadata = core.metadata.withRelationships(contract)
            val relationshipTable = contract?.let {
                TableLayout(it.tableOffset, it.rows, 2, variableLength = true, bank = it.bank,
                    format = TableRecordFormat.GEN2_COMPACT_RELATIONSHIPS)
            }
            val decoded = contract?.let { Gen2CompactRelationshipResolver.decode(session.rom, metadata, moveCount) }
            val tables = core.tables.copy(moveNames = moves.moveNames, moveData = moves.moveData,
                evolutions = relationshipTable, learnsets = relationshipTable)
            val localized = LocalizedTableLayout(speciesNames = tables.speciesNames,
                moveNames = tables.moveNames, typeNames = core.typeNames)
            val manifest = RomLanguageManifest(LanguageTag.ENGLISH, listOf(RomLanguageProjection(
                LanguageTag.ENGLISH, core.codec.id, core.codec.version, localized, emptyList(),
                LanguageResolutionStatus.RESOLVED,
            )), LanguageResolutionStatus.RESOLVED,
                diagnostics = listOf("compiled compact names and complete English type-label semantic domain"))
            return Gen2CompactFamilyAuthority(core, tables,
                evidence(moves.moveNames, "complete compiled byte-ID move-name registry"),
                evidence(moves.moveData, "complete compiled eight-byte moves and native type/category references"), manifest,
                metadata,
                decoded?.let { relationshipEvidence(relationshipTable!!, it.evolutions.size, metadata.slots.size,
                    "compiled compact evolution rows with in-domain species targets") },
                decoded?.let { relationshipEvidence(relationshipTable!!, it.learnsets.size, metadata.slots.size,
                    "compiled compact level/move learnsets") })
        }

        private fun relationshipEvidence(table: TableLayout, valid: Int, total: Int, reason: String) = ValidationEvidence(
            valid > 0, valid, total, if (total == 0) 0.0 else valid.toDouble() / total, listOf(reason), table.offset,
            table.recordSize, coveredRecords = valid, expectedRecords = total, format = table.format,
        )

        private fun evidence(table: TableLayout, reason: String) = ValidationEvidence(
            true, table.count, table.count, 1.0, listOf(reason), table.offset, table.recordSize,
            coveredRecords = table.count, expectedRecords = table.count, format = table.format,
        )
    }
}
