package com.enrpau.dualscreendex.parser.family

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.Gen1DetachedSpeciesResolver
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.parse.DatasetResolvers
import com.enrpau.dualscreendex.parser.parse.Gen2CompiledNamePairResolver
import com.enrpau.dualscreendex.parser.parse.Gen3DynamicTableResolver
import com.enrpau.dualscreendex.parser.parse.Gen3PublishedPartialBaseStatsResolver
import com.enrpau.dualscreendex.parser.parse.PokeemeraldExpansionResolver
import com.enrpau.dualscreendex.parser.parse.PublishedPartialBaseStatsCandidate
import com.enrpau.dualscreendex.parser.parse.PublishedPartialBaseStatsResolution
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsAbi
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsResolver
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsSemanticDomain
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsTableLayout
import com.enrpau.dualscreendex.parser.dataset.moves.ResolvedMoveDetailsLayout
import com.enrpau.dualscreendex.parser.dataset.learnsets.EmbeddedLearnsetPointerResolver
import com.enrpau.dualscreendex.parser.resolution.DatasetResolution
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.language.RomLanguageManifest
import com.enrpau.dualscreendex.parser.validate.TableValidators
import java.util.Collections

internal sealed interface CoreDatasetsPhaseResult {
    class Resolved internal constructor(
        candidateTables: ProfileTables,
        val speciesCount: Int?,
        val inferredMoveCount: Int?,
        val moveCount: Int?,
        speciesNames: ValidationEvidence,
        baseStats: ValidationEvidence,
        moveNames: ValidationEvidence,
        moveData: ValidationEvidence,
        speciesNamesLayout: TableLayout?,
        baseStatsLayout: TableLayout?,
        moveNamesLayout: TableLayout?,
        moveDataLayout: TableLayout?,
        resolvedMoveDetails: ResolvedMoveDetailsLayout? = null,
        val headerlessEmbeddedLearnsets: EmbeddedLearnsetPointerResolver.Resolution? = null,
        moveDetailsTypedRejectionReason: String? = null,
        publishedPartialBaseStatsCandidate: PublishedPartialBaseStatsCandidate? = null,
        val languageManifest: RomLanguageManifest = RomLanguageManifest.UNKNOWN,
    ) : CoreDatasetsPhaseResult {
        val candidateTables = candidateTables.immutableCopy()
        val speciesNames = speciesNames.immutableCopy()
        val baseStats = baseStats.immutableCopy()
        val moveNames = moveNames.immutableCopy()
        val moveData = moveData.immutableCopy()
        val speciesNamesLayout = speciesNamesLayout?.immutableCopy()
        val baseStatsLayout = baseStatsLayout?.immutableCopy()
        val moveNamesLayout = moveNamesLayout?.immutableCopy()
        val moveDataLayout = moveDataLayout?.immutableCopy()
        val resolvedMoveDetails = resolvedMoveDetails?.immutableSnapshot()
        val moveDetailsTypedRejectionReason = moveDetailsTypedRejectionReason?.also {
            require(it.isNotBlank()) { "typed move-detail rejection reason must not be blank" }
        }
        val publishedPartialBaseStatsCandidate = publishedPartialBaseStatsCandidate?.immutableCopy()

        fun withPromotedBaseStats(recovered: PublishedPartialBaseStatsResolution): Resolved = Resolved(
            candidateTables = candidateTables,
            speciesCount = speciesCount,
            inferredMoveCount = inferredMoveCount,
            moveCount = moveCount,
            speciesNames = speciesNames,
            baseStats = recovered.evidence,
            moveNames = moveNames,
            moveData = moveData,
            speciesNamesLayout = speciesNamesLayout,
            baseStatsLayout = recovered.layout,
            moveNamesLayout = moveNamesLayout,
            moveDataLayout = moveDataLayout,
            resolvedMoveDetails = resolvedMoveDetails,
            headerlessEmbeddedLearnsets = headerlessEmbeddedLearnsets,
            moveDetailsTypedRejectionReason = moveDetailsTypedRejectionReason,
            publishedPartialBaseStatsCandidate = publishedPartialBaseStatsCandidate,
            languageManifest = languageManifest,
        )
    }
}

/** Resolves and validates the independent species-name, base-stat, and move datasets. */
internal class CoreDatasetsStrategy : FamilyProbePhaseStrategy {
    override fun execute(
        session: RomAnalysisSession,
        definition: EngineFamilyDefinition,
        state: FamilyProbeState,
    ): FamilyProbeState {
        val identity = requireNotNull(state.identityRoots) {
            "${FamilyProbePhase.CORE_DATASETS} requires resolved identity roots"
        } as? IdentityRootsPhaseResult.Resolved
            ?: error("${FamilyProbePhase.CORE_DATASETS} cannot consume rejected identity roots")
        return state.withCoreDatasets(resolve(session, definition, identity))
    }

