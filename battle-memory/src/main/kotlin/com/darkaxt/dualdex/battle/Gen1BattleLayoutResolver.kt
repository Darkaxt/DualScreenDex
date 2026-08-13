package com.darkaxt.dualdex.battle

/**
 * Shape-based Red/Blue/Yellow resolver. The official Yellow layout is one byte before Red/Blue,
 * while the battle flag remains exactly 0x72 bytes after the enemy battle struct and 0x43 bytes
 * after the player battle struct. Scanning that relationship keeps derived ROMs profile-free.
 */
class Gen1BattleLayoutResolver {
    fun resolve(bytes: ByteArray, catalog: BattleCatalogView): LayoutResolution {
        if (bytes.size < WRAM_BYTES || catalog.species.isEmpty() || catalog.moves.isEmpty()) {
            return LayoutResolution.NotFound
        }
        val candidates = buildList {
            for (battleFlagOffset in MIN_BATTLE_FLAG_OFFSET until bytes.size - PARTY_COUNT_DELTA) {
                val inBattle = bytes.u8(battleFlagOffset)
                if (inBattle !in WILD_BATTLE..TRAINER_BATTLE) continue
                resolveAt(bytes, battleFlagOffset, catalog)?.let(::add)
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
    ): BattleMemorySample? = resolveAt(
        bytes = wram,
        battleFlagOffset = layout.battlerCountOffset,
        catalog = catalog,
        knownLayout = layout,
        validatePartyCount = false,
    )

    private fun resolveAt(
        bytes: ByteArray,
        battleFlagOffset: Int,
        catalog: BattleCatalogView,
        knownLayout: ResolvedBattleLayout? = null,
        validatePartyCount: Boolean = true,
    ): BattleMemorySample? {
        if (battleFlagOffset !in bytes.indices || bytes.u8(battleFlagOffset) !in WILD_BATTLE..TRAINER_BATTLE) return null
        val enemyOffset = battleFlagOffset - ENEMY_DELTA
        val playerOffset = battleFlagOffset - PLAYER_DELTA
        val battleType = bytes.u8(battleFlagOffset + BATTLE_TYPE_DELTA)
        if (battleType !in NORMAL_BATTLE..PIKACHU_BATTLE) return null
        if (validatePartyCount && bytes.u8(battleFlagOffset + PARTY_COUNT_DELTA) !in 0..6) return null

        val enemy = readBattleMon(bytes, enemyOffset, battlerIndex = 1, position = 1, catalog) ?: return null
        val player = readBattleMon(bytes, playerOffset, battlerIndex = 0, position = 0, catalog)
        if (player == null && battleType != PIKACHU_BATTLE) return null

        val playerSelectedMoveOffset = knownLayout?.moveCursorOffset ?: PLAYER_SELECTED_MOVE_OFFSET
        val enemySelectedMoveOffset = knownLayout?.targetCursorOffset ?: ENEMY_SELECTED_MOVE_OFFSET
        val selectedMove = bytes.moveAt(playerSelectedMoveOffset, catalog)
        val playerExecuted = bytes.moveAt(playerSelectedMoveOffset + USED_MOVE_DELTA, catalog)
        val enemyExecuted = bytes.moveAt(enemySelectedMoveOffset + USED_MOVE_DELTA, catalog)
        val layout = knownLayout ?: ResolvedBattleLayout(
            battleMonsOffset = enemyOffset,
            battlerCountOffset = battleFlagOffset,
            battlerPositionsOffset = battleFlagOffset + PARTY_COUNT_DELTA,
            outcomeOffset = battleFlagOffset - BATTLE_RESULT_DELTA,
            moveCursorOffset = PLAYER_SELECTED_MOVE_OFFSET,
            targetCursorOffset = ENEMY_SELECTED_MOVE_OFFSET,
            battlerCount = if (player == null) 1 else 2,
        )
        return BattleMemorySample(
            layout = layout,
            battlers = listOfNotNull(player, enemy),
            opponents = listOf(enemy),
            selectedMoveId = selectedMove,
            target = BattleTarget(0, TargetMode.AUTOMATIC),
            capabilities = mapOf(
                BattleCapability.BATTLE_LAYOUT to CapabilityState.AVAILABLE,
                BattleCapability.MULTIPLE_OPPONENTS to CapabilityState.NOT_APPLICABLE,
                BattleCapability.SELECTED_TARGET to CapabilityState.AVAILABLE,
                BattleCapability.SELECTED_MOVE to CapabilityState.AVAILABLE,
                BattleCapability.OPPONENT_IVS to CapabilityState.AVAILABLE,
                BattleCapability.OPPONENT_PP to CapabilityState.AVAILABLE,
            ),
            battleOutcome = bytes.u8OrZero(layout.outcomeOffset),
            playerExecutedMoveId = playerExecuted,
            opponentExecutedMoveId = enemyExecuted,
            encounterKind = classifyEncounter(bytes.u8(battleFlagOffset), battleType),
        )
    }

    private fun classifyEncounter(battleMode: Int, battleType: Int): BattleEncounterKind = when {
        battleType !in setOf(NORMAL_BATTLE, SAFARI_BATTLE) -> BattleEncounterKind.UNKNOWN
        battleMode == WILD_BATTLE -> BattleEncounterKind.WILD
        battleMode == TRAINER_BATTLE -> BattleEncounterKind.TRAINER
        else -> BattleEncounterKind.UNKNOWN
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
        val species = catalog.species[speciesId] ?: return null
        val level = bytes.u8(offset + LEVEL_OFFSET)
        if (level !in 1..100) return null
        val hp = bytes.be16(offset + HP_OFFSET)
        val maxHp = bytes.be16(offset + MAX_HP_OFFSET)
        if (maxHp !in 1..999 || hp !in 0..maxHp) return null
        val typeIds = listOf(bytes.u8(offset + TYPE1_OFFSET), bytes.u8(offset + TYPE2_OFFSET))
        if (typeIds.any { it !in catalog.typeIds }) return null
        if (species.typeIds.isNotEmpty() && typeIds.none { it in species.typeIds }) return null
        val moves = List(4) { bytes.u8(offset + MOVES_OFFSET + it) }
        val pp = List(4) { bytes.u8(offset + PP_OFFSET + it) and PP_MASK }
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

    companion object {
        private const val WRAM_BYTES = 0x2000
        private const val MIN_BATTLE_FLAG_OFFSET = 0x100
        private const val WILD_BATTLE = 1
        private const val TRAINER_BATTLE = 2
        private const val NORMAL_BATTLE = 0
        private const val PIKACHU_BATTLE = 4
        private const val SAFARI_BATTLE = 2
        private const val ENEMY_DELTA = 0x72
        private const val PLAYER_DELTA = 0x43
        private const val BATTLE_TYPE_DELTA = 3
        private const val PARTY_COUNT_DELTA = 0x10c
        private const val BATTLE_RESULT_DELTA = 0x14b
        private const val PLAYER_SELECTED_MOVE_OFFSET = 0x0cdc
        private const val ENEMY_SELECTED_MOVE_OFFSET = 0x0cdd
        private const val PLAYER_USED_MOVE_OFFSET = 0x0cf1
        private const val USED_MOVE_DELTA = PLAYER_USED_MOVE_OFFSET - PLAYER_SELECTED_MOVE_OFFSET
        private const val BATTLE_MON_BYTES = 29
        private const val HP_OFFSET = 1
        private const val TYPE1_OFFSET = 5
        private const val TYPE2_OFFSET = 6
        private const val MOVES_OFFSET = 8
        private const val DVS_OFFSET = 12
        private const val LEVEL_OFFSET = 14
        private const val MAX_HP_OFFSET = 15
        private const val PP_OFFSET = 25
        private const val PP_MASK = 0x3f
    }
}
