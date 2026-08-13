package com.enrpau.dualscreendex.parser.model

import com.enrpau.dualscreendex.parser.dataset.types.ResolvedTypeChartLayout
import com.enrpau.dualscreendex.parser.dataset.descriptions.ResolvedDescriptionLayout
import com.enrpau.dualscreendex.parser.dataset.evolutions.ResolvedEvolutionLayout
import com.enrpau.dualscreendex.parser.dataset.learnsets.ResolvedLearnsetSet
import com.enrpau.dualscreendex.parser.dataset.moves.ResolvedMoveDetailsLayout
import com.enrpau.dualscreendex.parser.dataset.abilities.ResolvedAbilityNameLayout

enum class Platform { GB, GBC, GBA, UNKNOWN }

enum class EngineFamily {
    RED_BLUE,
    YELLOW,
    GOLD_SILVER,
    CRYSTAL,
    RUBY_SAPPHIRE,
    EMERALD,
    FIRERED_LEAFGREEN,
}

enum class RomCapability {
    SPECIES_CATALOG,
    SPECIES_NAMES,
    SPECIES_TYPES,
    TYPE_CHART,
    BASE_STATS,
    SPRITES,
    POKEDEX_DESCRIPTIONS,
    EVOLUTIONS,
    MOVE_CATALOG,
    MOVE_DETAILS,
    MOVE_DESCRIPTIONS,
    LEARNSETS,
    EGG_MOVES,
    MACHINE_MOVES,
    TUTOR_MOVES,
    ABILITIES,
    ABILITY_DESCRIPTIONS,
    ABILITY_MECHANICS,
    AREA_ENCOUNTERS,
    TYPE_PRESENTATION,
    BALL_CATALOG,
}

enum class CapabilityStatus { AVAILABLE, PARTIAL, AMBIGUOUS, NOT_FOUND, NOT_APPLICABLE }

enum class CapabilityReviewStatus { NONE, MANUAL_REVIEW }

enum class SelectionStatus { SELECTED, AMBIGUOUS, NO_FAMILY_MATCH, ERROR }

data class RomHeader(
    val platform: Platform,
    val title: String,
    val gameCode: String? = null,
    val revision: Int = 0,
    val cgbFlag: Int? = null,
)

data class TableLayout(
    val offset: Int,
    val count: Int,
    val recordSize: Int,
    val variableLength: Boolean = false,
    val bank: Int? = null,
    val banks: List<Int> = emptyList(),
    val pointerOffsets: List<Int> = emptyList(),
    val elementSize: Int? = null,
    val bankAdjustment: Int = 0,
    val bankRemap: Map<Int, Int> = emptyMap(),
    /** Physical distance between records when [recordSize] describes only the exposed field. */
    val stride: Int? = null,
    /** Each record starts with a GBA pointer to the exposed value instead of inline bytes. */
    val valuesArePointers: Boolean = false,
    /** Byte-level record interpretation after structural validation. */
    val format: TableRecordFormat = TableRecordFormat.STANDARD,
)

enum class TableRecordFormat {
    STANDARD,
    CFRU_MOVE_16,
    BATTLE_ENGINE_MOVE_20,
    GEN3_PACKED_U16,
    GEN3_MOVE_U16_LEVEL_U8,
    GEN3_MOVE_U16_LEVEL_U16,
    GEN3_LEVEL_U8_MOVE_U16,
}

data class ProfileTables(
    val speciesNames: TableLayout? = null,
    val baseStats: TableLayout? = null,
    val moveNames: TableLayout? = null,
    val moveData: TableLayout? = null,
    val typeChart: TableLayout? = null,
    val evolutions: TableLayout? = null,
    val learnsets: TableLayout? = null,
    val sprites: TableLayout? = null,
    val descriptions: TableLayout? = null,
    val abilities: TableLayout? = null,
)

