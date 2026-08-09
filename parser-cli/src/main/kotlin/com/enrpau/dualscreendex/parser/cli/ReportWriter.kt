package com.enrpau.dualscreendex.parser.cli

import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.model.ParseResult
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import com.enrpau.dualscreendex.parser.parse.ParserOrchestrator
import com.google.gson.GsonBuilder

data class CorpusReport(
    val schemaVersion: Int = 4,
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
    val catalogError: String? = null,
    val error: String? = null,
)

data class CatalogMetrics(
    val species: Int,
    val namedSpecies: Int,
    val speciesWithStats: Int,
    val speciesWithSprites: Int,
    val speciesWithDescriptions: Int,
    val evolutionEdges: Int,
    val learnsetEntries: Int,
    val moves: Int,
    val movesWithDetails: Int,
    val types: Int,
    val typeMatchups: Int,
    val abilities: Int,
    val captureBalls: Int,
) {
    companion object {
        fun from(catalog: ParsedCatalog) = CatalogMetrics(
            species = catalog.speciesById.size,
            namedSpecies = catalog.speciesById.values.count { it.name.status == CapabilityStatus.AVAILABLE },
            speciesWithStats = catalog.speciesById.values.count { it.baseStats.status == CapabilityStatus.AVAILABLE },
            speciesWithSprites = catalog.speciesById.values.count { it.sprite.status == CapabilityStatus.AVAILABLE },
            speciesWithDescriptions = catalog.speciesById.values.count { it.description.status == CapabilityStatus.AVAILABLE },
            evolutionEdges = catalog.speciesById.values.sumOf { it.evolutionEdges.value?.size ?: 0 },
            learnsetEntries = catalog.speciesById.values.sumOf { it.learnset.value?.size ?: 0 },
            moves = catalog.movesById.size,
            movesWithDetails = catalog.movesById.values.count { move ->
                move.typeId.status == CapabilityStatus.AVAILABLE &&
                    move.power.status == CapabilityStatus.AVAILABLE &&
                    move.accuracy.status == CapabilityStatus.AVAILABLE &&
                    move.pp.status == CapabilityStatus.AVAILABLE
            },
            types = catalog.typesById.size,
            typeMatchups = catalog.typeChart.size,
            abilities = catalog.abilitiesById.size,
            captureBalls = catalog.captureBallsById.values.count { it.sprite.status == CapabilityStatus.AVAILABLE },
        )
    }
}

object ReportWriter {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    fun json(report: CorpusReport): String = gson.toJson(report) + "\n"

