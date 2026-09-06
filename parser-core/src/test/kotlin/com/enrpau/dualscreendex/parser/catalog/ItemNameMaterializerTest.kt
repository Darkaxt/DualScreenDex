package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.language.*
import com.enrpau.dualscreendex.parser.model.*
import com.enrpau.dualscreendex.parser.parse.ItemConsumerFixture
import com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.*
import org.junit.Test

class ItemNameMaterializerTest {
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
        val names = ItemNameMaterializer(f.session()).materialize(layout(), ids)
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
            val names = ItemNameMaterializer(f.session()).materialize(layout(JapanesePokemonTextCodecs.gen3Later), setOf(1, 4))
            assertNull(label, names.getValue(1).value)
            assertNotNull("ordinary record remains available", names.getValue(4).value)
        }
    }

    @Test
    fun `compiled zero row punctuation is native text not a guessed placeholder`() {
        val f = fixture()
        repeat(8) { f.bytes[f.root + it] = 0xAC.toByte() }; f.bytes[f.root + 8] = 0xFF.toByte()
        val names = ItemNameMaterializer(f.session()).materialize(layout(JapanesePokemonTextCodecs.gen3Later), setOf(0))
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
        val names = ItemNameMaterializer(f.session()).materialize(layout(), setOf(4, 175))
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

    private fun fixture() = ItemConsumerFixture().also { f ->
        listOf(0x2800, 0x2900, 0x2A00, f.root, 0x2B00, 0x2C00, 0x2D00)
            .forEachIndexed { i, root -> f.pointer(0x1BC + i * 4, root) }
    }
    private fun layout(codec: PokemonTextCodec = PokemonTextCodec.gbaEnglish) = ResolvedRomLayout(
        EngineFamily.EMERALD, 3, Platform.GBA, 0, 0, ProfileTables(),
        itemRootNomination = GbaItemRootNomination.Nominated(0x4000),
        languageManifest = RomLanguageManifest(codec.language,
            listOf(RomLanguageProjection(codec.language, codec.id, codec.version, LocalizedTableLayout(), emptyList(), LanguageResolutionStatus.RESOLVED)),
            LanguageResolutionStatus.RESOLVED),
    )
}
