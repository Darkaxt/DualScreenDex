package com.darkaxt.dualdex.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class MapperSessionStoreTest {
    @Test
    fun `appends new snapshots and compacts only after bounded history evicts`() {
        val root = File("build/tmp/mapper-store-tests/${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val store = MapperSessionStore(root)
            var snapshots = emptyList<MemorySnapshot>()
            repeat(32) { index ->
                snapshots = snapshots + snapshot("snapshot-${index + 1}")

                val result = store.write(record(snapshots))

                assertFalse(result.compacted)
                assertEquals(1, result.appendedSnapshots)
            }

            snapshots = (snapshots + snapshot("snapshot-33")).takeLast(32)
            val evictionWrite = store.write(record(snapshots))
            val journal = root.listFiles().orEmpty().single { it.name.endsWith(".mapper.jsonl") }
            val lines = journal.readLines()

            assertTrue(evictionWrite.compacted)
            assertEquals(0, evictionWrite.appendedSnapshots)
            assertEquals(32, lines.size)
            assertFalse(lines.any { "snapshot-1\"" in it })
            assertTrue(lines.any { "snapshot-2\"" in it })
            assertTrue(lines.any { "snapshot-33\"" in it })
        } finally {
            root.deleteRecursively()
        }
    }

    private fun record(snapshots: List<MemorySnapshot>) = MapperSessionRecord(
        id = "bounded-session",
        coreIdentity = "GBA",
        contentIdentity = "ROM",
        descriptors = listOf(DESCRIPTOR),
        snapshots = snapshots,
    )

    private fun snapshot(id: String) = MemorySnapshot(
        id = id,
        label = MapperLabel.OVERWORLD,
        customLabel = null,
        capturedAtEpochMs = id.substringAfterLast('-').toLong(),
        coreIdentity = "GBA",
        contentIdentity = "ROM",
        regions = listOf(MemoryRegionSnapshot(DESCRIPTOR, byteArrayOf(1, 2, 3, 4), "a".repeat(64))),
    )

    private companion object {
        val DESCRIPTOR = MemoryDescriptor("ewram", "GBA EWRAM sample", 0x02000000, 4)
    }
}
