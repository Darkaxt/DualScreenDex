package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.language.*
import com.enrpau.dualscreendex.parser.model.*
import com.enrpau.dualscreendex.parser.parse.ItemConsumerFixture
import com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.WesternPokemonTextCodecs
import org.junit.Assert.*
import org.junit.Test

class ItemNameMaterializerTest {
    @Test fun genThreeSimpleAndDynamicContractsPreserveZeroAndExplicitExclusion() {
        for (mul in listOf(false, true)) for (simple in listOf(true, false)) {
            val f = ItemConsumerFixture(mulStride = if (mul) 44 else null, maximumHalf = 174, simpleWrapper = simple)
            val session = f.session()
            val authority = session.itemNameResolver.original(GbaItemPublishedRoute.NotInvoked)
            assertTrue("mul=$mul simple=$simple: $authority", authority is GbaItemNameAuthority.Available)
            assertEquals(if (simple) null else 175, (authority as GbaItemNameAuthority.Available).excludedId)
            val names = ItemNameMaterializer(session).materialize(layout().copy(itemNameAuthority = authority), setOf(0, 4, 175, 348, 349))
            for (id in listOf(0, 4, 348)) assertEquals("A", names.getValue(id).value)
            assertEquals(if (simple) "A" else null, names.getValue(175).value)
            assertNull(names.getValue(349).value)
            if (!simple) assertTrue(names.getValue(175).reasons.any { it.contains("dynamic") })
        }
    }

    @Test
    fun `default unavailable authority never discovers readable published records`() {
        val f = fixture()
        val names = ItemNameMaterializer(f.session()).materialize(layout(), setOf(4))
        assertNull(names.getValue(4).value)
    }

    @Test
    fun `frozen compiled authority decodes without rediscovering instructions or published root`() {
        val f = fixture()
        val authority = f.session().itemNameResolver.original(GbaItemPublishedRoute.NotInvoked)
        assertTrue(authority is GbaItemNameAuthority.Available)
        f.bytes.fill(0, 0, f.root)
        val frozen = layout().copy(itemRootNomination = GbaItemRootNomination.Absent, itemNameAuthority = authority)
        assertEquals("A", ItemNameMaterializer(f.session()).materialize(frozen, setOf(4)).getValue(4).value)
    }

    @Test
    fun `original absent or ambiguous nomination survives differing layout counts without retry`() {
        val f = fixture()
        val producer = ItemNameMaterializer(f.session())
        for (nomination in listOf(GbaItemRootNomination.Absent, GbaItemRootNomination.Ambiguous)) {
            val original = layout().copy(itemRootNomination = nomination)
            val later = original.copy(speciesCount = 1, moveCount = 2,
                tables = ProfileTables(speciesNames = TableLayout(0x2800, 1, 11), moveNames = TableLayout(0x2900, 2, 13)))
            assertSame(nomination, later.itemRootNomination)
            val result = producer.materialize(later, setOf(4))
            assertNull("original $nomination is terminal despite readable root and changed counts", result.getValue(4).value)
        }
    }

    @Test
    fun `only referenced exact u16 IDs decode including zero and final row`() {
        val f = fixture()
        val ids = setOf(-1, 0, 4, 175, 376, 377, 65535, 65536)
        val names = ItemNameMaterializer(f.session()).materialize(provedLayout(f), ids)
        assertEquals(ids, names.keys)
        for (id in listOf(0, 4, 376)) assertEquals("A", names.getValue(id).value)
        for (id in listOf(-1, 175, 377, 65535, 65536)) {
            assertNull(names.getValue(id).value)
            assertEquals(CapabilityStatus.NOT_FOUND, names.getValue(id).status)
        }
        assertTrue(names.getValue(175).reasons.any { it.contains("dynamic") })
    }

    @Test
    fun `numeric and next row terminators invalid tokens and control substitutions are rejected`() {
        for ((label, mutate) in listOf<Pair<String, (ItemConsumerFixture) -> Unit>>(
            "numeric FF" to { f -> f.bytes.fill(0xBB.toByte(), f.root + 40, f.root + 50); f.bytes[f.root + 50] = 0xFF.toByte() },
            "next row FF" to { f -> f.bytes.fill(0xBB.toByte(), f.root + 40, f.root + 80); f.bytes[f.root + 80] = 0xFF.toByte() },
            "invalid" to { f -> f.bytes[f.root + 40] = 0xF9.toByte() },
            "control" to { f -> f.bytes[f.root + 40] = 0xFE.toByte() },
            "substitution" to { f -> f.bytes[f.root + 40] = 0xFD.toByte(); f.bytes[f.root + 41] = 1; f.bytes[f.root + 42] = 0xFF.toByte() },
        )) {
            val f = fixture(); mutate(f)
            val names = ItemNameMaterializer(f.session()).materialize(provedLayout(f, JapanesePokemonTextCodecs.gen3Later), setOf(1, 4))
            assertNull(label, names.getValue(1).value)
            assertNotNull("ordinary record remains available", names.getValue(4).value)
        }
    }

