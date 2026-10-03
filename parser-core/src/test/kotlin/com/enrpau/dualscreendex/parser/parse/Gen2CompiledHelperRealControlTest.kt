package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.TypeSemanticRole
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.text.Gen2PlainNameCodec
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class Gen2CompiledHelperRealControlTest {
    @Test fun polishedCrystalHasCompleteModernHelperAuthority() {
        verifyHelpers(
            "DUALDEX_POLISHED_CRYSTAL_ROM",
            "469d08907d0ef474d174d87d84d25e108b86ca481131b41fa291bd8aae97c29b",
            255,
        )
    }

    @Test fun crystalInheritanceHasIndependentModernHelperAuthority() {
        verifyHelpers(
            "DUALDEX_CRYSTAL_INHERITANCE_ROM",
            "8a7f102ec2f04572bb0bcf95e44279ea5f3e113f73ce217f499c514c14dbd4a4",
            127,
        )
    }

    private fun verifyHelpers(environment: String, expectedSha: String, maximumBank: Int) {
        val configured = System.getenv(environment)
        assumeTrue("set $environment to run this real helper control", !configured.isNullOrBlank())
        val rom = RomImage(Files.readAllBytes(Path.of(requireNotNull(configured))))
        assertEquals(expectedSha, rom.sha256)
        val copySites = (0 until minOf(0x4000, rom.size) - 3).mapNotNull { offset ->
            GbCompiledFarCopy.resolve(rom, offset)?.let { offset to it }
        }
        assertEquals("complete far-copy routine must be unambiguous", 1, copySites.size)
        assertEquals(maximumBank, copySites.single().second.maximumBank)
        val copyAddress = copySites.single().first
        val linkedCopies = (0 until 0x4000 - 3).filter { offset ->
            rom.u8(offset) == 0xCD && rom.u16le(offset + 1) == copyAddress
        }
        assertTrue("multiple compiled callers must use the proven copy helper", linkedCopies.size >= 2)
        val multiplyVector = 0x18
        val multiplyAddress = if (rom.u8(multiplyVector) == 0xC3) {
            rom.u16le(multiplyVector + 1)
        } else {
            multiplyVector
        }
        assertTrue(GbCompiledBankCalls.repeatedAdd(rom, multiplyAddress))
        val codec = if (maximumBank == 255) Gen2PlainNameCodec.english53 else Gen2PlainNameCodec.english53Ngrams
        val typeNames = requireNotNull(CompiledTypeNameResolver.resolve(
            RomAnalysisSession(rom, RomHeaderReader.read(rom)), 2, codec,
        ))
        assertEquals(if (maximumBank == 255) 1 else 2, typeNames.recordSize)
        val types = requireNotNull(CompiledTypeNameResolver.decode(rom, 2, typeNames, codec))
        assertEquals(19, types.size)
        assertEquals(TypeSemanticRole.entries.toSet(), types.values.map { it.semanticRole }.toSet())
        assertEquals(TypeSemanticRole.FIRE, types[9]?.semanticRole)
        assertEquals(TypeSemanticRole.GRASS, types[11]?.semanticRole)
        assertEquals(TypeSemanticRole.FAIRY, types[17]?.semanticRole)
        assertEquals(TypeSemanticRole.MYSTERY, types[18]?.semanticRole)
    }
}
