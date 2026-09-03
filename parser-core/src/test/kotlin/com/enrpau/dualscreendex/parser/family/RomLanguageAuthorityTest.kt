package com.enrpau.dualscreendex.parser.family

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.LanguageEvidenceKind
import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.WesternPokemonTextCodecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomLanguageAuthorityTest {
    @Test
    fun resolvesEnglishFromSelectedVariableTablesAndEnglishControls() {
        val bytes = encodeGbNames(
            "POUND",
            "KARATE CHOP",
            "DOUBLESLAP",
            "SMOLDER JAB",
            "DUSKY BREAKER",
            "BROOK WARD",
        )
        val manifest = resolve(
            rom = RomImage(bytes),
            header = RomHeader(
                platform = Platform.GBC,
                title = "POKEMON_GLD",
                cgbFlag = 0x80,
                gbManufacturerCode = "AAUE",
            ),
            generation = 2,
            codec = PokemonTextCodec.gbEnglish,
            moveNames = TableLayout(0, 6, 0, variableLength = true),
        )

        assertEquals(LanguageResolutionStatus.RESOLVED, manifest.status)
        assertEquals(LanguageTag.ENGLISH, manifest.defaultLanguage)
        assertEquals(PokemonTextCodec.gbEnglish.id, manifest.defaultProjection()?.codecId)
        assertEquals(
            setOf(
                LanguageEvidenceKind.HEADER_REGION_HINT,
                LanguageEvidenceKind.TABLE_RELATIONSHIP,
                LanguageEvidenceKind.CODEC_PLAUSIBILITY,
                LanguageEvidenceKind.RETAIL_VALIDATION_CONTROL,
            ),
            manifest.defaultProjection()?.evidence?.map { it.kind }?.toSet(),
        )
    }

    @Test
    fun resolvesEveryRatifiedWesternLanguageFromBoundedContentEvidence() {
        val cases = mapOf(
            LanguageTag.FRENCH to listOf(
                "BRULER FLAMME", "ECLAIR TONNERRE", "PIERRE ROCHE", "OMBRE SOMBRE", "GRIFFE CROC",
            ),
            LanguageTag.GERMAN to listOf(
                "BRENNEN FUNKE", "BLITZ DONNER", "WASSER FLUSS", "SCHATTEN DUNKEL", "KRALLE ZAHN",
            ),
            LanguageTag.ITALIAN to listOf(
                "BRUCIARE FIAMMA", "SCINTILLA FULMINE", "ACQUA FIUME", "OMBRA SCURO", "ARTIGLIO ZANNA",
            ),
            LanguageTag.SPANISH to listOf(
                "QUEMAR LLAMA", "CHISPA TRUENO", "AGUA MAREA", "SOMBRA OSCURO", "GARRA COLMILLO",
            ),
        )
        val markerByLanguage = mapOf(
            LanguageTag.FRENCH to 'F',
            LanguageTag.GERMAN to 'D',
            LanguageTag.ITALIAN to 'I',
            LanguageTag.SPANISH to 'S',
        )

        for ((language, names) in cases) {
            val codec = requireNotNull(WesternPokemonTextCodecs.forLanguage(language, generation = 2))
            val manifest = resolve(
                rom = RomImage(encodeGbNames(*names.toTypedArray())),
                header = RomHeader(
                    platform = Platform.GBC,
                    title = "POKEMON_GLD",
                    cgbFlag = 0x80,
                    gbManufacturerCode = "AAU${markerByLanguage.getValue(language)}",
                ),
                generation = 2,
                codec = codec,
                moveNames = TableLayout(0, names.size, 0, variableLength = true),
            )

            assertEquals(language.value, LanguageResolutionStatus.RESOLVED, manifest.status)
            assertEquals(language.value, language, manifest.defaultLanguage)
            assertEquals(language.value, codec.id, manifest.defaultProjection()?.codecId)
        }
    }

    @Test
    fun resolvesEnglishControlsWithoutTreatingARegionalHeaderAsAuthority() {
        val width = 18
        val names = listOf(
            "POUND",
            "KARATE CHOP",
            "DOUBLESLAP",
            "SMOLDER JAB",
            "DUSKY BREAKER",
            "BROOK WARD",
        )
        val bytes = ByteArray((names.size + 1) * width) { 0xFF.toByte() }
        names.forEachIndexed { index, name ->
            encodeGbaName(bytes, (index + 1) * width, width, name)
        }

        val manifest = resolve(
            rom = RomImage(bytes),
            header = RomHeader(Platform.GBA, "CUSTOM", gameCode = "BPEF"),
            generation = 3,
            codec = PokemonTextCodec.gbaEnglish,
            moveNames = TableLayout(0, names.size + 1, width),
        )

        assertEquals(LanguageResolutionStatus.RESOLVED, manifest.status)
        assertFalse(
            manifest.defaultProjection()?.evidence.orEmpty()
                .any { it.kind == LanguageEvidenceKind.HEADER_REGION_HINT },
        )
    }

    @Test
    fun resolvesEnglishFromBoundedPointerBackedMoveControls() {
        val names = listOf(
            "Pound",
            "Karate Chop",
            "Double Slap",
            "Smolder Jab",
            "Dusky Breaker",
            "Brook Ward",
        )
        val bytes = ByteArray(256) { 0xFF.toByte() }
        names.forEachIndexed { index, name ->
            val textOffset = 64 + index * 32
            writeGbaPointer(bytes, (index + 1) * 4, textOffset)
            encodeGbaName(bytes, textOffset, 24, name)
        }

        val manifest = resolve(
            rom = RomImage(bytes),
            header = RomHeader(Platform.GBA, "CUSTOM", gameCode = "BPEF"),
            generation = 3,
            codec = PokemonTextCodec.gbaEnglish,
            moveNames = TableLayout(0, names.size + 1, 4, valuesArePointers = true),
        )

        assertEquals(LanguageResolutionStatus.RESOLVED, manifest.status)
        assertEquals(LanguageTag.ENGLISH, manifest.defaultLanguage)
    }

    @Test
    fun resolvesCustomEnglishNamesWithoutRetailLiterals() {
        val width = 18
        val names = listOf("SMOLDER JAB", "DUSKY BREAKER", "BROOK WARD")
        val bytes = ByteArray((names.size + 1) * width) { 0xFF.toByte() }
        names.forEachIndexed { index, name ->
            encodeGbaName(bytes, (index + 1) * width, width, name)
        }

        val manifest = resolve(
            rom = RomImage(bytes),
            header = RomHeader(Platform.GBA, "CUSTOM", gameCode = "BPEE"),
            generation = 3,
            codec = PokemonTextCodec.gbaEnglish,
            moveNames = TableLayout(0, names.size + 1, width),
        )

        assertEquals(LanguageResolutionStatus.RESOLVED, manifest.status)
        assertEquals(LanguageTag.ENGLISH, manifest.defaultLanguage)
        assertTrue(
            manifest.defaultProjection()?.evidence.orEmpty()
                .none { it.kind == LanguageEvidenceKind.RETAIL_VALIDATION_CONTROL },
        )
    }

    @Test
    fun rejectsStructurallyCompatibleNonEnglishTextWithoutEnglishCorroboration() {
        val bytes = ByteArray(64) { 0xFF.toByte() }
        encodeGbaName(bytes, 13, 13, "CHARGE")
        encodeGbaName(bytes, 26, 13, "FORCE")
        encodeGbaName(bytes, 39, 13, "CLAQUE")

        val manifest = resolve(
            rom = RomImage(bytes),
            header = RomHeader(Platform.GBA, "POKEMON EMER", gameCode = "BPEF"),
            generation = 3,
            codec = PokemonTextCodec.gbaEnglish,
            moveNames = TableLayout(0, 4, 13),
        )

        assertEquals(LanguageResolutionStatus.UNKNOWN, manifest.status)
        assertNull(manifest.defaultLanguage)
        assertTrue(manifest.projections.isEmpty())
    }

    @Test
    fun regionalEnglishHeaderCannotRelabelStructurallyValidFrenchText() {
        val bytes = ByteArray(64) { 0xFF.toByte() }
        encodeGbaName(bytes, 13, 13, "CHARGE")
        encodeGbaName(bytes, 26, 13, "FORCE")
        encodeGbaName(bytes, 39, 13, "CLAQUE")

        val manifest = resolve(
            rom = RomImage(bytes),
            header = RomHeader(Platform.GBA, "CUSTOM", gameCode = "BPEE"),
            generation = 3,
            codec = PokemonTextCodec.gbaEnglish,
            moveNames = TableLayout(0, 4, 13),
        )

        assertEquals(LanguageResolutionStatus.UNKNOWN, manifest.status)
        assertNull(manifest.defaultLanguage)
        assertTrue(manifest.projections.isEmpty())
    }

    @Test
    fun canonicalEnglishControlsCannotOverridePredominantlyFrenchNames() {
        val names = arrayOf(
            "POUND",
            "KARATE CHOP",
            "DOUBLESLAP",
            "CHARGE",
            "FORCE",
            "CLAQUE",
            "POING ARDENT",
            "OMBRE NOIRE",
            "GARDE PIERRE",
        )
        val manifest = resolve(
            rom = RomImage(encodeGbNames(*names)),
            header = RomHeader(Platform.GBC, "POKEMON_GLD", gbManufacturerCode = "AAUE"),
            generation = 2,
            codec = PokemonTextCodec.gbEnglish,
            moveNames = TableLayout(0, names.size, 0, variableLength = true),
        )

        assertEquals(LanguageResolutionStatus.UNKNOWN, manifest.status)
        assertNull(manifest.defaultLanguage)
        assertTrue(manifest.projections.isEmpty())
    }

    @Test
    fun requiresBothSelectedNameTables() {
        val manifest = RomLanguageAuthority.resolve(
            rom = RomImage(encodeGbNames("POUND", "KARATE CHOP", "DOUBLESLAP")),
            header = RomHeader(Platform.GBC, "POKEMON_GLD", gbManufacturerCode = "AAUE"),
            generation = 2,
            probeCodec = PokemonTextCodec.gbEnglish,
            speciesNamesEvidence = compatibleEvidence(3, 10),
            moveNamesEvidence = incompatibleEvidence(),
            speciesNamesLayout = TableLayout(32, 3, 10),
            moveNamesLayout = null,
            cancellation = ParserCancellationToken.NONE,
        )

        assertEquals(LanguageResolutionStatus.UNKNOWN, manifest.status)
    }

    @Test
    fun rejectsTruncatedControlsAndCodecApplicabilityMismatch() {
        val truncated = resolve(
            rom = RomImage(encodeGbNames("POUND", "KARATE CHOP")),
            header = RomHeader(Platform.GBC, "CUSTOM", gbManufacturerCode = "AAUF"),
            generation = 2,
            codec = PokemonTextCodec.gbEnglish,
            moveNames = TableLayout(0, 3, 0, variableLength = true),
        )
        val mismatched = resolve(
            rom = RomImage(encodeGbNames("POUND", "KARATE CHOP", "DOUBLESLAP")),
            header = RomHeader(Platform.GBA, "CUSTOM", gameCode = "BPEE"),
            generation = 3,
            codec = PokemonTextCodec.gbEnglish,
            moveNames = TableLayout(0, 3, 0, variableLength = true),
        )

        assertEquals(LanguageResolutionStatus.UNKNOWN, truncated.status)
        assertEquals(LanguageResolutionStatus.UNKNOWN, mismatched.status)
    }

    private fun resolve(
        rom: RomImage,
        header: RomHeader,
        generation: Int,
        codec: PokemonTextCodec,
        moveNames: TableLayout,
    ) = RomLanguageAuthority.resolve(
        rom = rom,
        header = header,
        generation = generation,
        probeCodec = codec,
        speciesNamesEvidence = compatibleEvidence(3, 10),
        moveNamesEvidence = compatibleEvidence(moveNames.count, moveNames.recordSize),
        speciesNamesLayout = TableLayout(32, 3, 10),
        moveNamesLayout = moveNames,
        cancellation = ParserCancellationToken.NONE,
    )

    private fun compatibleEvidence(count: Int, recordSize: Int) = ValidationEvidence(
        compatible = true,
        validRecords = count,
        totalRecords = count,
        confidence = 1.0,
        reasons = listOf("structurally selected synthetic table"),
        offset = 0,
        recordSize = recordSize,
    )

    private fun incompatibleEvidence() = ValidationEvidence(
        compatible = false,
        validRecords = 0,
        totalRecords = 0,
        confidence = 0.0,
        reasons = listOf("synthetic rejection"),
    )

    private fun encodeGbNames(vararg names: String): ByteArray = buildList<Byte> {
        names.forEach { name ->
            name.forEach { character ->
                add(
                    when (character) {
                        ' ' -> 0x7F.toByte()
                        else -> (0x80 + character.code - 'A'.code).toByte()
                    },
                )
            }
            add(0x50)
        }
    }.toByteArray()

    private fun writeGbaPointer(bytes: ByteArray, offset: Int, target: Int) {
        val pointer = 0x08000000 + target
        repeat(4) { index -> bytes[offset + index] = (pointer ushr (index * 8)).toByte() }
    }

    private fun encodeGbaName(bytes: ByteArray, offset: Int, width: Int, name: String) {
        name.forEachIndexed { index, character ->
            bytes[offset + index] = when (character) {
                ' ' -> 0x00
                in 'A'..'Z' -> (0xBB + character.code - 'A'.code).toByte()
                in 'a'..'z' -> (0xD5 + character.code - 'a'.code).toByte()
                else -> error("unsupported fixture character")
            }
        }
        bytes[offset + minOf(name.length, width - 1)] = 0xFF.toByte()
    }
}
