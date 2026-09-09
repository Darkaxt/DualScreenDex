package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.*
import org.junit.Test

class CompiledGbaTextPrinterTest {
    @Test
    fun bindsSourceAndWindowToDifferentPacketFieldsAfterRelocation() {
        for (entry in listOf(0x100, 0x302, 0x700)) {
            val fixture = Fixture(entry)
            val contract = CompiledGbaTextPrinter.resolve(RomImage(fixture.bytes), entry, ParserCancellationToken.NONE)
            assertNotNull("entry=$entry", contract)
            assertEquals(2, contract!!.sourceRegister)
            assertEquals(0, contract.windowRegister)
            assertEquals(fixture.dispatcher, contract.dispatcherOffset)
        }
    }

    @Test
    fun rejectsEachChangedSourceWindowAndCoordinateStore() {
        for (relative in listOf(0x20, 0x24, 0x28, 0x2a, 0x2c, 0x2e, 0x30)) {
            val fixture = Fixture()
            fixture.op(fixture.entry + relative, 0x46c0)
            assertNull("changed store=$relative", fixture.resolve())
        }
    }

    @Test
    fun doesNotSkipUnknownMiddleInstructionsOrCalls() {
        for (op in listOf(0x6002, 0xe000, 0xd000, 0xf000, 0x4700, 0xb081, 0x9200)) {
            val fixture = Fixture()
            fixture.op(fixture.entry + 0x66, op)
            assertNull("middle op=$op", fixture.resolve())
        }
    }

    @Test
    fun requiresCompleteBalancedReturn() {
        for (relative in listOf(0x9c, 0x9e, 0xa0, 0xa2, 0xa4, 0xa6)) {
            val fixture = Fixture()
            fixture.op(fixture.entry + relative, 0x46c0)
            assertNull(fixture.resolve())
        }
    }

    @Test
    fun rejectsLiteralPoolInsideExecutableBodyAndNonRamFontRoot() {
        val fixture = Fixture()
        fixture.literal(fixture.entry + 0x32, fixture.entry + 0x70, 0x03000100)
        assertNull(fixture.resolve())
        for (root in listOf(0x08001000, 0x04000000, 0x03000101, 0)) {
            val other = Fixture()
            other.word(other.pool, root)
            assertNull(other.resolve())
        }
    }

    @Test
    fun requiresAtomicInBoundsExternalDispatch() {
        val fixture = Fixture()
        fixture.op(fixture.entry + 0x96, 0xe800)
        assertNull(fixture.resolve())
        fixture.bl(fixture.entry + 0x94, fixture.entry + 0x20)
        assertNull(fixture.resolve())
        fixture.bl(fixture.entry + 0x94, fixture.bytes.size)
        assertNull(fixture.resolve())
    }

    @Test
    fun rejectsEveryTruncatedFunctionAndLiteralWord() {
        val fixture = Fixture()
        for (length in 0 until 0xa8) {
            assertNull(CompiledGbaTextPrinter.resolve(RomImage(fixture.bytes.copyOf(fixture.entry + length)),
                fixture.entry, ParserCancellationToken.NONE))
        }
        for (length in 0..3) {
            assertNull(CompiledGbaTextPrinter.resolve(RomImage(fixture.bytes.copyOf(fixture.pool + length)),
                fixture.entry, ParserCancellationToken.NONE))
        }
    }

    @Test
    fun rejectsNegativeOddHeaderAndOverflowingEntries() {
        val fixture = Fixture()
        for (entry in listOf(-2, 0x40, 0x101, Int.MAX_VALUE - 1)) {
            assertNull(CompiledGbaTextPrinter.resolve(RomImage(fixture.bytes), entry, ParserCancellationToken.NONE))
        }
    }

    @Test
    fun checksCancellationEvenForInvalidEntry() {
        var checked = false
        val token = ParserCancellationToken { checked = true; throw IllegalStateException("cancelled") }
        val error = assertThrows(IllegalStateException::class.java) {
            CompiledGbaTextPrinter.resolve(RomImage(ByteArray(0)), -1, token)
        }
        assertEquals("cancelled", error.message)
        assertTrue(checked)
    }

    internal class Fixture(val entry: Int = 0x100) {
        val bytes = ByteArray(0x2000)
        val pool = (entry + 0xb0) and -4
        val dispatcher = if (entry < 0x700) 0x1000 else 0x200

        init {
            // Fabricated Thumb packet constructor: r2=source, r0=window, r1=font, r3=x.
            ops(entry, 0xb5f0, 0x4647, 0xb480, 0xb084, 0x9c0a, 0x9d0b, 0x9f0c,
                0x0609, 0x0e09, 0x061b, 0x0e1b, 0x0624, 0x0e24, 0x062d, 0x0e2d, 0x46a8,
                0x9200, 0x466a, 0x7110, 0x4668, 0x7141, 0x7183, 0x71c4, 0x7203, 0x7244)
            literal(entry + 0x32, pool, 0x03000100)
            ops(entry + 0x34, 0x6800, 0x004b, 0x185b, 0x009b, 0x181b, 0x7998, 0x7290,
                0x4669, 0x79d8, 0x72c8, 0x466d, 0x7a19, 0x0709, 0x260f, 0x0f09, 0x7b2c,
                0x2210, 0x4252, 0x1c10, 0x4020, 0x4308, 0x7328, 0x466c, 0x7a19, 0x0909,
                0x0109, 0x4030, 0x4308, 0x7320, 0x7a58, 0x0700, 0x0f00, 0x1c31, 0x4001,
                0x7b60, 0x4002, 0x430a, 0x7362, 0x4669, 0x7a58, 0x0900, 0x0100, 0x4032,
                0x4302, 0x734a, 0x4668, 0x4641, 0x1c3a)
            bl(entry + 0x94, dispatcher)
            ops(entry + 0x98, 0x0400, 0x0c00, 0xb004, 0xbc08, 0x4698, 0xbcf0, 0xbc02, 0x4708)
        }

        fun resolve() = CompiledGbaTextPrinter.resolve(RomImage(bytes), entry, ParserCancellationToken.NONE)
        fun op(at: Int, value: Int) { bytes[at] = value.toByte(); bytes[at + 1] = (value ushr 8).toByte() }
        fun ops(at: Int, vararg values: Int) { values.forEachIndexed { i, value -> op(at + i * 2, value) } }
        fun word(at: Int, value: Int) { repeat(4) { bytes[at + it] = (value ushr (it * 8)).toByte() } }
        fun literal(at: Int, pool: Int, value: Int) {
            val delta = pool - ((at + 4) and -4)
            require(delta >= 0 && delta % 4 == 0)
            op(at, 0x4800 or (delta / 4))
            word(pool, value)
        }
        fun bl(at: Int, target: Int) {
            val delta = (target - at - 4) and 0x7fffff
            op(at, 0xf000 or (delta ushr 12))
            op(at + 2, 0xf800 or ((delta ushr 1) and 0x7ff))
        }
    }
}
