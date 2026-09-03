package com.enrpau.dualscreendex.parser.language

import com.enrpau.dualscreendex.parser.model.TableLayout
import java.util.Collections

@JvmInline
value class LanguageTag private constructor(val value: String) {
    companion object {
        val ENGLISH = LanguageTag("en")
        val FRENCH = LanguageTag("fr")
        val GERMAN = LanguageTag("de")
        val ITALIAN = LanguageTag("it")
        val SPANISH = LanguageTag("es")
        val JAPANESE = LanguageTag("ja")
        val KOREAN = LanguageTag("ko")

        fun of(value: String): LanguageTag {
            val parts = value.trim().split('-')
            require(parts.isNotEmpty() && parts.first().matches(Regex("[A-Za-z]{2,8}"))) {
                "language tag must start with a 2-8 letter language subtag"
            }
            require(parts.drop(1).all { it.matches(Regex("[A-Za-z0-9]{1,8}")) }) {
                "language tag contains an invalid subtag"
            }
            return LanguageTag(
                parts.mapIndexed { index, part ->
                    when {
                        index == 0 -> part.lowercase()
                        part.length == 4 && part.all(Char::isLetter) ->
                            part.lowercase().replaceFirstChar(Char::uppercaseChar)
                        part.length == 2 && part.all(Char::isLetter) -> part.uppercase()
                        else -> part.lowercase()
                    }
                }.joinToString("-"),
            )
        }
    }
}

enum class LanguageResolutionStatus {
    RESOLVED,
    AMBIGUOUS,
    UNKNOWN,
}

enum class LanguageEvidenceKind {
    COMPILED_CONSUMER,
    TABLE_RELATIONSHIP,
    HEADER_REGION_HINT,
    CODEC_PLAUSIBILITY,
    TERMINATOR_GEOMETRY,
    RETAIL_VALIDATION_CONTROL,
}

data class LanguageEvidence(
    val kind: LanguageEvidenceKind,
    val summary: String,
    val confidence: Int,
) {
    init {
        require(summary.isNotBlank()) { "language evidence summary must not be blank" }
        require(confidence in 0..100) { "language evidence confidence must be in 0..100" }
    }
}

class LocalizedTableLayout(
    speciesNames: TableLayout? = null,
    moveNames: TableLayout? = null,
    descriptions: TableLayout? = null,
    abilities: TableLayout? = null,
    typeNames: TableLayout? = null,
) {
    val speciesNames: TableLayout? = speciesNames?.immutableSnapshot()
    val moveNames: TableLayout? = moveNames?.immutableSnapshot()
    val descriptions: TableLayout? = descriptions?.immutableSnapshot()
    val abilities: TableLayout? = abilities?.immutableSnapshot()
    val typeNames: TableLayout? = typeNames?.immutableSnapshot()

    override fun equals(other: Any?): Boolean = other is LocalizedTableLayout &&
        speciesNames == other.speciesNames &&
        moveNames == other.moveNames &&
        descriptions == other.descriptions &&
        abilities == other.abilities &&
        typeNames == other.typeNames

    override fun hashCode(): Int {
        var result = speciesNames?.hashCode() ?: 0
        result = 31 * result + (moveNames?.hashCode() ?: 0)
        result = 31 * result + (descriptions?.hashCode() ?: 0)
        result = 31 * result + (abilities?.hashCode() ?: 0)
        result = 31 * result + (typeNames?.hashCode() ?: 0)
        return result
    }
}

