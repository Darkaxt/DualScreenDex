package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.model.GbaItemNameAuthority
import com.enrpau.dualscreendex.parser.model.GbaItemPublishedRoute
import org.junit.Assert.*
import org.junit.Test

/** Generated complete guarded byte consumers; dispatch values and RAM addresses have no claimed semantics. */
class Gen3GuardedItemAuxiliaryTest {
    private data class Fixture(val f: ItemConsumerFixture, val entry: Int, val count: Int,
                               val table: Int, val zero: Int, val ordinary: Int, val rootPool: Int)

    private fun fixture(root: Int = 0x6000, width: Int = 14, first: Int = 3, last: Int = 3,
                        entry: Int = 0x2000, count: Int = 22): Fixture {
        val f = ItemConsumerFixture(root = root, nameBytes = width, firstShift = first, finalShift = last,
            simpleWrapper = true)
        val table = entry + 60
        val arms = table + count * 4
        val zero = arms + 16
        val ordinary = zero + 4
        val rootPool = ordinary + 20
        fun branch(at: Int, target: Int, conditional: Int? = null) {
            val delta = (target - at - 4) / 2
            f.half(at, if (conditional == null) 0xE000 or (delta and 0x7FF)
                else 0xD000 or (conditional shl 8) or (delta and 255))
        }
        f.emit(entry, 0xB500, 0x0400, 0x0C00)
        f.bl(entry + 6, f.sanitizer)
        f.emit(entry + 10, 0x0400, 0x0C02)
        f.literalLoad(entry + 14, 0, entry + 48, 0)
        f.half(entry + 48, 175); f.half(entry + 50, 0)
        f.half(entry + 16, 0x4282); branch(entry + 18, ordinary, 1)
        f.literalLoad(entry + 20, 0, entry + 52, 0)
        f.half(entry + 52, 0x0100); f.half(entry + 54, 0x0200)
        f.half(entry + 22, 0x8800); f.bl(entry + 24, 0x3800)
        f.emit(entry + 28, 0x0600, 0x0E00, 0x2800 or (count - 1))
        branch(entry + 34, zero, 8)
        f.half(entry + 36, 0x0080)
        f.literalLoad(entry + 38, 1, entry + 56, table)
        f.emit(entry + 40, 0x1840, 0x6800, 0x4687, 0)
        for (id in 0 until count) f.pointer(table + id * 4, arms + (id % 5) * 4)
        for ((id, value) in listOf(7, 3, 9, 18, 0).withIndex()) {
            f.half(arms + id * 4, 0x2000 or value)
            branch(arms + id * 4 + 2, ordinary + 14)
        }
        f.literalLoad(ordinary, 0, rootPool, root)
        f.emit(ordinary + 2, (first shl 6) or 0x11, 0x1889, (last shl 6) or 9,
            0x1809, 0x3100 or (((width + 9) and -4) + 12), 0x7808, 0xBC02, 0x4708, 0)
        f.half(0x3800, 0x4770)
        return Fixture(f, entry, count, table, zero, ordinary, rootPool)
    }

    @Test(timeout = 60_000)
    fun completeRelocatedGuardedDispatchReconcilesOrdinaryByteWithoutNameSemantics() {
        for (case in listOf(listOf(0x6000, 14, 3, 3, 0x2000, 22),
            listOf(0x7000, 10, 2, 3, 0x2800, 17), listOf(0x9000, 10, 1, 4, 0x3200, 9))) {
            val x = fixture(case[0], case[1], case[2], case[3], case[4], case[5])
            val resolver = x.f.session().itemNameResolver
            val result = resolver.resolve(x.f.root)
            assertEquals("complete guarded non-name consumer: ${result.reason}",
                Gen3CompiledItemNameResolver.Table(x.f.root, x.f.stride, 377, x.f.nameBytes, emptySet()), result.table)
            assertTrue(resolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Available)
        }
    }

    @Test(timeout = 60_000)
    fun projectionUnsignedGuardFrameAndEveryDispatchDependencyStayClosed() {
        for (offset in listOf(0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26,
            28, 30, 32, 34, 36, 38, 40, 42, 44, 46)) {
            val x = fixture(); x.f.half(x.entry + offset, 0x46C0)
            assertNull("guarded dependency+$offset", x.f.session().itemNameResolver.resolve(x.f.root).table)
        }
        for (offset in 0..18 step 2) {
            val x = fixture()
            // Keep the root nomination while damaging its required destination register.
            x.f.half(x.ordinary + offset, if (offset == 0) 0x4904 else 0x46C0)
            assertNull("ordinary dependency+$offset", x.f.session().itemNameResolver.resolve(x.f.root).table)
        }
        for (id in 0 until 5) for (offset in listOf(0, 2)) {
            val x = fixture(); x.f.half(x.table + x.count * 4 + id * 4 + offset, 0x46C0)
            assertNull("constant arm=$id dependency+$offset", x.f.session().itemNameResolver.resolve(x.f.root).table)
        }
    }

    @Test(timeout = 60_000)
    fun dataCellsCannotExecuteEnterOrdinaryPathEscapeOrChangeThumbAlignment() {
        for (target in listOf(0x203C, 0x202C, 0x20A8, 0x3800, 0x2095)) {
            val x = fixture(); x.f.pointer(x.table + 12, target)
            assertNull("dispatch target ${target.toString(16)}", x.f.session().itemNameResolver.resolve(x.f.root).table)
        }
        val odd = fixture()
        odd.f.half(odd.table + 8, (odd.table + odd.count * 4) or 1)
        assertNull("raw target low bit cannot be normalized away", odd.f.session().itemNameResolver.resolve(odd.f.root).table)
    }

