package com.darkaxt.dualdex.battle

/**
 * Shape-based Gold/Silver/Crystal battle resolver. Gen 2 keeps the enemy battle
 * structure 0x27 bytes before wBattleMode while the player structure lives in
 * fixed WRAM. The move signals remain grouped beside that player structure.
 */
class Gen2BattleLayoutResolver {
    fun resolve(wram: ByteArray, catalog: BattleCatalogView): LayoutResolution {
        if (wram.size < WRAM_BYTES || catalog.species.isEmpty() || catalog.moves.isEmpty()) {
            return LayoutResolution.NotFound
        }
        val candidates = buildList {
            for (battleFlagOffset in WRAM_BANK_BYTES until wram.size - BATTLE_TYPE_DELTA) {
                if (wram.u8(battleFlagOffset) !in WILD_BATTLE..TRAINER_BATTLE) continue
                resolveAt(wram, battleFlagOffset, catalog)?.let(::add)
            }
        }
        return when (candidates.size) {
            0 -> LayoutResolution.NotFound
            1 -> LayoutResolution.Resolved(candidates.single())
            else -> LayoutResolution.Ambiguous(candidates.size)
        }
    }

    fun resolveKnown(
        wram: ByteArray,
        layout: ResolvedBattleLayout,
        catalog: BattleCatalogView,
    ): BattleMemorySample? = resolveAt(wram, layout.battlerCountOffset, catalog, layout)

    private fun resolveAt(
        bytes: ByteArray,
        battleFlagOffset: Int,
        catalog: BattleCatalogView,
        knownLayout: ResolvedBattleLayout? = null,
    ): BattleMemorySample? {
        if (battleFlagOffset !in bytes.indices || bytes.u8(battleFlagOffset) !in WILD_BATTLE..TRAINER_BATTLE) return null
        if (bytes.u8OrZero(battleFlagOffset + BATTLE_TYPE_DELTA) !in MIN_BATTLE_TYPE..MAX_BATTLE_TYPE) return null
        val enemyOffset = battleFlagOffset - ENEMY_DELTA
        val enemy = readBattleMon(bytes, enemyOffset, battlerIndex = 1, position = 1, catalog) ?: return null

        val playerCandidates = if (knownLayout != null) {
            listOfNotNull(
                readBattleMon(bytes, knownLayout.battleMonsOffset, battlerIndex = 0, position = 0, catalog)
                    ?.let { player ->
                        signalCandidate(
                            bytes,
                            knownLayout.battleMonsOffset,
                            knownLayout.moveCursorOffset - knownLayout.battleMonsOffset,
                            player,
                            enemy,
                            catalog,
                            requireSelectedMove = false,
                        )
                    },
            )
        } else {
            findPlayerCandidates(bytes, battleFlagOffset, enemy, catalog)
        }
        if (playerCandidates.size != 1) return null
        val playerCandidate = playerCandidates.single()
        val player = playerCandidate.mon
        val moveSignalOffset = playerCandidate.moveSignalOffset
        val selectedMove = bytes.moveAt(moveSignalOffset, catalog)?.takeIf { it in player.moves }
        val currentEnemyMove = bytes.moveAt(moveSignalOffset + CURRENT_ENEMY_MOVE_DELTA, catalog)
        if (currentEnemyMove != null && currentEnemyMove !in enemy.moves) return null
        val lastEnemyMove = bytes.moveAt(moveSignalOffset + LAST_ENEMY_MOVE_DELTA, catalog)
        if (lastEnemyMove != null && lastEnemyMove !in enemy.moves) return null

        val layout = knownLayout ?: ResolvedBattleLayout(
            battleMonsOffset = playerCandidate.offset,
            battlerCountOffset = battleFlagOffset,
            battlerPositionsOffset = battleFlagOffset + BATTLE_TYPE_DELTA,
            outcomeOffset = moveSignalOffset + BATTLE_ENDED_DELTA,
            moveCursorOffset = moveSignalOffset,
            targetCursorOffset = moveSignalOffset + CURRENT_ENEMY_MOVE_DELTA,
            battlerCount = 2,
        )
        return BattleMemorySample(
            layout = layout,
            battlers = listOf(player, enemy),
            opponents = listOf(enemy),
            selectedMoveId = selectedMove,
            target = BattleTarget(0, TargetMode.AUTOMATIC),
            capabilities = mapOf(
                BattleCapability.BATTLE_LAYOUT to CapabilityState.AVAILABLE,
                BattleCapability.MULTIPLE_OPPONENTS to CapabilityState.NOT_APPLICABLE,
                BattleCapability.SELECTED_TARGET to CapabilityState.AVAILABLE,
                BattleCapability.SELECTED_MOVE to if (selectedMove != null) CapabilityState.AVAILABLE else CapabilityState.NOT_FOUND,
                BattleCapability.OPPONENT_IVS to CapabilityState.AVAILABLE,
                BattleCapability.OPPONENT_PP to CapabilityState.AVAILABLE,
            ),
            battleOutcome = bytes.u8OrZero(layout.outcomeOffset),
            playerExecutedMoveId = selectedMove,
            opponentExecutedMoveId = lastEnemyMove,
            encounterKind = classifyEncounter(
                bytes.u8(battleFlagOffset),
                bytes.u8OrZero(battleFlagOffset + BATTLE_TYPE_DELTA),
            ),
        )
    }

