package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
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
    fun decodesUniqueAuthorizedStaticRegionTitle() {
        val f = Fixture()
        byteArrayOf(0xc2.toByte(), 0xc9.toByte(), 0xbf.toByte(), 0xc8.toByte(), 0xc8.toByte(), 0xff.toByte())
            .copyInto(f.printer.bytes, f.source)
        val result = CompiledGbaFieldMapTitleText.resolve(
            RomImage(f.printer.bytes), f.loader, PokemonTextCodec.gbaEnglish, ParserCancellationToken.NONE)
        assertEquals("HOENN", (result as CompiledGbaFieldMapTitleText.Result.Resolved).value)
    }

    @Test
    fun rejectsUnterminatedMalformedOrImplausibleStaticTitles() {
        val invalid = listOf(
            byteArrayOf(0xc2.toByte(), 0xc9.toByte(), 0x30, 0xff.toByte()),
            byteArrayOf(0xc2.toByte(), 0xfa.toByte(), 0xff.toByte()),
            byteArrayOf(0xdc.toByte(), 0xe3.toByte(), 0xd9.toByte(), 0xe2.toByte(), 0xe2.toByte(), 0xff.toByte()),
            ByteArray(64) { 0xbb.toByte() },
        )
        for (value in invalid) {
            val f = Fixture()
            value.copyInto(f.printer.bytes, f.source)
            assertTrue(
                "bytes=${value.joinToString { (it.toInt() and 255).toString(16) }}",
                CompiledGbaFieldMapTitleText.resolve(
                    RomImage(f.printer.bytes), f.loader, PokemonTextCodec.gbaEnglish,
                    ParserCancellationToken.NONE) is CompiledGbaFieldMapTitleText.Result.Unavailable,
            )
        }
    }

    @Test
    fun titleTextRequiresExactCodecUniqueNominationAndBothFlows() {
        fun Fixture.writeHoenn() {
            byteArrayOf(0xc2.toByte(), 0xc9.toByte(), 0xbf.toByte(), 0xc8.toByte(), 0xc8.toByte(), 0xff.toByte())
                .copyInto(printer.bytes, source)
        }
        val unsupported = Fixture().apply { writeHoenn() }
        assertTrue(CompiledGbaFieldMapTitleText.resolve(
            RomImage(unsupported.printer.bytes), unsupported.loader, PokemonTextCodec.gbEnglish,
            ParserCancellationToken.NONE) is CompiledGbaFieldMapTitleText.Result.Unavailable)

        val ambiguous = Fixture().apply {
            writeHoenn()
            addOwner(0x800, source + 16)
        }
        assertTrue(CompiledGbaFieldMapTitleText.resolve(
            RomImage(ambiguous.printer.bytes), ambiguous.loader, PokemonTextCodec.gbaEnglish,
            ParserCancellationToken.NONE) is CompiledGbaFieldMapTitleText.Result.Unavailable)

        val incomplete = Fixture().apply {
            writeHoenn()
            printer.op(owner + 0xbc, 0x46c0)
        }
        assertTrue(CompiledGbaFieldMapTitleText.resolve(
            RomImage(incomplete.printer.bytes), incomplete.loader, PokemonTextCodec.gbaEnglish,
            ParserCancellationToken.NONE) is CompiledGbaFieldMapTitleText.Result.Unavailable)
    }

    @Test
    fun nominatesRelocatedLegacyCenteredTitleWithIndependentAuthorities() {
        for (owner in listOf(0x400, 0x800, 0xc00)) {
            val f = LegacyFixture(owner)
            val declaration = (f.nominate() as CompiledGbaFieldMapTitle.Result.Complete).declarations.single()
            assertEquals(owner, declaration.owner)
            assertEquals(f.loader, declaration.loader)
            assertEquals(f.source, declaration.source)
            assertEquals(f.sharedWindow, declaration.window)
            assertEquals(f.printer, declaration.printer)
            assertEquals(f.frame, declaration.frame)
            assertNotNull(declaration.windowFlow)
            assertNotNull(declaration.ownerFlow)

            f.hoenn.copyInto(f.bytes, f.source)
            val resolved = CompiledGbaFieldMapTitleText.resolve(
                RomImage(f.bytes), f.loader, PokemonTextCodec.gbaEnglish, ParserCancellationToken.NONE)
            assertEquals("HOENN", (resolved as CompiledGbaFieldMapTitleText.Result.Resolved).value)
        }
    }

    @Test
    fun legacyConnectedDamageRemainsAnIncompleteContender() {
        for (relative in listOf(0x3c, 0x52, 0x58, 0x66, 0x68, 0x6a, 0x6c, 0x6e, 0x72, 0x74, 0x76, 0x78,
            0x7c, 0x7e, 0x80, 0x82, 0x84, 0x88, 0x8c, 0x92, 0x98, 0xa4, 0xa8, 0xaa, 0xac)) {
            val f = LegacyFixture()
            f.op(f.owner + relative, 0x46c0)
            assertTrue("legacy owner relative=$relative", f.nominate() is CompiledGbaFieldMapTitle.Result.Incomplete)
        }
    }

    @Test
    fun legacyConsumerRequiresSharedWindowAndCompleteCenteredSourceFlow() {
        for ((at, replacement) in listOf(
            0x10 to 0x46c0,
            0x1a to 0x46c0,
            0x1e to 0x46c0,
            0x22 to 0x46c0,
        )) {
            val f = LegacyFixture()
            f.op(f.printer + at, replacement)
            val declaration = (f.nominate() as CompiledGbaFieldMapTitle.Result.Complete).declarations.single()
            assertNull("legacy printer relative=$at", declaration.windowFlow)
            assertNull(declaration.ownerFlow)
        }
        val disconnected = LegacyFixture()
        disconnected.word(disconnected.printer + 0x2c, 0x02002000)
        val declaration = (disconnected.nominate() as CompiledGbaFieldMapTitle.Result.Complete).declarations.single()
        assertNull(declaration.windowFlow)
        assertNull(declaration.ownerFlow)
    }

    @Test
    fun legacyOwnerRejectsStaticSourceAliasingKnownCode() {
        for (source in listOf(0x400, 0x1000, 0x1200, 0x1400, 0x1600)) {
            val f = LegacyFixture()
            f.word(f.owner + 0xb8, 0x08000000 + source)
            val declaration = (f.nominate() as CompiledGbaFieldMapTitle.Result.Complete).declarations.single()
            assertEquals(source, declaration.source)
            assertNotNull(declaration.windowFlow)
            assertNull("legacy source=$source", declaration.ownerFlow)
        }
    }

    @Test
    fun legacyOwnersRemainAmbiguousAndRespectTheSharedBudget() {
        val f = LegacyFixture()
        f.addOwner(0x800, f.source + 16)
        val result = f.nominate() as CompiledGbaFieldMapTitle.Result.Complete
        assertEquals(setOf(f.source, f.source + 16), result.declarations.map { it.source }.toSet())
        assertTrue(f.nominate(maximumOwners = 1) is CompiledGbaFieldMapTitle.Result.Incomplete)
    }

    @Test
    fun nominatesInternationalFieldMapTitleFromThePublishedCompiledLayout() {
        val f = Fixture(international = true)
        f.printer.bytes[f.source] = 0xc2.toByte()
        f.printer.bytes[f.source + 1] = 0xc9.toByte()
        f.printer.bytes[f.source + 2] = 0xbf.toByte()
        f.printer.bytes[f.source + 3] = 0xc8.toByte()
        f.printer.bytes[f.source + 4] = 0xc8.toByte()
        f.printer.bytes[f.source + 5] = 0xff.toByte()

        val declaration = (f.nominate() as CompiledGbaFieldMapTitle.Result.Complete).declarations.single()
        assertEquals(f.source, declaration.source)
        assertNotNull(declaration.windowFlow)
        assertNotNull(declaration.ownerFlow)
        val resolved = CompiledGbaFieldMapTitleText.resolve(
            RomImage(f.printer.bytes), f.loader, PokemonTextCodec.gbaEnglish, ParserCancellationToken.NONE)
        assertEquals("HOENN", (resolved as CompiledGbaFieldMapTitleText.Result.Resolved).value)
    }

    @Test
    fun legacyOwnerMayReachTheSelectedAssetLoaderThroughItsPublishedWrapper() {
        val f = LegacyFixture()
        f.routeOwnerThroughLoaderWrapper()
        f.hoenn.copyInto(f.bytes, f.source)

        val declaration = (f.nominate() as CompiledGbaFieldMapTitle.Result.Complete).declarations.single()
        assertNotNull(declaration.ownerFlow)
        val resolved = CompiledGbaFieldMapTitleText.resolve(
            RomImage(f.bytes), f.loader, PokemonTextCodec.gbaEnglish, ParserCancellationToken.NONE)
        assertEquals("HOENN", (resolved as CompiledGbaFieldMapTitleText.Result.Resolved).value)
    }

    @Test
    fun malformedLegacyLoaderWrapperRemainsAnIncompleteContender() {
        val f = LegacyFixture()
        f.routeOwnerThroughLoaderWrapper()
        f.op(f.loaderWrapper + 16, 0xd0fa)
        assertTrue(f.nominate() is CompiledGbaFieldMapTitle.Result.Incomplete)
    }

    @Test
    fun internationalLayoutDamageCannotRetainTitleAuthority() {
        for (relative in listOf(0x7E, 0x84, 0x88, 0x9C, 0xA2, 0xB2, 0xB6, 0xC2, 0xD4, 0x158)) {
            val f = Fixture(international = true)
            f.printer.op(f.owner + relative, 0x46C0)
            val result = f.nominate()
            if (relative <= 0xC2) {
                assertTrue("owner relative=$relative", result is CompiledGbaFieldMapTitle.Result.Incomplete)
            } else {
                val declaration = (result as CompiledGbaFieldMapTitle.Result.Complete).declarations.single()
                assertNull("owner relative=$relative", declaration.ownerFlow)
            }
        }
    }

    @Test
    fun legacyWrapperCannotAliasTheStaticTitleSource() {
        val f = LegacyFixture()
        f.routeOwnerThroughLoaderWrapper()
        f.word(f.owner + 0xB8, 0x08000000 + f.loaderWrapper)
        val declaration = (f.nominate() as CompiledGbaFieldMapTitle.Result.Complete).declarations.single()
        assertNull(declaration.ownerFlow)
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
        assertThrows(ParserCancellationException::class.java) {
            CompiledGbaFieldMapTitleText.resolve(RomImage(ByteArray(0)), -1, null,
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

    private class LegacyFixture(val owner: Int = 0x400) {
        val bytes = ByteArray(0x4000)
        val loader = 0x1800
        val loaderWrapper = 0x1d00
        val source = 0x3000
        val frame = 0x1200
        val printer = 0x1000
        val sharedWindow = 0x02001000
        val hoenn = byteArrayOf(
            0xc2.toByte(), 0xc9.toByte(), 0xbf.toByte(), 0xc8.toByte(), 0xc8.toByte(), 0xff.toByte())
        private val centered = 0x1400
        private val initializer = 0x1600

        init {
            ops(frame, 0xb570, 0xb081, 0x1c04, 0x1c0d, 0x1c16, 0x0624, 0x0e24, 0x062d, 0x0e2d,
                0x0636, 0x0e36, 0x061b, 0x0e1b)
            literal(frame + 0x1a, 0, frame + 0x34, sharedWindow)
            ops(frame + 0x1c, 0x6800, 0x9300, 0x1c21, 0x1c2a, 0x1c33)
            bl(frame + 0x26, 0x1740)
            ops(frame + 0x2a, 0xb001, 0xbc70, 0xbc01, 0x4700)

            ops(printer, 0xb530, 0xb081, 0x1c05, 0x1c0b, 0x061b, 0x0e1b, 0x0612, 0x0e12)
            literal(printer + 0x10, 0, printer + 0x2c, sharedWindow)
            ops(printer + 0x12, 0x6800)
            literal(printer + 0x14, 1, printer + 0x30, 0x02001004)
            ops(printer + 0x16, 0x880c, 0x9200, 0x1c29, 0x1c22)
            bl(printer + 0x1e, centered)
            ops(printer + 0x22, 0xb001, 0xbc30, 0xbc01, 0x4700)

            ops(centered, 0xb510, 0xb081, 0x1c04, 0x9803, 0x0412, 0x0c12, 0x061b, 0x0e1b, 0x0600,
                0x0e00, 0x9000, 0x1c20)
            bl(centered + 0x18, initializer)
            ops(centered + 0x1c, 0x1c20)
            bl(centered + 0x1e, 0x1720)
            ops(centered + 0x22, 0x0600, 0x0e00, 0xb001, 0xbc10, 0xbc02, 0x4708)

            ops(initializer, 0xb510, 0xb081, 0x9c03, 0x0412, 0x0c12, 0x061b, 0x0e1b, 0x0624, 0x0e24,
                0x9400)
            bl(initializer + 0x14, 0x1700)
            ops(initializer + 0x18, 0xb001, 0xbc10, 0xbc01, 0x4700)
            addOwner(owner, source)
        }

        fun addOwner(owner: Int, source: Int) {
            ops(owner, 0xb500, 0xb081, 0x2080, 0x04c0, 0x2100, 0x8001, 0x3010, 0x8001,
                0x3002, 0x8001, 0x3002, 0x8001, 0x3002, 0x8001, 0x3002, 0x8001,
                0x3002, 0x8001, 0x3002, 0x8001, 0x3002, 0x8001)
            bl(owner + 0x2c, 0x1780)
            bl(owner + 0x30, 0x17a0)
            literal(owner + 0x34, 0, owner + 0xb0, 0x02000008)
            op(owner + 0x36, 0x2100)
            bl(owner + 0x38, loader)
            ops(owner + 0x3c, 0x2000, 0x2100)
            bl(owner + 0x40, 0x17c0)
            ops(owner + 0x44, 0x2001, 0x2101)
            bl(owner + 0x48, 0x17e0)
            op(owner + 0x4c, 0x2025)
            bl(owner + 0x4e, 0x1820)
            op(owner + 0x52, 0x2025)
            bl(owner + 0x54, 0x1840)
            bl(owner + 0x58, 0x1860)
            literal(owner + 0x5c, 1, owner + 0xb4, 0x04000008)
            ops(owner + 0x5e, 0x22f8, 0x0152, 0x1c10, 0x8008, 0x2015, 0x2100, 0x221d, 0x2303)
            bl(owner + 0x6e, frame)
            literal(owner + 0x72, 0, owner + 0xb8, 0x08000000 + source)
            ops(owner + 0x74, 0x2116, 0x2201)
            bl(owner + 0x78, printer)
            ops(owner + 0x7c, 0x2010, 0x2110, 0x221d, 0x2313)
            bl(owner + 0x84, frame)
            bl(owner + 0x88, 0x1880)
            literal(owner + 0x8c, 0, owner + 0xbc, 0x08001901)
            bl(owner + 0x8e, 0x18a0)
            literal(owner + 0x92, 0, owner + 0xc0, 0x08001921)
            bl(owner + 0x94, 0x18c0)
            ops(owner + 0x98, 0x2001, 0x4240, 0x2100, 0x9100, 0x2210, 0x2300)
            bl(owner + 0xa4, 0x18e0)
            ops(owner + 0xa8, 0xb001, 0xbc01, 0x4700)
        }

        fun routeOwnerThroughLoaderWrapper() {
            ops(loaderWrapper, 0xb500, 0x0609, 0x0e09)
            bl(loaderWrapper + 6, 0x1e00)
            bl(loaderWrapper + 10, loader)
            ops(loaderWrapper + 14, 0x0600, 0x2800, 0xd1fa, 0xbc01, 0x4700)
            bl(owner + 0x38, loaderWrapper)
        }

        fun nominate(maximumOwners: Int = 32) = CompiledGbaFieldMapTitle.nominate(
            RomImage(bytes), loader, ParserCancellationToken.NONE, maximumOwners)
        fun op(at: Int, value: Int) {
            bytes[at] = value.toByte()
            bytes[at + 1] = (value ushr 8).toByte()
        }
        fun ops(at: Int, vararg values: Int) = values.forEachIndexed { index, value -> op(at + index * 2, value) }
        fun word(at: Int, value: Int) = repeat(4) { bytes[at + it] = (value ushr (it * 8)).toByte() }
        fun literal(at: Int, register: Int, pool: Int, value: Int) {
            val delta = pool - ((at + 4) and -4)
            require(delta in 0..1020 && delta and 3 == 0)
            op(at, 0x4800 or (register shl 8) or (delta / 4))
            word(pool, value)
        }
        fun bl(at: Int, target: Int) {
            val delta = (target - at - 4) and 0x7fffff
            op(at, 0xf000 or (delta ushr 12))
            op(at + 2, 0xf800 or ((delta ushr 1) and 0x7ff))
        }
    }

    private class Fixture(val owner: Int = 0x400, private val international: Boolean = false) {
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
            val lateShift = if (international) 0xc else 0
            fun late(relative: Int) = owner + relative + lateShift
            val table = owner + 0x2c
            val initial = owner + 0x48
            val title = owner + 0x70
            val increment = late(0x13c)
            val end = late(0x17a)
            val dynamic = late(0x18c)
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
                printer.word(table + i * 4, 0x08000000 + owner + arm + if (i >= 2) lateShift else 0)
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

            if (international) {
                printer.ops(title, 0x2001, 0x2100, 0x2227, 0x230d)
                printer.bl(title + 8, 0x1480)
                literal(title + 12, 5, late(0xb8), 0x08000000 + source)
                printer.ops(title + 14, 0x2001, 0x1c29, 0x2238)
                printer.bl(title + 20, 0x1420)
                printer.ops(title + 24, 0x1c03, 0x061b, 0x0e1b, 0x2001, 0x9000, 0x2400,
                    0x9401, 0x9402, 0x2101, 0x1c2a)
                printer.bl(title + 44, printer.entry)
                printer.op(title + 48, 0x2000)
                printer.bl(title + 50, 0x1720)
                printer.ops(title + 54, 0x2000, 0x2100, 0x2227, 0x230d)
                printer.bl(title + 62, 0x1480)
                printer.bl(title + 66, dynamic)
                printer.ops(title + 70, 0x2001, 0x4240, 0x9400, 0x2100, 0x2210, 0x2300)
                branch(title + 82, late(0x130))
            } else {
                printer.ops(title, 0x2003, 0x2100, 0x2227, 0x230d)
                printer.bl(title + 8, 0x1480)
                literal(title + 12, 2, owner + 0xb8, 0x08000000 + source)
                printer.ops(title + 14, 0x2002, 0x9000, 0x2400, 0x9401, 0x9402, 0x2003, 0x2101, 0x2300)
                printer.bl(title + 30, printer.entry)
                printer.op(title + 34, 0x2000)
                printer.bl(title + 36, 0x1720)
                printer.ops(title + 40, 0x2000, 0x2100, 0x2227, 0x230d)
                printer.bl(title + 48, 0x1480)
                printer.bl(title + 52, dynamic)
                printer.ops(title + 56, 0x2001, 0x4240, 0x9400, 0x2100, 0x2210, 0x2300)
                branch(title + 68, late(0x130))
            }

            printer.ops(late(0xbc), 0x2182, 0x0149, 0x2000)
            printer.bl(late(0xc2), 0x1a00)
            printer.op(late(0xc6), 0x2000)
            printer.bl(late(0xc8), 0x1a20)
            printer.op(late(0xcc), 0x2002)
            printer.bl(late(0xce), 0x1a20)
            literal(late(0xd2), 0, late(0xdc), handlerRoot)
            printer.op(late(0xd4), 0x6801)
            literal(late(0xd6), 0, late(0xe0), stateOffset)
            printer.op(late(0xd8), 0x1809)
            branch(late(0xda), increment)

            literal(late(0xe4), 0, late(0xf4), fadeRoot)
            printer.ops(late(0xe6), 0x79c1, 0x2080, 0x4008, 0x2800)
            conditionalBranch(late(0xee), 1, end)
            printer.op(late(0xf0), 0x6821)
            branch(late(0xf2), late(0x138))

            printer.bl(late(0xf8), 0x1a40)
            printer.ops(late(0xfc), 0x0600, 0x0e00, 0x2803)
            conditionalBranch(late(0x102), 1, late(0x10a))
            printer.bl(late(0x104), dynamic)
            branch(late(0x108), end)
            printer.op(late(0x10a), 0x2803)
            conditionalBranch(late(0x10c), 0xb, end)
            printer.op(late(0x10e), 0x2805)
            conditionalBranch(late(0x110), 0xc, end)
            literal(late(0x112), 0, late(0x11c), handlerRoot)
            printer.op(late(0x114), 0x6801)
            literal(late(0x116), 0, late(0x120), stateOffset)
            printer.op(late(0x118), 0x1809)
            branch(late(0x11a), increment)

            printer.ops(late(0x124), 0x2001, 0x4240, 0x2100, 0x9100, 0x2200, 0x2310)
            printer.bl(late(0x130), 0x1760)
            literal(late(0x134), 0, late(0x144), handlerRoot)
            printer.op(late(0x136), 0x6801)
            literal(late(0x138), 2, late(0x148), stateOffset)
            printer.op(late(0x13a), 0x1889)
            printer.ops(increment, 0x8808, 0x3001, 0x8008)
            branch(increment + 6, end)

            literal(late(0x14c), 0, late(0x184), fadeRoot)
            printer.ops(late(0x14e), 0x79c1, 0x2080, 0x4008, 0x0600, 0x0e05, 0x2d00)
            conditionalBranch(late(0x15a), 1, end)
            printer.bl(late(0x15c), 0x1a60)
            literal(late(0x160), 4, late(0x188), handlerRoot)
            printer.ops(late(0x162), 0x6820, 0x6800)
            printer.bl(late(0x166), 0x1a80)
            printer.ops(late(0x16a), 0x6820, 0x2800)
            conditionalBranch(late(0x16e), 0, late(0x176))
            printer.bl(late(0x170), 0x1aa0)
            printer.op(late(0x174), 0x6025)
            printer.bl(late(0x176), 0x1ac0)
            printer.ops(end, 0xb003, 0xbc30, 0xbc01, 0x4700)

            printer.word(late(0xdc), handlerRoot)
            printer.word(late(0xe0), stateOffset)
            printer.word(late(0xf4), fadeRoot)
            printer.word(late(0x11c), handlerRoot)
            printer.word(late(0x120), stateOffset)
            printer.word(late(0x144), handlerRoot)
            printer.word(late(0x148), stateOffset)
            printer.word(late(0x184), fadeRoot)
            printer.word(late(0x188), handlerRoot)
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
