package com.enrpau.dualscreendex.parser.family

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.Gen1DetachedSpeciesResolver
import com.enrpau.dualscreendex.parser.dataset.evolutions.EvolutionResolver
import com.enrpau.dualscreendex.parser.dataset.evolutions.EvolutionTableLayout
import com.enrpau.dualscreendex.parser.dataset.evolutions.EmbeddedEvolutionPointerResolver
import com.enrpau.dualscreendex.parser.dataset.evolutions.ResolvedEvolutionLayout
import com.enrpau.dualscreendex.parser.dataset.learnsets.LearnsetFormat
import com.enrpau.dualscreendex.parser.dataset.learnsets.LearnsetResolver
import com.enrpau.dualscreendex.parser.dataset.learnsets.LearnsetTableLayout
import com.enrpau.dualscreendex.parser.dataset.learnsets.ResolvedLearnsetSet
import com.enrpau.dualscreendex.parser.dataset.learnsets.SaveBlock1LearnsetSelectorDescriptor
import com.enrpau.dualscreendex.parser.dataset.learnsets.SaveBlock1LearnsetSelectorProof
import com.enrpau.dualscreendex.parser.dataset.learnsets.SelectedLearnsetResolution
import com.enrpau.dualscreendex.parser.dataset.learnsets.SelectedLearnsetTable
import com.enrpau.dualscreendex.parser.model.Gen3LearnsetEncoding
import com.enrpau.dualscreendex.parser.model.Gen3LearnsetSelectorEvidence
import com.enrpau.dualscreendex.parser.model.Gen3LearnsetTableLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.parse.DatasetResolvers
import com.enrpau.dualscreendex.parser.parse.PokeemeraldExpansionResolver
import com.enrpau.dualscreendex.parser.resolution.DatasetResolution
import com.enrpau.dualscreendex.parser.validate.PokemonDatasetValidators
import com.enrpau.dualscreendex.parser.validate.SpriteValidators
import java.util.Collections

internal sealed interface DependentDatasetsPhaseResult {
    class Resolved internal constructor(
        val semanticDomain: SemanticDomainPhaseResult.Resolved,
        sprites: ValidationEvidence,
        evolutions: ValidationEvidence,
        resolvedEvolutions: ResolvedEvolutionLayout? = null,
        learnsets: ValidationEvidence,
        learnsetTables: List<Gen3LearnsetTableLayout>,
        val learnsetSelector: Gen3LearnsetSelectorEvidence?,
        resolvedLearnsets: ResolvedLearnsetSet? = null,
        val resolvedLearnsetTable: TableLayout? = null,
    ) : DependentDatasetsPhaseResult {
        val sprites = sprites.dependentSnapshot()
        val evolutions = evolutions.dependentSnapshot()
        val resolvedEvolutions = resolvedEvolutions?.immutableSnapshot()
        val learnsets = learnsets.dependentSnapshot()
        val learnsetTables = Collections.unmodifiableList(learnsetTables.map { entry ->
            entry.copy(table = entry.table.dependentSnapshot())
        })
        val resolvedLearnsets = resolvedLearnsets?.immutableSnapshot()
    }
}

