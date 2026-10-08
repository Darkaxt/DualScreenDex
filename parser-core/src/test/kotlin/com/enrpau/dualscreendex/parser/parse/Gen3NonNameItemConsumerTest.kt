package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.model.GbaItemNameAuthority
import com.enrpau.dualscreendex.parser.model.GbaItemPublishedRoute
import org.junit.Assert.*
import org.junit.Test

/** Complete synthetic numeric-return consumers; opaque category helpers are not item-name semantics. */
class Gen3NonNameItemConsumerTest {
    private fun halfword(entry: Int = 0x2000, root: Int = 0x6000, first: Int = 3, last: Int = 3,
                         nameWidth: Int = 14, category: Int = 3): ItemConsumerFixture {
        val f = ItemConsumerFixture(root = root, nameBytes = nameWidth, firstShift = first,
            finalShift = last, simpleWrapper = true)
        f.emit(entry, 0xB510, 0x0400, 0x0C04, 0x1C20)
        f.bl(entry + 8, 0x3800)
        f.emit(entry + 12, 0x0600, 0x0E00, 0x2800 or category, 0xD109)
        f.literalLoad(entry + 20, 1, entry + 36, root)
        f.emit(entry + 22, (first shl 6) or 0x20, 0x1900, last shl 6, 0x1840,
            0x8800 or (((((nameWidth + 9) and -4) + 14) / 2) shl 6), 0xE003, 0)
        f.emit(entry + 40, 0x2000, 0xBC10, 0xBC02, 0x4708)
        f.half(0x3800, 0x4770)
        return f
    }

    private fun byteOrigin(entry: Int = 0x2000, root: Int = 0x6000, first: Int = 3, last: Int = 3,
                           nameWidth: Int = 14): ItemConsumerFixture {
        val f = ItemConsumerFixture(root = root, nameBytes = nameWidth, firstShift = first,
            finalShift = last, simpleWrapper = true)
        f.emit(entry, 0xB5F0, 0x4657, 0x464E, 0x4645, 0xB4E0, 0xB082, 0x4680)
        f.bl(entry + 14, 0x3800)
        f.emit(entry + 18, 0x1C06, 0x9000, 0x9601)
        val site = entry + 24
        f.literalLoad(site, 4, entry + 60, root)
        f.emit(site + 2, 0x1C30, 0x210C)
        f.bl(site + 6, 0x3800)
        f.emit(site + 10, (first shl 6) or 1, 0x1809, (last shl 6) or 9, 0x1909,
            0x780C or ((nameWidth + 2) shl 6), 0x2C07, 0xD003, 0x2109)
        f.bl(entry + 50, 0x3800)
        f.half(entry + 54, 0xE003)
        f.emit(entry + 56, 0x2000, 0xE001)
        f.emit(entry + 64, 0xB002, 0xBC38, 0x4698, 0x46A1, 0x46AA, 0xBCF0, 0xBC02, 0x4708)
        f.half(0x3800, 0x4770)
        return f
    }

    @Test(timeout = 60_000)
    fun completeRelocatedByteOriginHasABalancedFrameAndNoRootEscape() {
        for (case in listOf(listOf(0x2000, 0x6000, 3, 3, 14),
            listOf(0x2800, 0x7000, 2, 3, 10), listOf(0x3200, 0x9000, 1, 4, 10))) {
            val f = byteOrigin(case[0], case[1], case[2], case[3], case[4])
            val resolver = f.session().itemNameResolver
            val proof = resolver.resolve(f.root)
            assertEquals("complete byte-origin disposition: ${proof.reason}",
                Gen3CompiledItemNameResolver.Table(f.root, f.stride, 377, f.nameBytes, emptySet()), proof.table)
            assertTrue(resolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Available)
        }
    }

    @Test(timeout = 60_000)
    fun byteFramePreservationIndexGeometryAndPhysicalReadCannotDrift() {
        for (offset in listOf(0, 2, 4, 6, 8, 10) + (24..42 step 2) + (64..78 step 2)) {
            val f = byteOrigin(); f.half(0x2000 + offset, if (offset == 24) 0x4B08 else 0x46C0)
            assertNull("byte frame/index dependency+$offset", f.session().itemNameResolver.resolve(f.root).table)
        }
        for (op in listOf(0x7C04, 0x7C14, 0x7BCC, 0x8C0C, 0x680C)) {
            val f = byteOrigin(); f.half(0x202A, op)
            assertNull("byte consumer physical field/register/width", f.session().itemNameResolver.resolve(f.root).table)
        }
        val stride = byteOrigin(); stride.half(0x2022, 0x0081)
        assertNull("byte stride must match complete getters", stride.session().itemNameResolver.resolve(stride.root).table)
        val missing = byteOrigin(); missing.half(missing.scalarEntries.first(), 0x46C0)
        assertNull("byte disposition cannot replace a scalar", missing.session().itemNameResolver.resolve(missing.root).table)
    }

