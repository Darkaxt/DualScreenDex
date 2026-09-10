package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.*
import org.junit.Test

class CompiledGbaTitleWindowFlowTest {
    @Test
    fun bindsRelocatedPacketAndFrameToTheSameWindowCopy() {
        for ((entry, printer) in listOf(0x400 to 0x100, 0x800 to 0x700)) {
            val f = Fixture(entry, printer)
            val result = f.resolve()
            assertNotNull(result)
            assertEquals(0x1600, result!!.rendererOffset)
            assertEquals(0x1700, result.frameCallbackOffset)
            assertEquals(0x1500, result.windowCopyOffset)
        }
    }

    @Test
    fun bindsPublishedWesternPacketAndFrameToTheSameWindowCopy() {
        val f = Fixture(western = true)
        val result = f.resolve()
        assertNotNull(result)
        assertEquals(0x1600, result!!.rendererOffset)
        assertEquals(0x1700, result.frameCallbackOffset)
        assertEquals(0x1500, result.windowCopyOffset)
    }

    @Test
    fun rejectsDamagedWesternPrinterStateFlow() {
        for (relative in listOf(
            0x4c, 0x50, 0x58, 0x64, 0x68, 0x76, 0x78, 0x80, 0x9c, 0xa4,
            0xb0, 0xbc, 0xc8, 0xce, 0xd2, 0xd4, 0xe2, 0xe4,
        )) {
            val f = Fixture(western = true)
            f.code.op(f.dispatcher + relative, 0x46c0)
            assertNull("western dispatcher instruction=$relative", f.resolve())
        }
        val complete = Fixture(western = true)
        for (size in 0 until 0xfc) {
            assertNull(complete.resolve(bytes = complete.code.bytes.copyOf(complete.dispatcher + size)))
        }
    }

    @Test
    fun rejectsChangedPacketCopyWindowLoadsAndBoundedLoop() {
        for (relative in listOf(0x32, 0x3a, 0x40, 0x44, 0x4a, 0x5e, 0x6c, 0x94, 0xa2, 0xa8, 0xb2, 0xba, 0xc4, 0xd6, 0xda)) {
            val f = Fixture()
            f.code.op(f.dispatcher + relative, 0x46c0)
            assertNull("dispatcher instruction=$relative", f.resolve())
        }
        val f = Fixture()
        f.code.word(f.dispatcher + 0x9c, 0xffff)
        assertNull(f.resolve())
    }

    @Test
    fun rejectsDisconnectedFontAndPacketRoots() {
        for (relative in listOf(0x18, 0x98, 0xdc, 0xe0)) {
            val f = Fixture()
            f.code.word(f.dispatcher + relative, 0x03000500)
            assertNull("disconnected root=$relative", f.resolve())
        }
    }

    @Test
    fun rejectsAliasingRamFieldsAndSlotsOutsideRam() {
        for ((offset, value) in listOf(0xe4 to 0x02000100, 0x88 to 0x02000100, 0x84 to 0x04000000)) {
            val f = Fixture()
            f.code.word(f.dispatcher + offset, value)
            if (offset == 0x88) f.code.word(f.dispatcher + 0xe0, value)
            assertNull(f.resolve())
        }
        val f = Fixture()
        f.code.word(f.dispatcher + 0x88, 0x03007fe0)
        f.code.word(f.dispatcher + 0xe0, 0x03007fe0)
        assertNull(f.resolve())
    }

    @Test
    fun rejectsRedirectedLiteralLoadsAndCallsIntoDataOrCode() {
        val f = Fixture()
        f.load(f.dispatcher + 0xaa, 0, f.dispatcher + 0xac)
        assertNull(f.resolve())
        for (target in listOf(0x40, 0x1b0, 0x1000, 0x1018, 0x2000)) {
            val other = Fixture()
            other.code.bl(other.dispatcher + 0xac, target)
            assertNull(other.resolve())
        }
        val other = Fixture()
        other.code.op(other.dispatcher + 0xae, 0xe800)
        assertNull(other.resolve())
    }

    @Test
    fun requiresFrameWindowForwardingBalancedReturnAndThumbCallback() {
        for (relative in listOf(0x06, 0x0e, 0x12, 0x16, 0x24, 0x2c, 0x36, 0x3a)) {
            val f = Fixture()
            f.code.op(f.frame + relative, 0x46c0)
            assertNull("frame instruction=$relative", f.resolve())
        }
        for (callback in listOf(0x08001700, 0x08000041, 0x0800043d, 0x02000001)) {
            val f = Fixture()
            f.code.word(f.frame + 0x44, callback)
            assertNull(f.resolve())
        }
    }