    @Test(timeout = 60_000)
    fun everyOwnedLiteralAndDispatchCellRejectsSupportedDirectIncomingEdges() {
        val sample = fixture()
        val pools = listOf(sample.entry + 48, sample.entry + 52, sample.entry + 56, sample.rootPool) +
            (0 until sample.count).map { sample.table + it * 4 }
        for (pool in pools) for (offset in listOf(0, 2)) for (kind in listOf("BL", "B", "BNE")) {
            val x = fixture()
            val at = if (pool < x.table) x.entry - 64 else pool - 192
            val delta = (pool + offset - at - 4) / 2
            when (kind) {
                "BL" -> x.f.bl(at, pool + offset)
                "B" -> x.f.half(at, 0xE000 or (delta and 0x7FF))
                else -> x.f.half(at, 0xD100 or (delta and 255))
            }
            assertNull("$kind enters owned data ${pool.toString(16)}+$offset",
                x.f.session().itemNameResolver.resolve(x.f.root).table)
        }
    }

    @Test(timeout = 60_000)
    fun rootLiteralGuardsDoNotDependOnLiteralBytesNominatingAnInstructionSite() {
        for (scalar in listOf(false, true)) for (offset in listOf(0, 2)) for (kind in listOf("BL", "B", "BEQ")) {
            val f = ItemConsumerFixture(root = 0x6000, firstShift = 3, finalShift = 3,
                nameBytes = 14, simpleWrapper = true)
            assertTrue(f.session().itemNameResolver.original(GbaItemPublishedRoute.NotInvoked)
                is GbaItemNameAuthority.Available)
            val site = if (scalar) f.scalarEntries.first() + 6 else f.nameGetter + 22
            val op = (f.bytes[site].toInt() and 255) or ((f.bytes[site + 1].toInt() and 255) shl 8)
            val pool = ((site + 4) and -4) + (op and 255) * 4
            val at = (if (scalar) f.scalarEntries.first() else f.nameGetter) - 64
            val delta = (pool + offset - at - 4) / 2
            when (kind) {
                "BL" -> f.bl(at, pool + offset)
                "B" -> f.half(at, 0xE000 or (delta and 0x7FF))
                else -> f.half(at, 0xD000 or (delta and 255))
            }
            assertNull("$kind enters owned root literal scalar=$scalar+$offset without a data-site nomination",
                f.session().itemNameResolver.resolve(f.root).table)
        }
    }

    @Test(timeout = 60_000)
    fun internalDescriptionRootLiteralAlsoNeedsIncomingGuardsWithoutDataSiteNomination() {
        for (offset in listOf(0, 2)) for (kind in listOf("BL", "B", "BEQ")) {
            val f = ItemConsumerFixture(root = 0x6000, mulStride = 44, nameBytes = 14, simpleWrapper = true)
            f.descriptionLineConsumer(0x2000)
            assertTrue(f.session().itemNameResolver.original(GbaItemPublishedRoute.NotInvoked)
                is GbaItemNameAuthority.Available)
            val at = 0x1FC0; val target = 0x203C + offset
            val delta = (target - at - 4) / 2
            when (kind) {
                "BL" -> f.bl(at, target)
                "B" -> f.half(at, 0xE000 or (delta and 0x7FF))
                else -> f.half(at, 0xD000 or (delta and 255))
            }
            assertNull("$kind enters complete description consumer's internal root literal+$offset",
                f.session().itemNameResolver.resolve(f.root).table)
        }
    }

    @Test(timeout = 60_000)
    fun aPositivelyOwnedRootLiteralIsDataNotAnIncomingBranchSource() {
        // Low literal halfword E07C resembles B into the first scalar pool at920.
        // Neither halfword is executable: the getter returns across its owned word.
        val f = ItemConsumerFixture(root = 0xE07C, nameGetter = 0x804, maximumHalf = 16,
            firstShift = 3, finalShift = 3, nameBytes = 14, simpleWrapper = true)
        val proof = f.session().itemNameResolver.resolve(f.root)
        assertEquals("complete owned data must not invent a branch: ${proof.reason}",
            Gen3CompiledItemNameResolver.Table(f.root, f.stride, 33, 14, emptySet()), proof.table)
    }

    @Test(timeout = 60_000)
    fun scalarGeometrySpecialDomainWritableHalfwordAndOpaqueCallOwnershipCannotDrift() {
        val mutations = listOf<(Fixture) -> Unit>(
            { x -> x.f.half(x.entry + 48, 377) },
            { x -> x.f.half(x.entry + 50, 1) },
            { x -> x.f.half(x.entry + 54, 0x0800) },
            { x -> x.f.half(x.entry + 52, 0x0101) },
            { x -> x.f.bl(x.entry + 24, x.table) },
            { x -> x.f.bl(x.entry + 6, x.ordinary) },
            { x -> x.f.half(x.ordinary + 2, 0x0051) },
            { x -> x.f.half(x.ordinary + 10, 0x3121) },
            { x -> x.f.emitSanitizer(0x3900, 187); x.f.bl(x.entry + 6, 0x3900) })
        for (mutate in mutations) {
            val x = fixture(); mutate(x)
            assertNull("conflicting auxiliary role/geometry", x.f.session().itemNameResolver.resolve(x.f.root).table)
        }
    }
}
