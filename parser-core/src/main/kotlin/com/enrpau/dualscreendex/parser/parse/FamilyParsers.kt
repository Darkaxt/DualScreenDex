package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.family.EngineFamilyDefinitions
import com.enrpau.dualscreendex.parser.family.FamilyProbeCoordinator
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.ParserProbe
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.profile.KnownProfiles

interface FamilyParser {
    val family: EngineFamily
    fun probe(session: RomAnalysisSession): ParserProbe

    fun probe(rom: RomImage, header: RomHeader = RomHeaderReader.read(rom)): ParserProbe = probe(
        RomAnalysisSession(
            rom = rom,
            header = header,
            exactProfile = KnownProfiles.bySha256(rom.sha256),
        ),
    )
}

/** Compatibility API for existing focused tests and callers during the phased migration. */
object FamilyParsers {
    private val coordinator = FamilyProbeCoordinator()

    val all: List<FamilyParser> = EngineFamilyDefinitions.all.map { definition ->
        object : FamilyParser {
            override val family: EngineFamily = definition.family
            override fun probe(session: RomAnalysisSession): ParserProbe = coordinator.probe(session, definition)
        }
    }
}
