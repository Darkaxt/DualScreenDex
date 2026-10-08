package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.model.GbaItemNameAuthority
import com.enrpau.dualscreendex.parser.model.GbaItemPublishedRoute
import org.junit.Assert.*
import org.junit.Test

/** Complete generated ten-field ABI; roots, geometry and payloads are synthetic. */
class Gen3ProjectedItemScalarAbiTest {
    private data class Field(val offset: Int, val bytes: Int, val bits: Int = bytes * 8)

    private fun fields(width: Int): List<Field> {
        val aligned = (width + 9) and -4
        return listOf(Field(width, 2), Field(width + 2, 1), Field(width + 3, 1), Field(aligned, 4),
            Field(aligned + 4, 1), Field(aligned + 5, 1), Field(aligned + 6, 1), Field(aligned + 8, 4),
            Field(aligned + 14, 2, 8), Field(aligned + 16, 1))
    }

    private fun fixture(root: Int = 0x6000, width: Int = 14, first: Int = 2, last: Int = 3,
                        maximum: Int = 798, reorder: Set<Int> = setOf(1, 5, 9)): ItemConsumerFixture {
        val f = ItemConsumerFixture(root = root, nameBytes = width, firstShift = first,
            finalShift = last, simpleWrapper = true)
        f.emit(f.sanitizer, 0xB500, 0x0400, 0x0C01, 0x4802, 0x4281, 0xD803, 0x1C08, 0xE002)
        f.half(f.sanitizer + 16, maximum and 0xFFFF); f.half(f.sanitizer + 18, maximum ushr 16)
        f.emit(f.sanitizer + 20, 0x2000, 0xBC02, 0x4708, 0)
        for (entry in f.scalarEntries) f.bytes.fill(0, entry, entry + f.scalarSpacing)
        f.scalarEntries.clear(); f.rootSites.clear(); f.rootSites += f.nameGetter + 22
        for ((index, field) in fields(width).withIndex()) {
            scalar(f, f.scalarStart + index * f.scalarSpacing, field, index in reorder)
        }
        f.bytes.fill(0xFC.toByte(), f.root, f.root + (maximum + 1) * f.stride)
        return f
    }

    private fun scalar(f: ItemConsumerFixture, entry: Int, field: Field, reordered: Boolean) {
        f.scalarEntries += entry
        f.half(entry, 0xB510)
        val site = entry + if (reordered) 2 else 6
        f.emit(entry + if (reordered) 4 else 2, 0x0400, 0x0C00)
        f.bl(entry + 8, f.sanitizer)
        f.emit(entry + 12, 0x0400, 0x0C00, (f.firstShift shl 6) or 1, 0x1809,
            (f.finalShift shl 6) or 9)
        var cursor = entry + 22
        if (field.bytes == 4) { f.half(cursor, 0x3400 or field.offset); cursor += 2 }
        f.half(cursor, 0x1909); cursor += 2
        val adjusted = field.bytes == 1 && field.offset > 31
        if (adjusted) { f.half(cursor, 0x3100 or field.offset); cursor += 2 }
        val immediate = if (field.bytes == 4 || adjusted) 0 else field.offset / field.bytes
        val load = when (field.bytes) { 1 -> 0x7808; 2 -> 0x8808; else -> 0x6808 }
        f.half(cursor, load or (immediate shl 6)); cursor += 2
        if (field.bits == 8 && field.bytes == 2) { f.emit(cursor, 0x0600, 0x0E00); cursor += 4 }
        f.emit(cursor, 0xBC10, 0xBC02, 0x4708)
        f.literalLoad(site, 4, (cursor + 9) and -4, f.root)
        f.rootSites += site
    }

    @Test(timeout = 60_000)
    fun completeProjectedAbiDerivesRelocatedFieldsAndRootLoadOrder() {
        for ((root, width, first, last, maximum) in listOf(
            listOf(0x6000, 14, 2, 3, 798), listOf(0x4000, 18, 3, 3, 513),
            listOf(0x7000, 10, 1, 4, 127))) {
            for (reordered in listOf(emptySet(), setOf(1, 5, 9), (0..9).toSet())) {
                val f = fixture(root, width, first, last, maximum, reordered)
                val resolver = f.session().itemNameResolver
                val proof = resolver.resolve(f.root)
                assertEquals("complete projected ABI: ${proof.reason}",
                    Gen3CompiledItemNameResolver.Table(root, f.stride, maximum + 1, width, emptySet()), proof.table)
                assertTrue(resolver.original(GbaItemPublishedRoute.NotInvoked) is GbaItemNameAuthority.Available)
            }
        }
    }

