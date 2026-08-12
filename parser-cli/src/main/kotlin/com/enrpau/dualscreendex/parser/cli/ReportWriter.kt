package com.enrpau.dualscreendex.parser.cli

import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.MoveAcquisitionMethod
import com.enrpau.dualscreendex.parser.model.ParseResult
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityReviewStatus
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import com.enrpau.dualscreendex.parser.parse.ParserOrchestrator
import com.google.gson.GsonBuilder
import kotlin.math.round

data class CorpusReport(
    val schemaVersion: Int = 11,
    val minimumParserScore: Int = ParserOrchestrator.minimumScore,
    val minimumRunnerUpMargin: Int = ParserOrchestrator.minimumMargin,
    val roots: List<String>,
    val results: List<CorpusResult>,
)

data class CorpusResult(
    val displayName: String,
    val source: String,
    val archiveEntry: String? = null,
    val durationMillis: Long,
    val result: ParseResult? = null,
    val catalog: CatalogMetrics? = null,
    val samples: CatalogSamples? = null,
    val catalogError: String? = null,
    val persistence: CatalogPersistenceMetrics? = null,
    val persistenceError: String? = null,
    val error: String? = null,
    val dataCompatibility: DataStructureCompatibility = assessDataCompatibility(
        result, catalog, samples, catalogError, error,
    ),
    val compatibilityPercent: Double = calculateCompatibility(result).compatibilityPercent,
    val resolvedFeatureCount: Int = calculateCompatibility(result).resolvedFeatureCount,
    val expectedFeatureCount: Int = calculateCompatibility(result).expectedFeatureCount,
    val manualReviewRequired: Boolean = calculateCompatibility(result).manualReviewRequired ||
        catalogError != null || persistenceError != null || error != null || samples?.referenceErrors?.isNotEmpty() == true,
)

data class RomCompatibilityScore(
    val compatibilityPercent: Double,
    val resolvedFeatureCount: Int,
    val expectedFeatureCount: Int,
    val manualReviewRequired: Boolean,
)

internal fun calculateCompatibility(result: ParseResult?): RomCompatibilityScore {
    val byCapability = result?.capabilities.orEmpty().associateBy { it.capability }
    val applicable = RomCapability.entries.filter { capability ->
        byCapability[capability]?.status != CapabilityStatus.NOT_APPLICABLE
    }
    val coverages = applicable.map { capability -> featureCoverage(byCapability[capability]) }
    val expected = coverages.size
    val resolved = coverages.count { it > 0.0 }
    val percent = if (expected == 0) 100.0 else 100.0 * coverages.sum() / expected
    val manualReview = result?.status == SelectionStatus.AMBIGUOUS || result?.status == SelectionStatus.ERROR || result?.capabilities.orEmpty().any {
        it.status == CapabilityStatus.AMBIGUOUS || it.reviewStatus == CapabilityReviewStatus.MANUAL_REVIEW
    }
    return RomCompatibilityScore(
        compatibilityPercent = round(percent.coerceIn(0.0, 100.0) * 100.0) / 100.0,
        resolvedFeatureCount = resolved,
        expectedFeatureCount = expected,
        manualReviewRequired = manualReview,
    )
}

private fun featureCoverage(evidence: CapabilityEvidence?): Double {
    if (evidence == null) return 0.0
    if (evidence.status == CapabilityStatus.NOT_FOUND || evidence.status == CapabilityStatus.AMBIGUOUS) return 0.0
    if (evidence.status == CapabilityStatus.NOT_APPLICABLE) return 0.0
    val semanticCounts = evidence.coveredRecords?.let { covered ->
        evidence.expectedRecords?.let { expected -> covered to expected }
    }
    val rawCounts = evidence.validRecords?.let { valid ->
        evidence.totalRecords?.let { total -> valid to total }
    }
    val counts = semanticCounts ?: rawCounts
    if (counts != null) {
        val (covered, expected) = counts
        return if (expected > 0) covered.toDouble().div(expected).coerceIn(0.0, 1.0) else 0.0
    }
    return if (evidence.status == CapabilityStatus.AVAILABLE) 1.0 else 0.0
}

enum class DataStructureCompatibility { COMPLETE, PARTIAL, UNRESOLVED, ERROR }

