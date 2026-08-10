package com.darkaxt.dualdex.retroarch

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

class CoreMemoryReaderTest {
    @Test
    fun sendsOnlyOneReadRequestAtATimeAndCompletesFragmentedRegions() {
        val sent = mutableListOf<String>()
        val replies = ArrayDeque<ByteArray>()
        val reader = CoreMemoryReadSession(
            sender = { sent += it.toString(Charsets.US_ASCII) },
            poller = { replies.pollFirst() },
            maximumChunkBytes = 4,
        )

        assertEquals(CoreMemoryReadState.Reading(0, 6), reader.start(listOf(CoreMemoryRegion("ewram", 0x02000000, 6))))
        assertEquals(listOf("READ_CORE_MEMORY 2000000 4"), sent)
        assertEquals(CoreMemoryReadState.Reading(0, 6), reader.heartbeat())
        assertEquals(1, sent.size)

        replies += "READ_CORE_MEMORY 2000000 00 01 02 03".toByteArray()
        assertEquals(CoreMemoryReadState.Reading(4, 6), reader.heartbeat())
        assertEquals("READ_CORE_MEMORY 2000004 2", sent.last())

        replies += "READ_CORE_MEMORY 2000004 04 05".toByteArray()
        val complete = reader.heartbeat() as CoreMemoryReadState.Complete
        assertArrayEquals(byteArrayOf(0, 1, 2, 3, 4, 5), complete.regions.getValue("ewram"))
        assertTrue(sent.all { it.startsWith("READ_CORE_MEMORY ") })
    }

    @Test
    fun malformedRepliesFailWithoutSendingAnotherCommand() {
        val sent = mutableListOf<String>()
        val replies = ArrayDeque<ByteArray>()
        val reader = CoreMemoryReadSession(
            sender = { sent += it.toString(Charsets.US_ASCII) },
            poller = { replies.pollFirst() },
        )
        reader.start(listOf(CoreMemoryRegion("window", 0x02001000, 2)))
        replies += "READ_CORE_MEMORY 2001001 00 01".toByteArray()

        assertTrue(reader.heartbeat() is CoreMemoryReadState.Failed)
        assertEquals(1, sent.size)
    }

    @Test
    fun usesBoundedProductionPackets() {
        val sent = mutableListOf<String>()
        val reader = CoreMemoryReadSession({ sent += it.toString(Charsets.US_ASCII) }, { null })

        reader.start(listOf(CoreMemoryRegion("ewram", 0x02000000, 4096)))

        assertEquals("READ_CORE_MEMORY 2000000 512", sent.single())
    }
}
