package com.enrpau.dualscreendex.parser.family

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.parse.nativeGbaNameGeometry
import org.junit.Assert.*
import org.junit.Test

class NativeNameLayoutIntegrationTest {
    @Test fun carriesCompiledSixAndEightByteGeometryIntoEveryGenThreeFamily() {
        for ((family, code) in listOf(EngineFamily.RUBY_SAPPHIRE to "AXVJ", EngineFamily.EMERALD to "BPEJ", EngineFamily.FIRERED_LEAFGREEN to "BPRJ")) {
            val session = RomAnalysisSession(RomImage(nativeGbaNameGeometry()), RomHeader(Platform.GBA, "CUSTOM", code))
            val definition = EngineFamilyDefinitions.byFamily.getValue(family)
            val state = IdentityRootsStrategy().execute(session, definition, FamilyProbeState.empty())
            val identity = state.identityRoots as IdentityRootsPhaseResult.Resolved
            assertEquals(family.name, 6, identity.tableResolution.tables.speciesNames?.recordSize)
            assertEquals(family.name, 8, identity.tableResolution.tables.moveNames?.recordSize)
            assertEquals(0x2018, identity.tableResolution.tables.moveNames?.offset)
            assertEquals(4, identity.tableResolution.tables.speciesNames?.count)
        }
    }

