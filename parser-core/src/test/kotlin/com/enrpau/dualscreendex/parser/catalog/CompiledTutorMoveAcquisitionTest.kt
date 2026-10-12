package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import org.junit.Assert.*
import org.junit.Test

class CompiledTutorMoveAcquisitionTest {
    @Test
    fun decodesOneScriptBackedTutorWithNativeMoveInputAndPartialSourceCoverage() {
        for (origin in listOf(0, 0x4000, 0x8000)) {
            val f = TutorFixture(origin)
            val result = f.materialize()
            for (species in 1..4) assertEquals(listOf(tutor(7)), result.acquisitionsBySpecies[species].orEmpty().filter { it.method == MoveAcquisitionMethod.TUTOR })
            assertEquals(emptyList<MoveAcquisition>(), result.acquisitionsBySpecies[5].orEmpty().filter { it.method == MoveAcquisitionMethod.TUTOR })
            val evidence = result.evidence.getValue(MoveAcquisitionMethod.TUTOR)
            assertTrue(evidence.compatible)
            assertEquals(CapabilityStatus.PARTIAL, evidence.status)
            assertEquals(0.0, evidence.confidence, 0.0)
            assertNull(evidence.expectedRecords)
            assertEquals(CapabilityStatus.AVAILABLE, result.evidence.getValue(MoveAcquisitionMethod.MACHINE).status)
        }
    }

    @Test
    fun addsOnlyTheNextDistinctSourceAfterSkippingRepeatedMoveInputs() {
        val f = TutorFixture(0)
        for (offset in listOf(16, 32)) f.bytes.copyInto(f.bytes, f.script + offset, f.script, f.script + 10)
        f.u16(f.script + 35, 301)
        val result = f.materialize()
        assertEquals(listOf(tutor(7), tutor(301)), result.acquisitionsBySpecies[3].orEmpty().filter { it.method == MoveAcquisitionMethod.TUTOR })
        assertEquals(CapabilityStatus.PARTIAL, result.evidence.getValue(MoveAcquisitionMethod.TUTOR).status)
    }

    @Test
    fun addsTheThirdDistinctSourceWhileKeepingOverrides() {
        for (origin in listOf(0, 0x4000, 0x8000)) {
            val f = TutorFixture(origin)
            for (offset in listOf(16, 32, 48)) f.bytes.copyInto(f.bytes, f.script + offset, f.script, f.script + 10)
            f.u16(f.script + 35, 301)
            f.u16(f.script + 51, 9)
            val result = f.materialize()
            assertEquals(listOf(tutor(7), tutor(301), tutor(9)), result.acquisitionsBySpecies[3].orEmpty().filter { it.method == MoveAcquisitionMethod.TUTOR })
            assertEquals(listOf(tutor(7), tutor(301)), result.acquisitionsBySpecies[4].orEmpty().filter { it.method == MoveAcquisitionMethod.TUTOR })
            assertEquals(listOf(tutor(9)), result.acquisitionsBySpecies[5].orEmpty().filter { it.method == MoveAcquisitionMethod.TUTOR })
            val evidence = result.evidence.getValue(MoveAcquisitionMethod.TUTOR)
            assertEquals(CapabilityStatus.PARTIAL, evidence.status)
            assertNull(evidence.expectedRecords)
        }
    }

    @Test
    fun addsOnlyTheFourthDistinctSourceAndLeavesTheFifthUnresolved() {
        for (origin in listOf(0, 0x4000, 0x8000)) {
            val f = TutorFixture(origin)
            for (offset in listOf(16, 32, 48, 64, 80)) f.bytes.copyInto(f.bytes, f.script + offset, f.script, f.script + 10)
            f.u16(f.script + 35, 301)
            f.u16(f.script + 51, 9)
            f.u16(f.script + 67, 10)
            f.u16(f.script + 83, 11)
            val result = f.materialize()
            assertEquals(listOf(tutor(7), tutor(301), tutor(9)), result.acquisitionsBySpecies[3].orEmpty().filter { it.method == MoveAcquisitionMethod.TUTOR })
            assertEquals(listOf(tutor(7), tutor(301), tutor(10)), result.acquisitionsBySpecies[4].orEmpty().filter { it.method == MoveAcquisitionMethod.TUTOR })
            assertEquals(CapabilityStatus.PARTIAL, result.evidence.getValue(MoveAcquisitionMethod.TUTOR).status)
            assertNull(result.evidence.getValue(MoveAcquisitionMethod.TUTOR).expectedRecords)
        }
    }

