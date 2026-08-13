package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityReviewStatus
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EncounterMaterializerTest {
    @Test
    fun discoversGenOneWildPointerTableAndMapSlots() {
        val bytes = ByteArray(0x8000)
        val table = 0x4000
        repeat(248) { map -> putU16(bytes, table + map * 2, if (map in 12..31) 0x5200 + (map - 12) * 42 else 0x5000) }
        putU16(bytes, table + 248 * 2, 0xFFFF)
        bytes[0x5000] = 0
        bytes[0x5001] = 0
        repeat(20) { index ->
            val record = 0x5200 + index * 42
            bytes[record] = 25
            repeat(10) { slot ->
                bytes[record + 1 + slot * 2] = (3 + slot).toByte()
                bytes[record + 2 + slot * 2] = 10
            }
            bytes[record + 21] = 10
            repeat(10) { slot ->
                bytes[record + 22 + slot * 2] = (5 + slot).toByte()
                bytes[record + 23 + slot * 2] = 20
            }
        }

        val areas = EncounterMaterializer.materialize(RomImage(bytes), layout(1, Platform.GB, 190))

        val grass = areas.single { it.id == 12 * 10 + EncounterMethods.GRASS }
        val water = areas.single { it.id == 12 * 10 + EncounterMethods.WATER }
        assertEquals(10, grass.slots.size)
        assertEquals(10, water.slots.size)
        assertEquals(10, grass.slots.first().speciesId)
        assertEquals(20, water.slots.first().speciesId)
        assertEquals(setOf(EncounterWindow.ANY), grass.windows)
        assertEquals(setOf(EncounterWindow.ANY), water.windows)
    }

    @Test
    fun discoversGenTwoGrassTimeAndWaterArrays() {
        val bytes = ByteArray(0x2000)
        repeat(5) { record ->
            val base = 0x100 + record * 47
            bytes[base] = 1
            bytes[base + 1] = (record + 1).toByte()
            bytes[base + 2] = 10
            bytes[base + 3] = 20
            bytes[base + 4] = 30
            repeat(21) { slot ->
                bytes[base + 5 + slot * 2] = (4 + slot % 7).toByte()
                bytes[base + 6 + slot * 2] = (10 + slot % 7).toByte()
            }
        }
        bytes[0x100 + 5 * 47] = 0xFF.toByte()
        val waterTable = 0x100 + 5 * 47 + 1
        repeat(5) { record ->
            val base = waterTable + record * 9
            bytes[base] = 1
            bytes[base + 1] = (record + 1).toByte()
            bytes[base + 2] = 15
            repeat(3) { slot ->
                bytes[base + 3 + slot * 2] = (10 + slot).toByte()
                bytes[base + 4 + slot * 2] = (20 + slot).toByte()
            }
        }
        bytes[waterTable + 5 * 9] = 0xFF.toByte()

        val areas = EncounterMaterializer.materialize(RomImage(bytes), layout(2, Platform.GBC, 251))

        assertEquals(20, areas.size)
        assertEquals(
            setOf(EncounterWindow.MORNING),
            areas.single { it.id == 0x101 * 10 + EncounterMethods.GRASS_MORNING }.windows,
        )
        assertEquals(
            setOf(EncounterWindow.DAY),
            areas.single { it.id == 0x101 * 10 + EncounterMethods.GRASS_DAY }.windows,
        )
        assertEquals(
            setOf(EncounterWindow.NIGHT),
            areas.single { it.id == 0x101 * 10 + EncounterMethods.GRASS_NIGHT }.windows,
        )
        val morning = areas.single { it.id == 0x101 * 10 + EncounterMethods.GRASS_MORNING }
        val water = areas.single { it.id == 0x101 * 10 + EncounterMethods.WATER }
        assertEquals(7, morning.slots.size)
        assertEquals(3, water.slots.size)
        assertEquals(setOf(EncounterWindow.ANY), water.windows)
    }

    @Test
    fun discoversGenThreeWildHeaderAndInfoPointers() {
        val bytes = ByteArray(0x2000)
        repeat(3) { map ->
            val header = 0x100 + map * 20
            bytes[header] = 1
            bytes[header + 1] = (map + 1).toByte()
            putGbaPointer(bytes, header + 4, 0x500 + map * 8)
            val info = 0x500 + map * 8
            bytes[info] = if (map == 1) 0 else 20
            putGbaPointer(bytes, info + 4, 0x800 + map * 48)
            repeat(12) { slot ->
                val entry = 0x800 + map * 48 + slot * 4
                bytes[entry] = (5 + slot).toByte()
                bytes[entry + 1] = (7 + slot).toByte()
                putU16(bytes, entry + 2, 30 + slot)
            }
        }
        bytes[0x100 + 3 * 20] = 0xFF.toByte()
        bytes[0x100 + 3 * 20 + 1] = 0xFF.toByte()

        val areas = EncounterMaterializer.materialize(RomImage(bytes), layout(3, Platform.GBA, 100))

        assertEquals(3, areas.size)
        val first = areas.single { it.id == 0x101 * 10 + EncounterMethods.GRASS }
        assertEquals(12, first.slots.size)
        assertEquals(30, first.slots.first().speciesId)
        assertTrue(first.name.value!!.contains("1-1"))
        assertEquals(setOf(EncounterWindow.ANY), first.windows)
    }

    @Test
    fun prefersTheMainReferencedGenThreeTableWhenAnEncounterRateExceedsOneHundred() {
        val bytes = ByteArray(0x4000)
        val main = 0x100
        val facility = 0x300
        putGenThreeInfo(bytes, 0x500, 150, 0x1000, 12, 20)
        putGenThreeInfo(bytes, 0x508, 20, 0x1100, 12, 40)
        putStandardGenThreeTable(bytes, main, 0x500)
        putStandardGenThreeTable(bytes, facility, 0x508)
        bytes[0x501] = 0x0F
        bytes[0x502] = 0xD5.toByte()
        bytes[main + 2] = 0x7A
        bytes[main + 3] = 0x55
        repeat(3) { bytes[main + it * 20] = 2 }
        repeat(3) { index -> putGbaPointer(bytes, 0x20 + index * 4, main) }
        repeat(2) { index -> putGbaPointer(bytes, 0x30 + index * 4, facility) }

        val result = EncounterMaterializer.materializeWithEvidence(
            RomImage(bytes),
            layout(3, Platform.GBA, 100),
        )

        assertEquals(listOf(0x201, 0x202, 0x203), result.areas.map { it.id / 10 })
        assertEquals(main, result.selectedRootOffset)
        assertEquals(20, result.headerSize)
        assertEquals(3, result.headerCount)
        assertEquals(3, result.populatedMethodCount)
        assertEquals(3, result.referenceCount)
        assertEquals(2, result.candidateCount)
        assertTrue(result.reasons.single().contains("root=0x100"))
    }

    @Test
    fun discoversReferencedTwentyFourByteGenThreeHeadersWithHiddenEncounters() {
        val bytes = ByteArray(0x4000)
        val table = 0x100
        putGbaPointer(bytes, 0x20, table)
        putGbaPointer(bytes, 0x24, table)

        // A plausible adjacent record is not part of the compiled-referenced root.
        bytes[table - 24] = 9
        bytes[table - 23] = 9
        putGbaPointer(bytes, table - 20, 0x520)
        putGenThreeInfo(bytes, 0x520, 20, 0x1400, 12, 80)

        bytes[table] = 1
        bytes[table + 1] = 1
        putGbaPointer(bytes, table + 4, 0x500)
        putGbaPointer(bytes, table + 16, 0x508)
        putGbaPointer(bytes, table + 20, 0x510)
        putGenThreeInfo(bytes, 0x500, 20, 0x1000, 12, 10)
        putGenThreeInfo(bytes, 0x508, 0, 0x1100, 3, 50)
        putGenThreeInfo(bytes, 0x510, 30, 0x1200, 10, 60)
        putU16(bytes, 0x1202, 0)

        // A compiled WildPokemonHeader may name a map while leaving every method empty.
        bytes[table + 24] = 1
        bytes[table + 25] = 2

        val third = table + 48
        bytes[third] = 1
        bytes[third + 1] = 3
        putGbaPointer(bytes, third + 16, 0x518)
        putGenThreeInfo(bytes, 0x518, 1, 0x1300, 3, 70)

        bytes[table + 72] = 0xFF.toByte()
        bytes[table + 73] = 0xFF.toByte()

        val areas = EncounterMaterializer.materialize(RomImage(bytes), layout(3, Platform.GBA, 100))

        assertEquals(4, areas.size)
        val hidden = areas.single { it.id == 0x101 * 10 + EncounterMethods.HIDDEN }
        assertTrue(hidden.name.value!!.contains("hidden"))
        assertTrue(hidden.name.value!!.contains("land"))
        assertEquals(setOf(EncounterWindow.ANY), hidden.windows)
        assertEquals(EncounterSlot(50, 5, 7, 60), hidden.slots.first())
        val fishing = areas.single { it.id == 0x101 * 10 + EncounterMethods.FISHING }
        assertEquals(9, fishing.slots.size)
        assertTrue(fishing.slots.none { it.speciesId == 0 })
        val waterHidden = areas.single { it.id == 0x103 * 10 + EncounterMethods.HIDDEN }
        assertTrue(waterHidden.name.value!!.contains("water"))
        assertEquals(setOf(EncounterWindow.ANY), waterHidden.windows)
    }

    @Test
    fun rejectsUnreferencedTwentyFourByteGenThreeHeaderShape() {
        val bytes = ByteArray(0x4000)
        val table = 0x100
        bytes[table] = 1
        bytes[table + 1] = 1
        putGbaPointer(bytes, table + 4, 0x500)
        putGbaPointer(bytes, table + 16, 0x508)
        putGbaPointer(bytes, table + 20, 0x510)
        putGenThreeInfo(bytes, 0x500, 20, 0x1000, 12, 10)
        putGenThreeInfo(bytes, 0x508, 0, 0x1100, 3, 50)
        putGenThreeInfo(bytes, 0x510, 30, 0x1200, 10, 60)
        bytes[table + 24] = 1
        bytes[table + 25] = 2
        val third = table + 48
        bytes[third] = 1
        bytes[third + 1] = 3
        putGbaPointer(bytes, third + 16, 0x518)
        putGenThreeInfo(bytes, 0x518, 1, 0x1300, 3, 70)
        bytes[table + 72] = 0xFF.toByte()
        bytes[table + 73] = 0xFF.toByte()

        val areas = EncounterMaterializer.materialize(RomImage(bytes), layout(3, Platform.GBA, 100))

        assertTrue(areas.isEmpty())
    }

    @Test
    fun discoversReferencedTwentyFourByteGenThreeTableWithEmptyFirstHeader() {
        val bytes = ByteArray(0x4000)
        val table = 0x100
        putGbaPointer(bytes, 0x20, table)
        putGbaPointer(bytes, 0x24, table)
        putLeadingEmptyTwentyFourByteTable(bytes, table)

        val areas = EncounterMaterializer.materialize(RomImage(bytes), layout(3, Platform.GBA, 100))

        assertEquals(3, areas.size)
        assertEquals(listOf(0x102, 0x103, 0x104), areas.map { it.id / 10 })
        assertTrue(areas.all { it.methodId == EncounterMethods.GRASS })
    }

    @Test
    fun discoversReferencedSparseTwentyFourByteTableWithFiveLeadingEmptyHeaders() {
        val bytes = ByteArray(0x5000)
        val table = 0x100
        putGbaPointer(bytes, 0x20, table)
        putGbaPointer(bytes, 0x24, table)
        repeat(8) { index ->
            val header = table + index * 24
            bytes[header] = 1
            bytes[header + 1] = (index + 1).toByte()
            if (index >= 5) {
                val info = 0x500 + (index - 5) * 8
                putGbaPointer(bytes, header + 4, info)
                putGenThreeInfo(bytes, info, 20, 0x1000 + (index - 5) * 0x100, 12, 10 + (index - 5) * 12)
            }
        }
        bytes[table + 8 * 24] = 0xFF.toByte()
        bytes[table + 8 * 24 + 1] = 0xFF.toByte()

        val areas = EncounterMaterializer.materialize(RomImage(bytes), layout(3, Platform.GBA, 100))

        assertEquals(3, areas.size)
        assertEquals(listOf(0x106, 0x107, 0x108), areas.map { it.id / 10 })
    }

    @Test
    fun rejectsUnreferencedTwentyFourByteTableWithEmptyFirstHeader() {
        val bytes = ByteArray(0x4000)
        putLeadingEmptyTwentyFourByteTable(bytes, 0x100)

        val areas = EncounterMaterializer.materialize(RomImage(bytes), layout(3, Platform.GBA, 100))

        assertTrue(areas.isEmpty())
    }

    @Test
    fun rejectsSameRootThatValidatesAsBothTwentyAndTwentyFourByteHeaders() {
        val bytes = ByteArray(0x5000)
        val table = 0x100
        putGbaPointer(bytes, 0x20, table)
        putGbaPointer(bytes, 0x24, table)
        val pointerFields = listOf(
            4 to 12,
            8 to 5,
            12 to 5,
            16 to 10,
            28 to 12,
            32 to 5,
            36 to 10,
            44 to 12,
            52 to 12,
            56 to 10,
            64 to 12,
            68 to 10,
            76 to 12,
            84 to 12,
            88 to 5,
            92 to 10,
            104 to 12,
            108 to 5,
            112 to 5,
            116 to 10,
        )
        pointerFields.forEachIndexed { index, (field, slotCount) ->
            val info = 0x500 + index * 8
            val slots = 0x1000 + index * 0x40
            putGbaPointer(bytes, table + field, info)
            putGenThreeInfo(
                bytes,
                info,
                if (field in setOf(16, 64, 88, 112)) 1 else 20,
                slots,
                slotCount,
                10 + index,
            )
        }
        bytes[table + 120] = 0xFF.toByte()
        bytes[table + 121] = 0xFF.toByte()

        val areas = EncounterMaterializer.materialize(RomImage(bytes), layout(3, Platform.GBA, 100))

        assertTrue(areas.isEmpty())
    }

    @Test
    fun rejectsEquallyReferencedGenThreeHeaderRootsAsAmbiguous() {
        val bytes = ByteArray(0x6000)
        val first = 0x100
        val second = 0x300
        putGbaPointer(bytes, 0x20, first)
        putGbaPointer(bytes, 0x24, first)
        putGbaPointer(bytes, 0x28, second)
        putGbaPointer(bytes, 0x2C, second)
        putGenThreeInfo(bytes, 0x500, 20, 0x1000, 12, 10)
        putStandardGenThreeTable(bytes, first, 0x500)
        putStandardGenThreeTable(bytes, second, 0x500)

        val areas = EncounterMaterializer.materialize(RomImage(bytes), layout(3, Platform.GBA, 100))

        assertTrue(areas.isEmpty())
    }

    @Test
    fun rejectsGenThreeHeaderCandidateBudgetOverflowBeforeADeceptiveWinner() {
        val bytes = ByteArray(0x50000)
        val info = 0x48000
        putGenThreeInfo(bytes, info, 20, 0x48100, 12, 10)
        val roots = List(258) { index -> 0x1000 + index * 0x80 }
        roots.forEachIndexed { index, root ->
            putGbaPointer(bytes, 0x100 + index * 4, root)
            putStandardGenThreeTable(bytes, root, info)
        }
        val deceptiveWinner = roots.last()
        putGbaPointer(bytes, 0x800, deceptiveWinner)
        putGbaPointer(bytes, 0x804, deceptiveWinner)

        val areas = EncounterMaterializer.materialize(RomImage(bytes), layout(3, Platform.GBA, 100))

        assertTrue(areas.isEmpty())
    }

    @Test
    fun findsLateReferencedClassicTableAfterManyCheapRejectedEmptyDecoys() {
        val bytes = ByteArray(0x70000)
        repeat(8_097) { index ->
            val decoy = 0x10000 + index * 4
            putGbaPointer(bytes, 0x6000 + index * 4, decoy)
        }
        val table = 0x60000
        putLeadingEmptyTwentyFourByteTable(bytes, table)
        putGbaPointer(bytes, 0xF000, table)
        putGbaPointer(bytes, 0xF004, table)

        val areas = EncounterMaterializer.materialize(RomImage(bytes), layout(3, Platform.GBA, 100))

        assertEquals(3, areas.size)
        assertEquals(listOf(0x102, 0x103, 0x104), areas.map { it.id / 10 })
    }

    @Test
    fun repeatedReferencesToOnePlausibleMalformedClassicTargetDoNotConsumeCandidateBudget() {
        val bytes = ByteArray(0x50000)
        val malformed = 0x10000
        putPlausibleMalformedLeadingEmptyTwentyFourByteTable(bytes, malformed, 0x48000)
        repeat(300) { index -> putGbaPointer(bytes, 0x1000 + index * 4, malformed) }
        val table = 0x40000
        putLeadingEmptyTwentyFourByteTable(bytes, table)
        putGbaPointer(bytes, 0x2000, table)
        putGbaPointer(bytes, 0x2004, table)

        val areas = EncounterMaterializer.materialize(RomImage(bytes), layout(3, Platform.GBA, 100))

        assertEquals(3, areas.size)
    }

    @Test
    fun repeatedReferencesToOneNoSentinelTargetReuseNegativeShellResult() {
        val bytes = ByteArray(0x30000)
        val noSentinel = 0x10000
        repeat(5_000) { index -> putGbaPointer(bytes, 0x1000 + index * 4, noSentinel) }

        val result = EncounterMaterializer.materializeWithEvidence(
            RomImage(bytes),
            layout(3, Platform.GBA, 100),
        )

        assertTrue(result.areas.isEmpty())
        assertEquals(1, result.probeStats.emptyClassicShellWalks)
    }

    @Test
    fun reportsTotalClassicShellWalkBudgetForInterleavedEvictedTargets() {
        val bytes = ByteArray(0x40000)
        repeat(17_000) { index ->
            putGbaPointer(bytes, 0x1000 + index * 4, 0x20000 + (index % 4_097) * 4)
        }

        val result = EncounterMaterializer.materializeWithEvidence(
            RomImage(bytes),
            layout(3, Platform.GBA, 100),
        )

        assertTrue(result.areas.isEmpty())
        assertEquals(CapabilityStatus.AMBIGUOUS, result.status)
        assertEquals(CapabilityReviewStatus.MANUAL_REVIEW, result.reviewStatus)
        assertEquals(
            "empty-first Classic24 total shell-walk budget exceeded (16384); encounter table selection is ambiguous",
            result.reasons.single(),
        )
        assertEquals(16_384, result.probeStats.emptyClassicShellWalks)
    }

    @Test
    fun followsCfruRelocatedHeaderPointerAndPreservesDynamicSlots() {
        val bytes = ByteArray(0x83000)
        putGbaPointer(bytes, 0x82990, 0x100)
        bytes[0x100] = 3
        bytes[0x101] = 19
        putGbaPointer(bytes, 0x104, 0x500)
        bytes[0x500] = 20
        putGbaPointer(bytes, 0x504, 0x800)
        repeat(12) { slot ->
            val entry = 0x800 + slot * 4
            bytes[entry] = if (slot == 0) 18 else 0
            bytes[entry + 1] = if (slot == 0) 2 else 0
            putU16(bytes, entry + 2, if (slot == 1) 0 else 80 + slot)
        }
        bytes[0x114] = 0xFF.toByte()
        bytes[0x115] = 0xFF.toByte()

        val areas = EncounterMaterializer.materialize(RomImage(bytes), layout(3, Platform.GBA, 200))

        val grass = areas.single()
        assertEquals(12, grass.slots.size)
        assertEquals(18, grass.slots.first().minimumLevel)
        assertEquals(2, grass.slots.first().maximumLevel)
        assertEquals(0, grass.slots[1].speciesId)
    }

    private fun layout(generation: Int, platform: Platform, speciesCount: Int) = ResolvedRomLayout(
        family = when (generation) {
            1 -> EngineFamily.RED_BLUE
            2 -> EngineFamily.CRYSTAL
            else -> EngineFamily.EMERALD
        },
        generation = generation,
        platform = platform,
        speciesCount = speciesCount,
        moveCount = 1,
        tables = ProfileTables(),
    )

    private fun putU16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
    }

    private fun putGbaPointer(target: ByteArray, offset: Int, targetOffset: Int) {
        val value = 0x08000000 + targetOffset
        repeat(4) { index -> target[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun putGenThreeInfo(
        target: ByteArray,
        offset: Int,
        rate: Int,
        slotsOffset: Int,
        slotCount: Int,
        firstSpecies: Int,
    ) {
        target[offset] = rate.toByte()
        putGbaPointer(target, offset + 4, slotsOffset)
        repeat(slotCount) { slot ->
            val entry = slotsOffset + slot * 4
            target[entry] = (5 + slot).toByte()
            target[entry + 1] = (7 + slot).toByte()
            putU16(target, entry + 2, firstSpecies + slot)
        }
    }

    private fun putStandardGenThreeTable(
        target: ByteArray,
        offset: Int,
        infoOffset: Int,
        mapCount: Int = 3,
        group: Int = 1,
    ) {
        repeat(mapCount) { map ->
            val header = offset + map * 20
            target[header] = group.toByte()
            target[header + 1] = (map + 1).toByte()
            putGbaPointer(target, header + 4, infoOffset)
        }
        target[offset + mapCount * 20] = 0xFF.toByte()
        target[offset + mapCount * 20 + 1] = 0xFF.toByte()
    }

    private fun putLeadingEmptyTwentyFourByteTable(target: ByteArray, offset: Int) {
        target[offset] = 1
        target[offset + 1] = 1
        repeat(3) { index ->
            val header = offset + (index + 1) * 24
            val info = 0x500 + index * 8
            target[header] = 1
            target[header + 1] = (index + 2).toByte()
            putGbaPointer(target, header + 4, info)
            putGenThreeInfo(target, info, 20, 0x1000 + index * 0x100, 12, 10 + index * 12)
        }
        target[offset + 96] = 0xFF.toByte()
        target[offset + 97] = 0xFF.toByte()
    }

    private fun putPlausibleMalformedLeadingEmptyTwentyFourByteTable(
        target: ByteArray,
        offset: Int,
        infoOffset: Int,
    ) {
        target[offset] = 1
        target[offset + 1] = 1
        putGenThreeInfo(target, infoOffset, 20, infoOffset + 0x100, 12, 10)
        repeat(4) { index ->
            val header = offset + (index + 1) * 24
            target[header] = 1
            target[header + 1] = (index + 2).toByte()
            putGbaPointer(target, header + 4, infoOffset)
        }
        target[offset + 120] = 64
    }
}
