package com.darkaxt.dualdex.retroarch

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

class CoreMemoryReaderTest {
    @Test
    fun retriesThePendingReadAndCompletesFragmentedRegionsDespiteALateDuplicate() {
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
        assertEquals(listOf("READ_CORE_MEMORY 2000000 4", "READ_CORE_MEMORY 2000000 4"), sent)

        replies += "READ_CORE_MEMORY 2000000 00 01 02 03".toByteArray()
        replies += "READ_CORE_MEMORY 2000000 00 01 02 03".toByteArray()
        assertEquals(CoreMemoryReadState.Reading(4, 6), reader.heartbeat())
        assertEquals("READ_CORE_MEMORY 2000004 2", sent.last())

        replies += "READ_CORE_MEMORY 2000004 04 05".toByteArray()
        val complete = reader.heartbeat() as CoreMemoryReadState.Complete
        assertArrayEquals(byteArrayOf(0, 1, 2, 3, 4, 5), complete.regions.getValue("ewram"))
        assertTrue(sent.all { it.startsWith("READ_CORE_MEMORY ") })
    }

    @Test
    fun malformedReplyForThePendingAddressFailsWithoutSendingAnotherCommand() {
        val sent = mutableListOf<String>()
        val replies = ArrayDeque<ByteArray>()
        val reader = CoreMemoryReadSession(
            sender = { sent += it.toString(Charsets.US_ASCII) },
            poller = { replies.pollFirst() },
        )
        reader.start(listOf(CoreMemoryRegion("window", 0x02001000, 2)))
        replies += "READ_CORE_MEMORY 2001000 GG 01".toByteArray()

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

    @Test
    fun acceptsMixedWhitespaceAndHexCaseWhileIgnoringOtherAddresses() {
        val replies = ArrayDeque<ByteArray>()
        val reader = CoreMemoryReadSession({}, { replies.pollFirst() }, maximumChunkBytes = 4)
        reader.start(listOf(CoreMemoryRegion("window", 0x0200ABCD, 4)))
        replies += "READ_CORE_MEMORY 200ABCE 00 00 00 00".toByteArray()
        replies += " \tREAD_CORE_MEMORY\t0200aBcD\r\n0a FF bC 01\u0000".toByteArray()

        val complete = reader.heartbeat() as CoreMemoryReadState.Complete

        assertArrayEquals(byteArrayOf(0x0a, 0xff.toByte(), 0xbc.toByte(), 0x01), complete.regions.getValue("window"))
    }

    @Test
    fun rejectsExplicitErrorsInvalidNibblesAndShortPayloads() {
        fun failureFor(reply: String, length: Int = 2): String {
            val replies = ArrayDeque<ByteArray>().apply { add(reply.toByteArray()) }
            val reader = CoreMemoryReadSession({}, { replies.pollFirst() })
            reader.start(listOf(CoreMemoryRegion("window", 0x02001000, length)))
            return (reader.heartbeat() as CoreMemoryReadState.Failed).reason
        }

        assertTrue(failureFor("READ_CORE_MEMORY 2001000 ERROR unavailable").contains("rejected"))
        assertTrue(failureFor("READ_CORE_MEMORY 2001000 0G 01").contains("invalid memory byte"))
        assertTrue(failureFor("READ_CORE_MEMORY 2001000 00").contains("expected 2 bytes"))
    }

    @Test
    fun decodesTheMaximumReplyDirectlyIntoTheDestinationRegion() {
        val payload = ByteArray(1024) { (it and 0xff).toByte() }
        val encoded = buildString {
            append("READ_CORE_MEMORY 2000000")
            payload.forEach { byte -> append(' ').append("%02X".format(byte.toInt() and 0xff)) }
        }.toByteArray(Charsets.US_ASCII)
        val replies = ArrayDeque<ByteArray>().apply { add(encoded) }
        val reader = CoreMemoryReadSession({}, { replies.pollFirst() }, maximumChunkBytes = 1024)

        reader.start(listOf(CoreMemoryRegion("ewram", 0x02000000, 1024)))
        val complete = reader.heartbeat() as CoreMemoryReadState.Complete

        assertArrayEquals(payload, complete.regions.getValue("ewram"))
    }
}
