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
