package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.family.*
import com.enrpau.dualscreendex.parser.model.*
import com.enrpau.dualscreendex.parser.text.WesternPokemonTextCodecs
import com.enrpau.dualscreendex.parser.validate.TableValidators
import org.junit.Assert.*
import org.junit.Test

/** Relocated hand-assembled core consumers and freshly validated synthetic physical tables. */
class Gen3SelectedCoreItemRouteTest {
    private data class Fixture(
        val f: ItemConsumerFixture,
        val getter: Int,
        val predicate: Int,
        val leaf: Int,
        val names: TableLayout,
        val stats: TableLayout,
    ) {
        fun route(session: RomAnalysisSession): Gen3SelectedCoreItemRoute = requireNotNull(
            Gen3SelectedCoreItemRoute.fromValidated(session, names, stats,
                TableValidators.names(session.rom, names, names.count, WesternPokemonTextCodecs.gen3English),
                TableValidators.baseStats(session.rom, stats.offset, stats.count, stats.recordSize, 3)))
    }

    private fun fixture(getter: Int = 0x2000, predicate: Int = 0x2200, leaf: Int = 0x2240,
                        nameRoot: Int = 0x4800, statRoot: Int = 0x5000, nameWidth: Int = 11,
                        first: Int = 3, last: Int = 2, count: Int = 16, inclusive: Boolean = true): Fixture {
        val f = ItemConsumerFixture(root = 0x6000, firstShift = 3, finalShift = 3, nameBytes = 14,
            simpleWrapper = true)
        val names = TableLayout(nameRoot, count, nameWidth)
        val stats = TableLayout(statRoot, count, ((1 shl first) + 1) shl last)
        for (id in 0 until count) {
            f.bytes[nameRoot + id * nameWidth] = 0xBB.toByte()
            f.bytes[nameRoot + id * nameWidth + 1] = 0xFF.toByte()
            for (at in 0..5) f.bytes[statRoot + id * stats.recordSize + at] = 50
        }
        f.emit(getter, 0xB500, 0x0400, 0x0C00)
        f.bl(getter + 6, predicate)
        f.emit(getter + 10, 0x0400, 0x0C00, 0x2100 or nameWidth, 0x4348)
        f.literalLoad(getter + 18, 1, getter + 28, nameRoot)
        f.emit(getter + 20, 0x1840, 0xBC02, 0x4708, 0)
        f.emit(predicate, 0xB510, 0x0400, 0x0C04, 0x4805, 0x4284, 0xD804, 0x1C20)
        f.bl(predicate + 14, leaf)
        f.emit(predicate + 18, 0x2800, 0xD104, 0x2000, 0xE003, 0)
        f.half(predicate + 28, count - if (inclusive) 0 else 1)
        f.half(predicate + 30, 0)
        f.emit(predicate + 32, 0x1C20, 0xBC10, 0xBC02, 0x4708)
        f.emit(leaf, 0x0400, 0x0C00, 0x4A04, (first shl 6) or 1, 0x1809,
            (last shl 6) or 9, 0x1889, 0x7809, 0x4248, 0x4308, 0x0FC0, 0x4770)
        f.pointer(leaf + 24, statRoot)
        return Fixture(f, getter, predicate, leaf, names, stats)
    }

