package com.enrpau.dualscreendex.parser.family

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.language.LanguageRegistry
import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.language.OfficialLanguageResolver
import com.enrpau.dualscreendex.parser.language.RomLanguageManifest
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.parse.GbCompiledNameConsumer
import com.enrpau.dualscreendex.parser.parse.Gen1CompiledMoveResolver
import com.enrpau.dualscreendex.parser.parse.Gen2CompiledNamePairResolver
import com.enrpau.dualscreendex.parser.parse.Gen3CompiledNameGeometryResolver
import com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.validate.TableValidators

internal data class NativeNameCandidate(
    val codec: PokemonTextCodec,
    val species: TableLayout,
    val moves: TableLayout,
    val manifest: RomLanguageManifest,
    val moveData: TableLayout? = null,
)

/** Complete consumers select geometry; exact-codec cross-table lexical evidence selects authority. */
internal object NativeNameCandidateResolver {
    fun resolve(session: RomAnalysisSession, definition: EngineFamilyDefinition, profile: FamilyProfileBasis?): List<NativeNameCandidate> {
        session.cancellation.throwIfCancellationRequested()
        if (profile == null) return emptyList()
        if (definition.formatGeneration == 2) return genTwo(session, definition, profile)
        if (definition.formatGeneration == 3) return genThree(session, definition)
        if (definition.formatGeneration != 1) return emptyList()
        val roots = GbCompiledNameConsumer.discover(session.rom, 1, session.cancellation)
        if (roots.isEmpty()) return emptyList()
        val candidates = mutableListOf<NativeNameCandidate>()
        val codecs = OfficialLanguageResolver.probeCodecs(session.rom, session.header, 1, definition.family, session.cancellation)
        for (codec in codecs.filter { it.language == LanguageTag.JAPANESE }) {
            session.cancellation.throwIfCancellationRequested()
            val matching = roots.filter { root ->
                root.gen1Family == definition.family && LanguageRegistry.candidateCodec(LanguageTag.JAPANESE, 1,
                    session.header.platform, root.gen1Family)?.id == codec.id
            }
            if (matching.isEmpty()) continue
            val moves = Gen1CompiledMoveResolver.resolve(session.rom, codec, session.cancellation) ?: continue
            for (root in matching) {
                session.cancellation.throwIfCancellationRequested()
                val species = TableLayout(root.offset, profile.internalSpeciesCount, root.width)
                if (root.offset.toLong() + species.count.toLong() * root.width >
                    minOf(session.rom.size.toLong(), (root.offset / 0x4000 + 1L) * 0x4000)) continue
                val speciesEvidence = TableValidators.names(session.rom, species, species.count, codec)
                val movesEvidence = TableValidators.names(session.rom, moves.moveNames, moves.moveNames.count, codec)
                val manifest = RomLanguageAuthority.resolve(session.rom, session.header, 1, codec,
                    speciesEvidence, movesEvidence, species, moves.moveNames, session.cancellation)
                if (manifest.status == LanguageResolutionStatus.RESOLVED) {
                    candidates += NativeNameCandidate(codec, species, moves.moveNames, manifest, moves.moveData)
                }
            }
        }
        return candidates.distinctBy { Triple(it.codec.id, it.species, it.moves) }
    }

    private fun genThree(session: RomAnalysisSession, definition: EngineFamilyDefinition): List<NativeNameCandidate> {
        val rom = session.rom
        fun pointer(slot: Int) = if (slot + 4 <= rom.size) rom.gbaPointer(slot) else null
        val publishedSpecies = pointer(0x144)
        val publishedMoves = pointer(0x148)
        val rubyRoots = linkedSetOf<Int>()
        rom.visitMatches(byteArrayOf(0x30, 0xb5.toByte(), 0, 0x25, 8, 0x4c, 0xc8.toByte(), 0xf7.toByte()),
            onCheck = session.cancellation::throwIfCancellationRequested) { offset ->
            if (offset >= 4) pointer(offset - 4)?.let(rubyRoots::add)
            true
        }
        if (rubyRoots.isEmpty() && (publishedSpecies == null || publishedMoves == null)) return emptyList()
        return OfficialLanguageResolver.probeCodecs(rom, session.header, 3, definition.family, session.cancellation)
            .filter { it.language == LanguageTag.JAPANESE }.mapNotNull { codec ->
                session.cancellation.throwIfCancellationRequested()
                val geometry = Gen3CompiledNameGeometryResolver.resolve(rom, codec, session.cancellation)
                if (geometry.ambiguous) return@mapNotNull null
                val species = geometry.speciesNames ?: return@mapNotNull null
                val moves = geometry.moveNames ?: return@mapNotNull null
                if (publishedSpecies != null && publishedSpecies != species.offset ||
                    publishedMoves != null && publishedMoves != moves.offset) return@mapNotNull null
                // The native x6/x8 table consumer is necessary but shared across dialects. Its root
                // must also agree with a lineage-specific consumer/published pointer interface.
                val lineage = when (codec.id) {
                    JapanesePokemonTextCodecs.gen3RubySapphire.id -> species.offset in rubyRoots
                    JapanesePokemonTextCodecs.gen3Later.id -> publishedSpecies == species.offset && publishedMoves == moves.offset
                    else -> false
                }
                if (!lineage) return@mapNotNull null
                val manifest = RomLanguageAuthority.resolve(rom, session.header, 3, codec,
                    TableValidators.names(rom, species, species.count, codec), TableValidators.names(rom, moves, moves.count, codec),
                    species, moves, session.cancellation)
                if (manifest.status == LanguageResolutionStatus.RESOLVED) NativeNameCandidate(codec, species, moves, manifest) else null
            }
    }

    private fun genTwo(session: RomAnalysisSession, definition: EngineFamilyDefinition, profile: FamilyProfileBasis): List<NativeNameCandidate> {
        // The ordinary numeric domain remains inherited/independently validated; native widths do
        // not imply expansion and are not gated on an expanded or contracted compiled core.
        val roots = GbCompiledNameConsumer.discover(session.rom, 2, session.cancellation)
        if (roots.isEmpty()) return emptyList()
        return OfficialLanguageResolver.probeCodecs(session.rom, session.header, 2, definition.family, session.cancellation)
            .filter { it.language == LanguageTag.JAPANESE || it.language == LanguageTag.KOREAN }
            .mapNotNull { codec ->
                session.cancellation.throwIfCancellationRequested()
                val pair = Gen2CompiledNamePairResolver.resolve(session.rom, profile.internalSpeciesCount, profile.moveCount,
                    codec, session.cancellation) ?: return@mapNotNull null
                val speciesEvidence = TableValidators.names(session.rom, pair.speciesNames, pair.speciesNames.count, codec)
                val movesEvidence = TableValidators.names(session.rom, pair.moveNames, pair.moveNames.count, codec)
                val manifest = RomLanguageAuthority.resolve(session.rom, session.header, 2, codec, speciesEvidence,
                    movesEvidence, pair.speciesNames, pair.moveNames, session.cancellation)
                if (manifest.status == LanguageResolutionStatus.RESOLVED) NativeNameCandidate(codec, pair.speciesNames, pair.moveNames, manifest)
                else null
            }
    }
}
