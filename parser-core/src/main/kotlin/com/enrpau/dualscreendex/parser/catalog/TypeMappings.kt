package com.enrpau.dualscreendex.parser.catalog

internal object TypeMappings {
    private val gba = listOf(
        "NORMAL", "FIGHTING", "FLYING", "POISON", "GROUND", "ROCK", "BUG", "GHOST", "STEEL",
        "MYSTERY", "FIRE", "WATER", "GRASS", "ELECTRIC", "PSYCHIC", "ICE", "DRAGON", "DARK",
    ).withIndex().associate { it.index to it.value }

    private val gb = mapOf(
        0 to "NORMAL",
        1 to "FIGHTING",
        2 to "FLYING",
        3 to "POISON",
        4 to "GROUND",
        5 to "ROCK",
        7 to "BUG",
        8 to "GHOST",
        9 to "STEEL",
        20 to "FIRE",
        21 to "WATER",
        22 to "GRASS",
        23 to "ELECTRIC",
        24 to "PSYCHIC",
        25 to "ICE",
        26 to "DRAGON",
        27 to "DARK",
    )

    fun name(generation: Int, typeId: Int): String =
        (if (generation == 3) gba[typeId] else gb[typeId]) ?: "TYPE $typeId"
}
