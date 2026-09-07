package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.*
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.*
import org.junit.Assert.*
import org.junit.Test

class Gen2CompiledItemNameResolverTest {
    @Test fun relocatedOriginalConsumerNeedsNoReadableNames() {
        for (hram in listOf(false, true)) for (shift in listOf(0, 0x100)) {
            val f = Gen2ItemFixture(hram, shift)
            val (resolver, result) = original(f.session())
            assertEquals("Available", result.javaClass.simpleName)
            assertEquals(f.root, result.javaClass.getMethod("getRoot").invoke(result))
            assertEquals(11, result.javaClass.getMethod("getCopyBytes").invoke(result))
            assertSame(result, resolver.javaClass.getMethod("original").invoke(resolver))
        }
    }

    @Test fun competingIncompleteAndBoundaryWrappersAreTerminal() {
        for (at in listOf(0x3000, 0x3ff6)) {
            val f = Gen2ItemFixture()
            f.bytes.copyInto(f.bytes, at, f.at("wrapper"), f.at("wrapper") + 10)
            assertEquals("Unavailable", original(f.session()).second.javaClass.simpleName)
        }
    }

    @Test fun selectedPointerCopySkipAndFarCallDependenciesFailClosed() {
        for ((symbol, offset) in listOf("copier" to 0, "nth" to 8, "helper" to 9, "farCall" to 0,
            "bankRst" to 0, "callHL" to 0, "generated" to 8, "getName" to 0)) {
            val f = Gen2ItemFixture()
            assertEquals("Available", original(f.session()).second.javaClass.simpleName)
            f.bytes[f.at(symbol) + offset] = 0
            assertEquals("mutation $symbol+$offset", "Unavailable", original(f.session()).second.javaClass.simpleName)
        }
    }

    @Test fun budgetsCancellationAndUnavailableOriginalAreTerminal() {
        for (limits in listOf(ResolutionLimits(maxProbeWorkPerDataset = 100), ResolutionLimits(maxDatasetExtentBytes = 16))) {
            val resolver = Gen2CompiledItemNameResolver(Gen2ItemFixture().session(limits))
            val result = resolver.original()
            assertTrue(result is Gen2ItemNameAuthority.Unavailable)
            assertSame(result, resolver.original())
        }
        var remaining = 100
        var armed = true
        val resolver = Gen2CompiledItemNameResolver(Gen2ItemFixture().session(cancellation = ParserCancellationToken {
            if (armed && --remaining == 0) throw ParserCancellationException()
        }))
        assertThrows(ParserCancellationException::class.java) { resolver.original() }
        armed = false
        val cancelled = resolver.original()
        assertTrue(cancelled is Gen2ItemNameAuthority.Unavailable)
        assertSame(cancelled, resolver.original())
        val authority = Gen2CompiledItemNameResolver(Gen2ItemFixture().session()).original() as Gen2ItemNameAuthority.Available
        assertThrows(UnsupportedOperationException::class.java) { (authority.codeOffsets as MutableSet).clear() }
    }

    @Test fun selectedDirectoryOutputScratchAndStoreOrderCannotBorrowAuthority() {
        fun replace(f: Gen2ItemFixture, routine: String, old: String, new: String) {
            val from = old.split(' ').map { it.toInt(16).toByte() }.toByteArray()
            val to = new.split(' ').map { it.toInt(16).toByte() }.toByteArray()
            assertEquals(from.size, to.size)
            val matches = (f.at(routine) until f.at(routine) + 128 - from.size).filter { at ->
                from.indices.all { f.bytes[at + it] == from[it] }
            }
            assertTrue(matches.isNotEmpty())
            matches.forEach { to.copyInto(f.bytes, it) }
        }
        val mutations: List<Pair<String, (Gen2ItemFixture) -> Unit>> = listOf(
            "bank-zero switchable pointer" to { f -> f.bytes[f.at("directory") + 15] = 0 },
            "unmapped pointer" to { f -> f.word(f.at("directory") + 16, 0x8000) },
            "copy count overflow" to { f -> replace(f, "getName", "01 0B 00 CD", "01 41 00 CD") },
            "returned C aliases B" to { f -> replace(f, "farCall", "21 CF", "20 CF") },
            "return low aliases output" to { f -> replace(f, "getName", "10 CF", "00 C4") },
            "scratch aliases object" to { f -> replace(f, "farCall", "30 D2", "10 D2") },
            "restore bank loses returned C" to { f -> replace(f, "farCall", "C1 78 D7", "C1 79 D7") },
            "prefix copy exceeds bound" to { f -> replace(f, "generated", "01 05 00", "01 21 00") },
            "helper bank missing" to { f -> replace(f, "generated", "3E 02 CF", "3E 00 CF") },
            "saved classification flags missing" to { f -> replace(f, "generated", "D1 F1 79", "D1 00 79") },
            "skip order contradictory" to { f -> replace(f, "helper", "FE D2", "FE B8") },
        )
        for ((label, mutate) in mutations) {
            val f = Gen2ItemFixture(); mutate(f)
            assertTrue(label, Gen2CompiledItemNameResolver(f.session()).original() is Gen2ItemNameAuthority.Unavailable)
        }
    }

