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
import com.enrpau.dualscreendex.parser.text.LanguageTextPlausibility

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
        cancellation.throwIfCancellationRequested()
        if (!probeCodec.supports(generation, header.platform)) {
            return unknown("probe codec does not support the selected generation and platform")
        }
        if (probeCodec.language !in LANGUAGE_CHARACTER_PROFILES && probeCodec.language !in NATIVE_CHARACTER_PROFILES) {
            return unknown("probe codec language has no bounded content profile")
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
        val plausibility = if (language in NATIVE_CHARACTER_PROFILES) {
            nativeLanguagePlausibility(rom, speciesNamesLayout, moveNamesLayout, generation, probeCodec, cancellation)
        } else moveLanguagePlausibility(
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

    fun combine(manifests: List<RomLanguageManifest>, cancellation: ParserCancellationToken): RomLanguageManifest {
        cancellation.throwIfCancellationRequested()
        val authorities = manifests.filter { it.status == LanguageResolutionStatus.RESOLVED }
            .distinctBy { it.defaultProjection()?.let { projection -> projection.codecId to projection.localizedTables } }
        if (authorities.size > 1 || manifests.any { it.status == LanguageResolutionStatus.AMBIGUOUS }) {
            return RomLanguageManifest(defaultLanguage = null, projections = emptyList(),
                status = LanguageResolutionStatus.AMBIGUOUS,
                diagnostics = listOf("conflicting independently corroborated codec/table authorities; no default or duplicate locale projection"))
        }
        return authorities.singleOrNull() ?: manifests.lastOrNull() ?: RomLanguageManifest.UNKNOWN
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

    private fun nativeLanguagePlausibility(
        rom: RomImage, species: TableLayout, moves: TableLayout, generation: Int,
        codec: PokemonTextCodec, cancellation: ParserCancellationToken,
    ): LanguagePlausibility {
        val absent = LanguagePlausibility(0, 0.0, 0.0)
        val firstIndex = if (generation == 3) 1 else 0
        val speciesNames = sampleNativeNames(rom, species, firstIndex, generation, codec, cancellation) ?: return absent
        val moveNames = sampleNativeNames(rom, moves, firstIndex, generation, codec, cancellation) ?: return absent
        if (speciesNames.distinct().size < 3 || moveNames.distinct().size < 6) return absent
        if ((speciesNames + moveNames).any { !LanguageTextPlausibility.looksLikeStandaloneFixedName(it, codec.language) }) return absent
        val trigrams = moveNames.distinct().flatMap(::nativeTrigrams).toSet()
        if (trigrams.isEmpty()) return absent
        val profile = NATIVE_CHARACTER_PROFILES.getValue(codec.language)
        val matches = trigrams.count(profile::contains)
        if (matches < MINIMUM_PLAUSIBILITY_TRIGRAMS) return absent
        val competing = NATIVE_CHARACTER_PROFILES.filterKeys { it != codec.language }.values
            .maxOf { other -> trigrams.count(other::contains).toDouble() / trigrams.size }
        return LanguagePlausibility(trigrams.size, matches.toDouble() / trigrams.size, competing)
    }

    /** Variable roots are traversed to their entire declared end: a good prefix cannot hide a tail.
     * Fixed/pointer samples keep bounded complete records; only GB fixed-width glyphs may omit EOS.
     */
    private fun sampleNativeNames(
        rom: RomImage, layout: TableLayout, firstIndex: Int, generation: Int,
        codec: PokemonTextCodec, cancellation: ParserCancellationToken,
    ): List<String>? {
        cancellation.throwIfCancellationRequested()
        if (layout.count !in 1..2048 || layout.offset !in 0 until rom.size) return null
        val width = layout.recordSize
        val stride = layout.stride ?: width
        if (!layout.variableLength && (width !in 1..MAXIMUM_CONTROL_BYTES || stride < width ||
                layout.valuesArePointers && width < 4)) return null
        val names = mutableListOf<String>()
        var cursor = layout.offset
        val dataEnd = if (generation < 3) minOf(rom.size.toLong(), (layout.offset / 0x4000 + 1L) * 0x4000).toInt() else rom.size
        val end = if (layout.variableLength) layout.count else minOf(layout.count, firstIndex + MAXIMUM_PLAUSIBILITY_RECORDS)
        for (index in 0 until end) {
            cancellation.throwIfCancellationRequested()
            var offset = if (layout.variableLength) cursor.toLong() else layout.offset.toLong() + index.toLong() * stride
            if (offset < 0 || offset >= dataEnd) return null
            if (layout.valuesArePointers) {
                if (offset + 4 > rom.size) return null
                offset = (rom.gbaPointer(offset.toInt()) ?: return null).toLong()
            }
            val fixed = !layout.variableLength && !layout.valuesArePointers
            val bytes = if (fixed) width else minOf(MAXIMUM_CONTROL_BYTES, dataEnd - offset.toInt())
            if (offset + bytes > dataEnd) return null
            val decoded = codec.decodeDetailed(rom, offset.toInt(), bytes, cancellation)
            if (!decoded.terminated && !(fixed && generation < 3 && decoded.contentBytes == width &&
                    decoded.validBytes == width && decoded.controlUnits == 0 && decoded.substitutionUnits == 0)) return null
            if (layout.variableLength) cursor += decoded.consumedBytes
            if (index < firstIndex) continue
            if (decoded.invalidUnits != 0 || decoded.controlUnits != 0 || decoded.substitutionUnits != 0 || decoded.text.isBlank()) return null
            if (index < firstIndex + MAXIMUM_PLAUSIBILITY_RECORDS) names += decoded.text
        }
        return names
    }

    /** Normalize kana only for lexical comparison, never the catalog's exact decoded spelling. */
    private fun nativeTrigrams(value: String): List<String> {
        val normalized = value.map { if (it in 'ァ'..'ヶ') (it.code - 0x60).toChar() else it }.joinToString("")
        return NATIVE_WORD_PATTERN.findAll(normalized).flatMap { match ->
            "^${match.value}$".windowed(3).asSequence()
        }.toList()
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

    private val NATIVE_WORD_PATTERN = Regex("[\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}ー]+")
    // Public source-derived lexical features, not ordered anchors or an exact-name acceptance list.
    // pokered-jp 258d1a89, text/move_names.asm; pokegold-kr 7743877d, data/moves/names.asm.
    private val NATIVE_CHARACTER_PROFILES = mapOf(
        LanguageTag.JAPANESE to nativeTrigrams(
            "はたく からてチョップ おうふくビンタ れんぞくパンチ メガトンパンチ ネコにこばん " +
                "ほのおのパンチ れいとうパンチ かみなりパンチ ひっかく はさむ ハサミギロチン " +
                "かまいたち つるぎのまい いあいぎり かぜおこし つばさでうつ ふきとばし そらをとぶ " +
                "しめつける たたきつける つるのムチ ふみつけ にどげり メガトンキック とびげり " +
                "まわしげり すなかけ ずつき つのでつく みだれづき つのドリル たいあたり のしかかり " +
                "まきつく とっしん あばれる すてみタックル しっぽをふる どくばり ダブルニードル " +
                "ミサイルばり にらみつける かみつく なきごえ ほえる うたう ちょうおんぱ ソニックブーム " +
                "かなしばり ようかいえき ひのこ かえんほうしゃ しろいきり みずでっぽう ハイドロポンプ " +
                "なみのり れいとうビーム ふぶき サイケこうせん バブルこうせん オーロラビーム はかいこうせん",
        ).toSet(),
        LanguageTag.KOREAN to nativeTrigrams(
            "막치기 태권당수 연속 뺨치기 연속펀치 메가톤펀치 고양이돈받기 불꽃펀치 냉동펀치 번개펀치 " +
                "할퀴기 찝기 가위자르기 칼바람 칼춤 풀베기 바람일으키기 날개치기 날려버리기 공중날기 " +
                "조이기 힘껏치기 덩쿨채찍 짓밟기 두번치기 메가톤킥 점프킥 돌려차기 모래뿌리기 박치기 " +
                "뿔찌르기 마구찌르기 뿔드릴 몸통박치기 누르기 김밥말이 돌진 난동부리기 이판사판태클 " +
                "꼬리흔들기 독침 더블니들 바늘미사일 째려보기 물기 울음소리 울부짖기 노래하기 초음파 " +
                "소닉붐 사슬묶기 용해액 불꽃세례 화염방사 흰안개 물대포 하이드로펌프 파도타기 냉동빔 " +
                "눈보라 환상빔 거품광선 오로라 빔 파괴광선",
        ).toSet(),
    )
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
