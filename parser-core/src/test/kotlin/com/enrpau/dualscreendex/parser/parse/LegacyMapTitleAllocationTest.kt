package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import java.lang.management.ManagementFactory
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LegacyMapTitleAllocationTest {
    private val compact = intArrayOf(
        0xB500, 0xB081, 0x2080, 0x04C0, 0x2100, 0x8001, 0x3010, 0x8001,
        0x3002, 0x8001, 0x3002, 0x8001, 0x3002, 0x8001, 0x3002, 0x8001,
        0x3002, 0x8001, 0x3002, 0x8001, 0x3002, 0x8001,
    )
    private val western = compact.copyOf().apply { this[0] = 0xB510 }
    private val classifier = CompiledGbaLegacyFieldMapTitle::class.java.getDeclaredMethod(
        "layout", CompiledGbaLegacyFieldMapTitle.Reader::class.java, Int::class.javaPrimitiveType,
    ).apply { isAccessible = true }

    @Test
    fun aPreservesBothLayoutsAtAllAlignmentsAndRelocations() {
        for (pattern in listOf(compact, western)) for (base in listOf(0, 0x100, 0x8000)) {
            for (alignment in 0..3) {
                val owner = base + alignment
                val reader = CompiledGbaLegacyFieldMapTitle.Reader(image(pattern, owner))
                assertEquals(if (pattern[0] == 0xB500) "COMPACT" else "WESTERN", actual(reader, owner))
                compare(reader, owner)
            }
        }
    }

    @Test
    fun bPreservesEveryOpcodeCheck() {
        for (pattern in listOf(compact, western)) for (index in pattern.indices) {
            for (replacement in listOf(0, 0xFFFF, pattern[index] xor 1)) for (owner in listOf(0, 1, 0x100, 0x8000)) {
                val mutated = pattern.copyOf().apply { this[index] = replacement }
                val reader = CompiledGbaLegacyFieldMapTitle.Reader(image(mutated, owner))
                assertNull(actual(reader, owner))
                compare(reader, owner)
            }
        }
    }

    @Test
    fun cPreservesRangeChecksForEveryTruncationAndInvalidOffset() {
        for (pattern in listOf(compact, western)) {
            val complete = image(pattern, 7)
            for (length in 0 until pattern.size * 2) {
                compare(CompiledGbaLegacyFieldMapTitle.Reader(RomImage.consume(complete.slice(0, 7 + length))), 7)
            }
            val reader = CompiledGbaLegacyFieldMapTitle.Reader(complete)
            for (owner in listOf(Int.MIN_VALUE, -1, complete.size - 1, complete.size, Int.MAX_VALUE)) compare(reader, owner)
        }
    }

    @Test
    fun dMatchesOriginalOnSeededRandomCandidates() {
        val random = Random(540)
        repeat(1024) { index ->
            val bytes = random.nextBytes(128)
            val owner = random.nextInt(-4, bytes.size + 5)
            if (index % 4 == 0 && owner >= 0 && owner + compact.size * 2 <= bytes.size) {
                val pattern = if (random.nextBoolean()) compact else western
                pattern.forEachIndexed { i, value ->
                    bytes[owner + i * 2] = value.toByte()
                    bytes[owner + i * 2 + 1] = (value ushr 8).toByte()
                }
            }
            compare(CompiledGbaLegacyFieldMapTitle.Reader(RomImage.consume(bytes)), owner)
        }
    }

    @Test
    fun zRejectsCandidatesWithoutConstructingOpcodeArrays() {
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        assertTrue(bean.isThreadAllocatedMemorySupported)
        bean.isThreadAllocatedMemoryEnabled = true
        val reader = CompiledGbaLegacyFieldMapTitle.Reader(RomImage(ByteArray(256)))
        repeat(1024) { assertNull(actual(reader, 0)) }
        val thread = Thread.currentThread().id
        val before = bean.getThreadAllocatedBytes(thread)
        repeat(4096) { assertNull(actual(reader, 0)) }
        val allocated = bean.getThreadAllocatedBytes(thread) - before
        println("Rejected legacy map-title candidates allocated $allocated bytes for4096 calls")
        assertTrue("Rejected candidates allocated $allocated bytes", allocated < 4096 * 64)
    }

    private fun actual(reader: CompiledGbaLegacyFieldMapTitle.Reader, owner: Int): String? =
        (classifier.invoke(CompiledGbaLegacyFieldMapTitle, reader, owner) as? Enum<*>)?.name

    private fun compare(reader: CompiledGbaLegacyFieldMapTitle.Reader, owner: Int) {
        assertEquals("owner=$owner size=${reader.rom.size}", original(reader, owner), actual(reader, owner))
    }

    private fun original(reader: CompiledGbaLegacyFieldMapTitle.Reader, owner: Int): String? = when {
        reader.ops(owner, 0xB500, 0xB081, 0x2080, 0x04C0, 0x2100, 0x8001, 0x3010, 0x8001,
            0x3002, 0x8001, 0x3002, 0x8001, 0x3002, 0x8001, 0x3002, 0x8001,
            0x3002, 0x8001, 0x3002, 0x8001, 0x3002, 0x8001) -> "COMPACT"
        reader.ops(owner, 0xB510, 0xB081, 0x2080, 0x04C0, 0x2100, 0x8001, 0x3010, 0x8001,
            0x3002, 0x8001, 0x3002, 0x8001, 0x3002, 0x8001, 0x3002, 0x8001,
            0x3002, 0x8001, 0x3002, 0x8001, 0x3002, 0x8001) -> "WESTERN"
        else -> null
    }

    private fun image(pattern: IntArray, owner: Int): RomImage {
        val bytes = ByteArray(owner + pattern.size * 2)
        pattern.forEachIndexed { index, value ->
            bytes[owner + index * 2] = value.toByte()
            bytes[owner + index * 2 + 1] = (value ushr 8).toByte()
        }
        return RomImage.consume(bytes)
    }
}
