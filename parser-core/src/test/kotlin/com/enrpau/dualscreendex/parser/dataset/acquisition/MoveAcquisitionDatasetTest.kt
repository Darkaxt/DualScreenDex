package com.enrpau.dualscreendex.parser.dataset.acquisition

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.GbaTargetReferenceEvidence
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.resolution.BudgetKind
import com.enrpau.dualscreendex.parser.resolution.CandidateSource
import com.enrpau.dualscreendex.parser.resolution.DatasetResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveAcquisitionDatasetTest {
    private val codec = MoveAcquisitionCodec()

    @Test
    fun decodesGenThreeEggGroupsAndKeepsTheFirstRepeatedSpeciesGroup() {
        val bytes = ByteArray(0x200)
        listOf(20_001, 3, 4, 20_002, 5, 20_001, 6, 0xFFFF)
            .forEachIndexed { index, value -> putU16(bytes, 0x40 + index * 2, value) }
        val layout = AcquisitionTableLayout(
            AcquisitionMethod.EGG,
            speciesCount = 3,
            abi = AcquisitionAbi.Gen3EggSentinelU16(0x40, maxRecords = 16),
        )

        val decoded = codec.decode(session(bytes), layout, domain(1..3, 1..10))
            as AcquisitionTableOutcome.Decoded

        assertEquals(listOf(3, 4), decoded.resolved.acquisitionsBySpecies.getValue(1).map { it.moveId })
        assertEquals(listOf(5), decoded.resolved.acquisitionsBySpecies.getValue(2).map { it.moveId })
        assertTrue(decoded.resolved.rows[2] is AcquisitionRowOutcome.StructuralEmpty)
    }

    @Test
    fun preservesMalformedEggRowsWithoutTurningThemIntoValidLinks() {
        val bytes = ByteArray(0x100)
        listOf(20_001, 3, 99, 20_002, 4, 0xFFFF)
            .forEachIndexed { index, value -> putU16(bytes, 0x20 + index * 2, value) }
        val layout = AcquisitionTableLayout(
            AcquisitionMethod.EGG,
            2,
            AcquisitionAbi.Gen3EggSentinelU16(0x20, 12),
        )

        val decoded = codec.decode(session(bytes), layout, domain(1..2, 1..10))
            as AcquisitionTableOutcome.Decoded

        assertTrue(decoded.resolved.rows[0] is AcquisitionRowOutcome.Malformed)
        assertEquals(listOf(4), decoded.resolved.acquisitionsBySpecies.getValue(2).map { it.moveId })
        assertFalse(decoded.resolved.acquisitionsBySpecies.containsKey(1))
    }

    @Test
    fun decodesBankLocalGenTwoEggPointersWithinTheirDeclaredBank() {
        val bytes = ByteArray(0x10000)
        putU16(bytes, 0x8100, 0x4200)
        putU16(bytes, 0x8102, 0x4203)
        bytes[0x8200] = 3
        bytes[0x8201] = 4
        bytes[0x8202] = 0xFF.toByte()
        bytes[0x8203] = 0xFF.toByte()
        val layout = AcquisitionTableLayout(
            AcquisitionMethod.EGG,
            2,
            AcquisitionAbi.GbBankedEggPointersU8(pointerTableOffset = 0x8100, bank = 2, maxMovesPerSpecies = 8),
        )

        val decoded = codec.decode(session(bytes, Platform.GBC), layout, domain(1..2, 1..10))
            as AcquisitionTableOutcome.Decoded

        assertEquals(listOf(3, 4), decoded.resolved.acquisitionsBySpecies.getValue(1).map { it.moveId })
        assertTrue(decoded.resolved.rows[1] is AcquisitionRowOutcome.StructuralEmpty)
    }

    @Test
    fun decodesEmbeddedGenOneAndTwoCompatibilityWithoutDiscoveringTheMoveList() {
        val bytes = ByteArray(0x400)
        bytes[0x40] = 3
        bytes[0x41] = 4
        bytes[0x42] = 5
        bytes[0x100 + 20] = 0b0000_0101
        bytes[0x120 + 20] = 0
        val layout = AcquisitionTableLayout(
            AcquisitionMethod.MACHINE,
            2,
            AcquisitionAbi.EmbeddedU8MoveListBitfield(
                moveListOffset = 0x40,
                itemCount = 3,
                statsOffset = 0x100,
                statsRecordSize = 0x20,
                flagOffset = 20,
                flagBytes = 1,
            ),
        )

        val decoded = codec.decode(session(bytes, Platform.GB), layout, domain(1..2, 1..10))
            as AcquisitionTableOutcome.Decoded

        assertEquals(listOf(3, 5), decoded.resolved.acquisitionsBySpecies.getValue(1).map { it.moveId })
        assertTrue(decoded.resolved.rows[1] is AcquisitionRowOutcome.StructuralEmpty)
    }

    @Test
    fun decodesGenThreeMoveBitfieldPairAndClassifiesStalePaddingPerRow() {
        val bytes = ByteArray(0x500)
        putU16(bytes, 0x80, 3)
        putU16(bytes, 0x82, 4)
        putU16(bytes, 0x84, 5)
        bytes[0x200] = 0b0000_0101
        bytes[0x201] = 0
        bytes[0x202] = 0b1000_0010.toByte()
        bytes[0x203] = 0
        val layout = AcquisitionTableLayout(
            AcquisitionMethod.TUTOR,
            2,
            AcquisitionAbi.Gen3U16MoveListBitfield(
                moveListOffset = 0x80,
                itemCount = 3,
                compatibilityOffset = 0x200,
                rowBytes = 2,
            ),
        )

        val decoded = codec.decode(session(bytes), layout, domain(0..1, 1..10))
            as AcquisitionTableOutcome.Decoded

        assertEquals(listOf(3, 5), decoded.resolved.acquisitionsBySpecies.getValue(0).map { it.moveId })
        assertTrue(decoded.resolved.rows[1] is AcquisitionRowOutcome.Malformed)
    }

    @Test
    fun joinsPointerIndexedTutorRowsToTheExplicitMoveList() {
        val bytes = ByteArray(0x500)
        putU16(bytes, 0x80, 3)
        putU16(bytes, 0x82, 4)
        putU16(bytes, 0x84, 5)
        putPointer(bytes, 0x100, 0x200)
        putU32(bytes, 0x104, 0)
        bytes[0x200] = 0
        bytes[0x201] = 2
        bytes[0x202] = 0xFF.toByte()
        val layout = AcquisitionTableLayout(
            AcquisitionMethod.TUTOR,
            2,
            AcquisitionAbi.GbaPointerIndexedTutorU8(
                pointerTableOffset = 0x100,
                moveListOffset = 0x80,
                tutorCount = 3,
                maxIndexesPerSpecies = 8,
            ),
        )

        val decoded = codec.decode(session(bytes), layout, domain(0..1, 1..10))
            as AcquisitionTableOutcome.Decoded

        assertEquals(listOf(3, 5), decoded.resolved.acquisitionsBySpecies.getValue(0).map { it.moveId })
        assertTrue(decoded.resolved.rows[1] is AcquisitionRowOutcome.StructuralEmpty)
    }

    @Test
    fun decodesExpansionSpeciesRecordPointerListsWithoutSplittingTheCombinedTeachableAbi() {
        val bytes = ByteArray(0x500)
        putPointer(bytes, 0x108, 0x300)
        putU32(bytes, 0x118, 0)
        putU16(bytes, 0x300, 3)
        putU16(bytes, 0x302, 4)
        putU16(bytes, 0x304, 0xFFFF)
        val layout = AcquisitionTableLayout(
            AcquisitionMethod.MACHINE,
            2,
            AcquisitionAbi.GbaRecordPointerMoveListsU16(
                recordTableOffset = 0x100,
                recordStride = 16,
                pointerFieldOffset = 8,
                maxMovesPerSpecies = 16,
            ),
        )

        val decoded = codec.decode(session(bytes), layout, domain(0..1, 1..10))
            as AcquisitionTableOutcome.Decoded

        assertEquals(listOf(3, 4), decoded.resolved.acquisitionsBySpecies.getValue(0).map { it.moveId })
        assertEquals(
            setOf(AcquisitionProvenance.COMBINED_MACHINE_TUTOR),
            decoded.resolved.acquisitionsBySpecies.getValue(0).map { it.provenance }.toSet(),
        )
        assertEquals(
            setOf(AcquisitionProvenance.COMBINED_MACHINE_TUTOR),
            MoveAcquisitionProjection.project(decoded.resolved).provenanceKinds,
        )
        assertTrue(decoded.resolved.rows[1] is AcquisitionRowOutcome.StructuralEmpty)
    }

    @Test
    fun embeddedCompatibilityFieldMustFitInsideItsDeclaredStatsRecord() {
        assertThrows(IllegalArgumentException::class.java) {
            AcquisitionAbi.EmbeddedU8MoveListBitfield(
                moveListOffset = 0x40,
                itemCount = 3,
                statsOffset = 0x100,
                statsRecordSize = 32,
                flagOffset = 31,
                flagBytes = 2,
            )
        }
    }

    @Test
    fun genTwoEggPointerTableMustBeWhollyInsideItsDeclaredBank() {
        val bytes = ByteArray(0x10000)
        val layout = AcquisitionTableLayout(
            AcquisitionMethod.EGG,
            2,
            AcquisitionAbi.GbBankedEggPointersU8(pointerTableOffset = 0xBFFF, bank = 2),
        )

        val result = codec.decode(session(bytes, Platform.GBC), layout, domain(1..2, 1..10))

        assertTrue(result is AcquisitionTableOutcome.Rejected)
    }

    @Test
    fun rejectsMoveListsOutsideTheIndependentlyEstablishedMoveDomain() {
        val bytes = ByteArray(0x300)
        putU16(bytes, 0x80, 3)
        putU16(bytes, 0x82, 99)
        bytes[0x200] = 3
        val layout = AcquisitionTableLayout(
            AcquisitionMethod.MACHINE,
            1,
            AcquisitionAbi.Gen3U16MoveListBitfield(0x80, 2, 0x200, 1),
        )

        val result = codec.decode(session(bytes), layout, domain(0..0, 1..10))

        assertTrue(result is AcquisitionTableOutcome.Rejected)
    }

    @Test
    fun checkedLongExtentAndAggregateWorkBudgetsFailClosed() {
        val overflow = AcquisitionTableLayout(
            AcquisitionMethod.MACHINE,
            Int.MAX_VALUE.toLong() + 1L,
            AcquisitionAbi.Gen3U16MoveListBitfield(0x80, 2, 0x200, 1),
        )
        assertTrue(
            codec.decode(session(ByteArray(0x300)), overflow, domain(0..0, 1..10))
                is AcquisitionTableOutcome.Rejected,
        )

        val bytes = ByteArray(0x100)
        listOf(20_001, 3, 0xFFFF).forEachIndexed { index, value ->
            putU16(bytes, 0x20 + index * 2, value)
        }
        val layout = AcquisitionTableLayout(
            AcquisitionMethod.EGG,
            1,
            AcquisitionAbi.Gen3EggSentinelU16(0x20, 8),
        )
        val limited = session(bytes, limits = ResolutionLimits(maxProbeWorkPerDataset = 2))

        val work = codec.decode(limited, layout, domain(1..1, 1..10))
            as AcquisitionTableOutcome.WorkBudgetExceeded
        assertTrue(work.reason.contains("probe-work"))
    }

    @Test
    fun chargesDeclaredMoveListsAndBitfieldRowsBeforeAllocationOrScan() {
        val moveHeavy = ByteArray(0x300)
        repeat(5) { index -> putU16(moveHeavy, 0x80 + index * 2, index + 1) }
        val moveHeavyLayout = AcquisitionTableLayout(
            AcquisitionMethod.MACHINE,
            1,
            AcquisitionAbi.Gen3U16MoveListBitfield(0x80, 5, 0x200, 1),
        )
        val moveWork = codec.decode(
            session(moveHeavy, limits = ResolutionLimits(maxProbeWorkPerDataset = 4)),
            moveHeavyLayout,
            domain(0..0, 1..10),
        )
        assertTrue(moveWork is AcquisitionTableOutcome.WorkBudgetExceeded)

        val rowHeavy = ByteArray(0x300)
        putU16(rowHeavy, 0x80, 3)
        val rowHeavyLayout = AcquisitionTableLayout(
            AcquisitionMethod.MACHINE,
            1,
            AcquisitionAbi.Gen3U16MoveListBitfield(0x80, 1, 0x200, 8),
        )
        val rowWork = codec.decode(
            session(rowHeavy, limits = ResolutionLimits(maxProbeWorkPerDataset = 4)),
            rowHeavyLayout,
            domain(0..0, 1..10),
        )
        assertTrue(rowWork is AcquisitionTableOutcome.WorkBudgetExceeded)
    }

    @Test
    fun reservesRetainedLinksBeforeMaterializingAmplifiedBitfieldRows() {
        val bytes = ByteArray(0x300)
        repeat(3) { index -> putU16(bytes, 0x80 + index * 2, index + 1) }
        repeat(3) { row -> bytes[0x200 + row] = 0b0000_0111 }
        val layout = AcquisitionTableLayout(
            AcquisitionMethod.MACHINE,
            3,
            AcquisitionAbi.Gen3U16MoveListBitfield(0x80, 3, 0x200, 1),
        )

        val result = codec.decode(
            session(bytes, limits = ResolutionLimits(maxProbeWorkPerDataset = 8)),
            layout,
            domain(0..2, 1..10),
        ) as AcquisitionTableOutcome.WorkBudgetExceeded

        assertTrue(result.reason.contains("retained"))
        assertEquals(9, result.observedWork)
    }

    @Test
    fun staleBitDiagnosticsRetainOnlyABoundedPreview() {
        val bytes = ByteArray(0x300)
        putU16(bytes, 0x80, 3)
        repeat(32) { bytes[0x200 + it] = 0xFF.toByte() }
        val layout = AcquisitionTableLayout(
            AcquisitionMethod.MACHINE,
            1,
            AcquisitionAbi.Gen3U16MoveListBitfield(0x80, 1, 0x200, 32),
        )

        val decoded = codec.decode(
            session(bytes, limits = ResolutionLimits(maxProbeWorkPerDataset = 512)),
            layout,
            domain(0..0, 1..10),
        ) as AcquisitionTableOutcome.Decoded
        val malformed = decoded.resolved.rows.single() as AcquisitionRowOutcome.Malformed

        assertTrue(malformed.reasons.single().contains("observed 255"))
        assertTrue(malformed.reasons.single().length < 200)
    }

    @Test
    fun directCompiledResolutionDoesNotBuildTheReferenceIndexAndPreservesSites() {
        val bytes = validMachineBytes()
        var builds = 0
        val analysis = session(bytes, onIndexBuild = { builds++ })
        val layout = machineLayout(0x80, 0x200)

        val result = MoveAcquisitionResolver().resolve(
            analysis,
            AcquisitionMethod.MACHINE,
            domain(0..1, 1..10),
            listOf(directProbe(analysis, layout)),
        ) as DatasetResolution.Resolved<ResolvedAcquisitionLayout>

        assertEquals(0, builds)
        assertEquals(listOf(0x20, 0x22), result.candidate.provenance.compiledReferenceSites.offsets)
    }

    @Test
    fun directProofRejectsArbitrarySitesAndCannotCrossRomDigests() {
        val bytes = validMachineBytes()
        val layout = machineLayout(0x80, 0x200)
        val analysis = session(bytes)
        val arbitrary = DirectAcquisitionProof.verify(
            analysis,
            layout,
            mapOf(0x80L to listOf(0x10), 0x200L to listOf(0x12)),
        )
        assertTrue(arbitrary is DirectAcquisitionProofResult.Rejected)

        val proof = (DirectAcquisitionProof.verify(analysis, layout, directSites(layout))
            as DirectAcquisitionProofResult.Verified).proof
        val otherBytes = bytes.copyOf().also { it[0x10] = 1 }
        val otherResult = MoveAcquisitionResolver().resolve(
            session(otherBytes),
            AcquisitionMethod.MACHINE,
            domain(0..1, 1..10),
            listOf(AcquisitionProbe.direct(layout, proof)),
        )
        assertTrue(otherResult is DatasetResolution.Unavailable<*>)
    }

    @Test
    fun exactAuthorityIsUnavailableUntilProfilesPublishAcquisitionMetadata() {
        assertThrows(IllegalArgumentException::class.java) {
            AcquisitionProbe(machineLayout(0x80, 0x200), CandidateSource.EXACT_PROFILE)
        }
    }

    @Test
    fun compiledPairRequiresReferencesToEveryPhysicalRoot() {
        val bytes = validMachineBytes()
        val result = MoveAcquisitionResolver().resolve(
            session(bytes, referenceCounts = mapOf(0x80 to 1)),
            AcquisitionMethod.MACHINE,
            domain(0..1, 1..10),
            listOf(AcquisitionProbe(machineLayout(0x80, 0x200), CandidateSource.COMPILED_REFERENCE)),
        )

        assertTrue(result is DatasetResolution.Unavailable<*>)
    }

    @Test
    fun compiledPairPreservesCountsOnlyReferenceProvenanceAsReviewableEvidence() {
        val bytes = validMachineBytes()
        val result = MoveAcquisitionResolver().resolve(
            session(bytes, referenceCounts = mapOf(0x80 to 2, 0x200 to 3)),
            AcquisitionMethod.MACHINE,
            domain(0..1, 1..10),
            listOf(AcquisitionProbe(machineLayout(0x80, 0x200), CandidateSource.COMPILED_REFERENCE)),
        ) as DatasetResolution.Resolved<ResolvedAcquisitionLayout>

        assertEquals(5, result.candidate.strength.compiledReferenceCount)
        assertTrue(result.candidate.provenance.validatorReviewRecommended)
        assertTrue(result.candidate.provenance.reasons.any { it.message.contains("counts-only") })
    }

    @Test
    fun multiRootAbisRequireDistinctRootsAndDisjointCompiledSites() {
        assertThrows(IllegalArgumentException::class.java) {
            AcquisitionAbi.EmbeddedU8MoveListBitfield(0x80, 2, 0x80, 32, 20, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AcquisitionAbi.Gen3U16MoveListBitfield(0x80, 2, 0x80, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AcquisitionAbi.GbaPointerIndexedTutorU8(0x80, 0x80, 2)
        }

        val evidence = GbaTargetReferenceEvidence(
            count = 1,
            instructionSites = listOf(0x20),
            observedSites = 1,
            limitSites = 16,
            overflowReason = null,
        )
        val index = GbaReferenceIndex.fromTargets(
            mapOf(0x80 to evidence, 0x200 to evidence),
            limitTargets = 32_768,
        )
        val result = MoveAcquisitionResolver().resolve(
            session(validMachineBytes(), referenceIndex = index),
            AcquisitionMethod.MACHINE,
            domain(0..1, 1..10),
            listOf(AcquisitionProbe(machineLayout(0x80, 0x200), CandidateSource.COMPILED_REFERENCE)),
        )
        assertTrue(result is DatasetResolution.Unavailable<*>)
    }

    @Test
    fun equalIndependentCandidatesRemainAmbiguousRegardlessOfProbeOrder() {
        val bytes = ByteArray(0x600)
        writeMachine(bytes, 0x80, 0x200)
        writeMachine(bytes, 0x100, 0x300)
        val first = AcquisitionProbe(machineLayout(0x80, 0x200), CandidateSource.INHERITED_FAMILY_LAYOUT)
        val second = AcquisitionProbe(machineLayout(0x100, 0x300), CandidateSource.INHERITED_FAMILY_LAYOUT)
        val resolver = MoveAcquisitionResolver()
        val forward = resolver.resolve(session(bytes), AcquisitionMethod.MACHINE, domain(0..1, 1..10), listOf(first, second))
        val reverse = resolver.resolve(session(bytes), AcquisitionMethod.MACHINE, domain(0..1, 1..10), listOf(second, first))

        assertTrue(forward is DatasetResolution.Ambiguous<*>)
        assertEquals(
            (forward as DatasetResolution.Ambiguous<ResolvedAcquisitionLayout>).candidates.map { it.layoutIdentity },
            (reverse as DatasetResolution.Ambiguous<ResolvedAcquisitionLayout>).candidates.map { it.layoutIdentity },
        )
    }

    @Test
    fun resolverReportsAggregateRootCandidateReferenceAndRetainedBudgets() {
        val bytes = validMachineBytes()
        val layout = machineLayout(0x80, 0x200)
        val rootLimited = session(bytes, limits = ResolutionLimits(maxProbeRootsPerDataset = 1))
        val root = MoveAcquisitionResolver().resolve(
            rootLimited,
            AcquisitionMethod.MACHINE,
            domain(0..1, 1..10),
            listOf(directProbe(rootLimited, layout)),
        ) as DatasetResolution.BudgetExceeded<ResolvedAcquisitionLayout>
        assertEquals(BudgetKind.PROBE_ROOTS, root.budgetKind)

        val siteLimited = session(bytes, limits = ResolutionLimits(maxCompiledReferenceSitesPerCandidate = 1))
        val sites = DirectAcquisitionProof.verify(
            siteLimited,
            layout,
            directSites(layout),
        ) as DirectAcquisitionProofResult.BudgetExceeded
        assertEquals(2, sites.observedSites)
        assertEquals(1, sites.limitSites)

        val retainedBytes = ByteArray(0x100).also { image ->
            listOf(20_001, 3, 0xFFFF).forEachIndexed { index, value ->
                putU16(image, 0x20 + index * 2, value)
            }
            putThumbLiteralReferences(image, 0x60, 0x70, 0x20)
        }
        val retainedLayout = AcquisitionTableLayout(
            AcquisitionMethod.EGG,
            4,
            AcquisitionAbi.Gen3EggSentinelU16(0x20, 8),
        )
        val retainedLimited = session(retainedBytes, limits = ResolutionLimits(maxProbeWorkPerDataset = 4))
        val retained = MoveAcquisitionResolver().resolve(
            retainedLimited,
            AcquisitionMethod.EGG,
            domain(1..4, 1..10),
            listOf(
                directProbe(retainedLimited, retainedLayout, mapOf(0x20L to listOf(0x60))),
            ),
        ) as DatasetResolution.BudgetExceeded<ResolvedAcquisitionLayout>
        assertEquals(BudgetKind.PROBE_WORK, retained.budgetKind)
        assertTrue(retained.reason.contains("retained"))

        val candidateBytes = ByteArray(0x600).also {
            writeMachine(it, 0x80, 0x200)
            writeMachine(it, 0x100, 0x300)
        }
        val candidates = MoveAcquisitionResolver().resolve(
            session(candidateBytes, limits = ResolutionLimits(maxCandidatesPerDataset = 1)),
            AcquisitionMethod.MACHINE,
            domain(0..1, 1..10),
            listOf(
                AcquisitionProbe(machineLayout(0x80, 0x200), CandidateSource.INHERITED_FAMILY_LAYOUT),
                AcquisitionProbe(machineLayout(0x100, 0x300), CandidateSource.INHERITED_FAMILY_LAYOUT),
            ),
        ) as DatasetResolution.BudgetExceeded<ResolvedAcquisitionLayout>
        assertEquals(BudgetKind.CANDIDATES, candidates.budgetKind)
    }

    @Test
    fun aggregateExtentBudgetCountsAllDistinctPhysicalSpans() {
        val bytes = validMachineBytes()
        val result = codec.decode(
            session(bytes, limits = ResolutionLimits(maxDatasetExtentBytes = 5)),
            machineLayout(0x80, 0x200),
            domain(0..1, 1..10),
        ) as AcquisitionTableOutcome.ExtentBudgetExceeded

        assertEquals(6, result.observedBytes)
        assertEquals(5, result.limitBytes)
        assertTrue(result.reason.contains("aggregate"))
    }

    @Test
    fun projectionConsumesOnlyTypedResolutionAndReturnsImmutableValueCollections() {
        val bytes = validMachineBytes()
        val analysis = session(bytes)
        val result = MoveAcquisitionResolver().resolve(
            analysis,
            AcquisitionMethod.MACHINE,
            domain(0..1, 1..10),
            listOf(directProbe(analysis, machineLayout(0x80, 0x200))),
        ) as DatasetResolution.Resolved<ResolvedAcquisitionLayout>

        val projection = MoveAcquisitionProjection.project(result.candidate.layout)

        assertEquals(result.candidate.layout.acquisitionsBySpecies, projection.acquisitionsBySpecies)
        assertNotSame(result.candidate.layout.acquisitionsBySpecies, projection.acquisitionsBySpecies)
        @Suppress("UNCHECKED_CAST")
        assertThrows(UnsupportedOperationException::class.java) {
            (projection.acquisitionsBySpecies as MutableMap<Int, List<AcquisitionLink>>)[99] = emptyList()
        }
    }

    @Test
    fun layoutAndProbeDefensivelyCopyCallerCollections() {
        val layout = machineLayout(0x80, 0x200)
        val sites = mutableListOf(0x20)
        val bindings = mutableMapOf<Long, Collection<Int>>(
            0x80L to sites,
            0x200L to listOf(0x22),
        )
        val proof = (
            DirectAcquisitionProof.verify(session(validMachineBytes()), layout, bindings)
                as DirectAcquisitionProofResult.Verified
            ).proof
        val probe = AcquisitionProbe.direct(layout, proof)
        sites += 0x24
        bindings.clear()

        assertEquals(listOf(0x20, 0x22), probe.directInstructionSites)
    }

    @Test
    fun directEvidenceCannotOmitOneHalfOfAnAssociationPair() {
        val layout = machineLayout(0x80, 0x200)

        val result = DirectAcquisitionProof.verify(
            session(validMachineBytes()),
            layout,
            mapOf(0x80L to listOf(0x20)),
        )
        assertTrue(result is DirectAcquisitionProofResult.Rejected)
    }

    @Test
    fun directEvidenceCannotReuseOneInstructionSiteForTwoPhysicalRoots() {
        val layout = machineLayout(0x80, 0x200)

        val result = DirectAcquisitionProof.verify(
            session(validMachineBytes()),
            layout,
            mapOf(
                0x80L to listOf(0x20),
                0x200L to listOf(0x20),
            ),
        )
        assertTrue(result is DirectAcquisitionProofResult.Rejected)
    }

    @Test
    fun abiPhysicalRootsCannotBeMutatedThroughAJvmMutableListCast() {
        val abi = AcquisitionAbi.Gen3U16MoveListBitfield(0x80, 2, 0x200, 1)

        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (abi.physicalRoots as MutableList<Long>)[0] = 0x300
        }
        assertEquals(listOf(0x80L, 0x200L), abi.physicalRoots)
    }

    @Test
    fun acquisitionLinksRejectImpossibleMethodProvenancePairs() {
        assertThrows(IllegalArgumentException::class.java) {
            AcquisitionLink(
                moveId = 3,
                method = AcquisitionMethod.MACHINE,
                provenance = AcquisitionProvenance.TUTOR,
            )
        }
    }

    @Test
    fun resolvedLayoutsRejectLinksFromAnotherAcquisitionMethod() {
        val layout = machineLayout(0x80, 0x200)
        val rows = listOf(
            AcquisitionRowOutcome.Decoded(
                0,
                listOf(AcquisitionLink(3, AcquisitionMethod.TUTOR)),
            ),
            AcquisitionRowOutcome.StructuralEmpty(1),
        )

        assertThrows(IllegalArgumentException::class.java) {
            ResolvedAcquisitionLayout(layout, rows)
        }
    }

    @Test
    fun boundsCallerProbesBeforeNormalizationEvenWhenTheyAreDuplicates() {
        val bytes = validMachineBytes()
        var builds = 0
        val layout = machineLayout(0x80, 0x200)
        val probe = AcquisitionProbe(layout, CandidateSource.INHERITED_FAMILY_LAYOUT)
        val result = MoveAcquisitionResolver().resolve(
            session(
                bytes,
                limits = ResolutionLimits(maxProbeWorkPerDataset = 2),
                onIndexBuild = { builds++ },
            ),
            AcquisitionMethod.MACHINE,
            domain(0..1, 1..10),
            listOf(probe, probe, probe),
        ) as DatasetResolution.BudgetExceeded<ResolvedAcquisitionLayout>

        assertEquals(BudgetKind.PROBE_WORK, result.budgetKind)
        assertTrue(result.reason.contains("input-proposal"))
        assertEquals(0, builds)
    }

    @Test
    fun variableLengthExtentBudgetIsConsumedBeforeTheReadThatWouldExceedIt() {
        val bytes = ByteArray(0x100)
        listOf(20_001, 3, 0xFFFF).forEachIndexed { index, value ->
            putU16(bytes, 0x20 + index * 2, value)
        }
        val layout = AcquisitionTableLayout(
            AcquisitionMethod.EGG,
            1,
            AcquisitionAbi.Gen3EggSentinelU16(0x20, 8),
        )

        val result = codec.decode(
            session(
                bytes,
                limits = ResolutionLimits(
                    maxProbeWorkPerDataset = 2,
                    maxDatasetExtentBytes = 2,
                ),
            ),
            layout,
            domain(1..1, 1..10),
        )

        assertTrue(result is AcquisitionTableOutcome.ExtentBudgetExceeded)
    }

    private fun validMachineBytes(): ByteArray = ByteArray(0x500).also {
        writeMachine(it, 0x80, 0x200)
        putThumbLiteralReferences(it, 0x20, 0x40, 0x80, 0x200)
    }

    private fun writeMachine(bytes: ByteArray, moveOffset: Int, flagsOffset: Int) {
        putU16(bytes, moveOffset, 3)
        putU16(bytes, moveOffset + 2, 4)
        bytes[flagsOffset] = 1
        bytes[flagsOffset + 1] = 2
    }

    private fun machineLayout(moveOffset: Int, flagsOffset: Int) = AcquisitionTableLayout(
        AcquisitionMethod.MACHINE,
        2,
        AcquisitionAbi.Gen3U16MoveListBitfield(moveOffset.toLong(), 2, flagsOffset.toLong(), 1),
    )

    private fun directProbe(
        analysis: RomAnalysisSession,
        layout: AcquisitionTableLayout,
        sites: Map<Long, Collection<Int>> = directSites(layout),
    ): AcquisitionProbe {
        val verified = DirectAcquisitionProof.verify(analysis, layout, sites)
            as DirectAcquisitionProofResult.Verified
        return AcquisitionProbe.direct(layout, verified.proof)
    }

    private fun directSites(layout: AcquisitionTableLayout): Map<Long, Collection<Int>> =
        layout.abi.physicalRoots.mapIndexed { index, root -> root to listOf(0x20 + index * 2) }.toMap()

    private fun putThumbLiteralReferences(
        bytes: ByteArray,
        instructionOffset: Int,
        literalOffset: Int,
        vararg targets: Int,
    ) {
        targets.forEachIndexed { index, target ->
            val instruction = instructionOffset + index * 2
            val literal = literalOffset + index * 4
            val pc = (instruction + 4) and -4
            putU16(bytes, instruction, 0x4800 or ((literal - pc) / 4))
            putU32(bytes, literal, 0x08000000L + target)
        }
    }

    private fun domain(species: IntRange, moves: IntRange) = AcquisitionSemanticDomain(
        species.toSet(),
        moves.toSet(),
    )

    private fun session(
        bytes: ByteArray,
        platform: Platform = Platform.GBA,
        limits: ResolutionLimits = ResolutionLimits(),
        onIndexBuild: () -> Unit = {},
        referenceCounts: Map<Int, Int> = emptyMap(),
        referenceIndex: GbaReferenceIndex? = null,
    ): RomAnalysisSession {
        val rom = RomImage(bytes)
        return RomAnalysisSession(
            rom,
            RomHeader(platform, "ACQUISITION"),
            exactProfile = null,
            limits,
            gbaReferenceIndexFactory = { _, _ ->
                onIndexBuild()
                referenceIndex ?: GbaReferenceIndex.countsOnlyForTesting(referenceCounts)
            },
        )
    }

    private fun putPointer(bytes: ByteArray, offset: Int, target: Int) =
        putU32(bytes, offset, 0x08000000L + target)

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