private fun assessDataCompatibility(
    result: ParseResult?,
    catalog: CatalogMetrics?,
    samples: CatalogSamples?,
    catalogError: String?,
    error: String?,
): DataStructureCompatibility {
    if (error != null || result?.status == SelectionStatus.ERROR) return DataStructureCompatibility.ERROR
    if (result == null) return DataStructureCompatibility.UNRESOLVED
    val byCapability = result.capabilities.associateBy { it.capability }
    val allApplicableResolved = RomCapability.entries.all { capability ->
        when (byCapability[capability]?.status) {
            CapabilityStatus.AVAILABLE, CapabilityStatus.NOT_APPLICABLE -> true
            CapabilityStatus.PARTIAL, CapabilityStatus.AMBIGUOUS, CapabilityStatus.NOT_FOUND, null -> false
        }
    }
    if (
        allApplicableResolved && catalog != null && samples != null &&
        samples.referenceErrors.isEmpty() && catalogError == null
    ) {
        return DataStructureCompatibility.COMPLETE
    }
    return if (result.capabilities.any { it.status == CapabilityStatus.AVAILABLE || it.status == CapabilityStatus.PARTIAL } || catalog != null) {
        DataStructureCompatibility.PARTIAL
    } else {
        DataStructureCompatibility.UNRESOLVED
    }
}

