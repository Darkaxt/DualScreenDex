package com.enrpau.dualscreendex.parser.text

/**
 * Independent oracle: synthetic bytes and literal expected text, tokens and all integer counters.
 * Authorities are the existing four *TextCodec(s)Test classes and their source references in
 * stage-04-codec-checkpoint.md. Shared vectors reuse proven dialect semantics, never decoder output.
 * Charset names below identify the source table/consumer dialect, not a Java character encoding.
 */
internal object CodecGoldenVectors {
    data class Identity(val id: String, val language: String, val generation: Int, val charset: String,
                        val platforms: List<String>, val terminator: Int, val version: Int = 1)
    data class Token(val kind: String, val text: String, val byteCount: Int = 1)
    data class Expected(val decoded: DecodedText?, val tokens: List<Token>?, val checks: Int,
                        val outcome: String = "DECODED")
    data class Vector(val name: String, val authority: String, val hex: String, val maximumBytes: Int,
                      val expected: Expected, val offset: Int = 0, val cancelAt: Int = 0)
    data class Case(val identity: Identity, val codec: PokemonTextCodec, val vectors: List<Vector>)

    private const val W = "WesternPokemonTextCodecsTest"
    private const val J = "JapanesePokemonTextCodecsTest"
    private const val K = "KoreanGen2PokemonTextCodecTest"
    private const val B = "PokemonTextCodecTest"
    private fun g(s: String, n: Int = 1) = Token("GLYPH", s, n)
    private fun s(s: String) = Token("SUBSTITUTION", s)
    private fun c(n: Int = 1) = Token("CONTROL", " ", n)
    private fun w(n: Int = 1) = Token("WHITESPACE", " ", n)
    private fun i(n: Int = 1) = Token("INVALID", "", n)
    private fun t() = Token("TERMINATOR", "")
    private fun d(text: String, term: Boolean, vb: Int, cb: Int, vu: Int, cu: Int, consumed: Int,
                  glyph: Int, space: Int, sub: Int, control: Int, invalid: Int) =
        DecodedText(text, term, vb, cb, vu, cu, consumed, glyph, space, sub, control, invalid)
    private val empty = d("", false, 0,0,0,0,0,0,0,0,0,0)
    private fun v(name: String, authority: String, hex: String, max: Int, text: DecodedText,
                  checks: Int, vararg tokens: Token) = Vector(name, authority, hex, max, Expected(text, tokens.toList(), checks))

    // Bounds use the glyph A / ア / あ / 가 already declared by the reference tests.
    private fun bounds(hex: String, max: Int, glyph: String, width: Int): List<Vector> {
        val one = if (width == 2) d(glyph,false,2,2,1,1,2,1,0,0,0,0) else d(glyph,false,1,1,1,1,1,1,0,0,0,0)
        return listOf(
            v("empty", "$B.stopsAtEofAndHonorsCancellation", "", 0, empty, 0),
            v("zero-window", "$B.decodesDirectlyFromABoundedRomWindow", hex, 0, empty, 0),
            v("offset-at-eof", "$B.stopsAtEofAndHonorsCancellation", hex, 10, empty, 0).copy(offset=max),
            v("offset-window", "$B.decodesDirectlyFromABoundedRomWindow", "00 $hex", width, one, 1, g(glyph,width)).copy(offset=1),
            v("maximum-window", "$B.reportsControlsInvalidUnitsAndTruncation", hex, width, one, 1, g(glyph,width)),
            v("eof-large-maximum", "$B.stopsAtEofAndHonorsCancellation", hex.substringBeforeLast(' '), Int.MAX_VALUE, one, 1, g(glyph,width)),
            Vector("cancel-first", "$B.stopsAtEofAndHonorsCancellation", hex, max, Expected(null,null,1,"CANCELLED"), cancelAt=1),
            Vector("cancel-next", "$B.checksCancellationBetweenVariableWidthTokens", hex, max, Expected(null,null,2,"CANCELLED"), cancelAt=2),
            Vector("negative-offset", "PokemonTextCodec.decodeDetailed bounds", hex, max, Expected(null,null,0,"INVALID_BOUNDS"), offset=-1),
            Vector("past-eof", "PokemonTextCodec.decodeDetailed bounds", hex, max, Expected(null,null,0,"INVALID_BOUNDS"), offset=max+1),
            Vector("negative-maximum", "PokemonTextCodec.decodeDetailed bounds", hex, -1, Expected(null,null,0,"INVALID_BOUNDS")),
        )
    }

