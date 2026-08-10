package com.darkaxt.dualdex.battle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen2BattleLayoutResolverTest {
    private val catalog = BattleCatalogView(
        species = mapOf(
            155 to BattleSpecies(155, listOf(20, 20)),
            19 to BattleSpecies(19, listOf(0, 0)),
        ),
        moves = mapOf(
            33 to BattleMove(33, 35),
            39 to BattleMove(39, 30),
            43 to BattleMove(43, 30),
        ),
        typeIds = setOf(0, 20),
    )

    @Test
    fun resolvesCrystalRev1BattleAndMoveSignals() {
        val wram = crystalBattle()

        val resolution = Gen2BattleLayoutResolver().resolve(wram, catalog)

        assertTrue(resolution is LayoutResolution.Resolved)
        val sample = (resolution as LayoutResolution.Resolved).sample
        assertEquals(0x122d, sample.layout.battlerCountOffset)
        assertEquals(155, sample.battlers.first().speciesId)
        assertEquals(5, sample.battlers.first().level)
        assertEquals(19, sample.opponents.single().speciesId)
        assertEquals(2, sample.opponents.single().level)
        assertEquals(listOf(5, 8, 9, 10), sample.opponents.single().dvs)
        assertEquals(33, sample.selectedMoveId)
        assertEquals(33, sample.opponentExecutedMoveId)
        assertEquals(CapabilityState.AVAILABLE, sample.capabilities[BattleCapability.OPPONENT_PP])
        assertEquals(CapabilityState.NOT_APPLICABLE, sample.capabilities[BattleCapability.MULTIPLE_OPPONENTS])
    }

    @Test
    fun resolvesTheSharedGoldAndSilverBattleShape() {
        val wram = ByteArray(0x2000).also { bytes ->
            putBattleMon(
                bytes, 0x0b0c, species = 155, level = 5, hp = 20, maxHp = 20,
                moves = listOf(33, 43), pp = listOf(35, 30), type1 = 20, type2 = 20,
                dv1 = 0x51, dv2 = 0x43,
            )
            putBattleMon(
                bytes, 0x10ef, species = 19, level = 2, hp = 13, maxHp = 13,
                moves = listOf(33, 39), pp = listOf(35, 30), type1 = 0, type2 = 0,
                dv1 = 0x58, dv2 = 0x9a,
            )
            bytes[0x1116] = 1
            bytes[0x1119] = 0
            bytes[0x0bc1] = 33
            bytes[0x0bc2] = 0
            bytes[0x0bfa] = 0
            bytes[0x0c12] = 0
        }

        val resolution = Gen2BattleLayoutResolver().resolve(wram, catalog)

        assertTrue(resolution is LayoutResolution.Resolved)
        val sample = (resolution as LayoutResolution.Resolved).sample
        assertEquals(0x1116, sample.layout.battlerCountOffset)
        assertEquals(0x0bc1, sample.layout.moveCursorOffset)
        assertEquals(19, sample.opponents.single().speciesId)
        assertEquals(33, sample.selectedMoveId)
    }

    @Test
    fun rejectsANonCatalogMoveSignalInAnOtherwisePlausibleShape() {
        val wram = crystalBattle()
        wram[0x06e3] = 250.toByte()

        val resolution = Gen2BattleLayoutResolver().resolve(wram, catalog)

        assertEquals(LayoutResolution.NotFound, resolution)
    }

    @Test
    fun acceptsCatalogTypesChangedByAnInBattleEffect() {
        val wram = crystalBattle().also { bytes ->
            bytes[0x1206 + 30] = 20
            bytes[0x1206 + 31] = 20
        }

        val resolution = Gen2BattleLayoutResolver().resolve(wram, catalog)

        assertTrue(resolution is LayoutResolution.Resolved)
        assertEquals(listOf(20, 20), (resolution as LayoutResolution.Resolved).sample.opponents.single().typeIds)
    }

    private fun crystalBattle(): ByteArray = ByteArray(0x2000).also { bytes ->
        putBattleMon(
            bytes, 0x062c, species = 155, level = 5, hp = 17, maxHp = 20,
            moves = listOf(33, 43), pp = listOf(34, 30), type1 = 20, type2 = 20,
            dv1 = 0x51, dv2 = 0x43,
        )
        putBattleMon(
            bytes, 0x1206, species = 19, level = 2, hp = 8, maxHp = 13,
            moves = listOf(33, 39), pp = listOf(34, 30), type1 = 0, type2 = 0,
            dv1 = 0x58, dv2 = 0x9a,
        )
        bytes[0x122d] = 1
        bytes[0x1230] = 0
        bytes[0x06e3] = 33
        bytes[0x06e4] = 33
        bytes[0x071c] = 33
        bytes[0x0734] = 0
    }

    private fun putBattleMon(
        bytes: ByteArray,
        offset: Int,
        species: Int,
        level: Int,
        hp: Int,
        maxHp: Int,
        moves: List<Int>,
        pp: List<Int>,
        type1: Int,
        type2: Int,
        dv1: Int,
        dv2: Int,
    ) {
        bytes[offset] = species.toByte()
        moves.forEachIndexed { index, move -> bytes[offset + 2 + index] = move.toByte() }
        bytes[offset + 6] = dv1.toByte()
        bytes[offset + 7] = dv2.toByte()
        pp.forEachIndexed { index, value -> bytes[offset + 8 + index] = value.toByte() }
        bytes[offset + 13] = level.toByte()
        putBe16(bytes, offset + 16, hp)
        putBe16(bytes, offset + 18, maxHp)
        repeat(5) { putBe16(bytes, offset + 20 + it * 2, 10) }
        bytes[offset + 30] = type1.toByte()
        bytes[offset + 31] = type2.toByte()
    }

    private fun putBe16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }
}