    @Test fun contradictoryNameRootsCannotRestoreInheritedNamesOrErasePublishedNumericRoots() {
        val bytes = nativeGbaNameGeometry()
        pointer(bytes, 0x144, 0x2400)
        for (index in 0..6) pointer(bytes, 0x1bc + index * 4, 0x2500 + index * 0x10)
        val session = RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBA, "CUSTOM", "BPEJ"))
        val state = IdentityRootsStrategy().execute(session, EngineFamilyDefinitions.byFamily.getValue(EngineFamily.EMERALD), FamilyProbeState.empty())
        val tables = (state.identityRoots as IdentityRootsPhaseResult.Resolved).tableResolution.tables
        assertNull(tables.speciesNames)
        assertNull(tables.moveNames)
        assertEquals(0x2500, tables.baseStats?.offset)
        assertEquals(0x2540, tables.moveData?.offset)
    }

    @Test fun unmarkedGenOneNativeDialectComesFromCompleteConsumerNotTitle() {
        for (yellow in listOf(false, true)) {
            val family = if (yellow) EngineFamily.YELLOW else EngineFamily.RED_BLUE
            val session = RomAnalysisSession(RomImage(nativeGenOneCandidates(yellow)), RomHeader(Platform.GBC, if (yellow) "POKEMON RED" else "POKEMON YELLOW"))
            val definition = EngineFamilyDefinitions.byFamily.getValue(family)
            val identity = IdentityRootsStrategy().execute(session, definition, FamilyProbeState.empty()).identityRoots as IdentityRootsPhaseResult.Resolved
            assertEquals(if (yellow) "gb-gen1-ja-yellow" else "gb-gen1-ja-red-blue", identity.probeCodec.id)
            assertEquals(5, identity.tableResolution.tables.speciesNames?.recordSize)
            assertEquals(0x9000, identity.tableResolution.tables.moveNames?.offset)
        }
    }

    @Test fun competingNativeRootsRemainAmbiguousWithoutDuplicateJapaneseProjections() {
        val bytes = nativeGenOneCandidates(false)
        com.enrpau.dualscreendex.parser.parse.nativeGbConsumer(1, root = 0x6000).copyInto(bytes, 0x180)
        bytes.copyInto(bytes, 0xa000, 0x8000, 0x8000 + 190 * 5)
        val session = RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBC, "POKEMON RED"))
        val definition = EngineFamilyDefinitions.byFamily.getValue(EngineFamily.RED_BLUE)
        val identity = IdentityRootsStrategy().execute(session, definition, FamilyProbeState.empty())
        val core = CoreDatasetsStrategy().execute(session, definition, identity).coreDatasets as CoreDatasetsPhaseResult.Resolved
        assertEquals(com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus.AMBIGUOUS, core.languageManifest.status)
        assertNull(core.languageManifest.defaultLanguage)
        assertTrue(core.languageManifest.projections.isEmpty())
    }

    @Test fun ordinaryGenTwoCountDoesNotBlockUnmarkedNativeNameRecovery() {
        for (korean in listOf(false, true)) {
            val codec = if (korean) com.enrpau.dualscreendex.parser.text.KoreanGen2PokemonTextCodec.codec else com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs.gen2
            val source = nativeAuthorityFixture(codec)
            val width = source.species.recordSize
            val bytes = com.enrpau.dualscreendex.parser.parse.nativeGbNames(2, width).copyOf(0x10000)
            repeat(251) { index -> source.bytes.copyInto(bytes, 0x8000 + index * width, 0x100 + index % 4 * width, 0x100 + (index % 4 + 1) * width) }
            val one = source.bytes.copyOfRange(source.moves.offset, source.bytes.size)
            var cursor = 0x9000
            repeat(31) { one.copyInto(bytes, cursor); cursor += one.size }
            var start = source.moves.offset
            repeat(3) {
                val decoded = codec.decodeDetailed(RomImage(source.bytes), start, 24, com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken.NONE)
                source.bytes.copyInto(bytes, cursor, start, start + decoded.consumedBytes)
                start += decoded.consumedBytes; cursor += decoded.consumedBytes
            }
            val session = RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBC, "CUSTOM"))
            val definition = EngineFamilyDefinitions.byFamily.getValue(EngineFamily.GOLD_SILVER)
            val identity = IdentityRootsStrategy().execute(session, definition, FamilyProbeState.empty()).identityRoots as IdentityRootsPhaseResult.Resolved
            assertEquals(codec.id, identity.probeCodec.id)
            assertEquals(width, identity.tableResolution.tables.speciesNames?.recordSize)
            assertEquals(251, identity.tableResolution.tables.speciesNames?.count)
        }
    }

    @Test fun genThreeNativeDialectUsesPublishedOrCompiledLineageNotRegionCode() {
        for (ruby in listOf(false, true)) {
            val bytes = nativeGenThreeCandidates(ruby)
            val family = if (ruby) EngineFamily.RUBY_SAPPHIRE else EngineFamily.EMERALD
            val session = RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBA, "CUSTOM", "BPEE"))
            val identity = IdentityRootsStrategy().execute(session, EngineFamilyDefinitions.byFamily.getValue(family), FamilyProbeState.empty()).identityRoots as IdentityRootsPhaseResult.Resolved
            assertEquals(if (ruby) "gba-gen3-ja-ruby-sapphire" else "gba-gen3-ja-emerald-frlg", identity.probeCodec.id)
        }
    }

    @Test fun nativeCandidateMoveConsumerScansObserveSessionCancellation() {
        val cancellation = com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken {
            if (Thread.currentThread().stackTrace.any { it.className.endsWith("Gen1CompiledMoveResolver") })
                throw com.enrpau.dualscreendex.parser.analysis.ParserCancellationException()
        }
        val session = RomAnalysisSession(RomImage(nativeGenOneCandidates(false)), RomHeader(Platform.GBC, "CUSTOM"), cancellation = cancellation)
        assertThrows(com.enrpau.dualscreendex.parser.analysis.ParserCancellationException::class.java) {
            IdentityRootsStrategy().execute(session, EngineFamilyDefinitions.byFamily.getValue(EngineFamily.RED_BLUE), FamilyProbeState.empty())
        }
    }

    @Test fun wrongFamilyCannotReacceptNativeConsumerThroughLegacyFallback() {
        val session = RomAnalysisSession(RomImage(nativeGenOneCandidates(true)),
            RomHeader(Platform.GBC, "POKEMON RED", gbDestinationCode = 0))
        val definition = EngineFamilyDefinitions.byFamily.getValue(EngineFamily.RED_BLUE)
        val identity = IdentityRootsStrategy().execute(session, definition, FamilyProbeState.empty())
        val core = CoreDatasetsStrategy().execute(session, definition, identity).coreDatasets as CoreDatasetsPhaseResult.Resolved
        assertEquals(com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus.UNKNOWN, core.languageManifest.status)
    }

    @Test fun nativeMoveConsumerCannotTraverseTheNextPhysicalBank() {
        val bytes = nativeGenOneCandidates(false)
        val moved = bytes.copyOfRange(0x9000, 0x9500)
        moved.copyInto(bytes, 0xbf00)
        bytes[0x1202] = 0x00; bytes[0x1203] = 0x7f
        // Move numeric records to bank3:5000, outside the copied overflowing strings.
        val data = nativeGenOneCandidates(false).copyOfRange(0xc000, 0xc000 + 165 * 6)
        data.copyInto(bytes, 0xd000); bytes[0x1303] = 0x50
        val session = RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBC, "POKEMON RED", gbDestinationCode = 0))
        val definition = EngineFamilyDefinitions.byFamily.getValue(EngineFamily.RED_BLUE)
        val identity = IdentityRootsStrategy().execute(session, definition, FamilyProbeState.empty())
        val core = CoreDatasetsStrategy().execute(session, definition, identity).coreDatasets as CoreDatasetsPhaseResult.Resolved
        assertEquals(com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus.UNKNOWN, core.languageManifest.status)
    }

    private fun nativeGenThreeCandidates(ruby: Boolean): ByteArray {
        val bytes = nativeGbaNameGeometry()
        val source = nativeAuthorityFixture(if (ruby) com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs.gen3RubySapphire else com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs.gen3Later)
        source.bytes.copyInto(bytes, 0x2000, 0x100, 0x118)
        source.bytes.copyInto(bytes, 0x2018, 0x200, source.bytes.size)
        if (ruby) {
            pointer(bytes, 0x700, 0x2000)
            byteArrayOf(0x30, 0xb5.toByte(), 0, 0x25, 8, 0x4c, 0xc8.toByte(), 0xf7.toByte()).copyInto(bytes, 0x704)
        } else {
            pointer(bytes, 0x144, 0x2000); pointer(bytes, 0x148, 0x2018)
        }
        return bytes
    }

    private fun nativeGenOneCandidates(yellow: Boolean): ByteArray {
        val bytes = com.enrpau.dualscreendex.parser.parse.nativeGbNames(1, helper = yellow).copyOf(0x10000)
        val codec = if (yellow) com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs.gen1Yellow else com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs.gen1RedBlue
        val source = nativeAuthorityFixture(codec)
        repeat(190) { index -> source.bytes.copyInto(bytes, 0x8000 + index * 5, 0x100 + index % 4 * 5, 0x105 + index % 4 * 5) }
        fun put(offset: Int, vararg values: Int) = values.map(Int::toByte).toByteArray().copyInto(bytes, offset)
        put(0x1000, 0xe5, 0x3e, 2, 0xea, 0, 0xd0, 0xfa, 1, 0xd0, 0xea, 2, 0xd0, 0x3e, 2, 0xea, 3, 0xd0, 0xcd, 0, 0x10, 0x11, 0, 0xd1, 0xe1, 0xc9)
        put(0x1100, 0x21, 0, 0x12, 0x19, 0x2a, 0xe0, 0x96, 0x7e, 0xe0, 0x95, 0xf0, 0x95, 0x67, 0xf0, 0x96, 0x6f)
        put(0x1202, 0, 0x50)
        put(0x1300, 0x3d, 0x21, 0, 0x40, 1, 6, 0, 0xcd, 0, 0x10, 0x11, 0, 0xd2, 0x3e, 3, 0xcd, 0, 0x11)
        val names = listOf("れんぞくパンチ", "メガトンパンチ", "ネコにこばん", "ほのおのパンチ", "れいとうパンチ", "かみなりパンチ", "ひっかく", "ハサミギロチン")
        var cursor = 0x9000
        repeat(165) { index ->
            val encoded = nativeEncode(names[index % names.size], codec)
            encoded.copyInto(bytes, cursor); cursor += encoded.size
            put(0xc000 + index * 6, 0, 1, index + 1, index % 28, 100, 15)
        }
        return bytes
    }

    private fun pointer(bytes: ByteArray, offset: Int, root: Int) {
        repeat(4) { bytes[offset + it] = ((root + 0x08000000) ushr (8 * it)).toByte() }
    }
}
