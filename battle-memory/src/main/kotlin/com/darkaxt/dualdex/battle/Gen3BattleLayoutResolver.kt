package com.darkaxt.dualdex.battle

class Gen3BattleLayoutResolver {
    fun resolve(ewram: ByteArray, catalog: BattleCatalogView): LayoutResolution {
        if (catalog.species.isEmpty() || catalog.moves.isEmpty() || catalog.typeIds.isEmpty()) return LayoutResolution.NotFound
        val candidates = buildList {
            var anchor = COUNT_DELTA
            val lastAnchor = ewram.size - REQUIRED_TAIL_BYTES
            while (anchor <= lastAnchor) {
                decodeCandidate(ewram, anchor, catalog)?.let(::add)
                anchor += ALIGNMENT
            }
        }
        return when (candidates.size) {
            0 -> LayoutResolution.NotFound
            1 -> LayoutResolution.Resolved(candidates.single())
            else -> LayoutResolution.Ambiguous(candidates.size)
        }
    }

    fun resolveKnown(ewram: ByteArray, layout: ResolvedBattleLayout, catalog: BattleCatalogView): BattleMemorySample? =
        decodeCandidate(ewram, layout.battleMonsOffset, catalog)

    private fun decodeCandidate(bytes: ByteArray, anchor: Int, catalog: BattleCatalogView): BattleMemorySample? {
        val countOffset = anchor - COUNT_DELTA
        val positionsOffset = anchor - POSITIONS_DELTA
        val outcomeOffset = anchor + OUTCOME_DELTA
        val moveCursorOffset = anchor + MOVE_CURSOR_DELTA
        val targetCursorOffset = anchor + TARGET_CURSOR_DELTA
        if (countOffset !in bytes.indices || targetCursorOffset + MAX_BATTLERS > bytes.size) return null

        val count = bytes.u8(countOffset)
        if (count != 2 && count != 4) return null
        if (anchor + count * RECORD_SIZE > bytes.size) return null
        val battleOutcome = bytes.u8(outcomeOffset)

        val positions = (0 until count).map { bytes.u8(positionsOffset + it) }
        if (positions.any { it !in 0 until MAX_BATTLERS } || positions.distinct().size != count) return null
        if (positions.count { it and 1 == 0 } != count / 2 || positions.count { it and 1 == 1 } != count / 2) return null

        val battlers = (0 until count).map { battlerIndex ->
            decodeMon(bytes, anchor + battlerIndex * RECORD_SIZE, battlerIndex, positions[battlerIndex], catalog)
                ?: return null
        }
        val opponents = battlers.filter { it.position and 1 == 1 }
        val players = battlers.filter { it.position and 1 == 0 }
        if (opponents.size != count / 2 || players.size != count / 2) return null

        val commandOwner = players.singleOrNull()
        val selectedMove = commandOwner?.let { player ->
            val slot = bytes.u8(moveCursorOffset + player.battlerIndex)
            player.moves.getOrNull(slot)?.takeIf { it != 0 }
        }
        val target = resolveTarget(opponents)
        val layout = ResolvedBattleLayout(
            battleMonsOffset = anchor,
            battlerCountOffset = countOffset,
            battlerPositionsOffset = positionsOffset,
            outcomeOffset = outcomeOffset,
            moveCursorOffset = moveCursorOffset,
            targetCursorOffset = targetCursorOffset,
            battlerCount = count,
        )
        return BattleMemorySample(
            layout = layout,
            battlers = battlers,
            opponents = opponents,
            selectedMoveId = selectedMove,
            target = target,
            capabilities = mapOf(
                BattleCapability.BATTLE_LAYOUT to CapabilityState.AVAILABLE,
                BattleCapability.MULTIPLE_OPPONENTS to if (opponents.size > 1) CapabilityState.AVAILABLE else CapabilityState.NOT_APPLICABLE,
                BattleCapability.SELECTED_TARGET to if (target.mode == TargetMode.AUTOMATIC) CapabilityState.AVAILABLE else CapabilityState.NOT_FOUND,
                BattleCapability.SELECTED_MOVE to if (selectedMove != null) CapabilityState.AVAILABLE else CapabilityState.NOT_FOUND,
                BattleCapability.OPPONENT_IVS to CapabilityState.AVAILABLE,
                BattleCapability.OPPONENT_PP to CapabilityState.AVAILABLE,
            ),
            battleOutcome = battleOutcome,
            commandOwnerBattlerIndex = commandOwner?.battlerIndex,
        )
    }

