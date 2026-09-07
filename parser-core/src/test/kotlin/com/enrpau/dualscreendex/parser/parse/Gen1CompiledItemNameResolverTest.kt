package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.*
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.*
import org.junit.Assert.*
import org.junit.Test

class Gen1CompiledItemNameResolverTest {
    @Test fun derivesRelocatedBankDirectoryAndMachineContractsWithoutNames() {
        for (yellow in listOf(false, true)) for (shift in listOf(0, 0x180)) {
            val fixture = Gen1ItemFixture(yellow, shift)
            val resolver = Gen1CompiledItemNameResolver(fixture.session())
            val result = resolver.original()
            assertTrue("complete relocated consumer", result is GbItemNameAuthority.Available)
            result as GbItemNameAuthority.Available
            assertEquals(fixture.root, result.root)
            assertEquals(0xc000, result.bankEnd)
            assertEquals(20, result.copyBytes)
            assertEquals(fixture.prefix, result.lowerPrefix)
            assertEquals(fixture.prefix + 8, result.upperPrefix)
            assertEquals(6, result.lowerPrefixBytes)
            assertEquals(5, result.upperPrefixBytes)
            assertSame(result, resolver.original())
        }
    }
    @Test fun malformedOrCompetingWrappersCannotDisappear() {
        for (malformed in listOf(false, true)) {
            val f = Gen1ItemFixture()
            f.bytes.copyInto(f.bytes, 0x3000, f.wrapper, f.wrapper + 36)
            if (malformed) f.bytes[0x3000 + 26] = 0
            assertTrue(Gen1CompiledItemNameResolver(f.session()).original() is GbItemNameAuthority.Unavailable)
        }
    }
    @Test fun truncatedCompetingNominationAtLastPossibleRomZeroOffsetCannotDisappear() {
        val f = Gen1ItemFixture()
        f.bytes.copyInto(f.bytes, 0x3ff6, f.wrapper, f.wrapper + 10)
        assertTrue("complete nomination at ROM0 boundary must compete even when its body is truncated",
            Gen1CompiledItemNameResolver(f.session()).original() is GbItemNameAuthority.Unavailable)
    }

    @Test fun rejectsBrokenCopyBranchesAndInputAliasing() {
        for ((at, value) in listOf(0x1000 + 7 to 0, 0x400 + 31 to 0x10, 0x800 + 8 to 0, 0xc00 + 10 to 0)) {
            val f = Gen1ItemFixture(); f.bytes[at] = value.toByte()
            assertTrue("mutation at $at", Gen1CompiledItemNameResolver(f.session()).original() is GbItemNameAuthority.Unavailable)
        }
    }
    @Test fun budgetAndCancellationWithholdBeforePublicationAndOnCachedResult() {
        val f = Gen1ItemFixture()
        assertTrue(Gen1CompiledItemNameResolver(f.session(ResolutionLimits(maxProbeWorkPerDataset = 4))).original() is GbItemNameAuthority.Unavailable)
        var cancelled = false
        val session = f.session(cancellation = ParserCancellationToken { if (cancelled) throw ParserCancellationException() })
        val resolver = Gen1CompiledItemNameResolver(session)
        assertTrue(resolver.original() is GbItemNameAuthority.Available)
        cancelled = true
        assertThrows(ParserCancellationException::class.java) { resolver.original() }
    }
}

