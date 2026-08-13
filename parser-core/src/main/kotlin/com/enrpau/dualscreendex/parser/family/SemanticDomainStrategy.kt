package com.enrpau.dualscreendex.parser.family

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.parse.DatasetResolvers
import com.enrpau.dualscreendex.parser.parse.Gen3PublishedPartialBaseStatsResolver
import com.enrpau.dualscreendex.parser.parse.PokeemeraldExpansionResolver
import com.enrpau.dualscreendex.parser.parse.selectAbilityNameEvidence
import com.enrpau.dualscreendex.parser.parse.compiledAbilityNameStride
import com.enrpau.dualscreendex.parser.parse.semanticAbilityNameBoundary
import com.enrpau.dualscreendex.parser.dataset.types.ResolvedTypeChartLayout
import com.enrpau.dualscreendex.parser.dataset.types.TypeChartAbi
import com.enrpau.dualscreendex.parser.dataset.types.TypeChartResolver
import com.enrpau.dualscreendex.parser.dataset.types.TypeChartTableLayout
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionResolver
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionTableLayout
import com.enrpau.dualscreendex.parser.dataset.descriptions.ResolvedDescriptionLayout
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameResolver
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameCodec
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameTableLayout
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilitySemanticDomain
import com.enrpau.dualscreendex.parser.dataset.abilities.ResolvedAbilityNameLayout
import com.enrpau.dualscreendex.parser.resolution.DatasetResolution
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.validate.Gen3BaseStatAbilitySlots
import com.enrpau.dualscreendex.parser.validate.PokemonDatasetValidators
import com.enrpau.dualscreendex.parser.validate.TableValidators
import java.util.Collections

internal sealed interface SemanticDomainPhaseResult {
    class Resolved internal constructor(
        val coreDatasets: CoreDatasetsPhaseResult.Resolved,
        descriptions: ValidationEvidence,
        descriptionsLayout: TableLayout?,
        resolvedDescriptions: ResolvedDescriptionLayout? = null,
        typeChart: ValidationEvidence,
        typeChartLayout: TableLayout?,
        abilities: ValidationEvidence,
        abilitiesLayout: TableLayout?,
        resolvedAbilityNames: ResolvedAbilityNameLayout? = null,
        resolvedTypeChart: ResolvedTypeChartLayout? = null,
    ) : SemanticDomainPhaseResult {
        val descriptions = descriptions.immutableCopy()
        val descriptionsLayout = descriptionsLayout?.immutableCopy()
        val resolvedDescriptions = resolvedDescriptions?.immutableSnapshot()
        val typeChart = typeChart.immutableCopy()
        val typeChartLayout = typeChartLayout?.immutableCopy()
        val abilities = abilities.immutableCopy()
        val abilitiesLayout = abilitiesLayout?.immutableCopy()
        val resolvedAbilityNames = resolvedAbilityNames?.immutableSnapshot()
        val resolvedTypeChart = resolvedTypeChart?.immutableSnapshot()
    }
}

private fun ValidationEvidence.immutableCopy(): ValidationEvidence = copy(
    reasons = Collections.unmodifiableList(reasons.toList()),
)

private fun TableLayout.immutableCopy(): TableLayout = copy(
    banks = Collections.unmodifiableList(banks.toList()),
    pointerOffsets = Collections.unmodifiableList(pointerOffsets.toList()),
    bankRemap = Collections.unmodifiableMap(LinkedHashMap(bankRemap)),
)

/** Resolves semantic datasets and promotes structurally recovered stats before dependent consumers. */
internal class SemanticDomainStrategy : FamilyProbePhaseStrategy {
    override fun execute(
        session: RomAnalysisSession,
        definition: EngineFamilyDefinition,
        state: FamilyProbeState,
    ): FamilyProbeState {
        val identity = requireNotNull(state.identityRoots) as IdentityRootsPhaseResult.Resolved
        val rawCore = requireNotNull(state.coreDatasets) as CoreDatasetsPhaseResult.Resolved
        val descriptionResolution = resolveDescriptions(session, definition, identity, rawCore)
        val descriptions = descriptionResolution.evidence
        val descriptionsLayout = resolvedLayout(rawCore.candidateTables.descriptions, descriptions)
        val core = promotePublishedPartialStats(
            session,
            definition,
            identity,
            rawCore,
            descriptionsLayout,
        )
        val tables = core.candidateTables
        val typeChart = resolveTypeChart(session, definition, identity, core)
        val abilityResolution = resolveAbilities(session, definition, identity, core)
        return state.withSemanticDomain(
            SemanticDomainPhaseResult.Resolved(
                coreDatasets = core,
                descriptions = descriptions,
                descriptionsLayout = descriptionsLayout,
                resolvedDescriptions = descriptionResolution.resolved,
                typeChart = typeChart.evidence,
                typeChartLayout = resolvedLayout(tables.typeChart, typeChart.evidence, variableLength = true),
                abilities = abilityResolution.evidence,
                abilitiesLayout = abilityResolution.resolved?.table?.toTableLayout()
                    ?: resolvedLayout(tables.abilities, abilityResolution.evidence),
                resolvedAbilityNames = abilityResolution.resolved,
                resolvedTypeChart = typeChart.resolved,
            ),
        )
    }

