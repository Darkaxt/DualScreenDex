package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class Gen1WorldMapResolverTest {
    @Test fun duplicateCompleteTownMapBankFailsClosedAsAmbiguous() {
        val source = sourceBytes("DUALDEX_POKERED_ROM")
        val duplicateBank = source.copyOfRange(TOWN_MAP_BANK * BANK_BYTES, (TOWN_MAP_BANK + 1) * BANK_BYTES)
        val mutated = RomImage(source + duplicateBank)

        val result = Gen1WorldMapResolver.resolve(
            RomAnalysisSession(mutated, RomHeaderReader.read(mutated)),
            setOf(EXTERNAL_MAP, INTERNAL_MAP),
            PokemonTextCodec.gbEnglish,
        )

        assertTrue("expected ambiguity, got $result", result is WorldMapResolution.Ambiguous)
        result as WorldMapResolution.Ambiguous
        assertTrue(result.stage == "asset-loader")
    }

    private fun sourceBytes(env: String): ByteArray {
        val configured = System.getenv(env)
        assumeTrue("set $env to run this source-derived rejection control", !configured.isNullOrBlank())
        return Files.readAllBytes(Path.of(requireNotNull(configured)))
    }

    private companion object {
        const val BANK_BYTES = 0x4000
        const val TOWN_MAP_BANK = 0x1c
        const val EXTERNAL_MAP = 12
        const val INTERNAL_MAP = 51
    }
}