    @Test(timeout = 60_000)
    fun byteOriginCannotEscapeThroughArgumentsStoresComparisonsOrReturnRegisters() {
        for (op in listOf(0x1C08, 0x6001, 0x2900, 0xB402, 0x9100, 0x468C)) {
            val f = byteOrigin(); f.half(0x2038, op)
            assertNull("live byte root alias cannot escape", f.session().itemNameResolver.resolve(f.root).table)
        }
        val argument = byteOrigin(); argument.bl(0x2038, 0x3800)
        assertNull("live r1 origin cannot reach an opaque call", argument.session().itemNameResolver.resolve(argument.root).table)
        for (target in listOf(0x2000, 0x2018, 0x203C, 0x2040, 0x204E)) {
            val f = byteOrigin(); f.bl(0x201E, target)
            assertNull("opaque call cannot enter complete owned frame", f.session().itemNameResolver.resolve(f.root).table)
        }
    }

    @Test(timeout = 60_000)
    fun byteControlFlowCannotEnterTheIndexChainLiteralOrAnUnbalancedFrame() {
        for (target in listOf(0x201A, 0x2022, 0x2028, 0x203C, 0x203E, 0x2000, 0x2400)) {
            val f = byteOrigin(); val at = 0x203A
            f.half(at, 0xE000 or (((target - at - 4) / 2) and 0x7FF))
            assertNull("byte branch cannot bypass ownership", f.session().itemNameResolver.resolve(f.root).table)
        }
        for (offset in listOf(0, 2)) for (kind in listOf("BL", "B", "BNE")) {
            val f = byteOrigin(); val at = 0x1FC0; val target = 0x203C + offset
            val delta = (target - at - 4) / 2
            when (kind) {
                "BL" -> f.bl(at, target)
                "B" -> f.half(at, 0xE000 or (delta and 0x7FF))
                else -> f.half(at, 0xD100 or (delta and 255))
            }
            assertNull("$kind enters byte consumer literal+$offset", f.session().itemNameResolver.resolve(f.root).table)
        }
        val unknown = byteOrigin(); unknown.literalLoad(0x3400, 0, 0x3500, unknown.root)
        assertNull("unknown extra root remains terminal", unknown.session().itemNameResolver.resolve(unknown.root).table)
    }

    @Test(timeout = 60_000)
    fun allByteFrameLiteralsAreOwnedWithoutInventingExecutableData() {
        fun fixture(): ItemConsumerFixture {
            val f = byteOrigin()
            f.literalLoad(0x2014, 0, 0x2040, 0)
            f.emit(0x2040, 0xE07C, 0xE07C)
            f.half(0x2036, 0xE005); f.half(0x203A, 0xE003)
            f.emit(0x2044, 0xB002, 0xBC38, 0x4698, 0x46A1, 0x46AA, 0xBCF0, 0xBC02, 0x4708)
            return f
        }
        val complete = fixture()
        assertNotNull("complete second literal remains data", complete.session().itemNameResolver.resolve(complete.root).table)
        for (pool in listOf(0x203C, 0x2040)) for (offset in listOf(0, 2)) for (kind in listOf("BL", "B", "BNE")) {
            val f = fixture(); val at = 0x1FC0; val target = pool + offset
            val delta = (target - at - 4) / 2
            when (kind) {
                "BL" -> f.bl(at, target)
                "B" -> f.half(at, 0xE000 or (delta and 0x7FF))
                else -> f.half(at, 0xD100 or (delta and 255))
            }
            assertNull("$kind enters complete byte-frame data+$offset", f.session().itemNameResolver.resolve(f.root).table)
        }
    }

    @Test(timeout = 60_000)
    fun byteLocalCyclesDoNotCountAsCompleteOriginClosure() {
        for (opcode in listOf(0xE7FE, 0xD0FE, 0xD1FE)) {
            val f = byteOrigin(); f.half(0x2038, opcode)
            assertNull("a local cycle cannot strand the live origin", f.session().itemNameResolver.resolve(f.root).table)
        }
    }

