package com.darkaxt.dualdex.live

import com.darkaxt.dualdex.battle.BattleCatalogView
import com.darkaxt.dualdex.battle.LiveClockState
import com.darkaxt.dualdex.battle.LiveGameSnapshot
import com.darkaxt.dualdex.battle.LiveLocationState
import com.darkaxt.dualdex.battle.LivePokedexState
import com.darkaxt.dualdex.battle.LiveTrainerState
import com.darkaxt.dualdex.battle.LiveUnavailableCode
import com.darkaxt.dualdex.battle.LiveUnavailableReason
import com.darkaxt.dualdex.battle.LiveValue
import com.darkaxt.dualdex.save.SaveSnapshot
import com.darkaxt.dualdex.save.TrainerIdentity
import com.darkaxt.dualdex.save.TrainerPlayTime
import com.darkaxt.dualdex.save.TrainerSnapshot
import com.enrpau.dualscreendex.companion.api.SaveRamView
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

    private fun context(rom: String) = TransientGameStateContext(
        romIdentity = rom,
        generation = 3,
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
