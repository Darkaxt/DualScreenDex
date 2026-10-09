package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.GbaTargetReferenceEvidence
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Gen3BlankMapNameTest {
    @Test
    fun nativeBlankFallbackOwnsOnlySectionsOutsideItsProvenBound() {
        listOf(0x200 to 0x500, 0x380 to 0x700).forEach { (entry, fill) ->
            val fixture = fixture(entry, fill)
            assertEquals((3..255).toSet(), blankSections(fixture))
        }
    }

    @Test
    fun theBoundAndDefaultPaddingComeFromCodeNotAnOrdinalProfile() {
        val fixture = fixture(0x200, 0x500, last = 6, defaultPad = 7)
        assertEquals((7..255).toSet(), blankSections(fixture))
    }

    @Test
    fun malformedCoercionSpaceFillTerminationAndReturnOwnershipStayUnresolved() {
        val entry = 0x200
        val guard = entry + 24
        listOf(entry + 4 to 0x0009, entry + 10 to 0x0C12, guard + 30 to 0xD101,
            guard + 36 to 0x2101, guard + 38 to 0x1C12, guard + 82 to 0xBC10,
            0x500 + 32 to 0x20FE, 0x500 + 18 to 0x7018).forEach { (at, op) ->
            val fixture = fixture(entry, 0x500)
            word(fixture.bytes, at, op)
            assertEquals("malformed instruction at $at", emptySet<Int>(), blankSections(fixture))
        }
    }

    @Test
    fun anOutsideDomainSpecialBranchCannotBeDeclaredBlank() {
        val fixture = fixture(0x200, 0x500)
        word(fixture.bytes, 0x20C, 0x2903)
        assertEquals(emptySet<Int>(), blankSections(fixture))
    }

    @Test
    fun tableReferencesExtentsAndCompetingBoundsFailClosed() {
        val fixture = fixture(0x200, 0x500)
        assertEquals(emptySet<Int>(), blankSections(fixture.copy(
            references = GbaReferenceIndex.countsOnlyForTesting(mapOf(0x1000 to 1)),
        )))
        assertEquals(emptySet<Int>(), blankSections(fixture, extentLimit = 23))
        val other = fixture(0x380, 0x700, last = 6)
        other.bytes.copyInto(fixture.bytes, 0x380, 0x380, 0x400)
        other.bytes.copyInto(fixture.bytes, 0x700, 0x700, 0x72C)
        assertEquals(emptySet<Int>(), blankSections(fixture.copy(
            references = references(0x1000 to 0x21C, 0x1000 to 0x39C),
        )))
    }

    @Test
    fun competingBlankGettersWithTheSameBoundCannotOverruleAnUnprovedTail() {
        val fixture = fixture(0x200, 0x500)
        val other = fixture(0x380, 0x700)
        other.bytes.copyInto(fixture.bytes, 0x380, 0x380, 0x400)
        other.bytes.copyInto(fixture.bytes, 0x700, 0x700, 0x72C)
        val combined = fixture.copy(references = references(0x1000 to 0x21C, 0x1000 to 0x39C))
        assertEquals((3..255).toSet(), blankSections(combined))
        val original = fixture.bytes.copyOf()
        listOf(0x380 to 0xB510, 0x382 to 0x1C04, 0x384 to 0x0009,
            0x38A to 0x0C12, 0x398 + 32 to 0x2400, 0x398 + 36 to 0x2101,
            0x398 + 82 to 0xBC10, 0x700 + 32 to 0x20FE).forEach { (at, op) ->
            original.copyInto(fixture.bytes)
            word(fixture.bytes, at, op)
            assertEquals("competing getter instruction at $at", emptySet<Int>(), blankSections(combined))
            assertEquals(listOf(CompiledGen3RegionEntryTable.Table(0x1000, 3)),
                CompiledGen3RegionEntryTable.discover(RomImage(fixture.bytes), combined.references,
                    ParserCancellationToken.NONE).tables)
        }
    }

    @Test
    fun readableOrEmptyDataAloneCannotAuthorizeBlankNames() {
        val fixture = fixture(0x200, 0x500)
        repeat(112) { fixture.bytes[0x200 + it] = 0 }
        assertEquals(emptySet<Int>(), blankSections(fixture))
    }

    @Test
    fun cancellationStillPropagatesFromTheDiscoveryScan() {
        val fixture = fixture(0x200, 0x500)
        assertThrows(ParserCancellationException::class.java) {
            blankSections(fixture, ParserCancellationToken { throw ParserCancellationException() })
        }
    }

    private data class Fixture(val bytes: ByteArray, val references: GbaReferenceIndex)

    private fun blankSections(
        fixture: Fixture,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
        extentLimit: Long = 0x10000,
    ): Set<Int> = Gen3MapLocationResolver.resolveBlankNameSections(
        RomImage(fixture.bytes), fixture.references, cancellation, extentLimit,
    )

    private fun fixture(entry: Int, fill: Int, last: Int = 2, defaultPad: Int = 18): Fixture {
        val bytes = ByteArray(0x3000)
        val assembler = Assembler(bytes, entry)
        with(assembler) {
            emit(0xB530); emit(0x1C05); emit(0x0409); emit(0x0C09); emit(0x0412); emit(0x0C14)
            emit(0x2901); branch("guard", 1); call(0x900); emit(0x1C01); branch("padding")
            label("guard"); emit(0x2900 or last); branch("blank", 8)
            literal(0, 0x1000); emit(0x00C9); emit(0x3004); emit(0x1809); emit(0x6809)
            emit(0x1C28); call(0x900); emit(0x1C01); branch("padding"); pool()
            label("blank"); emit(0x2C00); branch("fill", 1); emit(0x2400 or defaultPad)
            label("fill"); emit(0x1C28); emit(0x2100); emit(0x1C22); call(fill); branch("return")
            label("padding"); emit(0x2C00); branch("done", 0)
            emit(0x1B48); emit(0x0400); emit(0x0C00); emit(0x42A0); branch("terminator", 2)
            emit(0x2200); label("pad-loop"); emit(0x700A); emit(0x3101); emit(0x3001)
            emit(0x0400); emit(0x0C00); emit(0x42A0); branch("pad-loop", 3)
            label("terminator"); emit(0x20FF); emit(0x7008); label("done"); emit(0x1C08)
            label("return"); emit(0xBC30); emit(0xBC02); emit(0x4708); finish()
        }
        with(Assembler(bytes, fill)) {
            emit(0xB500); emit(0x1C03); emit(0x0609); emit(0x0E09); emit(0x0412); emit(0x0C12)
            emit(0x2000); emit(0x4290); branch("terminator", 2)
            label("loop"); emit(0x7019); emit(0x3301); emit(0x3001); emit(0x0400); emit(0x0C00)
            emit(0x4290); branch("loop", 3)
            label("terminator"); emit(0x20FF); emit(0x7018); emit(0x1C18); emit(0xBC02); emit(0x4708)
            finish()
        }
        return Fixture(bytes, references(0x1000 to entry + 28))
    }

    private class Assembler(private val bytes: ByteArray, start: Int) {
        private var cursor = start
        private val labels = mutableMapOf<String, Int>()
        private val branches = mutableListOf<Triple<Int, String, Int?>>()
        private val literals = mutableListOf<Triple<Int, Int, Int>>()
        fun emit(op: Int) { word(bytes, cursor, op); cursor += 2 }
        fun label(name: String) { labels[name] = cursor }
        fun branch(label: String, condition: Int? = null) {
            branches += Triple(cursor, label, condition); emit(0)
        }
        fun call(target: Int) {
            val delta = target - cursor - 4
            emit(0xF000 or (delta shr 12 and 2047)); emit(0xF800 or (delta shr 1 and 2047))
        }
        fun literal(register: Int, root: Int) {
            literals += Triple(cursor, register, root); emit(0)
        }
        fun pool() {
            require(cursor % 4 == 0)
            literals.forEach { (at, register, root) ->
                val distance = cursor - ((at + 4) and -4)
                word(bytes, at, 0x4800 or (register shl 8) or (distance / 4))
                repeat(4) { bytes[cursor + it] = ((0x08000000 + root) ushr (it * 8)).toByte() }
                cursor += 4
            }
            literals.clear()
        }
        fun finish() {
            branches.forEach { (at, name, condition) ->
                val delta = (labels.getValue(name) - at - 4) / 2
                require(delta in -128..127)
                word(bytes, at, if (condition == null) 0xE000 or (delta and 2047)
                    else 0xD000 or (condition shl 8) or (delta and 255))
            }
        }
    }

    companion object {
        private fun references(vararg members: Pair<Int, Int>) = GbaReferenceIndex.fromTargets(
            members.groupBy({ it.first }, { it.second }).mapValues { (_, sites) ->
                GbaTargetReferenceEvidence(sites.size, sites, sites.size, 16, null)
            },
            limitTargets = 32,
        )
        private fun word(bytes: ByteArray, at: Int, value: Int) {
            bytes[at] = value.toByte()
            bytes[at + 1] = (value ushr 8).toByte()
        }
    }
}