/** Resolves datasets that depend on identity, core counts, or semantic layouts. */
internal class DependentDatasetsStrategy : FamilyProbePhaseStrategy {
    override fun execute(
        session: RomAnalysisSession,
        definition: EngineFamilyDefinition,
        state: FamilyProbeState,
    ): FamilyProbeState {
        val identity = requireNotNull(state.identityRoots) as IdentityRootsPhaseResult.Resolved
        val semantic = requireNotNull(state.semanticDomain) as SemanticDomainPhaseResult.Resolved
        val core = semantic.coreDatasets
        val rom = session.rom
        val generation = definition.formatGeneration
        val expansion = identity.expansion
        val profile = identity.baseProfile
        val tables = core.candidateTables

        var sprites = when (generation) {
            1 -> tables.sprites?.let {
                SpriteValidators.gen1(rom, it.offset, it.count, it.recordSize, it.banks.toIntArray())
            } ?: missingEvidence("Gen 1 sprite references not resolved")
            2 -> tables.sprites?.let {
                SpriteValidators.gen2(
                    rom,
                    it.offset,
                    it.count,
                    it.bankAdjustment,
                    it.bankRemap,
                    session.cancellation,
                )
            } ?: missingEvidence("Gen 2 sprite pointer table not resolved")
            else -> when {
                expansion != null -> PokeemeraldExpansionResolver.validateSprites(rom, expansion)
                identity.headerlessUnifiedSpecies != null -> identity.headerlessUnifiedSpecies.spritesEvidence
                else -> tables.sprites?.let {
                    SpriteValidators.gen3(rom, it.offset, core.speciesCount ?: it.count, it.recordSize)
                } ?: missingEvidence("Gen 3 sprite pointer table not resolved")
            }
        }
        if (generation == 1 && tables.sprites != null) {
            sprites = Gen1DetachedSpeciesResolver.completeEvidence(
                sprites,
                Gen1DetachedSpeciesResolver.resolve(rom, tables.sprites, session.cancellation),
                "sprite record",
            )
        }

        val evolutionAndLearnset = if (generation < 3) {
            val layout = tables.evolutions ?: tables.learnsets
            layout?.let {
                PokemonDatasetValidators.gen12EvolutionsAndLearnsets(
                    rom,
                    it.offset,
                    it.count,
                    it.bank ?: 0,
                    core.inferredMoveCount ?: profile?.moveCount ?: it.count,
                    generation,
                )
            }
        } else {
            null
        }
        val embeddedEvolutions = if (generation == 3 && expansion == null) {
            identity.headerlessUnifiedSpecies?.let { unified ->
                EmbeddedEvolutionPointerResolver.resolve(
                    session = session,
                    metadata = unified.metadata,
                    speciesCount = unified.speciesCount,
                )
            }
        } else {
            null
        }
        val embeddedLearnsets = core.headerlessEmbeddedLearnsets
        val legacyEvolutions = embeddedEvolutions?.evidence ?: when {
            generation == 3 && identity.headerlessUnifiedSpecies != null ->
                missingEvidence("embedded unified-species evolution pointers not resolved")
            evolutionAndLearnset != null -> evolutionAndLearnset.evolutions
            generation == 3 && expansion != null ->
                PokeemeraldExpansionResolver.validateEvolutions(rom, expansion)
            generation == 3 -> DatasetResolvers.gen3Evolutions(
                session,
                core.speciesCount ?: profile?.internalSpeciesCount ?: 412,
                tables.evolutions,
            )
            else -> missingEvidence("combined evolution/learnset table not resolved")
        }
        val evolutionResolution = embeddedEvolutions?.let {
            ResolvedEvolutionEvidence(it.evidence, it.resolved)
        } ?: resolveEvolutions(
            session = session,
            generation = generation,
            expansion = expansion != null,
            speciesCount = core.speciesCount ?: profile?.internalSpeciesCount ?: 412,
            inherited = tables.evolutions,
            evidence = legacyEvolutions,
        )
        var learnsetTables = emptyList<Gen3LearnsetTableLayout>()
        var learnsetSelector: Gen3LearnsetSelectorEvidence? = null
        var resolvedLearnsets: ResolvedLearnsetSet? = embeddedLearnsets?.resolved
        var learnsets = embeddedLearnsets?.evidence ?: evolutionAndLearnset?.learnsets ?: if (generation == 3) {
            if (expansion != null) {
                PokeemeraldExpansionResolver.validateLearnsets(rom, expansion)
            } else {
                DatasetResolvers.gen3LearnsetResolution(
                    session,
                    core.speciesCount ?: profile?.internalSpeciesCount ?: 412,
                    core.moveCount ?: profile?.moveCount?.plus(1) ?: 355,
                    tables.learnsets,
                ).also { resolution ->
                    learnsetTables = resolution.tables
                    learnsetSelector = resolution.selector
                }.evidence
            }
        } else {
            missingEvidence("combined evolution/learnset table not resolved")
        }
        if (embeddedLearnsets == null && generation == 3 && expansion == null && learnsetTables.isNotEmpty()) {
            val typed = resolveLearnsets(
                session = session,
                moveCount = core.moveCount ?: profile?.moveCount?.plus(1) ?: 355,
                selected = learnsetTables,
                selectedPrimary = resolvedLayout(tables.learnsets, learnsets),
                selector = learnsetSelector,
            )
            resolvedLearnsets = typed.resolved
            if (resolvedLearnsets == null) {
                learnsets = learnsets.copy(
                    compatible = false,
                    reasons = learnsets.reasons + (typed.reason ?: "selected learnset layouts failed typed resolution"),
                )
            }
        }

        return state.withDependentDatasets(
            DependentDatasetsPhaseResult.Resolved(
                semanticDomain = semantic,
                sprites = sprites,
                evolutions = evolutionResolution.evidence,
                resolvedEvolutions = evolutionResolution.resolved,
                learnsets = learnsets,
                learnsetTables = learnsetTables,
                learnsetSelector = learnsetSelector,
                resolvedLearnsets = resolvedLearnsets,
                resolvedLearnsetTable = embeddedLearnsets?.table,
            ),
        )
    }

    private fun resolveLearnsets(
        session: RomAnalysisSession,
        moveCount: Int,
        selected: List<Gen3LearnsetTableLayout>,
        selectedPrimary: TableLayout?,
        selector: Gen3LearnsetSelectorEvidence?,
    ): SelectedLearnsetResolution {
        val selectedTables = selected.mapNotNull { entry ->
            entry.table.toTypedLearnset(moveCount)?.let { layout ->
                SelectedLearnsetTable(layout, entry.confidence, entry.referenceCount)
            }
        }
        if (selectedTables.size != selected.size) {
            return SelectedLearnsetResolution(null, "selected learnset ABI is not supported by the typed codec")
        }
        val selectorProof = selector?.toVerifiedProof(session, selected)
        if (selector != null && selectorProof == null) {
            return SelectedLearnsetResolution(null, "selected learnset selector failed typed provenance verification")
        }
        return LearnsetResolver().resolveSelectedGen3(
            session = session,
            moveCount = moveCount,
            selectedTables = selectedTables,
            primaryOffset = selectedPrimary?.offset?.toLong(),
            selectorProof = selectorProof,
        )
    }

