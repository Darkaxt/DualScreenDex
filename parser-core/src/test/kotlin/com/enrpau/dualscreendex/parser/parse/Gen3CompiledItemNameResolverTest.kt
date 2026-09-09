package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndexFactory
import com.enrpau.dualscreendex.parser.analysis.GbaTargetReferenceEvidence
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.GbaItemNameAuthority
import com.enrpau.dualscreendex.parser.model.GbaItemNameProvenance
import com.enrpau.dualscreendex.parser.model.GbaItemPublishedRoute
import com.enrpau.dualscreendex.parser.model.GbaItemRootNomination
import org.junit.Assert.*
import org.junit.Test

class Gen3CompiledItemNameResolverTest {
    @Test fun completePrefixedDynamicWrapperDerivesOnlyOrdinaryStaticAuthority() {
        for ((stride, maximum, width) in listOf(Triple(44, 174, 14), Triple(52, 150, 18), Triple(40, 188, 10))) {
            for (excluded in listOf(0, 31, 175)) {
                val f = ItemConsumerFixture(root = 0x6000, sanitizer = 0x1800, nameGetter = 0x1A00,
                    scalarStart = 0x1C00, wrapper = 0x2800, copier = 0x3200,
                    mulStride = stride, maximumHalf = maximum, nameBytes = width, excluded = excluded)
                f.prefixedCopyWrapper()
                f.descriptionLineConsumer(0x4000)
                f.bytes.fill(0xFC.toByte(), f.root, f.root + (maximum * 2 + 1) * stride)
                val resolver = f.session().itemNameResolver
                val proof = resolver.resolve(f.root)
                assertEquals("complete relocated r5 wrapper: ${proof.reason}",
                    Gen3CompiledItemNameResolver.Table(f.root, stride, maximum * 2 + 1, width, setOf(excluded)), proof.table)
                for (route in listOf(GbaItemPublishedRoute.NotInvoked,
                    GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Nominated(f.root)))) {
                    val authority = resolver.original(route)
                    assertTrue("$route: $authority", authority is GbaItemNameAuthority.Available)
                    assertEquals(excluded, (authority as GbaItemNameAuthority.Available).excludedId)
                    assertSame(authority, resolver.original(route))
                }
            }
        }
    }

    @Test fun everyPrefixedWrapperAndCopierInstructionMustBeComplete() {
        val offsets = (0 until 40 step 2) + (44 until 62 step 2)
        for (offset in offsets + (0 until 30 step 2).map { it + 0x200 }) {
            val f = ItemConsumerFixture(mulStride = 44, nameBytes = 14)
            f.prefixedCopyWrapper()
            f.half(f.wrapper + offset, if (offset == 38) 1 else 0)
            val proof = f.session().itemNameResolver.resolve(f.root)
            assertNull("required r5 wrapper/copier halfword +${offset.toString(16)}: ${proof.reason}", proof.table)
            assertTrue(f.session().itemNameResolver.original(GbaItemPublishedRoute.NotInvoked)
                is GbaItemNameAuthority.Unavailable)
        }
    }

    @Test fun prefixedWrapperCompetitorsCannotDisappearOrMergeWithExistingContracts() {
        val mutations = listOf<Int?>(null) + (8 until 40 step 2) + (48 until 62 step 2)
        for (simple in listOf(false, true)) for (offset in mutations) {
            val f = ItemConsumerFixture(mulStride = 44, simpleWrapper = simple)
            f.prefixedCopyWrapper(0x2000)
            if (offset != null) f.half(0x2000 + offset, if (offset == 38) 1 else 0)
            val proof = f.session().itemNameResolver.resolve(f.root)
            assertNull("simple=$simple competing r5 dependency=$offset: ${proof.reason}", proof.table)
            assertTrue(f.session().itemNameResolver.original(GbaItemPublishedRoute.NotInvoked)
                is GbaItemNameAuthority.Unavailable)
        }
        val f = ItemConsumerFixture(mulStride = 44)
        f.prefixedCopyWrapper(); f.prefixedCopyWrapper(0x2000)
        assertNull("two complete r5 wrappers remain distinct", f.session().itemNameResolver.resolve(f.root).table)
    }

    @Test fun prefixedWrapperPoolBranchRegisterAndCallDependenciesStayClosed() {
        val mutations = listOf<Pair<String, (ItemConsumerFixture) -> Unit>>(
            "wrong BNE destination" to { f -> f.half(f.wrapper + 10, 0xD10D) },
            "equal branch reaches ordinary getter" to { f -> f.half(f.wrapper + 36, 0xE002) },
            "equal branch reaches pool" to { f -> f.half(f.wrapper + 36, 0xE000) },
            "wrong saved source" to { f -> f.half(f.wrapper + 18, 0x1C05) },
            "wrong ordinary destination" to { f -> f.half(f.wrapper + 50, 0x1C20) },
            "escaped literal" to { f -> f.literalLoad(f.wrapper + 20, 1, f.wrapper + 64, 0x3000) },
            "literal is scalar" to { f -> f.half(f.wrapper + 42, 0) },
            "literal is RAM" to { f -> f.half(f.wrapper + 42, 0x0200) },
            "literal is outside ROM" to { f -> f.pointer(f.wrapper + 40, f.bytes.size) },
            "literal adds same-root consumer" to { f -> f.pointer(f.wrapper + 40, f.root) },
            "different equal copier" to { f -> f.bl(f.wrapper + 24, 0x2800) },
            "unknown ordinary copier" to { f -> f.bl(f.wrapper + 52, 0x2800) },
        ) + listOf(14, 32, 52).flatMap { offset -> listOf(
            "call+$offset outside" to { f: ItemConsumerFixture -> f.bl(f.wrapper + offset, f.bytes.size) },
            "call+$offset local pool" to { f: ItemConsumerFixture -> f.bl(f.wrapper + offset, f.wrapper + 40) },
            "call+$offset local ordinary" to { f: ItemConsumerFixture -> f.bl(f.wrapper + offset, f.wrapper + 44) },
        ) }
        for ((label, mutate) in mutations) {
            val f = ItemConsumerFixture(mulStride = 44, nameBytes = 14)
            f.prefixedCopyWrapper(); mutate(f)
            assertNull(label, f.session().itemNameResolver.resolve(f.root).table)
            assertTrue(label, f.session().itemNameResolver.original(GbaItemPublishedRoute.NotInvoked)
                is GbaItemNameAuthority.Unavailable)
        }
    }

    @Test fun prefixedDynamicPayloadAndOpaqueHelpersNeverGrantExcludedNameAuthority() {
        val f = ItemConsumerFixture(mulStride = 44, nameBytes = 14)
        f.prefixedCopyWrapper()
        f.bytes.fill(0xFC.toByte(), 0x3000, 0x3100) // unreadable prefix payload is deliberately not traversed
        f.emit(0x1600, 0x4770); f.emit(0x1700, 0x4770) // opaque, not independently proved string helpers
        val proof = f.session().itemNameResolver.resolve(f.root)
        assertNotNull("ordinary arm only: ${proof.reason}", proof.table)
        assertEquals(setOf(f.excluded), requireNotNull(proof.table).excludedIds)
        for (missing in listOf("pointer", "scalar", "wrapper")) {
            val broken = ItemConsumerFixture(mulStride = 44)
            broken.prefixedCopyWrapper()
            val at = when (missing) { "pointer" -> broken.nameGetter; "scalar" -> broken.scalarStart; else -> broken.wrapper }
            broken.bytes.fill(0, at, at + 64)
            assertNull(missing, broken.session().itemNameResolver.resolve(broken.root).table)
        }
    }

    @Test fun prefixedWrapperPreservesRecoveryCallerCapsBudgetsAndCancellation() {
        for (siteCap in listOf(1, 16)) for (passes in listOf(2, 3)) {
            val f = ItemConsumerFixture(mulStride = 44)
            f.prefixedCopyWrapper()
            val resolver = f.session(ResolutionLimits(maxCompiledReferenceSitesPerCandidate = siteCap,
                maxDatasetExtentBytes = f.bytes.size.toLong() * passes)).itemNameResolver
            assertEquals("siteCap=$siteCap passes=$passes", siteCap >= 14 || passes >= 3,
                resolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Available)
        }
        for (callers in listOf(127, 128)) {
            val f = ItemConsumerFixture(mulStride = 44)
            f.prefixedCopyWrapper()
            repeat(callers) { f.bl(0x2000 + it * 4, f.nameGetter) }
            assertEquals("caller count includes wrapper", callers == 127,
                f.session().itemNameResolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Available)
        }
        val f = ItemConsumerFixture(mulStride = 44)
        f.prefixedCopyWrapper()
        assertNull(f.session(ResolutionLimits(maxProbeWorkPerDataset = 8)).itemNameResolver.resolve(f.root).table)
        var stopped = false
        val resolver = f.session(cancellation = ParserCancellationToken {
            if (stopped) throw ParserCancellationException()
        }).itemNameResolver
        assertTrue(resolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Available)
        stopped = true
        assertThrows(ParserCancellationException::class.java) { resolver.original(GbaItemPublishedRoute.NotInvoked) }
        assertThrows(ParserCancellationException::class.java) { resolver.resolve(f.root) }
    }

    @Test fun completeR1DescriptionConsumerReconcilesWithoutAddingScalarField() {
        for ((stride, maximum, width) in listOf(Triple(44, 174, 10), Triple(44, 188, 14), Triple(52, 150, 18))) {
            for (entry in listOf(0x2000, 0x2800)) for (siteCap in listOf(1, 16)) {
                val f = ItemConsumerFixture(root = 0x6000, mulStride = stride, maximumHalf = maximum,
                    nameBytes = width, simpleWrapper = true)
                f.descriptionLineConsumer(entry)
                val session = f.session(ResolutionLimits(maxCompiledReferenceSitesPerCandidate = siteCap))
                assertEquals(15, requireNotNull(session.gbaReferenceIndex).referenceCount(f.root))
                assertEquals(1, requireNotNull(session.gbaReferenceIndex?.itemConsumerHints).observedSites)
                for (route in listOf(GbaItemPublishedRoute.NotInvoked,
                    GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Nominated(f.root)))) {
                    val authority = session.itemNameResolver.original(route)
                    assertTrue("stride=$stride width=$width entry=$entry siteCap=$siteCap: $authority",
                        authority is GbaItemNameAuthority.Available)
                    authority as GbaItemNameAuthority.Available
                    assertEquals(maximum * 2 + 1, authority.count); assertEquals(stride, authority.stride)
                    assertEquals(width, authority.nameBytes)
                    assertSame(authority, session.itemNameResolver.original(route))
                }
                assertNotNull(f.session().itemNameResolver.resolve(f.root).table)
            }
        }
    }

    @Test fun everyR1DescriptionInstructionDependencyMustBeComplete() {
        for (offset in (0 until 60 step 2) + (64 until 96 step 2)) {
            val f = ItemConsumerFixture(mulStride = 44, nameBytes = 14, simpleWrapper = true)
            f.descriptionLineConsumer(0x2000)
            // Preserve the root-reference nomination even when changing its LDR register.
            f.half(0x2000 + offset, if (offset == 8) 0x4D0C else 0)
            assertEquals("nomination survives +${offset.toString(16)}", 15,
                requireNotNull(f.session().gbaReferenceIndex).referenceCount(f.root))
            for (route in listOf(GbaItemPublishedRoute.NotInvoked,
                GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Nominated(f.root)))) {
                assertTrue("required auxiliary halfword +${offset.toString(16)}",
                    f.session().itemNameResolver.original(route) is GbaItemNameAuthority.Unavailable)
            }
            assertNull(f.session().itemNameResolver.resolve(f.root).table)
        }
    }

    @Test fun descriptionConsumerCannotReplaceIndependentScalarOrInlineAuthority() {
        for (missing in listOf("description scalar", "last scalar", "inline getter", "copier")) {
            val f = ItemConsumerFixture(mulStride = 44, nameBytes = 14, simpleWrapper = true)
            f.descriptionLineConsumer(0x2000)
            val (at, size) = when (missing) {
                "description scalar" -> f.scalarEntries[4] to 0x40
                "last scalar" -> f.scalarEntries.last() to 0x40
                "inline getter" -> f.nameGetter to 32
                else -> f.copier to 30
            }
            f.bytes.fill(0, at, at + size)
            assertTrue(missing, f.session().itemNameResolver.original(GbaItemPublishedRoute.NotInvoked)
                is GbaItemNameAuthority.Unavailable)
        }
    }

    @Test fun descriptionGeometryRootsLiteralOwnershipAndUnknownReferencesStayTerminal() {
        for (mutation in listOf("stride", "field", "count", "field-root", "escaped pool", "unknown reference")) {
            val f = ItemConsumerFixture(mulStride = 44, nameBytes = 14, simpleWrapper = true)
            f.descriptionLineConsumer(0x2000)
            when (mutation) {
                "stride" -> f.half(0x2016, 0x2134)
                "field" -> f.half(0x201A, 0x3418)
                "count" -> { f.emitSanitizer(0x3000, f.maximumHalf - 1); f.bl(0x200E, 0x3000) }
                "field-root" -> f.pointer(0x203C, f.root + 1)
                "escaped pool" -> f.literalLoad(0x2008, 4, 0x23FC, f.root)
                "unknown reference" -> f.literalLoad(0x3000, 3, 0x3004, f.root)
            }
            assertTrue(mutation, f.session().itemNameResolver.original(GbaItemPublishedRoute.NotInvoked)
                is GbaItemNameAuthority.Unavailable)
            assertNull(mutation, f.session().itemNameResolver.resolve(f.root).table)
        }
    }

    @Test fun unknownPartialOrLocalDescriptionSanitizerCallsCannotBeDischarged() {
        for (target in listOf(0x3000, 0x600 + 2, 0x800, 0x2040, 0x10000)) {
            val f = ItemConsumerFixture(mulStride = 44, nameBytes = 14, simpleWrapper = true)
            f.descriptionLineConsumer(0x2000)
            f.emit(0x3000, 0x4770) // opaque in-bounds leaf, not the complete u16 sanitizer
            f.bl(0x200E, target)
            assertTrue("call target=${target.toString(16)}",
                f.session().itemNameResolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Unavailable)
            assertNull(f.session().itemNameResolver.resolve(f.root).table)
        }
    }

    @Test fun descriptionWitnessKeepsCountsRecoveryBudgetsAndCancellation() {
        val f = ItemConsumerFixture(mulStride = 44, nameBytes = 14, simpleWrapper = true)
        f.descriptionLineConsumer(0x2000)
        for (cap in listOf(14, 15)) for (siteCap in listOf(1, 16)) for (passes in listOf(2, 3)) {
            val session = f.session(ResolutionLimits(maxNominatedGbaReferenceSites = cap,
                maxCompiledReferenceSitesPerCandidate = siteCap, maxCandidatesPerDataset = 1,
                maxDatasetExtentBytes = f.bytes.size.toLong() * passes))
            assertEquals(15, requireNotNull(session.gbaReferenceIndex).referenceCount(f.root))
            assertEquals("cap=$cap siteCap=$siteCap passes=$passes", cap >= 15 && (siteCap >= 15 || passes >= 3),
                session.itemNameResolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Available)
        }
        assertTrue(f.session(ResolutionLimits(maxProbeWorkPerDataset = 8)).itemNameResolver
            .original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Unavailable)
        var checks = 0
        assertThrows(ParserCancellationException::class.java) {
            f.session(cancellation = ParserCancellationToken {
                if (++checks > 40) throw ParserCancellationException()
            }).itemNameResolver.original(GbaItemPublishedRoute.NotInvoked)
        }
        var stopped = false
        val resolver = f.session(cancellation = ParserCancellationToken {
            if (stopped) throw ParserCancellationException()
        }).itemNameResolver
        assertTrue(resolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Available)
        stopped = true
        assertThrows(ParserCancellationException::class.java) { resolver.original(GbaItemPublishedRoute.NotInvoked) }
    }

    private fun pooledMulFixture(register: Int = 7, entry: Int = 0x800): ItemConsumerFixture =
        ItemConsumerFixture(root = 0x4800 or (register shl 8) or 80, nameGetter = entry,
            scalarStart = entry + 32, scalarSpacing = 36, mulStride = 44, maximumHalf = 187,
            nameBytes = 14, simpleWrapper = true)

    private fun fivePoolWords(f: ItemConsumerFixture): List<Int> =
        listOf(f.nameGetter + 28) + f.scalarEntries.take(4).map { it + 32 }

    @Test fun fiveOwnedLiteralWordsDoNotBecomeCompetingGetterInstructions() {
        for (register in listOf(6, 7)) for (entry in listOf(0x800, 0x1800)) for (siteCap in listOf(1, 16, 32)) {
            val f = pooledMulFixture(register, entry)
            val session = f.session(ResolutionLimits(maxCompiledReferenceSitesPerCandidate = siteCap))
            val index = requireNotNull(session.gbaReferenceIndex)
            assertEquals(19, index.referenceCount(f.root))
            assertEquals(1, requireNotNull(index.itemConsumerHints).observedSites)
            for (route in listOf(GbaItemPublishedRoute.NotInvoked,
                GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Nominated(f.root)))) {
                val authority = session.itemNameResolver.original(route)
                assertTrue("register=$register entry=$entry siteCap=$siteCap: $authority",
                    authority is GbaItemNameAuthority.Available)
                authority as GbaItemNameAuthority.Available
                assertEquals(375, authority.count); assertEquals(44, authority.stride)
                assertEquals(14, authority.nameBytes); assertEquals(f.root, authority.root)
            }
            assertNotNull(f.session().itemNameResolver.resolve(f.root).table)
        }
    }

    @Test fun shiftGetterOwnedLiteralInterpretationsAreAlsoReconciled() {
        val f = ItemConsumerFixture(root = 0x4800 or (7 shl 8) or 15, simpleWrapper = true)
        val session = f.session()
        assertTrue(requireNotNull(session.gbaReferenceIndex).referenceCount(f.root) > 14)
        val authority = session.itemNameResolver.original(GbaItemPublishedRoute.NotInvoked)
        assertTrue("$authority", authority is GbaItemNameAuthority.Available)
    }

    @Test fun everySupportedDirectEdgeIntoEitherPoolHalfwordRemainsTerminal() {
        for (kind in listOf("BL", "B", "BEQ")) for (poolIndex in 0..4) for (halfword in listOf(0, 2)) {
            val f = pooledMulFixture()
            val source = 0x7B0
            val target = fivePoolWords(f)[poolIndex] + halfword
            val halfDelta = (target - source - 4) / 2
            when (kind) {
                "BL" -> f.bl(source, target)
                "B" -> f.half(source, 0xE000 or (halfDelta and 0x7FF))
                "BEQ" -> { assertTrue(halfDelta in -128..127); f.half(source, 0xD000 or (halfDelta and 255)) }
            }
            for (route in listOf(GbaItemPublishedRoute.NotInvoked,
                GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Nominated(f.root)))) {
                assertTrue("kind=$kind pool=$poolIndex halfword=$halfword",
                    f.session().itemNameResolver.original(route) is GbaItemNameAuthority.Unavailable)
            }
            assertNull("explicit root kind=$kind", f.session().itemNameResolver.resolve(f.root).table)
        }
    }

    @Test fun backwardBranchesAndFinalHalfwordEdgesCannotEnterOwnedPools() {
        for (kind in listOf("BL", "B")) {
            val f = pooledMulFixture()
            val source = 0xA00
            val target = fivePoolWords(f).first() + 2
            if (kind == "BL") f.bl(source, target)
            else f.half(source, 0xE000 or (((target - source - 4) / 2) and 0x7FF))
            assertTrue(f.session().itemNameResolver.original(GbaItemPublishedRoute.NotInvoked)
                is GbaItemNameAuthority.Unavailable)
        }
        for (condition in 0..15) {
            val f = ItemConsumerFixture(root = 0x4800 or (7 shl 8) or 15, simpleWrapper = true)
            val source = f.scalarStart + 54 // unused gap after the first owned pool
            val target = f.scalarStart + 32
            f.half(source, 0xD000 or (condition shl 8) or (((target - source - 4) / 2) and 255))
            assertEquals("condition=$condition", condition >= 14,
                f.session().itemNameResolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Available)
        }
        val f = pooledMulFixture(entry = 0xFD80)
        val source = f.bytes.size - 2
        val target = fivePoolWords(f).first()
        f.half(source, 0xE000 or (((target - source - 4) / 2) and 0x7FF))
        assertTrue(f.session().itemNameResolver.original(GbaItemPublishedRoute.NotInvoked)
            is GbaItemNameAuthority.Unavailable)
    }

    @Test fun directIncomingAlignmentPaddingCannotFallThroughIntoOwnedPools() {
        for (kind in listOf("BL", "B", "BEQ")) for (poolIndex in 0..4) {
            val f = pooledMulFixture()
            val source = 0x7B0
            val target = fivePoolWords(f)[poolIndex] - 2
            val halfDelta = (target - source - 4) / 2
            when (kind) {
                "BL" -> f.bl(source, target)
                "B" -> f.half(source, 0xE000 or (halfDelta and 0x7FF))
                "BEQ" -> f.half(source, 0xD000 or (halfDelta and 255))
            }
            val authority = f.session().itemNameResolver.original(GbaItemPublishedRoute.NotInvoked)
            assertTrue("kind=$kind pool=$poolIndex: $authority", authority is GbaItemNameAuthority.Unavailable)
            assertNull(f.session().itemNameResolver.resolve(f.root).table)
        }
    }

    @Test fun nonzeroGetterAlignmentPaddingDoesNotGrantPoolOwnership() {
        for (poolIndex in 0..4) {
            val f = pooledMulFixture()
            f.half(fivePoolWords(f)[poolIndex] - 2, 0x1C00)
            assertTrue(f.session().itemNameResolver.original(GbaItemPublishedRoute.NotInvoked)
                is GbaItemNameAuthority.Unavailable)
        }
    }

    @Test fun incompleteGetterAndEscapedPoolOwnershipCannotDischargeWords() {
        for (poolIndex in 0..4) for (escape in listOf(false, true)) {
            val f = pooledMulFixture()
            val ownerSite = if (poolIndex == 0) f.nameGetter + 18 else f.scalarEntries[poolIndex - 1] + 6
            if (escape) f.literalLoad(ownerSite, if (poolIndex == 0) 1 else 4, 0xB00, f.root)
            else f.half(fivePoolWords(f)[poolIndex] - 4, 0) // damage required BX, keep nomination
            val session = f.session()
            assertEquals(1, requireNotNull(session.gbaReferenceIndex?.itemConsumerHints).observedSites)
            assertTrue("pool=$poolIndex escape=$escape",
                session.itemNameResolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Unavailable)
        }
    }

    @Test fun poolInterpretationCountsAndSharedScanBudgetsStayComplete() {
        for (siteCap in listOf(16, 32)) for (passes in listOf(2, 3)) {
            val f = pooledMulFixture()
            val session = f.session(ResolutionLimits(maxCompiledReferenceSitesPerCandidate = siteCap,
                maxDatasetExtentBytes = f.bytes.size.toLong() * passes))
            assertEquals(19, requireNotNull(session.gbaReferenceIndex).referenceCount(f.root))
            assertEquals("siteCap=$siteCap passes=$passes", siteCap >= 19 || passes >= 3,
                session.itemNameResolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Available)
        }
        for (cap in listOf(18, 19)) {
            val f = pooledMulFixture()
            assertEquals("site inventory cap=$cap", cap >= 19,
                f.session(ResolutionLimits(maxNominatedGbaReferenceSites = cap)).itemNameResolver
                    .original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Available)
        }
    }

    @Test fun ownedPoolWorkAndCachedAuthorityHonorCancellation() {
        val f = pooledMulFixture()
        assertTrue(f.session(ResolutionLimits(maxProbeWorkPerDataset = 8)).itemNameResolver
            .original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Unavailable)
        var checks = 0
        assertThrows(ParserCancellationException::class.java) {
            f.session(cancellation = ParserCancellationToken {
                if (++checks > 40) throw ParserCancellationException()
            }).itemNameResolver.original(GbaItemPublishedRoute.NotInvoked)
        }
        var stopped = false
        val resolver = f.session(cancellation = ParserCancellationToken {
            if (stopped) throw ParserCancellationException()
        }).itemNameResolver
        assertTrue(resolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Available)
        stopped = true
        assertThrows(ParserCancellationException::class.java) { resolver.original(GbaItemPublishedRoute.NotInvoked) }
    }

    @Test fun completeConditionalU8CallDoesNotPoisonInlineAuthority() {
        for (stride in listOf(44, 52)) for (scale in listOf(2, 3)) {
            val f = ItemConsumerFixture(root = 0x6000, mulStride = stride,
                nameBytes = 14, simpleWrapper = true)
            f.conditionalU8Call(0x300, 0x3800, scale)
            val session = f.session()
            assertEquals(2, requireNotNull(session.gbaReferenceIndex?.itemConsumerHints).observedSites)
            for (route in listOf(GbaItemPublishedRoute.NotInvoked,
                GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Nominated(f.root)))) {
                val result = session.itemNameResolver.original(route)
                assertTrue("$route: $result", result is GbaItemNameAuthority.Available)
                result as GbaItemNameAuthority.Available
                assertEquals(f.root, result.root); assertEquals(stride, result.stride)
                assertEquals(377, result.count); assertEquals(14, result.nameBytes)
                assertSame(result, session.itemNameResolver.original(route))
            }
        }
    }

    @Test fun completeU8CategoryCopyDoesNotPoisonInlineAuthority() {
        val f = ItemConsumerFixture(root = 0x6000, mulStride = 44, nameBytes = 14, simpleWrapper = true)
        f.conditionalU8Call(0x300, 0x3800)
        f.u8CategoryCopy(0x2000, 0x3900)
        val session = f.session()
        assertEquals(3, requireNotNull(session.gbaReferenceIndex?.itemConsumerHints).observedSites)
        for (route in listOf(GbaItemPublishedRoute.NotInvoked,
            GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Nominated(f.root)))) {
            val result = session.itemNameResolver.original(route)
            assertTrue("$route: $result", result is GbaItemNameAuthority.Available)
        }
    }

    @Test fun damagedU8WrapperProofsRemainTerminal() {
        for (category in listOf(false, true)) {
            val required = if (category) listOf(6, 8, 10, 12, 14, 16, 18, 20, 24, 32, 34, 36,
                38, 40, 42, 44, 46, 48, 50, 52, 54, 56, 58, 60, 62)
            else listOf(6, 8, 10, 12, 14, 16, 18, 20, 24, 26, 28, 30, 32)
            for (offset in required) {
                val f = ItemConsumerFixture(root = 0x6000, mulStride = 44, nameBytes = 14, simpleWrapper = true)
                if (category) f.u8CategoryCopy(0x2000, 0x3800) else f.conditionalU8Call(0x2000, 0x3800)
                f.half(0x2000 + offset, if (category && offset == 34) 0x0800 else 0)
                val session = f.session()
                assertEquals("category=$category offset=$offset", 2,
                    requireNotNull(session.gbaReferenceIndex?.itemConsumerHints).observedSites)
                assertTrue("category=$category offset=$offset",
                    session.itemNameResolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Unavailable)
            }
        }
    }

    @Test fun nonGetterHintsStillCountBeforeCapsAndRecovery() {
        for (hintCap in listOf(1, 2, 64)) for (siteCap in listOf(1, 16)) {
            val f = ItemConsumerFixture(root = 0x6000, mulStride = 44, nameBytes = 14, simpleWrapper = true)
            f.conditionalU8Call(0x300, 0x3800)
            f.u8CategoryCopy(0x2000, 0x3900)
            val session = f.session(ResolutionLimits(maxCandidatesPerDataset = hintCap,
                maxCompiledReferenceSitesPerCandidate = siteCap))
            val hints = requireNotNull(session.gbaReferenceIndex?.itemConsumerHints)
            assertEquals(3, hints.observedSites); assertEquals(minOf(3, hintCap), hints.sites.size)
            assertEquals(hintCap >= 3, hints.complete)
            assertEquals("hintCap=$hintCap siteCap=$siteCap", hintCap >= 3,
                session.itemNameResolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Available)
        }
    }

    @Test fun nonGetterProofDoesNotHideItemRootReferencesOrAuthorizeAlone() {
        for (sameRoot in listOf(false, true)) {
            val f = ItemConsumerFixture(root = 0x6000, mulStride = 44, nameBytes = 14, simpleWrapper = true)
            f.conditionalU8Call(0x300, if (sameRoot) f.root else 0x3800)
            if (!sameRoot) f.bytes.fill(0, f.nameGetter, f.nameGetter + 32)
            assertTrue(f.session().itemNameResolver.original(GbaItemPublishedRoute.NotInvoked)
                is GbaItemNameAuthority.Unavailable)
        }
    }

    @Test fun nonGetterScalarAndDestinationLiteralsCannotBecomeRomPointers() {
        for (category in listOf(false, true)) {
            val f = ItemConsumerFixture(root = 0x6000, mulStride = 44, nameBytes = 14, simpleWrapper = true)
            if (category) f.u8CategoryCopy(0x2000, 0x3800) else f.conditionalU8Call(0x2000, 0x3800)
            f.pointer(0x2000 + if (category) 68 else 36, f.root)
            assertTrue(f.session().itemNameResolver.original(GbaItemPublishedRoute.NotInvoked)
                is GbaItemNameAuthority.Unavailable)
        }
    }

    @Test fun nonGetterLiteralOwnershipAndLocalCallTargetsAreRequired() {
        for (category in listOf(false, true)) for (mutation in listOf("literal", "local call", "outside call", "frame")) {
            val f = ItemConsumerFixture(root = 0x6000, mulStride = 44, nameBytes = 14, simpleWrapper = true)
            if (category) f.u8CategoryCopy(0x2000, 0x3800) else f.conditionalU8Call(0x2000, 0x3800)
            when (mutation) {
                "literal" -> {
                    f.literalLoad(0x2000 + if (category) 38 else 14, 0, 0x23FC, 0)
                    f.half(0x23FC, 99); f.half(0x23FE, 0)
                }
                "local call" -> f.bl(0x2006, 0x2000 + if (category) 28 else 36)
                "outside call" -> f.bl(0x2006, f.bytes.size)
                "frame" -> f.half(0x2000 + if (category) 60 else 30, 0xBC02)
            }
            val session = f.session()
            assertEquals(2, requireNotNull(session.gbaReferenceIndex?.itemConsumerHints).observedSites)
            assertTrue("category=$category mutation=$mutation",
                session.itemNameResolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Unavailable)
        }
    }

    @Test fun nonGetterProofWorkBudgetAndCachedCancellationRemainTerminal() {
        val f = ItemConsumerFixture(root = 0x6000, mulStride = 44, nameBytes = 14, simpleWrapper = true)
        f.conditionalU8Call(0x300, 0x3800)
        f.u8CategoryCopy(0x2000, 0x3900)
        assertTrue(f.session(ResolutionLimits(maxProbeWorkPerDataset = 8)).itemNameResolver
            .original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Unavailable)
        var stopped = false
        val resolver = f.session(cancellation = ParserCancellationToken {
            if (stopped) throw ParserCancellationException()
        }).itemNameResolver
        assertTrue(resolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Available)
        stopped = true
        assertThrows(ParserCancellationException::class.java) { resolver.original(GbaItemPublishedRoute.NotInvoked) }
    }

    @Test fun crossRootShiftTailMutationsStayTerminalBeforeCapsAndRecovery() {
        assertTailDamagedCompetitorsRemainNominated(mulStride = null)
    }

    @Test fun crossRootMulTailMutationsStayTerminalBeforeCapsAndRecovery() {
        assertTailDamagedCompetitorsRemainNominated(mulStride = 52)
    }

    private fun assertTailDamagedCompetitorsRemainNominated(mulStride: Int?) {
        val failures = mutableListOf<String>()
        for (tailOffset in listOf(2, 4, 6)) {
            val first = ItemConsumerFixture(mulStride = mulStride, simpleWrapper = true)
            val second = ItemConsumerFixture(root = 0x9000, sanitizer = 0x2600, nameGetter = 0x2800,
                scalarStart = 0x2900, wrapper = 0x3200, copier = 0x3400,
                mulStride = mulStride, simpleWrapper = true)
            val secondSite = second.nameGetter + if (mulStride == null) 22 else 18
            second.half(secondSite + tailOffset, 0) // only the pointer ADD, POP, or BX is damaged
            second.bytes.copyInto(first.bytes, 0x2600, 0x2600, 0x3600)
            second.bytes.copyInto(first.bytes, second.root, second.root, second.root + (second.maximumHalf * 2 + 1) * second.stride)
            for (hintCap in listOf(1, 64)) for (siteCap in listOf(1, 16)) {
                val session = first.session(limits = ResolutionLimits(maxCandidatesPerDataset = hintCap,
                    maxCompiledReferenceSitesPerCandidate = siteCap))
                val label = "mul=$mulStride tail=+$tailOffset hintCap=$hintCap siteCap=$siteCap"
                val hints = requireNotNull(session.gbaReferenceIndex?.itemConsumerHints)
                for (route in listOf(GbaItemPublishedRoute.NotInvoked,
                    GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Nominated(first.root)))) {
                    val authority = session.itemNameResolver.original(route)
                    if (authority !is GbaItemNameAuthority.Unavailable) failures += "$label $route authorized $authority"
                    assertSame("terminal outcome is memoized", authority, session.itemNameResolver.original(route))
                }
                if (hints.observedSites != 2 || hints.sites.size != minOf(2, hintCap) || hints.complete != (hintCap >= 2)) {
                    failures += "$label lost malformed competitor: $hints"
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun completeMulSimpleDerivesRelocatedGeometryAndOriginalRouteAuthority() {
        for ((stride, halfMaximum, width) in listOf(Triple(44, 174, 10), Triple(52, 150, 14))) {
            val f = ItemConsumerFixture(root = 0x6000, sanitizer = 0x1800, nameGetter = 0x1A00,
                scalarStart = 0x1C00, wrapper = 0x2600, copier = 0x2800,
                mulStride = stride, maximumHalf = halfMaximum, nameBytes = width, simpleWrapper = true)
            f.bytes.fill(0xFC.toByte(), f.root, f.root + (halfMaximum * 2 + 1) * stride)
            val session = f.session()
            assertEquals(listOf(f.nameGetter + 18), requireNotNull(session.gbaReferenceIndex?.itemConsumerHints).sites)
            val resolver = session.itemNameResolver
            val count = halfMaximum * 2 + 1
            assertEquals(Gen3CompiledItemNameResolver.Table(f.root, stride, count, width, emptySet()), resolver.resolve(f.root).table)
            val original = resolver.original(GbaItemPublishedRoute.NotInvoked)
            assertTrue("$original", original is GbaItemNameAuthority.Available)
            original as GbaItemNameAuthority.Available
            assertEquals(f.root, original.root); assertEquals(stride, original.stride)
            assertEquals(count, original.count); assertEquals(width, original.nameBytes)
            assertNull(original.excludedId)
            assertEquals(GbaItemNameProvenance.COMPILED_CONSUMER, original.provenance)
            assertSame(original, resolver.original(GbaItemPublishedRoute.NotInvoked))
            val invoked = resolver.original(GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Nominated(f.root)))
            assertEquals(original.copy(provenance = GbaItemNameProvenance.PUBLISHED_ROOT), invoked)
            assertTrue(resolver.original(GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Nominated(f.root + 4)))
                is GbaItemNameAuthority.Unavailable)
        }
    }

    @Test fun completeMulDynamicKeepsSpecialExclusion() {
        val f = ItemConsumerFixture(mulStride = 44, maximumHalf = 174)
        val authority = f.session().itemNameResolver.original(GbaItemPublishedRoute.NotInvoked)
        assertEquals(GbaItemNameAuthority.Available(f.root, 44, 349, 10, 175,
            GbaItemNameProvenance.COMPILED_CONSUMER), authority)
    }

    @Test fun completeShiftSimpleHasNoDynamicExclusion() {
        val f = ItemConsumerFixture(simpleWrapper = true)
        val resolver = f.session().itemNameResolver
        assertEquals(Gen3CompiledItemNameResolver.Table(f.root, 40, 377, 10, emptySet()), resolver.resolve(f.root).table)
        val authority = resolver.original(GbaItemPublishedRoute.NotInvoked)
        assertTrue("$authority", authority is GbaItemNameAuthority.Available)
        assertNull((authority as GbaItemNameAuthority.Available).excludedId)
    }

    @Test fun mulHintCountsBeforeCapAndRecoveryRetainMalformedCompetitors() {
        for (oldShape in listOf(false, true)) {
            val f = ItemConsumerFixture(mulStride = 44, simpleWrapper = true)
            val second = ItemConsumerFixture(root = 0x9000, sanitizer = 0x2600, nameGetter = 0x2800,
                scalarStart = 0x2900, wrapper = 0x3200, copier = 0x3400,
                mulStride = if (oldShape) null else 52, simpleWrapper = true)
            second.half(second.nameGetter + if (oldShape) 18 else 16, 0) // keep nomination skeleton
            second.bytes.copyInto(f.bytes, 0x2600, 0x2600, 0x3600)
            for (cap in listOf(1, 64)) {
                val session = f.session(limits = ResolutionLimits(maxCandidatesPerDataset = cap,
                    maxCompiledReferenceSitesPerCandidate = 1))
                val hints = requireNotNull(session.gbaReferenceIndex?.itemConsumerHints)
                assertEquals(2, hints.observedSites)
                assertEquals(minOf(cap, 2), hints.sites.size)
                assertEquals(cap >= 2, hints.complete)
                for (route in listOf(GbaItemPublishedRoute.NotInvoked,
                    GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Nominated(f.root)))) {
                    assertTrue(session.itemNameResolver.original(route) is GbaItemNameAuthority.Unavailable)
                }
            }
        }
    }

    @Test fun mulRecoveryAndBudgetsDoNotAcquireFallbackAuthority() {
        for (passes in listOf(2, 3)) {
            val f = ItemConsumerFixture(mulStride = 44, simpleWrapper = true)
            val session = f.session(limits = ResolutionLimits(maxCompiledReferenceSitesPerCandidate = 1,
                maxDatasetExtentBytes = f.bytes.size.toLong() * passes))
            assertEquals(passes == 3, session.itemNameResolver.original(GbaItemPublishedRoute.NotInvoked)
                is GbaItemNameAuthority.Available)
        }
        for (limits in listOf(ResolutionLimits(maxProbeWorkPerDataset = 3),
            ResolutionLimits(maxNominatedGbaReferenceSites = 13), ResolutionLimits(maxDistinctGbaReferenceTargets = 1))) {
            val f = ItemConsumerFixture(mulStride = 44, simpleWrapper = true)
            if (limits.maxDistinctGbaReferenceTargets == 1) f.literalLoad(0x2400, 1, 0x2404, f.root + 0x100)
            assertTrue(f.session(limits).itemNameResolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Unavailable)
        }
        val f = ItemConsumerFixture(mulStride = 44, simpleWrapper = true)
        repeat(129) { f.bl(0x2000 + 4 * it, f.nameGetter) }
        assertTrue(f.session().itemNameResolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Unavailable)
    }

    @Test fun mulCancellationDuringWorkAndBeforeCachedAuthorityPropagates() {
        val f = ItemConsumerFixture(mulStride = 44, simpleWrapper = true)
        var checks = 0
        val during = f.session(cancellation = ParserCancellationToken { if (++checks > 40) throw ParserCancellationException() })
        assertThrows(ParserCancellationException::class.java) { during.itemNameResolver.original(GbaItemPublishedRoute.NotInvoked) }
        var stop = false
        val cached = f.session(cancellation = ParserCancellationToken { if (stop) throw ParserCancellationException() }).itemNameResolver
        assertTrue(cached.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Available)
        cached.original(GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Absent))
        stop = true
        for (route in listOf(GbaItemPublishedRoute.NotInvoked, GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Absent))) {
            assertThrows(ParserCancellationException::class.java) { cached.original(route) }
        }
    }

    @Test fun mulEveryRequiredInstructionAndFrameDependencyIsComplete() {
        val offsets = (0 until 24 step 2).map { 0x600 + it } +
            (0 until 26 step 2).map { 0x800 + it } +
            (0 until 30 step 2).map { 0x900 + it }
        for (at in offsets) {
            val f = ItemConsumerFixture(mulStride = 44, simpleWrapper = true)
            f.half(at, if (at == f.sanitizer + 8) 0x0800 else 0)
            assertNull("required MUL dependency ${at.toString(16)}", f.session().itemNameResolver.resolve(f.root).table)
            assertTrue(f.session().itemNameResolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Unavailable)
        }
    }

    @Test fun mulWrongRegistersStrideCountRootsAndFieldsRemainTerminal() {
        val mutations: List<Pair<String, (ItemConsumerFixture) -> Unit>> = listOf(
            "pointer MOV register" to { f -> f.half(f.nameGetter + 14, 0x222C) },
            "pointer MUL register" to { f -> f.half(f.nameGetter + 16, 0x4350) },
            "pointer stride below bound" to { f -> f.half(f.nameGetter + 14, 0x2101) },
            "scalar stride mismatch" to { f -> f.half(f.scalarStart + 16, 0x2130) },
            "scalar MOV register" to { f -> f.half(f.scalarStart + 16, 0x222C) },
            "scalar MUL register" to { f -> f.half(f.scalarStart + 18, 0x4350) },
            "scalar ADD register" to { f -> f.half(f.scalarStart + 20, 0x1909) },
            "scalar LDR base register" to { f -> f.half(f.scalarStart + 22, 0x8948) },
            "scalar LDR destination register" to { f -> f.half(f.scalarStart + 22, 0x8941) },
            "scalar field mismatch" to { f -> f.half(f.scalarStart + 22, 0x8980) },
            "scalar root mismatch" to { f -> f.pointer(f.scalarStart + 32, f.root + 0x100) },
            "scalar count mismatch" to { f -> f.emitSanitizer(0x1800, 173); f.bl(f.scalarStart + 8, 0x1800) },
            "missing first scalar" to { f -> f.bytes.fill(0, f.scalarStart, f.scalarStart + 0x40) },
            "missing final scalar" to { f -> f.bytes.fill(0, f.scalarEntries.last(), f.scalarEntries.last() + 0x40) },
            "root plus field witness" to { f -> f.literalLoad(0x1A00, 1, 0x1A04, f.root + 20) },
        )
        for ((label, mutate) in mutations) {
            val f = ItemConsumerFixture(mulStride = 44, simpleWrapper = true); mutate(f)
            assertNull(label, f.session().itemNameResolver.resolve(f.root).table)
            assertTrue(label, f.session().itemNameResolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Unavailable)
        }
    }

    @Test fun mulTableExtentRequiresEveryRecordByte() {
        val f = ItemConsumerFixture(mulStride = 44, maximumHalf = 174, simpleWrapper = true)
        for (delta in listOf(-1, 0)) {
            val session = RomAnalysisSession(RomImage(f.bytes.copyOf(f.root + 349 * f.stride + delta)), RomHeader(Platform.GBA, "SYNTHETIC"))
            val result = session.itemNameResolver.original(GbaItemPublishedRoute.NotInvoked)
            assertEquals("extent delta $delta: $result", delta == 0, result is GbaItemNameAuthority.Available)
        }
    }

    @Test fun nonpointerU16WrapperDoesNotBecomeMulHint() {
        val f = ItemConsumerFixture(mulStride = 44, simpleWrapper = true)
        val decoy = 0x2000
        f.emit(decoy, 0xB500, 0x0400, 0x0C00)
        f.bl(decoy + 6, f.sanitizer)
        f.emit(decoy + 10, 0x0400, 0x0C00, 0xBC02, 0x4708)
        val session = f.session()
        assertEquals(listOf(f.nameGetter + 18), requireNotNull(session.gbaReferenceIndex?.itemConsumerHints).sites)
        assertTrue(session.itemNameResolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Available)
        f.bytes.fill(0, f.nameGetter, f.nameGetter + 32)
        assertTrue(requireNotNull(f.session().gbaReferenceIndex?.itemConsumerHints).sites.isEmpty())
        assertTrue(f.session().itemNameResolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Unavailable)
    }

    @Test fun unsupportedR1Field20ConsumerAndUnknownLiteralReferencesStayTerminal() {
        for (largerConsumer in listOf(false, true)) {
            val f = ItemConsumerFixture(mulStride = 44, simpleWrapper = true)
            if (largerConsumer) {
                val entry = 0x2000
                f.emit(entry, 0xB510, 0x0400, 0x0C00)
                f.bl(entry + 6, f.sanitizer)
                f.emit(entry + 10, 0x0400, 0x0C00, 0x2100 or f.stride, 0x4348)
                f.literalLoad(entry + 18, 1, entry + 40, f.root)
                f.emit(entry + 20, 0x1840, 0x7D00, 0x2800, 0xD001, 0x2001) // deliberately incomplete larger body
            } else f.literalLoad(0x2000, 1, 0x2004, f.root)
            val session = f.session()
            assertEquals(1, requireNotNull(session.gbaReferenceIndex?.itemConsumerHints).observedSites)
            assertNull(session.itemNameResolver.resolve(f.root).table)
            assertTrue(session.itemNameResolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Unavailable)
        }
    }

    @Test fun simpleWrapperAndCopierCannotBePartialOrMissing() {
        val offsets = (0 until 26 step 2).map { 0x1200 + it } + (0 until 30 step 2).map { 0x1400 + it }
        for (at in offsets) {
            val f = ItemConsumerFixture(mulStride = 44, simpleWrapper = true); f.half(at, 0)
            assertNull("simple dependency ${at.toString(16)}", f.session().itemNameResolver.resolve(f.root).table)
        }
        val f = ItemConsumerFixture(mulStride = 44, simpleWrapper = true)
        f.bl(f.wrapper + 16, 0x2000) // in-bounds but no copier
        assertTrue(f.session().itemNameResolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Unavailable)
    }

    @Test fun secondSimpleWrapperIsNotSkippedWhenIncompleteOrMergedWhenComplete() {
        for (broken in listOf(false, true)) {
            val f = ItemConsumerFixture(mulStride = 44, simpleWrapper = true)
            f.simpleCopyWrapper(0x2000)
            if (broken) f.half(0x2018, 0)
            assertNull(f.session().itemNameResolver.resolve(f.root).table)
            assertTrue(f.session().itemNameResolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Unavailable)
        }
        val dynamic = ItemConsumerFixture(mulStride = 44)
        dynamic.simpleCopyWrapper(0x2000)
        assertNull(dynamic.session().itemNameResolver.resolve(dynamic.root).table)
    }

    @Test fun conditionalNonzeroCopyPadFragmentDoesNotGrantSimpleAuthority() {
        val f = ItemConsumerFixture(mulStride = 44, simpleWrapper = true)
        f.bytes.fill(0, f.wrapper, f.wrapper + 64)
        f.emit(f.wrapper, 0xB510, 0x1C0C, 0x0400, 0x0C00, 0x2800, 0xD001)
        f.bl(f.wrapper + 12, f.nameGetter)
        f.emit(f.wrapper + 16, 0x1C01, 0x1C20, 0x220A)
        f.bl(f.wrapper + 22, f.copier)
        f.emit(f.wrapper + 26, 0xBC10, 0xBC01, 0x4700)
        assertNull(f.session().itemNameResolver.resolve(f.root).table)
        assertTrue(f.session().itemNameResolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Unavailable)
    }

    @Test
    fun `original not invoked route proves relocated nonreadable headerless consumer`() {
        val f = ItemConsumerFixture(root = 0x7000, sanitizer = 0x1800, nameGetter = 0x2000,
            wrapper = 0x2200, copier = 0x2400, maximumHalf = 200, firstShift = 3, nameBytes = 8, excluded = 31)
        f.bytes.fill(0xFC.toByte(), f.root, f.root + 401 * f.stride)
        val resolver = f.session().itemNameResolver
        val result = resolver.original(GbaItemPublishedRoute.NotInvoked)
        assertEquals(GbaItemNameAuthority.Available(f.root, 72, 401, 8, 31,
            GbaItemNameProvenance.COMPILED_CONSUMER), result)
        assertSame(result, resolver.original(GbaItemPublishedRoute.NotInvoked))
    }

    @Test
    fun `original invoked published root is preserved and never replaced`() {
        val f = ItemConsumerFixture(); val resolver = f.session().itemNameResolver
        val published = GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Nominated(f.root))
        val result = resolver.original(published)
        assertEquals(GbaItemNameAuthority.Available(f.root, 40, 377, 10, 175,
            GbaItemNameProvenance.PUBLISHED_ROOT), result)
        assertSame(result, resolver.original(published))
        assertTrue(resolver.original(GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Nominated(f.root + 4)))
            is GbaItemNameAuthority.Unavailable)
        for (route in listOf(GbaItemPublishedRoute.NotEvaluated,
            GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Absent),
            GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Ambiguous))) {
            assertTrue(resolver.original(route) is GbaItemNameAuthority.Unavailable)
        }
    }

    @Test
    fun `competing original root and incomplete second root cannot disappear`() {
        for (broken in listOf(false, true)) {
            val f = twoConsumers()
            if (broken) f.half(0x2900 + 24, 0)
            val resolver = f.session().itemNameResolver
            for (route in listOf(GbaItemPublishedRoute.NotInvoked,
                GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Nominated(f.root)))) {
                val result = resolver.original(route)
                assertTrue("competing or incomplete root", result is GbaItemNameAuthority.Unavailable)
                assertSame(result, resolver.original(route))
            }
        }
    }

    @Test
    fun `original aggregate candidate root site caller and scan budgets fail closed`() {
        val limits = listOf(
            ResolutionLimits(maxCandidatesPerDataset = 1),
            ResolutionLimits(maxProbeRootsPerDataset = 1),
            ResolutionLimits(maxNominatedGbaReferenceSites = 20),
            ResolutionLimits(maxProbeWorkPerDataset = 3),
            ResolutionLimits(maxDatasetExtentBytes = 0x10000),
        )
        for (limit in limits) {
            val resolver = twoConsumers().session(limits = limit).itemNameResolver
            assertTrue(resolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Unavailable)
        }
        val f = ItemConsumerFixture()
        repeat(129) { f.bl(0x2000 + it * 4, f.nameGetter) }
        assertTrue(f.session().itemNameResolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Unavailable)
    }

    @Test
    fun `original headerless work uses one shared index and one target set scan`() {
        val f = ItemConsumerFixture()
        val index = requireNotNull(f.session().gbaReferenceIndex)
        var builds = 0
        val session = RomAnalysisSession(RomImage(f.bytes), RomHeader(Platform.GBA, "SYNTHETIC"),
            limits = ResolutionLimits(maxDatasetExtentBytes = f.bytes.size.toLong() * 2),
            gbaReferenceIndexFactory = GbaReferenceIndexFactory { _, _ -> builds++; index })
        val original = session.itemNameResolver.original(GbaItemPublishedRoute.NotInvoked)
        assertTrue(original is GbaItemNameAuthority.Available)
        assertTrue(session.itemNameResolver.original(GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Nominated(f.root)))
            is GbaItemNameAuthority.Available)
        assertSame(original, session.itemNameResolver.original(GbaItemPublishedRoute.NotInvoked))
        assertEquals(1, builds)
    }

    @Test
    fun `counts only original hints and truncated evidence grant no discovery permission`() {
        val f = ItemConsumerFixture()
        val incomplete = GbaReferenceIndex.countsOnlyForTesting(mapOf(f.root to f.rootSites.size))
        val session = RomAnalysisSession(RomImage(f.bytes), RomHeader(Platform.GBA, "SYNTHETIC"),
            gbaReferenceIndexFactory = GbaReferenceIndexFactory { _, _ -> incomplete })
        assertTrue(session.itemNameResolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Unavailable)
    }

    @Test
    fun `original cancellation precedes cached available and unavailable outcomes`() {
        val f = ItemConsumerFixture(); var stop = false
        val resolver = f.session(cancellation = ParserCancellationToken { if (stop) throw ParserCancellationException() }).itemNameResolver
        assertTrue(resolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Available)
        resolver.original(GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Absent))
        stop = true
        for (route in listOf(GbaItemPublishedRoute.NotInvoked, GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Absent))) {
            assertThrows(ParserCancellationException::class.java) { resolver.original(route) }
        }
    }

    @Test
    fun `original batch recovery is charged once and retains competing truncated candidates`() {
        for (passes in listOf(2, 3)) {
            val f = ItemConsumerFixture()
            val resolver = f.session(limits = ResolutionLimits(maxCompiledReferenceSitesPerCandidate = 1,
                maxDatasetExtentBytes = f.bytes.size.toLong() * passes)).itemNameResolver
            val result = resolver.original(GbaItemPublishedRoute.NotInvoked)
            assertEquals(passes == 3, result is GbaItemNameAuthority.Available)
            assertSame(result, resolver.original(GbaItemPublishedRoute.NotInvoked))
            assertEquals(passes == 3, resolver.original(GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Nominated(f.root)))
                is GbaItemNameAuthority.Available)
        }
        for (broken in listOf(false, true)) {
            val f = twoConsumers()
            if (broken) f.half(0x2600 + 8, 0x0800) // hinted candidate with incomplete sanitizer
            val session = f.session(limits = ResolutionLimits(maxCompiledReferenceSitesPerCandidate = 1))
            assertEquals(2, requireNotNull(session.gbaReferenceIndex?.itemConsumerHints).observedSites)
            assertTrue(session.itemNameResolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Unavailable)
        }
    }

    @Test
    fun `terminal original routes never build an index and cached outcomes survive other probes`() {
        var builds = 0
        val f = ItemConsumerFixture()
        val session = RomAnalysisSession(RomImage(f.bytes), RomHeader(Platform.GBA, "SYNTHETIC"),
            gbaReferenceIndexFactory = GbaReferenceIndexFactory { _, _ -> builds++; error("no discovery permission") })
        for (route in listOf(GbaItemPublishedRoute.NotEvaluated,
            GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Absent),
            GbaItemPublishedRoute.Invoked(GbaItemRootNomination.Ambiguous))) {
            val first = session.itemNameResolver.original(route)
            assertTrue(first is GbaItemNameAuthority.Unavailable)
            assertSame(first, session.itemNameResolver.original(route))
        }
        assertEquals(0, builds)
    }

    @Test
    fun `original cancellation during batch work never publishes an outcome`() {
        val f = ItemConsumerFixture()
        var checks = 0
        val session = f.session(cancellation = ParserCancellationToken { if (++checks > 40) throw ParserCancellationException() })
        assertThrows(ParserCancellationException::class.java) { session.itemNameResolver.original(GbaItemPublishedRoute.NotInvoked) }
        assertThrows(ParserCancellationException::class.java) { session.itemNameResolver.original(GbaItemPublishedRoute.NotInvoked) }
    }

    private fun twoConsumers(): ItemConsumerFixture {
        val first = ItemConsumerFixture()
        val second = ItemConsumerFixture(root = 0x9000, sanitizer = 0x2600, nameGetter = 0x2800,
            scalarStart = 0x2900, wrapper = 0x3200, copier = 0x3400)
        second.bytes.copyInto(first.bytes, 0x2600, 0x2600, 0x3600)
        return first
    }

    @Test
    fun `dynamic arm cannot branch into literal pool or fall through ordinary getter`() {
        for (branch in listOf(0xE7FF, 0xE001)) {
            val f = ItemConsumerFixture()
            f.half(f.wrapper + 34, branch)
            assertNull("reachable dynamic branch ${branch.toString(16)} must fail closed",
                Gen3CompiledItemNameResolver(f.session()).resolve(f.root).table)
        }
    }

    @Test
    fun `distinct complete wrappers with the same excluded id remain conflicting`() {
        val f = ItemConsumerFixture()
        val other = 0x2000
        f.bytes.copyInto(f.bytes, other, f.wrapper, f.wrapper + 58)
        f.bl(other + 14, 0x1600)
        f.bl(other + 22, f.copier)
        f.bl(other + 30, 0x1700)
        f.bl(other + 40, f.nameGetter)
        f.bl(other + 48, f.copier)
        assertNull(Gen3CompiledItemNameResolver(f.session()).resolve(f.root).table)
    }

    @Test
    fun `relocated complete consumer proves inline geometry before decoding`() {
        val fixture = ItemConsumerFixture()
        val result = Gen3CompiledItemNameResolver(fixture.session()).resolve(fixture.root)
        assertNotNull("complete relocated item consumer must resolve: ${result.reason}", result.table)
        val table = requireNotNull(result.table)
        assertEquals(fixture.root, table.root)
        assertEquals(40, table.stride)
        assertEquals(377, table.count)
        assertEquals(10, table.nameBytes)
        assertEquals(setOf(175), table.excludedIds)
    }
    @Test
    fun `relocation count stride width and exclusion are instruction derived`() {
        val f = ItemConsumerFixture(root = 0x7000, sanitizer = 0x1800, nameGetter = 0x2000,
            wrapper = 0x2200, copier = 0x2400, maximumHalf = 200, firstShift = 3, nameBytes = 8, excluded = 31)
        val result = Gen3CompiledItemNameResolver(f.session()).resolve(f.root)
        assertEquals(Gen3CompiledItemNameResolver.Table(f.root, 72, 401, 8, setOf(31)), result.table)
    }

    @Test
    fun `single instruction dependency mutations cannot publish a table`() {
        val offsets = buildList {
            addAll((0 until 24 step 2).map { 0x600 + it })
            addAll((0 until 30 step 2).map { 0x800 + it })
            addAll((0 until 32 step 2).map { 0x900 + it })
            addAll((0 until 30 step 2).map { 0x1400 + it })
            addAll(listOf(0, 2, 4, 6, 8, 10, 40, 42, 44, 46, 48, 50, 52, 54, 56).map { 0x1200 + it })
        }
        offsets.forEach { at ->
            val f = ItemConsumerFixture(); f.half(at, if (at == 0x608) 0x0800 else 0)
            val result = Gen3CompiledItemNameResolver(f.session()).resolve(f.root)
            assertNull("mutated instruction 0x${at.toString(16)}: ${result.reason}", result.table)
        }
    }

    @Test
    fun `numeric getters must agree on roots count stride and complete field inventory`() {
        val mutations: List<(ItemConsumerFixture) -> Unit> = listOf(
            { f -> f.pointer(0x920, f.root + 0x100) },
            { f -> f.half(0x916, 0x1900) },
            { f -> f.half(0x910, 0x00C1) },
            { f -> f.half(0x918, 0x8988) },
            { f -> f.emit(0x1800, 0xB500, 0x0400, 0x0C01, 0x20BD, 0x0040, 0x4281, 0xD801, 0x1C08, 0xE000, 0x2000, 0xBC02, 0x4708); f.bl(0x908, 0x1800) },
            { f -> f.half(0x1A00, 0x4900); f.pointer(0x1A04, f.root) },
            { f -> f.half(0x1A00, 0x4900); f.pointer(0x1A04, f.root + 1) },
        )
        mutations.forEachIndexed { i, mutation ->
            val f = ItemConsumerFixture(); mutation(f)
            assertNull("root/geometry dependency mutation $i", Gen3CompiledItemNameResolver(f.session()).resolve(f.root).table)
        }
    }

    @Test
    fun `competing complete name getters and copy contracts are terminal`() {
        for (other in listOf("getter", "wrapper")) {
            val f = ItemConsumerFixture()
            if (other == "getter") {
                f.bytes.copyInto(f.bytes, 0x2000, f.nameGetter, f.nameGetter + 36)
                f.bl(0x2006, f.sanitizer)
            } else {
                f.bytes.copyInto(f.bytes, 0x2000, f.wrapper, f.wrapper + 58)
                f.half(0x2008, 0x281F); f.bl(0x2028, f.nameGetter); f.bl(0x2030, f.copier)
            }
            val resolver = Gen3CompiledItemNameResolver(f.session())
            val first = resolver.resolve(f.root)
            assertNull(other, first.table)
            assertSame(first, resolver.resolve(f.root))
        }
    }

    @Test
    fun `incomplete or overflowed inventories do not trigger reference free fallback`() {
        val f = ItemConsumerFixture()
        val evidence = GbaTargetReferenceEvidence(f.rootSites.size + 1, f.rootSites,
            f.rootSites.size + 1, 128, null)
        for (index in listOf(GbaReferenceIndex.fromTargets(mapOf(f.root to evidence), 128),
            GbaReferenceIndex.budgetExceeded("synthetic overflow"))) {
            var builds = 0
            val session = RomAnalysisSession(RomImage(f.bytes), RomHeader(Platform.GBA, "SYNTHETIC"),
                gbaReferenceIndexFactory = GbaReferenceIndexFactory { _, _ -> builds++; index })
            val resolver = Gen3CompiledItemNameResolver(session)
            val first = resolver.resolve(f.root)
            assertNull(first.table); assertSame(first, resolver.resolve(f.root)); assertEquals(1, builds)
        }
    }

    @Test
    fun `nomination recovery and BL scans are charged and memoized`() {
        val f = ItemConsumerFixture()
        val index = GbaReferenceIndex.countsOnlyForTesting(mapOf(f.root to f.rootSites.size))
        fun run(limit: Long): Gen3CompiledItemNameResolver.Result {
            val session = RomAnalysisSession(RomImage(f.bytes), RomHeader(Platform.GBA, "SYNTHETIC"),
                limits = ResolutionLimits(maxDatasetExtentBytes = limit),
                gbaReferenceIndexFactory = GbaReferenceIndexFactory { _, _ -> index })
            val resolver = Gen3CompiledItemNameResolver(session)
            val result = resolver.resolve(f.root)
            assertSame(result, resolver.resolve(f.root))
            assertTrue(result.scannedBytes <= limit)
            return result
        }
        assertNull(run(f.bytes.size.toLong() * 2).table)
        val complete = run(f.bytes.size.toLong() * 3)
        assertNotNull(complete.table)
        assertEquals(f.bytes.size.toLong() * 3, complete.scannedBytes)
        val work = Gen3CompiledItemNameResolver(f.session(limits = ResolutionLimits(maxProbeWorkPerDataset = 3))).resolve(f.root)
        assertNull(work.table)
        assertTrue(work.reason.contains("budget"))
    }

    @Test
    fun `truncated record extent and overflowing caller inventory are terminal`() {
        val f = ItemConsumerFixture()
        val truncated = RomImage(f.bytes.copyOf(f.root + 377 * f.stride - 1))
        val truncatedResolver = Gen3CompiledItemNameResolver(RomAnalysisSession(truncated, RomHeader(Platform.GBA, "SYNTHETIC")))
        val extent = truncatedResolver.resolve(f.root)
        assertNull(extent.table)
        assertTrue(extent.reason.contains("extent"))
        assertSame(extent, truncatedResolver.resolve(f.root))

        repeat(129) { f.bl(0x2000 + it * 4, f.nameGetter) }
        val overflowResolver = Gen3CompiledItemNameResolver(f.session())
        val overflow = overflowResolver.resolve(f.root)
        assertNull(overflow.table)
        assertTrue(overflow.reason.contains("budget"))
        assertSame(overflow, overflowResolver.resolve(f.root))
    }

    @Test
    fun `cancellation propagates during nomination and after a cached result`() {
        val f = ItemConsumerFixture()
        var checks = 0
        val cancelled = f.session(cancellation = ParserCancellationToken { if (++checks > 10) throw ParserCancellationException() })
        assertThrows(ParserCancellationException::class.java) { Gen3CompiledItemNameResolver(cancelled).resolve(f.root) }
        var stop = false
        val resolver = Gen3CompiledItemNameResolver(f.session(cancellation = ParserCancellationToken { if (stop) throw ParserCancellationException() }))
        assertNotNull(resolver.resolve(f.root).table)
        stop = true
        assertThrows(ParserCancellationException::class.java) { resolver.resolve(f.root) }
    }
}

