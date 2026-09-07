package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.*
import com.enrpau.dualscreendex.parser.catalog.*
import com.enrpau.dualscreendex.parser.language.*
import com.enrpau.dualscreendex.parser.model.*
import com.enrpau.dualscreendex.parser.text.WesternPokemonTextCodecs
import org.junit.Assert.*
import org.junit.Test

/** Relocated structural controls, not retail Western item dictionaries. */
class WesternGen2ItemBaselineSyntheticTest {
    @Test fun thirteenByteCopyAndTwoBytePrefixesSupportBothRelocatedFarCalls() {
        for (hram in listOf(false, true)) for (shift in listOf(0, 0x100)) {
            val f = fixture(hram, shift)
            val session = f.session()
            session.freezeGen2ItemNameAuthority()
            val authority = session.gen2ItemNameAuthority as Gen2ItemNameAuthority.Available
            assertEquals(13, authority.copyBytes)
            assertEquals(2, authority.tmPrefixBytes)
            assertEquals(2, authority.hmPrefixBytes)
            val names = names(session)
            assertEquals(mapOf(1 to "ABCDEFGHIJKL", 2 to "B", 181 to "TM01", 233 to "HM01"),
                names.filterValues { it.value != null }.mapValues { it.value.value })
            assertEquals(requested, names.keys)
            assertTrue(listOf(-1, 0, 3, 256).all { names.getValue(it).value == null })
            assertSame(authority, session.gen2ItemNameAuthority)
        }
    }

    @Test fun westernOrdinaryStatic54UsesOnlyOneCompiledByte() {
        for (codec in WesternPokemonTextCodecs.all.filter { it.applicableGenerations == setOf(2) }) {
            val f = fixture()
            f.bytes[f.root] = 0x54
            val session = f.session()
            session.freezeGen2ItemNameAuthority()
            val names = names(session, codec)
            assertEquals(codec.id, "POKéBCDEFGHIJKL", names.getValue(1).value)
            assertEquals("B", names.getValue(2).value)
            assertEquals("TM01", names.getValue(181).value)
            assertEquals("HM01", names.getValue(233).value)
            assertEquals(requested, names.keys)
        }
    }

    @Test fun westernStatic54DoesNotAuthorizeDynamicOrOtherFixedSubstitutions() {
        for (bad in listOf(0x52, 0x53, 0x24, 0x4a, 0xe1, 0xe2, 0xd0, 0x4f, 0x01)) {
            val f = fixture()
            f.bytes[f.root] = 0x54
            f.bytes[f.root + 1] = bad.toByte()
            val session = f.session()
            session.freezeGen2ItemNameAuthority()
            val names = names(session)
            assertNull("ordinary byte $bad", names.getValue(1).value)
            assertEquals(CapabilityStatus.NOT_FOUND, names.getValue(1).status)
            assertEquals("B", names.getValue(2).value)
            assertEquals("TM01", names.getValue(181).value)
            assertEquals(requested, names.keys)
        }
    }

    @Test fun westernStatic54RemainsOrdinaryOnlyAndCannotEnterGeneratedPrefixes() {
        val f = fixture()
        f.bytes[f.root] = 0x54
        f.bytes[f.at("tmPrefix") + 1] = 0x54
        val session = f.session()
        session.freezeGen2ItemNameAuthority()
        val names = names(session)
        assertEquals("POKéBCDEFGHIJKL", names.getValue(1).value)
        assertNull(names.getValue(181).value)
        assertEquals("HM01", names.getValue(233).value)
        assertEquals(requested, names.keys)
    }

    @Test fun westernStatic54RequiresTheEntireThirteenByteCopyEvenWithEarlyTermination() {
        for (remaining in listOf(13, 12)) {
            val f = fixture()
            f.word(f.at("directory") + 16, 0x8000 - remaining)
            byteArrayOf(0x54, 0x50).copyInto(f.bytes, f.bytes.size - remaining)
            val session = f.session()
            session.freezeGen2ItemNameAuthority()
            assertEquals(13, (session.gen2ItemNameAuthority as Gen2ItemNameAuthority.Available).copyBytes)
            val names = names(session)
            assertEquals(if (remaining == 13) "POKé" else null, names.getValue(1).value)
            assertEquals("TM01", names.getValue(181).value)
            assertEquals(requested, names.keys)
        }
    }

    @Test fun westernStatic54StillRequiresOriginalExactProjectionAndCurrentReferences() {
        val f = fixture()
        f.bytes[f.root] = 0x54
        assertNull(names(f.session()).getValue(1).value)
        val session = f.session()
        session.freezeGen2ItemNameAuthority()
        val producer = ItemNameMaterializer(session)
        assertNull(producer.materialize(layout(), requested).getValue(1).value)
        assertEquals("POKéBCDEFGHIJKL", names(session).getValue(1).value)
        val references = session.gen2ItemReferences
        assertNull(producer.materialize(layout().copy(languageManifest = RomLanguageManifest.UNKNOWN), requested).getValue(1).value)
        assertNull(producer.materialize(layout(WesternPokemonTextCodecs.gen1English), requested).getValue(1).value)
        assertNull(producer.materialize(layout(com.enrpau.dualscreendex.parser.text.PokemonTextCodec.gbEnglish), requested).getValue(1).value)
        session.recordGen2ItemReferences(references.map { it.copy(mapHeader = null) })
        val incomplete = producer.materialize(layout(), requested)
        assertNull(incomplete.getValue(1).value)
        assertEquals(requested, incomplete.keys)
        f.bytes[0x6001] = 2
        val mismatched = f.session()
        mismatched.freezeGen2ItemNameAuthority()
        assertNull(names(mismatched).getValue(1).value)
    }