    private fun validateDescriptions(
        session: RomAnalysisSession,
        definition: EngineFamilyDefinition,
        identity: IdentityRootsPhaseResult.Resolved,
        core: CoreDatasetsPhaseResult.Resolved,
    ): ValidationEvidence {
        val rom = session.rom
        val tables = core.candidateTables
        return when (definition.formatGeneration) {
            1 -> tables.descriptions?.let {
                PokemonDatasetValidators.gen1Descriptions(
                    rom, it.offset, it.count, it.bank ?: 0, identity.codec,
                )
            } ?: missingEvidence("Gen 1 Pokédex description table not resolved")
            2 -> tables.descriptions?.let {
                PokemonDatasetValidators.gen2Descriptions(
                    rom, it.offset, it.count, it.banks.toIntArray(), codec = identity.codec,
                )
            } ?: missingEvidence("Gen 2 Pokédex description table not resolved")
            else -> identity.expansion?.let {
                PokeemeraldExpansionResolver.validateDescriptions(rom, it)
            } ?: DatasetResolvers.gen3Descriptions(
                session,
                if (identity.exactProfile != null) {
                    tables.descriptions?.count ?: 387
                } else {
                    core.speciesCount ?: identity.baseProfile?.internalSpeciesCount ?: 412
                },
                tables.descriptions,
                identity.codec,
            )
        }
    }

    private fun resolveDescriptions(
        session: RomAnalysisSession,
        definition: EngineFamilyDefinition,
        identity: IdentityRootsPhaseResult.Resolved,
        core: CoreDatasetsPhaseResult.Resolved,
    ): ResolvedDescriptionEvidence {
        val evidence = validateDescriptions(session, definition, identity, core)
        if (definition.formatGeneration != 3 || identity.expansion != null || !evidence.compatible) {
            return ResolvedDescriptionEvidence(evidence, null)
        }
        val selected = resolvedLayout(core.candidateTables.descriptions, evidence)
            ?.toDescriptionTableLayout()
            ?: return ResolvedDescriptionEvidence(
                evidence.copy(
                    compatible = false,
                    reasons = evidence.reasons +
                        "selected description ABI could not be represented by the typed codec",
                ),
                null,
            )
        val resolution = DescriptionResolver().resolve(
            session = session,
            expectedSpeciesCount = core.speciesCount ?: selected.count.toInt(),
            selectedLayout = selected,
        )
        val resolved = when (resolution) {
            is DatasetResolution.Resolved -> resolution.candidate.layout
            is DatasetResolution.Partial -> resolution.candidate.layout
            else -> null
        }
        return if (resolved != null) {
            ResolvedDescriptionEvidence(evidence, resolved)
        } else {
            ResolvedDescriptionEvidence(
                evidence.copy(
                    compatible = false,
                    reasons = evidence.reasons + "selected description layout failed typed resolution",
                ),
                null,
            )
        }
    }

    private fun TableLayout.toDescriptionTableLayout(): DescriptionTableLayout? {
        val pointers = pointerOffsets.ifEmpty {
            when (recordSize) {
                32 -> listOf(16)
                36 -> listOf(16, 20)
                else -> return null
            }
        }
        return runCatching {
            DescriptionTableLayout(offset.toLong(), count.toLong(), recordSize, pointers)
        }.getOrNull()
    }

    private data class ResolvedDescriptionEvidence(
        val evidence: ValidationEvidence,
        val resolved: ResolvedDescriptionLayout?,
    )

