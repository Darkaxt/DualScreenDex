package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.catalog.MoveCategory
import com.enrpau.dualscreendex.parser.dataset.learnsets.LearnsetFormat
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsAbi
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3CompiledWideMoveResolverTest {
    @Test
    fun couplesRelocatableNamesFieldsAndOrdinaryListsForBothPhysicalStrides() {
        for (width in listOf(20, 56)) for (relocation in listOf(0, 0x800)) {
            val f = Gen3CompiledWideMoveFixture(width, relocation)
            val r = resolved(f)
            assertEquals(f.nameRoot, r.names.offset)
            assertEquals(17, r.names.recordSize)
            assertEquals(8, r.acquisitionMinimumMoveCount)
            assertEquals(if (width == 20) MoveDetailsAbi.ALIGNED_BYTE_TARGET_MOVE_20
                else MoveDetailsAbi.ALIGNED_BYTE_TARGET_MOVE_56, r.details.table.abi)
            assertEquals(f.detailRoot.toLong(), r.details.table.offset)
            assertEquals(f.pointerRoot, r.learnsetTable.offset)
            assertEquals(LearnsetFormat.MoveU16LevelU16, r.learnsets.primary!!.layout.table.format)
            val entries = r.learnsets.catalogPrimaryEntries()
            assertEquals(listOf(0, 100), entries.getValue(1).map { it.level })
            assertEquals(listOf(2, 7), entries.getValue(1).map { it.moveId })
            assertEquals(17, entries.getValue(2).single().level)
            assertEquals(MoveCategory.UNKNOWN, r.details.catalogDetails().getValue(7).category)
        }
    }

    @Test
    fun ignoresNoncanonicalPointersAndDoesNotInferExtentFromATrailingNamePrefix() {
        val f = Gen3CompiledWideMoveFixture()
        f.putName(8, "EXTRA")
        f.core.putU32(f.pointerRoot + 3 * 4, 0x02000000)
        assertEquals(8, resolved(f).names.count)
    }

    @Test
    fun requiresSignedPriorityAndEveryIndependentPrefixFieldRead() {
        val unsigned = Gen3CompiledWideMoveFixture()
        unsigned.core.putU16(unsigned.fieldConsumers[7] + 12, 0x5cc3)
        rejected(unsigned)
        val missing = Gen3CompiledWideMoveFixture()
        missing.bytes.fill(0, missing.fieldConsumers[10], missing.fieldConsumers[10] + 40)
        rejected(missing)
    }

    @Test
    fun rejectsConflictingAcquisitionRootsAndBrokenLoopBranches() {
        val conflict = Gen3CompiledWideMoveFixture()
        conflict.putAcquisition(0x1500, conflict.pointerRoot + 32, conflict.detailRoot)
        rejected(conflict)
        val broken = Gen3CompiledWideMoveFixture()
        broken.core.putU16(broken.acquisition + 0x78, 0xddd3)
        rejected(broken)
    }

    @Test
    fun rejectsNameDataRootDisagreementOrAnIncompleteEosCopier() {
        val conflict = Gen3CompiledWideMoveFixture()
        conflict.core.putU32(conflict.nameConsumer + 72, 0x08000000 + conflict.detailRoot + 56)
        rejected(conflict)
        val copier = Gen3CompiledWideMoveFixture()
        copier.core.putU16(copier.copier + 18, 0xd1f7)
        rejected(copier)
    }

    @Test
    fun requiresEveryMinimumNameAndEveryReferencedScalarRow() {
        val badName = Gen3CompiledWideMoveFixture()
        badName.bytes.fill(1, badName.nameRoot + 6 * 17, badName.nameRoot + 7 * 17)
        rejected(badName)
        val badType = Gen3CompiledWideMoveFixture()
        badType.bytes[badType.detailRoot + 7 * 56 + 3] = 32
        rejected(badType)
    }

    @Test
    fun rejectsBadCanonicalPointerAndUnterminatedOrOutOfRangeLevels() {
        val pointer = Gen3CompiledWideMoveFixture()
        pointer.core.putU32(pointer.pointerRoot + 4, 0x02000000)
        rejected(pointer)
        val level = Gen3CompiledWideMoveFixture()
        level.core.putU16(level.listRoot + 18, 101)
        rejected(level)
        val noEnd = Gen3CompiledWideMoveFixture()
        for (at in noEnd.listRoot + 16 until noEnd.listRoot + 32 step 4) {
            noEnd.core.putU16(at, 4)
            noEnd.core.putU16(at + 2, 17)
        }
        rejected(noEnd)
    }

    @Test
    fun observesSharedRootWorkCandidateAndExtentBudgets() {
        val f = Gen3CompiledWideMoveFixture()
        for (limits in listOf(ResolutionLimits(maxProbeRootsPerDataset = 1),
            ResolutionLimits(maxProbeWorkPerDataset = 1), ResolutionLimits(maxCandidatesPerDataset = 1),
            ResolutionLimits(maxDatasetExtentBytes = 16))) {
            val core = core(f)
            assertTrue(Gen3CompiledWideMoveResolver.resolve(f.core.session(limits), core, PokemonTextCodec.gbaEnglish)
                is Gen3CompiledWideMoveOutcome.Rejected)
        }
    }

    @Test(expected = ParserCancellationException::class)
    fun preservesCancellation() {
        val f = Gen3CompiledWideMoveFixture()
        Gen3CompiledWideMoveResolver.resolve(f.core.session(cancellation =
            ParserCancellationToken { throw ParserCancellationException() }), core(f), PokemonTextCodec.gbaEnglish)
    }

    private fun core(f: Gen3CompiledWideMoveFixture) =
        Gen3CompiledWideCoreResolver.resolve(f.core.session(), PokemonTextCodec.gbaEnglish)
            as Gen3CompiledWideCoreOutcome.Resolved

    private fun resolved(f: Gen3CompiledWideMoveFixture): Gen3CompiledWideMoveOutcome.Resolved {
        val r = Gen3CompiledWideMoveResolver.resolve(f.core.session(), core(f), PokemonTextCodec.gbaEnglish)
        assertTrue("expected coupled move authority, got $r", r is Gen3CompiledWideMoveOutcome.Resolved)
        return r as Gen3CompiledWideMoveOutcome.Resolved
    }

    private fun rejected(f: Gen3CompiledWideMoveFixture) {
        val r = Gen3CompiledWideMoveResolver.resolve(f.core.session(), core(f), PokemonTextCodec.gbaEnglish)
        assertTrue("recognized incomplete move authority must fail closed: $r", r is Gen3CompiledWideMoveOutcome.Rejected)
    }
}
