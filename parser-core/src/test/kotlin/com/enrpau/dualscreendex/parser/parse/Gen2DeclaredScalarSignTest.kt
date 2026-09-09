package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs
import com.enrpau.dualscreendex.parser.text.KoreanGen2PokemonTextCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Fabricated relocated declarations. No native input, retained ROM payload or runtime emulation. */
class Gen2DeclaredScalarSignTest {
    @Test fun scalarInlineAndSetupCallBindRelocatedRolesWithoutFamilyRouting() {
        for (inline in listOf(false, true)) for (alternate in listOf(false, true)) {
            val f = scalarFixture(if (alternate) 0x20 else 0, if (alternate) 2 else 0, alternate, inline)
            for (family in listOf(EngineFamily.GOLD_SILVER, EngineFamily.CRYSTAL)) {
                assertEquals("ここは ワカバ", resolve(f, family).pois.single().displayName)
            }
        }
    }

    @Test fun declarationRejectionsRetainBoundedReasonsWithoutLosingNumericPois() {
        val rootFailure = scalarFixture()
        val numeric = resolve(rootFailure).pois.single().copy(displayName = null)
        rootFailure.bytes[rootFailure.at("copyBytes")] = 0xc9.toByte()
        val rootResult = resolve(rootFailure)
        assertEquals(numeric, rootResult.pois.single())
        assertEquals(1, rootResult.skippedReasons.size)
        org.junit.Assert.assertTrue(rootResult.skippedReasons.single().startsWith("Gen II sign declaration INCOMPLETE:"))

        val commandFailure = scalarFixture()
        commandFailure.bytes[commandFailure.at("waitHandler")] = 0
        val commandResult = resolve(commandFailure)
        assertEquals(numeric, commandResult.pois.single())
        assertEquals(1, commandResult.skippedReasons.size)
        org.junit.Assert.assertTrue(commandResult.skippedReasons.single().startsWith("Gen II sign command 0x52 INCOMPLETE:"))
        org.junit.Assert.assertTrue(commandResult.skippedReasons.single().contains("unsupported declaration"))
        assertEquals(emptyList<String>(), resolve(scalarFixture()).skippedReasons)
    }

    @Test fun repeatedDiagnosticReadsNeverRetryCommands() {
        val f = scalarFixture()
        f.bytes[f.at("waitHandler")] = 0
        var checks = 0
        val declaration = Gen2DeclaredSignAbi.resolve(RomImage(f.bytes), listOf(f.source), ResolutionLimits(),
            ParserCancellationToken { checks++ }).abi!!
        assertEquals(emptyList<String>(), declaration.failureReasons())
        declaration.grammar(0x52)
        val expected = declaration.failureReasons()
        assertEquals(1, expected.size)
        val before = checks
        repeat(300) { assertEquals(expected, declaration.failureReasons()) }
        assertEquals(before, checks)
    }

    @Test fun oldPairGrammarAndControlsRemainDistinct() {
        val f = Gen2DeclaredSignFixture(0x20, 2)
        assertEquals("이곳은 연두마을", Gen2LocalMapPoiResolver.resolve(RomImage(f.bytes), listOf(f.source), listOf(f.map),
            EngineFamily.CRYSTAL, KoreanGen2PokemonTextCodec.codec).pois.single().displayName)
        f.bytes[f.text + 1] = 0x37
        assertEquals(null, Gen2LocalMapPoiResolver.resolve(RomImage(f.bytes), listOf(f.source), listOf(f.map),
            EngineFamily.CRYSTAL, KoreanGen2PokemonTextCodec.codec).pois.single().displayName)
        val scalar = scalarFixture()
        assertEquals(null, Gen2LocalMapPoiResolver.resolve(RomImage(scalar.bytes), listOf(scalar.source), listOf(scalar.map),
            EngineFamily.CRYSTAL, KoreanGen2PokemonTextCodec.codec).pois.single().displayName)
    }