    private fun resolve(
        session: RomAnalysisSession,
        definition: EngineFamilyDefinition,
        identity: IdentityRootsPhaseResult.Resolved,
    ): CoreDatasetsPhaseResult.Resolved {
        val rom = session.rom
        val generation = definition.formatGeneration
        val exact = identity.exactProfile
        val profile = identity.baseProfile
        val expansion = identity.expansion
        val headerlessUnifiedSpecies = identity.headerlessUnifiedSpecies
        val tableResolution = identity.tableResolution
        val publishedDataEvidence = tableResolution.publishedDataEvidence
        val probeCodec = identity.probeCodec
        var tables = tableResolution.tables

        val headerlessEmbeddedLearnsets = if (generation == 3 && expansion == null) {
            headerlessUnifiedSpecies?.let { unified ->
                EmbeddedLearnsetPointerResolver.resolve(
                    session = session,
                    metadata = unified.metadata,
                    speciesCount = unified.speciesCount,
                )
            }
        } else {
            null
        }
        val ordinaryMoveCount = headerlessEmbeddedLearnsets
            ?.resolved
            ?.catalogPrimaryEntries()
            ?.values
            ?.asSequence()
            ?.flatten()
            ?.maxOfOrNull { it.moveId }
            ?.plus(1)
        val headerlessUnifiedMoves = ordinaryMoveCount?.let { moveCount ->
            com.enrpau.dualscreendex.parser.parse.HeaderlessUnifiedMoveResolver.resolve(
                session = session,
                ordinaryMoveCount = moveCount,
            )
        }
        headerlessUnifiedMoves?.let { unified ->
            tables = tables.copy(
                moveNames = unified.tables.moveNames,
                moveData = unified.tables.moveData,
            )
        }

        var speciesCount = expansion?.speciesCount ?: headerlessUnifiedSpecies?.speciesCount ?: inferSpeciesCount(
            rom, tables, probeCodec, profile, generation, exactProfile = exact != null,
        )
        if (generation == 3 && expansion == null && headerlessUnifiedSpecies == null && exact == null && speciesCount != null) {
            val reconciled = Gen3DynamicTableResolver.reconcileSpeciesExtent(
                rom,
                tables,
                speciesCount,
                identity.compiledGbaReferences,
                probeCodec,
                session.gbaReferenceIndex,
            )
            speciesCount = reconciled.speciesCount
            tables = reconciled.tables
        }
        val inferredMoveCount = expansion?.moveCount ?: headerlessUnifiedMoves?.moveCount ?: inferMoveCount(
            rom, tables.moveNames, probeCodec, profile, exactProfile = exact != null,
        )
        if (generation == 2 && speciesCount != null && inferredMoveCount != null) {
            Gen2CompiledNamePairResolver.resolve(
                rom = rom,
                speciesCount = speciesCount,
                moveCount = inferredMoveCount,
                codec = probeCodec,
                cancellation = session.cancellation,
            )?.let { compiledNames ->
                tables = tables.copy(
                    speciesNames = compiledNames.speciesNames,
                    moveNames = compiledNames.moveNames,
                )
            }
        }
        var dynamicBaseStatsEvidence: ValidationEvidence? = null
        var dynamicMoveDataEvidence: ValidationEvidence? = null
        if (
            generation == 3 && expansion == null && publishedDataEvidence == null &&
            speciesCount != null && inferredMoveCount != null
        ) {
            val dynamic = Gen3DynamicTableResolver.resolveWithEvidence(
                rom, tables, speciesCount, inferredMoveCount,
            )
            tables = dynamic.tables
            dynamicBaseStatsEvidence = dynamic.baseStatsEvidence.takeIf { headerlessUnifiedSpecies == null }
            dynamicMoveDataEvidence = dynamic.moveDataEvidence
            headerlessUnifiedSpecies?.let { unified ->
                tables = tables.copy(
                    speciesNames = unified.tables.speciesNames,
                    baseStats = unified.tables.baseStats,
                )
            }
        }

        var baseStatsLayout = tables.baseStats?.let { layout ->
            if (generation == 3 && speciesCount != null && expansion == null && headerlessUnifiedSpecies == null) {
                val inferredSize = TableValidators.inferBaseStatsRecordSize(
                    rom, layout.offset, speciesCount, generation,
                )
                if (inferredSize != null) layout.copy(recordSize = inferredSize) else layout
            } else {
                layout
            }
        }
        var speciesNamesLayout = tables.speciesNames
        var names = expansion?.let { PokeemeraldExpansionResolver.validateSpeciesNames(rom, it) }
            ?: headerlessUnifiedSpecies?.speciesNamesEvidence
            ?: validateNames(rom, speciesNamesLayout, speciesCount, probeCodec, generation)
        if (generation == 2 && !names.compatible && speciesCount != null) {
            TableValidators.locateFixedNameTable(
                rom, speciesCount, 8..16, probeCodec, preferredOffset = speciesNamesLayout?.offset,
            )?.let { relocated ->
                speciesNamesLayout = TableLayout(
                    offset = requireNotNull(relocated.offset),
                    count = speciesCount,
                    recordSize = requireNotNull(relocated.recordSize),
                )
                names = relocated
            }
        }
        var stats = expansion?.let { PokeemeraldExpansionResolver.validateBaseStats(rom, it) }
            ?: headerlessUnifiedSpecies?.baseStatsEvidence
            ?: publishedDataEvidence
            ?: dynamicBaseStatsEvidence
            ?: baseStatsLayout?.let {
            val validationCount = if (generation == 1) it.count else speciesCount ?: it.count
            TableValidators.baseStats(rom, it.offset, validationCount, it.recordSize, generation)
        } ?: missing("species base-stat table not resolved")
        if (generation == 1 && baseStatsLayout != null) {
            stats = Gen1DetachedSpeciesResolver.completeEvidence(
                stats,
                Gen1DetachedSpeciesResolver.resolve(rom, baseStatsLayout, session.cancellation),
                "base-stat record",
            )
        }
        if (generation == 2 && !stats.compatible && speciesCount != null) {
            TableValidators.locateBaseStatTable(rom, speciesCount, 28..64, generation)?.let { relocated ->
                baseStatsLayout = TableLayout(
                    offset = requireNotNull(relocated.offset),
                    count = speciesCount,
                    recordSize = requireNotNull(relocated.recordSize),
                )
                stats = relocated
            }
        }
        var moveNamesLayout = tables.moveNames
        var moveNames = headerlessUnifiedMoves?.moveNamesEvidence
            ?: validateNames(rom, moveNamesLayout, inferredMoveCount, probeCodec, generation)
        if (
            generation == 2 && exact == null && probeCodec.language == LanguageTag.ENGLISH &&
                inferredMoveCount != null &&
            moveNamesLayout?.variableLength == true &&
            tables.moveData?.let { hasCanonicalGen2MovePrefix(rom, it) } == true
        ) {
            TableValidators.locateVariableNameSequenceNear(
                rom = rom,
                approximateOffset = moveNamesLayout.offset,
                codec = probeCodec,
                expectedNames = listOf("POUND", "KARATE CHOP", "DOUBLESLAP"),
            )?.let { relocatedOffset ->
                val relocated = TableValidators.variableNames(rom, relocatedOffset, inferredMoveCount, probeCodec)
                if (relocated.compatible) {
                    moveNamesLayout = moveNamesLayout.copy(offset = relocatedOffset)
                    moveNames = relocated.copy(reasons = listOf("matched leading canonical Gen 2 move records"))
                }
            }
        }
        val moveData = headerlessUnifiedMoves?.moveDataEvidence ?: publishedDataEvidence ?: dynamicMoveDataEvidence ?: tables.moveData?.let {
            if (expansion != null) {
                TableValidators.pokeemeraldExpansionMoveData(rom, it, inferredMoveCount ?: it.count)
            } else if (it.format == TableRecordFormat.CFRU_MOVE_16) {
                TableValidators.cfruMoveData(rom, it.offset, inferredMoveCount ?: it.count)
            } else if (it.format == TableRecordFormat.WIDENED_RETAIL_MOVE_16) {
                TableValidators.widenedRetailMoveData(
                    rom,
                    it.offset,
                    inferredMoveCount ?: it.count,
                    Gen3DynamicTableResolver.moveTypeUpperBound(tables),
                )
            } else if (it.format == TableRecordFormat.HYBRID_BATTLE_MOVE_20) {
                TableValidators.hybridBattleMoveData(rom, it.offset, inferredMoveCount ?: it.count)
            } else if (it.format == TableRecordFormat.BATTLE_ENGINE_MOVE_20) {
                TableValidators.battleEngineMoveData(rom, it.offset, inferredMoveCount ?: it.count)
            } else {
                TableValidators.moveData(
                    rom, it.offset, inferredMoveCount ?: it.count, it.recordSize, generation,
                )
            }
        } ?: missing("move-data table not resolved")
        var effectiveMoveCount = DatasetResolvers.reconciledMoveCount(inferredMoveCount, moveData)
        if (effectiveMoveCount != inferredMoveCount && effectiveMoveCount != null && moveNamesLayout != null) {
            val reconciledNames = validateNames(rom, moveNamesLayout, effectiveMoveCount, probeCodec, generation)
            if (reconciledNames.compatible) {
                moveNames = reconciledNames.copy(
                    reasons = reconciledNames.reasons +
                        "bounded the move catalog by the validated move-data prefix",
                )
            } else {
                effectiveMoveCount = inferredMoveCount
            }
        }
        val moveDetailsResolution = if (
            headerlessUnifiedMoves == null && generation == 3 && expansion == null && moveData.compatible
        ) {
            val selected = resolvedLayout(tables.moveData, moveData)?.toMoveDetailsTableLayout()
            val activeRows = selected?.let { selectedLayout ->
                activeMoveRows(rom, moveNamesLayout, selectedLayout.count.toInt(), probeCodec)
            }.orEmpty()
            if (selected != null && activeRows.isNotEmpty()) {
                MoveDetailsResolver.resolve(
                    session = session,
                    semanticDomain = MoveDetailsSemanticDomain(selected.count, activeRows),
                    selectedLayout = selected,
                )
            } else {
                DatasetResolution.Unavailable(
                    kind = com.enrpau.dualscreendex.parser.resolution.DatasetKind.MOVE_DATA,
                    observedCandidates = if (selected == null) 0 else 1,
                    reason = "selected move-details layout has no typed ABI or active named semantic domain",
                )
            }
        } else {
            null
        }
        val resolvedMoveDetails = headerlessUnifiedMoves?.resolvedMoveDetails ?: when (moveDetailsResolution) {
            is DatasetResolution.Resolved -> moveDetailsResolution.candidate.layout
            is DatasetResolution.Partial -> moveDetailsResolution.candidate.layout
            else -> null
        }
        val moveDetailsTypedRejectionReason = if (headerlessUnifiedMoves != null) null else when (moveDetailsResolution) {
            is DatasetResolution.Unavailable -> moveDetailsResolution.reason
            is DatasetResolution.Ambiguous -> "selected move-details typed resolution was ambiguous"
            is DatasetResolution.BudgetExceeded -> moveDetailsResolution.reason
            else -> null
        }

        val publishedPartialBaseStatsCandidate =
            if (generation == 3 && expansion == null && !stats.compatible) {
                Gen3PublishedPartialBaseStatsResolver.resolveStructuralCandidate(
                rom = rom,
                publishedRoot = tableResolution.publishedBaseStatsRoot,
                names = names,
                generation = generation,
                speciesCount = speciesCount,
            )
            } else {
                null
            }

        val languageManifest = RomLanguageAuthority.resolve(
            rom = rom,
            header = session.header,
            generation = generation,
            probeCodec = probeCodec,
            speciesNamesEvidence = names,
            moveNamesEvidence = moveNames,
            speciesNamesLayout = resolvedLayout(speciesNamesLayout, names),
            moveNamesLayout = resolvedLayout(moveNamesLayout, moveNames),
            cancellation = session.cancellation,
        )

        return CoreDatasetsPhaseResult.Resolved(
            candidateTables = tables,
            speciesCount = speciesCount,
            inferredMoveCount = inferredMoveCount,
            moveCount = effectiveMoveCount,
            speciesNames = names,
            baseStats = stats,
            moveNames = moveNames,
            moveData = moveData,
            speciesNamesLayout = speciesNamesLayout,
            baseStatsLayout = baseStatsLayout,
            moveNamesLayout = moveNamesLayout,
            moveDataLayout = tables.moveData,
            resolvedMoveDetails = resolvedMoveDetails,
            headerlessEmbeddedLearnsets = headerlessEmbeddedLearnsets,
            moveDetailsTypedRejectionReason = moveDetailsTypedRejectionReason,
            publishedPartialBaseStatsCandidate = publishedPartialBaseStatsCandidate,
            languageManifest = languageManifest,
        )
    }

