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
) {
    val bytes = ByteArray(0x10000)
    val scalarEntries = mutableListOf<Int>()
    val rootSites = mutableListOf<Int>()
    val stride = ((1 shl firstShift) + 1) shl finalShift
    init {
        emit(sanitizer, 0xB500, 0x0400, 0x0C01, 0x2000 or maximumHalf, 0x0040,
            0x4281, 0xD801, 0x1C08, 0xE000, 0x2000, 0xBC02, 0x4708)
        emit(nameGetter, 0xB500, 0x0400, 0x0C00)
        bl(nameGetter + 6, sanitizer)
        emit(nameGetter + 10, 0x1C01, 0x0409, 0x0C09,
            (firstShift shl 6) or 0x0008, 0x1840, finalShift shl 6, 0x4902,
            0x1840, 0xBC02, 0x4708, 0)
        pointer(nameGetter + 32, root)
        rootSites += nameGetter + 22
        listOf(nameBytes to 2, nameBytes + 2 to 2, nameBytes + 4 to 1, nameBytes + 5 to 1, 16 to 4,
            20 to 1, 21 to 1, 22 to 1, 23 to 1, 24 to 4, 28 to 1, 32 to 4, 36 to 1)
            .forEachIndexed { index, (offset, width) -> scalar(scalarStart + index * 0x40, offset, width) }
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
        emit(copier, 0xB500, 0x1C03, 0xE002, 0x701A, 0x3301, 0x3101,
            0x780A, 0x1C10, 0x28FF, 0xD1F8, 0x20FF, 0x7018, 0x1C18, 0xBC02, 0x4708)
        for (id in 0..maximumHalf * 2) {
            bytes[root + id * stride] = 0xBB.toByte() // synthetic English A
            bytes[root + id * stride + 1] = 0xFF.toByte()
            half(root + id * stride + nameBytes, id)
        }
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
