package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiTextObligation
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.text.WesternPokemonTextCodecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Gen3BackgroundEventContractTest {
    @Test
    fun genuineNullUnionsUseTheCompiledDefaultBeforeKindDispatchAfterRelocation() {
        for (origin in listOf(0, 0x2000, 0x4000)) for (kind in (0..8) + 255) {
            val f = fixture(origin)
            f.bytes[f.background + 5] = kind.toByte()
            val poi = f.resolve()[0]
            assertEquals("local/1/bg/0", poi.key)
            assertEquals(LocalMapPoiTextObligation.DIRECT_TEXT, poi.textObligation)
            assertEquals("AB", poi.displayName)
            assertNull(poi.item)
        }
    }

    @Test
    fun hiddenFlagsUseTheCompiledBaseAndSixteenBitStoreRatherThanAConstant() {
        for (origin in listOf(0, 0x2000)) for ((immediate, shift) in listOf(250 to 1, 250 to 2, 128 to 3)) {
            for (index in listOf(0, 7, 0xffff)) {
                val f = fixture(origin)
                f.half(f.caller + 0x8e, 0x2300 or immediate)
                f.half(f.caller + 0x90, 0x001b or (shift shl 6))
                f.half(f.background + 12 + 10, index)
                val item = f.resolve()[1]
                assertEquals(25, item.item?.itemId)
                assertEquals(((immediate shl shift) + index) and 0xffff, item.item?.collectionFlagId)
                assertEquals(LocalMapPoiTextObligation.ITEM_NAME, item.textObligation)
            }
        }
    }

    @Test
    fun missingEmeraldConsumerPreservesItemsWithoutInventingACollectionFlag() {
        val f = fixture()
        f.half(f.caller, 0)
        val pois = f.resolve()
        assertNull(pois[0].displayName)
        assertEquals(25, pois[1].item?.itemId)
        assertNull(pois[1].item?.collectionFlagId)
    }

    @Test
    fun secretBaseKindAloneIsNotPositiveNativeNoTextAuthority() {
        val f = fixture()
        assertEquals(LocalMapPoiTextObligation.UNRESOLVED, f.resolve()[2].textObligation)
        assertNull(f.resolve()[2].displayName)
    }

    @Test
    fun invalidNonzeroPointersNeverBorrowTheNullUnionDefault() {
        for (raw in listOf(1L, 0xffffffffL, 0x08000000L + 0x100000)) {
            val f = fixture()
            f.word(f.background + 8, raw)
            assertUnavailable(f)
        }
    }

    @Test
    fun explicitSignScriptsRetainPrecedenceEvenWithAConflictingDefaultConsumer() {
        val f = fixture()
        f.message(f.script + 0x40, f.text + 0x40)
        f.pointer(f.background + 8, f.script + 0x40)
        f.bytes[f.text + 0x40] = 0xbd.toByte()
        f.bytes[f.text + 0x41] = 0xbe.toByte()
        f.bytes[f.text + 0x42] = 0xff.toByte()
        f.installCaller(f.extraCaller, f.script + 0x40)
        assertEquals("CD", f.resolve()[0].displayName)
    }

    @Test
    fun alteredFinderFieldsStrideAndReturnCannotQualifyTheDefaultOrFlags() {
        for (at in listOf(0x0e, 0x10, 0x12, 0x1a, 0x1c, 0x1e, 0x22, 0x28, 0x2e, 0x34, 0x38, 0x4c)) {
            val f = fixture()
            f.half(f.finder + at, 0)
            assertUnprovedContract(f)
        }
    }

    @Test
    fun changedCallerEffectsBranchesAndReturnsFailClosed() {
        for (at in listOf(0x24, 0x26, 0x28, 0x2a, 0x2c, 0x38, 0x3a, 0x46, 0x8c, 0x92, 0x94, 0x98, 0x9a, 0xa4, 0xda, 0xde)) {
            val f = fixture()
            f.half(f.caller + at, 0)
            assertUnprovedContract(f)
        }
    }

    @Test
    fun unqualifiedFlagPredicateOrStoragePointerInvalidatesTheCoupledContract() {
        for (at in listOf(0, 2, 4, 0x10, 0x12, 0x14, 0x16, 0x1a, 0x20, 0x2a)) {
            val f = fixture()
            f.half(f.flagGet + at, 0)
            assertUnprovedContract(f)
        }
        for (at in listOf(0x08, 0x12, 0x18, 0x20, 0x32, 0x3c, 0x42, 0x44)) {
            val f = fixture()
            f.half(f.flagPointer + at, 0)
            assertUnprovedContract(f)
        }
    }

    @Test
    fun wrongSwitchTargetsAndUnlinkedCallsDoNotBorrowNearbyCode() {
        for (kind in 0..8) {
            val f = fixture()
            f.pointer(f.caller + 0x4c + kind * 4, f.caller + 0xd8)
            if (kind == 0) f.pointer(f.caller + 0x4c, f.caller + 0x70)
            assertUnprovedContract(f)
        }
        for (at in listOf(0x1c, 0x9c)) {
            val f = fixture()
            f.branch(f.caller + at, f.script)
            assertUnprovedContract(f)
        }
        val f = fixture()
        f.branch(f.flagGet + 8, f.finder)
        assertUnprovedContract(f)
    }

    @Test
    fun invalidRamLiteralRelationshipsAreNotNativeConsumerAuthority() {
        for ((at, value) in listOf(0x30 to 0L, 0xac to 0x08000100L, 0xb0 to 0x02001204L, 0xd0 to 0x02001300L)) {
            val f = fixture()
            f.word(f.caller + at, value)
            assertUnprovedContract(f)
        }
    }

    @Test
    fun competingValidOrMalformedConsumersCannotSelectAConvenientDefault() {
        for (malformed in listOf(false, true)) {
            val f = fixture()
            f.message(f.script + 0x40, f.text)
            f.installCaller(f.extraCaller, f.script + 0x40)
            if (malformed) f.half(f.extraCaller + 0x94, 0)
            assertUnprovedContract(f)
        }
    }

    @Test
    fun equivalentConsumersCanAgreeOnOneContractWithoutDependingOnTheirLocations() {
        val f = fixture(0x2000)
        f.installCaller(f.extraCaller, f.script)
        assertEquals("AB", f.resolve()[0].displayName)
        assertEquals(507, f.resolve()[1].item?.collectionFlagId)
    }

    @Test
    fun candidateAndImageBudgetsFailClosedRatherThanStoppingAtTheFirstMatch() {
        val f = fixture()
        val expanded = f.bytes.copyOf(0x8000)
        val many = Fixture(expanded, 0)
        repeat(33) { many.installCaller(0x1000 + it * 0x100, many.script) }
        assertUnprovedContract(many)
        assertUnprovedContract(Fixture(f.bytes.copyOf(32 * 1024 * 1024 + 1), 0))
    }

    @Test
    fun defaultBodiesRequireBufferedSlotZeroStandardThreeAndACompleteEnd() {
        for ((at, value) in listOf(0 to 0, 1 to 1, 6 to 4, 7 to 1, 8 to 3)) {
            val f = fixture()
            f.bytes[f.script + at] = value.toByte()
            assertUnprovedContract(f)
        }
        val f = fixture()
        f.pointer(f.caller + 0x34, f.bytes.size - 8)
        assertUnprovedContract(f)
    }

    @Test
    fun unterminatedAndContextualDefaultHeadlinesDoNotPublishAStaticLabel() {
        val unterminated = fixture()
        unterminated.bytes.fill(0xbb.toByte(), unterminated.text, unterminated.text + 160)
        assertNull(unterminated.resolve()[0].displayName)
        val contextual = fixture()
        byteArrayOf(0xbb.toByte(), 0xfd.toByte(), 2, 0xff.toByte()).copyInto(contextual.bytes, contextual.text)
        assertNull(contextual.resolve()[0].displayName)
        assertEquals(LocalMapPoiTextObligation.CONTEXTUAL_TEXT, contextual.resolve()[0].textObligation)
    }

    @Test
    fun unqualifiedFamiliesDoNotBorrowTheEmeraldContract() {
        for (family in listOf(EngineFamily.RUBY_SAPPHIRE, EngineFamily.FIRERED_LEAFGREEN)) {
            val pois = fixture().resolve(family)
            assertNull(pois[0].displayName)
            assertEquals(1007, pois[1].item?.collectionFlagId)
            assertEquals(LocalMapPoiTextObligation.NO_TEXT, pois[2].textObligation)
        }
    }

    @Test(expected = ParserCancellationException::class)
    fun cancellationDuringConsumerDiscoveryEscapesOptionalMapFailureHandling() {
        var checks = 0
        fixture(0x2000).resolve(cancellation = ParserCancellationToken {
            if (++checks == 4) throw ParserCancellationException()
        })
    }

    @Test
    fun truncatedBodiesLinkedHelpersAndLiteralTargetsDoNotQualifyAPeer() {
        for (target in listOf(0, 1, 0x0fff, 0x1000)) {
            val f = fixture()
            f.pointer(f.caller + 0x34, target)
            assertUnprovedContract(f)
        }
        val finder = fixture()
        finder.branch(finder.caller + 0x1c, finder.bytes.size - 40)
        assertUnprovedContract(finder)
        val caller = fixture()
        val truncated = caller.bytes.size - 28
        caller.bytes.copyInto(caller.bytes, truncated, caller.caller, caller.caller + 28)
        assertUnprovedContract(caller)
    }

    @Test
    fun malformedBaseExpressionsAndFlagStorageLiteralsRemainUnavailable() {
        for ((at, value) in listOf(0x8e to 0x22fa, 0x90 to 0x005a, 0x90 to (0x001b or (16 shl 6)))) {
            val f = fixture()
            f.half(f.caller + at, value)
            assertUnprovedContract(f)
        }
        for ((at, value) in listOf(0x24 to 0x4000L, 0x28 to 0x08000000L, 0x2c to 0L,
            0x48 to 0xffffc001L, 0x4c to 0xffffc006L, 0x50 to 0x08000000L)) {
            val f = fixture()
            f.word(f.flagPointer + at, value)
            assertUnprovedContract(f)
        }
    }

    @Test
    fun oneMapResolutionDiscoversItsConsumerOnlyOnceForMultipleBackgrounds() {
        var checks = 0
        val points = fixture(0x2000).resolve(cancellation = ParserCancellationToken { checks++ })
        assertEquals("AB", points[0].displayName)
        assertEquals(507, points[1].item?.collectionFlagId)
        assertEquals(8, checks)
    }

    private fun assertUnavailable(f: Fixture) {
        val poi = f.resolve()[0]
        assertEquals("local/1/bg/0", poi.key)
        assertEquals(LocalMapPoiTextObligation.UNRESOLVED, poi.textObligation)
        assertNull(poi.displayName)
    }

    private fun assertUnprovedContract(f: Fixture) {
        assertUnavailable(f)
        assertNull(f.resolve()[1].item?.collectionFlagId)
    }

    private fun fixture(origin: Int = 0) = Fixture(ByteArray(origin + 0x1000), origin).apply {
        pointer(header + 4, events)
        bytes[events + 3] = 3
        pointer(events + 16, background)
        repeat(3) { i -> half(background + i * 12, i + 1); half(background + i * 12 + 2, 2) }
        bytes[background + 12 + 5] = 7
        half(background + 12 + 8, 25)
        half(background + 12 + 10, 7)
        bytes[background + 24 + 5] = 8
        word(background + 24 + 8, 72)
        message(script, text)
        byteArrayOf(0xbb.toByte(), 0xbc.toByte(), 0xff.toByte()).copyInto(bytes, text)
        words(finder, 0xb5f0, 0x0409, 0x0c0f, 0x0412, 0x0c16, 0x061b, 0x0e1d, 0x6840,
            0x6904, 0x78c3, 0x2200, 0x429a, 0xd215, 0x0050, 0x1880, 0x0080,
            0x1901, 0x8808, 0x42b8, 0xd109, 0x8848, 0x42b0, 0xd106, 0x7908,
            0x42a8, 0xd001, 0x2800, 0xd101, 0x1c08, 0xe005, 0x1c50, 0x0600,
            0x0e02, 0x429a, 0xd3e9, 0x2000, 0xbcf0, 0xbc02, 0x4708)
        words(flagGet, 0xb510, 0x0400, 0x0c04, 0x1c20)
        branch(flagGet + 8, flagPointer)
        words(flagGet + 0x0c, 0x2800, 0xd009, 0x7800, 0x2107, 0x4021, 0x4108, 0x2101,
            0x4008, 0x2800, 0xd001, 0x2001, 0xe000, 0x2000, 0xbc10, 0xbc02, 0x4708)
        words(flagPointer, 0xb500, 0x0401, 0x0c0a, 0x1c13, 0x2a00, 0xd101, 0x2000, 0xe018,
            0x4804, 0x4282, 0xd80c, 0x4804, 0x0cc9, 0x4a04, 0x1889, 0x6800, 0xe00e, 0)
        word(flagPointer + 0x24, 0x3fff)
        word(flagPointer + 0x28, 0x03001200)
        word(flagPointer + 0x2c, 0x200)
        words(flagPointer + 0x30, 0x4905, 0x1858, 0x2800, 0xda01, 0x4a04, 0x1898, 0x10c0,
            0x4904, 0x1840, 0xbc02, 0x4708, 0)
        word(flagPointer + 0x48, 0xffffc000L)
        word(flagPointer + 0x4c, 0xffffc007L)
        word(flagPointer + 0x50, 0x02001400)
        installCaller(caller, script)
    }

    private class Fixture(val bytes: ByteArray, origin: Int) {
        val header = origin + 0x100
        val events = origin + 0x180
        val background = origin + 0x240
        val caller = origin + 0x400
        val finder = origin + 0x700
        val flagGet = origin + 0x780
        val flagPointer = origin + 0x800
        val script = origin + 0xb01
        val text = origin + 0xb81
        val extraCaller = origin + 0xc00

        fun half(at: Int, value: Int) { repeat(2) { bytes[at + it] = (value ushr (8 * it)).toByte() } }
        fun words(at: Int, vararg values: Int) { values.forEachIndexed { i, value -> half(at + i * 2, value) } }
        fun word(at: Int, value: Long) { repeat(4) { bytes[at + it] = (value ushr (8 * it)).toByte() } }
        fun pointer(at: Int, target: Int) = word(at, 0x08000000L + target)
        fun branch(at: Int, target: Int) {
            val displacement = target - at - 4
            half(at, 0xf000 or ((displacement shr 12) and 0x7ff))
            half(at + 2, 0xf800 or ((displacement shr 1) and 0x7ff))
        }
        fun message(at: Int, target: Int) {
            bytes[at] = 15; bytes[at + 1] = 0; pointer(at + 2, target)
            bytes[at + 6] = 9; bytes[at + 7] = 3; bytes[at + 8] = 2
        }
        fun installCaller(at: Int, fallback: Int) {
            words(at, 0xb530, 0x0612, 0x0e15, 0x4c0a, 0x8801, 0x3907, 0x0409, 0x0c09,
                0x8842, 0x3a07, 0x0412, 0x0c12, 0x7903, 0x1c20)
            branch(at + 0x1c, finder)
            words(at + 0x20, 0x2800, 0xd027, 0x6882, 0x2a00, 0xd106, 0x4802, 0xe055)
            word(at + 0x30, 0x02001100); pointer(at + 0x34, fallback)
            words(at + 0x38, 0x7940, 0x2808, 0xd84c, 0x0080, 0x4901, 0x1840, 0x6800, 0x4687)
            pointer(at + 0x48, at + 0x4c)
            listOf(0xd8, 0x70, 0x78, 0x7e, 0x84, 0x8a, 0x8a, 0x8a, 0xb8).forEachIndexed { i, target ->
                pointer(at + 0x4c + i * 4, at + target)
            }
            words(at + 0x70, 0x2d02, 0xd031, 0x2000, 0xe030, 0x2d01, 0xd02d, 0xe7fa,
                0x2d04, 0xd02a, 0xe7f7, 0x2d03, 0xd027, 0xe7f4, 0x4908, 0x0c10,
                0x23fa, 0x005b, 0x18c0, 0x8008, 0x4806, 0x8002, 0x8808)
            branch(at + 0x9c, flagGet)
            words(at + 0xa0, 0x0600, 0x0e00, 0x2801, 0xd0e5, 0x4802, 0xe016)
            word(at + 0xac, 0x02001200); word(at + 0xb0, 0x02001202); pointer(at + 0xb4, script + 0x60)
            words(at + 0xb8, 0x2d02, 0xd1db, 0x4804, 0x8002)
            branch(at + 0xc0, script + 0x80)
            words(at + 0xc4, 0x0600, 0x2800, 0xd0d4, 0x4802, 0xe005)
            word(at + 0xd0, 0x02001200); pointer(at + 0xd4, script + 0xa0)
            words(at + 0xd8, 0x1c10, 0xbc30, 0xbc02, 0x4708)
        }
        fun resolve(
            family: EngineFamily = EngineFamily.EMERALD,
            cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
        ) = Gen3LocalMapPoiResolver.resolve(
            RomImage(bytes), mapOf(1 to header),
            listOf(LocalMap("local/1", null, 1, 160, 160, 10, 10, "asset/1")),
            family, WesternPokemonTextCodecs.gen3English, cancellation,
        ).also { assertEquals(emptyList<String>(), it.skippedReasons) }.pois
    }
}