    private fun promotePublishedPartialStats(
        session: RomAnalysisSession,
        definition: EngineFamilyDefinition,
        identity: IdentityRootsPhaseResult.Resolved,
        core: CoreDatasetsPhaseResult.Resolved,
        descriptionsLayout: TableLayout?,
    ): CoreDatasetsPhaseResult.Resolved {
        val candidate = core.publishedPartialBaseStatsCandidate ?: return core
        val provisionalLayout = ResolvedRomLayout(
            family = definition.family,
            generation = definition.formatGeneration,
            platform = session.header.platform,
            speciesCount = core.speciesCount,
            moveCount = core.moveCount,
            tables = ProfileTables(
                speciesNames = resolvedLayout(core.speciesNamesLayout, core.speciesNames),
                baseStats = candidate.layout,
                moveNames = resolvedLayout(core.moveNamesLayout, core.moveNames),
                moveData = resolvedLayout(core.moveDataLayout, core.moveData),
                descriptions = descriptionsLayout,
            ),
            compiledGbaReferences = identity.compiledGbaReferences,
        )
        return Gen3PublishedPartialBaseStatsResolver.confirmCandidate(
            rom = session.rom,
            candidate = candidate,
            names = core.speciesNames,
            provisionalLayout = provisionalLayout,
        )?.let(core::withPromotedBaseStats) ?: core
    }

    private fun resolveTypeChart(
        session: RomAnalysisSession,
        definition: EngineFamilyDefinition,
        identity: IdentityRootsPhaseResult.Resolved,
        core: CoreDatasetsPhaseResult.Resolved,
    ): ResolvedTypeChartEvidence {
        val rom = session.rom
        val tables = core.candidateTables
        val expansion = identity.expansion
        val activeTypeLowerBound = if (definition.formatGeneration == 3) {
            core.baseStatsLayout?.let { baseStats ->
                TableValidators.inferGen3ActiveTypeCount(
                    rom,
                    baseStats,
                    core.speciesCount ?: baseStats.count,
                )
            }
        } else {
            null
        }
        val evidence = if (expansion != null) {
            val chart = requireNotNull(tables.typeChart)
            ValidationEvidence(
                compatible = true,
                validRecords = chart.count,
                totalRecords = chart.count,
                confidence = 1.0,
                reasons = listOf("validated expansion Q4.12 type-effectiveness matrix"),
                offset = chart.offset,
                recordSize = chart.recordSize,
                elementSize = chart.elementSize,
            )
        } else if (definition.formatGeneration == 3) {
            TableValidators.resolveGen3TypeChart(rom, tables.typeChart?.offset, activeTypeLowerBound)
        } else {
            tables.typeChart?.let {
                TableValidators.typeChart(rom, it.offset, definition.formatGeneration)
            } ?: missingEvidence("type-chart table not resolved")
        }
        if (definition.formatGeneration != 3 || !evidence.compatible) {
            return ResolvedTypeChartEvidence(evidence, null)
        }
        val selected = resolvedLayout(tables.typeChart, evidence, variableLength = true)
            ?.toTypeChartTableLayout()
            ?: return ResolvedTypeChartEvidence(
                evidence.copy(
                    compatible = false,
                    reasons = evidence.reasons + "selected type-chart ABI could not be represented by the typed codec",
                ),
                null,
            )
        val resolution = TypeChartResolver.resolve(
            session = session,
            activeTypeIds = (0 until (activeTypeLowerBound ?: selected.typeCount ?: 1)).toSet(),
            selectedLayout = selected,
        )
        val resolved = when (resolution) {
            is DatasetResolution.Resolved -> resolution.candidate.layout
            is DatasetResolution.Partial -> resolution.candidate.layout
            else -> null
        }
        return if (resolved != null) {
            ResolvedTypeChartEvidence(evidence, resolved)
        } else {
            ResolvedTypeChartEvidence(
                evidence.copy(
                    compatible = false,
                    reasons = evidence.reasons + "selected type-chart layout failed typed resolution",
                ),
                null,
            )
        }
    }

    private fun TableLayout.toTypeChartTableLayout(): TypeChartTableLayout? = when (elementSize) {
        4 -> denseTypeChartLayout(TypeChartAbi.DENSE_U32_Q412)
        2 -> denseTypeChartLayout(TypeChartAbi.DENSE_U16_Q412_WITH_INVERSE)
        null -> TypeChartTableLayout(offset.toLong(), TypeChartAbi.LEGACY_TRIPLETS)
        else -> null
    }

    private fun TableLayout.denseTypeChartLayout(abi: TypeChartAbi): TypeChartTableLayout? {
        val width = requireNotNull(elementSize)
        val typeCount = (recordSize / width).takeIf { it > 0 && it * width == recordSize } ?: return null
        if (typeCount.toLong() * typeCount != count.toLong()) return null
        return TypeChartTableLayout(offset.toLong(), abi, typeCount)
    }

