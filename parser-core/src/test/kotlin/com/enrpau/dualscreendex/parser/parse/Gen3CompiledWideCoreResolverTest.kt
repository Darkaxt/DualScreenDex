package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.dataset.core.basestats.BaseStatsAbi
import com.enrpau.dualscreendex.parser.dataset.core.basestats.BaseStatsRowOutcome
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3CompiledWideCoreResolverTest {
    @Test
    fun resolvesRelocatedConsumersWithoutIdentityNumberingOrAliasLiveness() {
        for (relocation in listOf(0, 0x800)) {
            val fixture = Gen3CompiledWideCoreFixture(relocation)
            val result = resolve(fixture)
            assertTrue("complete coupled consumers must resolve: $result", result is Gen3CompiledWideCoreOutcome.Resolved)
            result as Gen3CompiledWideCoreOutcome.Resolved
            assertEquals(mapOf(1 to 44, 2 to 7, 4 to 9), result.nativeToDex)
            assertEquals(6, result.speciesCount)
            assertEquals(fixture.namesRoot, result.speciesNames.offset)
            assertEquals(13, result.speciesNames.recordSize)
            assertEquals(7, result.speciesNames.count)
            assertEquals(fixture.defaultStatsRoot.toLong(), result.baseStats.table.offset)
            assertEquals(BaseStatsAbi.WIDE_STATS_64, result.baseStats.table.abi)
            val first = result.baseStats.rows[1] as BaseStatsRowOutcome.Decoded
            assertEquals(345, first.record.stats.hp)
            assertEquals(listOf(260, 65, 34), first.record.abilityIds)
        }
    }

    @Test
    fun ignoresNamesAndStatsAtAnInverseAliasOrHoleWhenChoosingCanonicalRows() {
        val fixture = Gen3CompiledWideCoreFixture()
        fixture.bytes.fill(0, fixture.namesRoot + 3 * 13, fixture.namesRoot + 4 * 13)
        fixture.bytes.fill(0, fixture.defaultStatsRoot + 3 * 64, fixture.defaultStatsRoot + 4 * 64)
        val result = resolve(fixture)
        assertTrue(result is Gen3CompiledWideCoreOutcome.Resolved)
        assertEquals(setOf(1, 2, 4), (result as Gen3CompiledWideCoreOutcome.Resolved).nativeToDex.keys)
    }

    @Test
    fun rejectsANameBoundThatDisagreesWithTheInverseReservedSlotContract() {
        val fixture = Gen3CompiledWideCoreFixture()
        fixture.putU32(fixture.nameConsumer + 28, fixture.mapCount + 2)
        assertRejected(fixture)
    }

    @Test
    fun rejectsAnInverseSentinelThatDisagreesWithItsScanBound() {
        val fixture = Gen3CompiledWideCoreFixture()
        fixture.putU32(fixture.inverseConsumer + 64, fixture.mapCount + 1)
        assertRejected(fixture)
    }

    @Test
    fun rejectsADifferentForwardMapInsteadOfSilentlyUsingInverseIds() {
        val fixture = Gen3CompiledWideCoreFixture()
        fixture.putU32(fixture.forwardConsumer + 44, 0x08002700)
        assertRejected(fixture)
    }

    @Test
    fun unrelatedCompleteForwardConversionDoesNotCompeteWithThePairedInverseRoot() {
        val fixture = Gen3CompiledWideCoreFixture()
        fixture.bytes.copyInto(fixture.bytes, 0x700, fixture.forwardConsumer, fixture.forwardConsumer + 54)
        fixture.putU32(0x700 + 40, 0x08002a00)
        fixture.putU32(0x700 + 44, 0x08002700)
        val result = resolve(fixture)
        assertTrue(result is Gen3CompiledWideCoreOutcome.Resolved)
        assertEquals(mapOf(1 to 44, 2 to 7, 4 to 9), (result as Gen3CompiledWideCoreOutcome.Resolved).nativeToDex)
    }

    @Test
    fun competingFormRootsOnTheSamePairedConversionRemainAmbiguous() {
        val fixture = Gen3CompiledWideCoreFixture()
        fixture.bytes.copyInto(fixture.bytes, 0x700, fixture.forwardConsumer, fixture.forwardConsumer + 54)
        fixture.putU32(0x700 + 40, 0x08002a00)
        assertRejected(fixture)
    }

    @Test
    fun rejectsAFormOverrideThatConflictsWithTheCanonicalInverseJoin() {
        val fixture = Gen3CompiledWideCoreFixture()
        fixture.putU16(0x2900, 7)
        assertRejected(fixture)
    }

    @Test
    fun rejectsAnOutOfBoundsCanonicalFormPointer() {
        val fixture = Gen3CompiledWideCoreFixture()
        fixture.putU32(fixture.formRoot + 4, 0x08000000 + fixture.bytes.size - 1)
        assertRejected(fixture)
    }

    @Test
    fun rejectsAnInvalidCanonicalNameAndAnInvalidCanonicalWideRow() {
        val badName = Gen3CompiledWideCoreFixture()
        badName.bytes.fill(0, badName.namesRoot + 2 * 13, badName.namesRoot + 3 * 13)
        assertRejected(badName)
        val badStats = Gen3CompiledWideCoreFixture()
        badStats.putU16(badStats.defaultStatsRoot + 2 * 64 + 4, 0)
        assertRejected(badStats)
    }

    @Test
    fun requiresTheSixActualU16LoadsOnTheDefaultRoot() {
        val fixture = Gen3CompiledWideCoreFixture()
        fixture.putU16(fixture.statConsumer + 14, 0x7844)
        assertRejected(fixture)
    }

    @Test
    fun rejectsConflictingDefaultRootsRatherThanChoosingTheFirstCandidate() {
        val fixture = Gen3CompiledWideCoreFixture()
        fixture.putTypeConsumer(0x800, 0x4000, fixture.variantStatsRoot)
        fixture.putStatConsumer(0x1000, 0x4000)
        for (index in 1..4) fixture.putWideStats(0x4000 + index * 64, 90)
        assertRejected(fixture)
    }

    @Test
    fun rejectsAnInvalidConditionalBranchInARecognizedWideConsumer() {
        val fixture = Gen3CompiledWideCoreFixture()
        fixture.putU16(fixture.typeConsumer + 28, 0xd17f)
        assertRejected(fixture)
    }

    @Test
    fun rejectsTableExtentsAboveTheSharedDeterministicBudget() {
        val fixture = Gen3CompiledWideCoreFixture()
        val result = Gen3CompiledWideCoreResolver.resolve(
            fixture.session(limits = ResolutionLimits(maxDatasetExtentBytes = 128)), PokemonTextCodec.gbaEnglish,
        )
        assertTrue(result is Gen3CompiledWideCoreOutcome.Rejected)
    }

    @Test
    fun rejectsTheCoupledWideWitnessWhenItsModeSelectorIsMissing() {
        val fixture = Gen3CompiledWideCoreFixture()
        fixture.bytes.fill(0, fixture.typeConsumer, fixture.typeConsumer + 92)
        assertRejected(fixture)
    }

    @Test
    fun preservesUnsignedDexNumbersWithoutConfusingThemWithNativeRowIds() {
        val fixture = Gen3CompiledWideCoreFixture()
        fixture.putU16(fixture.mapRoot + 2, 50000)
        val result = resolve(fixture)
        assertTrue(result is Gen3CompiledWideCoreOutcome.Resolved)
        assertEquals(mapOf(1 to 44, 2 to 50000, 4 to 9), (result as Gen3CompiledWideCoreOutcome.Resolved).nativeToDex)
    }

    @Test
    fun doesNotPromoteTheVariantRootWhenOnlyItsSixStatReadsArePresent() {
        val fixture = Gen3CompiledWideCoreFixture()
        fixture.putStatConsumer(fixture.statConsumer, fixture.variantStatsRoot)
        assertRejected(fixture)
    }

    @Test
    fun enforcesTheSharedRootAndConsumerWorkBudgets() {
        val fixture = Gen3CompiledWideCoreFixture()
        for (limits in listOf(ResolutionLimits(maxProbeRootsPerDataset = 1), ResolutionLimits(maxProbeWorkPerDataset = 1))) {
            val result = Gen3CompiledWideCoreResolver.resolve(fixture.session(limits), PokemonTextCodec.gbaEnglish)
            assertTrue(result is Gen3CompiledWideCoreOutcome.Rejected)
        }
    }

    @Test
    fun ordinaryUnrecognizedInputIsAbsent() {
        val fixture = Gen3CompiledWideCoreFixture()
        fixture.bytes.fill(0)
        assertEquals(Gen3CompiledWideCoreOutcome.Absent, resolve(fixture))
    }

    @Test(expected = UnsupportedOperationException::class)
    fun publishedCanonicalIndexIsImmutable() {
        val result = resolve(Gen3CompiledWideCoreFixture())
        assertTrue(result is Gen3CompiledWideCoreOutcome.Resolved)
        (result as Gen3CompiledWideCoreOutcome.Resolved).nativeToDex.let { (it as MutableMap)[8] = 99 }
    }

    @Test(expected = ParserCancellationException::class)
    fun cancellationIsNotConvertedToAbsentOrRejected() {
        val fixture = Gen3CompiledWideCoreFixture()
        Gen3CompiledWideCoreResolver.resolve(
            fixture.session(cancellation = ParserCancellationToken { throw ParserCancellationException() }),
            PokemonTextCodec.gbaEnglish,
        )
    }

    private fun resolve(fixture: Gen3CompiledWideCoreFixture) =
        Gen3CompiledWideCoreResolver.resolve(fixture.session(), PokemonTextCodec.gbaEnglish)

    private fun assertRejected(fixture: Gen3CompiledWideCoreFixture) {
        val result = resolve(fixture)
        assertTrue("recognized malformed wide authority must fail closed: $result", result is Gen3CompiledWideCoreOutcome.Rejected)
    }
}