    private fun Gen3LearnsetSelectorEvidence.toVerifiedProof(
        session: RomAnalysisSession,
        selected: List<Gen3LearnsetTableLayout>,
    ): SaveBlock1LearnsetSelectorProof? = SaveBlock1LearnsetSelectorProof.verify(
        session,
        SaveBlock1LearnsetSelectorDescriptor(
            saveBlock1ByteOffset,
            mask,
            zeroTableOffset.toLong(),
            nonZeroTableOffset.toLong(),
            codeOffset,
        ),
        selected.mapTo(linkedSetOf()) { it.table.offset },
    )

    private fun TableLayout.toTypedLearnset(moveCount: Int): LearnsetTableLayout? {
        if (offset < 0 || count <= 0 || recordSize != 4) return null
        val typedFormat = when (format) {
            com.enrpau.dualscreendex.parser.model.TableRecordFormat.GEN3_PACKED_U16 ->
                LearnsetFormat.PackedU16(Gen3LearnsetEncoding.packedMoveBits(moveCount))
            com.enrpau.dualscreendex.parser.model.TableRecordFormat.GEN3_LEVEL_U8_MOVE_U16 ->
                LearnsetFormat.LevelU8MoveU16
            com.enrpau.dualscreendex.parser.model.TableRecordFormat.GEN3_MOVE_U16_LEVEL_U8 ->
                LearnsetFormat.MoveU16LevelU8
            com.enrpau.dualscreendex.parser.model.TableRecordFormat.GEN3_MOVE_U16_LEVEL_U16 ->
                LearnsetFormat.MoveU16LevelU16
            else -> return null
        }
        if (elementSize != typedFormat.entrySize) return null
        return LearnsetTableLayout(offset.toLong(), count, typedFormat, stride ?: 4)
    }

    private fun resolveEvolutions(
        session: RomAnalysisSession,
        generation: Int,
        expansion: Boolean,
        speciesCount: Int,
        inherited: TableLayout?,
        evidence: ValidationEvidence,
    ): ResolvedEvolutionEvidence {
        if (generation != 3 || expansion || !evidence.compatible) {
            return ResolvedEvolutionEvidence(evidence, null)
        }
        val selected = resolvedLayout(inherited, evidence)?.toEvolutionTableLayout()
            ?: return ResolvedEvolutionEvidence(
                evidence.copy(
                    compatible = false,
                    reasons = evidence.reasons +
                        "selected evolution ABI could not be represented by the typed codec",
                ),
                null,
            )
        val resolution = EvolutionResolver().resolveGen3(
            session = session,
            expectedSpeciesCount = speciesCount,
            selectedLayout = selected,
        )
        val resolved = when (resolution) {
            is DatasetResolution.Resolved -> resolution.candidate.layout
            is DatasetResolution.Partial -> resolution.candidate.layout
            else -> null
        }
        return if (resolved != null) {
            ResolvedEvolutionEvidence(evidence, resolved)
        } else {
            ResolvedEvolutionEvidence(
                evidence.copy(
                    compatible = false,
                    reasons = evidence.reasons + "selected evolution layout failed typed resolution",
                ),
                null,
            )
        }
    }

    private fun TableLayout.toEvolutionTableLayout(): EvolutionTableLayout? {
        val element = elementSize?.takeIf { it in setOf(6, 8) } ?: return null
        if (count <= 1 || recordSize <= 0 || recordSize % element != 0) return null
        return runCatching {
            EvolutionTableLayout(
                offset = offset.toLong(),
                count = count.toLong(),
                slotsPerSpecies = recordSize / element,
                recordSize = element,
            )
        }.getOrNull()
    }

    private data class ResolvedEvolutionEvidence(
        val evidence: ValidationEvidence,
        val resolved: ResolvedEvolutionLayout?,
    )
}

private fun ValidationEvidence.dependentSnapshot(): ValidationEvidence = copy(
    reasons = Collections.unmodifiableList(reasons.toList()),
)

private fun com.enrpau.dualscreendex.parser.model.TableLayout.dependentSnapshot() = copy(
    banks = Collections.unmodifiableList(banks.toList()),
    pointerOffsets = Collections.unmodifiableList(pointerOffsets.toList()),
    bankRemap = Collections.unmodifiableMap(LinkedHashMap(bankRemap)),
)

internal fun missingEvidence(reason: String) = ValidationEvidence(false, 0, 0, 0.0, listOf(reason))
