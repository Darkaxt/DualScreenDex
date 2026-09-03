package com.enrpau.dualscreendex.parser.family

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.LanguageEvidence
import com.enrpau.dualscreendex.parser.language.LanguageEvidenceKind
import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.language.LocalizedTableLayout
import com.enrpau.dualscreendex.parser.language.OfficialLanguageResolver
import com.enrpau.dualscreendex.parser.language.RomLanguageManifest
import com.enrpau.dualscreendex.parser.language.RomLanguageProjection
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

/** Converts a structural probe codec into language authority only after locale-scoped corroboration. */
internal object RomLanguageAuthority {
    fun resolve(
        rom: RomImage,
        header: RomHeader,
        generation: Int,
        probeCodec: PokemonTextCodec,
        speciesNamesEvidence: ValidationEvidence,
        moveNamesEvidence: ValidationEvidence,
        speciesNamesLayout: TableLayout?,
        moveNamesLayout: TableLayout?,
        cancellation: ParserCancellationToken,
    ): RomLanguageManifest {
        if (!probeCodec.supports(generation, header.platform)) {
            return unknown("probe codec does not support the selected generation and platform")
        }
        if (probeCodec.language !in LANGUAGE_CHARACTER_PROFILES) {
            return unknown("probe codec language is not a ratified Western language")
        }
        if (!speciesNamesEvidence.compatible || speciesNamesLayout == null) {
            return unknown("selected species-name table lacks compatible structural evidence")
        }
        if (!moveNamesEvidence.compatible || moveNamesLayout == null) {
            return unknown("selected move-name table lacks compatible structural evidence")
        }
        val language = probeCodec.language
        val headerEvidence = OfficialLanguageResolver.headerCandidate(header, generation)
            ?.takeIf { it.language == language }
            ?.evidence
        val matchesRetailControls = language == LanguageTag.ENGLISH && matchesEnglishMoveControls(
            rom,
            moveNamesLayout,
            generation,
            probeCodec,
            cancellation,
        )
        val plausibility = moveLanguagePlausibility(
            rom,
            moveNamesLayout,
            generation,
            probeCodec,
            cancellation,
        )
        if (!plausibility.compatible) {
            return unknown(
                "selected tables lack bounded ${language.value} language-specific content corroboration",
            )
        }

        val evidence = buildList {
            headerEvidence?.let(::add)
            add(
                LanguageEvidence(
                    kind = LanguageEvidenceKind.TABLE_RELATIONSHIP,
                    summary = "species-name table geometry and bounded text decoding agree",
                    confidence = speciesNamesEvidence.confidence.toConfidencePercent(),
                ),
            )
            add(
                LanguageEvidence(
                    kind = LanguageEvidenceKind.TABLE_RELATIONSHIP,
                    summary = "move-name table geometry and bounded text decoding agree",
                    confidence = moveNamesEvidence.confidence.toConfidencePercent(),
                ),
            )
            add(
                LanguageEvidence(
                    kind = LanguageEvidenceKind.CODEC_PLAUSIBILITY,
                    summary = "bounded move-name sample contains distinct ${language.value} language markers",
                    confidence = minOf(
                        plausibility.confidence,
                        speciesNamesEvidence.confidence.toConfidencePercent(),
                        moveNamesEvidence.confidence.toConfidencePercent(),
                    ),
                ),
            )
            if (matchesRetailControls) {
                add(
                    LanguageEvidence(
                        kind = LanguageEvidenceKind.RETAIL_VALIDATION_CONTROL,
                        summary = "selected move-name root matches bounded English validation controls",
                        confidence = 100,
                    ),
                )
            }
        }
        return RomLanguageManifest(
            defaultLanguage = language,
            projections = listOf(
                RomLanguageProjection(
                    language = language,
                    codecId = probeCodec.id,
                    codecVersion = probeCodec.version,
                    localizedTables = LocalizedTableLayout(
                        speciesNames = speciesNamesLayout,
                        moveNames = moveNamesLayout,
                    ),
                    evidence = evidence,
                    status = LanguageResolutionStatus.RESOLVED,
                ),
            ),
            status = LanguageResolutionStatus.RESOLVED,
            diagnostics = listOf(
                if (matchesRetailControls) {
                    "resolved ${language.value} from structurally selected tables and optional locale-scoped controls"
                } else {
                    "resolved ${language.value} from structurally selected tables and bounded language-specific content evidence"
                },
            ),
        )
    }

