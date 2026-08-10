package com.enrpau.dualscreendex.parser.model

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

enum class CapabilityStatus { AVAILABLE, NOT_FOUND, NOT_APPLICABLE }

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

data class ResolvedRomLayout(
    val family: EngineFamily,
    val generation: Int,
    val platform: Platform,
    val speciesCount: Int?,
    val moveCount: Int?,
    val tables: ProfileTables,
    val pokeemeraldExpansion: PokeemeraldExpansionMetadata? = null,
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
