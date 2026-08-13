package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.dataset.moves.CatalogMoveDetails

/**
 * Closes the move catalog over move IDs already decoded from ROM relationships.
 *
 * A relationship is evidence that an ID exists, but it is not evidence for a name or details. Missing fields
 * therefore remain explicitly unavailable unless the independently validated move-details dataset covers the ID.
 */
internal object ReferencedMoveCatalogClosure {
    fun close(
        moves: Map<Int, MoveRecord>,
        species: Map<Int, SpeciesRecord>,
        rulesets: List<LearnsetRuleset>,
        typedDetails: Map<Int, CatalogMoveDetails>,
        validTypeIds: Set<Int>? = null,
    ): Map<Int, MoveRecord> {
        val referencedIds = buildSet {
            species.values.forEach { record ->
                record.learnset.value.orEmpty().forEach { add(it.moveId) }
                record.moveAcquisitions.value.orEmpty().forEach { add(it.moveId) }
            }
            rulesets.forEach { ruleset ->
                ruleset.entriesBySpecies.values.forEach { entries ->
                    entries.forEach { add(it.moveId) }
                }
            }
        }.filter { it > 0 }.sorted()

        return moves.toSortedMap().apply {
            referencedIds.forEach { moveId ->
                val details = typedDetails[moveId]?.takeIf { validTypeIds == null || it.typeId in validTypeIds }
                putIfAbsent(moveId, placeholder(moveId, details))
            }
        }
    }

    private fun placeholder(moveId: Int, details: CatalogMoveDetails?): MoveRecord {
        val unresolvedDetails = "referenced move details were not resolved from the ROM"
        return MoveRecord(
            id = moveId,
            name = CatalogField.notFound("referenced move has no decoded ROM name"),
            typeId = details?.typeId?.let(CatalogField.Companion::available)
                ?: CatalogField.notFound(unresolvedDetails),
            category = details?.category?.let(CatalogField.Companion::available)
                ?: CatalogField.notFound(unresolvedDetails),
            power = details?.power?.let(CatalogField.Companion::available)
                ?: CatalogField.notFound(unresolvedDetails),
            accuracy = details?.accuracy?.let(CatalogField.Companion::available)
                ?: CatalogField.notFound(unresolvedDetails),
            pp = details?.pp?.let(CatalogField.Companion::available)
                ?: CatalogField.notFound(unresolvedDetails),
            priority = details?.priority?.let(CatalogField.Companion::available)
                ?: CatalogField.notFound(unresolvedDetails),
            effectId = details?.effectId?.let(CatalogField.Companion::available)
                ?: CatalogField.notFound(unresolvedDetails),
        )
    }
}

/** Closes only type IDs that the selected complete type domain independently proves valid. */
internal object ReferencedTypeCatalogClosure {
    fun close(
        types: Map<Int, TypeRecord>,
        moves: Map<Int, MoveRecord>,
        validTypeIds: Set<Int>,
    ): Map<Int, TypeRecord> = types.toSortedMap().apply {
        moves.values.mapNotNull { it.typeId.value }
            .filter { it in validTypeIds }
            .distinct()
            .sorted()
            .forEach { typeId ->
                putIfAbsent(
                    typeId,
                    TypeRecord(
                        id = typeId,
                        name = CatalogField.notFound("referenced type has no decoded ROM name"),
                    ),
                )
            }
    }
}
