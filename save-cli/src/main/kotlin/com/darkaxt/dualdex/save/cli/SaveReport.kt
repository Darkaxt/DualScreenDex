package com.darkaxt.dualdex.save.cli

import com.darkaxt.dualdex.save.SaveCapabilityEvidence

data class SaveCompatibilityReport(val results: List<SaveCompatibilityResult>)

data class SaveCompatibilityResult(
    val romName: String,
    val saveName: String,
    val romSha256: String? = null,
    val saveSha256Before: String,
    val saveSha256After: String,
    val sourceUnchanged: Boolean,
    val family: String? = null,
    val status: String,
    val saveCounter: Long? = null,
    val seen: Int? = null,
    val caught: Int? = null,
    val party: Int? = null,
    val stored: Int? = null,
    val currentArea: String? = null,
    val capabilities: List<SaveCapabilityEvidence> = emptyList(),
    val reasons: List<String> = emptyList(),
)

object SaveReportWriter {
    fun markdown(report: SaveCompatibilityReport): String = buildString {
        appendLine("# DualDex SaveRAM compatibility")
        appendLine()
        appendLine("Private files were read only. The report contains names and aggregate parser evidence, never trainer or raw save data.")
        appendLine()
        appendLine("| ROM | SaveRAM | Result | Revision token | Seen | Caught | Party | Stored | Area | Source |")
        appendLine("| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | --- |")
        report.results.forEach { result ->
            appendLine(
                "| ${cell(result.romName)} | ${cell(result.saveName)} | ${result.status} | " +
                    "${result.saveCounter ?: "-"} | ${result.seen ?: "-"} | ${result.caught ?: "-"} | " +
                    "${result.party ?: "-"} | ${result.stored ?: "-"} | ${result.currentArea ?: "-"} | " +
                    if (result.sourceUnchanged) "Unchanged |" else "CHANGED |",
            )
        }
        report.results.forEach { result ->
            appendLine()
            appendLine("## ${result.saveName}")
            appendLine()
            if (result.capabilities.isNotEmpty()) {
                appendLine("| Capability | Status | Records | Notes |")
                appendLine("| --- | --- | ---: | --- |")
                result.capabilities.forEach { evidence ->
                    appendLine(
                        "| ${evidence.capability} | ${evidence.status} | ${evidence.records} | " +
                            "${cell(evidence.reasons.joinToString("; ").ifBlank { "-" })} |",
                    )
                }
            }
            result.reasons.forEach { appendLine("- ${it.replace("\n", " ")}") }
        }
    }

    private fun cell(value: String): String = value.replace("|", "\\|").replace("\n", " ")
}
