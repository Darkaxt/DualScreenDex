package com.darkaxt.dualdex.battle

import com.darkaxt.dualdex.save.BagPocket
import com.darkaxt.dualdex.save.BagPocketSnapshot
import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.TrainerIdentity
import com.darkaxt.dualdex.save.TrainerPlayTime

enum class LiveUnavailableCode {
    UNSUPPORTED_LAYOUT,
    MISSING_REGION,
    INVALID_POINTER,
    INVALID_VALUE,
    AMBIGUOUS_LAYOUT,
}

data class LiveUnavailableReason(
    val code: LiveUnavailableCode,
    val detail: String,
) {
    init {
        require(detail.isNotBlank()) { "unavailable detail must not be blank" }
    }
}

sealed interface LiveValue<out T> {
    data class Available<T>(val value: T) : LiveValue<T>
    data class Unavailable(val reason: LiveUnavailableReason) : LiveValue<Nothing>
}

fun <T> LiveValue<T>.valueOrNull(): T? = (this as? LiveValue.Available<T>)?.value

data class LiveTrainerState(
    val identity: LiveValue<TrainerIdentity>,
    val publicTrainerId: LiveValue<Int>,
    val money: LiveValue<Long>,
    val playTime: LiveValue<TrainerPlayTime>,
    val badgeFlags: LiveValue<Int>,
    val stars: LiveValue<Int>,
) {
    init {
        publicTrainerId.valueOrNull()?.let {
            require(it in 0..0xFFFF) { "public trainer ID must fit in 16 bits" }
        }
        money.valueOrNull()?.let {
            require(it >= 0) { "money must not be negative" }
        }
        badgeFlags.valueOrNull()?.let {
            require(it in 0..0xFF) { "badge flags must fit in 8 bits" }
        }
        stars.valueOrNull()?.let {
            require(it >= 0) { "Trainer Card stars must not be negative" }
        }
    }
}

data class LivePokedexState(
    val seenDexNumbers: LiveValue<Set<Int>>,
    val caughtDexNumbers: LiveValue<Set<Int>>,
    val ownedFlagOffset: Int? = null,
) {
    init {
        seenDexNumbers.valueOrNull()?.let { seen ->
            require(seen.all { it > 0 }) { "seen Pokédex numbers must be positive" }
        }
        caughtDexNumbers.valueOrNull()?.let { caught ->
            require(caught.all { it > 0 }) { "caught Pokédex numbers must be positive" }
        }
        val seen = seenDexNumbers.valueOrNull()
        val caught = caughtDexNumbers.valueOrNull()
        if (seen != null && caught != null) {
            require(seen.containsAll(caught)) { "caught Pokédex numbers must be a subset of seen" }
        }
    }
}

data class LiveLocationState(
    val areaBaseId: LiveValue<Int>,
    val position: LiveValue<RuntimeMapPosition>,
) {
    init {
        areaBaseId.valueOrNull()?.let {
            require(it in 0..0xFFFF) { "area base ID must fit in 16 bits" }
        }
    }
}

enum class LiveClockPhase { MORNING, DAY, NIGHT, DARK }

data class LiveClockState(
    val hours: Int? = null,
    val minutes: Int? = null,
    val seconds: Int? = null,
    val phase: LiveClockPhase? = null,
) {
    init {
        require((hours == null) == (minutes == null) && (hours == null) == (seconds == null)) {
            "numeric clock fields must be all available or all unavailable"
        }
        require(hours != null || phase != null) { "clock must contain numeric time or a validated phase" }
        hours?.let { require(it in 0..23) { "clock hours must be in 0..23" } }
        minutes?.let { require(it in 0..59) { "clock minutes must be in 0..59" } }
        seconds?.let { require(it in 0..59) { "clock seconds must be in 0..59" } }
    }
}

data class LiveBattleState(
    val active: Boolean,
    val sample: BattleMemorySample?,
    val encounterKind: BattleEncounterKind,
)

data class LiveGameSnapshot(
    val romIdentity: String,
    val generation: Int,
    val sampleId: Long,
    val trainer: LiveTrainerState,
    val pokedex: LivePokedexState,
    val party: LiveValue<List<OwnedIndividual>>,
    val storedIndividuals: LiveValue<List<OwnedIndividual>> = LiveValue.Unavailable(
        LiveUnavailableReason(
            LiveUnavailableCode.UNSUPPORTED_LAYOUT,
            "live owned storage layout was unavailable",
        ),
    ),
    val battle: LiveValue<LiveBattleState>,
    val location: LiveLocationState,
    val clock: LiveValue<LiveClockState>,
    val bag: Map<BagPocket, LiveValue<BagPocketSnapshot>>,
    val eventFlags: LiveValue<Set<Int>>,
) {
    init {
        require(romIdentity.isNotBlank()) { "ROM identity must not be blank" }
        require(generation in 1..3) { "generation must be in 1..3" }
        require(sampleId >= 0) { "sample ID must not be negative" }
        eventFlags.valueOrNull()?.let { flags ->
            require(flags.all { it >= 0 }) { "event flag IDs must not be negative" }
        }
    }
}