    @Test fun everyScalarInterpretationMutationStartsFromPositive() {
        val mutations: List<Pair<String, (Gen2DeclaredSignFixture) -> Unit>> = listOf(
            "selected slot" to { f -> f.word(f.at("scriptTable") + 0x52 * 2, f.at("direct") + 1) },
            "second operand read" to { f -> f.bytes[f.at("direct") + 12] = 0 },
            "saved text high" to { f -> f.word(f.at("direct") + 16, f.at("textPointerHi") + 1, false) },
            "template bank" to { f -> f.bytes[f.at("direct") + 19] = (f.codeBank + 1).toByte() },
            "sentinel" to { f -> f.bytes[f.at("template") + 2] = 0 },
            "swapped wait and close" to { f -> f.raw(f.at("template") + 4, "49 53") },
            "wait body" to { f -> f.bytes[f.at("waitHandler")] = 0 },
            "close body" to { f -> f.bytes[f.at("closeHandler") + 6] = 0 },
            "end body" to { f -> f.bytes[f.at("endHandler") + 3] = 0 },
            "wait non-ROM target" to { f -> f.word(f.at("waitHandler") + 1, 0xc000, false) },
            "end non-ROM target" to { f -> f.word(f.at("endHandler") + 1, 0xc000, false) },
            "repeat continuation" to { f -> f.bytes[f.at("repeat") + 29] = 0 },
            "unknown inline speech" to { f -> f.word(f.at("mapTextbox") + 7, 0x3e00) },
            "inline print target" to { f -> f.word(f.at("mapTextbox") + 21, 0x3e00) },
            "inline stack restore" to { f -> f.bytes[f.at("mapTextbox") + 19] = 0 },
            "inline opaque target outside ROM0" to { f -> f.word(f.at("mapTextbox") + 10, 0x8000, false) },
            "open replaced by writer" to { f -> f.emit("openText", "3e 00 ea @textPointerState c9") },
            "START" to { f -> f.bytes[f.at("textStart")] = 0 },
            "LINE join" to { f -> f.word(f.at("line") + 6, f.at("nextChar") + 1) },
            "DONE stop byte" to { f -> f.word(f.at("done") + 2, f.at("stopByte") + 1) },
            "scalar fallback missing" to { f -> f.bytes[f.at("scalar")] = 0 },
            "scalar increment continuation" to { f -> f.word(f.at("scalar") + 60, f.at("nextChar") + 1) },
            "scalar range order" to { f -> f.bytes[f.at("scalar") + 24] = 0x70 },
            "scalar opaque target outside ROM0" to { f -> f.word(f.at("scalar") + 10, 0xc000, false) },
            "literal handler" to { f -> f.bytes[f.at("literalAHandler")] = 0 },
            "literal join" to { f -> f.word(f.at("literalJoin") + 1, f.at("placeString") + 1) },
            "literal bank alias" to { f -> f.word(f.at("literalAHandler") + 2, f.at("literalA") + 0x4000, false) },
            "nested literal control" to { f -> f.bytes[f.at("literalA")] = 0x37 },
            "unbounded literal" to { f -> f.bytes.fill(0x7f, f.at("literalA"), f.at("literalA") + 32) },
            "second-line literal invalid" to { f -> f.bytes[f.at("literalBHandler")] = 0 },
        )
        for ((name, mutate) in mutations) {
            val f = scalarFixture()
            val positive = resolve(f).pois.single()
            assertEquals("positive $name", "ここは ワカバ", positive.displayName)
            mutate(f)
            assertEquals(name, positive.copy(displayName = null), resolve(f).pois.single())
        }
    }

