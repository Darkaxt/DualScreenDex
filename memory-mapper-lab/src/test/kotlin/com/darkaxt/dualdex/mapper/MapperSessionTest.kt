package com.darkaxt.dualdex.mapper

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MapperSessionTest {
    @Test
    fun disabledLabIssuesNoReadAndRequiresPrivacyAcknowledgement() {
        val transport = RecordingTransport()
        val lab = MemoryMapperLab(transport, listOf(descriptor))

        assertTrue(lab.capture(MapperLabel.OVERWORLD) is CaptureResult.Disabled)
        assertEquals(0, transport.requests.size)
        assertThrows(IllegalStateException::class.java) { lab.enable(privacyAcknowledged = false) }

        lab.enable(privacyAcknowledged = true)
        assertTrue(lab.capture(MapperLabel.OVERWORLD) is CaptureResult.Captured)
        assertEquals(listOf(MemoryRead(0x02000000, 4)), transport.requests)
    }

    @Test
    fun publicSnapshotsCannotMutateRetainedMapperHistory() {
        val lab = MemoryMapperLab(RecordingTransport(), listOf(descriptor))
        lab.enable(privacyAcknowledged = true)

        val captured = lab.capture(MapperLabel.OVERWORLD) as CaptureResult.Captured
        captured.snapshot.regions.single().bytes[0] = 99
        val listed = lab.snapshots().single()
        listed.regions.single().bytes[1] = 88
        val recorded = lab.record().snapshots.single()
        recorded.regions.single().bytes[2] = 77

        assertArrayEquals(byteArrayOf(1, 2, 3, 4), lab.snapshots().single().regions.single().bytes)
        assertEquals(captured.snapshot.regions.single().sha256, lab.snapshots().single().regions.single().sha256)
    }

    @Test
    fun shortReadFailsOnlyTheCaptureAndDoesNotPublishIt() {
        val transport = RecordingTransport(byteArrayOf(1, 2))
        val lab = MemoryMapperLab(transport, listOf(descriptor))
        lab.enable(privacyAcknowledged = true)

        val result = lab.capture(MapperLabel.BATTLE_START)

        assertTrue(result is CaptureResult.Failed)
        assertTrue((result as CaptureResult.Failed).reason.contains("short read"))
        assertEquals(emptyList<MemorySnapshot>(), lab.snapshots())
        assertTrue(lab.enabled)
    }

    @Test
    fun disablingClosesTheSessionWithoutChangingItsExportableHistory() {
        val lab = MemoryMapperLab(RecordingTransport(), listOf(descriptor))
        lab.enable(privacyAcknowledged = true)
        lab.capture(MapperLabel.MOVE_SELECTED)

        lab.disable()

        assertFalse(lab.enabled)
        assertEquals(1, lab.snapshots().size)
    }

    @Test
    fun historyEvictsTheOldestSnapshotAtTheCountBoundary() {
        var nextId = 0
        val lab = MemoryMapperLab(
            RecordingTransport(),
            listOf(descriptor),
            idFactory = { "snapshot-${nextId++}" },
        )
        lab.enable(privacyAcknowledged = true)

        repeat(33) { lab.record(MapperLabel.OVERWORLD, mapOf("ewram" to byteArrayOf(1, 2, 3, 4))) }

        assertEquals(MemoryMapperLab.MAX_SNAPSHOT_COUNT, lab.snapshots().size)
        assertEquals("snapshot-2", lab.snapshots().first().id)
        assertEquals("snapshot-33", lab.snapshots().last().id)
    }

    @Test
    fun historyEvictsTheOldestSnapshotAtTheRawByteBoundary() {
        val regionSize = 1024 * 1024
        val largeDescriptors = listOf(
            MemoryDescriptor("ewram-a", "bounded capture A", 0x02000000, regionSize),
            MemoryDescriptor("ewram-b", "bounded capture B", 0x02100000, regionSize),
        )
        val lab = MemoryMapperLab(RecordingTransport(ByteArray(regionSize)), largeDescriptors)
        lab.enable(privacyAcknowledged = true)

        repeat(9) {
            lab.record(
                MapperLabel.OVERWORLD,
                mapOf("ewram-a" to ByteArray(regionSize), "ewram-b" to ByteArray(regionSize)),
            )
        }

        assertEquals(8, lab.snapshots().size)
        assertEquals(MemoryMapperLab.MAX_HISTORY_BYTES, lab.snapshots().sumOf { snapshot ->
            snapshot.regions.sumOf { it.bytes.size.toLong() }
        })
    }

    private class RecordingTransport(
        private val reply: ByteArray = byteArrayOf(1, 2, 3, 4),
    ) : ReadOnlyMemoryTransport {
        val requests = mutableListOf<MemoryRead>()

        override fun read(request: MemoryRead): ByteArray {
            requests += request
            return reply.copyOf()
        }
    }

    private companion object {
        val descriptor = MemoryDescriptor("ewram", "GBA EWRAM sample", 0x02000000, 4)
    }
}
