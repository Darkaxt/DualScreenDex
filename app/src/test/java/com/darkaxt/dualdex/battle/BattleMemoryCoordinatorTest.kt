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

    @Test
    fun readsTheSmallGen1WramRegionAndPublishesYellowBattles() {
        val wram = ByteArray(0x2000)
        gen1Mon(wram, 0x0fe4, 0x66, 5, 21, 0, 0, listOf(0x21, 0x27), listOf(35, 30), 0x98, 0x88)
        gen1Mon(wram, 0x1013, 0x54, 5, 20, 0x17, 0x17, listOf(0x54, 0x2d), listOf(30, 40), 0x91, 0xfb)
        wram[0x1056] = 2
        wram[0x1059] = 0
        wram[0x1162] = 1
        wram[0x0cdc] = 0x54
        val updates = mutableListOf<BattleTrackingUpdate>()
        val transport = MemoryTransport(wram, 0xc000)
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = { gen1Context() },
            publisher = updates::add,
            transportFactory = { transport },
            autoStart = false,
        )

        coordinator.updateSession(connected = true, systemId = "game_boy", romIdentity = "rom")
        coordinator.heartbeat()
        coordinator.heartbeat()

        assertEquals(0x66, updates.last().sample?.opponents?.single()?.speciesId)
        assertTrue(transport.commands.size <= 8)
        val discoveryReads = transport.commands.size

        wram[0x0cf2] = 0x21
        coordinator.heartbeat()
        coordinator.heartbeat()

        assertEquals(1, transport.commands.size - discoveryReads)
        assertEquals(mapOf(0x66 to mapOf(0x21 to 1)), updates.last().observations)

        coordinator.heartbeat()
        coordinator.heartbeat()
        assertTrue(updates.last().observations.isEmpty())

        wram[0x0cf2] = 0
        coordinator.heartbeat()
        coordinator.heartbeat()
        wram[0x0cf2] = 0x21
        coordinator.heartbeat()
        coordinator.heartbeat()

        assertEquals(mapOf(0x66 to mapOf(0x21 to 1)), updates.last().observations)
        coordinator.close()
    }

    private fun context() = BattleCatalogContext(
        romIdentity = "rom",
        generation = 3,
        catalog = BattleCatalogView(
            species = mapOf(
                252 to BattleSpecies(252, listOf(11), setOf(65)),
                13 to BattleSpecies(13, listOf(6, 3), setOf(19)),
            ),
            moves = mapOf(10 to BattleMove(10, 35), 40 to BattleMove(40, 35)),
            typeIds = setOf(3, 6, 11),
        ),
    )

    private fun gen1Context() = BattleCatalogContext(
        romIdentity = "rom",
        generation = 1,
        catalog = BattleCatalogView(
            species = mapOf(
                0x54 to BattleSpecies(0x54, listOf(0x17, 0x17)),
                0x66 to BattleSpecies(0x66, listOf(0, 0)),
            ),
            moves = mapOf(
                0x21 to BattleMove(0x21, 35), 0x27 to BattleMove(0x27, 30),
                0x2d to BattleMove(0x2d, 40), 0x54 to BattleMove(0x54, 30),
            ),
            typeIds = setOf(0, 0x17),
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

    private fun gen1Mon(
        bytes: ByteArray, offset: Int, species: Int, level: Int, hp: Int, type1: Int, type2: Int,
        moves: List<Int>, pp: List<Int>, dv1: Int, dv2: Int,
    ) {
        bytes[offset] = species.toByte()
        bytes[offset + 1] = (hp ushr 8).toByte()
        bytes[offset + 2] = hp.toByte()
        bytes[offset + 5] = type1.toByte()
        bytes[offset + 6] = type2.toByte()
        moves.forEachIndexed { index, move -> bytes[offset + 8 + index] = move.toByte() }
        bytes[offset + 12] = dv1.toByte()
        bytes[offset + 13] = dv2.toByte()
        bytes[offset + 14] = level.toByte()
        bytes[offset + 15] = (hp ushr 8).toByte()
        bytes[offset + 16] = hp.toByte()
        pp.forEachIndexed { index, value -> bytes[offset + 25 + index] = value.toByte() }
    }

    private class MemoryTransport(private val memory: ByteArray, private val baseAddress: Long = 0x02000000) : NetworkCommandTransport {
        val commands = mutableListOf<String>()
        private val replies = ArrayDeque<ByteArray>()

        override fun send(payload: ByteArray) {
            val command = payload.toString(Charsets.US_ASCII)
            commands += command
            val parts = command.split(' ')
            val address = parts[1].toLong(16)
            val length = parts[2].toInt()
            val offset = (address - baseAddress).toInt()
            val encoded = (0 until length).joinToString(" ") { "%02X".format(memory[offset + it].toInt() and 0xFF) }
            replies += "READ_CORE_MEMORY ${parts[1]} $encoded".toByteArray()
        }

        override fun poll(): ByteArray? = replies.pollFirst()
        override fun close() = Unit
    }
}
