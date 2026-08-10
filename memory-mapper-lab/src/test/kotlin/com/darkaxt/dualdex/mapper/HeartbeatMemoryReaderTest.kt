package com.darkaxt.dualdex.mapper

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

class HeartbeatMemoryReaderTest {
    @Test fun progressesOnlyThroughReadCommandsAndCompletesWithoutADeadline() {
        val sent = mutableListOf<String>()
        val replies = ArrayDeque<ByteArray>()
        val reader = HeartbeatMemoryReader(
            sender = RawCommandSender { payload ->
                val command = payload.toString(Charsets.US_ASCII)
                sent += command
                val parts = command.split(' ')
                val address = parts[1]
                val length = parts[2].toInt()
                replies += "READ_CORE_MEMORY $address ${List(length) { index -> "%02X".format(index and 0xff) }.joinToString(" ")}".toByteArray()
            },
            poller = RawCommandPoller { replies.pollFirst() },
            descriptors = listOf(MemoryDescriptor("wram", "Work RAM", 0xC000, 513)),
        )

        assertTrue(reader.start() is HeartbeatReadState.Reading)
        val result = reader.heartbeat() as HeartbeatReadState.Complete

        assertEquals(listOf("READ_CORE_MEMORY c000 512", "READ_CORE_MEMORY c200 1"), sent)
        assertEquals(513, result.regions.getValue("wram").size)
        assertArrayEquals(byteArrayOf(0), result.regions.getValue("wram").copyOfRange(512, 513))
        assertTrue(sent.none { it.contains("WRITE") })
    }

    @Test fun noReplyRemainsPendingInsteadOfTimingOut() {
        val reader = HeartbeatMemoryReader(
            RawCommandSender {}, RawCommandPoller { null },
            listOf(MemoryDescriptor("wram", "Work RAM", 0xC000, 8)),
        )
        reader.start()
        assertEquals(HeartbeatReadState.Reading(0, 8), reader.heartbeat())
    }
}