class RomLanguageProjection(
    val language: LanguageTag,
    val codecId: String,
    val codecVersion: Int,
    localizedTables: LocalizedTableLayout,
    evidence: List<LanguageEvidence>,
    val status: LanguageResolutionStatus,
) {
    val localizedTables = LocalizedTableLayout(
        speciesNames = localizedTables.speciesNames,
        moveNames = localizedTables.moveNames,
        descriptions = localizedTables.descriptions,
        abilities = localizedTables.abilities,
        typeNames = localizedTables.typeNames,
    )
    val evidence: List<LanguageEvidence> = Collections.unmodifiableList(evidence.toList())

    init {
        require(codecId.isNotBlank()) { "language projection codec ID must not be blank" }
        require(codecVersion > 0) { "language projection codec version must be positive" }
    }

    override fun equals(other: Any?): Boolean = other is RomLanguageProjection &&
        language == other.language &&
        codecId == other.codecId &&
        codecVersion == other.codecVersion &&
        localizedTables == other.localizedTables &&
        evidence == other.evidence &&
        status == other.status

    override fun hashCode(): Int {
        var result = language.hashCode()
        result = 31 * result + codecId.hashCode()
        result = 31 * result + codecVersion
        result = 31 * result + localizedTables.hashCode()
        result = 31 * result + evidence.hashCode()
        result = 31 * result + status.hashCode()
        return result
    }
}

class RomLanguageManifest(
    val defaultLanguage: LanguageTag?,
    projections: List<RomLanguageProjection>,
    val status: LanguageResolutionStatus,
    diagnostics: List<String> = emptyList(),
) {
    val projections: List<RomLanguageProjection> = Collections.unmodifiableList(projections.toList())
    val diagnostics: List<String> = Collections.unmodifiableList(diagnostics.toList())

    init {
        require(projections.map(RomLanguageProjection::language).distinct().size == projections.size) {
            "language manifest contains duplicate language projections"
        }
        require(defaultLanguage == null || projections.any { it.language == defaultLanguage }) {
            "language manifest default must reference a projection"
        }
        when (status) {
            LanguageResolutionStatus.RESOLVED -> {
                require(defaultLanguage != null) { "resolved language manifest requires a default language" }
                require(projections.any { it.language == defaultLanguage && it.status == LanguageResolutionStatus.RESOLVED }) {
                    "resolved language manifest default projection must be resolved"
                }
            }
            LanguageResolutionStatus.AMBIGUOUS ->
                require(defaultLanguage == null) { "ambiguous language manifest cannot select a default" }
            LanguageResolutionStatus.UNKNOWN -> {
                require(defaultLanguage == null) { "unknown language manifest cannot select a default" }
                require(projections.isEmpty()) { "unknown language manifest cannot publish projections" }
            }
        }
    }

    fun defaultProjection(): RomLanguageProjection? = projections.firstOrNull { it.language == defaultLanguage }

    fun withDefaultLocalizedTables(localizedTables: LocalizedTableLayout): RomLanguageManifest {
        val selected = defaultLanguage ?: return this
        return RomLanguageManifest(
            defaultLanguage = selected,
            projections = projections.map { projection ->
                if (projection.language != selected) {
                    projection
                } else {
                    RomLanguageProjection(
                        language = projection.language,
                        codecId = projection.codecId,
                        codecVersion = projection.codecVersion,
                        localizedTables = localizedTables,
                        evidence = projection.evidence,
                        status = projection.status,
                    )
                }
            },
            status = status,
            diagnostics = diagnostics,
        )
    }

    override fun equals(other: Any?): Boolean = other is RomLanguageManifest &&
        defaultLanguage == other.defaultLanguage &&
        projections == other.projections &&
        status == other.status &&
        diagnostics == other.diagnostics

    override fun hashCode(): Int {
        var result = defaultLanguage?.hashCode() ?: 0
        result = 31 * result + projections.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + diagnostics.hashCode()
        return result
    }

    companion object {
        val UNKNOWN = RomLanguageManifest(
            defaultLanguage = null,
            projections = emptyList(),
            status = LanguageResolutionStatus.UNKNOWN,
        )
    }
}

private fun TableLayout.immutableSnapshot(): TableLayout = copy(
    banks = Collections.unmodifiableList(banks.toList()),
    pointerOffsets = Collections.unmodifiableList(pointerOffsets.toList()),
    bankRemap = Collections.unmodifiableMap(LinkedHashMap(bankRemap)),
)