    @Test
    fun acceptsOtherMoveInputsAndVariableSlotsWithoutRetailOrdinals() {
        for (move in listOf(9, 301)) {
            val f = TutorFixture(0x4000)
            f.u16(f.script + 1, 0x8006); f.u16(f.script + 3, move)
            f.u32(f.variables + 6 * 4, 0x02002200)
            val result = f.materialize()
            assertEquals(listOf(tutor(move)), result.acquisitionsBySpecies[3].orEmpty().filter { it.method == MoveAcquisitionMethod.TUTOR })
            assertEquals(if (move == 9) emptyList<MoveAcquisition>() else listOf(tutor(move)),
                result.acquisitionsBySpecies[4].orEmpty().filter { it.method == MoveAcquisitionMethod.TUTOR })
        }
    }

    @Test
    fun retainsBoundedSourcesAndLeavesTotalCoverageUnknown() {
        val f = TutorFixture(0)
        f.bytes.copyInto(f.bytes, f.script + 16, f.script, f.script + 10)
        f.u16(f.script + 19, 301)
        val result = f.materialize()
        assertEquals(listOf(tutor(7), tutor(301)), result.acquisitionsBySpecies[3].orEmpty().filter { it.method == MoveAcquisitionMethod.TUTOR })
        assertNull(result.evidence.getValue(MoveAcquisitionMethod.TUTOR).incompleteRecords)
    }

    @Test
    fun failsClosedOnBrokenSourceJoinsWithoutLosingMachines() {
        for (failure in 0..14) {
            val f = TutorFixture(0)
            when (failure) {
                0 -> f.u16(f.script + 1, 0x8004)
                1 -> f.u16(f.script + 3, 400)
                2 -> f.bytes[f.script + 5] = 5
                3 -> f.bytes[f.helper + 3] = 0
                4 -> f.pointer(f.specials + 53 * 4, f.callback + 1)
                5 -> f.words(f.chooser + 28, 0xB004)
                6 -> f.u16(f.initializer + 26, 0x061B)
                7 -> f.u16(f.initializer + 110, 0x729F)
                8 -> f.u16(f.initializer + 124, 0x980B)
                9 -> f.u32(f.selection + 64, 0x02001204)
                10 -> f.pointer(f.dispatch + 9 * 4, f.callback)
                11 -> f.bl(f.workflow + 76, f.base.kernel)
                12 -> f.u16(f.readHalf + 8, 0x601A)
                13 -> f.u16(f.variableGetter + 36, 0x0049)
                14 -> f.u16(f.selection + 44, 0xD901)
            }
            val result = f.materialize()
            assertFalse("broken join $failure", result.evidence.getValue(MoveAcquisitionMethod.TUTOR).compatible)
            assertEquals(listOf(MoveAcquisition(7, MoveAcquisitionMethod.MACHINE, 1), MoveAcquisition(301, MoveAcquisitionMethod.MACHINE, 3)),
                result.acquisitionsBySpecies[1])
        }
    }