    private val gbCommon = listOf(
        v("controls-substitution", "$W.classifiesWesternControlsSubstitutionsInvalidBytesAndTermination", "8f 54 4e 80 50 81", 6,
            d("PPOKé A",true,4,4,4,4,5,2,0,1,1,0),5,g("P"),s("POKé"),c(),g("A"),t()),
        v("static-substitution-space", "$W.staticLabelPolicyPreservesEveryGeneralCounterAndOnlyRatifiesWesternGenTwo54", "54 7f 80 50",4,
            d("POKé A",true,3,3,3,3,4,1,1,1,0,0),4,s("POKé"),w(),g("A"),t()),
        v("invalid", "WesternPokemonTextCodecs.gbCodec unmapped byte", "01 50",2,
            d("",true,0,1,0,1,2,0,0,0,0,1),2,i(),t()),
    ) + bounds("80 50",2,"A",1)
    private val french = listOf(
        v("overlay", "$W.decodesDistinctGenOneWesternOverlays", "ba be cc d4 df 50",6,
            d("àßîc'y'",true,5,5,5,5,6,3,0,2,0,0),6,g("à"),g("ß"),g("î"),s("c'"),s("y'"),t()),
        v("plus-glyph", "$W.frenchGermanPlusIsALiteralGlyphNotASubstitution", "80 7f e4 50 54",5,
            d("A +",true,3,3,3,3,4,2,1,0,0,0),4,g("A"),w(),g("+"),t()),
    )
    private val italian = listOf(v("overlay", "$W.decodesDistinctGenOneWesternOverlays", "be c6 ca d2 d4 e4 e5 50",8,
        d("ÀÈÑñó¿¡",true,7,7,7,7,8,7,0,0,0,0),8,g("À"),g("È"),g("Ñ"),g("ñ"),g("ó"),g("¿"),g("¡"),t()))
    private val spanish = listOf(v("overlay", "$W.decodesDistinctGenOneWesternOverlays", "c9 ca cc d1 d2 50",6,
        d("ÍÑÓíñ",true,5,5,5,5,6,5,0,0,0,0),6,g("Í"),g("Ñ"),g("Ó"),g("í"),g("ñ"),t()))
    private val english1 = listOf(v("overlay", "$W.decodesDistinctGenOneWesternOverlays", "ba bb e4 e5 50",5,
        d("é'd'r'm",true,4,4,4,4,5,1,0,3,0,0),5,g("é"),s("'d"),s("'r"),s("'m"),t()))
    private val english2 = listOf(
        v("overlay", "$W.decodesDistinctGenTwoWesternOverlaysWithoutReusingGenOneSemantics", "c0 d0 d6 df ea 50",6,
            d("Ä'd'v←é",true,5,5,5,5,6,3,0,2,0,0),6,g("Ä"),s("'d"),s("'v"),g("←"),g("é"),t()),
        v("not-gen1-e4", "$W.plusCorrectionDoesNotChangeOtherE4DialectsOrRatifySubstitutions", "e4 50",2,
            d("",true,0,1,0,1,2,0,0,0,0,1),2,i(),t()),
    )
    private val french2 = listOf(v("gen2-overlay", "$W.decodesDistinctGenTwoWesternOverlaysWithoutReusingGenOneSemantics", "ba cc d4 df ea 50",6,
        d("àîc'y'é",true,5,5,5,5,6,3,0,2,0,0),6,g("à"),g("î"),s("c'"),s("y'"),g("é"),t()))
    private val italian2 = listOf(v("gen2-overlay", "$W.decodesDistinctGenTwoWesternOverlaysWithoutReusingGenOneSemantics", "be c6 c9 ca d4 e4 e5 ea 50",9,
        d("ÀÈÍÑó¿¡é",true,8,8,8,8,9,8,0,0,0,0),9,g("À"),g("È"),g("Í"),g("Ñ"),g("ó"),g("¿"),g("¡"),g("é"),t()))
    private val gbaCommon = listOf(
        v("accented-core", "$W.decodesTheCommonGenThreeCoreAndLocaleSpecificQuotes", "5a 6f 29 2a 2b f1 f5 ff",8,
            d("ÍíñºªÄö",true,7,7,7,7,8,7,0,0,0,0),8,g("Í"),g("í"),g("ñ"),g("º"),g("ª"),g("Ä"),g("ö"),t()),
        v("control-widths", "$W.classifiesWesternControlsSubstitutionsInvalidBytesAndTermination", "bb fc 0c fd 01 bc 0a ff",8,
            d("A B",true,6,7,4,5,8,2,0,0,2,1),6,g("A"),c(2),c(2),g("B"),i(),t()),
        v("truncated-escape", "$W.keepsInvalidAndTruncatedEscapeSequencesBounded", "fc",1,
            d("",false,0,1,0,1,1,0,0,0,0,1),1,i()),
        v("maximum-escape-window", "$W.keepsInvalidAndTruncatedEscapeSequencesBounded", "fc 0c ff",1,
            d("",false,0,1,0,1,1,0,0,0,0,1),1,i()),
        v("substitution-space", "WesternPokemonTextCodecs.gbaCodec static PK/MN declarations", "53 00 54 ff",4,
            d("PK MN",true,3,3,3,3,4,0,1,2,0,0),4,s("PK"),w(),s("MN"),t()),
    ) + bounds("bb ff",2,"A",1)

