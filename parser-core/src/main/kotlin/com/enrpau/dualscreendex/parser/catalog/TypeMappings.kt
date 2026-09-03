package com.enrpau.dualscreendex.parser.catalog

internal object TypeMappings {
    private val gbaRolesById = listOf(
        TypeSemanticRole.NORMAL,
        TypeSemanticRole.FIGHTING,
        TypeSemanticRole.FLYING,
        TypeSemanticRole.POISON,
        TypeSemanticRole.GROUND,
        TypeSemanticRole.ROCK,
        TypeSemanticRole.BUG,
        TypeSemanticRole.GHOST,
        TypeSemanticRole.STEEL,
        TypeSemanticRole.MYSTERY,
        TypeSemanticRole.FIRE,
        TypeSemanticRole.WATER,
        TypeSemanticRole.GRASS,
        TypeSemanticRole.ELECTRIC,
        TypeSemanticRole.PSYCHIC,
        TypeSemanticRole.ICE,
        TypeSemanticRole.DRAGON,
        TypeSemanticRole.DARK,
    )

    private val gbRolesById = mapOf(
        0 to TypeSemanticRole.NORMAL,
        1 to TypeSemanticRole.FIGHTING,
        2 to TypeSemanticRole.FLYING,
        3 to TypeSemanticRole.POISON,
        4 to TypeSemanticRole.GROUND,
        5 to TypeSemanticRole.ROCK,
        7 to TypeSemanticRole.BUG,
        8 to TypeSemanticRole.GHOST,
        9 to TypeSemanticRole.STEEL,
        20 to TypeSemanticRole.FIRE,
        21 to TypeSemanticRole.WATER,
        22 to TypeSemanticRole.GRASS,
        23 to TypeSemanticRole.ELECTRIC,
        24 to TypeSemanticRole.PSYCHIC,
        25 to TypeSemanticRole.ICE,
        26 to TypeSemanticRole.DRAGON,
        27 to TypeSemanticRole.DARK,
    )

    fun presentationRole(generation: Int, typeId: Int): TypeSemanticRole? = if (generation == 3) {
        gbaRolesById.getOrNull(typeId)
    } else {
        gbRolesById[typeId]
    }
}