    private data class LanguagePlausibility(
        val sampledTrigrams: Int,
        val languageCoverage: Double,
        val competingCoverage: Double,
    ) {
        private val margin: Double get() = languageCoverage - competingCoverage
        val compatible: Boolean
            get() = sampledTrigrams >= MINIMUM_PLAUSIBILITY_TRIGRAMS &&
                languageCoverage >= MINIMUM_LANGUAGE_COVERAGE &&
                margin >= MINIMUM_LANGUAGE_MARGIN
        val confidence: Int
            get() = ((languageCoverage + margin.coerceAtLeast(0.0)) * 100.0)
                .toInt()
                .coerceIn(0, 100)
    }

    private fun moveLanguagePlausibility(
        rom: RomImage,
        layout: TableLayout,
        generation: Int,
        codec: PokemonTextCodec,
        cancellation: ParserCancellationToken,
    ): LanguagePlausibility {
        val firstIndex = if (generation == 3) 1 else 0
        val names = when {
            layout.variableLength -> sampleVariableNames(
                rom,
                layout,
                firstIndex,
                codec,
                cancellation,
            )
            layout.valuesArePointers -> samplePointerNames(
                rom,
                layout,
                firstIndex,
                codec,
                cancellation,
            )
            else -> sampleFixedNames(
                rom,
                layout,
                firstIndex,
                codec,
                cancellation,
            )
        }.filterNot { normalizedName ->
            normalizeEnglishText(normalizedName) in ENGLISH_CONTROL_NAMES
        }
        val trigrams = names.flatMap(::characterTrigrams)
        if (trigrams.isEmpty()) return LanguagePlausibility(0, 0.0, 0.0)
        val languageProfile = requireNotNull(LANGUAGE_CHARACTER_PROFILES[codec.language])
        val languageCoverage = trigrams.count(languageProfile::contains).toDouble() / trigrams.size
        val competingCoverage = LANGUAGE_CHARACTER_PROFILES
            .filterKeys { it != codec.language }
            .values
            .maxOf { profile -> trigrams.count(profile::contains).toDouble() / trigrams.size }
        return LanguagePlausibility(
            sampledTrigrams = trigrams.size,
            languageCoverage = languageCoverage,
            competingCoverage = competingCoverage,
        )
    }

    private fun characterTrigrams(value: String): List<String> = WORD_PATTERN.findAll(value.uppercase())
        .flatMap { match ->
            val bounded = "^${match.value}$"
            (0..bounded.length - CHARACTER_TRIGRAM_WIDTH).asSequence()
                .map { index -> bounded.substring(index, index + CHARACTER_TRIGRAM_WIDTH) }
        }
        .toList()

    private fun languageCharacterProfile(sample: String): Set<String> =
        characterTrigrams(sample).toSet()

    private fun normalizeEnglishText(value: String): String =
        value.uppercase().replace(NON_ENGLISH_MARKER_CHARACTERS, " ").trim()

    private fun sampleVariableNames(
        rom: RomImage,
        layout: TableLayout,
        firstIndex: Int,
        codec: PokemonTextCodec,
        cancellation: ParserCancellationToken,
    ): List<String> {
        val names = mutableListOf<String>()
        var cursor = layout.offset
        val endIndex = minOf(layout.count, firstIndex + MAXIMUM_PLAUSIBILITY_RECORDS)
        for (index in 0 until endIndex) {
            cancellation.throwIfCancellationRequested()
            if (cursor !in 0 until rom.size) break
            val decoded = codec.decodeDetailed(
                rom = rom,
                offset = cursor,
                maximumBytes = minOf(MAXIMUM_CONTROL_BYTES, rom.size - cursor),
                cancellation = cancellation,
            )
            if (!decoded.terminated) break
            if (index >= firstIndex && decoded.invalidUnits == 0 && decoded.text.isNotBlank()) {
                names += decoded.text
            }
            cursor += decoded.consumedBytes
        }
        return names
    }