data class CatalogSamples(
    val species: List<String>,
    val moves: List<String>,
    val types: List<String>,
    val typeChart: List<String>,
    val evolutions: List<String>,
    val learnsets: List<String>,
    val abilities: List<String>,
    val encounters: List<String>,
    val balls: List<String>,
    val referenceErrors: List<String>,
    val speciesByDex: List<String> = emptyList(),
    val eggMoves: List<String> = emptyList(),
    val machineMoves: List<String> = emptyList(),
    val tutorMoves: List<String> = emptyList(),
) {
    companion object {
        fun from(catalog: ParsedCatalog, limit: Int = 3): CatalogSamples {
            val speciesRecords = catalog.navigableSpecies().sortedBy { it.id }
            val moves = catalog.movesById.values.filter { it.id > 0 }.sortedBy { it.id }
            val types = catalog.typesById.values.sortedBy { it.id }
            val abilities = catalog.abilitiesById.values.filter { it.id > 0 }.sortedBy { it.id }
            val balls = catalog.captureBallsById.values.filter { it.id > 0 }.sortedBy { it.id }
            val referenceErrors = buildList {
                speciesRecords.forEach { species ->
                    species.typeIds.value.orEmpty().forEach { typeId ->
                        if (typeId !in catalog.typesById) add("species ${species.id} references missing type $typeId")
                    }
                    species.abilityIds.value.orEmpty().forEach { abilityId ->
                        if (abilityId > 0 && abilityId !in catalog.abilitiesById) {
                            add("species ${species.id} references missing ability $abilityId")
                        }
                    }
                    species.evolutionEdges.value.orEmpty().forEach { edge ->
                        if (edge.targetSpeciesId !in catalog.speciesById) {
                            add("species ${species.id} evolves to missing species ${edge.targetSpeciesId}")
                        }
                    }
                    species.learnset.value.orEmpty().forEach { entry ->
                        if (entry.moveId !in catalog.movesById) {
                            add("species ${species.id} learns missing move ${entry.moveId}")
                        }
                    }
                }
                moves.forEach { move ->
                    move.typeId.value?.let { typeId ->
                        if (typeId !in catalog.typesById) add("move ${move.id} references missing type $typeId")
                    }
                }
                catalog.typeChart.forEach { matchup ->
                    if (matchup.attackingTypeId !in catalog.typesById) {
                        add("type chart references missing attacking type ${matchup.attackingTypeId}")
                    }
                    if (matchup.defendingTypeId !in catalog.typesById) {
                        add("type chart references missing defending type ${matchup.defendingTypeId}")
                    }
                }
                catalog.encounterAreas.flatMap { it.slots }.forEach { slot ->
                    if (slot.speciesId !in catalog.speciesById) {
                        add("encounter references missing species ${slot.speciesId}")
                    }
                }
            }.distinct().sorted()
            fun formatSpecies(species: com.enrpau.dualscreendex.parser.catalog.SpeciesRecord): String {
                val stats = species.baseStats.value?.let {
                    "${it.hp}/${it.attack}/${it.defense}/${it.speed}/${it.specialAttack}/${it.specialDefense}"
                } ?: "-"
                val sprite = species.sprite.value?.let {
                    "${it.width}x${it.height}#${it.argb.contentHashCode().toUInt().toString(16).uppercase()}"
                } ?: "-"
                return "id=${species.id}; dex=${species.dexNumber.value ?: "-"}; name=${species.name.value ?: "-"}; " +
                    "types=${species.typeIds.value.orEmpty()}; stats=$stats; sprite=$sprite"
            }
            fun acquisitionSamples(method: MoveAcquisitionMethod): List<String> = speciesRecords.asSequence()
                .flatMap { species ->
                    species.moveAcquisitions.value.orEmpty().asSequence()
                        .filter { it.method == method }
                        .map { acquisition ->
                            "species=${species.id}; move=${acquisition.moveId}; source=${acquisition.sourceId ?: "-"}"
                        }
                }
                .take(limit)
                .toList()
            return CatalogSamples(
                species = speciesRecords.take(limit).map(::formatSpecies),
                moves = moves.take(limit).map { move ->
                    "id=${move.id}; name=${move.name.value ?: "-"}; type=${move.typeId.value ?: "-"}; " +
                        "category=${move.category.value ?: "-"}; power=${move.power.value ?: "-"}; " +
                        "accuracy=${move.accuracy.value ?: "-"}; pp=${move.pp.value ?: "-"}; " +
                        "priority=${move.priority.value ?: "-"}; effect=${move.effectId.value ?: "-"}"
                },
                types = types.take(limit).map { type ->
                    val colors = type.presentation.value?.let {
                        "${it.foregroundArgb.toUInt().toString(16)}/${it.backgroundArgb.toUInt().toString(16)}/${it.borderArgb.toUInt().toString(16)}"
                    } ?: "-"
                    "id=${type.id}; name=${type.name.value ?: "-"}; colors=$colors"
                },
                typeChart = catalog.typeChart.take(limit).map {
                    "attack=${it.attackingTypeId}; defend=${it.defendingTypeId}; multiplier=${it.multiplierPercent}%"
                },
                evolutions = speciesRecords.asSequence().flatMap { species ->
                    species.evolutionEdges.value.orEmpty().asSequence().map { edge ->
                        "species=${species.id}; target=${edge.targetSpeciesId}; method=${edge.methodId}; parameter=${edge.parameter}"
                    }
                }.take(limit).toList(),
                learnsets = speciesRecords.asSequence().flatMap { species ->
                    species.learnset.value.orEmpty().asSequence().map { entry ->
                        "species=${species.id}; level=${entry.level}; move=${entry.moveId}; method=${entry.methodId}"
                    }
                }.take(limit).toList(),
                abilities = abilities.take(limit).map { ability ->
                    val mechanics = ability.mechanics.value.orEmpty().joinToString(",") { "${it.label}=${it.value}" }
                    "id=${ability.id}; name=${ability.name.value ?: "-"}; mechanics=${mechanics.ifBlank { "-" }}"
                },
                encounters = catalog.encounterAreas.asSequence().flatMap { area ->
                    area.slots.asSequence().map { slot ->
                        "area=${area.id}; method=${area.methodId}; windows=${area.windows}; species=${slot.speciesId}; " +
                            "levels=${slot.minimumLevel}-${slot.maximumLevel}; weight=${slot.weight ?: "-"}"
                    }
                }.take(limit).toList(),
                balls = balls.take(limit).map { ball ->
                    val sprite = ball.sprite.value?.let {
                        "${it.width}x${it.height}#${it.argb.contentHashCode().toUInt().toString(16).uppercase()}"
                    } ?: "-"
                    "id=${ball.id}; name=${ball.name.value ?: "-"}; sprite=$sprite"
                },
                referenceErrors = referenceErrors,
                speciesByDex = speciesRecords
                    .sortedWith(compareBy({ it.dexNumber.value ?: Int.MAX_VALUE }, { it.id }))
                    .take(limit)
                    .map(::formatSpecies),
                eggMoves = acquisitionSamples(MoveAcquisitionMethod.EGG),
                machineMoves = acquisitionSamples(MoveAcquisitionMethod.MACHINE),
                tutorMoves = acquisitionSamples(MoveAcquisitionMethod.TUTOR),
            )
        }
    }
}

data class CatalogPersistenceMetrics(
    val fileName: String,
    val bytes: Long,
    val writeMillis: Long,
    val reopenMillis: Long,
    val sections: Int,
)

