package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndexFactory
import com.enrpau.dualscreendex.parser.analysis.GbaTargetReferenceEvidence
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.dataset.moves.putUnifiedMoveInfo
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.PokemonTextToken
import com.enrpau.dualscreendex.parser.text.PokemonTextTokenDecoder
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class HeaderlessUnifiedMoveResolverLiveRomTest {
    @Test
    fun suppliedCodecControlsDeterministicHeaderlessMoveValidation() {
        val root = 0x200
        val moveCount = 2
        val bytes = ByteArray(0x1000)
        repeat(moveCount) { id ->
            val record = root + id * MOVE_STRIDE
            val name = 0x800 + id * 0x20
            val description = 0x900 + id * 0x20
            putU32(bytes, record, GBA_ROM_BASE + name)
            putU32(bytes, record + 4, GBA_ROM_BASE + description)
            putUnifiedMoveInfo(bytes, record)
            putGbaText(bytes, name, "MOVE")
            putGbaText(bytes, description, "HITS")
        }
        val referenceIndex = GbaReferenceIndex.fromTargets(
            targets = mapOf(
                root to GbaTargetReferenceEvidence(
                    count = 1,
                    instructionSites = listOf(0x20),
                    observedSites = 1,
                    limitSites = 16,
                    overflowReason = null,
                ),
            ),
            limitTargets = 16,
        )
        val session = RomAnalysisSession(
            rom = RomImage(bytes),
            header = RomHeader(Platform.GBA, ""),
            gbaReferenceIndexFactory = GbaReferenceIndexFactory { _, _ -> referenceIndex },
        )

        val resolved = requireNotNull(
            HeaderlessUnifiedMoveResolver.resolve(session, moveCount, PokemonTextCodec.gbaEnglish),
        )
        assertEquals(root, resolved.tables.moveData?.offset)
        assertNull(HeaderlessUnifiedMoveResolver.resolve(session, moveCount, rejectingEnglishCodec()))
    }

    @Test
    fun selectsTheUniqueCompiledReferencedOrdinaryMoveTableAndRejectsRealMutations() {
        val configured = System.getenv("DUALDEX_DREAMSTONE_ROM")
        assumeTrue("set DUALDEX_DREAMSTONE_ROM to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val original = Files.readAllBytes(path)
        assertEquals(EXPECTED_SHA, RomImage(original).sha256)

        val originalRom = RomImage(original)
        val originalSession = RomAnalysisSession(originalRom, RomHeaderReader.read(originalRom))
        val index = requireNotNull(originalSession.gbaReferenceIndex)
        assertTrue(!index.overflowed)
        assertEquals(379, index.target(MOVE_ROOT)?.count)
        val completeSites = originalSession.nominatedGbaReferenceSites(MOVE_ROOT)
        assertTrue(completeSites?.siteEvidenceAvailable == true)
        assertEquals(379, completeSites?.instructionSites?.size)
        val selected = HeaderlessUnifiedMoveResolver.resolve(
            originalSession,
            MOVE_COUNT,
            PokemonTextCodec.gbaEnglish,
        )
        assertNotNull(selected)
        requireNotNull(selected)
        assertEquals(MOVE_ROOT, selected.tables.moveNames?.offset)
        assertEquals(MOVE_ROOT, selected.tables.moveData?.offset)
        assertEquals(MOVE_COUNT, selected.moveCount)
        assertEquals(MOVE_COUNT, selected.resolvedMoveDetails.rows.size)
        assertTrue(selected.moveNamesEvidence.compatible)
        assertTrue(selected.moveDataEvidence.compatible)

        val malformed = original.copyOf()
        putU32(malformed, MOVE_ROOT + MOVE_STRIDE, 0x07000000)
        assertNull(resolve(malformed))

        val ambiguous = original.copyOf()
        ambiguous.copyInto(
            destination = ambiguous,
            destinationOffset = DUPLICATE_ROOT,
            startIndex = MOVE_ROOT,
            endIndex = MOVE_ROOT + MOVE_COUNT * MOVE_STRIDE,
        )
        putU32(ambiguous, COMPILED_LITERAL, GBA_ROM_BASE + DUPLICATE_ROOT)
        assertNull(resolve(ambiguous))
    }

    private fun resolve(bytes: ByteArray): HeaderlessUnifiedMoveResolver.Resolution? {
        val rom = RomImage(bytes)
        return HeaderlessUnifiedMoveResolver.resolve(
            session = RomAnalysisSession(rom, RomHeaderReader.read(rom)),
            ordinaryMoveCount = MOVE_COUNT,
            codec = PokemonTextCodec.gbaEnglish,
        )
    }

    private fun rejectingEnglishCodec() = PokemonTextCodec(
        id = "test-headerless-move-rejecting-en",
        version = 1,
        language = LanguageTag.ENGLISH,
        applicableGenerations = setOf(3),
        applicablePlatforms = setOf(Platform.GBA),
        terminator = 0xFF,
        tokenDecoder = PokemonTextTokenDecoder { _, _, _ -> PokemonTextToken.Invalid() },
    )

    private fun putGbaText(bytes: ByteArray, offset: Int, value: String) {
        value.forEachIndexed { index, char ->
            bytes[offset + index] = (0xBB + char.code - 'A'.code).toByte()
        }
        bytes[offset + value.length] = 0xFF.toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private companion object {
        const val EXPECTED_SHA = "ac31df9cc158823861294b17bd4e66857deab2a53dd81620ddcf6fc03a6a4220"
        const val GBA_ROM_BASE = 0x08000000
        const val MOVE_ROOT = 0x759858
        const val MOVE_STRIDE = 48
        const val MOVE_COUNT = 848
        const val COMPILED_LITERAL = 0x9BCC
        const val DUPLICATE_ROOT = 0x1BCA098
    }
}
