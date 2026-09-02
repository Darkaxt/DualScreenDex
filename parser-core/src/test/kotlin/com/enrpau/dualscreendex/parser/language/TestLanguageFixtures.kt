package com.enrpau.dualscreendex.parser.language

import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.GbaCompiledReferenceIndex
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.PokeemeraldExpansionMetadata
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedDatasetLayouts
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

internal fun resolvedLanguageManifest(
    codec: PokemonTextCodec,
    language: LanguageTag = LanguageTag.ENGLISH,
    localizedTables: LocalizedTableLayout = LocalizedTableLayout(),
): RomLanguageManifest = RomLanguageManifest(
    defaultLanguage = language,
    projections = listOf(
        RomLanguageProjection(
            language = language,
            codecId = codec.id,
            codecVersion = codec.version,
            localizedTables = localizedTables,
            evidence = emptyList(),
            status = LanguageResolutionStatus.RESOLVED,
        ),
    ),
    status = LanguageResolutionStatus.RESOLVED,
)

internal fun resolvedEnglishLayout(
    family: EngineFamily,
    generation: Int,
    platform: Platform,
    speciesCount: Int?,
    moveCount: Int?,
    tables: ProfileTables,
    pokeemeraldExpansion: PokeemeraldExpansionMetadata? = null,
    compiledGbaReferences: GbaCompiledReferenceIndex? = null,
    resolvedDatasets: ResolvedDatasetLayouts = ResolvedDatasetLayouts(),
): ResolvedRomLayout = ResolvedRomLayout(
    family = family,
    generation = generation,
    platform = platform,
    speciesCount = speciesCount,
    moveCount = moveCount,
    tables = tables,
    pokeemeraldExpansion = pokeemeraldExpansion,
    compiledGbaReferences = compiledGbaReferences,
    resolvedDatasets = resolvedDatasets,
    languageManifest = resolvedLanguageManifest(
        when (platform) {
            Platform.GB, Platform.GBC -> PokemonTextCodec.gbEnglish
            Platform.GBA -> PokemonTextCodec.gbaEnglish
            Platform.UNKNOWN -> error("resolved English test layouts require a known platform")
        },
    ),
)

internal val textUnavailableLanguageManifests = listOf(
    RomLanguageManifest.UNKNOWN,
    RomLanguageManifest(
        defaultLanguage = null,
        projections = emptyList(),
        status = LanguageResolutionStatus.AMBIGUOUS,
    ),
)