    private fun inferSpeciesCount(
        rom: RomImage,
        tables: ProfileTables,
        codec: PokemonTextCodec,
        profile: FamilyProfileBasis?,
        generation: Int,
        exactProfile: Boolean,
    ): Int? {
        val layout = tables.speciesNames ?: return null
        if (exactProfile || generation != 3) return layout.count
        val boundaryCount = TableValidators.inferCountFromFollowingTable(
            offset = layout.offset,
            recordSize = layout.recordSize,
            followingOffsets = listOfNotNull(
                tables.baseStats?.offset,
                tables.moveNames?.offset,
                tables.moveData?.offset,
                tables.typeChart?.offset,
                tables.sprites?.offset,
                tables.abilities?.offset,
            ),
            minimumCount = 300,
            maximumCount = 2048,
        )
        return boundaryCount
            ?: TableValidators.inferFixedNameCount(rom, layout.offset, layout.recordSize, codec, 300, 2048)
            ?: profile?.internalSpeciesCount
            ?: layout.count
    }

    private fun TableLayout.toMoveDetailsTableLayout(): MoveDetailsTableLayout? {
        val abi = MoveDetailsAbi.entries.singleOrNull {
            it.recordSize == recordSize && it.tableRecordFormat == format
        } ?: return null
        if (variableLength || valuesArePointers || (stride != null && stride != recordSize)) return null
        return MoveDetailsTableLayout(offset.toLong(), count.toLong(), abi)
    }

