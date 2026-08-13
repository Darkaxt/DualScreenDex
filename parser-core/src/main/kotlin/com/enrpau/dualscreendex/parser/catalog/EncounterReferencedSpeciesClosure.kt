package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

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
        generation: Int,
        names: TableLayout?,
        namesStatus: CapabilityStatus?,
        species: Map<Int, SpeciesRecord>,
        encounters: List<EncounterArea>,
    ): Map<Int, SpeciesRecord> {
        if (
            generation != 3 || names == null ||
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
                    PokemonTextCodec.gbaEnglish,
                )
                if (name.none(Char::isLetterOrDigit)) return@forEach
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
