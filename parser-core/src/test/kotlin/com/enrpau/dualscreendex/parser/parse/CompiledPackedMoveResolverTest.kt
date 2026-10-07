package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsAbi
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsSemanticDomain
import com.enrpau.dualscreendex.parser.dataset.moves.putPackedMove
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CompiledPackedMoveResolverTest {
    @Test
    fun completeRelocatedContractsResolveWithoutReferenceIndexNominees() {
        listOf(0x3000, 0x4800).forEach { root ->
            val bytes = fixture(root)
            val result = Gen3CompiledPackedMoveResolver.resolve(session(bytes), domain())!!
            assertEquals(root.toLong(), result.table.offset)
            assertEquals(MoveDetailsAbi.PACKED_FLAGS_MOVE_20, result.table.abi)
            assertEquals(-7, result.materializedRecords.getValue(1).priority)
            assertEquals(0x89123456ABL, result.materializedRecords.getValue(2).flags)
        }
    }

    @Test
    fun highRegisterAddressFormationAndForwardSignedReturnAreProven() {
        val bytes = fixture()
        consumer(bytes, prioritySite, 0x3000, 10, 1, signedPath = true, highIndex = true)
        assertNotNull(Gen3CompiledPackedMoveResolver.resolve(session(bytes), domain()))
    }

    @Test
    fun unsignedPriorityAloneDoesNotAuthorizeSignedMaterialization() {
        val bytes = fixture()
        consumer(bytes, prioritySite, 0x3000, 10, 1)
        assertNull(Gen3CompiledPackedMoveResolver.resolve(session(bytes), domain()))
    }

    @Test
    fun clobberedSignedReturnAndUnknownCallsFailClosed() {
        listOf(0x2500, 0xf000, 0x4700).forEach { clobber ->
            val bytes = fixture()
            consumer(bytes, prioritySite, 0x3000, 10, 1, signedPath = true)
            put16(bytes, prioritySite + 12, clobber)
            assertNull(Gen3CompiledPackedMoveResolver.resolve(session(bytes), domain()))
        }
    }

    @Test
    fun missingTailSplitAndArgumentWidthCannotBeRepairedByScalarShape() {
        listOf(11 to 1, 12 to 2, 19 to 1).forEach { removed ->
            val bytes = fixture()
            val index = fields.indexOf(removed)
            repeat(0x60) { bytes[0x100 + index * 0x60 + it] = 0 }
            assertNull(Gen3CompiledPackedMoveResolver.resolve(session(bytes), domain()))
        }
        val bytes = fixture()
        consumer(bytes, 0x100 + fields.indexOf(12 to 2) * 0x60, 0x3000, 12, 1)
        assertNull(Gen3CompiledPackedMoveResolver.resolve(session(bytes), domain()))
    }

    @Test
    fun branchingBeforeAddressReadOrClobberedIndexDoesNotYieldAFieldWitness() {
        listOf(0xe000, 0x2100).forEach { opcode ->
            val bytes = fixture()
            put16(bytes, 0x100 + 2, opcode)
            assertNull(Gen3CompiledPackedMoveResolver.resolve(session(bytes), domain()))
        }
    }

    @Test
    fun twoCompleteRootsRejectRatherThanRankByOffsetOrDiscoveryOrder() {
        val bytes = fixture()
        table(bytes, 0x4800)
        fields.forEachIndexed { index, (field, width) ->
            consumer(bytes, 0x1000 + index * 0x60, 0x4800, field, width,
                signedPath = field == 10)
        }
        assertNull(Gen3CompiledPackedMoveResolver.resolve(session(bytes), domain()))
    }

    @Test
    fun malformedActiveTailRowAndTruncatedExtentRejectCompleteConsumers() {
        val bytes = fixture()
        bytes[0x3000 + 40 + 11] = 3
        assertNull(Gen3CompiledPackedMoveResolver.resolve(session(bytes), domain()))
        assertNull(Gen3CompiledPackedMoveResolver.resolve(session(fixture().copyOf(0x3000 + 59)), domain()))
    }

    @Test
    fun deterministicRootCandidateWorkAndExtentBudgetsFailClosed() {
        val twoRoots = fixture()
        table(twoRoots, 0x4800)
        consumer(twoRoots, 0x1000, 0x4800, 0, 2)
        assertNull(Gen3CompiledPackedMoveResolver.resolve(
            session(twoRoots, ResolutionLimits(maxProbeRootsPerDataset = 1)), domain(),
        ))
        listOf(
            ResolutionLimits(maxCandidatesPerDataset = 1),
            ResolutionLimits(maxProbeWorkPerDataset = 1),
            ResolutionLimits(maxDatasetExtentBytes = 59),
        ).forEach { limits ->
            assertNull(Gen3CompiledPackedMoveResolver.resolve(session(fixture(), limits), domain()))
        }
    }

    @Test
    fun cancellationPropagatesInsteadOfBecomingAnAbsentOptionalContract() {
        val session = RomAnalysisSession(
            RomImage(fixture()), RomHeader(Platform.GBA, "PACKED CONSUMER TEST"),
            cancellation = ParserCancellationToken { throw ParserCancellationException() },
        )
        assertThrows(ParserCancellationException::class.java) {
            Gen3CompiledPackedMoveResolver.resolve(session, domain())
        }
    }

    private fun fixture(root: Int = 0x3000): ByteArray = ByteArray(0x6000).also { bytes ->
        table(bytes, root)
        fields.forEachIndexed { index, (field, width) ->
            consumer(bytes, 0x100 + index * 0x60, root, field, width, signedPath = field == 10)
        }
    }

    private fun table(bytes: ByteArray, root: Int) {
        putPackedMove(bytes, root + 20)
        putPackedMove(bytes, root + 40)
    }

    private fun consumer(
        bytes: ByteArray,
        site: Int,
        root: Int,
        field: Int,
        width: Int,
        signedPath: Boolean = false,
        highIndex: Boolean = false,
    ) {
        repeat(0x40) { bytes[site + it] = 0 }
        val instructions = mutableListOf(0x4b0f)
        if (highIndex) instructions += 0x4641
        instructions += listOf(0x0088, if (highIndex) 0x4440 else 0x1840, 0x0080, 0x18c2)
        instructions += (if (width == 1) 0x7800 else 0x8800) or ((field / width) shl 6) or 0x15
        if (signedPath) {
            // One forward arm preserves the byte; the other clobbers it and cannot prove signedness.
            instructions += listOf(0x2c00, 0xd000, 0x2500, 0x0628, 0x1600)
        }
        instructions += 0x4770
        instructions.forEachIndexed { index, word -> put16(bytes, site + index * 2, word) }
        val pointer = root + 0x08000000
        repeat(4) { bytes[site + 0x40 + it] = (pointer ushr (it * 8)).toByte() }
    }

    private fun put16(bytes: ByteArray, at: Int, value: Int) {
        bytes[at] = value.toByte()
        bytes[at + 1] = (value ushr 8).toByte()
    }

    private fun domain() = MoveDetailsSemanticDomain(3, setOf(1, 2))
    private fun session(bytes: ByteArray, limits: ResolutionLimits = ResolutionLimits()) =
        RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBA, "PACKED CONSUMER TEST"), limits = limits)

    private companion object {
        val fields = listOf(0 to 2, 2 to 1, 3 to 1, 4 to 1, 5 to 1, 6 to 1, 8 to 2,
            10 to 1, 11 to 1, 12 to 2, 14 to 1, 15 to 1, 16 to 1, 17 to 1, 18 to 1, 19 to 1)
        val prioritySite = 0x100 + fields.indexOf(10 to 1) * 0x60
    }
}