    private fun original(session: RomAnalysisSession): Pair<Any, Any> {
        val type = runCatching { Class.forName("com.enrpau.dualscreendex.parser.parse.Gen2CompiledItemNameResolver") }.getOrNull()
        assertTrue("separate original GenII compiled item authority must exist", type != null)
        val resolver = type!!.getDeclaredConstructor(RomAnalysisSession::class.java).newInstance(session)
        return resolver to type.getMethod("original").invoke(resolver)
    }
}

internal class Gen2ItemFixture(hram: Boolean = false, shift: Int = 0) {
    val bytes = ByteArray(0x14000)
    val symbols = mutableMapOf("wrapper" to 0x400 + shift, "getName" to 0x800 + shift, "nth" to 0xc00 + shift,
        "copier" to 0x1000 + shift, "generated" to 0x1400 + shift, "farCall" to 0x1800 + shift,
        "callHL" to 0x1c00 + shift, "directory" to 0x2000 + shift, "tmPrefix" to 0x2400 + shift,
        "hmPrefix" to 0x2420 + shift, "helper" to 0x9000 + shift, "helperAddress" to 0x5000 + shift,
        "object" to 0xd210, "index" to 0xd220, "selectorSlot" to 0xd221, "destination" to 0xc400,
        "returnLow" to 0xcf10, "returnHigh" to 0xcf11, "farScratch" to 0xd230, "savedB" to 0xcf20,
        "savedC" to 0xcf21, "bankRst" to 0x10)
    val root = 0x11000 + shift
    init {
        emit("wrapper", "E5 C5 FA @object FE B5 30 ~genBranch EA @index 3E 06 EA @selectorSlot CD @getName 18 ~copied :genBranch CD @generated :copied 11 @destination C1 E1 C9")
        emit("getName", "F0 A0 F5 E5 C5 D5 FA @selectorSlot FE 01 20 ~ordinary 00 00 00 :ordinary FA @selectorSlot 3D 5F 16 00 21 @directory 19 19 19 2A D7 2A 66 6F FA @index 3D CD @nth 11 @destination 01 0B 00 CD @copier 7B EA @returnLow 7A EA @returnHigh D1 C1 E1 F1 D7 C9")
        emit("nth", "A7 C8 C5 47 0E 50 2A B9 20 FC 05 20 F9 C1 C9")
        emit("copier", "04 0C 18 03 2A 12 13 0D 20 FA 05 20 F7 C9")
        emit("generated", "E5 D5 C5 FA @object F5 FE E9 F5 38 ~tmBranch 21 @hmPrefix 01 06 00 18 ~prefixJoin :tmBranch 21 @tmPrefix 01 05 00 :prefixJoin 11 @destination CD @copier D5 FA @object 4F 21 @helperAddress 3E 02 CF D1 F1 79 38 ~notHM D6 32 :notHM 06 F6 D6 0A 38 03 04 18 F9 C6 0A F5 78 12 13 F1 06 F6 80 12 13 3E 50 12 F1 EA @object C1 D1 E1 C9")
        emit("helper", "79 FE B9 38 ~skipDone FE D2 38 ~skipOne 3D :skipOne 3D :skipDone D6 B5 3C 4F C9")
        symbols["farRst"] = 8
        emit("farRst", "C3 @farCall")
        emit("bankRst", "E0 A0 EA 00 20 C9")
        emit("farCall", (if (hram) "E0 A1 F0 A0 F5 F0 A1" else "EA @farScratch F0 A0 F5 FA @farScratch") +
            " D7 CD @callHL 78 EA @savedB 79 EA @savedC C1 78 D7 FA @savedB 47 FA @savedC 4F C9")
        emit("callHL", "E9")
        val entry = at("directory") + 15
        bytes[entry] = 4
        word(entry + 1, root - 0x10000 + 0x4000)
    }
    fun at(name: String): Int = symbols.getValue(name)
    fun word(at: Int, value: Int) { bytes[at] = value.toByte(); bytes[at + 1] = (value ushr 8).toByte() }
    fun emit(name: String, text: String) {
        val tokens = text.split(" ")
        var pc = at(name)
        for (token in tokens) if (token.startsWith(":")) symbols[token.drop(1)] = pc else pc += if (token.startsWith("@")) 2 else 1
        pc = at(name)
        for (token in tokens) {
            when (token.first()) {
                ':' -> Unit
                '@' -> { word(pc, at(token.drop(1))); pc += 2 }
                '~' -> { val displacement = at(token.drop(1)) - pc - 1; require(displacement in -128..127); bytes[pc++] = displacement.toByte() }
                else -> bytes[pc++] = token.toInt(16).toByte()
            }
        }
    }
    fun session(limits: ResolutionLimits = ResolutionLimits(), cancellation: ParserCancellationToken = ParserCancellationToken.NONE) =
        RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBC, "unrelated"), limits = limits, cancellation = cancellation)
}
