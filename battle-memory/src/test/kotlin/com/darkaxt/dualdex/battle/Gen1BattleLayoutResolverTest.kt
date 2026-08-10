package com.darkaxt.dualdex.battle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen1BattleLayoutResolverTest {
    private val catalog = BattleCatalogView(
        species = mapOf(
            0x54 to BattleSpecies(0x54, listOf(0x17, 0x17)),
            0x66 to BattleSpecies(0x66, listOf(0, 0)),
        ),
        moves = mapOf(
            0x21 to BattleMove(0x21, 35),
            0x27 to BattleMove(0x27, 30),
            0x2d to BattleMove(0x2d, 40),
            0x54 to BattleMove(0x54, 30),
        ),
        typeIds = setOf(0, 0x17),
    )

    @Test
    fun resolvesTheYellowRivalShapeAndExecutedMoveLatch() {
        val bytes = ByteArray(0x2000)
        putBattleMon(bytes, 0x0fe4, 0x66, 5, 16, 21, listOf(0x21, 0x27), listOf(35, 30), 0, 0, 0x98, 0x88)
        putBattleMon(bytes, 0x1013, 0x54, 5, 20, 20, listOf(0x54, 0x2d), listOf(29, 40), 0x17, 0x17, 0x91, 0xfb)
        bytes[0x1056] = 2
        bytes[0x1059] = 0
        bytes[0x1162] = 1
        bytes[0x0cdc] = 0x54
        bytes[0x0cf1] = 0x54
        bytes[0x0cf2] = 0x21

        val resolution = Gen1BattleLayoutResolver().resolve(bytes, catalog)

        assertTrue(resolution is LayoutResolution.Resolved)
        val sample = (resolution as LayoutResolution.Resolved).sample
        assertEquals(0x66, sample.opponents.single().speciesId)
        assertEquals(listOf(9, 8, 8, 8), sample.opponents.single().dvs)
        assertEquals(0x54, sample.selectedMoveId)
        assertEquals(0x21, sample.opponentExecutedMoveId)
        assertEquals(0x0fe4, sample.layout.battleMonsOffset)
    }

    @Test
    fun recognizesTheDedicatedOakPikachuCaptureWithoutInventingAPlayer() {
        val bytes = ByteArray(0x2000)
        putBattleMon(bytes, 0x0fe4, 0x54, 5, 19, 19, listOf(0x54, 0x2d), listOf(30, 40), 0x17, 0x17, 0x03, 0x0f)
        bytes[0x1056] = 1
        bytes[0x1059] = 4
        bytes[0x1162] = 0

        val sample = (Gen1BattleLayoutResolver().resolve(bytes, catalog) as LayoutResolution.Resolved).sample

        assertEquals(0x54, sample.opponents.single().speciesId)
        assertEquals(1, sample.battlers.size)
        assertEquals(null, sample.selectedMoveId)
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
        putBe16(bytes, offset + 1, hp)
        bytes[offset + 5] = type1.toByte()
        bytes[offset + 6] = type2.toByte()
        moves.forEachIndexed { index, move -> bytes[offset + 8 + index] = move.toByte() }
        bytes[offset + 12] = dv1.toByte()
        bytes[offset + 13] = dv2.toByte()
        bytes[offset + 14] = level.toByte()
        putBe16(bytes, offset + 15, maxHp)
        pp.forEachIndexed { index, value -> bytes[offset + 25 + index] = value.toByte() }
    }

    private fun putBe16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }
}
