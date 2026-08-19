package com.darkaxt.dualdex.battle

data class BattleMatchupObservation(
    val speciesId: Int,
    val moveId: Int,
    val defendingTypeIds: List<Int>,
)

data class BattleTrackingUpdate(
    val active: Boolean,
    val sample: BattleMemorySample?,
    val observations: Map<Int, Map<Int, Int>> = emptyMap(),
    val discoveredMatchups: Set<BattleMatchupObservation> = emptySet(),
    val ended: Boolean = false,
)

class BattleObservationTracker(
    private val validatedNonBattleSamplesToClose: Int = 2,
) {
    private var romIdentity: String? = null
    private var lastSample: BattleMemorySample? = null
    private var validatedNonBattleSamples = 0
    private val opponentBaselines = mutableMapOf<Int, BattlerBaseline>()
    private val playerBaselines = mutableMapOf<Int, BattlerBaseline>()
    private var opponentExecutedLatch: ExecutedMoveLatch? = null

    init {
        require(validatedNonBattleSamplesToClose > 0) { "battle close sample count must be positive" }
    }

    fun update(romIdentity: String, sample: BattleMemorySample): BattleTrackingUpdate {
        require(romIdentity.isNotBlank()) { "ROM identity is required" }
        if (this.romIdentity != romIdentity) reset(romIdentity)
        validatedNonBattleSamples = 0
        val priorSample = lastSample

        val increments = mutableMapOf<Int, MutableMap<Int, Int>>()
        val activeBattlerIndexes = sample.opponents.mapTo(mutableSetOf()) { it.battlerIndex }
        opponentBaselines.keys.retainAll(activeBattlerIndexes)
        sample.opponents.forEach { opponent ->
            val identity = BattlerIdentity(opponent.position, opponent.speciesId, opponent.personality)
            val previous = opponentBaselines[opponent.battlerIndex]
            if (previous?.identity == identity) {
                opponent.moves.indices.forEach { slot ->
                    val moveId = opponent.moves[slot]
                    val oldMoveId = previous.moves.getOrNull(slot)
                    val oldPp = previous.pp.getOrNull(slot)
                    val currentPp = opponent.pp.getOrNull(slot)
                    if (moveId != 0 && moveId == oldMoveId && oldPp != null && currentPp != null && currentPp < oldPp) {
                        increments.getOrPut(opponent.speciesId, ::mutableMapOf)
                            .merge(moveId, oldPp - currentPp, Int::plus)
                    }
                }
            }
            opponentBaselines[opponent.battlerIndex] = BattlerBaseline(identity, opponent.moves.toList(), opponent.pp.toList())
        }

        val executedMoveId = sample.opponentExecutedMoveId
        val executedTarget = sample.opponents.getOrNull(sample.target.opponentIndex)
        if (executedMoveId != null && executedTarget != null) {
            val identity = BattlerIdentity(executedTarget.position, executedTarget.speciesId, executedTarget.personality)
            val currentLatch = ExecutedMoveLatch(identity, executedMoveId)
            if (opponentExecutedLatch != currentLatch) {
                increments.getOrPut(executedTarget.speciesId, ::mutableMapOf).putIfAbsent(executedMoveId, 1)
            }
            opponentExecutedLatch = currentLatch
        } else {
            opponentExecutedLatch = null
        }

        val discoveredMatchups = mutableSetOf<BattleMatchupObservation>()
        val players = sample.battlers.filter { it.position and 1 == 0 }
        playerBaselines.keys.retainAll(players.mapTo(mutableSetOf()) { it.battlerIndex })
        players.forEach { player ->
            val identity = BattlerIdentity(player.position, player.speciesId, player.personality)
            val previous = playerBaselines[player.battlerIndex]
            if (previous?.identity == identity) {
                player.moves.indices.forEach { slot ->
                    val moveId = player.moves[slot]
                    val oldMoveId = previous.moves.getOrNull(slot)
                    val oldPp = previous.pp.getOrNull(slot)
                    val currentPp = player.pp.getOrNull(slot)
                    if (moveId != 0 && moveId == oldMoveId && oldPp != null && currentPp != null && currentPp < oldPp) {
                        val commandSample = priorSample
                            ?.takeIf { prior ->
                                val priorPlayers = prior.battlers.filter { it.position and 1 == 0 }
                                val ownsCommand = prior.commandOwnerBattlerIndex == player.battlerIndex ||
                                    (priorPlayers.size == 1 && priorPlayers.single().battlerIndex == player.battlerIndex)
                                ownsCommand && prior.selectedMoveId == moveId
                            }
                            ?.takeIf { it.target.mode == TargetMode.AUTOMATIC }
                        val target = commandSample?.opponents?.getOrNull(commandSample.target.opponentIndex)
                        if (target != null) {
                            discoveredMatchups += BattleMatchupObservation(
                                target.speciesId,
                                moveId,
                                target.typeIds.distinct(),
                            )
                        }
                    }
                }
            }
            playerBaselines[player.battlerIndex] = BattlerBaseline(identity, player.moves.toList(), player.pp.toList())
        }
        lastSample = sample
        return BattleTrackingUpdate(
            active = true,
            sample = sample,
            observations = increments.mapValues { it.value.toMap() },
            discoveredMatchups = discoveredMatchups,
        )
    }

    fun missed(): BattleTrackingUpdate = BattleTrackingUpdate(
        active = lastSample != null,
        sample = lastSample,
    )

    fun validatedNoBattle(romIdentity: String): BattleTrackingUpdate {
        if (this.romIdentity != romIdentity) {
            reset(romIdentity)
            return BattleTrackingUpdate(active = false, sample = null)
        }
        val prior = lastSample
        if (prior == null) return BattleTrackingUpdate(active = false, sample = null)
        validatedNonBattleSamples++
        if (validatedNonBattleSamples < validatedNonBattleSamplesToClose) {
            return BattleTrackingUpdate(active = true, sample = prior)
        }
        lastSample = null
        opponentBaselines.clear()
        playerBaselines.clear()
        opponentExecutedLatch = null
        validatedNonBattleSamples = 0
        return BattleTrackingUpdate(active = false, sample = null, ended = true)
    }

    fun reset(romIdentity: String? = null) {
        this.romIdentity = romIdentity
        lastSample = null
        validatedNonBattleSamples = 0
        opponentBaselines.clear()
        playerBaselines.clear()
        opponentExecutedLatch = null
    }

    private data class BattlerIdentity(
        val position: Int,
        val speciesId: Int,
        val personality: Long,
    )

    private data class BattlerBaseline(
        val identity: BattlerIdentity,
        val moves: List<Int>,
        val pp: List<Int>,
    )

    private data class ExecutedMoveLatch(
        val identity: BattlerIdentity,
        val moveId: Int,
    )
}
