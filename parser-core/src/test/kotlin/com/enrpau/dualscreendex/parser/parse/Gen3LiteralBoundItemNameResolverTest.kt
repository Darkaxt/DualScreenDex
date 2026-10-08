package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.GbaItemNameAuthority
import com.enrpau.dualscreendex.parser.model.GbaItemPublishedRoute
import org.junit.Assert.*
import org.junit.Test

/** Hand-assembled complete consumers; no ROM payloads, identities or fixed ROM addresses. */
class Gen3LiteralBoundItemNameResolverTest {
    private fun literalBound(f: ItemConsumerFixture, maximum: Int, entry: Int = f.sanitizer) {
        f.bytes.fill(0, entry, entry + 32)
        f.emit(entry, 0xB500, 0x0400, 0x0C01, 0x4802, 0x4281, 0xD803, 0x1C08, 0xE002)
        f.half(entry + 16, maximum and 0xFFFF)
        f.half(entry + 18, maximum ushr 16)
        f.emit(entry + 20, 0x2000, 0xBC02, 0x4708, 0)
    }

    @Test(timeout = 60_000)
    fun completeLiteralBoundLeafDerivesRelocatedDomainWithoutReadingNames() {
        for ((stride, maximum, width) in listOf(Triple(44, 798, 14), Triple(52, 513, 18), Triple(40, 127, 10))) {
            val f = ItemConsumerFixture(root = 0x6000, sanitizer = 0x1800, nameGetter = 0x2000,
                scalarStart = 0x2200, wrapper = 0x3000, copier = 0x3200,
                mulStride = stride, nameBytes = width, simpleWrapper = true)
            literalBound(f, maximum)
            f.bytes.fill(0xFC.toByte(), f.root, f.root + (maximum + 1) * stride)
            val resolver = f.session().itemNameResolver
            val result = resolver.resolve(f.root)
            assertEquals("complete literal-bound leaf: ${result.reason}",
                Gen3CompiledItemNameResolver.Table(f.root, stride, maximum + 1, width, emptySet()), result.table)
            val original = resolver.original(GbaItemPublishedRoute.NotInvoked)
            assertTrue("$original", original is GbaItemNameAuthority.Available)
            assertEquals(maximum + 1, (original as GbaItemNameAuthority.Available).count)
            assertSame(original, resolver.original(GbaItemPublishedRoute.NotInvoked))
        }
    }

    @Test(timeout = 60_000)
    fun literalBoundEveryInstructionAndUnsignedDomainDependencyStaysClosed() {
        for (offset in listOf(0, 2, 4, 6, 8, 10, 12, 14, 20, 22, 24)) {
            val f = ItemConsumerFixture(mulStride = 44, nameBytes = 14, simpleWrapper = true)
            literalBound(f, 511)
            f.half(f.sanitizer + offset, 0)
            assertNull("missing literal sanitizer instruction +$offset", f.session().itemNameResolver.resolve(f.root).table)
        }
        for (maximum in listOf(65536, Int.MAX_VALUE, -1)) {
            val f = ItemConsumerFixture(mulStride = 44, nameBytes = 14, simpleWrapper = true)
            literalBound(f, maximum)
            assertNull("out-of-u16 bound $maximum", f.session().itemNameResolver.resolve(f.root).table)
        }
    }

    @Test(timeout = 60_000)
    fun literalBoundCannotWidenMissingNumericFieldOrAuthorizeDynamicId() {
        val missing = ItemConsumerFixture(mulStride = 44, nameBytes = 14, simpleWrapper = true)
        literalBound(missing, 511)
        missing.bytes.fill(0, missing.scalarStart, missing.scalarStart + 0x40)
        assertNull(missing.session().itemNameResolver.resolve(missing.root).table)
        val dynamic = ItemConsumerFixture(mulStride = 44, nameBytes = 14)
        literalBound(dynamic, 511)
        val proof = dynamic.session().itemNameResolver.resolve(dynamic.root)
        assertEquals("complete ordinary branch: ${proof.reason}", setOf(dynamic.excluded), proof.table?.excludedIds)
    }

    @Test(timeout = 60_000)
    fun literalPoolCannotBeEnteredByAnySupportedDirectEdge() {
        for (kind in listOf("BL", "B", "BEQ")) for (halfword in listOf(0, 2)) {
            val f = ItemConsumerFixture(mulStride = 44, nameBytes = 14, simpleWrapper = true)
            literalBound(f, 511)
            val source = f.sanitizer - 32
            val target = f.sanitizer + 16 + halfword
            val delta = (target - source - 4) / 2
            when (kind) {
                "BL" -> f.bl(source, target)
                "B" -> f.half(source, 0xE000 or (delta and 0x7FF))
                "BEQ" -> f.half(source, 0xD000 or (delta and 255))
            }
            val resolver = f.session().itemNameResolver
            assertNull("$kind enters sanitizer data+$halfword", resolver.resolve(f.root).table)
            assertTrue(resolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Unavailable)
        }
    }

