package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.*
import org.junit.Test

class CompiledGbaFieldMapTitleTest {
    @Test
    fun nominatesRelocatedSelectedLoaderAndStaticSourceWithoutDecoding() {
        for (owner in listOf(0x400, 0x600, 0xc00)) {
            val f = Fixture(owner)
            val result = f.nominate() as CompiledGbaFieldMapTitle.Result.Complete
            val title = result.declarations.single()
            assertEquals(owner, title.owner)
            assertEquals(f.loader, title.loader)
            assertEquals(f.source, title.source)
            assertEquals(3, title.window)
            assertEquals(f.printer.entry, title.printer)
            assertNotNull("dispatcher/frame flow is independently proven", title.windowFlow)
            assertNotNull("all seven owner states establish current-domain authority", title.ownerFlow)
            // Zero-filled source is deliberately not a plausible terminated title.
            assertEquals(0, f.printer.bytes[f.source].toInt())
        }
    }

    @Test
    fun anotherLoaderAndUnreferencedTextAreNotDeclarations() {
        val f = Fixture()
        assertTrue((f.nominate(f.loader + 4) as CompiledGbaFieldMapTitle.Result.Complete).declarations.isEmpty())
        f.printer.word(f.source + 16, 0x08000000 + f.source)
        assertEquals(1, (f.nominate() as CompiledGbaFieldMapTitle.Result.Complete).declarations.size)
    }

    @Test
    fun connectedMalformedTitleIsIncompleteRatherThanNoMatch() {
        for (relative in listOf(0, 2, 4, 6, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30)) {
            val f = Fixture()
            f.printer.op(f.title + relative, 0x46c0)
            assertTrue("title relative=$relative", f.nominate() is CompiledGbaFieldMapTitle.Result.Incomplete)
        }
    }

    @Test
    fun brokenWrapperLoopCannotEstablishSelectedLoaderBinding() {
        val f = Fixture()
        f.printer.op(f.wrapper + 20, 0xd0fa)
        assertTrue(f.nominate() is CompiledGbaFieldMapTitle.Result.Incomplete)
    }

    @Test
    fun brokenStateGuardAndIncrementFailClosed() {
        for (relative in listOf(0xa, 0xc, 0x10, 0x12, 0x14, 0x16, 0x1a, 0x1c, 0x1e, 0x13c, 0x13e, 0x140)) {
            val f = Fixture()
            f.printer.op(f.owner + relative, 0x46c0)
            assertTrue("owner relative=$relative", f.nominate() is CompiledGbaFieldMapTitle.Result.Incomplete)
        }
    }

    @Test
    fun incompleteLaterOwnerArmRetainsNominationWithoutAuthority() {
        for (relative in listOf(
            0x92, 0xa0, 0xa4, 0xb0, 0xbc, 0xc2, 0xc8, 0xce, 0xd2, 0xe4, 0xee,
            0xf8, 0x102, 0x104, 0x10c, 0x110, 0x124, 0x130, 0x134, 0x138,
            0x144, 0x148, 0x14c, 0x15a, 0x15c, 0x160, 0x166, 0x16e, 0x170,
            0x176, 0x184, 0x188,
        )) {
            val f = Fixture()
            f.printer.op(f.owner + relative, 0x46c0)
            val declaration = (f.nominate() as CompiledGbaFieldMapTitle.Result.Complete).declarations.single()
            assertNull("owner relative=$relative", declaration.ownerFlow)
        }
    }

    @Test
    fun unprovedWindowFlowCannotEstablishOwnerAuthority() {
        val f = Fixture()
        f.printer.op(0x1480 + 0x06, 0x46c0)
        val declaration = (f.nominate() as CompiledGbaFieldMapTitle.Result.Complete).declarations.single()
        assertNull(declaration.windowFlow)
        assertNull(declaration.ownerFlow)
    }

