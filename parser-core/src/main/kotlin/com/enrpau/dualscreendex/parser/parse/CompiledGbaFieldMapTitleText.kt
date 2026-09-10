package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.text.LanguageTextPlausibility
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

internal object CompiledGbaFieldMapTitleText {
    sealed interface Result {
        data class Resolved(val value: String) : Result
        data class Unavailable(val reason: String) : Result
    }

    fun resolve(
        rom: RomImage,
        selectedLoader: Int,
        codec: PokemonTextCodec?,
        cancellation: ParserCancellationToken,
    ): Result {
        cancellation.throwIfCancellationRequested()
        if (codec == null || !codec.supports(3, Platform.GBA)) {
            return Result.Unavailable("an exact GBA text codec is unavailable")
        }
        val nomination = CompiledGbaFieldMapTitle.nominate(rom, selectedLoader, cancellation)
        val declarations = when (nomination) {
            is CompiledGbaFieldMapTitle.Result.Complete -> nomination.declarations
            is CompiledGbaFieldMapTitle.Result.Incomplete ->
                return Result.Unavailable(nomination.reason)
        }
        val declaration = declarations.singleOrNull()
            ?: return Result.Unavailable("the selected loader did not have one unique title declaration")
        if (declaration.windowFlow == null || declaration.ownerFlow == null) {
            return Result.Unavailable("the title declaration did not retain complete consumer and owner authority")
        }
        val decoded = codec.decodeDetailed(rom, declaration.source, MAXIMUM_TITLE_BYTES, cancellation)
        if (!decoded.terminated || decoded.contentUnits !in 1..MAXIMUM_TITLE_UNITS ||
            decoded.invalidUnits != 0 || decoded.controlUnits != 0 || decoded.substitutionUnits != 0 ||
            decoded.glyphUnits == 0 || decoded.text.none(Char::isLetterOrDigit) ||
            !LanguageTextPlausibility.looksLikeStandaloneFixedName(decoded.text, codec.language)
        ) return Result.Unavailable("the authorized title source was not one bounded static label")
        cancellation.throwIfCancellationRequested()
        return Result.Resolved(decoded.text)
    }

    private const val MAXIMUM_TITLE_BYTES = 64
    private const val MAXIMUM_TITLE_UNITS = 32
}