    @Test
    fun `compiled zero row punctuation is native text not a guessed placeholder`() {
        val f = fixture()
        repeat(8) { f.bytes[f.root + it] = 0xAC.toByte() }; f.bytes[f.root + 8] = 0xFF.toByte()
        val names = ItemNameMaterializer(f.session()).materialize(provedLayout(f, JapanesePokemonTextCodecs.gen3Later), setOf(0))
        assertEquals("？？？？？？？？", names.getValue(0).value)
    }

    @Test
    fun `unknown authority and ambiguous published nominations never decode readable records`() {
        val f = fixture()
        val producer = ItemNameMaterializer(f.session())
        assertNull(producer.materialize(layout().copy(languageManifest = RomLanguageManifest.UNKNOWN), setOf(4)).getValue(4).value)
        repeat(11) { f.pointer(0x1AC + it * 4, if (it == 3) f.root else 0x2800 + it * 0x80) }
        val ambiguous = ItemNameMaterializer(f.session()).materialize(
            layout().copy(itemRootNomination = GbaItemRootNomination.Ambiguous), setOf(4))
        assertNull(ambiguous.getValue(4).value)
    }

    @Test
    fun `join gives ball and POI parity strips shared names and preserves unrelated prose`() {
        val f = fixture()
        val names = ItemNameMaterializer(f.session()).materialize(provedLayout(f), setOf(4, 175))
        val balls = mapOf(4 to CaptureBallRecord(4, CatalogField.notFound("name"), CatalogField.notFound("sprite")))
        val maps = LocalMapCatalog(
            maps = listOf(LocalMap("m", "map", 1, 16, 16, 1, 1, "a")),
            assets = mapOf("a" to PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))),
            pois = listOf(
                LocalMapPoi("item", "m", 1, 0, 0, LocalMapPoiKind.VISIBLE_ITEM, item = LocalMapPoiItem(4, collectionFlagId = 23)),
                LocalMapPoi("dynamic", "m", 1, 0, 0, LocalMapPoiKind.VISIBLE_ITEM, item = LocalMapPoiItem(175)),
                LocalMapPoi("sign", "m", 1, 0, 0, LocalMapPoiKind.PLACE, displayName = "native sign", destinationBaseAreaId = 2),
            ),
        )
        val joined = ItemNameMaterializer.join(names, balls, maps)
        assertEquals("A", joined.balls.getValue(4).name.value)
        assertEquals("A", joined.localMaps.pois[0].item?.displayName)
        assertEquals(balls.getValue(4).sprite, joined.balls.getValue(4).sprite)
        assertEquals(maps.pois[0].item?.collectionFlagId, joined.localMaps.pois[0].item?.collectionFlagId)
        assertEquals(maps.pois[2], joined.localMaps.pois[2])
        val extracted = CatalogLocalizedTextExtractor.extract(
            manifest = layout().languageManifest, speciesById = emptyMap(), movesById = emptyMap(),
            abilitiesById = emptyMap(), naturesById = emptyMap(), captureBallsById = joined.balls,
            localMaps = joined.localMaps, capabilities = emptyMap())
        assertNull(extracted.captureBallsById.getValue(4).name.value)
        assertNull(extracted.localMaps.pois[0].item?.displayName)
        val overlay = requireNotNull(extracted.localization.defaultOverlay())
        assertEquals("A", overlay.itemNames.getValue(4).value)
        assertEquals(LocalizedCapabilityState(CapabilityStatus.PARTIAL, 1.0, 1, 2),
            overlay.localizedCapabilities.getValue(LocalizedTextCapability.ITEM_NAMES))
        assertEquals(1, overlay.localizedCapabilities.getValue(LocalizedTextCapability.POI_TEXT).coveredRecords)
        assertEquals("native sign", overlay.poiTexts.getValue("sign").displayName?.value)
        assertNull(extracted.localization.overlay(LanguageTag.FRENCH))
    }

    @Test
    fun `Ruby exact arrows and B0 differ from later invalid and control tokens`() {
        val f = fixture()
        byteArrayOf(0xF7.toByte(), 0xF8.toByte(), 0xF9.toByte(), 0xB0.toByte(), 0xFF.toByte())
            .copyInto(f.bytes, f.root + 40)
        val frozen = provedLayout(f, JapanesePokemonTextCodecs.gen3RubySapphire)
        val names = ItemNameMaterializer(f.session()).materialize(frozen, setOf(1))
        assertEquals("↑↓←⋯", names.getValue(1).value)
        assertNull(ItemNameMaterializer(f.session()).materialize(
            provedLayout(f, JapanesePokemonTextCodecs.gen3Later), setOf(1)).getValue(1).value)
        for (token in listOf(0xFA, 0xFB, 0xFC, 0xFD, 0xFE)) {
            f.bytes.fill(0, f.root + 40, f.root + 50)
            f.bytes[f.root + 40] = token.toByte()
            f.bytes[f.root + 48] = 0xFF.toByte()
            assertNull("Ruby token $token", ItemNameMaterializer(f.session()).materialize(frozen, setOf(1)).getValue(1).value)
        }
        // Ruby maps every ordinary byte; an unknown extended-control unit is invalid instead.
        f.bytes[f.root + 40] = 0xFC.toByte()
        f.bytes[f.root + 41] = 0xFF.toByte()
        assertNull(ItemNameMaterializer(f.session()).materialize(frozen, setOf(1)).getValue(1).value)
        for (terminator in listOf(50, 80)) {
            f.bytes.fill(0xBB.toByte(), f.root + 40, f.root + 81)
            f.bytes[f.root + terminator] = 0xFF.toByte()
            assertNull(ItemNameMaterializer(f.session()).materialize(frozen, setOf(1)).getValue(1).value)
        }
    }

    @Test fun genOneOnlyCurrentStructuralReferencesReceiveOrdinaryOrGeneratedLabels() {
        for (yellow in listOf(false, true)) {
            val f = genOneFixture(yellow)
            val session = f.session(); session.freezeGen1ItemNameAuthority()
            session.recordGen1ItemReferences(listOf(1, 2, 201).map { genOneReference(it) })
            val names = ItemNameMaterializer(session).materialize(genOneLayout(yellow), setOf(-1, 0, 1, 2, 196, 201, 202, 255, 256))
            assertEquals("ア", names.getValue(1).value)
            assertEquals("イ", names.getValue(2).value)
            assertEquals(if (yellow) "あいうえお０１" else "あいうえお01", names.getValue(201).value)
            for (id in listOf(-1, 0, 196, 202, 255, 256)) assertNull("unrequested/invalid $id", names.getValue(id).value)
        }
    }

    @Test fun genOneFrenchGermanReferencedOrdinaryPlusNamesAreAvailable() {
        for (yellow in listOf(false, true)) {
            for (codec in listOf(WesternPokemonTextCodecs.gen1French, WesternPokemonTextCodecs.gen1German)) {
                val f = genOneFixture(yellow)
                byteArrayOf(0x80.toByte(), 0x7F, 0xE4.toByte(), 0x50, 0x81.toByte(), 0x50)
                    .copyInto(f.bytes, f.root)
                val session = f.session(); session.freezeGen1ItemNameAuthority()
                session.recordGen1ItemReferences(listOf(1, 2).map(::genOneReference))
                val names = ItemNameMaterializer(session).materialize(
                    layout(codec).copy(family = if (yellow) EngineFamily.YELLOW else EngineFamily.RED_BLUE,
                        generation = 1, platform = Platform.GB), setOf(1, 2))
                assertEquals("${codec.id} yellow=$yellow", "A +", names.getValue(1).value)
                assertEquals(CapabilityStatus.AVAILABLE, names.getValue(1).status)
                assertEquals("B", names.getValue(2).value)
                assertEquals(setOf(1, 2), names.keys)
            }
        }
    }

    @Test fun genOneFrenchGermanOrdinarySubstitutionsAndControlsStillFailLocally() {
        for (yellow in listOf(false, true)) {
            for (codec in listOf(WesternPokemonTextCodecs.gen1French, WesternPokemonTextCodecs.gen1German)) {
                for (token in listOf(0xD4, 0xDF, 0x4A, 0x54, 0xE1, 0xE2, 0x4E, 0x00)) {
                    val f = genOneFixture(yellow)
                    f.bytes[f.root] = token.toByte()
                    val session = f.session(); session.freezeGen1ItemNameAuthority()
                    session.recordGen1ItemReferences(listOf(1, 2).map(::genOneReference))
                    val names = ItemNameMaterializer(session).materialize(
                        layout(codec).copy(family = if (yellow) EngineFamily.YELLOW else EngineFamily.RED_BLUE,
                            generation = 1, platform = Platform.GB), setOf(1, 2))
                    assertNull("${codec.id} yellow=$yellow token=$token", names.getValue(1).value)
                    assertEquals(CapabilityStatus.NOT_FOUND, names.getValue(1).status)
                    assertEquals("B", names.getValue(2).value)
                    assertEquals(setOf(1, 2), names.keys)
                }
            }
        }
    }

    @Test fun genOneReadablePayloadCannotAcquireOriginalOrReferenceAuthority() {
        val f = genOneFixture(false)
        val missingOriginal = f.session(); missingOriginal.recordGen1ItemReferences(listOf(genOneReference(201)))
        assertNull(ItemNameMaterializer(missingOriginal).materialize(genOneLayout(false), setOf(201)).getValue(201).value)
        val missingReference = f.session(); missingReference.freezeGen1ItemNameAuthority()
        assertNull(ItemNameMaterializer(missingReference).materialize(genOneLayout(false), setOf(201)).getValue(201).value)
    }

    @Test fun genOneRejectsEmbeddedPrefixTerminationBadTokensAndCopyOverflowLocally() {
        for (token in listOf(0x50, 0x4e, 0x54, 0x00)) {
            val f = genOneFixture(true); f.bytes[f.prefix + 8] = token.toByte()
            val session = f.session(); session.freezeGen1ItemNameAuthority()
            session.recordGen1ItemReferences(listOf(1, 201).map { genOneReference(it) })
            val names = ItemNameMaterializer(session).materialize(genOneLayout(true), setOf(1, 201))
            assertEquals("ア", names.getValue(1).value)
            assertNull("prefix token $token", names.getValue(201).value)
        }
        val f = genOneFixture(false)
        f.bytes.fill(0x80.toByte(), f.root, f.root + 21); f.bytes[f.root + 21] = 0x50
        val session = f.session(); session.freezeGen1ItemNameAuthority()
        session.recordGen1ItemReferences(listOf(1, 201).map { genOneReference(it) })
        val names = ItemNameMaterializer(session).materialize(genOneLayout(false), setOf(1, 201))
        assertNull(names.getValue(1).value)
        assertEquals("あいうえお01", names.getValue(201).value)
    }

    @Test fun genOneSharedWalkBudgetAndCancellationDoNotCreateNames() {
        val f = genOneFixture(false)
        f.bytes.fill(0x80.toByte(), f.root, f.root + 4097)
        val session = f.session(); session.freezeGen1ItemNameAuthority()
        session.recordGen1ItemReferences(listOf(genOneReference(1)))
        assertNull(ItemNameMaterializer(session).materialize(genOneLayout(false), setOf(1)).getValue(1).value)
        var cancelled = false
        val cancel = f.session(cancellation = com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken {
            if (cancelled) throw com.enrpau.dualscreendex.parser.analysis.ParserCancellationException()
        })
        cancel.freezeGen1ItemNameAuthority(); cancelled = true
        assertThrows(com.enrpau.dualscreendex.parser.analysis.ParserCancellationException::class.java) {
            ItemNameMaterializer(cancel).materialize(genOneLayout(false), setOf(201))
        }
    }

    @Test fun genOneTraversalDoesNotDecodeUnrequestedRowsAndCopyCannotCrossMappedBank() {
        val f = genOneFixture(false)
        f.bytes[f.root] = 0 // invalid unrequested row, with a structurally usable delimiter
        val session = f.session(); session.freezeGen1ItemNameAuthority()
        session.recordGen1ItemReferences(listOf(genOneReference(2)))
        val names = ItemNameMaterializer(session).materialize(genOneLayout(false), setOf(2))
        assertEquals(setOf(2), names.keys)
        assertEquals("イ", names.getValue(2).value)

        f.bytes[f.directory + 10] = 0xf6.toByte(); f.bytes[f.directory + 11] = 0x7f
        f.bytes[0xbff6] = 0x80.toByte(); f.bytes[0xbff7] = 0x50
        val boundary = f.session(); boundary.freezeGen1ItemNameAuthority()
        boundary.recordGen1ItemReferences(listOf(1, 201).map { genOneReference(it) })
        val result = ItemNameMaterializer(boundary).materialize(genOneLayout(false), setOf(1, 201))
        assertNull("full 20-byte copy, not merely its terminator, must fit the bank", result.getValue(1).value)
        assertEquals("あいうえお01", result.getValue(201).value)
    }

    @Test fun genOneMaterializerNeverReentersOriginalResolverAndReferencesAreSnapshots() {
        val f = genOneFixture(false)
        f.bytes[0x5000 + 196] = 196.toByte()
        var forbidOriginal = false
        val session = f.session(cancellation = com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken {
            check(!forbidOriginal || Thread.currentThread().stackTrace.none { it.className.endsWith("Gen1CompiledItemNameResolver") })
        })
        session.freezeGen1ItemNameAuthority()
        val refs = mutableListOf(genOneReference(196))
        session.recordGen1ItemReferences(refs); refs.clear()
        assertEquals(1, session.gen1ItemReferences.size)
        assertThrows(UnsupportedOperationException::class.java) {
            (session.gen1ItemReferences as MutableList).clear()
        }
        forbidOriginal = true
        val names = ItemNameMaterializer(session).materialize(genOneLayout(false), setOf(0, 196, 201))
        assertNull(names.getValue(0).value)
        assertNull(names.getValue(201).value)
        assertEquals("あいうえおか01", names.getValue(196).value)
    }

    @Test fun genOneMismatchedOperandAndUnknownProjectionCannotAcquireNames() {
        val f = genOneFixture(false)
        f.bytes[0x5000 + 201] = 202.toByte()
        val session = f.session(); session.freezeGen1ItemNameAuthority()
        session.recordGen1ItemReferences(listOf(genOneReference(201)))
        assertTrue(session.gen1ItemReferences.isEmpty())
        assertNull(ItemNameMaterializer(session).materialize(genOneLayout(false), setOf(201)).getValue(201).value)
        session.recordGen1ItemReferences(listOf(genOneReference(1)))
        assertNull(ItemNameMaterializer(session).materialize(
            genOneLayout(false).copy(languageManifest = RomLanguageManifest.UNKNOWN), setOf(1)).getValue(1).value)
    }

    @Test fun genOneAndJapaneseGenTwoStatic54RemainUnavailable() {
        for (codec in listOf(JapanesePokemonTextCodecs.gen1RedBlue,
            com.enrpau.dualscreendex.parser.text.WesternPokemonTextCodecs.gen1English)) {
            val f = genOneFixture(false)
            f.bytes[f.root] = 0x54
            val session = f.session()
            session.freezeGen1ItemNameAuthority()
            session.recordGen1ItemReferences(listOf(1, 2).map(::genOneReference))
            val names = ItemNameMaterializer(session).materialize(
                layout(codec).copy(family = EngineFamily.RED_BLUE, generation = 1, platform = Platform.GB), setOf(1, 2))
            assertNull(codec.id, names.getValue(1).value)
            assertEquals(codec.decode(byteArrayOf(0x81.toByte(), 0x50)), names.getValue(2).value)
        }
        val f = genTwoFixture()
        f.bytes[f.root] = 0x54
        val session = f.session()
        freezeGenTwo(session)
        session.recordGen2ItemReferences(listOf(1, 2).map(::genTwoReference))
        val names = ItemNameMaterializer(session).materialize(genTwoLayout(), setOf(1, 2))
        assertNull(names.getValue(1).value)
        assertEquals("イ", names.getValue(2).value)
    }

    @Test fun genTwoCurrentReferencesGateBothSkippedIdArithmeticAndOrdinaryWalk() {
        val f = genTwoFixture()
        val session = f.session(); freezeGenTwo(session)
        val ids = listOf(1, 2, 181, 184, 185, 186, 209, 210, 211, 232, 233)
        session.recordGen2ItemReferences(ids.map(::genTwoReference))
        val queries = ids.toSet() + setOf(-1, 0, 182, 234, 255, 256)
        val names = ItemNameMaterializer(session).materialize(genTwoLayout(), queries)
        assertEquals(queries, names.keys)
        assertEquals("ア", names.getValue(1).value)
        assertEquals("イ", names.getValue(2).value)
        for ((id, number) in mapOf(181 to "０１", 184 to "０４", 185 to "０４", 186 to "０５",
            209 to "２８", 210 to "２８", 211 to "２９", 232 to "５０")) {
            assertEquals("あいうえお$number", names.getValue(id).value)
        }
        assertEquals("あいうえおか０１", names.getValue(233).value)
        for (id in listOf(-1, 0, 182, 234, 255, 256)) assertNull("unbound $id", names.getValue(id).value)
    }

    @Test fun genTwoOriginalReferenceSnapshotsAndDecodeOnlyBoundaryAreIndependent() {
        val f = genTwoFixture()
        val uninvoked = f.session(); uninvoked.recordGen2ItemReferences(listOf(genTwoReference(181)))
        assertNull(ItemNameMaterializer(uninvoked).materialize(genTwoLayout(), setOf(181)).getValue(181).value)
        var denyDiscovery = false
        val session = f.session(cancellation = com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken {
            check(!denyDiscovery || Thread.currentThread().stackTrace.none { it.className.endsWith("Gen2CompiledItemNameResolver") })
        })
        freezeGenTwo(session)
        val refs = mutableListOf(genTwoReference(181)); session.recordGen2ItemReferences(refs); refs.clear()
        assertThrows(UnsupportedOperationException::class.java) { (session.gen2ItemReferences as MutableList).clear() }
        denyDiscovery = true
        assertEquals("あいうえお０１", ItemNameMaterializer(session).materialize(genTwoLayout(), setOf(181)).getValue(181).value)
        session.recordGen2ItemReferences(listOf(genTwoReference(182))) // mismatched ROM operand
        assertTrue(session.gen2ItemReferences.isEmpty())
        assertNull(ItemNameMaterializer(session).materialize(genTwoLayout(), setOf(181)).getValue(181).value)
    }

    @Test fun genTwoBadPayloadIsLocalAndUnrequestedRowsHaveNoSemanticOutput() {
        for (token in listOf(0, 0x50, 0x4e, 0x37)) {
            val f = genTwoFixture(); f.bytes[f.at("tmPrefix")] = token.toByte()
            f.bytes[f.root] = 0 // invalid first ordinal is not requested
            val session = f.session(); freezeGenTwo(session)
            session.recordGen2ItemReferences(listOf(2, 181, 233).map(::genTwoReference))
            val result = ItemNameMaterializer(session).materialize(genTwoLayout(), setOf(2, 181, 233))
            assertEquals("イ", result.getValue(2).value)
            assertNull(result.getValue(181).value)
            assertEquals("あいうえおか０１", result.getValue(233).value)
        }
    }

    @Test fun genTwoUnknownProjectionIncompleteProvenanceAndReferenceOverflowStayUnavailable() {
        val f = genTwoFixture()
        val session = f.session(); session.freezeGen2ItemNameAuthority()
        session.recordGen2ItemReferences(listOf(genTwoReference(181)))
        assertNull(ItemNameMaterializer(session).materialize(genTwoLayout().copy(languageManifest = RomLanguageManifest.UNKNOWN), setOf(181)).getValue(181).value)
        session.recordGen2ItemReferences(listOf(genTwoReference(181).copy(mapHeader = null)))
        assertNull(ItemNameMaterializer(session).materialize(genTwoLayout(), setOf(181)).getValue(181).value)
        session.recordGen2ItemReferences(List(32769) { genTwoReference(181) })
        assertTrue(session.gen2ItemReferences.isEmpty())
        assertNull(ItemNameMaterializer(session).materialize(genTwoLayout(), setOf(181)).getValue(181).value)
    }

    @Test fun genTwoPackedBudgetFullCopyAndCancellationCannotPublishPartialNames() {
        val f = genTwoFixture()
        f.bytes.fill(0x80.toByte(), f.root, f.root + 4097)
        val session = f.session(); session.freezeGen2ItemNameAuthority()
        session.recordGen2ItemReferences(listOf(1, 181).map(::genTwoReference))
        val names = ItemNameMaterializer(session).materialize(genTwoLayout(), setOf(1, 181))
        assertNull(names.getValue(1).value)
        assertEquals("あいうえお０１", names.getValue(181).value)
        val bounded = genTwoFixture()
        bounded.word(bounded.at("directory") + 16, 0x7ffa)
        byteArrayOf(0x80.toByte(), 0x50).copyInto(bounded.bytes, 0x13ffa)
        val short = bounded.session(); short.freezeGen2ItemNameAuthority()
        short.recordGen2ItemReferences(listOf(genTwoReference(1)))
        assertNull(ItemNameMaterializer(short).materialize(genTwoLayout(), setOf(1)).getValue(1).value)
        var remaining = 12
        var armed = false
        val cancel = genTwoFixture().session(cancellation = com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken {
            if (armed && --remaining == 0) throw com.enrpau.dualscreendex.parser.analysis.ParserCancellationException()
        })
        cancel.freezeGen2ItemNameAuthority()
        cancel.recordGen2ItemReferences(listOf(1, 2, 181).map(::genTwoReference)); armed = true
        assertThrows(com.enrpau.dualscreendex.parser.analysis.ParserCancellationException::class.java) {
            ItemNameMaterializer(cancel).materialize(genTwoLayout(), setOf(1, 2, 181))
        }
    }

    @Test fun koreanGenTwoRelocatedTwentyOneByteCopiesAndLiteralPrefixes() {
        for (hram in listOf(false, true)) for (shift in listOf(0, 0x100)) {
            val f = koreanGenTwoFixture(hram, shift)
            val session = f.session(); session.freezeGen2ItemNameAuthority()
            val authority = session.gen2ItemNameAuthority as Gen2ItemNameAuthority.Available
            assertEquals(f.root, authority.root)
            assertEquals(21, authority.copyBytes)
            assertEquals(8, authority.tmPrefixBytes)
            assertEquals(8, authority.hmPrefixBytes)
            session.recordGen2ItemReferences(listOf(1, 2, 181, 233).map(::genTwoReference))
            val names = ItemNameMaterializer(session).materialize(koreanGenTwoLayout(), setOf(1, 2, 181, 233))
            assertEquals(mapOf(1 to "가".repeat(10), 2 to "각", 181 to "기술머신01", 233 to "비전머신01"),
                names.mapValues { it.value.value })
            assertTrue(names.values.all { it.status == CapabilityStatus.AVAILABLE })
        }
    }

    @Test fun koreanGenTwoFullCopyMustFitBankEvenWhenNameTerminates() {
        for (remaining in listOf(21, 20)) {
            val f = koreanGenTwoFixture(shift = 0x100)
            val start = f.bytes.size - remaining
            f.word(f.at("directory") + 16, 0x8000 - remaining)
            byteArrayOf(1, 1, 0x50).copyInto(f.bytes, start)
            val session = f.session(); session.freezeGen2ItemNameAuthority()
            assertTrue(session.gen2ItemNameAuthority is Gen2ItemNameAuthority.Available)
            session.recordGen2ItemReferences(listOf(1, 181).map(::genTwoReference))
            val names = ItemNameMaterializer(session).materialize(koreanGenTwoLayout(), setOf(1, 181))
            assertEquals(setOf(1, 181), names.keys)
            assertEquals(if (remaining == 21) "가" else null, names.getValue(1).value)
            assertEquals("기술머신01", names.getValue(181).value)
        }
    }

    @Test fun koreanGenTwoMalformedOrdinaryCopiesFailLocallyWithoutDroppingIds() {
        val mutations: List<Pair<String, (com.enrpau.dualscreendex.parser.parse.Gen2ItemFixture) -> Unit>> = listOf(
            "unsupported byte" to { f -> f.bytes[f.root] = 0x0c },
            "control" to { f -> f.bytes[f.root] = 0 },
            "substitution" to { f -> f.bytes[f.root] = 0x4a },
            "unratified native 54 control" to { f -> f.bytes[f.root] = 0x54 },
            "split pair at copy boundary" to { f -> f.bytes[f.root + 20] = 1 },
            "termination outside full copy" to { f -> f.bytes[f.root + 20] = 0x7f },
        )
        for ((label, mutate) in mutations) {
            val f = koreanGenTwoFixture(shift = 0x100)
            val session = f.session(); session.freezeGen2ItemNameAuthority()
            val ids = setOf(1, 181, 233)
            session.recordGen2ItemReferences(ids.map(::genTwoReference))
            assertEquals("가".repeat(10), ItemNameMaterializer(session).materialize(koreanGenTwoLayout(), ids).getValue(1).value)
            mutate(f)
            val changed = f.session(); changed.freezeGen2ItemNameAuthority()
            changed.recordGen2ItemReferences(ids.map(::genTwoReference))
            val names = ItemNameMaterializer(changed).materialize(koreanGenTwoLayout(), ids)
            assertEquals(ids, names.keys)
            assertEquals(label, CapabilityStatus.NOT_FOUND, names.getValue(1).status)
            assertNull(label, names.getValue(1).value)
            assertEquals("기술머신01", names.getValue(181).value)
            assertEquals("비전머신01", names.getValue(233).value)
        }
    }

    @Test fun koreanGenTwoCompiledIndexStillWalksRaw50InAnUnsupportedPair() {
        val f = koreanGenTwoFixture(shift = 0x100)
        // Pinned Korean declarations do NOT support 0150. No invented trail-50 glyph:
        // compiled GetNthString nevertheless consumes its raw 50 while seeking ordinal 2.
        byteArrayOf(1, 0x50, 1, 2, 0x50).copyInto(f.bytes, f.root)
        val invalid = com.enrpau.dualscreendex.parser.text.KoreanGen2PokemonTextCodec.codec
            .decodeDetailed(byteArrayOf(1, 0x50))
        assertTrue(invalid.invalidUnits > 0 && !invalid.terminated)
        val session = f.session(); session.freezeGen2ItemNameAuthority()
        session.recordGen2ItemReferences(listOf(1, 2).map(::genTwoReference))
        val names = ItemNameMaterializer(session).materialize(koreanGenTwoLayout(), setOf(1, 2))
        assertEquals(setOf(1, 2), names.keys)
        assertNull(names.getValue(1).value)
        assertEquals("각", names.getValue(2).value)
        session.recordGen2ItemReferences(listOf(genTwoReference(2)))
        assertEquals("각", ItemNameMaterializer(session).materialize(koreanGenTwoLayout(), setOf(2)).getValue(2).value)
    }

    @Test fun koreanGenTwoMalformedOrSplitLiteralPrefixCannotBorrowDigits() {
        for (raw in listOf("50", "00", "0c", "4a", "7f 01 b2 06 2a 04 73 01")) {
            val f = koreanGenTwoFixture(shift = 0x100)
            val session = f.session(); session.freezeGen2ItemNameAuthority()
            val ids = setOf(1, 181, 233)
            session.recordGen2ItemReferences(ids.map(::genTwoReference))
            assertEquals("기술머신01", ItemNameMaterializer(session).materialize(koreanGenTwoLayout(), ids).getValue(181).value)
            raw.split(' ').map { it.toInt(16).toByte() }.toByteArray().copyInto(f.bytes, f.at("tmPrefix"))
            val changed = f.session(); changed.freezeGen2ItemNameAuthority()
            changed.recordGen2ItemReferences(ids.map(::genTwoReference))
            val names = ItemNameMaterializer(changed).materialize(koreanGenTwoLayout(), ids)
            assertEquals(ids, names.keys)
            assertNull(raw, names.getValue(181).value)
            assertEquals(CapabilityStatus.NOT_FOUND, names.getValue(181).status)
            assertEquals("가".repeat(10), names.getValue(1).value)
            assertEquals("비전머신01", names.getValue(233).value)
        }
    }

    @Test fun koreanGenTwoReadableNamesNeedOriginalAndCompleteCurrentReferences() {
        val f = koreanGenTwoFixture(shift = 0x100)
        val ids = setOf(1, 2, 181, 233)
        val uninvoked = f.session(); uninvoked.recordGen2ItemReferences(ids.map(::genTwoReference))
        assertTrue(ItemNameMaterializer(uninvoked).materialize(koreanGenTwoLayout(), ids).values.all { it.value == null })
        val session = f.session(); session.freezeGen2ItemNameAuthority()
        val unbound = ItemNameMaterializer(session).materialize(koreanGenTwoLayout(), ids)
        assertEquals(ids, unbound.keys)
        assertTrue(unbound.values.all { it.value == null })
        session.recordGen2ItemReferences(listOf(genTwoReference(1), genTwoReference(181).copy(mapHeader = null)))
        val names = ItemNameMaterializer(session).materialize(koreanGenTwoLayout(), ids)
        assertEquals(ids, names.keys)
        assertEquals("가".repeat(10), names.getValue(1).value)
        assertTrue((ids - 1).all { names.getValue(it).value == null })
        assertNull(ItemNameMaterializer(session).materialize(koreanGenTwoLayout().copy(languageManifest = RomLanguageManifest.UNKNOWN), ids).getValue(1).value)
        f.bytes[0x6001] = 2
        val mismatched = f.session(); mismatched.freezeGen2ItemNameAuthority()
        mismatched.recordGen2ItemReferences(listOf(genTwoReference(1)))
        assertTrue(mismatched.gen2ItemReferences.isEmpty())
        assertNull(ItemNameMaterializer(mismatched).materialize(koreanGenTwoLayout(), ids).getValue(1).value)
    }

    private fun koreanGenTwoLayout() = layout(com.enrpau.dualscreendex.parser.text.KoreanGen2PokemonTextCodec.codec)
        .copy(family = EngineFamily.GOLD_SILVER, generation = 2, platform = Platform.GBC)
    private fun koreanGenTwoFixture(hram: Boolean = false, shift: Int = 0) =
        com.enrpau.dualscreendex.parser.parse.Gen2ItemFixture(hram, shift, korean = true).also { f ->
            // Synthetic relocated B5/E9 consumer deliberately differs from native BF/F3.
            // Payload tokens independently declared at pokegold-kr 7743877dc9fa8603f4b6eaebe904a7ba03fdb9e4.
            repeat(10) { byteArrayOf(1, 1).copyInto(f.bytes, f.root + it * 2) }
            byteArrayOf(0x50, 1, 2, 0x50).copyInto(f.bytes, f.root + 20)
            "01 b2 06 2a 04 73 06 65".split(' ').map { it.toInt(16).toByte() }.toByteArray().copyInto(f.bytes, f.at("tmPrefix"))
            "05 61 07 cc 04 73 06 65".split(' ').map { it.toInt(16).toByte() }.toByteArray().copyInto(f.bytes, f.at("hmPrefix"))
            for (id in listOf(1, 2, 181, 233)) f.bytes[0x6000 + id] = id.toByte()
        }

    private fun freezeGenTwo(session: com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession) {
        val freeze = session.javaClass.declaredMethods.singleOrNull { it.name.startsWith("freezeGen2ItemNameAuthority") }
        assertNotNull("original GenII authority must be frozen in the session", freeze)
        freeze!!.invoke(session)
    }
    private fun genTwoReference(id: Int) = com.enrpau.dualscreendex.parser.analysis.Gen2ItemReference(
        "item/$id", id, 0x6000 + id, com.enrpau.dualscreendex.parser.analysis.Gen2ItemReference.Kind.VISIBLE_OBJECT,
        0x101, 0x5000, 1, 0x5100, 1, 0x5200, 1, 0x5300, 0x5400, 0x5409, 1, 1, 1, null)
    private fun genTwoFixture() = com.enrpau.dualscreendex.parser.parse.Gen2ItemFixture().also { f ->
        byteArrayOf(0x80.toByte(), 0x50, 0x81.toByte(), 0x50).copyInto(f.bytes, f.root)
        byteArrayOf(0xb1.toByte(), 0xb2.toByte(), 0xb3.toByte(), 0xb4.toByte(), 0xb5.toByte()).copyInto(f.bytes, f.at("tmPrefix"))
        byteArrayOf(0xb1.toByte(), 0xb2.toByte(), 0xb3.toByte(), 0xb4.toByte(), 0xb5.toByte(), 0xb6.toByte()).copyInto(f.bytes, f.at("hmPrefix"))
        for (id in listOf(1, 2, 181, 184, 185, 186, 209, 210, 211, 232, 233)) f.bytes[0x6000 + id] = id.toByte()
    }
    private fun genTwoLayout() = layout(JapanesePokemonTextCodecs.gen2).copy(family = EngineFamily.CRYSTAL,
        generation = 2, platform = Platform.GBC)

    private fun genOneReference(id: Int) = com.enrpau.dualscreendex.parser.analysis.Gen1ItemReference(
        "item/$id", id, 0x5000 + id, com.enrpau.dualscreendex.parser.analysis.Gen1ItemReference.Kind.VISIBLE_OBJECT,
        1, 1, 0x5000,
    )
    private fun genOneFixture(yellow: Boolean) = com.enrpau.dualscreendex.parser.parse.Gen1ItemFixture(yellow).also { f ->
        byteArrayOf(0x80.toByte(), 0x50, 0x81.toByte(), 0x50).copyInto(f.bytes, f.root)
        byteArrayOf(0xb1.toByte(), 0xb2.toByte(), 0xb3.toByte(), 0xb4.toByte(), 0xb5.toByte(), 0xb6.toByte()).copyInto(f.bytes, f.prefix)
        byteArrayOf(0xb1.toByte(), 0xb2.toByte(), 0xb3.toByte(), 0xb4.toByte(), 0xb5.toByte()).copyInto(f.bytes, f.prefix + 8)
        for (id in listOf(1, 2, 201)) f.bytes[0x5000 + id] = id.toByte()
    }
    private fun genOneLayout(yellow: Boolean): ResolvedRomLayout {
        val codec = if (yellow) JapanesePokemonTextCodecs.gen1Yellow else JapanesePokemonTextCodecs.gen1RedBlue
        return layout(codec).copy(family = if (yellow) EngineFamily.YELLOW else EngineFamily.RED_BLUE,
            generation = 1, platform = Platform.GB)
    }

    private fun fixture() = ItemConsumerFixture().also { f ->
        listOf(0x2800, 0x2900, 0x2A00, f.root, 0x2B00, 0x2C00, 0x2D00)
            .forEachIndexed { i, root -> f.pointer(0x1BC + i * 4, root) }
    }
    private fun provedLayout(f: ItemConsumerFixture, codec: PokemonTextCodec = PokemonTextCodec.gbaEnglish) =
        layout(codec).copy(itemNameAuthority = f.session().itemNameResolver.original(
            GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Nominated(f.root))))

    private fun layout(codec: PokemonTextCodec = PokemonTextCodec.gbaEnglish) = ResolvedRomLayout(
        EngineFamily.EMERALD, 3, Platform.GBA, 0, 0, ProfileTables(),
        itemRootNomination = GbaItemRootNomination.Nominated(0x4000),
        languageManifest = RomLanguageManifest(codec.language,
            listOf(RomLanguageProjection(codec.language, codec.id, codec.version, LocalizedTableLayout(), emptyList(), LanguageResolutionStatus.RESOLVED)),
            LanguageResolutionStatus.RESOLVED),
    )
}