    @Test
    fun matchesRetainedManualNativeColumnAndPreservesAllMachinePairs() {
        val configured = System.getenv("DUALDEX_RETAINED_TUTOR_CONTROL")
        org.junit.Assume.assumeTrue("optional retained-only control", configured != null)
        val lines = java.io.File(requireNotNull(configured)).readLines().map { it.split('\t') }
        val values = lines.single { it[0] == "layout" }.drop(1).map(String::toInt)
        val bytes = ByteArray(32 * 1024 * 1024)
        val occupied = java.util.BitSet(bytes.size)
        for (row in lines.filter { it[0] == "bytes" }) {
            val start = row[1].toInt(); val hex = row[2]
            for (index in 0 until hex.length / 2) {
                val at = start + index; val byte = hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
                if (occupied[at]) assertEquals(bytes[at], byte)
                occupied.set(at); bytes[at] = byte
            }
        }
        val layout = com.enrpau.dualscreendex.parser.model.ResolvedRomLayout(
            com.enrpau.dualscreendex.parser.model.EngineFamily.EMERALD, 3,
            com.enrpau.dualscreendex.parser.model.Platform.GBA, values[0], values[1],
            com.enrpau.dualscreendex.parser.model.ProfileTables(moveNames = com.enrpau.dualscreendex.parser.model.TableLayout(values[2], values[1] - 1, values[3])),
            itemNameAuthority = com.enrpau.dualscreendex.parser.model.GbaItemNameAuthority.Available(values[4], values[5], values[6], values[6], null,
                com.enrpau.dualscreendex.parser.model.GbaItemNameProvenance.COMPILED_CONSUMER))
        val result = MoveAcquisitionMaterializer.materialize(RomImage(bytes), layout)
        val expectedSources = lines.filter { it[0] == "tutor" }.map { row ->
            row[1].toInt() to row[2].split(',').map(String::toInt).toSet()
        }
        for (species in 0 until values[0]) {
            val actual = result.acquisitionsBySpecies[species].orEmpty().filter { it.method == MoveAcquisitionMethod.TUTOR }
            val expected = expectedSources.mapNotNull { (move, eligible) -> tutor(move).takeIf { species in eligible } }
            assertEquals("native species input $species", expected, actual)
        }
        for (row in lines.filter { it[0] == "machine" }) {
            val actual = result.acquisitionsBySpecies[row[1].toInt()].orEmpty().filter { it.method == MoveAcquisitionMethod.MACHINE }
                .map { it.moveId to it.sourceId }.sortedWith(compareBy({ it.first }, { it.second }))
            val pairs = row[2].takeIf(String::isNotEmpty)?.split(',')?.map { pair -> pair.split(':').let { it[0].toInt() to it[1].toInt() } }.orEmpty()
            assertEquals("preserved machine input ${row[1]}", pairs, actual)
        }
        assertEquals(CapabilityStatus.PARTIAL, result.evidence.getValue(MoveAcquisitionMethod.TUTOR).status)
    }

    private fun tutor(move: Int) = MoveAcquisition(move, MoveAcquisitionMethod.TUTOR, move)

    internal class TutorFixture(val origin: Int) {
        val base = CompiledItemMoveAcquisitionTest.Fixture(origin)
        val bytes = base.bytes.copyOf(origin + 0x8000)
        val script = origin + 0x4000
        val helper = origin + 0x4080
        val workflow = origin + 0x4100
        val chooser = origin + 0x4200
        val initializer = origin + 0x4300
        val callback = origin + 0x4400
        val selection = origin + 0x4500
        val setter = origin + 0x4600
        val actionCase = origin + 0x4700
        val dispatch = origin + 0x4800
        val callCommand = origin + 0x4A00
        val variableGetter = origin + 0x4B00
        val readWord = origin + 0x4C00
        val scriptCall = origin + 0x4D00
        val pushStack = origin + 0x4D40
        val readHalf = origin + 0x4E00
        val specialCommand = origin + 0x4F00
        val commandTable = origin + 0x7000
        val variables = origin + 0x7600
        val specials = origin + 0x7800