    private fun classifyEncounter(battleMode: Int, battleType: Int): BattleEncounterKind = when {
        battleType in SPECIAL_BATTLE_TYPES -> BattleEncounterKind.UNKNOWN
        battleMode == WILD_BATTLE -> BattleEncounterKind.WILD
        battleMode == TRAINER_BATTLE -> BattleEncounterKind.TRAINER
        else -> BattleEncounterKind.UNKNOWN
    }

    private fun findPlayerCandidates(
        bytes: ByteArray,
        battleFlagOffset: Int,
        enemy: BattleMonSnapshot,
        catalog: BattleCatalogView,
    ): List<PlayerCandidate> {
        val officialShapes = OFFICIAL_LAYOUT_SHAPES.mapNotNull { shape ->
            val offset = battleFlagOffset - shape.playerFlagDelta
            val player = readBattleMon(bytes, offset, battlerIndex = 0, position = 0, catalog) ?: return@mapNotNull null
            signalCandidate(bytes, offset, shape.signalDelta, player, enemy, catalog, requireSelectedMove = false)
        }.distinctBy { it.offset to it.moveSignalOffset }
        if (officialShapes.isNotEmpty()) return officialShapes

        return buildList {
            for (offset in 0..WRAM_BANK_BYTES - BATTLE_MON_BYTES) {
                val player = readBattleMon(bytes, offset, battlerIndex = 0, position = 0, catalog) ?: continue
                SIGNAL_DELTAS.forEach { signalDelta ->
                    signalCandidate(bytes, offset, signalDelta, player, enemy, catalog, requireSelectedMove = true)
                        ?.let(::add)
                }
            }
        }.distinctBy { it.offset to it.moveSignalOffset }
    }

    private fun signalCandidate(
        bytes: ByteArray,
        offset: Int,
        signalDelta: Int,
        player: BattleMonSnapshot,
        enemy: BattleMonSnapshot,
        catalog: BattleCatalogView,
        requireSelectedMove: Boolean,
    ): PlayerCandidate? {
        val signalOffset = offset + signalDelta
        if (signalOffset + BATTLE_ENDED_DELTA >= WRAM_BANK_BYTES) return null
        val selectedMove = bytes.moveAt(signalOffset, catalog)
        if (bytes.u8OrZero(signalOffset) != 0 && selectedMove == null) return null
        if (requireSelectedMove && selectedMove == null) return null
        if (selectedMove != null && selectedMove !in player.moves) return null
        val enemyMove = bytes.moveAt(signalOffset + CURRENT_ENEMY_MOVE_DELTA, catalog)
        if (bytes.u8OrZero(signalOffset + CURRENT_ENEMY_MOVE_DELTA) != 0 && enemyMove == null) return null
        if (enemyMove != null && enemyMove !in enemy.moves) return null
        val lastEnemyMove = bytes.moveAt(signalOffset + LAST_ENEMY_MOVE_DELTA, catalog)
        if (bytes.u8OrZero(signalOffset + LAST_ENEMY_MOVE_DELTA) != 0 && lastEnemyMove == null) return null
        if (lastEnemyMove != null && lastEnemyMove !in enemy.moves) return null
        return PlayerCandidate(offset, signalOffset, player)
    }

