package com.enrpau.dualscreendex.companion.owned

import com.enrpau.dualscreendex.companion.model.OwnedPokemon
import com.enrpau.dualscreendex.parser.catalog.CaptureBallRecord
import kotlin.math.roundToInt

object PreferredIndividualSelector {
    fun select(individuals: Iterable<OwnedPokemon>): OwnedPokemon? = individuals.filterNot(OwnedPokemon::isEgg).maxWithOrNull(
        compareBy<OwnedPokemon> { innateQuality(it) }.thenByDescending { it.stableKey },
    )

    fun captureBall(
        individuals: Iterable<OwnedPokemon>,
        balls: Map<Int, CaptureBallRecord>,
    ): CaptureBallRecord? {
        val selected = select(individuals) ?: return null
        return selected.captureBallId?.let(balls::get)
            ?.takeIf { it.sprite.value != null }
            ?: balls.values.firstOrNull { it.generic && it.sprite.value != null }
            ?: balls.values.firstOrNull { it.sprite.value != null }
    }

    fun innateAverage(individual: OwnedPokemon): Int = when {
        individual.generation >= 3 && individual.ivs.size == 6 -> individual.ivs.sum() / 6
        individual.dvs.size >= 4 -> {
            val attack = individual.dvs[0]
            val defense = individual.dvs[1]
            val speed = individual.dvs[2]
            val special = individual.dvs[3]
            val hp = ((attack and 1) shl 3) or ((defense and 1) shl 2) or ((speed and 1) shl 1) or (special and 1)
            listOf(hp, attack, defense, speed, special).sumOf { (it * 31.0 / 15.0).roundToInt() } / 5
        }
        else -> -1
    }

    fun tier(individual: OwnedPokemon): String = when (innateAverage(individual)) {
        in 0..9 -> "FODDER"
        in 10..17 -> "STANDARD"
        in 18..23 -> "TRAINED"
        in 24..27 -> "VETERAN"
        in 28..29 -> "ELITE"
        in 30..31 -> "ACE"
        else -> "UNAVAILABLE"
    }

    fun levelPrefix(level: Int, reference: Int?): String? = reference?.let {
        when (level - it) {
            in Int.MIN_VALUE..-3 -> "WEAK"
            in -2..1 -> "ORDINARY"
            in 2..3 -> "COMPETENT"
            in 4..5 -> "STRONG"
            else -> "MAJOR"
        }
    }

    private fun innateQuality(individual: OwnedPokemon): Int = when {
        individual.generation >= 3 && individual.ivs.size == 6 -> individual.ivs.sum() * 31
        individual.dvs.size >= 4 -> {
            val attack = individual.dvs[0]
            val defense = individual.dvs[1]
            val speed = individual.dvs[2]
            val special = individual.dvs[3]
            val hp = ((attack and 1) shl 3) or ((defense and 1) shl 2) or ((speed and 1) shl 1) or (special and 1)
            listOf(hp, attack, defense, speed, special).sumOf { (it * 31.0 / 15.0).roundToInt() } * 6
        }
        else -> -1
    }
}
