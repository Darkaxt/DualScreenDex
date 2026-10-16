package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.BitSet

class CompiledTutorConfirmationAcquisitionTest {
    @Test
    fun doesNotTreatAnArbitraryGotoAsTheAcceptedImmediateCall() {
        for (origin in listOf(0, 0x4000, 0x8000)) {
            val fixture = CompiledTutorMoveAcquisitionTest.TutorFixture(origin)
            fixture.bytes[fixture.script + 5] = 5
            val result = fixture.materialize()
            assertFalse(result.evidence.getValue(MoveAcquisitionMethod.TUTOR).compatible)
            assertEquals(
                listOf(MoveAcquisition(7, MoveAcquisitionMethod.MACHINE, 1), MoveAcquisition(301, MoveAcquisitionMethod.MACHINE, 3)),
                result.acquisitionsBySpecies[1],
            )
        }
    }

    @Test
    fun unverifiedConfirmationScriptsCannotDisplaceAcceptedSources() {
        for (origin in listOf(0, 0x4000, 0x8000)) {
            fun acceptedSources(): CompiledTutorMoveAcquisitionTest.TutorFixture {
                val fixture = CompiledTutorMoveAcquisitionTest.TutorFixture(origin)
                for ((index, move) in listOf(7, 301, 9, 10, 11, 12, 13, 14, 15, 16).withIndex()) {
                    fixture.bytes.copyInto(fixture.bytes, fixture.script + index * 16, fixture.script, fixture.script + 10)
                    fixture.u16(fixture.script + index * 16 + 3, move)
                }
                return fixture
            }
            val expected = acceptedSources().materialize()
            for (failure in 0..9) {
                val fixture = acceptedSources()
                val helper = origin + 0x5200
                val beforeScript = origin + 0x3800
                val afterScript = origin + 0x5000
                for (script in listOf(beforeScript, afterScript)) {
                    assertTrue("unused generated-script extent", (script until script + 10).all { fixture.bytes[it] == 0.toByte() })
                    fixture.bytes[script] = 0x16
                    fixture.u16(script + 1, 0x8005)
                    fixture.u16(script + 3, 135)
                    fixture.bytes[script + 5] = 5
                    fixture.pointer(script + 6, helper)
                }
                when (failure) {
                    0 -> fixture.bytes.copyInto(fixture.bytes, helper, fixture.helper, fixture.helper + 7)
                    1 -> {
                        byteArrayOf(0x0F, 0, 0, 0, 0, 0, 9, 5, 0x25, 53, 0, 0x27, 2).copyInto(fixture.bytes, helper)
                        fixture.pointer(helper + 2, origin + 0x5300)
                    }
                    2 -> {
                        byteArrayOf(0x16, 5, 0x80.toByte(), 0, 0, 0x25, 53, 0, 0x27, 2).copyInto(fixture.bytes, helper)
                        fixture.u16(helper + 3, 301)
                    }
                    3 -> byteArrayOf(0x26, 5, 0x80.toByte(), 53, 0, 0x25, 53, 0, 0x27, 2).copyInto(fixture.bytes, helper)
                    4 -> {
                        fixture.bytes[helper] = 5
                        fixture.pointer(helper + 1, helper)
                    }
                    5 -> {
                        fixture.bytes[helper] = 5
                        fixture.pointer(helper + 1, helper + 8)
                        fixture.bytes[helper + 8] = 5
                        fixture.pointer(helper + 9, helper)
                    }
                    6 -> byteArrayOf(0x25, 0xFF.toByte(), 0xFF.toByte(), 0x25, 53, 0, 0x27, 2).copyInto(fixture.bytes, helper)
                    7 -> {
                        fixture.u32(beforeScript + 6, 0)
                        fixture.u32(afterScript + 6, 0x0A000000)
                    }
                    8 -> {
                        fixture.pointer(beforeScript + 6, fixture.bytes.size - 2)
                        fixture.pointer(afterScript + 6, fixture.bytes.size - 2)
                        fixture.bytes[fixture.bytes.size - 2] = 0x25
                    }
                    9 -> byteArrayOf(2, 0x25, 53, 0, 0x27, 2).copyInto(fixture.bytes, helper)
                }
                val actual = fixture.materialize()
                assertEquals("all acquisition rows at shift $origin, unverified script $failure", expected.acquisitionsBySpecies, actual.acquisitionsBySpecies)
                assertEquals("all method evidence at shift $origin, unverified script $failure", expected.evidence, actual.evidence)
            }
        }
    }