    @Test(timeout = 60_000)
    fun byteFrameWorkRemainsBoundedMemoizedAndCancellationAware() {
        val f = byteOrigin()
        assertNull(f.session(ResolutionLimits(maxProbeWorkPerDataset = 16)).itemNameResolver.resolve(f.root).table)
        val bounded = f.session(ResolutionLimits(maxDatasetExtentBytes = f.bytes.size.toLong() * 2)).itemNameResolver
        val authority = bounded.original(GbaItemPublishedRoute.NotInvoked)
        assertTrue("no scan-budget increase", authority is GbaItemNameAuthority.Available)
        assertSame(authority, bounded.original(GbaItemPublishedRoute.NotInvoked))
        var cancelled = false
        val resolver = f.session(cancellation = ParserCancellationToken {
            if (cancelled) throw ParserCancellationException()
        }).itemNameResolver
        assertNotNull(resolver.resolve(f.root).table)
        cancelled = true
        assertThrows(ParserCancellationException::class.java) { resolver.resolve(f.root) }
        assertThrows(ParserCancellationException::class.java) { resolver.original(GbaItemPublishedRoute.NotInvoked) }
    }

    @Test(timeout = 60_000)
    fun completeRelocatedHalfwordReturnHasNoNamePointerOrExpandedDomain() {
        for (case in listOf(listOf(0x2000, 0x6000, 3, 3, 14, 3),
            listOf(0x2800, 0x7000, 2, 3, 10, 7), listOf(0x3200, 0x9000, 1, 4, 10, 2))) {
            val f = halfword(case[0], case[1], case[2], case[3], case[4], case[5])
            val resolver = f.session().itemNameResolver
            val proof = resolver.resolve(f.root)
            assertEquals("complete numeric-return disposition: ${proof.reason}",
                Gen3CompiledItemNameResolver.Table(f.root, f.stride, 377, f.nameBytes, emptySet()), proof.table)
            assertTrue(resolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Available)
        }
    }

    @Test(timeout = 60_000)
    fun categoryProjectionBranchesNumericLoadAndBalancedReturnsRemainComplete() {
        for (offset in (0..34 step 2) + (40..46 step 2)) {
            val f = halfword()
            f.half(0x2000 + offset, if (offset == 20) 0x4803 else 0x46C0)
            assertNull("numeric consumer dependency+$offset", f.session().itemNameResolver.resolve(f.root).table)
        }
        for (op in listOf(0x8C00, 0x7C40, 0x6840)) {
            val f = halfword(); f.half(0x201E, op)
            assertNull("wrong physical field/load width", f.session().itemNameResolver.resolve(f.root).table)
        }
    }

    @Test(timeout = 60_000)
    fun numericRootLiteralCannotBeEnteredOrReturnedAsTheFieldValue() {
        for (offset in listOf(-2, 0, 2)) for (kind in listOf("BL", "B", "BNE")) {
            val f = halfword(); val at = 0x1FC0; val target = 0x2024 + offset
            val delta = (target - at - 4) / 2
            when (kind) {
                "BL" -> f.bl(at, target)
                "B" -> f.half(at, 0xE000 or (delta and 0x7FF))
                else -> f.half(at, 0xD100 or (delta and 255))
            }
            assertNull("$kind enters numeric-return data+$offset", f.session().itemNameResolver.resolve(f.root).table)
        }
        val pointer = halfword(); pointer.half(0x201E, 0x1C08)
        assertNull("root pointer cannot replace scalar load", pointer.session().itemNameResolver.resolve(pointer.root).table)
    }

    @Test(timeout = 60_000)
    fun numericDispositionCannotReplaceMandatoryFieldsOrDischargeUnknownWitnesses() {
        val missing = halfword(); missing.half(missing.scalarEntries.first(), 0x46C0)
        assertNull("numeric disposition cannot replace a scalar", missing.session().itemNameResolver.resolve(missing.root).table)
        val unknown = halfword(); unknown.literalLoad(0x3400, 0, 0x3500, unknown.root)
        assertNull("unknown root witness remains terminal", unknown.session().itemNameResolver.resolve(unknown.root).table)
        val stride = halfword(); stride.half(0x2016, 0x00A0)
        assertNull("numeric geometry must match mandatory getters", stride.session().itemNameResolver.resolve(stride.root).table)
        for (target in listOf(0x2000, 0x2014, 0x2022, 0x2024, 0x2028, 0x202E)) {
            val f = halfword(); f.bl(0x2008, target)
            assertNull("opaque call cannot enter owned code/data", f.session().itemNameResolver.resolve(f.root).table)
        }
    }
}