        init {
            bytes[script] = 0x16; u16(script + 1, 0x8005); u16(script + 3, 7)
            bytes[script + 5] = 4; pointer(script + 6, helper)
            byteArrayOf(0x25, 53, 0, 0x27, 0x6A, 0x5A, 3).copyInto(bytes, helper)
            words(workflow, 0xB5F0, 0x0600, 0x0E06)
            literal(workflow + 6, 0, workflow + 116, 0x02001000)
            words(workflow + 8, 0x79C1, 0x2080, 0x4008, 0x2800, 0xD100)
            literal(workflow + 18, 5, workflow + 120, 0x02001200)
            words(workflow + 20, 0x2109, 0x5669, 0x2064, 0x4341)
            literal(workflow + 28, 0, workflow + 124, 0x02001400)
            words(workflow + 30, 0x180C, 0x1C2F, 0x370E)
            literal(workflow + 36, 1, workflow + 128, 0x02001600)
            words(workflow + 38, 0x1C20); bl(workflow + 40, origin + 0x700)
            literal(workflow + 44, 0, workflow + 132, 0x02002200)
            words(workflow + 46, 0x8800, 0x81E8)
            literal(workflow + 50, 0, workflow + 136, 0x02001800)
            words(workflow + 52, 0x210E, 0x5E6A, 0x210D, 0x4351)
            literal(workflow + 60, 2, workflow + 144, 0x08000000L + base.names)
            words(workflow + 62, 0x1889); bl(workflow + 64, origin + 0x700)
            words(workflow + 68, 0x2002, 0x8078, 0x89E9, 0x1C20); bl(workflow + 76, base.wrapper)
            words(workflow + 80, 0x0600, 0x0E00, 0x2801)
            words(chooser, 0xB500, 0xB083, 0x2004, 0x9000)
            literal(chooser + 8, 0, chooser + 40, 0x08000001L + callback); words(chooser + 10, 0x9001)
            literal(chooser + 12, 0, chooser + 44, 0x08000001L + callback); words(chooser + 14, 0x9002, 0x2000, 0x2100, 0x220C, 0x2300)
            bl(chooser + 24, initializer); words(chooser + 28, 0xB003, 0xBC01, 0x4700)
            words(initializer, 0xB5F0, 0x4657, 0x464E, 0x4645, 0xB4E0, 0xB081, 0x9C09,
                0x0600, 0x0E00, 0x4680, 0x0609, 0x0E09, 0x4689, 0x0612, 0x0E17,
                0x061B, 0x0E1B, 0x9300, 0x0624, 0x0E26)
            bl(initializer + 40, origin + 0x700)
            literal(initializer + 44, 0, initializer + 76, 0x02002400)
            words(initializer + 46, 0x4682, 0x208E, 0x0080)
            literal(initializer + 52, 1, initializer + 80, 0x02002600)
            bl(initializer + 54, origin + 0x700)
            words(initializer + 58, 0x1C05, 0x4651, 0x600D, 0x2D00, 0xD107, 0x980B)
            bl(initializer + 70, origin + 0x700); words(initializer + 74, 0xE000)
            literal(initializer + 84, 3, initializer + 200, 0x02001200)
            words(initializer + 86, 0x210F, 0x4644, 0x4021, 0x7A1A, 0x2010, 0x4240, 0x4010,
                0x4308, 0x7218, 0x980B, 0x6018, 0x2400, 0x72DF, 0x00B2, 0x8969,
                0x2003, 0x4008, 0x4310, 0x8168, 0x980A, 0x6028, 0x606C)
            words(callback, 0xB570, 0x0600, 0x0E05, 0x1C2E); bl(callback + 74, selection)
            words(selection, 0xB5F0, 0x4647, 0xB480, 0x1C0C, 0x0600, 0x0E05, 0x2000, 0x5620, 0x2806, 0xD107)
            literal(selection + 20, 0, selection + 32, 0x02001200)
            words(selection + 22, 0x6841, 0x1C28); bl(selection + 26, origin + 0x700)
            words(selection + 30, 0xE000)
            literal(selection + 36, 0, selection + 64, 0x02001200)
            words(selection + 38, 0x7AC0, 0x3803, 0x280C, 0xD900, 0xE000, 0x0080)
            literal(selection + 50, 1, selection + 68, 0x08000000L + dispatch)
            words(selection + 52, 0x1840, 0x6800, 0x4687)
            repeat(13) { pointer(dispatch + it * 4, origin + 0x700) }; pointer(dispatch + 9 * 4, actionCase)
            words(actionCase, 0x1C20); bl(actionCase + 2, origin + 0x700)
            words(actionCase + 6, 0x0600, 0x2800, 0xD100, 0xE000, 0x2005)
            bl(actionCase + 16, origin + 0x700)
            literal(actionCase + 20, 0, actionCase + 64, 0x02002400)
            words(actionCase + 22, 0x6800, 0x300D); bl(actionCase + 26, origin + 0x700)
            words(actionCase + 30, 0x1C28); bl(actionCase + 32, workflow)
            commands()
        }

