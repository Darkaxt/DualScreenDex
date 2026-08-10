package com.darkaxt.dualdex.battle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3BattleLayoutResolverTest {
    private val catalog = BattleCatalogView(
        species = mapOf(
            1 to BattleSpecies(1, listOf(11), setOf(65)),
            13 to BattleSpecies(13, listOf(6, 3), setOf(19, 65)),
            16 to BattleSpecies(16, listOf(0, 2), setOf(51, 65)),
            252 to BattleSpecies(252, listOf(11), setOf(65)),
        ),
        moves = mapOf(
            10 to BattleMove(10, 35),
            11 to BattleMove(11, 25),
            40 to BattleMove(40, 35),
            81 to BattleMove(81, 40),
        ),
        typeIds = setOf(0, 2, 3, 6, 11),
    )

    @Test
    fun resolvesAUniqueSingleBattleAndHighlightedPlayerMove() {
        val region = ByteArray(0x3000)
        fixture(region, 0x1000, listOf(
            mon(252, 7, 11, 11, intArrayOf(10, 11), intArrayOf(35, 25), personality = 100),
            mon(13, 3, 6, 3, intArrayOf(40, 81), intArrayOf(35, 40), personality = 200),
        ), moveCursor = 1)

        val resolved = Gen3BattleLayoutResolver().resolve(region, catalog) as LayoutResolution.Resolved

        assertEquals(0x1000, resolved.sample.layout.battleMonsOffset)
        assertEquals(13, resolved.sample.opponents.single().speciesId)
        assertEquals(11, resolved.sample.selectedMoveId)
        assertEquals(TargetMode.AUTOMATIC, resolved.sample.target.mode)
        assertEquals(0, resolved.sample.target.opponentIndex)
    }

    @Test
    fun enumeratesBothDoubleBattleOpponentsAndFollowsAValidatedTargetCursor() {
        val region = ByteArray(0x3000)
        fixture(region, 0x1000, listOf(
            mon(252, 20, 11, 11, intArrayOf(10), intArrayOf(35), personality = 100),
            mon(13, 18, 6, 3, intArrayOf(40, 81), intArrayOf(35, 40), personality = 200),
            mon(1, 19, 11, 11, intArrayOf(10), intArrayOf(35), personality = 300),
            mon(16, 18, 0, 2, intArrayOf(10), intArrayOf(35), personality = 400),
        ), targetBattler = 3)

        val resolved = Gen3BattleLayoutResolver().resolve(region, catalog) as LayoutResolution.Resolved

        assertEquals(listOf(13, 16), resolved.sample.opponents.map { it.speciesId })
        assertEquals(TargetMode.AUTOMATIC, resolved.sample.target.mode)
        assertEquals(1, resolved.sample.target.opponentIndex)
    }

    @Test
    fun keepsBothDoubleOpponentsWithManualFallbackWhenTheCursorIsAmbiguous() {
        val region = ByteArray(0x3000)
        fixture(region, 0x1000, listOf(
            mon(252, 20, 11, 11, intArrayOf(10), intArrayOf(35)),
            mon(13, 18, 6, 3, intArrayOf(40), intArrayOf(35)),
            mon(1, 19, 11, 11, intArrayOf(10), intArrayOf(35)),
            mon(16, 18, 0, 2, intArrayOf(10), intArrayOf(35)),
        ))

        val resolved = Gen3BattleLayoutResolver().resolve(region, catalog) as LayoutResolution.Resolved

        assertEquals(2, resolved.sample.opponents.size)
        assertEquals(TargetMode.MANUAL_TARGET_FALLBACK, resolved.sample.target.mode)
    }

    @Test
    fun rejectsCompletedAndAmbiguousBattleArrays() {
        val completed = ByteArray(0x3000)
        fixture(completed, 0x1000, listOf(
            mon(252, 7, 11, 11, intArrayOf(10), intArrayOf(35)),
            mon(13, 3, 6, 3, intArrayOf(40), intArrayOf(35)),
        ), outcome = 1)
        putU16(completed, 0x1000 + 0x58 + 0x28, 0)
        completed[0x1000 + 0x58 + 0x20] = 0
        val terminal = Gen3BattleLayoutResolver().resolve(completed, catalog) as LayoutResolution.Resolved
        assertEquals(1, terminal.sample.battleOutcome)

        val ambiguous = ByteArray(0x4000)
        fixture(ambiguous, 0x0800, listOf(
            mon(252, 7, 11, 11, intArrayOf(10), intArrayOf(35)),
            mon(13, 3, 6, 3, intArrayOf(40), intArrayOf(35)),
        ))
        fixture(ambiguous, 0x2000, listOf(
            mon(252, 7, 11, 11, intArrayOf(10), intArrayOf(35)),
            mon(13, 3, 6, 3, intArrayOf(40), intArrayOf(35)),
        ))
        assertTrue(Gen3BattleLayoutResolver().resolve(ambiguous, catalog) is LayoutResolution.Ambiguous)
    }

    private fun fixture(
        bytes: ByteArray,
        anchor: Int,
        mons: List<Mon>,
        moveCursor: Int = 0,
        targetBattler: Int? = null,
        outcome: Int = 0,
    ) {
        bytes[anchor - 0x1C] = mons.size.toByte()
        mons.indices.forEach { bytes[anchor - 0x10 + it] = it.toByte() }
        mons.forEachIndexed { index, mon -> writeMon(bytes, anchor + index * 0x58, mon) }
        bytes[anchor + 0x438] = moveCursor.toByte()
        targetBattler?.let { bytes[anchor + 0x43C] = it.toByte() }
        bytes[anchor + 0x2B2] = outcome.toByte()
    }

    private fun writeMon(bytes: ByteArray, offset: Int, mon: Mon) {
        putU16(bytes, offset, mon.species)
        listOf(22, 20, 18, 24, 21).forEachIndexed { index, stat -> putU16(bytes, offset + 2 + index * 2, stat) }
        mon.moves.forEachIndexed { index, move -> putU16(bytes, offset + 0x0C + index * 2, move) }
        val packedIvs = listOf(14, 15, 13, 12, 16, 11).foldIndexed(0) { index, packed, iv -> packed or (iv shl (index * 5)) }
        putU32(bytes, offset + 0x14, packedIvs)
        repeat(8) { bytes[offset + 0x18 + it] = 6 }
        bytes[offset + 0x20] = 65
        bytes[offset + 0x21] = mon.type1.toByte()
        bytes[offset + 0x22] = mon.type2.toByte()
        mon.pp.forEachIndexed { index, pp -> bytes[offset + 0x24 + index] = pp.toByte() }
        putU16(bytes, offset + 0x28, 15)
        bytes[offset + 0x2A] = mon.level.toByte()
        putU16(bytes, offset + 0x2C, 15)
        putU32(bytes, offset + 0x48, mon.personality)
    }

    private fun mon(species: Int, level: Int, type1: Int, type2: Int, moves: IntArray, pp: IntArray, personality: Int = species) =
        Mon(species, level, type1, type2, moves, pp, personality)

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { bytes[offset + it] = (value ushr (it * 8)).toByte() }
    }

    private data class Mon(
        val species: Int,
        val level: Int,
        val type1: Int,
        val type2: Int,
        val moves: IntArray,
        val pp: IntArray,
        val personality: Int,
    )
}