    fun markdown(report: CorpusReport): String = buildString {
        val selected = report.results.count { it.result?.status == SelectionStatus.SELECTED }
        val exact = report.results.count { entry -> entry.result?.probes?.any { it.exactProfile } == true }
        val derived = report.results.count { entry ->
            entry.result?.status == SelectionStatus.SELECTED && entry.result.probes.none { it.exactProfile }
        }
        val ambiguous = report.results.count { it.result?.status == SelectionStatus.AMBIGUOUS }
        val noFamilyMatch = report.results.count { it.result?.status == SelectionStatus.NO_FAMILY_MATCH }
        val errors = report.results.count { it.error != null || it.result?.status == SelectionStatus.ERROR }
        val completeStatic = report.results.count { entry ->
            entry.result?.status == SelectionStatus.SELECTED && RomCapability.entries.all { capability ->
                when (entry.result.capabilities.firstOrNull { it.capability == capability }?.status) {
                    CapabilityStatus.AVAILABLE, CapabilityStatus.NOT_APPLICABLE -> true
                    CapabilityStatus.NOT_FOUND, null -> false
                }
            }
        }
        val partialStatic = selected - completeStatic

        appendLine("# DualDex ROM parser compatibility")
        appendLine()
        appendLine("This report contains structural parser evidence only. It contains no decoded Pokédex text, sprites, or ROM bytes.")
        appendLine()
        appendLine("## Summary")
        appendLine()
        appendLine("- Inputs evaluated: ${report.results.size}")
        appendLine("- Selected: $selected ($exact exact official, $derived structurally selected derivatives)")
        appendLine("- Complete for all applicable static datasets: $completeStatic")
        appendLine("- Selected with partial static datasets: $partialStatic")
        appendLine("- Ambiguous: $ambiguous")
        if (noFamilyMatch > 0) appendLine("- No mainline-family match: $noFamilyMatch")
        appendLine("- Read/parse errors: $errors")
        appendLine("- Selection rule: score >= ${report.minimumParserScore}, runner-up margin >= ${report.minimumRunnerUpMargin}, and at least two validated anchors")
        appendLine()
        appendNamedOutcomes(report)
        appendLine()
        appendCatalogCounts(report)
        appendLine()
        appendLine("## Capability matrix")
        appendLine()
        appendLine("- `yes` = found and validated")
        appendLine("- `N/F` = applicable but not found or validated")
        appendLine("- `N/A` = not applicable to that engine")
        appendLine()
        appendLine("| ROM | Status | Family | Profile | Ancestry score | Catalog | Names | Types | Type chart | Stats | Sprites | Descriptions | Evolutions | Moves | Move data | Learnsets | Abilities |")
        appendLine("| --- | --- | --- | --- | ---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |")
        report.results.forEach { entry ->
            val parsed = entry.result
            val selectedProbe = parsed?.probes?.firstOrNull { it.family == parsed.selectedFamily }
            fun capability(value: RomCapability): String = when (
                parsed?.capabilities?.firstOrNull { it.capability == value }?.status
            ) {
                CapabilityStatus.AVAILABLE -> "yes"
                CapabilityStatus.NOT_APPLICABLE -> "N/A"
                CapabilityStatus.NOT_FOUND, null -> "N/F"
            }
            appendLine(
                "| ${cell(entry.displayName)} | ${parsed?.status ?: "ERROR"} | ${parsed?.selectedFamily ?: "-"} | " +
                    "${cell(parsed?.selectedProfile ?: "-")} | ${selectedProbe?.score ?: "-"} | " +
                    "${capability(RomCapability.SPECIES_CATALOG)} | ${capability(RomCapability.SPECIES_NAMES)} | " +
                    "${capability(RomCapability.SPECIES_TYPES)} | ${capability(RomCapability.TYPE_CHART)} | " +
                    "${capability(RomCapability.BASE_STATS)} | ${capability(RomCapability.SPRITES)} | " +
                    "${capability(RomCapability.POKEDEX_DESCRIPTIONS)} | ${capability(RomCapability.EVOLUTIONS)} | " +
                    "${capability(RomCapability.MOVE_CATALOG)} | ${capability(RomCapability.MOVE_DETAILS)} | " +
                    "${capability(RomCapability.LEARNSETS)} | ${capability(RomCapability.ABILITIES)} |",
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
                    CapabilityStatus.NOT_FOUND -> "not found"
                    CapabilityStatus.NOT_APPLICABLE -> "not applicable"
                }
                appendLine("  - ${evidence.capability}: $status; confidence=${formatConfidence(evidence.confidence)}${if (location.isEmpty()) "" else "; $location"}${if (reason.isEmpty()) "" else "; $reason"}")
            }
        }
    }

    private fun formatConfidence(value: Double): String = String.format(java.util.Locale.ROOT, "%.3f", value)

    private fun cell(value: String): String = value.replace("|", "\\|").replace("\r", " ").replace("\n", " ")

    private fun heading(value: String): String = value.replace("\r", " ").replace("\n", " ")

    private fun StringBuilder.appendNamedOutcomes(report: CorpusReport) {
        val exact = report.results.filter { entry ->
            entry.result?.status == SelectionStatus.SELECTED && entry.result.probes.any { it.exactProfile }
        }
        val derived = report.results.filter { entry ->
            entry.result?.status == SelectionStatus.SELECTED && entry.result.probes.none { it.exactProfile }
        }
        val noFamily = report.results.filter { it.result?.status == SelectionStatus.NO_FAMILY_MATCH }
        val ambiguous = report.results.filter { it.result?.status == SelectionStatus.AMBIGUOUS }
        val errors = report.results.filter { it.error != null || it.result?.status == SelectionStatus.ERROR }

        appendLine("## Named outcomes")
        appendLine()
        appendNamedGroup("Exact official matches", exact) { entry -> entry.result?.selectedFamily?.name ?: "-" }
        appendNamedGroup("Structurally selected derivatives", derived) { entry -> entry.result?.selectedFamily?.name ?: "-" }
        if (noFamily.isNotEmpty()) appendNamedGroup("No mainline-family match", noFamily) { "capability flags retained below" }
        if (ambiguous.isNotEmpty()) appendNamedGroup("Ambiguous ancestry", ambiguous) { "no family selected" }
        if (errors.isNotEmpty()) appendNamedGroup("Read or parse errors", errors) { it.error ?: "parser error" }
    }

    private fun StringBuilder.appendCatalogCounts(report: CorpusReport) {
        appendLine("## Materialized catalog counts")
        appendLine()
        appendLine("Counts prove records were decoded and joined; the report intentionally contains no copyrighted ROM text or pixels.")
        appendLine()
        appendLine("| ROM | Species | Named | Stats | Sprites | Descriptions | Evolutions | Learnsets | Moves | Move details | Types | Matchups | Abilities | Balls |")
        appendLine("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
        report.results.forEach { entry ->
            val value = entry.catalog
            if (value == null) {
                appendLine("| ${cell(entry.displayName)} | - | - | - | - | - | - | - | - | - | - | - | - | - |")
            } else {
                appendLine(
                    "| ${cell(entry.displayName)} | ${value.species} | ${value.namedSpecies} | ${value.speciesWithStats} | " +
                        "${value.speciesWithSprites} | ${value.speciesWithDescriptions} | ${value.evolutionEdges} | " +
                        "${value.learnsetEntries} | ${value.moves} | ${value.movesWithDetails} | ${value.types} | " +
                        "${value.typeMatchups} | ${value.abilities} | ${value.captureBalls} |",
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