/** Immutable typed dataset outcomes selected during the shared ROM-analysis session. */
class ResolvedDatasetLayouts(
    typeChart: ResolvedTypeChartLayout? = null,
    descriptions: ResolvedDescriptionLayout? = null,
    evolutions: ResolvedEvolutionLayout? = null,
    learnsets: ResolvedLearnsetSet? = null,
    moveDetails: ResolvedMoveDetailsLayout? = null,
    abilityNames: ResolvedAbilityNameLayout? = null,
) {
    val typeChart: ResolvedTypeChartLayout? = typeChart?.immutableSnapshot()
    val descriptions: ResolvedDescriptionLayout? = descriptions?.immutableSnapshot()
    val evolutions: ResolvedEvolutionLayout? = evolutions?.immutableSnapshot()
    val learnsets: ResolvedLearnsetSet? = learnsets?.immutableSnapshot()
    val moveDetails: ResolvedMoveDetailsLayout? = moveDetails?.immutableSnapshot()
    val abilityNames: ResolvedAbilityNameLayout? = abilityNames?.immutableSnapshot()

    fun immutableSnapshot(): ResolvedDatasetLayouts = this

    override fun equals(other: Any?): Boolean = other is ResolvedDatasetLayouts &&
        typeChart == other.typeChart && descriptions == other.descriptions && evolutions == other.evolutions &&
            learnsets == other.learnsets && moveDetails == other.moveDetails && abilityNames == other.abilityNames

    override fun hashCode(): Int =
        31 * (
            31 * (
                31 * (31 * (typeChart?.hashCode() ?: 0) + (descriptions?.hashCode() ?: 0)) +
                    (evolutions?.hashCode() ?: 0)
                ) + (learnsets?.hashCode() ?: 0)
            ) + (moveDetails?.hashCode() ?: 0) + 31 * (abilityNames?.hashCode() ?: 0)
}

data class ResolvedRomLayout(
    val family: EngineFamily,
    val generation: Int,
    val platform: Platform,
    val speciesCount: Int?,
    val moveCount: Int?,
    val tables: ProfileTables,
    val pokeemeraldExpansion: PokeemeraldExpansionMetadata? = null,
    val headerlessUnifiedSpecies: HeaderlessUnifiedSpeciesMetadata? = null,
    val compiledGbaReferences: GbaCompiledReferenceIndex? = null,
    val learnsetTables: List<Gen3LearnsetTableLayout> = emptyList(),
    val learnsetSelector: Gen3LearnsetSelectorEvidence? = null,
    val resolvedDatasets: ResolvedDatasetLayouts = ResolvedDatasetLayouts(),
)

/** Binary-proven unified species record ABI without a published expansion header. */
data class HeaderlessUnifiedSpeciesMetadata(
    val speciesRecordSize: Int,
    val activePredicateOffset: Int,
    val speciesNameOffset: Int,
    val speciesNameWidth: Int,
    val nationalDexOffset: Int,
)

data class GbaCompiledReferenceIndex(
    val counts: Map<Int, Int>,
    val overflowReason: String? = null,
) {
    val overflowed: Boolean get() = overflowReason != null
}

data class Gen3LearnsetTableLayout(
    val table: TableLayout,
    val confidence: Double,
    val referenceCount: Int,
)

/** Direct compiled evidence selecting between two level-up learnset tables from SaveBlock1. */
data class Gen3LearnsetSelectorEvidence(
    val saveBlock1ByteOffset: Int,
    val mask: Int,
    val zeroTableOffset: Int,
    val nonZeroTableOffset: Int,
    val codeOffset: Int,
)

/**
 * Layout fields published or structurally validated for pokeemerald-expansion ROMs.
 *
 * The expansion deliberately publishes stable table roots and counts, but compile-time options
 * can change the size of its records. Keeping the validated offsets beside the resolved layout
 * avoids treating a particular Battle Theater build as a hard-coded profile.
 */