data class CatalogRulesetSelectorMetrics(
    val saveBlock1ByteOffset: Int,
    val mask: Int,
    val expectedValue: Int,
)

data class CatalogRulesetMetrics(
    val id: String,
    val label: String,
    val sourceOffset: Int,
    val confidence: Double,
    val primary: Boolean,
    val levelUpSelector: CatalogRulesetSelectorMetrics? = null,
)

data class CatalogMetrics(
    val species: Int,
    val namedSpecies: Int,
    val speciesWithStats: Int,
    val speciesWithSprites: Int,
    val speciesWithDescriptions: Int,
    val evolutionEdges: Int,
    val learnsetEntries: Int,
    val learnsetRulesets: Int,
    val moves: Int,
    val movesWithDetails: Int,
    val movesWithDescriptions: Int,
    val eggMoveLinks: Int,
    val machineMoveLinks: Int,
    val tutorMoveLinks: Int,
    val types: Int,
    val typeMatchups: Int,
    val abilities: Int,
    val abilitiesWithDescriptions: Int,
    val abilitiesWithMechanics: Int,
    val captureBalls: Int,
    val encounterAreas: Int = 0,
    val rulesetDetails: List<CatalogRulesetMetrics> = emptyList(),
) {
    companion object {
        fun from(catalog: ParsedCatalog): CatalogMetrics {
            val species = catalog.navigableSpecies()
            val moves = catalog.movesById.values.filter { it.id > 0 }
            val acquisitions = species.flatMap { it.moveAcquisitions.value.orEmpty() }
            val abilities = catalog.abilitiesById.values.filter { ability ->
                ability.id > 0 && ability.name.value?.isNotBlank() == true
            }
            return CatalogMetrics(
            species = species.size,
            namedSpecies = species.count { it.name.status == CapabilityStatus.AVAILABLE },
            speciesWithStats = species.count { it.baseStats.status == CapabilityStatus.AVAILABLE },
            speciesWithSprites = species.count { it.sprite.status == CapabilityStatus.AVAILABLE },
            speciesWithDescriptions = species.count { it.description.status == CapabilityStatus.AVAILABLE },
            evolutionEdges = species.sumOf { it.evolutionEdges.value?.size ?: 0 },
            learnsetEntries = species.sumOf { it.learnset.value?.size ?: 0 },
            learnsetRulesets = catalog.learnsetRulesets.size,
            moves = moves.size,
            movesWithDetails = moves.count { move ->
                move.typeId.status == CapabilityStatus.AVAILABLE &&
                    move.power.status == CapabilityStatus.AVAILABLE &&
                    move.accuracy.status == CapabilityStatus.AVAILABLE &&
                    move.pp.status == CapabilityStatus.AVAILABLE
            },
            movesWithDescriptions = moves.count { it.effectText.status == CapabilityStatus.AVAILABLE },
            eggMoveLinks = acquisitions.count { it.method == MoveAcquisitionMethod.EGG },
            machineMoveLinks = acquisitions.count { it.method == MoveAcquisitionMethod.MACHINE },
            tutorMoveLinks = acquisitions.count { it.method == MoveAcquisitionMethod.TUTOR },
            types = catalog.typesById.size,
            typeMatchups = catalog.typeChart.size,
            abilities = abilities.size,
            abilitiesWithDescriptions = abilities.count { it.description.status == CapabilityStatus.AVAILABLE },
            abilitiesWithMechanics = abilities.count { it.mechanics.status == CapabilityStatus.AVAILABLE },
            captureBalls = catalog.captureBallsById.values.count { it.sprite.status == CapabilityStatus.AVAILABLE },
            encounterAreas = catalog.encounterAreas.size,
            rulesetDetails = catalog.learnsetRulesets.map { ruleset ->
                CatalogRulesetMetrics(
                    id = ruleset.id,
                    label = ruleset.label,
                    sourceOffset = ruleset.sourceOffset,
                    confidence = ruleset.confidence,
                    primary = ruleset.primary,
                    levelUpSelector = ruleset.levelUpSelector?.let { selector ->
                        CatalogRulesetSelectorMetrics(
                            saveBlock1ByteOffset = selector.saveBlock1ByteOffset,
                            mask = selector.mask,
                            expectedValue = selector.expectedValue,
                        )
                    },
                )
            },
        )
        }
    }
}

