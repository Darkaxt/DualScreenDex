package com.darkaxt.dualdex.live

import com.darkaxt.dualdex.battle.BattleCatalogView
import com.darkaxt.dualdex.battle.BattleTrackingUpdate
import com.darkaxt.dualdex.battle.LiveClockState
import com.darkaxt.dualdex.battle.LiveGameSnapshot
import com.darkaxt.dualdex.battle.LiveLocationState
import com.darkaxt.dualdex.battle.LiveBattleState
import com.darkaxt.dualdex.battle.LiveClockPhase
import com.darkaxt.dualdex.battle.LivePokedexState
import com.darkaxt.dualdex.battle.LiveTrainerState
import com.darkaxt.dualdex.battle.LiveUnavailableCode
import com.darkaxt.dualdex.battle.LiveUnavailableReason
import com.darkaxt.dualdex.battle.LiveValue
import com.darkaxt.dualdex.save.SaveSnapshot
import com.darkaxt.dualdex.save.SavedArea
import com.darkaxt.dualdex.save.SaveDocumentSource
import com.darkaxt.dualdex.save.SaveObservation
import com.darkaxt.dualdex.save.SaveObservationKind
import com.darkaxt.dualdex.save.TrainerIdentity
import com.darkaxt.dualdex.save.TrainerPlayTime
import com.darkaxt.dualdex.save.TrainerSnapshot
import com.darkaxt.dualdex.save.BagEntry
import com.darkaxt.dualdex.save.BagPocket
import com.darkaxt.dualdex.save.BagPocketSnapshot
import com.enrpau.dualscreendex.companion.api.SaveRamView
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.darkaxt.dualdex.knowledge.SaveFileFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedGameStateDecoderTest {
    @Test
    fun `trace proves recovery is hidden until the first live player sample`() {
        val events = mutableListOf<ResolvedStateTraceEvent>()
        val decoder = UnifiedGameStateDecoder(
            stateTraceSink = ResolvedStateTraceSink(events::add),
        )
        decoder.beginSession(context(ROM))

        decoder.acceptRecovery(
            recovery(
                rom = ROM,
                seen = (1..52).toSet(),
                caught = (1..52).toSet(),
            ),
        )

        val recoveryCaught = requireNotNull(events.last().fields.singleOrNull { it.field == "pokedex.caught" })
        assertEquals(ResolvedStateTraceTrigger.RECOVERY_APPLIED, events.last().trigger)
        assertEquals(ResolvedValueSource.UNAVAILABLE, recoveryCaught.after?.source)
        assertNull(recoveryCaught.after?.count)

        decoder.acceptDecodedLive(
            liveSnapshot(
                rom = ROM,
                sampleId = 1,
                money = LiveValue.Available(3_000L),
                seen = LiveValue.Available(setOf(252)),
                caught = LiveValue.Available(setOf(252)),
            ),
        )

        val liveCaught = requireNotNull(events.last().fields.singleOrNull { it.field == "pokedex.caught" })
        assertEquals(ResolvedStateTraceTrigger.LIVE_SAMPLE, events.last().trigger)
        assertEquals(ResolvedValueSource.LIVE, liveCaught.after?.source)
        assertEquals(1, liveCaught.after?.count)
    }

    @Test
    fun `trace identifies a temporary fallback to held recovery caught data`() {
        val events = mutableListOf<ResolvedStateTraceEvent>()
        val decoder = UnifiedGameStateDecoder(
            stateTraceSink = ResolvedStateTraceSink(events::add),
        )
        decoder.beginSession(context(ROM))
        decoder.acceptRecovery(
            recovery(
                rom = ROM,
                seen = (1..52).toSet(),
                caught = (1..52).toSet(),
            ),
        )
        decoder.acceptDecodedLive(
            liveSnapshot(
                rom = ROM,
                sampleId = 1,
                money = LiveValue.Available(3_000L),
                seen = LiveValue.Available(setOf(252)),
                caught = LiveValue.Available(setOf(252)),
            ),
        )

        decoder.acceptDecodedLive(
            liveSnapshot(
                rom = ROM,
                sampleId = 2,
                money = LiveValue.Available(3_000L),
                seen = unavailable(),
                caught = unavailable(),
            ),
        )

        val fallback = requireNotNull(events.last().fields.singleOrNull { it.field == "pokedex.caught" })
        assertEquals(ResolvedStateTraceTrigger.LIVE_SAMPLE, events.last().trigger)
        assertEquals(ResolvedValueSource.LIVE, fallback.before?.source)
        assertEquals(1, fallback.before?.count)
        assertEquals(ResolvedValueSource.RECOVERY, fallback.after?.source)
        assertEquals(52, fallback.after?.count)

        decoder.acceptDecodedLive(
            liveSnapshot(
                rom = ROM,
                sampleId = 3,
                money = LiveValue.Available(3_000L),
                seen = LiveValue.Available(setOf(252)),
                caught = LiveValue.Available(setOf(252)),
            ),
        )

        val correction = requireNotNull(events.last().fields.singleOrNull { it.field == "pokedex.caught" })
        assertEquals(ResolvedValueSource.RECOVERY, correction.before?.source)
        assertEquals(52, correction.before?.count)
        assertEquals(ResolvedValueSource.LIVE, correction.after?.source)
        assertEquals(1, correction.after?.count)
    }

    @Test
    fun `trace distinguishes an incorrect live caught count from its live correction`() {
        val events = mutableListOf<ResolvedStateTraceEvent>()
        val decoder = UnifiedGameStateDecoder(
            stateTraceSink = ResolvedStateTraceSink(events::add),
        )
        decoder.beginSession(context(ROM))
        decoder.acceptDecodedLive(
            liveSnapshot(
                rom = ROM,
                sampleId = 1,
                money = LiveValue.Available(3_000L),
                seen = LiveValue.Available((1..52).toSet()),
                caught = LiveValue.Available((1..52).toSet()),
            ),
        )
        decoder.acceptDecodedLive(
            liveSnapshot(
                rom = ROM,
                sampleId = 2,
                money = LiveValue.Available(3_000L),
                seen = LiveValue.Available(setOf(252)),
                caught = LiveValue.Available(setOf(252)),
            ),
        )

        val correction = requireNotNull(events.last().fields.singleOrNull { it.field == "pokedex.caught" })
        assertEquals(ResolvedStateTraceTrigger.LIVE_SAMPLE, events.last().trigger)
        assertEquals(ResolvedValueSource.LIVE, correction.before?.source)
        assertEquals(52, correction.before?.count)
        assertEquals(ResolvedValueSource.LIVE, correction.after?.source)
        assertEquals(1, correction.after?.count)
    }

    @Test
    fun `trace runs before listeners suppresses semantic no-ops and fails open`() {
        val order = mutableListOf<String>()
        val events = mutableListOf<ResolvedStateTraceEvent>()
        val decoder = UnifiedGameStateDecoder(
            stateTraceSink = ResolvedStateTraceSink { event ->
                order += "trace"
                events += event
            },
        )
        decoder.subscribe { update ->
            if (update.snapshot != null) order += "listener"
        }
        decoder.beginSession(context(ROM))
        val first = liveSnapshot(
            rom = ROM,
            sampleId = 1,
            money = LiveValue.Available(3_000L),
            seen = LiveValue.Available(setOf(252)),
            caught = LiveValue.Available(setOf(252)),
        )

        decoder.acceptDecodedLive(first)
        assertEquals(listOf("trace", "listener"), order)
        val eventCount = events.size

        decoder.acceptDecodedLive(first.copy(sampleId = 2))
        assertEquals(eventCount, events.size)

        val failOpen = UnifiedGameStateDecoder(
            stateTraceSink = ResolvedStateTraceSink { error("trace failure") },
        )
        val publications = mutableListOf<ResolvedGameStateUpdate>()
        failOpen.subscribe(publications::add)
        failOpen.beginSession(context(ROM))
        failOpen.acceptDecodedLive(first)
        assertEquals(setOf(252), failOpen.current?.pokedex?.caughtSpeciesIds?.value)
        assertTrue(publications.any { it.snapshot?.pokedex?.caughtSpeciesIds?.value == setOf(252) })
    }

    @Test
    fun `trace omits player save and raw species identities`() {
        val events = mutableListOf<ResolvedStateTraceEvent>()
        val decoder = UnifiedGameStateDecoder(
            stateTraceSink = ResolvedStateTraceSink(events::add),
        )
        decoder.beginSession(context(ROM))
        decoder.acceptRecovery(
            recovery(ROM).copy(observation = observation(SaveObservationKind.INITIAL, 1)),
        )
        decoder.acceptDecodedLive(
            liveSnapshot(
                rom = ROM,
                sampleId = 1,
                money = LiveValue.Available(3_000L),
                seen = LiveValue.Available(setOf(252)),
                caught = LiveValue.Available(setOf(252)),
            ),
        )

        val encoded = events.joinToString("\n")
        assertFalse(encoded.contains("RED"))
        assertFalse(encoded.contains("12345"))
        assertFalse(encoded.contains("save-a"))
        assertFalse(encoded.contains("Game.srm"))
        assertFalse(encoded.contains("file:///"))
        assertFalse(encoded.contains("252"))
    }

    @Test
    fun `publishes exact changed sections while ignoring sample identity`() {
        val decoder = UnifiedGameStateDecoder()
        val updates = mutableListOf<ResolvedGameStateUpdate>()
        decoder.subscribe(updates::add)
        decoder.beginSession(context(ROM))
        val base = liveSnapshot(
            rom = ROM,
            sampleId = 1,
            money = LiveValue.Available(900L),
            seen = LiveValue.Available(setOf(1)),
            caught = LiveValue.Available(emptySet()),
        ).copy(
            location = LiveLocationState(LiveValue.Available(9), unavailable()),
            clock = LiveValue.Available(LiveClockState(10, 30, 0)),
        )

        decoder.acceptDecodedLive(base)
        assertEquals(ResolvedGameSection.entries.toSet(), updates.last().changedSections)

        val countAfterInitial = updates.size
        decoder.acceptDecodedLive(base.copy(sampleId = 2))
        assertEquals(countAfterInitial, updates.size)

        decoder.acceptDecodedLive(
            base.copy(
                sampleId = 3,
                clock = LiveValue.Available(LiveClockState(10, 30, 1)),
            ),
        )
        assertEquals(setOf(ResolvedGameSection.OVERWORLD), updates.last().changedSections)

        decoder.acceptDecodedLive(
            base.copy(
                sampleId = 4,
                clock = LiveValue.Available(LiveClockState(10, 30, 1)),
                pokedex = base.pokedex.copy(seenDexNumbers = LiveValue.Available(setOf(1, 2))),
            ),
        )
        assertEquals(setOf(ResolvedGameSection.PLAYER), updates.last().changedSections)
    }

    @Test
    fun liveMoneyAndRecoveryDexResolveIndependently() {
        val decoder = UnifiedGameStateDecoder()
        decoder.beginSession(context(ROM))
        decoder.acceptRecovery(
            recovery(
                rom = ROM,
                money = 500L,
                seen = setOf(1, 2),
                caught = setOf(1),
            ),
        )
        decoder.acceptDecodedLive(
            liveSnapshot(
                rom = ROM,
                sampleId = 1,
                money = LiveValue.Available(900L),
                seen = unavailable(),
                caught = unavailable(),
            ),
        )

        assertEquals(900L, decoder.current!!.trainer.money.value)
        assertEquals(ResolvedValueSource.LIVE, decoder.current!!.trainer.money.source)
        assertEquals(setOf(1, 2), decoder.current!!.pokedex.seenSpeciesIds.value)
        assertEquals(ResolvedValueSource.RECOVERY, decoder.current!!.pokedex.seenSpeciesIds.source)
    }

    @Test
    fun recoveryDoesNotPublishPlayerStateBeforeTheFirstLiveSample() {
        val decoder = UnifiedGameStateDecoder()
        val updates = mutableListOf<ResolvedGameStateUpdate>()
        decoder.subscribe(updates::add)
        decoder.beginSession(context(ROM))

        decoder.acceptRecovery(
            recovery(
                rom = ROM,
                money = 500L,
                seen = setOf(1, 2, 3),
                caught = setOf(1, 2),
            ),
        )

        assertNotNull(decoder.current)
        assertEquals(ResolvedValueSource.UNAVAILABLE, decoder.current?.trainer?.money?.source)
        assertEquals(ResolvedValueSource.UNAVAILABLE, decoder.current?.pokedex?.seenSpeciesIds?.source)
        assertEquals(ResolvedValueSource.UNAVAILABLE, decoder.current?.pokedex?.caughtSpeciesIds?.source)
        assertTrue(updates.none { it.snapshot?.pokedex?.seenSpeciesIds?.value?.isNotEmpty() == true })

        decoder.acceptDecodedLive(
            liveSnapshot(
                rom = ROM,
                sampleId = 1,
                money = LiveValue.Available(3_000L),
                seen = LiveValue.Available(setOf(1)),
                caught = LiveValue.Available(setOf(1)),
            ),
        )

        assertEquals(3_000L, decoder.current?.trainer?.money?.value)
        assertEquals(setOf(1), decoder.current?.pokedex?.seenSpeciesIds?.value)
        assertEquals(setOf(1), decoder.current?.pokedex?.caughtSpeciesIds?.value)
    }

    @Test
    fun mismatchedRecoveryIsRejectedWithoutPublishingState() {
        val decoder = UnifiedGameStateDecoder()
        decoder.beginSession(context(ROM))

        val application = decoder.acceptRecovery(recovery("different-rom"))

        assertFalse(application.accepted)
        assertNull(decoder.current)
    }

    @Test
    fun clearingRecoveryCannotEraseLiveState() {
        val decoder = UnifiedGameStateDecoder()
        decoder.beginSession(context(ROM))
        decoder.acceptRecovery(recovery(ROM, money = 500L))
        decoder.acceptDecodedLive(liveSnapshot(ROM, sampleId = 1, money = LiveValue.Available(900L)))

        decoder.clearRecovery()

        assertEquals(900L, decoder.current!!.trainer.money.value)
        assertEquals(ResolvedValueSource.LIVE, decoder.current!!.trainer.money.source)
    }

    @Test
    fun disconnectDropsOnlyLiveAuthorityAndImmediatelyRevealsRecovery() {
        val decoder = UnifiedGameStateDecoder()
        decoder.beginSession(context(ROM))
        decoder.acceptRecovery(recovery(ROM, money = 500L))
        decoder.acceptDecodedLive(liveSnapshot(ROM, sampleId = 1, money = LiveValue.Available(900L)))

        decoder.suspendLive()

        assertEquals(500L, decoder.current!!.trainer.money.value)
        assertEquals(ResolvedValueSource.RECOVERY, decoder.current!!.trainer.money.source)
    }

    @Test
    fun subscriptionPublishesSemanticChangesAndCanBeClosed() {
        val decoder = UnifiedGameStateDecoder()
        val publications = mutableListOf<ResolvedGameStateUpdate>()
        val subscription = decoder.subscribe(publications::add)
        decoder.beginSession(context(ROM))

        decoder.acceptDecodedLive(liveSnapshot(ROM, sampleId = 1, money = LiveValue.Available(900L)))
        decoder.acceptDecodedLive(liveSnapshot(ROM, sampleId = 2, money = LiveValue.Available(900L)))

        assertEquals(2, publications.size)
        assertNull(publications.first().snapshot)
        assertEquals(2L, decoder.current!!.sampleId)

        subscription.close()
        decoder.acceptDecodedLive(liveSnapshot(ROM, sampleId = 3, money = LiveValue.Available(901L)))
        assertEquals(2, publications.size)
    }

    @Test
    fun switchingRomClearsBothAuthoritiesBeforeNewState() {
        val decoder = UnifiedGameStateDecoder()
        val publications = mutableListOf<ResolvedGameStateUpdate>()
        decoder.subscribe(publications::add)
        decoder.beginSession(context(ROM))
        decoder.acceptDecodedLive(liveSnapshot(ROM, sampleId = 1, money = LiveValue.Available(900L)))

        decoder.beginSession(context("second-rom"))

        assertNull(decoder.current)
        assertNull(publications.last().snapshot)
        assertEquals(ResolvedGameSection.entries.toSet(), publications.last().changedSections)
        assertTrue(publications.any { it.snapshot?.romIdentity == ROM })
    }

    @Test
    fun wrapsExistingGen2BattleLocationPositionAndLightingAsOneLogicalSample() {
        val decoder = UnifiedGameStateDecoder()
        decoder.beginSession(context(ROM, generation = 2))

        decoder.acceptExistingGenerationSample(
            sampleId = 7,
            battle = LiveBattleState(false, null, com.darkaxt.dualdex.battle.BattleEncounterKind.UNKNOWN),
            areaBaseId = 0x1803,
            mapPosition = com.darkaxt.dualdex.battle.RuntimeMapPosition(14, 9),
            clock = LiveClockState(phase = LiveClockPhase.NIGHT),
        )

        val current = requireNotNull(decoder.current)
        assertEquals(7L, current.sampleId)
        assertEquals(0x1803, current.location.areaBaseId.value)
        assertEquals(com.darkaxt.dualdex.battle.RuntimeMapPosition(14, 9), current.location.position.value)
        assertEquals(LiveClockPhase.NIGHT, current.clock.value?.phase)
        assertEquals(ResolvedValueSource.LIVE, current.location.areaBaseId.source)
        assertTrue(current.gameAccessReady())
    }

    @Test
    fun gen3ReadinessRequiresLiveAreaIdentityAndAnAdvancingClock() {
        val decoder = UnifiedGameStateDecoder()
        decoder.beginSession(context(ROM))
        decoder.acceptRecovery(
            recovery(ROM).copy(snapshot = recovery(ROM).snapshot.copy(currentArea = SavedArea(0, 9))),
        )
        assertFalse(requireNotNull(decoder.current).gameAccessReady())

        val base = liveSnapshot(ROM, sampleId = 1, money = LiveValue.Available(900L)).copy(
            location = LiveLocationState(LiveValue.Available(9), unavailable()),
            clock = LiveValue.Available(LiveClockState(0, 0, 0)),
        )
        decoder.acceptDecodedLive(base)
        assertFalse(requireNotNull(decoder.current).gameAccessReady())

        decoder.acceptDecodedLive(base.copy(sampleId = 2, clock = LiveValue.Available(LiveClockState(0, 0, 1))))
        assertTrue(requireNotNull(decoder.current).gameAccessReady())
    }

    @Test
    fun changedRecoveryFreezesPreApplicationKnowledgeInsideTheStateOwner() {
        var currentLedger = KnowledgeLedger(seenSpecies = setOf(25))
        val decoder = UnifiedGameStateDecoder { currentLedger }
        decoder.beginSession(context(ROM))
        decoder.acceptRecovery(
            recovery(ROM).copy(observation = observation(SaveObservationKind.INITIAL, 1)),
        )
        decoder.acceptDecodedLive(
            liveSnapshot(ROM, sampleId = 1, money = LiveValue.Available(900L)),
        )
        currentLedger = KnowledgeLedger(
            seenSpecies = setOf(25, 133),
            localMapPoiPreferences = com.enrpau.dualscreendex.companion.model.LocalMapPoiPreferences(showPlaces = false),
        )

        val application = decoder.acceptRecovery(
            recovery(ROM, money = 700L).copy(observation = observation(SaveObservationKind.CHANGED, 2)),
        )

        assertTrue(application.accepted)
        assertEquals(emptySet<Int>(), application.checkpointLedger?.seenSpecies)
        assertFalse(requireNotNull(application.checkpointLedger).localMapPoiPreferences.showPlaces)
        assertEquals(900L, decoder.current?.trainer?.money?.value)
        assertEquals(2L, decoder.current?.recovery?.applicationId)
        assertEquals(SaveObservationKind.CHANGED, decoder.current?.recovery?.observationKind)
        assertFalse(requireNotNull(decoder.current?.recovery).resetKnowledge)
    }

    @Test
    fun `live battle knowledge stays in memory and does not checkpoint until the save changes`() {
        var checkpointReads = 0
        val decoder = UnifiedGameStateDecoder {
            checkpointReads++
            KnowledgeLedger()
        }
        decoder.beginSession(context(ROM))
        decoder.acceptRecovery(
            recovery(ROM).copy(observation = observation(SaveObservationKind.INITIAL, 1)),
        )
        decoder.acceptDecodedLive(
            liveSnapshot(ROM, sampleId = 1, money = LiveValue.Available(900L)),
        )

        decoder.acceptBattleTracking(
            BattleTrackingUpdate(
                active = true,
                sample = null,
                observations = mapOf(1 to mapOf(33 to 2)),
            ),
        )

        assertEquals(0, checkpointReads)
        assertEquals(mapOf(1 to mapOf(33 to 2)), decoder.current?.battleKnowledge?.observedMoves)

        decoder.acceptRecovery(
            recovery(ROM).copy(observation = observation(SaveObservationKind.UNCHANGED, 1)),
        )
        assertEquals(0, checkpointReads)

        decoder.acceptRecovery(
            recovery(ROM).copy(observation = observation(SaveObservationKind.CHANGED, 2)),
        )
        assertEquals(1, checkpointReads)
    }

    @Test
    fun bagPocketsAndEventFlagsResolveIndependently() {
        val decoder = UnifiedGameStateDecoder()
        decoder.beginSession(context(ROM))
        decoder.acceptRecovery(
            recovery(ROM).copy(
                snapshot = recovery(ROM).snapshot.copy(
                    bag = listOf(
                        BagPocketSnapshot(BagPocket.ITEMS, listOf(BagEntry(1, 2))),
                        BagPocketSnapshot(BagPocket.BALLS, listOf(BagEntry(4, 3))),
                    ),
                    eventFlagIds = setOf(1007),
                ),
            ),
        )
        decoder.acceptDecodedLive(
            liveSnapshot(ROM, 1, LiveValue.Available(900L)).copy(
                bag = mapOf(BagPocket.ITEMS to LiveValue.Available(BagPocketSnapshot(BagPocket.ITEMS, emptyList()))),
                eventFlags = LiveValue.Available(setOf(2000)),
            ),
        )

        val current = requireNotNull(decoder.current)
        assertEquals(ResolvedValueSource.LIVE, current.bag.getValue(BagPocket.ITEMS).source)
        assertTrue(current.bag.getValue(BagPocket.ITEMS).value?.entries?.isEmpty() == true)
        assertEquals(ResolvedValueSource.RECOVERY, current.bag.getValue(BagPocket.BALLS).source)
        assertEquals(4, current.bag.getValue(BagPocket.BALLS).value?.entries?.single()?.itemId)
        assertEquals(setOf(2000), current.eventFlags.value)
        assertEquals(ResolvedValueSource.LIVE, current.eventFlags.source)
    }

    @Test
    fun `gen3 translated sections reuse by fingerprint and clear on live suspension`() {
        val decoder = UnifiedGameStateDecoder()
        decoder.beginSession(gen3LiveContext())
        val regions = gen3Regions()
        val battle = LiveBattleState(false, null, com.darkaxt.dualdex.battle.BattleEncounterKind.UNKNOWN)

        decoder.acceptGen3LiveSample(1, regions, battle, null, null)
        decoder.acceptGen3LiveSample(2, regions, battle, null, null)

        assertEquals(1L, decoder.performanceCounters().getValue("live.decode.player"))
        assertEquals(1L, decoder.performanceCounters().getValue("live.decode.party"))
        assertEquals(1L, decoder.performanceCounters().getValue("live.decode.overworld"))
        assertEquals(1L, decoder.performanceCounters().getValue("live.decode.progression"))
        assertEquals(1L, decoder.performanceCounters().getValue("live.reuse.overworld"))

        val clockChanged = regions.mapValues { (_, bytes) -> bytes.copyOf() }.toMutableMap()
        clockChanged.getValue(com.darkaxt.dualdex.battle.Gen3LiveMemoryReader.CLOCK_ID)[4] = 11
        decoder.acceptGen3LiveSample(3, clockChanged, battle, null, null)

        assertEquals(2L, decoder.performanceCounters().getValue("live.decode.overworld"))
        assertEquals(1L, decoder.performanceCounters().getValue("live.decode.player"))
        assertEquals(2L, decoder.performanceCounters().getValue("live.reuse.player"))

        decoder.suspendLive()
        decoder.acceptGen3LiveSample(4, clockChanged, battle, null, null)
        assertEquals(2L, decoder.performanceCounters().getValue("live.decode.player"))
        assertEquals(3L, decoder.performanceCounters().getValue("live.decode.overworld"))

        val replacement = gen3LiveContext().let { original ->
            original.copy(
                romIdentity = "rom-b",
                saveParseContext = requireNotNull(original.saveParseContext).copy(romIdentity = "rom-b"),
            )
        }
        decoder.beginSession(replacement)
        decoder.acceptGen3LiveSample(5, clockChanged, battle, null, null)
        assertEquals(3L, decoder.performanceCounters().getValue("live.decode.player"))
        assertEquals(3L, decoder.performanceCounters().getValue("live.decode.party"))
        assertEquals(4L, decoder.performanceCounters().getValue("live.decode.overworld"))
        assertEquals(3L, decoder.performanceCounters().getValue("live.decode.progression"))
    }

    private fun context(rom: String, generation: Int = 3) = TransientGameStateContext(
        romIdentity = rom,
        generation = generation,
        catalog = BattleCatalogView(emptyMap(), emptyMap(), emptySet()),
        saveParseContext = com.darkaxt.dualdex.save.SaveParseContext(
            romIdentity = rom,
            speciesById = (1..500).associateWith { speciesId ->
                com.darkaxt.dualdex.save.SaveSpeciesContext(speciesId, speciesId, 0)
            },
        ),
    )

    private fun gen3LiveContext(): TransientGameStateContext {
        val layout = com.darkaxt.dualdex.battle.Gen3RuntimeMemoryLayout(
            mainAddress = 0x03002000,
            inBattleAddress = 0x03002040,
            inBattleMask = 1,
            saveBlock1MapGroupOffset = 4,
            saveBlock1MapNumberOffset = 5,
            liveClockAddress = 0x030039E8,
            playerPartyCountAddress = 0x02000200,
            playerPartyAddress = 0x02000300,
            playerPartyCapacity = 6,
            playerPartyRecordSize = 100,
            saveBlock1PointerAddress = 0x03001000,
            saveBlock2PointerAddress = 0x03001004,
            saveBlock1Size = 0x100,
            saveBlock2Size = 0x280,
        )
        val abi = com.darkaxt.dualdex.save.gen3.Gen3SaveRuntimeAbi(
            saveBlock1Size = 0x100,
            saveBlock2Size = 0x280,
            textEncoding = com.darkaxt.dualdex.save.gen3.Gen3TextEncoding.ENGLISH,
            trainer = com.darkaxt.dualdex.save.gen3.Gen3TrainerCardAbi(
                playerNameOffset = 0,
                playerNameLength = 8,
                genderOffset = 8,
                trainerIdOffset = 10,
                playTimeHoursOffset = 14,
                playTimeMinutesOffset = 16,
                encryptionKeyOffset = 0x240,
                moneyOffset = 0x10,
                maximumMoney = 999_999,
                badgeFlags = listOf(com.darkaxt.dualdex.save.gen3.Gen3BitFlag(0x11, 1)),
            ),
            bag = com.darkaxt.dualdex.save.gen3.Gen3BagAbi(
                listOf(com.darkaxt.dualdex.save.gen3.Gen3BagPocketAbi(BagPocket.ITEMS, 0x30, 2)),
            ),
            eventFlags = com.darkaxt.dualdex.save.gen3.Gen3EventFlagAbi(0x20, 4),
        )
        return TransientGameStateContext(
            romIdentity = ROM,
            generation = 3,
            catalog = BattleCatalogView(emptyMap(), emptyMap(), emptySet()),
            gen3RuntimeMemoryLayout = layout,
            saveParseContext = com.darkaxt.dualdex.save.SaveParseContext(
                romIdentity = ROM,
                speciesById = mapOf(1 to com.darkaxt.dualdex.save.SaveSpeciesContext(1, 1, 0)),
                gen3SaveRuntimeAbi = abi,
            ),
        )
    }

    private fun gen3Regions(): Map<String, ByteArray> = mapOf(
        com.darkaxt.dualdex.battle.Gen3LiveMemoryReader.SAVE_BLOCK1_ID to ByteArray(0x100).also {
            it[4] = 0
            it[5] = 9
        },
        com.darkaxt.dualdex.battle.Gen3LiveMemoryReader.SAVE_BLOCK2_ID to ByteArray(0x280),
        com.darkaxt.dualdex.battle.Gen3LiveMemoryReader.PARTY_COUNT_ID to byteArrayOf(0),
        com.darkaxt.dualdex.battle.Gen3LiveMemoryReader.PARTY_ID to ByteArray(600),
        com.darkaxt.dualdex.battle.Gen3LiveMemoryReader.CLOCK_ID to byteArrayOf(0, 0, 12, 30, 10),
    )

    private fun recovery(
        rom: String,
        money: Long = 500L,
        seen: Set<Int> = setOf(1),
        caught: Set<Int> = setOf(1),
    ) = RecoveryProjection(
        snapshot = SaveSnapshot(
            romIdentity = rom,
            saveIdentity = "save-a",
            saveGeneration = 3,
            saveCounter = 1,
            currentArea = null,
            seenDexNumbers = seen,
            caughtDexNumbers = caught,
            party = emptyList(),
            storedIndividuals = emptyList(),
            capabilities = emptyMap(),
            trainer = TrainerSnapshot(
                name = "RED",
                gender = 0,
                publicTrainerId = 12_345,
                money = money,
                playTimeHours = 2,
                playTimeMinutes = 17,
                badgeFlags = 0,
                dexSeen = seen.size,
                dexCaught = caught.size,
            ),
        ),
        saveRam = SaveRamView(status = "MATCHED"),
    )

    private fun observation(kind: SaveObservationKind, version: Int) = SaveObservation(
        kind = kind,
        source = SaveDocumentSource(
            id = "file:///Game.srm",
            displayPath = "Game.srm",
            name = "Game.srm",
            size = 4,
            lastModifiedEpochMs = version.toLong(),
            read = { byteArrayOf(1, 2, 3, 4) },
        ),
        fingerprint = SaveFileFingerprint(version.toString(16).padStart(64, '0'), 4, version.toLong()),
    )

    private fun liveSnapshot(
        rom: String,
        sampleId: Long,
        money: LiveValue<Long>,
        seen: LiveValue<Set<Int>> = unavailable(),
        caught: LiveValue<Set<Int>> = unavailable(),
    ) = LiveGameSnapshot(
        romIdentity = rom,
        generation = 3,
        sampleId = sampleId,
        trainer = LiveTrainerState(
            identity = LiveValue.Available(TrainerIdentity("RED", 0)),
            publicTrainerId = LiveValue.Available(12_345),
            money = money,
            playTime = LiveValue.Available(TrainerPlayTime(2, 17)),
            badgeFlags = LiveValue.Available(0),
            stars = unavailable(),
        ),
        pokedex = LivePokedexState(seen, caught),
        party = LiveValue.Available(emptyList()),
        battle = unavailable(),
        location = LiveLocationState(unavailable(), unavailable()),
        clock = LiveValue.Available(LiveClockState(12, 30, 10)),
        bag = emptyMap(),
        eventFlags = unavailable(),
    )

    private fun <T> unavailable(): LiveValue<T> = LiveValue.Unavailable(
        LiveUnavailableReason(
            code = LiveUnavailableCode.MISSING_REGION,
            detail = "test region is absent",
        ),
    )

    private companion object {
        const val ROM = "rom-a"
    }
}
