package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.GbaTargetReferenceEvidence
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class Gen3BoundedRegionEntryTest {
    @Test
    fun compiledBoundExcludesUnmappedSectionsWithoutDiscardingMapIdentities() {
        listOf(0x600 to 0x200, 0x900 to 0x280).forEach { (root, consumer) ->
            val fixture = fixture(root, consumer)
            val resolution = requireNotNull(resolve(fixture))
            assertEquals(mapOf(0 to 0, 1 to 1, 2 to 2, 3 to 3), resolution.sectionByBaseArea)
            assertEquals(setOf(0, 1, 2), resolution.entriesBySection.keys)
            assertEquals(mapOf(0 to "Alpha", 1 to "Beta", 2 to "Gamma"), resolution.namesByBaseArea)
        }
    }

    @Test
    fun readableBytesOutsideCompiledBoundNeverBecomePublishedEntries() {
        val fixture = fixture(0x600, 0x200)
        entry(fixture.bytes, 0x600, 3)
        assertEquals(setOf(0, 1, 2), requireNotNull(resolve(fixture)).entriesBySection.keys)
    }

    @Test
    fun completeCompetingConsumersAndConflictingBoundsFailClosed() {
        val fixture = fixture(0x600, 0x200)
        repeat(3) { entry(fixture.bytes, 0x900, it) }
        consumer(fixture.bytes, 0x280, 0x900)
        assertNull(resolve(fixture.copy(references = references(0x600 to 0x204, 0x900 to 0x284))))
        consumer(fixture.bytes, 0x280, 0x600, last = 3)
        assertNull(resolve(fixture.copy(references = references(0x600 to 0x204, 0x600 to 0x284))))
    }

    @Test
    fun countsOnlyOrTruncatedRequiredReferencesCannotAuthorizeTheBound() {
        val fixture = fixture(0x600, 0x200)
        assertNull(resolve(fixture.copy(references = GbaReferenceIndex.countsOnlyForTesting(mapOf(0x600 to 1)))))
        val incomplete = GbaReferenceIndex.fromTargets(
            mapOf(0x600 to GbaTargetReferenceEvidence(17, emptyList(), 17, 16, "site budget exceeded")),
            limitTargets = 32,
        )
        assertNull(resolve(fixture.copy(references = incomplete)))
        assertNull(resolve(fixture.copy(references = references(0x600 to 0x208))))
    }

    @Test
    fun boundedRowsStillRequireAllExistingShellAndNameTerminationChecks() {
        val fixture = fixture(0x600, 0x200)
        fixture.bytes[0x600 + 8 + 2] = 0
        assertNull(resolve(fixture))
        entry(fixture.bytes, 0x600, 1)
        putPointer(fixture.bytes, 0x600 + 8 + 4, 0x1FFF)
        assertNull(resolve(fixture))
        putPointer(fixture.bytes, 0x600 + 8 + 4, 0x1020)
        repeat(32) { fixture.bytes[0x1020 + it] = 0xBB.toByte() }
        assertNull(resolve(fixture))
    }

    @Test
    fun malformedGuardRegisterStrideAndPointerDoNotRecoverTheMissingMap() {
        listOf(0x202 to 0xD800, 0x202 to 0xD308, 0x206 to 0x0089,
            0x206 to 0x00D2, 0x208 to 0x3000, 0x20C to 0x6849).forEach { (at, op) ->
            val fixture = fixture(0x600, 0x200)
            putU16(fixture.bytes, at, op)
            assertNull("malformed instruction at $at", resolve(fixture))
        }
        val fixture = fixture(0x600, 0x200)
        putPointer(fixture.bytes, 0x220, 0x1FF8)
        assertNull(resolve(fixture.copy(references = references(0x1FF8 to 0x204))))
    }

    @Test
    fun boundSelectionDoesNotUseAnUnreferencedDataDecoy() {
        val fixture = fixture(0x600, 0x200)
        repeat(4) { entry(fixture.bytes, 0x900, it) }
        putPointer(fixture.bytes, 0x1500, 0x900)
        putPointer(fixture.bytes, 0x1504, 0x900)
        assertEquals(setOf(0, 1, 2), requireNotNull(resolve(fixture)).entriesBySection.keys)
    }

    @Test
    fun cancellationPropagatesDuringBoundedConsumerDiscovery() {
        val fixture = fixture(0x600, 0x200)
        var checks = 0
        assertThrows(ParserCancellationException::class.java) {
            resolve(fixture, ParserCancellationToken {
                if (++checks == 25) throw ParserCancellationException()
            })
        }
        assertEquals(25, checks)
    }

    @Test
    fun completeDuplicateConsumersAndDifferentRegisterAllocationRemainAuthoritative() {
        val fixture = fixture(0x600, 0x200)
        consumer(fixture.bytes, 0x280, 0x600)
        assertEquals(setOf(0, 1, 2), requireNotNull(resolve(fixture.copy(
            references = references(0x600 to 0x204, 0x600 to 0x284),
        ))).entriesBySection.keys)
        intArrayOf(0x2D02, 0xD808, 0x4B06, 0x00EA, 0x3304, 0x18D4, 0x6820)
            .forEachIndexed { index, op -> putU16(fixture.bytes, 0x200 + index * 2, op) }
        assertEquals(setOf(0, 1, 2), requireNotNull(resolve(fixture.copy(
            references = references(0x600 to 0x204, 0x600 to 0x284),
        ))).entriesBySection.keys)
    }

    @Test
    fun compiledExtentAndNominationBudgetsFailClosedAndScanCancellationPropagates() {
        val fixture = fixture(0x600, 0x200)
        assertEquals(true, CompiledGen3RegionEntryTable.discover(
            RomImage(fixture.bytes), fixture.references, ParserCancellationToken.NONE, extentLimit = 23,
        ).rejected)
        val many = ByteArray(0x8000)
        val members = (0..32).map { index ->
            val at = 0x200 + index * 0x40
            val root = 0x4000 + index * 0x20
            consumer(many, at, root)
            root to at + 4
        }.toTypedArray()
        val complete = GbaReferenceIndex.fromTargets(
            members.associate { (root, site) -> root to GbaTargetReferenceEvidence(1, listOf(site), 1, 16, null) },
            limitTargets = 64,
        )
        assertEquals(true, CompiledGen3RegionEntryTable.discover(
            RomImage(many), complete, ParserCancellationToken.NONE,
        ).rejected)
        var checks = 0
        assertThrows(ParserCancellationException::class.java) {
            CompiledGen3RegionEntryTable.discover(RomImage(ByteArray(0x8000)), complete, ParserCancellationToken {
                if (++checks == 3) throw ParserCancellationException()
            })
        }
        assertEquals(3, checks)
    }

    private data class Fixture(val bytes: ByteArray, val references: GbaReferenceIndex)

    private fun fixture(root: Int, at: Int): Fixture {
        val bytes = ByteArray(0x2000)
        intArrayOf(0x4B03, 0x0400, 0x0B80, 0x58C3, 0x0409, 0x0B89, 0x58C8, 0x4770)
            .forEachIndexed { index, op -> putU16(bytes, 0x40 + index * 2, op) }
        putPointer(bytes, 0x50, 0x180)
        putPointer(bytes, 0x180, 0x240)
        repeat(4) { map ->
            val header = 0x300 + map * 28
            putPointer(bytes, 0x240 + map * 4, header)
            putPointer(bytes, header, 0x500)
            putU16(bytes, header + 0x12, 1)
            bytes[header + 0x14] = map.toByte()
        }
        repeat(3) { entry(bytes, root, it) }
        // The next ordinal is a real encounter identity, but not a region-table row.
        bytes[root + 24] = 0xCE.toByte()
        consumer(bytes, at, root)
        return Fixture(bytes, references(root to at + 4))
    }

    private fun resolve(fixture: Fixture, cancellation: ParserCancellationToken = ParserCancellationToken.NONE) =
        Gen3MapLocationResolver.resolveDetailed(
            RomImage(fixture.bytes), setOf(0, 1, 2, 3), fixture.references,
            PokemonTextCodec.gbaEnglish, cancellation,
        )

    private fun references(vararg members: Pair<Int, Int>) = GbaReferenceIndex.fromTargets(
        members.groupBy({ it.first }, { it.second }).mapValues { (_, sites) ->
            GbaTargetReferenceEvidence(sites.size, sites, sites.size, 16, null)
        },
        limitTargets = 32,
    )

    private fun consumer(bytes: ByteArray, at: Int, root: Int, last: Int = 2) {
        intArrayOf(0x2900 or last, 0xD808, 0x4806, 0x00C9, 0x3004, 0x1809, 0x6809, 0x4770)
            .forEachIndexed { index, op -> putU16(bytes, at + index * 2, op) }
        putPointer(bytes, at + 0x20, root)
    }

    private fun entry(bytes: ByteArray, root: Int, section: Int) {
        val at = root + section * 8
        repeat(4) { bytes[at + it] = 1 }
        val text = 0x1000 + section * 32
        putPointer(bytes, at + 4, text)
        val name = listOf("Alpha", "Beta", "Gamma", "Delta")[section]
        name.forEachIndexed { index, c ->
            bytes[text + index] = (if (c in 'A'..'Z') 0xBB + c.code - 'A'.code else 0xD5 + c.code - 'a'.code).toByte()
        }
        bytes[text + name.length] = 0xFF.toByte()
    }

    private fun putPointer(bytes: ByteArray, at: Int, root: Int) {
        repeat(4) { bytes[at + it] = ((0x08000000 + root) ushr (it * 8)).toByte() }
    }

    private fun putU16(bytes: ByteArray, at: Int, value: Int) {
        bytes[at] = value.toByte()
        bytes[at + 1] = (value ushr 8).toByte()
    }
}