    private fun readBattleMon(
        bytes: ByteArray,
        offset: Int,
        battlerIndex: Int,
        position: Int,
        catalog: BattleCatalogView,
    ): BattleMonSnapshot? {
        if (offset < 0 || offset + BATTLE_MON_BYTES > bytes.size) return null
        val speciesId = bytes.u8(offset)
        catalog.species[speciesId] ?: return null
        val moves = List(MOVE_SLOTS) { bytes.u8(offset + MOVES_OFFSET + it) }
        val pp = List(MOVE_SLOTS) { bytes.u8(offset + PP_OFFSET + it) and PP_MASK }
        if (moves.none { it != 0 }) return null
        moves.indices.forEach { slot ->
            val moveId = moves[slot]
            if (moveId == 0) {
                if (pp[slot] != 0) return null
            } else {
                val move = catalog.moves[moveId] ?: return null
                if (pp[slot] !in 0..maximumPp(move.basePp)) return null
            }
        }
        val level = bytes.u8(offset + LEVEL_OFFSET)
        if (level !in 1..100) return null
        val hp = bytes.be16(offset + HP_OFFSET)
        val maxHp = bytes.be16(offset + MAX_HP_OFFSET)
        if (maxHp !in 1..999 || hp !in 0..maxHp) return null
        val stats = List(5) { bytes.be16(offset + STATS_OFFSET + it * 2) }
        if (stats.any { it !in 1..999 }) return null
        val typeIds = listOf(bytes.u8(offset + TYPE1_OFFSET), bytes.u8(offset + TYPE2_OFFSET))
        if (typeIds.any { it !in catalog.typeIds }) return null
        val attackDefense = bytes.u8(offset + DVS_OFFSET)
        val speedSpecial = bytes.u8(offset + DVS_OFFSET + 1)
        return BattleMonSnapshot(
            battlerIndex = battlerIndex,
            position = position,
            speciesId = speciesId,
            level = level,
            hp = hp,
            maxHp = maxHp,
            ivs = emptyList(),
            dvs = listOf(attackDefense ushr 4, attackDefense and 0xf, speedSpecial ushr 4, speedSpecial and 0xf),
            moves = moves,
            pp = pp,
            typeIds = typeIds,
            abilityId = 0,
            personality = 0,
        )
    }

    private fun maximumPp(basePp: Int): Int = basePp + (basePp * 3 / 5)

    private fun ByteArray.moveAt(offset: Int, catalog: BattleCatalogView): Int? =
        u8OrZero(offset).takeIf { it != 0 && it in catalog.moves }

    private fun ByteArray.u8(offset: Int): Int = this[offset].toInt() and 0xff
    private fun ByteArray.u8OrZero(offset: Int): Int = if (offset in indices) u8(offset) else 0
    private fun ByteArray.be16(offset: Int): Int = (u8(offset) shl 8) or u8(offset + 1)

    private data class PlayerCandidate(
        val offset: Int,
        val moveSignalOffset: Int,
        val mon: BattleMonSnapshot,
    )

    private data class LayoutShape(
        val playerFlagDelta: Int,
        val signalDelta: Int,
    )

    companion object {
        private const val WRAM_BYTES = 0x2000
        private const val WRAM_BANK_BYTES = 0x1000
        private const val WILD_BATTLE = 1
        private const val TRAINER_BATTLE = 2
        private const val MIN_BATTLE_TYPE = 0
        private const val MAX_BATTLE_TYPE = 12
        private val SPECIAL_BATTLE_TYPES = setOf(2, 3, 6) // debug, tutorial, bug contest
        private const val ENEMY_DELTA = 0x27
        private const val BATTLE_TYPE_DELTA = 3
        private val OFFICIAL_LAYOUT_SHAPES = listOf(
            LayoutShape(playerFlagDelta = 0x0c01, signalDelta = 0x0b7), // Crystal
            LayoutShape(playerFlagDelta = 0x060a, signalDelta = 0x0b5), // Gold/Silver
        )
        private val SIGNAL_DELTAS = intArrayOf(0x0b5, 0x0b7)
        private const val CURRENT_ENEMY_MOVE_DELTA = 1
        private const val LAST_ENEMY_MOVE_DELTA = 0x39
        private const val BATTLE_ENDED_DELTA = 0x51
        private const val BATTLE_MON_BYTES = 32
        private const val MOVE_SLOTS = 4
        private const val MOVES_OFFSET = 2
        private const val DVS_OFFSET = 6
        private const val PP_OFFSET = 8
        private const val LEVEL_OFFSET = 13
        private const val HP_OFFSET = 16
        private const val MAX_HP_OFFSET = 18
        private const val STATS_OFFSET = 20
        private const val TYPE1_OFFSET = 30
        private const val TYPE2_OFFSET = 31
        private const val PP_MASK = 0x3f
    }
}
