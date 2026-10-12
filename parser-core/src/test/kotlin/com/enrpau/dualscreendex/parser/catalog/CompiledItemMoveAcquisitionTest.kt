package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationSource
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CompiledItemMoveAcquisitionTest {
    @Test
    fun decodesFirstThreeRowsFromLinkedU16ListsRatherThanBitfields() {
        for (origin in listOf(0, 0x4000, 0x8000)) {
            val f = Fixture(origin)
            val result = f.materialize()
            val first = listOf(machine(7, 1), machine(301, 3))
            assertEquals(first, result.acquisitionsBySpecies[1])
            assertEquals(first, result.acquisitionsBySpecies[2])
            assertEquals(listOf(machine(7, 1), machine(9, 2), machine(301, 3)), result.acquisitionsBySpecies[3])
            assertEquals(CapabilityStatus.AVAILABLE, result.evidence.getValue(MoveAcquisitionMethod.MACHINE).status)
            assertFalse(result.evidence.getValue(MoveAcquisitionMethod.TUTOR).compatible)
        }
    }

    @Test
    fun respectsScalarOverrideAndEmptyRows() {
        val f = Fixture(0)
        val result = f.materialize()
        assertEquals(emptyList<MoveAcquisition>(), result.acquisitionsBySpecies[0].orEmpty())
        // This row is empty, but the compiled override allows every source except move 9.
        assertEquals(listOf(machine(7, 1), machine(301, 3)), result.acquisitionsBySpecies[4])
        assertEquals(listOf(machine(9, 2)), result.acquisitionsBySpecies[5])
    }

    @Test
    fun readsRelocatedStrideAndFieldsWithoutRetailSourceOrdinals() {
        val f = Fixture(0x4000, firstShift = 1, lastShift = 3, selectorField = 9, moveField = 18)
        f.u16(f.items + 3 * f.stride + f.moveField, 0)
        f.bytes[f.items + 7 * f.stride + f.selectorField] = 3
        f.u16(f.items + 7 * f.stride + f.moveField, 301)
        assertEquals(listOf(machine(7, 1), machine(301, 7)), f.materialize().acquisitionsBySpecies[1])
    }

    @Test
    fun keepsIndependentRowsAndDistinctNativeSourcesSeparate() {
        val f = Fixture(0)
        f.row(2, listOf(9))
        f.bytes[f.items + 5 * f.stride + f.selectorField] = 3
        f.u16(f.items + 5 * f.stride + f.moveField, 7)
        val result = f.materialize()
        assertEquals(listOf(machine(7, 1), machine(301, 3), machine(7, 5)), result.acquisitionsBySpecies[1])
        assertEquals(listOf(machine(9, 2)), result.acquisitionsBySpecies[2])
    }

    @Test
    fun acceptsTerminatorAt255ButDeniesCounterWrappingRows() {
        for (length in listOf(255, 256)) {
            val f = Fixture(0)
            val target = 0x3400
            f.pointer(f.pointers + 4, target)
            repeat(length) { f.u16(target + it * 2, 7) }
            f.u16(target + length * 2, 0xFFFF)
            val result = f.materialize()
            if (length == 255) assertEquals(listOf(machine(7, 1)), result.acquisitionsBySpecies[1])
            else {
                assertEquals(null, result.acquisitionsBySpecies[1])
                assertEquals(setOf(1), result.failedSpeciesByMethod[MoveAcquisitionMethod.MACHINE])
                assertEquals(CapabilityStatus.PARTIAL, result.evidence.getValue(MoveAcquisitionMethod.MACHINE).status)
            }
        }
    }

    @Test
    fun rejectsInvalidPointersAndMovesPerRowWithoutDiscardingValidRows() {
        for (pointerFailure in listOf(false, true)) {
            val f = Fixture(0)
            if (pointerFailure) f.pointer(f.pointers + 4, f.bytes.size - 1)
            else f.row(1, listOf(400))
            val result = f.materialize()
            assertEquals(setOf(1), result.failedSpeciesByMethod[MoveAcquisitionMethod.MACHINE])
            assertEquals(listOf(machine(7, 1), machine(301, 3)), result.acquisitionsBySpecies[2])
        }
    }

    @Test
    fun terminalCompiledFailureNeverResurrectsHeuristicBitfields() {
        val control = Fixture(0)
        control.decoyBitfield()
        control.u16(control.wrapper, 0)
        assertEquals(CapabilityStatus.AVAILABLE, control.materialize().evidence.getValue(MoveAcquisitionMethod.MACHINE).status)
        for (failure in 0..4) {
            val f = Fixture(0)
            f.decoyBitfield()
            when (failure) {
                0 -> f.u16(f.kernel + 166, 0x0400) // widen the native counter
                1 -> f.u16(f.kernel + 20, 0x6000) // opaque store in scalar override
                2 -> f.u16(f.items + f.stride + f.moveField, 400) // invalid source move
                3 -> f.u16(f.wrapper + 76, 0xBC04) // malformed frame restoration
                4 -> { // swapped RAM destination and ROM table operand roles
                    f.pointer(f.sourceCaller + 128, f.names)
                    f.u32(f.sourceCaller + 132, 0x02001800)
                }
            }
            val result = f.materialize()
            assertFalse(result.evidence.getValue(MoveAcquisitionMethod.MACHINE).compatible)
            assertFalse(result.evidence.getValue(MoveAcquisitionMethod.TUTOR).compatible)
            assertEquals(emptyMap<Int, List<MoveAcquisition>>(), result.acquisitionsBySpecies)
        }
    }

    @Test
    fun rejectsCompetingCompleteLinkedRootsInsteadOfUnioningThem() {
        val f = Fixture(0)
        val wrapper = 0x800
        val kernel = 0x900
        val caller = 0xA00
        f.bytes.copyInto(f.bytes, wrapper, f.wrapper, f.wrapper + 80)
        f.bytes.copyInto(f.bytes, kernel, f.kernel, f.kernel + 196)
        f.bytes.copyInto(f.bytes, caller, f.sourceCaller, f.sourceCaller + 136)
        f.bl(wrapper + 12, 0x600); f.bl(wrapper + 28, 0x600)
        f.bl(wrapper + 38, kernel); f.bl(wrapper + 56, 0x620)
        f.pointer(kernel + 188, 0x1500)
        f.bl(caller + 10, f.itemGetter)
        for (relative in listOf(28, 48, 62)) f.bl(caller + relative, 0x700)
        f.bl(caller + 70, wrapper)
        assertFalse(f.materialize().evidence.getValue(MoveAcquisitionMethod.MACHINE).compatible)
    }

    @Test
    fun rejectsAssociationAmplificationBeforeMaterializingRows() {
        val f = Fixture(0)
        f.u32(f.sanitizer + 16, 1023)
        val bytes = f.bytes.copyOf(0x10000)
        repeat(1023) { index ->
            val at = f.items + (index + 1) * f.stride
            bytes[at + f.selectorField] = 3
            bytes[at + f.moveField] = 7
            bytes[at + f.moveField + 1] = 0
        }
        val items = f.layout.itemNameAuthority as GbaItemNameAuthority.Available
        val layout = f.layout.copy(speciesCount = 1024, itemNameAuthority = items.copy(count = 1024))
        val result = CompiledItemMoveAcquisitions(RomImage(bytes), layout,
            com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken.NONE).resolve()
        assertEquals("compiled teaching association budget exhausted", result?.reason)
        assertEquals(null, result?.data)
    }

    @Test
    fun unrelatedShortPrefixesDoNotExhaustLinkedCallerBudget() {
        val f = Fixture(0)
        repeat(100) { index -> f.words(0x2800 + index * 8, 0xB570, 0x0600, 0x0E06) }
        assertEquals(listOf(machine(7, 1), machine(301, 3)), f.materialize().acquisitionsBySpecies[1])
    }

    @Test(expected = ParserCancellationException::class)
    fun propagatesCancellation() {
        val f = Fixture(0)
        val source = ParserCancellationSource()
        source.cancel()
        MoveAcquisitionMaterializer.materialize(RomImage(f.bytes), f.layout, source.token)
    }

    private fun machine(move: Int, source: Int) = MoveAcquisition(move, MoveAcquisitionMethod.MACHINE, source)

    internal class Fixture(val origin: Int, val firstShift: Int = 2, val lastShift: Int = 3,
                           val selectorField: Int = 25, val moveField: Int = 34) {
        val bytes = ByteArray(origin + 0x4000)
        val kernel = origin + 0x100
        val wrapper = origin + 0x300
        val sourceCaller = origin + 0x400
        val itemGetter = origin + 0x500
        val selector = origin + 0x540
        val sanitizer = origin + 0x580
        val items = origin + 0x1000
        val pointers = origin + 0x1400
        val names = origin + 0x1800
        val stride = ((1 shl firstShift) + 1) shl lastShift
        val layout = ResolvedRomLayout(EngineFamily.EMERALD, 3, Platform.GBA, 6, 400,
            ProfileTables(moveNames = TableLayout(names, 399, 13)),
            itemNameAuthority = GbaItemNameAuthority.Available(items, stride, 8, 8, null,
                GbaItemNameProvenance.COMPILED_CONSUMER))

        init {
            kernel()
            wrapper()
            getter()
            caller()
            listOf(emptyList(), listOf(7, 301), listOf(7, 301), listOf(7, 9, 301), emptyList(), listOf(9))
                .forEachIndexed { species, moves -> row(species, moves) }
            listOf(7, 9, 301).forEachIndexed { index, move ->
                bytes[items + (index + 1) * stride + selectorField] = 3
                u16(items + (index + 1) * stride + moveField, move)
            }
        }

        fun materialize() = MoveAcquisitionMaterializer.materialize(RomImage(bytes), layout)
        fun decoyBitfield() {
            val moves = origin + 0x2000
            val flags = origin + 0x2100
            repeat(58) { u16(moves + it * 2, it + 1) }
            pointer(origin + 0x20, moves); pointer(origin + 0x24, flags)
            repeat(6) { species ->
                repeat(7) { bytes[flags + species * 8 + it] = 0xFF.toByte() }
                bytes[flags + species * 8 + 7] = 3
            }
        }
        fun row(species: Int, moves: List<Int>, terminated: Boolean = true) {
            val target = origin + 0x3000 + species * 0x80
            pointer(pointers + species * 4, target)
            moves.forEachIndexed { i, move -> u16(target + i * 2, move) }
            if (terminated) u16(target + moves.size * 2, 0xFFFF)
        }
        fun u16(at: Int, value: Int) {
            bytes[at] = value.toByte(); bytes[at + 1] = (value ushr 8).toByte()
        }
        fun u32(at: Int, value: Long) { repeat(4) { bytes[at + it] = (value ushr (8 * it)).toByte() } }
        fun pointer(at: Int, target: Int) = u32(at, 0x08000000L + target)
        fun words(at: Int, vararg ops: Int) = ops.forEachIndexed { i, op -> u16(at + 2 * i, op) }
        fun literal(at: Int, register: Int, pool: Int, value: Long) {
            u16(at, 0x4800 or (register shl 8) or ((pool - ((at + 4) and -4)) / 4))
            u32(pool, value)
        }
        fun branch(at: Int, target: Int, condition: Int? = null) {
            val displacement = (target - at - 4) / 2
            u16(at, if (condition == null) 0xE000 or (displacement and 2047)
                else 0xD000 or (condition shl 8) or (displacement and 255))
        }
        fun bl(at: Int, target: Int) {
            val delta = target - at - 4
            words(at, 0xF000 or ((delta ushr 12) and 2047), 0xF800 or ((delta ushr 1) and 2047))
        }

        private fun kernel() {
            words(kernel, 0xB510, 0x0400, 0x0C03, 0x0409, 0x0C0A)
            literal(kernel + 10, 0, kernel + 60, 6)
            u16(kernel + 12, 0x4283); branch(kernel + 14, kernel + 180, 0)
            u16(kernel + 16, 0x2B04); branch(kernel + 18, kernel + 136, 1)
            words(kernel + 20, 0x2009, 0x4282)
            branch(kernel + 24, kernel + 180, 0); branch(kernel + 26, kernel + 132)
            u16(kernel + 132, 0x2001); branch(kernel + 134, kernel + 182)
            u16(kernel + 136, 0x2400)
            literal(kernel + 138, 0, kernel + 188, 0x08000000L + pointers)
            words(kernel + 140, 0x0099, 0x1809, 0x6809, 0x8808)
            literal(kernel + 148, 3, kernel + 192, 65535)
            u16(kernel + 150, 0x4298); branch(kernel + 152, kernel + 180, 0)
            words(kernel + 154, 0x0060, 0x1840, 0x8800, 0x4290)
            branch(kernel + 162, kernel + 132, 0)
            words(kernel + 164, 0x1C60, 0x0600, 0x0E04, 0x0060, 0x1840, 0x8800, 0x4298)
            branch(kernel + 178, kernel + 154, 1)
            words(kernel + 180, 0x2000, 0xBC10, 0xBC02, 0x4708)
        }

        private fun wrapper() {
            val actor = origin + 0x600
            words(wrapper, 0xB570, 0x1C04, 0x0409, 0x0C0D, 0x1C2E, 0x212D)
            bl(wrapper + 12, actor)
            words(wrapper + 16, 0x2800); branch(wrapper + 18, wrapper + 24, 0)
            u16(wrapper + 20, 0x2003); branch(wrapper + 22, wrapper + 74)
            words(wrapper + 24, 0x1C20, 0x2141); bl(wrapper + 28, actor)
            words(wrapper + 32, 0x0400, 0x0C00, 0x1C29); bl(wrapper + 38, kernel)
            words(wrapper + 42, 0x0600, 0x2800); branch(wrapper + 46, wrapper + 52, 1)
            u16(wrapper + 48, 0x2001); branch(wrapper + 50, wrapper + 74)
            words(wrapper + 52, 0x1C20, 0x1C31); bl(wrapper + 56, origin + 0x620)
            words(wrapper + 60, 0x0600, 0x0E00, 0x2801)
            branch(wrapper + 66, wrapper + 72, 0)
            u16(wrapper + 68, 0x2000); branch(wrapper + 70, wrapper + 74)
            words(wrapper + 72, 0x2002, 0xBC70, 0xBC02, 0x4708)
        }

        private fun getter() {
            words(itemGetter, 0xB510, 0x0400, 0x0C04, 0x1C20)
            bl(itemGetter + 8, selector)
            words(itemGetter + 12, 0x0600, 0x0E00, 0x2803)
            branch(itemGetter + 18, itemGetter + 40, 1)
            literal(itemGetter + 20, 1, itemGetter + 36, 0x08000000L + items)
            words(itemGetter + 22, (firstShift shl 6) or 0x20, 0x1900, lastShift shl 6, 0x1840,
                0x8800 or ((moveField / 2) shl 6))
            branch(itemGetter + 32, itemGetter + 42)
            u16(itemGetter + 34, 0)
            words(itemGetter + 40, 0x2000, 0xBC10, 0xBC02, 0x4708)
            words(selector, 0xB510, 0x0400, 0x0C00)
            literal(selector + 6, 4, selector + 32, 0x08000000L + items)
            bl(selector + 8, sanitizer)
            words(selector + 12, 0x0400, 0x0C00, (firstShift shl 6) or 1, 0x1809,
                (lastShift shl 6) or 9, 0x1909, 0x7808 or (selectorField shl 6), 0xBC10, 0xBC02, 0x4708)
            words(sanitizer, 0xB500, 0x0400, 0x0C01)
            literal(sanitizer + 6, 0, sanitizer + 16, 7)
            words(sanitizer + 8, 0x4281, 0xD803, 0x1C08, 0xE002)
            words(sanitizer + 20, 0x2000, 0xBC02, 0x4708)
        }

        private fun caller() {
            words(sourceCaller, 0xB570, 0x0600, 0x0E06)
            literal(sourceCaller + 6, 0, sourceCaller + 112, 0x02001000)
            u16(sourceCaller + 8, 0x8800); bl(sourceCaller + 10, itemGetter)
            words(sourceCaller + 14, 0x0400, 0x0C05)
            literal(sourceCaller + 18, 4, sourceCaller + 116, 0x02001200)
            words(sourceCaller + 20, 0x2000, 0x81E5, 0x8220, 0x2005)
            bl(sourceCaller + 28, origin + 0x700)
            words(sourceCaller + 32, 0x2109, 0x5661, 0x2064, 0x4341)
            literal(sourceCaller + 40, 0, sourceCaller + 120, 0x02001400)
            words(sourceCaller + 42, 0x180C)
            literal(sourceCaller + 44, 1, sourceCaller + 124, 0x02001600)
            u16(sourceCaller + 46, 0x1C20); bl(sourceCaller + 48, origin + 0x700)
            literal(sourceCaller + 52, 0, sourceCaller + 128, 0x02001800)
            words(sourceCaller + 54, 0x210D, 0x4369)
            literal(sourceCaller + 58, 2, sourceCaller + 132, 0x08000000L + names)
            u16(sourceCaller + 60, 0x1889); bl(sourceCaller + 62, origin + 0x700)
            words(sourceCaller + 66, 0x1C20, 0x1C29); bl(sourceCaller + 70, wrapper)
            words(sourceCaller + 74, 0x0600, 0x0E00, 0x2801)
        }
    }
}