    private fun samplePointerNames(
        rom: RomImage,
        layout: TableLayout,
        firstIndex: Int,
        codec: PokemonTextCodec,
        cancellation: ParserCancellationToken,
    ): List<String> {
        val width = layout.recordSize
        val stride = layout.stride ?: width
        if (width < Int.SIZE_BYTES || stride < width) return emptyList()
        val names = mutableListOf<String>()
        val endIndex = minOf(layout.count, firstIndex + MAXIMUM_PLAUSIBILITY_RECORDS)
        for (index in firstIndex until endIndex) {
            cancellation.throwIfCancellationRequested()
            val pointerOffset = layout.offset.toLong() + index.toLong() * stride.toLong()
            if (pointerOffset < 0 || pointerOffset + Int.SIZE_BYTES > rom.size || pointerOffset > Int.MAX_VALUE) {
                continue
            }
            val textOffset = rom.gbaPointer(pointerOffset.toInt()) ?: continue
            val decoded = codec.decodeDetailed(
                rom = rom,
                offset = textOffset,
                maximumBytes = minOf(MAXIMUM_CONTROL_BYTES, rom.size - textOffset),
                cancellation = cancellation,
            )
            if (decoded.terminated && decoded.invalidUnits == 0 && decoded.text.isNotBlank()) {
                names += decoded.text
            }
        }
        return names
    }

    private fun sampleFixedNames(
        rom: RomImage,
        layout: TableLayout,
        firstIndex: Int,
        codec: PokemonTextCodec,
        cancellation: ParserCancellationToken,
    ): List<String> {
        val width = layout.recordSize
        val stride = layout.stride ?: width
        if (width <= 0 || stride <= 0 || width > MAXIMUM_CONTROL_BYTES) return emptyList()
        val names = mutableListOf<String>()
        val endIndex = minOf(layout.count, firstIndex + MAXIMUM_PLAUSIBILITY_RECORDS)
        for (index in firstIndex until endIndex) {
            cancellation.throwIfCancellationRequested()
            val offset = layout.offset.toLong() + index.toLong() * stride.toLong()
            if (offset < 0 || offset + width > rom.size || offset > Int.MAX_VALUE) continue
            val decoded = codec.decodeDetailed(
                rom = rom,
                offset = offset.toInt(),
                maximumBytes = width,
                cancellation = cancellation,
            )
            if (decoded.invalidUnits == 0 && decoded.text.isNotBlank() &&
                (decoded.terminated || decoded.consumedBytes == width)
            ) {
                names += decoded.text
            }
        }
        return names
    }

    private fun matchesEnglishMoveControls(
        rom: RomImage,
        layout: TableLayout,
        generation: Int,
        codec: PokemonTextCodec,
        cancellation: ParserCancellationToken,
    ): Boolean {
        val firstIndex = if (generation == 3) 1 else 0
        if (layout.count < firstIndex + ENGLISH_MOVE_CONTROLS.size) return false
        return when {
            layout.variableLength -> matchesVariableControls(rom, layout.offset, codec, cancellation)
            layout.valuesArePointers -> matchesPointerControls(
                rom,
                layout,
                firstIndex,
                codec,
                cancellation,
            )
            else -> matchesFixedControls(rom, layout, firstIndex, codec, cancellation)
        }
    }