    private data class ResolvedTypeChartEvidence(
        val evidence: ValidationEvidence,
        val resolved: ResolvedTypeChartLayout?,
    )

    private fun resolveAbilities(
        session: RomAnalysisSession,
        definition: EngineFamilyDefinition,
        identity: IdentityRootsPhaseResult.Resolved,
        core: CoreDatasetsPhaseResult.Resolved,
    ): ResolvedAbilityEvidence {
        val rom = session.rom
        val domain = AbilitySemanticDomain(
            validatedDirectAbilityIds(
                rom,
                validatedAbilityCoverageLayout(
                    rom,
                    resolvedLayout(core.candidateTables.baseStats, core.baseStats),
                    core.baseStatsLayout,
                ),
            ),
        )
        val abilityEvidence = if (definition.formatGeneration == 3) {
        core.candidateTables.abilities?.let { layout ->
            identity.expansion?.let { expansion ->
                TableValidators.names(rom, layout, expansion.abilityCount, identity.codec)
            } ?: resolveAbilityNames(
                session = session,
                layout = layout,
                codec = identity.codec,
                profile = identity.baseProfile,
                exact = identity.exactProfile != null,
                baseStats = core.baseStatsLayout,
                speciesCount = core.speciesCount,
                semanticDomain = domain,
            )
        } ?: missingEvidence("ability-name table not resolved")
    } else {
        missingEvidence("abilities are not part of this engine")
    }
        val evidence = reconcileAbilityEvidence(
            abilityEvidence,
            identity.tableResolution.publishedDataEvidence,
        )
        if (definition.formatGeneration != 3 || identity.expansion != null || !evidence.compatible) {
            return ResolvedAbilityEvidence(evidence, null)
        }
        val selectedTable = resolvedLayout(core.candidateTables.abilities, evidence)
            ?: return ResolvedAbilityEvidence(evidence, null)
        val selected = selectedTable.toAbilityNameTableLayout()
            ?: return ResolvedAbilityEvidence(
                evidence.copy(compatible = false, reasons = evidence.reasons +
                    "selected ability-name ABI could not be represented by the typed codec"),
                null,
            )
        val resolution = AbilityNameResolver().resolve(
            session = session,
            semanticDomain = domain,
            selectedLayout = selected,
        )
        val resolved = when (resolution) {
            is DatasetResolution.Resolved -> resolution.candidate.layout
            is DatasetResolution.Partial -> resolution.candidate.layout
            else -> null
        }
        val candidate = when (resolution) {
            is DatasetResolution.Resolved -> resolution.candidate
            is DatasetResolution.Partial -> resolution.candidate
            else -> null
        }
        val resolvedEvidence = candidate?.let { selected ->
            val semantic = selected.strength.semanticCoverage
            evidence.copy(
                compatible = true,
                validRecords = selected.layout.baseRows.count { it !is com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameRowOutcome.Malformed },
                totalRecords = selected.layout.baseRowCount,
                confidence = selected.strength.structuralCoverage.let {
                    it.covered.toDouble() / it.expected.toDouble()
                },
                offset = selected.layout.table.offset.toInt(),
                recordSize = selected.layout.table.nameWidth,
                coveredRecords = semantic?.covered,
                expectedRecords = semantic?.expected,
                incompleteRecords = semantic?.let { it.expected - it.covered },
                reviewRecommended = resolution is DatasetResolution.Partial,
                reasons = evidence.reasons + "validated selected ability names through the typed codec" +
                    ((resolution as? DatasetResolution.Partial)?.reasons ?: emptyList()),
            )
        } ?: evidence
        return if (resolved != null) ResolvedAbilityEvidence(resolvedEvidence, resolved) else {
            ResolvedAbilityEvidence(
                evidence.copy(
                    compatible = false,
                    reasons = evidence.reasons + "selected ability-name layout failed typed resolution",
                ),
                null,
            )
        }
    }

    private fun TableLayout.toAbilityNameTableLayout(): AbilityNameTableLayout? {
        val stride = stride ?: recordSize
        if (offset < 0 || count <= 1 || recordSize <= 0 || stride < recordSize ||
            variableLength || valuesArePointers || bank != null || banks.isNotEmpty()
        ) return null
        return AbilityNameTableLayout(offset, count, recordSize, stride)
    }

