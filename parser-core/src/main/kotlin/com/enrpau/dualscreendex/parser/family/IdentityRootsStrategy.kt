package com.enrpau.dualscreendex.parser.family

import com.enrpau.dualscreendex.parser.analysis.ExactProfileSnapshot
import com.enrpau.dualscreendex.parser.analysis.ExactProfileTablesSnapshot
import com.enrpau.dualscreendex.parser.analysis.ExactTableLayoutSnapshot
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ExpandedSplitCaptureBallMetadata
import com.enrpau.dualscreendex.parser.model.GbaCompiledReferenceIndex
import com.enrpau.dualscreendex.parser.model.ParserProbe
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.RomProfile
import com.enrpau.dualscreendex.parser.model.ScoreEvidence
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.parse.GbaHeaderPointers
import com.enrpau.dualscreendex.parser.parse.GbaPublishedDataState
import com.enrpau.dualscreendex.parser.parse.GbaPublishedHeaderResolver
import com.enrpau.dualscreendex.parser.parse.PokeemeraldExpansionResolution
import com.enrpau.dualscreendex.parser.parse.PokeemeraldExpansionResolver
import com.enrpau.dualscreendex.parser.parse.Gen1CompiledBaseResolver
import com.enrpau.dualscreendex.parser.parse.Gen1CompiledDescriptionResolver
import com.enrpau.dualscreendex.parser.parse.Gen1CompiledMoveResolver
import com.enrpau.dualscreendex.parser.parse.Gen1CompiledNameResolver
import com.enrpau.dualscreendex.parser.parse.Gen1CompiledRelationshipResolver
import com.enrpau.dualscreendex.parser.parse.Gen1CompiledTypeChartResolver
import com.enrpau.dualscreendex.parser.parse.Gen2CompiledCoreResolver
import com.enrpau.dualscreendex.parser.parse.Gen2CompiledMoveResolver
import com.enrpau.dualscreendex.parser.parse.Gen2CompiledSpriteResolver
import com.enrpau.dualscreendex.parser.parse.ExpandedSplitCaptureBallResolver
import com.enrpau.dualscreendex.parser.parse.HeaderlessUnifiedSpeciesResolution
import com.enrpau.dualscreendex.parser.parse.HeaderlessUnifiedSpeciesResolver
import com.enrpau.dualscreendex.parser.parse.PublishedUnifiedSpeciesResolver
import com.enrpau.dualscreendex.parser.profile.KnownProfiles
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.language.OfficialLanguageResolver
import com.enrpau.dualscreendex.parser.validate.TableValidators
import java.util.Collections

internal sealed interface IdentityRootsPhaseResult {
    class Rejected(probe: ParserProbe) : IdentityRootsPhaseResult {
        val probe = probe.immutableCopy()
    }

    class Resolved internal constructor(
        exactProfile: ExactProfileSnapshot?,
        baseProfile: FamilyProfileBasis?,
        val identityMatched: Boolean,
        scoreEvidence: List<ScoreEvidence>,
        expansion: PokeemeraldExpansionResolution?,
        headerlessUnifiedSpecies: HeaderlessUnifiedSpeciesResolution? = null,
        expandedSplitCaptureBalls: ExpandedSplitCaptureBallMetadata? = null,
        compiledGbaReferences: GbaCompiledReferenceIndex?,
        tableResolution: ProfileTableResolution,
        val probeCodec: PokemonTextCodec,
    ) : IdentityRootsPhaseResult {
        val exactProfile = exactProfile
        val baseProfile = baseProfile?.immutableCopy()
        val scoreEvidence: List<ScoreEvidence> = Collections.unmodifiableList(scoreEvidence.toList())
        val expansion = expansion?.immutableCopy()
        val headerlessUnifiedSpecies = headerlessUnifiedSpecies
        val expandedSplitCaptureBalls = expandedSplitCaptureBalls?.copy(
            itemIdsByBallIndex = Collections.unmodifiableList(expandedSplitCaptureBalls.itemIdsByBallIndex.toList()),
        )
        val compiledGbaReferences = compiledGbaReferences?.copy(
            counts = Collections.unmodifiableMap(LinkedHashMap(compiledGbaReferences.counts)),
        )
        val tableResolution = tableResolution.immutableCopy()
    }
}

/** Resolves family identity, profile authority, and published/inherited roots before core datasets. */
internal class IdentityRootsStrategy : FamilyProbePhaseStrategy {
    override fun execute(
        session: RomAnalysisSession,
        definition: EngineFamilyDefinition,
        state: FamilyProbeState,
    ): FamilyProbeState = state.withIdentityRoots(resolve(session, definition))

