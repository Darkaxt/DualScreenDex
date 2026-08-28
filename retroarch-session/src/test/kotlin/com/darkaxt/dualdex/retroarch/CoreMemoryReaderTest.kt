package com.darkaxt.dualdex.retroarch

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

class CoreMemoryReaderTest {
    @Test
    fun completionSnapshotsDefensivelyCopyRetainedRegionBuffers() {
        val replies = ArrayDeque<ByteArray>()
        var scratchConstructions = 0
        val regionBuffers = mutableListOf<ByteArray>()
        val reader = CoreMemoryReadSession(
            sender = {},
            poller = { replies.pollFirst() },
            maximumChunkBytes = 4,
            scratchBufferFactory = { size ->
                scratchConstructions += 1
                ByteArray(size)
            },
            regionBufferFactory = { size -> ByteArray(size).also(regionBuffers::add) },
        )
        reader.start(
            listOf(
                CoreMemoryRegion("first", 0x02001000, 6),
                CoreMemoryRegion("second", 0x02001002, 6),
            ),
        )
        replies += "READ_CORE_MEMORY 2001000 00 01 02 03".toByteArray()
        replies += "READ_CORE_MEMORY 2001004 04 05 06 07".toByteArray()

        val complete = reader.heartbeat() as CoreMemoryReadState.Complete
        assertEquals(1, scratchConstructions)
        assertArrayEquals(byteArrayOf(0, 1, 2, 3, 4, 5), complete.regions.getValue("first"))
        assertArrayEquals(byteArrayOf(2, 3, 4, 5, 6, 7), complete.regions.getValue("second"))
        assertNotSame(regionBuffers[0], complete.regions.getValue("first"))
        assertNotSame(regionBuffers[1], complete.regions.getValue("second"))

        complete.regions.getValue("first")[0] = 99
        val repeated = reader.heartbeat() as CoreMemoryReadState.Complete

        assertArrayEquals(byteArrayOf(0, 1, 2, 3, 4, 5), repeated.regions.getValue("first"))
        assertArrayEquals(byteArrayOf(0, 1, 2, 3, 4, 5), regionBuffers[0])
        assertNotSame(complete.regions.getValue("first"), repeated.regions.getValue("first"))
        assertEquals(CoreMemoryReadMetrics(2, 8, 1, 2, 4, 2, 0, 0), reader.metrics())
    }

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
    fun boundsIrrelevantPacketsPerHeartbeatAndEventuallyHandlesTheMatch() {
        val replies = ArrayDeque<ByteArray>()
        repeat(10) { replies += "READ_CORE_MEMORY 2002000 00".toByteArray() }
        replies += "READ_CORE_MEMORY 2001000 2A".toByteArray()
        val reader = CoreMemoryReadSession(
            sender = {},
            poller = { replies.pollFirst() },
            maximumPacketsPerHeartbeat = 3,
        )
        reader.start(listOf(CoreMemoryRegion("window", 0x02001000, 1)))

        repeat(3) {
            assertTrue(reader.heartbeat() is CoreMemoryReadState.Reading)
        }
        val complete = reader.heartbeat() as CoreMemoryReadState.Complete

        assertArrayEquals(byteArrayOf(0x2A), complete.regions.getValue("window"))
        assertEquals(11, reader.metrics().packetsPolled)
        assertEquals(10, reader.metrics().ignoredPackets)
        assertEquals(3, reader.metrics().drainQuotaHits)
    }

