package com.darkaxt.dualdex

import com.darkaxt.dualdex.retroarch.NetworkCommandClient
import com.darkaxt.dualdex.retroarch.RetroArchConnection
import com.darkaxt.dualdex.retroarch.RetroArchStatus
import com.darkaxt.dualdex.retroarch.SessionMonitor

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawLiveMemoryQaAssetTest {
    @Test
    fun `packaged Modern Emerald scenarios preserve exact raw geometry and sequence`() {
        val asset = assetFile()
        val bytes = asset.readBytes()
        assertEquals(EXPECTED_ASSET_SHA256, bytes.sha256())
        val text = bytes.toString(Charsets.UTF_8)
        assertFalse(text.contains("sessionId"))
        assertFalse(text.contains("capturedAtEpochMs"))
        assertFalse(text.contains("customLabel"))
        assertTrue(text.contains(EXPECTED_SOURCE_SHA256))
        assertTrue(text.contains(EXPECTED_ROM_SHA256))

        val catalog = RawLiveMemoryScenarioLoader.decode(bytes)
        assertEquals(
            listOf(
                "modern-normal",
                "modern-unreadable",
                "modern-partial",
                "modern-malformed",
                "stale-identity",
            ),
            catalog.scenarios.map(RawLiveMemoryScenario::id),
        )
        val normal = catalog.requireScenario("modern-normal")
        assertEquals(
            listOf(
                "overworld-1",
                "battle-start",
                "move-selected",
                "move-executed",
                "battle-end",
                "overworld-2",
            ),
            normal.frames.map(RawLiveMemoryFrame::id),
        )

        normal.frames.forEach { frame ->
            assertEquals(EWRAM_SIZE, frame.readBytes(EWRAM_BASE, EWRAM_SIZE).size)
            assertEquals(IWRAM_SIZE, frame.readBytes(IWRAM_BASE, IWRAM_SIZE).size)
        }

        val controller = RawLiveMemoryQaController(catalog)
        SessionMonitor(NetworkCommandClient(controller.transportFactory()())).use { monitor ->
            assertEquals(RetroArchConnection.DISCONNECTED, monitor.heartbeat().connection)
            val status = monitor.heartbeat()
            assertEquals(RetroArchConnection.PAUSED, status.connection)
            assertEquals(
                RetroArchStatus.Running(
                    paused = true,
                    systemId = "game_boy_advance",
                    gameBasename = "Modern Emerald (v3.5).gba",
                    crc32 = "8C7DBECA",
                ),
                status.lastStatus,
            )
        }
        controller.close()
    }

    @Test
    fun `packaged frames contain only deterministic QA display identities`() {
        val normal = RawLiveMemoryScenarioLoader.decode(assetFile().readBytes())
            .requireScenario("modern-normal")

        normal.frames.forEach { frame ->
            val ewram = frame.readBytes(EWRAM_BASE, EWRAM_SIZE)
            val iwram = frame.readBytes(IWRAM_BASE, IWRAM_SIZE)
            val save1 = (iwram.pointerAt(SAVE_BLOCK_1_POINTER) - EWRAM_BASE).toInt()
            val save2 = (iwram.pointerAt(SAVE_BLOCK_2_POINTER) - EWRAM_BASE).toInt()
            val storage = (iwram.pointerAt(STORAGE_POINTER) - EWRAM_BASE).toInt()

            assertArrayEquals(qaText("QA", 8), ewram.copyOfRange(save2, save2 + 8))
            assertArrayEquals(byteArrayOf(0x12, 0x34, 0x56, 0x78), ewram.copyOfRange(save2 + 0x0A, save2 + 0x0E))
            assertTrue(ewram.copyOfRange(save2 + 0xB0, save2 + 0x1000).all { it == 0.toByte() })

            assertPokemonNames(ewram, save1 + 0x238, 100, 8, 10, 20, 7, 6)
            assertPokemonNames(ewram, (PLAYER_PARTY - EWRAM_BASE).toInt(), 100, 8, 10, 20, 7, 6)
            assertPokemonNames(ewram, storage + 4, 80, 8, 10, 20, 7, 15 * 30)
            assertPokemonNames(ewram, (BATTLE_MONS - EWRAM_BASE).toInt(), 88, 0x30, 11, 0x3C, 8, 4)

            val boxNames = storage + 4 + 15 * 30 * 80
            repeat(15) { box ->
                assertArrayEquals(
                    qaText("QA BOX", 9),
                    ewram.copyOfRange(boxNames + box * 9, boxNames + (box + 1) * 9),
                )
            }
            listOf(
                (save1 + 0x27CC) until (save1 + 0x3716),
                (save1 + 0x3728) until (save1 + 0x3B24),
                (save1 + 0x3B58) until (save1 + 0x3D5A),
                (save1 + 0x3D70) until (save1 + 0x3D88),
            ).forEach { range ->
                assertTrue(ewram.copyOfRange(range.first, range.last + 1).all { it == 0.toByte() })
            }
        }
    }

    private fun assertPokemonNames(
        memory: ByteArray,
        base: Int,
        recordSize: Int,
        nicknameOffset: Int,
        nicknameSize: Int,
        otOffset: Int,
        otSize: Int,
        count: Int,
    ) {
        repeat(count) { index ->
            val record = base + index * recordSize
            assertArrayEquals(
                qaText("", nicknameSize),
                memory.copyOfRange(record + nicknameOffset, record + nicknameOffset + nicknameSize),
            )
            assertArrayEquals(
                qaText("QA", otSize),
                memory.copyOfRange(record + otOffset, record + otOffset + otSize),
            )
        }
    }

    private fun RawLiveMemoryFrame.readBytes(address: Long, size: Int): ByteArray =
        (read(address, size) as RawLiveMemoryReadResult.Data).bytes

    private fun ByteArray.pointerAt(address: Long): Int {
        val offset = (address - IWRAM_BASE).toInt()
        val pointer = ByteBuffer.wrap(this, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFF_FFFFL
        assertTrue(pointer in EWRAM_BASE until EWRAM_BASE + EWRAM_SIZE)
        return pointer.toInt()
    }

    private fun qaText(value: String, size: Int): ByteArray = ByteArray(size) { index ->
        when {
            index >= value.length -> 0xFF.toByte()
            value[index] == ' ' -> 0
            else -> (0xBB + value[index].code - 'A'.code).toByte()
        }
    }

    private fun assetFile(): File = File("src/debug/assets/retroarch-free-ui-qa/raw-live-memory-scenarios.json")
        .also { assertTrue("packaged QA scenario asset is missing", it.isFile) }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private companion object {
        const val EWRAM_BASE = 0x02000000L
        const val EWRAM_SIZE = 0x40000
        const val IWRAM_BASE = 0x03000000L
        const val IWRAM_SIZE = 0x8000
        const val SAVE_BLOCK_1_POINTER = 0x030036F0L
        const val SAVE_BLOCK_2_POINTER = 0x030036F4L
        const val STORAGE_POINTER = 0x030036F8L
        const val PLAYER_PARTY = 0x0201D9C8
        const val BATTLE_MONS = 0x0200143C
        const val EXPECTED_ASSET_SHA256 = "e7d4337b3a456a26b3c69219983742883aed42626de1f493fe95bf2c97babfd0"
        const val EXPECTED_SOURCE_SHA256 = "40958796e0acd76bac20aef3c484d451685fffa255c45a5eec57df6a0511f5a5"
        const val EXPECTED_ROM_SHA256 = "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895"
    }
}
