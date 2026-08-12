package com.enrpau.dualscreendex.parser.family

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.GbaCompiledReferenceIndex
import com.enrpau.dualscreendex.parser.model.Gen3LearnsetTableLayout
import com.enrpau.dualscreendex.parser.model.ParserProbe
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.TableLayout
import java.util.Collections

enum class FamilyProbePhase {
    IDENTITY_ROOTS,
    CORE_DATASETS,
    SEMANTIC_DOMAIN,
    DEPENDENT_DATASETS,
    CAPABILITY_AGGREGATION,
}

/** Immutable handoff between dependency-ordered family probe phases. */
class FamilyProbeState private constructor(
    capabilities: Map<RomCapability, CapabilityEvidence>,
    internal val identityRoots: IdentityRootsPhaseResult?,
    internal val coreDatasets: CoreDatasetsPhaseResult?,
    internal val semanticDomain: SemanticDomainPhaseResult?,
    internal val dependentDatasets: DependentDatasetsPhaseResult?,
    val probe: ParserProbe?,
) {
    val capabilities: Map<RomCapability, CapabilityEvidence> =
        Collections.unmodifiableMap(LinkedHashMap(capabilities.mapValues { (_, value) -> value.immutableCopy() }))

    fun withCapability(value: CapabilityEvidence): FamilyProbeState = FamilyProbeState(
        capabilities + (value.capability to value),
        identityRoots,
        coreDatasets,
        semanticDomain,
        dependentDatasets,
        probe,
    )

    internal fun withIdentityRoots(value: IdentityRootsPhaseResult): FamilyProbeState = FamilyProbeState(
        capabilities,
        value,
        coreDatasets,
        semanticDomain,
        dependentDatasets,
        probe,
    )

    internal fun withCoreDatasets(value: CoreDatasetsPhaseResult): FamilyProbeState = FamilyProbeState(
        capabilities,
        identityRoots,
        value,
        semanticDomain,
        dependentDatasets,
        probe,
    )

    internal fun withSemanticDomain(value: SemanticDomainPhaseResult): FamilyProbeState = FamilyProbeState(
        capabilities,
        identityRoots,
        coreDatasets,
        value,
        dependentDatasets,
        probe,
    )

    internal fun withDependentDatasets(value: DependentDatasetsPhaseResult): FamilyProbeState = FamilyProbeState(
        capabilities,
        identityRoots,
        coreDatasets,
        semanticDomain,
        value,
        probe,
    )

    fun withProbe(value: ParserProbe): FamilyProbeState = FamilyProbeState(
        capabilities,
        identityRoots,
        coreDatasets,
        semanticDomain,
        dependentDatasets,
        value.immutableCopy(),
    )

    companion object {
        fun empty(): FamilyProbeState = FamilyProbeState(emptyMap(), null, null, null, null, null)
    }
}

private fun CapabilityEvidence.immutableCopy(): CapabilityEvidence = copy(
    reasons = Collections.unmodifiableList(reasons.toList()),
)

internal fun ParserProbe.immutableCopy(): ParserProbe = copy(
    scoreEvidence = Collections.unmodifiableList(scoreEvidence.toList()),
    capabilities = Collections.unmodifiableList(capabilities.map(CapabilityEvidence::immutableCopy)),
    diagnostics = Collections.unmodifiableList(diagnostics.toList()),
    resolvedLayout = resolvedLayout?.immutableCopy(),
)

private fun ResolvedRomLayout.immutableCopy(): ResolvedRomLayout = copy(
    tables = tables.immutableCopy(),
    compiledGbaReferences = compiledGbaReferences?.immutableCopy(),
    learnsetTables = Collections.unmodifiableList(learnsetTables.map(Gen3LearnsetTableLayout::immutableCopy)),
    resolvedDatasets = resolvedDatasets.immutableSnapshot(),
)

private fun Gen3LearnsetTableLayout.immutableCopy(): Gen3LearnsetTableLayout = copy(
    table = table.immutableCopy(),
)

private fun GbaCompiledReferenceIndex.immutableCopy(): GbaCompiledReferenceIndex = copy(
    counts = Collections.unmodifiableMap(LinkedHashMap(counts)),
)

private fun ProfileTables.immutableCopy(): ProfileTables = copy(
    speciesNames = speciesNames?.immutableCopy(),
    baseStats = baseStats?.immutableCopy(),
    moveNames = moveNames?.immutableCopy(),
    moveData = moveData?.immutableCopy(),
    typeChart = typeChart?.immutableCopy(),
    evolutions = evolutions?.immutableCopy(),
    learnsets = learnsets?.immutableCopy(),
    sprites = sprites?.immutableCopy(),
    descriptions = descriptions?.immutableCopy(),
    abilities = abilities?.immutableCopy(),
)

private fun TableLayout.immutableCopy(): TableLayout = copy(
    banks = Collections.unmodifiableList(banks.toList()),
    pointerOffsets = Collections.unmodifiableList(pointerOffsets.toList()),
    bankRemap = Collections.unmodifiableMap(LinkedHashMap(bankRemap)),
)

fun interface FamilyProbePhaseStrategy {
    fun execute(
        session: RomAnalysisSession,
        definition: EngineFamilyDefinition,
        state: FamilyProbeState,
    ): FamilyProbeState
}

data class FamilyProbeStrategies(
    val identityRoots: FamilyProbePhaseStrategy,
    val coreDatasets: FamilyProbePhaseStrategy,
    val semanticDomain: FamilyProbePhaseStrategy,
    val dependentDatasets: FamilyProbePhaseStrategy,
    val capabilityAggregation: FamilyProbePhaseStrategy,
)

/** Executes every family through the same explicit dependency order exactly once. */
class FamilyProbeCoordinator(
    private val strategies: FamilyProbeStrategies = productionStrategies(),
) {
    fun probe(session: RomAnalysisSession, definition: EngineFamilyDefinition): ParserProbe {
        var state = FamilyProbeState.empty()
        state = strategies.identityRoots.execute(session, definition, state)
        (state.identityRoots as? IdentityRootsPhaseResult.Rejected)?.let { rejected ->
            return rejected.probe
        }
        state = strategies.coreDatasets.execute(session, definition, state)
        state = strategies.semanticDomain.execute(session, definition, state)
        state = strategies.dependentDatasets.execute(session, definition, state)
        state = strategies.capabilityAggregation.execute(session, definition, state)
        return requireNotNull(state.probe) {
            "${FamilyProbePhase.CAPABILITY_AGGREGATION} must publish a parser probe for ${definition.family}"
        }
    }

    fun probeAll(session: RomAnalysisSession): List<ParserProbe> =
        EngineFamilyDefinitions.all.map { definition -> probe(session, definition) }

    companion object {
        private fun productionStrategies() = FamilyProbeStrategies(
            identityRoots = IdentityRootsStrategy(),
            coreDatasets = CoreDatasetsStrategy(),
            semanticDomain = SemanticDomainStrategy(),
            dependentDatasets = DependentDatasetsStrategy(),
            capabilityAggregation = CapabilityAggregationStrategy(),
        )
    }
}
