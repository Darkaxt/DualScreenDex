package com.enrpau.dualscreendex.parser.dataset.media

import java.util.Locale
import java.util.Collections

/** Applies only the explicitly permitted same-name plus canonical-Dex inference policy. */
object SpriteAliasResolver {
    fun resolve(
        species: Collection<SpriteSpeciesIdentity>,
        explicitFrames: Map<Int, DecodedSpriteFrame>,
    ): Map<Int, SpriteProjection> {
        val identities = species.associateBy { it.speciesId }
        require(identities.size == species.size) { "sprite species identities must have unique IDs" }
        require(explicitFrames.keys.all { it in identities }) {
            "explicit sprite frames must belong to the supplied species domain"
        }
        val explicit = explicitFrames.mapValues { SpriteProjection.Explicit(it.value) }
        return Collections.unmodifiableMap(
            identities.toSortedMap().mapValues { (speciesId, identity) ->
                explicit[speciesId] ?: infer(identity, identities.values, explicitFrames)
            },
        )
    }

    private fun infer(
        target: SpriteSpeciesIdentity,
        species: Collection<SpriteSpeciesIdentity>,
        explicitFrames: Map<Int, DecodedSpriteFrame>,
    ): SpriteProjection {
        val normalizedName = normalize(target.name)
            ?: return SpriteProjection.Missing("species has no normalized ROM name for sprite inference")
        val dex = target.canonicalDexNumber?.takeIf { it > 0 }
            ?: return SpriteProjection.Missing("species has no canonical Pokédex number for sprite inference")
        val donors = species.asSequence()
            .filter { it.speciesId != target.speciesId }
            .filter { it.canonicalDexNumber == dex && normalize(it.name) == normalizedName }
            .mapNotNull { donor -> explicitFrames[donor.speciesId]?.let { donor.speciesId to it } }
            .sortedBy { it.first }
            .toList()
        return when (donors.size) {
            0 -> SpriteProjection.Missing("no explicit same-name canonical-Dex sprite donor")
            1 -> SpriteProjection.Inferred(donors.single().first, donors.single().second)
            else -> SpriteProjection.Ambiguous(donors.map { it.first })
        }
    }

    private fun normalize(value: String?): String? = value
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.uppercase(Locale.ROOT)
        ?.takeIf(String::isNotBlank)
}