    @Test
    fun rejectsDifferentWindowCopyAndOverlappingFrameState() {
        val f = Fixture()
        f.code.bl(f.frame + 0x32, 0x1510)
        assertNull(f.resolve())
        val other = Fixture()
        other.code.word(other.frame + 0x40, 0x02001001)
        assertNull(other.resolve())
    }

    @Test
    fun rejectsTruncationInvalidEntriesAndInvalidWindows() {
        val f = Fixture()
        for (size in 0 until 0xe8) {
            assertNull(f.resolve(bytes = f.code.bytes.copyOf(f.dispatcher + size)))
        }
        val laterFrame = Fixture(0x1800)
        assertNotNull(laterFrame.resolve())
        for (size in 0 until 0x48) {
            assertNull(laterFrame.resolve(bytes = laterFrame.code.bytes.copyOf(laterFrame.frame + size)))
        }
        for (frame in listOf(-1, 0x40, 0x401, Int.MAX_VALUE - 1)) assertNull(f.resolve(frame = frame))
        for (window in listOf(-1, 256, Int.MAX_VALUE)) assertNull(f.resolve(window = window))
    }

    @Test
    fun checksCancellationBeforeInspectingInvalidInput() {
        assertThrows(IllegalStateException::class.java) {
            CompiledGbaTitleWindowFlow.resolve(RomImage(ByteArray(0)), -1, -1, -1,
                ParserCancellationToken { throw IllegalStateException("cancelled") })
        }
    }

    internal class Fixture(val frame: Int = 0x400, printer: Int = 0x100, western: Boolean = false) {
        val code = CompiledGbaTextPrinterTest.Fixture(printer)
        val dispatcher = code.dispatcher

        init {
            val d = dispatcher
            if (western) addWesternDispatcher(d) else addCompactDispatcher(d)

            code.ops(frame, 0xb530, 0x1c0c, 0x0600, 0x0e05, 0x0624, 0x0e24)
            load(frame + 0x0c, 0, frame + 0x3c)
            code.op(frame + 0x0e, 0x8002)
            load(frame + 0x10, 0, frame + 0x40)
            code.op(frame + 0x12, 0x7003)
            load(frame + 0x14, 1, frame + 0x44)
            code.op(frame + 0x16, 0x1c28)
            code.bl(frame + 0x18, 0x1400)
            code.ops(frame + 0x1c, 0x1c28, 0x2111)
            code.bl(frame + 0x20, 0x1420)
            code.op(frame + 0x24, 0x1c28)
            code.bl(frame + 0x26, 0x1440)
            code.ops(frame + 0x2a, 0x2c01, 0xd103, 0x1c28, 0x2103)
            code.bl(frame + 0x32, 0x1500)
            code.ops(frame + 0x36, 0xbc30, 0xbc01, 0x4700)
            code.word(frame + 0x3c, 0x02001000)
            code.word(frame + 0x40, 0x02001002)
            code.word(frame + 0x44, 0x08001701)
        }

        private fun addCompactDispatcher(d: Int) {
            code.ops(d, 0xb5f0, 0x1c06, 0x4694, 0x0609, 0x0e0d)
            load(d + 0x0a, 0, d + 0x18)
            code.ops(d + 0x0c, 0x6800, 0x2800, 0xd104, 0x2000, 0xe05f)
            code.word(d + 0x18, 0x03000100)
            load(d + 0x1c, 0, d + 0x84)
            code.ops(d + 0x1e, 0x2200, 0x2101, 0x76c1, 0x7702, 0x7745, 0x7782, 0x77c2,
                0x1c04, 0x2106, 0x301a, 0x7002, 0x3801, 0x3901, 0x2900, 0xdafa,
                0x1c21, 0x1c30, 0xc88c, 0xc18c, 0x6800, 0x6008, 0x4660, 0x6120,
                0x7b30, 0x0900, 0x7b72, 0x0711, 0x0f09, 0x0912)
            code.bl(d + 0x58, 0x1680)
            code.ops(d + 0x5c, 0x2dff, 0xd015, 0x2d00, 0xd013, 0x7f60, 0x3801, 0x7760)
            load(d + 0x6a, 0, d + 0x88)
            code.ops(d + 0x6c, 0x7931, 0x0149, 0x1809, 0x1c20, 0xc81c, 0xc11c,
                0xc88c, 0xc18c, 0xc890, 0xc190, 0xe025)
            code.word(d + 0x84, 0x02000100)
            code.word(d + 0x88, 0x02000200)
            load(d + 0x8c, 1, d + 0x98)
            code.ops(d + 0x8e, 0x2000, 0x7748, 0x2400)
            load(d + 0x94, 7, d + 0x9c)
            code.op(d + 0x96, 0xe006)
            code.word(d + 0x98, 0x02000100)
            code.word(d + 0x9c, 0x3ff)
            code.ops(d + 0xa0, 0x1c60, 0x0400, 0x0c04, 0x42bc, 0xd804)
            load(d + 0xaa, 0, d + 0xdc)
            code.bl(d + 0xac, 0x1600)
            code.ops(d + 0xb0, 0x2801, 0xd1f5, 0x2dff, 0xd004)
            load(d + 0xb8, 0, d + 0xdc)
            code.ops(d + 0xba, 0x7900, 0x2102)
            code.bl(d + 0xbe, 0x1500)
            load(d + 0xc2, 0, d + 0xe0)
            code.ops(d + 0xc4, 0x7931, 0x0149, 0x1809, 0x2000, 0x76c8)
            load(d + 0xce, 1, d + 0xe4)
            code.ops(d + 0xd0, 0x2000, 0x7008, 0x2001, 0xbcf0, 0xbc02, 0x4708)
            code.word(d + 0xdc, 0x02000100)
            code.word(d + 0xe0, 0x02000200)
            code.word(d + 0xe4, 0x02000800)
        }

