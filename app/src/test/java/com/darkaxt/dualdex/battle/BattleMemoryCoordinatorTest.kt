package com.darkaxt.dualdex.battle

import com.darkaxt.dualdex.retroarch.NetworkCommandTransport
import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.SaveSpeciesContext
import com.enrpau.dualscreendex.parser.model.EngineFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.ScheduledThreadPoolExecutor

class BattleMemoryCoordinatorTest {
    @Test
    fun ineligibleCoordinatorSchedulesNoIdleHeartbeat() {
        val scheduler = ScheduledThreadPoolExecutor(1)
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = { null },
            transientGameState = com.darkaxt.dualdex.live.UnifiedGameStateDecoder(),
            heartbeatExecutor = scheduler,
        )

        assertEquals(0, scheduler.queue.size)
        coordinator.updateSession(connected = false, systemId = null, romIdentity = null)
        assertEquals(0, scheduler.queue.size)

        coordinator.close()
    }

    @Test
    fun terminalMemoryTransportFailureSuspendsStaleLiveAuthority() {
        val wram = ByteArray(0x2000).apply {
            this[0x135d] = 0x28
            this[0x1360] = 7
            this[0x1361] = 12
        }
        val state = com.darkaxt.dualdex.live.UnifiedGameStateDecoder()
        val transport = MemoryTransport(wram, 0xc000)
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = { gen1Context() },
            transientGameState = state,
            transportFactory = { transport },
            autoStart = false,
        )
        coordinator.updateSession(connected = true, systemId = "game_boy", romIdentity = "rom")
        repeat(2) { coordinator.heartbeat() }
        assertEquals(0x28, state.current?.location?.areaBaseId?.value)

        transport.failPolls = true
        repeat(2) { coordinator.heartbeat() }

        assertNull(state.current)

        transport.failPolls = false
        repeat(2) { coordinator.heartbeat() }
        assertEquals(0x28, state.current?.location?.areaBaseId?.value)

        transport.failSends = true
        coordinator.heartbeat()
        assertNull(state.current)

        transport.failSends = false
        repeat(2) { coordinator.heartbeat() }
        assertEquals(0x28, state.current?.location?.areaBaseId?.value)
        coordinator.close()
    }

    @Test
    fun publishesTheRightPlayerBattlersMoveWithAnIndependentDoubleBattleTarget() {
        val ewram = ByteArray(0x40000)
        val iwram = ByteArray(0x8000)
        val anchor = 0x1000
        ewram[anchor - 0x1C] = 4
        repeat(4) { ewram[anchor - 0x10 + it] = it.toByte() }
        mon(ewram, anchor, 252, 20, 11, 11, 10, 35, 65)
        mon(ewram, anchor + 0x58, 13, 18, 6, 3, 40, 35, 19)
        mon(ewram, anchor + 0xB0, 1, 19, 11, 11, 11, 25, 65)
        mon(ewram, anchor + 0x108, 16, 18, 0, 2, 40, 35, 65)
        ewram[0x3000] = 2
        ewram[0x3012] = 0
        ewram[0x3022] = 0
        iwram[0x2378] = 3
        iwram[0x19AD] = 2
        mainState(iwram, callback1 = 0x0807B025, callback2 = 0x08078E01, counter = 200)
        val updates = mutableListOf<BattleTrackingUpdate>()
        val transport = MemoryTransport(ewram, extraMemory = mapOf(0x03000000L to iwram))
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = {
                context(
                    runtimeLayout = gen3RuntimeLayout(
                        battleMonsOffset = anchor,
                        battleUi = Gen3BattleUiMemoryLayout(
                            activeBattlerAddress = 0x02003000,
                            actionCursorAddress = 0x02003010,
                            moveCursorAddress = 0x02003020,
                        ),
                        liveTargetOffset = 0x0E04,
                    ),
                )
            },
            transientGameState = recordingState(updates),
            transportFactory = { transport },
            autoStart = false,
        )
        coordinator.updateSession(connected = true, systemId = "game_boy_advance", romIdentity = "rom")

        repeat(12) { coordinator.heartbeat() }

        val sample = requireNotNull(updates.last().sample)
        assertEquals(2, sample.commandOwnerBattlerIndex)
        assertEquals(11, sample.selectedMoveId)
        assertEquals(1, sample.target.opponentIndex)
        assertTrue(transport.commands.any { it == "READ_CORE_MEMORY 2003000 1" })
        assertTrue(transport.commands.any { it == "READ_CORE_MEMORY 2003020 4" })
        assertTrue(transport.commands.any { it == "READ_CORE_MEMORY 3002378 1" })
        coordinator.close()
    }

    @Test
    fun publishesGen1AndGen2LiveAreasBeforeAnyBattleStarts() {
        assertEquals(LiveAreaMemoryLayout(0x135e, 1, 0x1362, 0x1361), liveAreaMemoryLayout(EngineFamily.RED_BLUE))
        assertEquals(LiveAreaMemoryLayout(0x135d, 1, 0x1361, 0x1360), liveAreaMemoryLayout(EngineFamily.YELLOW))
        assertEquals(LiveAreaMemoryLayout(0x1a00, 2, 0x1a03, 0x1a02), liveAreaMemoryLayout(EngineFamily.GOLD_SILVER))
        assertEquals(LiveAreaMemoryLayout(0x1cb5, 2, 0x1cb8, 0x1cb7), liveAreaMemoryLayout(EngineFamily.CRYSTAL))

        val yellowWram = ByteArray(0x2000).apply {
            this[0x135d] = 0x28
            this[0x1360] = 7
            this[0x1361] = 12
        }
        val yellowState = com.darkaxt.dualdex.live.UnifiedGameStateDecoder()
        val yellow = BattleMemoryCoordinator(
            catalogProvider = { gen1Context() },
            transientGameState = yellowState,
            transportFactory = { MemoryTransport(yellowWram, 0xc000) },
            autoStart = false,
        )
        yellow.updateSession(connected = true, systemId = "game_boy", romIdentity = "rom")
        repeat(2) { yellow.heartbeat() }
        assertEquals(0x28, yellowState.current?.location?.areaBaseId?.value)
        assertEquals(RuntimeMapPosition(12, 7), yellowState.current?.location?.position?.value)

        yellowWram[0x135d] = 0xFF.toByte()
        yellowWram[0x1360] = 0xFF.toByte()
        yellowWram[0x1361] = 0xFF.toByte()
        repeat(2) { yellow.heartbeat() }
        assertNull(yellowState.current?.location?.areaBaseId?.value)
        assertNull(yellowState.current?.location?.position?.value)
        yellow.close()

        val crystalWram = ByteArray(0x2000).apply {
            this[0x1cb5] = 24
            this[0x1cb6] = 3
            this[0x1cb7] = 9
            this[0x1cb8] = 14
        }
        val crystalState = com.darkaxt.dualdex.live.UnifiedGameStateDecoder()
        val crystal = BattleMemoryCoordinator(
            catalogProvider = { gen2Context() },
            transientGameState = crystalState,
            transportFactory = { MemoryTransport(crystalWram, 0xc000) },
            autoStart = false,
        )
        crystal.updateSession(connected = true, systemId = "game_boy_color", romIdentity = "rom")
        repeat(2) { crystal.heartbeat() }
        assertEquals(0x1803, crystalState.current?.location?.areaBaseId?.value)
        assertEquals(RuntimeMapPosition(14, 9), crystalState.current?.location?.position?.value)
        crystal.close()
    }

    @Test
    fun publishesGen2BattleAreaPositionAndLightingThroughOneUnifiedSample() {
        val wram = ByteArray(0x2000).apply {
            gen2Mon(this, 0x062c, 155, 5, 20, 20, 20, listOf(33, 43), listOf(35, 30), 0x51, 0x43)
            gen2Mon(this, 0x1206, 19, 2, 13, 0, 0, listOf(33, 39), listOf(35, 30), 0x58, 0x9a)
            this[0x122d] = 1
            this[0x1230] = 0
            this[0x06e3] = 33
            this[0x1cb5] = 24
            this[0x1cb6] = 3
            this[0x1cb7] = 9
            this[0x1cb8] = 14
            this[0x1841] = 2
        }
        val transient = com.darkaxt.dualdex.live.UnifiedGameStateDecoder()
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = { gen2Context() },
            transientGameState = transient,
            transportFactory = { MemoryTransport(wram, 0xc000) },
            autoStart = false,
        )

        coordinator.updateSession(connected = true, systemId = "game_boy_color", romIdentity = "rom")
        repeat(2) { coordinator.heartbeat() }

        val snapshot = requireNotNull(transient.current)
        assertTrue(snapshot.battle.value?.active == true)
        assertEquals(19, snapshot.battle.value?.sample?.opponents?.single()?.speciesId)
        assertEquals(0x1803, snapshot.location.areaBaseId.value)
        assertEquals(RuntimeMapPosition(14, 9), snapshot.location.position.value)
        assertEquals(com.darkaxt.dualdex.battle.LiveClockPhase.NIGHT, snapshot.clock.value?.phase)
        coordinator.close()
    }

    @Test
    fun configuredHeartbeatRateAppliesOnlyToDiscoveryReads() {
        assertEquals(1L, battleHeartbeatDelayMillis(eligible = true, discovering = true, pollingIntervalMs = 0))
        assertEquals(7L, battleHeartbeatDelayMillis(eligible = true, discovering = true, pollingIntervalMs = 7))
        assertEquals(20L, battleHeartbeatDelayMillis(eligible = true, discovering = true, pollingIntervalMs = 99))
        assertEquals(25L, battleHeartbeatDelayMillis(eligible = true, discovering = false, pollingIntervalMs = 1))
        assertEquals(25L, battleHeartbeatDelayMillis(eligible = true, discovering = false, pollingIntervalMs = 7))
        assertEquals(25L, battleHeartbeatDelayMillis(eligible = false, discovering = true, pollingIntervalMs = 1))
    }

    @Test
    fun leavesBattleWhenMainReturnsToTheObservedOverworldCallbacksEvenIfBattleRecordsRemainValid() {
        val ewram = ByteArray(0x40000)
        val iwram = ByteArray(0x8000)
        mainState(iwram, callback1 = 0x0816086D, callback2 = 0x08160D3D, counter = 100)
        val updates = mutableListOf<BattleTrackingUpdate>()
        val transport = MemoryTransport(ewram, extraMemory = mapOf(0x03000000L to iwram))
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = { context() },
            transientGameState = recordingState(updates),
            transportFactory = { transport },
            autoStart = false,
        )
        coordinator.updateSession(connected = true, systemId = "game_boy_advance", romIdentity = "rom")

        repeat(2) { coordinator.heartbeat() }
        assertTrue(updates.isEmpty())

        fixture(ewram, 0x143C, opponentPp = 35)
        putU32(ewram, 0x03A0, 1 shl 2)
        mainState(iwram, callback1 = 0x0807B025, callback2 = 0x08078E01, counter = 200)
        repeat(2) { coordinator.heartbeat() }
        assertTrue(updates.last().active)

        mainState(iwram, callback1 = 0x0816086D, callback2 = 0x08160D3D, counter = 300)
        repeat(2) { coordinator.heartbeat() }

        assertTrue(updates.last().ended)
        assertTrue(!updates.last().active)
        assertEquals(13, updates.dropLast(1).last().sample?.opponents?.single()?.speciesId)
        assertEquals(2, ewram[0x143C - 0x1C].toInt())
        assertTrue(transport.commands.any { it.startsWith("READ_CORE_MEMORY 300") })
        coordinator.close()
    }

    @Test
    fun ignoresStaleGen3BattleRecordsWhenTheSessionStartsInTheOverworld() {
        val ewram = ByteArray(0x40000)
        val iwram = ByteArray(0x8000)
        fixture(ewram, 0x143C, opponentPp = 35)
        mainState(iwram, callback1 = 0x0816086D, callback2 = 0x08160D3D, counter = 100)
        val updates = mutableListOf<BattleTrackingUpdate>()
        val transport = MemoryTransport(ewram, extraMemory = mapOf(0x03000000L to iwram))
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = { context() },
            transientGameState = recordingState(updates),
            transportFactory = { transport },
            autoStart = false,
        )
        coordinator.updateSession(connected = true, systemId = "game_boy_advance", romIdentity = "rom")

        repeat(2) { coordinator.heartbeat() }
        assertTrue(updates.isEmpty())

        mainState(iwram, callback1 = 0x08170001, callback2 = 0x08171001, counter = 150)
        repeat(2) { coordinator.heartbeat() }
        assertTrue(updates.isEmpty())

        mainState(iwram, callback1 = 0x0816086D, callback2 = 0x08160D3D, counter = 175)
        repeat(2) { coordinator.heartbeat() }
        assertTrue(updates.isEmpty())

        ewram[0x143C + 0x58 + 0x24] = 34
        mainState(iwram, callback1 = 0x0807B025, callback2 = 0x08078E01, counter = 200)
        repeat(2) { coordinator.heartbeat() }
        assertTrue(updates.last().active)
        assertEquals(13, updates.last().sample?.opponents?.single()?.speciesId)

        mainState(iwram, callback1 = 0x0816086D, callback2 = 0x08160D3D, counter = 300)
        repeat(2) { coordinator.heartbeat() }
        assertTrue(updates.last().ended)
        coordinator.close()
    }

    @Test
    fun usesTheValidatedGen3LifecycleByteAndKeepsAFirstBattleWild() {
        val ewram = ByteArray(0x40000)
        val iwram = ByteArray(0x8000)
        fixture(ewram, 0x143C, opponentPp = 35)
        ewram[0x03A0] = 0x14
        mainState(iwram, callback1 = 0x0807B025, callback2 = 0x08078E01, counter = 100)
        iwram[0x1574 + 0x439] = 0x02
        val updates = mutableListOf<BattleTrackingUpdate>()
        val transient = recordingState(updates)
        val transport = MemoryTransport(ewram, extraMemory = mapOf(0x03000000L to iwram))
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = { context(runtimeLayout = gen3RuntimeLayout(liveTargetOffset = 0xE04)) },
            transientGameState = transient,
            transportFactory = { transport },
            autoStart = false,
        )
        coordinator.updateSession(connected = true, systemId = "game_boy_advance", romIdentity = "rom")

        repeat(4) { coordinator.heartbeat() }
        assertTrue(updates.last().active)
        assertEquals(BattleEncounterKind.WILD, updates.last().sample?.encounterKind)
        assertEquals(13, transient.current?.battle?.value?.sample?.opponents?.single()?.speciesId)
        assertEquals(BattleEncounterKind.WILD, transient.current?.battle?.value?.encounterKind)

        iwram[0x1574 + 0x439] = 0
        repeat(2) { coordinator.heartbeat() }
        assertTrue(updates.last().ended)
        assertTrue(!updates.last().active)
        assertTrue(transient.current?.battle?.value?.active == false)
        assertTrue(transport.commands.any { it.startsWith("READ_CORE_MEMORY 30019ad 1") })
        assertTrue(transport.commands.any { it.startsWith("READ_CORE_MEMORY 3002378 1") })
        assertTrue(transport.commands.any { it == "READ_CORE_MEMORY 20003a0 4" })
        coordinator.close()
    }

    @Test
    fun pollsTheRomDecodedLiveAddressWithoutRediscoveringAMainStructureCandidate() {
        val ewram = ByteArray(0x40000)
        val iwram = ByteArray(0x8000)
        fixture(ewram, 0x143C, opponentPp = 35)
        mainState(iwram, callback1 = 0x0807B025, callback2 = 0x08078E01, counter = 100)
        // A second callback-shaped structure makes the old RAM pattern scan ambiguous.
        mainState(iwram, offset = 0x2800, callback1 = 0x0816086D, callback2 = 0x08160D3D, counter = 100)
        iwram[0x19AD] = 0x02
        val updates = mutableListOf<BattleTrackingUpdate>()
        val transport = MemoryTransport(ewram, extraMemory = mapOf(0x03000000L to iwram))
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = { context(runtimeLayout = gen3RuntimeLayout()) },
            transientGameState = recordingState(updates),
            transportFactory = { transport },
            autoStart = false,
        )
        coordinator.updateSession(connected = true, systemId = "game_boy_advance", romIdentity = "rom")

        repeat(4) { coordinator.heartbeat() }

        assertTrue(updates.last().active)
        assertTrue(transport.commands.any { it.startsWith("READ_CORE_MEMORY 30019ad 1") })
        coordinator.close()
    }

    @Test
    fun suppressesStaleBattleRecordsWhenTheValidatedLifecycleSaysOverworld() {
        val ewram = ByteArray(0x40000)
        val iwram = ByteArray(0x8000)
        fixture(ewram, 0x143C, opponentPp = 35)
        mainState(iwram, callback1 = 0x0807B025, callback2 = 0x08078E01, counter = 100)
        iwram[0x1574 + 0x439] = 0
        val updates = mutableListOf<BattleTrackingUpdate>()
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = { context(runtimeLayout = gen3RuntimeLayout()) },
            transientGameState = recordingState(updates),
            transportFactory = { MemoryTransport(ewram, extraMemory = mapOf(0x03000000L to iwram)) },
            autoStart = false,
        )
        coordinator.updateSession(connected = true, systemId = "game_boy_advance", romIdentity = "rom")

        repeat(4) { coordinator.heartbeat() }

        assertTrue(updates.isEmpty())
        coordinator.close()
    }

    @Test
    fun pollsOnlyBoundedRuntimeScalarsOutsideBattleAndDiscoversBattleMemoryOnEntry() {
        val ewram = ByteArray(0x40000)
        val iwram = ByteArray(0x8000)
        mainState(iwram, callback1 = 0x0816086D, callback2 = 0x08160D3D, counter = 100)
        iwram[0x1574 + 0x439] = 0
        val updates = mutableListOf<BattleTrackingUpdate>()
        val transport = MemoryTransport(ewram, extraMemory = mapOf(0x03000000L to iwram))
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = { context(runtimeLayout = gen3RuntimeLayout()) },
            transientGameState = recordingState(updates),
            transportFactory = { transport },
            autoStart = false,
        )
        coordinator.updateSession(connected = true, systemId = "game_boy_advance", romIdentity = "rom")
        repeat(2) { coordinator.heartbeat() }
        val initialDiscoveryReads = transport.commands.size

        repeat(2) { coordinator.heartbeat() }

        assertTrue(transport.commands.size - initialDiscoveryReads < 10)
        assertTrue(transport.commands.drop(initialDiscoveryReads).none { it.startsWith("READ_CORE_MEMORY 2000000 400") })

        fixture(ewram, 0x143C, opponentPp = 35)
        iwram[0x1574 + 0x439] = 0x02
        repeat(4) { coordinator.heartbeat() }

        assertTrue(updates.last().active)
        coordinator.close()
    }

    @Test
    fun usesTheRomProvenBattleWindowImmediatelyOnGen3Entry() {
        val ewram = ByteArray(0x40000)
        val iwram = ByteArray(0x8000)
        fixture(ewram, 0x143C, opponentPp = 35)
        mainState(iwram, callback1 = 0x0807B025, callback2 = 0x08078E01, counter = 100)
        iwram[0x1574 + 0x439] = 0x02
        val updates = mutableListOf<BattleTrackingUpdate>()
        val transport = MemoryTransport(ewram, extraMemory = mapOf(0x03000000L to iwram))
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = { context(runtimeLayout = gen3RuntimeLayout(battleMonsOffset = 0x143C)) },
            transientGameState = recordingState(updates),
            transportFactory = { transport },
            autoStart = false,
        )
        coordinator.updateSession(connected = true, systemId = "game_boy_advance", romIdentity = "rom")

        repeat(4) { coordinator.heartbeat() }

        assertTrue(updates.last().active)
        assertEquals(13, updates.last().sample?.opponents?.single()?.speciesId)
        assertTrue(transport.commands.any { it.startsWith("READ_CORE_MEMORY 2001420 ") })
        assertTrue(transport.commands.none { it.startsWith("READ_CORE_MEMORY 2000000 ") })
        assertTrue(transport.commands.none { it.startsWith("READ_CORE_MEMORY 3000000 ") })
        coordinator.close()
    }

    @Test
    fun learnsTheOverworldAfterASessionStartsDuringAnExistingGen3Battle() {
        val ewram = ByteArray(0x40000)
        val iwram = ByteArray(0x8000)
        fixture(ewram, 0x143C, opponentPp = 35)
        mainState(iwram, callback1 = 0x0807B025, callback2 = 0x08078E01, counter = 100)
        val updates = mutableListOf<BattleTrackingUpdate>()
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = { context() },
            transientGameState = recordingState(updates),
            transportFactory = { MemoryTransport(ewram, extraMemory = mapOf(0x03000000L to iwram)) },
            autoStart = false,
        )
        coordinator.updateSession(connected = true, systemId = "game_boy_advance", romIdentity = "rom")

        repeat(2) { coordinator.heartbeat() }
        assertTrue(updates.isEmpty())

        mainState(iwram, callback1 = 0x0816086D, callback2 = 0x08160D3D, counter = 200)
        repeat(4) { coordinator.heartbeat() }
        assertTrue(updates.isEmpty())

        ewram[0x143C + 0x58 + 0x24] = 34
        mainState(iwram, callback1 = 0x0807B025, callback2 = 0x08078E01, counter = 300)
        repeat(2) { coordinator.heartbeat() }
        assertTrue(updates.last().active)
        coordinator.close()
    }

    @Test
    fun discoversThenPollsABoundedWindowWithoutTheMapper() {
        val ewram = ByteArray(0x40000)
        fixture(ewram, 0x143C, opponentPp = 35)
        val updates = mutableListOf<BattleTrackingUpdate>()
        val transport = MemoryTransport(ewram)
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = { context() },
            transientGameState = recordingState(updates),
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
    fun publishesTheLiveMapOutsideBattleFromTheRomProvenSaveBlockPointer() {
        val ewram = ByteArray(0x40000)
        putU16(ewram, 0x1000, 12)
        putU16(ewram, 0x1002, 7)
        ewram[0x1004] = 0
        ewram[0x1005] = 16
        val pointer = byteArrayOf(0x00, 0x10, 0x00, 0x02)
        val updates = mutableListOf<BattleTrackingUpdate>()
        val transient = recordingState(updates)
        val transport = MemoryTransport(ewram, extraMemory = mapOf(0x030036F0L to pointer))
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = { context(saveBlock1Pointer = 0x030036F0L, runtimeLayout = gen3RuntimeLayout()) },
            transientGameState = transient,
            transportFactory = { transport },
            autoStart = false,
        )
        coordinator.updateSession(connected = true, systemId = "game_boy_advance", romIdentity = "rom")

        repeat(4) { coordinator.heartbeat() }

        assertTrue(updates.isEmpty())
        assertEquals(0x0010, transient.current?.location?.areaBaseId?.value)
        assertEquals(RuntimeMapPosition(12, 7), transient.current?.location?.position?.value)
        assertTrue(transport.commands.any { it.startsWith("READ_CORE_MEMORY 30036f0 4") })
        assertTrue(transport.commands.any { it.startsWith("READ_CORE_MEMORY 2001000 6") })
        coordinator.close()
    }

    @Test
    fun rejectsAnInvalidGen3SaveBlockPointerWithoutPublishingAnArea() {
        val transient = com.darkaxt.dualdex.live.UnifiedGameStateDecoder()
        val invalidPointer = byteArrayOf(0x00, 0x10, 0x00, 0x01)
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = { context(saveBlock1Pointer = 0x030036F0L, runtimeLayout = gen3RuntimeLayout()) },
            transientGameState = transient,
            transportFactory = {
                MemoryTransport(ByteArray(0x40000), extraMemory = mapOf(0x030036F0L to invalidPointer))
            },
            autoStart = false,
        )
        coordinator.updateSession(connected = true, systemId = "game_boy_advance", romIdentity = "rom")

        repeat(2) { coordinator.heartbeat() }

        assertNull(transient.current?.location?.areaBaseId?.value)
        coordinator.close()
    }

    @Test
    fun retainsTheGen3CachedWindowAcrossValidatedNonBattleSamples() {
        val ewram = ByteArray(0x40000)
        fixture(ewram, 0x143C, opponentPp = 35)
        val updates = mutableListOf<BattleTrackingUpdate>()
        val transport = MemoryTransport(ewram)
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = { context() },
            transientGameState = recordingState(updates),
            transportFactory = { transport },
            autoStart = false,
        )
        coordinator.updateSession(connected = true, systemId = "game_boy_advance", romIdentity = "rom")
        repeat(2) { coordinator.heartbeat() }
        val fullDiscoveryCommands = transport.commands.size

        ewram[0x143C - 0x1C] = 0
        repeat(4) { coordinator.heartbeat() }
        assertTrue(updates.last().ended)

        coordinator.heartbeat()
        val nextCommand = transport.commands.last()
        assertTrue(transport.commands.size - fullDiscoveryCommands < 10)
        assertTrue(nextCommand.startsWith("READ_CORE_MEMORY 2001420 "))
        coordinator.close()
    }

    @Test
    fun unsupportedSystemsDoNotReadMemoryOrClearTheCatalogContext() {
        val transport = MemoryTransport(ByteArray(0x40000))
        val updates = mutableListOf<BattleTrackingUpdate>()
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = { context() },
            transientGameState = recordingState(updates),
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
        wram[0x135d] = 0
        val updates = mutableListOf<BattleTrackingUpdate>()
        val transient = recordingState(updates)
        val transport = MemoryTransport(wram, 0xc000)
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = { gen1Context() },
            transientGameState = transient,
            transportFactory = { transport },
            autoStart = false,
        )

        coordinator.updateSession(connected = true, systemId = "game_boy", romIdentity = "rom")
        coordinator.heartbeat()
        coordinator.heartbeat()

        assertEquals(0x66, updates.last().sample?.opponents?.single()?.speciesId)
        assertEquals(0, transient.current?.location?.areaBaseId?.value)
        assertTrue(transport.commands.size <= 8)
        val discoveryReads = transport.commands.size

        wram[0x0cf2] = 0x21
        wram[0x135d] = 0x28
        coordinator.heartbeat()
        coordinator.heartbeat()

        assertEquals(3, transport.commands.size - discoveryReads)
        assertEquals(mapOf(0x66 to mapOf(0x21 to 1)), updates.last().observations)
        assertEquals(0x28, transient.current?.location?.areaBaseId?.value)
        assertTrue(transport.commands.any { it.startsWith("READ_CORE_MEMORY d35d 1") })
        assertTrue(transport.commands.any { it.startsWith("READ_CORE_MEMORY d360 2") })

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

    @Test
    fun readsGen2ThroughTheGameBoyCoreIdentityAndPublishesCrystalBattles() {
        val wram = ByteArray(0x2000)
        gen2Mon(wram, 0x062c, 155, 5, 20, 20, 20, listOf(33, 43), listOf(35, 30), 0x51, 0x43)
        gen2Mon(wram, 0x1206, 19, 2, 13, 0, 0, listOf(33, 39), listOf(35, 30), 0x58, 0x9a)
        wram[0x122d] = 1
        wram[0x1230] = 0
        wram[0x06e3] = 33
        wram[0x1cb5] = 24
        wram[0x1cb6] = 3
        wram[0x1cb7] = 9
        wram[0x1cb8] = 14
        wram[0x1841] = 2
        val updates = mutableListOf<BattleTrackingUpdate>()
        val transient = recordingState(updates)
        val transport = MemoryTransport(wram, 0xc000)
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = { gen2Context() },
            transientGameState = transient,
            transportFactory = { transport },
            autoStart = false,
        )

        coordinator.updateSession(connected = true, systemId = "game_boy", romIdentity = "rom")
        coordinator.heartbeat()
        coordinator.heartbeat()

        assertEquals(19, updates.last().sample?.opponents?.single()?.speciesId)
        assertEquals(33, updates.last().sample?.selectedMoveId)
        assertEquals(0x1803, transient.current?.location?.areaBaseId?.value)
        assertEquals(RuntimeMapPosition(14, 9), transient.current?.location?.position?.value)
        assertEquals(LiveClockPhase.NIGHT, transient.current?.clock?.value?.phase)
        assertTrue(transport.commands.all { it.startsWith("READ_CORE_MEMORY ") })

        wram[0x120e] = 34
        wram[0x071c] = 33
        wram[0x1cb6] = 4
        wram[0x1cb7] = 10
        wram[0x1cb8] = 15
        wram[0x1841] = 3
        repeat(2) { coordinator.heartbeat() }
        assertEquals(mapOf(19 to mapOf(33 to 1)), updates.last().observations)
        assertEquals(0x1804, transient.current?.location?.areaBaseId?.value)
        assertEquals(RuntimeMapPosition(15, 10), transient.current?.location?.position?.value)
        assertEquals(LiveClockPhase.DARK, transient.current?.clock?.value?.phase)
        assertTrue(transport.commands.any { it.startsWith("READ_CORE_MEMORY dcb5 4") })
        assertTrue(transport.commands.any { it.startsWith("READ_CORE_MEMORY d841 1") })

        repeat(2) { coordinator.heartbeat() }
        assertTrue(updates.last().observations.isEmpty())

        wram[0x122d] = 0
        repeat(4) { coordinator.heartbeat() }
        assertTrue(updates.last().ended)
        assertTrue(!updates.last().active)

        wram[0x1841] = 4
        repeat(2) { coordinator.heartbeat() }
        assertNull(transient.current?.clock?.value)
        coordinator.updateSession(false, null, null)
        assertNull(transient.current)
        coordinator.close()
    }

    @Test
    fun keepsGen2BattleActiveAcrossTransientInvalidBankReads() {
        val wram = ByteArray(0x2000)
        gen2Mon(wram, 0x062c, 155, 5, 20, 20, 20, listOf(33, 43), listOf(35, 30), 0x51, 0x43)
        gen2Mon(wram, 0x1206, 19, 2, 13, 0, 0, listOf(33, 39), listOf(35, 30), 0x58, 0x9a)
        wram[0x122d] = 1
        wram[0x1230] = 0
        wram[0x06e3] = 33
        val updates = mutableListOf<BattleTrackingUpdate>()
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = { gen2Context() },
            transientGameState = recordingState(updates),
            transportFactory = { MemoryTransport(wram, 0xc000) },
            autoStart = false,
        )
        coordinator.updateSession(connected = true, systemId = "game_boy_color", romIdentity = "rom")
        repeat(2) { coordinator.heartbeat() }
        assertTrue(updates.last().active)

        wram[0x062c] = 0
        repeat(4) { coordinator.heartbeat() }

        assertTrue(updates.last().active)
        assertTrue(!updates.last().ended)
        coordinator.close()
    }

    @Test
    fun publishesTheChecksumValidatedLivePartyOutsideBattle() {
        val ewram = ByteArray(0x40000)
        val iwram = ByteArray(0x8000)
        ewram[0x1001] = 1
        plainPartyRecord(ewram, 0x1004, species = 252, level = 5)
        mainState(iwram, callback1 = 0x0816086D, callback2 = 0x08160D3D, counter = 100)
        iwram[0x1574 + 0x439] = 0
        val transient = com.darkaxt.dualdex.live.UnifiedGameStateDecoder()
        val transport = MemoryTransport(ewram, extraMemory = mapOf(0x03000000L to iwram))
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = {
                context(
                    runtimeLayout = gen3RuntimeLayout(playerPartyOffset = 0x1004),
                    saveContext = SaveParseContext(
                        "rom",
                        mapOf(252 to SaveSpeciesContext(252, 252, 0)),
                    ),
                )
            },
            transientGameState = transient,
            transportFactory = { transport },
            autoStart = false,
        )
        coordinator.updateSession(connected = true, systemId = "game_boy_advance", romIdentity = "rom")

        repeat(6) { coordinator.heartbeat() }

        assertEquals(listOf(252), transient.current?.party?.value?.map { it.speciesId })
        assertEquals(5, transient.current?.party?.value?.single()?.level)
        assertTrue(transport.commands.any { it.startsWith("READ_CORE_MEMORY 2001001 1") })
        assertTrue(transport.commands.any { it.startsWith("READ_CORE_MEMORY 2001004 600") })
        coordinator.close()
    }

    @Test
    fun publishesTheRomDecodedClockWithoutSaveBlockPointers() {
        val ewram = ByteArray(0x40000)
        val iwram = ByteArray(0x8000)
        mainState(iwram, callback1 = 0x0816086D, callback2 = 0x08160D3D, counter = 100)
        iwram[0x19AD] = 0
        iwram[0x39E8] = 0
        iwram[0x39E9] = 0
        iwram[0x39EA] = 19
        iwram[0x39EB] = 18
        iwram[0x39EC] = 48
        val transient = com.darkaxt.dualdex.live.UnifiedGameStateDecoder()
        val transport = MemoryTransport(ewram, extraMemory = mapOf(0x03000000L to iwram))
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = {
                context(runtimeLayout = gen3RuntimeLayout(liveClockAddress = 0x030039E8))
            },
            transientGameState = transient,
            transportFactory = { transport },
            autoStart = false,
        )
        coordinator.updateSession(connected = true, systemId = "game_boy_advance", romIdentity = "rom")

        repeat(20) { coordinator.heartbeat() }

        assertTrue(transport.commands.any { it == "READ_CORE_MEMORY 30039e8 5" })
        assertEquals(
            LiveClockState(hours = 19, minutes = 18, seconds = 48),
            transient.current?.clock?.value,
        )
        coordinator.close()
    }

    @Test
    fun rereadsSavePointersBeforeEachUnifiedLiveSnapshotAndKeepsIndependentSections() {
        val ewram = ByteArray(0x40000)
        val iwram = ByteArray(0x8000)
        putU32(iwram, 0x36F0, 0x02001000)
        putU32(iwram, 0x36F4, 0x01002000)
        ewram[0x1004] = 3
        ewram[0x1005] = 12
        ewram[0x0200] = 0
        mainState(iwram, callback1 = 0x0816086D, callback2 = 0x08160D3D, counter = 100)
        val transient = com.darkaxt.dualdex.live.UnifiedGameStateDecoder()
        val snapshots = mutableListOf<com.darkaxt.dualdex.live.ResolvedGameSnapshot?>()
        transient.subscribe { update -> snapshots += update.snapshot }
        val transport = MemoryTransport(ewram, extraMemory = mapOf(0x03000000L to iwram))
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = {
                context(
                    runtimeLayout = gen3RuntimeLayout(
                        playerPartyOffset = 0x300,
                        saveBlockPointers = true,
                    ),
                    saveContext = SaveParseContext("rom", mapOf(252 to SaveSpeciesContext(252, 252, 0))),
                )
            },
            transientGameState = transient,
            transportFactory = { transport },
            autoStart = false,
        )
        coordinator.updateSession(connected = true, systemId = "game_boy_advance", romIdentity = "rom")

        repeat(30) { coordinator.heartbeat() }
        assertEquals(0x030C, transient.current?.location?.areaBaseId?.value)
        assertEquals(emptyList<Any>(), transient.current?.party?.value)
        assertEquals(
            com.darkaxt.dualdex.live.ResolvedValueSource.UNAVAILABLE,
            transient.current?.trainer?.identity?.source,
        )
        val firstDataRead = transport.commands.indexOfFirst { it.startsWith("READ_CORE_MEMORY 2001000 ") }
        assertTrue(firstDataRead > 1)
        assertTrue(transport.commands[0].startsWith("READ_CORE_MEMORY 30036f0 8"))

        putU32(iwram, 0x36F0, 0x02001200)
        ewram[0x1204] = 7
        ewram[0x1205] = 9
        val oldCount = snapshots.size
        repeat(30) { coordinator.heartbeat() }

        assertTrue(snapshots.size > oldCount)
        assertEquals(0x0709, transient.current?.location?.areaBaseId?.value)
        assertTrue(transport.commands.count { it.startsWith("READ_CORE_MEMORY 30036f0 8") } >= 2)
        coordinator.close()
    }

    @Test
    fun unifiedSourceReceivesLiveTrainerAndPokedexWithoutSavedTrainer() {
        val ewram = ByteArray(0x40000)
        val iwram = ByteArray(0x8000)
        fixture(ewram, 0x143C, opponentPp = 35)
        mainState(iwram, callback1 = 0x0807B025, callback2 = 0x08078E01, counter = 100)
        iwram[0x19AD] = 0x02
        val block1Offset = 0x1000
        val block2Offset = 0x6000
        val partyOffset = 0x30000
        putU32(iwram, 0x36F0, 0x02000000 + block1Offset)
        putU32(iwram, 0x36F4, 0x02000000 + block2Offset)
        ewram[partyOffset - 3] = 1
        plainPartyRecord(ewram, partyOffset, species = 6, level = 5)
        intArrayOf(0xC7, 0xBB, 0xD3, 0xFF).forEachIndexed { index, value ->
            ewram[block2Offset + index] = value.toByte()
        }
        ewram[block2Offset + 0x08] = 1
        putU32(ewram, block2Offset + 0x0A, 0x1234_5678)
        putU16(ewram, block2Offset + 0x0E, 2)
        ewram[block2Offset + 0x10] = 17
        putU32(ewram, block2Offset + 0xAC, 0x1357_2468)
        putU32(ewram, block1Offset + 0x490, 3_000 xor 0x1357_2468)
        val saveContext = emeraldSaveContext()
        val flagBytes = (saveContext.internalSpeciesCount + 7) / 8
        setFlag(ewram, block2Offset + 0x28, 6)
        setFlag(ewram, block2Offset + 0x28 + flagBytes, 6)
        val layout = gen3RuntimeLayout(
            playerPartyOffset = partyOffset,
            saveBlockPointers = true,
            battleMonsOffset = 0x143C,
        ).copy(
            saveBlock1Size = 0x3D88,
            saveBlock2Size = 0xF2C,
        )
        val updates = mutableListOf<BattleTrackingUpdate>()
        val transient = recordingState(updates)
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = { context(runtimeLayout = layout, saveContext = saveContext) },
            transientGameState = transient,
            transportFactory = { MemoryTransport(ewram, extraMemory = mapOf(0x03000000L to iwram)) },
            autoStart = false,
        )

        coordinator.updateSession(connected = true, systemId = "game_boy_advance", romIdentity = "rom")
        repeat(12) { coordinator.heartbeat() }

        assertEquals(0x5678, transient.current?.trainer?.publicTrainerId?.value)
        assertEquals(3_000L, transient.current?.trainer?.money?.value)
        assertEquals(setOf(6), transient.current?.pokedex?.seenSpeciesIds?.value)
        assertEquals(setOf(6), transient.current?.pokedex?.caughtSpeciesIds?.value)
        assertTrue(updates.last().active)
        assertTrue(transient.current?.battle?.value?.active == true)
        assertEquals(13, transient.current?.battle?.value?.sample?.opponents?.single()?.speciesId)
        assertEquals(BattleEncounterKind.WILD, transient.current?.battle?.value?.encounterKind)
        coordinator.close()
    }

    @Test
    fun unifiedLiveSavePathPublishesLocalAreaAndPositionWithoutNullOverwrite() {
        val ewram = ByteArray(0x40000)
        val iwram = ByteArray(0x8000)
        putU32(iwram, 0x36F0, 0x02001000)
        putU32(iwram, 0x36F4, 0x01002000)
        putU16(ewram, 0x1000, 12)
        putU16(ewram, 0x1002, 7)
        ewram[0x1004] = 0
        ewram[0x1005] = 16
        mainState(iwram, callback1 = 0x0816086D, callback2 = 0x08160D3D, counter = 100)
        val transient = com.darkaxt.dualdex.live.UnifiedGameStateDecoder()
        val transport = MemoryTransport(ewram, extraMemory = mapOf(0x03000000L to iwram))
        val coordinator = BattleMemoryCoordinator(
            catalogProvider = {
                context(
                    saveBlock1Pointer = 0x030036F0L,
                    runtimeLayout = gen3RuntimeLayout(saveBlockPointers = true),
                )
            },
            transientGameState = transient,
            transportFactory = { transport },
            autoStart = false,
        )
        coordinator.updateSession(connected = true, systemId = "game_boy_advance", romIdentity = "rom")

        repeat(8) { coordinator.heartbeat() }

        assertEquals(0x0010, transient.current?.location?.areaBaseId?.value)
        assertEquals(RuntimeMapPosition(12, 7), transient.current?.location?.position?.value)
        coordinator.close()
    }

    private fun recordingState(
        updates: MutableList<BattleTrackingUpdate>,
    ): com.darkaxt.dualdex.live.UnifiedGameStateDecoder =
        com.darkaxt.dualdex.live.UnifiedGameStateDecoder().also { state ->
            state.subscribe { update ->
                update.snapshot?.battleKnowledge?.latestUpdate?.let { latest ->
                    if (updates.lastOrNull() != latest) updates += latest
                }
            }
        }

    private fun context(
        saveBlock1Pointer: Long? = null,
        runtimeLayout: Gen3RuntimeMemoryLayout? = null,
        saveContext: SaveParseContext? = null,
    ) = BattleCatalogContext(
        romIdentity = "rom",
        generation = 3,
        gen3SaveBlock1PointerAddress = saveBlock1Pointer,
        gen3RuntimeMemoryLayout = runtimeLayout,
        saveParseContext = saveContext,
        catalog = BattleCatalogView(
            species = mapOf(
                252 to BattleSpecies(252, listOf(11), setOf(65)),
                13 to BattleSpecies(13, listOf(6, 3), setOf(19)),
                1 to BattleSpecies(1, listOf(11), setOf(65)),
                16 to BattleSpecies(16, listOf(0, 2), setOf(65)),
            ),
            moves = mapOf(10 to BattleMove(10, 35), 11 to BattleMove(11, 25), 40 to BattleMove(40, 35)),
            typeIds = setOf(0, 2, 3, 6, 11),
        ),
    )

    private fun gen3RuntimeLayout(
        liveTargetOffset: Int? = null,
        playerPartyOffset: Int? = null,
        battleMonsOffset: Int? = null,
        saveBlockPointers: Boolean = false,
        liveClockAddress: Long? = null,
        battleUi: Gen3BattleUiMemoryLayout? = null,
    ) = Gen3RuntimeMemoryLayout(
        mainAddress = 0x03001574,
        inBattleAddress = 0x030019AD,
        inBattleMask = 0x02,
        saveBlock1MapGroupOffset = 4,
        saveBlock1MapNumberOffset = 5,
        liveClockAddress = liveClockAddress,
        battleUi = battleUi,
        multiUsePlayerCursorAddress = liveTargetOffset?.let { 0x03001574L + it },
        playerPartyCountAddress = playerPartyOffset?.let { 0x02000000L + it - 3 },
        playerPartyAddress = playerPartyOffset?.let { 0x02000000L + it },
        battleMonsAddress = battleMonsOffset?.let { 0x02000000L + it },
        battleTypeFlagsAddress = 0x020003A0,
        trainerBattleMask = 1 shl 3,
        nonWildBattleMask = 0x8FFF8B72.toInt(),
        saveBlock1PointerAddress = 0x030036F0L.takeIf { saveBlockPointers },
        saveBlock2PointerAddress = 0x030036F4L.takeIf { saveBlockPointers },
        saveBlock1Size = 0x100.takeIf { saveBlockPointers },
        saveBlock2Size = 0x80.takeIf { saveBlockPointers },
    )

    private fun emeraldSaveContext() = SaveParseContext(
        romIdentity = "rom",
        speciesById = (1..386).associateWith { SaveSpeciesContext(it, it, 0) },
        gen3SaveRuntimeAbi = com.darkaxt.dualdex.save.gen3.Gen3SaveRuntimeAbi(
            saveBlock1Size = 0x3D88,
            saveBlock2Size = 0xF2C,
            textEncoding = com.darkaxt.dualdex.save.gen3.Gen3TextEncoding.ENGLISH,
            trainer = com.darkaxt.dualdex.save.gen3.Gen3TrainerCardAbi(
                playerNameOffset = 0,
                playerNameLength = 8,
                genderOffset = 0x08,
                trainerIdOffset = 0x0A,
                playTimeHoursOffset = 0x0E,
                playTimeMinutesOffset = 0x10,
                encryptionKeyOffset = 0xAC,
                moneyOffset = 0x490,
                maximumMoney = 999_999,
                badgeFlags = (0x867..0x86E).map { flag ->
                    com.darkaxt.dualdex.save.gen3.Gen3BitFlag(0x1270 + flag / 8, 1 shl (flag % 8))
                },
            ),
            bag = com.darkaxt.dualdex.save.gen3.Gen3BagAbi(
                com.darkaxt.dualdex.save.BagPocket.entries.map { pocket ->
                    com.darkaxt.dualdex.save.gen3.Gen3BagPocketAbi(pocket, 0, 1)
                },
            ),
        ),
    )

    private fun gen1Context() = BattleCatalogContext(
        romIdentity = "rom",
        generation = 1,
        liveAreaMemoryLayout = LiveAreaMemoryLayout(0x135d, 1, 0x1361, 0x1360),
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

    private fun gen2Context() = BattleCatalogContext(
        romIdentity = "rom",
        generation = 2,
        gen2TimeOfDayWramOffset = 0x1841,
        liveAreaMemoryLayout = LiveAreaMemoryLayout(0x1cb5, 2, 0x1cb8, 0x1cb7),
        catalog = BattleCatalogView(
            species = mapOf(
                155 to BattleSpecies(155, listOf(20, 20)),
                19 to BattleSpecies(19, listOf(0, 0)),
            ),
            moves = mapOf(
                33 to BattleMove(33, 35), 39 to BattleMove(39, 30), 43 to BattleMove(43, 30),
            ),
            typeIds = setOf(0, 20),
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

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun setFlag(bytes: ByteArray, offset: Int, dexNumber: Int) {
        val index = dexNumber - 1
        bytes[offset + index / 8] = (bytes[offset + index / 8].toInt() or (1 shl (index % 8))).toByte()
    }

    private fun plainPartyRecord(bytes: ByteArray, offset: Int, species: Int, level: Int) {
        bytes[offset + 19] = 0x02
        putU16(bytes, offset + 32, species)
        putU32(bytes, offset + 36, 125)
        bytes[offset + 84] = level.toByte()
    }

    private fun mainState(bytes: ByteArray, callback1: Int, callback2: Int, counter: Int, offset: Int = 0x1574) {
        listOf(callback1, callback2, 0x08000301, 0x08000401, 0, 0, 0x08000501)
            .forEachIndexed { index, value -> putU32(bytes, offset + index * 4, value) }
        putU32(bytes, offset + 0x20, counter)
        putU32(bytes, offset + 0x24, counter)
        putU16(bytes, offset + 0x32, 40)
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
        repeat(4) { index ->
            bytes[offset + 17 + index * 2] = 0
            bytes[offset + 18 + index * 2] = 10
        }
        pp.forEachIndexed { index, value -> bytes[offset + 25 + index] = value.toByte() }
    }

    private fun gen2Mon(
        bytes: ByteArray, offset: Int, species: Int, level: Int, hp: Int, type1: Int, type2: Int,
        moves: List<Int>, pp: List<Int>, dv1: Int, dv2: Int,
    ) {
        bytes[offset] = species.toByte()
        moves.forEachIndexed { index, move -> bytes[offset + 2 + index] = move.toByte() }
        bytes[offset + 6] = dv1.toByte()
        bytes[offset + 7] = dv2.toByte()
        pp.forEachIndexed { index, value -> bytes[offset + 8 + index] = value.toByte() }
        bytes[offset + 13] = level.toByte()
        bytes[offset + 16] = (hp ushr 8).toByte()
        bytes[offset + 17] = hp.toByte()
        bytes[offset + 18] = (hp ushr 8).toByte()
        bytes[offset + 19] = hp.toByte()
        repeat(5) {
            bytes[offset + 20 + it * 2] = 0
            bytes[offset + 21 + it * 2] = 10
        }
        bytes[offset + 30] = type1.toByte()
        bytes[offset + 31] = type2.toByte()
    }

    private class MemoryTransport(
        private val memory: ByteArray,
        private val baseAddress: Long = 0x02000000,
        private val extraMemory: Map<Long, ByteArray> = emptyMap(),
    ) : NetworkCommandTransport {
        val commands = mutableListOf<String>()
        var failPolls = false
        var failSends = false
        private val replies = ArrayDeque<ByteArray>()

        override fun send(payload: ByteArray) {
            if (failSends) error("injected send failure")
            val command = payload.toString(Charsets.US_ASCII)
            commands += command
            val parts = command.split(' ')
            val address = parts[1].toLong(16)
            val length = parts[2].toInt()
            val source = extraMemory.entries.firstOrNull { (start, bytes) ->
                address >= start && address + length <= start + bytes.size
            }
            val encoded = if (source != null) {
                val offset = (address - source.key).toInt()
                (0 until length).joinToString(" ") { "%02X".format(source.value[offset + it].toInt() and 0xFF) }
            } else if (address >= 0x03000000L && address + length <= 0x03008000L) {
                (0 until length).joinToString(" ") { "00" }
            } else {
                val offset = (address - baseAddress).toInt()
                (0 until length).joinToString(" ") { "%02X".format(memory[offset + it].toInt() and 0xFF) }
            }
            replies += "READ_CORE_MEMORY ${parts[1]} $encoded".toByteArray()
        }

        override fun poll(): ByteArray? {
            if (failPolls) error("injected poll failure")
            return replies.pollFirst()
        }
        override fun close() = Unit
    }
}
