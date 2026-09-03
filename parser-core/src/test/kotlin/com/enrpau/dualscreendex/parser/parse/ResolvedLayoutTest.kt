package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityReviewStatus
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.profile.KnownProfiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ResolvedLayoutTest {
    @Test
    fun probeDoesNotExposeInheritedTablesThatFailedValidation() {
        val bytes = ByteArray(2_097_152) { 0x7F }
        val probe = FamilyParsers.all.single { it.family == EngineFamily.CRYSTAL }
            .probe(RomImage(bytes), RomHeader(Platform.GBC, "PM_CRYSTAL", null))

        val layout = assertNotNull(probe.resolvedLayout).let { probe.resolvedLayout!! }
        assertNull(layout.tables.speciesNames)
        assertNull(layout.tables.moveNames)
    }

    @Test
    fun probePreservesRelocatedTableLayoutUsedByValidation() {
        val chartOffset = 300
        val chart = byteArrayOf(
            0, 5, 5,
            0, 8, 5,
            10, 10, 5,
            10, 11, 5,
            10, 12, 20,
            10, 15, 20,
            10, 6, 20,
            10, 5, 5,
            10, 16, 5,
            10, 8, 20,
            11, 10, 20,
            11, 11, 5,
            0xFF.toByte(), 0xFF.toByte(), 0,
        )
        val bytes = ByteArray(512) { 0x7F }
        chart.copyInto(bytes, chartOffset)

        val probe = FamilyParsers.all.single { it.family == EngineFamily.EMERALD }
            .probe(RomImage(bytes), RomHeader(Platform.GBA, "POKEMON EMER", "BPEE"))

        val layout = assertNotNull(probe.resolvedLayout).let { probe.resolvedLayout!! }
        assertEquals(3, layout.generation)
        assertEquals(chartOffset, layout.tables.typeChart?.offset)
        assertEquals(3, layout.tables.typeChart?.recordSize)
        assertEquals(true, layout.tables.typeChart?.variableLength)
    }

    @Test
    fun ambiguousPublishedHeaderDoesNotRestoreInheritedSemanticRoots() {
        val profile = KnownProfiles.forFamily(EngineFamily.EMERALD).single()
        val bytes = ByteArray(0x340000)
        val speciesNames = requireNotNull(profile.tables.speciesNames)
        val stats = requireNotNull(profile.tables.baseStats)
        val moveNames = requireNotNull(profile.tables.moveNames)
        val moves = requireNotNull(profile.tables.moveData)
        val abilities = requireNotNull(profile.tables.abilities)
        repeat(speciesNames.count) { id ->
            putGbaText(bytes, speciesNames.offset + id * speciesNames.recordSize, "MON")
            val base = stats.offset + id * stats.recordSize
            if (id > 0) {
                repeat(6) { field -> bytes[base + field] = (40 + field).toByte() }
                bytes[base + 6] = 12
                bytes[base + 7] = 3
            }
        }
        repeat(moveNames.count) { id ->
            putGbaText(bytes, moveNames.offset + id * moveNames.recordSize, "MOVE")
            val base = moves.offset + id * moves.recordSize
            if (id > 0) {
                bytes[base + 1] = 40
                bytes[base + 2] = (id % 18).toByte()
                bytes[base + 3] = 100
                bytes[base + 4] = 20
            }
        }
        repeat(abilities.count) { id ->
            putGbaText(bytes, abilities.offset + id * abilities.recordSize, "ABILITY")
        }
        // Eleven consecutive pointers make all three overlapping published data windows complete,
        // while the independent name slots provide no semantic evidence to break the tie.
        repeat(11) { index -> putPointer(bytes, 0x1AC + index * 4, 0x8000 + index * 0x1000) }

        val probe = FamilyParsers.all.single { it.family == EngineFamily.EMERALD }
            .probe(RomImage(bytes), RomHeader(Platform.GBA, "POKEMON EMER", "BPEE"))
        val capabilities = probe.capabilities.associateBy { it.capability }
        val layout = requireNotNull(probe.resolvedLayout)

        listOf(RomCapability.BASE_STATS, RomCapability.MOVE_DETAILS).forEach { capability ->
            assertEquals(
                capability.name,
                CapabilityStatus.AMBIGUOUS,
                capabilities.getValue(capability).status,
            )
            assertEquals(CapabilityReviewStatus.MANUAL_REVIEW, capabilities.getValue(capability).reviewStatus)
        }
        assertEquals(CapabilityStatus.NOT_FOUND, capabilities.getValue(RomCapability.ABILITIES).status)
        assertEquals(CapabilityReviewStatus.NONE, capabilities.getValue(RomCapability.ABILITIES).reviewStatus)
        assertNull(layout.tables.baseStats)
        assertNull(layout.tables.moveData)
        assertNull(layout.tables.abilities)
    }

    @Test
    fun rubyDerivedRomAppliesSpeciesNameRelocationToThePreservedTableShape() {
        val delta = -24
        val names = 0x1F7184 + delta
        val stats = 0x1FEC30 + delta
        val moveNames = 0x1F8338 + delta
        val moveData = 0x1FB144 + delta
        val bytes = ByteArray(0x210000)

        putPointer(bytes, 0x100, names)
        byteArrayOf(0x30, 0xB5.toByte(), 0x00, 0x25, 0x08, 0x4C, 0xC8.toByte(), 0xF7.toByte())
            .copyInto(bytes, 0x104)
        repeat(412) { index ->
            putGbaText(bytes, names + index * 11, "SPECIES")
            val base = stats + index * 28
            repeat(6) { bytes[base + it] = (40 + index % 30).toByte() }
            bytes[base + 6] = 1
            bytes[base + 7] = 2
            bytes.fill(0xFF.toByte(), base + 8, base + 28)
        }
        repeat(355) { index ->
            putGbaText(bytes, moveNames + index * 13, "MOVE")
            val base = moveData + index * 12
            bytes[base + 1] = 40
            bytes[base + 2] = 1
            bytes[base + 3] = 100
            bytes[base + 4] = 35
        }

        val probe = FamilyParsers.all.single { it.family == EngineFamily.RUBY_SAPPHIRE }
            .probe(RomImage(bytes), RomHeader(Platform.GBA, "ARCOIRIS", "-S01"))
        val layout = assertNotNull(probe.resolvedLayout).let { probe.resolvedLayout!! }

        assertEquals(412, layout.speciesCount)
        assertEquals(names, layout.tables.speciesNames?.offset)
        assertEquals(stats, layout.tables.baseStats?.offset)
        assertEquals(moveNames, layout.tables.moveNames?.offset)
        assertEquals(moveData, layout.tables.moveData?.offset)
    }

    private fun putPointer(bytes: ByteArray, offset: Int, target: Int) {
        val value = 0x08000000 + target
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun putGbaText(bytes: ByteArray, offset: Int, value: String) {
        value.forEachIndexed { index, character ->
            bytes[offset + index] = (0xBB + character.code - 'A'.code).toByte()
        }
        bytes[offset + value.length] = 0xFF.toByte()
    }
}