        private fun addWesternDispatcher(d: Int) {
            code.ops(d, 0xb5f0, 0x1c06, 0x4694, 0x0609, 0x0e0d)
            load(d + 0x0a, 0, d + 0x18)
            code.ops(d + 0x0c, 0x6800, 0x2800, 0xd104, 0x2000, 0xe069)
            code.word(d + 0x18, 0x03000100)
            load(d + 0x1c, 0, d + 0x94)
            code.ops(d + 0x1e, 0x2200, 0x2101, 0x76c1, 0x7702, 0x7745, 0x7782, 0x77c2,
                0x1c04, 0x2106, 0x301a, 0x7002, 0x3801, 0x3901, 0x2900, 0xdafa,
                0x1c21, 0x1c30, 0xc88c, 0xc18c, 0x6800, 0x6008, 0x4660, 0x6120)
            code.ops(d + 0x4c, 0x1c20, 0x3020, 0x2100, 0x7001, 0x3001, 0x7001,
                0x7b30, 0x0900, 0x7b72, 0x0711, 0x0f09, 0x0912)
            code.bl(d + 0x64, 0x1680)
            code.ops(d + 0x68, 0x2dff, 0xd017, 0x2d00, 0xd015, 0x7f60, 0x3801, 0x7760)
            load(d + 0x76, 2, d + 0x98)
            code.ops(d + 0x78, 0x7930, 0x00c1, 0x1809, 0x0089, 0x1889, 0x1c20, 0xc81c,
                0xc11c, 0xc88c, 0xc18c, 0xc894, 0xc194, 0xe027)
            code.word(d + 0x94, 0x02000100)
            code.word(d + 0x98, 0x02000200)
            load(d + 0x9c, 1, d + 0xa8)
            code.ops(d + 0x9e, 0x2000, 0x7748, 0x2400)
            load(d + 0xa4, 7, d + 0xac)
            code.op(d + 0xa6, 0xe006)
            code.word(d + 0xa8, 0x02000100)
            code.word(d + 0xac, 0x3ff)
            code.ops(d + 0xb0, 0x1c60, 0x0400, 0x0c04, 0x42bc, 0xd804)
            load(d + 0xba, 0, d + 0xf0)
            code.bl(d + 0xbc, 0x1600)
            code.ops(d + 0xc0, 0x2801, 0xd1f5, 0x2dff, 0xd004)
            load(d + 0xc8, 0, d + 0xf0)
            code.ops(d + 0xca, 0x7900, 0x2102)
            code.bl(d + 0xce, 0x1500)
            load(d + 0xd2, 2, d + 0xf4)
            code.ops(d + 0xd4, 0x7931, 0x00c8, 0x1840, 0x0080, 0x1880, 0x2100, 0x76c1)
            load(d + 0xe2, 1, d + 0xf8)
            code.ops(d + 0xe4, 0x2000, 0x7008, 0x2001, 0xbcf0, 0xbc02, 0x4708)
            code.word(d + 0xf0, 0x02000100)
            code.word(d + 0xf4, 0x02000200)
            code.word(d + 0xf8, 0x02000800)
        }

        fun load(at: Int, register: Int, pool: Int) {
            val delta = pool - ((at + 4) and -4)
            require(delta in 0..1020 && delta % 4 == 0)
            code.op(at, 0x4800 or (register shl 8) or (delta / 4))
        }

        fun resolve(bytes: ByteArray = code.bytes, frame: Int = this.frame, window: Int = 3) =
            CompiledGbaTitleWindowFlow.resolve(RomImage(bytes), code.entry, frame, window, ParserCancellationToken.NONE)
    }
}