    @Test fun completeDictionaryRejectsLateDuplicateOrCompetingLineDone() {
        for (kind in listOf("duplicate", "line", "done")) {
            val f = scalarFixture()
            assertEquals("ここは ワカバ", resolve(f).pois.single().displayName)
            // This entry follows DONE. A parser stopping at the first DONE misses it.
            when (kind) {
                "duplicate" -> f.bytes[f.at("dictionary") + 21] = 0x4f
                "line" -> f.word(f.at("dictionary") + 23, f.at("line"))
                else -> f.word(f.at("dictionary") + 23, f.at("done"))
            }
            assertEquals(kind, Gen2DeclaredSignAbi.Status.CONFLICT, outcome(f))
            assertEquals(kind, null, resolve(f).pois.single().displayName)
        }
    }

    @Test fun competingDispatchersAndMissingSelectedRootsAreTerminal() {
        for (name in listOf("scriptDispatch", "copyAttrsChain", "bgDispatch")) {
            val f = scalarFixture()
            assertEquals("ここは ワカバ", resolve(f).pois.single().displayName)
            val count = when (name) { "scriptDispatch" -> 8; "bgDispatch" -> 15; else -> 16 }
            f.bytes.copyInto(f.bytes, f.at(name) + 0x40, f.at(name), f.at(name) + count)
            assertEquals(Gen2DeclaredSignAbi.Status.CONFLICT, outcome(f))
            assertEquals(null, resolve(f).pois.single().displayName)
        }
        val f = scalarFixture()
        f.bytes[f.at("copyBytes")] = 0xc9.toByte()
        assertEquals(Gen2DeclaredSignAbi.Status.INCOMPLETE, outcome(f))
        assertEquals(null, resolve(f).pois.single().displayName)
    }

    @Test fun duplicateTemplateRolesAreConflict() {
        val f = scalarFixture()
        assertEquals("ここは ワカバ", resolve(f).pois.single().displayName)
        f.bytes[f.at("template") + 5] = f.bytes[f.at("template") + 4]
        assertEquals(Gen2DeclaredSignAbi.Status.CONFLICT, outcome(f))
        assertEquals(null, resolve(f).pois.single().displayName)
    }

    @Test fun unsupportedTokensAndMissingTerminationNeverYieldFirstLine() {
        for (value in listOf(0x52, 0x18, 0x50, 0x17)) for (offset in listOf(2, 7)) {
            val f = scalarFixture()
            assertEquals("ここは ワカバ", resolve(f).pois.single().displayName)
            f.bytes[f.text + offset] = value.toByte()
            assertEquals("value=$value offset=$offset", null, resolve(f).pois.single().displayName)
        }
        val f = scalarFixture()
        assertEquals("ここは ワカバ", resolve(f).pois.single().displayName)
        f.bytes[f.text + 9] = 0x7f
        assertEquals(null, resolve(f).pois.single().displayName)
    }

    @Test fun scalarCompiledEndOverridesCodecWhitespace() = assertCompiledEndStopsRecord(0x7f)

    @Test fun scalarCompiledEndOverridesCodecGlyph() = assertCompiledEndStopsRecord(0xca)

    private fun assertCompiledEndStopsRecord(relocatedEnd: Int) {
        for (end in listOf(0x50, relocatedEnd)) for (prefix in listOf("00 ba ba", "00 ba ba 4f ba")) {
            val f = scalarFixture()
            assertEquals("ここは ワカバ", resolve(f).pois.single().displayName)
            f.bytes[f.at("textDispatch") + 2] = end.toByte()
            f.bytes[f.at("placeString") + 3] = end.toByte()
            f.bytes[f.at("done") + 6] = end.toByte()
            f.raw(f.text, "$prefix 57")
            assertEquals(Gen2DeclaredSignAbi.Status.RESOLVED, outcome(f))
            assertEquals("ここ", resolve(f).pois.single().displayName)
            // END exits both PlaceString and the text dispatcher; later prose/DONE is unreachable.
            f.raw(f.text, "$prefix %02x %02x ba 57".format(end, end))
            assertEquals("END=$end prefix=$prefix", null, resolve(f).pois.single().displayName)
        }
    }