    private fun matchesVariableControls(
        rom: RomImage,
        startOffset: Int,
        codec: PokemonTextCodec,
        cancellation: ParserCancellationToken,
    ): Boolean {
        var cursor = startOffset
        for (controlIndex in ENGLISH_MOVE_CONTROLS.indices) {
            if (cursor !in 0 until rom.size) return false
            val decoded = codec.decodeDetailed(
                rom = rom,
                offset = cursor,
                maximumBytes = minOf(MAXIMUM_CONTROL_BYTES, rom.size - cursor),
                cancellation = cancellation,
            )
            if (!decoded.terminated || decoded.invalidUnits != 0 ||
                !matchesEnglishControl(decoded.text, controlIndex)
            ) return false
            cursor += decoded.consumedBytes
        }
        return true
    }

    private fun matchesPointerControls(
        rom: RomImage,
        layout: TableLayout,
        firstIndex: Int,
        codec: PokemonTextCodec,
        cancellation: ParserCancellationToken,
    ): Boolean {
        val width = layout.recordSize
        val stride = layout.stride ?: width
        if (width < Int.SIZE_BYTES || stride < width) return false
        return ENGLISH_MOVE_CONTROLS.indices.all { controlIndex ->
            val recordIndex = firstIndex + controlIndex
            val pointerOffset = layout.offset.toLong() + recordIndex.toLong() * stride
            if (pointerOffset < 0 || pointerOffset + Int.SIZE_BYTES > rom.size || pointerOffset > Int.MAX_VALUE) {
                false
            } else {
                val textOffset = rom.gbaPointer(pointerOffset.toInt())
                if (textOffset == null) {
                    false
                } else {
                    val decoded = codec.decodeDetailed(
                        rom = rom,
                        offset = textOffset,
                        maximumBytes = minOf(MAXIMUM_CONTROL_BYTES, rom.size - textOffset),
                        cancellation = cancellation,
                    )
                    decoded.terminated &&
                        decoded.invalidUnits == 0 &&
                        matchesEnglishControl(decoded.text, controlIndex)
                }
            }
        }
    }

    private fun matchesFixedControls(
        rom: RomImage,
        layout: TableLayout,
        firstIndex: Int,
        codec: PokemonTextCodec,
        cancellation: ParserCancellationToken,
    ): Boolean {
        val width = layout.recordSize
        val stride = layout.stride ?: width
        if (width <= 0 || stride <= 0 || width > MAXIMUM_CONTROL_BYTES) return false
        return ENGLISH_MOVE_CONTROLS.indices.all { controlIndex ->
            val recordIndex = firstIndex + controlIndex
            val offset = layout.offset.toLong() + recordIndex.toLong() * stride.toLong()
            if (offset < 0 || offset + width > rom.size || offset > Int.MAX_VALUE) {
                false
            } else {
                val decoded = codec.decodeDetailed(
                    rom = rom,
                    offset = offset.toInt(),
                    maximumBytes = width,
                    cancellation = cancellation,
                )
                decoded.terminated &&
                    decoded.invalidUnits == 0 &&
                    matchesEnglishControl(decoded.text, controlIndex)
            }
        }
    }

    private fun matchesEnglishControl(text: String, controlIndex: Int): Boolean =
        ENGLISH_MOVE_CONTROLS[controlIndex].any { expected ->
            text.equals(expected, ignoreCase = true)
        }

    private fun unknown(reason: String) = RomLanguageManifest(
        defaultLanguage = null,
        projections = emptyList(),
        status = LanguageResolutionStatus.UNKNOWN,
        diagnostics = listOf(reason),
    )

    private fun Double.toConfidencePercent(): Int =
        (coerceIn(0.0, 1.0) * 100.0).toInt()