    @Test
    fun senderAndPollerExceptionsBecomeBoundedTerminalFailures() {
        val sendFailure = CoreMemoryReadSession(
            sender = { throw IllegalStateException("private send detail") },
            poller = { null },
        ).start(listOf(CoreMemoryRegion("window", 0x02001000, 1))) as CoreMemoryReadState.Failed
        val pollFailureReader = CoreMemoryReadSession(
            sender = {},
            poller = { throw IllegalStateException("private poll detail") },
        )
        pollFailureReader.start(listOf(CoreMemoryRegion("window", 0x02001000, 1)))
        val pollFailure = pollFailureReader.heartbeat() as CoreMemoryReadState.Failed

        assertEquals("RetroArch memory transport failed", sendFailure.reason)
        assertEquals("RetroArch memory transport failed", pollFailure.reason)
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

    @Test
    fun missingRepliesExhaustThePerReadBudget() {
        val sent = mutableListOf<String>()
        val reader = CoreMemoryReadSession(
            sender = { sent += it.toString(Charsets.US_ASCII) },
            poller = { null },
            maximumMissedReplyHeartbeats = 2,
        )
        reader.start(listOf(CoreMemoryRegion("window", 0x02001000, 1)))

        assertTrue(reader.heartbeat() is CoreMemoryReadState.Reading)
        val failed = reader.heartbeat()

        assertTrue(failed is CoreMemoryReadState.Failed)
        assertEquals(2, sent.size)
    }

    @Test
    fun irrelevantRepliesExhaustTheSamePerReadBudget() {
        val sent = mutableListOf<String>()
        val replies = ArrayDeque<ByteArray>()
        val reader = CoreMemoryReadSession(
            sender = { sent += it.toString(Charsets.US_ASCII) },
            poller = { replies.pollFirst() },
            maximumMissedReplyHeartbeats = 2,
        )
        reader.start(listOf(CoreMemoryRegion("window", 0x02001000, 1)))

        replies += "READ_CORE_MEMORY 2005000 00".toByteArray()
        assertTrue(reader.heartbeat() is CoreMemoryReadState.Reading)
        replies += "READ_CORE_MEMORY 2005000 01".toByteArray()
        val failed = reader.heartbeat()

        assertTrue(failed is CoreMemoryReadState.Failed)
        assertEquals(2, sent.size)
    }

    @Test
    fun trickledChunksCannotResetTheWholeReadMissedReplyBudget() {
        val replies = ArrayDeque<ByteArray>()
        val reader = CoreMemoryReadSession(
            sender = {},
            poller = { replies.pollFirst() },
            maximumChunkBytes = 1,
            maximumMissedReplyHeartbeats = 2,
        )
        reader.start(listOf(CoreMemoryRegion("window", 0x02001000, 3)))

        assertTrue(reader.heartbeat() is CoreMemoryReadState.Reading)
        replies += "READ_CORE_MEMORY 2001000 01".toByteArray()
        assertTrue(reader.heartbeat() is CoreMemoryReadState.Reading)

        assertTrue(reader.heartbeat() is CoreMemoryReadState.Failed)
    }

    @Test
    fun successfulTrickleCannotExceedTheWholeReadHeartbeatBudget() {
        val replies = ArrayDeque<ByteArray>()
        val reader = CoreMemoryReadSession(
            sender = {},
            poller = { replies.pollFirst() },
            maximumChunkBytes = 1,
            maximumReadHeartbeats = 2,
        )
        reader.start(listOf(CoreMemoryRegion("window", 0x02001000, 4)))

        replies += "READ_CORE_MEMORY 2001000 01".toByteArray()
        assertTrue(reader.heartbeat() is CoreMemoryReadState.Reading)
        replies += "READ_CORE_MEMORY 2001001 02".toByteArray()
        assertTrue(reader.heartbeat() is CoreMemoryReadState.Reading)
        replies += "READ_CORE_MEMORY 2001002 03".toByteArray()

        assertTrue(reader.heartbeat() is CoreMemoryReadState.Failed)
    }

    @Test
    fun coalescesOverlappingLogicalRegionsAndScattersOnePhysicalRead() {
        val sent = mutableListOf<String>()
        val replies = ArrayDeque<ByteArray>()
        val reader = CoreMemoryReadSession(
            sender = { sent += it.toString(Charsets.US_ASCII) },
            poller = { replies.pollFirst() },
            maximumChunkBytes = 16,
        )

        assertEquals(
            CoreMemoryReadState.Reading(0, 6),
            reader.start(
                listOf(
                    CoreMemoryRegion("first", 0x02001000, 4),
                    CoreMemoryRegion("second", 0x02001002, 4),
                ),
            ),
        )
        assertEquals(listOf("READ_CORE_MEMORY 2001000 6"), sent)

        replies += "READ_CORE_MEMORY 2001000 00 01 02 03 04 05".toByteArray()
        val complete = reader.heartbeat() as CoreMemoryReadState.Complete

        assertArrayEquals(byteArrayOf(0, 1, 2, 3), complete.regions.getValue("first"))
        assertArrayEquals(byteArrayOf(2, 3, 4, 5), complete.regions.getValue("second"))
        assertEquals(1, sent.size)
    }
}