        private fun commands() {
            words(setter, 0xB530, 0x1C05); bl(setter + 4, readHalf)
            words(setter + 8, 0x0400, 0x0C00); bl(setter + 12, variableGetter)
            words(setter + 16, 0x1C04, 0x1C28); bl(setter + 20, readHalf)
            words(setter + 24, 0x8020, 0x2000, 0xBC30, 0xBC02, 0x4708)
            words(readHalf, 0x1C03, 0x689A, 0x7810, 0x3201, 0x609A, 0x7811, 0x0209, 0x4308, 0x3201, 0x609A, 0x4770)
            words(callCommand, 0xB510, 0x1C04); bl(callCommand + 4, readWord)
            words(callCommand + 8, 0x1C01, 0x1C20); bl(callCommand + 12, scriptCall)
            words(callCommand + 16, 0x2000, 0xBC10, 0xBC02, 0x4708)
            words(readWord, 0xB570, 0x1C03, 0x6898, 0x7806, 0x3001, 0x6098, 0x7805, 0x1C42, 0x609A, 0x7844, 0x1C51, 0x6099, 0x7850, 0x3101, 0x6099, 0x0200, 0x1900, 0x0200, 0x1940, 0x0200, 0x1980, 0xBC70, 0xBC02, 0x4708)
            words(scriptCall, 0xB530, 0x1C04, 0x1C0D, 0x68A1); bl(scriptCall + 8, pushStack)
            words(scriptCall + 12, 0x60A5, 0xBC30, 0xBC01, 0x4700)
            words(pushStack, 0xB500, 0x1C02, 0x1C0B, 0x7811, 0x1C48, 0x2813, 0xDC09, 0x0088, 0x1C11, 0x310C, 0x1809, 0x600B, 0x7810, 0x3001, 0x7010, 0x2000, 0xE000, 0x2001, 0xBC02, 0x4708)
            words(specialCommand, 0xB500); bl(specialCommand + 2, readHalf)
            words(specialCommand + 6, 0x0400)
            literal(specialCommand + 8, 1, specialCommand + 28, 0x08000000L + specials)
            words(specialCommand + 10, 0x0B80, 0x1840, 0x6800); bl(specialCommand + 16, origin + 0x4FF0)
            words(specialCommand + 20, 0x2000, 0xBC02, 0x4708); words(origin + 0x4FF0, 0x4700)
            words(variableGetter, 0xB500, 0x0400, 0x0C01, 0x1C0A)
            literal(variableGetter + 8, 0, variableGetter + 20, 0x3FFF)
            words(variableGetter + 10, 0x4281, 0xD804, 0x2000, 0xE016)
            words(variableGetter + 24, 0x0408, 0x2800, 0xDA0A)
            literal(variableGetter + 30, 0, variableGetter + 44, 0x08000000L + variables)
            literal(variableGetter + 32, 2, variableGetter + 48, 0xFFFF8000)
            words(variableGetter + 34, 0x1889, 0x0089, 0x1809, 0x6808, 0xE009)
            literal(variableGetter + 52, 0, variableGetter + 68, 0x03005DBC)
            words(variableGetter + 54, 0x0051)
            literal(variableGetter + 56, 2, variableGetter + 72, 0xFFFF939C)
            words(variableGetter + 58, 0x1889, 0x6800, 0x1840, 0xBC02, 0x4708)
            pointer(commandTable + 0x16 * 4, setter + 1)
            pointer(commandTable + 4 * 4, callCommand + 1)
            pointer(commandTable + 0x25 * 4, specialCommand + 1)
            u32(variables + 5 * 4, 0x02002200); pointer(specials + 53 * 4, chooser + 1)
        }

        fun materialize() = MoveAcquisitionMaterializer.materialize(RomImage(bytes), base.layout)
        fun u16(at: Int, value: Int) { bytes[at] = value.toByte(); bytes[at + 1] = (value ushr 8).toByte() }
        fun u32(at: Int, value: Long) { repeat(4) { bytes[at + it] = (value ushr (8 * it)).toByte() } }
        fun pointer(at: Int, target: Int) = u32(at, 0x08000000L + target)
        fun words(at: Int, vararg ops: Int) = ops.forEachIndexed { index, op -> u16(at + index * 2, op) }
        fun literal(at: Int, register: Int, pool: Int, value: Long) {
            u16(at, 0x4800 or (register shl 8) or ((pool - ((at + 4) and -4)) / 4)); u32(pool, value)
        }
        fun bl(at: Int, target: Int) {
            val delta = target - at - 4
            words(at, 0xF000 or ((delta ushr 12) and 2047), 0xF800 or ((delta ushr 1) and 2047))
        }
    }
}
