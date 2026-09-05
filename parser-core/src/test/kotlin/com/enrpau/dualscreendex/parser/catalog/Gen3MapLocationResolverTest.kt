package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3MapLocationResolverTest {
    @Test
    fun resolvesOnlyBoundedBiasedSectionNamesAndRejectsConflictingRoots() {
        val bytes = ByteArray(0x1000)
        fun op(at: Int, value: Int) { bytes[at] = value.toByte(); bytes[at + 1] = (value ushr 8).toByte() }
        fun consumer(start: Int, root: Int) {
            op(start, 0xB570)
            op(start + 2, 0x0409)
            op(start + 4, 0x4A1E)
            op(start + 6, 0x1889)
            op(start + 8, 0x0C0D)
            op(start + 10, 0x2D02)
            op(start + 12, 0xD818)
            op(start + 0x20, 0x4818)
            op(start + 0x22, 0x00A9)
            op(start + 0x24, 0x1809)
            op(start + 0x26, 0x6809)
            op(start + 0x80, 0); op(start + 0x82, 0xFFF9) // bias 7, not a family constant
            putPointer(bytes, start + 0x84, root)
        }
        consumer(0x200, 0x600)
        writeIndexedU16CompactConsumer(bytes, 0x100, 0x400)
        putPointer(bytes, 0x400, 0x420)
        repeat(3) { id ->
            putPointer(bytes, 0x420 + id * 4, 0x440 + id * 28)
            writeMapHeader(bytes, 0x440 + id * 28, 7 + id)
        }
        repeat(3) { id ->
            putPointer(bytes, 0x600 + id * 4, 0x800 + id * 16)
            byteArrayOf(1, 2, 3, 0xFF.toByte()).copyInto(bytes, 0x800 + id * 16)
        }
        fun names(extentLimit: Long = 64L * 1024 * 1024, countsOnly: Boolean = false): Map<Int, String> {
            val session = com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession(RomImage(bytes), com.enrpau.dualscreendex.parser.model.RomHeader(com.enrpau.dualscreendex.parser.model.Platform.GBA, "BIAS TEST"))
            val index = requireNotNull(session.gbaReferenceIndex)
            val refs = if (countsOnly) GbaReferenceIndex.countsOnlyForTesting(index.counts) else index
            return com.enrpau.dualscreendex.parser.parse.CompiledRegionSectionNames.resolve(session.rom, refs, setOf(6, 7, 8, 9, 10), com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs.gen3Later, session.cancellation, extentLimit)
        }
        fun joinedNames(): Map<Int, String> {
            val session = com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession(RomImage(bytes), com.enrpau.dualscreendex.parser.model.RomHeader(com.enrpau.dualscreendex.parser.model.Platform.GBA, "JOIN TEST"))
            val refs = requireNotNull(session.gbaReferenceIndex)
            assertEquals(mapOf(0 to 7, 1 to 8, 2 to 9), Gen3MapLocationResolver.resolveSectionByBaseArea(session.rom, setOf(0, 1, 2), refs))
            return Gen3MapLocationResolver.resolve(session.rom, setOf(0, 1, 2), refs, com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs.gen3Later)
        }
        assertEquals(mapOf(0 to "あいう", 1 to "あいう", 2 to "あいう"), joinedNames())
        assertEquals(mapOf(7 to "あいう", 8 to "あいう", 9 to "あいう"), names())
        assertTrue(names(extentLimit = 11).isEmpty())
        assertTrue(names(countsOnly = true).isEmpty())
        op(0x210, 0x4801); op(0x218, 0x2503) // referenced but reachable MOV r5,#3 is not pool data
        assertTrue("reachable index clobber must not be hidden by LDR reference", names().isEmpty())
        op(0x216, 0xE001) // now execution really jumps over the loaded word to 0x21C
        assertEquals(3, names().size)
        op(0x210, 0); op(0x216, 0); op(0x218, 0)
        op(0x220, 0x4D18); op(0x224, 0x1949) // root load overwrites the bounded index register
        assertTrue(names().isEmpty())
        op(0x220, 0x4818); op(0x224, 0x1809)
        bytes[0x810] = 0xBB.toByte(); bytes[0x811] = 0xBC.toByte(); bytes[0x812] = 0xBD.toByte()
        assertEquals(setOf(7, 9), names().keys) // wrong-language row leaves other numeric sections alone
        assertEquals(setOf(0, 2), joinedNames().keys)
        op(0x20C, 0xD800) // bound no longer protects lookup
        assertTrue(names().isEmpty())
        assertTrue(joinedNames().isEmpty())
        op(0x20C, 0xD818)
        consumer(0x300, 0x620)
        repeat(3) { putPointer(bytes, 0x620 + it * 4, 0x800) }
        assertTrue(names().isEmpty())
        assertTrue(joinedNames().isEmpty())
    }

    @Test
    fun resolvesEveryMapNameFromTheEncounterProvenMapGroupsAndRegionTable() {
        val bytes = ByteArray(0x1000)
        putPointer(bytes, 0x20C, 0x200)
        putPointer(bytes, 0x210, 0x208)
        putPointer(bytes, 0x200, 0x300)
        putPointer(bytes, 0x204, 0x31C)
        putPointer(bytes, 0x208, 0x338)
        writeMapHeader(bytes, 0x300, 0)
        writeMapHeader(bytes, 0x31C, 1)
        writeMapHeader(bytes, 0x338, 2)
        writeRegionEntry(bytes, 0x600, 0, 0x700, "Oldale Town")
        writeRegionEntry(bytes, 0x600, 1, 0x720, "Route 101")
        writeRegionEntry(bytes, 0x600, 2, 0x740, "Littleroot Town")
        putPointer(bytes, 0x900, 0x600)

        val names = Gen3MapLocationResolver.resolve(
            RomImage(bytes),
            setOf(0x0000, 0x0001, 0x0100),
            PokemonTextCodec.gbaEnglish,
        )

        assertEquals("Oldale Town", names[0x0000])
        assertEquals("Route 101", names[0x0001])
        assertEquals("Littleroot Town", names[0x0100])
        assertEquals(3, names.size)
    }

    @Test
    fun cancelsDuringFullRomMapAuthorityScanning() {
        var checks = 0
        val cancellation = ParserCancellationToken {
            checks++
            if (checks == 2) throw ParserCancellationException()
        }

        assertThrows(ParserCancellationException::class.java) {
            Gen3MapLocationResolver.resolveDetailed(
                RomImage(ByteArray(0x20_000)),
                setOf(0),
                GbaReferenceIndex.countsOnlyForTesting(emptyMap()),
                PokemonTextCodec.gbaEnglish,
                cancellation,
            )
        }
        assertEquals(2, checks)
    }

    @Test
    fun resolvesStructuralRegionEntriesWithoutTextAuthority() {
        val bytes = ByteArray(0x1000)
        writeIndexedU16CompactConsumer(bytes, 0x40, 0x180)
        putPointer(bytes, 0x180, 0x240)
        repeat(3) { map ->
            val header = 0x300 + map * 0x1C
            putPointer(bytes, 0x240 + map * 4, header)
            writeMapHeader(bytes, header, map)
            writeRegionEntry(bytes, 0x600, map, 0x700 + map * 0x20, "Section $map")
        }
        putPointer(bytes, 0x900, 0x600)

        val resolution = Gen3MapLocationResolver.resolveDetailed(
            RomImage(bytes),
            setOf(0, 1, 2),
            GbaReferenceIndex.countsOnlyForTesting(mapOf(0x700 to 1)),
            null,
        )

        assertEquals(mapOf(0 to 0, 1 to 1, 2 to 2), resolution?.sectionByBaseArea)
        assertEquals(setOf(0, 1, 2), resolution?.entriesBySection?.keys)
        assertTrue(resolution?.entriesBySection?.values?.all { it.displayName == null } == true)
        assertEquals(1, resolution?.entriesBySection?.getValue(0)?.x)
        assertEquals(1, resolution?.entriesBySection?.getValue(0)?.width)
    }

    @Test
    fun retainsStructuralEntryWhenItsLocalizedNameIsUnreadable() {
        val bytes = ByteArray(0x1000)
        writeIndexedU16CompactConsumer(bytes, 0x40, 0x180)
        putPointer(bytes, 0x180, 0x240)
        repeat(4) { map ->
            val header = 0x300 + map * 0x1C
            putPointer(bytes, 0x240 + map * 4, header)
            writeMapHeader(bytes, header, map)
            writeRegionEntry(bytes, 0x600, map, 0x700 + map * 0x20, "Section $map")
        }
        bytes[0x760] = 0xBB.toByte()
        bytes[0x761] = 0x7F
        bytes[0x762] = 0x7F
        bytes[0x763] = 0xFF.toByte()
        putPointer(bytes, 0x900, 0x600)

        val resolution = Gen3MapLocationResolver.resolveDetailed(
            RomImage(bytes),
            setOf(0, 1, 2, 3),
            GbaReferenceIndex.countsOnlyForTesting(mapOf(0x700 to 1)),
            PokemonTextCodec.gbaEnglish,
        )

        assertEquals(setOf(0, 1, 2, 3), resolution?.entriesBySection?.keys)
        assertEquals(null, resolution?.entriesBySection?.getValue(3)?.displayName)
    }

    @Test
    fun retainsNamedOffMapSentinelEntriesAsTableAuthority() {
        val bytes = ByteArray(0x1000)
        putPointer(bytes, 0x210, 0x200)
        putPointer(bytes, 0x214, 0x20C)
        putPointer(bytes, 0x200, 0x300)
        putPointer(bytes, 0x204, 0x31C)
        putPointer(bytes, 0x208, 0x338)
        putPointer(bytes, 0x20C, 0x354)
        writeMapHeader(bytes, 0x300, 0)
        writeMapHeader(bytes, 0x31C, 1)
        writeMapHeader(bytes, 0x338, 2)
        writeMapHeader(bytes, 0x354, 3)
        writeRegionEntry(bytes, 0x600, 0, 0x700, "Oldale Town")
        writeRegionEntry(bytes, 0x600, 1, 0x720, "Dark Cave")
        writeRegionEntry(bytes, 0x600, 2, 0x740, "Littleroot Town")
        writeRegionEntry(bytes, 0x600, 3, 0x760, "Route 101")
        bytes[0x608] = 0xFF.toByte()
        bytes[0x609] = 0xFF.toByte()
        putPointer(bytes, 0x900, 0x600)

        val names = Gen3MapLocationResolver.resolve(
            RomImage(bytes),
            setOf(0x0000, 0x0001, 0x0002, 0x0100),
            PokemonTextCodec.gbaEnglish,
        )

        assertEquals("Oldale Town", names[0x0000])
        assertEquals("Dark Cave", names[0x0001])
        assertEquals("Littleroot Town", names[0x0002])
        assertEquals("Route 101", names[0x0100])
        assertEquals(4, names.size)
    }

    @Test
    fun returnsNoNamesWhenEncounterKeysDoNotProveOneMapGroupsRoot() {
        assertTrue(
            Gen3MapLocationResolver.resolve(
                RomImage(ByteArray(0x400)),
                setOf(0x0000),
                PokemonTextCodec.gbaEnglish,
            ).isEmpty(),
        )
    }

    @Test
    fun compactConsumerJoinsRequiredSparseGroupsWithoutEnumeratingUnrelatedPacking() {
        val bytes = compactConsumerFixture(0x180)
        putPointer(bytes, 0x184, 0x240)
        putPointer(bytes, 0x188, 0x340)
        putPointer(bytes, 0x240, 0x500)
        putPointer(bytes, 0x340, 0x51C)
        writeMapHeader(bytes, 0x500, 88)
        writeMapHeader(bytes, 0x51C, 149)

        val sections = Gen3MapLocationResolver.resolveSectionByBaseArea(
            RomImage(bytes),
            setOf(0x0100, 0x0200),
            GbaReferenceIndex.countsOnlyForTesting(mapOf(0x700 to 1)),
        )

        assertEquals(mapOf(0x0100 to 88, 0x0200 to 149), sections)

        val narrow = ByteArray(0x1000)
        writeNarrowCompactConsumer(narrow, 0x40, 0x180)
        putPointer(narrow, 0x184, 0x240)
        putPointer(narrow, 0x188, 0x340)
        putPointer(narrow, 0x240, 0x500)
        putPointer(narrow, 0x340, 0x51C)
        writeMapHeader(narrow, 0x500, 88)
        writeMapHeader(narrow, 0x51C, 149)
        assertEquals(mapOf(0x0100 to 88, 0x0200 to 149), resolveCompact(narrow))

        val indexed = ByteArray(0x1000)
        writeIndexedU16CompactConsumer(indexed, 0x40, 0x180)
        putPointer(indexed, 0x184, 0x240)
        putPointer(indexed, 0x188, 0x340)
        putPointer(indexed, 0x240, 0x500)
        putPointer(indexed, 0x340, 0x51C)
        writeMapHeader(indexed, 0x500, 88)
        writeMapHeader(indexed, 0x51C, 149)
        assertEquals(mapOf(0x0100 to 88, 0x0200 to 149), resolveCompact(indexed))
    }

    @Test
    fun compiledConsumerOmitsEmptyEntriesButRetainsPunctuationNames() {
        val bytes = ByteArray(0x1000)
        writeIndexedU16CompactConsumer(bytes, 0x40, 0x180)
        putPointer(bytes, 0x180, 0x240)
        repeat(5) { map ->
            val header = 0x300 + map * 0x1C
            putPointer(bytes, 0x240 + map * 4, header)
            writeMapHeader(bytes, header, map)
        }
        writeRegionEntry(bytes, 0x600, 0, 0x800, "Alpha")
        writeRegionEntry(bytes, 0x600, 1, 0x820, "Beta")
        writeRegionEntry(bytes, 0x600, 3, 0x840, "???")
        writeRegionEntry(bytes, 0x600, 4, 0x860, "Gamma")
        putPointer(bytes, 0x900, 0x600)

        val names = Gen3MapLocationResolver.resolve(
            RomImage(bytes),
            (0..4).toSet(),
            GbaReferenceIndex.countsOnlyForTesting(mapOf(0x700 to 1)),
            PokemonTextCodec.gbaEnglish,
        )

        assertEquals(
            mapOf(0 to "Alpha", 1 to "Beta", 3 to "???", 4 to "Gamma"),
            names,
        )
    }

    @Test
    fun compactConsumerRequiresCompleteAndNonConflictingAuthorities() {
        val partial = compactConsumerFixture(0x180)
        putPointer(partial, 0x184, 0x240)
        putPointer(partial, 0x240, 0x500)
        writeMapHeader(partial, 0x500, 88)
        assertTrue(resolveCompact(partial).isEmpty())

        val duplicate = partial.copyOf()
        putPointer(duplicate, 0x188, 0x340)
        putPointer(duplicate, 0x340, 0x51C)
        writeMapHeader(duplicate, 0x51C, 149)
        writeCompactConsumer(duplicate, 0x80, 0x1C0)
        putPointer(duplicate, 0x1C4, 0x280)
        putPointer(duplicate, 0x1C8, 0x380)
        putPointer(duplicate, 0x280, 0x580)
        putPointer(duplicate, 0x380, 0x59C)
        writeMapHeader(duplicate, 0x580, 88)
        writeMapHeader(duplicate, 0x59C, 149)
        assertEquals(mapOf(0x0100 to 88, 0x0200 to 149), resolveCompact(duplicate))

        writeMapHeader(duplicate, 0x59C, 150)
        assertTrue(resolveCompact(duplicate).isEmpty())

        val decoy = compactConsumerFixture(0x180)
        putPointer(decoy, 0x184, 0x240)
        putPointer(decoy, 0x188, 0x340)
        putPointer(decoy, 0x240, 0x500)
        putPointer(decoy, 0x340, 0x51C)
        writeMapHeader(decoy, 0x500, 88)
        writeMapHeader(decoy, 0x51C, 149)
        writeCompactConsumer(decoy, 0x80, 0x1C0)
        assertEquals(mapOf(0x0100 to 88, 0x0200 to 149), resolveCompact(decoy))
    }

    @Test
    fun compactConsumerClassifiesExplicitNonRomHeadersAsUnbindable() {
        val bytes = compactConsumerFixture(0x180)
        putPointer(bytes, 0x184, 0x240)
        putPointer(bytes, 0x188, 0x340)
        putPointer(bytes, 0x240, 0x500)
        putU32(bytes, 0x340, 0xF7F7F7F7.toInt())
        writeMapHeader(bytes, 0x500, 88)

        assertEquals(mapOf(0x0100 to 88), resolveCompact(bytes))

        putU32(bytes, 0x340, 0)
        assertTrue(resolveCompact(bytes).isEmpty())
    }

    @Test
    fun compactConsumerAcceptsSourceValidNullEventsButRejectsInvalidNonNullPointers() {
        val nullEvents = compactConsumerFixture(0x180)
        putPointer(nullEvents, 0x184, 0x240)
        putPointer(nullEvents, 0x188, 0x340)
        putPointer(nullEvents, 0x240, 0x500)
        putPointer(nullEvents, 0x340, 0x51C)
        writeMapHeader(nullEvents, 0x500, 88)
        writeMapHeader(nullEvents, 0x51C, 149)
        putU32(nullEvents, 0x51C + 4, 0)
        assertEquals(mapOf(0x0100 to 88, 0x0200 to 149), resolveCompact(nullEvents))

        putU32(nullEvents, 0x51C + 4, 0x07000000)
        assertTrue(resolveCompact(nullEvents).isEmpty())
    }

    private fun resolveCompact(bytes: ByteArray): Map<Int, Int> =
        Gen3MapLocationResolver.resolveSectionByBaseArea(
            RomImage(bytes),
            setOf(0x0100, 0x0200),
            GbaReferenceIndex.countsOnlyForTesting(mapOf(0x700 to 1)),
        )

    private fun compactConsumerFixture(root: Int): ByteArray = ByteArray(0x1000).also {
        writeCompactConsumer(it, 0x40, root)
    }

    private fun writeCompactConsumer(bytes: ByteArray, offset: Int, root: Int) {
        val instructions = intArrayOf(0x0400, 0x0409, 0x4A06, 0x0B80, 0x1880, 0x6800, 0x0B89, 0x1809, 0x6808, 0x4770)
        instructions.forEachIndexed { index, instruction -> putU16(bytes, offset + index * 2, instruction) }
        putPointer(bytes, offset + 0x20, root)
    }

    private fun writeNarrowCompactConsumer(bytes: ByteArray, offset: Int, root: Int) {
        val instructions = intArrayOf(0x4A03, 0x0080, 0x1880, 0x6800, 0x0089, 0x1809, 0x6808, 0x4770)
        instructions.forEachIndexed { index, instruction -> putU16(bytes, offset + index * 2, instruction) }
        putPointer(bytes, offset + 0x10, root)
    }

    private fun writeIndexedU16CompactConsumer(bytes: ByteArray, offset: Int, root: Int) {
        val instructions = intArrayOf(0x4B03, 0x0400, 0x0B80, 0x58C3, 0x0409, 0x0B89, 0x58C8, 0x4770)
        instructions.forEachIndexed { index, instruction -> putU16(bytes, offset + index * 2, instruction) }
        putPointer(bytes, offset + 0x10, root)
    }

    private fun writeMapHeader(bytes: ByteArray, offset: Int, regionSection: Int) {
        putPointer(bytes, offset, 0x500)
        putPointer(bytes, offset + 4, 0x520)
        putPointer(bytes, offset + 8, 0x540)
        putU16(bytes, offset + 0x12, 1)
        bytes[offset + 0x14] = regionSection.toByte()
    }

    private fun writeRegionEntry(bytes: ByteArray, root: Int, index: Int, textOffset: Int, text: String) {
        val offset = root + index * 8
        bytes[offset] = 1
        bytes[offset + 1] = 1
        bytes[offset + 2] = 1
        bytes[offset + 3] = 1
        putPointer(bytes, offset + 4, textOffset)
        text.forEachIndexed { characterIndex, character ->
            bytes[textOffset + characterIndex] = when (character) {
                ' ' -> 0
                in 'A'..'Z' -> (0xBB + character.code - 'A'.code).toByte()
                in 'a'..'z' -> (0xD5 + character.code - 'a'.code).toByte()
                in '0'..'9' -> (0xA1 + character.code - '0'.code).toByte()
                '?' -> 0xAC.toByte()
                else -> error("unsupported fixture character $character")
            }
        }
        bytes[textOffset + text.length] = 0xFF.toByte()
    }

    private fun putPointer(bytes: ByteArray, offset: Int, target: Int) = putU32(bytes, offset, 0x08000000 + target)

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
