package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomCapability

data class CatalogField<T>(
    val status: CapabilityStatus,
    val value: T? = null,
    val reasons: List<String> = emptyList(),
) {
    init {
        require(status == CapabilityStatus.AVAILABLE || value == null) {
            "unavailable catalog fields cannot carry a value"
        }
        require(status != CapabilityStatus.AVAILABLE || value != null) {
            "available catalog fields require a value"
        }
    }

    companion object {
        fun <T> available(value: T): CatalogField<T> = CatalogField(CapabilityStatus.AVAILABLE, value)
        fun <T> notFound(reason: String): CatalogField<T> =
            CatalogField(CapabilityStatus.NOT_FOUND, reasons = listOf(reason))
        fun <T> notApplicable(reason: String): CatalogField<T> =
            CatalogField(CapabilityStatus.NOT_APPLICABLE, reasons = listOf(reason))
    }
}

data class RgbaSprite(
    val width: Int,
    val height: Int,
    val argb: IntArray,
) {
    init {
        require(width > 0 && height > 0) { "sprite dimensions must be positive" }
        require(argb.size == width * height) { "sprite pixel count must equal width * height" }
    }

    override fun equals(other: Any?): Boolean =
        other is RgbaSprite && width == other.width && height == other.height && argb.contentEquals(other.argb)

    override fun hashCode(): Int = 31 * (31 * width + height) + argb.contentHashCode()
}

data class BaseStats(
    val hp: Int,
    val attack: Int,
    val defense: Int,
    val speed: Int,
    val specialAttack: Int,
    val specialDefense: Int,
)

enum class MoveCategory { PHYSICAL, SPECIAL, STATUS, UNKNOWN }

data class SpeciesRecord(
    val id: Int,
    val formId: Int = 0,
    val dexNumber: CatalogField<Int>,
    val name: CatalogField<String>,
    val typeIds: CatalogField<List<Int>>,
    val baseStats: CatalogField<BaseStats>,
    val sprite: CatalogField<RgbaSprite>,
    val description: CatalogField<String> = CatalogField.notFound("description was not materialized"),
    val height: CatalogField<Int> = CatalogField.notFound("height was not materialized"),
    val weight: CatalogField<Int> = CatalogField.notFound("weight was not materialized"),
    val evolutionEdges: CatalogField<List<EvolutionEdge>> = CatalogField.notFound("evolutions were not materialized"),
    val learnset: CatalogField<List<LearnsetEntry>> = CatalogField.notFound("learnset was not materialized"),
    val moveAcquisitions: CatalogField<List<MoveAcquisition>> =
        CatalogField.notFound("non-level move acquisition was not materialized"),
    val abilityIds: CatalogField<List<Int>> = CatalogField.notApplicable("abilities are not part of this engine"),
)

data class MoveRecord(
    val id: Int,
    val name: CatalogField<String>,
    val typeId: CatalogField<Int>,
    val category: CatalogField<MoveCategory>,
    val power: CatalogField<Int>,
    val accuracy: CatalogField<Int>,
    val pp: CatalogField<Int>,
    val priority: CatalogField<Int> = CatalogField.notFound("priority was not materialized"),
    val effectId: CatalogField<Int> = CatalogField.notFound("effect was not materialized"),
    val effectText: CatalogField<String> = CatalogField.notFound("effect text was not materialized"),
)

data class TypeRecord(
    val id: Int,
    val name: CatalogField<String>,
    val presentation: CatalogField<TypePresentation> = CatalogField.notFound("type presentation was not materialized"),
)

data class TypeMatchup(val attackingTypeId: Int, val defendingTypeId: Int, val multiplierPercent: Int)

data class LearnsetEntry(val level: Int, val moveId: Int, val methodId: Int = 0)

data class NormalizedLevelUpMove(
    val moveId: Int,
    val initial: Boolean,
    val levels: List<Int>,
)

data class LearnsetRuleset(
    val id: String,
    val label: String,
    val sourceOffset: Int,
    val confidence: Double,
    val entriesBySpecies: Map<Int, List<LearnsetEntry>>,
    val primary: Boolean = false,
)

enum class MoveAcquisitionMethod { EGG, MACHINE, TUTOR }

data class MoveAcquisition(
    val moveId: Int,
    val method: MoveAcquisitionMethod,
    val sourceId: Int? = null,
)

data class EvolutionEdge(
    val targetSpeciesId: Int,
    val methodId: Int,
    val parameter: Int,
    val raw: ByteArray = byteArrayOf(),
) {
    override fun equals(other: Any?): Boolean =
        other is EvolutionEdge && targetSpeciesId == other.targetSpeciesId && methodId == other.methodId &&
            parameter == other.parameter && raw.contentEquals(other.raw)

    override fun hashCode(): Int =
        31 * (31 * (31 * targetSpeciesId + methodId) + parameter) + raw.contentHashCode()
}

data class AbilityRecord(val id: Int, val name: CatalogField<String>)

data class DescriptionRecord(
    val text: String,
    val height: Int? = null,
    val weight: Int? = null,
    val category: String? = null,
)

data class EncounterSlot(
    val speciesId: Int,
    val minimumLevel: Int,
    val maximumLevel: Int,
    val weight: Int?,
)

data class EncounterArea(
    val id: Int,
    val name: CatalogField<String>,
    val methodId: Int,
    val slots: List<EncounterSlot>,
)

enum class PresentationSource { ROM_EXTRACTED, FAMILY_FALLBACK, ACCESSIBLE_FALLBACK, NEUTRAL }

data class TypePresentation(
    val source: PresentationSource,
    val foregroundArgb: Int,
    val backgroundArgb: Int,
    val borderArgb: Int,
)

data class CaptureBallRecord(
    val id: Int,
    val name: CatalogField<String>,
    val sprite: CatalogField<RgbaSprite>,
    val generic: Boolean = false,
)

data class ParsedCatalog(
    val romSha256: String,
    val family: EngineFamily,
    val platform: Platform,
    val speciesById: Map<Int, SpeciesRecord> = emptyMap(),
    val movesById: Map<Int, MoveRecord> = emptyMap(),
    val typesById: Map<Int, TypeRecord> = emptyMap(),
    val abilitiesById: Map<Int, AbilityRecord> = emptyMap(),
    val typeChart: List<TypeMatchup> = emptyList(),
    val encounterAreas: List<EncounterArea> = emptyList(),
    val captureBallsById: Map<Int, CaptureBallRecord> = emptyMap(),
    val learnsetRulesets: List<LearnsetRuleset> = emptyList(),
    val capabilities: Map<RomCapability, CapabilityEvidence> = emptyMap(),
    val diagnostics: List<String> = emptyList(),
) {
    fun navigableSpecies(): List<SpeciesRecord> = speciesById.values.filter { species ->
        (species.dexNumber.value ?: 0) > 0 && species.name.value?.any(Char::isLetterOrDigit) == true
    }
}