    @Test
    fun ownerAuthorityRejectsStaticSourceAliasingKnownCode() {
        for (source in listOf(0x400, 0x58c, 0x1480, 0x1800, 0x200)) {
            val f = Fixture()
            f.printer.word(f.owner + 0xb8, 0x08000000 + source)
            val declaration = (f.nominate() as CompiledGbaFieldMapTitle.Result.Complete).declarations.single()
            assertEquals(source, declaration.source)
            assertNull("source=$source", declaration.ownerFlow)
        }
    }

    @Test
    fun ownerFlowRejectsEveryTruncatedOwnerEnvelope() {
        val f = Fixture()
        val declaration = (f.nominate() as CompiledGbaFieldMapTitle.Result.Complete).declarations.single()
        for (size in 0 until 0x18c) {
            val truncated = RomImage(f.printer.bytes.copyOf(f.owner + size))
            assertNull("owner bytes=$size",
                CompiledGbaFieldMapOwnerFlow.resolve(truncated, declaration, ParserCancellationToken.NONE))
        }
    }

    @Test
    fun ownerFlowChecksCancellationBeforeInvalidInput() {
        val declaration = CompiledGbaFieldMapTitle.Declaration(-1, -1, -1, -1, -1, -1)
        assertThrows(ParserCancellationException::class.java) {
            CompiledGbaFieldMapOwnerFlow.resolve(RomImage(ByteArray(0)), declaration,
                ParserCancellationToken { throw ParserCancellationException() })
        }
    }

    @Test
    fun keepsConflictingConnectedOwnersAndReportsCapInsteadOfChoosingFirst() {
        val f = Fixture()
        f.addOwner(0x800, f.source + 16)
        val result = f.nominate() as CompiledGbaFieldMapTitle.Result.Complete
        assertEquals(setOf(f.source, f.source + 16), result.declarations.map { it.source }.toSet())
        assertTrue(f.nominate(maximumOwners = 1) is CompiledGbaFieldMapTitle.Result.Incomplete)
        f.printer.op(0x800 + 0x70 + 24, 0x2000)
        assertTrue(f.nominate() is CompiledGbaFieldMapTitle.Result.Incomplete)
    }

    @Test
    fun rejectsRamSourceAndBrokenPrinterEvenWithAValidSourcePointer() {
        val f = Fixture()
        f.printer.word(f.owner + 0xb8, 0x02001000)
        assertTrue(f.nominate() is CompiledGbaFieldMapTitle.Result.Incomplete)
        val other = Fixture()
        other.printer.op(other.printer.entry + 0x20, 0x46c0)
        assertTrue(other.nominate() is CompiledGbaFieldMapTitle.Result.Incomplete)
    }

    @Test
    fun cancellationOccursDuringScanAndBeforeInvalidInput() {
        val f = Fixture()
        var checks = 0
        assertThrows(ParserCancellationException::class.java) {
            CompiledGbaFieldMapTitle.nominate(RomImage(f.printer.bytes), f.loader,
                ParserCancellationToken { if (++checks == 3) throw ParserCancellationException() })
        }
        assertEquals(3, checks)
        assertThrows(ParserCancellationException::class.java) {
            CompiledGbaFieldMapTitle.nominate(RomImage(ByteArray(0)), -1,
                ParserCancellationToken { throw ParserCancellationException() })
        }
    }

    @Test
    fun invalidBudgetsAndLoaderBoundsAreNotCompleteEmptyResults() {
        val f = Fixture()
        for (budget in listOf(-1, 0, 33, Int.MAX_VALUE)) {
            assertTrue(f.nominate(maximumOwners = budget) is CompiledGbaFieldMapTitle.Result.Incomplete)
        }
        for (loader in listOf(-1, 0x40, 0x101, Int.MAX_VALUE - 1, f.printer.bytes.size)) {
            assertTrue(f.nominate(loader) is CompiledGbaFieldMapTitle.Result.Incomplete)
        }
    }

    private class Fixture(val owner: Int = 0x400) {
        val printer = CompiledGbaTitleWindowFlowTest.Fixture(0x1480, 0x1800).code
        val loader = 0x1200
        val wrapper = 0x1300
        val source = 0x1c00
        val title = owner + 0x70