/** Hand-assembled synthetic program; symbol addresses and table payload are not ROM assets. */
internal class ItemConsumerFixture(
    val root: Int = 0x4000,
    val sanitizer: Int = 0x600,
    val nameGetter: Int = 0x800,
    val wrapper: Int = 0x1200,
    val copier: Int = 0x1400,
    val maximumHalf: Int = 188,
    val firstShift: Int = 2,
    val finalShift: Int = 3,
    val nameBytes: Int = 10,
    val excluded: Int = 175,
    val scalarStart: Int = 0x900,
    val mulStride: Int? = null,
    val simpleWrapper: Boolean = false,
    val scalarSpacing: Int = 0x40,
) {
    val bytes = ByteArray(0x10000)
    val scalarEntries = mutableListOf<Int>()
    val rootSites = mutableListOf<Int>()
    val stride = mulStride ?: (((1 shl firstShift) + 1) shl finalShift)
    init {
        emitSanitizer(sanitizer, maximumHalf)
        if (mulStride != null) mulPointerGetter(nameGetter) else {
            emit(nameGetter, 0xB500, 0x0400, 0x0C00)
            bl(nameGetter + 6, sanitizer)
            emit(nameGetter + 10, 0x1C01, 0x0409, 0x0C09,
                (firstShift shl 6) or 0x0008, 0x1840, finalShift shl 6, 0x4902,
                0x1840, 0xBC02, 0x4708, 0)
            pointer(nameGetter + 32, root)
            rootSites += nameGetter + 22
        }
        val description = (nameBytes + 9) and -4
        listOf(nameBytes to 2, nameBytes + 2 to 2, nameBytes + 4 to 1, nameBytes + 5 to 1, description to 4,
            description + 4 to 1, description + 5 to 1, description + 6 to 1, description + 7 to 1,
            description + 8 to 4, description + 12 to 1, description + 16 to 4, description + 20 to 1)
            .forEachIndexed { index, (offset, width) ->
                val entry = scalarStart + index * scalarSpacing
                if (mulStride != null) mulScalarGetter(entry, offset, width) else scalar(entry, offset, width)
            }
        if (simpleWrapper) simpleCopyWrapper(wrapper) else dynamicCopyWrapper()
        emit(copier, 0xB500, 0x1C03, 0xE002, 0x701A, 0x3301, 0x3101,
            0x780A, 0x1C10, 0x28FF, 0xD1F8, 0x20FF, 0x7018, 0x1C18, 0xBC02, 0x4708)
        for (id in 0..maximumHalf * 2) {
            bytes[root + id * stride] = 0xBB.toByte() // synthetic English A
            bytes[root + id * stride + 1] = 0xFF.toByte()
            half(root + id * stride + nameBytes, id)
        }
    }

    /** R1 item index; copies one description line to the incoming destination, returning a flag. */
    fun descriptionLineConsumer(entry: Int) {
        emit(entry, 0xB570, 0x1C06, 0x1C08, 0x1C55)
        literalLoad(entry + 8, 4, entry + 60, root)
        emit(entry + 10, 0x0400, 0x0C00)
        bl(entry + 14, sanitizer)
        emit(entry + 18, 0x0400, 0x0C00, 0x2100 or stride, 0x4348,
            0x3400 or ((nameBytes + 9) and -4), 0x1900, 0x6803,
            0x1C32, 0x7819, 0x1C88, 0x0600, 0x0E00, 0x2801, 0xD811,
            0x3D01, 0x2D00, 0xD105, 0x20FF, 0x7010, 0x2001, 0xE00E)
        emit(entry + 64, 0x0608, 0x0E00, 0x28FF, 0xD101, 0x2000, 0xE006,
            0x1C32, 0x3301, 0xE7E7, 0x7011, 0x3301, 0x3201, 0xE7E3,
            0xBC70, 0xBC02, 0x4708)
    }

    /** Synthetic u8-indexed call wrapper; opaque callees are not item-name authority. */
    fun conditionalU8Call(entry: Int, otherRoot: Int, scale: Int = 3) {
        emit(entry, 0xB500, 0x0400, 0x0C00)
        bl(entry + 6, 0x2400)
        emit(entry + 10, 0x0600, 0x0E01)
        literalLoad(entry + 14, 0, entry + 36, 0) // scalar literal, replaced below
        emit(entry + 16, 0x4281, 0xD004, (scale shl 6) or 8)
        literalLoad(entry + 22, 1, entry + 40, otherRoot)
        half(entry + 24, 0x1840)
        bl(entry + 26, 0x2420)
        emit(entry + 30, 0xBC01, 0x4700)
        half(entry + 36, 0x2345); half(entry + 38, 0)
        emit(0x2400, 0x4770); emit(0x2420, 0x4770)
    }

    /** Synthetic category-selected copy wrapper with two owned scalar IDs and writable destinations. */
    fun u8CategoryCopy(entry: Int, otherRoot: Int) {
        emit(entry, 0xB500, 0x0400, 0x0C00)
        bl(entry + 6, 0x2400)
        emit(entry + 10, 0x0600, 0x0E00, 0x2802, 0xD006, 0x2807, 0xD007)
        literalLoad(entry + 22, 1, entry + 28, otherRoot)
        half(entry + 24, 0xE009)
        emit(entry + 32, 0x2031, 0x0040, 0xE000)
        literalLoad(entry + 38, 0, entry + 64, 0)
        half(entry + 64, 99); half(entry + 66, 0)
        bl(entry + 40, nameGetter)
        half(entry + 44, 0x1C01)
        literalLoad(entry + 46, 0, entry + 68, 0)
        bl(entry + 48, copier)
        literalLoad(entry + 52, 0, entry + 72, 0)
        literalLoad(entry + 54, 1, entry + 76, 0)
        bl(entry + 56, 0x2420)
        emit(entry + 60, 0xBC01, 0x4700)
        for ((offset, value) in listOf(68 to 0x02000100, 72 to 0x02000200,
            76 to (0x08000000 + otherRoot + 0x80))) {
            half(entry + offset, value and 0xFFFF); half(entry + offset + 2, value ushr 16)
        }
        emit(0x2400, 0x4770); emit(0x2420, 0x4770)
    }

    fun emitSanitizer(entry: Int, halfMaximum: Int) {
        emit(entry, 0xB500, 0x0400, 0x0C01, 0x2000 or halfMaximum, 0x0040,
            0x4281, 0xD801, 0x1C08, 0xE000, 0x2000, 0xBC02, 0x4708)
    }

    fun mulPointerGetter(entry: Int) {
        emit(entry, 0xB500, 0x0400, 0x0C00)
        bl(entry + 6, sanitizer)
        emit(entry + 10, 0x0400, 0x0C00, 0x2100 or stride, 0x4348)
        literalLoad(entry + 18, 1, entry + 28, root)
        emit(entry + 20, 0x1840, 0xBC02, 0x4708)
        rootSites += entry + 18
    }

    fun mulScalarGetter(entry: Int, offset: Int, width: Int) {
        scalarEntries += entry
        emit(entry, 0xB510, 0x0400, 0x0C00)
        bl(entry + 8, sanitizer)
        emit(entry + 12, 0x0400, 0x0C00, 0x2100 or stride, 0x4348)
        var cursor = entry + 20
        if (width == 4) { half(cursor, 0x3400 or offset); cursor += 2 }
        half(cursor, 0x1900); cursor += 2
        if (width == 1 && offset > 31) { half(cursor, 0x3000 or offset); cursor += 2 }
        val immediate = if (width == 4 || width == 1 && offset > 31) 0 else offset / width
        val load = when (width) { 2 -> 0x8800; 4 -> 0x6800; else -> 0x7800 }
        emit(cursor, load or (immediate shl 6), 0xBC10, 0xBC02, 0x4708)
        literalLoad(entry + 6, 4, (cursor + 11) and -4, root)
        rootSites += entry + 6
    }

    /** Complete synthetic r5-destination wrapper; the equal-ID arm remains opaque/excluded. */
    fun prefixedCopyWrapper(entry: Int = wrapper) {
        bytes.fill(0, entry, entry + 64)
        emit(entry, 0xB530, 0x1C0D, 0x0400, 0x0C00, 0x2800 or excluded, 0xD10F, 0x202B)
        bl(entry + 14, 0x1600)
        half(entry + 18, 0x1C04)
        literalLoad(entry + 20, 1, entry + 40, 0x3000)
        half(entry + 22, 0x1C28)
        bl(entry + 24, copier)
        emit(entry + 28, 0x1C28, 0x1C21)
        bl(entry + 32, 0x1700)
        emit(entry + 36, 0xE008, 0)
        bl(entry + 44, nameGetter)
        emit(entry + 48, 0x1C01, 0x1C28)
        bl(entry + 52, copier)
        emit(entry + 56, 0xBC30, 0xBC01, 0x4700)
    }

    fun simpleCopyWrapper(entry: Int) {
        emit(entry, 0xB510, 0x1C0C, 0x0400, 0x0C00)
        bl(entry + 8, nameGetter)
        emit(entry + 12, 0x1C01, 0x1C20)
        bl(entry + 16, copier)
        emit(entry + 20, 0xBC10, 0xBC01, 0x4700)
    }

    fun literalLoad(site: Int, register: Int, slot: Int, target: Int) {
        val pc = (site + 4) and -4
        require(slot and 3 == 0 && slot >= pc && slot - pc <= 1020)
        half(site, 0x4800 or (register shl 8) or ((slot - pc) / 4))
        pointer(slot, target)
    }

    private fun dynamicCopyWrapper() {
        // The unequal branch skips a synthetic dynamic block. Its callees are deliberately unproved.
        emit(wrapper, 0xB510, 0x1C0C, 0x0400, 0x0C00, 0x2800 or excluded, 0xD10D)
        emit(wrapper + 12, 0x202B)
        bl(wrapper + 14, 0x1600)
        emit(wrapper + 18, 0x1C01, 0x1C20)
        bl(wrapper + 22, copier)
        emit(wrapper + 26, 0x4902, 0x1C20)
        bl(wrapper + 30, 0x1700)
        emit(wrapper + 34, 0xE007)
        pointer(wrapper + 36, 0x3000)
        bl(wrapper + 40, nameGetter)
        emit(wrapper + 44, 0x1C01, 0x1C20)
        bl(wrapper + 48, copier)
        emit(wrapper + 52, 0xBC10, 0xBC01, 0x4700)
    }

    fun scalar(entry: Int, offset: Int, width: Int) {
        scalarEntries += entry
        emit(entry, 0xB510, 0x0400, 0x0C00)
        bl(entry + 8, sanitizer)
        emit(entry + 12, 0x0400, 0x0C00, (firstShift shl 6) or 1, 0x1809,
            (finalShift shl 6) or 9)
        var cursor = entry + 22
        if (width == 4) { half(cursor, 0x3400 or offset); cursor += 2 }
        half(cursor, 0x1909); cursor += 2
        if (width == 1 && offset > 31) { half(cursor, 0x3100 or offset); cursor += 2 }
        val load = when (width) {
            2 -> 0x8808 or ((offset / 2) shl 6)
            4 -> 0x6808
            else -> 0x7808 or ((if (offset > 31) 0 else offset) shl 6)
        }
        emit(cursor, load, 0xBC10, 0xBC02, 0x4708)
        val literal = (cursor + 8 + 3) and -4
        half(entry + 6, 0x4C00 or ((literal - ((entry + 10) and -4)) / 4))
        pointer(literal, root)
        rootSites += entry + 6
    }

    fun session(limits: ResolutionLimits = ResolutionLimits(), cancellation: ParserCancellationToken = ParserCancellationToken.NONE) =
        RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBA, "SYNTHETIC"), limits = limits, cancellation = cancellation)
    fun emit(at: Int, vararg words: Int) = words.forEachIndexed { i, word -> half(at + i * 2, word) }
    fun half(at: Int, word: Int) { bytes[at] = word.toByte(); bytes[at + 1] = (word ushr 8).toByte() }
    fun pointer(at: Int, target: Int) {
        val value = target + 0x08000000
        repeat(4) { bytes[at + it] = (value ushr (8 * it)).toByte() }
    }
    fun bl(at: Int, target: Int) {
        val delta = target - at - 4
        emit(at, 0xF000 or ((delta shr 12) and 0x7FF), 0xF800 or ((delta shr 1) and 0x7FF))
    }
}