object ReportWriter {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val extendedCapabilities = setOf(
        RomCapability.MOVE_DESCRIPTIONS,
        RomCapability.EGG_MOVES,
        RomCapability.MACHINE_MOVES,
        RomCapability.TUTOR_MOVES,
        RomCapability.ABILITY_DESCRIPTIONS,
        RomCapability.ABILITY_MECHANICS,
    )
    private val coreCapabilities = RomCapability.entries.filterNot(extendedCapabilities::contains)

    fun json(report: CorpusReport): String = gson.toJson(
        report.copy(roots = report.roots.map(::publicRootLabel).distinct()),
    ) + "\n"

    fun markdown(report: CorpusReport): String = buildString {
        val exact = report.results.count { entry -> entry.result?.probes?.any { it.exactProfile } == true }
        val fullyCompatible = report.results.count { it.compatibilityPercent == 100.0 && !it.manualReviewRequired }
        val belowFullCompatibility = report.results.count { it.compatibilityPercent < 100.0 }
        val manualReview = report.results.count { it.manualReviewRequired }
        val errors = report.results.count { it.dataCompatibility == DataStructureCompatibility.ERROR }
        val persisted = report.results.count { it.persistence != null }
        val persistenceErrors = report.results.count { it.persistenceError != null }
        val referenceErrors = report.results.sumOf { it.samples?.referenceErrors?.size ?: 0 }
        fun complete(entry: CorpusResult, capabilities: Iterable<RomCapability>) =
            entry.result != null && entry.catalog != null && entry.samples?.referenceErrors?.isEmpty() != false && RomCapability.entries.all { capability ->
                if (capability !in capabilities) return@all true
                when (entry.result.capabilities.firstOrNull { it.capability == capability }?.status) {
                    CapabilityStatus.AVAILABLE, CapabilityStatus.NOT_APPLICABLE -> true
                    CapabilityStatus.PARTIAL, CapabilityStatus.AMBIGUOUS, CapabilityStatus.NOT_FOUND, null -> false
                }
            }
        val completeCore = report.results.count { complete(it, coreCapabilities) }
        val completeExtended = report.results.count { complete(it, RomCapability.entries) }

        appendLine("# DualDex ROM parser compatibility")
        appendLine()
        appendLine("This report contains structural parser evidence only. It contains no decoded Pokédex text, sprites, or ROM bytes.")
        appendLine()
        appendLine("## Summary")
        appendLine()
        appendLine("- Inputs evaluated: ${report.results.size}")
        appendLine("- ROMs at 100% compatibility: $fullyCompatible")
        appendLine("- ROMs below 100% compatibility: $belowFullCompatibility")
        appendLine("- ROMs requiring manual review: $manualReview")
        appendLine("- Exact official ROM profiles encountered: $exact")
        appendLine("- Complete core catalogs: $completeCore")
        appendLine("- Complete for every applicable extended dataset: $completeExtended")
        appendLine("- Read/parse errors: $errors")
        appendLine("- Persisted and reopened SQLite catalogs: $persisted")
        appendLine("- SQLite persistence errors: $persistenceErrors")
        appendLine("- Decoded cross-reference errors: $referenceErrors")
        appendLine("- Family scores are internal layout-routing evidence and do not determine ROM data compatibility")
        appendLine()
        appendNumericOutcomes(report)
        appendLine()
        appendCatalogCounts(report)
        appendLine()
        appendPersistence(report)
        appendLine()
        appendLine("## Compatibility matrix")
        appendLine()
        appendLine("The percentage is the mean semantic coverage of every applicable feature. Not-applicable features are excluded from each ROM's denominator.")
        appendLine()
        appendLine("| ROM | Compatibility | Resolved features | Expected features | Manual review | Routing status | Family hint | Profile hint | Routing score |")
        appendLine("| --- | ---: | ---: | ---: | :---: | --- | --- | --- | ---: |")
        report.results.forEach { entry ->
            val parsed = entry.result
            val selectedProbe = parsed?.probes?.firstOrNull { it.family == parsed.selectedFamily }
            appendLine(
                "| ${cell(entry.displayName)} | ${formatPercent(entry.compatibilityPercent)} | ${entry.resolvedFeatureCount} | " +
                    "${entry.expectedFeatureCount} | ${if (entry.manualReviewRequired) "yes" else "no"} | " +
                    "${parsed?.status ?: "ERROR"} | ${parsed?.selectedFamily ?: "-"} | " +
                    "${cell(parsed?.selectedProfile ?: "-")} | ${selectedProbe?.score ?: "-"} |",
            )
        }

        appendLine()
        appendLine("## Per-ROM evidence")
        report.results.forEach { entry ->
            appendLine()
            appendLine("### ${heading(entry.displayName)}")
            appendLine()
            if (entry.error != null) {
                appendLine("Error: ${entry.error}")
                return@forEach
            }
            val parsed = entry.result ?: return@forEach
            appendLine("- Identity: `${parsed.sha256}` (SHA-256), `${parsed.crc32}` (CRC32), ${parsed.size} bytes")
            appendLine("- Compatibility: ${formatPercent(entry.compatibilityPercent)}; resolved ${entry.resolvedFeatureCount}/${entry.expectedFeatureCount} applicable features")
            appendLine("- Manual review: ${if (entry.manualReviewRequired) "required" else "not required"}")
            appendLine("- Legacy data compatibility: ${entry.dataCompatibility}")
            appendLine("- Header: ${parsed.header.platform}, title `${cell(parsed.header.title)}`, code `${parsed.header.gameCode ?: "-"}`, revision ${parsed.header.revision}")
            appendLine("- Decision: ${parsed.status}; family ${parsed.selectedFamily ?: "-"}; profile ${parsed.selectedProfile ?: "-"}; margin ${parsed.runnerUpMargin ?: "-"}")
            if (parsed.diagnostics.isNotEmpty()) appendLine("- Diagnostics: ${parsed.diagnostics.joinToString("; ")}")
            if (entry.catalogError != null) appendLine("- Catalog materialization error: ${entry.catalogError}")
            appendLine("- Candidate scores: ${parsed.probes.joinToString(", ") { "${it.family}=${it.score}/${it.anchors} anchors" }}")
            appendLine("- Capabilities:")
            parsed.capabilities.forEach { evidence ->
                val location = listOfNotNull(
                    evidence.offset?.let { "offset=0x${it.toString(16).uppercase()}" },
                    evidence.count?.let { "count=$it" },
                    evidence.recordSize?.let { "recordSize=$it" },
                ).joinToString(", ")
                val reason = evidence.reasons.joinToString("; ")
                val status = when (evidence.status) {
                    CapabilityStatus.AVAILABLE -> "available"
                    CapabilityStatus.PARTIAL -> "partial"
                    CapabilityStatus.AMBIGUOUS -> "ambiguous"
                    CapabilityStatus.NOT_FOUND -> "not found"
                    CapabilityStatus.NOT_APPLICABLE -> "not applicable"
                }
                appendLine("  - ${evidence.capability}: $status; confidence=${formatConfidence(evidence.confidence)}${if (location.isEmpty()) "" else "; $location"}${if (reason.isEmpty()) "" else "; $reason"}")
            }
            entry.samples?.let { samples ->
                appendLine("- Leading decoded records:")
                appendSampleGroup("Species", samples.species)
                appendSampleGroup("Moves", samples.moves)
                appendSampleGroup("Types", samples.types)
                appendSampleGroup("Type chart", samples.typeChart)
                appendSampleGroup("Evolutions", samples.evolutions)
                appendSampleGroup("Learnsets", samples.learnsets)
                appendSampleGroup("Abilities", samples.abilities)
                appendSampleGroup("Encounters", samples.encounters)
                appendSampleGroup("Balls", samples.balls)
                if (samples.referenceErrors.isEmpty()) {
                    appendLine("  - Cross-references: validated")
                } else {
                    appendLine("  - Cross-reference errors: ${samples.referenceErrors.joinToString("; ")}")
                }
            }
        }
    }

