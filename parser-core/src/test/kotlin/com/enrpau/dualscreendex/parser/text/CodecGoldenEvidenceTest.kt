package com.enrpau.dualscreendex.parser.text

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class CodecGoldenEvidenceTest {
    @Test fun canonicalMatchesMatrixUtf8SortedCompactContract() {
        assertEquals("{\"a\":[true,null,21],\"z\":\"éあ가\\n\\\"\\\\\\t\\u0001\"}",
            CodecGoldenEvidence.canonical(linkedMapOf("z" to "éあ가\n\"\\\t", "a" to listOf(true, null, 21))))
    }

    @Test fun executesAllTwentyOneOfficialIdentitiesWithoutClaimingAcceptance() {
        val evidence = CodecGoldenEvidence.capture()
        assertEquals(false, evidence["acceptance"])
        assertEquals("EXECUTED", evidence["status"])
        val codecs = evidence["codecs"] as List<*>
        assertEquals(21, codecs.size)
        val expectedIds = (WesternPokemonTextCodecs.all + JapanesePokemonTextCodecs.all + KoreanGen2PokemonTextCodec.codec).map { it.id }.toSet()
        assertEquals(expectedIds, CodecGoldenVectors.all.map { it.identity.id }.toSet())
        for (row in codecs) {
            val check = (row as Map<*, *>)["check"] as Map<*, *>
            assertEquals(row.toString(), "PASS", check["status"])
            assertEquals(0, check["failures"])
            assertEquals(0, check["errors"])
            assertEquals(0, check["skipped"])
            val data = check["data"] as Map<*, *>
            assertEquals(data["vectorCount"], data["matchedCount"])
            assertTrue((data["vectorCount"] as Int) >= 15)
        }
        assertEquals(CodecGoldenEvidence.canonical(evidence), CodecGoldenEvidence.canonical(CodecGoldenEvidence.capture()))
        System.getProperty("codecGolden.output")?.let { output ->
            Files.writeString(Path.of(output), CodecGoldenEvidence.canonical(evidence) + "\n", java.nio.file.StandardOpenOption.CREATE_NEW)
        }
    }

    @Test fun wrongLiteralTextCannotProduceMatchedSummary() {
        val case = CodecGoldenVectors.all.first()
        val vector = case.vectors.first()
        val bad = vector.copy(expected = vector.expected.copy(decoded = vector.expected.decoded!!.copy(text="WRONG")))
        val failed = CodecGoldenEvidence.execute(case.copy(vectors = listOf(bad) + case.vectors.drop(1)))
        assertEquals("FAIL", failed["status"])
        assertNull(failed["data"])
        assertEquals(1, failed["failures"])
    }

    @Test fun wrongTokenAndCounterCannotHideBehindCorrectText() {
        val case = CodecGoldenVectors.all.first()
        val vector = case.vectors.first()
        val wrongCounter = vector.expected.copy(decoded = vector.expected.decoded!!.copy(consumedBytes=999))
        val wrongToken = vector.expected.copy(tokens=vector.expected.tokens!!.map { it.copy(byteCount=9) })
        for (expected in listOf(wrongCounter, wrongToken)) {
            val failed = CodecGoldenEvidence.execute(case.copy(vectors=listOf(vector.copy(expected=expected))))
            assertEquals("FAIL", failed["status"])
            assertNull(failed["data"])
        }
    }

    @Test fun manifestIsIndependentOfObservedCodecOutput() {
        val case = CodecGoldenVectors.all.first()
        val swapped = case.copy(codec=JapanesePokemonTextCodecs.gen1RedBlue)
        assertFalse(CodecGoldenEvidence.manifest(case).isEmpty())
        assertEquals(CodecGoldenEvidence.manifest(case), CodecGoldenEvidence.manifest(swapped))
        val failed = CodecGoldenEvidence.execute(swapped)
        assertEquals("FAIL", failed["status"])
        assertNull(failed["data"])
    }

    @Test fun rejectsIdentityVersionLanguageAndCharsetRelabeling() {
        val case = CodecGoldenVectors.all.first()
        for (identity in listOf(case.identity.copy(id="fake"),case.identity.copy(version=2),
            case.identity.copy(language="ja"),case.identity.copy(charset="GEN1_YELLOW_JAPANESE"),
            case.identity.copy(generation=2),case.identity.copy(platforms=listOf("GBA")),case.identity.copy(terminator=0xff))) {
            val failed = CodecGoldenEvidence.execute(case.copy(identity=identity))
            assertEquals(identity.toString(), "FAIL", failed["status"])
            assertNull(failed["data"])
        }
    }

    @Test fun emptyAndDuplicateVectorsCannotPass() {
        val case = CodecGoldenVectors.all.first()
        for (vectors in listOf(emptyList(), listOf(case.vectors.first(),case.vectors.first()))) {
            val failed = CodecGoldenEvidence.execute(case.copy(vectors=vectors))
            assertEquals("FAIL", failed["status"])
            assertNull(failed["data"])
        }
    }

    @Test fun sourceBindingContainsActualEmbeddedSourcesAndRuntimeClasses() {
        val binding = CodecGoldenEvidence.sourceBinding()
        val sources = binding["sources"] as? Map<*, *>
        assertNotNull(sources)
        assertTrue(sources!!.keys.any { it.toString().endsWith("KoreanGen2CharacterTables.kt") })
        assertTrue(sources.keys.any { it.toString().endsWith("CodecGoldenVectors.kt") })
        assertTrue(sources.keys.any { it.toString().endsWith("official_matrix.py") })
        assertTrue((binding["classes"] as Map<*, *>).keys.any { it.toString().endsWith("PokemonTextCodec.class") })
        assertTrue(sources.values.all { Regex("[0-9a-f]{64}").matches(it.toString()) })
        assertFalse(CodecGoldenEvidence.canonical(binding).contains(":/"))
    }

    @Test fun refusesAnUnregisteredDecoderEvenWhenAllIdentityLabelsMatch() {
        val case = CodecGoldenVectors.all.first()
        val original = case.codec
        val forged = PokemonTextCodec(original.id, original.version, original.language,
            original.applicableGenerations, original.applicablePlatforms, original.terminator) { _, _, _ ->
            PokemonTextToken.Glyph("WRONG")
        }
        val failed = CodecGoldenEvidence.execute(case.copy(codec=forged))
        assertEquals("FAIL", failed["status"])
        assertNull(failed["data"])
        assertEquals(false, failed["identityMatched"])
    }

    @Test fun manifestHashBindsIdentityInputBoundsTokensAndCounters() {
        val case = CodecGoldenVectors.all.first()
        val vector = case.vectors.first()
        val baseline = CodecGoldenEvidence.digest(CodecGoldenEvidence.manifest(case))
        fun replaced(v: CodecGoldenVectors.Vector) = case.copy(vectors=listOf(v)+case.vectors.drop(1))
        val variants = listOf(
            case.copy(identity=case.identity.copy(charset="different")),
            replaced(vector.copy(hex="80 50")),
            replaced(vector.copy(maximumBytes=1)),
            replaced(vector.copy(expected=vector.expected.copy(checks=999))),
            replaced(vector.copy(expected=vector.expected.copy(tokens=emptyList()))),
        )
        for (variant in variants) assertNotEquals(baseline, CodecGoldenEvidence.digest(CodecGoldenEvidence.manifest(variant)))
    }

    @Test fun serializerRejectsNonContractValues() {
        for (value in listOf(Double.NaN, 1.0, mapOf(1 to "not-string-key"), "\uD800")) {
            assertThrows(IllegalArgumentException::class.java) { CodecGoldenEvidence.canonical(value) }
        }
    }
}