    private fun authority(x: Fixture, session: RomAnalysisSession = x.f.session(),
                          route: GbaItemPublishedRoute = GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Absent)):
        GbaItemNameAuthority? = (session.itemNameResolver.selectedCore(x.route(session), route)
            as? Gen3SelectedCoreItemOutcome.Evaluated)?.authority

    @Test(timeout = 60_000)
    fun completeSelectedCoreBindingNominatesIndependentRouteWithoutPromotingPhysicalCounts() {
        for (case in listOf(
            listOf(0x2000, 0x2200, 0x2240, 0x4800, 0x5000, 11, 3, 2, 16),
            listOf(0x2800, 0x2A00, 0x2B00, 0x3800, 0x4800, 15, 2, 3, 29),
            listOf(0x3000, 0x3200, 0x3300, 0x4400, 0x5000, 9, 1, 4, 21))) {
            for (inclusive in listOf(false, true)) {
                val x = fixture(case[0], case[1], case[2], case[3], case[4], case[5], case[6], case[7], case[8], inclusive)
                val session = x.f.session()
                val published = GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Absent)
                assertTrue(session.itemNameResolver.original(published) is GbaItemNameAuthority.Unavailable)
                assertTrue(session.itemNameResolver.original(GbaItemPublishedRoute.NotInvoked)
                    is GbaItemNameAuthority.Unavailable)
                val result = authority(x, session)
                assertTrue("independently bound native hint: $result", result is GbaItemNameAuthority.Available)
                assertEquals(377, (result as GbaItemNameAuthority.Available).count)
                assertEquals(x.names.count, x.route(session).count) // inclusive bound is not count+1
                assertTrue(session.itemNameResolver.original(published) is GbaItemNameAuthority.Unavailable)
            }
        }
    }

    @Test(timeout = 60_000)
    fun absentNativeBindingDoesNotNominateOrReplaceExistingOriginalAuthority() {
        val x = fixture()
        x.f.bytes.fill(0, x.getter, x.getter + 32)
        val session = x.f.session()
        val original = session.itemNameResolver.original(GbaItemPublishedRoute.NotInvoked)
        assertTrue(original is GbaItemNameAuthority.Available)
        assertSame(Gen3SelectedCoreItemOutcome.NotNominated,
            session.itemNameResolver.selectedCore(x.route(session), GbaItemPublishedRoute.NotInvoked))
        assertSame(original, session.itemNameResolver.original(GbaItemPublishedRoute.NotInvoked))
    }

    @Test(timeout = 60_000)
    fun publishedConflictAndAmbiguityRemainTerminalForTheIndependentRoute() {
        for ((nomination, accepted) in listOf(
            GbaItemRootNomination.Absent to true,
            GbaItemRootNomination.Nominated(0x6000) to true,
            GbaItemRootNomination.Nominated(0x6004) to false,
            GbaItemRootNomination.Ambiguous to false)) {
            val x = fixture()
            val result = authority(x, route = GbaItemPublishedRoute.Invoked(nomination))
            assertNotNull(result)
            assertEquals("nomination=$nomination", accepted, result is GbaItemNameAuthority.Available)
        }
    }

    @Test(timeout = 60_000)
    fun everyNativeGetterPredicateAndBooleanLeafDependencyRemainsRequired() {
        val sample = fixture()
        val dependencies = listOf(
            sample.getter to listOf(0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26),
            sample.predicate to listOf(0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 32, 34, 36, 38),
            sample.leaf to (0..22 step 2).toList())
        for ((entry, offsets) in dependencies) for (offset in offsets) {
            val x = fixture()
            x.f.half(entry + offset, if (offset == 26) 1 else 0x46C0)
            assertFalse("dependency ${entry.toString(16)}+$offset", authority(x) is GbaItemNameAuthority.Available)
        }
    }

    @Test(timeout = 60_000)
    fun matchingHintStrideAloneCannotDischargeDifferentRootsCountsOrLoadRoles() {
        val mutations = listOf<(Fixture) -> Unit>(
            { x -> x.f.pointer(x.getter + 28, x.names.offset + 4) },
            { x -> x.f.pointer(x.leaf + 24, x.stats.offset + 4) },
            { x -> x.f.half(x.predicate + 28, x.names.count + 1) },
            { x -> x.f.half(x.predicate + 30, 1) },
            { x -> x.f.half(x.leaf + 14, 0x7849) },
            { x -> x.f.half(x.leaf + 6, 0x0081) },
            { x -> x.f.literalLoad(x.getter + 18, 1, x.getter + 36, x.names.offset) },
            { x -> x.f.literalLoad(x.leaf + 4, 2, x.leaf + 32, x.stats.offset) })
        for (mutate in mutations) {
            val x = fixture(); mutate(x)
            assertFalse(authority(x) is GbaItemNameAuthority.Available)
        }
    }

    @Test(timeout = 60_000)
    fun allThreeOwnedCoreLiteralWordsRejectSupportedDirectIncomingEdgesAndPadding() {
        for (pool in listOf(0x201C, 0x221C, 0x2258)) for (offset in listOf(0, 2)) {
            for (kind in listOf("BL", "B", "BEQ")) {
                val x = fixture()
                val source = pool - 32
                // Leave consumer bodies intact by emitting backward edges from after each function.
                val at = pool + 64
                val delta = (pool + offset - at - 4) / 2
                when (kind) {
                    "BL" -> x.f.bl(at, pool + offset)
                    "B" -> x.f.half(at, 0xE000 or (delta and 0x7FF))
                    else -> x.f.half(at, 0xD000 or (delta and 255))
                }
                assertTrue("$kind enters core pool ${pool.toString(16)}+$offset from $source",
                    authority(x) is GbaItemNameAuthority.Unavailable)
            }
        }
        for (pad in listOf(0x201A, 0x221A)) {
            val x = fixture(); x.f.bl(0x3500, pad)
            assertTrue("owned zero alignment padding", authority(x) is GbaItemNameAuthority.Unavailable)
        }
    }

    @Test(timeout = 60_000)
    fun unknownExtraHintsReferencesAndMissingItemFieldsDoNotDisappear() {
        val mutations = listOf<(Fixture) -> Unit>(
            { x -> x.f.bytes.fill(0, x.f.scalarEntries.first(), x.f.scalarEntries.first() + 0x40) },
            { x -> x.f.literalLoad(0x3600, 4, 0x3604, x.f.root) },
            { x -> x.f.literalLoad(0x3600, 4, 0x3604, x.f.root + 1) },
            { x -> x.f.emit(0x3400, 0xB500, 0x0400, 0x0C00)
                x.f.bl(0x3406, 0x3800)
                x.f.emit(0x340A, 0x0400, 0x0C00, 0x210B, 0x4348)
                x.f.literalLoad(0x3412, 1, 0x341C, 0x4200)
                x.f.emit(0x3414, 0x1840, 0xBC02, 0x4708) })
        for (mutate in mutations) {
            val x = fixture(); mutate(x)
            assertTrue("positive core cannot filter an unsupported item witness",
                authority(x) is GbaItemNameAuthority.Unavailable)
        }
    }

    @Test(timeout = 60_000)
    fun coreFactoryRequiresCurrentCoherentPositiveFixedNonPointerValidation() {
        val x = fixture(); val session = x.f.session()
        val names = TableValidators.names(session.rom, x.names, x.names.count, WesternPokemonTextCodecs.gen3English)
        val stats = TableValidators.baseStats(session.rom, x.stats.offset, x.stats.count, x.stats.recordSize, 3)
        assertNotNull(Gen3SelectedCoreItemRoute.fromValidated(session, x.names, x.stats, names, stats))
        for (bad in listOf(names.copy(compatible = false), names.copy(ambiguous = true),
            names.copy(offset = names.offset!! + 4), names.copy(recordSize = names.recordSize!! + 1),
            names.copy(totalRecords = names.totalRecords + 1), names.copy(validRecords = 0),
            names.copy(format = TableRecordFormat.WIDE_STATS_64))) {
            assertNull(Gen3SelectedCoreItemRoute.fromValidated(session, x.names, x.stats, bad, stats))
        }
        for (bad in listOf(stats.copy(compatible = false), stats.copy(ambiguous = true),
            stats.copy(offset = stats.offset!! + 4), stats.copy(recordSize = stats.recordSize!! + 4),
            stats.copy(totalRecords = stats.totalRecords - 1), stats.copy(validRecords = 0))) {
            assertNull(Gen3SelectedCoreItemRoute.fromValidated(session, x.names, x.stats, names, bad))
        }
        for (bad in listOf(x.names.copy(variableLength = true), x.names.copy(valuesArePointers = true),
            x.names.copy(stride = 20), x.names.copy(pointerOffsets = listOf(0x100)), x.names.copy(bank = 1),
            x.names.copy(count = 17), x.names.copy(format = TableRecordFormat.WIDE_STATS_64))) {
            assertNull(Gen3SelectedCoreItemRoute.fromValidated(session, bad, x.stats, names, stats))
        }
        val beyond = x.stats.copy(offset = session.rom.size - 4)
        assertNull(Gen3SelectedCoreItemRoute.fromValidated(session, x.names, beyond, names,
            stats.copy(offset = beyond.offset)))
        assertNull(Gen3SelectedCoreItemRoute.fromValidated(session, null, x.stats, names, stats))
    }

    private fun aggregate(x: Fixture, session: RomAnalysisSession, published: GbaItemPublishedRoute,
                          validNames: Boolean = true): ResolvedRomLayout {
        val missing = ValidationEvidence(false, 0, 0, 0.0, listOf("synthetic missing optional dataset"))
        val original = session.itemNameResolver.original(published)
        val identity = IdentityRootsPhaseResult.Resolved(exactProfile = null, baseProfile = null,
            identityMatched = true, scoreEvidence = emptyList(), expansion = null, compiledGbaReferences = null,
            tableResolution = ProfileTableResolution(ProfileTables(), itemPublishedRoute = published,
                itemNameAuthority = original), probeCodec = WesternPokemonTextCodecs.gen3English)
        val names = TableValidators.names(session.rom, x.names, x.names.count, WesternPokemonTextCodecs.gen3English)
        val stats = TableValidators.baseStats(session.rom, x.stats.offset, x.stats.count, x.stats.recordSize, 3)
        val core = CoreDatasetsPhaseResult.Resolved(
            // Deliberately different candidate/profile layouts cannot become the handoff's selected roots.
            candidateTables = ProfileTables(speciesNames = x.names.copy(offset = x.names.offset + 4),
                baseStats = x.stats.copy(offset = x.stats.offset + 4)),
            speciesCount = x.names.count, inferredMoveCount = null, moveCount = null,
            speciesNames = names.copy(compatible = validNames), baseStats = stats,
            moveNames = missing, moveData = missing, speciesNamesLayout = x.names, baseStatsLayout = x.stats,
            moveNamesLayout = null, moveDataLayout = null)
        val semantic = SemanticDomainPhaseResult.Resolved(core, descriptions = missing, descriptionsLayout = null,
            typeChart = missing, typeChartLayout = null, abilities = missing, abilitiesLayout = null)
        val dependent = DependentDatasetsPhaseResult.Resolved(semantic, sprites = missing, evolutions = missing,
            learnsets = missing, learnsetTables = emptyList(), learnsetSelector = null)
        val state = FamilyProbeState.empty().withIdentityRoots(identity).withCoreDatasets(core)
            .withSemanticDomain(semantic).withDependentDatasets(dependent)
        return requireNotNull(CapabilityAggregationStrategy().execute(session,
            EngineFamilyDefinitions.byFamily.getValue(EngineFamily.EMERALD), state).probe?.resolvedLayout)
    }

    @Test(timeout = 60_000)
    fun productionAggregationUsesCurrentValidatedCoreRatherThanIdentityOrProfileDefaults() {
        val x = fixture(); val session = x.f.session()
        val published = GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Absent)
        val result = aggregate(x, session, published)
        assertTrue("post-core production phase must compose independent authority: ${result.itemNameAuthority}",
            result.itemNameAuthority is GbaItemNameAuthority.Available)
        assertEquals(x.names, result.tables.speciesNames)
        assertEquals(x.stats, result.tables.baseStats)
        assertEquals(x.names.count, result.tables.speciesNames!!.count)
        assertTrue(session.itemNameResolver.original(published) is GbaItemNameAuthority.Unavailable)
    }

    @Test(timeout = 60_000)
    fun aggregationCannotRetryAbsentValidationOrReplaceRejectedPositiveConsumerProof() {
        val published = GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Absent)
        val invalid = fixture(); val session = invalid.f.session()
        val original = session.itemNameResolver.original(published)
        assertSame(original, aggregate(invalid, session, published, validNames = false).itemNameAuthority)
        val damaged = fixture()
        damaged.f.bytes.fill(0, damaged.f.scalarEntries.first(), damaged.f.scalarEntries.first() + 0x40)
        val result = aggregate(damaged, damaged.f.session(), published)
        assertTrue(result.itemNameAuthority is GbaItemNameAuthority.Unavailable)
        assertTrue("keep the positive route's rejection, not the earlier published absence",
            (result.itemNameAuthority as GbaItemNameAuthority.Unavailable).reason.contains("scalar"))
    }

    @Test(timeout = 60_000)
    fun sharedNominationRemainsChargedOnceAcrossOriginalAndPositiveCoreWithoutRaisingBudgets() {
        val x = fixture()
        val session = x.f.session(ResolutionLimits(maxDatasetExtentBytes = x.f.bytes.size.toLong() * 2))
        assertTrue(session.itemNameResolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Unavailable)
        assertTrue(authority(x, session) is GbaItemNameAuthority.Available)
    }

    @Test(timeout = 60_000)
    fun foreignSessionAndExhaustedWorkStayUnavailableWhileMemoizationStillCancels() {
        val x = fixture(); val owner = x.f.session(); val other = x.f.session()
        assertTrue((other.itemNameResolver.selectedCore(x.route(owner), GbaItemPublishedRoute.NotInvoked)
            as? Gen3SelectedCoreItemOutcome.Evaluated)?.authority is GbaItemNameAuthority.Unavailable)
        for (passes in listOf(1, 2)) {
            val session = x.f.session(ResolutionLimits(maxDatasetExtentBytes = x.f.bytes.size.toLong() * passes))
            val result = authority(x, session)
            assertEquals("shared nomination/caller budget=$passes", passes == 2, result is GbaItemNameAuthority.Available)
        }
        assertFalse(authority(x, x.f.session(ResolutionLimits(maxProbeWorkPerDataset = 8)))
            is GbaItemNameAuthority.Available)
        var stopped = false
        val session = x.f.session(cancellation = ParserCancellationToken {
            if (stopped) throw ParserCancellationException()
        })
        val core = x.route(session)
        val published = GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Absent)
        val result = session.itemNameResolver.selectedCore(core, published)
        assertSame(result, session.itemNameResolver.selectedCore(core, published))
        stopped = true
        assertThrows(ParserCancellationException::class.java) { session.itemNameResolver.selectedCore(core, published) }
    }
}