    private fun resolve(
        session: RomAnalysisSession,
        definition: EngineFamilyDefinition,
    ): IdentityRootsPhaseResult {
        val header = session.header
        if (!definition.isPlatformCompatible(header.platform)) {
            return IdentityRootsPhaseResult.Rejected(
                ParserProbe(
                    definition.family,
                    0,
                    false,
                    0,
                    listOf(
                        ScoreEvidence(
                            "platform",
                            0,
                            10,
                            "expected ${definition.expectedPlatform}, found ${header.platform}",
                        ),
                    ),
                    emptyList(),
                ),
            )
        }

        val exact = definition.authoritativeExactProfile(session)
        val baseProfile = exact?.toFamilyProfileBasis()
            ?: closestProfile(definition, header)?.toFamilyProfileBasis()
        val identityMatched = definition.matchesIdentity(header)
        val score = listOf(
            ScoreEvidence("platform", 10, 10, "${header.platform} is compatible"),
            ScoreEvidence(
                "engine identity",
                if (identityMatched) 20 else 0,
                20,
                if (identityMatched) {
                    "title/game code matches ${definition.family}"
                } else {
                    "title/game code does not match ${definition.family}"
                },
            ),
        )
        val generation = definition.formatGeneration
        val probeCodec = OfficialLanguageResolver.preferredProbeCodec(
            rom = session.rom,
            header = header,
            generation = generation,
            cancellation = session.cancellation,
        )
        val expansion = if (generation == 3 && identityMatched) {
            PokeemeraldExpansionResolver.resolve(session.rom, probeCodec)
        } else {
            null
        }
        val headerlessUnifiedSpecies = if (
            generation == 3 && identityMatched && expansion == null &&
            definition.family == com.enrpau.dualscreendex.parser.model.EngineFamily.EMERALD
        ) {
            HeaderlessUnifiedSpeciesResolver.resolve(session, probeCodec)
                ?: PublishedUnifiedSpeciesResolver.resolve(session, probeCodec)
        } else {
            null
        }
        val expandedSplitCaptureBalls = if (generation == 3 && identityMatched && expansion == null) {
            ExpandedSplitCaptureBallResolver.resolve(session)
        } else {
            null
        }
        // A larger 8-bit boundary is authoritative only when a complete compiled sprite consumer
        // resolves the same expanded species count.
        val gen2CompiledCore = if (generation == 2 && exact == null) {
            Gen2CompiledCoreResolver.resolve(session.rom, probeCodec)?.takeIf { compiled ->
                baseProfile != null && when {
                    compiled.speciesCount < baseProfile.internalSpeciesCount -> true
                    compiled.speciesCount > baseProfile.internalSpeciesCount ->
                        Gen2CompiledSpriteResolver.resolve(
                            session.rom,
                            compiled.speciesCount,
                            session.cancellation,
                            session.limits,
                        ) != null
                    else -> false
                }
            }
        } else {
            null
        }
        val compiledGbaReferences = if (generation == 3 && expansion == null) {
            requireNotNull(session.gbaReferenceIndex).asLegacyCounts()
        } else {
            null
        }
        val inheritedTableResolution = expansion?.let { ProfileTableResolution(it.tables) }
            ?: resolveTables(session.rom, definition, baseProfile, probeCodec)
        val compiledGen1Names = if (generation == 1 && exact == null) {
            inheritedTableResolution.tables.speciesNames?.let { inherited ->
                Gen1CompiledNameResolver.resolve(session.rom, inherited.count, probeCodec)
            }
        } else {
            null
        }
        val compiledGen1NameTableResolution = compiledGen1Names?.let { speciesNames ->
            inheritedTableResolution.copy(
                tables = inheritedTableResolution.tables.copy(speciesNames = speciesNames),
            )
        } ?: inheritedTableResolution
        val compiledGen1Base = if (generation == 1 && exact == null) {
            compiledGen1NameTableResolution.tables.baseStats?.let { inherited ->
                Gen1CompiledBaseResolver.resolve(session.rom, inherited.count)
            }
        } else {
            null
        }
        val compiledGen1BaseTableResolution = compiledGen1Base?.let { baseStats ->
            val inheritedSprites = compiledGen1NameTableResolution.tables.sprites
            compiledGen1NameTableResolution.copy(
                tables = compiledGen1NameTableResolution.tables.copy(
                    baseStats = baseStats,
                    sprites = inheritedSprites?.copy(
                        offset = baseStats.offset,
                        count = baseStats.count,
                        recordSize = baseStats.recordSize,
                    ),
                ),
            )
        } ?: compiledGen1NameTableResolution
        val compiledGen1StructuralIdentity = generation == 1 && exact == null && !identityMatched &&
            compiledGen1Names != null && compiledGen1Base != null &&
            inheritedTableResolution.tables.moveNames?.let { inherited ->
                TableValidators.names(
                    session.rom,
                    inherited,
                    inherited.count,
                    probeCodec,
                    minimumRatio = 0.70,
                ).compatible
            } == true
        val effectiveIdentityMatched = identityMatched || compiledGen1StructuralIdentity
        val compiledGen1Moves = if (generation == 1 && exact == null && effectiveIdentityMatched) {
            Gen1CompiledMoveResolver.resolve(session.rom, probeCodec)
        } else {
            null
        }
        val compiledGen1MoveTableResolution = compiledGen1Moves?.let { moves ->
            compiledGen1BaseTableResolution.copy(
                tables = compiledGen1BaseTableResolution.tables.copy(
                    moveNames = moves.moveNames,
                    moveData = moves.moveData,
                ),
            )
        } ?: compiledGen1BaseTableResolution
        val compiledGen1Relationships = if (generation == 1 && exact == null) {
            val candidateTables = compiledGen1MoveTableResolution.tables
            candidateTables.speciesNames?.count?.let { preferredCount ->
                Gen1CompiledRelationshipResolver.resolve(
                    rom = session.rom,
                    preferredCount = preferredCount,
                    fallbackCounts = listOfNotNull(
                        candidateTables.baseStats?.count,
                        candidateTables.evolutions?.count,
                    ),
                )
            }
        } else {
            null
        }
        val compiledGen1RelationshipTableResolution = compiledGen1Relationships?.let { relationships ->
            compiledGen1MoveTableResolution.copy(
                tables = compiledGen1MoveTableResolution.tables.copy(
                    evolutions = relationships,
                    learnsets = relationships,
                ),
            )
        } ?: compiledGen1MoveTableResolution
        val compiledGen1Description = if (generation == 1 && exact == null) {
            val candidateTables = compiledGen1RelationshipTableResolution.tables
            candidateTables.speciesNames?.count?.let { preferredCount ->
                Gen1CompiledDescriptionResolver.resolve(
                    rom = session.rom,
                    preferredCount = preferredCount,
                    fallbackCounts = listOfNotNull(
                        candidateTables.baseStats?.count,
                        candidateTables.descriptions?.count,
                    ),
                    codec = probeCodec,
                )
            }
        } else {
            null
        }
        val compiledGen1DescriptionTableResolution = compiledGen1Description?.let { descriptions ->
            compiledGen1RelationshipTableResolution.copy(
                tables = compiledGen1RelationshipTableResolution.tables.copy(descriptions = descriptions),
            )
        } ?: compiledGen1RelationshipTableResolution
        val compiledGen1TypeChart = if (generation == 1 && exact == null) {
            Gen1CompiledTypeChartResolver.resolve(session.rom)
        } else {
            null
        }
        val compiledGen1TableResolution = compiledGen1TypeChart?.let { typeChart ->
            compiledGen1DescriptionTableResolution.copy(
                tables = compiledGen1DescriptionTableResolution.tables.copy(typeChart = typeChart),
            )
        } ?: compiledGen1DescriptionTableResolution
        val compiledCoreTableResolution = gen2CompiledCore?.let { compiled ->
            compiledGen1TableResolution.copy(
                tables = compiledGen1TableResolution.tables.copy(
                    speciesNames = compiled.tables.speciesNames,
                    baseStats = compiled.tables.baseStats,
                ),
            )
        } ?: compiledGen1TableResolution
        val compiledMoveData = if (generation == 2 && exact == null) {
            compiledCoreTableResolution.tables.moveData?.let { inherited ->
                Gen2CompiledMoveResolver.resolve(session.rom, inherited.count)
            }
        } else {
            null
        }
        val compiledMoveTableResolution = compiledMoveData?.let { moveData ->
            compiledCoreTableResolution.copy(
                tables = compiledCoreTableResolution.tables.copy(moveData = moveData),
            )
        } ?: compiledCoreTableResolution
        val compiledSpriteTable = if (generation == 2 && exact == null) {
            compiledMoveTableResolution.tables.sprites?.let { inherited ->
                val speciesCount = compiledMoveTableResolution.tables.speciesNames?.count ?: inherited.count
                Gen2CompiledSpriteResolver.resolve(
                    session.rom,
                    speciesCount,
                    session.cancellation,
                    session.limits,
                )
            }
        } else {
            null
        }
        val compiledSpriteTableResolution = compiledSpriteTable?.let { sprites ->
            compiledMoveTableResolution.copy(
                tables = compiledMoveTableResolution.tables.copy(sprites = sprites),
            )
        } ?: compiledMoveTableResolution
        val tableResolution = headerlessUnifiedSpecies?.let { unified ->
            compiledSpriteTableResolution.copy(
                tables = compiledSpriteTableResolution.tables.copy(
                    speciesNames = unified.tables.speciesNames,
                    baseStats = unified.tables.baseStats,
                    sprites = unified.tables.sprites,
                    descriptions = unified.tables.descriptions,
                    abilities = unified.tables.abilities ?: compiledSpriteTableResolution.tables.abilities,
                ),
            )
        } ?: compiledSpriteTableResolution
        return IdentityRootsPhaseResult.Resolved(
            exactProfile = exact,
            baseProfile = baseProfile,
            identityMatched = effectiveIdentityMatched,
            scoreEvidence = score + if (compiledGen1StructuralIdentity) {
                listOf(
                    ScoreEvidence(
                        "compiled Gen I lineage",
                        10,
                        10,
                        "compiled species consumers and this family's inherited move-name root agree",
                    ),
                )
            } else {
                emptyList()
            },
            expansion = expansion,
            headerlessUnifiedSpecies = headerlessUnifiedSpecies,
            expandedSplitCaptureBalls = expandedSplitCaptureBalls,
            compiledGbaReferences = compiledGbaReferences,
            tableResolution = tableResolution,
            probeCodec = probeCodec,
        )
    }