    @Test fun scalarTextAndLiteralCannotCrossBankOrRecordBounds() {
        val mutations: List<(Gen2DeclaredSignFixture) -> Unit> = listOf(
            { f -> f.word(f.script + 1, f.attributes + 1) },
            { f -> f.word(f.script + 1, f.events) },
            { f -> f.word(f.script + 1, f.script + 1) },
            { f -> f.word(f.script + 1, 0xc000, false) },
            { f -> val end = (f.dataBank + 1) * 0x4000; f.word(f.script + 1, end - 3); f.raw(end - 3, "00 85 19") },
            { f -> f.bytes.fill(0x7f, f.text + 7, f.text + 97); f.bytes[f.text + 97] = 0x57 },
            { f -> f.word(f.at("literalAHandler") + 2, 0x3ffe); f.raw(0x3ffe, "ba ba") },
            { f -> f.word(f.at("textCommands"), f.at("textStart") + 0x4000, false) },
        )
        mutations.forEachIndexed { index, mutate ->
            val f = scalarFixture()
            assertEquals("ここは ワカバ", resolve(f).pois.single().displayName)
            mutate(f)
            assertEquals("bound $index", null, resolve(f).pois.single().displayName)
        }
    }

    @Test fun declaredNeighborBoundsScalarProseEvenWhenNeighborMalformed() {
        val f = scalarFixture()
        f.raw(f.events, "00 00 00 00 02 01 02 00 00 00 02 03 00 00 00 00")
        f.word(f.events + 8, f.script); f.word(f.events + 13, f.script + 8)
        f.raw(f.script + 8, "52"); f.word(f.script + 9, f.text + 16)
        assertEquals(listOf("ここは ワカバ", null), resolve(f).pois.map { it.displayName })
        f.word(f.script + 9, f.text + 9)
        // Root9 belongs to the neighbor, so the first record cannot borrow its DONE.
        f.raw(f.text + 9, "57 00 85 19 57")
        assertEquals(listOf(null, null), resolve(f).pois.map { it.displayName })
    }

    @Test fun scalarDictionaryCannotContinueFromHomeIntoAnotherBank() {
        val f = scalarFixture()
        assertEquals("ここは ワカバ", resolve(f).pois.single().displayName)
        val prefix = f.bytes.copyOfRange(f.at("dictionary"), f.at("dictionary") + 25)
        val zero = f.bytes.copyOfRange(f.at("dictionary") + 25, f.at("dictionary") + 29)
        val fallback = f.bytes.copyOfRange(f.at("scalar"), f.at("scalar") + 62)
        f.symbol("placeString", 0x3fef); f.symbol("nextPlace", 0x3ff0); f.symbol("nextChar", 0x3ffa)
        f.emit("placeString", "e5 1a fe 50 20 09 44 4d e1 c9 d1 13 c3 @nextPlace")
        zero.copyInto(f.bytes, 0x3ffe); prefix.copyInto(f.bytes, 0x4002); fallback.copyInto(f.bytes, 0x401b)
        f.word(0x401b + 13, f.at("nextChar")); f.word(0x401b + 60, f.at("nextChar"))
        f.emit("textStart", "54 5d 60 69 cd @placeString 62 6b 23 c9")
        f.emit("line", "e1 21 @lineOrigin e5 c3 @nextChar")
        f.emit("literalJoin", "cd @placeString 60 69 d1 c3 @nextChar")
        assertEquals(Gen2DeclaredSignAbi.Status.INCOMPLETE, outcome(f))
        assertEquals(null, resolve(f).pois.single().displayName)
    }

