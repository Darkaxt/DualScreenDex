package com.darkaxt.dualdex.mapper

import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotDiffTest {
    @Test
    fun groupsChangedBytesIntoBoundedContiguousRanges() {
        val before = snapshot(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7))
        val after = snapshot(byteArrayOf(0, 9, 8, 3, 4, 7, 6, 0))

        val diff = SnapshotDiff.between(before, after, maximumRanges = 2)

        assertEquals(4, diff.changedBytes)
        assertEquals(2, diff.ranges.size)
        assertEquals(1, diff.ranges[0].offset)
        assertEquals(byteArrayOf(1, 2).toList(), diff.ranges[0].before.toList())
        assertEquals(byteArrayOf(9, 8).toList(), diff.ranges[0].after.toList())
        assertEquals(1, diff.omittedRanges)
    }

    private fun snapshot(bytes: ByteArray) = MemorySnapshot(
        id = bytes.joinToString("") { "%02x".format(it) },
        label = MapperLabel.OVERWORLD,
        customLabel = null,
        capturedAtEpochMs = 1,
        coreIdentity = "mGBA",
        contentIdentity = "rom",
        regions = listOf(
            MemoryRegionSnapshot(
                MemoryDescriptor("ewram", "EWRAM", 0x02000000, bytes.size),
                bytes,
                "hash",
            ),
        ),
    )
}
