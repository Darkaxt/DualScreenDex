package com.enrpau.dualscreendex.parser.family

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.ParserProbe
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.ResolvedDatasetLayouts
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.ScoreEvidence
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.parse.capabilityEvidence
import com.enrpau.dualscreendex.parser.parse.speciesCatalogEvidence

/** Aggregates independent phase evidence into the stable public probe and resolved layout. */
internal class CapabilityAggregationStrategy : FamilyProbePhaseStrategy {
    override fun execute(
        session: RomAnalysisSession,
        definition: EngineFamilyDefinition,
        state: FamilyProbeState,
    ): FamilyProbeState {
        val identity = requireNotNull(state.identityRoots) as IdentityRootsPhaseResult.Resolved
        val dependent = requireNotNull(state.dependentDatasets) as DependentDatasetsPhaseResult.Resolved
        val semantic = dependent.semanticDomain
        val core = semantic.coreDatasets
        val tables = core.candidateTables
        val score = identity.scoreEvidence.toMutableList()

        score += ScoreEvidence(
            "species names", if (core.speciesNames.compatible) 15 else 0, 15, core.speciesNames.summary(),
        )
        score += ScoreEvidence(
            "base stats", if (core.baseStats.compatible) 15 else 0, 15, core.baseStats.summary(),
        )
        val movePoints = when {
            core.moveNames.compatible && core.moveData.compatible -> 15
            core.moveNames.compatible || core.moveData.compatible -> 7
            else -> 0
        }
        score += ScoreEvidence(
            "moves",
            movePoints,
            15,
            "names=${core.moveNames.compatible}, data=${core.moveData.compatible}",
        )
        val crossPoints = (if (core.speciesNames.compatible && core.baseStats.compatible) 10 else 0) +
            (if (core.moveNames.compatible && core.moveData.compatible) 5 else 0)
        score += ScoreEvidence(
            "cross-table integrity",
            crossPoints,
            15,
            "species=${core.speciesNames.compatible && core.baseStats.compatible}, " +
                "moves=${core.moveNames.compatible && core.moveData.compatible}",
        )
        score += ScoreEvidence(
            "sprites", if (dependent.sprites.compatible) 10 else 0, 10, dependent.sprites.summary(),
        )

        val capabilities = buildCapabilities(definition, identity, core, semantic, dependent)
        val anchors = listOf(
            identity.identityMatched,
            tables.speciesNames != null,
            core.speciesNames.compatible,
            core.baseStats.compatible,
            core.moveNames.compatible && core.moveData.compatible,
        ).count { it }
        val total = if (identity.exactProfile != null) 100 else score.sumOf { it.points }
        val expansion = identity.expansion
        val resolvedTables = ProfileTables(
            speciesNames = resolvedLayout(core.speciesNamesLayout, core.speciesNames),
            baseStats = resolvedLayout(core.baseStatsLayout, core.baseStats),
            moveNames = resolvedLayout(core.moveNamesLayout, core.moveNames),
            moveData = resolvedLayout(core.moveDataLayout, core.moveData),
            typeChart = semantic.typeChartLayout,
            evolutions = resolvedLayout(tables.evolutions, dependent.evolutions),
            learnsets = resolvedLayout(tables.learnsets, dependent.learnsets),
            sprites = resolvedLayout(tables.sprites, dependent.sprites),
            descriptions = semantic.descriptionsLayout,
            abilities = semantic.abilitiesLayout,
        )
        return state.withProbe(
            ParserProbe(
                family = definition.family,
                score = total,
                hardGatePassed = true,
                anchors = anchors,
                scoreEvidence = score,
                capabilities = capabilities,
                profileName = identity.exactProfile?.identity?.name ?: identity.baseProfile?.name,
                exactProfile = identity.exactProfile != null,
                diagnostics = buildDiagnostics(definition, identity, core),
                resolvedLayout = ResolvedRomLayout(
                    family = definition.family,
                    generation = definition.formatGeneration,
                    platform = session.header.platform,
                    speciesCount = core.speciesCount,
                    moveCount = core.moveCount,
                    tables = resolvedTables,
                    pokeemeraldExpansion = expansion?.metadata,
                    compiledGbaReferences = identity.compiledGbaReferences,
                    learnsetTables = dependent.learnsetTables,
                    learnsetSelector = dependent.learnsetSelector,
                    resolvedDatasets = ResolvedDatasetLayouts(
                        typeChart = semantic.resolvedTypeChart,
                        descriptions = semantic.resolvedDescriptions,
                        evolutions = dependent.resolvedEvolutions,
                        learnsets = dependent.resolvedLearnsets,
                        moveDetails = core.resolvedMoveDetails,
                        abilityNames = semantic.resolvedAbilityNames,
                    ),
                ),
            ),
        )
    }

    private fun buildCapabilities(
        definition: EngineFamilyDefinition,
        identity: IdentityRootsPhaseResult.Resolved,
        core: CoreDatasetsPhaseResult.Resolved,
        semantic: SemanticDomainPhaseResult.Resolved,
        dependent: DependentDatasetsPhaseResult.Resolved,
    ): List<CapabilityEvidence> {
        val catalog = speciesCatalogEvidence(core.speciesNames, core.baseStats)
        val discovered = listOf(
            capabilityEvidence(RomCapability.SPECIES_CATALOG, catalog),
            capabilityEvidence(RomCapability.SPECIES_NAMES, core.speciesNames),
            capabilityEvidence(RomCapability.SPECIES_TYPES, core.baseStats),
            capabilityEvidence(RomCapability.TYPE_CHART, semantic.typeChart),
            capabilityEvidence(RomCapability.BASE_STATS, core.baseStats),
            capabilityEvidence(RomCapability.SPRITES, dependent.sprites),
            capabilityEvidence(RomCapability.POKEDEX_DESCRIPTIONS, semantic.descriptions),
            capabilityEvidence(RomCapability.EVOLUTIONS, dependent.evolutions),
            capabilityEvidence(RomCapability.MOVE_CATALOG, core.moveNames),
            moveDetailsCapability(definition, identity, core),
            capabilityEvidence(RomCapability.LEARNSETS, dependent.learnsets),
            capabilityEvidence(
                RomCapability.ABILITIES,
                semantic.abilities,
                if (semantic.abilities.compatible) CapabilityStatus.AVAILABLE else CapabilityStatus.NOT_FOUND,
            ),
        )
        return applyCapabilityApplicability(definition, discovered)
    }

