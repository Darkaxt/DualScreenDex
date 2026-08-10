package com.darkaxt.dualdex.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RetroArchReadProtocolTest {
    @Test
    fun encodesOnlyTheOfficialReadCoreMemoryGrammar() {
        val command = RetroArchReadProtocol.command(MemoryRead(0x02000000, 4))

        assertEquals("READ_CORE_MEMORY 2000000 4", command)
    }

    @Test
    fun parsesTheAddressAndHexByteReply() {
        val bytes = RetroArchReadProtocol.parse(
            MemoryRead(0x02000000, 4),
            "READ_CORE_MEMORY 2000000 00 7F 80 FF\n",
        )

        assertEquals(listOf(0, 0x7F, 0x80, 0xFF), bytes.map { it.toInt() and 0xFF })
    }

    @Test
    fun rejectsErrorsWrongAddressesAndShortReplies() {
        val request = MemoryRead(0x02000000, 4)

        assertThrows(IllegalArgumentException::class.java) {
            RetroArchReadProtocol.parse(request, "READ_CORE_MEMORY 2000000 ERROR invalid address")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RetroArchReadProtocol.parse(request, "READ_CORE_MEMORY 2000001 00 01 02 03")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RetroArchReadProtocol.parse(request, "READ_CORE_MEMORY 2000000 00 01")
        }
    }

    @Test
    fun chunksLargeDescriptorsBelowTheRetroArchUdpReplyLimit() {
        val reads = RetroArchReadProtocol.chunks(MemoryDescriptor("ewram", "EWRAM", 0x02000000, 1300))

        assertEquals(listOf(512, 512, 276), reads.map { it.length })
        assertEquals(listOf(0x02000000L, 0x02000200L, 0x02000400L), reads.map { it.address })
    }
}