    @Test(timeout = 60_000)
    fun auxiliaryDescriptionSanitizerPoolIsInventoriedBeforeBatchCallerScan() {
        for (halfword in listOf(0, 2)) {
            val f = ItemConsumerFixture(mulStride = 44, nameBytes = 14, simpleWrapper = true)
            literalBound(f, 511)
            f.descriptionLineConsumer(0x2000)
            literalBound(f, 511, 0x2800)
            f.bl(0x200E, 0x2800)
            assertTrue("complete distinct auxiliary leaf", f.session().itemNameResolver
                .original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Available)
            f.bl(0x3000, 0x2810 + halfword)
            assertTrue("incoming auxiliary literal+$halfword", f.session().itemNameResolver
                .original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Unavailable)
        }
    }

    @Test(timeout = 60_000)
    fun literalOwnershipBranchesRegistersAndCountsCannotDrift() {
        val mutations = listOf<Pair<String, (ItemConsumerFixture) -> Unit>>(
            "escaped literal" to { f ->
                f.half(f.sanitizer + 6, 0x4806)
                f.half(f.sanitizer + 32, 511); f.half(f.sanitizer + 34, 0)
            },
            "wrong literal register" to { f -> f.half(f.sanitizer + 6, 0x4902) },
            "signed/equal bound guard" to { f -> f.half(f.sanitizer + 10, 0xD003) },
            "selected branch enters data" to { f -> f.half(f.sanitizer + 14, 0xE7FF) },
            "bound branch enters data" to { f -> f.half(f.sanitizer + 10, 0xD801) },
            "scalar count conflict" to { f ->
                literalBound(f, 510, 0x2800); f.bl(f.scalarStart + 8, 0x2800)
            },
        )
        for ((label, mutate) in mutations) {
            val f = ItemConsumerFixture(mulStride = 44, nameBytes = 14, simpleWrapper = true)
            literalBound(f, 511); mutate(f)
            assertNull(label, f.session().itemNameResolver.resolve(f.root).table)
        }
    }

    @Test(timeout = 60_000)
    fun fullUnsignedDomainAndExactPhysicalExtentRemainRequired() {
        for (maximum in listOf(0, 511, 65535)) {
            val f = ItemConsumerFixture(mulStride = 44, nameBytes = 14, simpleWrapper = true)
            literalBound(f, maximum)
            val end = f.root + (maximum + 1) * f.stride
            for (delta in listOf(-1, 0)) {
                val bytes = f.bytes.copyOf(maxOf(end + delta, f.root))
                val resolver = RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBA, "SYNTHETIC"))
                    .itemNameResolver
                val result = resolver.resolve(f.root)
                assertEquals("maximum=$maximum extent delta=$delta: ${result.reason}", delta == 0, result.table != null)
                if (delta == 0) assertEquals(maximum + 1, result.table?.count)
            }
        }
    }

    @Test(timeout = 60_000)
    fun literalLeafPreservesWorkBudgetsMemoizationAndCancellation() {
        for (passes in listOf(1, 2)) {
            val f = ItemConsumerFixture(mulStride = 44, nameBytes = 14, simpleWrapper = true)
            literalBound(f, 511)
            val resolver = f.session(ResolutionLimits(maxDatasetExtentBytes = f.bytes.size.toLong() * passes))
                .itemNameResolver
            val result = resolver.original(GbaItemPublishedRoute.NotInvoked)
            assertEquals("full scan budget=$passes", passes == 2, result is GbaItemNameAuthority.Available)
            assertSame(result, resolver.original(GbaItemPublishedRoute.NotInvoked))
        }
        val f = ItemConsumerFixture(mulStride = 44, nameBytes = 14, simpleWrapper = true)
        literalBound(f, 511)
        assertNull(f.session(ResolutionLimits(maxProbeWorkPerDataset = 8)).itemNameResolver.resolve(f.root).table)
        var stopped = false
        val resolver = f.session(cancellation = ParserCancellationToken {
            if (stopped) throw ParserCancellationException()
        }).itemNameResolver
        assertNotNull(resolver.resolve(f.root).table)
        stopped = true
        assertThrows(ParserCancellationException::class.java) { resolver.resolve(f.root) }
        assertThrows(ParserCancellationException::class.java) { resolver.original(GbaItemPublishedRoute.NotInvoked) }
    }
}
