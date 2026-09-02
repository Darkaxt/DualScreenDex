package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.dataset.natures.NatureResolution
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.textUnavailableLanguageManifests
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.RomHeader
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserOrchestratorLanguageAuthorityTest {
    @Test
    fun unknownAndAmbiguousLanguagePreserveStructuralMapResolutionAndNatureMechanics() {
        val rom = RomImage(ByteArray(0x100))
        val session = RomAnalysisSession(rom, RomHeader(Platform.GBA, "TEST", "TEST"))

        textUnavailableLanguageManifests.forEach { manifest ->
            val layout = ResolvedRomLayout(
                family = EngineFamily.EMERALD,
                generation = 3,
                platform = Platform.GBA,
                speciesCount = 0,
                moveCount = 0,
                tables = ProfileTables(),
                languageManifest = manifest,
            )

            val localMaps = ParserOrchestrator.resolveLocalMaps(
                session,
                layout,
                EngineFamily.EMERALD,
                setOf(1),
            )
            val worldMap = ParserOrchestrator.resolveWorldMap(session, layout, setOf(1))
            val natures = ParserOrchestrator.resolveNatures(session, layout)

            assertTrue(localMaps is LocalMapResolution.Unavailable)
            assertFalse((localMaps as LocalMapResolution.Unavailable).stage == "language")
            assertTrue(worldMap is WorldMapResolution.Unavailable)
            assertFalse((worldMap as WorldMapResolution.Unavailable).stage == "language")
            assertTrue(natures is NatureResolution.Unavailable)
            assertFalse((natures as NatureResolution.Unavailable).reason.contains("language codec"))
        }
    }
}