    private fun moveDetailsCapability(
        definition: EngineFamilyDefinition,
        identity: IdentityRootsPhaseResult.Resolved,
        core: CoreDatasetsPhaseResult.Resolved,
    ): CapabilityEvidence {
        if (definition.formatGeneration != 3 || identity.expansion != null) {
            return capabilityEvidence(RomCapability.MOVE_DETAILS, core.moveData)
        }
        if (!core.moveData.compatible) {
            return capabilityEvidence(RomCapability.MOVE_DETAILS, core.moveData)
        }
        val typed = core.resolvedMoveDetails
        if (typed == null) {
            return CapabilityEvidence(
                capability = RomCapability.MOVE_DETAILS,
                compatible = false,
                confidence = 0.0,
                offset = core.moveData.offset,
                count = core.moveData.totalRecords.takeIf { it > 0 },
                recordSize = core.moveData.recordSize,
                reasons = listOf(
                    "selected move-details typed resolution rejected: " +
                        (core.moveDetailsTypedRejectionReason ?: "no typed selected outcome was available"),
                ),
                status = CapabilityStatus.NOT_FOUND,
            )
        }
        return CapabilityEvidence(
            capability = RomCapability.MOVE_DETAILS,
            compatible = true,
            confidence = core.moveData.confidence,
            offset = typed.table.offset.toInt(),
            count = typed.table.count.toInt(),
            recordSize = typed.table.abi.recordSize,
            reasons = core.moveData.reasons + "validated the selected move-details layout through the typed codec",
            status = CapabilityStatus.AVAILABLE,
            validRecords = core.moveData.validRecords,
            totalRecords = typed.table.count.toInt(),
        )
    }

    private fun buildDiagnostics(
        definition: EngineFamilyDefinition,
        identity: IdentityRootsPhaseResult.Resolved,
        core: CoreDatasetsPhaseResult.Resolved,
    ): List<String> = buildList {
        val profile = identity.baseProfile
        val exact = identity.exactProfile
        val tables = core.candidateTables
        if (profile == null) add("no official family profile matched the header")
        if (exact == null && profile != null) add("using ${profile.name} as structural ancestor")
        if (definition.formatGeneration == 3 && tables.speciesNames?.offset != profile?.tables?.speciesNames?.offset) {
            add("resolved relocated GBA table pointers")
        }
        if (definition.formatGeneration == 2 && core.speciesNamesLayout?.offset != profile?.tables?.speciesNames?.offset) {
            add("resolved relocated Gen 2 species-name table")
        }
        if (definition.formatGeneration == 2 && core.baseStatsLayout?.offset != profile?.tables?.baseStats?.offset) {
            add("resolved relocated Gen 2 base-stat table")
        }
        if (definition.formatGeneration == 2 && core.moveNamesLayout?.offset != profile?.tables?.moveNames?.offset) {
            add("resolved relocated Gen 2 move-name table")
        }
        if (core.baseStatsLayout != null && core.baseStatsLayout.recordSize != profile?.tables?.baseStats?.recordSize) {
            add("inferred base-stat record size ${core.baseStatsLayout.recordSize}")
        }
        identity.expansion?.let { expansion ->
            add(
                "resolved pokeemerald-expansion ${expansion.metadata.versionMajor}." +
                    "${expansion.metadata.versionMinor}.${expansion.metadata.versionPatch}; " +
                    "first species=${expansion.firstRegisters.speciesName}/Dex " +
                    "${expansion.firstRegisters.speciesNationalDex}, " +
                    "move=${expansion.firstRegisters.moveName}, ability=${expansion.firstRegisters.abilityName}",
            )
        }
    }
}

internal fun applyCapabilityApplicability(
    definition: EngineFamilyDefinition,
    discovered: List<CapabilityEvidence>,
): List<CapabilityEvidence> {
    val byCapability = discovered.associateBy(CapabilityEvidence::capability)
    return RomCapability.entries.map { capability ->
        if (capability !in definition.applicableCapabilities) {
            CapabilityEvidence(
                capability = capability,
                compatible = false,
                confidence = 0.0,
                reasons = listOf("not applicable to ${definition.family}"),
                status = CapabilityStatus.NOT_APPLICABLE,
            )
        } else {
            byCapability[capability] ?: CapabilityEvidence(
                capability = capability,
                compatible = false,
                confidence = 0.0,
                reasons = listOf("no validated ${capability.name.lowercase()} evidence"),
                status = CapabilityStatus.NOT_FOUND,
            )
        }
    }
}

private fun ValidationEvidence.summary(): String = if (compatible) {
    "$validRecords/$totalRecords valid at ${offset?.let { "0x${it.toString(16).uppercase()}" } ?: "unknown"}"
} else {
    reasons.joinToString("; ").ifBlank { "$validRecords/$totalRecords valid" }
}
