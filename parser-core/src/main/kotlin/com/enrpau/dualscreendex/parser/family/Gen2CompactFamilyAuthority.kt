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

/** A single admission unit: canonical species, named moves, native types, and compatible text. */
internal class Gen2CompactFamilyAuthority private constructor(
    val core: Gen2CompactCoreResolution,
    val tables: ProfileTables,
    val moveNamesEvidence: ValidationEvidence,
    val moveDataEvidence: ValidationEvidence,
    val manifest: RomLanguageManifest,
) {
    companion object {
        fun resolve(session: RomAnalysisSession): Gen2CompactFamilyAuthority? {
            val core = Gen2CompactCoreResolver.resolve(session) ?: return null
            val types = CompiledTypeNameResolver.decode(session.rom, 2, core.typeNames, core.codec) ?: return null
            val moves = Gen2CompactMoveNamesResolver.resolve(session, core.codec, types.keys) ?: return null
            val tables = core.tables.copy(moveNames = moves.moveNames, moveData = moves.moveData)
            val localized = LocalizedTableLayout(speciesNames = tables.speciesNames,
                moveNames = tables.moveNames, typeNames = core.typeNames)
            val manifest = RomLanguageManifest(LanguageTag.ENGLISH, listOf(RomLanguageProjection(
                LanguageTag.ENGLISH, core.codec.id, core.codec.version, localized, emptyList(),
                LanguageResolutionStatus.RESOLVED,
            )), LanguageResolutionStatus.RESOLVED,
                diagnostics = listOf("compiled compact names and complete English type-label semantic domain"))
            return Gen2CompactFamilyAuthority(core, tables,
                evidence(moves.moveNames, "complete compiled byte-ID move-name registry"),
                evidence(moves.moveData, "complete compiled eight-byte moves and native type/category references"), manifest)
        }

        private fun evidence(table: TableLayout, reason: String) = ValidationEvidence(
            true, table.count, table.count, 1.0, listOf(reason), table.offset, table.recordSize,
            coveredRecords = table.count, expectedRecords = table.count, format = table.format,
        )
    }
}