    private fun decodeMon(
        bytes: ByteArray,
        offset: Int,
        battlerIndex: Int,
        position: Int,
        catalog: BattleCatalogView,
    ): BattleMonSnapshot? {
        if (offset < 0 || offset + RECORD_SIZE > bytes.size) return null
        val speciesId = bytes.u16(offset)
        val species = catalog.species[speciesId] ?: return null
        val stats = (0 until 5).map { bytes.u16(offset + 2 + it * 2) }
        if (stats.any { it !in 1..4095 }) return null
        val moves = (0 until MOVE_SLOTS).map { bytes.u16(offset + MOVES_OFFSET + it * 2) }
        if (moves.none { it != 0 } || moves.any { it != 0 && it !in catalog.moves }) return null
        val hp = bytes.u16(offset + HP_OFFSET)
        val maxHp = bytes.u16(offset + MAX_HP_OFFSET)
        if (maxHp !in 1..4095 || hp !in 0..maxHp) return null
        val ivBits = bytes.u32(offset + IVS_OFFSET)
        val ivs = (0 until 6).map { ((ivBits ushr (it * 5)) and 0x1F).toInt() }
        if ((0 until 8).any { bytes.u8(offset + STAT_STAGES_OFFSET + it) !in 0..12 }) return null
        val abilityId = bytes.u8(offset + ABILITY_OFFSET)
        if (species.abilityIds.isNotEmpty() && abilityId !in species.abilityIds && !(hp == 0 && abilityId == 0)) return null
        val typeIds = listOf(bytes.u8(offset + TYPE1_OFFSET), bytes.u8(offset + TYPE2_OFFSET))
        if (typeIds.any { it !in catalog.typeIds }) return null
        if (species.typeIds.isNotEmpty() && typeIds.none { it in species.typeIds }) return null
        val pp = (0 until MOVE_SLOTS).map { bytes.u8(offset + PP_OFFSET + it) }
        if (moves.indices.any { slot ->
                val move = moves[slot]
                val currentPp = pp[slot]
                move == 0 && currentPp != 0 || move != 0 && currentPp > requireNotNull(catalog.moves[move]).basePp * 8 / 5
            }) return null
        val level = bytes.u8(offset + LEVEL_OFFSET)
        if (level !in 1..100) return null

        return BattleMonSnapshot(
            battlerIndex = battlerIndex,
            position = position,
            speciesId = speciesId,
            level = level,
            hp = hp,
            maxHp = maxHp,
            ivs = ivs,
            moves = moves,
            pp = pp,
            typeIds = typeIds,
            abilityId = abilityId,
            personality = bytes.u32(offset + PERSONALITY_OFFSET),
        )
    }

    private fun resolveTarget(opponents: List<BattleMonSnapshot>): BattleTarget {
        if (opponents.size == 1) return BattleTarget(0, TargetMode.AUTOMATIC)
        return BattleTarget(0, TargetMode.MANUAL_TARGET_FALLBACK)
    }

    private fun ByteArray.u8(offset: Int): Int = this[offset].toInt() and 0xFF

    private fun ByteArray.u16(offset: Int): Int = u8(offset) or (u8(offset + 1) shl 8)

    private fun ByteArray.u32(offset: Int): Long =
        u8(offset).toLong() or
            (u8(offset + 1).toLong() shl 8) or
            (u8(offset + 2).toLong() shl 16) or
            (u8(offset + 3).toLong() shl 24)

    companion object {
        const val RECORD_SIZE = 0x58
        private const val MAX_BATTLERS = 4
        private const val MOVE_SLOTS = 4
        private const val ALIGNMENT = 4
        private const val COUNT_DELTA = 0x1C
        private const val POSITIONS_DELTA = 0x10
        private const val OUTCOME_DELTA = 0x2B2
        private const val MOVE_CURSOR_DELTA = 0x438
        private const val TARGET_CURSOR_DELTA = 0x43C
        private const val REQUIRED_TAIL_BYTES = TARGET_CURSOR_DELTA + MAX_BATTLERS
        private const val MOVES_OFFSET = 0x0C
        private const val IVS_OFFSET = 0x14
        private const val STAT_STAGES_OFFSET = 0x18
        private const val ABILITY_OFFSET = 0x20
        private const val TYPE1_OFFSET = 0x21
        private const val TYPE2_OFFSET = 0x22
        private const val PP_OFFSET = 0x24
        private const val HP_OFFSET = 0x28
        private const val LEVEL_OFFSET = 0x2A
        private const val MAX_HP_OFFSET = 0x2C
        private const val PERSONALITY_OFFSET = 0x48
    }
}