data class PokeemeraldExpansionMetadata(
    val headerOffset: Int,
    val versionMajor: Int,
    val versionMinor: Int,
    val versionPatch: Int,
    val speciesRecordSize: Int,
    val speciesNameOffset: Int,
    val speciesNameWidth: Int,
    val categoryOffset: Int,
    val nationalDexOffset: Int,
    val heightOffset: Int,
    val weightOffset: Int,
    val descriptionPointerOffset: Int,
    val frontSpritePointerOffset: Int,
    val normalPalettePointerOffset: Int,
    val abilitiesOffset: Int,
    val growthRateOffset: Int,
    val levelUpPointerOffset: Int,
    val teachablePointerOffset: Int,
    val eggMovePointerOffset: Int,
    val evolutionPointerOffset: Int,
    val moveRecordSize: Int,
    val abilityRecordSize: Int,
    val abilityNameWidth: Int,
    val abilityDescriptionPointerOffset: Int,
)

data class RomProfile(
    val name: String,
    val sha256: String,
    val crc32: String,
    val family: EngineFamily,
    val platform: Platform,
    val title: String,
    val gameCode: String? = null,
    val revision: Int,
    val romSize: Int,
    val dexSpeciesCount: Int,
    val internalSpeciesCount: Int,
    val moveCount: Int,
    val tables: ProfileTables,
)

data class ValidationEvidence(
    val compatible: Boolean,
    val validRecords: Int,
    val totalRecords: Int,
    val confidence: Double,
    val reasons: List<String>,
    val offset: Int? = null,
    val recordSize: Int? = null,
    val elementSize: Int? = null,
    val ambiguous: Boolean = false,
    val reviewRecommended: Boolean = false,
    /** Records that cover the feature's semantic domain after structural sentinels are excluded. */
    val coveredRecords: Int? = null,
    /** Records expected by the feature's semantic domain, distinct from raw table slots. */
    val expectedRecords: Int? = null,
    /** Expected semantic records that were declared but could not be fully materialized. */
    val incompleteRecords: Int? = null,
    val format: TableRecordFormat? = null,
)

data class CapabilityEvidence(
    val capability: RomCapability,
    val compatible: Boolean,
    val confidence: Double,
    val offset: Int? = null,
    val count: Int? = null,
    val recordSize: Int? = null,
    val reasons: List<String> = emptyList(),
    val status: CapabilityStatus = if (compatible) CapabilityStatus.AVAILABLE else CapabilityStatus.NOT_FOUND,
    val validRecords: Int? = null,
    val totalRecords: Int? = null,
    val elementSize: Int? = null,
    val reviewStatus: CapabilityReviewStatus = CapabilityReviewStatus.NONE,
    val coveredRecords: Int? = null,
    val expectedRecords: Int? = null,
    val incompleteRecords: Int? = null,
    /** Review intent emitted directly by the validator, excluding aggregate coverage and ambiguity review. */
    val validatorReviewRecommended: Boolean = false,
)

data class ScoreEvidence(
    val category: String,
    val points: Int,
    val maximum: Int,
    val reason: String,
)

data class ParserProbe(
    val family: EngineFamily,
    val score: Int,
    val hardGatePassed: Boolean,
    val anchors: Int,
    val scoreEvidence: List<ScoreEvidence>,
    val capabilities: List<CapabilityEvidence>,
    val profileName: String? = null,
    val exactProfile: Boolean = false,
    val diagnostics: List<String> = emptyList(),
    val resolvedLayout: ResolvedRomLayout? = null,
)

data class ParseResult(
    val header: RomHeader,
    val sha256: String,
    val crc32: String,
    val size: Int,
    val status: SelectionStatus,
    val selectedFamily: EngineFamily?,
    val selectedProfile: String?,
    val runnerUpMargin: Int?,
    val probes: List<ParserProbe>,
    val capabilities: List<CapabilityEvidence>,
    val diagnostics: List<String> = emptyList(),
)
