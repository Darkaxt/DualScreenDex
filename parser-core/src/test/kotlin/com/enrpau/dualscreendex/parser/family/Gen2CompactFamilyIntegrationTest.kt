package com.enrpau.dualscreendex.parser.family

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.CatalogMaterializationPhase
import com.enrpau.dualscreendex.parser.catalog.CatalogMaterializer
import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.MoveCategory
import com.enrpau.dualscreendex.parser.catalog.TypeSemanticRole
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.ParseResult
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.parse.Gen2CompactCoreResolver
import com.enrpau.dualscreendex.parser.parse.Gen2CompactCoreResolverTest
import com.enrpau.dualscreendex.parser.parse.Gen2CompactMoveNamesResolver
import com.enrpau.dualscreendex.parser.text.Gen2PlainNameCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen2CompactFamilyIntegrationTest {
    @Test fun admitsZeroBasedCoreWithoutInheritedTablesOrNativeNameOverlays() = verifyCore(false, 289)

    @Test fun admitsOneBasedOddWidthCoreWithItsIndependentIndices() = verifyCore(true, 254)

    @Test fun fixtureHasIndependentCoreAndMoveAuthorityBeforeFamilyIntegration() {
        for (oneBased in listOf(false, true)) {
            val session = session(fixture(oneBased))
            assertNotNull(Gen2CompactCoreResolver.resolve(session))
            assertNotNull(Gen2CompactMoveNamesResolver.resolve(session, Gen2PlainNameCodec.english53, (0..18).toSet()))
        }
    }

    @Test fun rejectsRecognizedCompactCoreWithBrokenIndicesInsteadOfClassicFallback() {
        for (oneBased in listOf(false, true)) {
            val bytes = fixture(oneBased)
            bytes[if (oneBased) 0x380 else 0x220] = 0
            val probe = probe(bytes)
            assertFalse(probe.hardGatePassed)
            assertNull(probe.resolvedLayout)
        }
    }

    @Test fun rejectsBrokenIndependentMoveNamesInsteadOfRetainingInheritedMoveRoots() {
        val bytes = fixture(false)
        bytes[0x2306] = 0
        assertFalse(probe(bytes).hardGatePassed)
        assertNull(probe(bytes).resolvedLayout)
    }

    @Test fun doesNotAdmitCompactCoreUnderUnmatchedFamilyIdentity() {
        val result = FamilyProbeCoordinator().probe(session(fixture(false)),
            EngineFamilyDefinitions.byFamily.getValue(EngineFamily.GOLD_SILVER))
        assertFalse(result.hardGatePassed)
        assertNull(result.resolvedLayout)
    }

    @Test fun materializesEssentialCatalogAndCompletesWithoutOptionalCallbacksOrRetailPresentation() {
        val bytes = fixture(false)
        val rom = RomImage(bytes)
        val probe = probe(bytes)
        val layout = requireNotNull(probe.resolvedLayout)
        val analysis = ParseResult(HEADER, rom.sha256, rom.crc32, rom.size, SelectionStatus.SELECTED,
            EngineFamily.CRYSTAL, null, null, listOf(probe), probe.capabilities)
        val progress = mutableListOf<CatalogMaterializationPhase>()
        val catalog = CatalogMaterializer.materialize(rom, analysis, layout,
            onProgress = { progress += it.phase },
            resolveWorldMap = { _, _ -> error("unproven world maps must not run") },
            resolveLocalMaps = { _, _ -> error("unproven local maps must not run") },
            resolveMoveDescriptions = { error("unproven prose must not run") },
            resolveAbilityMechanics = { _, _, _, _ -> error("unproven ability mechanics must not run") },
            resolveNatures = { error("unproven natures must not run") },
            resolveItemNames = { _, _ -> error("unproven item names must not run") },
            materializeTheme = { _, _ -> error("unproven theme assets must not run") })
        assertEquals(listOf(CatalogMaterializationPhase.ESSENTIAL, CatalogMaterializationPhase.COMPLETE), progress)
        assertEquals(289, catalog.speciesById.size)
        assertEquals(289, catalog.navigableSpecies().size)
        assertEquals(255, catalog.movesById.size)
        assertEquals(TypeSemanticRole.FIRE, catalog.typesById[9]?.semanticRole?.value)
        assertEquals(0xFFF08030.toInt(), catalog.typesById[9]?.presentation?.value?.backgroundArgb)
        assertTrue(catalog.speciesById.values.all { it.dexNumber.value == null && it.baseStats.value != null })
        val localized = requireNotNull(catalog.localizedText(LanguageTag.ENGLISH))
        assertEquals(catalog.speciesById.keys, localized.speciesNames.keys)
        assertEquals(catalog.movesById.keys, localized.moveNames.keys)
        assertEquals(setOf("Shift"), localized.moveNames.values.map { it.value }.toSet())
        assertEquals(setOf(MoveCategory.SPECIAL), catalog.movesById.values.map { it.category.value }.toSet())
        assertTrue(catalog.speciesById.values.all { it.typeIds.value?.all(catalog.typesById::containsKey) == true })
        assertTrue(catalog.movesById.values.all { it.typeId.value in catalog.typesById.keys })
        assertTrue(catalog.encounterAreas.isEmpty())
        assertTrue(catalog.abilitiesById.isEmpty())
        assertEquals(LanguageResolutionStatus.RESOLVED, catalog.languageManifest.status)
        assertEquals(CapabilityStatus.NOT_FOUND, catalog.capabilities[RomCapability.NATURES]?.status)
    }

    @Test fun fullSyntheticParserSelectsOneFamilyAndPublishesLocalizedCanonicalRecords() {
        val bytes = fixture(false)
        "POKEMON CRYSTAL".toByteArray(Charsets.US_ASCII).copyInto(bytes, 0x134)
        bytes[0x143] = 0x80.toByte()
        val parsed = CatalogParser.parse(RomImage(bytes))
        assertEquals(SelectionStatus.SELECTED, parsed.analysis.status)
        assertEquals(EngineFamily.CRYSTAL, parsed.analysis.selectedFamily)
        assertEquals(289, parsed.catalog?.speciesById?.size)
        assertEquals(255, parsed.catalog?.localizedText(LanguageTag.ENGLISH)?.moveNames?.size)
        assertEquals(289, parsed.layout?.gen2CompactCore?.slots?.size)
    }

    private fun verifyCore(oneBased: Boolean, count: Int) {
        val result = probe(fixture(oneBased))
        assertTrue(result.hardGatePassed)
        assertEquals(90, result.score)
        assertEquals(5, result.anchors)
        val layout = requireNotNull(result.resolvedLayout)
        assertEquals(count, layout.speciesCount)
        assertEquals(count, layout.gen2CompactCore?.slots?.size)
        assertEquals(255, layout.moveCount)
        assertEquals(TableRecordFormat.GEN2_COMPACT_BASE_STATS, layout.tables.baseStats?.format)
        assertEquals(TableRecordFormat.GEN2_SPLIT_MOVE_8, layout.tables.moveData?.format)
        assertNull(layout.tables.sprites)
        assertNull(layout.tables.descriptions)
        assertNull(layout.tables.typeChart)
        assertNull(layout.tables.evolutions)
        assertNull(layout.tables.learnsets)
        assertNull(layout.tables.abilities)
        assertEquals(LanguageTag.ENGLISH, layout.languageManifest.defaultLanguage)
        assertEquals(Gen2PlainNameCodec.english53.id, layout.languageManifest.projections.single().codecId)
        val capabilities = result.capabilities.associateBy { it.capability }
        for (capability in listOf(RomCapability.SPECIES_CATALOG, RomCapability.SPECIES_NAMES,
            RomCapability.BASE_STATS, RomCapability.MOVE_CATALOG, RomCapability.MOVE_DETAILS)) {
            assertEquals(capability.name, CapabilityStatus.AVAILABLE, capabilities[capability]?.status)
        }
        for (capability in listOf(RomCapability.SPRITES, RomCapability.POKEDEX_DESCRIPTIONS,
            RomCapability.TYPE_CHART, RomCapability.ABILITIES, RomCapability.ABILITY_DESCRIPTIONS,
            RomCapability.ABILITY_MECHANICS, RomCapability.NATURES)) {
            assertEquals(capability.name, CapabilityStatus.NOT_FOUND, capabilities[capability]?.status)
        }
    }

    private fun probe(bytes: ByteArray) = FamilyProbeCoordinator().probe(session(bytes),
        EngineFamilyDefinitions.byFamily.getValue(EngineFamily.CRYSTAL))

    private fun session(bytes: ByteArray) = RomAnalysisSession(RomImage(bytes), HEADER)

    private fun fixture(oneBased: Boolean): ByteArray = Gen2CompactCoreResolverTest().fixture(oneBased).also { bytes ->
        bytes.put(0x520, "cd 00 04 7e c9")
        bytes.put(0x2080, "3e 04 18 7c")
        bytes.put(0x2100, "e5 11 00 c8 d5 c5 c6 00 6f ce 22 95 67 f0 87 f5 2a 47 2a cf 2a 66 6f fa 00 c1 ea 00 c2 80 cd 00 23 01 0d 00 e7 f1 cf c3 40 00")
        bytes.put(0x2200, "ff 01 00 60 ff 02 00 60")
        bytes.put(0x2300, "a7 c8 c5 47 2a fe 53 20 fb 05 20 f8 c1 c9")
        bytes.put(0x2400, "3d 21 00 50 01 08 00 df 3e 03 d5 cd 00 05 e1 cd 80 24 01 07 00 09 77 c9")
        bytes.put(0x2480, "e5 01 07 00 09 3e 03 cd 20 05 e1 fe 02 c8 47 fa 00 c2 cb 7f 78 c0 e5 01 03 00 09 3e 03 cd 20 05 e1 fe 09 3e 00 d8 3c c9")
        repeat(255) { index ->
            bytes.put(0xA000 + index * 6, "92 a7 a8 a5 b3 53")
            bytes.put(0xD000 + index * 8, "00 06 50 09 ff 0a 00 01")
            bytes[0xD000 + index * 8] = (index + 1).toByte()
        }
    }

    private fun ByteArray.put(offset: Int, value: String) {
        value.split(' ').map { it.toInt(16).toByte() }.toByteArray().copyInto(this, offset)
    }

    private companion object {
        val HEADER = RomHeader(Platform.GBC, "CRYSTAL")
    }
}
