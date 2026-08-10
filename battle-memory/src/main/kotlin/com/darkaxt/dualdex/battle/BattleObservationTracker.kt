package com.darkaxt.dualdex.battle

data class BattleTrackingUpdate(
    val active: Boolean,
    val sample: BattleMemorySample?,
    val observations: Map<Int, Map<Int, Int>> = emptyMap(),
    val ended: Boolean = false,
)

class BattleObservationTracker(
    private val validatedNonBattleSamplesToClose: Int = 2,
) {
    private var romIdentity: String? = null
    private var lastSample: BattleMemorySample? = null
    private var validatedNonBattleSamples = 0
    private val baselines = mutableMapOf<Int, OpponentBaseline>()

    init {
        require(validatedNonBattleSamplesToClose > 0) { "battle close sample count must be positive" }
    }

    fun update(romIdentity: String, sample: BattleMemorySample): BattleTrackingUpdate {
        require(romIdentity.isNotBlank()) { "ROM identity is required" }
        if (this.romIdentity != romIdentity) reset(romIdentity)
        validatedNonBattleSamples = 0

        val increments = mutableMapOf<Int, MutableMap<Int, Int>>()
        val activeBattlerIndexes = sample.opponents.mapTo(mutableSetOf()) { it.battlerIndex }
        baselines.keys.retainAll(activeBattlerIndexes)
        sample.opponents.forEach { opponent ->
            val identity = OpponentIdentity(opponent.position, opponent.speciesId, opponent.personality)
            val previous = baselines[opponent.battlerIndex]
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
            baselines[opponent.battlerIndex] = OpponentBaseline(identity, opponent.moves.toList(), opponent.pp.toList())
        }
        lastSample = sample
        return BattleTrackingUpdate(
            active = true,
            sample = sample,
            observations = increments.mapValues { it.value.toMap() },
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
        baselines.clear()
        validatedNonBattleSamples = 0
        return BattleTrackingUpdate(active = false, sample = null, ended = true)
    }

    fun reset(romIdentity: String? = null) {
        this.romIdentity = romIdentity
        lastSample = null
        validatedNonBattleSamples = 0
        baselines.clear()
    }

    private data class OpponentIdentity(
        val position: Int,
        val speciesId: Int,
        val personality: Long,
    )

    private data class OpponentBaseline(
        val identity: OpponentIdentity,
        val moves: List<Int>,
        val pp: List<Int>,
    )
}