    private fun closestProfile(
        definition: EngineFamilyDefinition,
        header: RomHeader,
    ): RomProfile? {
        val candidates = KnownProfiles.forFamily(definition.family)
        return candidates.firstOrNull { profile ->
            profile.gameCode != null && profile.gameCode == header.gameCode
        } ?: candidates.firstOrNull { profile ->
            header.title.startsWith(profile.title.take(8), ignoreCase = true)
        } ?: candidates.firstOrNull()
    }

    private fun resolveTables(
        rom: RomImage,
        definition: EngineFamilyDefinition,
        profile: FamilyProfileBasis?,
        probeCodec: PokemonTextCodec,
    ): ProfileTableResolution {
        var inherited = profile?.tables ?: ProfileTables()
        if (definition.formatGeneration != 3) return ProfileTableResolution(inherited)

        val headerPointers = if (
            definition.family == com.enrpau.dualscreendex.parser.model.EngineFamily.EMERALD ||
            definition.family == com.enrpau.dualscreendex.parser.model.EngineFamily.FIRERED_LEAFGREEN
        ) {
            GbaPublishedHeaderResolver.resolve(rom, probeCodec)
        } else {
            val locatedNames = locateRubySapphireNames(rom)
            val expectedNames = inherited.speciesNames?.offset
            if (locatedNames != null && expectedNames != null && locatedNames != expectedNames) {
                inherited = inherited.relocatedBy(locatedNames - expectedNames)
            }
            GbaHeaderPointers(speciesNames = locatedNames)
        }

        fun publishedSemanticTable(pointer: Int?, fallback: TableLayout?, count: Int, recordSize: Int): TableLayout? =
            when (headerPointers.publishedDataState) {
                GbaPublishedDataState.RESOLVED -> pointer?.let { TableLayout(it, count, recordSize) }
                GbaPublishedDataState.ABSENT -> pointer?.let { TableLayout(it, count, recordSize) } ?: fallback
                GbaPublishedDataState.AMBIGUOUS -> null
            }

        return ProfileTableResolution(
            tables = ProfileTables(
                speciesNames = headerPointers.speciesNames?.let {
                    TableLayout(it, inherited.speciesNames?.count ?: 412, 11)
                } ?: inherited.speciesNames,
                baseStats = publishedSemanticTable(
                    headerPointers.baseStats,
                    inherited.baseStats,
                    inherited.baseStats?.count ?: 412,
                    28,
                ),
                moveNames = publishedSemanticTable(
                    headerPointers.moveNames,
                    inherited.moveNames,
                    inherited.moveNames?.count ?: 355,
                    13,
                ),
                moveData = publishedSemanticTable(
                    headerPointers.moveData,
                    inherited.moveData,
                    inherited.moveData?.count ?: 355,
                    12,
                ),
                typeChart = inherited.typeChart,
                evolutions = inherited.evolutions,
                learnsets = inherited.learnsets,
                sprites = headerPointers.sprites?.let {
                    TableLayout(it, inherited.sprites?.count ?: 412, 8)
                } ?: inherited.sprites,
                descriptions = inherited.descriptions,
                abilities = publishedSemanticTable(
                    headerPointers.abilities,
                    inherited.abilities,
                    inherited.abilities?.count ?: 78,
                    13,
                ),
            ),
            publishedDataEvidence = headerPointers.publishedDataEvidence,
            publishedBaseStatsRoot = headerPointers.baseStats.takeIf {
                headerPointers.publishedDataState == GbaPublishedDataState.RESOLVED
            },
        )
    }

