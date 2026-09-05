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
    @Test fun compiledAdjacentPaletteExcludesOnlyDescriptionFieldsAndPreservesEveryPublicRecord() {
        for (roots in listOf(listOf(0x1000, 0x1400, 0x1800), listOf(0x1800, 0x1000, 0x1400))) {
            val fixture = fixture(28, descriptionCount = 8, roots = roots)
            putPaletteNeighbor(fixture.bytes, 0x3000 + 8 * 28)
            val before = RecordMaterializers.species(RomImage(fixture.bytes), fixture.layout)
            val catalog = media(fixture)
            assertEquals(before.keys, catalog.speciesById.keys)
            for ((id, record) in catalog.speciesById) {
                assertEquals(before.getValue(id).dexNumber, record.dexNumber)
                assertEquals(before.getValue(id).baseStats, record.baseStats)
                assertEquals(before.getValue(id).typeIds, record.typeIds)
                assertEquals(before.getValue(id).navigable, record.navigable)
                if (id > 0 && fixture.national[id - 1] >= 8) {
                    assertEquals("species $id", CapabilityStatus.NOT_APPLICABLE, record.description.status)
                    assertEquals(CapabilityStatus.NOT_APPLICABLE, record.height.status)
                    assertEquals(CapabilityStatus.NOT_APPLICABLE, record.weight.status)
                }
            }
            val capability = catalog.defaultLocalizedText()!!.localizedCapabilities.getValue(
                LocalizedTextCapability.SPECIES_DESCRIPTIONS)
            assertEquals(7, capability.coveredRecords)
            assertEquals(7, capability.expectedRecords)
        }
    }

    @Test fun unconsumedZeroNeighborDoesNotAuthorizeDescriptionExclusion() {
        val fixture = fixture(28, descriptionCount = 8)
        fixture.bytes.fill(0, 0x3000 + 8 * 28, 0x3000 + 8 * 28 + 32)
        val catalog = media(fixture)
        fixture.national.forEachIndexed { i, row ->
            if (row >= 8) {
                assertNull(catalog.defaultLocalizedText()!!.speciesDescriptions[i + 1]?.value)
                assertEquals(CapabilityStatus.NOT_FOUND, catalog.speciesById.getValue(i + 1).height.status)
                assertEquals(CapabilityStatus.NOT_FOUND, catalog.speciesById.getValue(i + 1).weight.status)
            }
        }
    }

    @Test fun malformedNeighborWitnessesAndNonAdjacentObjectsCannotExcludeDescriptions() {
        val mutations: List<(Fixture) -> Unit> = listOf(
            { put16(it.bytes, 0x500, 0x4907) }, // pointer clobbered/wrong argument
            { put16(it.bytes, 0x50a, 0x2210) }, // wrong object length
            { put16(it.bytes, 0x600 + 18, 0x0c2d) }, // wrong byte-to-halfword conversion
            { put16(it.bytes, 0x600 + 44, 0x4708) }, // wrong saved return
            { put16(it.bytes, 0x700, 0xdf0c) }, // unsupported service
            { putBl(it.bytes, 0x600 + 36, 0x704) }, // conflicting copy targets
            { put32(it.bytes, 0x520, 0x080030e4) }, // gap
            { put32(it.bytes, 0x520, 0x080030dc) }, // overlap
            { putBl(it.bytes, 0x50c, it.bytes.size - 20) }, // truncated callee
            { put16(it.bytes, 0x500, 0x48ff) }, // wrong literal
            { putCaller(it.bytes, 0x220, 0xc0) }, // conflicting B authority
        )
        mutations.forEachIndexed { index, mutate ->
            val fixture = fixture(28, descriptionCount = 8)
            putPaletteNeighbor(fixture.bytes, 0x30e0)
            mutate(fixture)
            val catalog = media(fixture)
            assertEquals("mutation $index", 11, catalog.defaultLocalizedText()!!.localizedCapabilities
                .getValue(LocalizedTextCapability.SPECIES_DESCRIPTIONS).expectedRecords)
            assertTrue(catalog.speciesById.filterKeys { it > 0 }.values.all {
                it.height.status != CapabilityStatus.NOT_APPLICABLE && it.weight.status != CapabilityStatus.NOT_APPLICABLE
            })
        }
    }

    @Test fun inDomainMissingProseRemainsApplicableAndDoesNotChangePublicRecords() {
        val original = fixture(28, descriptionCount = 8)
        putPaletteNeighbor(original.bytes, 0x30e0)
        val descriptions = original.layout.resolvedDatasets.descriptions!!
        val fixture = original.copy(layout = original.layout.copy(resolvedDatasets = ResolvedDatasetLayouts(
            descriptions = ResolvedDescriptionLayout(descriptions.table, descriptions.rows.map { if (it.rowIndex == 3) DescriptionRowOutcome.Malformed(3, listOf("unterminated prose")) else it }))))
        val catalog = media(fixture)
        val id = fixture.national.indexOf(3) + 1
        assertEquals(fixture.regional[id - 1], catalog.speciesById.getValue(id).dexNumber.value)
        assertNull(catalog.defaultLocalizedText()!!.speciesDescriptions[id]?.value)
        assertEquals(CapabilityStatus.NOT_FOUND, catalog.speciesById.getValue(id).height.status)
        assertEquals(CapabilityStatus.NOT_FOUND, catalog.speciesById.getValue(id).description.status)
        val capability = catalog.defaultLocalizedText()!!.localizedCapabilities.getValue(LocalizedTextCapability.SPECIES_DESCRIPTIONS)
        assertEquals(6, capability.coveredRecords)
        assertEquals(7, capability.expectedRecords)
    }

    @Test fun extentProofHonorsWorkNominationCancellationAndTruncatedObjects() {
        val fixture = fixture(28, descriptionCount = 8)
        putPaletteNeighbor(fixture.bytes, 0x30e0)
        val table = fixture.layout.resolvedDatasets.descriptions!!.table
        assertFalse(CompiledDescriptionExtentBinding.matches(RomImage(fixture.bytes), table, ResolutionLimits()) { false })
        assertThrows(ParserCancellationException::class.java) {
            CompiledDescriptionExtentBinding.matches(RomImage(fixture.bytes), table, ResolutionLimits()) { throw ParserCancellationException() }
        }
        putPaletteNeighbor(fixture.bytes, 0x30e0, site = 0x800)
        assertFalse(CompiledDescriptionExtentBinding.matches(RomImage(fixture.bytes), table,
            ResolutionLimits(maxNominatedGbaReferenceSites = 1)) { true })
        assertFalse(CompiledDescriptionExtentBinding.matches(RomImage(fixture.bytes.copyOf(0x30f0)), table, ResolutionLimits()) { true })
    }

    @Test fun descriptionOnlySemanticDomainKeepsNamesAndStatsUntrimmed() {
        val fixture = fixture(28, descriptionCount = 8)
        putPaletteNeighbor(fixture.bytes, 0x30e0)
        putWrapper(fixture.bytes, 0x400, 0x1000)
        val layout = fixture.layout.copy(tables = fixture.layout.tables.copy(baseStats = TableLayout(0xa000, 12, 28)))
        val capabilities = listOf(RomCapability.SPECIES_NAMES, RomCapability.BASE_STATS, RomCapability.POKEDEX_DESCRIPTIONS)
            .map { CapabilityEvidence(it, true, 1.0, count = 12, validRecords = 12, totalRecords = 12) }
        val actual = ParserOrchestrator.applySpeciesSemanticDomain(RomImage(fixture.bytes), layout, capabilities)
        assertEquals(11, actual.single { it.capability == RomCapability.SPECIES_NAMES }.expectedRecords)
        assertEquals(11, actual.single { it.capability == RomCapability.BASE_STATS }.expectedRecords)
        assertEquals(7, actual.single { it.capability == RomCapability.POKEDEX_DESCRIPTIONS }.expectedRecords)
        assertEquals(7, actual.single { it.capability == RomCapability.POKEDEX_DESCRIPTIONS }.coveredRecords)
    }

    @Test fun compiledAdjacentBackgroundTemplatesExcludeOnlyDescriptionRows() {
        for (shift in listOf(0, 0x100)) {
            val fixture = fixture(28, descriptionCount = 8)
            putBackgroundNeighbor(fixture.bytes, 0x30e0, shift)
            val catalog = media(fixture)
            assertEquals(12, catalog.speciesById.size)
            assertEquals(7, catalog.defaultLocalizedText()!!.localizedCapabilities
                .getValue(LocalizedTextCapability.SPECIES_DESCRIPTIONS).expectedRecords)
            fixture.national.forEachIndexed { i, row ->
                assertEquals(fixture.regional[i], catalog.speciesById.getValue(i + 1).dexNumber.value)
                if (row >= 8) assertEquals(CapabilityStatus.NOT_APPLICABLE, catalog.speciesById.getValue(i + 1).height.status)
            }
        }
    }

    @Test fun backgroundConsumerRequiresFullLoopAndCalleePreservation() {
        for (offset in listOf(12, 48, 58, 100, 172, 178, 184, 188, 200,
            0x300 + 72, 0x300 + 282, 0x600 + 16, 0x680 + 18)) {
            val fixture = fixture(28, descriptionCount = 8)
            putBackgroundNeighbor(fixture.bytes, 0x30e0)
            put16(fixture.bytes, 0x600 + offset, 0)
            val catalog = media(fixture)
            assertEquals("body offset $offset", 11, catalog.defaultLocalizedText()!!.localizedCapabilities
                .getValue(LocalizedTextCapability.SPECIES_DESCRIPTIONS).expectedRecords)
        }
    }

    @Test fun conflictingTypedAdjacentObjectLengthsDoNotExcludeDescriptionRows() {
        val fixture = fixture(28, descriptionCount = 8)
        putBackgroundNeighbor(fixture.bytes, 0x30e0)
        putPaletteNeighbor(fixture.bytes, 0x30e0, site = 0xe80, callee = 0xe00, cpu = 0xf00)
        val catalog = media(fixture)
        assertEquals(11, catalog.defaultLocalizedText()!!.localizedCapabilities
            .getValue(LocalizedTextCapability.SPECIES_DESCRIPTIONS).expectedRecords)
    }

    private fun putBackgroundNeighbor(bytes: ByteArray, root: Int, shift: Int = 0) {
        val site = 0x500 + shift
        val body = 0x600 + shift
        val setter = 0x900 + shift
        listOf(0x4907, 0x2000, 0x2204).forEachIndexed { i, op -> put16(bytes, site + i * 2, op) }
        putBl(bytes, site + 6, body)
        put32(bytes, site + 32, 0x08000000 + root)
        listOf(0x50, 0x1049, 0x206a, 0x3073).forEachIndexed { i, v -> put32(bytes, root + i * 4, v) }
        listOf(0xb5f0, 0x4657, 0x464e, 0x4645, 0xb4e0, 0xb084, 0x1c0d, 0x600, 0xe00, 0x612, 0xe14, 0xf7ff, 0xfce7, 0xf7ff, 0xfcfb, 0x2c00, 0xd04b, 0x2700, 0x4829, 0x4681, 0x1c2e, 0x4a29, 0x4692, 0x46a0, 0x6834, 0x7a0, 0xf85, 0x2d03, 0xd838, 0x721, 0xf89, 0x5e2, 0xed2, 0x563, 0xf9b, 0x520, 0xfc0, 0x9000, 0x4a0, 0xf80, 0x9001, 0x9702, 0x9703, 0x1c28, 0xf7ff, 0xfd02, 0x12c, 0x464d, 0x1963, 0x6832, 0x212, 0xd92, 0x8818, 0x4d1a, 0x1c29, 0x4008, 0x4310, 0x8018, 0x7858, 0x223d, 0x4252, 0x1c11, 0x4008, 0x7058, 0x6818, 0x4915, 0x4008, 0x6018, 0x4648, 0x3004, 0x1820, 0x6007, 0x4648, 0x3008, 0x1820, 0x6007, 0x4d10, 0x1964, 0x6027, 0x6830, 0x700, 0xf80, 0x180, 0x4450, 0x2101, 0x7001, 0x3604, 0x2001, 0x4240, 0x4480, 0x4642, 0x2a00, 0xd1ba, 0xb004, 0xbc38, 0x4698, 0x46a1, 0x46aa, 0xbcf0, 0xbc01, 0x4700).forEachIndexed { i, op -> put16(bytes, body + i * 2, op) }
        putBl(bytes, body + 22, 0xc00)
        putBl(bytes, body + 26, 0xc40)
        putBl(bytes, body + 88, setter)
        put32(bytes, body + 204, 0x03001000)
        put32(bytes, body + 208, 0x03001050)
        put32(bytes, body + 212, 0xfffffc00.toInt())
        put32(bytes, body + 216, 0x3fff)
        put32(bytes, body + 220, 0x0300100c)
        listOf(0xb5f0, 0x4657, 0x464e, 0x4645, 0xb4e0, 0xb084, 0x9c0c, 0x9d0d, 0x9e0e, 0x46b4, 0x9e0f, 0x46b0, 0x600, 0xe07, 0x9700, 0x609, 0xe09, 0x468a, 0x612, 0xe16, 0x61b, 0xe1b, 0x4699, 0x624, 0xe24, 0x9401, 0x62d, 0xe2d, 0x4660, 0x600, 0xe04, 0x4641, 0x609, 0xe09, 0x9103, 0x1c38, 0xf000, 0xf9ec, 0x600, 0xe00, 0x4684, 0x2800, 0xd160, 0x4a34, 0x4690, 0x4650, 0x28ff, 0xd009, 0xba, 0x4442, 0x2103, 0x4001, 0x7853, 0x2004, 0x4240, 0x4018, 0x4308, 0x7050, 0x2eff, 0xd009, 0xb9, 0x4441, 0x201f, 0x4006, 0xb3, 0x784a, 0x389c, 0x4010, 0x4318, 0x7048, 0x4649, 0x29ff, 0xd00a, 0xb9, 0x4441, 0x2003, 0x464a, 0x4002, 0x93, 0x780a, 0x3810, 0x4010, 0x4318, 0x7008, 0x9e01, 0x2eff, 0xd007, 0xb9, 0x4441, 0x1f3, 0x784a, 0x207f, 0x4010, 0x4318, 0x7048, 0x2dff, 0xd009, 0xb9, 0x4441, 0x2003, 0x4005, 0x12b, 0x780a, 0x3834, 0x4010, 0x4318, 0x7008, 0x2cff, 0xd009, 0xb9, 0x4441, 0x2001, 0x4004, 0x1a3, 0x780a, 0x3842, 0x4010, 0x4318, 0x7008, 0x9803, 0x28ff, 0xd007, 0xb9, 0x4441, 0x1c3, 0x780a, 0x207f, 0x4010, 0x4318, 0x7008, 0x9900, 0x88, 0x4440, 0x4662, 0x7082, 0x70c2, 0x7801, 0x2201, 0x4311, 0x7001, 0xb004, 0xbc38, 0x4698, 0x46a1, 0x46aa, 0xbcf0, 0xbc01, 0x4700).forEachIndexed { i, op -> put16(bytes, setter + i * 2, op) }
        putBl(bytes, setter + 72, 0xc80)
        put32(bytes, setter + 296, 0x03001080)
        listOf(0x0600, 0x0e00, 0x4b03, 0x8a1a, 0x4903, 0x4011, 0x4301, 0x8219, 0x4770)
            .forEachIndexed { i, op -> put16(bytes, 0xc00 + i * 2, op) }
        put32(bytes, 0xc14, 0x03001080)
        put32(bytes, 0xc18, 0xfff8)
        listOf(0xb500, 0x4a05, 0x4805, 0x6800, 0x1c11, 0x310c, 0x6008, 0x3904, 0x4291, 0xdafb, 0xbc01, 0x4700)
            .forEachIndexed { i, op -> put16(bytes, 0xc40 + i * 2, op) }
        put32(bytes, 0xc58, 0x03001080)
        put32(bytes, 0xc5c, 0x08000d00)
        put32(bytes, 0xd00, 0)
        listOf(0xb500, 0x0600, 0x0e00, 0x2803, 0xd801, 0x2000, 0xe000, 0x2001, 0xbc02, 0x4708)
            .forEachIndexed { i, op -> put16(bytes, 0xc80 + i * 2, op) }
    }

    private fun putPaletteNeighbor(bytes: ByteArray, root: Int, site: Int = 0x500, callee: Int = 0x600, cpu: Int = 0x700) {
        bytes.fill(0, root, root + 32)
        listOf(0x4807, 0x7961, 0x0909, 0x3110, 0x0109, 0x2220).forEachIndexed { i, op -> put16(bytes, site + i * 2, op) }
        putBl(bytes, site + 12, callee)
        put32(bytes, site + 32, 0x08000000 + root)
        listOf(0xb570, 0x1c06, 0x1c0c, 0x1c15, 0x0424, 0x042d, 0x0be4, 0x4908,
            0x1861, 0x0c6d, 0x1c2a, 0, 0, 0x4806, 0x1824, 0x1c30, 0x1c21, 0x1c2a,
            0, 0, 0xbc70, 0xbc01, 0x4700, 0).forEachIndexed { i, op -> put16(bytes, callee + i * 2, op) }
        putBl(bytes, callee + 22, cpu)
        putBl(bytes, callee + 36, cpu)
        put32(bytes, callee + 48, 0x02001000)
        put32(bytes, callee + 52, 0x02001400)
        put16(bytes, cpu, 0xdf0b)
        put16(bytes, cpu + 2, 0x4770)
    }

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
