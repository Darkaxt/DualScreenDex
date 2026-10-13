package com.enrpau.dualscreendex.parser.validate

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import java.lang.management.ManagementFactory
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class Q412ValidationPerformanceTest {
    private val validator = TableValidators::class.java.getDeclaredMethod(
        "q412SquareTypeChart", RomImage::class.java,
        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
    ).apply { isAccessible = true }
    private val common = setOf(0L, 512L, 819L, 1024L, 1365L, 2048L, 2730L, 4096L,
        6144L, 8192L, 12288L, 16384L, 20480L, 32768L)

    @Test
    fun aMatchesOriginalEvidenceAcrossSizesWidthsRelocationsAndThresholds() {
        for (types in 18..64) for (width in listOf(2, 4)) for (origin in listOf(0, 0x4000, 0x8000)) {
            val size = types * types
            val neutralMinimum = (size * 2 + 4) / 5
            val commonMinimum = (size * 9 + 9) / 10
            val candidates = listOf(
                LongArray(size) { if (it % 4 == 0) 2048 else if (it % 7 == 0) 8192 else 4096 },
                LongArray(size) { 4096 },
                LongArray(size) { if (it < types - 1) if (it % 2 == 0) 2048 else 8192 else 4096 },
                LongArray(size) { if (it < types) if (it % 2 == 0) 2048 else 8192 else 4096 },
                LongArray(size) { if (it % 2 == 0) 4096 else 2048 },
                LongArray(size) { if (it < neutralMinimum) 4096 else if (it % 2 == 0) 2048 else 8192 },
                LongArray(size) { if (it < neutralMinimum - 1) 4096 else if (it % 2 == 0) 2048 else 8192 },
                LongArray(size) { if (it >= commonMinimum) 1234 else if (it < neutralMinimum) 4096 else if (it % 2 == 0) 2048 else 8192 },
                LongArray(size) { if (it >= commonMinimum - 1) 1234 else if (it < neutralMinimum) 4096 else if (it % 2 == 0) 2048 else 8192 },
            )
            for (values in candidates) compare(image(values, width, origin), origin, types, width)
        }
    }

    @Test
    fun bPreservesAllCommonValuesAndUnsignedRejection() {
        for (width in listOf(2, 4)) {
            for (value in common + 65535L) {
                val values = LongArray(18 * 18) { if (it < 18) 2048 else if (it < 36) 8192 else 4096 }
                values[0] = value
                val rom = image(values, width, 0)
                assertTrue(original(rom, 0, 18, width) != null)
                compare(rom, 0, 18, width)
            }
        }
        for (value in listOf(65536L, 0x80000000L, 0xFFFFFFFFL)) for (index in listOf(0, 162, 323)) {
            val values = LongArray(18 * 18) { if (it % 3 == 0) 2048 else if (it % 3 == 1) 8192 else 4096 }
            values[index] = value
            val rom = image(values, 4, 0)
            assertNull(original(rom, 0, 18, 4))
            compare(rom, 0, 18, 4)
        }
    }

    @Test
    fun cPreservesAlignmentExtentAndUnsupportedWidthChecks() {
        for (width in listOf(2, 4)) {
            val rom = image(LongArray(18 * 18) { 4096 }, width, 0)
            for (offset in listOf(-1, 1, rom.size, rom.size - width)) compare(rom, offset, 18, width)
            compare(RomImage(ByteArray(18 * 18 * width - 1)), 0, 18, width)
        }
        for (width in listOf(0, 1, 3, 8)) compare(RomImage(ByteArray(4096)), 0, 18, width)
    }

    @Test
    fun dMatchesOriginalOnSeededMixedMatrices() {
        val random = Random(412)
        repeat(512) {
            val types = random.nextInt(18, 65)
            val width = if (random.nextBoolean()) 2 else 4
            val palette = common.toList()
            val values = LongArray(types * types) {
                when (random.nextInt(10)) {
                    in 0..5 -> 4096
                    in 6..8 -> palette[random.nextInt(palette.size)]
                    else -> random.nextLong(if (width == 2) 65536 else 0x100000000L)
                }
            }
            compare(image(values, width, 0x4000), 0x4000, types, width)
        }
    }

    @Test
    fun zRejectsEarlyInvalidCandidatesWithoutAllocatingBoxedMatrices() {
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        assertTrue(bean.isThreadAllocatedMemorySupported)
        bean.isThreadAllocatedMemoryEnabled = true
        val rom = image(LongArray(18 * 18) { 0xFFFFFFFFL }, 4, 0)
        repeat(512) { assertNull(actual(rom, 0, 18, 4)) }
        val thread = Thread.currentThread().id
        val before = bean.getThreadAllocatedBytes(thread)
        repeat(1024) { assertNull(actual(rom, 0, 18, 4)) }
        val allocated = bean.getThreadAllocatedBytes(thread) - before
        assertTrue("Early rejected candidates allocated $allocated bytes", allocated < 1024 * 1024)
    }

    private fun actual(rom: RomImage, offset: Int, types: Int, width: Int): ValidationEvidence? =
        validator.invoke(TableValidators, rom, offset, types, width) as ValidationEvidence?

    private fun compare(rom: RomImage, offset: Int, types: Int, width: Int) {
        assertEquals("types=$types width=$width offset=$offset", original(rom, offset, types, width), actual(rom, offset, types, width))
    }

    private fun image(values: LongArray, width: Int, origin: Int): RomImage {
        val bytes = ByteArray(origin + values.size * width)
        values.forEachIndexed { index, value ->
            repeat(width) { byte -> bytes[origin + index * width + byte] = (value ushr (byte * 8)).toByte() }
        }
        return RomImage.consume(bytes)
    }

    private fun original(rom: RomImage, offset: Int, types: Int, width: Int): ValidationEvidence? {
        if (width !in setOf(2, 4) || offset % width != 0 || offset !in 0 until rom.size) return null
        val values = types * types
        if (offset.toLong() + values.toLong() * width > rom.size) return null
        val matrix = (0 until values).map { index ->
            if (width == 2) rom.u16le(offset + index * width).toLong() else rom.u32le(offset + index * width)
        }
        if (matrix.any { it > 0xFFFFL }) return null
        val neutral = matrix.count { it == 4096L }
        val accepted = matrix.count { it in common }
        if (neutral * 5 < values * 2 || values - neutral < types || matrix.toSet().size < 3 || accepted * 10 < values * 9) return null
        return ValidationEvidence(
            compatible = true, validRecords = values, totalRecords = values,
            confidence = accepted.toDouble() / values,
            reasons = listOf("validated referenced ${types}x$types u${width * 8} Q4.12 type-effectiveness matrix"),
            offset = offset, recordSize = types * width, elementSize = width,
        )
    }
}