    @Test
    fun decodesSeventeenConfirmationSourcesWithoutExpandingTheDirectSourceBound() {
        for (origin in listOf(0, 0x4000, 0x8000)) {
            val fixture = ConfirmationFixture(origin)
            val actual = fixture.f.materialize()
            val tutors = actual.acquisitionsBySpecies.getValue(4).filter { it.method == MoveAcquisitionMethod.TUTOR }
            assertEquals(listOf(7, 301, 10, 11, 12, 13, 14, 15, 16, 17, 19, 21, 23, 25, 27, 29, 31, 33, 35, 37, 39, 41, 43, 45, 47, 49), tutors.map { it.moveId })
            assertEquals(tutors.map { it.moveId }, tutors.map { it.sourceId })
            assertEquals(CapabilityStatus.PARTIAL, actual.evidence.getValue(MoveAcquisitionMethod.TUTOR).status)
        }
    }

    @Test
    fun acceptsRenamedConfirmationVariablesWithoutUsingRetailSlots() {
        for (origin in listOf(0, 0x4000, 0x8000)) {
            val fixture = ConfirmationFixture(origin)
            val expected = fixture.f.materialize()
            assertTrue(expected.acquisitionsBySpecies.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 17 })
            assertTrue(expected.acquisitionsBySpecies.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 19 })
            assertTrue(expected.acquisitionsBySpecies.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 21 })
            assertTrue(expected.acquisitionsBySpecies.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 23 })
            assertTrue(expected.acquisitionsBySpecies.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 25 })
            assertTrue(expected.acquisitionsBySpecies.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 27 })
            assertTrue(expected.acquisitionsBySpecies.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 29 })
            assertTrue(expected.acquisitionsBySpecies.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 31 })
            assertTrue(expected.acquisitionsBySpecies.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 33 })
            assertTrue(expected.acquisitionsBySpecies.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 35 })
            assertTrue(expected.acquisitionsBySpecies.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 37 })
            assertTrue(expected.acquisitionsBySpecies.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 39 })
            assertTrue(expected.acquisitionsBySpecies.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 41 })
            assertTrue(expected.acquisitionsBySpecies.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 43 })
            assertTrue(expected.acquisitionsBySpecies.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 45 })
            assertTrue(expected.acquisitionsBySpecies.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 47 })
            assertTrue(expected.acquisitionsBySpecies.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 49 })
            with(fixture.f) {
                for (site in listOf(fixture.source, fixture.source + 16, fixture.source + 32, fixture.source + 48, fixture.source + 64, fixture.source + 80, fixture.source + 96, fixture.source + 112, fixture.source + 128, fixture.source + 144, fixture.source + 160, fixture.source + 176, fixture.source + 192, fixture.source + 208, fixture.source + 224, fixture.source + 240, fixture.source + 256)) {
                    u16(site + 1, 0x8010)
                    u16(site - 4, 0x8011)
                }
                u32(variables + 0x10 * 4, 0x02002200)
                u32(variables + 0x11 * 4, 0x02002300)
                u32(variables + 0x12 * 4, 0x02002304)
                u32(variables + 0x13 * 4, 0x02002308)
                for (offset in listOf(5, 34, 98)) u16(fixture.helper + offset, 0x8011)
                for (offset in listOf(16, 77)) u16(fixture.helper + offset, 0x8012)
                u16(fixture.helper + 96, 0x8013)
            }
            val actual = fixture.f.materialize()
            assertEquals(expected.acquisitionsBySpecies, actual.acquisitionsBySpecies)
            assertEquals(expected.evidence, actual.evidence)
        }
    }

    @Test
    fun acceptsMoveNameBuffersEndingExactlyAtRamBankBoundaries() {
        for (origin in listOf(0, 0x4000, 0x8000)) {
            val fixture = ConfirmationFixture(origin)
            val expected = fixture.f.materialize().acquisitionsBySpecies
            assertTrue(expected.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 17 })
            assertTrue(expected.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 19 })
            assertTrue(expected.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 21 })
            assertTrue(expected.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 23 })
            assertTrue(expected.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 25 })
            assertTrue(expected.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 27 })
            assertTrue(expected.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 29 })
            assertTrue(expected.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 31 })
            assertTrue(expected.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 33 })
            assertTrue(expected.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 35 })
            assertTrue(expected.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 37 })
            assertTrue(expected.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 39 })
            assertTrue(expected.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 41 })
            assertTrue(expected.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 43 })
            assertTrue(expected.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 45 })
            assertTrue(expected.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 47 })
            assertTrue(expected.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 49 })
            for (output in listOf(0x0203FFF3L, 0x03007FF3L)) {
                fixture.f.u32(fixture.formatter + 24, output)
                assertEquals(expected, fixture.f.materialize().acquisitionsBySpecies)
            }
        }
    }

    @Test
    fun rejectsChangedConfirmationValuesBranchesAndNativeJoins() {
        for (origin in listOf(0, 0x4000, 0x8000)) {
            val accepted = ConfirmationFixture(origin).f.materialize().acquisitionsBySpecies
            assertTrue(accepted.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 17 })
            assertTrue(accepted.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 19 })
            assertTrue(accepted.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 21 })
            assertTrue(accepted.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 23 })
            assertTrue(accepted.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 25 })
            assertTrue(accepted.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 27 })
            assertTrue(accepted.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 29 })
            assertTrue(accepted.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 31 })
            assertTrue(accepted.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 33 })
            assertTrue(accepted.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 35 })
            assertTrue(accepted.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 37 })
            assertTrue(accepted.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 39 })
            assertTrue(accepted.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 41 })
            assertTrue(accepted.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 43 })
            assertTrue(accepted.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 45 })
            assertTrue(accepted.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 47 })
            assertTrue(accepted.getValue(4).any { it.method == MoveAcquisitionMethod.TUTOR && it.moveId == 49 })
            val expected = accepted.mapValues { (_, row) ->
                row.filterNot { it.method == MoveAcquisitionMethod.TUTOR && it.moveId in listOf(17, 19, 21, 23, 25, 27, 29, 31, 33, 35, 37, 39, 41, 43, 45, 47, 49) }
            }
            for (failure in 0..24) {
                val fixture = ConfirmationFixture(origin)
                with(fixture.f) {
                    when (failure) {
                        0 -> bytes[fixture.helper] = 0x16
                        1 -> u16(fixture.helper + 27, 0x8005)
                        2 -> u16(fixture.helper + 96, 0x8005)
                        3 -> u16(fixture.helper + 5, 0x8005)
                        4 -> bytes[fixture.helper + 37] = 1
                        5 -> pointer(fixture.helper + 38, fixture.helper)
                        6 -> bytes[fixture.helper + 72] = 2
                        7 -> u16(fixture.helper + 73, 60)
                        8 -> u16(fixture.formatter + 6, 0x800A)
                        9 -> u32(fixture.formatter + 24, 0x02002200)
                        10 -> u16(fixture.wallet + 8, 0x8000)
                        11 -> u16(fixture.closeOne + 12, 0x8020)
                        12 -> bl(fixture.closeTwo + 8, origin + 0x710)
                        13 -> words(fixture.jumpTarget, 0x6001)
                        14 -> words(fixture.compare + 26, 0x2000)
                        15 -> bytes[fixture.conditions + 13] = 0
                        16 -> words(fixture.specialVar + 38, 0x8031)
                        17 -> bytes[fixture.question + 9] = 5
                        18 -> u32(fixture.buffers + 4, 0x02002200)
                        19 -> words(origin + 0x6020 + 12, 0x8020)
                        20 -> words(origin + 0x60C0 + 4, 0x7000)
                        21 -> words(origin + 0x6180 + 24, 0x6000)
                        22 -> words(fixture.specialVar + 20, 0x46C0)
                        23 -> u32(fixture.formatter + 24, 0x0203FFFF)
                        24 -> {
                            u32(fixture.formatter + 24, 0x03007FFF)
                            u32(fixture.formatter + 28, 0x03000000)
                            u32(workflow + 132, 0x03000000)
                            u32(variables + 5 * 4, 0x03000000)
                        }
                    }
                }
                assertEquals("changed confirmation $failure at $origin", expected, fixture.f.materialize().acquisitionsBySpecies)
            }
        }
    }

    private class ConfirmationFixture(origin: Int) {
        val f = CompiledTutorMoveAcquisitionTest.TutorFixture(origin)
        val source = origin + 0x6805
        val helper = origin + 0x5100
        val formatter = origin + 0x5300
        val wallet = origin + 0x5380
        val closeOne = origin + 0x5400
        val closeTwo = origin + 0x5480
        val jumpTarget = origin + 0x5500
        val compare = origin + 0x5580
        val specialVar = origin + 0x5800
        val conditions = origin + 0x6400
        val question = origin + 0x6210
        val buffers = origin + 0x6500

        init {
            with(f) {
                val helper = this@ConfirmationFixture.helper
                assertTrue("generated source extent precedes dispatch tables", source - 5 + 0x400 <= commandTable)
                assertTrue("unused generated source extent", (source - 5 until source - 5 + 0x400).all { bytes[it] == 0.toByte() })
                for ((index, move) in listOf(7, 301, 9, 10, 11, 12, 13, 14, 15, 16, 18).withIndex()) {
                    bytes.copyInto(bytes, script + index * 16, script, script + 10)
                    u16(script + index * 16 + 3, move)
                }
                bytes[source - 5] = 0x16; u16(source - 4, 0x8008); u16(source - 2, 16)
                bytes[source] = 0x16; u16(source + 1, 0x8005); u16(source + 3, 17)
                bytes[source + 5] = 5; pointer(source + 6, helper)
                for ((index, move) in listOf(19, 21, 23, 25, 27, 29, 31, 33, 35, 37, 39, 41, 43, 45, 47, 49, 51).withIndex()) {
                    bytes.copyInto(bytes, source + 11 + index * 16, source - 5, source + 10)
                    u16(source + 19 + index * 16, move)
                }
                fun command(offset: Int, vararg data: Int) = data.forEachIndexed { i, byte -> bytes[helper + offset + i] = byte.toByte() }
                command(0, 0x25, 60, 0, 0x83, 1, 8, 0x80, 0x0F, 0); pointer(helper + 9, origin + 0x6300)
                command(13, 9, 5, 0x21, 13, 0x80, 0, 0, 6, 1); pointer(helper + 22, origin + 0x6280)
                command(26, 0x26, 1, 0x40, 61, 0, 0x22, 1, 0x40, 8, 0x80, 6, 4); pointer(helper + 38, helper + 56)
                command(42, 0x0F, 0); pointer(helper + 44, origin + 0x6300)
                command(48, 9, 4, 5); pointer(helper + 51, origin + 0x6280); command(55, 2, 0x0F, 0)
                pointer(helper + 58, origin + 0x6300)
                command(62, 9, 4, 0x97, 1, 0x25, 62, 0, 0x25, 63, 0, 0x25, 53, 0, 0x27, 0x21, 13, 0x80, 0, 0, 6, 1)
                pointer(helper + 83, origin + 0x6290); command(87, 0x0F, 0); pointer(helper + 89, origin + 0x6300)
                command(93, 9, 4, 0x19, 4, 0x80, 8, 0x80, 0x25, 64, 0, 0x6C, 2)
                u32(variables + 8 * 4, 0x02002300); u32(variables + 13 * 4, 0x02002304); u32(variables + 4 * 4, 0x02002308)
                for ((special, target) in listOf(60 to formatter, 61 to wallet, 62 to closeOne, 63 to closeTwo, 64 to (origin + 0x700))) pointer(specials + special * 4, target + 1)
                words(formatter, 0xB500)
                literal(formatter + 2, 0, formatter + 24, 0x02003000)
                literal(formatter + 4, 1, formatter + 28, 0x02002200)
                words(formatter + 6, 0x880A, 0x210D, 0x4351)
                literal(formatter + 12, 2, formatter + 32, 0x08000000L + base.names)
                words(formatter + 14, 0x1889); bl(formatter + 16, origin + 0x6600); words(formatter + 20, 0xBC01, 0x4700)
                for (move in listOf(17, 19, 21, 23, 25, 27, 29, 31, 33, 35, 37, 39, 41, 43, 45, 47, 49, 51)) bytes[base.names + move * 13 + 12] = 0xFF.toByte()
                words(origin + 0x6600, 0xB500, 0x1C03, 0xE002, 0x701A, 0x3301, 0x3101, 0x780A, 0x1C10, 0x28FF, 0xD1F8, 0x20FF, 0x7018, 0x1C18, 0xBC02, 0x4708)
                literal(wallet, 0, wallet + 12, 0x03002000); words(wallet + 2, 0x6800)
                literal(wallet + 4, 1, wallet + 16, 0x100); words(wallet + 6, 0x1840, 0x8800, 0x4770)
                for ((index, closer) in listOf(closeOne, closeTwo).withIndex()) {
                    words(closer, 0xB510); literal(closer + 2, 4, closer + 24, 0x02003200L + index)
                    words(closer + 4, 0x7820, 0x2101); bl(closer + 8, origin + 0x700)
                    words(closer + 12, 0x7820); bl(closer + 14, origin + 0x720); words(closer + 18, 0xBC10, 0xBC01, 0x4700)
                }
                fun handler(opcode: Int, target: Int) { pointer(commandTable + opcode * 4, target + 1) }
                val jump = origin + 0x5600
                handler(5, jump); words(jump, 0xB510, 0x1C04); bl(jump + 4, readWord)
                words(jump + 8, 0x1C01, 0x1C20); bl(jump + 12, jumpTarget); words(jump + 16, 0x2000, 0xBC10, 0xBC02, 0x4708)
                words(jumpTarget, 0x6081, 0x4770)
                val standard = origin + 0x5700
                handler(9, standard); words(standard, 0xB500, 0x1C02, 0x6890, 0x7801, 0x3001, 0x6090, 0x0089)
                literal(standard + 14, 0, standard + 40, 0x08000000L + origin + 0x6380)
                words(standard + 16, 0x1809); literal(standard + 18, 0, standard + 44, 0x08000000L + origin + 0x63AC)
                words(standard + 20, 0x4281, 0xD203, 0x6809, 0x1C10); bl(standard + 28, scriptCall); words(standard + 32, 0x2000, 0xBC02, 0x4708)
                pointer(origin + 0x6390, origin + 0x6200); pointer(origin + 0x6394, question)
                intArrayOf(0x67, 0, 0, 0, 0, 0x66, 0x6D, 3).forEachIndexed { i, b -> bytes[origin + 0x6200 + i] = b.toByte() }
                intArrayOf(0x67, 0, 0, 0, 0, 0x66, 0x6E, 20, 8, 3).forEachIndexed { i, b -> bytes[question + i] = b.toByte() }
                handler(0x26, specialVar); words(specialVar, 0xB570, 0x1C05); bl(specialVar + 4, readHalf)
                words(specialVar + 8, 0x0400, 0x0C00); bl(specialVar + 12, variableGetter); words(specialVar + 16, 0x1C06)
                literal(specialVar + 18, 4, specialVar + 48, 0x08000000L + specials)
                words(specialVar + 20, 0x1C28); bl(specialVar + 22, readHalf)
                words(specialVar + 26, 0x0400, 0x0B80, 0x1900, 0x6800); bl(specialVar + 34, origin + 0x4FF0)
                words(specialVar + 38, 0x8030, 0x2000, 0xBC70, 0xBC02, 0x4708)
                val branch = origin + 0x5900
                handler(6, branch); words(branch, 0xB530, 0x1C05, 0x68A8, 0x7804, 0x3001, 0x60A8, 0x1C28)
                bl(branch + 14, readWord); words(branch + 18, 0x1C02); literal(branch + 20, 1, branch + 56, 0x08000000L + conditions)
                words(branch + 22, 0x0060, 0x1900, 0x78AB, 0x18C0, 0x1840, 0x7800, 0x2801, 0xD103, 0x1C28, 0x1C11)
                bl(branch + 42, jumpTarget); words(branch + 46, 0x2000, 0xBC30, 0xBC02, 0x4708)
                bytes[conditions + 4] = 1; bytes[conditions + 13] = 1; bytes[conditions + 14] = 1
                words(compare, 0xB500, 0x0400, 0x0C00, 0x0409, 0x0C09, 0x4288, 0xD201, 0x2000, 0xE004, 0x4288, 0xD001, 0x2002, 0xE000, 0x2001, 0xBC02, 0x4708)
                for ((opcode, at) in listOf(0x21 to (origin + 0x5A00), 0x22 to (origin + 0x5B00))) {
                    handler(opcode, at); words(at, 0xB530, 0x1C04); bl(at + 4, readHalf); words(at + 8, 0x0400, 0x0C00); bl(at + 12, variableGetter)
                    if (opcode == 0x21) {
                        words(at + 16, 0x8805, 0x1C20); bl(at + 20, readHalf); words(at + 24, 0x1C01, 0x0409, 0x0C09, 0x1C28)
                        bl(at + 32, compare); words(at + 36, 0x70A0, 0x2000, 0xBC30, 0xBC02, 0x4708)
                    } else {
                        words(at + 16, 0x1C05, 0x1C20); bl(at + 20, readHalf); words(at + 24, 0x0400, 0x0C00); bl(at + 28, variableGetter)
                        words(at + 32, 0x882A, 0x8801, 0x1C10); bl(at + 38, compare); words(at + 42, 0x70A0, 0x2000, 0xBC30, 0xBC02, 0x4708)
                    }
                }
                val load = origin + 0x5C00
                handler(0x0F, load); words(load, 0xB530, 0x1C04, 0x68A0, 0x7805, 0x3001, 0x60A0, 0x1C20); bl(load + 14, readWord)
                words(load + 18, 0x00AD, 0x3464, 0x1964, 0x6020, 0x2000, 0xBC30, 0xBC02, 0x4708)
                val number = origin + 0x5D00
                val valueGetter = origin + 0x5E00
                handler(0x83, number); words(number, 0xB530, 0x6881, 0x780D, 0x3101, 0x6081); bl(number + 10, readHalf)
                words(number + 14, 0x0400, 0x0C00); bl(number + 18, valueGetter); words(number + 22, 0x1C04, 0x0424, 0x0C24, 0x1C20)
                bl(number + 30, origin + 0x700); words(number + 34, 0x1C03, 0x061B, 0x0E1B); literal(number + 40, 0, number + 64, 0x08000000L + buffers)
                words(number + 42, 0x00AD, 0x182D, 0x6828, 0x1C21, 0x2200); bl(number + 52, origin + 0x700)
                words(number + 56, 0x2000, 0xBC30, 0xBC02, 0x4708); u32(buffers + 4, 0x02003400)
                words(valueGetter, 0xB510, 0x0400, 0x0C04, 0x1C20); bl(valueGetter + 8, variableGetter)
                words(valueGetter + 12, 0x2800, 0xD001, 0x8800, 0xE000, 0x1C20, 0xBC10, 0xBC02, 0x4708)
                val fade = origin + 0x5F00
                handler(0x97, fade); words(fade, 0xB510, 0x1C04, 0x68A1, 0x7808, 0x3101, 0x60A1, 0x2100); bl(fade + 14, origin + 0x700)
                literal(fade + 18, 1, fade + 36, 0x08000001L + origin + 0x700); words(fade + 20, 0x1C20); bl(fade + 22, origin + 0x6000)
                words(fade + 26, 0x2001, 0xBC10, 0xBC02, 0x4708)
                val setup = origin + 0x6000
                val message = origin + 0x6020
                val messagePoll = origin + 0x60C0
                val keyPoll = origin + 0x6100
                words(setup, 0x2202, 0x7042, 0x6041, 0x4770)
                handler(0x67, message); words(message, 0xB510, 0x1C04); bl(message + 4, readWord)
                words(message + 8, 0x2800, 0xD100, 0x6E60); bl(message + 14, origin + 0x700)
                words(message + 18, 0x2000, 0xBC10, 0xBC02, 0x4708)
                for ((opcode, wait, poll) in listOf(Triple(0x66, origin + 0x6040, messagePoll), Triple(0x6D, origin + 0x6060, keyPoll))) {
                    handler(opcode, wait); words(wait, 0xB500); literal(wait + 2, 1, wait + 16, 0x08000001L + poll)
                    bl(wait + 4, setup); words(wait + 8, 0x2001, 0xBC02, 0x4708)
                }
                words(messagePoll, 0xB500); literal(messagePoll + 2, 0, messagePoll + 16, 0x02003800)
                words(messagePoll + 4, 0x7800, 0x2800, 0xD004, 0x2000, 0xE003); words(messagePoll + 20, 0x2001, 0xBC02, 0x4708)
                words(keyPoll, 0xB500); literal(keyPoll + 2, 0, keyPoll + 28, 0x03002000)
                words(keyPoll + 4, 0x8DC1, 0x2001, 0x4008, 0x2800, 0xD108, 0x2002, 0x4008, 0x2800, 0xD104, 0x2000, 0xE003)
                words(keyPoll + 32, 0x2001, 0xBC02, 0x4708)
                val yesNo = origin + 0x6080
                handler(0x6E, yesNo); words(yesNo, 0xB500, 0x6882, 0x7813, 0x3201, 0x6082, 0x7811, 0x3201, 0x6082, 0x1C18)
                bl(yesNo + 18, origin + 0x700); words(yesNo + 22, 0x0600, 0x0E00, 0x2801, 0xD001, 0x2000, 0xE002)
                bl(yesNo + 34, origin + 0x720); words(yesNo + 38, 0x2001, 0xBC02, 0x4708)
                val returning = origin + 0x6140
                val restore = origin + 0x6160
                val pop = origin + 0x6180
                handler(3, returning); words(returning, 0xB500); bl(returning + 2, restore); words(returning + 6, 0x2000, 0xBC02, 0x4708)
                words(restore, 0xB510, 0x1C04); bl(restore + 4, pop); words(restore + 8, 0x60A0, 0xBC10, 0xBC01, 0x4700)
                words(pop, 0xB500, 0x1C02, 0x7810, 0x2800, 0xD008, 0x3801, 0x7010, 0x7811, 0x0089, 0x1C10, 0x300C, 0x1840, 0x6800, 0xE000, 0x2000, 0xBC02, 0x4708)
            }
        }
    }

    @Test
    fun addsTheNextConfirmedSourceAtThreeRelocationsWithoutChangingPriorColumns() {
        val configured = System.getenv("DUALDEX_RETAINED_TUTOR_CONFIRMATION_CONTROLS")
        assumeTrue("optional retained-only relocated controls", configured != null)
        val controls = File(requireNotNull(configured)).readLines().filter(String::isNotBlank).map { it.split('\t') }
        assertEquals(3, controls.size)
        assertTrue(controls.all { it.size == 3 })
        val missing = mutableListOf<Pair<Int, Int>>()
        val shifts = mutableSetOf<Int>()
        for (control in controls) {
            val rows = File(control[0]).readLines().map { it.split('\t') }
            val values = rows.single { it[0] == "layout" }.drop(1).map(String::toInt)
            val next = rows.single { it[0] == "next" }[1].toInt()
            val shift = rows.single { it[0] == "shift" }[1].toInt()
            assertTrue(shifts.add(shift))
            val bytes = ByteArray(32 * 1024 * 1024)
            val occupied = BitSet(bytes.size)
            for (row in rows.filter { it[0] == "bytes" }) {
                val start = row[1].toInt()
                val hex = row[2]
                for (index in 0 until hex.length / 2) {
                    val at = start + index
                    val value = hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
                    if (occupied[at]) assertEquals(bytes[at], value)
                    occupied.set(at)
                    bytes[at] = value
                }
            }
            // Collection stopped at the chooser: the post-teach payment dispatch cell
            // was not retained. Supply only that omitted dependency as generated test
            // scaffolding, never as actual-byte/native authority. Retained bytes stay intact.
            val paymentCell = control[1].toInt()
            val paymentStub = control[2].toInt()
            assertTrue((paymentCell until paymentCell + 4).all { !occupied[it] })
            assertTrue((paymentStub until paymentStub + 2).all { !occupied[it] })
            val paymentPointer = 0x08000001 + paymentStub
            repeat(4) { bytes[paymentCell + it] = (paymentPointer ushr (it * 8)).toByte() }
            bytes[paymentStub] = 0x70
            bytes[paymentStub + 1] = 0x47
            val layout = ResolvedRomLayout(
                EngineFamily.EMERALD, 3, Platform.GBA, values[0], values[1],
                ProfileTables(moveNames = TableLayout(values[2], values[1] - 1, values[3])),
                itemNameAuthority = GbaItemNameAuthority.Available(
                    values[4], values[5], values[6], values[6], null, GbaItemNameProvenance.COMPILED_CONSUMER,
                ),
            )
            val expected = rows.filter { it[0] == "tutor" }.map { row ->
                row[1].toInt() to row[2].split(',').map(String::toInt).toSet()
            }
            assertEquals(27, expected.size)
            var missingAtShift = 0
            val result = MoveAcquisitionMaterializer.materialize(RomImage(bytes), layout)
            for (species in 0 until values[0]) {
                val actual = result.acquisitionsBySpecies[species].orEmpty().filter { it.method == MoveAcquisitionMethod.TUTOR }
                val previous = expected.filter { it.first != next }.mapNotNull { (move, eligible) ->
                    MoveAcquisition(move, MoveAcquisitionMethod.TUTOR, move).takeIf { species in eligible }
                }
                assertEquals("preserved tutor columns at shift $shift, species $species", previous, actual.filter { it.moveId != next })
                val shouldHaveNext = species in expected.single { it.first == next }.second
                val nextLinks = actual.filter { it.moveId == next }
                if (shouldHaveNext && nextLinks.isEmpty()) missingAtShift++
                else assertEquals(
                    "next source at shift $shift, species $species",
                    if (shouldHaveNext) listOf(MoveAcquisition(next, MoveAcquisitionMethod.TUTOR, next)) else emptyList(),
                    nextLinks,
                )
            }
            for (row in rows.filter { it[0] == "machine" }) {
                val actual = result.acquisitionsBySpecies[row[1].toInt()].orEmpty()
                    .filter { it.method == MoveAcquisitionMethod.MACHINE }.map { it.moveId to it.sourceId }
                    .sortedWith(compareBy({ it.first }, { it.second }))
                val pairs = row[2].takeIf(String::isNotEmpty)?.split(',')?.map { pair ->
                    pair.split(':').let { it[0].toInt() to it[1].toInt() }
                }.orEmpty()
                assertEquals("preserved machines at shift $shift, species ${row[1]}", pairs, actual)
            }
            assertEquals(CapabilityStatus.PARTIAL, result.evidence.getValue(MoveAcquisitionMethod.TUTOR).status)
            assertNull(result.evidence.getValue(MoveAcquisitionMethod.TUTOR).expectedRecords)
            missing += shift to missingAtShift
        }
        assertEquals("missing confirmed-source links by relocation: $missing", 0, missing.sumOf { it.second })
    }
}
