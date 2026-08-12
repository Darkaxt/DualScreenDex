package com.enrpau.dualscreendex.parser.family

import com.enrpau.dualscreendex.parser.analysis.ExactProfileSnapshot
import com.enrpau.dualscreendex.parser.analysis.ExactProfileTablesSnapshot
import com.enrpau.dualscreendex.parser.analysis.ExactTableLayoutSnapshot
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
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
import com.enrpau.dualscreendex.parser.profile.KnownProfiles
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
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
        compiledGbaReferences: GbaCompiledReferenceIndex?,
        tableResolution: ProfileTableResolution,
        val codec: PokemonTextCodec,
    ) : IdentityRootsPhaseResult {
        val exactProfile = exactProfile
        val baseProfile = baseProfile?.immutableCopy()
        val scoreEvidence: List<ScoreEvidence> = Collections.unmodifiableList(scoreEvidence.toList())
        val expansion = expansion?.immutableCopy()
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
        val expansion = if (generation == 3 && identityMatched) {
            PokeemeraldExpansionResolver.resolve(session.rom)
        } else {
            null
        }
        val compiledGbaReferences = if (generation == 3 && expansion == null) {
            requireNotNull(session.gbaReferenceIndex).asLegacyCounts()
        } else {
            null
        }
        val tableResolution = expansion?.let { ProfileTableResolution(it.tables) }
            ?: resolveTables(session.rom, definition, baseProfile)
        val codec = if (generation == 3) PokemonTextCodec.gbaEnglish else PokemonTextCodec.gbEnglish
        return IdentityRootsPhaseResult.Resolved(
            exactProfile = exact,
            baseProfile = baseProfile,
            identityMatched = identityMatched,
            scoreEvidence = score,
            expansion = expansion,
            compiledGbaReferences = compiledGbaReferences,
            tableResolution = tableResolution,
            codec = codec,
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
    ): ProfileTableResolution {
        var inherited = profile?.tables ?: ProfileTables()
        if (definition.formatGeneration != 3) return ProfileTableResolution(inherited)

        val headerPointers = if (
            definition.family == com.enrpau.dualscreendex.parser.model.EngineFamily.EMERALD ||
            definition.family == com.enrpau.dualscreendex.parser.model.EngineFamily.FIRERED_LEAFGREEN
        ) {
            GbaPublishedHeaderResolver.resolve(rom)
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
                moveNames = headerPointers.moveNames?.let {
                    TableLayout(it, inherited.moveNames?.count ?: 355, 13)
                } ?: inherited.moveNames,
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
