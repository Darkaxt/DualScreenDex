package com.enrpau.dualscreendex.parser.dataset.learnsets

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.HeaderlessUnifiedSpeciesMetadata
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test

class EmbeddedLearnsetPointerResolverLiveRomTest {
    @Test
    fun selectsOneCompleteRealPointerFieldAndRejectsAmbiguityOrMalformedPointers() {
        val configured = System.getenv("DUALDEX_DREAMSTONE_ROM")
        assumeTrue("set DUALDEX_DREAMSTONE_ROM to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val original = Files.readAllBytes(path)
        assertEquals(EXPECTED_SHA, RomImage(original).sha256)

        val selected = resolve(original)
        assertNotNull(selected)
        assertEquals(148, requireNotNull(selected).pointerFieldOffset)

        val ambiguous = original.copyOf()
        repeat(SPECIES_COUNT) { speciesId ->
            val row = SPECIES_ROOT + speciesId * SPECIES_STRIDE
            ambiguous.copyInto(
                destination = ambiguous,
                destinationOffset = row + 144,
                startIndex = row + 148,
                endIndex = row + 152,
            )
        }
        assertNull(resolve(ambiguous))

        val malformed = original.copyOf()
        putU32(malformed, SPECIES_ROOT + SPECIES_STRIDE + 148, 0x07000000)
        assertNull(resolve(malformed))
    }

    private fun resolve(bytes: ByteArray): EmbeddedLearnsetPointerResolver.Resolution? {
        val rom = RomImage(bytes)
        return EmbeddedLearnsetPointerResolver.resolve(
            session = RomAnalysisSession(rom, RomHeaderReader.read(rom)),
            metadata = HeaderlessUnifiedSpeciesMetadata(
                speciesTableOffset = SPECIES_ROOT,
                speciesRecordSize = SPECIES_STRIDE,
                activePredicateOffset = 0,
                speciesNameOffset = 44,
                speciesNameWidth = 13,
                nationalDexOffset = 60,
            ),
            speciesCount = SPECIES_COUNT,
        )
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private companion object {
        const val EXPECTED_SHA = "ac31df9cc158823861294b17bd4e66857deab2a53dd81620ddcf6fc03a6a4220"
        const val SPECIES_ROOT = 0x7B0160
        const val SPECIES_STRIDE = 260
        const val SPECIES_COUNT = 0x5F5
    }
}