    private val jpGb = listOf(
        v("kana", "$J.decodesRedBlueKanaPunctuationDigitsAndSubstitutions", "80 05 b1 26 50",5,
            d("アガあが",true,4,4,4,4,5,4,0,0,0,0),5,g("ア"),g("ガ"),g("あ"),g("が"),t()),
        v("substitution-control", "$J.decodesRedBlueKanaPunctuationDigitsAndSubstitutions", "80 54 4e 5c b1 50 81",7,
            d("アポケモン わざマシンあ",true,5,5,5,5,6,2,0,2,1,0),6,g("ア"),s("ポケモン"),c(),s("わざマシン"),g("あ"),t()),
        v("invalid-space", "JapanesePokemonTextCodecs.gbCodec unmapped byte and whitespace", "01 7f 50",3,
            d("",true,1,2,1,2,3,0,1,0,0,1),3,i(),w(),t()),
    ) + bounds("80 50",2,"ア",1)
    private val jp1 = listOf(v("punctuation", "$J.preservesYellowPunctuationWithoutChangingRedBlue", "74 75 56 50",4,
        d("・………",true,3,3,3,3,4,2,0,1,0,0),4,g("・"),g("…"),s("……"),t()),
        v("digits", "$J.decodesRedBlueKanaPunctuationDigitsAndSubstitutions", "f2 f3 f4 f6 50",5,
            d("⠄/,0",true,4,4,4,4,5,4,0,0,0,0),5,g("⠄"),g("/"),g(","),g("0"),t()))
    private val jpYellow = listOf(v("punctuation", "$J.preservesYellowPunctuationWithoutChangingRedBlue", "74 75 56 50",4,
        d("·⋯⋯⋯",true,3,3,3,3,4,2,0,1,0,0),4,g("·"),g("⋯"),s("⋯⋯"),t()),
        v("yellow-substitution-digits", "$J.decodesYellowSpecificSubstitutionPunctuationKanaAndFullWidthDigits", "4a e4 e5 f2 f3 f4 f6 50",8,
            d("が ゜゛．／ォ０",true,7,7,7,7,8,6,0,1,0,0),8,s("が "),g("゜"),g("゛"),g("．"),g("／"),g("ォ"),g("０"),t()))
    private val jp2 = listOf(v("dictionary", "$J.decodesGenerationTwoKanaDictionaryTokensAndFullWidthDigits", "23 7f 35 50",4,
        d("こうげき ばん どうろ",true,3,3,3,3,4,0,1,2,0,0),4,s("こうげき"),w(),s("ばん どうろ"),t()),
        v("dictionary-spacing", "$J.decodesGenerationTwoKanaDictionaryTokensAndFullWidthDigits", "37 80 56 50",4,
            d("ここは ア⋯⋯",true,3,3,3,3,4,1,0,2,0,0),4,s("ここは "),g("ア"),s("⋯⋯"),t()),
        v("fullwidth-digit", "$J.decodesGenerationTwoKanaDictionaryTokensAndFullWidthDigits", "f6 50",2,
            d("０",true,1,1,1,1,2,1,0,0,0,0),2,g("０"),t()))
    private val jpGba = listOf(
        v("kana-punctuation", "$J.decodesGenerationThreeHiraganaKatakanaAndPunctuation", "01 37 51 87 a1 ab ac ad ae af b0 ff",12,
            d("あがアガ0！？。ー·⋯",true,11,11,11,11,12,11,0,0,0,0),12,g("あ"),g("が"),g("ア"),g("ガ"),g("0"),g("！"),g("？"),g("。"),g("ー"),g("·"),g("⋯"),t()),
        v("extended-width", "$J.appliesDialectSpecificExtendedControlArityWithinTheByteWindow", "fc 11 80 ff",4,
            d("",true,3,3,1,1,4,0,0,0,1,0),2,c(3),t()),
        v("truncated-extended", "$J.appliesDialectSpecificExtendedControlArityWithinTheByteWindow", "fc 11",2,
            d("",false,0,2,0,1,2,0,0,0,0,1),1,i(2)),
        v("unknown-extended", "$J.rejectsUnknownAndTruncatedGenerationThreeControlsWithinTheByteWindow", "fc 19 ff",3,
            d("",true,0,2,0,1,3,0,0,0,0,1),2,i(2),t()),
        v("truncated-five-byte-control", "$J.rejectsUnknownAndTruncatedGenerationThreeControlsWithinTheByteWindow", "fc 04 01",3,
            d("",false,0,3,0,1,3,0,0,0,0,1),1,i(3)),
        v("maximum-extended-window", "$J.rejectsUnknownAndTruncatedGenerationThreeControlsWithinTheByteWindow", "fc 04 01 02 03 ff",3,
            d("",false,0,3,0,1,3,0,0,0,0,1),1,i(3)),
        v("control-window", "$J.consumesGenerationThreeControlsAtTheirExactArity", "01 fc 04 01 02 03 fd 0d fe 51 ff",11,
            d("あ ア",true,10,10,5,5,11,2,0,0,3,0),6,g("あ"),c(5),c(2),c(),g("ア"),t()),
        v("space", "JapanesePokemonTextCodecs.decodeGen3Token whitespace", "01 00 51 ff",4,
            d("あ ア",true,3,3,3,3,4,2,1,0,0,0),4,g("あ"),w(),g("ア"),t()),
    ) + bounds("01 ff",2,"あ",1)
    private val jpRuby = listOf(v("arrows-not-controls", "$J.distinguishesRubySapphireGlyphsFromLaterGenerationThreeControls", "f7 f8 f9 ff",4,
        d("↑↓←",true,3,3,3,3,4,3,0,0,0,0),4,g("↑"),g("↓"),g("←"),t()),
        v("later-only-control", "$J.rejectsLaterOnlyExtendedControlsInRubySapphire", "fc 17 ff",3,
            d("",true,0,2,0,1,3,0,0,0,0,1),2,i(2),t()))
    private val jpLater = listOf(v("dynamic-controls", "$J.distinguishesRubySapphireGlyphsFromLaterGenerationThreeControls", "f7 01 f8 02 f9 03 ff",7,
        d("",true,6,6,3,3,7,0,0,0,3,0),4,c(2),c(2),c(2),t()),
        v("later-only-control", "$J.rejectsLaterOnlyExtendedControlsInRubySapphire", "fc 17 ff",3,
            d("",true,2,2,1,1,3,0,0,0,1,0),2,c(2),t()),
        v("truncated-dynamic", "$J.rejectsUnknownAndTruncatedGenerationThreeControlsWithinTheByteWindow", "f7",1,
            d("",false,0,1,0,1,1,0,0,0,0,1),1,i()))
    private val korean = listOf(
        v("hangul-jamo", "$K.decodesSourceBackedJamoAndHangulAdditions", "0b 00 01 42 01 43 01 7f 07 8c 03 d0 06 2f 06 30 06 a0 08 30 50",21,
            d("ㄱ겹겸괻읆뢔쌰쎼쓔쬬",true,20,20,10,10,21,10,0,0,0,0),11,g("ㄱ",2),g("겹",2),g("겸",2),g("괻",2),g("읆",2),g("뢔",2),g("쌰",2),g("쎼",2),g("쓔",2),g("쬬",2),t()),
        v("null-control", "$K.keepsNullControlDistinctFromTableElevenLead", "00 0b 00 50",4,
            d("ㄱ",true,3,3,2,2,4,1,0,0,1,0),3,c(),g("ㄱ",2),t()),
        v("pair-space-trail-terminator", "$K.decodesPairAndSingleByteWhitespaceWithoutConfusingTrailWithTerminator", "01 01 0b ff 01 50 7f 01 02 50",10,
            d("가 각",true,7,9,4,5,10,2,2,0,0,1),6,g("가",2),w(2),i(2),w(),g("각",2),t()),
        v("native-substitutions", "$K.decodesNativeStaticSubstitutionsAndKeepsRuntimeControlsDistinct", "1f 7f 47 7f 49 4a 4b 4d 4e 55 5b 5a 50",13,
            d("こうげき 포켓몬 컴퓨터기술머신로켓단레드그린트레이너어머니",true,12,12,12,12,13,0,2,9,1,0),13,s("こうげき"),w(),s("포켓몬"),w(),s("컴퓨터"),s("기술머신"),s("로켓단"),s("레드"),s("그린"),s("트레이너"),s("어머니"),c(),t()),
        v("single-byte-glyphs", "$K.decodesCanonicalSingleByteGlyphsAfterTheLeadRange", "33 70 71 80 b9 c0 d0 e7 e6 f6 50",11,
            d("POKéPOKéAzÄ'd!?0",true,10,10,10,10,11,8,0,2,0,0),11,s("POKé"),g("PO"),g("Ké"),g("A"),g("z"),g("Ä"),s("'d"),g("!"),g("?"),g("0"),t()),
        v("invalid-pair", "$K.rejectsUnmappedAndTruncatedPairsWithoutCrossingTheByteWindow", "01 50 50",3,
            d("",true,0,2,0,1,3,0,0,0,0,1),2,i(2),t()),
        v("truncated-pair", "$K.rejectsUnmappedAndTruncatedPairsWithoutCrossingTheByteWindow", "01 42",1,
            d("",false,0,1,0,1,1,0,0,0,0,1),1,i()),
    ) + bounds("01 01 50",3,"가",2)

