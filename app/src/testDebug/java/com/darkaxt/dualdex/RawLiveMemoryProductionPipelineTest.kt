package com.darkaxt.dualdex

import com.darkaxt.dualdex.battle.BattleCatalogContext
import com.darkaxt.dualdex.battle.BattleCatalogView
import com.darkaxt.dualdex.battle.BattleMemoryCoordinator
import com.darkaxt.dualdex.battle.Gen3RuntimeMemoryLayout
import com.darkaxt.dualdex.battle.LiveClockState
import com.darkaxt.dualdex.battle.RuntimeMapPosition
import com.darkaxt.dualdex.live.UnifiedGameStateDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawLiveMemoryProductionPipelineTest {
    @Test
    fun `raw frames publish through catalog addresses and the production decoder`() {
        val ewram = ByteArray(0x40000).apply {
            putU16(0x1000, 12)
            putU16(0x1002, 7)
            this[0x1004] = 0
            this[0x1005] = 16
        }
        val iwram = ByteArray(0x8000).apply {
            putU32(0x36F0, 0x02001000)
            this[0x19AD] = 0
            this[0x39E8] = 0
            this[0x39E9] = 0
            this[0x39EA] = 19
            this[0x39EB] = 18
            this[0x39EC] = 48
            putMainState(0x1574)
        }
        val scenario = RawLiveMemoryScenario(
            id = "modern-emerald-pipeline",
            systemId = "game_boy_advance",
            gameBasename = "Modern Emerald.gba",
            crc32 = "8C7DBECA",
            frames = listOf(
                RawLiveMemoryFrame(
                    id = "overworld",
                    regions = listOf(
                        RawLiveMemoryRegion(0x02000000, ewram),
                        RawLiveMemoryRegion(0x03000000, iwram),
                    ),
                ),
            ),
        )
        val simulator = RawLiveMemorySimulator(scenario)
        val transient = UnifiedGameStateDecoder()
        val layout = Gen3RuntimeMemoryLayout(
            mainAddress = 0x03001574,
            inBattleAddress = 0x030019AD,
            inBattleMask = 2,
            saveBlock1MapGroupOffset = 4,
            saveBlock1MapNumberOffset = 5,
            liveClockAddress = 0x030039E8,
        )
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = {
                BattleCatalogContext(
                    romIdentity = "rom-sha",
                    generation = 3,
                    catalog = BattleCatalogView(emptyMap(), emptyMap(), emptySet()),
                    gen3SaveBlock1PointerAddress = 0x030036F0,
                    gen3RuntimeMemoryLayout = layout,
                )
            },
            transientGameState = transient,
            transportFactory = simulator.transportFactory(),
            autoStart = false,
        )

        coordinator.updateSession(
            connected = true,
            systemId = "game_boy_advance",
            romIdentity = "rom-sha",
        )
        repeat(12) { coordinator.heartbeat() }

        assertEquals(0x0010, transient.current?.location?.areaBaseId?.value)
        assertEquals(RuntimeMapPosition(12, 7), transient.current?.location?.position?.value)
        assertEquals(LiveClockState(hours = 19, minutes = 18, seconds = 48), transient.current?.clock?.value)
        assertTrue(transient.current?.battle?.value?.active == false)
        coordinator.close()
        simulator.close()
    }

    private fun ByteArray.putU16(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
    }

    private fun ByteArray.putU32(offset: Int, value: Int) {
        repeat(4) { index -> this[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun ByteArray.putMainState(offset: Int) {
        listOf(0x0816086D, 0x08160D3D, 0x08000301, 0x08000401, 0, 0, 0x08000501)
            .forEachIndexed { index, value -> putU32(offset + index * 4, value) }
        putU32(offset + 0x20, 100)
        putU32(offset + 0x24, 100)
        putU16(offset + 0x32, 40)
    }
}
