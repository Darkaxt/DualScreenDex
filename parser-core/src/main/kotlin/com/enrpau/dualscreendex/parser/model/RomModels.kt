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
)

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