    private fun StringBuilder.appendSampleGroup(label: String, values: List<String>) {
        appendLine("  - $label: ${if (values.isEmpty()) "-" else values.joinToString(" | ") { "`${it.replace("`", "'")}`" }}")
    }

    private fun formatConfidence(value: Double): String = String.format(java.util.Locale.ROOT, "%.3f", value)

    private fun formatPercent(value: Double): String = String.format(java.util.Locale.ROOT, "%.2f%%", value)

    private fun publicRootLabel(value: String): String = value
        .replace('\\', '/')
        .trimEnd('/')
        .substringAfterLast('/')
        .ifBlank { "ROM library" }

    private fun cell(value: String): String = value.replace("|", "\\|").replace("\r", " ").replace("\n", " ")

    private fun heading(value: String): String = value.replace("\r", " ").replace("\n", " ")

    private fun StringBuilder.appendNumericOutcomes(report: CorpusReport) {
        val complete = report.results.filter { it.compatibilityPercent == 100.0 && !it.manualReviewRequired }
        val incomplete = report.results.filter { it.compatibilityPercent < 100.0 || it.manualReviewRequired }
        val errors = report.results.filter { it.error != null || it.result?.status == SelectionStatus.ERROR }

        appendLine("## Named outcomes")
        appendLine()
        appendNamedGroup("100% compatibility", complete) { "all ${it.expectedFeatureCount} applicable features fully covered" }
        if (incomplete.isNotEmpty()) appendNamedGroup("Below 100% or manual review", incomplete) {
            "${formatPercent(it.compatibilityPercent)}; ${it.resolvedFeatureCount}/${it.expectedFeatureCount} features resolved" +
                if (it.manualReviewRequired) "; manual review required" else ""
        }
        if (errors.isNotEmpty()) appendNamedGroup("Read or parse errors", errors) { it.error ?: "parser error" }
    }

    private fun StringBuilder.appendCatalogCounts(report: CorpusReport) {
        appendLine("## Materialized catalog counts")
        appendLine()
        appendLine("Counts prove records were decoded and joined; the report intentionally contains no copyrighted ROM text or pixels.")
        appendLine()
        appendLine("| ROM | Species | Named | Stats | Sprites | Dex text | Evolutions | Learnsets | Rulesets | Moves | Move data | Move text | Egg links | Machine links | Tutor links | Types | Matchups | Abilities | Ability text | Ability values | Balls | Areas |")
        appendLine("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
        report.results.forEach { entry ->
            val value = entry.catalog
            if (value == null) {
                appendLine("| ${cell(entry.displayName)} | - | - | - | - | - | - | - | - | - | - | - | - | - | - | - | - | - | - | - | - | - |")
            } else {
                appendLine(
                    "| ${cell(entry.displayName)} | ${value.species} | ${value.namedSpecies} | ${value.speciesWithStats} | " +
                        "${value.speciesWithSprites} | ${value.speciesWithDescriptions} | ${value.evolutionEdges} | " +
                        "${value.learnsetEntries} | ${value.learnsetRulesets} | ${value.moves} | ${value.movesWithDetails} | " +
                        "${value.movesWithDescriptions} | ${value.eggMoveLinks} | ${value.machineMoveLinks} | " +
                        "${value.tutorMoveLinks} | ${value.types} | ${value.typeMatchups} | ${value.abilities} | " +
                        "${value.abilitiesWithDescriptions} | ${value.abilitiesWithMechanics} | ${value.captureBalls} | ${value.encounterAreas} |",
                )
            }
        }
    }

    private fun StringBuilder.appendPersistence(report: CorpusReport) {
        appendLine("## SQLite catalog persistence")
        appendLine()
        appendLine("Each row is a complete SHA-256-keyed database that was written, closed, reopened, and decoded back into the production catalog model.")
        appendLine()
        appendLine("| ROM | SHA-256 prefix | Bytes | Sections | Write ms | Reopen ms |")
        appendLine("| --- | --- | ---: | ---: | ---: | ---: |")
        report.results.forEach { entry ->
            val value = entry.persistence
            if (value == null) {
                appendLine("| ${cell(entry.displayName)} | ${entry.persistenceError?.let(::cell) ?: "-"} | - | - | - | - |")
            } else {
                appendLine(
                    "| ${cell(entry.displayName)} | ${value.fileName.substringBefore('.').take(12)} | ${value.bytes} | " +
                        "${value.sections} | ${value.writeMillis} | ${value.reopenMillis} |",
                )
            }
        }
    }

    private fun StringBuilder.appendNamedGroup(
        title: String,
        entries: List<CorpusResult>,
        suffix: (CorpusResult) -> String,
    ) {
        appendLine("### $title (${entries.size})")
        appendLine()
        if (entries.isEmpty()) {
            appendLine("- None")
        } else {
            entries.forEach { appendLine("- ${heading(it.displayName)} — ${suffix(it)}") }
        }
        appendLine()
    }
}