    private val NON_ENGLISH_MARKER_CHARACTERS = Regex("[^A-Z0-9]+")
    private val WORD_PATTERN = Regex("[A-Z]+")
    private val ENGLISH_MOVE_CONTROLS = listOf(
        setOf("POUND"),
        setOf("KARATE CHOP"),
        setOf("DOUBLESLAP", "DOUBLE SLAP"),
    )
    private val ENGLISH_CONTROL_NAMES = ENGLISH_MOVE_CONTROLS
        .flatten()
        .mapTo(linkedSetOf(), ::normalizeEnglishText)
    private val LANGUAGE_CHARACTER_PROFILES = mapOf(
        LanguageTag.ENGLISH to languageCharacterProfile(
            "burn burning blaze flame fiery spark lightning thunder storm wind breeze frost frozen icy snow " +
                "water river wave tidal stone rocky earth muddy sand iron steel shadow dark night bright light " +
                "solar lunar mind dream sleep waking poison toxic venom healing restore guard shield strike " +
                "crush slash jab sweep throw spinning rising falling hidden ancient wild fierce gentle swift " +
                "slow sharp heavy mighty focus echo roar song dance power energy claw fang wing tail beam burst " +
                "punch kick tackle growl whip",
        ),
        LanguageTag.FRENCH to languageCharacterProfile(
            "bruler brulant flamme ardent etincelle eclair tonnerre tempete vent brise gel gele glace neige " +
                "eau riviere vague maree pierre roche terre boue sable fer acier ombre sombre nuit clair " +
                "lumiere solaire lunaire esprit reve sommeil reveil poison toxique venin soin restaurer garde " +
                "bouclier frappe ecraser trancher coup balayer lancer tournoyer monter tomber cache ancien " +
                "sauvage feroce doux rapide lent aigu lourd puissant concentration echo rugissement chanson " +
                "danse puissance energie griffe croc aile queue rayon explosion poing pied charge force claque",
        ),
        LanguageTag.GERMAN to languageCharacterProfile(
            "brennen brennend flamme feurig funke blitz donner sturm wind brise frost gefroren eis schnee " +
                "wasser fluss welle gezeiten stein fels erde schlamm sand eisen stahl schatten dunkel nacht " +
                "hell licht sonne mond geist traum schlaf wach gift toxisch heilung heilen wache schild schlag " +
                "zermalmen schneiden hieb fegen werfen drehen steigen fallen verborgen uralt wild heftig sanft " +
                "schnell langsam scharf schwer macht fokus echo brullen lied tanz kraft energie kralle zahn " +
                "flugel schwanz strahl ausbruch faust tritt",
        ),
        LanguageTag.SPANISH to languageCharacterProfile(
            "quemar ardiente llama fuego chispa relampago trueno tormenta viento brisa escarcha helado hielo " +
                "nieve agua rio ola marea piedra roca tierra barro arena hierro acero sombra oscuro noche claro " +
                "luz solar lunar mente sueno dormir despertar veneno toxico curar restaurar guardia escudo golpe " +
                "aplastar cortar punzada barrer lanzar girar subir caer oculto antiguo salvaje feroz suave rapido " +
                "lento afilado pesado poderoso enfoque eco rugido cancion baile poder energia garra colmillo ala " +
                "cola rayo estallido puno patada",
        ),
        LanguageTag.ITALIAN to languageCharacterProfile(
            "bruciare ardente fiamma fuoco scintilla fulmine tuono tempesta vento brezza gelo gelato ghiaccio " +
                "neve acqua fiume onda marea pietra roccia terra fango sabbia ferro acciaio ombra scuro notte " +
                "chiaro luce solare lunare mente sogno sonno sveglio veleno tossico guarire ristoro guardia " +
                "scudo colpo schiacciare taglio pugno spazzare lanciare girare salire cadere nascosto antico " +
                "selvaggio feroce gentile rapido lento affilato pesante potente eco ruggito canzone danza " +
                "potere energia artiglio zanna ala coda raggio esplosione calcio",
        ),
    )
    private const val CHARACTER_TRIGRAM_WIDTH = 3
    private const val MINIMUM_PLAUSIBILITY_TRIGRAMS = 18
    private const val MINIMUM_LANGUAGE_COVERAGE = 0.35
    private const val MINIMUM_LANGUAGE_MARGIN = 0.08
    private const val MAXIMUM_PLAUSIBILITY_RECORDS = 128
    private const val MAXIMUM_CONTROL_BYTES = 24
}
