package com.darkaxt.dualdex.battle

import com.darkaxt.dualdex.retroarch.NetworkCommandTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

class BattleMemoryCoordinatorTest {
    @Test
    fun discoversThenPollsABoundedWindowWithoutTheMapper() {
        val ewram = ByteArray(0x40000)
        fixture(ewram, 0x143C, opponentPp = 35)
        val updates = mutableListOf<BattleTrackingUpdate>()
        val transport = MemoryTransport(ewram)
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = { context() },
            publisher = updates::add,
            transportFactory = { transport },
            autoStart = false,
        )
        coordinator.updateSession(connected = true, systemId = "game_boy_advance", romIdentity = "rom")

        coordinator.heartbeat()
        coordinator.heartbeat()

        assertEquals(13, updates.last().sample?.opponents?.single()?.speciesId)
        assertTrue(transport.commands.all { it.startsWith("READ_CORE_MEMORY ") })
        val discoveryReads = transport.commands.size

        ewram[0x143C + 0x58 + 0x24] = 34
        coordinator.heartbeat()
        coordinator.heartbeat()

        assertTrue(transport.commands.size - discoveryReads < 10)
        assertEquals(mapOf(13 to mapOf(40 to 1)), updates.last().observations)
        coordinator.close()
    }

    @Test
    fun unsupportedSystemsDoNotReadMemoryOrClearTheCatalogContext() {
        val transport = MemoryTransport(ByteArray(0x40000))
        val updates = mutableListOf<BattleTrackingUpdate>()
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = { context() },
            publisher = updates::add,
            transportFactory = { transport },
            autoStart = false,
        )

        coordinator.updateSession(connected = true, systemId = "game_boy_color", romIdentity = "rom")
        coordinator.heartbeat()

        assertTrue(transport.commands.isEmpty())
        assertTrue(updates.isEmpty())
        coordinator.close()
    }

    private fun context() = BattleCatalogContext(
        romIdentity = "rom",
        catalog = BattleCatalogView(
            species = mapOf(
                252 to BattleSpecies(252, listOf(11), setOf(65)),
                13 to BattleSpecies(13, listOf(6, 3), setOf(19)),
            ),
            moves = mapOf(10 to BattleMove(10, 35), 40 to BattleMove(40, 35)),
            typeIds = setOf(3, 6, 11),
        ),
    )

    private fun fixture(bytes: ByteArray, anchor: Int, opponentPp: Int) {
        bytes[anchor - 0x1C] = 2
        bytes[anchor - 0x10] = 0
        bytes[anchor - 0x0F] = 1
        mon(bytes, anchor, 252, 7, 11, 11, 10, 35, 65)
        mon(bytes, anchor + 0x58, 13, 3, 6, 3, 40, opponentPp, 19)
    }

    private fun mon(bytes: ByteArray, offset: Int, species: Int, level: Int, type1: Int, type2: Int, move: Int, pp: Int, ability: Int) {
        putU16(bytes, offset, species)
        repeat(5) { putU16(bytes, offset + 2 + it * 2, 20) }
        putU16(bytes, offset + 0x0C, move)
        repeat(8) { bytes[offset + 0x18 + it] = 6 }
        bytes[offset + 0x20] = ability.toByte()
        bytes[offset + 0x21] = type1.toByte()
        bytes[offset + 0x22] = type2.toByte()
        bytes[offset + 0x24] = pp.toByte()
        putU16(bytes, offset + 0x28, 15)
        bytes[offset + 0x2A] = level.toByte()
        putU16(bytes, offset + 0x2C, 15)
        bytes[offset + 0x48] = species.toByte()
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private class MemoryTransport(private val memory: ByteArray) : NetworkCommandTransport {
        val commands = mutableListOf<String>()
        private val replies = ArrayDeque<ByteArray>()

        override fun send(payload: ByteArray) {
            val command = payload.toString(Charsets.US_ASCII)
            commands += command
            val parts = command.split(' ')
            val address = parts[1].toLong(16)
            val length = parts[2].toInt()
            val offset = (address - 0x02000000).toInt()
            val encoded = (0 until length).joinToString(" ") { "%02X".format(memory[offset + it].toInt() and 0xFF) }
            replies += "READ_CORE_MEMORY ${parts[1]} $encoded".toByteArray()
        }

        override fun poll(): ByteArray? = replies.pollFirst()
        override fun close() = Unit
    }
}