    @Test fun westernStatic54DoesNotChangePackedBudgetOrCancellation() {
        val f = fixture()
        f.bytes.fill(0x80.toByte(), f.root, f.root + 4097)
        f.bytes[f.root] = 0x54
        val session = f.session()
        session.freezeGen2ItemNameAuthority()
        val names = names(session)
        assertNull(names.getValue(1).value)
        assertEquals("TM01", names.getValue(181).value)
        assertEquals(requested, names.keys)
        val bounded = fixture()
        bounded.bytes[bounded.root] = 0x54
        var armed = false
        var remaining = 20
        val cancelled = bounded.session(cancellation = ParserCancellationToken {
            if (armed && --remaining == 0) throw ParserCancellationException()
        })
        cancelled.freezeGen2ItemNameAuthority()
        names(cancelled)
        armed = true
        assertThrows(ParserCancellationException::class.java) {
            ItemNameMaterializer(cancelled).materialize(layout(), requested)
        }
    }

    @Test fun westernControlSubstitutionTerminatorAndInvalidPrefixStayUnavailable() {
        for (bad in listOf(0x4f, 0x52, 0x50, 0x01)) {
            val f = fixture()
            f.bytes[f.at("tmPrefix") + 1] = bad.toByte()
            val session = f.session()
            session.freezeGen2ItemNameAuthority()
            val names = names(session)
            assertEquals(requested, names.keys)
            assertNull("prefix byte $bad", names.getValue(181).value)
            assertEquals("HM01", names.getValue(233).value)
            assertEquals("ABCDEFGHIJKL", names.getValue(1).value)
        }
    }

    @Test fun westernCopyMustContainTerminatorAndEntireMappedCopy() {
        val missing = fixture()
        missing.bytes[missing.root + 12] = 0x80.toByte()
        val session = missing.session()
        session.freezeGen2ItemNameAuthority()
        assertNull(names(session).getValue(1).value)
        val bounded = fixture()
        bounded.word(bounded.at("directory") + 16, 0x7ff8)
        bounded.bytes[0x13ff8] = 0x80.toByte()
        bounded.bytes[0x13ff9] = 0x50
        val edge = bounded.session()
        edge.freezeGen2ItemNameAuthority()
        assertTrue(edge.gen2ItemNameAuthority is Gen2ItemNameAuthority.Available)
        assertNull(names(edge).getValue(1).value)
    }

    @Test fun westernBudgetsCancellationAndConflictAreTerminal() {
        for (limits in listOf(ResolutionLimits(maxProbeWorkPerDataset = 100), ResolutionLimits(maxDatasetExtentBytes = 16))) {
            val resolver = Gen2CompiledItemNameResolver(fixture().session(limits))
            val denied = resolver.original()
            assertTrue(denied is Gen2ItemNameAuthority.Unavailable)
            assertSame(denied, resolver.original())
        }
        var checks = 0
        var armed = true
        val resolver = Gen2CompiledItemNameResolver(fixture().session(cancellation = ParserCancellationToken {
            if (armed && ++checks == 100) throw ParserCancellationException()
        }))
        assertThrows(ParserCancellationException::class.java) { resolver.original() }
        armed = false
        val denied = resolver.original()
        assertTrue(denied is Gen2ItemNameAuthority.Unavailable)
        assertSame(denied, resolver.original())
        val conflicting = fixture()
        conflicting.bytes.copyInto(conflicting.bytes, 0x3000, conflicting.at("wrapper"), conflicting.at("wrapper") + 10)
        assertTrue(Gen2CompiledItemNameResolver(conflicting.session()).original() is Gen2ItemNameAuthority.Unavailable)
    }