    private fun ProfileTables.relocatedBy(delta: Int): ProfileTables {
        fun TableLayout?.relocated() = this?.copy(offset = offset + delta)
        return copy(
            speciesNames = speciesNames.relocated(),
            baseStats = baseStats.relocated(),
            moveNames = moveNames.relocated(),
            moveData = moveData.relocated(),
            typeChart = typeChart.relocated(),
            evolutions = evolutions.relocated(),
            learnsets = learnsets.relocated(),
            sprites = sprites.relocated(),
            descriptions = descriptions.relocated(),
            abilities = abilities.relocated(),
        )
    }

    private fun locateRubySapphireNames(rom: RomImage): Int? {
        val suffix = byteArrayOf(0x30, 0xB5.toByte(), 0x00, 0x25, 0x08, 0x4C, 0xC8.toByte(), 0xF7.toByte())
        val signature = rom.findAll(suffix).firstOrNull() ?: return null
        return if (signature >= 4) rom.gbaPointerOrNull(signature - 4) else null
    }

    private fun RomImage.gbaPointerOrNull(offset: Int): Int? = try {
        gbaPointer(offset)
    } catch (_: RuntimeException) {
        null
    }
}

internal data class FamilyProfileBasis(
    val name: String,
    val internalSpeciesCount: Int,
    val moveCount: Int,
    val tables: ProfileTables,
)

