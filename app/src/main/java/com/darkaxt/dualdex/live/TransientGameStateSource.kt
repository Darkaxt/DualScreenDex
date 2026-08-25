package com.darkaxt.dualdex.live

import com.darkaxt.dualdex.battle.BattleCatalogView
import com.darkaxt.dualdex.battle.Gen3RuntimeMemoryLayout
import com.darkaxt.dualdex.battle.LiveAreaMemoryLayout
import com.darkaxt.dualdex.battle.LiveBattleState
import com.darkaxt.dualdex.battle.LiveClockState
import com.darkaxt.dualdex.battle.RuntimeMapPosition
import com.darkaxt.dualdex.save.BagPocket
import com.darkaxt.dualdex.save.BagPocketSnapshot
import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.SaveObservationKind
import com.darkaxt.dualdex.save.TrainerIdentity
import com.darkaxt.dualdex.save.TrainerPlayTime
import com.enrpau.dualscreendex.companion.api.SaveRamView
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger

fun interface TransientGameStateListener {
    fun onStateChanged(update: ResolvedGameStateUpdate)
}

interface TransientGameStateSource {
    val current: ResolvedGameSnapshot?

    fun subscribe(listener: TransientGameStateListener): AutoCloseable

    fun performanceCounters(): Map<String, Long> = emptyMap()
}

enum class ResolvedGameSection {
    RECOVERY,
    PLAYER,
    PARTY,
    OVERWORLD,
    BATTLE,
}

data class ResolvedGameStateUpdate(
    val snapshot: ResolvedGameSnapshot?,
    val changedSections: Set<ResolvedGameSection>,
)

enum class ResolvedValueSource { LIVE, RECOVERY, UNAVAILABLE }

data class ResolvedValue<T>(
    val value: T?,
    val source: ResolvedValueSource,
) {
    init {
        require((source == ResolvedValueSource.UNAVAILABLE) == (value == null)) {
            "unavailable values must be null and available values must be non-null"
        }
    }

    companion object {
        fun <T> live(value: T) = ResolvedValue(value, ResolvedValueSource.LIVE)
        fun <T> recovery(value: T) = ResolvedValue(value, ResolvedValueSource.RECOVERY)
        fun <T> unavailable() = ResolvedValue<T>(null, ResolvedValueSource.UNAVAILABLE)
    }
}

data class ResolvedTrainerState(
    val identity: ResolvedValue<TrainerIdentity>,
    val publicTrainerId: ResolvedValue<Int>,
    val money: ResolvedValue<Long>,
    val playTime: ResolvedValue<TrainerPlayTime>,
    val badgeFlags: ResolvedValue<Int>,
    val stars: ResolvedValue<Int>,
)

data class ResolvedPokedexState(
    val seenSpeciesIds: ResolvedValue<Set<Int>>,
    val caughtSpeciesIds: ResolvedValue<Set<Int>>,
)

data class ResolvedLocationState(
    val areaBaseId: ResolvedValue<Int>,
    val position: ResolvedValue<RuntimeMapPosition>,
)

data class RecoveryState(
    val applicationId: Long? = null,
    val saveIdentity: String? = null,
    val saveRam: SaveRamView? = null,
    val observationKind: SaveObservationKind? = null,
    val checkpointLedger: KnowledgeLedger? = null,
    val resetKnowledge: Boolean = false,
)

data class ResolvedGameSnapshot(
    val romIdentity: String,
    val generation: Int,
    val sampleId: Long?,
    val trainer: ResolvedTrainerState,
    val pokedex: ResolvedPokedexState,
    val party: ResolvedValue<List<OwnedIndividual>>,
    val storedIndividuals: ResolvedValue<List<OwnedIndividual>>,
    val battle: ResolvedValue<LiveBattleState>,
    val location: ResolvedLocationState,
    val clock: ResolvedValue<LiveClockState>,
    val bag: Map<BagPocket, ResolvedValue<BagPocketSnapshot>>,
    val eventFlags: ResolvedValue<Set<Int>>,
    val levelUpRulesetId: ResolvedValue<String>,
    val recovery: RecoveryState,
)

data class TransientGameStateContext(
    val romIdentity: String,
    val generation: Int,
    val catalog: BattleCatalogView,
    val gen2TimeOfDayWramOffset: Int? = null,
    val gen3RuntimeMemoryLayout: Gen3RuntimeMemoryLayout? = null,
    val liveAreaMemoryLayout: LiveAreaMemoryLayout? = null,
    val saveParseContext: SaveParseContext? = null,
) {
    init {
        require(romIdentity.isNotBlank()) { "ROM identity must not be blank" }
        require(generation in 1..3) { "generation must be in 1..3" }
        require(gen2TimeOfDayWramOffset == null || gen2TimeOfDayWramOffset in 0 until 0x2000)
    }
}

fun ResolvedGameSnapshot.gameAccessReady(): Boolean = when (generation) {
    3 -> location.areaBaseId.source == ResolvedValueSource.LIVE &&
        trainer.identity.source == ResolvedValueSource.LIVE &&
        clock.source == ResolvedValueSource.LIVE &&
        clock.value?.let { value ->
            val hours = value.hours ?: return@let false
            val minutes = value.minutes ?: return@let false
            val seconds = value.seconds ?: return@let false
            hours != 0 || minutes != 0 || seconds != 0
        } == true
    1, 2 -> location.areaBaseId.source == ResolvedValueSource.LIVE
    else -> false
}
