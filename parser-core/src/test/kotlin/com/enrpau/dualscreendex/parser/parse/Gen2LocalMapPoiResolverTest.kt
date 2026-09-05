package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.text.KoreanGen2PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.PokemonTextToken
import com.enrpau.dualscreendex.parser.text.PokemonTextTokenDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen2LocalMapPoiResolverTest {
    @Test
    fun isolatesMalformedMapsAndIgnoresUnrelatedObjectPointers() {
        val bytes = ByteArray(0x8000)
        writeAttributes(bytes, ATTRIBUTES_1, EVENTS_1_ADDRESS)
        writeAttributes(bytes, ATTRIBUTES_2, 0x7FFF)
        byteArrayOf(
            0, 0,
            2,
            1, 2, 1, 0, 2,
            20, 20, 1, 0, 2,
            0,
            0,
            1,
            0, 5, 6, 0, 0, -1, -1, 0, 0, 0x12, 0x28, -1, -1,
        ).copyInto(bytes, EVENTS_1)

        val resolution = Gen2LocalMapPoiResolver.resolve(
            rom = RomImage(bytes),
            sources = listOf(
                Gen2LocalMapPoiResolver.Source(1, 1, ATTRIBUTES_1),
                Gen2LocalMapPoiResolver.Source(2, 1, ATTRIBUTES_2),
            ),
            maps = listOf(localMap(1), localMap(2)),
            family = EngineFamily.GOLD_SILVER,
            codec = null,
        )

        val poi = resolution.pois.single()
        assertEquals(LocalMapPoiKind.PLACE, poi.kind)
        assertEquals(1, poi.baseAreaId)
        assertEquals(2, poi.tileX)
        assertEquals(1, poi.tileY)
        assertEquals(2, poi.destinationBaseAreaId)
        assertTrue(resolution.skippedReasons.any { it.startsWith("map 0x0002 POIs:") })
    }

    @Test
    fun signHeadlinesAdvanceByVariableWidthCodecTokens() {
        val bytes = ByteArray(0x8000)
        writeAttributes(bytes, ATTRIBUTES_1, EVENTS_1_ADDRESS)
        byteArrayOf(
            0, 0,
            0,
            0,
            1,
            1, 2, 0, (SIGN_SCRIPT_ADDRESS and 0xFF).toByte(), (SIGN_SCRIPT_ADDRESS ushr 8).toByte(),
            0,
        ).copyInto(bytes, EVENTS_1)
        byteArrayOf(
            0x52,
            (SIGN_TEXT_ADDRESS and 0xFF).toByte(),
            (SIGN_TEXT_ADDRESS ushr 8).toByte(),
        ).copyInto(bytes, SIGN_SCRIPT)
        byteArrayOf(0x00, 0x70, 0x50, 0x70, 0x01, 0x50).copyInto(bytes, SIGN_TEXT)

        val resolution = Gen2LocalMapPoiResolver.resolve(
            rom = RomImage(bytes),
            sources = listOf(Gen2LocalMapPoiResolver.Source(1, 1, ATTRIBUTES_1)),
            maps = listOf(localMap(1)),
            family = EngineFamily.GOLD_SILVER,
            codec = variableWidthCodec(),
        )

        assertEquals("ABAB", resolution.pois.single().displayName)
    }

    @Test
    fun declaredDirectSignUsesSelectedConsumerLineAndDoneAfterRelocation() {
        for (fixture in listOf(Gen2DeclaredSignFixture(), Gen2DeclaredSignFixture(0x20, 2))) {
            assertEquals("이곳은 연두마을", resolveDeclared(fixture).pois.single().displayName)
        }
    }

    @Test
    fun selectedIncompleteDeclarationCannotEscapeToLegacyOpcode() {
        val fixture = legacyCompatibleDeclaration()
        assertEquals("이곳은 연두마을", resolveDeclared(fixture).pois.single().displayName)
        fixture.bytes[fixture.at("textStart")] = 0
        assertEquals(null, resolveDeclared(fixture).pois.single().displayName)
        assertEquals(Gen2DeclaredSignAbi.Status.INCOMPLETE, abi(fixture).abi!!.outcome(0x52).status)
    }

    @Test
    fun explicitWriterReplacingDeclaredSetupWrapperRejectsWithoutRuntimeClaim() {
        val fixture = legacyCompatibleDeclaration()
        assertEquals("이곳은 연두마을", resolveDeclared(fixture).pois.single().displayName)
        fixture.emit("openText", "3e 00 ea @textPointerState c9")
        assertEquals(null, resolveDeclared(fixture).pois.single().displayName)
    }

    private fun legacyCompatibleDeclaration() = Gen2DeclaredSignFixture().apply {
        bytes[script] = 0x52
        word(at("scriptTable") + 0x52 * 2, at("direct"))
        // Outside the complete declared record; legacy decoding would consume both lines.
        bytes[text + 24] = 0x50
    }

    private fun abi(f: Gen2DeclaredSignFixture, limits: ResolutionLimits = ResolutionLimits()) =
        Gen2DeclaredSignAbi.resolve(RomImage(f.bytes), listOf(f.source), limits, ParserCancellationToken.NONE)

    @Test
    fun everyInterpretationDependencyMutationStartsFromAResolvedPositive() {
        val mutations: List<Pair<String, (Gen2DeclaredSignFixture) -> Unit>> = listOf(
            "LE second operand reader" to { f -> f.bytes[f.at("direct") + 12] = 0 },
            "saved pointer high address" to { f -> f.word(f.at("direct") + 16, f.at("textPointerHi") + 1, false) },
            "captured bank address" to { f -> f.word(f.at("direct") + 4, f.at("textBankState") + 1, false) },
            "template bank" to { f -> f.bytes[f.at("direct") + 19] = (f.codeBank + 1).toByte() },
            "template operand" to { f -> f.bytes[f.at("template") + 2] = 0 },
            "selected handler pointer" to { f -> f.word(f.at("scriptTable") + 0x52 * 2, f.at("direct") + 1) },
            "unknown repeat consumer" to { f -> f.word(f.at("repeat") + 27, 0x3e00) },
            "repeat branch" to { f -> f.bytes[f.at("repeat") + 11] = 0 },
            "repeat return" to { f -> f.bytes[f.at("repeat") + 29] = 0 },
            "open return" to { f -> f.bytes[f.at("openHandler") + 3] = 0 },
            "unknown setup call" to { f -> f.word(f.at("mapTextbox") + 6, 0x3e00) },
            "setup bank instruction" to { f -> f.bytes[f.at("openText") + 8] = 0 },
            "graphics address outside bank window" to { f -> f.word(f.at("openText") + 10, 0x8000, false) },
            "farcall continuation" to { f -> f.bytes[f.at("farCall") + 24] = 0 },
            "text dispatch step" to { f -> f.word(f.at("textDispatch") + 5, f.at("textDispatchStep") + 1) },
            "START target" to { f -> f.word(f.at("textCommands"), f.at("textStart") + 1) },
            "LINE next-character target" to { f -> f.word(f.at("line") + 6, f.at("nextChar") + 1) },
            "DONE embedded stop pointer" to { f -> f.word(f.at("done") + 2, f.at("stopByte") + 1) },
            "token trail increment" to { f -> f.bytes[f.at("doubleByte") + 1] = 0 },
            "byte reader pointer width" to { f -> f.bytes[f.at("getByte") + 14] = 0 },
        )
        for ((name, mutate) in mutations) {
            val f = legacyCompatibleDeclaration()
            assertEquals("positive $name", "이곳은 연두마을", resolveDeclared(f).pois.single().displayName)
            mutate(f)
            assertEquals(name, null, resolveDeclared(f).pois.single().displayName)
            val root = abi(f)
            assertEquals(name, Gen2DeclaredSignAbi.Status.INCOMPLETE, root.abi?.outcome(0x52)?.status ?: root.status)
        }
    }

    @Test
    fun selectedMissingAndConflictingDispatchersAreTerminal() {
        for (duplicate in listOf(false, true)) {
            val f = legacyCompatibleDeclaration()
            assertEquals("이곳은 연두마을", resolveDeclared(f).pois.single().displayName)
            if (duplicate) f.bytes.copyInto(f.bytes, f.at("scriptDispatch") + 0x40, f.at("scriptDispatch"), f.at("scriptDispatch") + 8)
            else f.bytes[f.at("scriptDispatch")] = 0
            assertEquals(if (duplicate) Gen2DeclaredSignAbi.Status.CONFLICT else Gen2DeclaredSignAbi.Status.INCOMPLETE, abi(f).status)
            assertEquals(null, resolveDeclared(f).pois.single().displayName)
        }
    }

    @Test
    fun selectedRootAndBackgroundAmbiguityHaveConflictOutcomes() {
        for (symbol in listOf("copyAttrsChain", "bgDispatch")) {
            val f = legacyCompatibleDeclaration()
            assertEquals("이곳은 연두마을", resolveDeclared(f).pois.single().displayName)
            val length = if (symbol == "bgDispatch") 15 else 16
            f.bytes.copyInto(f.bytes, f.at(symbol) + 0x40, f.at(symbol), f.at(symbol) + length)
            assertEquals(symbol, Gen2DeclaredSignAbi.Status.CONFLICT, abi(f).status)
            assertEquals(null, resolveDeclared(f).pois.single().displayName)
        }
    }

    @Test
    fun duplicateDictionaryControlIsConflict() {
        val f = legacyCompatibleDeclaration()
        assertEquals("이곳은 연두마을", resolveDeclared(f).pois.single().displayName)
        f.bytes[f.at("placeString") + 26] = 0x5a
        assertEquals(Gen2DeclaredSignAbi.Status.CONFLICT, abi(f).abi!!.outcome(0x52).status)
        assertEquals(null, resolveDeclared(f).pois.single().displayName)
    }

    @Test
    fun unrelatedStructuredDecoysDoNotPoisonLegacyGoldOrCrystal() {
        for ((family, command) in listOf(EngineFamily.GOLD_SILVER to 0x52, EngineFamily.CRYSTAL to 0x53)) {
            val f = Gen2DeclaredSignFixture()
            // This complete code belongs to a different map descriptor, not this accepted map.
            f.word(f.at("mapHeader") + 3, f.attributes + 0x20)
            f.bytes[f.script] = command.toByte()
            f.raw(f.text, "00 70 50 70 01 50")
            assertEquals(Gen2DeclaredSignAbi.Status.ABSENT, abi(f).status)
            val result = Gen2LocalMapPoiResolver.resolve(RomImage(f.bytes), listOf(f.source), listOf(f.map), family, variableWidthCodec())
            assertEquals("ABAB", result.pois.single().displayName)
        }
    }

    @Test
    fun tokenTrailControlsAreNotGrammarAndWesternControlsAreNotImplicit() {
        val f = Gen2DeclaredSignFixture()
        f.raw(f.text, "00 01 5a 01 5e 01 50 5a 01 5e 5e")
        val codec = declaredTokenCodec()
        assertEquals("ABC", resolveDeclared(f, codec).pois.single().displayName)
        // One lead becomes a Western headline-control byte; not supported by declared grammar.
        f.bytes[f.text + 1] = 0x4f
        assertEquals(null, resolveDeclared(f, codec).pois.single().displayName)
    }

    @Test
    fun missingTerminationMalformedSecondLineAndReadBoundNeverYieldHeadline() {
        val mutations: List<Pair<String, (Gen2DeclaredSignFixture) -> Unit>> = listOf(
            "missing DONE" to { f -> f.bytes[f.text + 23] = 0xff.toByte() },
            "malformed second line" to { f -> f.bytes[f.text + 17] = 0xff.toByte() },
            "missing START" to { f -> f.bytes[f.text] = 0xff.toByte() },
            "DONE beyond bounded extent" to { f -> f.bytes.fill(0x7f, f.text + 16, f.text + 97); f.bytes[f.text + 97] = 0x5e },
            "bank crossing trail" to { f -> val end = (f.dataBank + 1) * 0x4000; f.word(f.script + 1, end - 4); f.raw(end - 4, "00 01 67 01") },
            "text inside attributes" to { f -> f.word(f.script + 1, f.attributes + 1) },
            "text inside event object" to { f -> f.word(f.script + 1, f.events) },
            "text inside script operand" to { f -> f.word(f.script + 1, f.script + 1) },
            "script pointer inside event object" to { f -> f.word(f.events + 8, f.events) },
            "banked operand in WRAM" to { f -> f.word(f.script + 1, 0xc000, false) },
        )
        for ((name, mutate) in mutations) {
            val f = Gen2DeclaredSignFixture()
            val positive = resolveDeclared(f).pois.single()
            assertEquals("이곳은 연두마을", positive.displayName)
            mutate(f)
            val negative = resolveDeclared(f).pois.single()
            assertEquals(name, positive.copy(displayName = null), negative)
        }
        val f = Gen2DeclaredSignFixture()
        assertEquals("이곳은 연두마을", resolveDeclared(f).pois.single().displayName)
        val truncated = RomImage(f.bytes.copyOf(f.text + 22))
        assertEquals(null, Gen2LocalMapPoiResolver.resolve(truncated, listOf(f.source), listOf(f.map), EngineFamily.GOLD_SILVER, KoreanGen2PokemonTextCodec.codec).pois.single().displayName)
    }

    @Test
    fun limitsAndCancellationAreBoundedAndNeverRetryFamily() {
        for (limits in listOf(ResolutionLimits(maxProbeWorkPerDataset = 20), ResolutionLimits(maxProbeRootsPerDataset = 1), ResolutionLimits(maxDatasetExtentBytes = 16))) {
            val f = legacyCompatibleDeclaration()
            assertEquals("이곳은 연두마을", resolveDeclared(f).pois.single().displayName)
            val root = abi(f, limits)
            assertEquals(Gen2DeclaredSignAbi.Status.BUDGET, root.abi?.outcome(0x52)?.status ?: root.status)
            val result = Gen2LocalMapPoiResolver.resolve(RomImage(f.bytes), listOf(f.source), listOf(f.map), EngineFamily.GOLD_SILVER, KoreanGen2PokemonTextCodec.codec, limits)
            assertEquals(null, result.pois.single().displayName)
        }
        var totalChecks = 0
        val positive = Gen2DeclaredSignFixture()
        val baseline = Gen2LocalMapPoiResolver.resolve(RomImage(positive.bytes), listOf(positive.source), listOf(positive.map),
            EngineFamily.GOLD_SILVER, KoreanGen2PokemonTextCodec.codec,
            cancellation = ParserCancellationToken { totalChecks++ })
        assertEquals("이곳은 연두마을", baseline.pois.single().displayName)
        for (at in listOf(1, 10, totalChecks - 1)) {
            val f = Gen2DeclaredSignFixture()
            var calls = 0
            org.junit.Assert.assertThrows(ParserCancellationException::class.java) {
                Gen2LocalMapPoiResolver.resolve(RomImage(f.bytes), listOf(f.source), listOf(f.map), EngineFamily.GOLD_SILVER, KoreanGen2PokemonTextCodec.codec,
                    cancellation = ParserCancellationToken { if (++calls >= at) throw ParserCancellationException() })
            }
        }
    }

    private fun declaredTokenCodec() = PokemonTextCodec(
        id = "test-declared-width", version = 1, language = LanguageTag.KOREAN,
        applicableGenerations = setOf(2), applicablePlatforms = setOf(Platform.GBC), terminator = 0x50,
        tokenDecoder = PokemonTextTokenDecoder { rom, offset, end ->
            if (rom.u8(offset) == 1 && offset + 1 < end) when (rom.u8(offset + 1)) {
                0x5a -> PokemonTextToken.Glyph("A", 2)
                0x5e -> PokemonTextToken.Glyph("B", 2)
                0x50 -> PokemonTextToken.Glyph("C", 2)
                0 -> PokemonTextToken.Glyph("D", 2)
                else -> PokemonTextToken.Invalid()
            } else PokemonTextToken.Invalid()
        },
    )

    @Test
    fun declaredTextTableAndStartCannotAliasHomeThroughBankWindow() {
        for (table in listOf(false, true)) {
            val f = Gen2DeclaredSignFixture(shift = 0x20, bankShift = 2)
            assertEquals("이곳은 연두마을", resolveDeclared(f).pois.single().displayName)
            if (table) f.word(f.at("textDispatch") + 15, f.at("textCommands") + 0x4000, romAddress = false)
            else f.word(f.at("textCommands"), f.at("textStart") + 0x4000, romAddress = false)
            assertEquals("bank-window alias table=$table", null, resolveDeclared(f).pois.single().displayName)
            assertEquals(Gen2DeclaredSignAbi.Status.INCOMPLETE, abi(f).abi!!.outcome(0x53).status)
        }
    }

    @Test
    fun directCopierBodyMustProveSelectedRowTransfer() {
        for ((offset, replacement) in listOf(0 to 0xc9, 6 to 0x00, 9 to 0xfb)) {
            val f = legacyCompatibleDeclaration()
            assertEquals("이곳은 연두마을", resolveDeclared(f).pois.single().displayName)
            f.bytes[f.at("copyBytes") + offset] = replacement.toByte()
            assertEquals("copier offset=$offset", null, resolveDeclared(f).pois.single().displayName)
            assertEquals(Gen2DeclaredSignAbi.Status.INCOMPLETE, abi(f).status)
        }
    }

    @Test
    fun directStrideBodyMustProveSelectedRecordArithmetic() {
        for ((offset, replacement) in listOf(0 to 0xc9, 2 to 0x19, 5 to 0xfd)) {
            val f = legacyCompatibleDeclaration()
            assertEquals("이곳은 연두마을", resolveDeclared(f).pois.single().displayName)
            f.bytes[f.at("addNTimes") + offset] = replacement.toByte()
            assertEquals("stride offset=$offset", null, resolveDeclared(f).pois.single().displayName)
            assertEquals(Gen2DeclaredSignAbi.Status.INCOMPLETE, abi(f).status)
        }
    }

    @Test
    fun nextDeclaredRootBoundsSplitTokenWithoutLosingNeighborAliasesOrItems() {
        val f = twoDeclaredRecordsWithItems()
        val positive = resolveDeclared(f).pois
        assertEquals(6, positive.size)
        assertEquals("이곳은 연두마을", positive.single { it.key.endsWith("/bg/0") }.displayName)
        for (index in listOf(1, 2)) assertEquals("공박사", positive.single { it.key.endsWith("/bg/$index") }.displayName)
        assertEquals(2, positive.count { it.item != null })
        assertEquals(0x101, positive.single { it.key.endsWith("/warp/0") }.destinationBaseAreaId)
        // This lead byte would consume the next record's START as its valid token trail.
        f.bytes[f.text + 23] = 0x02
        assertEquals(positive.map { if (it.key.endsWith("/bg/0")) it.copy(displayName = null) else it }, resolveDeclared(f).pois)
    }

    @Test
    fun nextDeclaredRootBoundsProseEvenWhenNeighborIsNotDecodable() {
        val f = twoDeclaredRecordsWithItems()
        val positive = resolveDeclared(f).pois
        assertEquals("이곳은 연두마을", positive.single { it.key.endsWith("/bg/0") }.displayName)
        // Unsupported prose does not erase an independently bound declaration's boundary.
        f.bytes[f.text + 24] = 0x01 // not START, but a valid trail for the preceding 0x02 lead
        val malformedNeighbor = resolveDeclared(f).pois
        assertEquals(positive.map { if (it.key.endsWith("/bg/1") || it.key.endsWith("/bg/2")) it.copy(displayName = null) else it }, malformedNeighbor)
        f.bytes[f.text + 23] = 0x02
        assertEquals(malformedNeighbor.map { if (it.key.endsWith("/bg/0")) it.copy(displayName = null) else it }, resolveDeclared(f).pois)
    }

    private fun twoDeclaredRecordsWithItems() = Gen2DeclaredSignFixture().apply {
        // Destination warp, three signs (two aliases), hidden and visible items.
        raw(events, "00 00 01 08 08 01 01 01 00 04 01 02 00 00 00 03 03 00 00 00 04 04 00 00 00 06 06 07 00 00 01")
        word(events + 13, script)
        word(events + 18, script + 8)
        word(events + 23, script + 8)
        word(events + 28, script + 16)
        raw(events + 31, "01 08 09 00 00 00 00 01 00 00 00 34 12")
        word(events + 40, script + 32)
        raw(script + 8, "53"); word(script + 9, text + 24)
        raw(script + 16, "45 23 07")
        raw(script + 32, "08 01")
    }

    private fun resolveDeclared(fixture: Gen2DeclaredSignFixture, codec: PokemonTextCodec = KoreanGen2PokemonTextCodec.codec) =
        Gen2LocalMapPoiResolver.resolve(
            RomImage(fixture.bytes), listOf(fixture.source), listOf(fixture.map), EngineFamily.GOLD_SILVER, codec,
        )

    private fun variableWidthCodec() = PokemonTextCodec(
        id = "test-gb-variable",
        version = 1,
        language = LanguageTag.ENGLISH,
        applicableGenerations = setOf(2),
        applicablePlatforms = setOf(Platform.GBC),
        terminator = 0x50,
        tokenDecoder = PokemonTextTokenDecoder { rom, offset, endExclusive ->
            when {
                rom.u8(offset) == 0x70 && offset + 1 < endExclusive -> PokemonTextToken.Glyph("AB", 2)
                rom.u8(offset) == 0x50 -> PokemonTextToken.Terminator()
                else -> PokemonTextToken.Invalid()
            }
        },
    )

    private fun writeAttributes(bytes: ByteArray, attributes: Int, eventsAddress: Int) {
        bytes[attributes + 6] = 1
        putU16(bytes, attributes + 9, eventsAddress)
    }

    private fun localMap(baseAreaId: Int) = LocalMap(
        key = "local/$baseAreaId",
        displayName = null,
        baseAreaId = baseAreaId,
        pixelWidth = 160,
        pixelHeight = 160,
        gridWidth = 10,
        gridHeight = 10,
        imageAssetKey = "asset/$baseAreaId",
    )

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private companion object {
        const val ATTRIBUTES_1 = 0x4000
        const val ATTRIBUTES_2 = 0x4020
        const val EVENTS_1 = 0x4100
        const val EVENTS_1_ADDRESS = 0x4100
        const val SIGN_SCRIPT = 0x4200
        const val SIGN_SCRIPT_ADDRESS = 0x4200
        const val SIGN_TEXT = 0x4300
        const val SIGN_TEXT_ADDRESS = 0x4300
    }
}
