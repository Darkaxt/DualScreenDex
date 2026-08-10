package com.darkaxt.dualdex.mapper

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
