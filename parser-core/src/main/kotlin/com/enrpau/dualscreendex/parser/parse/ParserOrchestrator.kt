package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ParseResult
import com.enrpau.dualscreendex.parser.model.ParserProbe
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
            capabilities = selection.winner?.capabilities ?: emptyList(),
            diagnostics = when (selection.status) {
                SelectionStatus.AMBIGUOUS -> listOf("top parser did not lead by $minimumMargin points")
                SelectionStatus.UNSUPPORTED -> listOf("no parser passed score and anchor requirements")
                else -> emptyList()
            },
        )
    }

    fun select(probes: List<ParserProbe>): Selection {
        val eligible = probes
            .filter { it.hardGatePassed && it.anchors >= 2 }
            .sortedWith(compareByDescending<ParserProbe> { it.score }.thenBy { it.family.name })
        val top = eligible.firstOrNull() ?: return Selection(SelectionStatus.UNSUPPORTED, null, null)
        if (top.score < minimumScore) return Selection(SelectionStatus.UNSUPPORTED, null, null)
        val runnerUp = eligible.drop(1).firstOrNull()
        val margin = top.score - (runnerUp?.score ?: 0)
        return if (margin >= minimumMargin) {
            Selection(SelectionStatus.SELECTED, top, margin)
        } else {
            Selection(SelectionStatus.AMBIGUOUS, null, margin)
        }
    }

    data class Selection(
        val status: SelectionStatus,
        val winner: ParserProbe?,
        val margin: Int?,
    )
}
