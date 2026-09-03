package com.enrpau.dualscreendex.parser.catalog

object TypePresentationMaterializer {
    fun apply(
        types: Map<Int, TypeRecord>,
        fallbackRole: (Int) -> TypeSemanticRole? = { null },
    ): Map<Int, TypeRecord> = types.mapValues { (_, type) ->
        val presentationRole = type.semanticRole.value ?: fallbackRole(type.id)
        val roleColor = presentationRole?.let(ROLE_COLORS::get)
        val background = roleColor ?: accessibleColor(type.id)
        val presentation = TypePresentation(
            source = if (roleColor != null) PresentationSource.FAMILY_FALLBACK else PresentationSource.ACCESSIBLE_FALLBACK,
            foregroundArgb = readableForeground(background),
            backgroundArgb = background,
            borderArgb = darken(background),
        )
        type.copy(presentation = CatalogField.available(presentation))
    }

    private fun readableForeground(background: Int): Int {
        val red = background ushr 16 and 0xFF
        val green = background ushr 8 and 0xFF
        val blue = background and 0xFF
        val luminance = red * 299 + green * 587 + blue * 114
        return if (luminance >= 150_000) 0xFF101510.toInt() else 0xFFFFFFFF.toInt()
    }

    private fun darken(color: Int): Int {
        val red = (color ushr 16 and 0xFF) * 2 / 3
        val green = (color ushr 8 and 0xFF) * 2 / 3
        val blue = (color and 0xFF) * 2 / 3
        return 0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue
    }

    private fun accessibleColor(typeId: Int): Int = ACCESSIBLE_COLORS[Math.floorMod(typeId, ACCESSIBLE_COLORS.size)]

    private val ROLE_COLORS = mapOf(
        TypeSemanticRole.NORMAL to 0xFFA8A878.toInt(),
        TypeSemanticRole.FIGHTING to 0xFFC03028.toInt(),
        TypeSemanticRole.FLYING to 0xFFA890F0.toInt(),
        TypeSemanticRole.POISON to 0xFFA040A0.toInt(),
        TypeSemanticRole.GROUND to 0xFFE0C068.toInt(),
        TypeSemanticRole.ROCK to 0xFFB8A038.toInt(),
        TypeSemanticRole.BUG to 0xFFA8B820.toInt(),
        TypeSemanticRole.GHOST to 0xFF705898.toInt(),
        TypeSemanticRole.STEEL to 0xFFB8B8D0.toInt(),
        TypeSemanticRole.MYSTERY to 0xFF68A090.toInt(),
        TypeSemanticRole.FIRE to 0xFFF08030.toInt(),
        TypeSemanticRole.WATER to 0xFF6890F0.toInt(),
        TypeSemanticRole.GRASS to 0xFF78C850.toInt(),
        TypeSemanticRole.ELECTRIC to 0xFFF8D030.toInt(),
        TypeSemanticRole.PSYCHIC to 0xFFF85888.toInt(),
        TypeSemanticRole.ICE to 0xFF98D8D8.toInt(),
        TypeSemanticRole.DRAGON to 0xFF7038F8.toInt(),
        TypeSemanticRole.DARK to 0xFF705848.toInt(),
        TypeSemanticRole.FAIRY to 0xFFEE99AC.toInt(),
    )
    private val ACCESSIBLE_COLORS = intArrayOf(
        0xFF52677A.toInt(),
        0xFF6A5A8C.toInt(),
        0xFF356B62.toInt(),
        0xFF81513F.toInt(),
    )
}