    private fun activeMoveRows(
        rom: RomImage,
        names: TableLayout?,
        detailCount: Int,
        codec: PokemonTextCodec,
    ): Set<Int> {
        names ?: return emptySet()
        if (names.variableLength || names.valuesArePointers) return emptySet()
        val stride = names.stride ?: names.recordSize
        return (1 until minOf(names.count, detailCount)).filterTo(linkedSetOf()) { index ->
            codec.decode(rom.slice(names.offset + index * stride, names.recordSize)).any(Char::isLetterOrDigit)
        }
    }

    private fun inferMoveCount(
        rom: RomImage,
        layout: TableLayout?,
        codec: PokemonTextCodec,
        profile: FamilyProfileBasis?,
        exactProfile: Boolean,
    ): Int? {
        layout ?: return null
        if (layout.variableLength || exactProfile) return layout.count
        return TableValidators.inferFixedNameCount(rom, layout.offset, layout.recordSize, codec, 100, 2048)
            ?: profile?.moveCount?.plus(1)
            ?: layout.count
    }

    private fun validateNames(
        rom: RomImage,
        layout: TableLayout?,
        inferredCount: Int?,
        codec: PokemonTextCodec,
        generation: Int,
    ): ValidationEvidence {
        if (layout == null || inferredCount == null) return missing("name table not resolved")
        val minimumRatio = if (generation == 1) 0.70 else 0.85
        return TableValidators.names(rom, layout, inferredCount, codec, minimumRatio)
    }

