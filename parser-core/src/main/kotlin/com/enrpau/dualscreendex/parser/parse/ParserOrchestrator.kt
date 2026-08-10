package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ParseResult
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.ParserProbe
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import com.enrpau.dualscreendex.parser.profile.KnownProfiles

object ParserOrchestrator {
    const val minimumScore = 75
    const val minimumMargin = 10

    fun analyze(rom: RomImage): ParseResult {
        val header = RomHeaderReader.read(rom)
        val probes = FamilyParsers.all.map { parser -> parser.probe(rom, header) }
        val exact = KnownProfiles.bySha256(rom.sha256)
        val selection = if (exact != null) {
            val probe = probes.first { it.family == exact.family }
            Selection(SelectionStatus.SELECTED, probe, null)
        } else {
            select(probes)
        }
        return ParseResult(
            header = header,
            sha256 = rom.sha256,
            crc32 = rom.crc32,
            size = rom.size,
            status = selection.status,
            selectedFamily = selection.winner?.family,
            selectedProfile = selection.winner?.profileName,
            runnerUpMargin = selection.margin,
            probes = probes,
            capabilities = resolveCapabilities(selection, probes),
            diagnostics = when (selection.status) {
                SelectionStatus.AMBIGUOUS -> listOf("top parser did not lead by $minimumMargin points")
                SelectionStatus.NO_FAMILY_MATCH -> listOf("no mainline-family parser passed score and anchor requirements")
                else -> emptyList()
            },
        )
    }

    fun select(probes: List<ParserProbe>): Selection {
        val eligible = probes
            .filter { it.hardGatePassed && it.anchors >= 2 }
            .sortedWith(compareByDescending<ParserProbe> { it.score }.thenBy { it.family.name })
        val top = eligible.firstOrNull() ?: return Selection(SelectionStatus.NO_FAMILY_MATCH, null, null)
        if (top.score < minimumScore) return Selection(SelectionStatus.NO_FAMILY_MATCH, null, null)
        val runnerUp = eligible.drop(1).firstOrNull()
        val margin = top.score - (runnerUp?.score ?: 0)
        return if (margin >= minimumMargin) {
            Selection(SelectionStatus.SELECTED, top, margin)
        } else {
            Selection(SelectionStatus.AMBIGUOUS, null, margin)
        }
    }

    fun resolveCapabilities(selection: Selection, probes: List<ParserProbe>): List<CapabilityEvidence> {
        selection.winner?.let { return completeCapabilitySet(it.capabilities) }
        val structurallyCredible = probes.filter { it.hardGatePassed && it.anchors >= 2 }
        return RomCapability.entries.map { capability ->
            val evidence = structurallyCredible.mapNotNull { probe ->
                probe.capabilities.firstOrNull { it.capability == capability }
            }
            val compatible = evidence.filter { it.compatible }
            if (compatible.isNotEmpty()) {
                val locations = compatible.map { Triple(it.offset, it.count, it.recordSize) }.distinct()
                if (locations.size == 1) {
                    compatible.maxBy { it.confidence }
                } else {
                    CapabilityEvidence(
                        capability = capability,
                        compatible = false,
                        confidence = compatible.maxOf { it.confidence },
                        reasons = listOf("conflicting validated locators across candidate families"),
                        status = CapabilityStatus.NOT_FOUND,
                    )
                }
            } else {
                evidence.maxByOrNull { it.confidence }?.copy(
                    compatible = false,
                    reasons = (evidence.maxByOrNull { it.confidence }?.reasons.orEmpty() +
                        "no family-independent compatible evidence").distinct(),
                    status = CapabilityStatus.NOT_FOUND,
                ) ?: unavailable(capability)
            }
        }
    }

    private fun completeCapabilitySet(capabilities: List<CapabilityEvidence>): List<CapabilityEvidence> =
        RomCapability.entries.map { capability ->
            capabilities.firstOrNull { it.capability == capability } ?: unavailable(capability)
        }

    private fun unavailable(capability: RomCapability) = CapabilityEvidence(
        capability = capability,
        compatible = false,
        confidence = 0.0,
        reasons = listOf("no validated locator was found"),
        status = CapabilityStatus.NOT_FOUND,
    )

    data class Selection(
        val status: SelectionStatus,
        val winner: ParserProbe?,
        val margin: Int?,
    )
}
