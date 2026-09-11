package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiTextObligation
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs
import com.enrpau.dualscreendex.parser.text.KoreanGen2PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.PokemonTextTokenDecoder
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

    @Test fun japaneseGoldClearWindowEnvelopeRetainsStaticSignText() {
        val mutations: List<(Gen2DeclaredSignFixture) -> Unit> = listOf(
            { f -> f.word(f.at("clearWindow") + 7, 0xc011, false) },
            { f -> f.word(f.at("clearWindow") + 40, 0xd002, false) },
            { f -> f.word(f.at("clearWindow") + 4, 0xc000, false) },
            { f -> f.bytes[f.at("clearWindow") + 45] = 0 },
        )
        for ((index, mutate) in (listOf<(Gen2DeclaredSignFixture) -> Unit>({}) + mutations).withIndex()) {
            val f = scalarFixture(inline = false)
            f.symbol("clearRow", 0x3200)
            f.symbol("clearSprites", 0x3300)
            f.symbol("updateWindow", 0x3400)
            for ((name, address) in mapOf(
                "clearRow0" to 0xc000,
                "clearRow1" to 0xc010,
                "clearRow2" to 0xc020,
                "clearRow3" to 0xc030,
                "windowEnd" to 0xbfff,
                "windowLo" to 0xd000,
                "windowHi" to 0xd001,
            )) f.symbol(name, address, false)
            f.emit("clearWindow", "21 @clearRow0 cd @clearRow 21 @clearRow1 cd @clearRow " +
                "21 @clearRow2 cd @clearRow 21 @clearRow3 cd @clearRow af cd @clearSprites " +
                "af 21 @windowEnd 32 32 7d ea @windowLo 7c ea @windowHi cd @updateWindow c9")
            mutate(f)
            assertEquals(if (index == 0) "ここは ワカバ" else null, resolve(f).pois.single().displayName)
        }
    }

    @Test fun declaredStandardServiceScriptRetainsRomNativeText() {
        val f = standardSignFixture(16)
        val poi = resolve(f).pois.single()
        assertEquals("ここは ワカバ", poi.displayName)
        assertEquals(com.enrpau.dualscreendex.parser.catalog.LocalMapPoiService.POKEMON_CENTER, poi.service)
    }

    @Test fun declaredFarStandardServiceScriptRetainsRomNativeText() {
        val f = standardSignFixture(17)
        f.emit("direct", "cd @getByte ea @textBankState cd @getByte ea @textPointerState " +
            "cd @getByte ea @textPointerHi 06 #codeBank 21 @template c3 @scriptJump")
        f.raw(f.at("stdTarget"), "52 07")
        f.word(f.at("stdTarget") + 2, f.at("stdText"))

        val poi = resolve(f).pois.single()
        assertEquals("ここは ワカバ", poi.displayName)
        assertEquals(com.enrpau.dualscreendex.parser.catalog.LocalMapPoiService.MART, poi.service)
    }

    @Test fun malformedStandardServiceDeclarationsFailClosed() {
        val mutations: List<(Gen2DeclaredSignFixture) -> Unit> = listOf(
            { f -> f.bytes[f.at("jumpStdHandler")] = 0 },
            { f -> f.bytes[f.at("stdResolver") + 12] = 0 },
            { f -> f.bytes[f.at("stdTable") + 16 * 3] = 0x7f },
            { f -> f.bytes[f.at("stdTarget")] = 0x7f },
            { f -> f.bytes[f.at("stdText") + 9] = 0 },
        )
        for (mutate in mutations) {
            val f = standardSignFixture(16)
            mutate(f)
            val poi = resolve(f).pois.single()
            assertEquals(null, poi.displayName)
            assertEquals(LocalMapPoiTextObligation.UNRESOLVED, poi.textObligation)
            assertEquals(com.enrpau.dualscreendex.parser.catalog.LocalMapPoiService.POKEMON_CENTER, poi.service)
        }
    }

    @Test fun declaredTextlessStandardScriptIsNoText() {
        val f = standardSignFixture(20)
        bindTextlessBehaviorCommands(f)
        f.raw(f.at("stdTarget"), "2f 34 12 4b 0f 2f 78 56 90")

        val poi = resolve(f).pois.single()
        assertEquals(null, poi.displayName)
        assertEquals(LocalMapPoiTextObligation.NO_TEXT, poi.textObligation)
    }

    @Test fun declaredOpenEndScriptIsNoTextAcrossDirectionalSigns() {
        for (kind in 0..4) {
            val f = scalarFixture()
            f.bytes[f.events + 10] = kind.toByte()
            f.raw(f.script, "47 90")

            val poi = resolve(f).pois.single()
            assertEquals(null, poi.displayName)
            assertEquals(LocalMapPoiTextObligation.NO_TEXT, poi.textObligation)
        }
    }

    @Test fun malformedTextlessBehaviorScriptsRemainUnresolved() {
        val mutations: List<(Gen2DeclaredSignFixture) -> Unit> = listOf(
            { f -> f.bytes[f.at("pauseHandler") + 5]++ },
            { f -> f.word(f.at("pauseHandler") + 7, f.at("scriptDelay") + 1, false) },
            { f -> f.word(f.at("pauseHandler") + 15, f.at("scriptDelay") + 1, false) },
            { f -> f.word(f.at("pauseHandler") + 12, 0xc000, false) },
            { f -> f.word(f.at("soundHandler") + 9, 0xc000, false) },
            { f -> f.bytes[f.at("stdTarget") + 8] = 0x7f },
            { f -> f.raw(f.at("stdTarget"), "2f 34 12 4b 0f 52"); f.word(f.at("stdTarget") + 6, f.at("stdText")) },
        )
        for (mutate in mutations) {
            val f = standardSignFixture(20)
            bindTextlessBehaviorCommands(f)
            f.raw(f.at("stdTarget"), "2f 34 12 4b 0f 2f 78 56 90")
            mutate(f)
            val poi = resolve(f).pois.single()
            assertEquals(null, poi.displayName)
            assertEquals(LocalMapPoiTextObligation.UNRESOLVED, poi.textObligation)
        }
    }

    @Test fun declaredRuntimeStandardScriptIsContextual() {
        val f = standardSignFixture(12)
        f.symbol("setValueHandler", f.codeBank * 0x4000 + 0x2b00)
        f.symbol("specialHandler", f.codeBank * 0x4000 + 0x2b40)
        f.symbol("scriptVar", 0xd250, false)
        f.word(f.at("scriptTable") + 0x15 * 2, f.at("setValueHandler"))
        f.word(f.at("scriptTable") + 0x0f * 2, f.at("specialHandler"))
        f.emit("setValueHandler", "cd @getByte ea @scriptVar c9")
        f.symbol("specialDispatch", 8 * 0x4000 + 0x100)
        f.emit("specialHandler", "cd @getByte 5f cd @getByte 57 3e 08 21 @specialDispatch cf c9")
        f.raw(f.at("stdTarget"), "47 15 03 0f 34 12")

        val poi = resolve(f).pois.single()
        assertEquals(null, poi.displayName)
        assertEquals(LocalMapPoiTextObligation.CONTEXTUAL_TEXT, poi.textObligation)
    }

    @Test fun declaredOpenWriteTextChainsRetainRomNativeTextForDirectionalSigns() {
        for (kind in 0..4) {
            val f = scalarFixture()
            f.bytes[f.events + 10] = kind.toByte()
            f.symbol("writeHandler", f.codeBank * 0x4000 + 0x2b00)
            f.word(f.at("scriptTable") + 0x4c * 2, f.at("writeHandler"))
            f.emit("writeHandler", "cd @getByte 6f cd @getByte 67 fa @scriptBankState 47 cd @mapTextbox c9")
            f.raw(f.script, "47 4c")
            f.word(f.script + 2, f.text)

            val poi = resolve(f).pois.single()
            assertEquals("ここは ワカバ", poi.displayName)
            assertEquals(LocalMapPoiTextObligation.DIRECT_TEXT, poi.textObligation)
        }
    }

    @Test fun directionalSignDispatchMustConvergeOnTheSelectedScriptPath() {
        val mutations: List<(Gen2DeclaredSignFixture) -> Unit> = listOf(
            { f -> f.bytes[f.at("bgDirectionCheck") + 4] = 0 },
            { f -> f.bytes[f.at("bgRead") - 0x40 + 1] = 0x10 },
            { f -> f.bytes[f.at("bgRead") - 0x40 + 4 + 3]++ },
            { f -> f.word(f.at("bgTable") + 8, f.at("bgRead")) },
        )
        for (mutate in mutations) {
            val f = scalarFixture()
            f.bytes[f.events + 10] = 1
            mutate(f)
            val poi = resolve(f).pois.single()
            assertEquals(null, poi.displayName)
            assertEquals(LocalMapPoiTextObligation.UNRESOLVED, poi.textObligation)
        }
    }
    @Test fun compiledFacePlayerOpenWriteTextChainRetainsRomNativeText() {
        val f = scalarFixture()
        f.symbol("faceHandler", f.codeBank * 0x4000 + 0x2b00)
        f.symbol("writeHandler", f.codeBank * 0x4000 + 0x2b80)
        f.symbol("relativeFacing", 8 * 0x4000 + 0x100)
        f.symbol("applyFacing", f.codeBank * 0x4000 + 0x3500)
        f.word(f.at("scriptTable") + 0x6a * 2, f.at("faceHandler"))
        f.word(f.at("scriptTable") + 0x4c * 2, f.at("writeHandler"))
        f.emit("faceHandler", "f0 d1 a7 c8 16 00 f0 d1 5f 3e 08 21 @relativeFacing cf " +
            "7a 87 87 5f f0 d1 57 cd @applyFacing c9")
        f.emit("writeHandler", "cd @getByte 6f cd @getByte 67 fa @scriptBankState 47 cd @mapTextbox c9")
        f.raw(f.script, "6a 47 4c")
        f.word(f.script + 3, f.text)

        val poi = resolve(f).pois.single()
        assertEquals("ここは ワカバ", poi.displayName)
        assertEquals(LocalMapPoiTextObligation.DIRECT_TEXT, poi.textObligation)
    }

    @Test fun compiledReanchorSpecialChainsAreContextual() {
        for (kind in 0..4) {
            val f = scalarFixture()
            f.bytes[f.events + 10] = kind.toByte()
            f.symbol("reanchorHandler", f.codeBank * 0x4000 + 0x2b00)
            f.symbol("specialHandler", f.codeBank * 0x4000 + 0x2b40)
            f.symbol("reanchorMap", 0x3500)
            f.word(f.at("scriptTable") + 0x48 * 2, f.at("reanchorHandler"))
            f.word(f.at("scriptTable") + 0x0f * 2, f.at("specialHandler"))
            f.emit("reanchorHandler", "cd @reanchorMap cd @getByte c9")
            f.symbol("specialDispatch", 8 * 0x4000 + 0x100)
        f.emit("specialHandler", "cd @getByte 5f cd @getByte 57 3e 08 21 @specialDispatch cf c9")
            f.raw(f.script, "48 01 0f 34 12 49")

            val poi = resolve(f).pois.single()
            assertEquals(null, poi.displayName)
            assertEquals(LocalMapPoiTextObligation.CONTEXTUAL_TEXT, poi.textObligation)
        }
    }

    @Test fun malformedSpecialDispatchRemainsUnresolved() {
        val mutations: List<(Gen2DeclaredSignFixture) -> Unit> = listOf(
            { f -> f.bytes[f.at("specialHandler") + 13] = 0xd7.toByte() },
            { f -> f.word(f.at("specialHandler") + 11, 0xc000, false) },
        )
        for (mutate in mutations) {
            val f = scalarFixture()
            f.bytes[f.events + 10] = 3
            f.symbol("reanchorHandler", f.codeBank * 0x4000 + 0x2b00)
            f.symbol("specialHandler", f.codeBank * 0x4000 + 0x2b40)
            f.symbol("reanchorMap", 0x3500)
            f.symbol("specialDispatch", 8 * 0x4000 + 0x100)
            f.word(f.at("scriptTable") + 0x48 * 2, f.at("reanchorHandler"))
            f.word(f.at("scriptTable") + 0x0f * 2, f.at("specialHandler"))
            f.emit("reanchorHandler", "cd @reanchorMap cd @getByte c9")
            f.emit("specialHandler", "cd @getByte 5f cd @getByte 57 3e 08 21 @specialDispatch cf c9")
            f.raw(f.script, "48 01 0f 34 12 49")
            mutate(f)
            assertEquals(LocalMapPoiTextObligation.UNRESOLVED, resolve(f).pois.single().textObligation)
        }
    }

    @Test fun compiledDynamicMenuIsContextual() {
        val f = scalarFixture()
        f.symbol("dynamicMenuHandler", f.codeBank * 0x4000 + 0x2b00)
        f.symbol("dynamicMenu", 8 * 0x4000 + 0x100)
        f.symbol("scriptVar", 0xd250, false)
        f.word(f.at("scriptTable") + 0x60 * 2, f.at("dynamicMenuHandler"))
        f.emit("dynamicMenuHandler", "af ea @scriptVar cd @getByte 5f cd @getByte 57 " +
            "fa @scriptBankState 47 3e 08 21 @dynamicMenu cf d8 3e 01 ea @scriptVar c9")
        f.raw(f.script, "47 60 34 12")

        assertEquals(LocalMapPoiTextObligation.CONTEXTUAL_TEXT, resolve(f).pois.single().textObligation)
    }

    @Test fun malformedDynamicMenusRemainUnresolved() {
        val mutations: List<(Gen2DeclaredSignFixture) -> Unit> = listOf(
            { f -> f.word(f.at("dynamicMenuHandler") + 26, f.at("scriptVar") + 1, false) },
            { f -> f.word(f.at("dynamicMenuHandler") + 19, 0xc000, false) },
            { f -> f.bytes[f.at("dynamicMenuHandler") + 22] = 0xc9.toByte() },
            { f -> f.bytes[f.at("dynamicMenuHandler") + 24] = 2 },
        )
        for (mutate in mutations) {
            val f = scalarFixture()
            f.symbol("dynamicMenuHandler", f.codeBank * 0x4000 + 0x2b00)
            f.symbol("dynamicMenu", 8 * 0x4000 + 0x100)
            f.symbol("scriptVar", 0xd250, false)
            f.word(f.at("scriptTable") + 0x60 * 2, f.at("dynamicMenuHandler"))
            f.emit("dynamicMenuHandler", "af ea @scriptVar cd @getByte 5f cd @getByte 57 " +
                "fa @scriptBankState 47 3e 08 21 @dynamicMenu cf d8 3e 01 ea @scriptVar c9")
            f.raw(f.script, "47 60 34 12")
            mutate(f)
            assertEquals(LocalMapPoiTextObligation.UNRESOLVED, resolve(f).pois.single().textObligation)
        }
    }

    @Test fun compiledRandomSelectorsAreContextualAcrossDirectionalSigns() {
        for (kind in 0..4) {
            val f = scalarFixture()
            f.bytes[f.events + 10] = kind.toByte()
            f.symbol("randomSelector", f.codeBank * 0x4000 + 0x2b00)
            f.symbol("randomDivide", f.codeBank * 0x4000 + 0x2c00)
            f.symbol("randomSource", 0x3000)
            f.symbol("simpleDivide", 0x3100)
            f.symbol("scriptVar", 0xd250, false)
            f.word(f.at("scriptTable") + 0x17 * 2, f.at("randomSelector"))
            f.emit("randomSelector", "cd @getByte ea @scriptVar a7 c8 4f cd @randomDivide a7 28 10 " +
                "47 af 90 47 c5 cd @randomSource c1 f0 d3 b8 30 f6 18 07 c5 " +
                "cd @randomSource c1 f0 d3 f5 fa @scriptVar 4f f1 cd @simpleDivide ea @scriptVar c9")
            f.raw(f.at("randomDivide"), "af 47 91 04 91 30 fc 05 81 c9")
            f.raw(f.script, "17 04")

            val poi = resolve(f).pois.single()
            assertEquals(null, poi.displayName)
            assertEquals(LocalMapPoiTextObligation.CONTEXTUAL_TEXT, poi.textObligation)
        }
    }

    @Test fun compiledFlagSelectorsAreContextualAcrossDirectionalSigns() {
        for (kind in 0..4) for (open in listOf(false, true)) {
            val f = scalarFixture()
            f.bytes[f.events + 10] = kind.toByte()
            f.symbol("flagSelector", f.codeBank * 0x4000 + 0x2b00)
            f.symbol("flagAction", f.codeBank * 0x4000 + 0x3600)
            f.symbol("scriptVar", 0xd250, false)
            f.word(f.at("scriptTable") + 0x31 * 2, f.at("flagSelector"))
            f.emit("flagSelector", "cd @getByte 5f cd @getByte 57 06 02 cd @flagAction 79 a7 " +
                "28 02 3e 01 ea @scriptVar c9")
            f.raw(f.script, if (open) "47 31 34 12" else "31 34 12")

            val poi = resolve(f).pois.single()
            assertEquals(null, poi.displayName)
            assertEquals(LocalMapPoiTextObligation.CONTEXTUAL_TEXT, poi.textObligation)
        }
    }

    @Test fun malformedFlagSelectorsRemainUnresolved() {
        val mutations: List<(Gen2DeclaredSignFixture) -> Unit> = listOf(
            { f -> f.bytes[f.at("flagSelector") + 4] = 0 },
            { f -> f.word(f.at("flagSelector") + 11, 0xc000, false) },
            { f -> f.bytes[f.at("flagSelector") + 15] = 0 },
            { f -> f.bytes[f.at("flagSelector") + 16]++ },
            { f -> f.bytes[f.at("flagSelector") + 18] = 2 },
            { f -> f.word(f.at("flagSelector") + 20, 0x2000, false) },
        )
        for (mutate in mutations) {
            val f = scalarFixture()
            f.symbol("flagSelector", f.codeBank * 0x4000 + 0x2b00)
            f.symbol("flagAction", f.codeBank * 0x4000 + 0x3600)
            f.symbol("scriptVar", 0xd250, false)
            f.word(f.at("scriptTable") + 0x31 * 2, f.at("flagSelector"))
            f.emit("flagSelector", "cd @getByte 5f cd @getByte 57 06 02 cd @flagAction 79 a7 " +
                "28 02 3e 01 ea @scriptVar c9")
            f.raw(f.script, "31 34 12")
            mutate(f)
            assertEquals(LocalMapPoiTextObligation.UNRESOLVED, resolve(f).pois.single().textObligation)
        }
    }

    @Test fun callScriptAndEndBindRunningFlagSeparatelyFromMode() {
        for (shift in listOf(0, 0x20)) for (inline in listOf(false, true)) {
            val f = scalarFixture(shift, 2, inline = inline)
            // CallScript sets wScriptRunning; Script_end clears that same flag, then wScriptMode.
            f.emit("callScript", "ea @scriptBankState 7d ea @scriptPointerState 7c ea @scriptPointerHi 3e ff ea @scriptRunning 37 c9")
            val positive = resolve(f).pois.single()
            assertEquals("ここは ワカバ", positive.displayName)
            f.word(f.at("endHandler") + 8, f.at("scriptRunning") + 8, false)
            assertEquals(positive.copy(textObligation = LocalMapPoiTextObligation.UNRESOLVED, displayName = null), resolve(f).pois.single())
            f.word(f.at("endHandler") + 8, f.at("scriptRunning"), false)
            f.word(f.at("endHandler") + 13, f.at("scriptRunning"), false)
            assertEquals(positive.copy(textObligation = LocalMapPoiTextObligation.UNRESOLVED, displayName = null), resolve(f).pois.single())
        }
    }

    @Test fun inlineCloseBindsOamSaveRestoreAndBoundedCallTargets() {
        val mutations: List<(Gen2DeclaredSignFixture) -> Unit> = listOf(
            { f -> f.bytes[f.at("closeHandler") + 2] = 0 },
            { f -> f.bytes[f.at("closeHandler") + 4] = 2 },
            { f -> f.bytes[f.at("closeHandler") + 6] = 0xdb.toByte() },
            { f -> f.bytes[f.at("closeHandler") + 10] = 0 },
            { f -> f.bytes[f.at("closeHandler") + 12] = 0xdb.toByte() },
            { f -> f.bytes[f.at("closeHandler") + 16] = 0 },
            { f -> f.word(f.at("closeHandler") + 8, 0xc000, false) },
            { f -> f.word(f.at("closeHandler") + 14, 0xc000, false) },
            { f -> for (offset in listOf(1, 6, 12)) f.bytes[f.at("closeHandler") + offset] = 0xdb.toByte() },
        )
        for (shift in listOf(0, 0x20)) for (shape in listOf("pair", "scalarSetup", "scalarInline")) for (mutate in mutations) {
            val f = if (shape == "pair") Gen2DeclaredSignFixture(shift, 2)
                else scalarFixture(shift, 2, inline = shape == "scalarInline")
            f.symbol("closeTransfer", 0x3300 + shift)
            f.symbol("closeText", 0x3400 + shift)
            f.emit("closeHandler", "f0 da f5 3e 01 e0 da cd @closeTransfer f1 e0 da cd @closeText c9")
            fun result() = Gen2LocalMapPoiResolver.resolve(RomImage(f.bytes), listOf(f.source), listOf(f.map),
                EngineFamily.CRYSTAL, if (shape == "pair") KoreanGen2PokemonTextCodec.codec else JapanesePokemonTextCodecs.gen2)
            val positive = result().pois.single()
            assertEquals(shape, if (shape == "pair") "이곳은 연두마을" else "ここは ワカバ", positive.displayName)
            mutate(f)
            assertEquals(positive.copy(textObligation = LocalMapPoiTextObligation.UNRESOLVED, displayName = null), result().pois.single())
        }
    }

    @Test fun declarationRejectionsRetainBoundedReasonsWithoutLosingNumericPois() {
        val rootFailure = scalarFixture()
        val numeric = resolve(rootFailure).pois.single().copy(
            textObligation = LocalMapPoiTextObligation.UNRESOLVED,
            displayName = null,
        )
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

    @Test fun pairedRuntimeSubstitutionRequiresCompiledWramAuthority() {
        val f = Gen2DeclaredSignFixture(0x20, 2)
        f.symbol("runtimePlayer", 0x3600 + f.shift)
        f.symbol("literalJoin", 0x3680 + f.shift)
        f.symbol("runtimeText", 0xd300 + f.shift, false)
        val dictionary = f.at("placeString") + 20
        f.raw(dictionary, "fe 51 ca 00 00 fe 5a ca 00 00 fe 5e ca 00 00")
        f.word(dictionary + 3, f.at("runtimePlayer"))
        f.word(dictionary + 8, f.at("line"))
        f.word(dictionary + 13, f.at("done"))
        f.emit("runtimePlayer", "d5 11 @runtimeText c3 @literalJoin")
        f.emit("literalJoin", "cd @placeString 60 69 d1 c3 @nextChar")
        f.raw(f.text, "00 51 5e")

        fun result() = Gen2LocalMapPoiResolver.resolve(
            RomImage(f.bytes), listOf(f.source), listOf(f.map),
            EngineFamily.GOLD_SILVER, KoreanGen2PokemonTextCodec.codec,
        ).pois.single()
        assertEquals(LocalMapPoiTextObligation.CONTEXTUAL_TEXT, result().textObligation)
        assertEquals(null, result().displayName)

        f.raw(f.text, "00 07 9c 01 67 07 8a 7f 07 0c 03 2e 04 46 07 8b 5a 51 5e")
        assertEquals(LocalMapPoiTextObligation.DIRECT_TEXT, result().textObligation)
        assertEquals("이곳은 연두마을", result().displayName)

        f.raw(f.text, "00 51 5e")
        f.symbol("runtimeText", 0x3700 + f.shift)
        f.emit("runtimePlayer", "d5 11 @runtimeText c3 @literalJoin")
        assertEquals(LocalMapPoiTextObligation.UNRESOLVED, result().textObligation)
        assertEquals(null, result().displayName)
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
            assertEquals(
                name,
                positive.copy(textObligation = LocalMapPoiTextObligation.UNRESOLVED, displayName = null),
                resolve(f).pois.single(),
            )
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

    @Test fun compiledRuntimeTextControlIsContextual() {
        val f = scalarFixture()
        f.symbol("runtimeName", 0xd099, false)
        f.emit("unknown", "d5 11 @runtimeName c3 @literalJoin")
        f.raw(f.text, "00 ba ba 52 ba 57")
        val poi = resolve(f).pois.single()
        assertEquals(null, poi.displayName)
        assertEquals(LocalMapPoiTextObligation.CONTEXTUAL_TEXT, poi.textObligation)
    }

    @Test fun compiledGenderedRuntimeTextControlIsContextual() {
        val f = scalarFixture()
        bindGenderedRuntimeText(f)
        f.raw(f.text, "00 ba 52 ba 57")
        val poi = resolve(f).pois.single()
        assertEquals(null, poi.displayName)
        assertEquals(LocalMapPoiTextObligation.CONTEXTUAL_TEXT, poi.textObligation)
    }

    @Test fun genderedRuntimeTextAfterFirstHeadlineDoesNotEraseStaticHeadline() {
        val f = scalarFixture()
        bindGenderedRuntimeText(f)
        f.raw(f.text, "00 ba ba 4f 52 57")
        assertEquals("ここ", resolve(f).pois.single().displayName)
    }

    @Test fun malformedRuntimeTextControlsRemainUnresolved() {
        val mutations: List<(Gen2DeclaredSignFixture) -> Unit> = listOf(
            { f -> f.word(f.at("unknown") + 2, 0x2000, false) },
            { f -> f.word(f.at("unknown") + 5, f.at("literalJoin") + 1) },
        )
        for (mutate in mutations) {
            val f = scalarFixture()
            f.symbol("runtimeName", 0xd099, false)
            f.emit("unknown", "d5 11 @runtimeName c3 @literalJoin")
            f.raw(f.text, "00 ba ba 52 ba 57")
            mutate(f)
            val poi = resolve(f).pois.single()
            assertEquals(null, poi.displayName)
            assertEquals(LocalMapPoiTextObligation.UNRESOLVED, poi.textObligation)
        }
    }

    @Test fun compiledParagraphControlCompletesDeclaredHeadline() {
        val f = scalarFixture()
        bindParagraphLayout(f)
        f.raw(f.text, "00 ba ba 52 ba 57")
        assertEquals("ここ", resolve(f).pois.single().displayName)
    }

    @Test fun compiledMobileParagraphControlCompletesDeclaredHeadline() {
        val f = scalarFixture()
        bindMobileParagraphLayout(f)
        f.raw(f.text, "00 ba ba 52 ba 57")
        assertEquals("ここ", resolve(f).pois.single().displayName)
    }

    @Test fun malformedParagraphControlsRejectDeclaredHeadline() {
        val mutations: List<(Gen2DeclaredSignFixture) -> Unit> = listOf(
            { f -> f.bytes[f.at("unknown") + 7]++ },
            { f -> f.word(f.at("unknown") + 2, 0x2000, false) },
            { f -> f.word(f.at("unknown") + 39, f.at("nextChar") + 1) },
        )
        for (mutate in mutations) {
            val f = scalarFixture()
            bindParagraphLayout(f)
            f.raw(f.text, "00 ba ba 52 ba 57")
            mutate(f)
            assertEquals(null, resolve(f).pois.single().displayName)
        }
    }

    @Test fun compiledContinuationWrapperCompletesDeclaredHeadline() {
        for (japaneseVariant in listOf(false, true)) {
            val f = scalarFixture()
            bindContinuationLayout(f, japaneseVariant)
            f.raw(f.text, "00 ba ba 52 ba 57")
            assertEquals("variant=$japaneseVariant", "ここ", resolve(f).pois.single().displayName)
        }
    }

    @Test fun compiledMobileContinuationWrapperCompletesDeclaredHeadline() {
        val f = scalarFixture()
        bindContinuationLayout(f, mobileVariant = true)
        f.raw(f.text, "00 ba ba 52 ba 57")
        assertEquals("ここ", resolve(f).pois.single().displayName)
    }

    @Test fun malformedContinuationMarkersRejectDeclaredHeadline() {
        for (marker in listOf("52 50", "4b 57", "4b 50")) {
            val f = scalarFixture()
            bindContinuationLayout(f)
            f.raw(f.text, "00 ba ba 52 ba 57")
            when (marker) {
                "4b 50" -> f.bytes[f.at("contInner") + 5]++
                else -> f.raw(f.at("contMarker"), marker)
            }
            assertEquals(marker, null, resolve(f).pois.single().displayName)
        }
    }

    @Test fun ratifiedStaticSubstitutionInsideCompiledLiteralDecodes() {
        val f = scalarFixture()
        f.raw(f.at("literalA"), "ba 14 50")
        f.raw(f.text, "00 37 ba 57")
        assertEquals("こナﾞこ", resolve(f).pois.single().displayName)
    }

    @Test fun japaneseGen2RatifiedStaticSubstitutionsDecodeInDeclaredSigns() {
        val substitutions = linkedMapOf(
            0x14 to "ナﾞ",
            0x18 to "ノ゛",
            0x1d to "に ",
            0x1e to "って",
            0x1f to "を ",
            0x22 to "た！",
            0x23 to "こうげき",
            0x24 to "は ",
            0x25 to "の ",
            0x35 to "ばん どうろ",
            0x36 to "わたし",
            0x37 to "ここは ",
            0x4a to "が ",
            0x54 to "ポケモン",
            0x56 to "⋯⋯",
            0x5b to "パソコン",
            0x5c to "わざマシン",
            0x5d to "トレーナー",
            0x5e to "ロケットだん",
        )
        for ((value, text) in substitutions) {
            val f = scalarFixture()
            f.raw(f.text, "00 ba %02x ba 57".format(value))
            assertEquals("value=$value", "こ${text}こ", resolve(f).pois.single().displayName)
        }
    }

    @Test fun unratifiedStaticSubstitutionRemainsRejectedInDeclaredSigns() {
        val f = scalarFixture()
        f.raw(f.text, "00 ba 56 ba 57")
        val japanese = JapanesePokemonTextCodecs.gen2
        val unratified = PokemonTextCodec(
            id = "test-unratified-gen2-ja",
            version = 1,
            language = LanguageTag.JAPANESE,
            applicableGenerations = setOf(2),
            applicablePlatforms = setOf(Platform.GB, Platform.GBC),
            terminator = 0x50,
            tokenDecoder = PokemonTextTokenDecoder { rom, offset, endExclusive ->
                japanese.decodeToken(rom, offset, endExclusive)
            },
        )
        assertEquals(null, Gen2LocalMapPoiResolver.resolve(
            RomImage(f.bytes), listOf(f.source), listOf(f.map), EngineFamily.CRYSTAL, unratified,
        ).pois.single().displayName)
    }

    @Test fun unratifiedRuntimeSubstitutionAfterFirstHeadlineDoesNotEraseStaticHeadline() {
        val f = scalarFixture()
        f.raw(f.text, "00 ba ba 4f 56 ba 57")
        val japanese = JapanesePokemonTextCodecs.gen2
        val unratified = PokemonTextCodec(
            id = "test-unratified-gen2-ja",
            version = 1,
            language = LanguageTag.JAPANESE,
            applicableGenerations = setOf(2),
            applicablePlatforms = setOf(Platform.GB, Platform.GBC),
            terminator = 0x50,
            tokenDecoder = PokemonTextTokenDecoder { rom, offset, endExclusive ->
                japanese.decodeToken(rom, offset, endExclusive)
            },
        )
        val poi = Gen2LocalMapPoiResolver.resolve(
            RomImage(f.bytes), listOf(f.source), listOf(f.map), EngineFamily.CRYSTAL, unratified,
        ).pois.single()
        assertEquals("ここ", poi.displayName)
        assertEquals(LocalMapPoiTextObligation.DIRECT_TEXT, poi.textObligation)
    }

    @Test fun unsupportedTokensAndMissingTerminationNeverYieldFirstLine() {
        for (value in listOf(0x52, 0x50, 0x17)) for (offset in listOf(2, 7)) {
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

    @Test fun boundedLongContinuationDoesNotEraseStaticFirstHeadline() {
        val f = scalarFixture()
        f.raw(f.text, (listOf(0x00, 0xba, 0xba, 0x4f) + List(133) { 0xba } + 0x57)
            .joinToString(" ") { "%02x".format(it) })
        val poi = resolve(f).pois.single()
        assertEquals("ここ", poi.displayName)
        assertEquals(LocalMapPoiTextObligation.DIRECT_TEXT, poi.textObligation)
    }

    @Test fun longDeclaredRecordRemainsBoundedAndRequiresDone() {
        val f = scalarFixture()
        f.raw(f.text, (listOf(0x00, 0xba, 0xba, 0x4f) + List(124) { 0xba } + 0x57)
            .joinToString(" ") { "%02x".format(it) })
        assertEquals("ここ", resolve(f).pois.single().displayName)
        f.bytes[f.text + 128] = 0x7f
        assertEquals(null, resolve(f).pois.single().displayName)
    }

    @Test fun scalarTextAndLiteralCannotCrossBankOrRecordBounds() {
        val mutations: List<(Gen2DeclaredSignFixture) -> Unit> = listOf(
            { f -> f.word(f.script + 1, f.attributes + 1) },
            { f -> f.word(f.script + 1, f.events) },
            { f -> f.word(f.script + 1, f.script + 1) },
            { f -> f.word(f.script + 1, 0xc000, false) },
            { f -> val end = (f.dataBank + 1) * 0x4000; f.word(f.script + 1, end - 3); f.raw(end - 3, "00 85 19") },
            { f -> f.bytes.fill(0x7f, f.text + 7, f.text + 256); f.bytes[f.text + 256] = 0x57 },
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
        val fallback = f.bytes.copyOfRange(f.at("scalar"), f.at("scalar") + 62)
        f.symbol("extraDictionary", f.at("scalar"))
        f.emit("extraDictionary", (0x60..0x66).joinToString(" ") {
            "fe %02x ca @literalAHandler".format(it)
        })
        fallback.copyInto(f.bytes, f.at("scalar") + 35)
        f.raw(f.events, "00 00 00 00 02 01 02 00 00 00 02 03 00 00 00 00")
        f.word(f.events + 8, f.script); f.word(f.events + 13, f.script + 8)
        f.raw(f.script + 8, "52"); f.word(f.script + 9, f.text + 20)
        f.raw(f.text, "00 37 1f 60 61 62 63 64 65 57")
        f.raw(f.text + 20, "00 37 a9 85 19 57")
        assertEquals(listOf(true, true), resolve(f).pois.map { it.displayName != null })
        f.raw(f.text + 9, "66 57")
        assertEquals(listOf(false, true), resolve(f).pois.map { it.displayName != null })
    }

    @Test fun literalBudgetAppliesOnlyToTheDisplayedHeadline() {
        val f = scalarFixture()
        val fallback = f.bytes.copyOfRange(f.at("scalar"), f.at("scalar") + 62)
        f.symbol("extraDictionary", f.at("scalar"))
        f.emit("extraDictionary", (0x60..0x66).joinToString(" ") {
            "fe %02x ca @literalAHandler".format(it)
        })
        fallback.copyInto(f.bytes, f.at("scalar") + 35)
        f.raw(f.text, "00 ba ba 4f 37 1f 60 61 62 63 64 65 66 57")
        val poi = resolve(f).pois.single()
        assertEquals("ここ", poi.displayName)
        assertEquals(LocalMapPoiTextObligation.DIRECT_TEXT, poi.textObligation)
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

    private fun bindGenderedRuntimeText(f: Gen2DeclaredSignFixture) {
        f.symbol("runtimeName", 0xd099, false)
        f.symbol("genderState", 0xd09a, false)
        f.symbol("maleSuffix", 0x1a00)
        f.symbol("femaleSuffix", 0x1a10)
        f.emit("unknown", "d5 11 @runtimeName cd @placeString 60 69 fa @genderState cb 47 " +
            "11 @maleSuffix 28 05 11 @femaleSuffix 18 00 cd @placeString 60 69 d1 c3 @nextChar")
        f.raw(f.at("maleSuffix"), "ba 50")
        f.raw(f.at("femaleSuffix"), "bb 50")
    }

    private fun bindParagraphLayout(f: Gen2DeclaredSignFixture) {
        f.symbol("linkMode", 0xd050, false)
        f.symbol("loadCursor", 0x1200)
        f.symbol("waitBg", 0x1300)
        f.symbol("prompt", 0x1400)
        f.symbol("layoutOrigin", 0xc4a5, false)
        f.symbol("clearBox", 0x1500)
        f.symbol("unloadCursor", 0x1600)
        f.symbol("delayFrames", 0x1700)
        f.symbol("layoutOrigin2", 0xc4b9, false)
        f.emit("unknown", "d5 fa @linkMode fe 03 28 03 cd @loadCursor cd @waitBg cd @prompt " +
            "21 @layoutOrigin 01 12 04 cd @clearBox cd @unloadCursor 0e 14 cd @delayFrames " +
            "21 @layoutOrigin2 d1 c3 @nextChar")
    }

    private fun bindMobileParagraphLayout(f: Gen2DeclaredSignFixture) {
        f.symbol("linkMode", 0xd050, false)
        f.symbol("loadCursor", 0x1200)
        f.symbol("waitBg", 0x1300)
        f.symbol("prompt", 0x1400)
        f.symbol("layoutOrigin", 0xc5a5, false)
        f.symbol("clearBox", 0x1500)
        f.symbol("unloadCursor", 0x1600)
        f.symbol("delayFrames", 0x1700)
        f.symbol("layoutOrigin2", 0xc5b9, false)
        f.emit("unknown", "d5 fa @linkMode fe 03 28 07 fe 04 28 03 cd @loadCursor cd @waitBg cd @prompt " +
            "21 @layoutOrigin 01 12 05 cd @clearBox cd @unloadCursor 0e 14 cd @delayFrames " +
            "21 @layoutOrigin2 d1 c3 @nextChar")
    }

    private fun bindContinuationLayout(
        f: Gen2DeclaredSignFixture,
        japaneseVariant: Boolean = false,
        mobileVariant: Boolean = false,
    ) {
        require(!japaneseVariant || !mobileVariant)
        f.symbol("linkMode", 0xd050, false)
        f.symbol("loadCursor", 0x1200)
        f.symbol("waitBg", 0x1300)
        f.symbol("prompt", 0x1400)
        f.symbol("unloadCursor", 0x1600)
        f.symbol("textScroll", 0x1700)
        f.symbol("layoutOrigin", 0xc4e1, false)
        f.symbol("contInner", 0x1800)
        f.symbol("contMarker", 0x1900)
        f.bytes[f.at("dictionary") + 1] = 0x4b
        f.word(f.at("dictionary") + 3, f.at("contInner"))
        f.emit("contInner", when {
            mobileVariant ->
                "fa @linkMode fe 03 28 07 fe 04 28 03 cd @loadCursor cd @waitBg d5 cd @prompt d1 " +
                    "cd @unloadCursor d5 cd @textScroll cd @textScroll 21 @layoutOrigin d1 c3 @nextChar"
            japaneseVariant ->
                "fa @linkMode fe 03 28 03 cd @loadCursor cd @waitBg d5 cd @prompt d1 " +
                    "cd @unloadCursor d5 cd @textScroll cd @textScroll 21 @layoutOrigin d1 c3 @nextChar"
            else ->
                "fa @linkMode b7 20 03 cd @loadCursor cd @waitBg d5 cd @prompt d1 " +
                    "fa @linkMode b7 cc @unloadCursor d5 cd @textScroll cd @textScroll " +
                    "21 @layoutOrigin d1 c3 @nextChar"
        })
        f.emit("unknown", "d5 11 @contMarker 44 4d cd @placeString 60 69 d1 c3 @nextChar")
        f.raw(f.at("contMarker"), "4b 50")
    }

    private fun bindTextlessBehaviorCommands(f: Gen2DeclaredSignFixture) {
        f.symbol("soundHandler", f.codeBank * 0x4000 + 0x2b00)
        f.symbol("pauseHandler", f.codeBank * 0x4000 + 0x2b40)
        f.symbol("playSound", 0x3500)
        f.symbol("delayFrames", 0x3580)
        f.symbol("scriptDelay", 0xd250, false)
        f.word(f.at("scriptTable") + 0x2f * 2, f.at("soundHandler"))
        f.word(f.at("scriptTable") + 0x4b * 2, f.at("pauseHandler"))
        f.emit("soundHandler", "cd @getByte 5f cd @getByte 57 cd @playSound c9")
        f.emit("pauseHandler", "cd @getByte a7 28 03 ea @scriptDelay 0e 02 cd @delayFrames " +
            "21 @scriptDelay 35 20 f5 c9")
    }

    private fun standardSignFixture(index: Int) = scalarFixture(inline = false).apply {
        val jumpStdHandler = codeBank * 0x4000 + 0x16c0
        val stdResolver = codeBank * 0x4000 + 0x2a00
        val stdBank = 7
        val stdTable = stdBank * 0x4000 + 0x100
        val stdTarget = stdBank * 0x4000 + 0x300
        val stdText = stdBank * 0x4000 + 0x400
        symbol("jumpStdHandler", jumpStdHandler)
        symbol("stdResolver", stdResolver)
        symbol("stdTable", stdTable)
        symbol("stdTarget", stdTarget)
        symbol("stdText", stdText)
        symbol("stdBank", stdBank, false)
        symbol("farByte", 0x3500)
        symbol("farWord", 0x3580)
        word(at("scriptTable") + 0x0c * 2, jumpStdHandler)
        bytes[jumpStdHandler] = 0xcd.toByte()
        word(jumpStdHandler + 1, stdResolver)
        bytes[jumpStdHandler + 3] = 0x18
        bytes[jumpStdHandler + 4] = (at("scriptJump") - (jumpStdHandler + 5)).toByte()
        emit("stdResolver", "cd @getByte 5f cd @getByte 57 21 @stdTable 19 19 19 " +
            "3e #stdBank cd @farByte 47 23 3e #stdBank cd @farWord c9")
        bytes[stdTable + index * 3] = stdBank.toByte()
        word(stdTable + index * 3 + 1, stdTarget)
        raw(script, "0c %02x 00".format(index))
        raw(stdTarget, "52")
        word(stdTarget + 1, stdText)
        raw(stdText, "00 37 a9 85 19 4f 1f b6 7f 57")
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