    private fun AbilityNameTableLayout.toTableLayout(): TableLayout = TableLayout(
        offset = offset.toInt(),
        count = count.toInt(),
        recordSize = nameWidth,
        stride = stride.takeIf { it != nameWidth },
    )

    private data class ResolvedAbilityEvidence(
        val evidence: ValidationEvidence,
        val resolved: ResolvedAbilityNameLayout?,
    )

    private fun resolveAbilityNames(
        session: RomAnalysisSession,
        layout: TableLayout,
        codec: PokemonTextCodec,
        profile: FamilyProfileBasis?,
        exact: Boolean,
        baseStats: TableLayout?,
        speciesCount: Int?,
        semanticDomain: AbilitySemanticDomain,
    ): ValidationEvidence {
        val rom = session.rom
        val inherited = TableValidators.names(
            rom,
            layout,
            inferAbilityCount(rom, layout, codec, profile, exact),
            codec,
            minimumRatio = 0.85,
        )
        val requiredDirectCount = (semanticDomain.maximumDirectAbilityId + 1).takeIf { it > 1 }
        val consumerStride = compiledAbilityNameStride(session, layout.offset)
        val dynamicCandidates = (8..32).flatMap { recordSize ->
            val inferredCount = TableValidators.inferFixedNameCount(
                rom, layout.offset, recordSize, codec, minimumCount = 10, maximumCount = 512,
            )
            buildSet {
                inferredCount?.let(::add)
                requiredDirectCount?.takeIf {
                    consumerStride == recordSize && (inferredCount == null || it > inferredCount)
                }?.let(::add)
            }.map { count ->
                TableValidators.fixedNames(rom, layout.offset, count, recordSize, codec)
            }
        }
        val typedCandidates = if (exact) emptyList() else dynamicCandidates.mapNotNull { evidence ->
            val offset = evidence.offset ?: return@mapNotNull null
            val width = evidence.recordSize ?: return@mapNotNull null
            val candidate = runCatching {
                AbilityNameTableLayout(offset.toLong(), evidence.totalRecords.toLong(), width)
            }.getOrNull() ?: return@mapNotNull null
            val decoded = AbilityNameCodec().decode(session, candidate, semanticDomain)
                as? com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameTableOutcome.Decoded
                ?: return@mapNotNull null
            evidence to decoded.resolved
        }
        val consumerBoundCandidates = consumerStride?.let { stride ->
            typedCandidates.filter { (_, resolved) -> resolved.table.stride == stride }
        }.orEmpty()
        val eligibleTypedCandidates = if (consumerStride == null) typedCandidates else consumerBoundCandidates
        val selected = when {
            exact -> inherited
            eligibleTypedCandidates.size == 1 -> {
                val (evidence, resolved) = eligibleTypedCandidates.single()
                val decodedIds = resolved.decodedDirectAbilityIds()
                val covered = semanticDomain.activeAbilityIds.count(decodedIds::contains)
                val expected = semanticDomain.activeAbilityIds.size
                val decodedBase = resolved.baseRows.drop(1)
                    .count { it is com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameRowOutcome.Decoded }
                evidence.copy(
                    compatible = true,
                    validRecords = decodedBase,
                    totalRecords = resolved.table.count.toInt(),
                    confidence = decodedBase.toDouble() / resolved.baseAbilityCount.toDouble(),
                    offset = resolved.table.offset.toInt(),
                    recordSize = resolved.table.nameWidth,
                    coveredRecords = covered.takeIf { expected > 0 },
                    expectedRecords = expected.takeIf { it > 0 },
                    incompleteRecords = (expected - covered).takeIf { expected > 0 },
                    reviewRecommended = covered < expected,
                    reasons = evidence.reasons + (
                        "selected the sole typed ability-name ABI coherent with compiled base-stat ability IDs" +
                            (consumerStride?.let { " and complete compiled stride-$it consumers" } ?: "")
                        ),
                )
            }
            eligibleTypedCandidates.size > 1 -> inherited.copy(
                compatible = false,
                ambiguous = true,
                reasons = inherited.reasons +
                    (
                        "multiple typed ability-name ABIs are coherent with compiled base-stat ability IDs: " +
                            eligibleTypedCandidates.joinToString { (_, resolved) ->
                                "${resolved.table.nameWidth}x${resolved.table.count}/base${resolved.baseRowCount}"
                            }
                        ),
            )
            else -> selectAbilityNameEvidence(exact, inherited) {
                dynamicCandidates
            }
        }
        val offset = selected.offset ?: return selected
        val width = selected.recordSize ?: return selected
        val directAbilityCount = minOf(speciesCount ?: baseStats?.count ?: 0, baseStats?.count ?: 0)
        if (baseStats == null || !Gen3BaseStatAbilitySlots.supportsLayout(rom, baseStats, directAbilityCount)) {
            return selected
        }
        val maximumAbilityId = validatedDirectAbilityIds(rom, baseStats).maxOrNull() ?: return selected
        val names = (0 until selected.totalRecords).map { index ->
            codec.decode(rom.slice(offset + index * width, width)).trim()
        }
        val boundary = semanticAbilityNameBoundary(names, maximumAbilityId) ?: return selected
        val bounded = TableValidators.fixedNames(rom, offset, boundary.count, width, codec)
        val coveredAbilities = boundary.count - 1
        val expectedAbilities = coveredAbilities + boundary.aliasLabelCount
        return bounded.copy(
            reasons = bounded.reasons + (
                "bounded direct ability IDs at ${boundary.count - 1} from species max $maximumAbilityId, " +
                    "${boundary.aliasLabelCount} post-separator runtime alias labels require species-conditioned lookup " +
                    "(coverage $coveredAbilities/$expectedAbilities)"
                ),
            coveredRecords = coveredAbilities,
            expectedRecords = expectedAbilities,
            incompleteRecords = boundary.aliasLabelCount,
            reviewRecommended = true,
        )
    }