    @Test fun completeScalarDictionaryHonorsFortyBranchCeiling() {
        for (extraCount in listOf(34, 35)) {
            val f = scalarFixture()
            assertEquals("ここは ワカバ", resolve(f).pois.single().displayName)
            val used = setOf(0, 0x37, 0x1f, 0x4f, 0x57, 0x52, 0xa9, 0x85, 0x19, 0xb6, 0x7f, 0xba, 0xca, 0xdd, 0x50)
            val controls = (0..255).filter { it !in used }.take(extraCount)
            val fallback = f.bytes.copyOfRange(f.at("scalar"), f.at("scalar") + 62)
            f.symbol("extraDictionary", f.at("scalar"))
            f.emit("extraDictionary", controls.joinToString(" ") { "fe %02x ca @unknown".format(it) })
            fallback.copyInto(f.bytes, f.at("scalar") + controls.size * 5)
            assertEquals(if (extraCount == 34) Gen2DeclaredSignAbi.Status.RESOLVED else Gen2DeclaredSignAbi.Status.BUDGET, outcome(f))
            assertEquals(if (extraCount == 34) "ここは ワカバ" else null, resolve(f).pois.single().displayName)
        }
    }

    @Test fun literalBudgetIsPerRecordNotSharedAcrossSigns() {
        val f = scalarFixture()
        assertEquals("ここは ワカバ", resolve(f).pois.single().displayName)
        val fallback = f.bytes.copyOfRange(f.at("scalar"), f.at("scalar") + 62)
        f.symbol("extraDictionary", f.at("scalar"))
        f.emit("extraDictionary", "fe 60 ca @literalAHandler fe 61 ca @literalAHandler fe 62 ca @literalAHandler")
        fallback.copyInto(f.bytes, f.at("scalar") + 15)
        f.raw(f.events, "00 00 00 00 02 01 02 00 00 00 02 03 00 00 00 00")
        f.word(f.events + 8, f.script); f.word(f.events + 13, f.script + 8)
        f.raw(f.script + 8, "52"); f.word(f.script + 9, f.text + 20)
        f.raw(f.text, "00 37 a9 85 19 4f 1f 60 61 57")
        f.raw(f.text + 20, "00 62 a9 85 19 57")
        assertEquals(listOf("ここは ワカバ", "ここは ワカバ"), resolve(f).pois.map { it.displayName })
        f.raw(f.text + 9, "62 57") // Five different literal controls within the first record only.
        assertEquals(listOf(null, "ここは ワカバ"), resolve(f).pois.map { it.displayName })
    }

    @Test fun scalarCancellationAndBudgetsDoNotRetryLegacyOpcode() {
        for (limits in listOf(ResolutionLimits(maxProbeWorkPerDataset = 20), ResolutionLimits(maxProbeRootsPerDataset = 1), ResolutionLimits(maxDatasetExtentBytes = 16))) {
            val f = scalarFixture()
            assertEquals("ここは ワカバ", resolve(f).pois.single().displayName)
            assertEquals(null, Gen2LocalMapPoiResolver.resolve(RomImage(f.bytes), listOf(f.source), listOf(f.map),
                EngineFamily.GOLD_SILVER, JapanesePokemonTextCodecs.gen2, limits).pois.single().displayName)
        }
        var total = 0
        val positive = scalarFixture()
        Gen2LocalMapPoiResolver.resolve(RomImage(positive.bytes), listOf(positive.source), listOf(positive.map),
            EngineFamily.GOLD_SILVER, JapanesePokemonTextCodecs.gen2, cancellation = ParserCancellationToken { total++ })
        for (cut in listOf(1, 10, total - 1)) {
            val f = scalarFixture(); var checks = 0
            assertThrows(ParserCancellationException::class.java) {
                Gen2LocalMapPoiResolver.resolve(RomImage(f.bytes), listOf(f.source), listOf(f.map), EngineFamily.GOLD_SILVER,
                    JapanesePokemonTextCodecs.gen2, cancellation = ParserCancellationToken { if (++checks >= cut) throw ParserCancellationException() })
            }
        }
    }

    private fun resolve(f: Gen2DeclaredSignFixture, family: EngineFamily = EngineFamily.CRYSTAL) =
        Gen2LocalMapPoiResolver.resolve(RomImage(f.bytes), listOf(f.source), listOf(f.map), family, JapanesePokemonTextCodecs.gen2)

