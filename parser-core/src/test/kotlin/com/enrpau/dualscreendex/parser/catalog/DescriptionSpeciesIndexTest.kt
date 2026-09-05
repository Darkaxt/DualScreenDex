package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.parse.ParserOrchestrator
import com.enrpau.dualscreendex.parser.dataset.descriptions.*
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.*
import com.enrpau.dualscreendex.parser.model.*
import com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class DescriptionSpeciesIndexTest {
    @Test fun compiledDescriptionJoinKeepsPublicRegionalNumbersForAllThreeStrides() {
        for (stride in listOf(28, 32, 36)) {
            val fixture = fixture(stride)
            val catalog = media(fixture)
            for (id in 1..11) {
                val row = fixture.national[id - 1]
                assertEquals(fixture.regional[id - 1], catalog.speciesById.getValue(id).dexNumber.value)
                assertEquals("stride $stride species $id", "prose $row",
                    catalog.defaultLocalizedText()!!.speciesDescriptions.getValue(id).value)
                assertEquals(row + 10, catalog.speciesById.getValue(id).height.value)
                assertEquals(row + 100, catalog.speciesById.getValue(id).weight.value)
            }
        }
    }

    @Test fun divergentMapsWithoutBoundDescriptionConsumerDoNotPublishWrongMedia() {
        val fixture = fixture(28)
        put16(fixture.bytes, 0x200, 0x2000) // no mapping-wrapper call
        val catalog = media(fixture)
        assertEquals(fixture.regional[0], catalog.speciesById.getValue(1).dexNumber.value)
        assertNull(catalog.defaultLocalizedText()!!.speciesDescriptions[1]?.value)
        assertNull(catalog.speciesById.getValue(1).height.value)
    }

    @Test fun wrongRootStrideOrClobberedReturnCannotBindDescriptions() {
        val mutations: List<(Fixture) -> Unit> = listOf(
            { putAccessor(it.bytes, 0x300, 0x3800, 28) },
            { putAccessor(it.bytes, 0x300, 0x3000, 36) },
            { put16(it.bytes, 0x204, 0x2001) },
            { put16(it.bytes, 0x206, 0x0c08) },
            { put16(it.bytes, 0x40 + 18, 0x8809) },
            { put16(it.bytes, 0x300 + 32, 0x8888) },
            { put16(it.bytes, 0x300 + 54, 0x4700) },
        )
        mutations.forEachIndexed { index, mutate ->
            val fixture = fixture(28)
            mutate(fixture)
            val result = SpeciesIndexResolver.resolveWithEvidence(RomImage(fixture.bytes), fixture.layout)
            assertTrue("mutation $index", result.descriptionRows.isEmpty())
        }
    }

    @Test fun mappingWrapperMustActuallyIndexItsSpeciesArgument() {
        for (aliasBase in listOf(false, true)) {
            val fixture = fixture(28)
            if (aliasBase) {
                put16(fixture.bytes, 0x40 + 10, 0x4903)
                put16(fixture.bytes, 0x40 + 16, 0x1849)
            } else {
                put16(fixture.bytes, 0x40 + 12, 0x3a01)
                put16(fixture.bytes, 0x40 + 14, 0x0052)
                put16(fixture.bytes, 0x40 + 16, 0x1812)
                put16(fixture.bytes, 0x40 + 18, 0x8810)
            }
            val result = SpeciesIndexResolver.resolveWithEvidence(RomImage(fixture.bytes), fixture.layout)
            assertTrue("aliasBase=$aliasBase", result.descriptionRows.isEmpty())
        }
    }

    @Test fun mappingWrapperMustReturnThroughItsActualSavedCallerFrame() {
        for (stride in listOf(28, 32, 36)) {
            for (push in listOf(0xb510, 0xb501, 0xb502, 0xb580, 0xb5ff)) {
                val fixture = fixture(stride)
                // POP {r1}; BX r1 would consume the saved low register, not the caller's LR.
                put16(fixture.bytes, 0x40, push)
                val result = SpeciesIndexResolver.resolveWithEvidence(RomImage(fixture.bytes), fixture.layout)
                assertTrue(result is SpeciesIndexResolution.Resolved)
                assertEquals(fixture.regional, (1..11).map { result.values[it] })
                assertTrue("stride=$stride push=${push.toString(16)}", result.descriptionRows.isEmpty())
                val catalog = media(fixture)
                assertNull(catalog.defaultLocalizedText()!!.speciesDescriptions[1]?.value)
                assertNull(catalog.speciesById.getValue(1).height.value)
                assertNull(catalog.speciesById.getValue(1).weight.value)
            }
        }
    }

    @Test fun equivalentRelocatedRootsKeepDescriptionConsumersIndependentOfEncounterOrder() {
        for (stride in listOf(28, 32, 36)) {
            for (duplicateWrapper in listOf(0, 0x100)) {
                for (duplicateRoot in listOf(0x800, 0x2000)) {
                    for (consumer in listOf(0x40, duplicateWrapper)) {
                        val fixture = fixture(stride)
                        fixture.national.forEachIndexed { i, row -> put16(fixture.bytes, duplicateRoot + i * 2, row) }
                        putWrapper(fixture.bytes, duplicateWrapper, duplicateRoot)
                        putCaller(fixture.bytes, 0x200, consumer)
                        val result = SpeciesIndexResolver.resolveWithEvidence(RomImage(fixture.bytes), fixture.layout,
                            ResolutionLimits(maxCandidatesPerDataset = 3))
                        val label = "stride=$stride wrapper=$duplicateWrapper root=$duplicateRoot consumer=$consumer"
                        assertTrue(label, result is SpeciesIndexResolution.Resolved)
                        assertEquals(label, fixture.regional, (1..11).map { result.values[it] })
                        assertEquals(label, fixture.national, (1..11).map { result.descriptionRows[it] })
                        // Both roots may have genuine consumers: identical row values are not conflicting authority.
                        putCaller(fixture.bytes, 0x220, if (consumer == 0x40) duplicateWrapper else 0x40)
                        val catalog = media(fixture)
                        for (id in 1..11) {
                            val row = fixture.national[id - 1]
                            assertEquals(label, fixture.regional[id - 1], catalog.speciesById.getValue(id).dexNumber.value)
                            assertEquals(label, "prose $row", catalog.defaultLocalizedText()!!.speciesDescriptions[id]?.value)
                            assertEquals(label, row + 10, catalog.speciesById.getValue(id).height.value)
                            assertEquals(label, row + 100, catalog.speciesById.getValue(id).weight.value)
                        }
                    }
                }
            }
        }
    }

    @Test fun conflictingBoundMapsCannotChooseAnArbitraryCompositionPartner() {
        val fixture = fixture(28)
        putCaller(fixture.bytes, 0x220, 0xc0)
        val result = SpeciesIndexResolver.resolveWithEvidence(RomImage(fixture.bytes), fixture.layout)
        assertEquals(fixture.regional[0], result.values[1])
        assertTrue(result.descriptionRows.isEmpty())
    }

    @Test fun malformedCompositionSupportCannotAuthorizeDescriptionRows() {
        val fixture = fixture(28)
        // The historical public resolver tolerates one composition mismatch; description authority does not.
        put16(fixture.bytes, 0x1800, 1)
        val result = SpeciesIndexResolver.resolveWithEvidence(RomImage(fixture.bytes), fixture.layout)
        assertTrue(result.descriptionRows.isEmpty())
    }

    @Test fun descriptionAuthorityIsAnImmutableSnapshotIndependentOfPublicMapMutation() {
        val public = linkedMapOf(1 to 2)
        val rows = linkedMapOf(1 to 3)
        val result = SpeciesIndexResolution.Resolved(public, SpeciesDescriptionIndex(rows))
        rows[1] = 9
        public[1] = 8
        assertEquals(3, result.descriptionRows[1])
        assertThrows(UnsupportedOperationException::class.java) {
            (result.descriptionRows as MutableMap)[1] = 7
        }
        assertEquals(result.descriptionRows, result.copy(values = mapOf(1 to 5)).descriptionRows)
    }

    @Test fun twoDivergentCompiledMapsCannotBorrowThePublicPrefixFallbackForDescriptions() {
        val fixture = fixture(28)
        val public = listOf(1, 2) + (3..11).reversed()
        public.forEachIndexed { i, value -> put16(fixture.bytes, 0x1000 + i * 2, value) }
        put16(fixture.bytes, 0x80 + 10, 0) // absent conversion consumer: no unique composition role
        val layout = fixture.layout.copy(compiledGbaReferences = GbaCompiledReferenceIndex(mapOf(0x1000 to 1, 0x1400 to 1)))
        val result = SpeciesIndexResolver.resolveWithEvidence(RomImage(fixture.bytes), layout)
        assertEquals(public, (1..11).map { result.values[it] })
        assertTrue(result.descriptionRows.isEmpty())
    }

    @Test fun singleCompiledWesternMapRetainsExistingJoinWithoutNewConsumerRequirement() {
        for (stride in listOf(32, 36)) {
            val fixture = fixture(stride)
            put16(fixture.bytes, 0x80 + 10, 0)
            put16(fixture.bytes, 0xc0 + 10, 0)
            put16(fixture.bytes, 0x200, 0)
            val result = SpeciesIndexResolver.resolveWithEvidence(RomImage(fixture.bytes), fixture.layout)
            assertEquals(fixture.national[0], result.values[1])
            assertEquals(result.values, result.descriptionRows)
        }
    }

    @Test fun reorderedRootsAndPostReservedSpeciesUseCompiledRowsNotInternalArithmetic() {
        for (stride in listOf(28, 32, 36)) {
            val fixture = fixture(stride, count = 412, descriptionCount = 387,
                roots = listOf(0x1800, 0x1000, 0x1400), descriptionRoot = 0x4000)
            val result = SpeciesIndexResolver.resolveWithEvidence(RomImage(fixture.bytes), fixture.layout)
            assertEquals(252, result.descriptionRows[277])
            assertEquals(fixture.regional[276], result.values[277])
            val row = RelationshipMaterializers.descriptions(RomImage(fixture.bytes), fixture.layout)
                .getValue(result.descriptionRows.getValue(277))
            assertEquals("category 252", row.category)
            assertEquals("prose 252", row.text)
            val catalog = media(fixture)
            assertEquals("prose 252", catalog.defaultLocalizedText()!!.speciesDescriptions.getValue(277).value)
            assertEquals(262, catalog.speciesById.getValue(277).height.value)
            assertEquals(352, catalog.speciesById.getValue(277).weight.value)
        }
    }

    @Test fun descriptionBindingHonorsSelectedTableExtentAndCallerBudgets() {
        for (limits in listOf(ResolutionLimits(maxDatasetExtentBytes = 40),
            ResolutionLimits(maxCompiledReferenceSitesPerCandidate = 1),
            ResolutionLimits(maxNominatedGbaReferenceSites = 1),
            ResolutionLimits(maxProbeWorkPerDataset = 1),
            ResolutionLimits(maxCandidatesPerDataset = 1))) {
            val fixture = fixture(28)
            putCaller(fixture.bytes, 0x220, 0x40)
            val result = SpeciesIndexResolver.resolveWithEvidence(RomImage(fixture.bytes), fixture.layout, limits)
            val descriptionOnlyBudget = limits.maxDatasetExtentBytes == 40L ||
                limits.maxCompiledReferenceSitesPerCandidate == 1 || limits.maxNominatedGbaReferenceSites == 1
            if (descriptionOnlyBudget) {
                assertTrue("description budget must preserve resolved public numbering", result is SpeciesIndexResolution.Resolved)
                assertEquals(fixture.regional, (1..11).map { result.values[it] })
                assertTrue((result as SpeciesIndexResolution.Resolved).descriptionIndex.unavailableReason!!.contains("budget"))
            } else {
                assertTrue("limits $limits: $result", result is SpeciesIndexResolution.BudgetExceeded)
            }
            assertTrue(result.descriptionRows.isEmpty())
        }
    }

    @Test fun descriptionMappingCancellationPropagatesAtEntryAndDuringDiscovery() {
        for (successfulChecks in listOf(0, 30)) {
            val fixture = fixture(28)
            var checks = 0
            val session = RomAnalysisSession(RomImage(fixture.bytes), RomHeader(Platform.GBA, "TEST"),
                cancellation = ParserCancellationToken {
                    if (checks++ >= successfulChecks) throw ParserCancellationException()
                })
            assertThrows(ParserCancellationException::class.java) { SpeciesIndexResolver.resolve(session, fixture.layout) }
        }
    }

    @Test fun semanticCallerPropagatesSessionCancellationIntoBothDescriptionBindings() {
        for (cancelDuringDomain in listOf(true, false)) {
            val fixture = fixture(28)
            putWrapper(fixture.bytes, 0x400, 0x1000)
            val layout = fixture.layout.copy(tables = fixture.layout.tables.copy(baseStats = TableLayout(0xa000, 12, 28)))
            val capabilities = listOf(RomCapability.SPECIES_NAMES, RomCapability.BASE_STATS, RomCapability.POKEDEX_DESCRIPTIONS)
                .map { CapabilityEvidence(it, true, 1.0, count = 12, validRecords = 12, totalRecords = 12) }
            var reachedBinding = false
            val session = RomAnalysisSession(RomImage(fixture.bytes), RomHeader(Platform.GBA, "TEST"),
                cancellation = ParserCancellationToken {
                    // Arm only inside real binding, not the outer stage's entry check. This catches
                    // either nested semantic-domain materialization or its later coverage projection.
                    val frames = Throwable().stackTrace
                    val inBinding = frames.any { it.methodName == "bindDescriptionIndex" }
                    val inDomain = frames.any { it.className.endsWith(".SpeciesSemanticDomainResolver") }
                    if (inBinding && inDomain == cancelDuringDomain) {
                        reachedBinding = true
                        throw ParserCancellationException()
                    }
                })
            assertThrows("domain=$cancelDuringDomain", ParserCancellationException::class.java) {
                ParserOrchestrator.applySpeciesSemanticDomain(session, layout, capabilities)
            }
            assertTrue(reachedBinding)
        }
    }

    @Test fun semanticDescriptionCoverageUsesTheSameFailClosedAuthorityAsCatalogMedia() {
        val fixture = fixture(28)
        putWrapper(fixture.bytes, 0x400, 0x1000) // second compiled reference makes the semantic domain authoritative
        val layout = fixture.layout.copy(tables = fixture.layout.tables.copy(baseStats = TableLayout(0xa000, 12, 28)))
        val capabilities = listOf(RomCapability.SPECIES_NAMES, RomCapability.BASE_STATS, RomCapability.POKEDEX_DESCRIPTIONS)
            .map { CapabilityEvidence(it, true, 1.0, count = 12, validRecords = 12, totalRecords = 12) }
        put16(fixture.bytes, 0x200, 0)
        val evidence = ParserOrchestrator.applySpeciesSemanticDomain(RomImage(fixture.bytes), layout, capabilities)
            .single { it.capability == RomCapability.POKEDEX_DESCRIPTIONS }
        assertEquals(11, evidence.expectedRecords)
        assertEquals(0, evidence.coveredRecords)
    }

    private data class Fixture(
        val bytes: ByteArray,
        val layout: ResolvedRomLayout,
        val regional: List<Int>,
        val national: List<Int>,
    )

    private fun fixture(
        stride: Int,
        count: Int = 12,
        descriptionCount: Int = count,
        roots: List<Int> = listOf(0x1000, 0x1400, 0x1800),
        descriptionRoot: Int = 0x3000,
    ): Fixture {
        val bytes = ByteArray(0x12000) { 0x7f }
        val regional = (1 until count).shuffled(Random(1234))
        val conversion = (1 until count).shuffled(Random(5678)).toMutableList()
        val national = if (count == 412) {
            ((1..251) + (387..411) + (252..386)).also { national ->
                regional.forEachIndexed { i, r -> conversion[r - 1] = national[i] }
            }
        } else regional.map { conversion[it - 1] }
        for ((root, values) in roots.zip(listOf(regional, national, conversion))) {
            values.forEachIndexed { i, v -> put16(bytes, root + i * 2, v) }
        }
        putWrapper(bytes, 0x40, roots[1])
        putWrapper(bytes, 0x80, roots[2])
        putWrapper(bytes, 0xc0, roots[0])
        putCaller(bytes, 0x200, 0x40)
        putAccessor(bytes, 0x300, descriptionRoot, stride)
        val codec = if (stride == 28) JapanesePokemonTextCodecs.gen3Later else PokemonTextCodec.gbaEnglish
        repeat(count) { id ->
            val name = if (stride == 28) byteArrayOf(1, 2, 0xff.toByte()) else byteArrayOf(0xc7.toByte(), 0xc9.toByte(), 0xc8.toByte(), 0xff.toByte())
            name.copyInto(bytes, 0x8000 + id * 6)
        }
        val names = TableLayout(0x8000, count, 6)
        val table = TableLayout(descriptionRoot, descriptionCount, stride, pointerOffsets = listOf(if (stride == 28) 12 else 16))
        val typedTable = DescriptionTableLayout(descriptionRoot.toLong(), descriptionCount.toLong(), stride, table.pointerOffsets)
        val rows = List(descriptionCount) { row -> DescriptionRowOutcome.Decoded(row, "category $row", row + 10, row + 100,
            listOf(DecodedDescriptionPage("prose $row", DescriptionRecoveryProvenance.Direct(0x5000 + row * 16)))) }
        val manifest = RomLanguageManifest(codec.language,
            listOf(RomLanguageProjection(codec.language, codec.id, codec.version,
                LocalizedTableLayout(speciesNames = names, descriptions = table), emptyList(), LanguageResolutionStatus.RESOLVED)),
            LanguageResolutionStatus.RESOLVED)
        return Fixture(bytes, ResolvedRomLayout(EngineFamily.EMERALD, 3, Platform.GBA, count, 0,
            ProfileTables(speciesNames = names, descriptions = table), languageManifest = manifest,
            resolvedDatasets = ResolvedDatasetLayouts(descriptions = ResolvedDescriptionLayout(typedTable, rows))), regional, national)
    }

    private fun media(fixture: Fixture): ParsedCatalog {
        val rom = RomImage(fixture.bytes)
        val analysis = ParseResult(RomHeader(Platform.GBA, "TEST"), rom.sha256, rom.crc32, rom.size,
            SelectionStatus.SELECTED, EngineFamily.EMERALD, null, 20, emptyList(), emptyList())
        var media: ParsedCatalog? = null
        assertThrows(MediaComplete::class.java) {
            CatalogMaterializer.materialize(rom, analysis, fixture.layout, onProgress = {
                if (it.phase == CatalogMaterializationPhase.SPECIES_MEDIA) {
                    media = it.catalog
                    throw MediaComplete()
                }
            })
        }
        return requireNotNull(media)
    }

    private class MediaComplete : RuntimeException()

    private fun putWrapper(bytes: ByteArray, start: Int, root: Int) {
        listOf(0xb500, 0x0400, 0x0c01, 0x2900, 0xd008, 0x4803, 0x3901, 0x0049,
            0x1809, 0x8808, 0xe003, 0x0000).forEachIndexed { i, v -> put16(bytes, start + i * 2, v) }
        put32(bytes, start + 24, 0x08000000 + root)
        listOf(0x2000, 0xbc02, 0x4708).forEachIndexed { i, v -> put16(bytes, start + 28 + i * 2, v) }
    }

    private fun putAccessor(bytes: ByteArray, start: Int, root: Int, stride: Int) {
        // GetPokedexHeightWeight: u16 argument; selector 0/1; both branches use the same table.
        val prefix = listOf(0xb500, 0x0400, 0x0c02, 0x0609, 0x0e09, 0x2900, 0xd003, 0x2901, 0xd00a, 0x2001, 0xe00e)
        prefix.forEachIndexed { i, v -> put16(bytes, start + i * 2, v) }
        for ((offset, field) in listOf(22 to (if (stride == 28) 6 else 12), 40 to (if (stride == 28) 8 else 14))) {
            val multiply = when (stride) {
                28 -> listOf(0x00d1, 0x1a89, 0x0089)
                36 -> listOf(0x00d1, 0x1889, 0x0089)
                else -> listOf(0x0151, 0x1c09, 0x1c09)
            }
            (listOf(0x4803) + multiply + listOf(0x1809, 0x8808 or ((field / 2) shl 6)))
                .forEachIndexed { i, v -> put16(bytes, start + offset + i * 2, v) }
            put16(bytes, start + offset + 12, if (offset == 22) 0xe007 else 0xbc02)
            if (offset == 40) put16(bytes, start + 54, 0x4708)
            put32(bytes, ((start + offset + 4) and -4) + 12, 0x08000000 + root)
        }
    }

    private fun putCaller(bytes: ByteArray, offset: Int, wrapper: Int) {
        putBl(bytes, offset, wrapper)
        put16(bytes, offset + 4, 0x0400)
        put16(bytes, offset + 6, 0x0c00)
        put16(bytes, offset + 8, 0x2101)
        putBl(bytes, offset + 10, 0x300)
    }

    private fun putBl(bytes: ByteArray, offset: Int, target: Int) {
        val displacement = target - offset - 4
        put16(bytes, offset, 0xf000 or ((displacement shr 12) and 0x7ff))
        put16(bytes, offset + 2, 0xf800 or ((displacement shr 1) and 0x7ff))
    }
    private fun put16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte(); bytes[offset + 1] = (value ushr 8).toByte()
    }
    private fun put32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { bytes[offset + it] = (value ushr (it * 8)).toByte() }
    }
}