    val all: List<Case> by lazy {
        fun gb(id: String, lang: String, gen: Int, charset: String, codec: PokemonTextCodec, vectors: List<Vector>) =
            Case(Identity(id,lang,gen,charset,listOf("GB","GBC"),0x50),codec,vectors)
        fun gba(id: String, lang: String, charset: String, codec: PokemonTextCodec, vectors: List<Vector>) =
            Case(Identity(id,lang,3,charset,listOf("GBA"),0xff),codec,vectors)
        fun quotes(open: String, close: String) = listOf(v("quotes", "$W.decodesTheCommonGenThreeCoreAndLocaleSpecificQuotes", "b1 b2 ff",3,
            d(open+close,true,2,2,2,2,3,2,0,0,0,0),3,g(open),g(close),t()))
        listOf(
            gb("gb-gen1-en","en",1,"GEN1_ENGLISH",WesternPokemonTextCodecs.gen1English,gbCommon+english1),
            gb("gb-gen1-fr","fr",1,"GEN1_FRENCH_GERMAN",WesternPokemonTextCodecs.gen1French,gbCommon+french),
            gb("gb-gen1-de","de",1,"GEN1_FRENCH_GERMAN",WesternPokemonTextCodecs.gen1German,gbCommon+french),
            gb("gb-gen1-it","it",1,"GEN1_ITALIAN_SPANISH",WesternPokemonTextCodecs.gen1Italian,gbCommon+italian),
            gb("gb-gen1-es","es",1,"GEN1_ITALIAN_SPANISH",WesternPokemonTextCodecs.gen1Spanish,gbCommon+spanish),
            gb("gb-gen2-en","en",2,"GEN2_ENGLISH",WesternPokemonTextCodecs.gen2English,gbCommon+english2),
            gb("gb-gen2-fr","fr",2,"GEN2_FRENCH_GERMAN",WesternPokemonTextCodecs.gen2French,gbCommon+french+french2),
            gb("gb-gen2-de","de",2,"GEN2_FRENCH_GERMAN",WesternPokemonTextCodecs.gen2German,gbCommon+french+french2),
            gb("gb-gen2-it","it",2,"GEN2_ITALIAN_SPANISH",WesternPokemonTextCodecs.gen2Italian,gbCommon+italian+italian2),
            gb("gb-gen2-es","es",2,"GEN2_ITALIAN_SPANISH",WesternPokemonTextCodecs.gen2Spanish,gbCommon+spanish+italian2),
            gba("gba-gen3-en","en","GEN3_WESTERN_EN_QUOTES",WesternPokemonTextCodecs.gen3English,gbaCommon+quotes("“","”")),
            gba("gba-gen3-fr","fr","GEN3_WESTERN_FR_QUOTES",WesternPokemonTextCodecs.gen3French,gbaCommon+quotes("«","»")),
            gba("gba-gen3-de","de","GEN3_WESTERN_DE_QUOTES",WesternPokemonTextCodecs.gen3German,gbaCommon+quotes("„","“")),
            gba("gba-gen3-it","it","GEN3_WESTERN_EN_QUOTES",WesternPokemonTextCodecs.gen3Italian,gbaCommon+quotes("“","”")),
            gba("gba-gen3-es","es","GEN3_WESTERN_EN_QUOTES",WesternPokemonTextCodecs.gen3Spanish,gbaCommon+quotes("“","”")),
            gb("gb-gen1-ja-red-blue","ja",1,"GEN1_RED_BLUE_JAPANESE",JapanesePokemonTextCodecs.gen1RedBlue,jpGb+jp1),
            gb("gb-gen1-ja-yellow","ja",1,"GEN1_YELLOW_JAPANESE",JapanesePokemonTextCodecs.gen1Yellow,jpGb+jpYellow),
            gb("gb-gen2-ja","ja",2,"GEN2_JAPANESE",JapanesePokemonTextCodecs.gen2,jpGb+jp2),
            gba("gba-gen3-ja-ruby-sapphire","ja","GEN3_JAPANESE_RUBY_SAPPHIRE",JapanesePokemonTextCodecs.gen3RubySapphire,jpGba+jpRuby),
            gba("gba-gen3-ja-emerald-frlg","ja","GEN3_JAPANESE_EMERALD_FRLG",JapanesePokemonTextCodecs.gen3Later,jpGba+jpLater),
            Case(Identity("gb-gen2-ko","ko",2,"KOREAN_GEN2_CHARACTER_TABLES",listOf("GBC"),0x50),KoreanGen2PokemonTextCodec.codec,korean),
        )
    }
}