    private fun outcome(f: Gen2DeclaredSignFixture): Gen2DeclaredSignAbi.Status {
        val root = Gen2DeclaredSignAbi.resolve(RomImage(f.bytes), listOf(f.source), ResolutionLimits(), ParserCancellationToken.NONE)
        return root.abi?.outcome(f.bytes[f.script].toInt() and 255)?.status ?: root.status
    }

    private fun scalarFixture(shift: Int = 0, bankShift: Int = 0, alternate: Boolean = false, inline: Boolean = true) =
        Gen2DeclaredSignFixture(shift, bankShift).apply {
            val command = if (alternate) 0x72 else 0x52
            val roles = if (alternate) listOf(0x80, 0x81, 0x82, 0x83, 0x84) else listOf(0x47, 0x4d, 0x53, 0x49, 0x90)
            val literalAControl = if (alternate) 0x61 else 0x37
            val literalBControl = if (alternate) 0x62 else 0x1f
            val lineControl = if (alternate) 0x63 else 0x4f
            val doneControl = if (alternate) 0x64 else 0x57
            symbol("scriptTable", codeBank * 0x4000 + 0x3000 + shift)
            emit("scriptDispatch", "cd @getByte 21 @scriptTable ef c9")
            bytes[script] = command.toByte(); word(at("scriptTable") + command * 2, at("direct"))
            for ((opcode, name) in roles.zip(listOf("openHandler", "repeat", "waitHandler", "closeHandler", "endHandler"))) {
                word(at("scriptTable") + opcode * 2, at(name))
            }
            raw(at("template"), (roles.take(2) + listOf(255, 255) + roles.drop(2)).joinToString(" ") { "%02x".format(it) })
            if (inline) emit("mapTextbox", "f0 9f f5 78 d7 e5 cd @speech cd @graphicsC 3e 01 e0 da cd @graphicsE e1 cd @printText af e0 da f1 d7 c9")
            symbol("placeString", 0x2800 + shift)
            symbol("nextPlace", at("placeString") + 1); symbol("nextChar", at("placeString") + 11)
            symbol("dictionary", at("placeString") + 15); symbol("scalar", at("dictionary") + 29)
            listOf("literalAHandler", "literalBHandler", "literalJoin", "literalA", "literalB", "unknown").forEachIndexed { i, name -> symbol(name, 0x2c00 + shift + i * 0x40) }
            emit("textStart", "54 5d 60 69 cd @placeString 62 6b 23 c9")
            emit("placeString", "e5 1a fe 50 20 09 44 4d e1 c9 d1 13 c3 @nextPlace")
            emit("dictionary", "fe %02x ca @literalAHandler fe %02x ca @literalBHandler fe %02x ca @line fe %02x ca @done fe 52 ca @unknown a7 ca @unknown".format(literalAControl, literalBControl, lineControl, doneControl))
            emit("scalar", "fe e4 28 04 fe e5 20 07 47 cd @graphicsD c3 @nextChar fe 60 30 24 fe 40 30 11 fe 20 30 04 c6 80 18 02 c6 90 06 e5 cd @graphicsD 18 0f fe 44 30 04 c6 59 18 02 c6 86 06 e4 cd @graphicsD 22 cd @graphicsE c3 @nextChar")
            emit("line", "e1 21 @lineOrigin e5 c3 @nextChar")
            emit("literalAHandler", "d5 11 @literalA c3 @literalJoin")
            emit("literalBHandler", "d5 11 @literalB c3 @literalJoin")
            emit("literalJoin", "cd @placeString 60 69 d1 c3 @nextChar")
            // Independent public pokecrystal Japanese charmap: こ=ba は=ca を=dd space=7f.
            raw(at("literalA"), "ba ba ca 7f 50"); raw(at("literalB"), "dd 7f 50")
            // Deliberately fabricated two-line phrase; not the retained Japanese sign payload.
            raw(text, "00 %02x a9 85 19 %02x %02x b6 7f %02x".format(literalAControl, lineControl, literalBControl, doneControl))
        }
}