internal data class ProfileTableResolution(
    val tables: ProfileTables,
    val publishedDataEvidence: ValidationEvidence? = null,
    val publishedBaseStatsRoot: Int? = null,
)

private fun FamilyProfileBasis.immutableCopy(): FamilyProfileBasis = copy(tables = tables.immutableCopy())

private fun ProfileTableResolution.immutableCopy(): ProfileTableResolution = copy(
    tables = tables.immutableCopy(),
    publishedDataEvidence = publishedDataEvidence?.copy(
        reasons = Collections.unmodifiableList(publishedDataEvidence.reasons.toList()),
    ),
)

private fun PokeemeraldExpansionResolution.immutableCopy(): PokeemeraldExpansionResolution = copy(
    tables = tables.immutableCopy(),
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

private fun RomProfile.toFamilyProfileBasis(): FamilyProfileBasis = FamilyProfileBasis(
    name = name,
    internalSpeciesCount = internalSpeciesCount,
    moveCount = moveCount,
    tables = tables,
)

private fun ExactProfileSnapshot.toFamilyProfileBasis(): FamilyProfileBasis = FamilyProfileBasis(
    name = identity.name,
    internalSpeciesCount = internalSpeciesCount,
    moveCount = moveCount,
    tables = tables.toProfileTables(),
)

private fun ExactProfileTablesSnapshot.toProfileTables(): ProfileTables = ProfileTables(
    speciesNames = speciesNames?.toTableLayout(),
    baseStats = baseStats?.toTableLayout(),
    moveNames = moveNames?.toTableLayout(),
    moveData = moveData?.toTableLayout(),
    typeChart = typeChart?.toTableLayout(),
    evolutions = evolutions?.toTableLayout(),
    learnsets = learnsets?.toTableLayout(),
    sprites = sprites?.toTableLayout(),
    descriptions = descriptions?.toTableLayout(),
    abilities = abilities?.toTableLayout(),
)

private fun ExactTableLayoutSnapshot.toTableLayout(): TableLayout = TableLayout(
    offset = offset,
    count = count,
    recordSize = recordSize,
    variableLength = variableLength,
    bank = bank,
    banks = banks,
    pointerOffsets = pointerOffsets,
    elementSize = elementSize,
    bankAdjustment = bankAdjustment,
    bankRemap = bankRemap,
    stride = stride,
    valuesArePointers = valuesArePointers,
    format = format,
)
