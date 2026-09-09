package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class Gen2ContextualMapNameResolverTest {
    @Test
    fun bindsOnlySentinelHeadersThroughRelocatedCompiledDeclarations() {
        for (shift in listOf(0, 0x120)) {
            for (bank in listOf(3, 5)) {
                val fixture = Fixture(shift, bank)
                val binding = fixture.resolve()
                assertEquals(setOf(0x1401, 0x1403), binding?.contextualMapIds)
                assertEquals(fixture.selector, binding?.selectorOffset)
                assertEquals(0xd234, binding?.currentGroupAddress)
                assertEquals(0xd120, binding?.backupGroupAddress)
            }
        }
    }

    @Test
    fun unboundZeroHeaderIsNotAContextualDeclaration() {
        val fixture = Fixture()
        fixture.data[fixture.selector] = 0
        assertNull(fixture.resolve())
    }

    @Test
    fun malformedDirectDependenciesFailClosed() {
        val offsets = listOf<(Fixture) -> Int>(
            { it.field + 4 }, // Wrong header field.
            { it.member + 4 }, // Wrong mapped bank.
            { it.member + 1 }, // Saved bank register not bound to RST.
            { 0x13 }, // Wrong RST mapper address.
            { it.pointer + 18 }, // Wrong header stride.
            { it.pointer + 22 }, // CALL must return.
            { it.stride + 5 }, // Wrong multiplication loop branch.
            { it.selector + 12 }, // Nonzero sentinel cannot authorize field zero.
            { it.selector + 13 }, // Wrong sentinel branch direction.
            { it.selector + 23 }, // Backup must use the same field consumer.
        )
        for (offset in offsets) {
            val fixture = Fixture()
            val position = offset(fixture)
            fixture.data[position] = (fixture.data[position].toInt() xor 1).toByte()
            assertNull("accepted malformed dependency at $position", fixture.resolve())
        }
    }

    @Test
    fun matchingIoRegistersAreNotSavedHramBankAuthority() {
        for (register in listOf(0x01, 0x7f, 0xff)) {
            val fixture = Fixture()
            fixture.data[0x11] = register.toByte()
            fixture.data[fixture.member + 1] = register.toByte()
            assertNull("accepted non-HRAM register $register", fixture.resolve())
        }
    }

    @Test
    fun unrelatedGroupNumbersAndCrystalStyleHramRegisterAreStructural() {
        val fixture = Fixture()
        fixture.data[0x11] = 0x9d.toByte()
        fixture.data[fixture.member + 1] = 0x9d.toByte()
        fixture.word(fixture.table + 2, 0x4000 + fixture.root % 0x4000)
        fixture.headers.clear()
        fixture.headers[0x201] = fixture.root
        fixture.headers[0x203] = fixture.root + 18
        assertEquals(setOf(0x201, 0x203), fixture.resolve()?.contextualMapIds)
    }

    @Test
    fun jumpTailStrideVariantBindsWithoutInventingAReturnByte() {
        val fixture = Fixture()
        fixture.data[fixture.pointer + 19] = 0xc3.toByte()
        fixture.data[fixture.pointer + 22] = 0xff.toByte()
        assertEquals(setOf(0x1401, 0x1403), fixture.resolve()?.contextualMapIds)
    }

    @Test
    fun alteredDirectoryOrSelectedHeaderCannotBorrowTheSelector() {
        val wrongDirectory = Fixture()
        wrongDirectory.word(wrongDirectory.table + 38, 0x4800)
        assertNull(wrongDirectory.resolve())
        val wrongHeader = Fixture()
        wrongHeader.headers[0x1401] = wrongHeader.root + 1
        assertNull(wrongHeader.resolve())
        val wrongTable = Fixture()
        wrongTable.word(wrongTable.pointer + 6, 0x4700)
        assertNull(wrongTable.resolve())
    }

    @Test
    fun invalidOrOverlappingWramPairsCannotAuthorizeContext() {
        for ((current, backup) in listOf(0xbffe to 0xd120, 0xdfff to 0xd120, 0xd234 to 0xd234, 0xd234 to 0xd235)) {
            val fixture = Fixture()
            fixture.word(fixture.selector + 1, current)
            fixture.word(fixture.selector + 5, current + 1)
            fixture.word(fixture.selector + 15, backup)
            fixture.word(fixture.selector + 19, backup + 1)
            assertNull(fixture.resolve())
        }
    }

    @Test
    fun twoCompleteCompetingSelectorsRemainUnavailable() {
        val fixture = Fixture()
        fixture.data.copyInto(fixture.data, fixture.selector + 0x100, fixture.selector, fixture.selector + 26)
        fixture.word(fixture.selector + 0x101, 0xd430)
        fixture.word(fixture.selector + 0x105, 0xd431)
        assertNull(fixture.resolve())
    }

    @Test
    fun malformedCompetingSelectorForSameAuthorityCannotBeIgnored() {
        for (position in listOf(12, 13, 23)) {
            val fixture = Fixture()
            val competitor = fixture.selector + 0x100
            fixture.data.copyInto(fixture.data, competitor, fixture.selector, fixture.selector + 26)
            fixture.data[competitor + position] = (fixture.data[competitor + position].toInt() xor 1).toByte()
            assertNull("ignored malformed competing selector operand $position", fixture.resolve())
        }
    }

    @Test
    fun noncontextualMapsRemainPresentButAreNotExempt() {
        val fixture = Fixture()
        fixture.headers.values.forEach { fixture.data[it + 5] = 17 }
        val before = fixture.headers.toMap()
        assertEquals(emptySet<Int>(), fixture.resolve()?.contextualMapIds)
        assertEquals(before, fixture.headers)
    }

    @Test
    fun truncatedAndCrossBankDeclarationsFailClosed() {
        val fixture = Fixture()
        assertNull(Gen2ContextualMapNameResolver.resolve(RomImage(fixture.data.copyOf(32)), fixture.bank, fixture.table, fixture.headers))
        val crossing = 0x1bff0
        fixture.data.copyInto(fixture.data, crossing, fixture.selector, fixture.selector + 26)
        fixture.data[fixture.selector] = 0
        assertNull(fixture.resolve())
        val headerCrossing = Fixture()
        headerCrossing.word(headerCrossing.table + 38, 0x7ffc)
        headerCrossing.headers.clear()
        headerCrossing.headers[0x1401] = headerCrossing.bank * 0x4000 + 0x3ffc
        assertNull(headerCrossing.resolve())
    }

    @Test
    fun cancellationBeforeWorkAndDuringScanPropagates() {
        val fixture = Fixture()
        assertThrows(ParserCancellationException::class.java) {
            fixture.resolve(ParserCancellationToken { throw ParserCancellationException() })
        }
        var checks = 0
        assertThrows(ParserCancellationException::class.java) {
            fixture.resolve(ParserCancellationToken { if (++checks == 4) throw ParserCancellationException() })
        }
        assertEquals(4, checks)
    }

    private class Fixture(shift: Int = 0, val bank: Int = 3) {
        val data = ByteArray(8 * 0x4000) { 0xff.toByte() }
        val pointer = 0x2100 + shift
        val member = 0x2200 + shift
        val field = 0x2300 + shift
        val stride = 0x2400 + shift
        val selector = 0x18000 + shift
        val table = bank * 0x4000 + 0x100 + shift
        val root = bank * 0x4000 + 0x300 + shift
        val headers = linkedMapOf(0x1401 to root, 0x1402 to root + 9, 0x1403 to root + 18)

        init {
            put(0x10, "e09fea0020c9")
            put(pointer, "c50548060021000009092a666fc10d06003e09cd0000c9")
            word(pointer + 6, 0x4000 + table % 0x4000)
            word(pointer + 20, stride)
            put(stride, "a7c8093d20fcc9")
            put(member, "f09ff53e00d7cd0000194e2346f1d7c9")
            data[member + 4] = bank.toByte()
            word(member + 7, pointer)
            put(field, "e5d5c5110500cd000079c1d1e1c9")
            word(field + 7, member)
            put(selector, "fa34d247fa35d24fcd0000fe00c0fa20d147fa21d14fcd0000c9")
            word(selector + 9, field)
            word(selector + 23, field)
            word(table + 38, 0x4000 + root % 0x4000)
            headers.values.forEach { put(it, "250703004000091101") }
            data[root + 9 + 5] = 17
        }

        fun resolve(cancellation: ParserCancellationToken = ParserCancellationToken.NONE) =
            Gen2ContextualMapNameResolver.resolve(RomImage(data), bank, table, headers, cancellation)

        fun put(offset: Int, hex: String) {
            hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray().copyInto(data, offset)
        }

        fun word(offset: Int, value: Int) {
            data[offset] = value.toByte()
            data[offset + 1] = (value ushr 8).toByte()
        }
    }
}