    @Test(timeout = 60_000)
    fun everyScalarIsMandatoryAndCannotSilentlyWidenNames() {
        for (index in 0..9) {
            val f = fixture()
            val entry = f.scalarEntries[index]
            f.bytes.fill(0, entry, entry + f.scalarSpacing)
            val proof = f.session().itemNameResolver.resolve(f.root)
            assertNull("missing projected ABI field $index: ${proof.reason}", proof.table)
        }
    }

    @Test(timeout = 60_000)
    fun requiredCallsNormalizationScaleAndReturnsStayComplete() {
        for (reordered in listOf(false, true)) for (index in 0..9) {
            val sample = fixture(reorder = if (reordered) (0..9).toSet() else emptySet())
            val entry = sample.scalarEntries[index]
            val tail = (24..40 step 2).first { offset ->
                val at = entry + offset
                ((sample.bytes[at].toInt() and 255) or ((sample.bytes[at + 1].toInt() and 255) shl 8)) == 0xBC10
            }
            for (offset in listOf(8, 10, 12, 14, 16, 18, 20, tail, tail + 2, tail + 4)) {
                val f = fixture(reorder = if (reordered) (0..9).toSet() else emptySet())
                f.half(f.scalarEntries[index] + offset, 0)
                assertNull("field=$index reordered=$reordered dependency=$offset",
                    f.session().itemNameResolver.resolve(f.root).table)
            }
        }
    }

    @Test(timeout = 60_000)
    fun halfwordProjectionAndPhysicalLoadWidthAreIndependentRequirements() {
        for (offset in listOf(24, 26, 28, 30, 32, 34)) {
            val f = fixture(reorder = emptySet())
            val entry = f.scalarEntries[8]
            f.half(entry + offset, 0)
            assertNull("projected-halfword dependency+$offset", f.session().itemNameResolver.resolve(f.root).table)
        }
        val f = fixture()
        val entry = f.scalarEntries[8]
        f.half(entry + 24, 0x7808 or (17 shl 6)) // byte17, not halfword34
        assertNull("physical read may not shrink to its result width", f.session().itemNameResolver.resolve(f.root).table)
    }

    @Test(timeout = 60_000)
    fun aCompleteUnprojectedOrWrongProjectedFieldDoesNotMatchEitherAbi() {
        val unprojected = fixture()
        val entry = unprojected.scalarEntries[8]
        unprojected.bytes.fill(0, entry, entry + unprojected.scalarSpacing)
        scalar(unprojected, entry, fields(unprojected.nameBytes)[8].copy(bits = 16), false)
        assertNull("ten scalar reads without the complete result projection",
            unprojected.session().itemNameResolver.resolve(unprojected.root).table)
        val wrong = fixture()
        val other = wrong.scalarEntries[0]
        wrong.bytes.fill(0, other, other + wrong.scalarSpacing)
        scalar(wrong, other, fields(wrong.nameBytes)[0].copy(bits = 8), false)
        assertNull("projection on the first numeric field", wrong.session().itemNameResolver.resolve(wrong.root).table)
    }

    @Test(timeout = 60_000)
    fun reorderedRootsDoNotWeakenTheClassicThirteenFieldInventory() {
        val f = ItemConsumerFixture(root = 0x6000, nameBytes = 14, firstShift = 3, finalShift = 3,
            simpleWrapper = true)
        for (entry in f.scalarEntries) {
            val slot = ((entry + 10) and -4) + (f.bytes[entry + 6].toInt() and 255) * 4
            f.literalLoad(entry + 2, 4, slot, f.root)
            f.emit(entry + 4, 0x0400, 0x0C00)
        }
        val proof = f.session().itemNameResolver.resolve(f.root)
        assertEquals("complete reordered classic inventory: ${proof.reason}",
            Gen3CompiledItemNameResolver.Table(f.root, f.stride, 377, 14, emptySet()), proof.table)
        f.bytes.fill(0, f.scalarEntries.first(), f.scalarEntries.first() + f.scalarSpacing)
        assertNull("classic fields remain mandatory", f.session().itemNameResolver.resolve(f.root).table)
    }

    @Test(timeout = 60_000)
    fun completeExtraClassicFieldAndFieldRootRemainTerminal() {
        val extra = fixture()
        extra.scalar(0x2400, ((extra.nameBytes + 9) and -4) + 7, 1)
        assertNull("cannot merge scalar ABI subsets", extra.session().itemNameResolver.resolve(extra.root).table)
        val conflict = fixture()
        val originalRoot = conflict.root
        conflict.literalLoad(0x2800, 4, 0x2804, originalRoot + 1)
        assertNull("field-root nomination stays visible", conflict.session().itemNameResolver.resolve(originalRoot).table)
    }
}