    private fun inferAbilityCount(
        rom: RomImage,
        layout: TableLayout,
        codec: PokemonTextCodec,
        profile: FamilyProfileBasis?,
        exact: Boolean,
    ): Int = if (exact) {
        layout.count
    } else {
        TableValidators.inferFixedNameCount(rom, layout.offset, layout.recordSize, codec, 10, 512)
            ?: profile?.tables?.abilities?.count
            ?: layout.count
    }
}

/** Ability target coverage follows the complete independently validated physical base-stat extent. */
internal fun validatedDirectAbilityIds(
    rom: RomImage,
    baseStats: TableLayout?,
): Set<Int> {
    val table = baseStats ?: return emptySet()
    val count = table.count
    if (!Gen3BaseStatAbilitySlots.supportsLayout(rom, table, count)) return emptySet()
    val stride = table.stride ?: table.recordSize
    return buildSet {
        repeat(count) { index ->
            val offset = table.offset + index * stride
            val validEcology = table.recordSize != 28 || (
                rom.u8(offset + 19) in 0..5 && rom.u8(offset + 20) in 0..15 &&
                    rom.u8(offset + 21) in 0..15 && (rom.u8(offset + 25) and 0x7F) in 0..13 &&
                    rom.u8(offset + 26) == 0 && rom.u8(offset + 27) == 0
                )
            if (validEcology) addAll(Gen3BaseStatAbilitySlots.read(rom, table, index, count))
        }
    }
}

/** Promotes physical coverage only after the exact candidate root/count/ABI independently validates. */
internal fun validatedAbilityCoverageLayout(
    rom: RomImage,
    physical: TableLayout?,
    semantic: TableLayout?,
): TableLayout? {
    val candidate = physical ?: return semantic
    val recordSize = semantic?.recordSize ?: candidate.recordSize
    if (candidate.offset != semantic?.offset || candidate.recordSize != recordSize) return semantic
    val full = candidate.copy(recordSize = recordSize)
    val stride = full.stride ?: full.recordSize
    val endExclusive = full.offset.toLong() + (full.count - 1L) * stride + full.recordSize
    if (full.count <= 0 || full.offset < 0 || stride < full.recordSize || endExclusive > rom.size.toLong()) {
        return semantic
    }
    val evidence = TableValidators.baseStats(
        rom,
        full.offset,
        full.count,
        full.recordSize,
        generation = 3,
    )
    return if (evidence.compatible && evidence.offset == full.offset &&
        evidence.totalRecords == full.count && evidence.recordSize == full.recordSize
    ) full else semantic
}

/** Generic published-block ambiguity can gate only evidence explicitly tied to the same ability root. */
internal fun reconcileAbilityEvidence(
    abilityEvidence: ValidationEvidence,
    publishedDataEvidence: ValidationEvidence?,
): ValidationEvidence = publishedDataEvidence?.takeIf { published ->
    published.ambiguous && (
        !abilityEvidence.compatible ||
            (published.offset != null && published.offset == abilityEvidence.offset)
        )
} ?: abilityEvidence
