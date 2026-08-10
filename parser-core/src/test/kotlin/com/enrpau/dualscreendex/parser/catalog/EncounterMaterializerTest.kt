package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
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
        assertEquals(7, areas.single { it.id == 0x101 * 10 + EncounterMethods.GRASS_MORNING }.slots.size)
        assertEquals(3, areas.single { it.id == 0x101 * 10 + EncounterMethods.WATER }.slots.size)
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
}
