package com.darkaxt.dualdex.live

import com.darkaxt.dualdex.battle.BattleCatalogView
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedGameStateDecoderTest {
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
        assertEquals(setOf(1, 2), decoder.current!!.pokedex.seenDexNumbers.value)
        assertEquals(ResolvedValueSource.RECOVERY, decoder.current!!.pokedex.seenDexNumbers.source)
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
        val publications = mutableListOf<ResolvedGameSnapshot?>()
        val subscription = decoder.subscribe(publications::add)
        decoder.beginSession(context(ROM))

        decoder.acceptDecodedLive(liveSnapshot(ROM, sampleId = 1, money = LiveValue.Available(900L)))
        decoder.acceptDecodedLive(liveSnapshot(ROM, sampleId = 2, money = LiveValue.Available(900L)))

        assertEquals(2, publications.size)
        assertNull(publications.first())
        assertEquals(2L, decoder.current!!.sampleId)

        subscription.close()
        decoder.acceptDecodedLive(liveSnapshot(ROM, sampleId = 3, money = LiveValue.Available(901L)))
        assertEquals(2, publications.size)
    }

    @Test
    fun switchingRomClearsBothAuthoritiesBeforeNewState() {
        val decoder = UnifiedGameStateDecoder()
        val publications = mutableListOf<ResolvedGameSnapshot?>()
        decoder.subscribe(publications::add)
        decoder.beginSession(context(ROM))
        decoder.acceptDecodedLive(liveSnapshot(ROM, sampleId = 1, money = LiveValue.Available(900L)))

        decoder.beginSession(context("second-rom"))

        assertNull(decoder.current)
        assertNull(publications.last())
        assertTrue(publications.any { it?.romIdentity == ROM })
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
        currentLedger = KnowledgeLedger(seenSpecies = setOf(25, 133))

        val application = decoder.acceptRecovery(
            recovery(ROM, money = 700L).copy(observation = observation(SaveObservationKind.CHANGED, 2)),
        )

        assertTrue(application.accepted)
        assertEquals(currentLedger, application.checkpointLedger)
        assertEquals(700L, decoder.current?.trainer?.money?.value)
        assertEquals(2L, decoder.current?.recovery?.applicationId)
        assertEquals(SaveObservationKind.CHANGED, decoder.current?.recovery?.observationKind)
        assertFalse(requireNotNull(decoder.current?.recovery).resetKnowledge)
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

    private fun context(rom: String, generation: Int = 3) = TransientGameStateContext(
        romIdentity = rom,
        generation = generation,
        catalog = BattleCatalogView(emptyMap(), emptyMap(), emptySet()),
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
