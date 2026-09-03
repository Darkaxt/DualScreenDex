package com.enrpau.dualscreendex.parser.language

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfficialLanguageResolverTest {
    @Test
    fun seedsGbaCandidatesOnlyFromRecognizedPokemonGameCodes() {
        assertEquals(
            LanguageTag.FRENCH,
            OfficialLanguageResolver.headerCandidate(
                RomHeader(Platform.GBA, "POKEMON EMER", gameCode = "BPEF"),
                generation = 3,
            )?.language,
        )
        assertEquals(
            LanguageTag.GERMAN,
            OfficialLanguageResolver.headerCandidate(
                RomHeader(Platform.GBA, "POKEMON FIRE", gameCode = "BPRD"),
                generation = 3,
            )?.language,
        )
        assertNull(
            OfficialLanguageResolver.headerCandidate(
                RomHeader(Platform.GBA, "CUSTOM", gameCode = "ZZZF"),
                generation = 3,
            ),
        )
    }

    @Test
    fun seedsGbAndGbcCandidatesOnlyFromRecognizedProductIdentifiers() {
        assertEquals(
            LanguageTag.SPANISH,
            OfficialLanguageResolver.headerCandidate(
                RomHeader(Platform.GBC, "POKEMON_GLD", gbManufacturerCode = "AAUS"),
                generation = 2,
            )?.language,
        )
        assertEquals(
            LanguageTag.ITALIAN,
            OfficialLanguageResolver.headerCandidate(
                RomHeader(Platform.GBC, "PM_CRYSTAL", gbManufacturerCode = "BYTI"),
                generation = 2,
            )?.language,
        )
        assertEquals(
            LanguageTag.FRENCH,
            OfficialLanguageResolver.headerCandidate(
                RomHeader(Platform.GBC, "POKEMON YELLOW", gbManufacturerCode = "APSF"),
                generation = 1,
            )?.language,
        )
        assertNull(
            OfficialLanguageResolver.headerCandidate(
                RomHeader(Platform.GBC, "CUSTOM", gbManufacturerCode = "ZZZF"),
                generation = 2,
            ),
        )
    }

    @Test
    fun resolvesRedBlueProbeLanguageFromTwoIndependentMenuLabels() {
        for ((language, labels) in RED_BLUE_LABELS) {
            val rom = RomImage(
                byteArrayOf(0x00) + encodeGb(labels.first) + byteArrayOf(0x50) +
                    byteArrayOf(0x00) + encodeGb(labels.second) + byteArrayOf(0x50),
            )
            val codec = OfficialLanguageResolver.preferredProbeCodec(
                rom = rom,
                header = RomHeader(Platform.GB, "POKEMON RED"),
                generation = 1,
                cancellation = ParserCancellationToken.NONE,
            )

            assertEquals(language, codec.language)
            assertEquals(setOf(1), codec.applicableGenerations)
        }
    }

    @Test
    fun doesNotTreatOneMenuLiteralOrAnOverseasDestinationAsLanguageAuthority() {
        val codec = OfficialLanguageResolver.preferredProbeCodec(
            rom = RomImage(encodeGb("NOUVEAU JEU")),
            header = RomHeader(Platform.GB, "POKEMON RED"),
            generation = 1,
            cancellation = ParserCancellationToken.NONE,
        )

        assertEquals(LanguageTag.ENGLISH, codec.language)
    }

    private fun encodeGb(value: String): ByteArray = ByteArray(value.length) { index ->
        when (val character = value[index]) {
            ' ' -> 0x7F
            else -> 0x80 + character.code - 'A'.code
        }.toByte()
    }

    private companion object {
        val RED_BLUE_LABELS = mapOf(
            LanguageTag.ENGLISH to ("NEW GAME" to "OPTION"),
            LanguageTag.FRENCH to ("NOUVEAU JEU" to "OPTIONS"),
            LanguageTag.GERMAN to ("NEUES SPIEL" to "OPTION"),
            LanguageTag.ITALIAN to ("NUOVO GIOCO" to "OPZIONI"),
            LanguageTag.SPANISH to ("JUEGO NUEVO" to "OPCIONES"),
        )
    }
}
