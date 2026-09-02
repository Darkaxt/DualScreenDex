package com.enrpau.dualscreendex.parser.language

import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageModelsTest {
    @Test
    fun normalizesExtensibleLanguageTags() {
        assertEquals("de-DE", LanguageTag.of(" DE-de ").value)
        assertEquals("zh-Hant-TW", LanguageTag.of("zh-hant-tw").value)
        assertThrows(IllegalArgumentException::class.java) { LanguageTag.of("de_DE") }
    }

    @Test
    fun boundsEvidenceConfidence() {
        assertThrows(IllegalArgumentException::class.java) {
            LanguageEvidence(LanguageEvidenceKind.CODEC_PLAUSIBILITY, "invalid", 101)
        }
    }

    @Test
    fun rejectsDuplicateProjectionsAndDefaultsOutsideManifest() {
        val english = projection(LanguageTag.ENGLISH)
        assertThrows(IllegalArgumentException::class.java) {
            RomLanguageManifest(
                defaultLanguage = LanguageTag.ENGLISH,
                projections = listOf(english, english),
                status = LanguageResolutionStatus.RESOLVED,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RomLanguageManifest(
                defaultLanguage = LanguageTag.GERMAN,
                projections = listOf(english),
                status = LanguageResolutionStatus.RESOLVED,
            )
        }
    }

    @Test
    fun snapshotsProjectionCollectionsAndTableLayouts() {
        val evidence = mutableListOf(
            LanguageEvidence(LanguageEvidenceKind.HEADER_REGION_HINT, "official English header", 70),
        )
        val diagnostics = mutableListOf("resolved")
        val manifest = RomLanguageManifest(
            defaultLanguage = LanguageTag.ENGLISH,
            projections = listOf(
                RomLanguageProjection(
                    language = LanguageTag.ENGLISH,
                    codecId = "gb-english",
                    codecVersion = 1,
                    localizedTables = LocalizedTableLayout(
                        speciesNames = TableLayout(12, 151, 10, banks = mutableListOf(1, 2)),
                    ),
                    evidence = evidence,
                    status = LanguageResolutionStatus.RESOLVED,
                ),
            ),
            status = LanguageResolutionStatus.RESOLVED,
            diagnostics = diagnostics,
        )

        evidence += LanguageEvidence(LanguageEvidenceKind.CODEC_PLAUSIBILITY, "late mutation", 80)
        diagnostics += "late mutation"

        val projection = manifest.projections.single()
        assertEquals(1, projection.evidence.size)
        assertEquals(listOf("resolved"), manifest.diagnostics)
        assertEquals(listOf(1, 2), projection.localizedTables.speciesNames?.banks)
        assertNotSame(evidence, projection.evidence)
    }

    @Test
    fun unknownAndAmbiguousManifestsHaveNoDefaultCodec() {
        textUnavailableLanguageManifests.forEach { manifest ->
            val layout = ResolvedRomLayout(
                family = EngineFamily.EMERALD,
                generation = 3,
                platform = Platform.GBA,
                speciesCount = 0,
                moveCount = 0,
                tables = ProfileTables(),
                languageManifest = manifest,
            )

            assertNull(layout.defaultTextCodec())
        }
    }

    @Test
    fun resolvesOnlyAnExactCompatibleCodecIdentity() {
        val layout = languageLayout(
            generation = 2,
            platform = Platform.GBC,
            manifest = resolvedLanguageManifest(PokemonTextCodec.gbEnglish),
        )

        assertSame(PokemonTextCodec.gbEnglish, layout.defaultTextCodec())
    }

    @Test
    fun rejectsCodecLanguageGenerationAndPlatformMismatches() {
        val mismatchedLanguage = RomLanguageManifest(
            defaultLanguage = LanguageTag.JAPANESE,
            projections = listOf(
                projection(LanguageTag.JAPANESE, PokemonTextCodec.gbEnglish.id),
            ),
            status = LanguageResolutionStatus.RESOLVED,
        )
        assertNull(
            languageLayout(
                generation = 2,
                platform = Platform.GBC,
                manifest = mismatchedLanguage,
            ).defaultTextCodec(),
        )
        assertNull(
            languageLayout(
                generation = 3,
                platform = Platform.GBC,
                manifest = resolvedLanguageManifest(PokemonTextCodec.gbEnglish),
            ).defaultTextCodec(),
        )
        assertNull(
            languageLayout(
                generation = 2,
                platform = Platform.GBA,
                manifest = resolvedLanguageManifest(PokemonTextCodec.gbEnglish),
            ).defaultTextCodec(),
        )
    }

    @Test
    fun exposesExplicitUnknownAndAmbiguousStates() {
        assertEquals(LanguageResolutionStatus.UNKNOWN, RomLanguageManifest.UNKNOWN.status)
        assertTrue(RomLanguageManifest.UNKNOWN.projections.isEmpty())

        val ambiguous = RomLanguageManifest(
            defaultLanguage = null,
            projections = listOf(
                projection(LanguageTag.ENGLISH),
                projection(LanguageTag.GERMAN, "gb-german"),
            ),
            status = LanguageResolutionStatus.AMBIGUOUS,
        )
        assertEquals(null, ambiguous.defaultLanguage)
    }

    private fun languageLayout(
        generation: Int,
        platform: Platform,
        manifest: RomLanguageManifest,
    ): ResolvedRomLayout = ResolvedRomLayout(
        family = EngineFamily.EMERALD,
        generation = generation,
        platform = platform,
        speciesCount = 0,
        moveCount = 0,
        tables = ProfileTables(),
        languageManifest = manifest,
    )

    private fun projection(
        language: LanguageTag,
        codecId: String = "gb-english",
    ): RomLanguageProjection = RomLanguageProjection(
        language = language,
        codecId = codecId,
        codecVersion = 1,
        localizedTables = LocalizedTableLayout(),
        evidence = emptyList(),
        status = LanguageResolutionStatus.RESOLVED,
    )
}