internal class Gen1ItemFixture(yellow: Boolean = false, shift: Int = 0) {
    val bytes = ByteArray(0x10000)
    val wrapper = 0x400 + shift
    val packed = 0x800 + shift
    val machine = 0xc00 + shift
    val copier = 0x1000 + shift
    val bankHelper = 0x1400 + shift
    val directory = 0x1800 + shift
    val prefix = 0x1c00 + shift
    val root = 0x9000 + shift
    init {
        val addresses = mapOf(0xd0e3 to 0xd210, 0xd092 to 0xd220, 0xd093 to 0xd221, 0xd094 to 0xd222,
            0xcd68 to 0xc400, 0xcf74 to 0xcf10, 0xcf75 to 0xcf11,
            (if (yellow) 0x3784 else 0x37a1) to packed,
            (if (yellow) 0x2f7d else 0x1aef) to machine,
            (if (yellow) 0x16df else 0x01bb) to copier,
            0x16ef to copier + 16, 0x3e78 to bankHelper,
            (if (yellow) 0x3776 else 0x3793) to directory,
            (if (yellow) 0x2fcb else 0x1b3d) to prefix,
            (if (yellow) 0x2fc6 else 0x1b38) to prefix + 8)
        fun code(at: Int, hex: String) {
            val raw = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            var cursor = 0
            while (cursor < raw.size) {
                val op = raw[cursor].toInt() and 255
                val length = when (op) {
                    0xfa, 0xea, 0xcd, 0x01, 0x11, 0x21, 0xd2 -> 3
                    0xfe, 0xf0, 0xe0, 0x3e, 0x20, 0x18, 0x30, 0x38, 0x28, 0x16, 0x0e, 0x06, 0xc6, 0xd6 -> 2
                    else -> 1
                }
                if (length == 3) {
                    val old = (raw[cursor + 1].toInt() and 255) or ((raw[cursor + 2].toInt() and 255) shl 8)
                    addresses[old]?.let { value -> raw[cursor + 1] = value.toByte(); raw[cursor + 2] = (value ushr 8).toByte() }
                }
                cursor += length
            }
            raw.copyInto(bytes, at)
        }
        code(wrapper, if (yellow) "e5c5fae3d0fec43012ea92d03e04ea93d03e01ea94d0cd84371803cd7d2f1168cdc1e1c9"
            else "e5c5fae3d0fec43012ea92d03e04ea93d03e01ea94d0cda1371803cdef1a1168cdc1e1c9")
        bytes[wrapper + 13] = 6; bytes[wrapper + 18] = 2
        code(packed, if (yellow) "fa92d0eae3d0fec4d27d2ff0b8f5e5c5d5fa93d03d200bcd2b2f210600195d54183efa94d0cd783efa93d03d8716005f300114217637192ae0967ee095f09567f0966ffa92d0470e00545d2afe5020fb0c78b920f4626b1168cd011400cddf167bea74cf7aea75cfd1c1e1f1cd783ec9"
            else "fa92d0eae3d0fec4d2ef1af0b8f5e5c5d5fa93d03d200bcd991a210600195d541840fa94d0e0b8ea0020fa93d03d8716005f300114219337192ae0967ee095f09567f0966ffa92d0470e00545d2afe5020fb0c78b920f4626b1168cd011400cdbb017bea74cf7aea75cfd1c1e1f1e0b8ea0020c9")
        code(machine, if (yellow) "e5d5c5fae3d0f5fec9300dc605eae3d021cb2f010600180621c62f0105001168cdcddf16fae3d0d6c806f6d60a38030418f9c60af5781213f106f68012133e5012f1eae3d0c1d1e1c9"
            else "e5d5c5fae3d0f5fec9300dc605eae3d0213d1b010600180621381b0105001168cdcdbb01fae3d0d6c806f6d60a38030418f9c60af5781213f106f68012133e5012f1eae3d0c1d1e1c9")
        code(copier, if (yellow) "78a7280c79a7280104cdef160520fac92a12130d20fac9" else "2a12130b79b020f8c9")
        code(bankHelper, "e0b8ea0020c9")
        val cpu = root - 0x8000 + 0x4000
        bytes[directory + 10] = cpu.toByte(); bytes[directory + 11] = (cpu ushr 8).toByte()
        // Instruction tests need no readable payload; materializer tests explicitly populate these spans.
    }
    fun session(limits: ResolutionLimits = ResolutionLimits(), cancellation: ParserCancellationToken = ParserCancellationToken.NONE) =
        RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GB, "unrelated"), limits = limits, cancellation = cancellation)
}