    @Test fun westernExtractorPublishesOnlySparseUnambiguousNamesWithCompleteNumericDomain() {
        val codec = WesternPokemonTextCodecs.gen2English
        val manifest = RomLanguageManifest(codec.language,
            listOf(RomLanguageProjection(codec.language, codec.id, codec.version, LocalizedTableLayout(), emptyList(),
                LanguageResolutionStatus.RESOLVED)), LanguageResolutionStatus.RESOLVED)
        val balls = mapOf(1 to CatalogField.available("SYNTHETIC"), 2 to CatalogField.available("CONFLICT A"),
            3 to CatalogField.available(" "), 5 to CatalogField.notFound<String>("synthetic unavailable"))
            .mapValues { (id, name) -> CaptureBallRecord(id, name, CatalogField.notFound("synthetic sprite")) }
        val candidates = listOf(1 to "SYNTHETIC", 1 to "SYNTHETIC", 2 to "CONFLICT B",
            4 to "POI CONFLICT A", 4 to "POI CONFLICT B", 5 to null)
        val pois = candidates.mapIndexed { index, (id, name) ->
            LocalMapPoi("synthetic/$index", "synthetic", 1, 0, 0, LocalMapPoiKind.VISIBLE_ITEM,
                item = LocalMapPoiItem(id, name, collectionFlagId = 500 + index))
        }
        for (ordered in listOf(pois, pois.reversed())) {
            val maps = LocalMapCatalog(
                maps = listOf(LocalMap("synthetic", null, 1, 16, 16, 1, 1, "synthetic-image")),
                assets = mapOf("synthetic-image" to PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))),
                pois = ordered)
            val extracted = CatalogLocalizedTextExtractor.extract(manifest, emptyMap(), emptyMap(),
                abilitiesById = emptyMap(), naturesById = emptyMap(), captureBallsById = balls,
                localMaps = maps, capabilities = emptyMap())
            val overlay = requireNotNull(extracted.localization.defaultOverlay())
            assertEquals(mapOf(1 to CatalogField.available("SYNTHETIC")), overlay.itemNames)
            assertEquals(LocalizedTextCapability.entries.toSet(), overlay.localizedCapabilities.keys)
            val state = overlay.localizedCapabilities.getValue(LocalizedTextCapability.ITEM_NAMES)
            assertEquals(CapabilityStatus.PARTIAL, state.status)
            assertEquals(1, state.coveredRecords)
            assertEquals(5, state.expectedRecords)
            assertEquals(4, state.incompleteRecords)
            assertEquals(setOf(1, 2, 3, 4, 5), extracted.captureBallsById.keys +
                extracted.localMaps.pois.mapNotNull { it.item?.itemId })
            assertEquals(balls.keys, extracted.captureBallsById.keys)
            assertTrue(extracted.captureBallsById.values.all { it.name.value == null })
            assertEquals(ordered.map { poi -> poi.copy(item = poi.item!!.copy(displayName = null)) }, extracted.localMaps.pois)
            assertTrue(overlay.poiTexts.isEmpty())
            assertFalse(5 in overlay.itemNames) // Required numeric ID remains; this is not semantic acceptance.
        }
    }

    private fun fixture(hram: Boolean = false, shift: Int = 0) = Gen2ItemFixture(hram, shift).also { f ->
        // Change only compiled length operands; preserve native fixture and its tests verbatim.
        fun replace(routine: String, from: List<Int>, to: List<Int>) {
            val matches = (f.at(routine) until f.at(routine) + 128 - from.size).filter { at ->
                from.indices.all { (f.bytes[at + it].toInt() and 255) == from[it] }
            }
            assertEquals(1, matches.size)
            to.map(Int::toByte).toByteArray().copyInto(f.bytes, matches.single())
        }
        replace("getName", listOf(0x01, 11, 0, 0xcd), listOf(0x01, 13, 0, 0xcd))
        replace("generated", listOf(0x01, 6, 0), listOf(0x01, 2, 0))
        replace("generated", listOf(0x01, 5, 0), listOf(0x01, 2, 0))
        ((0x80..0x8b).toList() + listOf(0x50, 0x81, 0x50)).map(Int::toByte).toByteArray().copyInto(f.bytes, f.root)
        byteArrayOf(0x93.toByte(), 0x8c.toByte()).copyInto(f.bytes, f.at("tmPrefix"))
        byteArrayOf(0x87.toByte(), 0x8c.toByte()).copyInto(f.bytes, f.at("hmPrefix"))
        for (id in listOf(1, 2, 181, 233)) f.bytes[0x6000 + id] = id.toByte()
    }

    private fun names(
        session: RomAnalysisSession,
        codec: com.enrpau.dualscreendex.parser.text.PokemonTextCodec = WesternPokemonTextCodecs.gen2English,
    ): Map<Int, CatalogField<String>> {
        session.recordGen2ItemReferences(listOf(1, 2, 181, 233).map { id ->
            Gen2ItemReference("item/$id", id, 0x6000 + id, Gen2ItemReference.Kind.VISIBLE_OBJECT,
                0x101, 0x5000, 1, 0x5100, 1, 0x5200, 1, 0x5300, 0x5400, 0x5409, 1, 1, 1, null)
        })
        return ItemNameMaterializer(session).materialize(layout(codec), requested)
    }

    private fun layout(codec: com.enrpau.dualscreendex.parser.text.PokemonTextCodec = WesternPokemonTextCodecs.gen2English) =
        ResolvedRomLayout(EngineFamily.GOLD_SILVER, 2, Platform.GBC, 0, 0, ProfileTables(),
            languageManifest = RomLanguageManifest(codec.language,
                listOf(RomLanguageProjection(codec.language, codec.id, codec.version, LocalizedTableLayout(), emptyList(), LanguageResolutionStatus.RESOLVED)),
                LanguageResolutionStatus.RESOLVED))

    private val requested = setOf(-1, 0, 1, 2, 3, 181, 233, 256)
}
