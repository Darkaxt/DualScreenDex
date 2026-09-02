package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.defaultTextCodec
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout

/**
 * Closes Gen III encounter relationships only over IDs with an independently decoded name row.
 *
 * Gen III wild slots and the fixed-width species-name table share the engine's internal species
 * ID domain. A selected encounter is therefore authority for the referenced ID, while the name
 * row supplies real catalog data. Dex identity and every optional field remain unavailable when
 * their own datasets were not resolved. Zero and out-of-domain IDs fail closed.
 */
internal object EncounterReferencedSpeciesClosure {
    fun close(
        rom: RomImage,
        layout: ResolvedRomLayout,
        namesStatus: CapabilityStatus?,
        species: Map<Int, SpeciesRecord>,
        encounters: List<EncounterArea>,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
    ): Map<Int, SpeciesRecord> {
        val names = layout.tables.speciesNames
        val codec = layout.defaultTextCodec()
        if (
            layout.generation != 3 || names == null || codec == null ||
            namesStatus !in setOf(CapabilityStatus.AVAILABLE, CapabilityStatus.PARTIAL)
        ) {
            return species
        }
        val referencedIds = encounters.asSequence()
            .flatMap { it.slots.asSequence() }
            .map { it.speciesId }
            .filter { it > 0 }
            .distinct()
            .sorted()
        return species.toSortedMap().apply {
            referencedIds.forEach { speciesId ->
                if (speciesId in this || speciesId !in 0 until names.count) return@forEach
                val name = RecordMaterializers.readName(
                    rom,
                    names,
                    speciesId,
                    codec,
                    cancellation,
                )
                if (name?.any(Char::isLetterOrDigit) != true) return@forEach
                put(speciesId, identityRecord(speciesId, name))
            }
        }
    }

    private fun identityRecord(speciesId: Int, name: String): SpeciesRecord {
        val unresolved = "encounter-referenced species optional data was not resolved from the ROM"
        return SpeciesRecord(
            id = speciesId,
            dexNumber = CatalogField.notFound("species-to-Dex mapping was not resolved from the ROM"),
            name = CatalogField.available(name),
            typeIds = CatalogField.notFound(unresolved),
            baseStats = CatalogField.notFound(unresolved),
            sprite = CatalogField.notFound(unresolved),
            abilityIds = CatalogField.notFound(unresolved),
            growthRate = CatalogField.notFound(unresolved),
        )
    }
}