    private fun hasCanonicalGen2MovePrefix(rom: RomImage, layout: TableLayout): Boolean {
        if (layout.count < 3 || layout.recordSize < 7) return false
        val expected = listOf(
            intArrayOf(40, 0, 255, 35),
            intArrayOf(50, 1, 255, 25),
            intArrayOf(15, 0, 216, 10),
        )
        return expected.indices.all { index ->
            val base = layout.offset + index * layout.recordSize
            val values = intArrayOf(rom.u8(base + 2), rom.u8(base + 3), rom.u8(base + 4), rom.u8(base + 5))
            values.contentEquals(expected[index])
        }
    }
}

internal fun resolvedLayout(
    inherited: TableLayout?,
    evidence: ValidationEvidence,
    variableLength: Boolean = inherited?.variableLength ?: false,
): TableLayout? {
    if (!evidence.compatible || evidence.offset == null) return null
    val offset = evidence.offset
    val count = evidence.totalRecords.takeIf { it > 0 } ?: inherited?.count ?: 0
    val recordSize = evidence.recordSize ?: inherited?.recordSize ?: 0
    return inherited?.copy(
        offset = offset,
        count = count,
        recordSize = recordSize,
        variableLength = variableLength,
        elementSize = evidence.elementSize ?: inherited.elementSize,
        format = evidence.format ?: inherited.format,
    ) ?: TableLayout(
        offset = offset,
        count = count,
        recordSize = recordSize,
        variableLength = variableLength,
        elementSize = evidence.elementSize,
        format = evidence.format ?: TableRecordFormat.STANDARD,
    )
}

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

private fun ValidationEvidence.immutableCopy(): ValidationEvidence = copy(
    reasons = Collections.unmodifiableList(reasons.toList()),
)

private fun PublishedPartialBaseStatsCandidate.immutableCopy(): PublishedPartialBaseStatsCandidate = copy(
    layout = layout.immutableCopy(),
    rawEvidence = rawEvidence.immutableCopy(),
)

private fun missing(reason: String) = ValidationEvidence(false, 0, 0, 0.0, listOf(reason))