        init {
            printer.ops(wrapper, 0xb500, 0x060a, 0x0e12, 0x2100)
            printer.bl(wrapper + 8, 0x1400)
            printer.bl(wrapper + 12, loader)
            printer.ops(wrapper + 16, 0x0600, 0x2800, 0xd1fa, 0xbc01, 0x4700)
            addOwner(owner, source)
        }

        fun addOwner(owner: Int, source: Int) {
            val table = owner + 0x2c
            val initial = owner + 0x48
            val title = owner + 0x70
            val increment = owner + 0x13c
            val end = owner + 0x17a
            val handlerRoot = 0x02000100
            val stateOffset = 0x220
            val fadeRoot = 0x02000800
            printer.ops(owner, 0xb530, 0xb083)
            literal(owner + 4, 1, owner + 0x20, handlerRoot)
            printer.op(owner + 6, 0x6808)
            literal(owner + 8, 2, owner + 0x24, stateOffset)
            printer.ops(owner + 0xa, 0x1880, 0x8800, 0x1c0c, 0x2806, 0xd900)
            branch(owner + 0x14, end)
            printer.op(owner + 0x16, 0x0080)
            literal(owner + 0x18, 1, owner + 0x28, 0x08000000 + table)
            printer.ops(owner + 0x1a, 0x1840, 0x6800, 0x4687)
            listOf(0x48, 0x70, 0xbc, 0xe4, 0xf8, 0x124, 0x14c).forEachIndexed { i, arm ->
                printer.word(table + i * 4, 0x08000000 + owner + arm)
            }

            printer.ops(initial, 0x6820, 0x3008, 0x2100)
            printer.bl(initial + 6, wrapper)
            printer.ops(initial + 10, 0x2000, 0x2100)
            printer.bl(initial + 14, 0x1440)
            printer.ops(initial + 18, 0x2001, 0x2101)
            printer.bl(initial + 22, 0x1460)
            printer.op(initial + 26, 0x6821)
            literal(initial + 28, 0, owner + 0x6c, stateOffset)
            printer.op(initial + 30, 0x1809)
            branch(initial + 32, increment)

            printer.ops(title, 0x2003, 0x2100, 0x2227, 0x230d)
            printer.bl(title + 8, 0x1480)
            literal(title + 12, 2, owner + 0xb8, 0x08000000 + source)
            printer.ops(title + 14, 0x2002, 0x9000, 0x2400, 0x9401, 0x9402, 0x2003, 0x2101, 0x2300)
            printer.bl(title + 30, printer.entry)
            printer.op(title + 34, 0x2000)
            printer.bl(title + 36, 0x1720)
            printer.ops(title + 40, 0x2000, 0x2100, 0x2227, 0x230d)
            printer.bl(title + 48, 0x1480)
            printer.bl(title + 52, owner + 0x18c)
            printer.ops(title + 56, 0x2001, 0x4240, 0x9400, 0x2100, 0x2210, 0x2300)
            branch(title + 68, owner + 0x130)

            printer.ops(owner + 0xbc, 0x2182, 0x0149, 0x2000)
            printer.bl(owner + 0xc2, 0x1a00)
            printer.op(owner + 0xc6, 0x2000)
            printer.bl(owner + 0xc8, 0x1a20)
            printer.op(owner + 0xcc, 0x2002)
            printer.bl(owner + 0xce, 0x1a20)
            literal(owner + 0xd2, 0, owner + 0xdc, handlerRoot)
            printer.op(owner + 0xd4, 0x6801)
            literal(owner + 0xd6, 0, owner + 0xe0, stateOffset)
            printer.op(owner + 0xd8, 0x1809)
            branch(owner + 0xda, increment)

            literal(owner + 0xe4, 0, owner + 0xf4, fadeRoot)
            printer.ops(owner + 0xe6, 0x79c1, 0x2080, 0x4008, 0x2800)
            conditionalBranch(owner + 0xee, 1, end)
            printer.op(owner + 0xf0, 0x6821)
            branch(owner + 0xf2, owner + 0x138)

            printer.bl(owner + 0xf8, 0x1a40)
            printer.ops(owner + 0xfc, 0x0600, 0x0e00, 0x2803)
            conditionalBranch(owner + 0x102, 1, owner + 0x10a)
            printer.bl(owner + 0x104, owner + 0x18c)
            branch(owner + 0x108, end)
            printer.op(owner + 0x10a, 0x2803)
            conditionalBranch(owner + 0x10c, 0xb, end)
            printer.op(owner + 0x10e, 0x2805)
            conditionalBranch(owner + 0x110, 0xc, end)
            literal(owner + 0x112, 0, owner + 0x11c, handlerRoot)
            printer.op(owner + 0x114, 0x6801)
            literal(owner + 0x116, 0, owner + 0x120, stateOffset)
            printer.op(owner + 0x118, 0x1809)
            branch(owner + 0x11a, increment)

            printer.ops(owner + 0x124, 0x2001, 0x4240, 0x2100, 0x9100, 0x2200, 0x2310)
            printer.bl(owner + 0x130, 0x1760)
            literal(owner + 0x134, 0, owner + 0x144, handlerRoot)
            printer.op(owner + 0x136, 0x6801)
            literal(owner + 0x138, 2, owner + 0x148, stateOffset)
            printer.op(owner + 0x13a, 0x1889)
            printer.ops(increment, 0x8808, 0x3001, 0x8008)
            branch(increment + 6, end)

            literal(owner + 0x14c, 0, owner + 0x184, fadeRoot)
            printer.ops(owner + 0x14e, 0x79c1, 0x2080, 0x4008, 0x0600, 0x0e05, 0x2d00)
            conditionalBranch(owner + 0x15a, 1, end)
            printer.bl(owner + 0x15c, 0x1a60)
            literal(owner + 0x160, 4, owner + 0x188, handlerRoot)
            printer.ops(owner + 0x162, 0x6820, 0x6800)
            printer.bl(owner + 0x166, 0x1a80)
            printer.ops(owner + 0x16a, 0x6820, 0x2800)
            conditionalBranch(owner + 0x16e, 0, owner + 0x176)
            printer.bl(owner + 0x170, 0x1aa0)
            printer.op(owner + 0x174, 0x6025)
            printer.bl(owner + 0x176, 0x1ac0)
            printer.ops(end, 0xb003, 0xbc30, 0xbc01, 0x4700)

            printer.word(owner + 0xdc, handlerRoot)
            printer.word(owner + 0xe0, stateOffset)
            printer.word(owner + 0xf4, fadeRoot)
            printer.word(owner + 0x11c, handlerRoot)
            printer.word(owner + 0x120, stateOffset)
            printer.word(owner + 0x144, handlerRoot)
            printer.word(owner + 0x148, stateOffset)
            printer.word(owner + 0x184, fadeRoot)
            printer.word(owner + 0x188, handlerRoot)
        }

        fun nominate(selected: Int = loader, maximumOwners: Int = 32) = CompiledGbaFieldMapTitle.nominate(
            RomImage(printer.bytes), selected, ParserCancellationToken.NONE, maximumOwners)
        fun literal(at: Int, register: Int, pool: Int, value: Int) {
            printer.literal(at, pool, value)
            printer.op(at, (printer.bytes[at].toInt() and 255) or 0x4800 or (register shl 8))
        }
        fun conditionalBranch(at: Int, condition: Int, target: Int) {
            val delta = target - at - 4
            require(condition in 0..13 && delta and 1 == 0 && delta in -256..254)
            printer.op(at, 0xd000 or (condition shl 8) or ((delta shr 1) and 0xff))
        }
        fun branch(at: Int, target: Int) {
            val delta = target - at - 4
            require(delta and 1 == 0 && delta in -2048..2046)
            printer.op(at, 0xe000 or ((delta shr 1) and 0x7ff))
        }
    }
}
